## Why

The existing `lightcrypto-link-azure-kms` module contains only a skeleton with `UnsupportedOperationException` stubs. Following the pattern established by the Alibaba Cloud KMS provider (local public key wrap + remote unwrap), this change implements the Azure Key Vault CMK provider using the Azure Key Vault Java SDK (`CryptographyClient`) for real key wrap/unwrap operations. The private key never leaves the Azure HSM — the application holds only the public key for local wrap, while unwrap requires an authenticated Azure API call.

## What Changes

- Replace `AzureKeyVaultCmkProvider` skeleton with a real implementation using `CryptographyClient`
- Support RSA-OAEP-256 algorithm: local wrap with cached public key (Java `Cipher` + `OAEPParameterSpec`), remote unwrap via `CryptographyClient.unwrapKey()`
- Add `com.azure:azure-security-keyvault-keys` and `com.azure:azure-identity` dependencies
- Update `AzureKeyVaultCmkProperties` with Azure AD authentication fields (`tenant-id`, `client-id`, `client-secret`) and `algorithm` configuration
- Update `AzureKeyVaultCmkAutoConfiguration` to build `CryptographyClient`, fetch public key, and construct the provider
- Add unit tests and integration tests gated by environment variables
- Add Azure Key Vault example in `lightcrypto-link-examples`

## Capabilities

### New Capabilities
- `azure-keyvault-cmk`: Azure Key Vault asymmetric CMK provider — local wrap with RSA-OAEP-256 public key, remote unwrap via Azure `CryptographyClient`, with public key fetching from Key Vault and Azure AD authentication support.

### Modified Capabilities
*(None — the `CmkProvider` SPI and `WrappedKey` record are unchanged; they already support multi-provider via `algorithm` field and `@ConditionalOnMissingBean`.)*

## Impact

- **Code**: Only `lightcrypto-link-azure-kms` module is affected; core `spring-boot-starter` module has zero changes.
- **Dependencies**: `com.azure:azure-security-keyvault-keys:4.11.0` and `com.azure:azure-identity:1.14.2` become active dependencies.
- **Configuration**: New properties under `lcl.crypto.azure`: `vault-uri`, `key-name`, `public-key` (optional PEM), `tenant-id`, `client-id`, `client-secret` (optional, all-or-nothing), `algorithm` (default `RSA`). Key version is auto-resolved via `getKey()`.
- **Security**: Azure AD credentials must be injected via environment variables; sensitive data is never committed.
- **CI**: Integration tests skip when Azure credentials are absent (no impact on existing CI pipeline).
