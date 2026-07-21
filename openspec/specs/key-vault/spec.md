## MODIFIED Requirements

### Requirement: Key vault document structure
The system SHALL store wrapped DEK and HMAC key via the `VaultStore` SPI with the following logical structure: `namespace` (String, canonical four-segment form), `keys[]` (array of KeyEntry: kid, status, wrappedDek, wrappedHmac, dekKcv, hmacKcv, binding, createdAt), `activeKid` (String), `version` (long), `cmkProvider` (String), `cmkId` (String), `createdAt` (Instant), `updatedAt` (Instant). The physical storage format is determined by the active `VaultStore` implementation.

#### Scenario: Vault document created on first init
- **WHEN** the vault is empty and auto-init is enabled
- **THEN** the system SHALL create a `VaultDocument` via `VaultStore.save()` with `status` = ACTIVE for the key entry, and all crypto fields populated

#### Scenario: Storage-agnostic persistence
- **WHEN** a vault document is saved
- **THEN** the system SHALL delegate to `VaultStore.save(doc)` without referencing any database-specific API

### Requirement: Auto-initialization on first startup
The system SHALL detect an empty vault (no document matching the namespace via `VaultStore.load()`) and automatically generate a new DEK (32 random bytes) and HMAC key (32 random bytes), wrap them with the CMK, compute KCV and binding, and persist via `VaultStore.save()`.

#### Scenario: First startup with empty vault
- **WHEN** the application starts and `VaultStore.load(namespace)` returns empty
- **THEN** the system SHALL generate random DEK and HMAC key, wrap both with CMK, compute KCV for each, compute binding, persist via `VaultStore.save()`, unwrap and verify, then log "LCL key vault initialized"

#### Scenario: Subsequent startup with existing vault
- **WHEN** the application starts and `VaultStore.load(namespace)` returns a document with an ACTIVE key
- **THEN** the system SHALL skip generation, load the wrapped keys, unwrap with CMK, verify KCV and binding, and proceed

#### Scenario: Concurrent first startup
- **WHEN** two application instances start simultaneously against an empty vault
- **THEN** both SHALL attempt save; the `VaultStore` implementation SHALL handle insert-if-absent semantics so that one succeeds and the other loads the existing document without error

### Requirement: Vault configuration
The system SHALL read vault settings from the new hierarchical configuration:
- Cache TTL from `lightcrypto.keyvault.cache.ttl` (Duration, default `PT1H`)
- Cache max entries from `lightcrypto.keyvault.cache.max-entries` (int, default 10000)
- Tenant from `lightcrypto.tenants.tenant` (String, default "default")
- Realm from `lightcrypto.tenants.realm` (String, default "default")

The vault collection name and database SHALL be provided by the adapter-specific configuration (e.g., `lightcrypto.adapters.mongodb.key-vault-collection`), NOT by core properties.

#### Scenario: Default tenant and realm
- **WHEN** neither `lightcrypto.tenants.tenant` nor `lightcrypto.tenants.realm` is configured
- **THEN** namespaces SHALL use "default" for both segments

#### Scenario: Custom tenant
- **WHEN** `lightcrypto.tenants.tenant` is set to "acme"
- **THEN** namespaces SHALL use "acme" as the tenant segment

#### Scenario: Cache TTL from keyvault properties
- **WHEN** `lightcrypto.keyvault.cache.ttl` is set to `PT30M`
- **THEN** the DEK cache SHALL expire entries after 30 minutes

### Requirement: Startup integrity verification
The system SHALL verify the unwrapped DEK and HMAC key on every startup by recomputing KCV and binding and comparing against stored values. Any mismatch SHALL cause a `FatalCryptoException` and prevent application startup.

#### Scenario: KCV mismatch
- **WHEN** the unwrapped DEK's recomputed KCV does not match the stored `dekKcv`
- **THEN** the system SHALL throw `FatalCryptoException` with message indicating DEK integrity failure

#### Scenario: Binding mismatch
- **WHEN** the recomputed binding does not match the stored `binding`
- **THEN** the system SHALL throw `FatalCryptoException` with message indicating key pair mismatch
