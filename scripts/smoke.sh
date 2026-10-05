#!/usr/bin/env bash
# ScoreGrid smoke check: is the local Compose stack up and wired together?
#
#   scripts/smoke.sh
#
# Read-only. It only sends GETs (and one unauthenticated GET that must be
# refused). Exits non-zero if any check fails, so it can gate a demo rehearsal.
#
# Checks:
#   1. docker compose ps   every service with a healthcheck reports healthy
#   2. Eureka              gateway + the four business services have >= 1 UP
#                          instance; prints how many each has. Fewer than 2
#                          tournament-service instances is a WARN, not a
#                          failure: the platform works, failover does not
#   3. Gateway edge        GET /api/tournaments without a token is a 401 in the
#                          contract error envelope (error = UNAUTHORIZED)
#   4. Prometheus          every scoregrid-services target is up (skipped when
#                          the observability profile is not running)
#
# What it does NOT do: log in, write data, or exercise business flows. A green
# smoke run says the platform is wired, not that predictions score correctly.
# It never reads .env. It refuses to run against anything but localhost.
set -euo pipefail

# shellcheck source=scripts/lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_local_target
require_tools docker curl jq

failures=0
pass() { printf 'PASS  %s\n' "$*"; }
warn() { printf 'WARN  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*"; failures=$((failures + 1)); }

# 1. Container health. Services without a healthcheck (frontend, observability)
# report an empty Health and are listed for information only.
unhealthy=$(compose ps --format json \
  | jq -rs 'flatten | .[] | select(.Health != "" and .Health != "healthy") | "\(.Name)=\(.Health)"')
running=$(compose ps --format json | jq -s 'flatten | length')
if [ -n "$unhealthy" ]; then
  fail "containers not healthy: $(echo "$unhealthy" | tr '\n' ' ')"
elif [ "$running" -eq 0 ]; then
  fail "no containers running; start the stack with: docker compose up -d --build"
else
  pass "$running containers running, every healthcheck healthy"
fi

# 2. Eureka registrations.
for app in API-GATEWAY AUTH-SERVICE TOURNAMENT-SERVICE PREDICTION-SERVICE SCORE-SERVICE; do
  count=$(eureka_up_count "$app")
  if [ "$count" -ge 1 ]; then
    pass "eureka $app: $count UP"
  else
    fail "eureka $app: no UP instance"
  fi
done
# tournament-service runs 2 replicas by default (deploy.replicas in compose.yaml);
# with one, a GET to it has nowhere to fail over to.
count=$(eureka_up_count TOURNAMENT-SERVICE)
if [ "$count" -lt 2 ]; then
  warn "eureka TOURNAMENT-SERVICE: $count UP, failover needs 2 (scripts/failover-demo.sh restore)"
fi

# 3. Edge authentication answers in the contract envelope.
body=$(mktemp)
trap 'rm -f "$body"' EXIT
status=$(curl -s -o "$body" -w '%{http_code}' "$GATEWAY_URL/api/tournaments" || echo 000)
error=$(jq -r '.error // empty' "$body" 2>/dev/null || true)
if [ "$status" = 401 ] && [ "$error" = UNAUTHORIZED ]; then
  pass "gateway: unauthenticated GET /api/tournaments -> 401 UNAUTHORIZED envelope"
else
  fail "gateway: expected 401 UNAUTHORIZED envelope, got HTTP $status error='$error'"
fi

# 4. Prometheus scrape targets, one per replica.
if curl -s -o /dev/null "$PROMETHEUS_URL/-/ready"; then
  targets=$(prometheus_targets)
  down=$(awk '$3 != "up"' <<<"$targets")
  total=$(grep -c . <<<"$targets" || true)
  if [ "$total" -eq 0 ]; then
    fail "prometheus: no scoregrid-services targets"
  elif [ -n "$down" ]; then
    fail "prometheus: targets not up: $(echo "$down" | tr '\n' ';')"
  else
    pass "prometheus: $total scoregrid-services targets up"
  fi
else
  info "SKIP  prometheus not reachable (docker compose --profile observability up -d)"
fi

if [ "$failures" -gt 0 ]; then
  info "$failures check(s) failed"
  exit 1
fi
info "all checks passed"
