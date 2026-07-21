## MODIFIED Requirements

### Requirement: LocalSymmetricCmkProvider implementation (unchanged)
The system SHALL provide `LocalSymmetricCmkProvider` as the v1 default implementation. It SHALL use the CMK (32-byte symmetric key) to wrap/unwrap using AES-256-GCM with a random 12-byte IV. The wrapped ciphertext SHALL be formatted as `IV (12 bytes) || GCM-ciphertext`. The CMK key source SHALL be determined by `KmsProperties` provider entry: either `keyHex` (inline 64-char hex string) or `keyHexFile` (path to file containing hex key).

#### Scenario: Wrap produces unique output
- **WHEN** the same key material is wrapped twice
- **THEN** the two wrapped results SHALL differ (due to random IV)

#### Scenario: Invalid CMK length
- **WHEN** the CMK configuration value is not exactly 32 bytes (64 hex characters)
- **THEN** the provider SHALL throw `IllegalArgumentException` at construction time

#### Scenario: CMK loaded from keyHex
- **WHEN** a `LOCAL_SYMMETRIC` provider entry has `keyHex` set to a valid 64-char hex string
- **THEN** the `LocalSymmetricCmkProvider` SHALL be constructed with the decoded key bytes

### Requirement: Provider auto-configuration
The system SHALL auto-configure `CmkProvider` beans based on `KmsProperties.providers[]` list. For each entry:
- `LOCAL_SYMMETRIC` type → creates `LocalSymmetricCmkProvider`
- `ALIYUN` type → delegates to Alibaba KMS provider auto-configuration
- `AZURE` type → delegates to Azure Key Vault provider auto-configuration

When no providers are configured and no custom `CmkProvider` bean exists, the system SHALL NOT create a default provider (fail-closed: CMK must be explicitly configured).

#### Scenario: Provider created from KmsProperties
- **WHEN** `lightcrypto.kms.providers` contains one `LOCAL_SYMMETRIC` entry
- **THEN** a `LocalSymmetricCmkProvider` bean SHALL be created from that entry's key

#### Scenario: No default provider when unconfigured
- **WHEN** `lightcrypto.kms.providers` is empty or not configured
- **THEN** no `CmkProvider` bean SHALL be auto-created

#### Scenario: Custom provider override
- **WHEN** the application defines a custom `CmkProvider` bean
- **THEN** the custom bean SHALL take precedence and provider list SHALL be ignored
