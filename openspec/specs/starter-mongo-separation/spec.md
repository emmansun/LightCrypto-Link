## ADDED Requirements

### Requirement: Starter module has no MongoDB compile-time dependency
The `lcl-spring-boot-starter` module's POM SHALL NOT declare `spring-boot-starter-data-mongodb` (or any `org.mongodb:*` / `org.springframework.data:spring-data-mongodb` artifact) as a compile-scope dependency. No source file in the starter module SHALL import any class from `org.bson.*`, `org.springframework.data.mongodb.*`, or `com.mongodb.*` packages.

#### Scenario: Starter compiles without MongoDB on classpath
- **WHEN** `lcl-spring-boot-starter` is compiled in isolation (no MongoDB artifacts on classpath)
- **THEN** compilation SHALL succeed with zero errors

#### Scenario: No MongoDB imports in starter sources
- **WHEN** all `.java` files under `lcl-spring-boot-starter/src/main/java` are scanned
- **THEN** no file SHALL contain `import org.bson`, `import com.mongodb`, or `import org.springframework.data.mongodb`

### Requirement: Starter AutoConfiguration is storage-agnostic
`LightCryptoLinkAutoConfiguration` SHALL NOT declare `@ConditionalOnClass(MongoTemplate.class)` or `@EnableMongoRepositories`. It SHALL only register storage-agnostic beans: `CmkProvider`, `TypeSerializer`, `TypeDeserializer`, `EntityMetadataCache`, `KeyVaultService`, `BlindIndexEngine`, `HmacKeyProvider`, `BlindIndexFieldChecker`, `FieldCryptoService`, `ProgrammaticCryptoService`.

#### Scenario: Starter AutoConfiguration activates without MongoDB
- **WHEN** Spring Boot starts with `lcl-spring-boot-starter` on classpath but without `MongoTemplate`
- **THEN** `LightCryptoLinkAutoConfiguration` SHALL still activate and register all storage-agnostic beans

#### Scenario: FieldCryptoService bean is created with injected DocumentAccessor and StructuredValueCodec
- **WHEN** Spring Boot starts with both starter and adapter-mongodb
- **THEN** `FieldCryptoService` SHALL be created with `DocumentAccessor` and `StructuredValueCodec` beans injected from adapter

### Requirement: Adapter module provides all MongoDB-specific AutoConfiguration
`MongoAdapterAutoConfiguration` SHALL register all MongoDB-specific beans: `MongoVaultStore`, `MongoStorageAdapter`, `MongoQueryTransformer`, `CryptoBeforeSaveListener`, `CryptoMappingMongoConverter`, `MongoEncryptHandler`, `MongoDecryptHandler`, `CryptoMongoQueryCreator`, `BsonDocumentAccessor`, `BsonStructuredValueCodec`, and the `@EnableMongoRepositories` configuration. It SHALL inject `MongoAdapterProperties` (prefix `lightcrypto.adapters.mongodb`) for adapter-specific settings instead of `CryptoProperties`. It SHALL inject `TenantProperties` for namespace-related concerns.

#### Scenario: Adapter AutoConfiguration activates when MongoTemplate is present
- **WHEN** Spring Boot starts with both starter and adapter-mongodb on classpath
- **THEN** `MongoAdapterAutoConfiguration` SHALL register all MongoDB infrastructure beans

#### Scenario: Adapter AutoConfiguration does NOT activate without MongoDB
- **WHEN** Spring Boot starts with only adapter-mongodb on classpath (no `MongoTemplate`)
- **THEN** `MongoAdapterAutoConfiguration` SHALL NOT activate (conditional on `MongoTemplate.class`)

#### Scenario: Adapter uses MongoAdapterProperties
- **WHEN** `MongoAdapterAutoConfiguration` creates `MongoVaultStore`
- **THEN** it SHALL read the collection name from `MongoAdapterProperties.keyVaultCollection`

### Requirement: Adapter AutoConfiguration ordering after starter
`MongoAdapterAutoConfiguration` SHALL declare `@AutoConfiguration(after = LightCryptoLinkAutoConfiguration.class)` to ensure that storage-agnostic beans (e.g., `KeyVaultService`, `EntityMetadataCache`) are available when MongoDB-specific beans are created.

#### Scenario: MongoDB beans can depend on starter beans
- **WHEN** `MongoAdapterAutoConfiguration` creates `CryptoBeforeSaveListener`
- **THEN** the `KeyVaultService` and `EntityMetadataCache` beans from starter SHALL already be initialized

### Requirement: MongoDB-specific classes reside in adapter module
All classes listed below SHALL be located in `lcl-adapter-mongodb` under package `io.github.emmansun.lightcrypto.adapter.mongodb`:
- `CryptoMappingMongoConverter`
- `CryptoMongoRepositoryFactory`
- `CryptoMongoRepositoryFactoryBean`
- `CryptoPartTreeMongoQuery`
- `CryptoQueryLookupStrategy`
- `MongoEncryptHandler`
- `MongoDecryptHandler`
- `CryptoBeforeSaveListener`
- `CryptoMongoQueryCreator`

#### Scenario: No MongoDB classes in starter
- **WHEN** all `.java` files under `lcl-spring-boot-starter/src/main/java` are listed
- **THEN** none of the 9 classes above SHALL exist in the starter module
