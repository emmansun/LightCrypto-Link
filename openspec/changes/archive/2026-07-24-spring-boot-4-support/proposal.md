## Why

Spring Boot 4.0 (Spring Framework 7, Spring Data 2025.1) introduces **three breaking API changes** that prevent LCL from compiling under SB4:

1. **`QueryMethodEvaluationContextProvider` removed** from `org.springframework.data.repository.query` (spring-data-commons 4.0.x) — used by LCL's query layer in `lcl-adapter-mongodb` (3 files)
2. **`MongoAutoConfiguration` package moved** from `org.springframework.boot.autoconfigure.mongo` to `org.springframework.boot.mongodb.autoconfigure` — used by `MongoAdapterAutoConfiguration` (1 file)
3. **Health classes package moved** from `org.springframework.boot.actuate.health` to `org.springframework.boot.health.contributor` — used by `LclHealthIndicator` and `ObservabilityAutoConfiguration` in `lcl-spring-boot-starter` (2 files)

Since these APIs have fundamentally different package names between SB3 and SB4, the same source code cannot compile against both versions. A dual-module approach is required for the adapter layer, and conditional imports for the health layer.

## What Changes

- Create new module `lcl-adapter-mongodb-v4` — a fork of `lcl-adapter-mongodb` with adapted query layer classes (replacing `QueryMethodEvaluationContextProvider` with `ValueExpressionDelegate`/`QueryMethodValueEvaluationContextAccessor`) and updated `MongoAutoConfiguration` import
- Update `lcl-spring-boot-starter` to conditionally reference Health classes (SB3 vs SB4 package) via a thin abstraction or dual source sets
- Add `lcl-adapter-mongodb-v4` to parent POM reactor build and release pipeline
- Add CI build matrix entry for Spring Boot 4.x
- Existing `lcl-adapter-mongodb` and `lcl-spring-boot-starter` remain unchanged for SB3 users

## Capabilities

### New Capabilities
- `lcl-adapter-mongodb-v4`: A Spring Boot 4.x-compatible MongoDB adapter module with adapted query layer (`CryptoMongoRepositoryFactory`, `CryptoPartTreeMongoQuery`, `CryptoQueryLookupStrategy`) using the new SpEL evaluation APIs, and updated `MongoAdapterAutoConfiguration` import path
- `starter-health-sb4-compat`: The starter's health indicator support works on both SB3 (`org.springframework.boot.actuate.health`) and SB4 (`org.springframework.boot.health.contributor`)

### Modified Capabilities
<!-- No existing spec-level behavior changes. Encryption semantics, blind index, and KMS integration remain identical. -->

## Impact

- **New module**: `lcl-adapter-mongodb-v4` with its own `pom.xml`, source directory, and auto-configuration registration
- **Starter**: Health-related classes need conditional compilation or a thin abstraction layer for cross-version compatibility
- **Parent POM**: Add v4 adapter module to `<modules>`, `<dependencyManagement>`, and Central release `-pl` list
- **CI/CD**: Add SB4 build matrix row; release workflow includes v4 module in Central deploy
- **KMS modules**: NO changes needed — they already only depend on `lcl-spi`
- **Users**: SB3 users continue with `lcl-adapter-mongodb`; SB4 users switch to `lcl-adapter-mongodb-v4`
