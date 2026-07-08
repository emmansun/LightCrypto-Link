# Encryption Behavior

## Algorithms

| Algorithm | Key Size | IV Size | Mode | Provider |
|---|---|---|---|---|
| `AES_256_GCM` (default) | 32 bytes | 12 bytes | GCM/NoPadding | JDK |
| `AES_256_CBC` | 32 bytes | 16 bytes | CBC/PKCS5Padding | JDK |
| `SM4_GCM` | 16 bytes (first 16 bytes of DEK) | 12 bytes | GCM/NoPadding | Bouncy Castle |
| `SM4_CBC` | 16 bytes (first 16 bytes of DEK) | 16 bytes | CBC/PKCS5Padding | Bouncy Castle |

## Nested and Collection Encryption

| Field pattern | Storage behavior |
|---|---|
| `@Encrypted String phone` | Scalar encrypted sub-document |
| `List<Address>` with `Address.street @Encrypted` | Recursive traversal; nested leaf fields encrypted |
| `@Encrypted List<String> tags` | Element-level encrypted BSON array |
| `@Encrypted Map<String,String> settings` | Encrypted BSON document values |
| `@Encrypted Address address` | Whole object encrypted with `_t: "DOC"` |
| `@Encrypted List<Address> addresses` | Whole collection encrypted with `_t: "COL"` |
| `@Encrypted Map<String, Address> contacts` | Whole map encrypted with `_t: "MAP"` |

## Mode Selection

`EncryptionMode` supports `AUTO`, `ELEMENT`, and `WHOLE`.

| Goal | Recommended modeling |
|---|---|
| Need exact-match query | `@Encrypted(blindIndex = true)` in element-level mode |
| Need query on collection elements | Keep element-level mode (default behavior for simple collections) |
| Maximize confidentiality for whole container | `@Encrypted(mode = WHOLE)` on `List<T>` or `Map<String, T>` |

Notes:
- Whole-object mode does not support `blindIndex = true`.
- Avoid mixing whole-object encryption and nested `@Encrypted` fields in the same object graph.

## Supported Types

| Java Type | Encrypted | Blind Index |
|---|:---:|:---:|
| `String` | yes | yes |
| `Integer`, `Long`, `Short`, `Byte` | yes | yes |
| `Float`, `Double`, `BigDecimal` | yes | yes |
| `Boolean` | yes | yes |
| `LocalDate`, `LocalDateTime` | yes | yes |
| `byte[]` | yes | yes |
| `Enum` | yes | yes |
