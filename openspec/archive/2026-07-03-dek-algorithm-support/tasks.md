## 1. SymmetricAlgorithm Enum Extension

- [x] 1.1 Add `AES_256_CBC`, `SM4_GCM`, `SM4_CBC` to `SymmetricAlgorithm` enum
- [x] 1.2 Add unit test verifying all 4 enum values exist

## 2. SymmetricEncryptor Strategy Pattern

- [x] 2.1 Create `SymmetricEncryptor` interface with `encrypt`, `decrypt`, `computeKcv` methods
- [x] 2.2 Implement `AesGcmEncryptor` (12-byte IV, AES/GCM/NoPadding)
- [x] 2.3 Implement `AesCbcEncryptor` (16-byte IV, AES/CBC/PKCS5Padding)
- [x] 2.4 Implement `Sm4GcmEncryptor` (16-byte key from first 16 bytes of DEK, 12-byte IV, SM4/GCM/NoPadding via BC)
- [x] 2.5 Implement `Sm4CbcEncryptor` (16-byte key from first 16 bytes of DEK, 16-byte IV, SM4/CBC/PKCS5Padding via BC)
- [x] 2.6 Unit test: encrypt/decrypt roundtrip for each encryptor
- [x] 2.7 Unit test: KCV consistency and cross-algorithm difference

## 3. CryptoCodec Refactoring

- [x] 3.1 Add `Map<SymmetricAlgorithm, SymmetricEncryptor>` field and constructor initialization
- [x] 3.2 Add `encrypt(byte[] dek, byte[] plaintext, SymmetricAlgorithm algorithm)` method
- [x] 3.3 Add `decrypt(byte[] dek, byte[] data, SymmetricAlgorithm algorithm)` method
- [x] 3.4 Add `computeKcv(byte[] key, SymmetricAlgorithm algorithm)` method
- [x] 3.5 Deprecate or redirect old `encrypt(byte[], byte[])` and `decrypt(byte[], byte[])` to default AES-256-GCM
- [x] 3.6 Unit test: CryptoCodec dispatches to correct encryptor per algorithm
- [x] 3.7 Unit test: algorithm mismatch throws CryptoException

## 4. Sub-document Algorithm Tag

- [x] 4.1 Add `_a` field (algorithm name) to encrypted sub-document in `CryptoBeforeSaveListener`
- [x] 4.2 Read `_a` field in `CryptoMappingMongoConverter`; default to `AES_256_GCM` if absent
- [x] 4.3 Pass `meta.algorithm()` from listener to `CryptoCodec.encrypt()`
- [x] 4.4 Pass parsed algorithm from converter to `CryptoCodec.decrypt()`
- [x] 4.5 Unit test: sub-document contains `_a` field with correct algorithm name
- [x] 4.6 Unit test: legacy document without `_a` decrypts with AES-256-GCM

## 5. Key Vault KCV Per Algorithm

- [x] 5.1 Update `KeyVaultService.createKeyEntry()` to accept algorithm parameter or use a default (AES-256-GCM)
- [x] 5.2 Update KCV computation in vault initialization to use the appropriate algorithm
- [x] 5.3 Unit test: KCV stored in vault matches algorithm-specific computation

> **Note**: Vault KCV uses default AES-256-GCM for backward compatibility. Field-level encryption uses per-field algorithm via `_a` tag.

## 6. Integration Tests

- [x] 6.1 Integration test: save entity with `@Encrypted(algorithm = AES_256_GCM)`, read back, verify
- [x] 6.2 Integration test: save entity with `@Encrypted(algorithm = AES_256_CBC)`, read back, verify
- [x] 6.3 Integration test: save entity with `@Encrypted(algorithm = SM4_GCM)`, read back, verify
- [x] 6.4 Integration test: save entity with `@Encrypted(algorithm = SM4_CBC)`, read back, verify
- [x] 6.5 Integration test: mixed algorithms in same entity, all fields decrypt correctly
- [x] 6.6 Integration test: backward compatibility — read existing AES-256-GCM document without `_a` tag

## 7. Documentation

- [x] 7.1 Update README.md Supported Types table to include algorithm column
- [x] 7.2 Add algorithm configuration examples to README.md Quick Start
