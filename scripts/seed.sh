#!/usr/bin/env bash
# ScoreGrid demo data seeder against the LOCAL Compose stack.
#
#   scripts/seed.sh
#
# Creates, through the gateway (http://localhost:8080) and its public REST API
# only:
#   * 3 PLAYER accounts: sofia, lucas, carla — one shared password from .env
#   * 10 Argentine teams
#   * "Liga Master": 10 teams in one group, 45 single round-robin matches in
#     9 rounds, one round a day, so every team plays once per day
#   * "Copa UTN": 8 teams, 4 knockout phases, only the 4 quarter-final matches;
#     later rounds are built from results in the admin ("Armar siguiente fase")
#   * enrolment of the 3 players in both ACTIVE tournaments
#
# Required .env keys: SCOREGRID_ADMIN_USERNAME (default "admin"),
# SCOREGRID_ADMIN_PASSWORD and SEED_USER_PASSWORD (at least 8 characters).
# No password is ever printed, exported or passed on a command line.
#
# Idempotent: teams, tournaments and users are matched by name and reused, so a
# second run creates nothing and exits 0. An existing tournament is kept only
# when its structure looks complete (expected group and phase counts, at least
# the expected matches, since the admin adds knockout rounds later) and it is
# ACTIVE; otherwise the script warns and skips it. It never deletes data.
#
# JSON is parsed and built with jq when available, otherwise with python3; the
# backend in use is reported. The script refuses to run against anything but
# localhost and the local Docker daemon (shared guard in scripts/lib.sh).
set -euo pipefail

# shellcheck source=scripts/lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_local_target
require_tools curl

WORK=$(mktemp -d)
trap 'rm -rf "$WORK" || true' EXIT
BODY_FILE="$WORK/body.json"
ADMIN_AUTH="$WORK/admin.header"

warn() { printf 'WARN  %s\n' "$*" >&2; }

usage() {
    # The header comment, without its "# ", is the usage.
    awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "$0"
}

# ────────────────────────────── JSON backend ─────────────────────────────────
# One function per operation, two implementations. Everything reads stdin and
# writes stdout except the body_* builders, which return a JSON document.

PY_JSON='
import json, os, sys

def stdin_json():
    try:
        return json.loads(sys.stdin.read())
    except Exception:
        return None

def value(item, key):
    if not isinstance(item, dict):
        return ""
    v = item.get(key)
    return "" if v is None else v

mode = sys.argv[1]

if mode == "scalar":
    data = stdin_json()
    print(value(data if isinstance(data, dict) else {}, sys.argv[2]))
elif mode == "content":
    data = stdin_json()
    print(json.dumps(data.get("content", []) if isinstance(data, dict) else []))
elif mode == "pairs":
    data = stdin_json()
    for item in data if isinstance(data, list) else []:
        print("%s\t%s" % (value(item, sys.argv[2]), value(item, sys.argv[3])))
elif mode == "values":
    data = stdin_json()
    for item in data if isinstance(data, list) else []:
        v = value(item, sys.argv[2])
        if v != "":
            print(v)
elif mode == "count":
    data = stdin_json()
    print(len(data) if isinstance(data, list) else 0)
elif mode == "build-login":
    print(json.dumps({"usernameOrEmail": os.environ["SG_USER"], "password": os.environ["SG_PASS"]}))
elif mode == "build-register":
    print(json.dumps({"username": os.environ["SG_USER"], "email": os.environ["SG_MAIL"], "password": os.environ["SG_PASS"]}))
elif mode == "build-team":
    print(json.dumps({"name": os.environ["SG_NAME"], "shortName": os.environ["SG_SHORT"],
                      "country": os.environ["SG_COUNTRY"], "logoUrl": None}))
elif mode == "build-tournament":
    print(json.dumps({"name": os.environ["SG_NAME"], "description": os.environ["SG_DESC"],
                      "startDate": os.environ["SG_START"], "endDate": os.environ["SG_END"]}))
