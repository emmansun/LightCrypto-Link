## MODIFIED Requirements

### Requirement: Transparent encryption on write (BeforeSaveEvent)
The system SHALL register an `AbstractMongoEventListener` (located in `lcl-adapter-mongodb` module, package `io.github.emmansun.lightcrypto.adapter.mongodb`) that intercepts `BeforeSaveEvent`. For each entity, it SHALL look up `@Encrypted` fields via `EntityMetadataCache`, serialize each field value using `TypeSerializer`, encrypt it with `CryptoCodec`, and replace the field value in the BSON Document with an encrypted sub-document `{c: Binary, b?: String, _e: 1, _t: String}`. The listener SHALL be registered as a Spring Bean by `MongoAdapterAutoConfiguration`.

#### Scenario: Entity with one encrypted String field
- **WHEN** `repository.save(user)` is called where `user.phone = "13900001111"` and phone has `@Encrypted(blindIndex = true)`
- **THEN** the BSON Document sent to MongoDB SHALL have `phone` replaced with `{c: <Binary>, b: "hex_hash", _e: 1, _t: "STR"}`

#### Scenario: Entity with null encrypted field
- **WHEN** an encrypted field's value is null
- **THEN** the field SHALL remain null in the BSON Document (no encryption performed)

#### Scenario: Entity without encrypted fields
- **WHEN** an entity has no `@Encrypted` fields
- **THEN** the listener SHALL not modify the BSON Document

### Requirement: Transparent decryption on read (BeforeConvertEvent)
The system SHALL register a custom `CryptoMappingMongoConverter` (located in `lcl-adapter-mongodb` module) that intercepts `BeforeConvertEvent`. For each entity type with `@Encrypted` fields, it SHALL inspect the BSON Document, locate sub-documents with `_e: 1`, decrypt the `c` field using `CryptoCodec`, parse the result according to `_t` type marker, and replace the sub-document with the original typed value in the Document. The converter SHALL be registered as a Spring Bean by `MongoAdapterAutoConfiguration`.

#### Scenario: Read entity with encrypted String field
- **WHEN** MongoDB returns a document where `phone` is `{c: <Binary>, _e: 1, _t: "STR"}`
- **THEN** the listener SHALL decrypt `c`, replace `phone` in the Document with the plaintext String, and Spring Data SHALL map it to the Java entity's String field

#### Scenario: Read entity with encrypted Integer field
- **WHEN** MongoDB returns a document where `age` is `{c: <Binary>, _e: 1, _t: "INT"}`
- **THEN** the listener SHALL decrypt, parse as Integer, and replace `age` with the Integer value in the Document

#### Scenario: Read entity with encrypted LocalDate field
- **WHEN** MongoDB returns a document where `birthDate` is `{c: <Binary>, _e: 1, _t: "LDATE"}`
- **THEN** the listener SHALL decrypt, parse as ISO-8601 LocalDate, and replace `birthDate` with a BSON-compatible date value

### Requirement: Null-safe listener processing
The system SHALL gracefully handle missing, null, or already-processed fields without throwing exceptions.

#### Scenario: Missing encrypted field in document
- **WHEN** a BSON Document does not contain a field that is marked `@Encrypted` in the entity
- **THEN** the listener SHALL skip that field without error
