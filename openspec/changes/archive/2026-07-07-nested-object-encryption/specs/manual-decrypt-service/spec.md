## MODIFIED Requirements

### Requirement: FieldCryptoService decryptDocument API
The system SHALL provide a `FieldCryptoService` Spring Bean that exposes `Document decryptDocument(Document rawDocument, Class<?> entityClass)`. The method SHALL iterate over all `@Encrypted` fields declared in `entityClass` — including nested fields discovered by recursive scanning — navigate the BSON Document using each field's `path` segments to locate the nested encrypted sub-document, decrypt it, and replace the sub-document with the plaintext value in-place. If any intermediate path segment is missing or not a Document, the field SHALL be skipped. The method SHALL return the same `Document` instance.

#### Scenario: Decrypt a raw Document obtained from aggregation pipeline
- **WHEN** a raw `Document` is obtained via `MongoTemplate.aggregate()` containing `phone` as `{c: <Binary>, _e: 1, _t: "STR", _k: "v1-a3b2c1d4", _a: "AES_256_GCM"}` and `User.class` has `@Encrypted` on the `phone` field
- **THEN** `fieldCryptoService.decryptDocument(doc, User.class)` SHALL decrypt the `c` field using the DEK for kid `v1-a3b2c1d4` with algorithm `AES_256_GCM`, deserialize according to `_t: "STR"`, and replace the sub-document with the plaintext String

#### Scenario: Decrypt raw Document with nested encrypted field
- **WHEN** a raw `Document` contains `address` as a Document with `street: {c: <Binary>, _e: 1, _t: "STR", _k: "v1-xxx", _a: "AES_256_GCM"}` and `User.class` has `Address.street` with `@Encrypted`
- **THEN** `fieldCryptoService.decryptDocument(doc, User.class)` SHALL navigate to `address.street`, decrypt the sub-document, and replace `address.street` with the plaintext String

#### Scenario: Decrypt raw Document with missing nested path
- **WHEN** a raw `Document` does not contain the `address` key and `User.class` has `Address.street` with `@Encrypted`
- **THEN** the method SHALL skip the `address.street` field without error

#### Scenario: Decrypt raw Document with typed encrypted field (Integer)
- **WHEN** a raw `Document` contains `age` as `{c: <Binary>, _e: 1, _t: "INT", _k: "v1-xxx", _a: "SM4_GCM"}`
- **THEN** `decryptDocument` SHALL decrypt `c` with SM4-GCM using the DEK for kid `v1-xxx`, deserialize the plaintext bytes as Integer, and replace `age` with the Integer value

#### Scenario: Return the same Document instance
- **WHEN** `decryptDocument(doc, User.class)` is called
- **THEN** the method SHALL modify `doc` in-place and return the same reference (not a copy)
