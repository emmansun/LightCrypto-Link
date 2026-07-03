## ADDED Requirements

### Requirement: AES-256-GCM encryption and decryption
The system SHALL encrypt plaintext using AES-256-GCM with a randomly generated 12-byte IV per operation. The ciphertext SHALL be stored as `IV (12 bytes) || GCM-ciphertext (including 16-byte auth tag)` in a single Binary blob.

#### Scenario: Encrypt produces unique ciphertext
- **WHEN** the same plaintext is encrypted twice
- **THEN** the two resulting Binary blobs SHALL be different (due to random IV)

#### Scenario: Decrypt roundtrip
- **WHEN** a plaintext is encrypted and then decrypted
- **THEN** the decrypted value SHALL be identical to the original plaintext

#### Scenario: Tampered ciphertext detection
- **WHEN** a ciphertext is modified after encryption and then decryption is attempted
- **THEN** the system SHALL throw a `CryptoException` due to GCM authentication tag failure

### Requirement: KCV (Key Check Value) computation
The system SHALL compute a KCV for a given key by encrypting a fixed zero block with AES-256-GCM using a deterministic IV (all zeros). The KCV SHALL be represented as a lowercase hex string.

#### Scenario: KCV consistency
- **WHEN** the same key is used to compute KCV multiple times
- **THEN** the KCV output SHALL be identical each time

#### Scenario: KCV detects key change
- **WHEN** a different key is used to compute KCV
- **THEN** the KCV output SHALL be different from the original

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
