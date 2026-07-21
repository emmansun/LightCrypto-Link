## MODIFIED Requirements

### Requirement: Deterministic serialization for supported types
The system SHALL provide a `TypeSerializer` (located in `lcl-spring-boot-starter`) that converts Java values to deterministic String representations for encryption and blind index computation. The serialization SHALL be:
- `String`: original value unchanged
- `Integer / Long / Short / Byte`: `String.valueOf(value)`
- `Float / Double`: `String.valueOf(value)`
- `BigDecimal`: `toPlainString()` (NOT `toString()` to avoid scientific notation)
- `Boolean`: `"true"` or `"false"`
- `LocalDate`: `DateTimeFormatter.ISO_LOCAL_DATE` (e.g., "1996-05-15")
- `LocalDateTime`: `DateTimeFormatter.ISO_LOCAL_DATE_TIME` (e.g., "1996-05-15T14:30:00")
- `Enum`: `enum.name()`
- `byte[]`: `HexFormat.of().formatHex(value)` (lowercase hex)

#### Scenario: BigDecimal avoids scientific notation
- **WHEN** a BigDecimal value of `15000` is serialized
- **THEN** the output SHALL be `"15000"`, not `"1.5E+4"`

#### Scenario: LocalDate ISO format
- **WHEN** a LocalDate of May 15, 1996 is serialized
- **THEN** the output SHALL be `"1996-05-15"`

#### Scenario: Serialization determinism
- **WHEN** the same value is serialized multiple times
- **THEN** the output SHALL be identical each time (critical for blind index consistency)

### Requirement: Type deserialization for read-back
The system SHALL provide a `TypeDeserializer` (located in `lcl-spring-boot-starter`) that converts decrypted String values back to the original Java type, guided by the `_t` type marker stored in the encrypted sub-document.

Type markers:
- `"STR"` -> `String`
- `"INT"` -> `Integer`
- `"LONG"` -> `Long`
- `"SHORT"` -> `Short`
- `"BYTE"` -> `Byte`
- `"FLOAT"` -> `Float`
- `"DOUBLE"` -> `Double`
- `"DEC"` -> `BigDecimal`
- `"BOOL"` -> `Boolean`
- `"LDATE"` -> `LocalDate` (parse ISO-8601)
- `"LDT"` -> `LocalDateTime` (parse ISO-8601)
- `"ENUM:<fqcn>"` -> Enum (valueOf on specified class)
- `"BYTES"` -> `byte[]` (HexFormat parse)

#### Scenario: Deserialize Integer
- **WHEN** the decrypted value is `"28"` with `_t = "INT"`
- **THEN** the result SHALL be `Integer.valueOf(28)`

#### Scenario: Deserialize LocalDate
- **WHEN** the decrypted value is `"1996-05-15"` with `_t = "LDATE"`
- **THEN** the result SHALL be `LocalDate.of(1996, 5, 15)`

#### Scenario: Deserialize Enum
- **WHEN** the decrypted value is `"TYPE_A"` with `_t = "ENUM:com.example.BloodType"`
- **THEN** the result SHALL be `BloodType.TYPE_A`

### Requirement: Unsupported type rejection
The system SHALL throw `UnsupportedTypeException` when an `@Encrypted` field's Java type is not in the supported type list.

#### Scenario: Custom object type
- **WHEN** an `@Encrypted` field is declared as `Address` (custom class)
- **THEN** the system SHALL throw `UnsupportedTypeException` at metadata scan time with a message indicating the type is not supported in v1
