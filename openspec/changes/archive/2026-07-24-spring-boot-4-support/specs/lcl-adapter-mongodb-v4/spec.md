## ADDED Requirements

### Requirement: V4 adapter module exists and compiles
A new Maven module `lcl-adapter-mongodb-v4` SHALL exist with `spring-boot.version` set to a 4.0.x release, and SHALL compile successfully against Spring Data MongoDB 4.x.

#### Scenario: V4 adapter compiles against Spring Boot 4.x
- **WHEN** the `lcl-adapter-mongodb-v4` module is built
- **THEN** `mvn clean compile` SHALL succeed with zero compilation errors using Spring Boot 4.x and Spring Data MongoDB 4.x

### Requirement: V4 query layer adapted for Spring Data 4.x
The query layer classes in the v4 adapter SHALL replace the removed `QueryMethodEvaluationContextProvider` API with its Spring Data 4.x equivalent (`ValueExpressionDelegate` or `QueryMethodValueEvaluationContextAccessor`).

#### Scenario: CryptoMongoRepositoryFactory compiles without QueryMethodEvaluationContextProvider
- **WHEN** `CryptoMongoRepositoryFactory` in the v4 adapter is compiled
- **THEN** it SHALL NOT import or reference `org.springframework.data.repository.query.QueryMethodEvaluationContextProvider`
- **THEN** it SHALL use the Spring Data MongoDB 4.x `getQueryLookupStrategy()` signature

#### Scenario: CryptoPartTreeMongoQuery compiles without QueryMethodEvaluationContextProvider
- **WHEN** `CryptoPartTreeMongoQuery` in the v4 adapter is compiled
- **THEN** it SHALL NOT import or reference `QueryMethodEvaluationContextProvider`
- **THEN** it SHALL use the Spring Data MongoDB 4.x `super()` constructor signature

#### Scenario: CryptoQueryLookupStrategy compiles without QueryMethodEvaluationContextProvider
- **WHEN** `CryptoQueryLookupStrategy` in the v4 adapter is compiled
- **THEN** it SHALL NOT import or reference `QueryMethodEvaluationContextProvider`
- **THEN** it SHALL pass the correct SB4 SpEL evaluation delegate to `StringBasedMongoQuery` and `StringBasedAggregation`

### Requirement: V4 MongoAutoConfiguration import updated
The `MongoAdapterAutoConfiguration` in the v4 adapter SHALL use the SB4 package path for `MongoAutoConfiguration`.

#### Scenario: MongoAdapterAutoConfiguration compiles on SB4
- **WHEN** `MongoAdapterAutoConfiguration` in the v4 adapter is compiled
- **THEN** it SHALL import `org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration` (not the old `org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration`)

### Requirement: V4 adapter provides identical encryption behavior
The v4 adapter SHALL provide the same encryption, decryption, and blind-index query behavior as the SB3 adapter.

#### Scenario: Encrypted CRUD works identically on SB4
- **WHEN** a document with `@Encrypted` fields is saved and loaded using the v4 adapter on Spring Boot 4.x
- **THEN** fields SHALL be encrypted on save and decrypted on read, producing the same results as the SB3 adapter

#### Scenario: Blind-index queries work identically on SB4
- **WHEN** a repository method-name query involving `@Encrypted(blindIndex = true)` fields is executed using the v4 adapter
- **THEN** the query SHALL be rewritten to a blind-index lookup and return correct results

### Requirement: V4 adapter reuses unchanged classes from SB3 adapter
The v4 adapter module SHALL depend on `lcl-adapter-mongodb` (compile scope) for the 13 unchanged classes (VaultStore, Listener, EncryptHandler, DecryptHandler, StorageAdapter, etc.), overriding only the 4 incompatible files.

#### Scenario: No code duplication for unchanged classes
- **WHEN** inspecting the v4 adapter source directory
- **THEN** it SHALL contain only the 4 adapted files plus auto-configuration registration
- **THEN** all other classes SHALL be resolved from the `lcl-adapter-mongodb` dependency

### Requirement: V4 auto-configuration registration
The v4 adapter SHALL register its auto-configuration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

#### Scenario: AutoConfiguration loads on Spring Boot 4.x
- **WHEN** a Spring Boot 4.x application includes `lcl-adapter-mongodb-v4` on the classpath
- **THEN** the SB4-adapted `MongoAdapterAutoConfiguration` SHALL be loaded

#### Scenario: Mutual exclusion with SB3 adapter
- **WHEN** both `lcl-adapter-mongodb` and `lcl-adapter-mongodb-v4` are on the classpath
- **THEN** only ONE `MongoAdapterAutoConfiguration` SHALL be active (via `@ConditionalOnMissingBean`)

### Requirement: V4 adapter included in build and release
The v4 adapter module SHALL be part of the parent POM reactor build and Maven Central release pipeline.

#### Scenario: V4 adapter builds in reactor
- **WHEN** `mvn clean install` is run from the parent POM
- **THEN** `lcl-adapter-mongodb-v4` SHALL be built along with other modules

#### Scenario: V4 adapter published to Maven Central
- **WHEN** the release workflow runs with `-Pcentral`
- **THEN** `lcl-adapter-mongodb-v4` SHALL be included in the `-pl` list and deployed to Maven Central
