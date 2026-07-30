## MODIFIED Requirements

### Requirement: DocumentRewriteStore SPI
The system SHALL provide a `DocumentRewriteStore` interface in `lcl-spi` enabling adapter-agnostic batch document scanning, atomic replacement, and checkpoint persistence.

#### Scenario: Batch scan returns raw documents
- **WHEN** `scan(ScanOptions)` is invoked with a collection hint and batch size
- **THEN** the implementation SHALL return a `CloseableIterator<RawDocument>` yielding documents in stable order without decrypting any fields

#### Scenario: Atomic replace with per-field kid CAS
- **WHEN** `replace(RawDocument)` is invoked and `fieldKids` is non-empty
- **THEN** the implementation SHALL atomically replace the document ONLY IF each encrypted field's current `_k` value matches the kid snapshot in `fieldKids`, returning `true` on success and `false` on conflict

#### Scenario: Replace with empty fieldKids
- **WHEN** `replace(RawDocument)` is invoked and `fieldKids` is empty
- **THEN** the implementation SHALL use `_id`-only filter (no CAS protection)

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
- **THEN** the engine SHALL scan all documents for that entity, extract per-field kid snapshots from encrypted sub-documents, and for each encrypted field with `dekVersion != activeKid`, decrypt with the old DEK, re-encrypt with the active DEK, recompute blind index with the active HMAC key, and write back via `DocumentRewriteStore.replace()`

#### Scenario: Skip already-current documents
- **WHEN** a document's encrypted field already has `dekVersion == activeKid`
- **THEN** the engine SHALL skip that field without modification (counted as `fieldsSkipped`)

#### Scenario: CAS conflict handling with kid mismatch
- **WHEN** `DocumentRewriteStore.replace()` returns `false` because an encrypted field's `_k` was changed concurrently
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

## ADDED Requirements

### Requirement: RawDocument carries per-field kid snapshot
The `RawDocument` record SHALL include a `fieldKids` map (`Map<String, String>`) containing field path to kid snapshot entries for each encrypted field that has a `_k` sub-field. The record SHALL NOT include a `concurrencyToken` field.

#### Scenario: fieldKids populated during scan
- **WHEN** a document with encrypted field `phone` containing `{_k: "v1-kid-123", _e: "..."}` is scanned
- **THEN** the resulting `RawDocument.fieldKids()` SHALL contain entry `("phone", "v1-kid-123")`

#### Scenario: fieldKids empty for blobs without _k
- **WHEN** a document with encrypted field `phone` containing a plain Base64URL string (no `_k` sub-field) is scanned
- **THEN** the resulting `RawDocument.fieldKids()` SHALL NOT contain an entry for `phone`

### Requirement: MongoDB adapter uses dot-notation kid filter
The `MongoDocumentRewriteStore` SHALL construct CAS filters using MongoDB dot-notation for per-field kid matching: `{_id: <id>, "<field>._k": <kid>}`.

#### Scenario: Single encrypted field CAS
- **WHEN** replacing a document with `fieldKids = {"phone": "v1-kid"}`
- **THEN** the MongoDB filter SHALL be `{_id: <rawId>, "phone._k": "v1-kid"}`

#### Scenario: Multiple encrypted fields CAS
- **WHEN** replacing a document with `fieldKids = {"phone": "v1-kid", "email": "v1-kid"}`
- **THEN** the MongoDB filter SHALL be `{_id: <rawId>, "phone._k": "v1-kid", "email._k": "v1-kid"}`

#### Scenario: Concurrent modification detected
- **WHEN** the application re-encrypts `phone` with a new kid between scan and replace
- **THEN** the filter `"phone._k": "old-kid"` SHALL NOT match, `replace()` SHALL return `false`
