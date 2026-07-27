# Review — Stream C (prediction-service, score-service)

**Reviewer:** Bernard (Stream A)
**Reviewing:** `c896f6a`, `f328f86`, merged to `main` in #3
**Date:** 2026-07-27
**Status:** open — Werlen to action

Review of the code as merged, against [`contracts.md`](contracts.md) and the hard rules in [`AGENTS.md`](../AGENTS.md#4-hard-rules). Nothing here has been changed in Werlen's files; this document is the whole deliverable so he can correct his own service.

---

## First, the parts that are right

Worth saying, because the list below is long and it is not a list of sloppiness.

- **Scoring is genuinely idempotent.** `MatchScoreDocument` uses `@Id private String matchId`, so `save()` replaces the per-match document rather than appending. Re-delivering `match.finished` produces identical numbers. This is the subtlest requirement in the project and it is correct.
- **The scoring rule is exactly the contract table**, with `exact` returning 3 and not 4, and a parameterised test behind it.
- **The hexagonal split is real.** Domain models carry no framework annotations, ports are properly separated, adapters live in `infrastructure`. This is the reference to copy.
- **`MatchFinishedConsumer` rethrows**, so the DLQ actually works. (Contrast with `MatchCacheConsumer` — see C6.)
- **`derivedOutcome` is computed on write and never accepted as input**, as specified.
- **The unique index on `(userId, matchId)`** is declared on the document with `auto-index-creation: true`, not faked with a read-then-write check.

---

## Blocking

### C1 — A failed enrolment check is reported to the user as "not enrolled"

`prediction-service` · `TournamentRestClient.java:83-99`

```java
} catch (Exception e) {
    if (e.getMessage() != null && e.getMessage().contains("404")) {
        return false;
    }
    // Enrollment check returning 404 means the user is not enrolled
    return false;
}
```

Every failure returns `false`: a timeout, a 500, a 401, a connection refused. The caller then throws `403 NOT_ENROLLED`.

This breaks the contract explicitly ([`contracts.md`](contracts.md#prediction-service--apipredictions)):

> If step 2 or 5 cannot be answered because Tournament Service is unavailable, respond `503`. Never accept a prediction you could not validate.

and hard rule 8. When tournament-service is down, every player is told they are not enrolled in a tournament they are enrolled in. The error blames the user for an infrastructure failure.

There is a second failure hiding inside the first: the `catch` sits **inside** the supplier passed to `executeWithResilience`, so the circuit breaker and the retry never observe a failure. Every call looks successful. The breaker for enrolment checks can never open, and the retry never retries.

Also: `e.getMessage().contains("404")` detects an HTTP status by substring-matching an exception message. `RestClient` throws `HttpClientErrorException.NotFound`; catch the type.

**Fix:** catch only `HttpClientErrorException.NotFound` → `false`. Let everything else propagate so the breaker sees it and the caller gets `503 DOWNSTREAM_UNAVAILABLE`.

### C2 — The tie-break sorts exactHits and hits backwards

`score-service` · `GetRankingsService.java:22-34`

```java
Comparator.comparingInt(TournamentRankingEntry::points).reversed()
        .thenComparingInt(TournamentRankingEntry::exactHits).reversed()
        .thenComparingInt(TournamentRankingEntry::hits).reversed()
        ...
```

`reversed()` reverses **the entire comparator chain built so far**, not the key immediately before it. Working it through:

| Step | Resulting order |
|------|-----------------|
| `comparingInt(points).reversed()` | points desc |
| `.thenComparingInt(exactHits).reversed()` | points **asc**, exactHits **desc** |
| `.thenComparingInt(hits).reversed()` | points desc, exactHits **asc**, hits desc |

Final order: `points desc → exactHits ASC → hits DESC → predictionsScored asc → username asc`.

The contract requires `points desc → exactHits desc → hits desc → predictionsScored asc → username asc`. `points` lands correctly only because it is reversed an odd number of times.

Verified, not just reasoned — three players tied on 10 points:

```
Werlen's order  : [ana(exact=1), caro(exact=2), beto(exact=4)]
Contract order  : [beto(exact=4), caro(exact=2), ana(exact=1)]
```

On a points tie the player with the **fewest** exact hits is ranked first. This is the headline number of the whole application and it is silently wrong. Both comparators are affected.

**Fix:** reverse each key individually, not the chain:

```java
Comparator.comparingInt(TournamentRankingEntry::points).reversed()
        .thenComparing(Comparator.comparingInt(TournamentRankingEntry::exactHits).reversed())
        .thenComparing(Comparator.comparingInt(TournamentRankingEntry::hits).reversed())
        .thenComparingInt(TournamentRankingEntry::predictionsScored)
        .thenComparing(TournamentRankingEntry::username);
```

And add a test — `ScoringRuleV1Test` covers the rule table but nothing covers the sort, which is why this got through.

### C3 — `tournamentsPlayed` is credited to one arbitrary user per match

`score-service` · `GetRankingsService.java:71-78`

```java
.collect(Collectors.groupingBy(
        ms -> ms.individualScores().getFirst().userId(),
        Collectors.mapping(MatchScore::tournamentId, Collectors.toSet())))
```

Each `MatchScore` is grouped by the userId of the **first prediction in its list** — whoever happens to be first. That user is credited with the tournament; every other participant in the same match is credited with nothing and falls through to `getOrDefault(..., 0L)` at line 87.

So in the global ranking nearly every player shows `tournamentsPlayed: 0`, and therefore `averagePointsPerTournament: 0.0` via the divide-by-zero guard.

**Fix:** derive it per user — for each user, the set of distinct `tournamentId`s across the match scores that contain a prediction of theirs.

### C4 — Service tokens are minted once at startup and expire after 24 hours

`prediction-service` · `TournamentRestClient.java:40`
`score-service` · `PredictionRestClient.java:38`, `AuthRestClient.java:28`

```java
.defaultHeader("Authorization", "Bearer " + ServiceToken.generate(jwtSecret, "prediction-service"))
```

The token is generated when the bean is constructed and baked into the client as a static default header. `ServiceToken` sets `exp` to `now + 86400`. After 24 hours of uptime, every service-to-service call returns `401` — forever, until the service restarts.

This compounds with C1: after 24 hours the enrolment check gets a 401, the blanket `catch` turns it into `false`, and every player is told `403 NOT_ENROLLED`. Nobody can create a prediction, and nothing in the logs points at an expired token.

**Fix:** mint per request (cheap) or cache with refresh before expiry. A `ClientHttpRequestInterceptor` that sets the header per call is the smallest change — one place, all three clients.

---

## Should fix

### C5 — The kickoff lock is recomputed from prediction-service's own clock

`prediction-service` · `MatchCachePort.java:21-25`

```java
public boolean predictionsOpen() {
    return "ACTIVE".equals(tournamentStatus)
            && "SCHEDULED".equals(matchStatus)
            && Instant.now().isBefore(startTime);
}
```

Hard rule 5:

> `predictionsOpen` is computed by `tournament-service` and trusted by everyone else. Prediction Service does not recompute the kickoff lock from its own clock. One clock, one answer.

This is a second clock. If prediction-service's clock runs ahead, it locks a match that tournament-service still reports as open.

It gets worse on the REST path — `TournamentRestClient.java:63,75-80`:

```java
String tournamentStatus = match.predictionsOpen() ? deriveTournamentStatus(match) : null;
...
private String deriveTournamentStatus(MatchResponse match) {
    return "ACTIVE";
}
```

The authoritative `predictionsOpen` arrives in the response and is **discarded**, used only to decide whether to hardcode the string `"ACTIVE"` or `null`. The service then re-derives the answer it was already given.

**Fix:** carry `predictionsOpen` on `CachedMatch` as a field and trust it. The event payload has `tournamentStatus` and `status`; if the authoritative flag is not on `match.scheduled`/`match.updated`, that is a contracts PR, not a local re-derivation.

### C6 — Malformed match events are logged and acknowledged, never dead-lettered

`prediction-service` · `MatchCacheConsumer.java:42-44`

```java
} catch (Exception e) {
    log.error("Failed to deserialize match event payload: {}", envelope.eventType(), e);
}
```

Swallowing the exception acknowledges the message. `prediction.match-cache.dlq` is configured and can never receive anything from this path — a bad payload is lost, and the match cache silently goes stale for that match.

`MatchFinishedConsumer` in score-service does this correctly (`throw e`). Make them consistent.

### C7 — Step 4 of the validation chain is missing, so the wrong error code is returned

`prediction-service` · `CreatePredictionService.java:56`

```java
// Step 4: tournament must be ACTIVE (already checked by predictionsOpen)
```

The contract lists it as a distinct step with its own code:

> 4. Tournament is `ACTIVE` → else `409 TOURNAMENT_NOT_ACTIVE`

Folding it into `predictionsOpen` means a non-ACTIVE tournament returns `409 PREDICTION_LOCKED`. Error codes are part of the contract — the frontend branches on them, and `TOURNAMENT_NOT_ACTIVE` is currently unreachable.

### C8 — `GET /api/predictions/match/{matchId}` is open to every PLAYER

`prediction-service` · `PredictionController.java:81`

```java
@PreAuthorize("hasAnyRole('ADMIN','PLAYER')")
```

The contract restricts this to `ADMIN` or service. As written, any logged-in player can read every other player's predictions for a match **before kickoff** — which is a competitive advantage in a prediction pool.

`score-service` reaches it with a service token carrying `roles: ["ADMIN"]`, so `hasRole('ADMIN')` alone is sufficient. Drop `PLAYER`.

### C9 — A 404 is retried three times with backoff

`prediction-service` · `ResilienceConfig.java:78-85` with `TournamentRestClient.getMatch`

`retryExceptions(RuntimeException.class)` combined with `DomainException extends RuntimeException` means the `NOT_FOUND` thrown in `onStatus` is retried. A match that does not exist costs three round trips and ~700ms of backoff before returning 404.

**Fix:** `retryExceptions` on the transport exceptions only, or `ignoreExceptions(DomainException.class)`.

---

## Worth cleaning up

### C10 — `ServiceToken` hand-builds a JWT while Nimbus sits on the classpath

`prediction-service` and `score-service` · `shared/security/ServiceToken.java`

```java
String payload = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(("{\"sub\":\"" + serviceName + "\",\"roles\":[\"ADMIN\"],...").getBytes(...));
```

Hand-concatenated JSON with no escaping, hand-assembled JWS. Both services already have `spring-boot-starter-security-oauth2-resource-server`, which brings Nimbus — the same `JwtEncoder` that `auth-service` now uses in `JwtTokenIssuer`. There is no reason to hand-roll the one thing in the system that must not be subtly wrong.

Separately, this mechanism is **undocumented and needs a contracts PR** before it spreads to a third service — see [`workstreams.md`](workstreams.md#open-contract-questions). `sub` currently holds a service name where the contract says it holds a user ID, and services grant themselves `ADMIN`, so no downstream service can tell a service caller from a human administrator.

That last part is no longer theoretical. Minting a token exactly as `ServiceToken.generate(secret, "score-service")` does, and pointing it at the now-real auth-service:

```
GET /api/users/batch?ids=1,2   → 200  [{"id":"1","username":"maxi"},{"id":"2","username":"ana"}]
GET /api/users?page=0&size=10  → 200  {"items":[{"id":"1","username":"maxi","email":"maxi@example.com",...
```

The first call is the one score-service needs, and it works. The second is the `ADMIN`-only listing, and the same token reads **every user's email address** — because `roles: ["ADMIN"]` is indistinguishable from a real administrator.

Nothing is wrong in `auth-service` here: it is enforcing exactly the role the token claims. The problem is that a service grants itself the highest privilege in the system for a call that only needs to read usernames. A distinct `SERVICE` role, or scoping service tokens to the endpoints they actually use, would both fix it — that is the decision the contracts PR needs to make.

### C11 — Three layers of fallback hide the missing `/api/users/batch`

`score-service` · `AuthRestClient.java:44-48`, `GetRankingsService.java:159-165`, and `getOrDefault(..., "?")` at lines 56 and 85.

`/api/users/batch` does not exist yet — that is my work, and it is coming. But the failure is currently caught in three separate places, each with its own fallback, so rankings render user IDs where usernames belong and nothing surfaces the cause. One fallback, logged once, is enough.

Note this also degrades C2's tie-break: `username` is the final tie-break key, and it is currently the string `"42"` or `"?"`.

### C12 — Rankings load every score document into memory on every request

`score-service` · `GetRankingsService.java:63,96` — `findAll()` / `findAllByTournamentId()`, grouped in Java, then `subList` for paging.

Fine at demo scale and not worth rewriting now. Flagging it so it is a known limit rather than a surprise: `page`/`size` do not reduce the work done, only the rows returned.

### C13 — `locked` is always `false` on create

`prediction-service` · `CreatePredictionService.java:71`

```java
boolean locked = !match.predictionsOpen();
```

Step 3 already threw if `predictionsOpen()` was false, so this is always `false`. Harmless, but it reads as if it handles a case it cannot reach.

### C14 — Auditing annotations fight the constructor

`prediction-service` · `PredictionDocument` carries `@CreatedDate` / `@LastModifiedDate` while `CreatePredictionService` also passes explicit `Instant.now()` values. Two sources of truth for the same two fields; the auditor wins. Pick one.

### C15 — `MatchFinishedConsumer` depends on the concrete service, not the port

`score-service` · `MatchFinishedConsumer` injects `ScoreMatchService` rather than `ScoreMatchUseCase`. `ScoreMatchService` is also `public` with a package-private constructor. Everything else in Stream C injects the port — this is the one place that reaches past it.

---

## Cross-boundary note

Stream C wrote stubs inside `auth-service` and `tournament-service` to unblock integration. That was the right call and it is not a complaint — but it should be visible rather than assumed:

| Stub | Service | Owner replacing it |
|------|---------|--------------------|
| `AuthController`, `UserEntity`, `RoleEntity`, repositories, `V1__create_users_roles.sql` | `auth-service` | Bernard — **in progress** on `feat/bernard-stream-a` |
| `MatchController`, `ParticipantController`, `MatchEntity`, `TeamEntity`, `V1__create_tournament_tables.sql` | `tournament-service` | Paggi — not started |

`V1__create_users_roles.sql` and `V1__create_tournament_tables.sql` are on `main` and therefore **forward-only** (hard rule 6). Neither can be edited now; corrections go in a `V2`.

---

## Tests

Two unit tests exist across both services: `ScoringRuleV1Test` and `DerivedOutcomeTest`. Both are good. But the definition of done in [`workstreams.md`](workstreams.md#working-agreements) asks for integration tests with Testcontainers on anything touching a database or a queue, and there are none.

The gap is not academic — of the four blocking findings above, three would have been caught by a test that exercised the behaviour rather than the pure function:

- C2 by any test asserting ranking order on a points tie
- C3 by any test with two users in one tournament
- C1 by a test that stubs tournament-service returning 500

Suggested minimum: a ranking-order test (pure, fast, no containers — this is the highest value per line in the whole list), a `@DataMongoTest` proving the unique index rejects a duplicate, and a `@WebMvcTest` per controller for status codes and the error envelope.

---

## Suggested order

1. **C2** — pure logic, a few lines, fixes the product's headline number.
2. **C3** — same file, same session.
3. **C1** and **C4** together — both are the service-call path, and they compound.
4. **C5**, **C7**, **C8** — contract conformance.
5. **C6**, **C9** — messaging and retry correctness.
6. The rest as cleanup.

C10's contracts PR is a conversation for all three of us, not a solo fix.
