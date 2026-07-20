## MODIFIED Requirements

### Requirement: AES-256-GCM encryption and decryption
The system SHALL encrypt plaintext using the algorithm specified by `SymmetricAlgorithm`. The encryption output SHALL be a Base64URL-encoded Wire Format V1 blob containing: version byte, algorithm ID, namespace (canonical form), DEK version, IV, AAD extension (empty in V1), and ciphertext. For GCM modes, the ciphertext SHALL include the 16-byte authentication tag appended at the end. AAD SHALL be implicitly bound as `version ‖ algorithmId ‖ namespace_bytes ‖ dekVersion_bytes`. Other algorithms (AES-256-CBC, SM4-GCM, SM4-CBC) SHALL use their respective IV lengths, key sizes, and cipher modes as defined by the `SymmetricEncryptor` strategy.

#### Scenario: Encrypt produces unique ciphertext
- **WHEN** the same plaintext is encrypted twice with the same algorithm, namespace, and dekVersion
- **THEN** the two resulting Wire Format blobs SHALL differ (due to random IV), but both SHALL decode to the same plaintext

#### Scenario: Decrypt roundtrip
- **WHEN** a plaintext is encrypted into a Wire Format V1 blob and then decrypted with the same DEK
- **THEN** the decrypted value SHALL be identical to the original plaintext

#### Scenario: Tampered ciphertext detection (GCM modes)
- **WHEN** a ciphertext encrypted with a GCM mode is modified after encryption and then decryption is attempted
- **THEN** the system SHALL throw a `CryptoException` due to GCM authentication tag failure

#### Scenario: Tampered namespace detection (GCM modes)
- **WHEN** the namespace bytes in a Wire Format blob are modified after encryption
- **THEN** GCM decryption SHALL fail because the reconstructed AAD no longer matches the original

#### Scenario: Multi-algorithm encrypt/decrypt
- **WHEN** plaintext is encrypted with each of the 4 supported algorithms and decrypted with the corresponding algorithm
- **THEN** each roundtrip SHALL produce the original plaintext
