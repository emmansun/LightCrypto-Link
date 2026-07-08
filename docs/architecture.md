# Architecture

## Overview

```text
Application
  -> LCL save/query/read integration
  -> KeyVaultService (per-entity DEK/HMAC management)
  -> MongoDB encrypted documents + __lcl_keyvault
```

LCL uses envelope encryption:
- CMK wraps DEK/HMAC key material.
- DEK encrypts business fields.
- HMAC key generates blind indexes.

## Vault Model

- Collection: `__lcl_keyvault`
- Scope: one vault document per entity class with encrypted fields
- Vault id: `lcl-dek-{EntitySimpleName}`
- Each vault stores versioned key entries in `keys[]` with `kid` (for example `v1-a3b2c1d4`)

## Encrypted Sub-document Format

```json
{
  "_k": "v1-a3b2c1d4",
  "_a": "AES_256_GCM",
  "_e": 1,
  "_t": "STR",
  "c": "<cipher-binary>",
  "b": "<blind-index-optional>"
}
```

Field notes:
- `_k`: DEK version id
- `_a`: symmetric algorithm
- `_e`: encryption marker
- `_t`: type marker (`STR`, `INT`, `LDATE`, `DOC`, `COL`, `MAP`, ...)
- `c`: ciphertext
- `b`: blind index, only when enabled

Backward compatibility:
- If `_a` is missing in legacy data, decryption falls back to `AES_256_GCM`.

## Whole-object Storage Markers

- `DOC`: whole POJO/document payload
- `COL`: whole collection payload
- `MAP`: whole map payload

## Rotation

Use:

```java
keyVaultService.rotateDek(EntityClass.class);
```

Behavior:
1. Current ACTIVE key entry is marked as `ROTATED`.
2. New DEK/HMAC pair is generated and wrapped by CMK.
3. New `kid` becomes ACTIVE.
4. New writes use new `kid`; old ciphertext can still be read via old entries.

## Blind Index

When `@Encrypted(blindIndex = true)`:
- Deterministic HMAC-SHA256 is computed from serialized value + field context.
- Stored in `b` field.
- Repository query rewriting targets blind-index field, avoiding decryption on query path.
