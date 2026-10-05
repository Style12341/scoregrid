# ScoreGrid Demo Runbook

This runbook is the shortest reproducible path for the final presentation. It
uses only local Docker services and does not require secrets in the repository.

## Start The Stack

```bash
cp .env.example .env
# Set SCOREGRID_JWT_SECRET, SCOREGRID_ADMIN_PASSWORD and the database passwords in .env.
docker compose up -d --build
docker compose --profile observability up -d
docker compose ps
```

Wait until the core services report `healthy`. Eureka should list the Gateway
and the four business services at <http://localhost:8761>. Then run the
read-only smoke check, which exits non-zero if anything is not wired:

```bash
scripts/smoke.sh
```

For the API walkthrough, import postman/ScoreGrid.postman_collection.json and postman/ScoreGrid-local.postman_environment.json into Postman (see postman/README.md), or run the same checks headless with scripts/api-tests.sh, which exits non-zero on any failed assertion.

## Prepare Users

The Auth Service creates the initial administrator automatically on startup,
using `SCOREGRID_ADMIN_USERNAME`, `SCOREGRID_ADMIN_EMAIL` and
`SCOREGRID_ADMIN_PASSWORD` from `.env`. The account receives both `PLAYER` and
`ADMIN` roles, so the values configured in `.env` are the credentials for the
first login. Register one additional participant from the frontend at
<http://localhost:3000>.

If the database already contains the configured admin username, startup keeps
its existing password and grants the two required roles; it never resets that
password.

## Vertical Slice

1. As `admin`, create a tournament and keep it in `DRAFT` while configuring it.
2. Create two teams and assign them to the tournament.
3. Create a group, assign both teams, create a scheduled match with a future kickoff, and activate the tournament.
4. As the participant, join the tournament and submit a score prediction.
5. As `admin`, load the match result with the same score as the prediction.
6. Watch `tournament-service` publish `match.finished` and `score-service` consume it. The publish logs `Published match.finished matchId=<id> eventId=<uuid>`; score-service logs `Received match.finished event: <uuid>` with the same `eventId`, then `Match scored`:

```bash
docker compose logs -f tournament-service score-service | grep -E 'match.finished|scored'
```

