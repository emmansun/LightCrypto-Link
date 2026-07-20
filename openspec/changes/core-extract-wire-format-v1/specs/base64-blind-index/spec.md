## MODIFIED Requirements

### Requirement: HMAC accepts byte[] input
`BlindIndexEngine.computeBlindIndex(String namespace, String fieldName, byte[] serializedValue)` SHALL accept `byte[]` for the serialized value. The HMAC key SHALL be derived via HKDF-SHA-256 from the master HMAC key and the namespace (see hkdf-blind-index capability). The HMAC input SHALL be `fieldName.getBytes(UTF_8) + ":" + serializedValue` concatenated as raw bytes.

#### Scenario: Blind index for string field
- **WHEN** `computeBlindIndex("default.default.User#phone", "phone", "13800138000".getBytes(UTF_8))` is called
- **THEN** the system SHALL derive HMAC key via HKDF(masterKey, namespace), then compute HMAC-SHA-256 over the bytes `phone:13800138000`

#### Scenario: Blind index for byte[] field
- **WHEN** `computeBlindIndex("default.default.User#avatar", "avatar", new byte[]{0x01, 0x02})` is called
- **THEN** the system SHALL compute HMAC-SHA-256 over the bytes `avatar:` + `[0x01, 0x02]` using the namespace-derived key

#### Scenario: Deterministic output for same input
- **WHEN** `computeBlindIndex` is called twice with the same master key, namespace, fieldName, and serializedValue
- **THEN** both calls SHALL return identical results

### Requirement: Blind index output uses base64url without padding
`BlindIndexEngine.computeBlindIndex` SHALL encode the HMAC-SHA-256 output using `Base64.getUrlEncoder().withoutPadding()`.

#### Scenario: Blind index output format
- **WHEN** HMAC-SHA-256 produces a 32-byte result
- **THEN** the blind index string SHALL be 43 characters long (base64url, no padding) using only characters `[A-Za-z0-9_-]`

#### Scenario: Blind index stored in sub-document b field
- **WHEN** an encrypted field with `blindIndex=true` is saved
- **THEN** the `b` field in the BSON sub-document SHALL contain the base64url-encoded HMAC value computed with the HKDF-derived key

### Requirement: Query rewrite uses base64url blind index
`CryptoMongoQueryCreator` SHALL generate blind index values using the HKDF-derived key for the field's namespace when rewriting queries, consistent with the format used during encryption.

#### Scenario: findByPhone query rewrite produces base64url blind index
- **WHEN** `findByPhone("13800138000")` is rewritten
- **THEN** the query criteria SHALL target `phone.b` with the base64url-encoded HMAC value derived from the field's namespace

#### Scenario: $in query rewrite produces base64url blind index for each value
- **WHEN** `findByPhoneIn(["138", "139"])` is rewritten
- **THEN** each value in `$in` SHALL be individually hashed with the namespace-derived key and base64url-encoded
