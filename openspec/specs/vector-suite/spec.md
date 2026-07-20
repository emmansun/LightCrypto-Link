## ADDED Requirements

### Requirement: Vector Suite directory structure
The system SHALL maintain a `vectors/` directory at the repository root (not a Maven module) containing golden test vectors in JSON format. The directory structure SHALL be:
- `vectors/manifest.json` — suite metadata and file integrity hashes
- `vectors/encryption/<algorithm>.json` — encryption/decryption vectors per algorithm
- `vectors/blind-index/hmac-sha256.json` — blind index determinism vectors
- `vectors/kcv/kcv.json` — Key Check Value vectors
- `vectors/roundtrip/roundtrip.json` — random-IV decryption verification vectors

#### Scenario: All vector files present
- **WHEN** the vector suite is validated
- **THEN** all files listed in manifest.json SHALL exist and their SHA-256 hashes SHALL match

#### Scenario: Vector suite is language-agnostic
- **WHEN** a non-Java SDK consumes the vector suite
- **THEN** it SHALL be able to read the JSON files directly without any Java/Maven tooling

### Requirement: Manifest integrity
The `manifest.json` SHALL contain: `vectorSuiteVersion` (semver), `wireFormatVersion` (integer), `interopVersion` (integer), `generatedAt` (ISO-8601), and a `files` array where each entry has `path` (relative) and `sha256` (hex hash of file content).

#### Scenario: Manifest lists all vector files
- **WHEN** reading manifest.json
- **THEN** every JSON file in the vectors/ directory (excluding manifest.json itself) SHALL be listed in the files array

#### Scenario: Tampered vector file detected
- **WHEN** a vector file's content is modified after manifest generation
- **THEN** SHA-256 verification SHALL fail for that file

### Requirement: Encryption vector schema
Each encryption vector entry SHALL contain:
- `id`: unique identifier (e.g., "enc-aes256gcm-001")
- `algorithm`: algorithm name string
- `algorithmId`: integer (1-4)
- `input.keyHex`: 32-byte key in hex
- `input.plaintextHex`: plaintext in hex
- `input.namespace`: canonical namespace string
- `input.dekVersion`: integer
- `input.ivHex`: fixed IV in hex (for deterministic verification)
- `expected.wireFormatHex`: complete Wire Format V1 blob in hex
- `expected.ciphertextHex`: ciphertext portion in hex (including GCM tag)

#### Scenario: Encryption vector verification
- **WHEN** the Java SDK encrypts `input.plaintextHex` with `input.keyHex`, `input.ivHex`, `input.namespace`, `input.dekVersion`
- **THEN** the output blob hex SHALL exactly match `expected.wireFormatHex`

#### Scenario: Decryption vector verification
- **WHEN** the Java SDK decodes and decrypts `expected.wireFormatHex` with `input.keyHex`
- **THEN** the output plaintext hex SHALL match `input.plaintextHex`

### Requirement: Blind index vector schema
Each blind index vector entry SHALL contain:
- `id`: unique identifier
- `input.masterHmacKeyHex`: 32-byte master key in hex
- `input.namespace`: canonical namespace string
- `input.fieldName`: field name string
- `input.plaintext`: plaintext value string
- `input.normalization`: normalization rule applied ("trim+lowercase" or "none")
- `expected.derivedHmacKeyHex`: HKDF-derived key in hex
- `expected.blindIndexBase64url`: final blind index string

#### Scenario: Blind index vector verification
- **WHEN** computing blind index with the given master key, namespace, field name, and plaintext
- **THEN** the derived key SHALL match `expected.derivedHmacKeyHex` and the blind index SHALL match `expected.blindIndexBase64url`

### Requirement: Minimum vector coverage
The vector suite SHALL contain at minimum: 5 vectors per encryption algorithm (20 total), 5 blind index vectors, 4 KCV vectors (one per algorithm), and 4 roundtrip vectors.

#### Scenario: Suite meets minimum coverage
- **WHEN** counting vectors across all files
- **THEN** there SHALL be ≥ 20 encryption vectors, ≥ 5 blind index vectors, ≥ 4 KCV vectors, ≥ 4 roundtrip vectors
