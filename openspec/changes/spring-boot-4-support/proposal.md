## Why

Spring Boot 4.0 (based on Spring Framework 7) introduces breaking API changes in Spring Data MongoDB 4.x. Specifically, `QueryMethodEvaluationContextProvider` was removed from `org.springframework.data.repository.query`, which LCL's query layer (`CryptoMongoRepositoryFactory`, `CryptoPartTreeMongoQuery`, `CryptoQueryLookupStrategy`) deeply depends on. Since this API cannot be conditionally adapted within the same codebase, LCL needs a separate starter module for SB4 users.

## What Changes

- Create new module `lightcrypto-link-spring-boot-starter-v4` — a fork of the starter adapted for Spring Data MongoDB 4.x APIs (replacing `QueryMethodEvaluationContextProvider` and updating `PartTreeMongoQuery` constructor signatures)
- Change KMS modules (`azure-kms`, `alibaba-kms`) to declare `<scope>provided</scope>` on the starter dependency, so they don't transitively pull in the SB3 starter for SB4 users
- Add the v4 starter module to the parent POM reactor build and release pipeline (`-pl` list)
- Add CI build matrix entry for Spring Boot 4.x using the v4 starter
- Existing `lightcrypto-link-spring-boot-starter` remains unchanged for SB3 users

## Capabilities

### New Capabilities
- `spring-boot-4-starter-v4`: A Spring Boot 4.x-compatible starter module with adapted query layer (`CryptoMongoRepositoryFactory`, `CryptoPartTreeMongoQuery`, `CryptoQueryLookupStrategy`) that replaces the removed `QueryMethodEvaluationContextProvider` API
- `kms-provided-scope`: KMS modules decouple from the starter via `<scope>provided</scope>`, allowing SB4 users to depend on `starter-v4` without classpath conflicts

### Modified Capabilities
<!-- No existing spec-level behavior changes. Encryption semantics, blind index, and KMS integration remain identical. -->

## Impact

- **New module**: `lightcrypto-link-spring-boot-starter-v4` with its own `pom.xml`, source directory, and auto-configuration registration
- **KMS modules**: `lightcrypto-link-azure-kms/pom.xml` and `lightcrypto-link-alibaba-kms/pom.xml` — starter dependency scope changes to `provided`
- **Parent POM**: Add v4 module to `<modules>`, `<dependencyManagement>`, and Central release `-pl` list
- **CI/CD**: Add SB4 build matrix row; release workflow includes v4 module in Central deploy
- **Users**: SB3 users continue with `lightcrypto-link-spring-boot-starter`; SB4 users switch to `lightcrypto-link-spring-boot-starter-v4`
