# AGENTS.md

Instructions for AI coding agents working in the ScoreGrid repository.

`CLAUDE.md` is a symlink to this file — one set of instructions, not two. If you edit one, you have edited both.

---

## 1. What this is

ScoreGrid is a football prediction pool: admins build tournaments with group stages and knockout phases, participants predict match scores, the system scores predictions automatically and maintains per-tournament and global rankings.

Five Spring Boot services, a React frontend, two PostgreSQL databases and two MongoDB databases (sharing one instance per engine, isolated by per-service logins), RabbitMQ, all under one Docker Compose.

| Service | Port | Owns | Database |
|---------|------|------|----------|
| `api-gateway` | 8080 | Routing, edge JWT validation, CORS | — |
| `auth-service` | 8081 | Users, roles, JWT issuance | PostgreSQL |
| `tournament-service` | 8082 | Tournaments, teams, groups, phases, matches, results, enrolments | PostgreSQL |
| `prediction-service` | 8083 | Predictions, kickoff lock | MongoDB |
| `score-service` | 8084 | Scoring, tournament ranking, global ranking | MongoDB |

**Stack (verified, not assumed):** Java 21 · Spring Boot 4.1.0 · Spring Cloud 2025.1.2 · Spring Cloud Gateway 5.0.2 · Spring AMQP 4.1.0 · Maven (one independent POM per service, no aggregator) · React 19 · Vite 8 · TypeScript 6 · Tailwind CSS 4 · shadcn/ui (Radix primitives).

---

## 2. Current state

Scaffolding is complete and verified. Stream C (predictions and scoring) has landed. Streams A and B are still mostly unbuilt, and both currently carry **temporary stubs written by Stream C** that their owners are expected to replace.

**Platform — done:**
- Service skeletons, `shared/` package (security, error handling, current-user), `ResilienceConfig`, `RabbitConfig` with the full messaging topology, `application.yml` per service.
- `compose.yaml` — one Postgres and one MongoDB, each with one database and one login per service, provisioned from `infra/postgres/init` and `infra/mongo/init`; RabbitMQ; five services; frontend. `infra/` also holds the observability config.
- Git repository and `.github/workflows/ci.yml` — five services in a matrix, frontend lint + build, compose validation on both profiles. Green on `main`.
- Frontend: axios client, auth context, route guard, route map, and the **design system** (Tailwind v4 + shadcn/ui restyled to the mock) — see §8.

**Stream C — done** (`prediction-service`, `score-service`):
- `prediction-service`: full hexagonal slice — domain model, ports, the six-step validation chain, Mongo persistence with the unique index on `(userId, matchId)`, in-memory match cache fed by `match.scheduled` / `match.updated`, `RestClient` fallback to tournament-service behind a circuit breaker, six controller endpoints, event publisher.
- `score-service`: `ScoringRuleV1` (3 exact / 1 outcome / 0) with the parameterised table from `contracts.md`, idempotent per-match scoring by replace, tournament and global ranking projections with the tie-break chain, `match.finished` consumer, REST clients to prediction-service and auth-service, five ranking endpoints.
- Screens: `PredictionPage`, `MyPredictionsPage`, `AdminResultsPage`, wired into `App.tsx`.

**Temporary stubs — replace, do not build on:**

| Stub | Lives in | Owner who must replace it |
|------|----------|---------------------------|
| `AuthController` (register / login / me), `UserEntity`, `RoleEntity`, repositories, `V1__create_users_roles.sql` | `auth-service` | **Bernard** |
| `MatchController`, `ParticipantController`, `MatchEntity`, `TeamEntity`, `V1__create_tournament_tables.sql` | `tournament-service` | **Paggi** |

These exist so Stream C could integrate against something real. They are not hexagonal, they carry no tests, and they do not implement the full contract. Treat them as scaffolding with a deadline, not as a starting point to extend.

