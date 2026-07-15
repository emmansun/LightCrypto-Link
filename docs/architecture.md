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

## DEK Cache

`KeyVaultService` caches unwrapped DEK and HMAC keys in memory to avoid repeated MongoDB reads and CMK unwrap operations.

- **Storage**: `ConcurrentHashMap<String, EntityKeyContext>` keyed by entity class simple name.
- **TTL**: Configurable via `lcl.crypto.cache-ttl` (default `PT1H` / 1 hour). Expired entries are lazily evicted on the next access — no background thread is needed.
- **Secure eviction**: When a cache entry expires or is flushed, all `byte[]` key material (DEK and HMAC keys, including historical versions) is explicitly zeroed with `Arrays.fill()` before the entry is removed.
- **Disable caching**: Set `lcl.crypto.cache-ttl=PT0S` to skip caching entirely. Every access will reload from MongoDB and verify KCV/binding.
- **Manual flush**: Call `keyVaultService.flushCache()` to immediately zero and evict all cached entries (e.g., on shutdown or after a security event).

## Blind Index

When `@Encrypted(blindIndex = true)`:
- Deterministic HMAC-SHA256 is computed from serialized value + field context.
- Stored in `b` field.
- Repository query rewriting targets blind-index field, avoiding decryption on query path.
