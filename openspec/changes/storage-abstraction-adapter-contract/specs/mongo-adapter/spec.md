## ADDED Requirements

### Requirement: lcl-adapter-mongodb module structure
The project SHALL include a new Maven module `lcl-adapter-mongodb` with:
- `groupId`: `io.github.emmansun`
- `artifactId`: `lcl-adapter-mongodb`
- Dependencies: `lcl-spi`, `lcl-core`, `spring-boot-starter-data-mongodb`
- Package: `io.github.emmansun.lightcrypto.adapter.mongodb`

#### Scenario: Module compiles independently
- **WHEN** `mvn compile -pl lcl-adapter-mongodb` is executed
- **THEN** the module SHALL compile successfully with only its declared dependencies

### Requirement: MongoVaultStore implementation
`MongoVaultStore` SHALL implement `VaultStore` using `MongoTemplate`:
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

#### Scenario: Load maps BSON to VaultDocument
- **WHEN** `load("default.default.User#phone")` is called and the document exists
- **THEN** the returned `VaultDocument` SHALL have all fields correctly mapped from BSON (keys[], activeKid, version, cmkProvider, cmkId, timestamps)

#### Scenario: Rotate uses optimistic locking
- **WHEN** `rotate(updatedDoc)` is called with version N+1 while stored version is N
- **THEN** the document SHALL be updated atomically and version SHALL become N+1

#### Scenario: Rotate fails on stale version
- **WHEN** `rotate(updatedDoc)` is called with version N+1 while stored version is already N+1 (concurrent rotation)
- **THEN** `OptimisticLockException` SHALL be thrown

#### Scenario: Unique index on vault collection
- **WHEN** the adapter initializes
- **THEN** it SHALL ensure a unique index on `{_id: 1}` in `__lcl_keyvault`

### Requirement: MongoStorageAdapter implementation
`MongoStorageAdapter` SHALL implement `StorageAdapter` using BSON `Document`:
- `buildEncryptedPayload(blob, typeMarker, blindIndex)`: returns `Document {c: blob, _e: 1, _t: typeMarker, b?: blindIndex}`
- `extractBlob(payload)`: returns `((Document) payload).getString("c")`
- `extractTypeMarker(payload)`: returns `((Document) payload).getString("_t")`
- `extractBlindIndex(payload)`: returns `((Document) payload).getString("b")` or null
- `isEncryptedPayload(value)`: returns `value instanceof Document doc && doc.containsKey("_e") && doc.getInteger("_e") == 1`

#### Scenario: Build MongoDB sub-document with blind index
- **WHEN** `buildEncryptedPayload("AQAB...", "STR", "a3f2b1")` is called
- **THEN** the result SHALL be `Document {c: "AQAB...", _e: 1, _t: "STR", b: "a3f2b1"}`

#### Scenario: Build MongoDB sub-document without blind index
- **WHEN** `buildEncryptedPayload("AQAB...", "INT", null)` is called
- **THEN** the result SHALL be `Document {c: "AQAB...", _e: 1, _t: "INT"}` (no `b` field)

#### Scenario: isEncryptedPayload recognizes BSON sub-document
- **WHEN** `isEncryptedPayload(new Document("_e", 1).append("c", "AQAB"))` is called
- **THEN** it SHALL return `true`

#### Scenario: isEncryptedPayload rejects plain string
- **WHEN** `isEncryptedPayload("hello")` is called
- **THEN** it SHALL return `false`

### Requirement: MongoQueryTransformer implementation
`MongoQueryTransformer` SHALL implement `QueryTransformer`:
- `rewriteFieldName(field)`: returns `field + ".b"`
- `rewriteQueryValue(value, namespace)`: computes blind index via `BlindIndexEngine`
- `supportsField(field, entityType)`: checks `EntityMetadataCache` for `blindIndex = true`

#### Scenario: Field name rewrite appends .b suffix
- **WHEN** `rewriteFieldName("phone")` is called
- **THEN** it SHALL return `"phone.b"`

#### Scenario: Query value rewritten to blind index hash
- **WHEN** `rewriteQueryValue("13900001111", "default.default.User#phone")` is called
- **THEN** it SHALL return the hex-encoded HMAC-SHA-256 blind index

### Requirement: Mongo event listeners in adapter module
The adapter module SHALL provide `MongoCryptoEventListener` (extending `AbstractMongoEventListener<Object>`) that delegates to the starter's `FieldCryptoService` for encrypt/decrypt and uses `MongoStorageAdapter` for payload format:
- `onBeforeSave`: encrypt `@Encrypted` fields, build payload via `MongoStorageAdapter`
- `onBeforeConvert`: detect encrypted payloads via `MongoStorageAdapter.isEncryptedPayload()`, decrypt via `FieldCryptoService`

#### Scenario: BeforeSave encrypts and stores as BSON sub-document
- **WHEN** an entity with `@Encrypted` phone field is saved
- **THEN** the BSON Document SHALL have phone replaced with `{c: "...", _e: 1, _t: "STR", b: "..."}` via `MongoStorageAdapter.buildEncryptedPayload()`

#### Scenario: BeforeConvert decrypts BSON sub-document
- **WHEN** a BSON Document with `phone = {c: "...", _e: 1, _t: "STR"}` is read
- **THEN** the listener SHALL use `MongoStorageAdapter.extractBlob()` to get the blob, decrypt it, and replace the field with plaintext

### Requirement: Spring Boot auto-configuration for adapter
The adapter module SHALL provide `MongoAdapterAutoConfiguration`:
- `@ConditionalOnClass(MongoTemplate.class)`
- `@ConditionalOnBean(MongoTemplate.class)`
- Registers `MongoVaultStore`, `MongoStorageAdapter`, `MongoQueryTransformer`, `MongoCryptoEventListener` beans
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
