## Why

Users who start with `LOCAL_SYMMETRIC` CMK (development/evaluation) need a supported path to migrate to a production cloud KMS (Azure Key Vault, Alibaba Cloud KMS) — or between cloud providers. The current architecture cleanly separates CMK (wraps DEK) from DEK (encrypts data), making it possible to migrate by re-wrapping vault key material alone, without re-encrypting any business data. However, no API or tooling exists today to perform this re-wrap; users would have to manually manipulate vault documents, risking key loss or integrity violations.

## What Changes

- Add `KeyVaultService.rewrapVault(namespace, newProvider)` API that unwraps all key entries (including ROTATED) with the current provider, re-wraps with the target provider, verifies KCV invariance, and persists atomically with optimistic locking.
- Add `KeyVaultService.rewrapAllVaults(newProvider)` convenience method iterating all namespaces via `VaultStore.loadAll()`.
- Add `CmkProviderRewrapRunner` — a Spring Boot `CommandLineRunner` with configuration toggles (`enabled`, `dry-run`, `target-provider-id`), progress logging, and per-namespace error isolation.
- Add migration guide document `docs/migration/cross-cmk-provider-migration.md` covering prerequisites, step-by-step procedure, transition-window strategy, rollback, and checklist.
- Add a demo rewrap runner in the `basic-crud` example (mirroring the existing `UserPlaintextBackfillRunner` pattern).
- Emit `lcl.rewrap.*` events via the existing EventBus for observability.

## Capabilities

### New Capabilities

- `cmk-rewrap`: Programmatic API and operational tooling for re-wrapping vault DEK/HMAC keys under a different CMK provider without data re-encryption.

### Modified Capabilities

- `key-vault`: Add `rewrapVault` and `rewrapAllVaults` operations to the KeyVaultService contract, extending its lifecycle management beyond initialization and rotation.

## Impact

- **Code**: `lcl-spring-boot-starter` (KeyVaultService, new CmkProviderRewrapRunner, auto-configuration for runner bean), `lcl-examples/basic-crud` (demo runner).
- **APIs**: New public methods on `KeyVaultService`; new configuration properties under `lightcrypto.migration.rewrap.*`.
- **Dependencies**: No new external dependencies.
- **Data**: VaultDocument `cmkProvider`, `cmkId`, and `keys[].wrappedDek/wrappedHmac/wrappingAlgorithm` fields are updated in-place. No schema change. Business data and blind index values remain untouched.
- **Operational**: Brief write-pause required during re-wrap window (seconds-level for typical namespace counts).
