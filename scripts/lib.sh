# shellcheck shell=bash
# Shared helpers for scripts/smoke.sh, failover-demo.sh and api-tests.sh.
# Source it; do not run it. Everything here targets the LOCAL Compose stack;
# require_local_target refuses anything else instead of warning about it.

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
GATEWAY_URL=${GATEWAY_URL:-http://localhost:8080}
EUREKA_URL=${EUREKA_URL:-http://localhost:8761}
PROMETHEUS_URL=${PROMETHEUS_URL:-http://localhost:9090}

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
info() { printf '%s\n' "$*"; }

# Milliseconds since the epoch (GNU date).
now_ms() { date +%s%3N; }

compose() { docker compose --project-directory "$REPO_ROOT" "$@"; }

require_tools() {
  local tool
  for tool in "$@"; do
    command -v "$tool" >/dev/null 2>&1 || die "missing required tool: $tool"
  done
}

# These scripts stop and kill containers: pointed at anything but this
# machine's Docker and localhost ports, that is an outage, so they refuse.
# Docker picks its daemon from DOCKER_HOST, then DOCKER_CONTEXT, then the
# context selected with "docker context use", so all three are checked.
require_local_target() {
  local url host endpoint
  for url in "$GATEWAY_URL" "$EUREKA_URL" "$PROMETHEUS_URL"; do
    host=${url#*://}
    host=${host%%/*}
    host=${host%%:*}
    case "$host" in
      localhost|127.0.0.1) ;;
      *) die "refusing to run against '$url': only localhost or 127.0.0.1 is allowed" ;;
    esac
  done
  case "${DOCKER_HOST:-}" in
    ""|unix://*) ;;
    *) die "refusing to run with DOCKER_HOST=$DOCKER_HOST: only the local Docker daemon is allowed" ;;
  esac
  case "${DOCKER_CONTEXT:-}" in
    ""|default) ;;
    *) die "refusing to run with DOCKER_CONTEXT=$DOCKER_CONTEXT: only the default local context is allowed" ;;
  esac
  if command -v docker >/dev/null 2>&1; then
    endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null) \
      || die "could not read the active Docker context; refusing to run"
    case "$endpoint" in
      unix://*) ;;
      *) die "refusing to run: the active Docker context points at '$endpoint', not a local unix socket" ;;
    esac
  fi
  [ -f "$REPO_ROOT/compose.yaml" ] || die "compose.yaml not found under $REPO_ROOT"
}

# env_value KEY -> value of KEY in .env, unquoted. Callers keep it in a
# variable; it is never echoed.
env_value() {
  local key=$1 file="$REPO_ROOT/.env"
  [ -f "$file" ] || die ".env not found; copy .env.example and fill it in"
  sed -n "s/^${key}=//p" "$file" | tail -n 1 | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/"
}

# login USERNAME PASSWORD HEADER_FILE
# Writes "Authorization: Bearer <token>" to HEADER_FILE (mode 600). Neither the
# token nor the password is ever in argv, which ps shows to everyone: jq reads
# the password from its environment and curl reads the body from stdin.
login() {
  local user=$1 password=$2 header_file=$3 response token
  response=$(P=$password jq -n --arg u "$user" '{usernameOrEmail: $u, password: env.P}' \
    | curl -s -X POST "$GATEWAY_URL/api/auth/login" -H 'Content-Type: application/json' --data-binary @-)
  token=$(jq -r '.token // empty' <<<"$response" 2>/dev/null || true)
  [ -n "$token" ] || die "login as '$user' failed: $(jq -c 'del(.token)' <<<"$response" 2>/dev/null || echo "$response")"
  ( umask 077; printf 'Authorization: Bearer %s\n' "$token" > "$header_file" )
}

# eureka_up_count APP -> number of UP instances Eureka lists for APP (e.g. TOURNAMENT-SERVICE).
# Always an integer: an unknown app answers 404 with an empty body, and an
# empty count would make every "[ ... -lt N ]" that reads it return at once.
eureka_up_count() {
  local count
  count=$(curl -s -H 'Accept: application/json' "$EUREKA_URL/eureka/apps/$1" 2>/dev/null \
    | jq '[.application.instance[]? | select(.status == "UP")] | length' 2>/dev/null || true)
  case "$count" in
    ''|*[!0-9]*) count=0 ;;
  esac
  printf '%s\n' "$count"
}

# wait_for_eureka APP COUNT TIMEOUT_S -> waits until APP has COUNT UP instances.
wait_for_eureka() {
  local app=$1 want=$2 timeout=$3 start=$SECONDS
  while [ "$(eureka_up_count "$app")" -lt "$want" ]; do
    [ $((SECONDS - start)) -lt "$timeout" ] || return 1
    sleep 1
  done
}

# Prints "<instanceId> <status>" for every instance of every app in Eureka.
eureka_summary() {
  curl -s -H 'Accept: application/json' "$EUREKA_URL/eureka/apps" \
    | jq -r '.applications.application[] | .name as $n | "\($n) \(.instance | length) instance(s): \([.instance[] | "\(.instanceId)=\(.status)"] | join(", "))"' \
    | sort
}

# Prints "<service> <instance> <health>" for every Prometheus scrape target of the services job.
prometheus_targets() {
  curl -s "$PROMETHEUS_URL/api/v1/targets?state=active" \
    | jq -r '.data.activeTargets[] | select(.labels.job == "scoregrid-services") | "\(.labels.service) \(.labels.instance) \(.health)"' \
    | sort
}