**Does not exist yet:**
- `auth-service`: `/api/users/{id}`, `/api/users`, `/api/users/batch`. **`score-service` already calls `/api/users/batch`** and silently falls back to rendering the raw user ID as the username when the call fails — so rankings look subtly wrong rather than broken until this ships.
- `tournament-service`: everything except the match/participant stubs — tournaments, teams, groups, phases, the state machine, `predictionsOpen`, enrolment, event publishing.
- Screens: Register, Dashboard, Tournament Ranking, Global Ranking (Stream A); tournament list, tournament detail, admin panel (Stream B).
- Integration tests. Only two unit tests exist repo-wide (`ScoringRuleV1Test`, `DerivedOutcomeTest`). No Testcontainers, `@WebMvcTest`, `@DataJpaTest` or `@DataMongoTest` suite yet, against the definition of done in [`docs/workstreams.md`](docs/workstreams.md#working-agreements).

**Undocumented cross-service mechanism — needs a contracts PR.** Stream C introduced `ServiceToken` (duplicated in `prediction-service` and `score-service`) which mints an HS256 JWT from `SCOREGRID_JWT_SECRET` with `sub` set to the *service name* and `roles: ["ADMIN"]`, for service-to-service REST calls. This is load-bearing and is **not** in [`docs/contracts.md`](docs/contracts.md) — which currently states `sub` is always a user ID. Do not copy the pattern into a third service until the contract is amended by PR with all three approvals. See [`docs/workstreams.md`](docs/workstreams.md#open-contract-questions).

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
   One deliberate exception, and it is not a secret: `application.yml` carries a dev-only default JWT secret (`dev-only-insecure-secret-do-not-deploy-anywhere-real`) so `./mvnw spring-boot:run` works in a fresh terminal and all five services agree on a locally minted token. `compose.yaml` still uses `${SCOREGRID_JWT_SECRET:?}`, so anything containerised hard-fails without a real value. Do not copy this pattern for database passwords or provider API keys.
10. **Code is English. The user interface is Spanish.** The line runs at the screen, not at the file.
    - **English:** identifiers, types, comments, commit messages, branch names, log output, error codes (`PREDICTION_LOCKED`), API field names (`predictionsOpen`), test names, documentation.
    - **Spanish:** every string a participant reads — nav labels, buttons, headings, form labels, validation messages, empty/error/loading copy, dates and number formatting.
    - The register is **Rioplatense** (voseo: "Ingresá", "Pronosticá"), matching `scoregrid_mock_interfaces_html.html`. The mock is the copy reference; when a screen exists there, reuse its wording rather than inventing a synonym.
    - A Spanish identifier and an English button are both wrong. An error code stays `NOT_ENROLLED` on the wire and is rendered to the user as "No estás inscripto en este torneo".

---

## 5. Version traps in this exact stack

All three fail **silently** — no compile error, no warning. Verified against the resolved dependency tree.

**Boot 4 renamed starters.** `spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-security-oauth2-resource-server` (not `-oauth2-resource-server`), `spring-boot-starter-flyway`, and modular `-test` starters. Do not copy dependency blocks from Boot 3 examples.

**Gateway routes live under `spring.cloud.gateway.server.webmvc.routes`.** Not `spring.cloud.gateway.routes`, not `spring.cloud.gateway.mvc.routes`. The old forms are ignored and the gateway routes nothing without complaining.

**Resilience4J has no annotations and no YAML instances here.** `spring-cloud-starter-circuitbreaker-resilience4j` brings only `resilience4j-circuitbreaker` and `resilience4j-timelimiter` — *not* `resilience4j-spring-boot`. So:
- `resilience4j.circuitbreaker.instances.*` in YAML does not bind. Silent no-op.
- `@CircuitBreaker` / `@Retry` are not on the classpath.
- Configure via `Customizer<Resilience4JCircuitBreakerFactory>` beans; call through `CircuitBreakerFactory`. The pattern is already in each `shared/config/ResilienceConfig.java`.
- Retry needs an explicit `resilience4j-retry` dependency or a `RestClient` interceptor.

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
```

Testcontainers needs Docker running. On Windows the Maven wrapper needs `JAVA_HOME` set to a JDK (not a JRE).

| Endpoint | URL |
|----------|-----|
| Gateway | http://localhost:8080 |
| Frontend (compose) | http://localhost:3000 |
| RabbitMQ management | http://localhost:15672 |
| Grafana | http://localhost:3001 |

---

## 8. Ownership — check before editing

Work is split three ways ([`docs/workstreams.md`](docs/workstreams.md)). Editing another person's service causes merge conflicts, not just etiquette problems.

| Path | Owner |
|------|-------|
| `services/auth-service/**`, `services/api-gateway/**` | **Bernard** (Stream A) |
| `services/tournament-service/**` | **Paggi** (Stream B) |
| `services/prediction-service/**`, `services/score-service/**` | **Werlen** (Stream C) |
| `compose.yaml`, `infra/**`, `.env.example`, `.github/**` | **Bernard** — ask first |
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

Equally, do not add caching, service discovery, an extra abstraction layer, or a broker abstraction that was not asked for. This design is sized for three developers and a local Compose environment.

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
