## Context

`KeyVaultService` manages per-entity-class DEK/HMAC key pairs in a `ConcurrentHashMap<String, EntityKeyContext>`. Currently, once a vault is loaded and verified, the unwrapped key bytes remain in heap memory until the JVM exits or the service is garbage-collected. The Node.js counterpart (`lightcrypto-link-node/src/service/KeyVaultService.js`) implements a configurable TTL (default 1 hour) with `expiresAt` per cache entry and secure buffer destruction on eviction.

Constraints:
- Java `byte[]` cannot be deterministically zeroed by the GC; we must explicitly call `Arrays.fill()` before discarding references.
- The cache is already guarded by `synchronized (this)` for writes but reads use `containsKey()` without synchronization — this must remain safe under concurrency.
- Vault reload (MongoDB read + CMK unwrap + KCV/binding verification) adds latency on cache miss; this is acceptable because it only happens once per TTL window.

## Goals / Non-Goals

**Goals:**
- Add configurable TTL to the DEK/HMAC key cache with secure key material destruction on expiry.
- Provide a `flushCache()` public API for on-demand cache purging (e.g., shutdown hooks, security events).
- Use ISO-8601 `Duration` format for the TTL configuration property to align with Spring Boot conventions.
- Default TTL: 1 hour (`PT1H`), matching the Node.js reference.
- TTL = 0 (`PT0S`) disables caching entirely — every call reloads from MongoDB.

**Non-Goals:**
- Background eviction / scheduled cleanup — expiry is checked lazily on access (matching Node.js behavior).
- Cache statistics, metrics, or observability hooks.
- Caffeine/Guava cache integration — keep the implementation lightweight with `ConcurrentHashMap`.
- CMK rotation-triggered cache invalidation (separate concern).

## Decisions

### Decision 1: Lazy TTL check on `ensureVaultInitialized()`
**Choice**: Check `expiresAt` inside `ensureVaultInitialized()` — the single entry point for all cache reads. If expired, zero key material, remove the entry, and reload.
**Rationale**: Simple, matches Node.js pattern, avoids adding a background scheduler. The check is O(1) and happens under the existing `synchronized` block for cache misses.
**Alternative considered**: A `ScheduledExecutorService` to periodically scan and evict. Rejected because it adds thread management complexity and the lazy approach is sufficient for the security goal.

### Decision 2: `Duration` type for `cacheTtl` property
**Choice**: Use `java.time.Duration` for the `CryptoProperties.cacheTtl` field (e.g., `lcl.crypto.cache-ttl=PT1H`).
**Rationale**: Spring Boot natively supports `Duration` binding from YAML/properties with unit suffixes (`1h`, `30m`, `3600s`). More idiomatic than raw milliseconds.
**Alternative considered**: `long cacheTtlMillis`. Rejected because `Duration` is more expressive and self-documenting.

### Decision 3: `expiresAt` field in `EntityKeyContext`
**Choice**: Add `Instant expiresAt` to the existing `EntityKeyContext` inner class. Compute as `Instant.now().plus(cacheTtl)` when caching.
**Rationale**: `Instant` comparison is straightforward. Using `Instant` avoids `System.currentTimeMillis()` and aligns with the project's existing use of `java.time`.

### Decision 4: Secure zeroing via `Arrays.fill()`
**Choice**: On eviction (TTL expiry or `flushCache()`), call `Arrays.fill(array, (byte) 0)` on every `byte[]` DEK and HMAC key in the entry, including historical `ResolvedKeyPair` entries.
**Rationale**: Explicit zeroing reduces the window where key material is reachable in heap. While Java GC may move objects, zeroing the known reference is the best-effort practice (same as Node.js `crypto.randomFillSync`).

### Decision 5: TTL = 0 means "no caching"
**Choice**: When `cacheTtl` is `Duration.ZERO`, skip caching entirely — always load from MongoDB.
**Rationale**: Provides a way to disable caching for environments with strict memory requirements or for testing. Consistent with Node.js behavior where `cacheTtl: 0` results in immediate expiry.

## Risks / Trade-offs

- **[Risk] Brief performance degradation on cache expiry** → Mitigation: Vault reload is fast (single MongoDB query + CMK unwrap). The TTL window (default 1h) is large enough that the amortized cost is negligible.
- **[Risk] `synchronized` block contention on cache miss under high concurrency** → Mitigation: Only the first thread after TTL expiry pays the cost; subsequent threads hit the freshly populated cache. This is identical to the current behavior on cold start.
- **[Risk] `Arrays.fill()` may not zero all heap copies if GC has moved the array** → Mitigation: This is a fundamental JVM limitation. We zero what we can control. For higher assurance, users should combine with JVM-level protections (encrypted swap, restricted heap dumps).
- **[Trade-off] No background eviction** → Accepted: Lazy eviction means expired keys stay in memory until the next access. For most use cases this is acceptable because entity classes are accessed frequently. A `flushCache()` API is provided for explicit cleanup.
