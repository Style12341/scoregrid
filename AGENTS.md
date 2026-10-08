# AGENTS.md

Instructions for AI coding agents working in the ScoreGrid repository.

`CLAUDE.md` is a symlink to this file — one set of instructions, not two. If you edit one, you have edited both.

---

## 1. What this is

ScoreGrid is a football prediction pool: admins build tournaments with group stages and knockout phases, participants predict match scores, the system scores predictions automatically and maintains per-tournament and global rankings.

Five Spring Boot business services plus a Eureka registry, a React frontend, two PostgreSQL databases and two MongoDB databases (sharing one instance per engine, isolated by per-service logins), RabbitMQ, all under one Docker Compose.

| Service | Port | Owns | Database |
|---------|------|------|----------|
| `eureka-server` | 8761 | Service registry and discovery | — |
| `api-gateway` | 8080 | Routing, edge JWT validation, CORS | — |
| `auth-service` | 8081 | Users, roles, JWT issuance | PostgreSQL |
| `tournament-service` | 8082 | Tournaments, teams, groups, phases, matches, results, enrolments | PostgreSQL |
| `prediction-service` | 8083 | Predictions, kickoff lock | MongoDB |
| `score-service` | 8084 | Scoring, tournament ranking, global ranking | MongoDB |

**Stack (verified, not assumed):** Java 21 · Spring Boot 4.1.0 · Spring Cloud 2025.1.2 · Spring Cloud Gateway 5.0.2 · Spring AMQP 4.1.0 · Maven (one independent POM per service, no aggregator) · React 19 · Vite 8 · TypeScript 6 · Tailwind CSS 4 · shadcn/ui (Radix primitives).

---

## 2. Current state

The application is implemented end to end and the full Compose stack is verified locally. Eureka is the service registry; the Gateway and internal REST clients use discovery by default.

