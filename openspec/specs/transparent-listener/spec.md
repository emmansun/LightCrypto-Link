## MODIFIED Requirements

### Requirement: Transparent encryption on write (BeforeSaveEvent)
The system SHALL intercept entity save operations. For each entity, it SHALL look up `@Encrypted` fields via `EntityMetadataCache`, serialize each field value using `TypeSerializer`, encrypt it with `CryptoCodec`, and replace the field value with an encrypted payload constructed via `StorageAdapter.buildEncryptedPayload(blob, typeMarker, blindIndex)`. The event binding mechanism (e.g., MongoDB `AbstractMongoEventListener`) is provided by the active adapter module.

#### Scenario: Entity with one encrypted String field
- **WHEN** `repository.save(user)` is called where `user.phone = "13900001111"` and phone has `@Encrypted(blindIndex = true)`
- **THEN** the field SHALL be replaced with the result of `StorageAdapter.buildEncryptedPayload(blob, "STR", blindIndexHex)`

#### Scenario: Entity with null encrypted field
- **WHEN** an encrypted field's value is null
- **THEN** the field SHALL remain null (no encryption performed, no StorageAdapter call)

#### Scenario: Entity without encrypted fields
- **WHEN** an entity has no `@Encrypted` fields
- **THEN** the system SHALL not invoke StorageAdapter for any field

### Requirement: Transparent decryption on read
The system SHALL intercept entity read operations. For each entity type with `@Encrypted` fields, it SHALL inspect the raw field value, check `StorageAdapter.isEncryptedPayload(value)`, extract the blob via `StorageAdapter.extractBlob(payload)`, decrypt using `CryptoCodec`, parse the result according to `StorageAdapter.extractTypeMarker(payload)`, and replace the payload with the original typed value.

#### Scenario: Read entity with encrypted String field
- **WHEN** a document is read where `phone` is an encrypted payload (isEncryptedPayload returns true)
- **THEN** the system SHALL extract blob via `StorageAdapter.extractBlob()`, decrypt, parse as String per type marker, and replace the field value

#### Scenario: Read entity with encrypted Integer field
- **WHEN** a document is read where `age` is an encrypted payload with type marker "INT"
- **THEN** the system SHALL decrypt, parse as Integer, and replace the field value

#### Scenario: Read entity with encrypted LocalDate field
- **WHEN** a document is read where `birthDate` is an encrypted payload with type marker "LDATE"
- **THEN** the system SHALL decrypt, parse as ISO-8601 LocalDate, and replace the field value

#### Scenario: Read plaintext field (not encrypted)
- **WHEN** a document is read where a field value is NOT an encrypted payload (`isEncryptedPayload` returns false)
- **THEN** the system SHALL leave the field value unchanged (supports mixed encrypted/plaintext data)

### Requirement: Null-safe listener processing
The system SHALL gracefully handle missing, null, or already-processed fields without throwing exceptions.

#### Scenario: Missing encrypted field in document
- **WHEN** a document does not contain a field that is marked `@Encrypted` in the entity
- **THEN** the system SHALL skip that field without error
