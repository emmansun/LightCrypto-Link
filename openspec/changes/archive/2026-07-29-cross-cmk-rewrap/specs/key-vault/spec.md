## ADDED Requirements

### Requirement: KeyVaultService rewrap operations
The `KeyVaultService` SHALL expose `rewrapVault(String namespace, CmkProvider targetProvider)` and `rewrapAllVaults(CmkProvider targetProvider)` as public operations for cross-CMK provider key migration. These operations SHALL NOT generate new DEK/HMAC key material — they only change the wrapping layer.

#### Scenario: rewrapVault delegates to VaultStore with optimistic locking
- **WHEN** `rewrapVault` is invoked for a namespace
- **THEN** the system SHALL load the VaultDocument, unwrap all entries with the current provider, re-wrap with the target provider, and persist via `VaultStore.rotate()` with version increment

#### Scenario: rewrapAllVaults uses VaultStore.loadAll
- **WHEN** `rewrapAllVaults` is invoked
- **THEN** the system SHALL call `VaultStore.loadAll()` to enumerate all namespaces and invoke `rewrapVault` for each with per-namespace error isolation

### Requirement: Rewrap invalidates DEK cache
After a successful `rewrapVault` for a namespace, the system SHALL evict the cached `NamespaceKeyContext` for that namespace so that subsequent operations use the newly re-wrapped keys via the target provider.

#### Scenario: Cache eviction after re-wrap
- **WHEN** `rewrapVault` completes successfully for namespace "default.default.User#phone"
- **THEN** the DEK cache entry for that namespace SHALL be invalidated, and the next encrypt/decrypt operation SHALL reload from VaultStore using the target provider
