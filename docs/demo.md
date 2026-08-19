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
and the five business services at <http://localhost:8761>.

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
6. Watch `tournament-service` publish `match.finished` and `score-service` consume it:

```bash
docker compose logs -f tournament-service score-service
```

7. Open the tournament ranking and show the participant's three points.
8. Submit a corrected result once more and show that rescoring replaces the match score instead of doubling the ranking.

The same flow demonstrates the service boundaries: the frontend calls only the
Gateway, Prediction Service validates the tournament through REST, and Score
Service reads predictions through REST after the RabbitMQ event.

## Platform Evidence

- Gateway and edge authentication: `curl -i http://localhost:8080/api/auth/me` returns `401` without a token.
- Eureka registration: open <http://localhost:8761> and show the registered applications.
- Metrics: open <http://localhost:9090/targets> and show the six `scoregrid-services` targets as `UP`.
- Dashboard and centralized logs: open <http://localhost:3001> and select `ScoreGrid / ScoreGrid - Overview`.
- RabbitMQ: open <http://localhost:15672> and show the event queues and consumers.

## Resilience Evidence

For a short fault-tolerance demonstration, stop `prediction-service`, invoke an
endpoint that requires predictions, and show the bounded failure response. Start
the service again and verify recovery:

```bash
docker compose stop prediction-service
# Exercise the ranking/recalculation flow through the frontend or Gateway.
docker compose start prediction-service
```

The service clients use named Resilience4J circuit breakers and retries; they do
not accept a write when a required downstream validation cannot be answered.
