# ScoreGrid Demo Runbook

The live demo is driven from the browser. The terminal is used only to break
things and bring them back: kill a replica, stop a service, start them again.

Timings were measured on the local stack on 2026-10-09.

## Before the demo

1. Start the whole stack, observability included (`.env` filled in as
   described in [`start.md`](start.md)):

   ```bash
   docker compose --profile observability up -d
   docker compose --profile observability ps
   ```

   Wait until every service with a health check reads `healthy`.

2. Check the demo data: open <http://localhost:3000/tournaments> as the admin.
   If **Liga Master** and **Copa UTN** are listed, the data is there. **Do not
   reseed.** Only on an empty database run `scripts/seed.sh`: it creates the
   players `sofia`, `lucas` and `carla`, ten teams, Liga Master (one group,
   45 matches) and Copa UTN (four knockout phases, the four quarter-finals).

3. Log in everywhere before you start. The passwords are in `.env`; never
   type them while the screen is shared.

   | Where | User | Password key in `.env` |
   |-------|------|------------------------|
   | App, admin: <http://localhost:3000/login> | `SCOREGRID_ADMIN_USERNAME` | `SCOREGRID_ADMIN_PASSWORD` |
   | App, participant: same URL, in a **private window** | `sofia` | `SEED_USER_PASSWORD` |
   | Grafana: <http://localhost:3001> | `GRAFANA_ADMIN_USER` | `GRAFANA_ADMIN_PASSWORD` |
   | RabbitMQ: <http://localhost:15672> | `RABBITMQ_USER` | `RABBITMQ_PASSWORD` |

   The app login form is "Usuario o email", "Contraseña", **Ingresar**. The
   private window keeps the participant's session apart from the admin's, so
   both stay open.

4. Turn off Bitwarden (or any password manager) for `localhost`. Its bar
   appears after a login and swallows clicks on the page.

5. Open the tabs in this order: Eureka, Prometheus targets, RabbitMQ, the app
   as admin, Grafana. Keep the participant's private window beside them and a
   terminal in the repository root.

## 1. The architecture in the browser

**Eureka**: <http://localhost:8761>. Under "Instances currently registered
with Eureka": `API-GATEWAY`, `AUTH-SERVICE`, `PREDICTION-SERVICE` and
`SCORE-SERVICE` with `UP (1)`, `TOURNAMENT-SERVICE` with `UP (2)`. Each
instance id is `<container id>:<service>:<port>`; Prometheus and Grafana use
the same ids. The red "SELF PRESERVATION MODE IS TURNED OFF" banner is
deliberate: it lets Eureka drop a dead replica within seconds. The page does
not refresh itself: reload it.

**Prometheus**: <http://localhost:9090/targets>. The `scoregrid-services`
pool reads `7 / 7 up`: eureka-server, the gateway, auth, prediction, score and
two tournament-service targets. Nothing is listed by hand: Prometheus
discovers every replica from Eureka.

**RabbitMQ**: <http://localhost:15672>.

- **Exchanges**: `scoregrid.events` (topic) and `scoregrid.dlx`. Click
  `scoregrid.events`; its **Bindings** route `match.scheduled` and
  `match.updated` to `prediction.match-cache`, and `match.finished` to
  `score.match-finished`.
- **Queues and Streams**: those two queues with one consumer each, and their
  dead-letter queues `prediction.match-cache.dlq` and
  `score.match-finished.dlq` with 0 messages.

## 2. The user journey in the app

