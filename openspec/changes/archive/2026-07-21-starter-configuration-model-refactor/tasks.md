## 1. Batch 1 — Hierarchical Properties Classes

- [x] 1.1 Create `RuntimeProperties` (`@ConfigurationProperties` prefix `lightcrypto.runtime`) with fields: `spiVersion` (int, default 1), `mode` (enum `SPRING_BOOT`/`STANDALONE`/`MIGRATION`), `strictMode` (boolean, default true). Add `@Validated`.
- [x] 1.2 Create `CryptographyProperties` (`@ConfigurationProperties` prefix `lightcrypto.cryptography`) with fields: `defaultAlgorithm` (SymmetricAlgorithm, default `AES_256_GCM`), `allowedAlgorithms` (List<SymmetricAlgorithm>, default all four), `requireAead` (boolean, default false). Add `@Validated` + `@NotNull` on `defaultAlgorithm`.
- [x] 1.3 Create `KeyVaultProperties` (`@ConfigurationProperties` prefix `lightcrypto.keyvault`) with nested `Cache` inner class: `ttl` (Duration, default `PT1H`), `maxEntries` (int, default 10000). Add `@Validated`.
- [x] 1.4 Create `TenantProperties` (`@ConfigurationProperties` prefix `lightcrypto.tenants`) with fields: `tenant` (String, default "default"), `realm` (String, default "default"). Add `@Validated` + `@NotBlank`.
- [x] 1.5 Create `KmsProperties` (`@ConfigurationProperties` prefix `lightcrypto.kms`) with `List<ProviderEntry> providers` and nested `ProviderEntry` record: `id`, `type` (enum `LOCAL_SYMMETRIC`/`ALIYUN`/`AZURE`), `keyHex`, `keyHexFile`, `config` (Map<String,String>). Add `@Validated`.

## 2. Batch 1 — AutoConfiguration Rewrite

- [x] 2.1 Rewrite `LightCryptoLinkAutoConfiguration`: replace `@EnableConfigurationProperties(CryptoProperties.class)` with all new properties classes; update `@ConditionalOnProperty` prefix from `lcl.crypto` to `lightcrypto`.
- [x] 2.2 Implement KMS provider dispatch in `LightCryptoLinkAutoConfiguration`: iterate `KmsProperties.providers`, create `LocalSymmetricCmkProvider` for `LOCAL_SYMMETRIC` type; support `keyHex` and `keyHexFile` reading.
- [x] 2.3 Remove `CryptoProperties.java` class entirely.

## 3. Batch 1 — Consumer Refactoring

- [x] 3.1 Refactor `EntityMetadataCache`: change constructor from `CryptoProperties` to `CryptographyProperties`. Update `defaultAlgorithm` resolution to read from `CryptographyProperties.defaultAlgorithm`.
- [x] 3.2 Refactor `KeyVaultService`: change constructor from `(VaultStore, CmkProvider, CryptoProperties)` to `(VaultStore, CmkProvider, KeyVaultProperties, TenantProperties)`. Update cache TTL to `KeyVaultProperties.cache.ttl`, tenant/realm to `TenantProperties`.
- [x] 3.3 Update all test classes that construct `EntityMetadataCache` and `KeyVaultService` with old `CryptoProperties` — replace with new properties classes.

## 4. Batch 1 — Validation

- [x] 4.1 Add JSR-380 validation annotations to all properties classes (`@NotNull`, `@NotBlank`, `@Min`, `@Pattern` where appropriate).
- [x] 4.2 Create `ConfigurationValidator` for cross-field rules: `defaultAlgorithm` must be in `allowedAlgorithms`; `LOCAL_SYMMETRIC` provider must have `keyHex` or `keyHexFile`; provider `id` uniqueness.
- [x] 4.3 Register `ConfigurationValidator` as a Spring `Validator` bean bound to `KmsProperties` via `@ConfigurationPropertiesBinding`.
- [x] 4.4 Write validation unit tests: missing CMK key, duplicate provider IDs, invalid algorithm, negative TTL.

## 5. Batch 2 — Adapter Configuration Isolation

- [x] 5.1 Create `MongoAdapterProperties` (`@ConfigurationProperties` prefix `lightcrypto.adapters.mongodb`) with fields: `enabled` (boolean, default true), `keyVaultCollection` (String, default "__lcl_keyvault"), `autoInit` (boolean, default true), `keyVaultDatabase` (String, optional).
- [x] 5.2 Refactor `MongoAdapterAutoConfiguration`: remove `CryptoProperties` import; inject `MongoAdapterProperties` + `TenantProperties`; pass adapter properties to `MongoVaultStore` and `KeyVaultService`.
- [x] 5.3 Add `@ConditionalOnProperty(prefix = "lightcrypto.adapters.mongodb", name = "enabled", matchIfMissing = true)` to `MongoAdapterAutoConfiguration`.
- [x] 5.4 Update `MongoVaultStore` to accept collection name and optional database name from `MongoAdapterProperties`.

## 6. Batch 2 — Sensitive Value Protection

- [x] 6.1 Implement `keyHexFile` support in `LightCryptoLinkAutoConfiguration`: when `keyHexFile` is set and `keyHex` is not, read file contents (trimmed UTF-8) as hex key.
- [x] 6.2 Add error handling for missing/unreadable `keyHexFile` with descriptive `ConfigurationException`.

## 7. Examples and Tests Migration

- [x] 7.1 Update `lcl-examples/basic-crud/application.yml`: migrate `lcl.crypto.*` to `lightcrypto.*` hierarchical structure.
- [x] 7.2 Update `lcl-examples/alibaba-kms/application.yml`: migrate to `lightcrypto.kms.providers` list format.
- [x] 7.3 Update `lcl-examples/azure-keyvault/application.yml`: migrate to `lightcrypto.kms.providers` list format.
- [x] 7.4 Update all starter test resources (`application.yml` / test properties) to use new prefix and structure.
- [x] 7.5 Update all adapter-mongodb test resources to use new prefix and structure.
- [x] 7.6 Fix all test classes with old `CryptoProperties` references — update imports and construction.

## 8. Verification

- [x] 8.1 Run `mvn clean verify -pl lcl-spring-boot-starter` — all tests pass.
- [x] 8.2 Run `mvn clean verify -pl lcl-adapter-mongodb` — all tests pass.
- [x] 8.3 Run `mvn -pl lcl-spring-boot-starter spotbugs:check` — no warnings.
- [x] 8.4 Run `mvn clean verify` — full build passes.
- [x] 8.5 Verify `lcl-spring-boot-starter` has no MongoDB compile dependency (`mvn dependency:tree -pl lcl-spring-boot-starter`).
- [x] 8.6 Verify no source file references `CryptoProperties` or `lcl.crypto` anywhere in the codebase.
