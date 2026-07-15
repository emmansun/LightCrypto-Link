## ADDED Requirements

### Requirement: Configurable cache TTL
The system SHALL support a `lcl.crypto.cache-ttl` configuration property of type `java.time.Duration` (ISO-8601 format, e.g., `PT1H`, `PT30M`). The default value SHALL be `PT1H` (1 hour). A value of `PT0S` SHALL disable caching entirely.

#### Scenario: Default TTL is 1 hour
- **WHEN** `lcl.crypto.cache-ttl` is not configured
- **THEN** the system SHALL use a cache TTL of `PT1H` (1 hour)

#### Scenario: Custom TTL via YAML
- **WHEN** `lcl.crypto.cache-ttl` is set to `PT30M` in `application.yml`
- **THEN** the system SHALL use a cache TTL of 30 minutes

#### Scenario: TTL zero disables caching
- **WHEN** `lcl.crypto.cache-ttl` is set to `PT0S`
- **THEN** the system SHALL skip caching and reload keys from MongoDB on every access

### Requirement: Cache entry expiry on access
`KeyVaultService` SHALL check the `expiresAt` timestamp of each cached `EntityKeyContext` before returning it. If the current time is at or past `expiresAt`, the entry SHALL be considered expired.

#### Scenario: Cache hit within TTL
- **WHEN** `ensureVaultInitialized()` is called and the cached entry has not expired
- **THEN** the system SHALL return the cached entry without accessing MongoDB

#### Scenario: Cache miss due to TTL expiry
- **WHEN** `ensureVaultInitialized()` is called and the cached entry has expired (current time >= `expiresAt`)
- **THEN** the system SHALL securely zero all key byte arrays in the expired entry, remove it from the cache, reload the vault from MongoDB, verify KCV and binding, cache the new entry with a fresh `expiresAt`, and return the new entry

#### Scenario: No caching when TTL is zero
- **WHEN** `cacheTtl` is `Duration.ZERO` and `ensureVaultInitialized()` is called
- **THEN** the system SHALL always reload from MongoDB and SHALL NOT store the result in cache

### Requirement: Secure key material destruction on eviction
When a cache entry is evicted (due to TTL expiry or explicit `flushCache()` call), the system SHALL securely zero all `byte[]` key material by calling `Arrays.fill(array, (byte) 0)` on every DEK and HMAC key buffer, including all historical `ResolvedKeyPair` entries.

#### Scenario: Key material zeroed on TTL expiry
- **WHEN** a cache entry expires and is evicted
- **THEN** all `byte[]` arrays (active DEK, active HMAC key, and all historical `ResolvedKeyPair` DEK/HMAC keys) in that entry SHALL be overwritten with zeros before the entry is removed from the cache map

#### Scenario: Key material zeroed on flushCache
- **WHEN** `flushCache()` is called
- **THEN** all `byte[]` arrays in every cached entry SHALL be overwritten with zeros, and the cache map SHALL be cleared

### Requirement: flushCache public API
`KeyVaultService` SHALL expose a public `flushCache()` method that securely zeroes and removes all cached entries.

#### Scenario: flushCache clears all entries
- **WHEN** `flushCache()` is called and the cache contains entries for `User` and `Order`
- **THEN** all key material in both entries SHALL be zeroed, the cache SHALL be empty, and subsequent calls to `ensureVaultInitialized()` SHALL trigger a full reload from MongoDB

### Requirement: Cache entry includes expiresAt timestamp
Each cached `EntityKeyContext` SHALL carry an `expiresAt` field of type `java.time.Instant`, computed as `Instant.now().plus(cacheTtl)` at the time the entry is written to the cache.

#### Scenario: expiresAt computed on cache write
- **WHEN** a new cache entry is created at time T with TTL of 1 hour
- **THEN** the entry's `expiresAt` SHALL be `T + 1 hour`

#### Scenario: expiresAt recomputed on reload after expiry
- **WHEN** an expired entry triggers a reload at time T2
- **THEN** the new entry's `expiresAt` SHALL be `T2 + cacheTtl`
