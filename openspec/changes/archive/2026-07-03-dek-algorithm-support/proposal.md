## Why

The current implementation only supports AES-256-GCM for DEK encryption. The `@Encrypted(algorithm = ...)` annotation already exists but the `SymmetricAlgorithm` enum only has `AES_256_GCM`. To support compliance requirements (国密 SM4 for China regulations) and broader compatibility (AES-256-CBC for legacy systems), the encryption engine needs to support multiple symmetric algorithms.

## What Changes

- **Extend `SymmetricAlgorithm` enum** — add `AES_256_CBC`, `SM4_GCM`, `SM4_CBC`
- **Refactor `CryptoCodec`** — replace hardcoded AES-256-GCM with algorithm-aware dispatch (strategy pattern per algorithm)
- **Store algorithm in sub-document** — add `_a` field to encrypted BSON sub-document to identify which algorithm was used for encryption
- **Algorithm-aware listeners** — `CryptoBeforeSaveListener` and `CryptoMappingMongoConverter` pass the per-field algorithm to `CryptoCodec`
- **KCV per-algorithm** — KCV computation must use the same algorithm as the field's encryption
- **DEK key derivation** — SM4 uses 128-bit keys; derive the appropriate key length from the DEK based on the algorithm

## Capabilities

### New Capabilities
- `dek-algorithm-dispatch`: Per-field symmetric algorithm selection, CryptoCodec strategy dispatch, sub-document algorithm tagging, and KCV computation per algorithm

### Modified Capabilities
- `envelope-encryption`: DEK encryption/decryption must support multiple algorithms (AES-256-GCM, AES-256-CBC, SM4-GCM, SM4-CBC)
- `encrypted-annotation`: Add new `SymmetricAlgorithm` enum values

## Impact

- **Core code**: `CryptoCodec`, `CryptoBeforeSaveListener`, `CryptoMappingMongoConverter`, `SymmetricAlgorithm`, `EntityMetadataCache`
- **Sub-document format**: New `_a` field (algorithm tag) in encrypted BSON documents — backward compatible (old documents without `_a` default to AES-256-GCM)
- **Dependencies**: Bouncy Castle already provides SM4 cipher support; no new dependencies needed
- **Key vault**: KCV computation changes per algorithm — existing vaults using AES-256-GCM remain valid
- **Tests**: Unit tests for each algorithm, round-trip tests, backward compatibility tests for existing encrypted data
