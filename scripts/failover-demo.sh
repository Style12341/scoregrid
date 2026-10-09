#!/usr/bin/env bash
# ScoreGrid failover demo against the LOCAL Compose stack.
#
#   scripts/failover-demo.sh instance      A: GET loop while one tournament replica is stopped, then killed
#   scripts/failover-demo.sh service-down  B: prediction-service stopped -> 503 envelope; time to recover
#   scripts/failover-demo.sh downstream    C: POST /api/predictions with one tournament replica killed
#   scripts/failover-demo.sh restore       default topology back (compose up -d + observability), then status
#   scripts/failover-demo.sh status        Eureka instances and Prometheus targets
#
# Logs in as the admin from .env; the password and token stay in variables and
# a mode-600 temp file, nothing secret is printed.
#
# Traps, learned on the live stack:
# * SIGTERM ("docker stop") deregisters the replica from Eureka and its web
#   server refuses connections at once (server.shutdown: immediate), so the
#   gateway's GET retry lands on the other replica. SIGKILL ("docker kill")
#   deregisters nothing: the dead address stays routable until the lease
#   expires (~20 s, Eureka doubles the 10 s setting) plus caches, 30-40 s in
#   all, and only the retry hides it. Eureka's UP count is no proof of
#   recovery: a killed replica's stale lease still says UP.
# * A restarted replica needs ~20-30 s to start and register, then ~10 s for
#   the gateway's registry fetch and LoadBalancer cache to pick it up.
# * Only GETs whose connection could not be opened are retried. A read
#   timeout or a write that hits a dead replica gets 503 DOWNSTREAM_UNAVAILABLE.
# * prediction-service stays at ONE replica: replicas share the
#   prediction.match-cache queue, so a match.updated would reach only one of
#   them (docs/demo.md#failover).
#
# What it does NOT do: load-test (one client, two requests a second), clean up
# data ("downstream" leaves its matches and predictions), or touch databases,
# RabbitMQ, the gateway or Eureka. On Ctrl-C it restarts what it took down
# without waiting for health: run "restore" after. It refuses to run unless
# every URL is localhost and Docker is the local daemon; there is no override.
set -euo pipefail

# shellcheck source=scripts/lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_local_target
require_tools docker curl jq awk

# Mirrors deploy.replicas of tournament-service in compose.yaml.
REPLICAS=2
WORK=$(mktemp -d)
AUTH="$WORK/auth.header"

# What a scenario has running or taken down right now, for cleanup().
LOOP_PID=
LOOP_STOP=
DOWN_CONTAINERS=()
PREDICTION_DOWN=0

# On any exit, Ctrl-C included: stop the background GET loop (a backgrounded
# subshell ignores SIGINT) and restart whatever the scenario took down.
cleanup() {
  local status=${1:-$?}
  trap - EXIT INT TERM
  if [ -n "$LOOP_PID" ]; then
    touch "$LOOP_STOP"
    kill "$LOOP_PID" 2>/dev/null || true
    wait "$LOOP_PID" 2>/dev/null || true
  fi
  if [ "${#DOWN_CONTAINERS[@]}" -gt 0 ]; then
    info "cleanup: starting ${DOWN_CONTAINERS[*]} again" >&2
    docker start "${DOWN_CONTAINERS[@]}" >/dev/null 2>&1 || info "cleanup: failed; run: $0 restore" >&2
  fi
  if [ "$PREDICTION_DOWN" = 1 ]; then
    info "cleanup: starting prediction-service again" >&2
    compose start prediction-service >/dev/null 2>&1 || info "cleanup: failed; run: $0 restore" >&2
  fi
  rm -rf "$WORK"
  exit "$status"
}
trap cleanup EXIT
trap 'cleanup 130' INT
trap 'cleanup 143' TERM

admin_login() {
  local user password
  user=$(env_value SCOREGRID_ADMIN_USERNAME)
  password=$(env_value SCOREGRID_ADMIN_PASSWORD)
  [ -n "$password" ] || die "SCOREGRID_ADMIN_PASSWORD is not set in .env"
  login "${user:-admin}" "$password" "$AUTH"
}

container_ids() { compose ps -q "$1"; }
container_name() { docker inspect --format '{{.Name}}' "$1" | sed 's#^/##'; }

# wait_for_healthy SERVICE TIMEOUT_S -> every running replica passes its healthcheck.
wait_for_healthy() {
  local start=$SECONDS id all
  while :; do
    all=1
    for id in $(container_ids "$1"); do
      [ "$(docker inspect --format '{{.State.Health.Status}}' "$id")" = healthy ] || all=0
    done
    [ "$all" = 1 ] && return 0
    [ $((SECONDS - start)) -lt "$2" ] || return 1
    sleep 1
  done
}

