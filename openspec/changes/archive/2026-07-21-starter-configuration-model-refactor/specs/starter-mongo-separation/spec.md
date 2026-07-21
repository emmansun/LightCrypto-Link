## MODIFIED Requirements

### Requirement: Adapter module provides all MongoDB-specific AutoConfiguration
`MongoAdapterAutoConfiguration` SHALL register all MongoDB-specific beans: `MongoVaultStore`, `MongoStorageAdapter`, `MongoQueryTransformer`, `CryptoBeforeSaveListener`, `CryptoMappingMongoConverter`, `MongoEncryptHandler`, `MongoDecryptHandler`, `CryptoMongoQueryCreator`, `BsonDocumentAccessor`, `BsonStructuredValueCodec`, and the `@EnableMongoRepositories` configuration. It SHALL inject `MongoAdapterProperties` (prefix `lightcrypto.adapters.mongodb`) for adapter-specific settings instead of `CryptoProperties`. It SHALL inject `TenantProperties` for namespace-related concerns.

#### Scenario: Adapter AutoConfiguration activates when MongoTemplate is present
- **WHEN** Spring Boot starts with both starter and adapter-mongodb on classpath
- **THEN** `MongoAdapterAutoConfiguration` SHALL register all MongoDB infrastructure beans

#### Scenario: Adapter AutoConfiguration does NOT activate without MongoDB
- **WHEN** Spring Boot starts with only adapter-mongodb on classpath (no `MongoTemplate`)
- **THEN** `MongoAdapterAutoConfiguration` SHALL NOT activate (conditional on `MongoTemplate.class`)

#### Scenario: Adapter uses MongoAdapterProperties
- **WHEN** `MongoAdapterAutoConfiguration` creates `MongoVaultStore`
- **THEN** it SHALL read the collection name from `MongoAdapterProperties.keyVaultCollection`
