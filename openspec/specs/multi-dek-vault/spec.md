## Purpose

Define the behavioral contract for the multi-dek-vault capability to match current implementation behavior.

## Requirements
### Requirement: Per-entity-class vault document
The system SHALL create and manage a separate vault document in `__lcl_keyvault` for each entity class that contains `@Encrypted` fields. The vault document `_id` SHALL be `lcl-dek-{entitySimpleName}`.

#### Scenario: First-time vault initialization for an entity
- **WHEN** the application starts and no vault document exists for entity class `User`
- **THEN** the system SHALL create a vault document with `_id` = `lcl-dek-User` containing a new DEK/HMAC key pair

#### Scenario: Multiple entity classes have independent vaults
- **WHEN** entity classes `User` and `Order` both have `@Encrypted` fields
- **THEN** the system SHALL maintain separate vault documents `lcl-dek-User` and `lcl-dek-Order` with independent DEK/HMAC key pairs

#### Scenario: Concurrent initialization of same entity vault
- **WHEN** multiple application instances start simultaneously for the same entity class
- **THEN** only one instance SHALL successfully insert the vault document; other instances SHALL fall back to loading the existing document

### Requirement: Key versioning in vault document
Each vault document SHALL maintain a `keys[]` array where each entry contains `kid`, `status` (ACTIVE/ROTATED/REVOKED), wrapped DEK, wrapped HMAC key, KCV values, binding hash, and creation timestamp. The vault document SHALL have an `activeKid` field pointing to the currently active key entry.

#### Scenario: Initial vault has one active key
- **WHEN** a vault document is first created
- **THEN** it SHALL contain exactly one key entry with `status` = `ACTIVE` and `activeKid` SHALL point to that entry's `kid`

#### Scenario: Key ID format
- **WHEN** a new key entry is generated
- **THEN** the `kid` SHALL follow the format `v{n}-{8hexchars}` where `n` is the version number (1-based, incrementing)

#### Scenario: KCV and binding validation on load
- **WHEN** a vault document is loaded from MongoDB
- **THEN** for each key entry in `keys[]`, the system SHALL unwrap the DEK and HMAC key, verify KCV for both, and verify the binding hash between them. If any check fails, the system SHALL throw `FatalCryptoException`.

### Requirement: Entity-class-aware key lookup
`KeyVaultService` SHALL provide methods to get the active DEK/HMAC key for a specific entity class: `getActiveKid(Class<?> entityClass)`, `getDek(String kid)`, `getHmacKey(String kid)`. All cache reads SHALL be subject to TTL-based expiry as defined in the `dek-cache-ttl` capability.

#### Scenario: Encrypt uses active key for entity class
- **WHEN** encrypting a field of entity `User`
- **THEN** the system SHALL use the DEK from `User`'s active key entry

#### Scenario: Decrypt uses kid from encrypted sub-document
- **WHEN** decrypting a field whose sub-document has `_k` = `v1-a3b2c1d4`
- **THEN** the system SHALL look up the key entry with `kid` = `v1-a3b2c1d4` and use its DEK for decryption

#### Scenario: Decryption with rotated key succeeds
- **WHEN** a field was encrypted with key version `v1` and `v1` has been rotated (status = ROTATED)
- **THEN** decryption SHALL still succeed because the rotated key entry remains in the vault

#### Scenario: Cache expired before key lookup
- **WHEN** `getDek(kid)` is called and the cache entry for the owning entity class has expired
- **THEN** the system SHALL reload the vault from MongoDB (with KCV/binding verification), re-populate the cache, and return the requested DEK

### Requirement: DEK Rotation
`KeyVaultService.rotateKey(Class<?> entityClass)` SHALL mark the current active key as ROTATED, generate a new DEK/HMAC key pair with incremented version, insert the new entry into `keys[]`, update `activeKid`, and persist to MongoDB.

#### Scenario: Successful key rotation
- **WHEN** `rotateKey(User.class)` is called and User currently has active key `v1-a3b2c1d4`
- **THEN** the system SHALL mark `v1-a3b2c1d4` as ROTATED, create new entry `v2-{newSuffix}` as ACTIVE, update `activeKid` to the new kid, and save the vault document

#### Scenario: Rotation generates cryptographically independent keys
- **WHEN** a key rotation occurs
- **THEN** the new DEK and HMAC key SHALL be independently generated using `SecureRandom` and SHALL NOT be derivable from the previous keys

#### Scenario: Rotation preserves KCV and binding integrity
- **WHEN** a new key entry is created during rotation
- **THEN** the system SHALL compute and store KCV for both DEK and HMAC key, and compute and store the binding hash between them

### Requirement: Auto-init on first encrypted save
When a save operation involves an entity class whose vault has not been initialized, `KeyVaultService` SHALL lazily initialize the vault for that entity class before proceeding with encryption.

#### Scenario: Lazy vault initialization on first save
- **WHEN** a `User` entity is saved for the first time and no vault exists for `User`
- **THEN** `KeyVaultService` SHALL create and initialize the vault for `User` before encrypting the field

### Requirement: Encrypted sub-document includes kid
Every encrypted BSON sub-document SHALL include a `_k` field containing the `kid` of the key version used for encryption.

#### Scenario: Encrypted document has kid field
- **WHEN** a field is encrypted using active key `v1-a3b2c1d4`
- **THEN** the resulting sub-document SHALL contain `{c: Binary, b?: String, _e: 1, _t: String, _k: "v1-a3b2c1d4"}`

#### Scenario: Unknown kid during decryption
- **WHEN** a sub-document references a `kid` not present in the vault
- **THEN** the system SHALL throw `FatalCryptoException` with a message indicating the missing kid


