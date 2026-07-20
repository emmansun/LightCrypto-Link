## MODIFIED Requirements

### Requirement: Per-namespace vault document
The system SHALL create and manage a separate vault document in `__lcl_keyvault` for each namespace that contains encrypted fields. The vault document `_id` SHALL be `lcl-dek-{namespace}` where namespace is the canonical four-segment form (e.g., `lcl-dek-default.default.User`). This replaces the previous per-entity-class naming (`lcl-dek-{entitySimpleName}`).

#### Scenario: First-time vault initialization for a namespace
- **WHEN** the application starts and no vault document exists for namespace `default.default.User`
- **THEN** the system SHALL create a vault document with `_id` = `lcl-dek-default.default.User` containing a new DEK/HMAC key pair

#### Scenario: Multiple namespaces have independent vaults
- **WHEN** namespaces `tenantA.default.User` and `tenantB.default.User` both have encrypted fields
- **THEN** the system SHALL maintain separate vault documents with independent DEK/HMAC key pairs, providing cryptographic tenant isolation

#### Scenario: Same entity different tenants have different DEKs
- **WHEN** entity `User` is used under namespaces `tenantA.default.User` and `tenantB.default.User`
- **THEN** each namespace SHALL have its own vault with independently generated DEK/HMAC keys

#### Scenario: Concurrent initialization of same namespace vault
- **WHEN** multiple application instances start simultaneously for the same namespace
- **THEN** only one instance SHALL successfully insert the vault document; other instances SHALL fall back to loading the existing document

### Requirement: Key versioning in vault document (unchanged structure)
Each vault document SHALL maintain a `keys[]` array where each entry contains `kid`, `status` (ACTIVE/ROTATED/REVOKED), wrapped DEK, wrapped HMAC key, KCV values, binding hash, and creation timestamp. The vault document SHALL have an `activeKid` field pointing to the currently active key entry. The vault document SHALL additionally store a `namespace` field recording the canonical namespace this vault serves.

#### Scenario: Vault document stores namespace
- **WHEN** a vault document is created for namespace `default.default.User`
- **THEN** the document SHALL contain field `namespace` = `"default.default.User"` alongside `_id` = `"lcl-dek-default.default.User"`

#### Scenario: Initial vault has one active key
- **WHEN** a vault document is first created
- **THEN** it SHALL contain exactly one key entry with `status` = `ACTIVE` and `activeKid` SHALL point to that entry's `kid`

#### Scenario: Key ID format
- **WHEN** a new key entry is generated
- **THEN** the `kid` SHALL follow the format `v{n}-{8hexchars}` where `n` is the version number (1-based, incrementing)

#### Scenario: KCV and binding validation on load
- **WHEN** a vault document is loaded from MongoDB
- **THEN** for each key entry in `keys[]`, the system SHALL unwrap the DEK and HMAC key, verify KCV for both, and verify the binding hash between them. If any check fails, the system SHALL throw `FatalCryptoException`.

### Requirement: Namespace-aware key lookup
`KeyVaultService` SHALL provide methods to get the active DEK/HMAC key for a specific namespace: `getActiveKid(String namespace)`, `getDek(String kid)`, `getHmacKey(String kid)`. The namespace SHALL be resolved from the entity class and field annotation metadata at the listener layer. All cache reads SHALL be subject to TTL-based expiry.

#### Scenario: Encrypt uses active key for namespace
- **WHEN** encrypting a field with namespace `default.default.User`
- **THEN** the system SHALL use the DEK from that namespace's active key entry

#### Scenario: Decrypt uses kid from encrypted sub-document
- **WHEN** decrypting a field whose sub-document has `_k` = `v1-a3b2c1d4`
- **THEN** the system SHALL look up the key entry with `kid` = `v1-a3b2c1d4` and use its DEK for decryption

#### Scenario: Decryption with rotated key succeeds
- **WHEN** a field was encrypted with key version `v1` and `v1` has been rotated (status = ROTATED)
- **THEN** decryption SHALL still succeed because the rotated key entry remains in the vault

#### Scenario: Cache expired before key lookup
- **WHEN** `getDek(kid)` is called and the cache entry for the owning namespace has expired
- **THEN** the system SHALL reload the vault from MongoDB (with KCV/binding verification), re-populate the cache, and return the requested DEK

### Requirement: DEK Rotation per namespace
`KeyVaultService.rotateKey(String namespace)` SHALL mark the current active key as ROTATED, generate a new DEK/HMAC key pair with incremented version, insert the new entry into `keys[]`, update `activeKid`, and persist to MongoDB.

#### Scenario: Successful key rotation
- **WHEN** `rotateKey("default.default.User")` is called and the namespace currently has active key `v1-a3b2c1d4`
- **THEN** the system SHALL mark `v1-a3b2c1d4` as ROTATED, create new entry `v2-{newSuffix}` as ACTIVE, update `activeKid` to the new kid, and save the vault document

#### Scenario: Rotation generates cryptographically independent keys
- **WHEN** a key rotation occurs
- **THEN** the new DEK and HMAC key SHALL be independently generated using `SecureRandom` and SHALL NOT be derivable from the previous keys

#### Scenario: Rotation preserves KCV and binding integrity
- **WHEN** a new key entry is created during rotation
- **THEN** the system SHALL compute and store KCV for both DEK and HMAC key, and compute and store the binding hash between them

### Requirement: Auto-init on first encrypted save (namespace-based)
When a save operation involves a field whose namespace vault has not been initialized, `KeyVaultService` SHALL lazily initialize the vault for that namespace before proceeding with encryption.

#### Scenario: Lazy vault initialization on first save
- **WHEN** a `User` entity is saved for the first time and no vault exists for namespace `default.default.User`
- **THEN** `KeyVaultService` SHALL create and initialize the vault for that namespace before encrypting the field

### Requirement: Encrypted sub-document includes kid (unchanged)
Every encrypted BSON sub-document SHALL include a `_k` field containing the `kid` of the key version used for encryption.

#### Scenario: Encrypted document has kid field
- **WHEN** a field is encrypted using active key `v1-a3b2c1d4`
- **THEN** the resulting sub-document SHALL contain `{c: Binary, b?: String, _e: 1, _t: String, _k: "v1-a3b2c1d4"}`

#### Scenario: Unknown kid during decryption
- **WHEN** a sub-document references a `kid` not present in the vault
- **THEN** the system SHALL throw `FatalCryptoException` with a message indicating the missing kid
