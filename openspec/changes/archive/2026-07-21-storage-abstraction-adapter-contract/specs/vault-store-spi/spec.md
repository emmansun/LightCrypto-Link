## ADDED Requirements

### Requirement: VaultStore interface contract
The `VaultStore` interface in `lcl-spi` SHALL define the following methods for vault document persistence:
- `void save(VaultDocument doc)` — persist a vault document (upsert semantics)
- `Optional<VaultDocument> load(String namespace)` — load by canonical namespace
- `boolean exists(String namespace)` — existence check
- `VaultDocument rotate(VaultDocument updatedDoc)` — persist with optimistic-locking (CAS on `version` field); SHALL throw `OptimisticLockException` if the stored version does not match `updatedDoc.version() - 1`
- `List<VaultDocument> loadAll()` — bulk load all vault documents (for startup preloading)

#### Scenario: Save new vault document
- **WHEN** `save(doc)` is called with a `VaultDocument` whose namespace does not yet exist in the store
- **THEN** the store SHALL persist the document and it SHALL be retrievable via `load(namespace)`

#### Scenario: Save existing vault document (upsert)
- **WHEN** `save(doc)` is called with a `VaultDocument` whose namespace already exists
- **THEN** the store SHALL overwrite the existing document with the new content

#### Scenario: Load non-existent namespace
- **WHEN** `load("unknown.namespace.Entity#field")` is called
- **THEN** the store SHALL return `Optional.empty()`

#### Scenario: Rotate with correct version
- **WHEN** `rotate(updatedDoc)` is called where `updatedDoc.version()` equals stored version + 1
- **THEN** the store SHALL persist the updated document and return it

#### Scenario: Rotate with stale version (concurrent modification)
- **WHEN** `rotate(updatedDoc)` is called where `updatedDoc.version()` does NOT equal stored version + 1
- **THEN** the store SHALL throw `OptimisticLockException` and NOT modify the stored document

#### Scenario: LoadAll returns all vaults
- **WHEN** `loadAll()` is called and 3 namespaces have vault documents
- **THEN** the store SHALL return a list of exactly 3 `VaultDocument` instances

### Requirement: VaultDocument data model
The `VaultDocument` record SHALL contain the following fields:
- `namespace` (String) — canonical four-segment namespace
- `keys` (List<KeyEntry>) — versioned key entries
- `activeKid` (String) — kid of the currently active key
- `version` (long) — monotonically increasing document version for optimistic locking
- `cmkProvider` (String) — identifier of the CMK provider used for wrapping
- `cmkId` (String) — CMK key identifier in the external KMS
- `createdAt` (Instant) — document creation timestamp
- `updatedAt` (Instant) — last modification timestamp

#### Scenario: New vault document has version 1
- **WHEN** a vault document is first created
- **THEN** its `version` SHALL be 1

#### Scenario: Rotation increments version
- **WHEN** a vault document is rotated
- **THEN** the new document's `version` SHALL be previous version + 1

#### Scenario: CMK info recorded on creation
- **WHEN** a vault document is created using CMK provider "alibaba-kms" with key ID "key-123"
- **THEN** the document SHALL have `cmkProvider` = "alibaba-kms" and `cmkId` = "key-123"

### Requirement: OptimisticLockException
The `lcl-spi` module SHALL define `OptimisticLockException extends CryptoException` for CAS failure signaling.

#### Scenario: Exception carries namespace context
- **WHEN** `OptimisticLockException` is thrown for namespace "default.default.User#phone"
- **THEN** the exception message SHALL contain the namespace string

### Requirement: VaultStore implementations are thread-safe
Any `VaultStore` implementation SHALL be safe for concurrent access from multiple threads without external synchronization.

#### Scenario: Concurrent load and save
- **WHEN** thread A calls `load(ns)` while thread B calls `save(doc)` for the same namespace
- **THEN** thread A SHALL receive either the old or new document, never a partially-written state
