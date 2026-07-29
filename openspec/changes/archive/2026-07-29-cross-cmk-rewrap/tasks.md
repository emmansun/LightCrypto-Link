## 1. Core API — KeyVaultService rewrap

- [x] 1.1 Add `RewrapResult` record (namespace, success, keyCount, errorMessage, durationMicros) to `lcl-spring-boot-starter`
- [x] 1.2 Implement `KeyVaultService.rewrapVault(String namespace, CmkProvider targetProvider)` — load VaultDocument, skip if same provider, unwrap all entries with current provider, verify KCV/binding invariance, re-wrap with target provider, post-rewrap roundtrip verification, persist via `VaultStore.rotate()`, evict DEK cache entry, emit `lcl.rewrap.namespace.completed` / `lcl.rewrap.namespace.failed` events
- [x] 1.3 Implement `KeyVaultService.rewrapAllVaults(CmkProvider targetProvider)` — call `VaultStore.loadAll()`, iterate namespaces invoking `rewrapVault` with try/catch per namespace, emit `lcl.rewrap.batch.completed` event, return `List<RewrapResult>`
- [x] 1.4 Verify compilation: `mvn compile -pl lcl-spring-boot-starter`

## 2. Configuration Properties

- [x] 2.1 Create `RewrapProperties` class under `lightcrypto.migration.rewrap` prefix with fields: `enabled` (boolean, default false), `dryRun` (boolean, default true), `targetProviderId` (String)
- [x] 2.2 Register `RewrapProperties` via `@ConfigurationProperties` in auto-configuration
- [x] 2.3 Add metadata entry in `additional-spring-configuration-metadata.json` for IDE hinting

## 3. CmkProviderRewrapRunner

- [x] 3.1 Create `CmkProviderRewrapRunner` implementing `CommandLineRunner` — inject `KeyVaultService`, `List<CmkProvider>`, `RewrapProperties`, `EventBus`
- [x] 3.2 Implement `run()` logic: check enabled flag, resolve target provider by providerId from bean list, validate target found, dry-run path (load all vaults + canary wrap/unwrap + log), live path (invoke `rewrapAllVaults` + log summary)
- [x] 3.3 Register runner bean in auto-configuration with `@ConditionalOnProperty(prefix = "lightcrypto.migration.rewrap", name = "enabled", havingValue = "true")`
- [x] 3.4 Verify compilation: `mvn compile -pl lcl-spring-boot-starter`

## 4. Unit Tests

- [x] 4.1 Test `rewrapVault` happy path: LOCAL_SYMMETRIC → mock target provider, assert KCV unchanged, wrappingAlgorithm updated, VaultDocument.cmkProvider updated
- [x] 4.2 Test `rewrapVault` same-provider no-op: assert VaultStore.rotate() NOT called
- [x] 4.3 Test `rewrapVault` KCV mismatch: corrupt stored KCV → assert FatalCryptoException
- [x] 4.4 Test `rewrapVault` optimistic lock conflict: mock VaultStore.rotate() throwing OptimisticLockException → assert clean error
- [x] 4.5 Test `rewrapAllVaults` partial failure: 3 namespaces, middle one fails → assert results contain 2 success + 1 failure
- [x] 4.6 Test `CmkProviderRewrapRunner` disabled by default: assert no interaction with KeyVaultService
- [x] 4.7 Test `CmkProviderRewrapRunner` dry-run: assert vaults loaded but not modified
- [x] 4.8 Test `CmkProviderRewrapRunner` target not found: assert error logged, no mutation
- [x] 4.9 Run full test suite: `mvn verify -pl lcl-spring-boot-starter`

## 5. Documentation

- [x] 5.1 Create `docs/migration/cross-cmk-provider-migration.md` — prerequisites, architecture diagram, step-by-step procedure (dry-run → live → config switch), transition window guidance, rollback strategy, checklist
- [x] 5.2 Update `docs/configuration.md` with `lightcrypto.migration.rewrap.*` property table

## 6. Example

- [x] 6.1 Add `CmkRewrapDemoRunner` in `lcl-examples/basic-crud` demonstrating LOCAL_SYMMETRIC → (simulated) cloud provider re-wrap with config toggles
- [x] 6.2 Verify example compiles: `mvn compile -pl lcl-examples/basic-crud`

## 7. Quality Gates

- [x] 7.1 Run SpotBugs: `mvn -pl lcl-spring-boot-starter spotbugs:check`
- [x] 7.2 Run full build: `mvn clean verify`
- [ ] 7.3 Commit with structured message

## 8. Enhanced Target Resolution & Same-Provider Check

- [x] 8.1 Add `targetBeanName` and `targetPublicReference` fields to `RewrapProperties`
- [x] 8.2 Implement three-level target resolution in `CmkProviderRewrapRunner` (bean name → providerId+publicRef → providerId)
- [x] 8.3 Enhance `KeyVaultService.rewrapVault()` same-provider check to compare both providerId AND publicReference (cmkId)
- [x] 8.4 ~~Remove redundant `"local-cmk-sha256:"` prefix~~ — CANCELLED: kept for Node.js cross-language compatibility
- [x] 8.5 Update `additional-spring-configuration-metadata.json` with new properties
- [x] 8.6 Add unit tests: same-providerId-different-key proceeds, bean-name resolution
- [x] 8.7 Update cloud provider modules (`lcl-provider-alibaba-kms`, `lcl-provider-azure-kms`) to support coexistence with LOCAL provider via `@ConditionalOnBean` migration bean
- [x] 8.8 Run full build: `mvn clean verify`
