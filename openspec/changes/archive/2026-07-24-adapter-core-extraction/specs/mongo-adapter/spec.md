## MODIFIED Requirements

### Requirement: lcl-adapter-mongodb module structure
The project SHALL include a Maven module `lcl-adapter-mongodb` with:
- `groupId`: `io.github.emmansun`
- `artifactId`: `lcl-adapter-mongodb`
- Dependencies: `lcl-adapter-mongodb-core`, `spring-boot-starter-data-mongodb`
- Package: `io.github.emmansun.lightcrypto.adapter.mongodb`

#### Scenario: Module compiles independently
- **WHEN** `mvn compile -pl lcl-adapter-mongodb` is executed
- **THEN** the module SHALL compile successfully with only its declared dependencies

#### Scenario: Module depends on core for shared classes
- **WHEN** inspecting the `lcl-adapter-mongodb` POM
- **THEN** it SHALL declare a compile dependency on `lcl-adapter-mongodb-core`
- **THEN** shared classes (MongoVaultStore, handlers, listeners, etc.) SHALL be resolved transitively from core

### Requirement: lcl-adapter-mongodb retains only SB3-specific classes
The `lcl-adapter-mongodb` module SHALL contain only the following 6 source files:
- `CryptoMappingMongoConverter` (SB3 EntityProjection API)
- `CryptoMongoRepositoryFactory` (SB3 getQueryLookupStrategy signature)
- `CryptoMongoRepositoryFactoryBean` (SB3 getFactoryInstance return type)
- `CryptoPartTreeMongoQuery` (SB3 constructor signature)
- `CryptoQueryLookupStrategy` (SB3 QueryMethodEvaluationContextProvider)
- `MongoAdapterAutoConfiguration` (SB3 MongoAutoConfiguration import path)

#### Scenario: No shared classes in SB3 adapter source
- **WHEN** inspecting `lcl-adapter-mongodb/src/main/java`
- **THEN** it SHALL NOT contain MongoVaultStore, MongoEncryptHandler, MongoDecryptHandler, MongoStorageAdapter, BsonDocumentAccessor, BsonStructuredValueCodec, CryptoBeforeSaveListener, MongoCryptoEventListener, MongoQueryTransformer, MongoAdapterProperties, or CryptoMongoQueryCreator source files

### Requirement: Spring Boot auto-configuration for adapter
The adapter module SHALL provide `MongoAdapterAutoConfiguration`:
- `@ConditionalOnClass(MongoTemplate.class)`
- `@ConditionalOnBean(MongoTemplate.class)`
- Registers `MongoVaultStore`, `MongoStorageAdapter`, `MongoQueryTransformer`, `MongoCryptoEventListener` beans (classes loaded from `lcl-adapter-mongodb-core`)
- All beans annotated with `@ConditionalOnMissingBean` for user override

#### Scenario: Auto-configuration activates with MongoTemplate present
- **WHEN** the application has `MongoTemplate` bean and `lcl-adapter-mongodb` on classpath
- **THEN** all adapter beans SHALL be registered automatically

#### Scenario: Auto-configuration skipped without MongoTemplate
- **WHEN** no `MongoTemplate` bean exists
- **THEN** no adapter beans SHALL be registered

#### Scenario: User can override adapter beans
- **WHEN** the user defines a custom `VaultStore` bean
- **THEN** the auto-configured `MongoVaultStore` SHALL NOT be registered

### Requirement: MongoVaultStore implementation
`MongoVaultStore` SHALL implement `VaultStore` using `MongoTemplate` (class resides in `lcl-adapter-mongodb-core`, registered as bean by this module's auto-configuration):
- Collection name: `__lcl_keyvault`
- Document `_id`: `lcl-dek-{namespace}`
- `save()`: upsert via `MongoTemplate.save()`
- `load()`: query by `_id` = `lcl-dek-{namespace}`, map BSON to `VaultDocument`
- `exists()`: query by `_id` existence
- `rotate()`: use `findAndModify` with version condition for CAS semantics
- `loadAll()`: `MongoTemplate.findAll()` on the vault collection

#### Scenario: Save creates document in __lcl_keyvault
- **WHEN** `save(doc)` is called with namespace "default.default.User#phone"
- **THEN** a document with `_id` = "lcl-dek-default.default.User#phone" SHALL be persisted in `__lcl_keyvault`

#### Scenario: Rotate uses optimistic locking
- **WHEN** `rotate(updatedDoc)` is called with version N+1 while stored version is N
- **THEN** the document SHALL be updated atomically and version SHALL become N+1

### Requirement: Mongo event listeners in adapter module
The adapter module SHALL register `MongoCryptoEventListener` (class from `lcl-adapter-mongodb-core`) that delegates to the starter's `FieldCryptoService` for encrypt/decrypt and uses `MongoStorageAdapter` for payload format.

#### Scenario: BeforeSave encrypts and stores as BSON sub-document
- **WHEN** an entity with `@Encrypted` phone field is saved
- **THEN** the BSON Document SHALL have phone replaced with `{c: "...", _e: 1, _t: "STR", b: "..."}` via `MongoStorageAdapter.buildEncryptedPayload()`

#### Scenario: BeforeConvert decrypts BSON sub-document
- **WHEN** a BSON Document with `phone = {c: "...", _e: 1, _t: "STR"}` is read
- **THEN** the listener SHALL use `MongoStorageAdapter.extractBlob()` to get the blob, decrypt it, and replace the field with plaintext