elif mode == "build-teams":
    print(json.dumps({"teamIds": sys.argv[2:]}))
elif mode == "build-group":
    print(json.dumps({"name": os.environ["SG_NAME"], "displayOrder": int(os.environ["SG_ORDER"])}))
elif mode == "build-phase":
    print(json.dumps({"type": os.environ["SG_TYPE"], "name": os.environ["SG_NAME"],
                      "displayOrder": int(os.environ["SG_ORDER"])}))
elif mode == "build-match":
    print(json.dumps({"groupId": os.environ.get("SG_GROUP") or None,
                      "phaseId": os.environ.get("SG_PHASE") or None,
                      "homeTeamId": os.environ["SG_HOME"],
                      "awayTeamId": os.environ["SG_AWAY"],
                      "startTime": os.environ["SG_START"]}))
else:
    sys.stderr.write("pyjson: unknown mode %s\n" % mode)
    sys.exit(2)
'

pyjson() { python3 -c "$PY_JSON" "$@"; }

detect_json_tool() {
    if command -v jq >/dev/null 2>&1; then
        JSON_TOOL=jq
    elif command -v python3 >/dev/null 2>&1; then
        JSON_TOOL=python3
    else
        die "need jq or python3 for JSON handling"
    fi
}

# json_scalar KEY            object  -> value
json_scalar() {
    if [ "$JSON_TOOL" = jq ]; then
        jq -r --arg k "$1" '.[$k] // empty' 2>/dev/null || true
    else
        pyjson scalar "$1"
    fi
}

# json_content               paged object -> its "content" array
json_content() {
    if [ "$JSON_TOOL" = jq ]; then
        jq -c '.content // []' 2>/dev/null || true
    else
        pyjson content
    fi
}

# json_pairs KEY_A KEY_B     array -> "A<TAB>B" lines
json_pairs() {
    if [ "$JSON_TOOL" = jq ]; then
        jq -r --arg a "$1" --arg b "$2" '.[] | "\(.[$a])\t\(.[$b])"' 2>/dev/null || true
    else
        pyjson pairs "$1" "$2"
    fi
}

# json_values KEY            array -> KEY per line
json_values() {
    if [ "$JSON_TOOL" = jq ]; then
        jq -r --arg k "$1" '.[] | .[$k] // empty' 2>/dev/null || true
    else
        pyjson values "$1"
    fi
}

# json_count                 array -> length
json_count() {
    if [ "$JSON_TOOL" = jq ]; then
        jq 'length' 2>/dev/null || true
    else
        pyjson count
    fi
}

body_login() { # USER PASSWORD
    if [ "$JSON_TOOL" = jq ]; then
        SG_USER=$1 SG_PASS=$2 jq -n '{usernameOrEmail: env.SG_USER, password: env.SG_PASS}'
    else
        SG_USER=$1 SG_PASS=$2 pyjson build-login
    fi
}

body_register() { # USER EMAIL PASSWORD
    if [ "$JSON_TOOL" = jq ]; then
        SG_USER=$1 SG_MAIL=$2 SG_PASS=$3 jq -n \
            '{username: env.SG_USER, email: env.SG_MAIL, password: env.SG_PASS}'
    else
        SG_USER=$1 SG_MAIL=$2 SG_PASS=$3 pyjson build-register
    fi
}

body_team() { # NAME SHORT_NAME COUNTRY
    if [ "$JSON_TOOL" = jq ]; then
        SG_NAME=$1 SG_SHORT=$2 SG_COUNTRY=$3 jq -n \
            '{name: env.SG_NAME, shortName: env.SG_SHORT, country: env.SG_COUNTRY, logoUrl: null}'
    else
        SG_NAME=$1 SG_SHORT=$2 SG_COUNTRY=$3 pyjson build-team
    fi
}

