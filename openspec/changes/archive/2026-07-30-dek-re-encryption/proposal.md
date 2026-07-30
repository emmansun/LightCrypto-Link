## Why

Compliance frameworks (PCI-DSS 8.2.4, HIPAA, SOC2) require periodic rotation of data encryption keys AND eventual destruction of old key material. The current `rotateDek()` generates a new DEK for future writes, but existing documents remain encrypted under the old DEK indefinitely. This means old key material can never be safely destroyed — a compliance gap.

CMK re-wrap (completed) changes the wrapping layer without touching business data. DEK re-encryption is the complementary operation: it re-encrypts all existing business data under the active DEK, enabling safe retirement and eventual deletion of old key material.

## What Changes

- Add `DocumentRewriteStore` SPI in `lcl-spi` — adapter-agnostic contract for batch document scanning and atomic replacement with checkpoint support.
- Add `DekReEncryptionService` in `lcl-spring-boot-starter` — orchestration engine that scans documents, decrypts with old DEK, re-encrypts with active DEK, recomputes blind index with active HMAC key, and writes back atomically.
- Add `MongoDocumentRewriteStore` in `lcl-adapter-mongodb-core` — MongoDB implementation using cursor-based batch scanning and CAS replacement via `updatedAt`.
- Add `RETIRED` key status to `VaultDocument.KeyStatus` — marks old key entries as safely deletable after re-encryption completes for a namespace.
- Add `KeyVaultService.pruneRetiredKeys(namespace)` API for ops to manually remove RETIRED entries.
- Add `docs/key-lifecycle.md` — unified documentation explaining CMK re-wrap, DEK rotation, and DEK re-encryption as three layers of the key lifecycle.
- Emit `lcl.reencrypt.*` events via EventBus for observability.

## Capabilities

### New Capabilities

- `dek-re-encryption`: Adapter-agnostic orchestration engine for re-encrypting all business data under the active DEK, with blind index recomputation, CAS-based concurrency protection, checkpoint/resume, and key retirement marking.

### Modified Capabilities

- `key-vault`: Add `RETIRED` status to KeyStatus enum; add `pruneRetiredKeys(namespace)` operation; add `markKeysRetired(namespace, kidVersions)` internal operation.

## Impact

- **Code**: `lcl-spi` (new `DocumentRewriteStore` interface, `ScanOptions`, `RawDocument`), `lcl-spring-boot-starter` (new `DekReEncryptionService`, KeyVaultService extensions, KeyStatus enum), `lcl-adapter-mongodb-core` (new `MongoDocumentRewriteStore`).
- **APIs**: New public `DekReEncryptionService.reEncrypt(Class<?> entityClass, ReEncryptOptions)` and `reEncryptAll(ReEncryptOptions)`. New `KeyVaultService.pruneRetiredKeys(namespace)`.
- **Dependencies**: No new external dependencies.
- **Data**: Business documents are rewritten in-place (encrypted fields get new blob + new blind index). VaultDocument keys gain `RETIRED` status. Wire Format unchanged (same structure, just newer dekVersion).
- **Operational**: Long-running for large collections (hours). No write-pause required — CAS protection allows concurrent application writes. Checkpoint enables resume after interruption.

## Non-Goals

- Automatic scheduling (left to application — `@Scheduled`, cron, or manual trigger).
- Automatic deletion of RETIRED keys (ops decision, manual API call).
- Parallel/sharded re-encryption (future optimization; initial implementation is single-threaded per namespace).
- Node.js SDK implementation (will reference Java implementation separately).
