# market-agri-catalog-api

Service API of the **catalog** domain (products published by producers and the internal stock
reservations used by the purchase saga) of the **Marketplace Agrícola Huila** distributed system:
Java 21, Spring Boot 3.5, hexagonal architecture in three Maven modules (Anexo C). Contract:
`market-agri-docs/07-api/api-contract.md` §4.2.

## Modules

```
catalog-core/      domain/model, application/port/{in,out}, application/usecase — plain Java, no framework
catalog-adapters/  adapter/in/http, adapter/out/persistence
catalog-app/       composition root: entry point, wiring and every limit (application.yml)
deploy/            Dockerfile and compose.yml, included by market-agri-infra (no host port)
```

`catalog-core` declares no framework: a Spring or JDBC type there does not compile. The schema and
its migrations live in `market-agri-catalog-db` (ADR-011); this service connects as
`catalog_app` and never migrates.

## What the service already does

| Rule | Where |
|---|---|
| `GET /health` → `200 {"status":"UP"}` without token (liveness, E-60) | `adapter/in/http/HealthController` |
| One error body `{error, message, details, traceId}` from **one** handler (D-C21) | `adapter/in/http/ErrorHandler` |
| `X-Correlation-Id` reused or generated, returned, as `traceId` and in every JSON log line (D-C28) | `adapter/in/http/CorrelationFilter` |
| `GET /health/ready` → `200`/`503` `{"status","checks":{"database"}}` without token (readiness, E-61) | `adapter/in/http/HealthController`, `adapter/out/persistence/JdbcDatabaseCheck` |
| Pool of 10 connections, 5 s wait, `statement_timeout` 5 s, always as `catalog_app` (Anexo C 5.3.10) | `catalog-app/src/main/resources/application.yml` |

## Run locally

```bash
./mvnw -B verify                      # build, unit and HTTP tests (Windows: mvnw.cmd)
cp .env.example .env                  # DATABASE_* of a database where catalog-db is migrated
set -a && . ./.env && set +a && java -jar catalog-app/target/catalog-app-0.0.1-SNAPSHOT.jar
curl -i http://localhost:8080/health/ready
```

## Integration tests

They run against a real PostgreSQL with the schema of `market-agri-catalog-db`, reached through
`TEST_DATABASE_URL` (Anexo C), and are skipped when it is not set. With Docker and catalog-db
cloned next to this repository:

```bash
export "$(bash scripts/test-database.sh)"   # throwaway container on 127.0.0.1:55432, migrated
./mvnw -B verify -Pintegration              # unit, HTTP and *IT
bash scripts/test-database.sh down
```

In CI, the `integration` job does the same on every pull request to `qa`.
