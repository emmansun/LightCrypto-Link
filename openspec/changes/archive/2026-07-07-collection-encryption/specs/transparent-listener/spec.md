## MODIFIED Requirements

### Requirement: Transparent encryption on write (BeforeSaveEvent)
The system SHALL encrypt `@Encrypted` fields during BeforeSaveEvent. For collection fields (LIST_ITER/MAP_ITER path segments), the system SHALL iterate over each element/value using the unified traversal algorithm, encrypt each individually, and store the results as a BSON Array (for Collections) or BSON Document (for Maps) of encrypted sub-documents.

#### Scenario: Save entity with @Encrypted List<String>
- **WHEN** `repository.save(article)` is called where `article.tags = ["java", "spring"]`
- **THEN** the BSON Document SHALL have `tags` as an Array of 2 encrypted sub-documents

#### Scenario: Save entity with @Encrypted Map<String, String>
- **WHEN** `repository.save(entity)` is called where `entity.settings = {"theme": "dark"}`
- **THEN** the BSON Document SHALL have `settings` as a Document with `theme` key whose value is an encrypted sub-document

#### Scenario: Save entity with List<Address> containing @Encrypted field
- **WHEN** `repository.save(user)` is called where `user.addresses = [Address{street:"xx"}]` and `Address.street` has `@Encrypted`
- **THEN** the BSON Document SHALL have `addresses` as an Array where each element is a Document with `street` replaced by an encrypted sub-document

#### Scenario: Save entity with whole-collection encrypted POJO list
- **WHEN** `repository.save(user)` is called where `user.addresses` has `@Encrypted` (whole-collection) and contains `[Address{street:"xx"}]`
- **THEN** the BSON Document SHALL have `addresses` replaced with `{c: Binary(encrypt(bsonBytes)), _e: 1, _t: "COL", _k: "...", _a: "..."}` (single encrypted blob)

### Requirement: Transparent decryption on read
The system SHALL decrypt `@Encrypted` fields during document read. For collection fields, the system SHALL iterate over each element in the BSON Array/Document, decrypt each individually, and reconstruct the Java Collection/Map with decrypted values.

#### Scenario: Read entity with encrypted List<String>
- **WHEN** MongoDB returns `tags` as an Array of encrypted sub-documents
- **THEN** the system SHALL decrypt each element and reconstruct `tags` as a `List<String>` with plaintext values

#### Scenario: Read entity with encrypted Map values
- **WHEN** MongoDB returns `settings` as a Document where each value is an encrypted sub-document
- **THEN** the system SHALL decrypt each value and reconstruct `settings` as a `Map<String, String>` with plaintext values

#### Scenario: Read entity with whole-collection encrypted POJO list
- **WHEN** MongoDB returns `addresses` as `{c: Binary, _e: 1, _t: "COL", _k: "...", _a: "..."}`
- **THEN** the system SHALL decrypt `c`, deserialize BSON bytes back to a `List<Address>`, and set it as the `addresses` field value
