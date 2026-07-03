## ADDED Requirements

### Requirement: Key vault document structure
The system SHALL store wrapped DEK and HMAC key in a MongoDB collection named `__lcl_keyvault` with the following document structure: `_id` (String), `v` (int), `status` (String: ACTIVE/ROTATED/DISABLED), `dek` (embedded: wrapped Binary, algorithm String, kcv String), `hmk` (embedded: wrapped Binary, algorithm String, kcv String), `binding` (String), `cmk` (embedded: provider String, id String), `createdAt` (Date), `updatedAt` (Date).

#### Scenario: Vault document created on first init
- **WHEN** the vault is empty and auto-init is enabled
- **THEN** the system SHALL create a document with `_id` matching `key-vault-id`, `status` = "ACTIVE", and all crypto fields populated

#### Scenario: Unique index on vault
- **WHEN** the vault collection is created
- **THEN** the system SHALL ensure a unique index on `{_id: 1, status: 1}`

### Requirement: Auto-initialization on first startup
The system SHALL detect an empty vault (no document matching `key-vault-id` with `status = ACTIVE`) and automatically generate a new DEK (32 random bytes) and HMAC key (32 random bytes), wrap them with the CMK, compute KCV and binding, and insert the vault document.

#### Scenario: First startup with empty vault
- **WHEN** the application starts and no vault document exists
- **THEN** the system SHALL generate random DEK and HMAC key, wrap both with CMK, compute KCV for each, compute binding, insert the document, unwrap and verify, then log "LCL key vault initialized"

#### Scenario: Subsequent startup with existing vault
- **WHEN** the application starts and a vault document with `status = ACTIVE` exists
- **THEN** the system SHALL skip generation, load the wrapped keys, unwrap with CMK, verify KCV and binding, and proceed

#### Scenario: Concurrent first startup
- **WHEN** two application instances start simultaneously against an empty vault
- **THEN** both SHALL attempt upsert; one succeeds, the other detects the existing document and loads it without error

### Requirement: Vault configuration
The system SHALL read vault settings from `lcl.crypto` configuration properties: `key-vault-id` (required, String), `key-vault-database` (optional, defaults to the application's MongoDB database).

#### Scenario: Default database
- **WHEN** `key-vault-database` is not configured
- **THEN** the system SHALL use the same MongoDB database as the application's primary `MongoTemplate`

#### Scenario: Custom database
- **WHEN** `key-vault-database` is set to "encryption"
- **THEN** the system SHALL read/write the vault document in the "encryption" database

### Requirement: Startup integrity verification
The system SHALL verify the unwrapped DEK and HMAC key on every startup by recomputing KCV and binding and comparing against stored values. Any mismatch SHALL cause a `FatalCryptoException` and prevent application startup.

#### Scenario: KCV mismatch
- **WHEN** the unwrapped DEK's recomputed KCV does not match the stored `dek.kcv`
- **THEN** the system SHALL throw `FatalCryptoException` with message indicating DEK integrity failure

#### Scenario: Binding mismatch
- **WHEN** the recomputed binding does not match the stored `binding`
- **THEN** the system SHALL throw `FatalCryptoException` with message indicating key pair mismatch
