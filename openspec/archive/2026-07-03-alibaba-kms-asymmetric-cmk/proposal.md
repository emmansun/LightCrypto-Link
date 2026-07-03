## Why

The current `LocalSymmetricCmkProvider` holds the CMK (32-byte symmetric key) directly in application memory. Even if the CMK is stored as a KMS secret, it still leaves the KMS boundary when loaded into the JVM — undermining the security guarantee of a Hardware Security Module (HSM). An asymmetric approach ensures the CMK private key never leaves the KMS HSM: the application holds only the public key for local wrap operations, while unwrap requires a KMS API call. This change implements that model for Alibaba Cloud KMS, supporting both RSA-OAEP (international standard) and SM2 (Chinese national standard / 国密).

## What Changes

- Implement `AlibabaKmsCmkProvider` with asymmetric wrap (local, using cached public key) and unwrap (remote, via KMS Decrypt API)
- Support two algorithms:
  - **RSA-OAEP-SHA256**: `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` (algorithm identifier `RSAES-OAEP-SHA256`)
  - **SM2**: Bouncy Castle SM2 Cipher, C1C3C2 ciphertext format (algorithm identifier `SM2PKE`)
- Add `algorithm` (RSA | SM2) and optional `public-key` (PEM) fields to `AlibabaKmsCmkProperties`
- Enable the `alibabacloud-kms20160120` SDK dependency in the alibaba-kms module `pom.xml`
- Update `AlibabaKmsCmkAutoConfiguration` to wire new properties into the provider
- Add `application-local.properties` to `.gitignore` for safe local testing with real KMS credentials
- Add integration tests gated by environment variables (skipped in CI when credentials are absent)

## Capabilities

### New Capabilities
- `alibaba-kms-asymmetric`: Alibaba Cloud KMS asymmetric CMK provider — local wrap with public key (RSA-OAEP or SM2), remote unwrap via KMS Decrypt API, with public key caching from configuration or KMS GetPublicKey API.

### Modified Capabilities
*(None — the `CmkProvider` SPI interface and `WrappedKey` record are unchanged; they already support multi-algorithm self-describing unwrap via the `algorithm` field.)*

## Impact

- **Code**: Only `lightcrypto-link-alibaba-kms` module is affected; core `spring-boot-starter` module has zero changes.
- **Dependencies**: `alibabacloud-kms20160120:1.0.12` becomes an active (non-commented) dependency in the alibaba-kms module. Bouncy Castle is already available via the starter.
- **Configuration**: New properties `lcl.crypto.alibaba.algorithm` (RSA | SM2, default RSA) and `lcl.crypto.alibaba.public-key` (optional PEM).
- **Security**: Sensitive credentials (AK/SK, Key ID, public key) must be injected via environment variables; `.gitignore` updated to exclude `application-local.properties`.
- **CI**: Alibaba KMS integration tests are skipped when environment variables are absent (no impact on existing CI pipeline).