7. Open the tournament ranking (**Ver ranking** on the tournament's page) and show the participant's three points.
8. Submit a corrected result once more and show that rescoring replaces the match score instead of doubling the ranking.

The same flow demonstrates the service boundaries: the frontend calls only the
Gateway, Prediction Service validates the tournament through REST, and Score
Service reads predictions through REST after the RabbitMQ event.

## Platform Evidence

- Gateway and edge authentication: `curl -i http://localhost:8080/api/auth/me` returns `401` without a token.
- Eureka registration: open <http://localhost:8761> and show the registered applications.
- Metrics: open <http://localhost:9090/targets> and show the `scoregrid-services` targets as `UP`. Prometheus discovers them from Eureka, so there is one target per registered replica plus `eureka-server`: seven, since tournament-service runs two replicas. The dashboard's "Servicios saludables" counts services with at least one healthy replica, so it reads 6 and turns red when any whole service is down.
- Dashboard and centralized logs: open <http://localhost:3001> and select `ScoreGrid / ScoreGrid - Overview`.
- RabbitMQ: open <http://localhost:15672> and show the event queues and consumers.

## Failover

`scripts/failover-demo.sh` drives every step below against the local stack and
prints the numbers. It refuses to run unless the gateway, Eureka and
Prometheus URLs are `localhost`/`127.0.0.1` and Docker is the local daemon. It
logs in as the admin from `.env` without printing the password. Read the
header of the script for the traps (SIGTERM vs SIGKILL, restart delays) and
for what it does not do.

tournament-service runs two replicas by default (`deploy.replicas` in
`compose.yaml`), so the commands above already start the failover pair;
`scripts/smoke.sh` warns if it has fewer.

```bash
scripts/failover-demo.sh status     # Eureka: 2 tournament instances; Prometheus: one target per replica
```

**prediction-service stays at one replica.** Its match cache is fed by the
`prediction.match-cache` queue, and replicas of one service share a queue:
RabbitMQ delivers each match event to only one of them. With two replicas a
`match.updated` (a postponement, a moved kickoff) can reach the replica that
never cached the match, while the one that did keeps accepting predictions it
should lock. The fix is a queue per replica, which changes the RabbitMQ
topology frozen in `docs/contracts.md`; it needs the three owners' agreement,
so until then it runs one replica. Failover is shown on
tournament-service, which has no such cache, and `service-down` still shows
the gateway's breaker with the single prediction replica stopped.

| Command | What happens | What to show |
|---------|--------------|--------------|
| `instance` | `GET /api/tournaments` every 0.5 s; one tournament replica is stopped (SIGTERM), restarted, then killed (SIGKILL) and restarted | Zero failed GETs. In both cases the gateway retries a GET that cannot connect to the dead replica on the other one. After SIGTERM the replica refuses connections at once and leaves Eureka, so the window is short; after SIGKILL the dead address stays cached for 30-40 s and latency spikes to a few seconds |
| `service-down` | prediction-service stopped; `GET /api/predictions/me` twelve times | Each call is a fast `503 DOWNSTREAM_UNAVAILABLE` envelope; after four failures the breaker opens and calls are not even attempted. Then the time to the first `2xx` after restart |
| `downstream` | One tournament replica killed while a participant submits predictions | Every prediction accepted. Prediction Service's call to Tournament Service spends at most one connect timeout on the dead replica, then its retry goes to the live one |
| `restore` | `docker compose --profile observability up -d`, then waits for two tournament replicas and one prediction replica in Eureka | Back to the starting point |

How it works, for the questions:

- **Gateway** (`services/api-gateway`): every route has a Resilience4J
  `CircuitBreaker` filter whose fallback answers `503` with the contract
  envelope, and a `RetryReads` filter that retries a `GET` whose connection
  could not be opened (refused, no route to host, connect timeout), up to
  three attempts, 1 s apart. The `lb://` resolution runs inside the retry, so
  the next attempt goes to another replica. A read timeout is not retried:
  the replica has the request and is slow, and a second read would double its
  load. Writes are never retried: a timed-out `POST` may already have been
  applied. Each retry logs one `WARN` line (`Retrying GET <path> on route
  <route> ...`), visible in Loki. Proxy timeouts: 2 s connect, 5 s read
  (`SCOREGRID_GATEWAY_CONNECT_TIMEOUT`, `SCOREGRID_GATEWAY_READ_TIMEOUT`);
  breaker time limit 8 s (2 s dead connect + 1 s back-off + 5 s read).
- **Service to service** (`prediction-service` → `tournament-service`,
  `score-service` → `prediction-service`): a `@LoadBalanced` RestClient with
  a 500 ms connect and 1.5 s read timeout
  (`SCOREGRID_CLIENTS_CONNECT_TIMEOUT`, `SCOREGRID_CLIENTS_READ_TIMEOUT`).
  Each request gets up to three attempts (200 ms, then 400 ms back-off),
  every attempt through the LoadBalancer again, inside a breaker whose time
  limit is 2.2 s (0.5 s dead connect + 0.2 s back-off + 1.5 s read). A
  prediction on a match not yet cached makes three such requests in a row;
  with one tournament replica dead that is about 2.2 s, well under the
  gateway's 5 s. A `4xx` from Tournament
  Service (an unknown match) is neither retried nor counted by the breaker,
  so made-up match ids cannot open it. Each retry logs one `WARN` line.
  `score-service` → `auth-service` (usernames for the rankings) has the same
  timeouts but no breaker or retry: if auth-service is down, rankings show
  user ids instead of names after at most one timeout. Known limit: if
  Tournament Service is slow on every request rather than dead, the three
  requests can pass 5 s; the gateway then answers `503`
  although the prediction was saved, and a resend gets `409`. Closing that
  needs a deadline for the whole use case or an idempotency key.
- **Discovery speed** (`docker` profile of each service): 5 s heartbeats,
  registry fetches and LoadBalancer cache, 5 s eviction and response cache on
  the Eureka server. A stopped tournament replica (SIGTERM) deregisters and,
  with `server.shutdown: immediate`, refuses new connections at once, so a
  GET that still picks it fails to connect and is retried on the other
  replica. Graceful shutdown would pause the connector instead, and new
  connections would hang until the gateway's 5 s read timeout, which is not
  retried. A killed one (SIGKILL) stays routable until its lease expires,
  about 30-40 s.
- **Breaker state in Prometheus**:
  `resilience4j_circuitbreaker_state{application="api-gateway", state="open"}`
  goes to 1 for the downstream that is down while `service-down` runs.
- **Security at the edge**: the gateway validates the token issuer, so an
  internal service token (no `iss`) is refused with a `401` envelope.

Measured on the local stack (2026-10-05, after `docker compose up -d --build`):

- `instance`, twice: 230 and 191 GETs, zero failures. Max latency 2.0 s and
  3.0 s while the SIGTERMed replica was down, 6.0 s both times after SIGKILL
  (a GET that hit the dead address twice: 2 s connect timeout + 1 s back-off,
  twice). `docker stop` returns in about 4.5 s. In Loki,
  `{container="sg-api-gateway"} |= "Retrying GET"` shows the retries: 5-11 s
  of them after SIGTERM (`HttpHostConnectException`, then a
  `ConnectTimeoutException` once the container is gone), 30-35 s of
  `ConnectTimeoutException` after SIGKILL.
- `downstream`: 6/6 predictions accepted in 0.49-1.62 s, typically 0.72 s
  (0.5 s connect timeout on the dead replica + 0.2 s back-off).
- `service-down`: twelve `503` envelopes in 3-26 ms (the first one 26 ms),
  first `2xx` 17 s after restart. The gateway log gives the cause of each:
  "Unable to find instance" first, then the open breaker.
- "Servicios saludables": 6; still 6 with one tournament replica stopped; 5
  within 20 s of stopping prediction-service.
- Twelve predictions on unknown match ids: twelve `404 NOT_FOUND`, and
  Prediction Service's `tournamentClient` breaker stays closed (0 failed, 12
  ignored calls).
