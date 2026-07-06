## ADDED Requirements

### Requirement: FieldCryptoService decryptDocument API
The system SHALL provide a `FieldCryptoService` Spring Bean that exposes `Document decryptDocument(Document rawDocument, Class<?> entityClass)`. The method SHALL iterate over all `@Encrypted` fields declared in `entityClass`, locate encrypted sub-documents in `rawDocument`, decrypt them, and replace the sub-document with the plaintext value in-place. The method SHALL return the same `Document` instance for fluent/chain usage.

#### Scenario: Decrypt a raw Document obtained from aggregation pipeline
- **WHEN** a raw `Document` is obtained via `MongoTemplate.aggregate()` containing `phone` as `{c: <Binary>, _e: 1, _t: "STR", _k: "v1-a3b2c1d4", _a: "AES_256_GCM"}` and `User.class` has `@Encrypted` on the `phone` field
- **THEN** `fieldCryptoService.decryptDocument(doc, User.class)` SHALL decrypt the `c` field using the DEK for kid `v1-a3b2c1d4` with algorithm `AES_256_GCM`, deserialize according to `_t: "STR"`, and replace the sub-document with the plaintext String

#### Scenario: Decrypt raw Document with multiple encrypted fields
- **WHEN** a raw `Document` contains encrypted sub-documents for both `phone` and `idCard` fields
- **THEN** `decryptDocument` SHALL decrypt both fields and replace each sub-document with its respective plaintext value in a single call

#### Scenario: Decrypt raw Document with typed encrypted field (Integer)
- **WHEN** a raw `Document` contains `age` as `{c: <Binary>, _e: 1, _t: "INT", _k: "v1-xxx", _a: "SM4_GCM"}`
- **THEN** `decryptDocument` SHALL decrypt `c` with SM4-GCM using the DEK for kid `v1-xxx`, deserialize the plaintext bytes as Integer, and replace `age` with the Integer value

#### Scenario: Decrypt raw Document with typed encrypted field (LocalDate)
- **WHEN** a raw `Document` contains `birthDate` as `{c: <Binary>, _e: 1, _t: "LDATE", _k: "v1-xxx", _a: "AES_256_CBC"}`
- **THEN** `decryptDocument` SHALL decrypt and deserialize as ISO-8601 LocalDate, replacing `birthDate` with a BSON-compatible date value

#### Scenario: Return the same Document instance
- **WHEN** `decryptDocument(doc, User.class)` is called
- **THEN** the method SHALL modify `doc` in-place and return the same reference (not a copy)

### Requirement: Null-safe and idempotent processing
`FieldCryptoService.decryptDocument` SHALL gracefully handle edge cases without throwing exceptions for non-critical conditions.

#### Scenario: Null Document input
- **WHEN** `decryptDocument(null, User.class)` is called
- **THEN** the method SHALL throw `IllegalArgumentException`

#### Scenario: Null entity class input
- **WHEN** `decryptDocument(doc, null)` is called
- **THEN** the method SHALL throw `IllegalArgumentException`

#### Scenario: Entity class without encrypted fields
- **WHEN** `decryptDocument(doc, SomePlainEntity.class)` is called and `SomePlainEntity` has no `@Encrypted` fields
- **THEN** the method SHALL return `doc` unmodified without error

#### Scenario: Encrypted field absent from Document
- **WHEN** `rawDocument` does not contain a key for an `@Encrypted` field declared in `entityClass`
- **THEN** the method SHALL skip that field without error

#### Scenario: Field value is null in Document
- **WHEN** an `@Encrypted` field's value in `rawDocument` is `null` (not a sub-document)
- **THEN** the method SHALL skip that field without error

#### Scenario: Field value is already decrypted (non-Document type)
- **WHEN** an `@Encrypted` field's value in `rawDocument` is a `String` (i.e., already decrypted or stored as plaintext)
- **THEN** the method SHALL skip that field without error

#### Scenario: Sub-document missing _e marker
- **WHEN** an `@Encrypted` field's value is a `Document` but does not contain `_e: 1`
- **THEN** the method SHALL skip that field without error

#### Scenario: Idempotent — calling decryptDocument twice on the same Document
- **WHEN** `decryptDocument` is called on an already-decrypted Document (field values are no longer sub-documents with `_e: 1`)
- **THEN** the second call SHALL be a no-op for already-decrypted fields and return the Document unchanged

### Requirement: Multi-DEK and key rotation support
`FieldCryptoService.decryptDocument` SHALL support the multi-DEK envelope encryption model, reading the kid from the `_k` field of each encrypted sub-document and looking up the corresponding DEK via `KeyVaultService.getDek(kid)`. Decryption SHALL succeed even if the key version is in `ROTATED` status (historical key).

#### Scenario: Decrypt field encrypted with rotated key version
- **WHEN** an encrypted sub-document has `_k: "v1"` and key version `v1` has status `ROTATED`
- **THEN** `decryptDocument` SHALL successfully decrypt the field using the ROTATED DEK

#### Scenario: Sub-document missing _k field (incompatible legacy format)
- **WHEN** an encrypted sub-document has `_e: 1` but does not contain a `_k` field
- **THEN** the method SHALL throw `FatalCryptoException` indicating incompatible format

### Requirement: Algorithm dispatch from sub-document
`FieldCryptoService.decryptDocument` SHALL read the algorithm from the `_a` field of each encrypted sub-document and dispatch to the correct decryptor. If `_a` is absent, the method SHALL default to `AES_256_GCM` for backward compatibility.

#### Scenario: Decrypt with explicit SM4-GCM algorithm marker
- **WHEN** an encrypted sub-document has `_a: "SM4_GCM"`
- **THEN** `decryptDocument` SHALL use the SM4-GCM decryptor with the kid-specific DEK

#### Scenario: Decrypt with absent algorithm marker (backward compatibility)
- **WHEN** an encrypted sub-document does not contain an `_a` field
- **THEN** `decryptDocument` SHALL default to `AES_256_GCM` for decryption

### Requirement: CryptoMappingMongoConverter delegates to FieldCryptoService
`CryptoMappingMongoConverter.decryptFields()` SHALL delegate its decryption logic to `FieldCryptoService.decryptDocument()`. The Converter retains responsibility for *when* to decrypt (during `read()` and `project()`); the Service owns *how* to decrypt.

#### Scenario: Transparent decryption still works after refactoring
- **WHEN** `repository.findById(id)` returns an entity with encrypted fields
- **THEN** the entity SHALL be returned with all encrypted fields decrypted, identical to pre-refactoring behavior