# Healthy, registered, and old enough for the gateway's caches to have seen it.
back_in_rotation() {
  wait_for_healthy tournament-service 180 || die "tournament-service replicas did not become healthy"
  wait_for_eureka TOURNAMENT-SERVICE "$REPLICAS" 180 || die "TOURNAMENT-SERVICE did not reach $REPLICAS UP"
  sleep 10
}

# probe METHOD PATH [BODY] -> "<epoch_ms> <http_status> <seconds>"; never fails the script.
probe() {
  local args=(-s -o "$WORK/last.body" -w '%{http_code} %{time_total}' -X "$1" -H @"$AUTH" --max-time 15) out
  [ -n "${3:-}" ] && args+=(-H 'Content-Type: application/json' --data-binary "$3")
  out=$(curl "${args[@]}" "$GATEWAY_URL$2" 2>/dev/null || true)
  printf '%s %s\n' "$(now_ms)" "${out:-000 0}"
}

# Background GET loop, one probe every 0.5 s, appended to FILE until STOPFILE exists.
start_loop() {
  LOOP_STOP=$3
  ( while [ ! -e "$3" ]; do probe GET "$1" >> "$2"; sleep 0.5; done ) &
  LOOP_PID=$!
}

# summarize FILE FROM_MS TO_MS LABEL -> requests, non-2xx and max latency in the window.
summarize() {
  awk -v from="$2" -v to="$3" -v label="$4" '
    $1 >= from && $1 < to { n++; if ($2 !~ /^2/) bad++; if ($3 > max) max = $3 }
    END { printf "%-28s requests=%d non2xx=%d max_latency=%.3fs\n", label, n, bad + 0, max }' "$1"
}

need_two_replicas() {
  [ "$(eureka_up_count TOURNAMENT-SERVICE)" -ge "$REPLICAS" ] \
    || die "need $REPLICAS UP tournament-service replicas; run: $0 restore"
}

cmd_restore() {
  compose --profile observability up -d
  wait_for_healthy tournament-service 240 || die "tournament-service replicas did not become healthy"
  wait_for_healthy prediction-service 240 || die "prediction-service did not become healthy"
  wait_for_eureka TOURNAMENT-SERVICE "$REPLICAS" 60 || die "TOURNAMENT-SERVICE has fewer than $REPLICAS UP"
  wait_for_eureka PREDICTION-SERVICE 1 60 || die "PREDICTION-SERVICE has no UP instance"
  info "waiting 10 s for the gateway's registry fetch and LoadBalancer cache"
  sleep 10
  cmd_status
}

cmd_status() {
  info "== Eureka ($EUREKA_URL)"
  eureka_summary
  info "== Prometheus targets, job scoregrid-services ($PROMETHEUS_URL)"
  if curl -s -o /dev/null "$PROMETHEUS_URL/-/ready"; then
    prometheus_targets
  else
    info "(not reachable: docker compose --profile observability up -d)"
  fi
}

# A: one tournament replica dies under a steady GET load.
cmd_instance() {
  need_two_replicas
  admin_login
  local victim log="$WORK/instance.log" t0 t1 t2 t3 t4
  victim=$(container_ids tournament-service | tail -n 1)
  info "victim: $(container_name "$victim")"

  start_loop /api/tournaments "$log" "$WORK/instance.stop"
  sleep 5
  t0=$(now_ms)
  info "SIGTERM (docker stop) ..."
  DOWN_CONTAINERS=("$victim")
  docker stop "$victim" >/dev/null
  sleep 20
  t1=$(now_ms)
  info "restarting it ..."
  docker start "$victim" >/dev/null
  DOWN_CONTAINERS=()
  back_in_rotation
  t2=$(now_ms)
  info "SIGKILL (docker kill) ..."
  DOWN_CONTAINERS=("$victim")
  docker kill "$victim" >/dev/null
  sleep 45
  t3=$(now_ms)
  info "restarting it ..."
  docker start "$victim" >/dev/null
  DOWN_CONTAINERS=()
  back_in_rotation
  t4=$(now_ms)
  touch "$LOOP_STOP"
  wait "$LOOP_PID" 2>/dev/null || true
  LOOP_PID=

  info "== GET /api/tournaments every 0.5 s"
  summarize "$log" 0 "$t0" "baseline"
  summarize "$log" "$t0" "$t1" "SIGTERM, replica down"
  summarize "$log" "$t1" "$t2" "SIGTERM, replica rejoining"
  summarize "$log" "$t2" "$t3" "SIGKILL, replica down"
  summarize "$log" "$t3" "$t4" "SIGKILL, replica rejoining"
  summarize "$log" 0 "$t4" "TOTAL"
}

