# ScoreGrid — Project Setup

**Audience:** Engineering
**Purpose:** Get from an empty repository to five running Spring Boot services with one command.

Everything in this document was generated against `start.spring.io` on 2026-07-26 and reflects what the Initializr actually produces today — not what older tutorials say.

---

## Toolchain

| Tool | Version | Note |
|------|---------|------|
| Java | **21 (LTS)** | Initializr also offers 17, 25, 26. We pin 21: it is the widest-supported LTS, and every developer's IDE handles it without a plugin update. |
| Spring Boot | **4.1.0** | Current GA |
| Spring Cloud | **2025.1.2** | The BOM version Initializr pairs with Boot 4.1.0. Do not pick this yourself — let the generated `pom.xml` set it. |
| Build | **Maven** | One independent `pom.xml` per service. No aggregator POM. |
| Node | **22 LTS** | Frontend only |
| Docker Desktop | latest | Compose v2 |

### Read this before you generate anything — Boot 4.x renamed starters

Spring Boot 4 reorganised the starter artifacts. Copying dependency blocks from a Boot 3 tutorial will not compile.

| Boot 3 | Boot 4.1 |
|--------|----------|
| `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** |
| `spring-boot-starter-oauth2-resource-server` | **`spring-boot-starter-security-oauth2-resource-server`** |
| `flyway-core` (plain dependency) | **`spring-boot-starter-flyway`** |
| `spring-boot-starter-test` (one jar for everything) | **modular `-test` starters** — `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-amqp-test`, … |
| `spring-cloud-starter-gateway` | **`spring-cloud-starter-gateway-server-webmvc`** (servlet) |

The Initializr gets all of this right. **Generate every service from the Initializr rather than hand-writing a POM.**

---

## Generating the services

> **Already done.** All five services exist under `services/`, generated with exactly these commands and verified to compile. This section is kept so you can regenerate a service or add a sixth without guessing.

Run these from the repository root. Each produces a self-contained Maven project with its own wrapper.

```bash
mkdir -p services && cd services

# ── api-gateway ─────────────────────────────────────────────────────────────
curl -G https://start.spring.io/starter.tgz \
  -d type=maven-project -d language=java -d bootVersion=4.1.0 -d javaVersion=21 \
  -d groupId=com.scoregrid -d artifactId=api-gateway -d name=api-gateway \
  -d packageName=com.scoregrid.gateway -d packaging=jar \
  -d dependencies=cloud-gateway,security,oauth2-resource-server,cloud-resilience4j,actuator,prometheus,distributed-tracing \
  | tar -xzvf - -C . --one-top-level=api-gateway

# ── auth-service ────────────────────────────────────────────────────────────
curl -G https://start.spring.io/starter.tgz \
  -d type=maven-project -d language=java -d bootVersion=4.1.0 -d javaVersion=21 \
  -d groupId=com.scoregrid -d artifactId=auth-service -d name=auth-service \
  -d packageName=com.scoregrid.auth -d packaging=jar \
  -d dependencies=web,data-jpa,postgresql,flyway,security,oauth2-resource-server,validation,actuator,prometheus,distributed-tracing,testcontainers \
  | tar -xzvf - -C . --one-top-level=auth-service

# ── tournament-service ──────────────────────────────────────────────────────
curl -G https://start.spring.io/starter.tgz \
  -d type=maven-project -d language=java -d bootVersion=4.1.0 -d javaVersion=21 \
  -d groupId=com.scoregrid -d artifactId=tournament-service -d name=tournament-service \
  -d packageName=com.scoregrid.tournament -d packaging=jar \
  -d dependencies=web,data-jpa,postgresql,flyway,amqp,validation,security,oauth2-resource-server,cloud-resilience4j,actuator,prometheus,distributed-tracing,testcontainers \
  | tar -xzvf - -C . --one-top-level=tournament-service

