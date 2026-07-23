## Context

LightCrypto-Link currently depends on Spring Boot 3.2.5 (Spring Framework 6.x, Jakarta EE 9/10). Spring Boot 4.0 is based on Spring Framework 7 and Spring Data 2025.1, introducing three categories of breaking changes:

**1. Spring Data Commons API Removal (lcl-adapter-mongodb)**

`QueryMethodEvaluationContextProvider` was removed from `org.springframework.data.repository.query` in spring-data-commons 4.0.x. This interface was used in three query layer classes:
- `CryptoMongoRepositoryFactory` — `getQueryLookupStrategy()` method signature
- `CryptoPartTreeMongoQuery` — constructor parameter and `super()` call
- `CryptoQueryLookupStrategy` — constructor parameter and passed to `StringBasedMongoQuery`/`StringBasedAggregation`

The replacement in Spring Data MongoDB 4.x is `ValueExpressionDelegate` (for query classes) or `QueryMethodValueEvaluationContextAccessor` (for factory/lookup strategy).

**2. Spring Boot Module Package Restructuring (lcl-adapter-mongodb + lcl-spring-boot-starter)**

Spring Boot 4.0 modularized its codebase with new package conventions:
- `org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration` → `org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration`
- `org.springframework.boot.actuate.health.{HealthIndicator, Health, Status}` → `org.springframework.boot.health.contributor.{HealthIndicator, Health, Status}`
- New artifacts: `spring-boot-health` (was part of `spring-boot-actuator`)

**3. Verified Non-Issues**

Compilation testing with SB 4.0.7 confirmed:
- `lcl-spi` (pure interfaces) — compiles unchanged
- `lcl-core` (pure crypto, Bouncy Castle only) — compiles unchanged
- `lcl-provider-azure-kms` / `lcl-provider-alibaba-kms` (depend only on `lcl-spi`) — no changes needed
- KMS modules already don't depend on the starter (they depend on `lcl-spi` only)
- `@AutoConfiguration`, `@ConditionalOnProperty`, `@EnableConfigurationProperties` — stable across SB3/SB4
- `MappingMongoConverter`, `MongoTemplate`, `MongoDatabaseFactory` APIs — unchanged

## Goals / Non-Goals

**Goals:**
- Provide SB4 users with a fully functional `lcl-adapter-mongodb-v4` module
- Make `lcl-spring-boot-starter` health indicators work on both SB3 and SB4
- Maintain existing modules unchanged for SB3 users
- Ensure both adapter versions pass their respective test suites

**Non-Goals:**
- Adopting new Spring Boot 4.x-only features (virtual threads, GraalVM native image)
- Changing LCL's public API or encryption semantics
- Dropping Spring Boot 3.x support
- Migrating to `ValueExpressionDelegate` before required (deprecation is in 4.4, not removal)

## Decisions

### 1. Fork `lcl-adapter-mongodb` (not the starter)

**Decision**: Create `lcl-adapter-mongodb-v4` as a separate Maven module.

**Rationale**: The breaking API changes are concentrated in the adapter layer (query classes + auto-config import). The starter is mostly SB-version-agnostic except for 2 health-related files.

**Alternative considered**: Fork the entire starter — rejected because the starter contains 38 files and only 2 need changes, vs the adapter's 5 files that all need changes.

### 2. V4 adapter module structure

**Decision**: The v4 adapter module has its own:
- `pom.xml` with `spring-boot.version=4.0.x` and `spring-boot-starter-data-mongodb` 4.x
- `src/main/java/` containing only the 4 adapted files:
  - `CryptoMongoRepositoryFactory.java` — new `getQueryLookupStrategy()` signature
  - `CryptoPartTreeMongoQuery.java` — new `super()` constructor
  - `CryptoQueryLookupStrategy.java` — `ValueExpressionDelegate` instead of `QueryMethodEvaluationContextProvider`
  - `MongoAdapterAutoConfiguration.java` — updated `MongoAutoConfiguration` import
- Shared classes (VaultStore, Listener, EncryptHandler, DecryptHandler, etc.) are accessed via compile dependency on `lcl-adapter-mongodb`

**Rationale**: Only 4 of 17 adapter files need adaptation. The v4 module depends on the SB3 adapter for the 13 unchanged classes, overriding only the 4 incompatible ones.

### 3. Health classes — thin wrapper or conditional source sets

**Decision**: Create a `HealthClasses` abstraction in the starter that isolates the SB3/SB4 package difference. Two options:
- **Option A**: Extract health-related code into a `LclHealthAdapter` interface with SB3/SB4 implementations
- **Option B**: Use Maven resource filtering or annotation processing to select the correct import at build time

**Recommendation**: Option A (thin interface abstraction) — cleaner, testable, no build magic.

### 4. No KMS module changes

**Decision**: KMS provider modules (`lcl-provider-azure-kms`, `lcl-provider-alibaba-kms`) require zero changes.

**Rationale**: They depend only on `lcl-spi` (pure Java interfaces), not on the starter or any Spring Boot-specific code. The original proposal's `provided` scope change is unnecessary.

### 5. Auto-configuration registration

**Decision**: V4 adapter uses its own `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, registering `MongoAdapterAutoConfiguration` (with the SB4-correct import path). The v4 adapter's auto-config must exclude the SB3 adapter's auto-config if both are on classpath.

## Risks / Trade-offs

- **[Classpath conflict if user adds both adapters]** → Mitigation: Document that users must choose ONE adapter. Add `@ConditionalOnMissingBean` guards on `MongoAdapterAutoConfiguration`.
- **[Health abstraction adds complexity]** → Mitigation: The interface is minimal (3 methods). Health is optional (`spring-boot-actuator` is already `<optional>true</optional>`).
- **[Flapdoodle SB4 support may lag]** → Mitigation: Use Testcontainers MongoDB as fallback for v4 integration tests.
- **[spring-data-commons version drift]** → Mitigation: The `spring-data-commons.version` property in parent POM allows the sb4 profile to override cleanly.
