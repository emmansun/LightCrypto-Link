## ADDED Requirements

### Requirement: Element-level encryption for Collection types
When a field of type `List<T>`, `Set<T>`, or `Collection<T>` is annotated with `@Encrypted` and `T` is a supported scalar type, the system SHALL encrypt each element individually and store the result as a BSON Array of encrypted sub-documents.

#### Scenario: @Encrypted List<String>
- **WHEN** an entity has `@Encrypted List<String> tags` with value `["java", "spring"]`
- **THEN** the BSON Document SHALL have `tags` as an Array containing 2 encrypted sub-documents, each with `{c: Binary, _e: 1, _t: "STR", _k: "...", _a: "..."}`

#### Scenario: @Encrypted Set<Integer>
- **WHEN** an entity has `@Encrypted Set<Integer> codes` with value `[100, 200]`
- **THEN** the BSON Document SHALL have `codes` as an Array of 2 encrypted sub-documents with `_t: "INT"`

#### Scenario: Empty collection
- **WHEN** an entity has `@Encrypted List<String> tags` with value `[]`
- **THEN** the BSON Document SHALL have `tags` as an empty Array

#### Scenario: Null collection
- **WHEN** an entity has `@Encrypted List<String> tags` with value `null`
- **THEN** the field SHALL remain null in the BSON Document (no encryption performed)

### Requirement: Element-level encryption for Map value types
When a field of type `Map<String, T>` is annotated with `@Encrypted` and `T` is a supported scalar type, the system SHALL encrypt each value individually and store the result as a BSON Document where keys remain plaintext and values are encrypted sub-documents.

#### Scenario: @Encrypted Map<String, String>
- **WHEN** an entity has `@Encrypted Map<String, String> settings` with value `{"theme": "dark", "lang": "zh"}`
- **THEN** the BSON Document SHALL have `settings` as a Document with `theme` and `lang` keys, each value being an encrypted sub-document

### Requirement: PathSegmentType path model
`EncryptedFieldMetadata` SHALL include a `List<PathSegmentType> pathTypes` field where each entry corresponds to a segment in `path`. PathSegmentType values:
- `FIELD`: direct field access
- `LIST_ITER`: iterate over Collection elements
- `MAP_ITER`: iterate over Map values

For top-level scalar fields, `pathTypes` SHALL be `[FIELD]`. For collection fields, `pathTypes` SHALL end with `LIST_ITER` or `MAP_ITER`.

#### Scenario: Scalar field path types
- **WHEN** a top-level field `phone` has `@Encrypted`
- **THEN** `pathTypes` SHALL be `[FIELD]`

#### Scenario: Collection field path types
- **WHEN** a field `tags` of type `List<String>` has `@Encrypted`
- **THEN** `pathTypes` SHALL be `[LIST_ITER]`

#### Scenario: POJO collection with nested encrypted field
- **WHEN** `List<Address> addresses` (without `@Encrypted`) contains `Address` with `@Encrypted String street`
- **THEN** `pathTypes` SHALL be `[LIST_ITER, FIELD]` and `path` SHALL be `["addresses", "street"]`

### Requirement: Generic type parameter resolution
When scanning a Collection or Map field, the system SHALL resolve the element type (Collection) or value type (Map) from `ParameterizedType`. If the generic type information is missing (raw type), the system SHALL throw `UnsupportedTypeException` at scan time.

#### Scenario: Parameterized List type
- **WHEN** a field is declared as `List<String> tags` with `@Encrypted`
- **THEN** the system SHALL resolve element type as `String.class`

#### Scenario: Parameterized Map type
- **WHEN** a field is declared as `Map<String, Integer> counts` with `@Encrypted`
- **THEN** the system SHALL resolve value type as `Integer.class`

#### Scenario: Raw type List
- **WHEN** a field is declared as `List tags` (no generic parameter) with `@Encrypted`
- **THEN** the system SHALL throw `UnsupportedTypeException` with a message indicating that generic type parameter is required

### Requirement: POJO collection recursive scanning
When a field of type `Collection<T>` or `Map<String, T>` does NOT have `@Encrypted` and `T` is a POJO, the system SHALL iterate conceptually over each element during encryption/decryption, recursively scanning `T` for `@Encrypted` fields. Each discovered encrypted field's metadata SHALL include the collection field name with `LIST_ITER` or `MAP_ITER` path segment type.

#### Scenario: List<Address> with @Encrypted inside Address
- **WHEN** an entity has `List<Address> addresses` (without `@Encrypted`) and `Address` has `@Encrypted String street`
- **THEN** `EntityMetadataCache` SHALL return metadata with `path = ["addresses", "street"]` and `pathTypes = [LIST_ITER, FIELD]`

#### Scenario: Map<String, Address> with @Encrypted inside Address
- **WHEN** an entity has `Map<String, Address> contactMap` (without `@Encrypted`) and `Address` has `@Encrypted String phone`
- **THEN** `EntityMetadataCache` SHALL return metadata with `path = ["contactMap", "phone"]` and `pathTypes = [MAP_ITER, FIELD]`

### Requirement: Whole-collection encryption for POJO collections
When a field of type `Collection<T>` or `Map<String, T>` where `T` is a POJO is annotated with `@Encrypted`, the system SHALL:
- Serialize the entire collection to BSON bytes using `MappingMongoConverter`
- Encrypt the BSON bytes as a single Binary value with `_t: "COL"` (Collection) or `_t: "MAP"` (Map)
- NOT recurse into the elements' internal fields for individual encryption
- Mark the metadata with `wholeObject = true`

#### Scenario: @Encrypted on List<Address> (whole-collection encryption)
- **WHEN** an entity has `@Encrypted List<Address> addresses` where `Address` is a POJO with no internal `@Encrypted` fields
- **THEN** the system SHALL create an `EncryptedFieldMetadata` with `wholeObject = true`, `path = ["addresses"]`, and `pathTypes = [LIST_ITER]`
- **AND** the BSON output SHALL be `addresses: {c: Binary, _e: 1, _t: "COL", _k: "...", _a: "..."}`

#### Scenario: @Encrypted on Map<String, Address> (whole-map encryption)
- **WHEN** an entity has `@Encrypted Map<String, Address> contacts`
- **THEN** the BSON output SHALL be `contacts: {c: Binary, _e: 1, _t: "MAP", _k: "...", _a: "..."}`

### Requirement: Whole-collection encryption does not support blind index
When whole-collection encryption is used (`@Encrypted` on POJO collection), the `blindIndex` parameter MUST be `false` (default). If `blindIndex = true` is specified, the system SHALL throw `UnsupportedTypeException` at scan time, since the entire collection is encrypted as a single Binary blob with no indexable field.

#### Scenario: @Encrypted(blindIndex = true) on POJO collection
- **WHEN** an entity has `@Encrypted(blindIndex = true) List<Address> addresses`
- **THEN** the system SHALL throw `UnsupportedTypeException` indicating whole-collection encryption does not support blind index

### Requirement: Collection annotation conflict detection (fail-fast)
When a POJO collection field is annotated with `@Encrypted` (whole-collection) and the element POJO type also contains `@Encrypted` fields internally, the system SHALL throw `IllegalStateException` at scan time.

#### Scenario: @Encrypted on List<Address> + Address has @Encrypted → conflict
- **WHEN** an entity has `@Encrypted List<Address> addresses` and `Address` has `@Encrypted String street`
- **THEN** the system SHALL throw `IllegalStateException` with a message indicating the annotation conflict