# ── prediction-service ──────────────────────────────────────────────────────
curl -G https://start.spring.io/starter.tgz \
  -d type=maven-project -d language=java -d bootVersion=4.1.0 -d javaVersion=21 \
  -d groupId=com.scoregrid -d artifactId=prediction-service -d name=prediction-service \
  -d packageName=com.scoregrid.prediction -d packaging=jar \
  -d dependencies=web,data-mongodb,amqp,validation,security,oauth2-resource-server,cloud-resilience4j,actuator,prometheus,distributed-tracing,testcontainers \
  | tar -xzvf - -C . --one-top-level=prediction-service

# ── score-service ───────────────────────────────────────────────────────────
curl -G https://start.spring.io/starter.tgz \
  -d type=maven-project -d language=java -d bootVersion=4.1.0 -d javaVersion=21 \
  -d groupId=com.scoregrid -d artifactId=score-service -d name=score-service \
  -d packageName=com.scoregrid.score -d packaging=jar \
  -d dependencies=web,data-mongodb,amqp,validation,security,oauth2-resource-server,cloud-resilience4j,actuator,prometheus,distributed-tracing,testcontainers \
  | tar -xzvf - -C . --one-top-level=score-service
```

On Windows without `tar`/`curl`, use start.spring.io in the browser with the same field values and unzip into `services/<name>/`.

### Frontend

```bash
npm create vite@latest frontend -- --template react-ts
cd frontend && npm i react-router-dom axios @tanstack/react-query
```

### What each dependency is for

| Initializr id | Resolves to | Why |
|---------------|-------------|-----|
| `web` | `spring-boot-starter-webmvc` | REST controllers |
| `cloud-gateway` | `spring-cloud-starter-gateway-server-webmvc` | Gateway routing (servlet stack, same threading model as the other services) |
| `security` | `spring-boot-starter-security` | Filter chain, method security, `BCryptPasswordEncoder` |
| `oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` | JWT validation. Removes the need for a hand-rolled filter — this is the single biggest reason not to write your own JWT plumbing. |
| `data-jpa` | `spring-boot-starter-data-jpa` | Auth + Tournament |
| `postgresql` | `org.postgresql:postgresql` | Driver |
| `flyway` | `spring-boot-starter-flyway` + `flyway-database-postgresql` | Versioned schema. **Never `ddl-auto: update`.** |
| `data-mongodb` | `spring-boot-starter-data-mongodb` | Prediction + Score |
| `amqp` | `spring-boot-starter-amqp` | RabbitMQ producer/consumer |
| `validation` | `spring-boot-starter-validation` | `@Valid` on request bodies |
| `cloud-resilience4j` | `spring-cloud-starter-circuitbreaker-resilience4j` | Circuit breaker + retry on outbound calls |
| `actuator` | `spring-boot-starter-actuator` | `/actuator/health`, `/actuator/prometheus` |
| `prometheus` | `micrometer-registry-prometheus` | Metrics scrape endpoint |
| `distributed-tracing` | `micrometer-tracing-bridge-brave` | Trace ID across HTTP **and** AMQP |
| `testcontainers` | `spring-boot-testcontainers` + modules | Real Postgres / Mongo / RabbitMQ in tests. H2 lies about Postgres behaviour. |

`auth-service` deliberately has **no** `amqp` and **no** `cloud-resilience4j`: it publishes no events and calls no other service. Adding dependencies "just in case" is how a service quietly grows responsibilities it should not have.

---

## Three traps in this exact version combination

All three were verified against the resolved dependency tree, not assumed. Each fails **silently** — no compile error, no startup warning — which is what makes them expensive.

### 1. Gateway routes live under `server.webmvc`

Spring Cloud Gateway 5.0.2 reads routes from:

```yaml
spring.cloud.gateway.server.webmvc.routes
```

Not `spring.cloud.gateway.routes` (Gateway 3.x) and not `spring.cloud.gateway.mvc.routes` (Gateway 4.x). Both older forms are simply ignored — the gateway starts happily and routes nothing. If a request through `:8080` 404s while the service answers directly on its own port, this is why.

### 2. Resilience4J: no annotations, no YAML instances

`spring-cloud-starter-circuitbreaker-resilience4j` pulls in **only** `resilience4j-circuitbreaker` and `resilience4j-timelimiter`. It does **not** pull in `resilience4j-spring-boot`. Consequences:

| What you might reach for | Reality |
|--------------------------|---------|
| `resilience4j.circuitbreaker.instances.*` in `application.yml` | Does not bind. Silent no-op. |
| `@CircuitBreaker`, `@Retry` annotations | Not on the classpath. |
| Configuring instances | `Customizer<Resilience4JCircuitBreakerFactory>` beans, in Java |
| Calling through a breaker | Inject `CircuitBreakerFactory` |

See `shared/config/ResilienceConfig.java` in tournament, prediction and score — the pattern is already set up with the named instances (`resultsProvider`, `tournamentClient`, `predictionClient`).

**Retry is not included either.** Add `io.github.resilience4j:resilience4j-retry` explicitly, or use a `RestClient` request interceptor. Whichever you pick, all three services do it the same way — agree it once.

### 3. Spring AMQP: `JacksonJsonMessageConverter`, not `Jackson2JsonMessageConverter`

Spring AMQP 4.1 ships both classes. The `Jackson2`-prefixed one is the legacy Jackson 2 converter, and Boot 4 ships **Jackson 3**. Use:

```java
new JacksonJsonMessageConverter()   // Jackson 3 — correct here
```

The Jackson2 variant compiles only if you add Jackson 2 to the classpath yourself.

---

## Repository structure

Monorepo. One Docker Compose, one documentation set, five independently buildable services.

```
scoregrid/
├── AGENTS.md                     # instructions for AI coding agents
├── CLAUDE.md                     # symlink → AGENTS.md
├── README.md
├── compose.yaml                  # the whole system
├── .env.example
├── .gitignore
├── .editorconfig
│
├── docs/
│   ├── PRD.md                    # what we are building and why
│   ├── start.md                  # this file
│   ├── contracts.md              # FROZEN interfaces — read before coding
│   └── workstreams.md            # who builds what
│
├── infra/                        # observability profile config
│   ├── prometheus/prometheus.yml
│   ├── grafana/provisioning/datasources/datasources.yml
│   ├── loki/loki-config.yml
│   └── promtail/promtail-config.yml
│
├── services/                     # each independently buildable
│   ├── api-gateway/
│   ├── auth-service/
│   ├── tournament-service/
│   ├── prediction-service/
│   └── score-service/
│
└── frontend/
    ├── Dockerfile                # multi-stage: node build → nginx
    ├── nginx.conf                # SPA fallback + cache headers
    └── src/
        ├── lib/api.ts            # the one axios client, points at the gateway
        ├── auth/                 # AuthContext + RequireAuth route guard
        └── App.tsx               # route map, placeholders tagged by owner
