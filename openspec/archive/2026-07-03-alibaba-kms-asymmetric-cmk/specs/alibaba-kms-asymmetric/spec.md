## ADDED Requirements

### Requirement: AlibabaKmsCmkProvider implements CmkProvider
The system SHALL provide `AlibabaKmsCmkProvider` implementing the `CmkProvider` SPI with:
- `getProviderId()` returning `"alibaba-kms"`
- `wrap(byte[])` performing **local** asymmetric encryption using the cached public key
- `unwrap(WrappedKey)` performing **remote** decryption via Alibaba Cloud KMS `Decrypt` API

#### Scenario: Wrap and unwrap roundtrip (RSA)
- **WHEN** a 32-byte key is wrapped with algorithm `RSAES-OAEP-SHA256` and unwrapped via KMS Decrypt API
- **THEN** the unwrapped key SHALL be identical to the original

#### Scenario: Wrap and unwrap roundtrip (SM2)
- **WHEN** a 32-byte key is wrapped with algorithm `SM2PKE` and unwrapped via KMS Decrypt API
- **THEN** the unwrapped key SHALL be identical to the original

#### Scenario: Wrap produces unique output
- **WHEN** the same key material is wrapped twice
- **THEN** the two wrapped results SHALL differ (due to randomness in OAEP padding or SM2 ephemeral key)

### Requirement: RSA-OAEP-SHA256 local wrap
When the configured algorithm is `RSA`, the system SHALL use `Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")` to encrypt the plaintext key with the cached RSA public key. The resulting `WrappedKey.algorithm` SHALL be `"RSAES-OAEP-SHA256"`.

#### Scenario: RSA wrap output format
- **WHEN** a key is wrapped with RSA-OAEP-SHA256
- **THEN** the WrappedKey ciphertext SHALL be the raw RSA OAEP ciphertext bytes, and algorithm SHALL be `"RSAES-OAEP-SHA256"`

#### Scenario: RSA unwrap calls KMS Decrypt API
- **WHEN** `unwrap()` is called with algorithm `"RSAES-OAEP-SHA256"`
- **THEN** the system SHALL call Alibaba KMS `Decrypt` API with `encryptionAlgorithm = "RSAES_OAEP_SHA_256"` and return the plaintext key bytes

### Requirement: SM2 local wrap
When the configured algorithm is `SM2`, the system SHALL use `Cipher.getInstance("SM2", "BC")` (Bouncy Castle provider) to encrypt the plaintext key with the cached SM2 public key. The resulting `WrappedKey.algorithm` SHALL be `"SM2PKE"`. The ciphertext format SHALL be C1C3C2 (compatible with Alibaba KMS SM2PKE).

#### Scenario: SM2 wrap output format
- **WHEN** a key is wrapped with SM2
- **THEN** the WrappedKey ciphertext SHALL be the BC SM2 C1C3C2-encoded bytes, and algorithm SHALL be `"SM2PKE"`

#### Scenario: SM2 unwrap calls KMS Decrypt API
- **WHEN** `unwrap()` is called with algorithm `"SM2PKE"`
- **THEN** the system SHALL call Alibaba KMS `Decrypt` API with `encryptionAlgorithm = "SM2PKE"` and return the plaintext key bytes

### Requirement: Public key loading
The system SHALL load the asymmetric public key using one of two strategies (in priority order):
1. **From configuration**: if `lcl.crypto.alibaba.public-key` is set, parse the PEM string into a `java.security.PublicKey`
2. **From KMS API**: if `public-key` is not configured, call Alibaba KMS `GetPublicKey` API with the configured `key-id` and parse the returned PEM

#### Scenario: Public key from configuration
- **WHEN** `lcl.crypto.alibaba.public-key` is set to a valid PEM public key
- **THEN** the provider SHALL use that key for local wrap without calling KMS API

#### Scenario: Public key fetched from KMS
- **WHEN** `lcl.crypto.alibaba.public-key` is not configured
- **THEN** the provider SHALL call KMS `GetPublicKey(keyId)` at initialization and cache the result

#### Scenario: Invalid public key PEM
- **WHEN** the configured `public-key` value is not a valid PEM public key
- **THEN** the provider SHALL throw an `IllegalArgumentException` at construction time

### Requirement: Algorithm configuration
The system SHALL support a `lcl.crypto.alibaba.algorithm` property accepting `RSA` or `SM2` (case-insensitive), defaulting to `RSA` when not set.

#### Scenario: Default algorithm is RSA
- **WHEN** `lcl.crypto.alibaba.algorithm` is not configured
- **THEN** the provider SHALL use RSA-OAEP-SHA256 for wrap operations

#### Scenario: SM2 algorithm configured
- **WHEN** `lcl.crypto.alibaba.algorithm` is set to `SM2`
- **THEN** the provider SHALL use SM2 (BC Cipher) for wrap operations

#### Scenario: Invalid algorithm value
- **WHEN** `lcl.crypto.alibaba.algorithm` is set to a value other than `RSA` or `SM2`
- **THEN** the provider SHALL throw an `IllegalArgumentException` at construction time

### Requirement: KMS client construction
The system SHALL create an Alibaba Cloud KMS client using the configured `regionId`, `accessKeyId`, and `accessKeySecret`. The KMS client SHALL be used for `GetPublicKey` (when public key is not configured) and `Decrypt` API calls.

#### Scenario: Missing credentials
- **WHEN** `accessKeyId` or `accessKeySecret` is null or empty
- **THEN** the auto-configuration SHALL throw an `IllegalArgumentException`

### Requirement: Auto-configuration activation
`AlibabaKmsCmkAutoConfiguration` SHALL activate when `lcl.crypto.alibaba.key-id` is set. It SHALL create an `AlibabaKmsCmkProvider` bean that takes precedence over the default `LocalSymmetricCmkProvider` via `@ConditionalOnMissingBean(CmkProvider.class)`.

#### Scenario: Alibaba provider active
- **WHEN** `lcl.crypto.alibaba.key-id` is configured
- **THEN** the `AlibabaKmsCmkProvider` bean SHALL be created and `LocalSymmetricCmkProvider` SHALL not be created

#### Scenario: Alibaba provider inactive
- **WHEN** `lcl.crypto.alibaba.key-id` is not configured
- **THEN** `AlibabaKmsCmkAutoConfiguration` SHALL not activate and `LocalSymmetricCmkProvider` SHALL remain the default

### Requirement: Integration test gating
Integration tests requiring real KMS credentials SHALL be annotated with `@EnabledIfEnvironmentVariable` (or equivalent JUnit 5 assumption) to skip execution when `ALIBABA_AK_ID` is not set.

#### Scenario: Tests skipped in CI
- **WHEN** environment variable `ALIBABA_AK_ID` is absent
- **THEN** the integration test SHALL be skipped (not failed)

#### Scenario: Tests run locally
- **WHEN** environment variables `ALIBABA_AK_ID`, `ALIBABA_AK_SECRET`, `ALIBABA_KMS_KEY_ID` are set
- **THEN** the integration test SHALL execute a full wrap→unwrap roundtrip against real Alibaba KMS
