# ScoreGrid Demo Runbook

This runbook is the shortest reproducible path for the final presentation. It
uses only local Docker services and does not require secrets in the repository.

## Start The Stack

```bash
cp .env.example .env
# Set SCOREGRID_JWT_SECRET and the database passwords in .env.
docker compose up -d --build
docker compose --profile observability up -d
docker compose ps
```

Wait until the core services report `healthy`. Eureka should list the Gateway
and the five business services at <http://localhost:8761>.

## Prepare Users

Register two users from the frontend at <http://localhost:3000>: one called
`admin` and one participant. New accounts are `PLAYER` by default. Promote the
admin account in the local Auth database with the following command, replacing
the username if necessary:

```bash
docker compose exec -T postgres sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_AUTH_DB"' <<'SQL'
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ADMIN'
WHERE u.username = 'admin'
ON CONFLICT DO NOTHING;
SQL
```

Log in again as `admin` so the new JWT contains the `ADMIN` role.

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
