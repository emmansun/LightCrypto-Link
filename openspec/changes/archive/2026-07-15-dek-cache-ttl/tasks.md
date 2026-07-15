## 1. Configuration Property

- [x] 1.1 Add `cacheTtl` field (`java.time.Duration`, default `PT1H`) to `CryptoProperties.java` with Javadoc
- [x] 1.2 Verify Spring Boot binds `lcl.crypto.cache-ttl` from YAML/properties to `Duration` correctly (add a quick config test or verify via existing config tests)

## 2. EntityKeyContext TTL Support

- [x] 2.1 Add `Instant expiresAt` field to the `EntityKeyContext` inner class in `KeyVaultService.java`
- [x] 2.2 Update `verifyAndLoadKeys()` to compute `expiresAt = Instant.now().plus(cacheTtl)` and pass it to the `EntityKeyContext` constructor
- [x] 2.3 Add a `Duration cacheTtl` field to `KeyVaultService` (read from `CryptoProperties` in the constructor)

## 3. TTL-Based Cache Read in ensureVaultInitialized()

- [x] 3.1 Refactor `ensureVaultInitialized()` to check `expiresAt` on the cached entry; if expired, call a new `destroyKeyMaterial()` helper on the old entry, remove it from the map, and reload
- [x] 3.2 Handle `Duration.ZERO` TTL: skip the cache put in `verifyAndLoadKeys()` and always reload from MongoDB
- [x] 3.3 Ensure the TTL check and reload are correctly guarded by the existing `synchronized (this)` block

## 4. Secure Key Material Destruction

- [x] 4.1 Add a private `destroyKeyMaterial(EntityKeyContext ctx)` method that calls `Arrays.fill(array, (byte) 0)` on every `byte[]` in all `ResolvedKeyPair` entries of the context
- [x] 4.2 Call `destroyKeyMaterial()` on TTL expiry in `ensureVaultInitialized()` before removing the entry from the map

## 5. flushCache() Public API

- [x] 5.1 Add a public `flushCache()` method to `KeyVaultService` that iterates all cached entries, calls `destroyKeyMaterial()` on each, then clears the `entityKeyContexts` map
- [x] 5.2 Add Javadoc explaining the method zeroes key material and clears the cache

## 6. Auto-Configuration Wiring

- [x] 6.1 Verify `LightCryptoLinkAutoConfiguration` passes `CryptoProperties` (which now contains `cacheTtl`) to `KeyVaultService` constructor — update if needed

## 7. Unit Tests

- [x] 7.1 Add test: cache hit within TTL returns same entry without MongoDB access (in `KeyVaultServiceTest.java`)
- [x] 7.2 Add test: cache miss after TTL expiry triggers reload and zeros old key material
- [x] 7.3 Add test: `flushCache()` zeroes all key bytes and clears the map
- [x] 7.4 Add test: TTL = `PT0S` skips caching and always reloads
- [x] 7.5 Add test: default `cacheTtl` is `PT1H` when not configured

## 8. Integration Test

- [x] 8.1 Update `KeyVaultCorruptionTest` (or add a new integration test) to verify TTL expiry behavior end-to-end with a real MongoDB instance

## 9. Documentation

- [x] 9.1 Add `lcl.crypto.cache-ttl` row to the Core Properties table in `docs/configuration.md` (type `Duration`, default `PT1H`, description of TTL behavior and `PT0S` = no caching)
- [x] 9.2 Add a `cache-ttl` YAML example to the Local Symmetric CMK section in `docs/configuration.md`
- [x] 9.3 Add a DEK Cache section to `docs/architecture.md` explaining the in-memory cache, TTL-based expiry, secure key zeroing, and the `flushCache()` API
