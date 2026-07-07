## 1. Test Infrastructure Setup

- [x] 1.1 Add test dependencies to `pom.xml`: JUnit 5, Mockito, AssertJ, `de.flapdoodle.embed.mongo.spring3x` (embedded MongoDB for integration tests), `spring-boot-starter-test`
- [x] 1.2 Create test base class `LclTestBase` in `src/test/java/com/lcl/crypto/` with shared test fixtures: test CMK (hex), test DEK, test HMAC key, helper methods for building test entities
- [x] 1.3 Create test entity classes in `src/test/java/com/lcl/crypto/testmodel/`: `TestUser` (String phone with `@Encrypted(blindIndex=true)`), `TestEmployee` (Integer age, LocalDate birthDate with `@Encrypted`), `TestPlainEntity` (no `@Encrypted` fields)

## 2. Foundation: Annotation, Config, and Models

- [x] 2.1 Create `@Encrypted` annotation in `annotation/` with `algorithm`, `blindIndex`, `fieldName` attributes and defaults per spec
- [x] 2.2 Create `SymmetricAlgorithm` enum (v1: only `AES_256_GCM`) in `annotation/`
- [x] 2.3 Create `CryptoProperties` in `config/` with `@ConfigurationProperties("lcl.crypto")`: `cmk`, `keyVaultId`, `keyVaultDatabase`, `enabled`, `autoInit`
- [x] 2.4 Create `WrappedKey` record in `model/` with `byte[] ciphertext` and `String algorithm`
- [x] 2.5 Create `EncryptedFieldMetadata` record in `model/` holding field name, Java type, annotation attributes, and effective fieldName
- [x] 2.6 Create `KeyVaultDocument` class in `model/` mapping the `__lcl_keyvault` BSON structure
- [x] 2.7 Create exception classes: `CryptoException`, `FatalCryptoException`, `UnsupportedTypeException` in a new `exception/` package
- [x] 2.8 **Test**: `@Encrypted` annotation defaults — verify `blindIndex=false`, `algorithm=AES_256_GCM`, `fieldName=""` via reflection

## 3. CMK Provider SPI

- [x] 3.1 Create `CmkProvider` interface in `provider/` with `getProviderId()`, `wrap(byte[])`, `unwrap(WrappedKey)`
- [x] 3.2 Implement `LocalSymmetricCmkProvider` in `provider/`: AES-256-GCM wrap/unwrap with random IV, validate CMK length = 32 bytes
- [x] 3.3 **Test**: `LocalSymmetricCmkProviderTest` — wrap/unwrap roundtrip produces identical key material
- [x] 3.4 **Test**: wrap same key twice produces different ciphertexts (random IV)
- [x] 3.5 **Test**: invalid CMK length (not 32 bytes) throws `IllegalArgumentException` at construction
- [x] 3.6 **Test**: `WrappedKey.algorithm` returns `"AES-256-GCM"`

## 4. Type Serialization

- [x] 4.1 Implement `TypeSerializer` in `service/` with deterministic serialization for String, Integer, Long, Short, Byte, Float, Double, BigDecimal, Boolean, LocalDate, LocalDateTime, Enum, byte[]
- [x] 4.2 Implement `TypeDeserializer` in `service/` with type-marker-guided deserialization (STR, INT, LONG, SHORT, BYTE, FLOAT, DOUBLE, DEC, BOOL, LDATE, LDT, ENUM:fqcn, BYTES)
- [x] 4.3 Implement `resolveTypeMarker(Class<?>)` utility that returns the `_t` string for a given Java type
- [x] 4.4 **Test**: `TypeSerializerTest` — deterministic output for all 13 types across multiple calls
- [x] 4.5 **Test**: BigDecimal `toPlainString()` — verify `15000` serializes as `"15000"` not `"1.5E+4"`
- [x] 4.6 **Test**: `TypeDeserializerTest` — roundtrip serialize→deserialize for all types, including Enum with FQCN parsing
- [x] 4.7 **Test**: `resolveTypeMarker` returns correct markers (STR, INT, LDATE, ENUM:fqcn, etc.)
- [x] 4.8 **Test**: unsupported type (custom class) throws `UnsupportedTypeException`

## 5. Crypto Core Engine

