## ADDED Requirements

### Requirement: Encryption mode selection model
The system SHALL define and document a deterministic selection model for encrypted fields and object graphs.

#### Scenario: Default simple collection behavior remains element-level
- **WHEN** a field is declared as `@Encrypted List<String> tags`
- **THEN** the system SHALL keep element-level encryption as the default behavior for backward compatibility

#### Scenario: Queryable scalar collection field
- **WHEN** a field is declared as `@Encrypted(blindIndex = true) List<String> tags`
- **THEN** the system SHALL apply element-level encryption and blind-index storage per element, and query rewriting SHALL target the blind-index sub-field

#### Scenario: Non-queryable simple collection uses whole-object mode
- **WHEN** a field is declared as `@Encrypted(mode = WHOLE) List<String> tags`
- **THEN** the system SHALL apply whole-object encryption and store the field as a single encrypted payload with `_t` equal to `COL`

#### Scenario: Non-queryable simple map uses whole-object mode
- **WHEN** a field is declared as `@Encrypted(mode = WHOLE) Map<String, String> settings`
- **THEN** the system SHALL apply whole-object encryption and store the field as a single encrypted payload with `_t` equal to `MAP`

#### Scenario: Non-queryable sensitive object collection
- **WHEN** a field is declared as `@Encrypted(mode = WHOLE) List<Address> secureAddresses` and `Address` contains no nested `@Encrypted` fields
- **THEN** the system SHALL apply whole-object encryption and store the field as a single encrypted payload with `_t` equal to `COL`

### Requirement: Explicit mode selector
The system SHALL provide an explicit encryption mode selector on `@Encrypted` for collection and map fields to choose between element-level and whole-object behavior.

#### Scenario: Element mode on simple map
- **WHEN** a field is declared as `@Encrypted(mode = ELEMENT) Map<String, String> settings`
- **THEN** the system SHALL encrypt each map value independently and keep map keys in plaintext

#### Scenario: Mode selector on non-collection field
- **WHEN** a non-collection scalar field declares `@Encrypted(mode = WHOLE)`
- **THEN** the system SHALL treat it as valid and equivalent to existing scalar encrypted sub-document behavior

### Requirement: Fail-fast validation for invalid combinations
The system SHALL reject invalid encryption-mode combinations during metadata scanning at startup.

#### Scenario: Whole-object mode with blind index
- **WHEN** a field is declared as `@Encrypted(mode = WHOLE, blindIndex = true) List<Address> secureAddresses`
- **THEN** startup SHALL fail with an `IllegalStateException` indicating that blind index is unsupported for whole-object mode

#### Scenario: Whole-object mode mixed with nested encrypted fields
- **WHEN** a field is declared as `@Encrypted(mode = WHOLE) List<Address> secureAddresses` and `Address` contains nested `@Encrypted` fields
- **THEN** startup SHALL fail with an `IllegalStateException` indicating the mode conflict and required migration options

### Requirement: Canonical decision guidance
The system SHALL provide user-facing guidance that maps business intent to the correct encryption mode.

#### Scenario: Query requirement guidance
- **WHEN** users review the project documentation for modeling encrypted collections
- **THEN** the documentation SHALL state that query-required fields use element-level encryption and non-query high-confidentiality fields use `mode = WHOLE` whole-object encryption

#### Scenario: Storage-shape guidance
- **WHEN** users review storage examples
- **THEN** documentation SHALL include representative encrypted BSON examples for `DOC`, `COL`, and `MAP` payloads
