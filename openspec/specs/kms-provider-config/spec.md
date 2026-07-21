## Requirements

### Requirement: KmsProperties class
The system SHALL define `KmsProperties` (`@ConfigurationProperties` prefix `lightcrypto.kms`) with a `providers` field of type `List<ProviderEntry>`. Each `ProviderEntry` SHALL contain: `id` (String, `@NotBlank`), `type` (enum: `LOCAL_SYMMETRIC`, `ALIYUN`, `AZURE`, `@NotNull`), `keyHex` (String, optional), `keyHexFile` (String, optional — path to file), `config` (Map<String, String>, optional — provider-specific settings). The class SHALL be annotated with `@Validated`.

#### Scenario: Empty providers list
- **WHEN** `lightcrypto.kms.providers` is not configured
- **THEN** no `CmkProvider` bean SHALL be auto-created from KMS configuration; the system SHALL fall back to `@ConditionalOnMissingBean` behavior

#### Scenario: Single local provider
- **WHEN** one provider with `type: LOCAL_SYMMETRIC` and valid `keyHex` is configured
- **THEN** a `LocalSymmetricCmkProvider` bean SHALL be created with the specified key

### Requirement: Provider type dispatch
`LightCryptoLinkAutoConfiguration` SHALL dispatch `CmkProvider` creation based on `ProviderEntry.type`:
- `LOCAL_SYMMETRIC` → `LocalSymmetricCmkProvider` using `keyHex` (or `keyHexFile`)
- `ALIYUN` → delegates to `AlibabaKmsCmkProvider` factory (from `lcl-provider-alibaba-kms`)
- `AZURE` → delegates to `AzureKeyVaultCmkProvider` factory (from `lcl-provider-azure-kms`)

Unknown types SHALL cause startup failure with a descriptive error message.

#### Scenario: Unknown provider type rejected
- **WHEN** a provider entry has a `type` value not in the supported enum
- **THEN** startup SHALL fail with `ConfigurationException` indicating the unsupported type

#### Scenario: Multiple providers configured
- **WHEN** two or more providers are listed in `lightcrypto.kms.providers`
- **THEN** each SHALL be created as a named bean (using provider `id` as qualifier)

### Requirement: Sensitive value via keyHexFile
For `LOCAL_SYMMETRIC` providers, the system SHALL support `keyHexFile` as an alternative to inline `keyHex`. When `keyHexFile` is set, the system SHALL read the file contents (trimmed, UTF-8) as the hex-encoded key. If both `keyHex` and `keyHexFile` are set, `keyHex` SHALL take precedence.

#### Scenario: Key loaded from file
- **WHEN** `keyHexFile` is set to `/run/secrets/cmk_hex` and the file contains `abcdef...` (64 hex chars)
- **THEN** the `LocalSymmetricCmkProvider` SHALL use the file content as the CMK

#### Scenario: keyHex takes precedence over keyHexFile
- **WHEN** both `keyHex` and `keyHexFile` are configured
- **THEN** `keyHex` SHALL be used and `keyHexFile` SHALL be ignored

#### Scenario: keyHexFile not found
- **WHEN** `keyHexFile` points to a non-existent file
- **THEN** startup SHALL fail with `ConfigurationException` indicating the missing file

### Requirement: Provider validation
Each `ProviderEntry` SHALL be validated at startup:
- `LOCAL_SYMMETRIC` requires either `keyHex` or `keyHexFile` (not both empty)
- Provider `id` values MUST be unique across the list
- Duplicate `id` values SHALL cause startup failure

#### Scenario: LOCAL_SYMMETRIC without key
- **WHEN** a `LOCAL_SYMMETRIC` provider has neither `keyHex` nor `keyHexFile`
- **THEN** startup validation SHALL fail with a descriptive error

#### Scenario: Duplicate provider IDs
- **WHEN** two providers have the same `id` value
- **THEN** startup validation SHALL fail with a duplicate ID error
