## MODIFIED Requirements

### Requirement: Vault configuration
The system SHALL read vault settings from the new hierarchical configuration:
- Cache TTL from `lightcrypto.keyvault.cache.ttl` (Duration, default `PT1H`)
- Cache max entries from `lightcrypto.keyvault.cache.max-entries` (int, default 10000)
- Tenant from `lightcrypto.tenants.tenant` (String, default "default")
- Realm from `lightcrypto.tenants.realm` (String, default "default")

The vault collection name and database SHALL be provided by the adapter-specific configuration (e.g., `lightcrypto.adapters.mongodb.key-vault-collection`), NOT by core properties.

#### Scenario: Default tenant and realm
- **WHEN** neither `lightcrypto.tenants.tenant` nor `lightcrypto.tenants.realm` is configured
- **THEN** namespaces SHALL use "default" for both segments

#### Scenario: Custom tenant
- **WHEN** `lightcrypto.tenants.tenant` is set to "acme"
- **THEN** namespaces SHALL use "acme" as the tenant segment

#### Scenario: Cache TTL from keyvault properties
- **WHEN** `lightcrypto.keyvault.cache.ttl` is set to `PT30M`
- **THEN** the DEK cache SHALL expire entries after 30 minutes
