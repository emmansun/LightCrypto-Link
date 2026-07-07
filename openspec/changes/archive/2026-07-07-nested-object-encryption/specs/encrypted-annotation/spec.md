## MODIFIED Requirements

### Requirement: Entity metadata scanning
The system SHALL scan entity classes at startup (or lazily on first access) to discover all fields annotated with `@Encrypted` and cache the metadata in `EntityMetadataCache`. The scan SHALL traverse the entity's direct fields AND recursively enter nested POJO fields (as defined by the `nested-object-scanning` capability) to discover `@Encrypted` fields at any depth. Each discovered field's metadata SHALL include its full path from the root entity.

#### Scenario: Entity with encrypted fields
- **WHEN** an entity class has 2 fields annotated with `@Encrypted`
- **THEN** `EntityMetadataCache` SHALL return metadata entries for exactly those 2 fields, including field path, Java type, algorithm, blindIndex flag, and effective fieldName

#### Scenario: Entity without encrypted fields
- **WHEN** an entity class has no `@Encrypted` fields (including inside nested POJOs)
- **THEN** `EntityMetadataCache` SHALL return an empty metadata list and skip the entity during listener processing

#### Scenario: Entity with nested POJO containing encrypted fields
- **WHEN** an entity `User` has `private Address address` and `Address` has `@Encrypted private String zipCode`
- **THEN** `EntityMetadataCache` SHALL return a metadata entry with `path = ["address", "zipCode"]` alongside any top-level encrypted field entries
