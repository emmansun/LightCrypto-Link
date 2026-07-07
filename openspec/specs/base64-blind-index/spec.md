## Purpose

Define the behavioral contract for the base64-blind-index capability to match current implementation behavior.

## Requirements
### Requirement: TypeSerializer returns raw bytes for byte[] input
`TypeSerializer.serialize(Object value)` SHALL return the raw `byte[]` directly when the input type is `byte[]`, without hex or base64 encoding. For all other supported types, the output SHALL be the UTF-8 encoding of the deterministic string representation.

#### Scenario: byte[] serialization returns raw bytes
- **WHEN** `serialize(new byte[]{0x01, 0x02, 0xFF})` is called
- **THEN** the result SHALL be `new byte[]{0x01, 0x02, 0xFF}` (3 bytes, no encoding)

#### Scenario: String serialization unchanged
- **WHEN** `serialize("hello")` is called
- **THEN** the result SHALL be `"hello".getBytes(UTF_8)`

#### Scenario: Integer serialization unchanged
- **WHEN** `serialize(42)` is called
- **THEN** the result SHALL be `"42".getBytes(UTF_8)`

### Requirement: TypeDeserializer BYTES marker uses base64
When the type marker is `BYTES`, `TypeDeserializer` SHALL decode the value using `Base64.getDecoder().decode()`.

#### Scenario: BYTES type deserialization from base64
- **WHEN** `deserialize("BYTES", "AQID")` is called (base64 of `[0x01, 0x02, 0x03]`)
- **THEN** the result SHALL be `new byte[]{0x01, 0x02, 0x03}`

#### Scenario: BYTES type serialization to base64
- **WHEN** `serializeToStorageString(new byte[]{0x01, 0x02, 0x03})` is called for BYTES marker
- **THEN** the result SHALL be `"AQID"` (standard base64, no padding needed for 3 bytes)

### Requirement: HMAC accepts byte[] input
`CryptoCodec.generateBlindIndex(byte[] hmacKey, String fieldName, byte[] serializedValue)` SHALL accept `byte[]` for the serialized value. The HMAC input SHALL be `fieldName.getBytes(UTF_8) + ":" + serializedValue` concatenated as raw bytes.

#### Scenario: Blind index for string field
- **WHEN** `generateBlindIndex(hmacKey, "phone", "13800138000".getBytes(UTF_8))` is called
- **THEN** the system SHALL compute HMAC-SHA-256 over the bytes `phone:13800138000` (colon separator included)

#### Scenario: Blind index for byte[] field
- **WHEN** `generateBlindIndex(hmacKey, "avatar", new byte[]{0x01, 0x02})` is called
- **THEN** the system SHALL compute HMAC-SHA-256 over the bytes `avatar:` + `[0x01, 0x02]` (no String conversion of the byte array)

#### Scenario: Deterministic output for same input
- **WHEN** `generateBlindIndex` is called twice with the same `hmacKey`, `fieldName`, and `serializedValue`
- **THEN** both calls SHALL return identical results

### Requirement: Blind index output uses base64url without padding
`CryptoCodec.generateBlindIndex` SHALL encode the HMAC-SHA-256 output using `Base64.getUrlEncoder().withoutPadding()`.

#### Scenario: Blind index output format
- **WHEN** HMAC-SHA-256 produces a 32-byte result
- **THEN** the blind index string SHALL be 43 characters long (base64url, no padding) using only characters `[A-Za-z0-9_-]`

#### Scenario: Blind index stored in sub-document b field
- **WHEN** an encrypted field with `blindIndex=true` is saved
- **THEN** the `b` field in the BSON sub-document SHALL contain the base64url-encoded HMAC value

### Requirement: Query rewrite uses base64url blind index
`CryptoMongoQueryCreator` SHALL generate blind index values in base64url format when rewriting queries, consistent with the format used during encryption.

#### Scenario: findByPhone query rewrite produces base64url blind index
- **WHEN** `findByPhone("13800138000")` is rewritten
- **THEN** the query criteria SHALL target `phone.b` with the base64url-encoded HMAC value (same as stored during save)

#### Scenario: $in query rewrite produces base64url blind index for each value
- **WHEN** `findByPhoneIn(["138", "139"])` is rewritten
- **THEN** each value in `$in` SHALL be individually hashed and base64url-encoded

### Requirement: Colon separator prevents HMAC collision
The HMAC input SHALL use a colon byte (`0x3A`) as separator between `fieldName` and `serializedValue` to prevent concatenation ambiguity.

#### Scenario: Different field-value pairs produce different HMAC
- **WHEN** computing HMAC for `(fieldName="ab", value="cd")` and `(fieldName="a", value="bcd")`
- **THEN** the inputs SHALL be `ab:cd` (5 bytes) vs `a:bcd` (5 bytes) - different byte sequences - producing different HMAC outputs


