## ADDED Requirements

### Requirement: StorageAdapter interface contract
The `StorageAdapter` interface in `lcl-spi` SHALL define the following methods for encrypted field payload handling:
- `Object buildEncryptedPayload(String blob, String typeMarker, String blindIndex)` — construct a database-specific encrypted payload from components; `blindIndex` MAY be null
- `String extractBlob(Object payload)` — extract the Base64URL wire-format blob from a payload
- `String extractTypeMarker(Object payload)` — extract the type marker (e.g., "STR", "INT") from a payload
- `String extractBlindIndex(Object payload)` — extract the blind index value from a payload; returns null if absent
- `boolean isEncryptedPayload(Object value)` — determine whether a raw field value is an encrypted payload (vs plaintext)

#### Scenario: Build payload with blind index
- **WHEN** `buildEncryptedPayload("AQAB...", "STR", "a3f2b1")` is called on a MongoDB adapter
- **THEN** the result SHALL be a BSON Document `{c: "AQAB...", _e: 1, _t: "STR", b: "a3f2b1"}`

#### Scenario: Build payload without blind index
- **WHEN** `buildEncryptedPayload("AQAB...", "INT", null)` is called
- **THEN** the result SHALL NOT contain a blind index field

#### Scenario: Extract blob from payload
- **WHEN** `extractBlob(payload)` is called with a valid encrypted payload
- **THEN** it SHALL return the Base64URL wire-format blob string

#### Scenario: isEncryptedPayload with encrypted value
- **WHEN** `isEncryptedPayload(value)` is called with a value that is an encrypted sub-document
- **THEN** it SHALL return `true`

#### Scenario: isEncryptedPayload with plaintext value
- **WHEN** `isEncryptedPayload(value)` is called with a plain String "hello"
- **THEN** it SHALL return `false`

#### Scenario: isEncryptedPayload with null
- **WHEN** `isEncryptedPayload(null)` is called
- **THEN** it SHALL return `false`

### Requirement: StorageAdapter payload round-trip
For any `StorageAdapter` implementation, the following round-trip invariant SHALL hold: given `payload = buildEncryptedPayload(blob, typeMarker, blindIndex)`, then `extractBlob(payload) == blob`, `extractTypeMarker(payload) == typeMarker`, and `extractBlindIndex(payload) == blindIndex`.

#### Scenario: Round-trip with all fields
- **WHEN** a payload is built with blob="AQAB", typeMarker="STR", blindIndex="abc123"
- **THEN** extracting each field from the payload SHALL return the original values

#### Scenario: Round-trip with null blind index
- **WHEN** a payload is built with blob="AQAB", typeMarker="LDATE", blindIndex=null
- **THEN** `extractBlindIndex(payload)` SHALL return null

### Requirement: StorageAdapter is stateless
A `StorageAdapter` implementation SHALL be stateless and thread-safe. It SHALL NOT hold references to database connections or sessions.

#### Scenario: Shared adapter across threads
- **WHEN** the same `StorageAdapter` instance is used by multiple threads concurrently
- **THEN** all operations SHALL produce correct results without synchronization
