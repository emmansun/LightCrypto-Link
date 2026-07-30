# Key Lifecycle Guide

This guide explains the three key operations in Light Crypto Link's key lifecycle: **CMK re-wrap**, **DEK rotation**, and **DEK re-encryption**. Understanding when to use each operation is essential for maintaining compliance with security frameworks like PCI-DSS, HIPAA, and SOC2.

## Overview

Light Crypto Link uses envelope encryption with a three-layer key hierarchy:

```
CMK (Customer Master Key)
 └── DEK (Data Encryption Key) + HMAC Key
      └── Encrypted Business Data
```

Each layer has its own rotation/lifecycle operation:

| Operation | Scope | Cost | Downtime | Frequency |
|-----------|-------|------|----------|-----------|
| CMK Re-wrap | Vault only | Seconds | None | On KMS migration |
| DEK Rotation | Vault only | Instant | None | Periodic (e.g., quarterly) |
| DEK Re-encryption | All documents | Hours | None | After rotation, before key destruction |

## CMK Re-wrap

**What it does:** Changes the wrapping layer without touching business data. All DEK/HMAC keys are unwrapped with the old CMK and re-wrapped with a new CMK.

**When to use:**
- Migrating between KMS providers (e.g., AWS KMS → Azure Key Vault)
- Rotating the CMK in your KMS
- Changing from local symmetric CMK to cloud KMS

**API:**
```java
CmkProvider targetProvider = ...; // new CMK provider
List<RewrapResult> results = keyVaultService.rewrapAllVaults(targetProvider);
```

**Characteristics:**
- Fast: O(number of namespaces), typically seconds
- No business data modification
- Old CMK can be destroyed immediately after completion

## DEK Rotation

**What it does:** Generates a new DEK/HMAC key pair for future writes. Existing documents remain encrypted under the old DEK.

**When to use:**
- Periodic key rotation (compliance requirement)
- Suspected key compromise (rotate immediately)
- Before planned re-encryption

**API:**
```java
keyVaultService.rotateDek("default.default.User#email");
```

**Characteristics:**
- Instant: Only vault document is modified
- New writes use the new DEK
- Old DEK remains available for decryption
- Old key status: `ACTIVE` → `ROTATED`

## DEK Re-encryption

**What it does:** Re-encrypts all existing business data under the active DEK. Recomputes blind index values with the active HMAC key.

**When to use:**
- After DEK rotation, to migrate existing data
- Before destroying old key material (compliance requirement)
- To enable safe deletion of ROTATED keys

**API:**
```java
DekReEncryptionService reEncryptionService = ...;

// Re-encrypt a specific entity class
ReEncryptResult result = reEncryptionService.reEncrypt(
    User.class,
    ReEncryptOptions.forEntity(User.class)
        .withBatchSize(1000)
        .withTaskId("quarterly-rotation-2024-Q1")
);

// Check result
if (result.success()) {
    System.out.println("Processed: " + result.docsProcessed());
    System.out.println("Skipped: " + result.docsSkipped());
}
```

**Characteristics:**
- Heavy: O(all documents), may run for hours
- No downtime required (per-field kid-based CAS concurrency protection)
- CAS strategy: each encrypted field's `_k` (kid) sub-document value is used as the
  compare-and-swap condition. If a field is concurrently re-encrypted by the application
  (kid changes between scan and write-back), the replace is skipped — not the entire document.
  Documents without `_k` (legacy blobs) fall back to `_id`-only filter.
- Checkpoint-based resumability
- Old key status: `ROTATED` → `RETIRED` (automatic on completion)

## Key Status Lifecycle

```
ACTIVE → ROTATED → RETIRED → (deleted)
   │         │          │
   │         │          └── Safe to delete (pruneRetiredKeys)
   │         └── Still needed for decryption
   └── Current encryption key
```

- **ACTIVE**: Currently used for encryption. One per namespace.
- **ROTATED**: Still needed for decryption of old data. Cannot be deleted.
- **RETIRED**: All data migrated. Safe to delete via `pruneRetiredKeys()`.

## Recommended Workflow

### Quarterly Key Rotation

1. **Rotate DEK** for all namespaces:
   ```java
   for (String namespace : allNamespaces) {
       keyVaultService.rotateDek(namespace);
   }
   ```

2. **Re-encrypt** during maintenance window:
   ```java
   ReEncryptOptions options = ReEncryptOptions.forAll()
       .withBatchSize(500)
       .withTaskId("q1-2024-rotation");
   
   for (Class<?> entityClass : registeredEntities) {
       reEncryptionService.reEncrypt(entityClass, options);
   }
   ```

3. **Verify** completion (all keys at RETIRED status)

4. **Prune** retired keys (optional, after verification):
   ```java
   for (String namespace : allNamespaces) {
       keyVaultService.pruneRetiredKeys(namespace);
   }
   ```

### KMS Migration

1. **Configure** new CMK provider
2. **Re-wrap** all vaults:
   ```java
   keyVaultService.rewrapAllVaults(newCmkProvider);
   ```
3. **Verify** re-wrap results
4. **Decommission** old KMS (after verification period)

## Monitoring

Re-encryption emits events for observability:

- `lcl.reencrypt.batch.completed` — per batch progress
- `lcl.reencrypt.namespace.completed` — full completion

Monitor these metrics:
- `docsProcessed` / `docsSkipped` / `docsFailed`
- `durationMicros`
- High skip rate may indicate heavy concurrent write load

## Troubleshooting

### High Skip Rate

If `docsSkipped` is high relative to `docsProcessed`:
- Application is updating documents faster than re-encryption
- Run during low-traffic windows
- Multiple runs will converge (idempotent)

### Checkpoint Resume

If re-encryption is interrupted:
```java
// Resume with same taskId
ReEncryptOptions options = ReEncryptOptions.forEntity(User.class)
    .withTaskId("same-task-id-as-before");
reEncryptionService.reEncrypt(User.class, options);
```

### RETIRED Key Access Error

If you see "Key has been RETIRED" errors:
- Some documents were not re-encrypted before key retirement
- Re-run re-encryption for affected namespace
- Do NOT prune keys until re-encryption completes with zero failures
