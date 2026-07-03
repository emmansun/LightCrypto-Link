## 1. Phase 1 — RSA: Dependency & Configuration Setup

- [x] 1.1 Add `com.aliyun:kms20160120:1.2.3` dependency in `lightcrypto-link-alibaba-kms/pom.xml`
- [x] 1.2 Add `algorithm` (String, default "RSA"), `publicKey` (String, optional PEM), and `keyVersionId` fields to `AlibabaKmsCmkProperties`
- [x] 1.3 Add `application-local.properties` to root `.gitignore`
- [x] 1.4 Verify module compiles: `mvn compile -pl lightcrypto-link-alibaba-kms`

## 2. Phase 1 — RSA: Core Provider Implementation

- [x] 2.1 Implement `AlibabaKmsCmkProvider` constructor: accept `keyId`, `keyVersionId`, `PublicKey`, KMS client, and `algorithm`; validate algorithm
- [x] 2.2 Implement RSA local wrap: `Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")` with cached public key, return `WrappedKey(ciphertext, "RSAES-OAEP-SHA256")`
- [x] 2.3 Implement remote unwrap: call KMS `AsymmetricDecrypt` API with `keyId`, `keyVersionId`, `ciphertextBlob` (base64), and `algorithm` mapped from WrappedKey.algorithm
- [x] 2.4 Verify compilation of `AlibabaKmsCmkProvider`

## 3. Phase 1 — RSA: Public Key Loading

- [x] 3.1 Implement `PublicKeyLoader` utility: parse X.509 SubjectPublicKeyInfo PEM string into `java.security.PublicKey` (RSA via `KeyFactory.getInstance("RSA")`)
- [x] 3.2 Implement KMS `GetPublicKey` API call: fetch PEM from KMS when `public-key` property is not configured, then parse via `PublicKeyLoader`
- [x] 3.3 Wire public key loading into `AlibabaKmsCmkAutoConfiguration`: pass loaded `PublicKey` to provider constructor
- [x] 3.4 Add validation: throw `IllegalArgumentException` for invalid PEM or unsupported algorithm value

## 4. Phase 1 — RSA: Auto-Configuration Update

- [x] 4.1 Update `AlibabaKmsCmkAutoConfiguration.cmkProvider()` bean method: read all properties, load public key (from config or KMS), construct provider
- [x] 4.2 Add credential validation: throw `IllegalArgumentException` when `accessKeyId`, `accessKeySecret`, or `keyVersionId` is null/empty
- [x] 4.3 Verify module compiles with updated auto-configuration

## 5. Phase 1 — RSA: Unit Tests

- [x] 5.1 Add unit test for RSA wrap: verify `WrappedKey.algorithm == "RSAES-OAEP-SHA256"` and ciphertext is non-empty (use a generated RSA key pair for testing)
- [x] 5.2 Add unit test for RSA wrap uniqueness: wrap same key twice, assert different ciphertexts
- [x] 5.3 Add unit test for `PublicKeyLoader`: parse valid RSA PEM → RSA PublicKey, invalid PEM → `IllegalArgumentException`
- [x] 5.4 Add unit test for invalid algorithm: construct provider with algorithm `"DES"` → `IllegalArgumentException`
- [x] 5.5 Verify unit tests pass: `mvn test -pl lightcrypto-link-alibaba-kms`

## 6. Phase 1 — RSA: Integration Tests (Environment-Gated)

- [x] 6.1 Create `AlibabaKmsCmkProviderRsaIntegrationTest` with `@EnabledIfEnvironmentVariable(named = "ALIBABA_AK_ID", matches = ".+")` guard
- [x] 6.2 Add RSA roundtrip integration test: read AK/SK/KeyId/KeyVersionId/publicKey from env vars, construct real provider, wrap a 32-byte key, unwrap it, assert equality
- [x] 6.3 Add public key auto-fetch integration test: omit `public-key` config, verify provider fetches from KMS and wrap/unwrap still works
- [x] 6.4 Verify integration tests skip correctly when env vars absent: `mvn test -pl lightcrypto-link-alibaba-kms`

## 7. Phase 2 — SM2: Extend Provider with SM2 Support (Roadmap)

> **Status: Deferred — No SM2 key available for testing. To be implemented when SM2 KMS key is provisioned.**

- [~] 7.1 Extend `AlibabaKmsCmkProvider` to accept algorithm "SM2"; add SM2 local wrap branch: `Cipher.getInstance("SM2", "BC")` with cached public key, return `WrappedKey(ciphertext, "SM2PKE")`
- [~] 7.2 Extend remote unwrap: map `SM2PKE` algorithm → KMS `encryptionAlgorithm = "SM2PKE"`
- [~] 7.3 Extend `PublicKeyLoader`: parse SM2/EC public key PEM via `KeyFactory.getInstance("EC")` (already supported in current code, just needs verification)
- [~] 7.4 Verify compilation

## 8. Phase 2 — SM2: Tests (Roadmap)

> **Status: Deferred — Depends on Phase 2 implementation and SM2 test key availability.**

- [~] 8.1 Add unit test for SM2 wrap: verify `WrappedKey.algorithm == "SM2PKE"` and ciphertext is non-empty (use a generated EC key pair with SM2 curve `sm2p256v1`)
- [~] 8.2 Add unit test for SM2 wrap uniqueness: wrap same key twice, assert different ciphertexts
- [~] 8.3 Add unit test for `PublicKeyLoader`: parse valid EC PEM → EC PublicKey
- [~] 8.4 Create `AlibabaKmsCmkProviderSm2IntegrationTest` with env var guard; add SM2 roundtrip integration test
- [~] 8.5 Verify all tests pass (or skip correctly when env vars absent): `mvn test -pl lightcrypto-link-alibaba-kms`
