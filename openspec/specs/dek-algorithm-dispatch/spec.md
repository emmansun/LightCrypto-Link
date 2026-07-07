## Purpose

Define the behavioral contract for the dek-algorithm-dispatch capability to match current implementation behavior.

## Requirements
### Requirement: SymmetricEncryptor strategy interface
The system SHALL provide a `SymmetricEncryptor` interface with three methods: `encrypt(byte[] key, byte[] plaintext)`, `decrypt(byte[] key, byte[] data)`, and `computeKcv(byte[] key)`. Concrete implementations SHALL exist for each `SymmetricAlgorithm` value.

#### Scenario: Encryptor selection by algorithm
- **WHEN** `CryptoCodec` receives an encrypt/decrypt request with `SymmetricAlgorithm.AES_256_GCM`
- **THEN** the system SHALL dispatch to the `AesGcmEncryptor` implementation

#### Scenario: SM4-GCM encryptor
- **WHEN** `CryptoCodec` receives an encrypt request with `SymmetricAlgorithm.SM4_GCM`
- **THEN** the system SHALL dispatch to the `Sm4GcmEncryptor` implementation using the first 16 bytes of the DEK

### Requirement: Algorithm-aware encrypt method
`CryptoCodec.encrypt(byte[] dek, byte[] plaintext, SymmetricAlgorithm algorithm)` SHALL encrypt the plaintext using the specified algorithm and return the ciphertext bytes (IV || encrypted-data).

#### Scenario: AES-256-GCM encryption
- **WHEN** algorithm is `AES_256_GCM`
- **THEN** the system SHALL use 12-byte IV, AES/GCM/NoPadding cipher, and return `IV (12 bytes) || ciphertext+tag`

#### Scenario: AES-256-CBC encryption
- **WHEN** algorithm is `AES_256_CBC`
- **THEN** the system SHALL use 16-byte IV, AES/CBC/PKCS5Padding cipher, and return `IV (16 bytes) || ciphertext`

#### Scenario: SM4-GCM encryption
- **WHEN** algorithm is `SM4_GCM`
- **THEN** the system SHALL derive a 16-byte key (first 16 bytes of DEK), use 12-byte IV, SM4/GCM/NoPadding cipher via Bouncy Castle, and return `IV (12 bytes) || ciphertext+tag`

#### Scenario: SM4-CBC encryption
- **WHEN** algorithm is `SM4_CBC`
- **THEN** the system SHALL derive a 16-byte key (first 16 bytes of DEK), use 16-byte IV, SM4/CBC/PKCS5Padding cipher via Bouncy Castle, and return `IV (16 bytes) || ciphertext`

### Requirement: Algorithm-aware decrypt method
`CryptoCodec.decrypt(byte[] dek, byte[] data, SymmetricAlgorithm algorithm)` SHALL decrypt the data using the specified algorithm and return the plaintext.

#### Scenario: Decrypt roundtrip per algorithm
- **WHEN** plaintext is encrypted with any of the 4 supported algorithms and then decrypted with the same algorithm
- **THEN** the decrypted value SHALL be identical to the original plaintext

#### Scenario: Algorithm mismatch detection
- **WHEN** data encrypted with AES-256-GCM is decrypted with AES-256-CBC
- **THEN** the system SHALL throw a `CryptoException`

### Requirement: Algorithm-aware KCV computation
`CryptoCodec.computeKcv(byte[] key, SymmetricAlgorithm algorithm)` SHALL compute the KCV using the same algorithm as the field's encryption.

#### Scenario: KCV per algorithm
- **WHEN** KCV is computed for the same key with different algorithms
- **THEN** each algorithm SHALL produce a different KCV value

#### Scenario: KCV consistency per algorithm
- **WHEN** KCV is computed multiple times with the same key and algorithm
- **THEN** the output SHALL be identical each time

### Requirement: Backward compatibility for algorithm tag
When decrypting a sub-document without an `_a` (algorithm) field, the system SHALL default to `AES_256_GCM`.

#### Scenario: Legacy document decryption
- **WHEN** a sub-document has `_e=1`, `_k`, `_t`, `c` but no `_a` field
- **THEN** the system SHALL decrypt using AES-256-GCM

### Requirement: Sub-document algorithm tag
When encrypting a field, the system SHALL store the algorithm name in the `_a` field of the encrypted sub-document.

#### Scenario: New document with algorithm tag
- **WHEN** a field with `@Encrypted(algorithm = SM4_GCM)` is saved
- **THEN** the sub-document SHALL include `"_a": "SM4_GCM"`