```

Every service directory is self-contained: its own `pom.xml`, `mvnw`, `Dockerfile` and `src/`. You can `cd services/auth-service && ./mvnw test` without anything else being built — there is no aggregator POM and no build order.

### Inside a service

Hexagonal, organised by **feature first, layer second**. Open the source root and you should see what the service *does*, not what framework it uses.

```
services/tournament-service/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/java/com/scoregrid/tournament/
    │   ├── TournamentServiceApplication.java
    │   │
    │   ├── tournament/                    ← feature
    │   │   ├── domain/
    │   │   │   ├── model/                 Tournament, TournamentStatus, Participant
    │   │   │   └── port/
    │   │   │       ├── in/                CreateTournamentUseCase, JoinTournamentUseCase
    │   │   │       └── out/               TournamentRepository, EventPublisher
    │   │   ├── application/               CreateTournamentService (implements the in-port)
    │   │   └── infrastructure/
    │   │       ├── web/                   TournamentController + request/response DTOs
    │   │       ├── persistence/           TournamentJpaEntity, TournamentJpaAdapter
    │   │       └── messaging/             TournamentEventPublisherAdapter
    │   │
    │   ├── team/
    │   ├── group/
    │   ├── phase/
    │   ├── match/
    │   │   └── infrastructure/external/   ResultProviderAdapter (Resilience4J lives here)
    │   │
    │   └── shared/
    │       ├── config/                    SecurityConfig, RabbitConfig, OpenApiConfig
    │       ├── error/                     GlobalExceptionHandler, ApiError
    │       └── security/                  CurrentUser resolver
    │
    ├── main/resources/
    │   ├── application.yml
    │   └── db/migration/                  V1__init.sql, V2__add_participants.sql
    └── test/java/...                      mirrors main
