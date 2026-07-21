## MODIFIED Requirements

### Requirement: Per-namespace vault document
The system SHALL create and manage a separate vault document via `VaultStore` for each namespace that contains encrypted fields. The vault document is identified by its canonical namespace. The physical `_id` format is determined by the `VaultStore` implementation (e.g., MongoDB uses `lcl-dek-{namespace}`).

#### Scenario: First-time vault initialization for a namespace
- **WHEN** the application starts and `VaultStore.load("default.default.User#phone")` returns empty
- **THEN** the system SHALL create a vault document via `VaultStore.save()` containing a new DEK/HMAC key pair

#### Scenario: Multiple namespaces have independent vaults
- **WHEN** namespaces `tenantA.default.User#phone` and `tenantB.default.User#phone` both have encrypted fields
- **THEN** the system SHALL maintain separate vault documents with independent DEK/HMAC key pairs via `VaultStore`

#### Scenario: Same entity different tenants have different DEKs
- **WHEN** entity `User` is used under namespaces `tenantA.default.User#phone` and `tenantB.default.User#phone`
- **THEN** each namespace SHALL have its own vault with independently generated DEK/HMAC keys

#### Scenario: Concurrent initialization of same namespace vault
- **WHEN** multiple application instances start simultaneously for the same namespace
- **THEN** the `VaultStore` implementation SHALL ensure only one instance successfully creates the document; others SHALL fall back to loading

### Requirement: Key versioning in vault document
Each vault document SHALL maintain a `keys[]` list where each entry contains `kid`, `status` (ACTIVE/ROTATED/REVOKED), wrapped DEK, wrapped HMAC key, KCV values, binding hash, and creation timestamp. The vault document SHALL have an `activeKid` field and a `version` field for optimistic locking.

#### Scenario: Vault document stores namespace
- **WHEN** a vault document is created for namespace `default.default.User#phone`
- **THEN** the document SHALL contain field `namespace` = `"default.default.User#phone"`

#### Scenario: Initial vault has one active key
- **WHEN** a vault document is first created
- **THEN** it SHALL contain exactly one key entry with `status` = ACTIVE and `activeKid` SHALL point to that entry's `kid`

#### Scenario: Key ID format
- **WHEN** a new key entry is generated
- **THEN** the `kid` SHALL follow the format `v{n}-{8hexchars}` where `n` is the version number (1-based, incrementing)

#### Scenario: KCV and binding validation on load
- **WHEN** a vault document is loaded from `VaultStore`
- **THEN** for each key entry in `keys[]`, the system SHALL unwrap the DEK and HMAC key, verify KCV for both, and verify the binding hash. If any check fails, the system SHALL throw `FatalCryptoException`.

### Requirement: Namespace-aware key lookup
`KeyVaultService` SHALL provide methods to get the active DEK/HMAC key for a specific namespace: `getActiveKid(String namespace)`, `getDekByVersion(String namespace, int dekVersion)`, `getActiveHmacKey(String namespace)`. The namespace SHALL be resolved from the entity class and field annotation metadata. All cache reads SHALL be subject to TTL-based expiry.

#### Scenario: Encrypt uses active key for namespace
- **WHEN** encrypting a field with namespace `default.default.User#phone`
- **THEN** the system SHALL use the DEK from that namespace's active key entry

#### Scenario: Decrypt uses dekVersion from wire format blob
- **WHEN** decrypting a field whose wire format blob contains dekVersion = 1
- **THEN** the system SHALL call `getDekByVersion(namespace, 1)` to obtain the correct DEK

#### Scenario: Decryption with rotated key succeeds
- **WHEN** a field was encrypted with key version 1 and version 1 has been rotated (status = ROTATED)
- **THEN** decryption SHALL still succeed because the rotated key entry remains in the vault

#### Scenario: Cache expired before key lookup
- **WHEN** `getDekByVersion(namespace, version)` is called and the cache entry has expired
- **THEN** the system SHALL reload the vault from `VaultStore` (with KCV/binding verification), re-populate the cache, and return the requested DEK

### Requirement: DEK Rotation per namespace
`KeyVaultService.rotateDek(String namespace)` SHALL mark the current active key as ROTATED, generate a new DEK/HMAC key pair with incremented version, insert the new entry into `keys[]`, update `activeKid`, increment `version`, and persist via `VaultStore.rotate()` with optimistic locking.

#### Scenario: Successful key rotation
- **WHEN** `rotateDek("default.default.User#phone")` is called and the namespace currently has active key `v1-a3b2c1d4`
- **THEN** the system SHALL mark `v1-a3b2c1d4` as ROTATED, create new entry `v2-{newSuffix}` as ACTIVE, update `activeKid`, and persist via `VaultStore.rotate()`

#### Scenario: Rotation generates cryptographically independent keys
- **WHEN** a key rotation occurs
- **THEN** the new DEK and HMAC key SHALL be independently generated using `SecureRandom`

#### Scenario: Rotation preserves KCV and binding integrity
- **WHEN** a new key entry is created during rotation
- **THEN** the system SHALL compute and store KCV for both DEK and HMAC key, and compute and store the binding hash

#### Scenario: Concurrent rotation conflict
- **WHEN** two instances attempt rotation simultaneously for the same namespace
- **THEN** one SHALL succeed and the other SHALL receive `OptimisticLockException` from `VaultStore.rotate()`

### Requirement: Auto-init on first encrypted save (namespace-based)
When a save operation involves a field whose namespace vault has not been initialized, `KeyVaultService` SHALL lazily initialize the vault for that namespace via `VaultStore` before proceeding with encryption.

#### Scenario: Lazy vault initialization on first save
- **WHEN** a `User` entity is saved for the first time and `VaultStore.load(namespace)` returns empty
- **THEN** `KeyVaultService` SHALL create and persist the vault via `VaultStore.save()` before encrypting the field

## REMOVED Requirements

### Requirement: Encrypted sub-document includes kid
**Reason**: Wire Format V1 embeds dekVersion in the blob itself; the `_k` field is no longer stored in the sub-document.
**Migration**: dekVersion is extracted from the wire format blob during decryption via `WireFormatDecoder`.