**Participant** (private window, <http://localhost:3000>):

1. **Torneos**: Liga Master and Copa UTN, both "Activo".
2. Liga Master → **Ver torneo**: tabs "Fixture", "Grupos" and "Fases". Every
   match reads "Programado" with its kickoff, and **Pronosticar** on the right.
3. **Pronosticar** on the first match, Atlético Central vs Ciudad Vieja FC:
   badges "Programado" and "Pronósticos abiertos", one score field per team
   with − and + buttons. Enter 2 and 1, then **Enviar pronóstico**: the toast
   "Pronóstico enviado" and a shortcut to the next match without a prediction.
4. **Mis pronósticos**: the match, "Tu pronóstico" 2 – 1, "Resultado"
   "Pendiente". Leave this page open: it refreshes every 5 s.

**Admin** (normal window):

5. **Cargar resultados** → "Torneo": Liga Master. Under "Por cargar", type 2
   and 1 for Atlético Central – Ciudad Vieja FC → **Cargar resultado**. The
   toast reads "Resultado cargado" and the match moves to "Finalizados".
6. Back in the participant's window, without touching it: "Resultado" turns
   2 – 1 within 5 s. **Rankings** and Liga Master's **Ver ranking** show
   `sofia` with 3 "Puntos" and 1 "Exactos"; **Panel principal** shows "Puntos
   totales" 3. Nobody computed that in the request: tournament-service
   published `match.finished` to RabbitMQ, score-service consumed it, read the
   predictions from prediction-service over REST and saved the match score.
   In RabbitMQ, **Queues and Streams** → `score.match-finished` shows the
   message delivered and acknowledged.
7. Rescoring replaces, it never adds: in **Cargar resultados** the finished
   match now offers **Corregir resultado**. Send the same 2 – 1 again ("Resultado
   corregido"): the ranking still says 3, not 6.
8. **Panel admin** → Liga Master → **Administrar** → tab "Grupos": the
   "Tabla general" standings (PJ, G, E, P, GF, GC, DG, PTS) now count the
   result. **Generar fixture** opens "Generar fixture de Tabla general",
   which says "Todos los cruces de este grupo ya tienen partido. No queda
   nada por generar." and keeps "Crear 0 partidos" disabled: generating twice
   creates nothing. Close it with ×.
9. **Panel admin** → Copa UTN → **Administrar** → tab "Partidos": the four
   "Cuartos de final" with their score fields. Load the four results, one of
   them a draw. Then tab "Fases" → **Armar siguiente fase**: the dialog
   proposes the semi-finals from the winners and, for the draw, asks
   "¿Quién pasó?" ("Elegí el equipo que pasó"). Before the four results are
   in, the same dialog says "Faltan terminar 4 partidos de Cuartos de
   final".
10. **Panel admin** → Liga Master → **Administrar** → **Finalizar torneo**:
    the dialog "¿Finalizar Liga Master?" warns "Quedan N partidos sin
    terminar" and its **Finalizar torneo** button stays disabled while any
    match is pending. Click **Volver**.

## 3. Load balancing

1. Grafana → the dashboard <http://localhost:3001/d/scoregrid-overview>. In
   **Servicio** pick only `tournament-service`. In its row, "Instancias
   arriba" is 2 and "Requests por segundo, por instancia" has one line per
   replica, labelled with the Eureka instance ids.
2. In the app, open Liga Master and reload it five times, one at a time.
   Each load sends three GETs to tournament-service.
3. Within 25 s (one 15 s scrape, one 10 s dashboard refresh) both lines rise
   together.
4. For the exact split, open this URL. It is the per-replica request count,
   as a table:

   <http://localhost:9090/query?g0.expr=sum%20by%20%28instance%29%20%28http_server_requests_seconds_count%7Bservice%3D%22tournament-service%22%2C%20uri%21~%22%2Factuator.%2A%22%7D%29&g0.tab=table>

   Note both numbers, reload Liga Master five more times, wait 15 s and click
   **Execute**: each replica went up by about half (measured: +7 and +8 for
   15 GETs). The gateway resolves `lb://tournament-service` for every request
   and the LoadBalancer takes the next replica from Eureka's list in turn.

tournament-service writes no log line per read, so the logs panel cannot show
which replica answered a GET. A write does show it: the `Published
match.finished` line of the result loaded in section 2 carries `replica=1` or
`replica=2`.

## 4. Kill a replica

In Grafana, set **Servicio** to `api-gateway` and `tournament-service`. In the
terminal:

```bash
docker compose ps tournament-service
docker kill scoregrid-tournament-service-2
```

`docker kill` (SIGKILL) is the worst case: the replica cannot deregister, so
Eureka keeps listing it until its lease expires.

- **App**: reload Liga Master and wait until it loads, then once more. It
  still loads, but some requests take 3 s: 2 s connect timeout on the dead
  replica, 1 s back-off, then the live replica answers. The gateway retries
  only a GET whose connection could not be opened, at most three attempts.
  Reload one at a time: overlapping reloads can send all three attempts of
  one request to the dead replica, and the gateway then answers 503 at its
  8 s limit. The page shows "No pudimos cargar el fixture" / "El servicio no
  está disponible. Intentá nuevamente."; **Reintentar** loads it.
- **Grafana**: "Instancias arriba por servicio" shows tournament-service 1 at
  the next scrape. "Reintentos por minuto (WARN en los logs)" rises for
  api-gateway. The logs panel shows orange `WARN` lines: `Retrying GET
  /api/tournaments/1/participants/1 on route tournament (retry 1 of 2) after
  ConnectTimeoutException`. Expand one → **Links** → traceId → **Ver traza en
  Tempo**: a 6 s trace with two red `http get (2s)` spans, the connect
  timeouts, then the call tournament-service answered.
- **Eureka** (reload): `TOURNAMENT-SERVICE` `UP (1)`. Eureka evicted the
  replica 20 s after the kill. The gateway keeps retrying until its own
  registry copy and LoadBalancer cache catch up, 25-35 s after the kill.

Bring it back:

```bash
docker start scoregrid-tournament-service-2
```

Healthy and back in Eureka 21 s later. The logs panel shows `replica=2`
starting and "Registering application TOURNAMENT-SERVICE with eureka with
status UP"; "Instancias arriba" is 2 again at the next scrape.

## 5. A whole service down

In Grafana, set **Servicio** to `api-gateway` and `prediction-service`, and
the time range to **Last 5 minutes** so the breaker change is wide enough to
read. In the terminal:

```bash
docker compose stop prediction-service
```

- **App**, participant window: reload **Mis pronósticos**. It shows "No
  pudimos cargar tus pronósticos" / "Puede que el servicio de pronósticos no
  esté disponible. Volvé a intentarlo en unos segundos." with
  **Reintentar**. **Enviar pronóstico** on a match fails with "No se pudo
  guardar el pronóstico" / "El servicio no está disponible. Intentá
  nuevamente.", and nothing is saved.
- **Grafana**: "Servicios saludables" turns red at 5. "Respuestas del gateway
  por código HTTP" gets a `503` series. In "Estado de los circuit breakers",
  `api-gateway / prediction-service` goes from "Cerrado" to "Abierto" (red)
  after four failed calls, and "Llamadas rechazadas por breaker abierto"
  rises: the gateway answers 503 at once without calling. The logs panel
  names the cause: `Unable to find instance for prediction-service`, then
  `CircuitBreaker 'prediction-service' is OPEN`.

**Do not load a match result now.** score-service would consume
`match.finished`, fail to read the predictions and dead-letter the event (see
[below](#if-something-is-left-half-done)).

```bash
docker compose start prediction-service
```

Healthy 27 s later. The open "Mis pronósticos" page recovers by itself at
its next refresh (35 s after the start, measured); the breaker goes through
half-open back to "Cerrado".

## 6. Observability tour

The dashboard <http://localhost:3001/d/scoregrid-overview>, row by row:

- **Resumen**: "Servicios saludables" (6: eureka-server, api-gateway and the
  four business services), "Instancias arriba por servicio", "Requests por
  segundo, por servicio".
- **Gateway y Resilience4J**: "Estado de los circuit breakers", "Llamadas
  rechazadas por breaker abierto", "Respuestas del gateway por código HTTP",
  "Reintentos por minuto (WARN en los logs)".
- **Servicio: …**: one row per service picked in **Servicio**, per instance:
  requests, 5xx, latency (mean and p95), JVM heap.
- **Logs**: "Logs de los servicios seleccionados", each line labelled with
  its `container` and `replica`.
- **Trazas**: "Trazas recientes", the 20 newest traces through the services
  in **Servicio**.

From logs to a trace, across RabbitMQ: set **Servicio** to
`tournament-service` and `score-service`. After the result of step 2 the
logs panel shows `Published match.finished matchId=<id> eventId=<uuid>` from
tournament-service, then score-service's `Received match.finished event:
<same uuid>` and `Match scored`. Expand the `Published` line → **Links** →
traceId → **Ver traza en Tempo**. The waterfall: api-gateway →
tournament-service `http put /api/matches/{id}/result` →
`scoregrid.events/match.finished send` → score-service `score.match-finished
receive` → its `http get` to prediction-service → `score.calculated send`. A
second branch, `match.updated`, reaches prediction-service's match cache.
**Node graph**, above the waterfall, draws the same hops as boxes.

From a trace back to the logs: click a span → **Related logs**. A Loki pane
opens beside the trace with every line of that trace, from every service it
crossed.

"Trazas recientes": click an id in the "Traza" column to open it. A new trace
takes 10-17 s to show up here (Tempo search; measured 12 s), while **Ver
traza en Tempo** from a log line is immediate.

Grafana → **Drilldown** → **Traces**: span rate, errors and duration, per
service under **Breakdown**. The **Traces** tab lists the traces behind the
graphs: the 6 s retried GETs and the 8 s fallback of step 4 stand out by
duration. Click a trace name to open it in a side panel; **Related logs**
there opens Logs Drilldown filtered by that trace. Keep the default "Last 30
minutes": these TraceQL metrics come from the metrics-generator's recent
blocks (`infra/tempo/tempo.yml`), and older data is not kept for them.

Actuator calls (scrapes, health checks) and the gateway's Eureka polling are
not traced, so every trace is a real request or event.

## If something is left half-done

```bash
docker compose --profile observability up -d
```

Starts every container that is stopped or killed. Then reload Eureka until
`TOURNAMENT-SERVICE` shows `UP (2)` and `PREDICTION-SERVICE` `UP (1)`. The
fallback is `scripts/failover-demo.sh restore`, which also waits for those
registrations.

A result loaded while prediction-service was stopped is lost for scoring:
score-service retries three times and dead-letters the event. RabbitMQ →
**Queues and Streams** → `score.match-finished.dlq` shows 1 message. Once
prediction-service is up, send the same result again with **Corregir
resultado** (rescoring replaces, so nothing is counted twice), then empty the
queue from its page: **Purge** → **Purge Messages**.

## Failover

How steps 4 and 5 work, for the questions.

- **Gateway** (`services/api-gateway`): every route has a Resilience4J
  breaker whose fallback answers `503 DOWNSTREAM_UNAVAILABLE` in the contract
  envelope. It opens at a 50% failure rate over the last 10 calls (at least
  4) and stays open 10 s. A `RetryReads` filter retries a `GET` whose
  connection could not be opened (refused, no route, connect timeout), up to
  three attempts 1 s apart; `lb://` is resolved inside the retry, so the next
  attempt can go to another replica. A read timeout is not retried (the
  replica has the request and a second one doubles its load), and writes are
  never retried (a timed-out `POST` may already be applied). Proxy timeouts:
  2 s connect, 5 s read (`SCOREGRID_GATEWAY_CONNECT_TIMEOUT`,
  `SCOREGRID_GATEWAY_READ_TIMEOUT`); breaker time limit 8 s (2 s dead
  connect + 1 s back-off + 5 s read).
- **Known limit**: the LoadBalancer's turn order is shared by all requests,
  so while overlapping requests are in flight one GET can meet the dead
  replica on all three attempts and get a `503` at 8 s. It lasts only until
  Eureka evicts the replica.
- **Service to service** (`prediction-service` → `tournament-service`,
  `score-service` → `prediction-service`): a `@LoadBalanced` RestClient with
  a 500 ms connect and 1.5 s read timeout (`SCOREGRID_CLIENTS_CONNECT_TIMEOUT`,
  `SCOREGRID_CLIENTS_READ_TIMEOUT`). Each request gets up to three attempts
  (200 ms, then 400 ms back-off), every attempt through the LoadBalancer
  again, inside a breaker with a 2.2 s limit. A `4xx` from tournament-service
  (an unknown match) is neither retried nor counted by the breaker.
  `score-service` → `auth-service` (usernames for the rankings) has the same
  timeouts but no breaker or retry: with auth-service down, rankings show
  user ids instead of names. If tournament-service is slow on every request
  rather than dead, a prediction's three calls can pass the gateway's 5 s:
  the gateway answers `503` although the prediction was saved, and a resend
  gets `409`.
- **Discovery speed** (`docker` profile of each service): 5 s heartbeats,
  registry fetches and LoadBalancer cache; 5 s eviction and response cache on
  the Eureka server. A stopped replica (SIGTERM) deregisters and, with
  `server.shutdown: immediate`, refuses new connections at once, so a GET
  that still picks it is retried on the other replica. A killed one
  (SIGKILL) stays routable until its lease expires and the clients' caches
  refresh: 25-35 s.
- **prediction-service stays at one replica.** Its match cache is fed by the
  `prediction.match-cache` queue, and replicas of one service share a queue:
  RabbitMQ delivers each match event to only one of them. With two replicas a
  `match.updated` (a postponement, a moved kickoff) could reach the replica
  that never cached the match, while the one that did keeps accepting
  predictions it should lock. The fix is a queue per replica, which changes
  the RabbitMQ topology frozen in `docs/contracts.md` and needs the three
  owners' agreement. Failover is shown on tournament-service, which has no
  such cache; step 5 shows the breaker with the single prediction replica
  stopped.
