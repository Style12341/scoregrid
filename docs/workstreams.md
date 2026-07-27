# ScoreGrid — Work Split for Three Developers

**Audience:** Bernard, Paggi, Werlen

| Stream | Owner | Scope in one line |
|--------|-------|-------------------|
| **A — Identity, Edge & Platform** | **Bernard** | `auth-service`, `api-gateway`, Compose, observability, CI, frontend foundation |
| **B — Tournament Core** | **Paggi** | `tournament-service` and every tournament/admin screen |
| **C — Predictions & Scoring** | **Werlen** | `prediction-service`, `score-service`, prediction and result screens |

Three people, five backend services and one frontend. This document exists to answer one question: **what can I build right now without waiting for anybody?**

The split is vertical. Each stream owns backend services *and* the screens that consume them, end to end. Nobody is "the frontend person" waiting on "the backend person" — that arrangement guarantees one of you is idle half the time.

---

## Phase 0 — Scaffolding

| # | Output | Status |
|---|--------|--------|
| 1 | Repository files: `.gitignore`, `.gitattributes`, `.editorconfig`, `.env.example` | **done** |
| 1b | Git repository on `main`, remote set, CI building all five services + frontend on every PR | **done** |
| 2 | All five services generated from the Initializr, Boot 4.1.0 / Java 21 | **done** |
| 3 | `compose.yaml`: 2× Postgres, 2× Mongo, RabbitMQ, 5 services, frontend, health-gated startup | **done** |
| 4 | `shared/` package (`SecurityConfig`, `GlobalExceptionHandler`, `ApiError`, `ErrorKind`, `DomainException`, `CurrentUser`) copied into all services | **done** |
| 5 | RabbitMQ exchange, queues, bindings and DLQs declared per [`contracts.md`](contracts.md#events--rabbitmq) | **done** |
| 6 | Vite app with router, axios client, auth context, route guard | **done** |
| 6b | Design system (Tailwind 4 + shadcn/ui restyled to the mock) and the Login screen | **done** |
| 7 | Observability stack (`infra/`) behind the `observability` profile | **done** |
| 8 | **[`contracts.md`](contracts.md) read aloud and frozen** — every endpoint, payload and status code | **← do this together** |

Step 8 is the one that matters and the one nobody can do for you. Every hour spent agreeing contracts before splitting saves a day of integration pain in week three. After this, contracts change only by PR reviewed by all three.

Verified working at scaffold time: all five services compile, the frontend builds, `docker compose config` validates both profiles.

---

## Where we are

| Stream | Owner | State |
|--------|-------|-------|
| **A** — Identity, Edge & Platform | Bernard | Platform and design system done. `auth-service` is a **stub written by Stream C**; gateway routes configured but unexercised; four of five screens unbuilt. |
| **B** — Tournament Core | Paggi | Not started. `MatchController` / `ParticipantController` / `V1__create_tournament_tables.sql` are **stubs written by Stream C**. |
| **C** — Predictions & Scoring | Werlen | **Done** — both services implemented hexagonally end to end, three screens shipped, merged to `main` in #3. |

Stream C could not integrate against nothing, so it wrote the minimum stubs it needed inside `auth-service` and `tournament-service`. That was pragmatic and it unblocked the work, but it means **two people now own code they did not write**. Bernard and Paggi replace those stubs as part of their own streams — the stubs are not a head start, they are a placeholder that happens to compile.

**Outstanding, in rough dependency order:**

1. **Bernard** — real `auth-service`: hexagonal structure, the error envelope on every failure path, and the three missing user endpoints. `GET /api/users/batch` is the urgent one: `score-service` already calls it and falls back to showing raw user IDs as usernames, so rankings render *plausibly wrong* rather than failing loudly.
2. **Paggi** — real `tournament-service`. Everything downstream of `predictionsOpen` and `match.finished` is already built and waiting for a real publisher.
3. **All three** — tests. Two unit tests exist repo-wide. The definition of done below is not currently being met by any stream, including C.

---

## Open contract questions

Raise these as PRs against [`contracts.md`](contracts.md) with all three approvals. Do not resolve them by writing more code.

**Service-to-service authentication is undocumented.** `prediction-service` and `score-service` each carry a `ServiceToken` helper that mints an HS256 JWT from `SCOREGRID_JWT_SECRET` with `sub` set to the service name (`"score-service"`) and `roles: ["ADMIN"]`, and attach it to outbound REST calls. `contracts.md` states that `sub` is a user ID and that `roles` carries `PLAYER` / `ADMIN` — so today a downstream service cannot distinguish a service caller from a genuinely ADMIN human, and `CurrentUser.requireId()` on such a request returns a string that is not a user ID.

Needs an explicit decision on: the claim shape for a service principal, whether services should hold `ADMIN` at all or a distinct `SERVICE` role, and whether the token is minted per call or cached. Until it is decided, do not add a third copy of `ServiceToken`.

---

## The three streams

### Stream A — Identity, Edge and Platform · **Bernard**

**Owns:** `services/auth-service`, `services/api-gateway`, `compose.yaml`, `infra/`, CI, and the frontend foundation.

Backend
- `auth-service` end to end: register, login, `/me`, user lookup, batch lookup. Flyway migrations for `users`, `roles`, `user_roles`. BCrypt hashing. JWT issuance with the claim set from [`contracts.md`](contracts.md#authentication-contract).
- `api-gateway`: all five route groups, edge JWT validation, CORS configured here and nowhere else.
- The `SecurityConfig` that the other four services consume — you define how a JWT becomes an authenticated principal with roles, and the other two copy it.

Platform
- `compose.yaml` for the whole system: one Postgres and one MongoDB (each holding one database per service, with per-service logins), RabbitMQ, five services, frontend. Health checks and `depends_on: condition: service_healthy`.
- `infra/`: Prometheus scrape config, Grafana provisioning + one dashboard per service, Loki + Promtail for centralised JSON logs. All behind the `observability` Compose profile.
- CI: build and test all five services plus the frontend on every PR.

Frontend
- App shell, routing, axios interceptor that attaches the JWT and handles `401` by redirecting to login, protected-route guard, role-based navigation.
- The **design system primitives** the other two build on: layout, sidebar, table, form field, button, empty state, error state, loading state. Ship these in week one — B and C are blocked on them in a way they are not blocked on your APIs.
- Screens: Login, Register, Dashboard, Tournament Ranking, Global Ranking.

**Why this shape:** auth-service and the gateway are the two smallest services, so this stream absorbs the platform work and the frontend foundation. The rankings screens land here because they are read-only tables — the purest exercise of the design system you are already building.

---

### Stream B — Tournament Core · **Paggi**

**Owns:** `services/tournament-service` and every tournament and admin screen.

Backend — the largest single service in the system
- Entities and Flyway migrations: `tournaments`, `teams`, `tournament_teams`, `groups`, `group_teams`, `phases`, `matches`, `tournament_participants`.
- Tournament CRUD and the `DRAFT → ACTIVE → FINISHED / CANCELLED` state machine, with the rules that hang off it.
- Team catalogue and tournament–team assignment (a team appears at most once per tournament).
- Groups, group–team assignment, knockout phases.
- Match CRUD with the invariants: exactly one of group/phase, home ≠ away, both teams registered in the tournament, kickoff in the future.
- `predictionsOpen` computed server-side. **This field is the prediction lock.** Prediction Service trusts it and never second-guesses it — get it right.
- Result loading (`PUT /api/matches/{id}/result`), which sets `FINISHED` and publishes `match.finished`.
- Event publishing: `match.scheduled`, `match.updated`, `match.finished`.
- Enrolment: `POST /tournaments/{id}/join` and the participants check Prediction Service depends on.
- `ResultProvider` port plus the external-API adapter behind a Resilience4J circuit breaker and retry, feature-flagged off by default.

Frontend
- Tournament list with status filter.
- Tournament detail: general info, groups tab, phases tab, fixture with results.
- Admin panel: tournament CRUD, team catalogue, assign teams, create groups, assign teams to groups, create phases, create and edit matches.

**Why this shape:** `tournament-service` is roughly as large as the other two backend streams' services combined, so this stream gets no platform work and no cross-cutting responsibility. Its UI is the natural consumer of its own API.

**Designated cut:** if this stream falls behind, the external `ResultProvider` adapter is the first thing to drop. Manual result loading is the primary path and covers every requirement on its own. Define the port anyway — the adapter can land later, or move to Stream C.

---

### Stream C — Predictions and Scoring · **Werlen**

**Owns:** `services/prediction-service`, `services/score-service`, and the prediction and result-loading screens.

`prediction-service`
- Mongo document model with the **unique index on `(userId, matchId)`**. That index is the duplicate-prediction rule; a read-then-write check is not.
- Create and update predictions with the six-step validation chain in [`contracts.md`](contracts.md#prediction-service--apipredictions), in that order.
- `derivedOutcome` computed from the score on write, never accepted as input.
- Local match cache fed by `match.scheduled` / `match.updated`, so the lock check normally costs no network call. REST fallback to `GET /api/matches/{id}` on cache miss, wrapped in a circuit breaker.
- Query endpoints by user, match and tournament.
- Publishes `prediction.created` / `prediction.updated`.

`score-service`
- Consumes `match.finished`. Fetches predictions from Prediction Service over REST with retry + circuit breaker; on exhaustion the message dead-letters and retries later rather than being lost.
- The scoring rule: 3 exact / 1 outcome / 0. Build it as a `ScoringRule` port with one implementation and the parameterised test table from [`contracts.md`](contracts.md#scoring-rule-v1).
- Per-match score document written by **replace, not append** — this is what makes rescoring a corrected result idempotent.
- Tournament and global ranking projections derived from score documents, with the tie-break chain. `position` computed at query time, never stored.
- Ranking endpoints, plus the two admin recalculate endpoints as the escape hatch for a lost event.

Frontend
- Prediction form (score inputs, locked state after kickoff, validation errors).
- My Predictions, filterable by tournament, showing points earned.
- Load Results admin screen — the screen that triggers your own scoring pipeline, which makes you the owner of the end-to-end demo.

**Why this shape:** two medium services that are tightly coupled to each other through one event and one REST call. Splitting them across two people would put the system's most subtle interaction — idempotent scoring — across an ownership boundary.

---

## Load balance

| Stream | Backend | Platform | Frontend |
|--------|---------|----------|----------|
| **A** · Bernard | 2 small services | compose, observability, CI | shell + design system + 5 screens |
| **B** · Paggi | 1 very large service | — | 3 screen groups (incl. full admin CRUD) |
| **C** · Werlen | 2 medium services | — | 3 screens |

Deliberately not identical. B carries one oversized service and therefore no infrastructure; A carries the smallest services and therefore the platform and the shared UI foundation.

**Rebalance levers, in order:** move the external `ResultProvider` adapter from B to C · move the admin panel screens from B to A · move the Rankings screens from A to C.

---

## Who depends on whom

Nobody waits. Every dependency has a stub.

| Who | Needs | From | Until then |
|-----|-------|------|------------|
| Paggi, Werlen | A valid JWT | Bernard | Sign one in a test fixture with `SCOREGRID_JWT_SECRET` and claims `sub` / `roles`. No HTTP call, no running auth-service. |
| Paggi, Werlen | Design system components | Bernard | **Shipped.** Import from `@/components/**` — see [`AGENTS.md`](../AGENTS.md#the-design-system). Do not edit those files; ask and a variant gets added once for all three. |
| Werlen | `GET /api/matches/{id}`, participants check | Paggi | **Resolved by stub.** Werlen wrote a minimal `MatchController` / `ParticipantController` in `tournament-service`; Paggi replaces them. |
| Werlen | `match.scheduled` / `match.finished` | Paggi | Publish the payloads by hand from the RabbitMQ management UI at `localhost:15672` |
| Werlen (ranking usernames) | `GET /api/users/batch` | Bernard | **Unresolved and silent.** `AuthRestClient` catches the failure and maps each ID to itself, so rankings show `"42"` where a username belongs. Nothing breaks; it just reads wrong. |
| Bernard (rankings UI) | `GET /api/rankings/**` | Werlen | **Shipped.** Call `score-service` directly. |
| Paggi (fixture UI) | Nothing from anyone | — | — |
| Everyone | Contracts | Phase 0 | Frozen — with one undocumented addition, see [Open contract questions](#open-contract-questions). |

The one hard sequencing constraint in the whole project was Bernard's design system in week one. It has shipped, so nothing blocks anyone now — everything else is stubbable, which is exactly why the contracts are frozen up front.

---

## Milestones

Set real dates against your own deadline. What matters is the order — breadth comes after the vertical slice, never before.

### M1 — It boots

`docker compose up` reaches all-healthy. Log in through the gateway, receive a real JWT, and reach an authenticated endpoint on all four services with it.

*Integration checkpoint, all three present.* Nobody continues until a token minted by auth-service is accepted by tournament, prediction and score.

### M2 — Vertical slice

The thinnest possible end-to-end path, all five services, no shortcuts:

1. Admin creates one tournament, two teams, one match.
2. Player joins the tournament and predicts 2–1.
3. Admin loads the result 2–1.
4. `match.finished` fires; score-service scores 3 points.
5. The tournament ranking shows the player with 3 points.

*Integration checkpoint.* **This is the milestone that de-risks the project.** Every hard part — event flow, cross-service REST, idempotent scoring, the JWT chain — is exercised once, on the smallest possible data. Feature breadth before this point is building on unverified foundations.

### M3 — Breadth

Groups, phases, the full admin panel, all prediction and ranking screens, global ranking, pagination, empty states.

### M4 — Hardening and demo

Circuit breakers verified by actually killing a container. DLQ behaviour verified by killing prediction-service while a match finishes. Observability profile up with real dashboards. Seed script for demo data. README verified by one of you following it on a clean checkout.

---

## Working agreements

**Ownership.** A file belongs to exactly one person. Two people editing `tournament-service` at once is how you lose an afternoon to merge conflicts.

| File | Owner | Rule |
|------|-------|------|
| `services/auth-service/**`, `services/api-gateway/**` | Bernard | — |
| `services/tournament-service/**` | Paggi | — |
| `services/prediction-service/**`, `services/score-service/**` | Werlen | — |
| `compose.yaml`, `infra/**`, `.github/**` | Bernard | Ask before editing |
| `.env.example` | Bernard | Append only |
| `frontend/src/App.tsx`, `frontend/src/lib/**`, `frontend/src/auth/**` | Bernard | Ask — shared routing and client |
| `frontend/src/features/<your-area>/**` | Each stream | Free |
| `docs/contracts.md` | All three | PR + all three approve |

**Definition of done** for any work unit:
- Unit tests on domain logic, integration test with Testcontainers on anything touching a database or queue
- Error responses use the shared envelope
- Endpoint matches `contracts.md` exactly — path, payload, status codes
- Runs inside `docker compose up`, not just from the IDE
- OpenAPI annotations present
- Reviewed by one other developer

**Daily.** Fifteen minutes: what I finished, what I am on, what I am blocked by. A blocker older than a day goes to all three.

**When you want to change a contract.** Do not just change it. Open the PR against `docs/contracts.md`, say why, get both approvals, update stubs in the same PR. The alternative is discovering the change at the M2 checkpoint, which costs everyone a day instead of costing you ten minutes.