**Platform — done:**
- Service skeletons, `shared/` package (security, error handling, current-user), `ResilienceConfig`, `RabbitConfig` with the full messaging topology, `application.yml` per service.
- `compose.yaml` — one Postgres and one MongoDB, each with one database and one login per service, provisioned from `infra/postgres/init` and `infra/mongo/init`; RabbitMQ; Eureka; five services; frontend. `infra/` also holds the observability config.
- Git repository and `.github/workflows/ci.yml` — Eureka plus five services in a matrix, frontend lint + build, compose validation on both profiles.
- Frontend: axios client, auth context, route guard, route map, and the **design system** (Tailwind v4 + shadcn/ui restyled to the mock) — see §8.
- Observability (`--profile observability`): Prometheus scrapes every replica found in Eureka; Promtail ships the JSON logs to Loki with the labels `service`, `container`, `replica` and `level` (`traceId` is structured metadata, not a label); every service reports spans to Tempo over Zipkin, across the gateway, the service-to-service calls and RabbitMQ; Grafana links logs and traces both ways. The hand-driven walkthrough is [`docs/demo.md`](docs/demo.md#hand-demo).
- Resilience and failover: every gateway route has a breaker with a `503 DOWNSTREAM_UNAVAILABLE` fallback, GET-only retry onto another replica when the connection cannot be opened, and proxy timeouts; tournament-service runs two replicas by default and shuts down immediately, so a stopping replica refuses connections at once and the retry moves on; the internal clients prediction→tournament and score→prediction retry each request through the LoadBalancer under connect/read timeouts and a fixed time limit, and score→auth has the same timeouts and falls back to user ids. `scripts/failover-demo.sh` drives it live, `scripts/smoke.sh` checks the wiring — see [`docs/demo.md`](docs/demo.md#failover). prediction-service runs one replica only (its match-cache queue is shared; see there).

**Stream C — done** (`prediction-service`, `score-service`):
- `prediction-service`: full hexagonal slice — domain model, ports, the six-step validation chain, Mongo persistence with the unique index on `(userId, matchId)`, in-memory match cache fed by `match.scheduled` / `match.updated`, `RestClient` fallback to tournament-service behind a circuit breaker, six controller endpoints, event publisher.
- `score-service`: `ScoringRuleV1` (3 exact / 1 outcome / 0) with the parameterised table from `contracts.md`, idempotent per-match scoring by replace, tournament and global ranking projections with the tie-break chain, `match.finished` consumer, REST clients to prediction-service and auth-service, ranking endpoints.
- Screens: `PredictionPage`, `MyPredictionsPage`, `AdminResultsPage`, wired into `App.tsx`.

**Stream A — done** (`auth-service`, `api-gateway`, frontend foundation):
- `auth-service` end to end and hexagonal: register, login, `/me`, `/api/users/{id}`, `/api/users`, `/api/users/batch`. BCrypt hashing, JWT issuance from `scoregrid.jwt.ttl`, the shared error envelope on every failure path. 81 tests including Testcontainers against real PostgreSQL. It replaced the Stream C stub.
- `api-gateway`: five route groups, edge JWT rejection, CORS. Verified live — a request to `:8080` reaches auth-service and score-service, and an unauthenticated one is refused at the edge.
- Screens: Login, Register, Dashboard, Tournament Ranking, Global Ranking.
- Lombok and MapStruct are the agreed project-wide convention; `auth-service` is the reference wiring (see the annotation-processor ordering in its POM).

**Stream B — done** (`tournament-service`):
- `tournament-service` end to end and hexagonal: Tournament CRUD with state machine (DRAFT → ACTIVE → FINISHED/CLOSED), Team catalogue CRUD, tournament-team assignment (idempotent), player enrolment (join, list, single lookup), groups, phases, full match management with state machine, invariant validation, event publishing, and result loading. The complete domain, persistence and Testcontainers suite passes. Frontend screens (tournament list, detail, admin panel: groups/phases/fixture/results management) shipped in `Feat/workstream-b-completion`.

**Current cross-service follow-up:**
- The service-to-service JWT mechanism is documented in [`docs/contracts.md`](docs/contracts.md#internal-service-to-service-jwts). The prediction and scoring controller, persistence and Testcontainers suites are implemented and passing.

**Documented cross-service mechanism.** Stream C introduced `ServiceToken` (duplicated in `prediction-service` and `score-service`) which mints an HS256 JWT from `SCOREGRID_JWT_SECRET` with `sub` set to the service name and `roles: ["ADMIN"]`, for service-to-service REST calls. It is an internal credential, not a user identity, and is only valid for endpoints that do not use `CurrentUser.requireId()`. Keep the implementation local to each service; do not extract shared DTOs or security libraries.

---

## 3. Read before you write

| Task | Read first |
|------|------------|
| Anything crossing a service boundary | [`docs/contracts.md`](docs/contracts.md) — **frozen** |
| Adding a dependency, generating a service, version questions | [`docs/start.md`](docs/start.md) |
| Deciding whether something is in scope | [`docs/PRD.md`](docs/PRD.md), especially Non-Goals |
| Who owns the file you are about to edit | [`docs/workstreams.md`](docs/workstreams.md) |

**If a request conflicts with `docs/contracts.md`, stop and say so.** Do not silently implement a different contract — three developers are coding against that document, and a unilateral change breaks two of them.

---

## 4. Hard rules

Not style preferences. Breaking any of these breaks the architecture.

1. **A service touches only its own database.** No cross-service datasource, no shared schema, no "just this one join". Data owned elsewhere comes from that service's API or from an event.
   Postgres and MongoDB each run as a *single* instance holding one database per service. That is a container-count decision, not a boundary decision: every database has its own login, `CONNECT` is revoked from `PUBLIC` in Postgres, Mongo runs authenticated with `readWrite` scoped to one database, and neither engine can query across databases. Connecting with another service's credentials to "just check something" defeats the only thing holding the boundary up. See [`docs/start.md`](docs/start.md#one-instance-per-engine-one-database-per-service).
2. **No shared Java library for DTOs, events or domain types.** Each service owns its own copy of the payload classes. Deliberate — rationale in [`docs/PRD.md`](docs/PRD.md#alternatives-considered). Do not "clean this up" by extracting a common module.
3. **The acting user comes from the JWT `sub` claim**, via the `CurrentUser` bean. Never from a request body, query parameter or header. A `userId` field in a request payload is a privilege escalation bug.
4. **Scoring is idempotent.** The per-match score document is *replaced*; rankings are *derived* from score documents. Never increment a running total. Re-scoring a corrected result or a redelivered message must produce identical numbers.
5. **`predictionsOpen` is computed by `tournament-service` and trusted by everyone else.** Prediction Service does not recompute the kickoff lock from its own clock. One clock, one answer.
6. **Flyway migrations are forward-only.** Never edit a pushed migration. `ddl-auto` is `validate`, never `update`.
7. **The unique index on `(userId, matchId)` is the duplicate-prediction rule** — not a read-then-write check, which loses under concurrency.
8. **Never accept a write you could not validate.** If a downstream check cannot be answered because a service is down, return `503`. Do not assume the happy path.
9. **No secrets in the repository.** `.env` is gitignored; `.env.example` holds placeholders only.
   One deliberate exception, and it is not a secret: `application.yml` carries a dev-only default JWT secret (`dev-only-insecure-secret-do-not-deploy-anywhere-real`) so `./mvnw spring-boot:run` works in a fresh terminal and all five business services agree on a locally minted token. `compose.yaml` still uses `${SCOREGRID_JWT_SECRET:?}`, so anything containerised hard-fails without a real value. Do not copy this pattern for database passwords or provider API keys.
10. **Code is English. The user interface is Spanish.** The line runs at the screen, not at the file.
    - **English:** identifiers, types, comments, commit messages, branch names, log output, error codes (`PREDICTION_LOCKED`), API field names (`predictionsOpen`), test names, documentation.
    - **Spanish:** every string a participant reads — nav labels, buttons, headings, form labels, validation messages, empty/error/loading copy, dates and number formatting.
    - The register is **Rioplatense** (voseo: "Ingresá", "Pronosticá"), matching `scoregrid_mock_interfaces_html.html`. The mock is the copy reference; when a screen exists there, reuse its wording rather than inventing a synonym.
    - A Spanish identifier and an English button are both wrong. An error code stays `NOT_ENROLLED` on the wire and is rendered to the user as "No estás inscripto en este torneo".
9.  **If you find a bug or broken behaviour that blocks your work, fix it.** Do not write a review document, file a ticket and wait, or stay blocked. The owner boundary in §8 prevents merge conflicts, not bug fixes. If the fix crosses ownership, mention it — but ship it. A review document is useless without code.

---

## 5. Version traps in this exact stack

All three fail **silently** — no compile error, no warning. Verified against the resolved dependency tree.

**Boot 4 renamed starters.** `spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-security-oauth2-resource-server` (not `-oauth2-resource-server`), `spring-boot-starter-flyway`, and modular `-test` starters. Do not copy dependency blocks from Boot 3 examples.

**Gateway routes live under `spring.cloud.gateway.server.webmvc.routes`.** Not `spring.cloud.gateway.routes`, not `spring.cloud.gateway.mvc.routes`. The old forms are ignored and the gateway routes nothing without complaining.

**Resilience4J: the time limiter comes only from a `TimeLimiterRegistry` bean.** `spring-cloud-circuitbreaker-resilience4j` 5.0.2 does bring `resilience4j-spring-boot3` 2.3.0 (compile scope), so do not reason from "there is no Boot integration". What bites:
- A `timeLimiterConfig(...)` passed through `configure`/`configureDefault` is **ignored**. `Resilience4JCircuitBreakerFactory` resolves the limiter from the `TimeLimiterRegistry` only (by id, then group, then its default), so every breaker silently runs on Resilience4J's 1 s default. That 1 s cut prediction→tournament failover short. The gateway's, prediction's and score's `ResilienceConfig` each define a `TimeLimiterRegistry` bean, which replaces the auto-configured one; `circuitBreakerConfig(...)` in the customizers does apply. The limit is a constant sized from that service's HTTP client timeouts (the arithmetic is in the comment): change them together.
- `@CircuitBreaker` / `@Retry` compile (the annotations jar comes along) but nothing applies them: AspectJ is not on the classpath. Silent no-op. Call through `CircuitBreakerFactory`.
- Keep breaker, retry and limiter in each service's `ResilienceConfig` (gateway: `config/`; the others: `shared/config/`), not in `resilience4j.*` YAML: one place holds the timing budget.
- Retry is the explicit `resilience4j-retry` dependency. Retry each HTTP request, not a whole use case: each attempt is a new request through the `@LoadBalanced` client, so it can land on another replica.

**The gateway's stock `Retry` filter cannot do "GET only, when the connection could not be opened".** In Gateway Server WebMvc 5.0.2 the Spring Framework implementation applies `methods` only to status-based retries (a POST that hit a read timeout is replayed) and matches only the outermost exception, where a refused connect and a read timeout are the same `ResourceAccessException`; the spring-retry one never retries exceptions. Routes use `RetryReads` instead — see `services/api-gateway/.../filter/ReadRetryFilterFunctions.java`.

**HTTP client timeouts have different names per service.** The gateway's are `spring.http.clients.connect-timeout` and `spring.http.clients.read-timeout` (Boot 4; `spring.http.client.*`, singular, is the deprecated 3.x namespace); they bind because the gateway starter brings `spring-boot-restclient`. prediction-service and score-service do not have that module, so `spring.http.clients.*` binds to nothing there: their timeouts are `scoregrid.clients.connect-timeout` / `read-timeout`, applied to an explicit request factory in `ClientConfig`. Without one, `RestClient.builder()` picks Apache HttpClient 5 (via the Eureka client): a 3-minute connect timeout and no response timeout.

**Spring AMQP: use `JacksonJsonMessageConverter`,** not `Jackson2JsonMessageConverter`. Boot 4 ships Jackson 3; the `Jackson2` class is the legacy one.

**MongoDB connection properties moved to `spring.mongodb.*`.** In Boot 4.0 every *connection* property — `uri`, `host`, `port`, `username`, `password`, `database`, `authentication-database`, `replica-set-name`, `ssl.*` — moved out of `spring.data.mongodb.*`. The old names are deprecated at level `error`: they bind to **nothing**. The driver then falls back to its own default, `mongodb://localhost/test`, so the application starts normally and only `/actuator/health` reveals `mongo: DOWN`. The environment variable is `SPRING_MONGODB_URI`, not `SPRING_DATA_MONGODB_URI`.

The split is the trap: `spring.data.mongodb.auto-index-creation` did **not** move and is still correct where it is. Half of one YAML block relocated, half did not.

```yaml
spring:
  mongodb:
    uri: ${SPRING_MONGODB_URI:mongodb://user:pw@localhost:27018/db?authSource=db}
  data:
    mongodb:
      auto-index-creation: true      # stays here
```

**A trace breaks silently at every hop it is not wired for.** Nothing fails: the next service just starts a new trace, and Tempo shows two short traces instead of one. Each one was checked against the resolved jars or the running stack:
- Boot 4 renamed the Zipkin export properties to `management.tracing.export.zipkin.*` (`endpoint`, `enabled`). The 3.x `management.zipkin.tracing.*` names are deprecated at level `error` and bind to nothing, so spans go to the default `localhost:9411`. The reporter comes from `spring-boot-zipkin`; compose sets the endpoint, `application.yml` keeps export off.
- A `RestClient` built from `RestClient.builder()` records no observation: no client span, no `traceparent` header. The `@LoadBalanced` builders in `ClientConfig` set the `ObservationRegistry` by hand.
- Spring Cloud CircuitBreaker carries the trace onto the time limiter's thread only for a breaker created after the factory got the `ObservationRegistry`, which Spring Cloud sets in a `@PostConstruct` that can run after a client's constructor has already called `create()`. `ResilienceConfig` hands the registry over in a `Customizer`, which runs while the factory is built.
- Spring AMQP observation is off by default: `spring.rabbitmq.template.observation-enabled` and `spring.rabbitmq.listener.simple.observation-enabled` carry the trace through RabbitMQ.
- Tempo (2.7+) binds its receivers to `localhost` unless the endpoint says `0.0.0.0`: the other containers cannot reach it, and nothing reports an error. See `infra/tempo/tempo.yml`.

**Boot 4 moved the test slice annotations into per-module packages.** The Boot 3 imports do not exist and there is no deprecation shim — you get `package ... does not exist`, which at least fails loudly, unlike the traps above. Verified against the resolved jars:

| Annotation | Boot 3 (wrong here) | Boot 4.1 |
|------------|---------------------|----------|
| `@WebMvcTest` | `o.s.b.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@DataJpaTest` | `o.s.b.test.autoconfigure.orm.jpa` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `o.s.b.test.autoconfigure.jdbc` | `org.springframework.boot.jdbc.test.autoconfigure` |

`MockMvc`, `MockMvcRequestBuilders`, `@MockitoBean` and `SecurityMockMvcRequestPostProcessors` did **not** move — they come from `spring-test` and `spring-security-test`, not Boot. Use `@MockitoBean`, not the removed `@MockBean`.

**MapStruct reads a `withX()` copy-method as a target property.** A domain type with `User withId(Long)` makes MapStruct report `Unmapped target property: "withId"`, and with `unmappedTargetPolicy = ERROR` that fails the build. It is a wither, not a builder, so `disableBuilder` does not help — add `@Mapping(target = "withId", ignore = true)`. Keep the strict policy: it is what catches a genuinely forgotten field.

Order matters in `annotationProcessorPaths`: lombok, then `lombok-mapstruct-binding`, then `mapstruct-processor`. Wrong order and MapStruct cannot see Lombok-generated accessors.

**Maven's incremental build will lie about annotation processors.** Changing a class into a MapStruct interface and rebuilding without `clean` can leave the old compiled class in `target/classes` and skip processing entirely — tests that do not load a Spring context still pass, and the missing `@Component` only surfaces at runtime. After touching a mapper or a processor path, run `./mvnw clean test`, and check `target/generated-sources/annotations/` actually contains the `*Impl`.

**A readiness probe that excludes the datastore will lie to you.** `/actuator/health/readiness` contains only `readinessState` by default, so a container reports healthy while every query fails. Each data service adds the relevant indicator to the readiness group — see `management.endpoint.health.group.readiness` in its `application.yml`. This is how the property change above went unnoticed in the first place.

---

## 6. Code conventions

### Package layout — hexagonal, feature first, layer second

```
com.scoregrid.<service>.<feature>.domain.model          entities, value objects
com.scoregrid.<service>.<feature>.domain.port.in        use case interfaces
com.scoregrid.<service>.<feature>.domain.port.out       repository / publisher interfaces
com.scoregrid.<service>.<feature>.application           use case implementations
com.scoregrid.<service>.<feature>.infrastructure.web    controllers + DTOs
com.scoregrid.<service>.<feature>.infrastructure.persistence
com.scoregrid.<service>.<feature>.infrastructure.messaging
com.scoregrid.<service>.shared                          config, error, security
```

Boundary rules:
- **`domain` imports no framework.** No `@Entity`, no `@JsonProperty`, no Spring annotation on a domain model. If `domain` would still compile with the framework jars removed, the boundary is real.
- **JPA / Mongo entities are not domain models.** They live in `persistence` and map to domain objects. Yes, that is a mapper you have to write — it is why a schema change does not ripple into business rules.
- **Controllers use request/response DTOs.** Never serialise a domain model directly; that leaks internals into a public contract.

### Errors

Throw `DomainException(ErrorKind, errorCode, message)`. `GlobalExceptionHandler` maps `ErrorKind` to an HTTP status and produces the envelope in [`docs/contracts.md`](docs/contracts.md#error-envelope). Never `RuntimeException("nope")`.

Error codes are part of the contract: `VALIDATION_FAILED`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `DUPLICATE_PREDICTION`, `PREDICTION_LOCKED`, `TOURNAMENT_NOT_ACTIVE`, `NOT_ENROLLED`, `INVALID_MATCH_STATE`, `DOWNSTREAM_UNAVAILABLE`.

### Tests

- Domain and application logic: plain unit tests, no Spring context. These run in milliseconds.
- Controllers: `@WebMvcTest`. Persistence: `@DataJpaTest` / `@DataMongoTest`.
- Anything touching a real database or queue: **Testcontainers, not H2.** H2 does not behave like PostgreSQL where it matters.
- **Name every test class `*Test`, never `*IT`.** Surefire only collects `*Test`; `*IT` belongs to Failsafe, and no POM here declares Failsafe. A class named `SomethingIT` runs under neither `./mvnw test` nor CI's `./mvnw verify` — it is silently skipped, and the suite still reports green. Verified the hard way.
- Pin Testcontainers images (`postgres:17-alpine`, not `postgres:latest`) so a major-version bump cannot change behaviour with no commit.
- A green suite proves nothing until you have watched it go red. When a test guards something that matters — an authorisation rule, a field that must not be serialised — break the code deliberately once and confirm that test, and not some unrelated one, is what fails.
- The scoring rule table in [`docs/contracts.md`](docs/contracts.md#scoring-rule-v1) is a parameterised test. Implement it verbatim.

---

## 7. Commands

```bash
# One service — no aggregator POM, no build order
cd services/<service> && ./mvnw test
cd services/<service> && ./mvnw spring-boot:run

# Infra only — the usual loop: databases in Docker, services from the console
cp .env.example .env               # then set SCOREGRID_JWT_SECRET
docker compose up -d postgres mongodb rabbitmq
cd services/<service> && ./mvnw spring-boot:run

# Whole system
docker compose up -d --build       # first build: 5–10 min
docker compose ps                  # wait for all healthy
docker compose logs -f <service>
docker compose --profile observability up -d

# Re-provision databases from scratch (drops all local data)
docker compose down -v && docker compose up -d postgres mongodb

# Frontend
cd frontend && npm run dev         # :5173
cd frontend && npm run build

# Local stack checks (refuse anything but localhost and the local Docker daemon)
scripts/smoke.sh                   # read-only: containers healthy, Eureka, 401 envelope, Prometheus targets
scripts/failover-demo.sh           # stops and kills replicas to show failover; no argument prints its usage
```

Testcontainers needs Docker running. On Windows the Maven wrapper needs `JAVA_HOME` set to a JDK (not a JRE).

| Endpoint | URL |
|----------|-----|
| Gateway | http://localhost:8080 |
| Frontend (compose) | http://localhost:3000 |
| RabbitMQ management | http://localhost:15672 |
| Grafana | http://localhost:3001 |
| Tempo (API, loopback only) | http://localhost:3200 |

---

## 8. Ownership — check before editing

Work is split three ways ([`docs/workstreams.md`](docs/workstreams.md)). Editing another person's service causes merge conflicts, not just etiquette problems.

| Path | Owner |
|------|-------|
| `services/auth-service/**`, `services/api-gateway/**` | **Bernard** (Stream A) |
| `services/tournament-service/**` | **Paggi** (Stream B) |
| `services/prediction-service/**`, `services/score-service/**` | **Werlen** (Stream C) |
| `compose.yaml`, `infra/**`, `.env.example`, `.github/**` | **Bernard** — ask first |
| `scripts/**` | **Bernard** — ask first |
| `frontend/src/App.tsx`, `frontend/src/lib/**`, `frontend/src/auth/**` | **Bernard** — shared routing and client, ask first |
| `frontend/src/components/**`, `frontend/src/index.css` | **Bernard** — the design system. **Import it; do not edit it.** Need a variant that does not exist? Ask, and it gets added once for all three. |
| `frontend/src/features/<area>/**` | The stream owning that area |
| `docs/contracts.md` | All three — PR, all three approve, bump event `version` if a payload changed |

### The design system

Built on Tailwind v4 + shadcn/ui, restyled to `scoregrid_mock_interfaces_html.html`. Design tokens live in `frontend/src/index.css` — change a colour there, never in a component.

| Import | What you get |
|--------|--------------|
| `@/components/ui/*` | shadcn primitives already themed: `Button`, `Card`, `Input`, `Label`, `Table`, `Badge`, `Tabs`, `Select`, `Dialog`, `Separator` |
| `@/components/layout/AppLayout` | Sidebar + topbar shell. Applied by the router; screens render inside it |
| `@/components/layout/page-header` | `usePageHeader(title, subtitle)` — sets the topbar heading from inside a screen |
| `@/components/common/states` | `EmptyState`, `ErrorState`, `LoadingState` — use these three, do not invent a fourth |
| `@/components/common/FormField` | Label + control + error, with the `aria-describedby` / `aria-invalid` wiring done |
| `@/components/common/StatusBadge` | `TournamentStatusBadge`, `MatchStatusBadge`, `PredictionLockBadge` — contract status to Spanish label and colour, in one place |
| `@/components/common/MetricCard` | `MetricCard`, `MiniStat` |
| `@/components/common/PageTitle` | Section heading with an action on the right |

Non-obvious variants, both from the mock: `Button` has `variant="success"` (green, confirm) and `size="block"` (full width); `Badge` has `active` / `draft` / `finished` / `closed`; `TabsList` has `variant="pill"`.

If the task spans another owner's files, say so and propose the split rather than editing across the boundary.

---

## 9. Scope discipline

[`docs/PRD.md`](docs/PRD.md) has an explicit Non-Goals list: automatic bracket generation, group standings tables, notifications, top-scorer predictions, private tournaments, per-tournament scoring rules. All **deliberately out**.

Do not implement them because they seem natural. If one is genuinely needed, say why and let a human decide.

Equally, do not add caching, service discovery, an extra abstraction layer, or a broker abstraction that was not asked for. Eureka is the explicit exception because it is a mandatory TP requirement and is already part of the platform design. This design is sized for three developers and a local Compose environment.

---

## 10. Commits

Conventional commits, scoped by service:

```
feat(tournament): add group team assignment
fix(prediction): reject prediction when kickoff has passed
test(score): parameterise scoring rule table
docs(contracts): add batch user lookup endpoint
```

Branches: `feat/<owner>-<short-description>`, e.g. `feat/paggi-match-crud`.

Never add AI attribution or `Co-Authored-By` trailers.
