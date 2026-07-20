## ADDED Requirements

### Requirement: Four-segment namespace structure
The system SHALL represent namespaces using the format `<tenant>.<realm>.<entity>#<field>`. The `#` character separates the path (tenant.realm.entity) from the field name. The `.` character separates tenant, realm, and entity segments.

#### Scenario: Full namespace parsing
- **WHEN** parsing "acme.production.users#email"
- **THEN** the result SHALL be tenant="acme", realm="production", entity="users", field="email"

#### Scenario: Nested field path
- **WHEN** parsing "default.default.User#address.city"
- **THEN** the result SHALL be tenant="default", realm="default", entity="User", field="address.city"

#### Scenario: Shorthand form (entity#field only)
- **WHEN** parsing "User#email"
- **THEN** the result SHALL be tenant="default", realm="default", entity="User", field="email"

### Requirement: Two-segment form is forbidden
The system SHALL reject namespace strings that contain exactly one `.` separator before the `#` (two-segment path), as this form is ambiguous between `tenant.realm` and `realm.entity`.

#### Scenario: Two-segment path rejected
- **WHEN** parsing "production.User#email" (one dot before #)
- **THEN** the system SHALL throw `IllegalArgumentException` indicating ambiguous namespace format

### Requirement: Character restrictions
Each namespace segment (tenant, realm, entity, field) SHALL only contain characters matching `[a-zA-Z0-9_\-]`. The only allowed separators are `.` (between path segments) and `#` (between path and field). The field segment MAY contain `.` for nested paths.

#### Scenario: Invalid character rejected
- **WHEN** parsing "tenant app.realm.User#email" (space in tenant)
- **THEN** the system SHALL throw `IllegalArgumentException` indicating invalid namespace character

#### Scenario: Valid special characters accepted
- **WHEN** parsing "my-tenant.prod_v2.User-Entity#field_name"
- **THEN** parsing SHALL succeed with tenant="my-tenant", realm="prod_v2", entity="User-Entity", field="field_name"

### Requirement: Case sensitivity
Namespace segments SHALL be case-sensitive. "User#email" and "user#email" SHALL be treated as different namespaces.

#### Scenario: Different case produces different namespace
- **WHEN** comparing "Default.Default.User#email" with "default.default.User#email"
- **THEN** they SHALL be considered different namespaces (different UTF-8 bytes in Wire Format)

### Requirement: Maximum length constraint
The total UTF-8 byte length of a namespace string SHALL NOT exceed 256 bytes. The system SHALL reject namespaces exceeding this limit.

#### Scenario: Namespace within limit accepted
- **WHEN** parsing a namespace of 200 UTF-8 bytes
- **THEN** parsing SHALL succeed

#### Scenario: Namespace exceeding limit rejected
- **WHEN** parsing a namespace of 300 UTF-8 bytes
- **THEN** the system SHALL throw `IllegalArgumentException` indicating namespace exceeds 256-byte limit

### Requirement: Default values for single-tenant
When only the shorthand form `entity#field` is provided, the system SHALL use "default" for both tenant and realm. The full canonical form SHALL be `default.default.<entity>#<field>`.

#### Scenario: Shorthand expands to canonical form
- **WHEN** resolving namespace from "Order#totalAmount"
- **THEN** the canonical namespace string SHALL be "default.default.Order#totalAmount"

### Requirement: Namespace serialization in Wire Format
The namespace SHALL be serialized as its canonical UTF-8 string representation in the Wire Format blob. The `namespaceLength` field SHALL be the byte length of this UTF-8 encoding.

#### Scenario: Namespace bytes in blob match canonical form
- **WHEN** encoding with namespace shorthand "User#email"
- **THEN** the namespace bytes in the blob SHALL be the UTF-8 encoding of "default.default.User#email" (canonical form, not shorthand)
