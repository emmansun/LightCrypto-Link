## Why

The Java `KeyVaultService` caches unwrapped DEK and HMAC key material in a `ConcurrentHashMap` with no expiry — once loaded, raw key bytes remain in JVM heap indefinitely. The Node.js reference implementation (`lightcrypto-link-node`) already implements a configurable TTL (default 1 hour) that automatically evicts expired entries and securely destroys key buffers. Adding TTL to the Java cache reduces the window of exposure if heap memory is compromised (e.g., heap dump, memory forensics) and brings behavioral parity across both runtimes.

## What Changes

- Add a configurable `cacheTtl` property to `CryptoProperties` (`lcl.crypto.cache-ttl`, default `PT1H` / 1 hour).
- Add an `expiresAt` timestamp to each cached `EntityKeyContext` entry in `KeyVaultService`.
- On cache read (`ensureVaultInitialized`), check expiry; if expired, securely zero the cached key byte arrays and evict the entry, then reload from MongoDB.
- Add a public `flushCache()` method that zeroes all cached key material and clears the map.
- TTL = 0 means "no caching" (always reload); negative values are rejected at configuration validation time.

## Capabilities

### New Capabilities
- `dek-cache-ttl`: TTL-based expiry for the in-memory DEK/HMAC key cache in `KeyVaultService`, including secure key material destruction on eviction and a `flushCache()` API.

### Modified Capabilities
- `multi-dek-vault`: Cache read path now checks TTL before returning cached keys; expired entries trigger a full reload from MongoDB with KCV/binding re-verification.

## Impact

- **Code**: `KeyVaultService.java` (cache read/write logic, new `flushCache()` method), `CryptoProperties.java` (new `cacheTtl` field), `LightCryptoLinkAutoConfiguration.java` (pass TTL to service).
- **Tests**: Existing `KeyVaultService` tests need TTL-related scenarios; new unit tests for expiry and flush behavior.
- **Configuration**: New optional property `lcl.crypto.cache-ttl` (ISO-8601 `Duration`, e.g., `PT1H`, `PT30M`, `PT0S`).
- **Documentation**: `docs/configuration.md` (new property row + YAML example), `docs/architecture.md` (new DEK Cache section).
- **Backward compatibility**: Default TTL of 1 hour is a behavioral change from "never expire" but functionally transparent for most workloads since vault reload is fast. Users who want the old behavior can set `PT0S` to disable caching entirely or set a very large duration.
