## Requirements

### Requirement: RuntimeProperties class
The system SHALL define `RuntimeProperties` (`@ConfigurationProperties` prefix `lightcrypto.runtime`) with fields: `spiVersion` (int, default 1), `mode` (enum: `SPRING_BOOT`, `STANDALONE`, `MIGRATION`, default `SPRING_BOOT`), `strictMode` (boolean, default true). The class SHALL be annotated with `@Validated`.

#### Scenario: Default runtime values
- **WHEN** no `lightcrypto.runtime.*` properties are configured
- **THEN** `spiVersion` SHALL be 1, `mode` SHALL be `SPRING_BOOT`, `strictMode` SHALL be true

#### Scenario: Strict mode rejects warnings
- **WHEN** `strictMode` is true and a non-fatal configuration warning is detected
- **THEN** the system SHALL treat the warning as an error and fail startup

### Requirement: CryptographyProperties class
The system SHALL define `CryptographyProperties` (`@ConfigurationProperties` prefix `lightcrypto.cryptography`) with fields: `defaultAlgorithm` (SymmetricAlgorithm, default `AES_256_GCM`), `allowedAlgorithms` (List<SymmetricAlgorithm>, default all four), `requireAead` (boolean, default false). The class SHALL be annotated with `@Validated` and `defaultAlgorithm` SHALL be `@NotNull`.

#### Scenario: Default algorithm is AES_256_GCM
- **WHEN** no `lightcrypto.cryptography.*` properties are configured
- **THEN** `defaultAlgorithm` SHALL be `AES_256_GCM`

#### Scenario: Custom default algorithm
- **WHEN** `lightcrypto.cryptography.default-algorithm` is set to `SM4_GCM`
- **THEN** `@Encrypted` fields without explicit algorithm SHALL use `SM4_GCM`

#### Scenario: All algorithms allowed by default
- **WHEN** `allowedAlgorithms` is not configured
- **THEN** all four algorithms (`AES_256_GCM`, `AES_256_CBC`, `SM4_GCM`, `SM4_CBC`) SHALL be allowed

### Requirement: KeyVaultProperties class
The system SHALL define `KeyVaultProperties` (`@ConfigurationProperties` prefix `lightcrypto.keyvault`) with fields: `cache.ttl` (Duration, default `PT1H`), `cache.maxEntries` (int, default 10000). The class SHALL be annotated with `@Validated`. Cache `ttl` SHALL have `@Min(0)` constraint (0 means disabled).

#### Scenario: Default cache TTL
- **WHEN** no `lightcrypto.keyvault.cache.*` properties are configured
- **THEN** cache TTL SHALL be 1 hour (`PT1H`)

#### Scenario: Cache TTL of zero disables caching
- **WHEN** `lightcrypto.keyvault.cache.ttl` is set to `PT0S`
- **THEN** the DEK cache SHALL be disabled entirely

#### Scenario: Negative cache TTL rejected
- **WHEN** `lightcrypto.keyvault.cache.ttl` is set to a negative duration
- **THEN** startup validation SHALL fail with a validation error

### Requirement: TenantProperties class
The system SHALL define `TenantProperties` (`@ConfigurationProperties` prefix `lightcrypto.tenants`) with fields: `tenant` (String, default "default"), `realm` (String, default "default"). The class SHALL be annotated with `@Validated` and both fields SHALL be `@NotBlank`.

#### Scenario: Default tenant and realm
- **WHEN** no `lightcrypto.tenants.*` properties are configured
- **THEN** namespace construction SHALL use "default" for both tenant and realm segments

#### Scenario: Custom tenant
- **WHEN** `lightcrypto.tenants.tenant` is set to "acme"
- **THEN** namespace construction SHALL use "acme" as the tenant segment

### Requirement: CryptoProperties removal
The existing `CryptoProperties` class (prefix `lcl.crypto`) SHALL be removed. All consumers SHALL be updated to use the new hierarchical properties classes. The `LightCryptoLinkAutoConfiguration` SHALL enable all new `@ConfigurationProperties` classes.

#### Scenario: No CryptoProperties class exists
- **WHEN** searching for `CryptoProperties.java` in the codebase
- **THEN** no file SHALL be found

#### Scenario: New properties classes are enabled
- **WHEN** Spring Boot starts with `lcl-spring-boot-starter` on classpath
- **THEN** `RuntimeProperties`, `CryptographyProperties`, `KeyVaultProperties`, `KmsProperties`, `TenantProperties` SHALL all be bound and validated

### Requirement: EntityMetadataCache uses CryptographyProperties
`EntityMetadataCache` SHALL inject `CryptographyProperties` and `TenantProperties` instead of the old `CryptoProperties`. It SHALL read `defaultAlgorithm` from `CryptographyProperties`.

#### Scenario: Default algorithm source
- **WHEN** `EntityMetadataCache` resolves the algorithm for an `@Encrypted` field with `algorithm = DEFAULT`
- **THEN** it SHALL read the default from `CryptographyProperties.defaultAlgorithm`

### Requirement: KeyVaultService uses KeyVaultProperties and TenantProperties
`KeyVaultService` SHALL inject `KeyVaultProperties` for cache TTL configuration and `TenantProperties` for namespace construction. It SHALL NOT depend on the removed `CryptoProperties`.

#### Scenario: Cache TTL from KeyVaultProperties
- **WHEN** `KeyVaultService` initializes the DEK cache
- **THEN** it SHALL use `KeyVaultProperties.cache.ttl` as the cache expiration

#### Scenario: Namespace tenant from TenantProperties
- **WHEN** `KeyVaultService` constructs a namespace for vault operations
- **THEN** it SHALL use `TenantProperties.tenant` and `TenantProperties.realm`

### Requirement: Configuration enabled flag
The system SHALL support a top-level `lightcrypto.enabled` property (boolean, default true) to globally disable all LCL auto-configuration. When set to false, no LCL beans SHALL be created.

#### Scenario: LCL disabled
- **WHEN** `lightcrypto.enabled` is set to false
- **THEN** `LightCryptoLinkAutoConfiguration` and all adapter auto-configurations SHALL NOT activate

#### Scenario: LCL enabled by default
- **WHEN** `lightcrypto.enabled` is not configured
- **THEN** all LCL auto-configurations SHALL activate normally
