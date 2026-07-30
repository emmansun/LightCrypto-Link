## Context

DEK re-encryption scans all documents of an entity class, re-encrypts fields with outdated DEK versions, and writes back atomically. The current CAS strategy uses a document-level `updatedAt` timestamp:

```java
// Current: MongoDocumentRewriteStore.replace()
Document filter = new Document("_id", rawId);
if (document.concurrencyToken() != null) {
    filter.append(concurrencyField, document.concurrencyToken());  // "updatedAt"
}
```

Problems:
- Entities without `updatedAt` have no CAS protection
- Any unrelated field change triggers CAS failure

The Node.js SDK uses per-field `_k` (kid) matching:
```javascript
{ _id: doc.id, 'phone._k': 'v1-old-kid' }
```

## Goals / Non-Goals

**Goals:**
- Replace document-level CAS with per-field `_k` matching
- Zero business schema dependency (no `updatedAt` requirement)
- Field-level precision: only skip if the target encrypted field was concurrently modified
- Backward compatible with legacy blobs (no `_k` field)

**Non-Goals:**
- Changing the Wire Format or adding kid to existing blobs
- Multi-field atomic CAS across different encrypted fields (each field is independent)
- Supporting non-MongoDB storage adapters (SPI change is generic, but only MongoDB impl updated)

## Decisions

### D1: RawDocument carries per-field kid snapshot

Extend `RawDocument` record with a `fieldKids` map, replacing `concurrencyToken`:

```java
public record RawDocument(
    String id,
    Map<String, Object> fields,
    Map<String, String> fieldKids      // field path → kid snapshot
) {
}
```

**Rationale**: The scan phase already reads the full document. Extracting `_k` from encrypted sub-documents is trivial and adds no extra I/O. Since DEK re-encryption is unreleased, we can make a clean break without backward compatibility concerns.

**Alternative considered**: Store kid inside `fields` map with a reserved key prefix. Rejected — pollutes business data and complicates serialization.

### D2: MongoDB filter uses dot-notation `_k` conditions

```java
// New: MongoDocumentRewriteStore.replace()
Document filter = new Document("_id", rawId);
for (Map.Entry<String, String> entry : doc.fieldKids().entrySet()) {
    filter.append(entry.getKey() + "._k", entry.getValue());
}
// If fieldKids is empty (no encrypted fields or legacy blob), use _id-only
```

**Rationale**: MongoDB dot-notation naturally matches nested sub-document fields. If the application re-encrypted `phone` with a new kid, the filter `'phone._k': 'old-kid'` won't match → skip.

### D3: DekReEncryptionService extracts kid during scan

The scan phase iterates raw documents. For each encrypted field (identified by `CryptoMetadataCache`), extract the `_k` value:

```java
// In DekReEncryptionService.scanAndCollect()
Map<String, String> fieldKids = new HashMap<>();
for (EncryptedFieldMeta meta : encryptedFields) {
    Object subDoc = rawFields.get(meta.fieldName());
    if (subDoc instanceof Document doc && doc.containsKey("_k")) {
        fieldKids.put(meta.fieldName(), doc.getString("_k"));
    }
}
return new RawDocument(id, rawFields, concurrencyToken, fieldKids);
```

**Rationale**: The engine already knows which fields are encrypted via metadata cache. No schema introspection needed.

### D4: Remove `concurrencyToken` and `concurrencyField`

Since DEK re-encryption is unreleased, we can make a clean break:
- Remove `concurrencyToken` field from `RawDocument` record
- Remove `concurrencyField` constructor parameter from `MongoDocumentRewriteStore`
- Remove `DEFAULT_CONCURRENCY_FIELD` constant

This simplifies the SPI and implementation without migration concerns.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Multiple encrypted fields → multiple filter conditions | MongoDB handles compound filters efficiently; indexed `_id` is primary selector |
| `_k` field name collision with business data | `_k` is reserved by LCL wire format; documented as internal field |
| Bulk write with per-field filters slightly more complex | Each `ReplaceOneModel` already builds individual filters; minimal code change |
| Documents with no `_k` (corrupted/manual edit) | `fieldKids` empty → `_id`-only filter (no CAS, same as worst case before) |
