## ADDED Requirements

### Requirement: AzureKeyVaultCmkProvider implements CmkProvider
The system SHALL provide `AzureKeyVaultCmkProvider` implementing the `CmkProvider` SPI with:
- `getProviderId()` returning `"azure-keyvault"`
- `wrap(byte[])` performing **local** asymmetric encryption using the cached RSA public key
- `unwrap(WrappedKey)` performing **remote** unwrap via Azure Key Vault `CryptographyClient.unwrapKey()`

#### Scenario: Wrap and unwrap roundtrip (RSA-OAEP-256)
- **WHEN** a 32-byte key is wrapped with algorithm `RSA-OAEP-256` and unwrapped via Azure Key Vault `CryptographyClient.unwrapKey()`
- **THEN** the unwrapped key SHALL be identical to the original

#### Scenario: Wrap produces unique output
- **WHEN** the same key material is wrapped twice
- **THEN** the two wrapped results SHALL differ (due to randomness in OAEP padding)

### Requirement: RSA-OAEP-256 local wrap
When the configured algorithm is `RSA`, the system SHALL use `Cipher.getInstance("RSA/ECB/OAEPPadding")` with explicit `OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)` to encrypt the plaintext key with the cached RSA public key. The resulting `WrappedKey.algorithm` SHALL be `"RSA-OAEP-256"`.

#### Scenario: RSA wrap output format
- **WHEN** a key is wrapped with RSA-OAEP-256
- **THEN** the WrappedKey ciphertext SHALL be the raw RSA OAEP ciphertext bytes, and algorithm SHALL be `"RSA-OAEP-256"`

#### Scenario: RSA unwrap calls CryptographyClient.unwrapKey()
- **WHEN** `unwrap()` is called with algorithm `"RSA-OAEP-256"`
- **THEN** the system SHALL call `CryptographyClient.unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, ciphertext)` and return the plaintext key bytes

### Requirement: Public key & key version loading from Azure Key Vault
The system SHALL load the RSA public key and key version using one of two strategies for public key (in priority order):
1. **From configuration**: if `lcl.crypto.azure.public-key` is set, parse the PEM string into a `java.security.PublicKey`
2. **From Azure Key Vault**: if `public-key` is not configured, call `CryptographyClient.getKey()` to retrieve the `KeyVaultKey`, extract the `JsonWebKey` public key parameters (`n`, `e`) and key version from `getProperties().getVersion()`

The key version SHALL always be auto-resolved via `CryptographyClient.getKey()` at startup — it is NOT a user-configurable property.

#### Scenario: Public key from configuration
- **WHEN** `lcl.crypto.azure.public-key` is set to a valid PEM public key
- **THEN** the provider SHALL use that key for local wrap, but SHALL still call `CryptographyClient.getKey()` to resolve the key version

#### Scenario: Public key and version fetched from Azure Key Vault
- **WHEN** `lcl.crypto.azure.public-key` is not configured
- **THEN** the provider SHALL call `CryptographyClient.getKey()` at initialization, convert the `JsonWebKey` to `java.security.PublicKey`, extract key version from `getProperties().getVersion()`, and cache both

#### Scenario: Invalid public key PEM
- **WHEN** the configured `public-key` value is not a valid PEM public key
- **THEN** the provider SHALL throw an `IllegalArgumentException` at construction time

#### Scenario: JsonWebKey to PublicKey conversion
- **WHEN** fetching public key from Azure Key Vault
- **THEN** the system SHALL decode base64url-encoded `n` (modulus) and `e` (exponent) from the `JsonWebKey`, construct `RSAPublicKeySpec`, and generate the key via `KeyFactory.getInstance("RSA")`

### Requirement: Azure AD authentication
The system SHALL support two authentication strategies for Azure Key Vault:
1. **Service principal**: when `tenant-id`, `client-id`, and `client-secret` are all configured, create a `ClientSecretCredential`
2. **DefaultAzureCredential**: when no explicit credentials are configured, fall back to `DefaultAzureCredential` (supports managed identity, Azure CLI, VS Code auth, etc.)

#### Scenario: Service principal authentication
- **WHEN** `lcl.crypto.azure.tenant-id`, `client-id`, and `client-secret` are all configured
- **THEN** the system SHALL create a `ClientSecretCredential` and use it for the `CryptographyClient`

#### Scenario: DefaultAzureCredential fallback
- **WHEN** none of the explicit credential properties are configured
- **THEN** the system SHALL use `DefaultAzureCredential` for authentication

#### Scenario: Partial credentials
- **WHEN** only some of `tenant-id`, `client-id`, `client-secret` are configured (not all three)
- **THEN** the auto-configuration SHALL throw an `IllegalArgumentException`

### Requirement: Auto-configuration activation
`AzureKeyVaultCmkAutoConfiguration` SHALL activate when `lcl.crypto.azure.vault-uri` is set. It SHALL create an `AzureKeyVaultCmkProvider` bean that takes precedence over the default `LocalSymmetricCmkProvider` via `@ConditionalOnMissingBean(CmkProvider.class)`.

#### Scenario: Azure provider active
- **WHEN** `lcl.crypto.azure.vault-uri` is configured
- **THEN** the `AzureKeyVaultCmkProvider` bean SHALL be created and `LocalSymmetricCmkProvider` SHALL not be created

#### Scenario: Azure provider inactive
- **WHEN** `lcl.crypto.azure.vault-uri` is not configured
- **THEN** `AzureKeyVaultCmkAutoConfiguration` SHALL not activate and `LocalSymmetricCmkProvider` SHALL remain the default

### Requirement: Configuration properties
The system SHALL support the following properties under `lcl.crypto.azure`:
- `vault-uri` (required): Azure Key Vault URI (e.g., `https://myvault.vault.azure.net`)
- `key-name` (required): Key name in the vault
- `public-key` (optional): PEM-encoded public key for local wrap
- `tenant-id`, `client-id`, `client-secret` (optional, all-or-nothing): Azure AD service principal credentials
- `algorithm` (optional, default `RSA`): Wrap algorithm identifier

#### Scenario: Minimal configuration
- **WHEN** only `vault-uri` and `key-name` are configured
- **THEN** the provider SHALL use `DefaultAzureCredential` and fetch the public key from Azure Key Vault

### Requirement: Integration test gating
Integration tests requiring real Azure credentials SHALL be annotated with `@EnabledIfEnvironmentVariable` (or equivalent JUnit 5 assumption) to skip execution when `AZURE_VAULT_URI` is not set.

#### Scenario: Tests skipped in CI
- **WHEN** environment variable `AZURE_VAULT_URI` is absent
- **THEN** the integration test SHALL be skipped (not failed)

#### Scenario: Tests run locally
- **WHEN** environment variables `AZURE_VAULT_URI`, `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_KEY_NAME` are set
- **THEN** the integration test SHALL execute a full wrap→unwrap roundtrip against real Azure Key Vault
