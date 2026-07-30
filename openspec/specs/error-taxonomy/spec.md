## ADDED Requirements

### Requirement: Exception hierarchy structure
The system SHALL define the following exception hierarchy rooted at `CryptoException` (lcl-spi):
- `DecryptionException extends CryptoException` (lcl-core) — grouping parent for decrypt-path failures
- `CryptoAuthenticationException extends DecryptionException` (lcl-core) — GCM auth tag / CBC padding verification failure
- `PayloadCorruptionException extends CryptoException` (lcl-core) — wire format parse failure (pre-decrypt)
- `UnsupportedAlgorithmException extends CryptoException` (lcl-core) — unknown algorithm ID or unregistered encryptor
- `EncryptionException extends CryptoException` (lcl-core) — encrypt-path failure
- `KeyResolutionException extends KeyManagementException` (starter) — vault or DEK version not found
- `SchemaDriftException extends DecryptionException` (starter) — decrypt succeeded but type deserialization failed

All exception classes SHALL reside in package `io.github.emmansun.lightcrypto.exception`.

#### Scenario: Catch by grouping parent
- **WHEN** code catches `DecryptionException`
- **THEN** it SHALL also catch `CryptoAuthenticationException` and `SchemaDriftException` (polymorphic subtypes)

#### Scenario: Catch by base
- **WHEN** code catches `CryptoException`
- **THEN** it SHALL catch all LCL exceptions including all new subtypes

#### Scenario: PayloadCorruptionException is NOT a DecryptionException
- **WHEN** code catches `DecryptionException`
- **THEN** `PayloadCorruptionException` SHALL NOT be caught (it extends CryptoException directly)

### Requirement: Structured context on exceptions
Each new exception class SHALL carry typed context fields accessible via getters:
- `PayloadCorruptionException`: `namespace` (String, nullable), `rawLength` (int, blob byte length or -1)
- `CryptoAuthenticationException`: `namespace` (String, nullable), `dekVersion` (int), `algorithm` (String)
- `UnsupportedAlgorithmException`: `algorithmId` (int), `algorithmName` (String, nullable)
- `KeyResolutionException`: `namespace` (String), `dekVersion` (int, 0 if vault-level miss)
- `SchemaDriftException`: `namespace` (String, nullable), `targetType` (String), `fieldPath` (String, nullable)

All context fields SHALL be set at construction time and immutable.

#### Scenario: CryptoAuthenticationException carries decrypt context
- **WHEN** GCM decryption fails for namespace "default.default.User#phone", dekVersion 2, algorithm "AES_256_GCM"
- **THEN** the thrown exception SHALL have `getNamespace()` = "default.default.User#phone", `getDekVersion()` = 2, `getAlgorithm()` = "AES_256_GCM"

#### Scenario: PayloadCorruptionException carries parse context
- **WHEN** WireFormatDecoder fails to parse a 5-byte blob
- **THEN** the thrown exception SHALL have `getRawLength()` = 5

#### Scenario: KeyResolutionException carries vault context
- **WHEN** getDekByVersion fails for namespace "default.default.User#email", version 3
- **THEN** the thrown exception SHALL have `getNamespace()` = "default.default.User#email", `getDekVersion()` = 3

### Requirement: Exception classes are unchecked
All new exception classes SHALL extend `RuntimeException` (via `CryptoException`). No method signature SHALL declare checked exceptions for LCL error types.

#### Scenario: No checked exception in API
- **WHEN** inspecting public method signatures of CryptoCodec, WireFormatDecoder, KeyVaultService, FieldCryptoService
- **THEN** no method SHALL declare `throws PayloadCorruptionException` or any other LCL exception in its signature

### Requirement: Module placement contract
- `DecryptionException`, `EncryptionException`, `PayloadCorruptionException`, `CryptoAuthenticationException`, `UnsupportedAlgorithmException` SHALL be defined in `lcl-core` module
- `KeyResolutionException`, `SchemaDriftException` SHALL be defined in `lcl-spring-boot-starter` module
- `lcl-spi` SHALL contain only `CryptoException` and `OptimisticLockException`

#### Scenario: lcl-core compiles independently with new exceptions
- **WHEN** building lcl-core in isolation (`mvn -pl lcl-core compile`)
- **THEN** all exception classes in lcl-core SHALL compile without starter on classpath

#### Scenario: Provider modules unaffected
- **WHEN** building lcl-provider-azure-kms or lcl-provider-alibaba-kms
- **THEN** compilation SHALL succeed without referencing any new exception class
