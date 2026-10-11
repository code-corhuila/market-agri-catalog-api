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

## Run locally

```bash
./mvnw -B verify                      # build and tests (Windows: mvnw.cmd)
java -jar catalog-app/target/catalog-app-0.0.1-SNAPSHOT.jar
curl -i http://localhost:8080/health  # liveness, no token
```
