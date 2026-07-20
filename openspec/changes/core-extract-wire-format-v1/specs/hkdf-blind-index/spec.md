## ADDED Requirements

### Requirement: HKDF-based per-namespace HMAC key derivation
The system SHALL derive a per-namespace HMAC key using HKDF-SHA-256 with the following parameters:
- IKM (Input Keying Material): the master HMAC key (32 bytes)
- Salt: SHA-256(namespace canonical string bytes)
- Info: the UTF-8 bytes of "lcl-blind-index-v1"
- Output length: 32 bytes

The derived key SHALL be used as the HMAC-SHA-256 key for blind index computation within that namespace.

#### Scenario: Same namespace produces same derived key
- **WHEN** deriving a key with the same master HMAC key and namespace "default.default.User#email" twice
- **THEN** both derived keys SHALL be byte-identical (deterministic)

#### Scenario: Different namespaces produce different derived keys
- **WHEN** deriving keys for "tenantA.app.User#email" and "tenantB.app.User#email" with the same master key
- **THEN** the two derived keys SHALL be different (cryptographic isolation)

#### Scenario: Different master keys produce different derived keys
- **WHEN** deriving keys for the same namespace with two different master HMAC keys
- **THEN** the two derived keys SHALL be different

### Requirement: Blind index computation with derived key
The system SHALL compute blind index as: `HMAC-SHA-256(derivedKey, fieldName.getBytes(UTF_8) ‖ 0x3A ‖ normalizedValue)`. The output SHALL be encoded as Base64URL without padding (43 characters for 32-byte output).

#### Scenario: Blind index deterministic for same input
- **WHEN** computing blind index for field "email", value "user@example.com", namespace "default.default.User#email" twice
- **THEN** both outputs SHALL be identical 43-character Base64URL strings

#### Scenario: Cross-tenant blind index isolation
- **WHEN** computing blind index for the same field name and value under "tenantA.app.User#email" vs "tenantB.app.User#email"
- **THEN** the two blind index outputs SHALL be different (different derived HMAC keys)

#### Scenario: Colon separator prevents collision
- **WHEN** computing HMAC for (fieldName="ab", value="cd") and (fieldName="a", value="bcd")
- **THEN** the HMAC inputs SHALL be "ab:cd" vs "a:bcd" — different byte sequences producing different outputs

### Requirement: Value normalization before HMAC
The system SHALL normalize string values before HMAC computation by applying: trim leading/trailing whitespace, then convert to lowercase. Non-string types SHALL be serialized via TypeSerializer before normalization is skipped (raw bytes used directly).

#### Scenario: Whitespace and case normalized
- **WHEN** computing blind index for value "  User@Example.COM  "
- **THEN** the normalized value "user@example.com" SHALL be used for HMAC computation

#### Scenario: Byte array values skip normalization
- **WHEN** computing blind index for a byte[] value
- **THEN** the raw bytes SHALL be used directly without normalization

### Requirement: Derived key caching
The system MAY cache derived HMAC keys per namespace to avoid repeated HKDF computation. Cached keys SHALL be invalidated when the master HMAC key changes (key rotation).

#### Scenario: Cache hit returns same key
- **WHEN** the derived key for a namespace is requested twice within the cache TTL
- **THEN** the second call SHALL return the cached key without recomputing HKDF

#### Scenario: Key rotation invalidates cache
- **WHEN** the master HMAC key is rotated and a blind index is requested for a previously cached namespace
- **THEN** the system SHALL recompute the derived key using the new master key
