## MODIFIED Requirements

### Requirement: Transparent encryption on write (BeforeSaveEvent)
The system SHALL register a listener that intercepts `BeforeSaveEvent`. For each entity, it SHALL look up `@Encrypted` fields via `EntityMetadataCache` (including nested fields discovered by recursive scanning). For each encrypted field, it SHALL:
1. Navigate the Java object using the `accessors` chain to obtain the leaf field value
2. If any intermediate object in the chain is null, skip the field
3. Serialize the value using `TypeSerializer`, encrypt it with `CryptoCodec`
4. Navigate the BSON Document using the `path` segments to locate the target nested Document
5. Replace the leaf field value with an encrypted sub-document `{c: Binary, b?: String, _e: 1, _t: String, _k: String, _a: String}`

#### Scenario: Entity with one encrypted String field
- **WHEN** `repository.save(user)` is called where `user.phone = "13900001111"` and phone has `@Encrypted(blindIndex = true)`
- **THEN** the BSON Document sent to MongoDB SHALL have `phone` replaced with `{c: <Binary>, b: "hex_hash", _e: 1, _t: "STR"}`

#### Scenario: Entity with null encrypted field
- **WHEN** an encrypted field's value is null (or an intermediate nested object is null)
- **THEN** the field SHALL remain unchanged in the BSON Document (no encryption performed)

#### Scenario: Entity without encrypted fields
- **WHEN** an entity has no `@Encrypted` fields
- **THEN** the listener SHALL not modify the BSON Document

#### Scenario: Entity with nested @Encrypted field
- **WHEN** `repository.save(user)` is called where `user.address.street = "xx路"` and `Address.street` has `@Encrypted`
- **THEN** the BSON Document SHALL have `address.street` replaced with `{c: <Binary>, _e: 1, _t: "STR", _k: "...", _a: "..."}` while `address` remains a Document with other non-encrypted fields intact

#### Scenario: Entity with null intermediate nested object
- **WHEN** `repository.save(user)` is called where `user.address = null` and `Address.street` has `@Encrypted`
- **THEN** the listener SHALL skip the `address.street` encryption entirely (no error, no BSON modification)

#### Scenario: Entity with whole-object encrypted POJO field
- **WHEN** `repository.save(user)` is called where `user.address` has `@Encrypted` (whole-object) and `address` contains `{street: "xx", city: "sh"}`
- **THEN** the BSON Document SHALL have `address` replaced with `{c: <Binary(encrypt(bsonBytes))>, _e: 1, _t: "DOC", _k: "...", _a: "..."}` (single encrypted blob, no internal field structure visible)

#### Scenario: Whole-object encrypted field is null
- **WHEN** `repository.save(user)` is called where `user.address` has `@Encrypted` (whole-object) and `address = null`
- **THEN** the field SHALL remain null in the BSON Document (no encryption performed)

### Requirement: Transparent decryption on read (BeforeConvertEvent)
The system SHALL decrypt `@Encrypted` fields during document read. For each encrypted field metadata (including nested fields), it SHALL:
1. Navigate the BSON Document using the `path` segments to locate the nested encrypted sub-document
2. If any intermediate path segment is missing or not a Document, skip the field
3. Decrypt the `c` field, deserialize according to `_t` type marker
4. Replace the encrypted sub-document with the original typed value at the nested position

#### Scenario: Read entity with encrypted String field
- **WHEN** MongoDB returns a document where `phone` is `{c: <Binary>, _e: 1, _t: "STR"}`
- **THEN** the system SHALL decrypt `c`, replace `phone` in the Document with the plaintext String

#### Scenario: Read entity with nested encrypted field
- **WHEN** MongoDB returns a document where `address` is a Document containing `street: {c: <Binary>, _e: 1, _t: "STR", _k: "...", _a: "..."}`
- **THEN** the system SHALL navigate to `address.street`, decrypt the sub-document, and replace it with the plaintext String, leaving other `address` fields unchanged

#### Scenario: Read entity with missing intermediate path
- **WHEN** MongoDB returns a document where `address` field is missing
- **THEN** the system SHALL skip decryption of any nested fields under `address` without error

#### Scenario: Read entity with whole-object encrypted POJO field
- **WHEN** MongoDB returns a document where `address` is `{c: <Binary>, _e: 1, _t: "DOC", _k: "...", _a: "..."}`
- **THEN** the system SHALL decrypt `c`, deserialize BSON bytes back to an `Address` POJO using `MappingMongoConverter`, and set it as the `address` field value