- [x] 5.1 Implement `CryptoCodec` in `service/`: `encrypt(byte[] dek, String plaintext)` returns Binary (IV || ciphertext), `decrypt(byte[] dek, Binary)` returns plaintext
- [x] 5.2 Implement `generateBlindIndex(byte[] hmacKey, String fieldName, String serializedValue)` in `CryptoCodec`
- [x] 5.3 Implement `computeKcv(byte[] key)` in `CryptoCodec`: AES-256-GCM with zero IV over zero block, return hex string
- [x] 5.4 Implement `computeBinding(byte[] hmacKey, byte[] aesKey)` in `CryptoCodec`: HMAC-SHA-256(hmacKey, aesKey), return hex string
- [x] 5.5 **Test**: `CryptoCodecTest` — encrypt/decrypt roundtrip for various plaintext lengths (empty, short, long)
- [x] 5.6 **Test**: encrypt same plaintext twice produces different ciphertexts (random IV)
- [x] 5.7 **Test**: tampered ciphertext (flip a byte) throws `CryptoException` on decrypt (GCM auth tag)
- [x] 5.8 **Test**: `generateBlindIndex` is deterministic — same input produces same hash
- [x] 5.9 **Test**: `generateBlindIndex` with different fieldName produces different hash (field isolation)
- [x] 5.10 **Test**: `computeKcv` is deterministic; different key produces different KCV
- [x] 5.11 **Test**: `computeBinding` is deterministic; swapped keys produce different binding

## 6. Key Vault Service

- [x] 6.1 Implement `KeyVaultService` in `service/`: reads `__lcl_keyvault` document by `keyVaultId` using `MongoTemplate`
- [x] 6.2 Implement auto-init logic: when vault is empty and `autoInit=true`, generate DEK + HMAC key (SecureRandom 32 bytes), wrap with CmkProvider, compute KCV + binding, insert document
- [x] 6.3 Implement startup verification: unwrap DEK + HMAC key, recompute KCV and binding, compare against stored values, throw `FatalCryptoException` on mismatch
- [x] 6.4 Handle concurrent init: use findAndModify or catch DuplicateKeyException, fall back to load path
- [x] 6.5 Expose `getDek()` and `getHmacKey()` methods returning unwrapped keys for CryptoCodec usage
- [x] 6.6 **Test**: `KeyVaultServiceTest` (with embedded MongoDB) — auto-init creates vault document with all required fields
- [x] 6.7 **Test**: subsequent startup loads existing vault without re-generating keys
- [x] 6.8 **Test**: KCV mismatch (corrupt `dek.kcv` in vault) throws `FatalCryptoException`
- [x] 6.9 **Test**: binding mismatch (corrupt `binding` in vault) throws `FatalCryptoException`
- [x] 6.10 **Test**: `getDek()` and `getHmacKey()` return 32-byte unwrapped keys

## 7. Entity Metadata Cache

- [x] 7.1 Implement `EntityMetadataCache` in `listener/`: scan entity class fields for `@Encrypted` annotation, cache results per class
- [x] 7.2 Implement `getEncryptedFields(Class<?>)` returning list of `EncryptedFieldMetadata`
- [x] 7.3 Implement `hasEncryptedFields(Class<?>)` for fast skip check
- [x] 7.4 Validate field types against supported type list at scan time, throw `UnsupportedTypeException` for unsupported types
- [x] 7.5 **Test**: `EntityMetadataCacheTest` — `TestUser` returns 1 encrypted field with correct metadata
- [x] 7.6 **Test**: `TestEmployee` returns 2 encrypted fields (age INT, birthDate LDATE)
- [x] 7.7 **Test**: `TestPlainEntity` returns empty list, `hasEncryptedFields` returns false
- [x] 7.8 **Test**: entity with unsupported type field throws `UnsupportedTypeException` at scan time
- [x] 7.9 **Test**: repeated calls for same class return cached result (same list instance)

## 8. Transparent Listeners

- [x] 8.1 Implement `CryptoBeforeSaveListener` extending `AbstractMongoEventListener<Object>`: on `BeforeSaveEvent`, for each `@Encrypted` field, serialize value, encrypt, build `{c, b?, _e:1, _t}` sub-document, replace in Document
- [x] 8.2 Implement null handling: skip encryption when field value is null
- [x] 8.3 Implement `CryptoBeforeConvertListener` extending `AbstractMongoEventListener<Object>`: on `BeforeConvertEvent`, for each known `@Encrypted` field in Document, detect `_e:1`, decrypt `c`, deserialize per `_t`, replace sub-document with typed value
- [x] 8.4 Handle missing/null fields gracefully without exceptions
- [x] 8.5 **Test**: `CryptoBeforeSaveListenerTest` — save String field, verify Document contains `{c: Binary, b: String, _e: 1, _t: "STR"}`
- [x] 8.6 **Test**: save with `blindIndex=false` produces Document without `b` field
- [x] 8.7 **Test**: save Integer field produces `{c: Binary, _e: 1, _t: "INT"}`
- [x] 8.8 **Test**: null field value is skipped (not encrypted, not replaced)
- [x] 8.9 **Test**: `CryptoBeforeConvertListenerTest` — decrypt Document with `_e:1, _t:"STR"` produces original String
- [x] 8.10 **Test**: decrypt Document with `_t:"INT"` produces correct Integer
- [x] 8.11 **Test**: decrypt Document with `_t:"LDATE"` produces correct LocalDate
- [x] 8.12 **Test**: missing encrypted field in Document does not throw exception
- [x] 8.13 **Test**: full write→read roundtrip via listeners: encrypt then decrypt produces original values for all supported types