```

Three rules that keep this honest:

1. **`domain` imports nothing from Spring, JPA, or Jackson.** No `@Entity`, no `@JsonProperty` on a domain model. If the domain package compiles with the framework jars removed, the boundary is real.
2. **JPA/Mongo entities are not domain models.** `TournamentJpaEntity` lives in `persistence` and maps to `Tournament`. Yes, it is a mapper class you have to write. It is also the reason a schema change does not ripple into business rules.
3. **Controllers own DTOs, never domain objects.** Serialising a domain model straight to JSON leaks internals into a public contract you then cannot change.

---

## Ports, databases, environment

| Container | Port (host) | Depends on |
|-----------|-------------|------------|
| `frontend` | 5173 (dev) / 3000 | gateway |
| `api-gateway` | 8080 | all services |
| `auth-service` | 8081 | `postgres-auth` |
| `tournament-service` | 8082 | `postgres-tournament`, `rabbitmq` |
| `prediction-service` | 8083 | `mongo-prediction`, `rabbitmq` |
| `score-service` | 8084 | `mongo-score`, `rabbitmq` |
| `postgres-auth` | 5433 | — |
| `postgres-tournament` | 5434 | — |
| `mongo-prediction` | 27018 | — |
| `mongo-score` | 27019 | — |
| `rabbitmq` | 5672, 15672 (UI) | — |
| `prometheus` | 9090 | profile `observability` |
| `grafana` | 3001 | profile `observability` |
| `loki` | 3100 | profile `observability` |

Host ports are offset (5433/5434, 27018/27019) so they never collide with a Postgres or Mongo already installed on a developer's machine. Inside the Compose network, services use standard ports and container names.

`.env.example` — copy to `.env`, never commit `.env`. The file in the repository root is authoritative; this is an abridged view:

```bash
# Shared JWT secret. Generate: openssl rand -base64 48
SCOREGRID_JWT_SECRET=change-me-min-32-bytes-base64
SCOREGRID_JWT_TTL=PT24H

POSTGRES_AUTH_DB=scoregrid_auth
POSTGRES_AUTH_USER=scoregrid
POSTGRES_AUTH_PASSWORD=scoregrid

POSTGRES_TOURNAMENT_DB=scoregrid_tournament
POSTGRES_TOURNAMENT_USER=scoregrid
POSTGRES_TOURNAMENT_PASSWORD=scoregrid

MONGO_PREDICTION_DB=scoregrid_prediction
MONGO_SCORE_DB=scoregrid_score

RABBITMQ_USER=scoregrid
RABBITMQ_PASSWORD=scoregrid

