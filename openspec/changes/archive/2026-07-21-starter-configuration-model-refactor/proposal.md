## Why

The current starter configuration uses a single flat `CryptoProperties` class (prefix `lcl.crypto`) with 8 properties covering runtime, cryptography, key vault, and tenant concerns — all mixed together. This violates LCL-CORE-009's hierarchical configuration model, lacks startup validation, hardcodes KMS as a single local symmetric key, and prevents adapter-level configuration isolation. With Phase 1 and Phase 2 complete, now is the right time to align the configuration model with the platform design before adding observability and multi-tenant features.

## What Changes

- **BREAKING**: Configuration prefix changes from `lcl.crypto.*` to `lightcrypto.*` with hierarchical sub-namespaces (`runtime`, `cryptography`, `keyvault`, `kms`, `tenants`, `adapters`)
- **BREAKING**: `CryptoProperties` class removed; replaced by 6 dedicated `@ConfigurationProperties` classes
- New `KmsProperties` with multi-provider list-style configuration supporting `LOCAL_SYMMETRIC`, `ALIYUN`, `AZURE` provider types
- Sensitive value protection: KMS keys support `keyHexFile` references and env variable injection
- Adapter configuration isolation: `MongoAdapterAutoConfiguration` reads from `lightcrypto.adapters.mongodb.*` instead of shared `CryptoProperties`
- Startup validation via `@Validated`: CMK format, algorithm whitelist, TTL constraints, required fields

## Capabilities

### New Capabilities
- `configuration-hierarchy`: Hierarchical `@ConfigurationProperties` structure replacing flat `CryptoProperties` — covers `RuntimeProperties`, `CryptographyProperties`, `KeyVaultProperties`, `TenantProperties`, and their validation rules
- `kms-provider-config`: Multi-provider KMS configuration with list-style `providers[]`, type-based dispatch (`LOCAL_SYMMETRIC` / `ALIYUN` / `AZURE`), and sensitive value protection (`keyHexFile`, env variables)
- `adapter-config-isolation`: Adapter-specific configuration namespace under `lightcrypto.adapters.*`, decoupling adapter config from core properties

### Modified Capabilities
- `cmk-provider-spi`: `LocalSymmetricCmkProvider` config source changes from `lcl.crypto.cmk` to `lightcrypto.kms.providers[]` list entry with type `LOCAL_SYMMETRIC`; auto-configuration dispatches based on provider type
- `key-vault`: Vault configuration source changes from `lcl.crypto.tenant`/`lcl.crypto.realm`/`lcl.crypto.cacheTtl` to `lightcrypto.keyvault.cache.*` and `lightcrypto.tenants.*`
- `starter-mongo-separation`: `MongoAdapterAutoConfiguration` no longer depends on `CryptoProperties`; reads adapter config from `AdapterProperties`

## Impact

- **lcl-spring-boot-starter**: Major refactoring — `CryptoProperties` replaced by 6 new config classes; `LightCryptoLinkAutoConfiguration` rewritten; `EntityMetadataCache`, `KeyVaultService` injection points updated
- **lcl-adapter-mongodb**: `MongoAdapterAutoConfiguration` reads `AdapterProperties` instead of `CryptoProperties`
- **lcl-provider-alibaba-kms / lcl-provider-azure-kms**: Auto-configuration aligned with new `KmsProperties` provider dispatch (no SPI changes)
- **lcl-examples**: All `application.yml` files migrated from `lcl.crypto.*` to `lightcrypto.*` structure
- **User-facing**: **BREAKING** — all existing `application.yml` configurations must update property keys