## 9. Transparent Query (QueryLookup Pipeline)

- [x] 9.1 Create `CryptoMongoRepositoryFactory` in `query/` extending `MongoRepositoryFactory`, override `getQueryLookupStrategy()`
- [x] 9.2 Create `CryptoQueryLookupStrategy` in `query/` implementing `QueryLookupStrategy`, delegate to `CryptoPartTreeMongoQuery` for method-name queries
- [x] 9.3 Create `CryptoPartTreeMongoQuery` in `query/` extending `PartTreeMongoQuery`, override query creation to inject `CryptoMongoQueryCreator`
- [x] 9.4 Create `CryptoMongoQueryCreator` in `query/`: for each Part, check if the property has `@Encrypted(blindIndex=true)`, if so rewrite Criteria to `fieldName.b` with hashed value; for IN queries hash each element
- [x] 9.5 Reject unsupported query types (CONTAINING, STARTING_WITH, BETWEEN, etc.) with `UnsupportedOperationException`
- [x] 9.6 Reject queries on `@Encrypted` fields where `blindIndex=false` with clear error message
- [x] 9.7 **Test**: `CryptoMongoQueryCreatorTest` — `findByPhone(value)` produces `Criteria.where("phone.b").is(expectedHash)`
- [x] 9.8 **Test**: `findByPhoneAndName(phone, name)` produces mixed Criteria (phone.b hashed, name plain)
- [x] 9.9 **Test**: `findByPhoneIn(list)` hashes each element and produces `$in` query on `phone.b`
- [x] 9.10 **Test**: `findByPhoneIsNull()` produces `Criteria.where("phone").is(null)` (no hashing)
- [x] 9.11 **Test**: `findByPhoneStartingWith` throws `UnsupportedOperationException`
- [x] 9.12 **Test**: query on `@Encrypted(blindIndex=false)` field throws `UnsupportedOperationException`

## 10. Auto-Configuration and Spring Boot Registration

- [x] 10.1 Rewrite `LightCryptoLinkAutoConfiguration`: register BouncyCastle Provider, configure `CryptoProperties`, create `CmkProvider` bean (`@ConditionalOnMissingBean`), create `KeyVaultService` bean (triggers vault init + verification), create `CryptoCodec`, `EntityMetadataCache`, `TypeSerializer`, `TypeDeserializer` beans
- [x] 10.2 Register `CryptoBeforeSaveListener` and `CryptoMappingMongoConverter` as beans (replaced event-based `CryptoBeforeConvertListener` with converter override for reliable read-path decryption)
- [x] 10.3 Configure `CryptoMongoRepositoryFactory` via `@EnableMongoRepositories(repositoryFactoryBeanClass=...)` or `RepositoryFactoryCustomizer`
- [x] 10.4 Create `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file registering `LightCryptoLinkAutoConfiguration`
- [x] 10.5 **Test**: `AutoConfigurationTest` (with `ApplicationContextRunner`) — all expected beans are created
- [x] 10.6 **Test**: custom `CmkProvider` bean overrides default `LocalSymmetricCmkProvider`
- [x] 10.7 **Test**: `lcl.crypto.enabled=false` disables all crypto beans
- [x] 10.8 Verify full compile with `mvn compile` and `mvn test-compile` pass with zero errors

## 11. End-to-End Integration Tests

- [x] 11.1 **Integration**: `LclEndToEndTest` with embedded MongoDB — save entity with `@Encrypted(blindIndex=true)` String field, read back, verify plaintext matches
- [x] 11.2 **Integration**: save entity with multiple encrypted fields (String + Integer + LocalDate), read back, verify all fields match
- [x] 11.3 **Integration**: blind index query — `findByPhone(value)` returns correct entity from a collection of 10 test records
- [x] 11.4 **Integration**: `findByPhoneIn(list)` batch query returns correct subset
- [x] 11.5 **Integration**: `findByPhoneAndName` compound query returns correct entity
- [x] 11.6 **Integration**: vault auto-init — start with empty MongoDB, confirm `__lcl_keyvault` document created with correct structure
- [x] 11.7 **Integration**: KCV corruption — manually corrupt vault document, restart, confirm `FatalCryptoException`
- [x] 11.8 **Integration**: update scenario — modify encrypted field value, save, read back, verify new value
- [x] 11.9 **Integration**: null field handling — save entity with null encrypted field, read back, verify null preserved
- [x] 11.10 Run full test suite: `mvn test` — all tests pass
