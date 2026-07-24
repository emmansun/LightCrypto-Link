## Context

`lcl-adapter-mongodb-v4` was created as a thin overlay on `lcl-adapter-mongodb`, reusing 11 unchanged classes via a compile-scope dependency with wildcard exclusions for Spring Boot/Data/Framework artifacts. While functional, this approach has proven fragile in practice:

- Maven's dependencyManagement priority (parent > imported BOM) forces every SB4 downstream module to explicitly override `spring-data-commons`, `spring-boot-starter-data-mongodb`, etc.
- The SB3 adapter's `MongoAdapterAutoConfiguration` is on the classpath and must be explicitly excluded via `spring.autoconfigure.exclude`
- The SB4 health indicator was placed in the v4 adapter for lack of a better location, coupling a cross-cutting concern to a specific storage adapter

## Goals

1. Eliminate the v4 → SB3 adapter JAR dependency entirely
2. Make health support adapter-agnostic (provided by starter for any SB3/SB4 application)
3. Preserve full backward compatibility for SB3 users (zero configuration changes)
4. Simplify SB4 application setup (remove exclude/override boilerplate)

## Decisions

### Decision 1: Core module compiles against Spring Data MongoDB 4.x (SB3 baseline)

**Rationale**: The 11 shared classes use `MongoTemplate`, `BeforeConvertCallback`, `AbstractMongoEventListener`, `MappingMongoConverter` — all stable APIs with no breaking changes between Spring Data MongoDB 4.x (SB3) and 5.x (SB4). Compiling against the lower version ensures binary compatibility on both.

**Alternative**: Compile against 5.x — rejected because it would break SB3 runtime (forward-incompatible bytecode references).

### Decision 2: Core module contains exactly 11 classes

Classes moved to `lcl-adapter-mongodb-core` (package `io.github.emmansun.lightcrypto.adapter.mongodb`):

| Class | Responsibility |
|-------|---------------|
| MongoVaultStore | VaultStore impl (MongoTemplate CRUD on `__lcl_keyvault`) |
| MongoEncryptHandler | Field-level encryption orchestration |
| MongoDecryptHandler | Field-level decryption orchestration |
| MongoStorageAdapter | StorageAdapter impl (BSON sub-document format) |
| BsonDocumentAccessor | DocumentAccessor impl (BSON Document traversal) |
| BsonStructuredValueCodec | StructuredValueCodec impl (BSON ↔ structured value) |
| CryptoBeforeSaveListener | BeforeConvertCallback (encrypt on write) |
| MongoCryptoEventListener | AbstractMongoEventListener (decrypt on read) |
| MongoQueryTransformer | QueryTransformer impl (blind-index rewrite) |
| MongoAdapterProperties | @ConfigurationProperties (prefix `lightcrypto.adapters.mongodb`) |
| CryptoMongoQueryCreator | Query creator (Criteria-based blind-index query rewrite) |

**Rationale**: These classes have zero imports from the breaking-change APIs (`QueryMethodEvaluationContextProvider`, `EntityProjection.getDomainType()`, `MongoRepositoryFactoryBean.getFactoryInstance()`).

**Alternative**: Also move query layer classes — rejected because `PartTreeMongoQuery`, `MongoRepositoryFactory` have incompatible constructor/signature changes between 4.x and 5.x.

### Decision 3: Health conditional activation in starter

The starter gains:
- `LclHealthCollector` — pure logic class composing `ComponentHealthCheck` map into `LclHealthStatus` + details map. No Spring Health imports.
- `LclHealthIndicator` (existing, SB3) — thin shell implementing `org.springframework.boot.actuate.health.HealthIndicator`, delegates to collector. Guarded by `@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")`.
- `LclHealthIndicatorV4` (new, SB4) — thin shell implementing `org.springframework.boot.health.contributor.HealthIndicator`, delegates to collector. Guarded by `@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")`.

POM changes: add `spring-boot-health` as `<optional>true</optional>` dependency (for SB4 compilation).

**Rationale**: Both SB3 and SB4 applications get health from the starter alone. Future adapters (JDBC, ES) need zero health code.

**Alternative**: Separate `lcl-health-sb4` module — rejected as over-engineering for 2 thin-shell classes.

### Decision 4: v4 adapter no longer requires spring.autoconfigure.exclude

After refactoring, `lcl-adapter-mongodb-v4` depends on `lcl-adapter-mongodb-core` (not `lcl-adapter-mongodb`). The SB3 `MongoAdapterAutoConfiguration` class is no longer on the classpath, so:
- `spring.autoconfigure.exclude=...MongoAdapterAutoConfiguration` is unnecessary
- `spring.main.allow-bean-definition-overriding=true` is unnecessary
- The v4 adapter's `@ConditionalOnMissingBean` guard can be simplified

**Rationale**: Removing the SB3 JAR from the dependency tree eliminates the root cause rather than patching symptoms.

## Risks

| Risk | Mitigation |
|------|-----------|
| Spring Data MongoDB 5.x introduces subtle runtime incompatibility in MongoTemplate APIs used by core classes | Integration tests on both SB3 and SB4 verify identical behavior; core classes use only basic CRUD/callback APIs |
| Package name unchanged (`io.github.emmansun.lightcrypto.adapter.mongodb`) across 3 JARs may confuse classpath scanning | Spring component scanning is not used (all beans registered via auto-configuration); no ambiguity at runtime |
| SB3 users who directly import `lcl-adapter-mongodb` classes that moved to core | Binary compatible: classes retain same package + name; Maven transitive dependency on core is automatic |
