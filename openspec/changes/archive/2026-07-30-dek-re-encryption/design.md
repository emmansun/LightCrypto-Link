# Design: DEK Re-Encryption Orchestration

## Context

DEK re-encryption is the heaviest key lifecycle operation: it touches every encrypted document in the database. Unlike CMK re-wrap (seconds, vault-only) and DEK rotation (instant, vault-only), re-encryption is O(all documents) and runs for hours on large collections.

## Decisions

### D1: Adapter-agnostic SPI

A new `DocumentRewriteStore` interface in `lcl-spi` provides batch scan + atomic replace + checkpoint. The engine (`DekReEncryptionService`) depends only on this SPI, not on any database-specific API.

**Rationale**: Consistent with the existing thin-adapter philosophy (VaultStore, StorageAdapter, QueryTransformer). Future MySQL/PostgreSQL adapters implement the same contract.

### D2: Blind index recomputation

During re-encryption, the engine recomputes blind index values using the active HMAC key (paired with the active DEK in the vault).

**Rationale**: Without recomputation, old HMAC keys cannot be destroyed, defeating the compliance goal. The plaintext is already available during the decrypt step; HMAC computation adds <50µs per field — negligible overhead.

### D3: CAS concurrency via updatedAt

Document replacement uses `updatedAt` (or equivalent timestamp field) as a compare-and-swap condition. If the document was modified by the application during re-encryption, the replace silently skips (counted as `docsSkipped`).

**Rationale**: Lighter than adding a dedicated version field. No retry needed — the next re-encryption run will pick up the document. Avoids blocking application writes.

### D4: RETIRED key status

A new `RETIRED` status is added to `KeyStatus` enum: `ACTIVE → ROTATED → RETIRED`.

- `ROTATED`: still needed for decryption + blind index queries.
- `RETIRED`: all data migrated, key can be safely deleted by ops.

The engine automatically marks old entries as RETIRED when re-encryption completes for a namespace (all docs at active dekVersion). Deletion is a separate manual API (`pruneRetiredKeys`).

**Rationale**: Separates "safe to delete" signal from actual deletion. Ops may want to verify before destroying key material.

### D5: Pure API trigger (no built-in scheduler)

`DekReEncryptionService` exposes programmatic APIs only. Scheduling is the application's responsibility (`@Scheduled`, Quartz, actuator endpoint, CLI).

**Rationale**: Scheduling policies vary wildly (maintenance windows, compliance calendars, manual approval flows). A built-in scheduler would be either too rigid or too complex.

### D6: Performance strategy delegated to adapter

The engine processes documents in configurable batches. Performance optimizations (cursor type, read preference, bulk writes, parallelism) are adapter-specific concerns handled in `DocumentRewriteStore` implementations.

**Rationale**: MongoDB optimal tuning (noCursorTimeout, secondary reads, bulkWrite) differs fundamentally from PostgreSQL (COPY, cursor FETCH, advisory locks). The SPI provides `batchSize` and `resumeAfter` knobs; the rest is implementation detail.

### D7: Checkpoint-based resumability

The engine persists a checkpoint (cursor position / last processed `_id`) via `DocumentRewriteStore.saveCheckpoint()` every N batches. On restart, `loadCheckpoint()` enables resume without re-scanning completed documents.

**Rationale**: Hours-long operations must survive process restarts. Checkpoint is lightweight (one string) and idempotent.

## Risks / Trade-offs

- **CAS skip rate under heavy write load**: If the application updates documents faster than re-encryption processes them, skip rate could be high. Mitigation: run during low-traffic windows; multiple runs will converge.
- **Blind index query gap during re-encryption**: Documents mid-migration have mixed BI versions. The existing multi-version BI query path handles this (queries with all active HMAC keys).
- **Memory pressure**: Engine holds one batch in memory at a time. Batch size is configurable (default 500).
- **RETIRED key accumulation**: If ops never calls `pruneRetiredKeys`, vault grows. Acceptable — vault docs are tiny; monitoring can alert on RETIRED count.
