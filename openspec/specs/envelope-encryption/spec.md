## Purpose

Define the behavioral contract for the envelope-encryption capability to match current implementation behavior.

## Requirements
### Requirement: AES-256-GCM encryption and decryption
The system SHALL encrypt plaintext using the algorithm specified by `SymmetricAlgorithm`. When the algorithm is `AES_256_GCM`, the system SHALL use AES-256-GCM with a randomly generated 12-byte IV per operation. The ciphertext SHALL be stored as `IV (12 bytes) || GCM-ciphertext (including 16-byte auth tag)` in a single Binary blob. Other algorithms (AES-256-CBC, SM4-GCM, SM4-CBC) SHALL use their respective IV lengths, key sizes, and cipher modes as defined by the `SymmetricEncryptor` strategy.

#### Scenario: Encrypt produces unique ciphertext
- **WHEN** the same plaintext is encrypted twice with the same algorithm
- **THEN** the two resulting Binary blobs SHALL be different (due to random IV)

#### Scenario: Decrypt roundtrip
- **WHEN** a plaintext is encrypted and then decrypted with the same algorithm
- **THEN** the decrypted value SHALL be identical to the original plaintext

#### Scenario: Tampered ciphertext detection (GCM modes)
- **WHEN** a ciphertext encrypted with a GCM mode is modified after encryption and then decryption is attempted
- **THEN** the system SHALL throw a `CryptoException` due to GCM authentication tag failure

#### Scenario: Multi-algorithm encrypt/decrypt
- **WHEN** plaintext is encrypted with each of the 4 supported algorithms and decrypted with the corresponding algorithm
- **THEN** each roundtrip SHALL produce the original plaintext

### Requirement: KCV (Key Check Value) computation
The system SHALL compute a KCV for a given key using the algorithm specified by `SymmetricAlgorithm`. For AES algorithms, the KCV SHALL use AES with a deterministic IV (all zeros). For SM4 algorithms, the KCV SHALL use SM4 with a deterministic IV (all zeros) and a 16-byte key derived from the first 16 bytes of the DEK. The KCV SHALL be represented as a lowercase hex string.

#### Scenario: KCV consistency
- **WHEN** the same key and algorithm are used to compute KCV multiple times
- **THEN** the KCV output SHALL be identical each time

#### Scenario: KCV detects key change
- **WHEN** a different key is used to compute KCV with the same algorithm
- **THEN** the KCV output SHALL be different from the original

#### Scenario: KCV varies by algorithm
- **WHEN** the same key is used to compute KCV with different algorithms
- **THEN** each algorithm SHALL produce a different KCV value

### Requirement: Dual-key binding verification
The system SHALL compute a `key-binding` value as `HMAC-SHA-256(hmacKey, aesKey)` to bind the AES DEK and HMAC key together as a matched pair.

#### Scenario: Binding verification passes
- **WHEN** the stored binding matches `HMAC(hmacKey, aesKey)` computed from unwrapped keys
- **THEN** the system SHALL proceed with startup

#### Scenario: Binding verification fails on key mismatch
- **WHEN** the AES key is from one vault entry and the HMAC key is from another
- **THEN** the binding verification SHALL fail and the system SHALL refuse to start with a `FatalCryptoException`

### Requirement: Hex encoding and decoding
The system SHALL use Java 17 `HexFormat` or BouncyCastle `Hex` for all hex string operations. The system SHALL NOT use `javax.xml.bind.DatatypeConverter`.

#### Scenario: Hex roundtrip
- **WHEN** a byte array is encoded to hex and decoded back
- **THEN** the result SHALL be identical to the original byte array

