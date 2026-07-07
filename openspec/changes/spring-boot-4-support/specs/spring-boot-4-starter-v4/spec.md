## ADDED Requirements

### Requirement: V4 starter module exists and compiles
A new Maven module `lightcrypto-link-spring-boot-starter-v4` SHALL exist with `spring-boot.version` set to a 4.x release, and SHALL compile successfully against Spring Data MongoDB 4.x.

#### Scenario: V4 module compiles against Spring Boot 4.x
- **WHEN** the `lightcrypto-link-spring-boot-starter-v4` module is built
- **THEN** `mvn clean compile` SHALL succeed with zero compilation errors using Spring Boot 4.x and Spring Data MongoDB 4.x

### Requirement: V4 query layer adapted for Spring Data MongoDB 4.x
The query layer classes in the v4 starter SHALL be adapted to replace the removed `QueryMethodEvaluationContextProvider` API with its Spring Data 4.x equivalent.

#### Scenario: CryptoMongoRepositoryFactory compiles without QueryMethodEvaluationContextProvider
- **WHEN** `CryptoMongoRepositoryFactory` in the v4 module is compiled
- **THEN** it SHALL NOT import or reference `QueryMethodEvaluationContextProvider`
- **THEN** it SHALL use the Spring Data MongoDB 4.x equivalent API

#### Scenario: CryptoPartTreeMongoQuery compiles without QueryMethodEvaluationContextProvider
- **WHEN** `CryptoPartTreeMongoQuery` in the v4 module is compiled
- **THEN** it SHALL NOT import or reference `QueryMethodEvaluationContextProvider`
- **THEN** it SHALL use the Spring Data MongoDB 4.x constructor signature

#### Scenario: CryptoQueryLookupStrategy compiles without QueryMethodEvaluationContextProvider
- **WHEN** `CryptoQueryLookupStrategy` in the v4 module is compiled
- **THEN** it SHALL NOT import or reference `QueryMethodEvaluationContextProvider`
- **THEN** it SHALL pass the correct SB4 evaluation context to `StringBasedMongoQuery` and `StringBasedAggregation`

### Requirement: V4 starter provides identical encryption behavior
The v4 starter SHALL provide the same encryption, decryption, and blind-index query behavior as the SB3 starter.

#### Scenario: Encrypted CRUD works identically on SB4
- **WHEN** a document with `@Encrypted` fields is saved and loaded using the v4 starter on Spring Boot 4.x
- **THEN** fields SHALL be encrypted on save and decrypted on read, producing the same results as the SB3 starter

#### Scenario: Blind-index queries work identically on SB4
- **WHEN** a repository method-name query involving `@Encrypted(blindIndex = true)` fields is executed using the v4 starter
- **THEN** the query SHALL be rewritten to a blind-index lookup and return correct results

### Requirement: V4 auto-configuration registration
The v4 starter SHALL register its auto-configuration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

#### Scenario: AutoConfiguration loads on Spring Boot 4.x
- **WHEN** a Spring Boot 4.x application includes `lightcrypto-link-spring-boot-starter-v4` on the classpath
- **THEN** the SB4-adapted `LightCryptoLinkAutoConfiguration` SHALL be loaded and all beans registered

### Requirement: V4 starter included in build and release
The v4 starter module SHALL be part of the parent POM reactor build and Maven Central release pipeline.

#### Scenario: V4 module builds in reactor
- **WHEN** `mvn clean install` is run from the parent POM
- **THEN** `lightcrypto-link-spring-boot-starter-v4` SHALL be built along with other modules

#### Scenario: V4 module published to Maven Central
- **WHEN** the release workflow runs with `-Pcentral`
- **THEN** `lightcrypto-link-spring-boot-starter-v4` SHALL be included in the `-pl` list and deployed to Maven Central
## ADDED Requirements

### Requirement: Spring Boot 4.x compilation compatibility
The project SHALL compile successfully against Spring Boot 4.x (Spring Framework 7) without errors. All Spring Boot and Spring Data MongoDB APIs used in LCL SHALL be compatible with their 4.x counterparts.

#### Scenario: Project compiles with Spring Boot 4.x
- **WHEN** the project is built with `spring-boot.version` set to a 4.x release
- **THEN** `mvn clean compile` SHALL succeed with zero compilation errors

#### Scenario: No usage of APIs removed in Spring Boot 4.x
- **WHEN** auditing all Spring Boot and Spring Data MongoDB API usage across LCL modules
- **THEN** no deprecated-and-removed APIs SHALL be present (e.g., removed overloads, deleted classes)

### Requirement: Auto-configuration registration on Spring Boot 4.x
The LCL auto-configuration SHALL be discoverable and loadable by Spring Boot 4.x's auto-configuration mechanism.

#### Scenario: AutoConfiguration loads on Spring Boot 4.x
- **WHEN** a Spring Boot 4.x application includes LCL on the classpath
- **THEN** `LightCryptoLinkAutoConfiguration` SHALL be loaded and all beans registered

#### Scenario: AutoConfiguration.imports file is recognized
- **WHEN** Spring Boot 4.x scans for auto-configurations
- **THEN** the `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file SHALL be processed correctly

### Requirement: MongoDB integration on Spring Data MongoDB 4.x
The custom `MappingMongoConverter` and repository factory SHALL function correctly with Spring Data MongoDB 4.x.

#### Scenario: CryptoMappingMongoConverter works with Spring Data MongoDB 4.x
- **WHEN** a document with `@Encrypted` fields is read from MongoDB using Spring Data MongoDB 4.x
- **THEN** the encrypted fields SHALL be decrypted correctly

#### Scenario: CryptoMongoRepositoryFactoryBean works with Spring Data MongoDB 4.x
- **WHEN** a repository query involving blind-index encrypted fields is executed on Spring Data MongoDB 4.x
- **THEN** the query SHALL return correct results with transparent decryption

### Requirement: Integration test infrastructure on Spring Boot 4.x
Integration tests SHALL execute successfully against Spring Boot 4.x with a working embedded or containerized MongoDB.

#### Scenario: Integration tests pass on Spring Boot 4.x
- **WHEN** integration tests are run with Spring Boot 4.x on the classpath
- **THEN** all tests SHALL pass (embedded MongoDB or Testcontainers provides the MongoDB instance)

### Requirement: Backward compatibility with Spring Boot 3.x
The project SHALL continue to compile and pass all tests against Spring Boot 3.x after the 4.x compatibility changes.

#### Scenario: Project still works with Spring Boot 3.x
- **WHEN** the project is built with `spring-boot.version` set to a 3.x release (e.g., 3.2.5)
- **THEN** `mvn clean verify` SHALL succeed with all tests passing

### Requirement: CI validation across Spring Boot versions
The CI pipeline SHALL validate the project against both Spring Boot 3.x and 4.x.

#### Scenario: CI tests Spring Boot 3.x
- **WHEN** the CI pipeline runs with Spring Boot 3.x
- **THEN** build, test, and verify phases SHALL succeed

#### Scenario: CI tests Spring Boot 4.x
- **WHEN** the CI pipeline runs with Spring Boot 4.x
- **THEN** build, test, and verify phases SHALL succeed