body_tournament() { # NAME DESCRIPTION START_DATE END_DATE
    if [ "$JSON_TOOL" = jq ]; then
        SG_NAME=$1 SG_DESC=$2 SG_START=$3 SG_END=$4 jq -n \
            '{name: env.SG_NAME, description: env.SG_DESC, startDate: env.SG_START, endDate: env.SG_END}'
    else
        SG_NAME=$1 SG_DESC=$2 SG_START=$3 SG_END=$4 pyjson build-tournament
    fi
}

body_teams() { # TEAM_ID...
    if [ "$JSON_TOOL" = jq ]; then
        jq -n --args '{teamIds: $ARGS.positional}' -- "$@"
    else
        pyjson build-teams "$@"
    fi
}

body_group() { # NAME DISPLAY_ORDER
    if [ "$JSON_TOOL" = jq ]; then
        SG_NAME=$1 SG_ORDER=$2 jq -n '{name: env.SG_NAME, displayOrder: (env.SG_ORDER | tonumber)}'
    else
        SG_NAME=$1 SG_ORDER=$2 pyjson build-group
    fi
}

body_phase() { # TYPE NAME DISPLAY_ORDER
    if [ "$JSON_TOOL" = jq ]; then
        SG_TYPE=$1 SG_NAME=$2 SG_ORDER=$3 jq -n \
            '{type: env.SG_TYPE, name: env.SG_NAME, displayOrder: (env.SG_ORDER | tonumber)}'
    else
        SG_TYPE=$1 SG_NAME=$2 SG_ORDER=$3 pyjson build-phase
    fi
}

body_match() { # GROUP_ID PHASE_ID HOME_TEAM_ID AWAY_TEAM_ID START_TIME
    if [ "$JSON_TOOL" = jq ]; then
        SG_GROUP=${1:-} SG_PHASE=${2:-} SG_HOME=$3 SG_AWAY=$4 SG_START=$5 jq -n \
            '{groupId: (if env.SG_GROUP == "" then null else env.SG_GROUP end),
        phaseId: (if env.SG_PHASE == "" then null else env.SG_PHASE end),
        homeTeamId: env.SG_HOME, awayTeamId: env.SG_AWAY, startTime: env.SG_START}'
    else
        SG_GROUP=${1:-} SG_PHASE=${2:-} SG_HOME=$3 SG_AWAY=$4 SG_START=$5 pyjson build-match
    fi
}

body_status() { # STATUS
    printf '{"status":"%s"}' "$1"
}

# ──────────────────────────────── HTTP helpers ───────────────────────────────

# api METHOD PATH [HEADER_FILE] [JSON_BODY]
# Sends the request to the gateway, leaves the response body in BODY_FILE and
# echoes the HTTP status (000 when the connection could not be opened).
api() {
    local method=$1 path=$2 header=${3:-} body=${4:-} status
    local -a args=(-s -o "$BODY_FILE" -w '%{http_code}' -X "$method" --max-time 30)
    if [ -n "$header" ]; then
        args+=(-H "@$header")
    fi
    if [ -n "$body" ]; then
        args+=(-H 'Content-Type: application/json' --data-binary @-)
        status=$(printf '%s' "$body" | curl "${args[@]}" "$GATEWAY_URL$path" 2>/dev/null) || status=000
    else
        status=$(curl "${args[@]}" "$GATEWAY_URL$path" 2>/dev/null) || status=000
    fi
    printf '%s\n' "${status:-000}"
}

expect_status() { # WANT ACTUAL CONTEXT
    if [ "$2" != "$1" ]; then
        die "$3 failed: HTTP $2 $(json_scalar error <"$BODY_FILE") $(json_scalar message <"$BODY_FILE")"
    fi
}

