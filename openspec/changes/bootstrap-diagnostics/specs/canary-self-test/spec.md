## ADDED Requirements

### Requirement: Canary encrypt/decrypt roundtrip
The system SHALL perform a canary self-test that encrypts a fixed canary plaintext using the default algorithm, then decrypts the result, and verifies the decrypted bytes match the original. The canary SHALL use a dedicated namespace `lcl.diagnostics#canary` and canary DEK (embedded in SDK, not user keys).

#### Scenario: Canary roundtrip succeeds
- **WHEN** canary encrypt("LCL-CANARY-2026") produces blob, then decrypt(blob) produces "LCL-CANARY-2026"
- **THEN** the canary self-test SHALL report success

#### Scenario: Canary roundtrip fails
- **WHEN** decrypt(canaryBlob) does not produce the original canary plaintext
- **THEN** the canary self-test SHALL report FATAL failure with `lcl.bootstrap.canary.failed` event

### Requirement: Multi-algorithm canary
The canary self-test SHALL run for all configured allowed algorithms: AES-256-GCM, SM4-GCM, AES-256-CBC, SM4-CBC. Each algorithm SHALL be independently verified.

#### Scenario: All algorithms pass canary
- **WHEN** all four algorithms successfully encrypt/decrypt the canary value
- **THEN** the canary phase SHALL report success with per-algorithm durations

#### Scenario: One algorithm fails canary
- **WHEN** SM4-GCM canary fails but AES-256-GCM passes
- **THEN** the canary phase SHALL report failure identifying SM4-GCM as the failing algorithm

### Requirement: Canary metadata roundtrip
The canary SHALL also verify Wire Format V1 metadata encoding/decoding: encrypt canary, encode metadata to binary, decode binary back, and verify the decoded metadata matches the original.

#### Scenario: Metadata roundtrip succeeds
- **WHEN** canary encrypt → binary encode → binary decode produces matching metadata
- **THEN** the metadata canary SHALL report success

#### Scenario: Metadata roundtrip fails (format corruption)
- **WHEN** decoded metadata does not match original
- **THEN** the canary SHALL report FATAL failure with `lcl.bootstrap.metadata.failed` event

### Requirement: Canary failure behavior
Any canary failure SHALL be classified as FATAL. The system SHALL emit `lcl.bootstrap.canary.failed` with the failing test name and error details. In STRICT mode, this aborts startup.

#### Scenario: Fatal canary failure aborts bootstrap
- **WHEN** canary encrypt/decrypt fails
- **THEN** the BootstrapEngine SHALL abort with FATAL status and emit `lcl.bootstrap.canary.failed`
