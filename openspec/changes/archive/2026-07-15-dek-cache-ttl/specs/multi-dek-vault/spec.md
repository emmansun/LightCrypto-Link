## MODIFIED Requirements

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
