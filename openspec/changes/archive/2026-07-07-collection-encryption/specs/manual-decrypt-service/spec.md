## MODIFIED Requirements

### Requirement: FieldCryptoService decryptDocument API
The system SHALL decrypt `@Encrypted` fields in raw BSON Documents, including collection fields. For LIST_ITER path segments, the system SHALL iterate over each element in the BSON Array, decrypt each encrypted sub-document, and replace the Array with decrypted values. For MAP_ITER path segments, the system SHALL iterate over each value in the BSON Document, decrypt each encrypted sub-document, and replace with decrypted values.

#### Scenario: Decrypt raw Document with encrypted List<String>
- **WHEN** a raw Document contains `tags` as an Array of encrypted sub-documents and `Article.class` has `@Encrypted List<String> tags`
- **THEN** `fieldCryptoService.decryptDocument(doc, Article.class)` SHALL decrypt each element and replace `tags` with a List of plaintext Strings

#### Scenario: Decrypt raw Document with encrypted Map values
- **WHEN** a raw Document contains `settings` as a Document where each value is an encrypted sub-document
- **THEN** `fieldCryptoService.decryptDocument(doc, Entity.class)` SHALL decrypt each value and replace with plaintext values

#### Scenario: Decrypt raw Document with POJO collection containing encrypted fields
- **WHEN** a raw Document contains `addresses` as an Array of Documents, each containing `street` as an encrypted sub-document
- **THEN** `fieldCryptoService.decryptDocument(doc, User.class)` SHALL navigate each array element and decrypt the `street` sub-document within each
