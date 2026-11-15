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
| Token validated **locally**: RS256 only, signature with `JWT_PUBLIC_KEY` (or `JWT_PUBLIC_KEY_FILE`), `iss` = `JWT_ISSUER`, `exp` with 30 s skew, `sub`, known `role`; `401` with `WWW-Authenticate: Bearer` (ADR-020, Anexo C 5.3.7) | `adapter/in/http/Rs256Verifier`, `AuthInterceptor` |
| E-10 `GET /api/v1/catalog/products` (newest first, then id; `page`/`limit` as D-C24, anything else `400`) and E-11 `GET /api/v1/catalog/products/{id}` (`404 PRODUCT_NOT_FOUND`), anonymous, `ProductView` with `priceCents` | `adapter/in/http/ProductController`, `application/usecase/BrowseProductsService`, `adapter/out/persistence/JdbcProductRepository` |
| Routes need a token unless marked `@PublicEndpoint` (cards marked *Anonymous*, which ignore tokens); a `SERVICE` token only on `/internal/**`, a user token only outside it: `403` (D-C27) | `adapter/in/http/AuthInterceptor`, `PublicEndpoint` |
| E-13 `POST /api/v1/catalog/products`, `PRODUCER` only (`@RequiredRole`, other roles `403`): `ProductWrite` → `201` + `Location`, or `200` with the original on a repeated `Idempotency-Key` (8–128, per producer, D-C35); `422 PRICE_NOT_POSITIVE` / `QUANTITY_NEGATIVE`; `municipality_key` normalized by the API (D-16); `priceCents` with decimals is `400` | `adapter/in/http/ProductController`, `application/usecase/PublishProductService`, `domain/model/MunicipalityKey`, `adapter/out/persistence/JdbcProductPublications` |
| Internal API, `SERVICE` token only: I-02 `GET /internal/v1/catalog/products/{id}` (`ProductView`); I-03 `POST …/products/{id}/reservations` — atomic and idempotent on `transactionId` (`201`, `200` on a retry, `409 RESERVATION_MISMATCH` / `PRODUCT_NOT_AVAILABLE` / `INSUFFICIENT_STOCK`, `422 QUANTITY_NOT_POSITIVE`), `ReservationView` with `unitPriceCents`; I-04 `DELETE …/reservations/{transactionId}` — always `204`, gives the stock back once | `adapter/in/http/InternalCatalogController`, `application/usecase/StockReservationService` (the product row locked with `SELECT … FOR UPDATE`), `adapter/out/persistence/JdbcReservationRepository` |
| E-16 `POST /api/v1/catalog/products/{id}/photo` (multipart part `file`, JPG/PNG/WebP by signature, up to 5 MB: `413`, `415`) and E-17 `DELETE …/photo` (`204`), owner only (`404` before `403 NOT_PRODUCT_OWNER`); the key is a random UUID plus the extension, the previous binary deleted after the commit. E-62 `GET /media/{key}`, anonymous, `Cache-Control: public, max-age=86400`. Binaries in `MEDIA_DIR` (volume `catalog-media`) | `adapter/in/http/ProductPhotoController`, `application/usecase/ProductPhotoService`, `domain/model/PhotoKey`, `ImageFormat`, `adapter/out/storage/FileSystemPhotoStorage` |

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
