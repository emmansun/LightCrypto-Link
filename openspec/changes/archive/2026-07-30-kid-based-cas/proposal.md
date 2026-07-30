## Why

DEK re-encryption currently uses `updatedAt` timestamp as the CAS (Compare-And-Swap) concurrency token. This creates two problems:

1. **Business schema dependency**: Entities without an `updatedAt` field lose CAS protection entirely — the filter degrades to `_id`-only, allowing concurrent writes to be silently overwritten during re-encryption.
2. **Coarse granularity**: Any field modification (even unrelated to encryption) triggers CAS failure, causing unnecessary skips and re-processing.

The Node.js SDK solves this by using the encrypted sub-document's `_k` (kid) field as the CAS condition, which is more precise and requires zero business schema assumptions.

## What Changes

- **Replace CAS strategy**: `MongoDocumentRewriteStore` builds per-field `_k` filter conditions instead of `updatedAt`
- **Replace RawDocument.concurrencyToken with fieldKids**: `Map<String, String>` carrying per-field kid snapshots captured during scan
- **DekReEncryptionService populates kid snapshot**: During scan, extract `_k` from each encrypted sub-document
- **Remove concurrencyField parameter**: No longer needed as CAS is field-level

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

- `dek-re-encryption`: CAS strategy changes from document-level `updatedAt` to per-field `_k` (kid) matching

## Impact

- **lcl-spi**: `RawDocument` record replaces `concurrencyToken` with `fieldKids` (breaking, unreleased API)
- **lcl-adapter-mongodb-core**: `MongoDocumentRewriteStore` CAS logic rewritten, `concurrencyField` removed
- **lcl-spring-boot-starter**: `DekReEncryptionService` extracts kid during scan phase
- **Tests**: Update CAS conflict tests to use `_k` semantics
- **Documentation**: Update `docs/key-lifecycle.md` CAS description
