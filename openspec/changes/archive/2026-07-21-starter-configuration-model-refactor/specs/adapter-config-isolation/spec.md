## ADDED Requirements

### Requirement: MongoAdapterProperties class
The system SHALL define `MongoAdapterProperties` (`@ConfigurationProperties` prefix `lightcrypto.adapters.mongodb`) with fields: `enabled` (boolean, default true), `keyVaultCollection` (String, default "__lcl_keyvault"), `autoInit` (boolean, default true), `keyVaultDatabase` (String, optional — defaults to application database). The class SHALL be annotated with `@Validated`.

#### Scenario: Default adapter properties
- **WHEN** no `lightcrypto.adapters.mongodb.*` properties are configured
- **THEN** `enabled` SHALL be true, `keyVaultCollection` SHALL be "__lcl_keyvault", `autoInit` SHALL be true

#### Scenario: Adapter disabled
- **WHEN** `lightcrypto.adapters.mongodb.enabled` is set to false
- **THEN** `MongoAdapterAutoConfiguration` SHALL NOT register any MongoDB-specific beans

#### Scenario: Custom key vault collection
- **WHEN** `lightcrypto.adapters.mongodb.key-vault-collection` is set to "my_vault"
- **THEN** `MongoVaultStore` SHALL use "my_vault" as the collection name

### Requirement: MongoAdapterAutoConfiguration reads MongoAdapterProperties
`MongoAdapterAutoConfiguration` SHALL inject `MongoAdapterProperties` instead of `CryptoProperties` for all adapter-specific settings. It SHALL pass `MongoAdapterProperties` values to `MongoVaultStore` (collection name, database) and respect the `autoInit` flag.

#### Scenario: VaultStore uses adapter collection name
- **WHEN** `MongoAdapterAutoConfiguration` creates the `MongoVaultStore` bean
- **THEN** it SHALL use `MongoAdapterProperties.keyVaultCollection` as the target collection

#### Scenario: Auto-init controlled by adapter property
- **WHEN** `MongoAdapterProperties.autoInit` is false
- **THEN** the vault initialization logic SHALL be skipped even if the vault is empty

### Requirement: Adapter conditional on enabled flag
`MongoAdapterAutoConfiguration` SHALL declare `@ConditionalOnProperty(prefix = "lightcrypto.adapters.mongodb", name = "enabled", havingValue = "true", matchIfMissing = true)` to support disabling via configuration.

#### Scenario: MongoDB adapter active by default
- **WHEN** no `lightcrypto.adapters.mongodb.enabled` property exists
- **THEN** `MongoAdapterAutoConfiguration` SHALL activate normally

#### Scenario: MongoDB adapter explicitly disabled
- **WHEN** `lightcrypto.adapters.mongodb.enabled` is set to false
- **THEN** no MongoDB beans SHALL be registered by LCL
