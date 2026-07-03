## ADDED Requirements

### Requirement: @Encrypted annotation definition
The system SHALL provide an `@Encrypted` field-level annotation in `com.lcl.crypto.annotation` with the following attributes:
- `algorithm`: symmetric algorithm enum, default `AES_256_GCM`. Supported values: `AES_256_GCM`, `AES_256_CBC`, `SM4_GCM`, `SM4_CBC`.
- `blindIndex`: boolean, default `false`
- `fieldName`: string for HMAC salt override, default `""` (uses Java field name)

The annotation SHALL have `@Target(ElementType.FIELD)`, `@Retention(RetentionPolicy.RUNTIME)`, and `@Documented`.

#### Scenario: Default annotation usage
- **WHEN** a field is annotated with `@Encrypted` without any attributes
- **THEN** the system SHALL use AES-256-GCM encryption, disable blind index, and use the Java field name as the HMAC salt

#### Scenario: Explicit blind index opt-in
- **WHEN** a field is annotated with `@Encrypted(blindIndex = true)`
- **THEN** the system SHALL generate a blind index hash for the field value and store it in the `b` sub-field of the BSON document

#### Scenario: Custom fieldName salt
- **WHEN** a field is annotated with `@Encrypted(fieldName = "national_id")`
- **THEN** the system SHALL use `"national_id"` instead of the Java field name when computing the HMAC blind index

#### Scenario: SM4-GCM algorithm selection
- **WHEN** a field is annotated with `@Encrypted(algorithm = SymmetricAlgorithm.SM4_GCM)`
- **THEN** the system SHALL encrypt the field using SM4-GCM with a 16-byte key derived from the DEK

#### Scenario: AES-256-CBC algorithm selection
- **WHEN** a field is annotated with `@Encrypted(algorithm = SymmetricAlgorithm.AES_256_CBC)`
- **THEN** the system SHALL encrypt the field using AES-256-CBC with the full 32-byte DEK and PKCS5 padding

#### Scenario: Algorithm stored in sub-document
- **WHEN** a field with any `algorithm` value is saved
- **THEN** the encrypted sub-document SHALL include an `_a` field containing the algorithm enum name (e.g., `"AES_256_GCM"`, `"SM4_GCM"`)

### Requirement: Entity metadata scanning
The system SHALL scan entity classes at startup (or lazily on first access) to discover all fields annotated with `@Encrypted` and cache the metadata in `EntityMetadataCache`.

#### Scenario: Entity with encrypted fields
- **WHEN** an entity class has 2 fields annotated with `@Encrypted`
- **THEN** `EntityMetadataCache` SHALL return metadata entries for exactly those 2 fields, including field name, Java type, algorithm, blindIndex flag, and effective fieldName

#### Scenario: Entity without encrypted fields
- **WHEN** an entity class has no `@Encrypted` fields
- **THEN** `EntityMetadataCache` SHALL return an empty metadata list and skip the entity during listener processing
## ADDED Requirements

### Requirement: @Encrypted annotation definition
The system SHALL provide an `@Encrypted` field-level annotation in `com.lcl.crypto.annotation` with the following attributes:
- `algorithm`: symmetric algorithm enum, default `AES_256_GCM`
- `blindIndex`: boolean, default `false`
- `fieldName`: string for HMAC salt override, default `""` (uses Java field name)

The annotation SHALL have `@Target(ElementType.FIELD)`, `@Retention(RetentionPolicy.RUNTIME)`, and `@Documented`.

#### Scenario: Default annotation usage
- **WHEN** a field is annotated with `@Encrypted` without any attributes
- **THEN** the system SHALL use AES-256-GCM encryption, disable blind index, and use the Java field name as the HMAC salt

#### Scenario: Explicit blind index opt-in
- **WHEN** a field is annotated with `@Encrypted(blindIndex = true)`
- **THEN** the system SHALL generate a blind index hash for the field value and store it in the `b` sub-field of the BSON document

#### Scenario: Custom fieldName salt
- **WHEN** a field is annotated with `@Encrypted(fieldName = "national_id")`
- **THEN** the system SHALL use `"national_id"` instead of the Java field name when computing the HMAC blind index

### Requirement: Entity metadata scanning
The system SHALL scan entity classes at startup (or lazily on first access) to discover all fields annotated with `@Encrypted` and cache the metadata in `EntityMetadataCache`.

#### Scenario: Entity with encrypted fields
- **WHEN** an entity class has 2 fields annotated with `@Encrypted`
- **THEN** `EntityMetadataCache` SHALL return metadata entries for exactly those 2 fields, including field name, Java type, algorithm, blindIndex flag, and effective fieldName

#### Scenario: Entity without encrypted fields
- **WHEN** an entity class has no `@Encrypted` fields
- **THEN** `EntityMetadataCache` SHALL return an empty metadata list and skip the entity during listener processing
