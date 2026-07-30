## ADDED Requirements

### Requirement: DocumentRewriteStore SPI
The system SHALL provide a `DocumentRewriteStore` interface in `lcl-spi` enabling adapter-agnostic batch document scanning, atomic replacement, and checkpoint persistence.

#### Scenario: Batch scan returns raw documents
- **WHEN** `scan(ScanOptions)` is invoked with a collection hint and batch size
- **THEN** the implementation SHALL return a `CloseableIterator<RawDocument>` yielding documents in stable order without decrypting any fields

#### Scenario: Atomic replace with CAS
- **WHEN** `replace(RawDocument)` is invoked
- **THEN** the implementation SHALL atomically replace the document ONLY IF the concurrency token (e.g., `updatedAt`) matches the value read during scan, returning `true` on success and `false` on conflict

#### Scenario: Batch replace optimization
- **WHEN** `replaceBatch(List<RawDocument>)` is invoked
- **THEN** the implementation MAY use database-specific bulk operations for throughput, returning the count of successfully replaced documents

#### Scenario: Checkpoint save and load
- **WHEN** `saveCheckpoint(taskId, cursorState)` is invoked
- **THEN** the implementation SHALL persist the cursor state such that a subsequent `loadCheckpoint(taskId)` returns it, enabling resume after interruption

### Requirement: DekReEncryptionService orchestration
The system SHALL provide a `DekReEncryptionService` that re-encrypts all documents of a given entity class under the active DEK, recomputing blind index values.

#### Scenario: Re-encrypt entity class
- **WHEN** `reEncrypt(User.class, options)` is invoked
- **THEN** the engine SHALL scan all documents for that entity, and for each encrypted field with `dekVersion != activeKid`, decrypt with the old DEK, re-encrypt with the active DEK, recompute blind index with the active HMAC key, and write back via `DocumentRewriteStore.replace()`

#### Scenario: Skip already-current documents
- **WHEN** a document's encrypted field already has `dekVersion == activeKid`
- **THEN** the engine SHALL skip that field without modification (counted as `fieldsSkipped`)

#### Scenario: CAS conflict handling
- **WHEN** `DocumentRewriteStore.replace()` returns `false` (document was modified concurrently)
- **THEN** the engine SHALL skip the document (counted as `docsSkipped`) and continue processing without error

#### Scenario: Checkpoint-based resume
- **WHEN** re-encryption is interrupted and restarted with the same `taskId`
- **THEN** the engine SHALL resume from the last saved checkpoint, not re-processing already-completed documents

#### Scenario: Completion marks keys RETIRED
- **WHEN** re-encryption completes for a namespace and zero documents remain at old dekVersion
- **THEN** the engine SHALL mark all ROTATED key entries for that namespace as RETIRED via `KeyVaultService`

#### Scenario: Event emission
- **WHEN** re-encryption progresses
- **THEN** the engine SHALL emit `lcl.reencrypt.batch.completed` (L2) per batch and `lcl.reencrypt.namespace.completed` (L2) on full completion, including docsProcessed, docsSkipped, docsFailed, and durationMicros

### Requirement: RETIRED key status
The `KeyStatus` enum SHALL include a `RETIRED` value indicating that a key entry is no longer needed for any runtime operation and can be safely deleted.

#### Scenario: RETIRED keys not used for decryption
- **WHEN** a decryption request resolves to a key entry with status RETIRED
- **THEN** the system SHALL throw `FatalCryptoException` indicating the key has been retired (data should have been re-encrypted before retirement)

#### Scenario: Prune retired keys
- **WHEN** `KeyVaultService.pruneRetiredKeys(namespace)` is invoked
- **THEN** the system SHALL remove all RETIRED entries from the vault document and persist atomically

### Requirement: Key lifecycle documentation
The project SHALL include `docs/key-lifecycle.md` explaining the three key operations (CMK re-wrap, DEK rotation, DEK re-encryption), their scope, cost, and when to use each.
