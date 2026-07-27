# AGENTS.md

Instructions for AI coding agents working in the ScoreGrid repository.

`CLAUDE.md` is a symlink to this file — one set of instructions, not two. If you edit one, you have edited both.

---

## 1. What this is

ScoreGrid is a football prediction pool: admins build tournaments with group stages and knockout phases, participants predict match scores, the system scores predictions automatically and maintains per-tournament and global rankings.

Five Spring Boot services, a React frontend, two PostgreSQL databases, two MongoDB databases, RabbitMQ, all under one Docker Compose.

| Service | Port | Owns | Database |
|---------|------|------|----------|
| `api-gateway` | 8080 | Routing, edge JWT validation, CORS | — |
| `auth-service` | 8081 | Users, roles, JWT issuance | PostgreSQL |
| `tournament-service` | 8082 | Tournaments, teams, groups, phases, matches, results, enrolments | PostgreSQL |
| `prediction-service` | 8083 | Predictions, kickoff lock | MongoDB |
| `score-service` | 8084 | Scoring, tournament ranking, global ranking | MongoDB |

**Stack (verified, not assumed):** Java 21 · Spring Boot 4.1.0 · Spring Cloud 2025.1.2 · Spring Cloud Gateway 5.0.2 · Spring AMQP 4.1.0 · Maven (one independent POM per service, no aggregator) · React 19 · Vite 8 · TypeScript 6.

---

## 2. Current state

Scaffolding is complete and verified: all five services compile, the frontend builds, `docker compose config` validates.

**Exists:** service skeletons, `shared/` package (security, error handling, current-user), `ResilienceConfig`, `RabbitConfig` with the full messaging topology, `application.yml` per service, `compose.yaml`, `infra/` observability config, frontend with axios client + auth context + route guard + route map.

**Does not exist:** any entity, controller, repository, use case, Flyway migration, Mongo document, or real screen. That is feature work — see §8.

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
2. **No shared Java library for DTOs, events or domain types.** Each service owns its own copy of the payload classes. Deliberate — rationale in [`docs/PRD.md`](docs/PRD.md#alternatives-considered). Do not "clean this up" by extracting a common module.
3. **The acting user comes from the JWT `sub` claim**, via the `CurrentUser` bean. Never from a request body, query parameter or header. A `userId` field in a request payload is a privilege escalation bug.
4. **Scoring is idempotent.** The per-match score document is *replaced*; rankings are *derived* from score documents. Never increment a running total. Re-scoring a corrected result or a redelivered message must produce identical numbers.
5. **`predictionsOpen` is computed by `tournament-service` and trusted by everyone else.** Prediction Service does not recompute the kickoff lock from its own clock. One clock, one answer.
6. **Flyway migrations are forward-only.** Never edit a pushed migration. `ddl-auto` is `validate`, never `update`.
7. **The unique index on `(userId, matchId)` is the duplicate-prediction rule** — not a read-then-write check, which loses under concurrency.
8. **Never accept a write you could not validate.** If a downstream check cannot be answered because a service is down, return `503`. Do not assume the happy path.
9. **No secrets in the repository.** `.env` is gitignored; `.env.example` holds placeholders only.
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

# Whole system
cp .env.example .env               # then set SCOREGRID_JWT_SECRET
docker compose up -d --build       # first build: 5–10 min
docker compose ps                  # wait for all healthy
docker compose logs -f <service>
docker compose --profile observability up -d

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