wait_for_gateway() {
    local start=$SECONDS
    while ! curl -fsS -o /dev/null --max-time 5 "$GATEWAY_URL/actuator/health"; do
        if [ $((SECONDS - start)) -ge 90 ]; then
            die "gateway not reachable at $GATEWAY_URL; start the stack with: docker compose up -d"
        fi
        info "waiting for the gateway at $GATEWAY_URL ..."
        sleep 2
    done
}

# login_user USER PASSWORD HEADER_FILE
# Reuses the shared helper when jq is available; a python3 fallback keeps the
# script runnable without jq. The token lands in a mode-600 header file.
login_user() {
    local user=$1 password=$2 header=$3 status token error
    if [ "$JSON_TOOL" = jq ]; then
        login "$user" "$password" "$header"
        return 0
    fi
    status=$(api POST /api/auth/login "" "$(body_login "$user" "$password")")
    token=$(json_scalar token <"$BODY_FILE")
    if [ "$status" != 200 ] || [ -z "$token" ]; then
        error=$(json_scalar error <"$BODY_FILE")
        die "login as '$user' failed: HTTP $status ${error:+($error)}"
    fi
    (
        umask 077
        printf 'Authorization: Bearer %s\n' "$token" >"$header"
    )
}

load_env() {
    ADMIN_USER=$(env_value SCOREGRID_ADMIN_USERNAME)
    ADMIN_USER=${ADMIN_USER:-admin}
    ADMIN_PASSWORD=$(env_value SCOREGRID_ADMIN_PASSWORD)
    [ -n "$ADMIN_PASSWORD" ] || die "SCOREGRID_ADMIN_PASSWORD is not set in .env"
    SEED_PASSWORD=$(env_value SEED_USER_PASSWORD)
    [ -n "$SEED_PASSWORD" ] || die "SEED_USER_PASSWORD is not set in .env; add a demo password of at least 8 characters (see .env.example)"
    [ "${#SEED_PASSWORD}" -ge 8 ] || die "SEED_USER_PASSWORD is shorter than 8 characters"
}

admin_login() {
    if ! (login_user "$ADMIN_USER" "$ADMIN_PASSWORD" "$ADMIN_AUTH"); then
        die "admin login failed. If this stack was first provisioned with a different SCOREGRID_ADMIN_PASSWORD, the stored hash no longer matches .env. Do not drop volumes; align .env or re-provision deliberately."
    fi
}

# ─────────────────────────────── Seed data ───────────────────────────────────

TEAM_NAMES=(
    "Atlético Central" "Deportivo Norte" "Unión del Sur" "Estrella del Oeste"
    "Club Portuario" "Villa Nueva FC" "Sportivo Andino" "Ribera FC"
    "Monteverde FC" "Ciudad Vieja FC"
)
TEAM_SHORTS=(CENT NORTE SUR OESTE PUERTO VILLA ANDINO RIBERA MONTE VIEJA)
TEAM_IDS=()

LIGA_NAME="Liga Master"
LIGA_DESC="Liga de todos contra todos"
COPA_NAME="Copa UTN"
COPA_DESC="Copa a eliminación directa"

PLAYERS=(
    "sofia:sofia@scoregrid.local"
    "lucas:lucas@scoregrid.local"
    "carla:carla@scoregrid.local"
)

CREATED_TEAMS=0
SKIPPED_TEAMS=0
CREATED_TOURNAMENTS=0
SKIPPED_TOURNAMENTS=0
CREATED_USERS=0
EXISTING_USERS=0
JOINED=0
ALREADY_ENROLLED=0
TOURNAMENT_ID=
TOURNAMENT_CREATED=0
TOURNAMENT_A_ID=
TOURNAMENT_B_ID=

# ──────────────────────────────── Teams ──────────────────────────────────────

