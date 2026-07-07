## Purpose

Define the behavioral contract for the blind-index capability to match current implementation behavior.

## Requirements
### Requirement: HMAC-SHA-256 blind index generation
The system SHALL generate a blind index by computing `HMAC-SHA-256(hmacKey, effectiveFieldName + ":" + serializedValue)` and returning the result as a lowercase hex string.

#### Scenario: Deterministic blind index
- **WHEN** the same plaintext and fieldName are hashed multiple times
- **THEN** the blind index output SHALL be identical each time

#### Scenario: Field name isolation
- **WHEN** the same value is hashed with fieldName "phone" and fieldName "ssn"
- **THEN** the two blind index outputs SHALL be different

#### Scenario: Null value handling
- **WHEN** the plaintext value is null
- **THEN** the blind index SHALL return null (no index generated)

### Requirement: Blind index conditional storage
The system SHALL include the `b` field in the BSON sub-document only when the field's `@Encrypted(blindIndex = true)` is set.

#### Scenario: blindIndex enabled
- **WHEN** a field has `@Encrypted(blindIndex = true)` and is encrypted
- **THEN** the BSON sub-document SHALL contain `{c: Binary, b: "hex_hash", _e: 1, _t: "STR"}`

#### Scenario: blindIndex disabled (default)
- **WHEN** a field has `@Encrypted` (blindIndex defaults to false) and is encrypted
- **THEN** the BSON sub-document SHALL contain `{c: Binary, _e: 1, _t: "STR"}` without a `b` field

### Requirement: Blind index uses TypeSerializer
The system SHALL serialize the value using `TypeSerializer` before computing the HMAC, ensuring type-consistent hashing across Java types.

#### Scenario: Integer blind index consistency
- **WHEN** an Integer field with value 28 is blind-indexed
- **THEN** the HMAC input SHALL be `"fieldName:28"` (serialized via `String.valueOf`)


