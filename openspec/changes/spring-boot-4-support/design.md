## Context

LightCrypto-Link currently depends on Spring Boot 3.2.5 (Spring Framework 6.x, Jakarta EE 9/10). Spring Boot 4.0 is based on Spring Framework 7 and introduces **breaking API changes** in Spring Data MongoDB 4.x:

- `QueryMethodEvaluationContextProvider` was removed from `org.springframework.data.repository.query`
- This class is deeply used in LCL's query layer: `CryptoMongoRepositoryFactory`, `CryptoPartTreeMongoQuery`, `CryptoQueryLookupStrategy`
- The removal is not a simple rename — the entire SpEL evaluation context mechanism was restructured

Since the same code cannot compile against both SB3 and SB4, a dual-module approach is required.

The project also uses:
- `@AutoConfiguration` for auto-config registration
- `@EnableMongoRepositories` + `MappingMongoConverter` for MongoDB integration
- `@EnableConfigurationProperties` + `@ConditionalOnProperty` for conditional beans
- `de.flapdoodle.embed.mongo.spring3x` for embedded MongoDB in tests
- `org.springframework.boot.autoconfigure.AutoConfiguration.imports` for discovery

## Goals / Non-Goals

**Goals:**
- Provide SB4 users with a fully functional `lightcrypto-link-spring-boot-starter-v4` module
- Maintain existing `lightcrypto-link-spring-boot-starter` unchanged for SB3 users
- Decouple KMS modules from the starter so they work with either version
- Ensure both starters pass their respective test suites (SB3 and SB4)

**Non-Goals:**
- Adopting new Spring Boot 4.x-only features (virtual threads, GraalVM native image) — future work
- Changing LCL's public API or encryption semantics (v4 starter provides identical behavior)
- Dropping Spring Boot 3.x support

## Decisions

### 1. Dual-module approach (not single codebase with Maven profile)

**Decision**: Create `lightcrypto-link-spring-boot-starter-v4` as a separate Maven module that contains the SB4-adapted query layer code.

**Rationale**: `QueryMethodEvaluationContextProvider` was removed in Spring Data Commons 4.x. The query layer classes (`CryptoMongoRepositoryFactory`, `CryptoPartTreeMongoQuery`, `CryptoQueryLookupStrategy`) have this type in their method signatures and constructor parameters. A single codebase cannot conditionally compile against two incompatible APIs.

**Alternative considered**: Maven profile with `-Dspring-boot.version=4.x` to switch versions — rejected because the compile-time API signatures are fundamentally different.

### 2. V4 module structure — fork of starter with adapted query classes

**Decision**: The v4 module has its own:
- `pom.xml` with `spring-boot.version=4.x` and Spring Data MongoDB 4.x
- `src/main/java/` containing adapted query layer classes (same Java package `io.github.emmansun.lightcrypto`)
- Shared classes (codec, annotation, model, provider, listener, service, config) are **duplicated or re-exported** via a shared dependency on the SB3 starter's non-query classes

**Rationale**: The v4 module only needs to adapt the query layer (~5 files). All other classes (CryptoCodec, TypeSerializer, annotations, providers, listeners, services, config) are Spring-version-agnostic. The v4 module can depend on the SB3 starter as a **compile dependency** for these shared classes, but override the query layer with SB4-compatible versions.

**Alternative considered**: Extract shared classes into a `lightcrypto-link-core` module — rejected as too much refactoring for this change. Can be done later.

### 3. KMS modules: `<scope>provided</scope>` on starter dependency

**Decision**: Change `lightcrypto-link-azure-kms` and `lightcrypto-link-alibaba-kms` to declare `<scope>provided</scope>` on `lightcrypto-link-spring-boot-starter`.

**Rationale**: KMS modules only use the `CmkProvider` SPI interface (a plain Java interface). They don't need the starter transitively. With `provided`, KMS modules compile against the starter but don't force SB3 starter onto the user's classpath. SB4 users explicitly add `starter-v4`, SB3 users add `starter`.

**Risk**: Users who previously only added a KMS module (relying on transitive starter) will now need to add the starter explicitly. This is a **minor breaking change** for KMS users, but the correct practice anyway (explicit dependencies are better).

### 4. Flapdoodle / Testcontainers for SB4 integration tests

**Decision**: The v4 starter module will evaluate test infrastructure. If `de.flapdoodle.embed.mongo.spring3x` doesn't work with SB4, use Testcontainers MongoDB.

**Rationale**: Flapdoodle's `spring3x` targets SB 3.x auto-configuration. SB 4.x may require a new artifact or Testcontainers.

### 5. Auto-configuration registration

**Decision**: V4 starter uses its own `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file, registering an SB4-adapted `LightCryptoLinkAutoConfiguration`.

## Risks / Trade-offs

- **[Shared class duplication]** → Mitigation: V4 module depends on SB3 starter for non-query classes. If this causes classpath issues, extract `lightcrypto-link-core` later.
- **[KMS provided scope is a minor breaking change]** → Mitigation: Document that users must explicitly add starter dependency. Add migration note to README.
- **[Flapdoodle SB4 support may lag]** → Mitigation: Use Testcontainers MongoDB as fallback for v4 integration tests.
- **[Bouncy Castle + Jakarta EE 11]** → Mitigation: Bouncy Castle `bcprov-jdk18on` targets JDK 18+, should be compatible. Verify during implementation.
- **[Two auto-configurations on classpath if user adds both starters]** → Mitigation: Document that users must choose ONE starter. Consider `@ConditionalOnMissingBean` guards.
