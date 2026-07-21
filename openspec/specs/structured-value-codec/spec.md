## ADDED Requirements

### Requirement: StructuredValueCodec SPI interface
The system SHALL provide a `StructuredValueCodec` interface in `lcl-spi` that abstracts serialization/deserialization of structured values (DOC, COL, MAP type markers) during encrypt/decrypt operations. The interface SHALL define:
- `byte[] encode(Object structuredValue, String typeMarker)` — serialize a structured value to bytes
- `Object decode(byte[] data, String typeMarker)` — deserialize bytes back to a structured value

#### Scenario: Encode a nested document for encryption
- **WHEN** `codec.encode(nestedDocument, "DOC")` is called with a document-like object
- **THEN** the codec SHALL serialize the object to a byte array suitable for encryption

#### Scenario: Decode decrypted bytes back to a document
- **WHEN** `codec.decode(plaintextBytes, "DOC")` is called
- **THEN** the codec SHALL reconstruct the document-like object from bytes

#### Scenario: Decode a collection payload
- **WHEN** `codec.decode(plaintextBytes, "COL")` is called
- **THEN** the codec SHALL return a List of objects deserialized from the encoded collection

#### Scenario: Decode a map payload
- **WHEN** `codec.decode(plaintextBytes, "MAP")` is called
- **THEN** the codec SHALL return a Map-like object deserialized from the encoded map

### Requirement: BSON implementation of StructuredValueCodec
The `lcl-adapter-mongodb` module SHALL provide a `BsonStructuredValueCodec` implementation that uses `DocumentCodec` + `RawBsonDocument` for encode/decode operations.

#### Scenario: BSON encode produces valid BSON binary
- **WHEN** `bsonCodec.encode(document, "DOC")` is called with a BSON `Document`
- **THEN** the output SHALL be valid BSON binary encoding of that document

#### Scenario: BSON decode reconstructs Document
- **WHEN** `bsonCodec.decode(bsonBytes, "DOC")` is called with valid BSON bytes
- **THEN** the result SHALL be a `Document` equivalent to the original

#### Scenario: BSON decode COL returns List
- **WHEN** `bsonCodec.decode(bsonBytes, "COL")` is called
- **THEN** the result SHALL be a `List<Object>` extracted from the `_v` field of the encoded BSON document

### Requirement: DocumentAccessor SPI interface
The system SHALL provide a `DocumentAccessor` interface in `lcl-spi` that abstracts field-level access on document-like objects, decoupling the starter from any specific document type (BSON Document, Map, etc.).

The interface SHALL define:
- `Object getField(Object document, String field)` — read a field value
- `void setField(Object document, String field, Object value)` — write a field value
- `boolean isDocumentLike(Object value)` — check if a value is a nested document
- `Iterable<?> asList(Object value)` — adapt a value as an iterable list
- `Iterable<Map.Entry<String, Object>> asMap(Object value)` — adapt a value as key-value pairs

#### Scenario: getField on a BSON Document
- **WHEN** `accessor.getField(bsonDoc, "phone")` is called
- **THEN** the accessor SHALL return the value at key "phone" in the Document

#### Scenario: setField modifies document in-place
- **WHEN** `accessor.setField(bsonDoc, "phone", "plaintext")` is called
- **THEN** the BSON Document SHALL have "phone" replaced with "plaintext"

#### Scenario: isDocumentLike returns true for nested documents
- **WHEN** `accessor.isDocumentLike(nestedDoc)` is called with a BSON Document
- **THEN** the result SHALL be `true`

#### Scenario: isDocumentLike returns false for scalar values
- **WHEN** `accessor.isDocumentLike("plaintext")` is called with a String
- **THEN** the result SHALL be `false`

### Requirement: BSON implementation of DocumentAccessor
The `lcl-adapter-mongodb` module SHALL provide a `BsonDocumentAccessor` implementation that operates on `org.bson.Document` instances.

#### Scenario: BsonDocumentAccessor handles null document gracefully
- **WHEN** `accessor.getField(null, "field")` is called
- **THEN** the accessor SHALL return `null` without throwing

#### Scenario: BsonDocumentAccessor asList wraps List values
- **WHEN** `accessor.asList(listValue)` is called with a `List<?>` value
- **THEN** the result SHALL be an iterable over the list elements

#### Scenario: BsonDocumentAccessor asMap wraps Document entries
- **WHEN** `accessor.asMap(bsonDoc)` is called with a `Document`
- **THEN** the result SHALL be an iterable over the document's key-value entries
