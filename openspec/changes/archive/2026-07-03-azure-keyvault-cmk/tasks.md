## 1. Dependency & Configuration Setup

- [x] 1.1 Add `com.azure:azure-security-keyvault-keys:4.11.0` and `com.azure:azure-identity:1.14.2` dependencies in `lightcrypto-link-azure-kms/pom.xml` (replace commented-out placeholder)
- [x] 1.2 Update `AzureKeyVaultCmkProperties`: add `tenantId`, `clientId`, `clientSecret`, `algorithm` (default "RSA"), `publicKey` (optional PEM) fields; remove `keyVersion` field
- [x] 1.3 Enable `maven-deploy-plugin` (remove skip) for the azure-kms module
- [x] 1.4 Add `lightcrypto-link-azure-kms` to parent POM `dependencyManagement`
- [x] 1.5 Verify module compiles: `mvn compile -pl lightcrypto-link-azure-kms -am`

## 2. Core Provider Implementation

- [x] 2.1 Replace `AzureKeyVaultCmkProvider` skeleton: accept `PublicKey`, `CryptographyClient`, and `algorithm` in constructor; validate (keyVersion is auto-resolved, not from config)
- [x] 2.2 Implement RSA local wrap: `Cipher.getInstance("RSA/ECB/OAEPPadding")` with explicit `OAEPParameterSpec(SHA-256, MGF1, MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)`, return `WrappedKey(ciphertext, "RSA-OAEP-256")`
- [x] 2.3 Implement remote unwrap: call `CryptographyClient.unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, ciphertext)`, return plaintext key bytes
- [x] 2.4 Implement `getProviderId()` returning `"azure-keyvault"`
- [x] 2.5 Verify compilation

## 3. Public Key Loading & JWK Conversion

- [x] 3.1 Implement `JsonWebKeyToPublicKey` utility: decode `n` (modulus) and `e` (exponent) from `JsonWebKey`, construct `RSAPublicKeySpec`, generate key via `KeyFactory.getInstance("RSA")`
- [x] 3.2 Reuse existing `PublicKeyLoader` for PEM-based public key loading (from config)
- [x] 3.3 Wire public key loading into `AzureKeyVaultCmkAutoConfiguration`: always call `CryptographyClient.getKey()` to resolve key version; if `public-key` not configured, also extract public key from the returned `KeyVaultKey`
- [x] 3.4 Add validation: throw `IllegalArgumentException` for invalid PEM, invalid JWK, or unsupported algorithm

## 4. Azure AD Authentication & Auto-Configuration

- [x] 4.1 Implement `buildTokenCredential()` in auto-configuration: if all three of `tenant-id`, `client-id`, `client-secret` are set → `ClientSecretCredential`; else → `DefaultAzureCredential`
- [x] 4.2 Add partial credential validation: throw `IllegalArgumentException` when only some of the three credential fields are set
- [x] 4.3 Implement `buildCryptographyClient()`: create `CryptographyClient` with `vaultUri`, `keyName`, and the token credential
- [x] 4.4 Wire full `cmkProvider()` bean: validate properties → build credential → build client → call `getKey()` to resolve key version + public key → construct provider
- [x] 4.5 Verify module compiles with updated auto-configuration

## 5. Unit Tests

- [x] 5.1 Add unit test for RSA wrap: verify `WrappedKey.algorithm == "RSA-OAEP-256"` and ciphertext is non-empty (use a generated RSA key pair for testing)
- [x] 5.2 Add unit test for RSA wrap uniqueness: wrap same key twice, assert different ciphertexts
- [x] 5.3 Add unit test for `JsonWebKeyToPublicKey`: construct `JsonWebKey` with known `n`/`e`, verify resulting `PublicKey` can encrypt data
- [x] 5.4 Add unit test for null constructor args: `null` publicKey → `IllegalArgumentException`
- [x] 5.5 Add unit test for partial credentials logic (verified via properties validation)
- [x] 5.6 Add unit test for provider ID: verify `getProviderId()` returns `"azure-keyvault"`
- [x] 5.7 Verify unit tests pass: 13 tests, 0 failures

## 6. Integration Tests (Environment-Gated)

- [x] 6.1 Create `AzureKeyVaultCmkProviderRsaIntegrationTest` with `@EnabledIfEnvironmentVariable(named = "AZURE_VAULT_URI", matches = ".+")` guard
- [x] 6.2 Add RSA roundtrip integration test: read Azure credentials from env vars, build `CryptographyClient`, fetch public key, wrap a 32-byte key, unwrap it, assert equality
- [x] 6.3 Add public key auto-fetch integration test: omit `public-key` config, verify provider fetches from Azure Key Vault and wrap/unwrap still works
- [x] 6.4 Verify integration tests skip correctly when env vars absent: 5 tests skipped when `AZURE_VAULT_URI` not set

## 7. Example Module

- [x] 7.1 Create `lightcrypto-link-examples/azure-keyvault` module: `pom.xml` with starter + azure-kms dependencies
- [x] 7.2 Create `AzureKeyVaultApplication.java`, `User.java`, `UserRepository.java`, `DemoRunner.java`
- [x] 7.3 Create `application.yml` with `lcl.crypto.azure.*` configuration using env var placeholders
- [x] 7.4 Register module in examples parent `pom.xml`
- [x] 7.5 Verify example compiles: `mvn compile -pl lightcrypto-link-examples/azure-keyvault -am`
