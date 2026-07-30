## MODIFIED Requirements

### Requirement: Wire Format V1 byte layout
The system SHALL encode encrypted payloads using the following binary layout:
- Offset 0, 1 byte: `version` = 0x01 (fixed)
- Offset 1, 1 byte: `algorithmId` (0x01=AES_256_GCM, 0x02=AES_256_CBC, 0x03=SM4_GCM, 0x04=SM4_CBC)
- Offset 2, 2 bytes: `namespaceLength` (big-endian uint16, MUST be ≥ 1)
- Offset 4, N bytes: `namespace` (UTF-8 encoded)
- Offset 4+N, 4 bytes: `dekVersion` (big-endian uint32, MUST be ≥ 1)
- Offset 8+N, 1 byte: `ivLength` (GCM=12, CBC=16)
- Offset 9+N, IV bytes: `iv` (CSPRNG random)
- Offset 9+N+IV, 2 bytes: `aadExtLength` (big-endian uint16, V1: MUST be 0)
- Offset 11+N+IV, A bytes: `aadExtension` (V1: empty)
- Remaining bytes: `ciphertext` (GCM: CT‖Tag(16B); CBC: PKCS7-padded CT)

#### Scenario: Encode produces correct byte layout
- **WHEN** encrypting "Hello" with AES_256_GCM, namespace "default.default.User#email", dekVersion 1
- **THEN** the output blob SHALL start with bytes `0x01 0x01` (version + algId), followed by 2-byte namespace length, the UTF-8 namespace, 4-byte dekVersion, 1-byte ivLength (12), 12-byte IV, 2-byte aadExtLength (0x0000), then ciphertext+tag

#### Scenario: Decode roundtrip
- **WHEN** a Wire Format V1 blob is encoded and then decoded
- **THEN** the decoded fields (version, algorithmId, namespace, dekVersion, iv, aadExt, ciphertext) SHALL be identical to the original inputs

#### Scenario: Reject invalid version byte
- **WHEN** decoding a blob whose first byte is not 0x01
- **THEN** the system SHALL throw `PayloadCorruptionException` indicating unsupported wire format version

#### Scenario: Reject zero namespace length
- **WHEN** decoding a blob whose namespaceLength field is 0
- **THEN** the system SHALL throw `PayloadCorruptionException` indicating namespace is required

### Requirement: Algorithm ID registry
The system SHALL define algorithm IDs as: 0x01=AES_256_GCM, 0x02=AES_256_CBC, 0x03=SM4_GCM, 0x04=SM4_CBC. The encoder SHALL map `SymmetricAlgorithm` enum values to their corresponding byte IDs. The decoder SHALL map byte IDs back to enum values.

#### Scenario: Unknown algorithm ID rejected
- **WHEN** decoding a blob with algorithmId byte 0xFF
- **THEN** the system SHALL throw `UnsupportedAlgorithmException` with `algorithmId` = 255

#### Scenario: All registered algorithms encode correctly
- **WHEN** encoding with each of the 4 supported algorithms
- **THEN** the algorithmId byte SHALL be 0x01, 0x02, 0x03, 0x04 respectively