# External results provider — disabled by default
RESULTS_PROVIDER_ENABLED=false
RESULTS_PROVIDER_BASE_URL=
RESULTS_PROVIDER_API_KEY=
```

### Observability is a Compose profile

```bash
docker compose up -d                          # 11 containers, ~2 GB
docker compose --profile observability up -d  # + Prometheus, Grafana, Loki, Promtail
```

Prometheus, Grafana and Loki are behind the `observability` profile so day-to-day development does not cost 2 extra GB of RAM. They are still part of the deliverable — they just are not always on.

---

## Conventions

**Health checks.** Every service exposes `/actuator/health/readiness` and `/actuator/health/liveness`. Compose `depends_on` uses `condition: service_healthy`. Without this, services race their databases at startup and you spend an afternoon debugging a problem that does not exist.

**Migrations.** Flyway, `V<n>__<description>.sql`, forward-only. Never edit a migration that has been pushed. `spring.jpa.hibernate.ddl-auto: validate` in every profile — it catches a drifted entity at startup instead of at 2am.

**Mongo.** No `ddl-auto` equivalent, so indexes are explicit. Define them with `@Indexed` / `@CompoundIndex` **and** assert them in an integration test. The unique index on `(userId, matchId)` is a business rule, not an optimisation — it is the only thing actually preventing duplicate predictions under concurrency.

**Errors.** One `@RestControllerAdvice` per service producing the envelope from [`contracts.md`](contracts.md#error-envelope). Business rule violations are typed domain exceptions, never `RuntimeException("nope")`.

**Testing.**
- Unit tests on `domain` and `application`. No Spring context. These should run in milliseconds — the scoring rule deserves a dozen of them.
- Slice tests: `@WebMvcTest` for controllers, `@DataJpaTest` / `@DataMongoTest` for adapters.
- Integration tests with Testcontainers for anything touching a real database or RabbitMQ.
- The scoring rule table in [`contracts.md`](contracts.md#scoring-rule-v1) is a parameterised test. Copy it verbatim.

**API docs.** `springdoc-openapi-starter-webmvc-ui` in each service; the gateway aggregates them at `/swagger-ui.html`. Add it manually — it is not an Initializr option.

**Commits.** Conventional commits, scoped by service: `feat(tournament): add group team assignment`.

**Branches.** `feat/<workstream>-<short-description>`, e.g. `feat/b-match-crud`. One PR per work unit, reviewed by one of the other two.

---

## What is already scaffolded

Phase 0 is done. In place and verified:

| | |
|---|---|
| Five Boot 4.1.0 / Java 21 services | compile clean (`./mvnw compile`) |
| `shared/` package in each service | `SecurityConfig` (HS256 JWT + role mapping), `GlobalExceptionHandler`, `ApiError`, `ErrorKind`, `DomainException`, `CurrentUser` |
| `ResilienceConfig` | tournament, prediction, score — named circuit breaker instances |
| `RabbitConfig` | full topology from [`contracts.md`](contracts.md#events--rabbitmq): exchange, DLX, both queues, both DLQs |
| `application.yml` per service | ports, datasources, actuator probes, Prometheus, tracing, `docker` profile with JSON logging |
| `compose.yaml` | 11 core containers, health-gated startup, `observability` profile |
| `infra/` | Prometheus, Loki, Promtail, Grafana datasources |
| `frontend/` | Vite + React 19 + TS, axios client with JWT interceptor, auth context, route guard, route map |

**First thing on a fresh clone:**

```bash
cp .env.example .env
openssl rand -base64 48        # paste into SCOREGRID_JWT_SECRET
docker compose up -d --build
```

The first build compiles five Spring services and pulls their dependencies — budget 5–10 minutes. After that, layer caching makes it fast.

### What is deliberately not scaffolded

No entities, no controllers, no migrations, no screens. Those are the [workstreams](workstreams.md). The scaffolding stops exactly where design decisions start.

The one Phase 0 step nobody can do for you: **read [`contracts.md`](contracts.md) together and freeze it** before splitting up.

---

## Upgrading to RS256

v1 signs JWTs HS256 with a shared secret because that is one environment variable instead of key files mounted into six containers.

The moment ScoreGrid runs anywhere other than one developer's laptop, switch:

1. `auth-service` loads an RSA keypair and exposes `GET /.well-known/jwks.json`.
2. Every other service replaces its secret-key decoder with
   `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://auth-service:8081/.well-known/jwks.json`.
3. `SCOREGRID_JWT_SECRET` is deleted everywhere.

This touches only `SecurityConfig` in each service and adds one endpoint to auth. It is a contained change — which is precisely why starting with HS256 is acceptable rather than a trap.