seed_teams() {
    local status i id name short
    local -A existing=()
    status=$(api GET /api/teams "$ADMIN_AUTH")
    if [ "$status" = 200 ]; then
        while IFS=$'\t' read -r id name; do
            if [ -n "$id" ]; then
                existing[$name]=$id
            fi
        done < <(json_pairs id name <"$BODY_FILE")
    else
        die "listing teams failed: HTTP $status"
    fi
    for i in "${!TEAM_NAMES[@]}"; do
        name=${TEAM_NAMES[$i]}
        short=${TEAM_SHORTS[$i]}
        if [ -n "${existing[$name]:-}" ]; then
            TEAM_IDS+=("${existing[$name]}")
            SKIPPED_TEAMS=$((SKIPPED_TEAMS + 1))
            continue
        fi
        status=$(api POST /api/teams "$ADMIN_AUTH" "$(body_team "$name" "$short" AR)")
        expect_status 201 "$status" "creating team '$name'"
        id=$(json_scalar id <"$BODY_FILE")
        TEAM_IDS+=("$id")
        CREATED_TEAMS=$((CREATED_TEAMS + 1))
    done
    info "teams: $CREATED_TEAMS created, $SKIPPED_TEAMS already present"
    [ "${#TEAM_IDS[@]}" -eq 10 ] || die "expected 10 team ids, got ${#TEAM_IDS[@]}"
}

# ───────────────────────────── Tournaments ───────────────────────────────────

# find_tournament_id NAME -> tournament id by exact name, or empty.
find_tournament_id() {
    local name=$1 page=0 total=1 id tname
    while [ "$page" -lt "$total" ]; do
        api GET "/api/tournaments?page=$page&size=100" "$ADMIN_AUTH" >/dev/null
        total=$(json_scalar totalPages <"$BODY_FILE")
        case "$total" in
        '' | *[!0-9]*) total=0 ;;
        esac
        while IFS=$'\t' read -r id tname; do
            if [ -n "$id" ] && [ "$tname" = "$name" ]; then
                printf '%s\n' "$id"
                return 0
            fi
        done < <(json_content <"$BODY_FILE" | json_pairs id name)
        page=$((page + 1))
    done
    return 0
}

# is_complete ID GROUPS PHASES MATCHES -> true when ACTIVE with those group and
# phase counts and at least MATCHES matches.
is_complete() {
    local id=$1 eg=$2 ep=$3 em=$4 status groups phases matches
    api GET "/api/tournaments/$id" "$ADMIN_AUTH" >/dev/null
    status=$(json_scalar status <"$BODY_FILE")
    api GET "/api/tournaments/$id/groups" "$ADMIN_AUTH" >/dev/null
    groups=$(json_count <"$BODY_FILE")
    api GET "/api/tournaments/$id/phases" "$ADMIN_AUTH" >/dev/null
    phases=$(json_count <"$BODY_FILE")
    api GET "/api/tournaments/$id/matches" "$ADMIN_AUTH" >/dev/null
    matches=$(json_count <"$BODY_FILE")
    info "checking '$id': status=$status groups=$groups phases=$phases matches=$matches" >&2
    [ "$status" = ACTIVE ] && [ "$groups" = "$eg" ] && [ "$phases" = "$ep" ] && [ "${matches:-0}" -ge "$em" ]
}

create_tournament() { # NAME DESCRIPTION START END
    local status
    status=$(api POST /api/tournaments "$ADMIN_AUTH" "$(body_tournament "$1" "$2" "$3" "$4")")
    expect_status 201 "$status" "creating tournament '$1'"
    json_scalar id <"$BODY_FILE"
}

