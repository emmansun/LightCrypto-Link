## 1. SPI Layer — DocumentRewriteStore

- [x] 1.1 Add `RawDocument` record to `lcl-spi` (id, fields map, concurrencyToken)
- [x] 1.2 Add `ScanOptions` record (collectionHint, batchSize, resumeAfter, maxScanTime)
- [x] 1.3 Add `DocumentRewriteStore` interface (scan, replace, replaceBatch, saveCheckpoint, loadCheckpoint)
- [x] 1.4 Verify compilation: `mvn compile -pl lcl-spi`

## 2. Key Status — RETIRED

- [x] 2.1 Add `RETIRED` to `VaultDocument.KeyStatus` enum
- [x] 2.2 Add `KeyVaultService.markKeysRetired(namespace, Set<String> kids)` — transition ROTATED → RETIRED
- [x] 2.3 Add `KeyVaultService.pruneRetiredKeys(namespace)` — remove RETIRED entries, persist atomically
- [x] 2.4 Guard: `getDekByVersion` throws FatalCryptoException if resolved entry is RETIRED
- [x] 2.5 Unit tests for RETIRED lifecycle (mark, prune, guard)

## 3. DekReEncryptionService Engine

- [x] 3.1 Create `ReEncryptOptions` record (entityClass, batchSize, taskId, dryRun, checkpointInterval)
- [x] 3.2 Create `ReEncryptResult` record (namespace, docsProcessed, docsSkipped, docsFailed, fieldsReEncrypted, durationMicros)
- [x] 3.3 Implement `DekReEncryptionService.reEncrypt(Class<?>, ReEncryptOptions)` — scan → per-field decrypt/re-encrypt/BI-recompute → CAS replace → checkpoint
- [x] 3.4 Implement `reEncryptAll(ReEncryptOptions)` — iterate all registered entity classes via EntityMetadataCache
- [x] 3.5 On completion: call `markKeysRetired` for namespaces with zero old-version docs
- [x] 3.6 Emit `lcl.reencrypt.batch.completed` and `lcl.reencrypt.namespace.completed` events
- [x] 3.7 Register `DekReEncryptionService` bean in auto-configuration
- [x] 3.8 Verify compilation: `mvn compile -pl lcl-spring-boot-starter`

## 4. MongoDB Adapter — MongoDocumentRewriteStore

- [x] 4.1 Implement `MongoDocumentRewriteStore` in `lcl-adapter-mongodb-core`
- [x] 4.2 `scan()`: MongoTemplate cursor with batchSize, noCursorTimeout, stable _id order
- [x] 4.3 `replace()`: replaceOne with `{_id: X, updatedAt: token}` CAS filter
- [x] 4.4 `replaceBatch()`: bulkWrite with ordered=false for throughput
- [x] 4.5 Checkpoint: persist to `__lcl_checkpoints` collection (taskId → cursorState)
- [x] 4.6 Auto-configuration: register bean with `@ConditionalOnBean(MongoTemplate.class)`
- [x] 4.7 Integration test: re-encrypt a small collection end-to-end (embedded MongoDB)

## 5. Unit Tests

- [x] 5.1 Engine test: mock DocumentRewriteStore, verify decrypt → re-encrypt → BI recompute → replace flow
- [x] 5.2 Engine test: dekVersion == active → skip (no replace call)
- [x] 5.3 Engine test: CAS conflict (replace returns false) → docsSkipped incremented
- [x] 5.4 Engine test: checkpoint save/load → resume skips processed docs
- [x] 5.5 Engine test: completion → markKeysRetired called
- [x] 5.6 Run full test suite: `mvn verify -pl lcl-spring-boot-starter,lcl-adapter-mongodb-core`

## 6. Documentation

- [x] 6.1 Create `docs/key-lifecycle.md` — unified guide: CMK re-wrap vs DEK rotation vs DEK re-encryption (scope, cost, when to use, sequence diagram)
- [x] 6.2 Update `docs/configuration.md` if new config properties are added
- [x] 6.3 Update `docs/migration/cross-cmk-provider-migration.md` to cross-reference key-lifecycle doc

## 7. Quality Gates

- [x] 7.1 Run SpotBugs: `mvn -pl lcl-spring-boot-starter spotbugs:check`
- [x] 7.2 Run full build: `mvn clean verify`
- [x] 7.3 Commit with structured message
