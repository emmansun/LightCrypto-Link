## MODIFIED Requirements

### Requirement: V4 adapter reuses unchanged classes from shared core module
The v4 adapter module SHALL depend on `lcl-adapter-mongodb-core` (compile scope) for the 11 version-independent shared classes (VaultStore, Listener, EncryptHandler, DecryptHandler, StorageAdapter, QueryTransformer, QueryCreator, Properties, DocumentAccessor, StructuredValueCodec, EventListener). The v4 adapter SHALL NOT depend on `lcl-adapter-mongodb`.

#### Scenario: No dependency on SB3 adapter JAR
- **WHEN** inspecting the `lcl-adapter-mongodb-v4` POM
- **THEN** it SHALL declare a compile dependency on `lcl-adapter-mongodb-core`
- **THEN** it SHALL NOT declare any dependency on `lcl-adapter-mongodb`

#### Scenario: No wildcard exclusions needed
- **WHEN** inspecting the `lcl-adapter-mongodb-v4` POM dependency on `lcl-adapter-mongodb-core`
- **THEN** it SHALL have zero `<exclusion>` elements (no Spring version conflicts to manage)

#### Scenario: V4 adapter source contains only query layer and auto-configuration
- **WHEN** inspecting the v4 adapter source directory
- **THEN** it SHALL contain only: `CryptoMappingMongoConverter`, `CryptoMongoRepositoryFactory`, `CryptoMongoRepositoryFactoryBean`, `CryptoPartTreeMongoQuery`, `CryptoQueryLookupStrategy`, `MongoAdapterV4AutoConfiguration`
- **THEN** it SHALL NOT contain any `health/` package or health-related classes

### Requirement: V4 adapter provides identical encryption behavior
The v4 adapter SHALL provide the same encryption, decryption, and blind-index query behavior as the SB3 adapter.

#### Scenario: Encrypted CRUD works identically on SB4
- **WHEN** a document with `@Encrypted` fields is saved and loaded using the v4 adapter on Spring Boot 4.x
- **THEN** fields SHALL be encrypted on save and decrypted on read, producing the same results as the SB3 adapter

#### Scenario: Blind-index queries work identically on SB4
- **WHEN** a repository method-name query involving `@Encrypted(blindIndex = true)` fields is executed using the v4 adapter
- **THEN** the query SHALL be rewritten to a blind-index lookup and return correct results

### Requirement: V4 application requires no special exclusion configuration
A Spring Boot 4.x application using `lcl-adapter-mongodb-v4` SHALL NOT need `spring.autoconfigure.exclude` or `spring.main.allow-bean-definition-overriding` properties.

#### Scenario: SB4 application starts without exclude configuration
- **WHEN** a Spring Boot 4.x application includes `lcl-adapter-mongodb-v4` without any `spring.autoconfigure.exclude` property
- **THEN** the application SHALL start successfully with only the v4 auto-configuration active

#### Scenario: No SB3 auto-configuration on classpath
- **WHEN** inspecting the runtime classpath of an application depending only on `lcl-adapter-mongodb-v4`
- **THEN** `MongoAdapterAutoConfiguration` (SB3) SHALL NOT be present

### Requirement: V4 adapter module exists and compiles
A Maven module `lcl-adapter-mongodb-v4` SHALL exist with `spring-boot.version` set to a 4.0.x release, and SHALL compile successfully against Spring Data MongoDB 5.x.

#### Scenario: V4 adapter compiles against Spring Boot 4.x
- **WHEN** the `lcl-adapter-mongodb-v4` module is built
- **THEN** `mvn clean compile` SHALL succeed with zero compilation errors using Spring Boot 4.x and Spring Data MongoDB 5.x

### Requirement: V4 auto-configuration registration
The v4 adapter SHALL register its auto-configuration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

#### Scenario: AutoConfiguration loads on Spring Boot 4.x
- **WHEN** a Spring Boot 4.x application includes `lcl-adapter-mongodb-v4` on the classpath
- **THEN** the SB4-adapted `MongoAdapterV4AutoConfiguration` SHALL be loaded

### Requirement: V4 adapter included in build and release
The v4 adapter module SHALL be part of the parent POM reactor build and Maven Central release pipeline.

#### Scenario: V4 adapter builds in reactor
- **WHEN** `mvn clean install` is run from the parent POM
- **THEN** `lcl-adapter-mongodb-v4` SHALL be built along with other modules

#### Scenario: V4 adapter published to Maven Central
- **WHEN** the release workflow runs with `-Pcentral`
- **THEN** `lcl-adapter-mongodb-v4` SHALL be included in the `-pl` list and deployed to Maven Central