# ensure_tournament NAME DESCRIPTION START END GROUPS PHASES MATCHES
# Sets TOURNAMENT_ID and TOURNAMENT_CREATED. Returns 1 when it exists but is
# incomplete, so the caller warns and keeps its hands off.
ensure_tournament() {
    local name=$1 desc=$2 start=$3 end=$4 eg=$5 ep=$6 em=$7
    TOURNAMENT_CREATED=0
    TOURNAMENT_ID=$(find_tournament_id "$name")
    if [ -n "$TOURNAMENT_ID" ]; then
        SKIPPED_TOURNAMENTS=$((SKIPPED_TOURNAMENTS + 1))
        if is_complete "$TOURNAMENT_ID" "$eg" "$ep" "$em"; then
            info "tournament '$name' already exists and is complete (id $TOURNAMENT_ID) — skipped"
            return 0
        fi
        return 1
    fi
    TOURNAMENT_ID=$(create_tournament "$name" "$desc" "$start" "$end")
    CREATED_TOURNAMENTS=$((CREATED_TOURNAMENTS + 1))
    TOURNAMENT_CREATED=1
    info "tournament '$name' created (id $TOURNAMENT_ID, DRAFT)"
    return 0
}

activate_tournament() { # ID NAME
    local status
    status=$(api PATCH "/api/tournaments/$1/status" "$ADMIN_AUTH" "$(body_status ACTIVE)")
    expect_status 200 "$status" "activating tournament '$2'"
    info "tournament '$2' is ACTIVE"
}

create_phase() { # TOURNAMENT_ID TYPE NAME ORDER -> phase id
    local status
    status=$(api POST "/api/tournaments/$1/phases" "$ADMIN_AUTH" "$(body_phase "$2" "$3" "$4")")
    expect_status 201 "$status" "creating phase '$3'"
    json_scalar id <"$BODY_FILE"
}

create_match() { # TOURNAMENT_ID GROUP_ID PHASE_ID HOME AWAY START CONTEXT
    local status
    status=$(api POST "/api/tournaments/$1/matches" "$ADMIN_AUTH" \
        "$(body_match "$2" "$3" "$4" "$5" "$6")")
    expect_status 201 "$status" "$7"
}

