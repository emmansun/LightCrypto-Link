## Context

LightCrypto-Link uses envelope encryption: a CMK (via `CmkProvider` SPI) wraps/unwraps DEK and HMAC keys stored in `VaultDocument`. Business data is encrypted with the DEK, never directly with the CMK. This separation means switching CMK providers only requires re-wrapping vault key material — business data, wire format, and blind index values remain untouched.

Current state:
- `KeyVaultService` supports vault initialization and DEK rotation, but has no re-wrap capability.
- `VaultDocument` already stores `cmkProvider` and `cmkId` at vault level, and each `KeyEntry` stores `wrappingAlgorithm`.
- `VaultStore.loadAll()` exists for bulk loading all namespaces.
- `VaultStore.rotate()` provides optimistic-locking CAS for safe concurrent updates.

## Goals / Non-Goals

**Goals:**
- Provide a safe, atomic, per-namespace re-wrap API that preserves key integrity (KCV/binding invariance).
- Re-wrap ALL key entries (ACTIVE + ROTATED) since historical DEK versions are needed for decryption.
- Provide an operational CommandLineRunner for batch migration across all namespaces.
- Emit observability events for re-wrap operations.
- Document the migration procedure including prerequisites, transition window, and rollback.

**Non-Goals:**
- Per-entry provider tracking (each KeyEntry remembering its own CMK) — deferred to Phase 5 crypto agility.
- Dual-provider concurrent read path (provider registry dispatching unwrap by entry) — not needed for vault-level migration.
- Automatic data re-encryption or DEK material rotation — that is the existing `rotateDek()` path.
- Zero-downtime live migration — brief write-pause is acceptable.

## Decisions

### D1: Vault-level atomic re-wrap (not per-entry gradual)

All KeyEntries in a VaultDocument are re-wrapped in a single atomic operation. The `cmkProvider` and `cmkId` fields are updated to the new provider.

**Rationale**: Simplicity. The read path (`verifyAndLoadKeys`) uses a single `CmkProvider` instance to unwrap all entries. Supporting mixed providers per entry would require a provider registry and per-entry dispatch — significant complexity for a rare use case.

**Alternative considered**: Per-entry `wrappingProvider` field with a provider registry. Rejected for v1.2.0; may revisit in Phase 5.

### D2: Reuse VaultStore.rotate() for persistence

The re-wrap operation builds an updated `VaultDocument` (new wrappedDek/wrappedHmac/wrappingAlgorithm, updated cmkProvider/cmkId, incremented version) and persists via `VaultStore.rotate()` which enforces optimistic locking.

**Rationale**: Reuses existing concurrency safety. If another node rotates concurrently, the operation fails fast with a clear error rather than corrupting key material.

### D3: KCV invariance as correctness gate

After unwrapping with the old provider and before re-wrapping with the new provider, the system SHALL recompute KCV and binding and verify they match stored values. After re-wrapping, a verification unwrap with the new provider SHALL confirm roundtrip correctness.

**Rationale**: Detects misconfigured target provider (wrong key, wrong algorithm) before committing. Prevents silent key corruption.

### D4: Runner as opt-in CommandLineRunner

`CmkProviderRewrapRunner` is registered as a Spring bean but disabled by default (`lightcrypto.migration.rewrap.enabled=false`). When enabled, it runs once at startup, performs re-wrap for all namespaces, and logs results. Supports `dry-run` mode for validation without mutation.

**Rationale**: Mirrors the existing `UserPlaintextBackfillRunner` pattern. Operators control execution via config toggles. No new actuator endpoint needed for v1.2.0.

### D5: Target provider resolution (three-level)

The runner resolves the target `CmkProvider` with the following priority:
1. **Bean name** (`target-bean-name`): Direct Spring `ApplicationContext.getBean()` lookup. Highest priority, useful for same-type key migration where providerId is identical.
2. **ProviderId + publicReference** (`target-provider-id` + `target-public-reference`): Dual match against registered beans. Useful for cloud KMS key rotation where keyId/keyName is known.
3. **ProviderId alone** (`target-provider-id`): Single match (backward compatible). Used for cross-type migration (e.g., LOCAL → ALI).

**Rationale**: Level 1 avoids requiring operators to pre-compute key fingerprints (especially for LOCAL keys stored in secret managers). Level 2 enables disambiguation when multiple providers share the same providerId. Level 3 preserves the original simple API for the common cross-type case.

### D6: Same-provider skip uses providerId + publicReference

The `rewrapVault` same-provider check compares BOTH `getProviderId()` AND `getPublicReference()` against the stored `VaultDocument.cmkProvider` and `cmkId`. Only when both match is the operation skipped.

**Rationale**: Same-type key rotation (e.g., AKV key A → AKV key B) shares the same providerId. Without publicReference comparison, the re-wrap would be incorrectly skipped. This eliminates the need for custom providerId wrappers or force flags.

### D7: LocalSymmetricCmkProvider publicReference format

The public reference retains the `"local-cmk-sha256:"` prefix followed by 16 hex chars (first 8 bytes of SHA-256 of the CMK).

**Rationale**: Cross-language compatibility — the Node.js implementation uses the same prefixed format. Changing it would break polyglot deployments sharing the same vault store.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| Old CMK unavailable during re-wrap → unwrap fails | Document prerequisite: old provider must remain configured and reachable until re-wrap completes. Runner validates with a canary unwrap before proceeding. |
| New CMK misconfigured → re-wrap produces garbage | D3 verification gate: post-rewrap unwrap + KCV check before persist. Operation is atomic per namespace — failure leaves vault unchanged. |
| Concurrent rotation during re-wrap | Optimistic locking via `VaultStore.rotate()` — concurrent modification causes clean failure, no corruption. |
| Large namespace count → extended write-pause | `rewrapAllVaults` processes sequentially with per-namespace error isolation. Typical deployments have < 100 namespaces; each re-wrap is a local crypto operation + one DB write (milliseconds). |
| Rollback needed after partial migration | Vaults are independent. Un-migrated namespaces still use old provider. Rollback = revert config to old provider. Already-migrated namespaces would need reverse re-wrap. |
