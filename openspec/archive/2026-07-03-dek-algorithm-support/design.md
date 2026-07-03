## Context

Currently, `CryptoCodec` hardcodes AES-256-GCM for all encryption/decryption. The `@Encrypted(algorithm = ...)` annotation exists but only supports `AES_256_GCM`. The sub-document format (`_e`, `_t`, `_k`) doesn't include algorithm information.

Key constraints:
- AES-256 uses 32-byte keys; SM4 uses 16-byte keys
- GCM modes need IV (12 bytes for AES, 12 bytes for SM4)
- CBC modes need IV (16 bytes) and PKCS5 padding
- Bouncy Castle provides SM4 cipher support out of the box

## Goals / Non-Goals

**Goals:**
- Support 4 algorithms: AES-256-GCM, AES-256-CBC, SM4-GCM, SM4-CBC
- Per-field algorithm selection via `@Encrypted(algorithm = ...)`
- Backward compatible with existing encrypted data (AES-256-GCM without `_a` tag)
- Algorithm tag stored in sub-document for correct decryption

**Non-Goals:**
- Per-algorithm DEK generation (keep single DEK per entity class for now)
- Algorithm rotation/re-encryption (that's a separate change)
- Authenticated encryption for CBC modes (no HMAC appended; rely on application-level integrity)

## Decisions

### Decision 1: Strategy Pattern for Algorithm Dispatch

**Choice**: Introduce `SymmetricEncryptor` interface with concrete implementations per algorithm.

```java
interface SymmetricEncryptor {
    byte[] encrypt(byte[] key, byte[] plaintext);
    byte[] decrypt(byte[] key, byte[] data);
    String computeKcv(byte[] key);
}
```

`CryptoCodec` holds a `Map<SymmetricAlgorithm, SymmetricEncryptor>` and dispatches based on the algorithm parameter.

**Rationale**: Clean separation of concerns, easy to add new algorithms, testable in isolation.

**Alternatives considered**:
- Single class with switch statements — harder to maintain and test
- External crypto library abstraction — over-engineered for 4 algorithms

### Decision 2: SM4 Key Derivation from 32-byte DEK

**Choice**: For SM4 (128-bit key), use first 16 bytes of the 32-byte DEK.

```java
byte[] sm4Key = Arrays.copyOf(dek, 16); // first 16 bytes
```

**Rationale**: Simple, deterministic, and sufficient for this use case. The full 32-byte DEK provides entropy; we just use a subset for SM4.

**Alternatives considered**:
- HKDF derivation — more cryptographically sound but adds complexity
- Per-algorithm DEK in vault — significant architectural change, not needed for v1
- Hash-based derivation — unnecessary complexity

### Decision 3: Algorithm Tag in Sub-document

**Choice**: Add `_a` field to the encrypted sub-document.

```json
{
  "c": <Binary>,      // ciphertext
  "_e": 1,            // encryption marker
  "_t": "str",        // type marker
  "_k": "v1-abc123",  // key version ID
  "_a": "SM4_GCM"     // algorithm tag (NEW)
}
```

For backward compatibility, documents without `_a` are treated as `AES_256_GCM`.

**Rationale**: Minimal change to existing format, self-documenting, enables correct decryption.

### Decision 4: IV Length Per Algorithm

| Algorithm | IV Length | Block Size | Notes |
|-----------|-----------|------------|-------|
| AES-256-GCM | 12 bytes | - | Standard GCM nonce |
| AES-256-CBC | 16 bytes | 16 bytes | AES block size |
| SM4-GCM | 12 bytes | - | Same as AES-GCM |
| SM4-CBC | 16 bytes | 16 bytes | SM4 block size |

**Rationale**: Follow standard IV lengths for each mode. GCM modes use 12-byte nonces; CBC modes use block-size IVs.

### Decision 5: KCV Computation Per Algorithm

**Choice**: KCV uses the same algorithm as the field's encryption. Each `SymmetricEncryptor` implements `computeKcv(byte[] key)` using its algorithm.

**Rationale**: KCV verifies key integrity for the specific algorithm used. Using a different algorithm for KCV would not detect algorithm-specific key corruption.

## Risks / Trade-offs

**Risk**: Mixed algorithms within one entity class could lead to confusion.
→ **Mitigation**: Document recommended practice (use one algorithm per entity). The per-field flexibility is for gradual migration, not mixing.

**Risk**: SM4 key derived from first 16 bytes reduces entropy.
→ **Mitigation**: Acceptable trade-off. The DEK is 32 random bytes; using 16 still provides 128 bits of entropy which is sufficient for SM4-128.

**Risk**: No authenticated encryption for CBC modes.
→ **Mitigation**: Document that GCM modes are preferred. CBC is for legacy compatibility. Application-level integrity checks (MongoDB, application logic) provide additional safety.

## Migration Plan

1. Deploy new code with multi-algorithm support
2. Existing documents (no `_a` tag) continue to work via default AES-256-GCM handling
3. New documents include `_a` tag based on `@Encrypted(algorithm = ...)`
4. No data migration required for existing deployments
