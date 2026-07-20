## ADDED Requirements

### Requirement: lcl-core module has zero framework dependencies
The `lcl-core` Maven module SHALL NOT depend on Spring Framework, Spring Boot, Spring Data, MongoDB driver, or any database driver. Its compile-scope dependencies SHALL be limited to: JDK 17 standard library, BouncyCastle (bcprov-jdk18on), and `lcl-spi`.

#### Scenario: Dependency verification
- **WHEN** inspecting lcl-core's effective POM
- **THEN** no Spring, MongoDB, or JDBC artifacts SHALL appear in compile or runtime scope

#### Scenario: Standalone compilation
- **WHEN** building lcl-core in isolation (`mvn -pl lcl-core compile`)
- **THEN** compilation SHALL succeed without any Spring or DB classes on the classpath

### Requirement: lcl-core provides stateless CryptoCodec
The `CryptoCodec` class in lcl-core SHALL be stateless and purely functional. All encryption/decryption methods SHALL accept explicit parameters (key, plaintext/ciphertext, algorithm, namespace, dekVersion) and return results without side effects. CryptoCodec SHALL NOT hold references to key vaults, databases, or configuration objects.

#### Scenario: Encrypt with explicit parameters
- **WHEN** calling `CryptoCodec.encrypt(dek, plaintext, algorithm, namespace, dekVersion)`
- **THEN** the method SHALL return a Base64URL-encoded Wire Format V1 string using a random IV

#### Scenario: Decrypt with explicit parameters
- **WHEN** calling `CryptoCodec.decrypt(dek, wireFormatBlob)`
- **THEN** the method SHALL parse the blob, extract algorithm/namespace/dekVersion/iv, reconstruct AAD, decrypt, and return plaintext bytes

#### Scenario: No hidden state between calls
- **WHEN** calling encrypt twice with identical parameters
- **THEN** both calls SHALL succeed independently (no cached state affects the second call)

### Requirement: lcl-core contains SymmetricEncryptor implementations
The `lcl-core` module SHALL contain all SymmetricEncryptor implementations (AesGcmEncryptor, AesCbcEncryptor, Sm4GcmEncryptor, Sm4CbcEncryptor) migrated from the spring-boot-starter. Each encryptor SHALL accept AAD bytes for authenticated encryption modes (GCM).

#### Scenario: GCM encryptor uses AAD
- **WHEN** encrypting with AesGcmEncryptor providing AAD bytes
- **THEN** the GCM authentication tag SHALL cover both the ciphertext and the AAD

#### Scenario: CBC encryptor ignores AAD
- **WHEN** encrypting with AesCbcEncryptor providing AAD bytes
- **THEN** the AAD SHALL NOT affect the ciphertext (CBC has no authentication)

### Requirement: lcl-core contains WireFormat encoder and decoder
The `lcl-core` module SHALL provide `WireFormatEncoder` and `WireFormatDecoder` classes implementing the wire-format-v1 capability. The encoder SHALL produce byte arrays conforming to the V1 layout. The decoder SHALL parse V1 blobs and reject invalid inputs.

#### Scenario: Encoder produces V1-compliant blob
- **WHEN** encoding with version=1, algorithm=AES_256_GCM, namespace, dekVersion=1, iv, ciphertext
- **THEN** the output SHALL conform to the Wire Format V1 byte layout specification

#### Scenario: Decoder rejects truncated blob
- **WHEN** decoding a blob shorter than the minimum header size (11 bytes + 1 byte namespace)
- **THEN** the decoder SHALL throw IllegalArgumentException

### Requirement: lcl-core contains BlindIndexEngine
The `lcl-core` module SHALL provide a `BlindIndexEngine` class implementing the hkdf-blind-index capability. It SHALL be instantiable with a master HMAC key and provide methods to compute blind indexes for given namespace/field/value combinations.

#### Scenario: Engine instantiation with master key
- **WHEN** creating `new BlindIndexEngine(masterHmacKey)`
- **THEN** the engine SHALL be ready to compute blind indexes for any namespace

#### Scenario: Engine computes tenant-isolated indexes
- **WHEN** computing blind index for the same value under two different namespaces
- **THEN** the outputs SHALL differ due to HKDF namespace-scoped key derivation

### Requirement: Module dependency rules enforced
The build SHALL enforce that lcl-provider-* modules depend only on lcl-spi (not lcl-core). The build SHALL enforce that lcl-adapter-* modules may depend on both lcl-spi and lcl-core.

#### Scenario: Provider module cannot access WireFormat
- **WHEN** a class in lcl-provider-alibaba-kms attempts to import a lcl-core class
- **THEN** compilation SHALL fail because lcl-core is not a dependency of provider modules
