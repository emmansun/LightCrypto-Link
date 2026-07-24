## ADDED Requirements

### Requirement: Core module structure and dependencies
A new Maven module `lcl-adapter-mongodb-core` SHALL exist with:
- `groupId`: `io.github.emmansun`
- `artifactId`: `lcl-adapter-mongodb-core`
- Package: `io.github.emmansun.lightcrypto.adapter.mongodb`
- Dependencies: `lcl-spi`, `lcl-core`, `lcl-spring-boot-starter`, `spring-boot-starter-data-mongodb` (SB3 version)
- Compiled against Spring Data MongoDB 4.x (Spring Boot 3.x baseline)

#### Scenario: Core module compiles independently
- **WHEN** `mvn compile -pl lcl-adapter-mongodb-core` is executed
- **THEN** the module SHALL compile successfully with only its declared dependencies

#### Scenario: Core module is part of reactor build
- **WHEN** `mvn clean install` is run from the parent POM
- **THEN** `lcl-adapter-mongodb-core` SHALL be built before `lcl-adapter-mongodb` and `lcl-adapter-mongodb-v4`

### Requirement: Core module contains version-independent shared classes
The following 11 classes SHALL reside in `lcl-adapter-mongodb-core` under package `io.github.emmansun.lightcrypto.adapter.mongodb`:
- `MongoVaultStore` (VaultStore implementation)
- `MongoEncryptHandler` (field encryption orchestration)
- `MongoDecryptHandler` (field decryption orchestration)
- `MongoStorageAdapter` (StorageAdapter implementation, BSON sub-document format)
- `BsonDocumentAccessor` (DocumentAccessor implementation)
- `BsonStructuredValueCodec` (StructuredValueCodec implementation)
- `CryptoBeforeSaveListener` (BeforeConvertCallback, encrypt on write)
- `MongoCryptoEventListener` (AbstractMongoEventListener, decrypt on read)
- `MongoQueryTransformer` (QueryTransformer implementation, blind-index rewrite)
- `MongoAdapterProperties` (@ConfigurationProperties, prefix `lightcrypto.adapters.mongodb`)
- `CryptoMongoQueryCreator` (Criteria-based blind-index query rewrite)

#### Scenario: No breaking-change API usage in core classes
- **WHEN** all source files in `lcl-adapter-mongodb-core` are scanned
- **THEN** no file SHALL import `QueryMethodEvaluationContextProvider`, `ValueExpressionDelegate`, or reference `EntityProjection.getDomainType()`

#### Scenario: Core classes use only stable Spring Data MongoDB APIs
- **WHEN** all source files in `lcl-adapter-mongodb-core` are scanned
- **THEN** imports from `org.springframework.data.mongodb` SHALL be limited to: `MongoTemplate`, `MongoCollectionUtils`, `BeforeConvertCallback`, `AbstractMongoEventListener`, `MappingMongoConverter`, `MongoMappingContext`, `MongoOperations`, and query `Criteria`/`Query` classes

### Requirement: Core module binary compatibility with Spring Data MongoDB 5.x
Classes in `lcl-adapter-mongodb-core` SHALL function correctly when loaded at runtime with Spring Data MongoDB 5.x (Spring Boot 4.x) on the classpath.

#### Scenario: MongoVaultStore works on Spring Data MongoDB 5.x
- **WHEN** `MongoVaultStore.save()` and `MongoVaultStore.load()` are invoked in a Spring Boot 4.x application with Spring Data MongoDB 5.x
- **THEN** vault CRUD operations SHALL succeed identically to Spring Boot 3.x behavior

#### Scenario: CryptoBeforeSaveListener works on Spring Data MongoDB 5.x
- **WHEN** an entity with `@Encrypted` fields is saved in a Spring Boot 4.x application
- **THEN** the `BeforeConvertCallback` SHALL fire and encrypt fields identically to Spring Boot 3.x behavior

### Requirement: Core module published to Maven Central
The `lcl-adapter-mongodb-core` module SHALL be included in the release workflow's `-pl` list and deployed to Maven Central alongside other modules.

#### Scenario: Core module published in release
- **WHEN** the release workflow runs with `-Pcentral`
- **THEN** `lcl-adapter-mongodb-core` SHALL be deployed to Maven Central

### Requirement: Core module does not register auto-configuration
The `lcl-adapter-mongodb-core` module SHALL NOT contain any `@AutoConfiguration` class or `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file. Bean registration is the responsibility of the version-specific adapter modules.

#### Scenario: No auto-configuration in core
- **WHEN** `lcl-adapter-mongodb-core` is the only LCL adapter module on the classpath
- **THEN** no adapter beans SHALL be auto-registered (no `MongoVaultStore`, no listeners, etc.)
