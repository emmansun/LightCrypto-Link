# Architecture

## Overview

```text
Application
  -> LCL save/query/read integration
  -> KeyVaultService (per-namespace DEK/HMAC management)
  -> StorageAdapter (database-specific encrypted field format)
  -> VaultStore (key vault persistence)
```

LCL uses envelope encryption:
- CMK wraps DEK/HMAC key material.
- DEK encrypts business fields (Wire Format V1 self-describing blob).
- HMAC key (HKDF-derived per namespace) generates blind indexes.

## Module Structure

| Module | Responsibility |
|--------|---------------|
| `lcl-spi` | Pure interfaces: `VaultStore`, `StorageAdapter`, `QueryTransformer`, `EncryptHandler`, `DecryptHandler` — no Spring/DB dependencies |
| `lcl-core` | Cryptographic primitives: `CryptoCodec`, wire format, blind index, namespace model |
| `lcl-adapter-mongodb` | MongoDB-specific implementations: `MongoVaultStore`, `MongoStorageAdapter`, `MongoQueryTransformer` |
| `lcl-spring-boot-starter` | Auto-configuration, event listeners, query rewriting, field encryption service |
| `lcl-provider-*` | CMK provider implementations (Alibaba KMS, Azure Key Vault) |

## Vault Model

- Collection: `__lcl_keyvault`
- Scope: one vault document per **namespace** (tenant.realm.entity#field)
- Vault id: `lcl-dek-{canonicalNamespace}` (e.g. `lcl-dek-default.default.User#phone`)
- Each vault stores versioned key entries in `keys[]` with `kid` (for example `v1-a3b2c1d4`)
- Namespace is derived from `lightcrypto.tenants.tenant`, `lightcrypto.tenants.realm`, entity simple name, and field path

## Encrypted Sub-document Format (Wire Format V1)

```json
{
  "_e": 1,
  "_t": "STR",
  "c": "AQEAIGRlZmF1bHQuZGVmYXVsdC5Vc2VyI3Bob25lAAAAAQ...",
  "b": "<blind-index-optional>"
}
```

Field notes:
- `_e`: encryption marker (always `1`)
- `_t`: type marker (`STR`, `INT`, `LDATE`, `DOC`, `COL`, `MAP`, ...)
- `c`: Base64URL-encoded Wire Format V1 blob (self-describing)
- `b`: blind index (HKDF-derived), only when enabled

The Wire Format V1 blob embeds:
- Version byte (`0x01`)
- Algorithm ID (1 byte)
- Namespace (UTF-8, length-prefixed)
- DEK version (4 bytes, big-endian)
- IV (length-prefixed)
- Ciphertext

No separate `_k` (kid) or `_a` (algorithm) fields are needed — the blob is fully self-describing.

## Whole-object Storage Markers

- `DOC`: whole POJO/document payload
- `COL`: whole collection payload
- `MAP`: whole map payload

## Rotation

Use:

```java
keyVaultService.rotateDek("default.default.User#phone");
```

Behavior:
1. Current ACTIVE key entry is marked as `ROTATED`.
2. New DEK/HMAC pair is generated and wrapped by CMK.
3. New `kid` becomes ACTIVE.
4. New writes use new `kid`; old ciphertext can still be read via old entries (dekVersion from blob).

## DEK Cache

`KeyVaultService` caches unwrapped DEK and HMAC keys in memory to avoid repeated MongoDB reads and CMK unwrap operations.

- **Storage**: `ConcurrentHashMap<String, NamespaceKeyContext>` keyed by canonical namespace.
- **TTL**: Configurable via `lightcrypto.keyvault.cache.ttl` (default `PT1H` / 1 hour). Expired entries are lazily evicted on the next access — no background thread is needed.
- **Secure eviction**: When a cache entry expires or is flushed, all `byte[]` key material (DEK and HMAC keys, including historical versions) is explicitly zeroed with `Arrays.fill()` before the entry is removed.
- **Disable caching**: Set `lightcrypto.keyvault.cache.ttl=PT0S` to skip caching entirely. Every access will reload from MongoDB and verify KCV/binding.
- **Manual flush**: Call `keyVaultService.flushCache()` to immediately zero and evict all cached entries (e.g., on shutdown or after a security event).

## Blind Index

When `@Encrypted(blindIndex = true)`:
- Per-namespace HKDF-derived key is computed from the vault HMAC key + namespace context.
- Deterministic HMAC-SHA256 blind index is computed from the derived key + serialized value.
- Stored in `b` field as Base64URL (43 chars, no padding).
- Repository query rewriting targets `field.b`, avoiding decryption on query path.

## Storage Adapter SPI

LCL separates storage concerns from cryptographic orchestration via three SPI interfaces (defined in `lcl-spi`):

- **`VaultStore`** — Document-level CRUD + optimistic-lock `rotate()` + batch `loadAll()`.  
  Implementation: `MongoVaultStore` (collection `__lcl_keyvault`).
- **`StorageAdapter`** — Database-specific encrypted field format. Builds/extracts `{c, _e, _t, b}` payloads.  
  Implementation: `MongoStorageAdapter` (BSON `Document` format).
- **`QueryTransformer`** — Rewrites plaintext field references to blind-index query targets (`field` → `field.b`, value → HMAC hash).  
  Implementation: `MongoQueryTransformer`.

This separation allows future adapter modules (e.g., `lcl-adapter-jdbc`, `lcl-adapter-elasticsearch`) without modifying core or starter code.