build_liga() { # TOURNAMENT_ID
    local id=$1 status group k=0 round slot day start home away
    local -a ids=("${TEAM_IDS[@]}")
    status=$(api POST "/api/tournaments/$id/teams" "$ADMIN_AUTH" "$(body_teams "${ids[@]}")")
    expect_status 200 "$status" "assigning the 10 teams to '$LIGA_NAME'"
    status=$(api POST "/api/tournaments/$id/groups" "$ADMIN_AUTH" "$(body_group "Tabla general" 1)")
    expect_status 201 "$status" "creating group 'Tabla general'"
    group=$(json_scalar id <"$BODY_FILE")
    status=$(api POST "/api/groups/$group/teams" "$ADMIN_AUTH" "$(body_teams "${ids[@]}")")
    expect_status 200 "$status" "assigning the 10 teams to group 'Tabla general'"

    # Full single round-robin by the circle method, like the admin's "Generar
    # fixture": the first seat stays, the others rotate one seat per round, so
    # each of the 9 rounds has every team playing exactly once. Round r is played
    # on day base+r, its 5 matches two hours apart from 14:00 UTC. The base is two
    # weeks out, so every kickoff is strictly in the future. (Pairing i<j in
    # order instead had one team play 5 times on day one.)
    local first_kickoff_hour=14 hours_between_kickoffs=2
    local -a seats=("${ids[@]}")
    local n=${#seats[@]}
    info "Liga: creating 45 round-robin matches in 9 rounds ..."
    for ((round = 0; round < n - 1; round++)); do
        day=$(date -u -d "$LIGA_BASE_DATE +$round days" +%Y-%m-%d)
        for ((slot = 0; slot < n / 2; slot++)); do
            home=${seats[$slot]}
            away=${seats[$((n - 1 - slot))]}
            # The fixed seat alternates home and away, so its team does not host every round.
            if [ "$slot" -eq 0 ] && [ $((round % 2)) -eq 1 ]; then
                home=${seats[$((n - 1))]}
                away=${seats[0]}
            fi
            start="${day}T$(printf '%02d' $((first_kickoff_hour + hours_between_kickoffs * slot))):00:00Z"
            create_match "$id" "$group" "" "$home" "$away" "$start" \
                "creating Liga match $((k + 1))/45"
            k=$((k + 1))
        done
        # Every seat but the first moves one place: the last one becomes the second.
        seats=("${seats[0]}" "${seats[$((n - 1))]}" "${seats[@]:1:$((n - 2))}")
    done
}

build_copa() { # TOURNAMENT_ID
    local id=$1 status qf
    local -a ids=("${TEAM_IDS[@]:0:8}")
    status=$(api POST "/api/tournaments/$id/teams" "$ADMIN_AUTH" "$(body_teams "${ids[@]}")")
    expect_status 200 "$status" "assigning the 8 teams to '$COPA_NAME'"

    qf=$(create_phase "$id" QUARTER_FINAL "Cuartos de final" 1)
    create_phase "$id" SEMI_FINAL "Semifinales" 2 >/dev/null
    create_phase "$id" THIRD_PLACE "Tercer puesto" 3 >/dev/null
    create_phase "$id" FINAL "Final" 4 >/dev/null

    # Only the quarter-finals: who plays the later rounds depends on results.
    # Load them in the admin, then "Armar siguiente fase" proposes each round.
    info "Copa: creating the 4 quarter-final matches ..."
    create_match "$id" "" "$qf" "${ids[0]}" "${ids[7]}" "${DAY30}T18:00:00Z" "creating Copa QF1"
    create_match "$id" "" "$qf" "${ids[3]}" "${ids[4]}" "${DAY30}T20:00:00Z" "creating Copa QF2"
    create_match "$id" "" "$qf" "${ids[2]}" "${ids[5]}" "${DAY31}T18:00:00Z" "creating Copa QF3"
    create_match "$id" "" "$qf" "${ids[1]}" "${ids[6]}" "${DAY31}T20:00:00Z" "creating Copa QF4"
}

seed_tournaments() {
    # Tournament A — league, all 10 teams, 1 group, 45 matches.
    if ensure_tournament "$LIGA_NAME" "$LIGA_DESC" "$LIGA_START" "$LIGA_END" 1 0 45; then
        if [ "$TOURNAMENT_CREATED" = 1 ]; then
            build_liga "$TOURNAMENT_ID"
            activate_tournament "$TOURNAMENT_ID" "$LIGA_NAME"
        fi
        TOURNAMENT_A_ID=$TOURNAMENT_ID
    else
        warn "'$LIGA_NAME' (id $TOURNAMENT_ID) already exists but its structure is incomplete; nothing was created. Delete it as admin (DELETE /api/tournaments/$TOURNAMENT_ID) and re-run to rebuild."
    fi

    # Tournament B — direct elimination, first 8 teams, 4 phases, 4 quarter-finals.
    if ensure_tournament "$COPA_NAME" "$COPA_DESC" "$COPA_START" "$COPA_END" 0 4 4; then
        if [ "$TOURNAMENT_CREATED" = 1 ]; then
            build_copa "$TOURNAMENT_ID"
            activate_tournament "$TOURNAMENT_ID" "$COPA_NAME"
        fi
        TOURNAMENT_B_ID=$TOURNAMENT_ID
    else
        warn "'$COPA_NAME' (id $TOURNAMENT_ID) already exists but its structure is incomplete; nothing was created. Delete it as admin (DELETE /api/tournaments/$TOURNAMENT_ID) and re-run to rebuild."
    fi
}

# ──────────────────────────────── Players ────────────────────────────────────

ensure_user() { # USERNAME EMAIL
    local user=$1 email=$2 status
    status=$(api POST /api/auth/register "" "$(body_register "$user" "$email" "$SEED_PASSWORD")")
    case "$status" in
    201)
        CREATED_USERS=$((CREATED_USERS + 1))
        info "user '$user' created"
        ;;
    409)
        EXISTING_USERS=$((EXISTING_USERS + 1))
        info "user '$user' already exists"
        ;;
    *)
        die "registering '$user' failed: HTTP $status $(json_scalar error <"$BODY_FILE") $(json_scalar message <"$BODY_FILE")"
        ;;
    esac
    # A subshell keeps the shared helper's failure from exiting here, so we can
    # point at the likely cause: an existing account with another password.
    if ! (login_user "$user" "$SEED_PASSWORD" "$WORK/$user.header"); then
        die "login as '$user' failed. If the account already existed, its password differs from SEED_USER_PASSWORD in .env."
    fi
    api GET /api/auth/me "$WORK/$user.header" >/dev/null
    USER_ID=$(json_scalar id <"$BODY_FILE")
    [ -n "$USER_ID" ] || die "could not read the id of '$user' from /api/auth/me"
}

