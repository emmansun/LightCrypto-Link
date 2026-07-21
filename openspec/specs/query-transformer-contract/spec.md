## ADDED Requirements

### Requirement: QueryTransformer interface contract
The `QueryTransformer` interface in `lcl-spi` SHALL define the following methods for blind-index query rewrite:
- `String rewriteFieldName(String originalField)` — transform a plaintext field reference to the blind-index query target (e.g., "phone" → "phone.b" for MongoDB)
- `Object rewriteQueryValue(Object plaintextValue, String namespace)` — transform a plaintext query value into the blind-index lookup value (HMAC-based hash)
- `boolean supportsField(String field, Class<?> entityType)` — determine whether a field supports blind-index query rewrite

#### Scenario: Rewrite field name for MongoDB
- **WHEN** `rewriteFieldName("phone")` is called on a MongoDB adapter
- **THEN** it SHALL return `"phone.b"`

#### Scenario: Rewrite query value to blind index
- **WHEN** `rewriteQueryValue("13900001111", "default.default.User#phone")` is called
- **THEN** it SHALL return the HMAC-SHA-256 blind index hex string for the given value and namespace

#### Scenario: Field without blind index not supported
- **WHEN** `supportsField("email", User.class)` is called and `email` does NOT have `blindIndex = true`
- **THEN** it SHALL return `false`

#### Scenario: Field with blind index is supported
- **WHEN** `supportsField("phone", User.class)` is called and `phone` has `@Encrypted(blindIndex = true)`
- **THEN** it SHALL return `true`

### Requirement: QueryTransformer uses BlindIndexEngine
The `rewriteQueryValue` method SHALL delegate blind index computation to `lcl-core`'s `BlindIndexEngine` using the namespace-scoped HMAC key. The transformer SHALL obtain the HMAC key via `KeyVaultService.getActiveHmacKey(namespace)`.

#### Scenario: Blind index matches stored value
- **WHEN** a field was encrypted with blind index and a query uses the same plaintext value
- **THEN** `rewriteQueryValue` SHALL produce the same blind index hash that was stored during encryption

#### Scenario: Different namespaces produce different blind indexes
- **WHEN** the same plaintext value is queried under different namespaces
- **THEN** the blind index values SHALL differ (due to per-namespace HKDF-derived HMAC keys)

### Requirement: QueryTransformer is stateless
A `QueryTransformer` implementation SHALL be thread-safe. It MAY hold a reference to `KeyVaultService` for HMAC key retrieval but SHALL NOT cache query results.

#### Scenario: Concurrent query rewrites
- **WHEN** multiple threads call `rewriteQueryValue` concurrently with different values
- **THEN** all results SHALL be correct without external synchronization
