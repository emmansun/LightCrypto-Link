## Purpose

Define the behavioral contract for the nested-object-scanning capability to match current implementation behavior.

## Requirements
### Requirement: Recursive nested POJO field scanning
`EntityMetadataCache` SHALL recursively scan fields of nested POJO objects when scanning an entity class. A field SHALL be treated as a nested POJO and recursively scanned if it meets ALL of the following conditions:
- The field does NOT have `@Encrypted` annotation
- The field type is NOT a primitive or wrapper type
- The field type is NOT String, BigDecimal, byte[], or java.time.* type
- The field type is NOT an Enum
- The field type is NOT a Collection, Map, or Array
- The field does NOT have `@DBRef` annotation
- The field does NOT have `@Transient` annotation

For each `@Encrypted` field discovered inside a nested POJO, the system SHALL create an `EncryptedFieldMetadata` entry with a `path` containing all field name segments from the root entity to the leaf field, and an `accessors` chain containing the corresponding MethodHandle getters.

#### Scenario: Entity with nested POJO containing @Encrypted field
- **WHEN** an entity `User` has a field `private Address address` (without `@Encrypted`) and `Address` has `@Encrypted private String street`
- **THEN** `EntityMetadataCache` SHALL return an `EncryptedFieldMetadata` with `path = ["address", "street"]` and `accessors = [User::address, Address::street]`

#### Scenario: Entity with deeply nested POJO (3 levels)
- **WHEN** `User` has `private Address address`, `Address` has `private GeoLocation geo`, and `GeoLocation` has `@Encrypted private String lat`
- **THEN** `EntityMetadataCache` SHALL return metadata with `path = ["address", "geo", "lat"]` and `accessors` containing 3 MethodHandle getters

#### Scenario: Nested POJO without any @Encrypted fields
- **WHEN** `User` has `private Address address` and `Address` has no `@Encrypted` fields
- **THEN** `EntityMetadataCache` SHALL return no metadata entries for the nested `Address` fields (same as current behavior for entities without encrypted fields)

#### Scenario: Mix of top-level and nested @Encrypted fields
- **WHEN** `User` has `@Encrypted private String phone` at top level AND `private Address address` with `@Encrypted private String street` inside
- **THEN** `EntityMetadataCache` SHALL return 2 metadata entries: one with `path = ["phone"]` and one with `path = ["address", "street"]`

### Requirement: POJO detection exclusion list
The system SHALL maintain a definitive exclusion list for POJO detection. Fields whose types match any exclusion criterion SHALL NOT be recursively scanned.

#### Scenario: Collection field is not recursed into
- **WHEN** an entity has `private List<Address> addresses` (without `@Encrypted`)
- **THEN** the system SHALL NOT recurse into the `Address` class through this field

#### Scenario: Map field is not recursed into
- **WHEN** an entity has `private Map<String, Address> addressMap` (without `@Encrypted`)
- **THEN** the system SHALL NOT recurse into the `Address` class through this field

#### Scenario: @DBRef field is not recursed into
- **WHEN** an entity has `@DBRef private Address address`
- **THEN** the system SHALL NOT recurse into the `Address` class (the field stores only a reference ID)

### Requirement: Maximum recursion depth
The system SHALL enforce a maximum recursion depth of 5 levels. If scanning exceeds this depth, the system SHALL throw `IllegalStateException` at metadata scan time (startup), not at runtime.

#### Scenario: Scanning within depth limit
- **WHEN** an entity has nested POJOs up to 3 levels deep with `@Encrypted` fields at the leaf
- **THEN** the system SHALL successfully scan and register all nested metadata

#### Scenario: Scanning exceeds depth limit
- **WHEN** an entity has nested POJOs exceeding 5 levels deep
- **THEN** the system SHALL throw `IllegalStateException` with a message indicating the maximum recursion depth has been exceeded

### Requirement: Circular reference detection
The system SHALL detect circular references during recursive scanning by maintaining a visited class set. If a cycle is detected, the system SHALL throw `IllegalStateException` at scan time.

#### Scenario: Direct circular reference
- **WHEN** class `A` has field `private B b` and class `B` has field `private A a` (both without `@Encrypted`)
- **THEN** the system SHALL throw `IllegalStateException` indicating a circular reference was detected between classes `A` and `B`

#### Scenario: Self-referencing class
- **WHEN** class `TreeNode` has field `private TreeNode child` (without `@Encrypted`)
- **THEN** the system SHALL throw `IllegalStateException` indicating a self-referencing circular reference

### Requirement: Path-based EncryptedFieldMetadata model
`EncryptedFieldMetadata` SHALL store field access information as:
- `List<MethodHandle> accessors`: ordered chain of getters from root entity to leaf field value
- `List<String> path`: ordered field name segments matching the BSON document structure
- `String effectiveFieldName`: full dot-joined path (e.g., `"address.zipCode"`) used as HMAC blind index salt

For top-level fields, `accessors` and `path` SHALL each contain exactly one element.

#### Scenario: Top-level field metadata structure
- **WHEN** a top-level field `phone` has `@Encrypted`
- **THEN** the metadata SHALL have `accessors.size() == 1`, `path == ["phone"]`, and `effectiveFieldName == "phone"`

#### Scenario: Nested field metadata structure
- **WHEN** a nested field at `address.zipCode` has `@Encrypted`
- **THEN** the metadata SHALL have `accessors.size() == 2`, `path == ["address", "zipCode"]`, and `effectiveFieldName == "address.zipCode"`

### Requirement: Whole-object encryption for POJO fields
When a POJO-typed field is annotated with `@Encrypted`, the system SHALL treat it as a whole-object encryption target. The system SHALL:
- Serialize the entire POJO to BSON bytes using `MappingMongoConverter`
- Encrypt the BSON bytes as a single Binary value with `_t: "DOC"`
- NOT recurse into the POJO's internal fields for individual encryption

#### Scenario: @Encrypted on a POJO field (whole-object encryption)
- **WHEN** an entity has `@Encrypted private Address address` where `Address` is a POJO with no `@Encrypted` fields
- **THEN** the system SHALL create an `EncryptedFieldMetadata` with `wholeObject = true`, `path = ["address"]`, `javaType = Address.class`

#### Scenario: Whole-object encryption does not recurse
- **WHEN** an entity has `@Encrypted private Address address`
- **THEN** the system SHALL NOT scan `Address`'s internal fields for additional `@Encrypted` annotations

### Requirement: Annotation conflict detection (fail-fast)
When a POJO-typed field is annotated with `@Encrypted` (whole-object) and the POJO type also contains `@Encrypted` fields internally (field-level), the system SHALL throw `IllegalStateException` at scan time, requiring the user to choose one mode.

#### Scenario: @Encrypted on POJO + internal @Encrypted -> conflict
- **WHEN** an entity has `@Encrypted private Address address` and `Address` has `@Encrypted private String street`
- **THEN** the system SHALL throw `IllegalStateException` with a message indicating the annotation conflict

#### Scenario: @Encrypted(blindIndex = true) on POJO -> not supported
- **WHEN** an entity has `@Encrypted(blindIndex = true) private Address address`
- **THEN** the system SHALL throw `UnsupportedTypeException` indicating whole-object encryption does not support blind index


