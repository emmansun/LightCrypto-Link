## MODIFIED Requirements

### Requirement: MongoDB-specific classes reside in adapter modules
All classes listed below SHALL be located in their respective modules under package `io.github.emmansun.lightcrypto.adapter.mongodb`:

**In `lcl-adapter-mongodb-core`** (version-independent shared classes):
- `MongoVaultStore`
- `MongoEncryptHandler`
- `MongoDecryptHandler`
- `CryptoBeforeSaveListener`
- `CryptoMongoQueryCreator`
- `MongoQueryTransformer`
- `MongoStorageAdapter`
- `BsonDocumentAccessor`
- `BsonStructuredValueCodec`
- `MongoCryptoEventListener`
- `MongoAdapterProperties`

**In `lcl-adapter-mongodb`** (SB3-specific query layer):
- `CryptoMappingMongoConverter`
- `CryptoMongoRepositoryFactory`
- `CryptoMongoRepositoryFactoryBean`
- `CryptoPartTreeMongoQuery`
- `CryptoQueryLookupStrategy`
- `MongoAdapterAutoConfiguration`

**In `lcl-adapter-mongodb-v4`** (SB4-specific query layer):
- `CryptoMappingMongoConverter`
- `CryptoMongoRepositoryFactory`
- `CryptoMongoRepositoryFactoryBean`
- `CryptoPartTreeMongoQuery`
- `CryptoQueryLookupStrategy`
- `MongoAdapterV4AutoConfiguration`

#### Scenario: No MongoDB classes in starter
- **WHEN** all `.java` files under `lcl-spring-boot-starter/src/main/java` are listed
- **THEN** none of the classes above SHALL exist in the starter module

#### Scenario: Shared classes not duplicated across adapters
- **WHEN** inspecting source directories of `lcl-adapter-mongodb` and `lcl-adapter-mongodb-v4`
- **THEN** neither SHALL contain source files for the 11 shared classes (they are resolved from `lcl-adapter-mongodb-core` dependency)

### Requirement: Starter module has no MongoDB compile-time dependency
The `lcl-spring-boot-starter` module's POM SHALL NOT declare `spring-boot-starter-data-mongodb` (or any `org.mongodb:*` / `org.springframework.data:spring-data-mongodb` artifact) as a compile-scope dependency. No source file in the starter module SHALL import any class from `org.bson.*`, `org.springframework.data.mongodb.*`, or `com.mongodb.*` packages.

#### Scenario: Starter compiles without MongoDB on classpath
- **WHEN** `lcl-spring-boot-starter` is compiled in isolation (no MongoDB artifacts on classpath)
- **THEN** compilation SHALL succeed with zero errors

### Requirement: Starter AutoConfiguration is storage-agnostic
`LightCryptoLinkAutoConfiguration` SHALL NOT declare `@ConditionalOnClass(MongoTemplate.class)` or `@EnableMongoRepositories`. It SHALL only register storage-agnostic beans: `CmkProvider`, `TypeSerializer`, `TypeDeserializer`, `EntityMetadataCache`, `KeyVaultService`, `BlindIndexEngine`, `HmacKeyProvider`, `BlindIndexFieldChecker`, `FieldCryptoService`, `ProgrammaticCryptoService`.

#### Scenario: Starter AutoConfiguration activates without MongoDB
- **WHEN** Spring Boot starts with `lcl-spring-boot-starter` on classpath but without `MongoTemplate`
- **THEN** `LightCryptoLinkAutoConfiguration` SHALL still activate and register all storage-agnostic beans

### Requirement: Adapter AutoConfiguration ordering after starter
Both `MongoAdapterAutoConfiguration` (SB3) and `MongoAdapterV4AutoConfiguration` (SB4) SHALL declare `@AutoConfiguration(after = LightCryptoLinkAutoConfiguration.class)` to ensure that storage-agnostic beans are available when MongoDB-specific beans are created.

#### Scenario: MongoDB beans can depend on starter beans
- **WHEN** adapter auto-configuration creates `CryptoBeforeSaveListener`
- **THEN** the `KeyVaultService` and `EntityMetadataCache` beans from starter SHALL already be initialized
