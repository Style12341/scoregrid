#!/usr/bin/env bash
# ScoreGrid API tests: the Postman collection, headless, against the LOCAL stack.
#
#   scripts/api-tests.sh [extra newman options, e.g. --bail or --reporters cli,json]
#
# Runs postman/ScoreGrid.postman_collection.json with newman (pinned below),
# folder by folder in collection order:
#   00 Platform, 01 Admin journey, 02 Participant journey, 03 Result and scoring,
#   04 Contract errors, and the read-only reference 06.1-06.3.
# Exits with newman's exit code: 0 only when every assertion passed.
#
# The admin password is SCOREGRID_ADMIN_PASSWORD from .env. It is read into a
# variable (never echoed), written into a mode-600 copy of the environment
# file inside a private temp dir, and deleted on exit. It never reaches a
# command line, so it does not show up in ps, and nothing is exported.
#
# Traps this script and the collection encode:
#
# * Scoring is asynchronous. Loading a result publishes match.finished;
#   score-service scores it after consuming the event from RabbitMQ. Asserting
#   the ranking right after the PUT is a race, so 03 polls the ranking once a
#   second (pm.execution.setNextRequest) and fails after ~15 s. A run that fails
#   there means score-service is not consuming: docker compose logs score-service.
# * startDate must be strictly after the server's today, or activating the
#   tournament fails with VALIDATION_FAILED. The collection sets it two days
#   ahead in UTC, so a time-zone difference with the containers cannot bite.
# * Every run creates fresh data: new users, teams and tournaments with a
#   run-unique suffix. That is what makes reruns pass against the same
#   database, and it is also why the local database grows with each run.
# * Folder 00 asserts Prometheus targets, so the observability profile must be
#   up. Right after a failover scenario a killed replica's target stays "down"
#   for 30-40 s until Eureka evicts it: run scripts/failover-demo.sh restore and
#   wait for scripts/smoke.sh to pass first.
# * The first run downloads newman through npx (network needed once).
#
# What it does NOT do:
# * It does not reset or clean the database. Data from every run stays.
# * It does not run folder 05 (failover) or 06.4 (writes): those are manual.
#   It never stops, starts or scales a container.
# * It does not start the stack. It refuses to run unless the gateway, Eureka
#   and Prometheus URLs are localhost/127.0.0.1 and Docker is the local daemon
#   (same guard as scripts/smoke.sh and scripts/failover-demo.sh).
set -euo pipefail

# shellcheck source=scripts/lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_local_target
require_tools npx jq curl

NEWMAN_VERSION=6.2.2
COLLECTION="$REPO_ROOT/postman/ScoreGrid.postman_collection.json"
ENVIRONMENT="$REPO_ROOT/postman/ScoreGrid-local.postman_environment.json"
# Collection order. Folder 05 and 06.4 are left out on purpose (see header).
FOLDERS=(
  "00 Platform"
  "01 Admin journey"
  "02 Participant journey"
  "03 Result and scoring"
  "04 Contract errors"
  "06.1 Auth service"
  "06.2 Tournament service"
  "06.3 Prediction service"
)

curl -s -o /dev/null --max-time 5 "$GATEWAY_URL/actuator/health" \
  || die "gateway not reachable at $GATEWAY_URL; start the stack (docs/demo.md)"
curl -s -o /dev/null --max-time 5 "$PROMETHEUS_URL/-/ready" \
  || die "Prometheus not reachable at $PROMETHEUS_URL; folder 00 needs it: docker compose --profile observability up -d"

admin_user=$(env_value SCOREGRID_ADMIN_USERNAME)
admin_user=${admin_user:-admin}
admin_password=$(env_value SCOREGRID_ADMIN_PASSWORD)
[ -n "$admin_password" ] || die "SCOREGRID_ADMIN_PASSWORD is not set in .env"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
RUN_ENV="$WORK/environment.json"

# The password reaches jq through its environment ($ENV), not its argv.
(
  umask 077
  SG_ADMIN_PASSWORD="$admin_password" jq \
    --arg baseUrl "$GATEWAY_URL" --arg eurekaUrl "$EUREKA_URL" \
    --arg prometheusUrl "$PROMETHEUS_URL" --arg adminUsername "$admin_user" '
      .values |= map(
        if .key == "adminPassword" then .value = $ENV.SG_ADMIN_PASSWORD
        elif .key == "adminUsername" then .value = $adminUsername
        elif .key == "baseUrl" then .value = $baseUrl
        elif .key == "eurekaUrl" then .value = $eurekaUrl
        elif .key == "prometheusUrl" then .value = $prometheusUrl
        else . end)' "$ENVIRONMENT" > "$RUN_ENV"
)
unset admin_password

folder_args=()
for folder in "${FOLDERS[@]}"; do folder_args+=(--folder "$folder"); done

status=0
npx --yes "newman@$NEWMAN_VERSION" run "$COLLECTION" \
  --environment "$RUN_ENV" "${folder_args[@]}" "$@" || status=$?
exit "$status"