# B: the whole prediction-service is down (its single replica).
cmd_service_down() {
  admin_login
  local i line start since
  since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  info "stopping prediction-service ..."
  PREDICTION_DOWN=1
  compose stop prediction-service
  info "== GET /api/predictions/me while it is down (status, seconds)"
  for i in $(seq 1 12); do
    line=$(probe GET /api/predictions/me)
    printf '  #%-2d %s\n' "$i" "${line#* }"
    sleep 0.5
  done
  info "last response body: $(jq -c . "$WORK/last.body" 2>/dev/null || true)"

  info "starting prediction-service again ..."
  start=$(now_ms)
  compose start prediction-service
  PREDICTION_DOWN=0
  for i in $(seq 1 240); do
    line=$(probe GET /api/predictions/me)
    case "$line" in *" 2"[0-9][0-9]" "*) break ;; esac
    sleep 1
  done
  info "first 2xx after $(( ($(now_ms) - start) / 1000 )) s"

  # Diagnostic only: no matching line must not fail the run.
  info "gateway log while it was down (count, root cause):"
  { compose logs api-gateway --since "$since" --no-log-prefix 2>/dev/null | grep '^{' \
      | jq -r 'select(.message | startswith("Downstream prediction-service unavailable"))
               | .message | capture("unavailable for [A-Z]+ [^ ]+: (?<cause>[A-Za-z]+)").cause' \
      | sort | uniq -c; } || info "  (none)"
}

# C: a write that needs tournament-service while one of its replicas is dead.
cmd_downstream() {
  need_two_replicas
  admin_login
  local tid group home away matches=() i id victim line ok=0
  tid=${TOURNAMENT_ID:-$(curl -s -H @"$AUTH" "$GATEWAY_URL/api/tournaments?status=ACTIVE" \
    | jq -r '(.content? // .)[0].id // empty')}
  [ -n "$tid" ] || die "no ACTIVE tournament; create one first (scripts/seed.sh)"
  group=$(curl -s -H @"$AUTH" "$GATEWAY_URL/api/tournaments/$tid/groups" | jq -r '.[0].id // empty')
  [ -n "$group" ] || die "tournament $tid has no group"
  read -r home away < <(curl -s -H @"$AUTH" "$GATEWAY_URL/api/groups/$group/teams" | jq -r '[.[].id] | "\(.[0] // "") \(.[1] // "")"')
  [ -n "$home" ] && [ -n "$away" ] || die "group $group needs two teams"

  # Enrol the admin as a participant; already enrolled is fine.
  curl -s -o /dev/null -X POST -H @"$AUTH" "$GATEWAY_URL/api/tournaments/$tid/join"

  info "creating 6 future matches in tournament $tid (group $group) ..."
  for i in $(seq 1 6); do
    # $RANDOM seconds, so reruns do not stack matches on the same kickoff times.
    id=$(jq -n --arg g "$group" --arg h "$home" --arg a "$away" \
           --arg t "$(date -u -d "+30 days +$i hours $RANDOM seconds" +%Y-%m-%dT%H:%M:%SZ)" \
           '{groupId: $g, homeTeamId: $h, awayTeamId: $a, startTime: $t}' \
         | curl -s -X POST -H @"$AUTH" -H 'Content-Type: application/json' --data-binary @- \
             "$GATEWAY_URL/api/tournaments/$tid/matches" | jq -r '.id // empty')
    [ -n "$id" ] || die "could not create a match in tournament $tid"
    matches+=("$id")
  done

  victim=$(container_ids tournament-service | tail -n 1)
  info "SIGKILL $(container_name "$victim") and predict right away ..."
  DOWN_CONTAINERS=("$victim")
  docker kill "$victim" >/dev/null
  for id in "${matches[@]}"; do
    line=$(probe POST /api/predictions "{\"matchId\":\"$id\",\"homeScore\":2,\"awayScore\":1}")
    case "$line" in *" 201 "*) ok=$((ok + 1)) ;; esac
    printf '  match %-6s -> %s  %s\n' "$id" "${line#* }" "$(jq -r '.error // empty' "$WORK/last.body" 2>/dev/null)"
    sleep 2
  done
  info "$ok/${#matches[@]} predictions accepted with one tournament-service replica dead"
  info "restarting the killed replica ..."
  docker start "$victim" >/dev/null
  DOWN_CONTAINERS=()
  back_in_rotation
  info "tournament-service back to $REPLICAS replicas in rotation"
}

case "${1:-}" in
  instance) cmd_instance ;;
  service-down) cmd_service_down ;;
  downstream) cmd_downstream ;;
  restore) cmd_restore ;;
  status) cmd_status ;;
  # The header comment, without its "# ", is the usage.
  *) awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "$0"; exit 2 ;;
esac
