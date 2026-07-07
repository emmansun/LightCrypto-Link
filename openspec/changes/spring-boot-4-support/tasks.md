## 1. V4 Starter Module Skeleton

- [ ] 1.1 Create `lightcrypto-link-spring-boot-starter-v4/pom.xml` with `spring-boot.version=4.x`, Spring Data MongoDB 4.x, and compile dependency on the SB3 starter for shared classes
- [ ] 1.2 Add `lightcrypto-link-spring-boot-starter-v4` to parent POM `<modules>` list
- [ ] 1.3 Add `lightcrypto-link-spring-boot-starter-v4` to parent POM `<dependencyManagement>`
- [ ] 1.4 Create `src/main/java/` directory structure mirroring the SB3 starter's package layout

## 2. Query Layer Adaptation

- [ ] 2.1 Research Spring Data MongoDB 4.x replacement for `QueryMethodEvaluationContextProvider` — identify the new API signature in `PartTreeMongoQuery`, `MongoRepositoryFactory.getQueryLookupStrategy()`, `StringBasedMongoQuery` constructors
- [ ] 2.2 Create SB4-adapted `CryptoMongoRepositoryFactory` in the v4 module — override `getQueryLookupStrategy()` with the new SB4 method signature
- [ ] 2.3 Create SB4-adapted `CryptoPartTreeMongoQuery` in the v4 module — update `super()` constructor call to match SB4's `PartTreeMongoQuery` signature
- [ ] 2.4 Create SB4-adapted `CryptoQueryLookupStrategy` in the v4 module — replace `QueryMethodEvaluationContextProvider` with SB4 equivalent, update `StringBasedMongoQuery` and `StringBasedAggregation` constructor calls
- [ ] 2.5 Verify `CryptoMongoQueryCreator` compiles against SB4 (may not need changes if it doesn't reference removed APIs)
- [ ] 2.6 Verify `CryptoMongoRepositoryFactoryBean` compiles against SB4

## 3. Auto-Configuration and Listener Adaptation

- [ ] 3.1 Review `LightCryptoLinkAutoConfiguration` for SB4 compatibility — check `@EnableMongoRepositories`, `MappingMongoConverter` bean factory method
- [ ] 3.2 If needed, create SB4-adapted `LightCryptoLinkAutoConfiguration` in the v4 module
- [ ] 3.3 Create `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` for the v4 module
- [ ] 3.4 Review `CryptoMappingMongoConverter` for SB4 `MappingMongoConverter` API changes
- [ ] 3.5 Review `CryptoBeforeSaveListener` and `EntityMetadataCache` for SB4 compatibility (likely no changes needed — they use Spring Data MongoDB event APIs)

## 4. KMS Modules — Provided Scope

- [ ] 4.1 Change `lightcrypto-link-azure-kms/pom.xml` — add `<scope>provided</scope>` to `lightcrypto-link-spring-boot-starter` dependency
- [ ] 4.2 Change `lightcrypto-link-alibaba-kms/pom.xml` — add `<scope>provided</scope>` to `lightcrypto-link-spring-boot-starter` dependency
- [ ] 4.3 Verify KMS modules still compile with `provided` scope
- [ ] 4.4 Verify existing example POMs already explicitly declare the starter dependency (they should)

## 5. Testing

- [ ] 5.1 Determine SB4 integration test strategy — evaluate if `de.flapdoodle.embed.mongo.spring3x` works with SB4, or use Testcontainers MongoDB
- [ ] 5.2 Create SB4-compatible integration test setup in the v4 module
- [ ] 5.3 Run v4 starter tests (`mvn -pl lightcrypto-link-spring-boot-starter-v4 clean verify`) — fix any failures
- [ ] 5.4 Run SB3 starter tests (`mvn -pl lightcrypto-link-spring-boot-starter clean verify`) — confirm no regressions
- [ ] 5.5 Run full reactor build (`mvn clean verify`) — confirm all modules pass

## 6. CI/CD and Release

- [ ] 6.1 Add Spring Boot 4.x build row to CI build matrix (`.github/workflows/build.yml`)
- [ ] 6.2 Add `lightcrypto-link-spring-boot-starter-v4` to the release workflow's `-pl` list (`.github/workflows/release.yml`)
- [ ] 6.3 Verify Maven Central publishing includes the v4 module
- [ ] 6.4 Verify GitHub Packages publishing includes the v4 module

## 7. Documentation

- [ ] 7.1 Update README to document dual starter support (SB3 vs SB4) and migration guide
- [ ] 7.2 Add note about explicit starter dependency requirement when using KMS modules
## 1. API Compatibility Audit

- [ ] 1.1 Audit `MappingMongoConverter` API usage in `CryptoMappingMongoConverter` against Spring Data MongoDB 4.x — check for removed/changed methods (e.g., `read()`, `write()`, `getConversionService()`)
- [ ] 1.2 Audit `MongoDatabaseFactory` and `MongoTemplate` API usage across all modules
- [ ] 1.3 Audit `@EnableMongoRepositories` and `MongoRepositoryFactoryBean` API in `CryptoMongoRepositoryFactoryBean`
- [ ] 1.4 Audit `@AutoConfiguration`, `@ConditionalOnProperty`, `@EnableConfigurationProperties` annotations for any behavioral changes in SB4
- [ ] 1.5 Audit `ApplicationContextRunner` test utilities for SB4 compatibility

## 2. Dependency Upgrade

- [ ] 2.1 Update `spring-boot.version` property in parent POM to 4.0.0 (or latest available 4.x)
- [ ] 2.2 Evaluate Flapdoodle `de.flapdoodle.embed.mongo.spring3x` compatibility with SB4 — upgrade to `spring4x` variant or migrate to Testcontainers MongoDB if needed
- [ ] 2.3 Verify Bouncy Castle `bcprov-jdk18on` / `bcpkix-jdk18on` compatibility with Spring Boot 4.x's minimum JDK requirement
- [ ] 2.4 Update Azure SDK and Alibaba KMS SDK dependencies if they conflict with Spring Framework 7

## 3. Code Changes

- [ ] 3.1 Fix any compilation errors found during the API audit
- [ ] 3.2 Replace any deprecated-and-removed API calls with their SB4 equivalents
- [ ] 3.3 Update `CryptoMappingMongoConverter` if `MappingMongoConverter` constructor or method signatures changed in Spring Data MongoDB 4.x
- [ ] 3.4 Update auto-configuration registration if `AutoConfiguration.imports` mechanism changed

## 4. Testing

- [ ] 4.1 Run full test suite (`mvn clean verify`) with Spring Boot 4.x — fix any failures
- [ ] 4.2 Run full test suite with Spring Boot 3.x — confirm no regressions
- [ ] 4.3 Verify `AutoConfigurationTest` passes on SB4 (context runner, bean registration, conditional properties)
- [ ] 4.4 Verify integration tests pass on SB4 (encrypted CRUD, blind index query)
- [ ] 4.5 Verify example modules (basic-crud, alibaba-kms, azure-keyvault) compile and run on SB4

## 5. CI/CD

- [ ] 5.1 Add Spring Boot 4.x to CI build matrix (parameterize `spring-boot.version`)
- [ ] 5.2 Ensure release workflow works with Spring Boot 4.x as default version
- [ ] 5.3 Verify Maven Central publishing works with SB4-built artifacts

## 6. Documentation

- [ ] 6.1 Update README to mention Spring Boot 3.x and 4.x support
- [ ] 6.2 Update example `application.yml` files if any SB4-specific properties changed
