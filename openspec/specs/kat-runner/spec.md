## ADDED Requirements

### Requirement: KAT vector loading from classpath
The system SHALL load KAT (Known Answer Test) vectors from classpath resources at `/kat/encryption/`, `/kat/blind-index/`, and `/kat/kcv/`. Vector files SHALL be JSON format matching the schema defined in the project `vectors/manifest.json`. The vector loader SHALL verify file existence and parse JSON.

#### Scenario: Load AES-256-GCM vectors
- **WHEN** KatRunner initializes
- **THEN** it SHALL load `/kat/encryption/aes-256-gcm.json` from classpath and parse the test vectors

#### Scenario: Missing vector file
- **WHEN** a required vector file is not found on classpath
- **THEN** KatRunner SHALL report a FATAL failure with error message identifying the missing file

### Requirement: Cryptographic KAT execution
The system SHALL execute KAT for the following algorithms: AES-256-GCM, SM4-GCM, AES-256-CBC, SM4-CBC. For each algorithm, the KAT SHALL encrypt the vector plaintext with the vector key and IV, then compare the output against the expected ciphertext. KAT SHALL NOT use system randomness (deterministic).

#### Scenario: AES-256-GCM KAT passes
- **WHEN** the AES-256-GCM vector encryption output matches the expected ciphertext
- **THEN** the KAT SHALL report success for AES-256-GCM

#### Scenario: AES-256-GCM KAT fails (tampered library)
- **WHEN** the encryption output does not match the expected ciphertext
- **THEN** the KAT SHALL report failure with the algorithm name and expected vs actual values

### Requirement: HKDF KAT execution
The system SHALL execute KAT for HKDF-SHA-256: derive a key from the vector input key material, salt, and info, then compare against the expected derived key bytes.

#### Scenario: HKDF KAT passes
- **WHEN** the HKDF derived key matches the expected output bytes
- **THEN** the KAT SHALL report success for HKDF-SHA-256

### Requirement: Blind index KAT execution
The system SHALL execute KAT for HMAC-SHA-256 blind index: compute HMAC over the vector input with the vector key, then compare against the expected hash output.

#### Scenario: Blind index KAT passes
- **WHEN** the HMAC-SHA-256 output matches the expected hash
- **THEN** the KAT SHALL report success for blind index HMAC-SHA-256

### Requirement: KAT timing budget
The total KAT execution SHALL complete within 200 milliseconds. Each individual primitive SHALL complete within 30 milliseconds. Exceeding the budget SHALL be treated as FATAL failure.

#### Scenario: KAT within budget
- **WHEN** all KAT primitives complete in 120ms total
- **THEN** the KAT phase SHALL report success with durationMicros recorded

#### Scenario: KAT exceeds budget
- **WHEN** total KAT execution takes 250ms
- **THEN** the KAT phase SHALL report FATAL failure with timeout indication

### Requirement: KAT determinism
KATs MUST NOT depend on system clock, random state, OS-specific libraries, CPU frequency, or any non-deterministic input. KAT results SHALL be identical across JVM versions, operating systems, and CPU architectures.

#### Scenario: Deterministic KAT across runs
- **WHEN** KAT is run twice with the same vectors
- **THEN** both runs SHALL produce byte-identical results
