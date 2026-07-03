## Context

LightCrypto-Link uses envelope encryption: each entity class has a DEK and HMAC key stored (wrapped) in a MongoDB `__lcl_keyvault` collection. The current v1 implementation (`LocalSymmetricCmkProvider`) wraps/unwraps these keys using a 32-byte AES-256 symmetric CMK that must reside in application memory — configured as a hex string in `lcl.crypto.cmk`.

The `CmkProvider` SPI already defines `wrap(byte[])` and `unwrap(WrappedKey)` as separate methods, and `WrappedKey.algorithm` already supports self-describing multi-algorithm unwrap. The interface requires **no changes** for asymmetric support.

The Alibaba KMS SDK (`alibabacloud-kms20160120:1.0.12`) is already referenced (commented out) in the alibaba-kms module `pom.xml`. Bouncy Castle is already available via the spring-boot-starter module.

## Goals / Non-Goals

**Goals:**
- Implement `AlibabaKmsCmkProvider` with local wrap (public key) + remote unwrap (KMS Decrypt API)
- Support RSA-OAEP-SHA256 and SM2 algorithms
- Support two public key sourcing strategies: from config PEM or fetched from KMS GetPublicKey API
- Add integration tests gated by environment variables (skip in CI when credentials absent)
- Keep all changes confined to the `lightcrypto-link-alibaba-kms` module

**Non-Goals:**
- Azure Key Vault implementation (separate future change)
- Symmetric KMS wrap/unwrap mode (only asymmetric is supported)
- Key rotation logic changes
- Any changes to the core `spring-boot-starter` module

## Decisions

### Decision 1: Local wrap with cached public key (not remote KMS Encrypt API)

**Choice**: `wrap()` uses a locally cached `java.security.PublicKey`; `unwrap()` calls KMS Decrypt API.

**Alternatives considered**:
- *Both wrap and unwrap remote*: Simpler, no public key management needed. But introduces network dependency for every wrap operation (DEK generation, key rotation), adding latency and reducing availability.
- *Hybrid with config toggle*: Support both modes via a `localWrap: true/false` flag. Adds complexity for an edge case with no clear demand.

**Rationale**: The asymmetric model is the whole point of this change — private key never leaves HSM, and the public key is safe to hold in application memory. Local wrap gives zero-latency DEK generation and works even during brief KMS outages (only unwrap on startup requires KMS connectivity).

### Decision 2: SM2 implementation via Bouncy Castle Cipher (not low-level SM2Engine)

**Choice**: Use `Cipher.getInstance("SM2", "BC")` for SM2 wrap.

**Alternatives considered**:
- *Use BC `SM2Engine` directly*: Gives full control over C1/C2/C3 ordering. More code to maintain.
- *Wait for JDK native SM2*: JDK has no SM2 support; would require a third-party provider regardless.

**Rationale**: BC 1.70+ SM2 Cipher outputs C1C3C2 format, which is confirmed compatible with Alibaba KMS SM2PKE. Using the standard `Cipher` API is simpler and more idiomatic than low-level `SM2Engine`. If a format mismatch were discovered later, a thin adapter layer could be added without changing the public API.

### Decision 3: RSA algorithm identifier `RSAES-OAEP-SHA256` (not `RSA-OAEP-256`)

**Choice**: Use `RSAES-OAEP-SHA256` as the `WrappedKey.algorithm` string for RSA.

**Alternatives considered**:
- *Use `RSA-OAEP-256`*: Matches Azure Key Vault naming. Would create false consistency across providers.
- *Use `RSAES_OAEP_SHA_256`*: Matches Alibaba KMS API parameter exactly. Contains underscores which are unusual in algorithm identifiers.

**Rationale**: Alibaba KMS uses `RSAES_OAEP_SHA_256` as the `encryptionAlgorithm` parameter value. Using the hyphenated variant `RSAES-OAEP-SHA256` in `WrappedKey.algorithm` keeps the self-describing identifier readable while mapping cleanly to the API value via a simple `replace("-", "_")`. Azure Key Vault will use its own identifier (`RSA-OAEP-256`) when implemented — the algorithm field is per-provider.

### Decision 4: Public key as optional PEM in configuration

**Choice**: `lcl.crypto.alibaba.public-key` accepts a PEM string (multi-line via YAML `|` literal). If absent, the provider calls KMS `GetPublicKey` API at construction time.

**Alternatives considered**:
- *Always fetch from KMS*: Eliminates config burden but requires network at every startup, even when the key never changes.
- *Require PEM in config*: No fallback; breaks if config is incomplete.

**Rationale**: The two-tier approach supports both production (pre-provisioned PEM for zero-network startup, e.g. injected from a secret manager) and development (auto-fetched from KMS for convenience).

### Decision 5: SM2 public key parsed from X.509 SubjectPublicKeyInfo PEM

**Choice**: Parse both RSA and SM2 public keys from standard X.509 `SubjectPublicKeyInfo` PEM format using `KeyFactory.getInstance("EC")` with `X509EncodedKeySpec` for SM2, and `KeyFactory.getInstance("RSA")` for RSA.

**Rationale**: Alibaba KMS `GetPublicKey` API returns standard X.509 SubjectPublicKeyInfo PEM for both RSA and SM2 keys. This is the same format used by `openssl` and standard Java key tooling.

## Risks / Trade-offs

- **[SM2 Cipher/KMS format mismatch]** → Mitigated: C1C3C2 confirmed compatible. If a mismatch surfaces during integration testing, add a C1/C2/C3 reordering adapter in the wrap path. This is a local-only change with no SPI impact.
- **[KMS SDK version incompatibility]** → `alibabacloud-kms20160120:1.0.12` is the confirmed available version. The SDK pulls in `aliyun-java-sdk-core` transitively. Test compilation before implementation.
- **[Network dependency for unwrap on startup]** → `verifyAndLoadKeys()` calls `unwrap()` for every vault entry during application startup. If KMS is unavailable, the application fails to start. This is acceptable: KMS connectivity is a prerequisite for the asymmetric model. Document in error messages.
- **[SM2 performance]** → SM2 encryption is slower than RSA for small payloads (due to EC point multiplication). For 32-byte DEK wrapping, the difference is negligible (< 5ms). Not a concern.
- **[CI cannot run integration tests]** → Tests are gated by `@EnabledIfEnvironmentVariable`. CI always skips them. Local developer testing is the primary validation path for KMS integration.