enrol_user() { # USERNAME HEADER_FILE USER_ID
    local user=$1 header=$2 user_id=$3 tid status present
    for tid in "$TOURNAMENT_A_ID" "$TOURNAMENT_B_ID"; do
        if [ -z "$tid" ]; then
            continue
        fi
        status=$(api POST "/api/tournaments/$tid/join" "$header")
        case "$status" in
        200)
            JOINED=$((JOINED + 1))
            info "  $user joined tournament $tid"
            ;;
        409)
            api GET "/api/tournaments/$tid/participants" "$header" >/dev/null
            present=$(json_values userId <"$BODY_FILE" | grep -Fx -- "$user_id" || true)
            if [ -n "$present" ]; then
                ALREADY_ENROLLED=$((ALREADY_ENROLLED + 1))
                info "  $user is already enrolled in tournament $tid"
            else
                die "$user got HTTP 409 joining tournament $tid but is not in its participants list"
            fi
            ;;
        *)
            die "joining tournament $tid as '$user' failed: HTTP $status $(json_scalar error <"$BODY_FILE")"
            ;;
        esac
    done
}

enrol_players() {
    local spec user email
    for spec in "${PLAYERS[@]}"; do
        user=${spec%%:*}
        email=${spec#*:}
        ensure_user "$user" "$email"
        enrol_user "$user" "$WORK/$user.header" "$USER_ID"
    done
}

summary() {
    local users="" spec
    for spec in "${PLAYERS[@]}"; do
        users+="${spec%%:*} "
    done
    info "== Seed summary"
    info "  teams:        $CREATED_TEAMS created, $SKIPPED_TEAMS already present"
    info "  tournaments:  $CREATED_TOURNAMENTS created, $SKIPPED_TOURNAMENTS already present"
    info "  users:        $CREATED_USERS created, $EXISTING_USERS already existed"
    info "  enrolments:   $JOINED new, $ALREADY_ENROLLED already present"
    info "  $LIGA_NAME id: ${TOURNAMENT_A_ID:-<skipped>}"
    info "  $COPA_NAME id: ${TOURNAMENT_B_ID:-<skipped>}"
    info "  log in as: ${users% }"
    info "  password: the SEED_USER_PASSWORD value in .env (never printed)"
}

main() {
    detect_json_tool
    wait_for_gateway
    load_env
    info "== ScoreGrid demo seeder"
    info "JSON backend: $JSON_TOOL"
    admin_login

    LIGA_START=$(date -u -d "+14 days" +%Y-%m-%d)
    LIGA_END=$(date -u -d "+35 days" +%Y-%m-%d)
    LIGA_BASE_DATE=$LIGA_START
    COPA_START=$(date -u -d "+30 days" +%Y-%m-%d)
    COPA_END=$(date -u -d "+60 days" +%Y-%m-%d)
    DAY30=$(date -u -d "+30 days" +%Y-%m-%d)
    DAY31=$(date -u -d "+31 days" +%Y-%m-%d)

    seed_teams
    seed_tournaments
    enrol_players
    summary
}

case "${1:-}" in
-h | --help) usage ;;
"") main ;;
*)
    usage
    exit 2
    ;;
esac
