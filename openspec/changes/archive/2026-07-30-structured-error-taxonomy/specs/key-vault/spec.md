## MODIFIED Requirements

### Requirement: Auto-initialization on first startup
The system SHALL detect an empty vault (no document matching the namespace via `VaultStore.load()`) and automatically generate a new DEK (32 random bytes) and HMAC key (32 random bytes), wrap them with the CMK, compute KCV and binding, and persist via `VaultStore.save()`. Auto-initialization SHALL only occur on the **encrypt path** (getActiveKid, getDek, getActiveHmacKey, rotateDek). The **decrypt path** (getDekByVersion, getHmacKeyByVersion) SHALL NOT trigger auto-initialization.

#### Scenario: First startup with empty vault (encrypt path)
- **WHEN** the application calls an encrypt-path method and `VaultStore.load(namespace)` returns empty
- **THEN** the system SHALL generate random DEK and HMAC key, wrap both with CMK, compute KCV for each, compute binding, persist via `VaultStore.save()`, unwrap and verify, then log "LCL key vault initialized"

#### Scenario: Subsequent startup with existing vault
- **WHEN** the application starts and `VaultStore.load(namespace)` returns a document with an ACTIVE key
- **THEN** the system SHALL skip generation, load the wrapped keys, unwrap with CMK, verify KCV and binding, and proceed

#### Scenario: Concurrent first startup
- **WHEN** two application instances start simultaneously against an empty vault
- **THEN** both SHALL attempt save; the `VaultStore` implementation SHALL handle insert-if-absent semantics so that one succeeds and the other loads the existing document without error

#### Scenario: Decrypt path with missing vault
- **WHEN** `getDekByVersion(namespace, version)` is called and `VaultStore.load(namespace)` returns empty
- **THEN** the system SHALL throw `KeyResolutionException` with the namespace and requested version; the system SHALL NOT create a new vault

#### Scenario: Decrypt path with missing DEK version
- **WHEN** `getDekByVersion(namespace, 5)` is called and the vault exists but contains no entry with kid matching version 5
- **THEN** the system SHALL throw `KeyResolutionException` with namespace and dekVersion=5

## ADDED Requirements

### Requirement: Decrypt-path read-only guarantee
The decrypt path of KeyVaultService (getDekByVersion, getHmacKeyByVersion) SHALL be strictly read-only with respect to vault state. These methods SHALL NOT invoke `VaultStore.save()` or `VaultStore.rotate()` under any circumstance. They SHALL only call `VaultStore.load()`.

#### Scenario: No vault mutation on decrypt
- **WHEN** getDekByVersion is called 100 times for a non-existent namespace
- **THEN** VaultStore.save() SHALL NOT be invoked; all 100 calls SHALL throw KeyResolutionException

#### Scenario: DEK re-encryption uses decrypt path safely
- **WHEN** DekReEncryptionService calls getDekByVersion during re-encryption
- **THEN** the vault SHALL already exist (created by prior rotateDek); getDekByVersion SHALL load and unwrap without mutation
