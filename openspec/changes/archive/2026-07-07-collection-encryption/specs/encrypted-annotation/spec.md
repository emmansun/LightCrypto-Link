## MODIFIED Requirements

### Requirement: Entity metadata scanning
The system SHALL scan entity classes at startup (or lazily on first access) to discover all `@Encrypted` fields and cache the metadata in `EntityMetadataCache`. The scan SHALL:
- Traverse the entity's direct fields and recursively enter nested POJO fields
- Identify Collection/Map typed fields, resolve their generic element/value types, and either register them as encrypted collection fields (if `@Encrypted` is present and element type is scalar) or recursively scan the element type (if no `@Encrypted` and element type is POJO)
- Each discovered field's metadata SHALL include its full path, path segment types, and element type information

#### Scenario: Entity with encrypted collection field
- **WHEN** an entity has `@Encrypted List<String> tags`
- **THEN** `EntityMetadataCache` SHALL return metadata with `path = ["tags"]`, `pathTypes = [LIST_ITER]`, and `javaType = String.class`

#### Scenario: Entity with POJO collection containing encrypted fields
- **WHEN** an entity has `List<Address> addresses` and `Address` has `@Encrypted String street`
- **THEN** `EntityMetadataCache` SHALL return metadata with `path = ["addresses", "street"]`, `pathTypes = [LIST_ITER, FIELD]`
