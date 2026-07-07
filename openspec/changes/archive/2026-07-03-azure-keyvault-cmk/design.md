## Context

The `lightcrypto-link-azure-kms` module already exists as a skeleton with placeholder classes (`AzureKeyVaultCmkProvider`, `AzureKeyVaultCmkAutoConfiguration`, `AzureKeyVaultCmkProperties`). All methods throw `UnsupportedOperationException`. The `CmkProvider` SPI, `WrappedKey` record, and auto-configuration infrastructure are already in place — this change only replaces the skeleton with a real implementation.

The Alibaba KMS provider (completed and archived) established the pattern: **local wrap with cached public key + remote unwrap via cloud API**. Azure Key Vault follows the same architecture using `CryptographyClient` from `com.azure:azure-security-keyvault-keys:4.11.0`.

Azure Key Vault uses `KeyWrapAlgorithm.RSA_OAEP_256` for key wrapping, which maps to the standard RSA-OAEP with SHA-256 + MGF1-SHA-256 (same as Alibaba KMS `RSAES_OAEP_SHA_256`).

## Goals / Non-Goals

**Goals:**
- Implement `AzureKeyVaultCmkProvider` with local wrap (public key) + remote unwrap (`CryptographyClient.unwrapKey()`)
- Support RSA-OAEP-256 algorithm (Azure's standard RSA wrap algorithm)
- Fetch public key from Azure Key Vault via `CryptographyClient.getKey()` at startup (or use configured PEM)
- Authenticate via Azure AD service principal (`ClientSecretCredential`) or `DefaultAzureCredential`
- Add unit tests and environment-gated integration tests
- Add Azure Key Vault example in `lightcrypto-link-examples`

**Non-Goals:**
- EC key support (Azure supports EC key wrap but requires different algorithm handling)
- Managed HSM support (different endpoint and authentication model)
- Symmetric key wrap mode (Azure supports it but asymmetric is the security goal)
- Any changes to the core `spring-boot-starter` module

## Decisions

### Decision 1: Local wrap with cached public key (same pattern as Alibaba KMS)

**Choice**: `wrap()` uses a locally cached `java.security.PublicKey`; `unwrap()` calls Azure Key Vault `CryptographyClient.unwrapKey()`.

**Alternatives considered**:
- *Both wrap and unwrap remote via `CryptographyClient.wrapKey()`*: Simpler but adds network dependency for every wrap operation.
- *Use `CryptographyClient.encrypt()`/`decrypt()` instead of wrap/unwrap*: Azure Key Vault separates "wrap/unwrap" (for key material) from "encrypt/decrypt" (for data). Wrap/unwrap is the correct semantic for DEK wrapping.

**Rationale**: Consistent with the Alibaba KMS pattern. Local wrap gives zero-latency DEK generation. The public key is safe to hold in memory — only unwrap requires Azure connectivity.

### Decision 2: Algorithm identifier `RSA-OAEP-256` (Azure naming convention)

**Choice**: Use `RSA-OAEP-256` as the `WrappedKey.algorithm` string. Map to `KeyWrapAlgorithm.RSA_OAEP_256` when calling Azure API.

**Alternatives considered**:
- *Use `RSAES-OAEP-SHA256`* (same as Alibaba): Would create ambiguity — the algorithm field should identify which provider wrapped the key.

**Rationale**: Azure Key Vault uses `RSA-OAEP-256` in its API (`KeyWrapAlgorithm` enum). Using Azure's own naming keeps the mapping clear. The `WrappedKey.algorithm` is self-describing per provider — different providers naturally use different identifiers.

### Decision 3: Java Cipher spec for local wrap (same OAEPParameterSpec as Alibaba KMS)

**Choice**: Use `Cipher.getInstance("RSA/ECB/OAEPPadding")` with explicit `OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)`.

**Rationale**: The Java default `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` uses MGF1 with SHA-1 (known pitfall from Alibaba KMS implementation). Azure Key Vault expects MGF1 with SHA-256, matching the RFC 3447 standard. Reusing the same `OAEPParameterSpec` pattern ensures compatibility.

### Decision 4: Azure AD authentication via ClientSecretCredential

**Choice**: Support two authentication strategies:
1. **Service principal**: `ClientSecretCredential` with `tenant-id`, `client-id`, `client-secret`
2. **DefaultAzureCredential**: fallback when no explicit credentials are configured (supports managed identity, Azure CLI, VS Code auth, etc.)

**Rationale**: Service principal is the most common production pattern. `DefaultAzureCredential` enables seamless local development and managed identity scenarios without code changes.

### Decision 5: Public key & key version fetched from CryptographyClient.getKey()

**Choice**: At startup, call `CryptographyClient.getKey()` which returns a `KeyVaultKey` containing both the `JsonWebKey` (public key parameters `n`, `e`) and the key version via `getProperties().getVersion()`. The key version is auto-resolved — no separate configuration needed.

**Alternatives considered**:
- *Use KeyClient.getKey() directly*: Requires a separate `KeyClient` instance. `CryptographyClient.getKey()` returns the same data.
- *Require key-version in config*: Adds configuration burden for a value that rarely changes and is always available from the API.
- *Always require PEM in config*: Eliminates the fetch but adds configuration burden.

**Rationale**: `CryptographyClient.getKey()` provides both the public key material and the version in a single API call. The key version is then used to construct the key identifier for unwrap operations. This mirrors the Alibaba KMS pattern where `keyVersionId` is auto-resolved via `ListKeyVersions`. The two-tier public key approach (config PEM or auto-fetch) supports both production (pre-provisioned PEM) and development (auto-fetched).

## Risks / Trade-offs

- **[Azure SDK transitive dependency bloat]** → `azure-security-keyvault-keys` pulls in `azure-core`, `azure-identity`, `netty`, etc. The dependency tree is large but scoped to the azure-kms module only. Not a concern for users who don't include this module.
- **[Network dependency for unwrap on startup]** → Same as Alibaba KMS: if Azure Key Vault is unreachable, application startup fails. This is inherent to the asymmetric model and acceptable.
- **[CI cannot run integration tests]** → Tests are gated by `@EnabledIfEnvironmentVariable`. CI always skips them. Local developer testing with real Azure credentials is the primary validation path.
- **[JsonWebKey to java.security.PublicKey conversion]** → Azure returns public key as JWK parameters (`n`, `e`), not PEM. Need to convert to `RSAPublicKeySpec`. Mitigation: well-documented conversion using `BigInteger` from base64url-encoded `n` and `e`.
- **[DefaultAzureCredential slow in non-Azure environments]** → When running outside Azure with no explicit credentials, `DefaultAzureCredential` tries multiple auth methods sequentially, which can be slow. Mitigation: log a warning recommending explicit `ClientSecretCredential` for production.
