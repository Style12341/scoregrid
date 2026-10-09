# ScoreGrid Postman kit

A Postman collection for testing the API live through the gateway: platform
checks, the admin and participant journeys, asynchronous scoring, every
contract error code, failover companion requests and an endpoint reference.

| File | What it is |
|------|------------|
| `ScoreGrid.postman_collection.json` | The collection (Postman v2.1) |
| `ScoreGrid-local.postman_environment.json` | `baseUrl`, `eurekaUrl`, `prometheusUrl`, `adminUsername`, and an empty `adminPassword` |
| `../scripts/api-tests.sh` | Headless run with newman |

The API contract is [`docs/contracts.md`](../docs/contracts.md). The demo
runbook is [`docs/demo.md`](../docs/demo.md).

## Import

1. Use the Postman desktop app. The web version cannot reach `localhost` or
   set the `Origin` header for the CORS check unless the Postman Desktop
   Agent is running.
2. **Import** both JSON files and select the **ScoreGrid local** environment.
3. Set `adminPassword`: open the environment and paste the
   `SCOREGRID_ADMIN_PASSWORD` value from `.env` into **Current value**. Leave
   **Initial value** empty: initial values are what gets shared and exported.
   Never commit an exported environment.

The stack must be running with the observability profile (`docs/demo.md`,
"Before the demo"); folder 00 checks Eureka and Prometheus.

## Folders

| Folder | What it shows | Runs in `api-tests.sh` |
|--------|---------------|:---:|
| 00 Platform | 401 envelope without a token, CORS preflight, Eureka registrations, Prometheus targets | yes |
| 01 Admin journey | Login, tournament in DRAFT, two teams, group, two matches, activate | yes |
| 02 Participant journey | Register, login, join, predict 2-1, list my predictions | yes |
| 03 Result and scoring | Load 2-1, poll the ranking until 3 points, global and user ranking, load it again (still 3), recalculate | yes |
| 04 Contract errors | Every error code, each asserting the exact envelope | yes |
| 05 Failover (manual) | Requests to send during `scripts/failover-demo.sh` | **no** |
| 06 API reference | Remaining endpoints by service; 06.1-06.3 read-only, 06.4 writes | 06.1-06.3 only |

Every run creates fresh users, teams and tournaments with a unique suffix, so
the collection can be rerun against the same database. Nothing is deleted.

## Run in the Collection Runner

1. **Run collection**. Deselect `05 Failover (manual)` and
   `06.4 Writes (documented, not run)`.
2. Keep **Keep variable values** on: folder 05 reuses the tokens and ids the run
   captures.
3. **Run**. Folders depend on each other in order (01 creates what 02-06 use),
   so run them top to bottom, whole or one at a time.

The ranking poll in 03 repeats itself once a second
(`pm.execution.setNextRequest`) until scoring arrives through RabbitMQ, and
fails after about 15 s. That loop only works in the Runner: with a single
**Send**, press Send again until the tests appear.

Tokens last 24 hours. If requests start answering 401, run 01 and 02 again.

## Run headless

```bash
scripts/api-tests.sh
```

It reads `SCOREGRID_ADMIN_PASSWORD` from `.env` without printing it, runs
folders 00-04 and 06.1-06.3 with a pinned newman through `npx`, and exits with
newman's code (0 when every assertion passed). Extra arguments go to newman,
e.g. `scripts/api-tests.sh --bail`. It refuses to run against anything but
`localhost`. It never resets data, never runs folder 05 and never touches
containers. The header of the script lists the traps.

## Folder 05 with `scripts/failover-demo.sh`

Before: run 01-04 in the Runner (for the tokens and ids), and check
`scripts/failover-demo.sh status` shows two TOURNAMENT-SERVICE instances (if
not, `scripts/failover-demo.sh restore`). Use two windows: a terminal and
Postman.

| Terminal | Postman request | How | Expect |
|----------|-----------------|-----|--------|
| `scripts/failover-demo.sh instance` | Instance failover: list tournaments (repeat in the Runner) | Runner with only this request: 420 iterations, 500 ms delay (about 4 min, the whole scenario). Start it together with the script. | Every iteration 200. About ten slow ones (1-6 s) right after the stop and the kill, while the gateway retries on the live replica. |
| `scripts/failover-demo.sh downstream` | Downstream failover, step 1, then step 2 | Send step 1 **before** starting the script. Send step 2 as soon as the script prints `SIGKILL`. | Step 2 answers 201, under 3 s even when it first tries the dead replica. |
| `scripts/failover-demo.sh service-down` | Service down: my predictions answer 503 | Send two or three times while the script prints its probes. | `503 DOWNSTREAM_UNAVAILABLE` envelope. The first call after the stop can take 3-6 s; once the breaker opens, milliseconds. |
| `scripts/failover-demo.sh restore`, then `scripts/smoke.sh` | — | After the demo. | Two tournament replicas, one prediction, every check passing. |

For a slower pace on stage than `service-down` allows, stop the service by
hand (`docker compose stop prediction-service`), send the 503 request as
often as you like, then run `scripts/failover-demo.sh restore`.

Step 2 can be repeated only after a new step 1: a second prediction on the
same match is `DUPLICATE_PREDICTION`.
