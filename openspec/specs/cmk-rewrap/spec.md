## ADDED Requirements

### Requirement: Rewrap vault keys under a new CMK provider
The system SHALL provide a `rewrapVault(String namespace, CmkProvider targetProvider)` operation that re-wraps all key entries (ACTIVE and ROTATED) in the specified namespace's VaultDocument using the target CMK provider, without modifying the underlying DEK/HMAC key material or any business data.

#### Scenario: Successful re-wrap of a namespace with multiple key versions
- **WHEN** `rewrapVault` is called with a valid namespace and a target provider different from the current provider
- **THEN** the system SHALL unwrap every KeyEntry's `wrappedDek` and `wrappedHmac` using the current provider, re-wrap them using the target provider, update `wrappingAlgorithm` to the target provider's algorithm, update `VaultDocument.cmkProvider` and `cmkId` to the target provider's identifiers, increment the document version, and persist via `VaultStore.rotate()`

#### Scenario: KCV invariance verification
- **WHEN** re-wrap is performed
- **THEN** the system SHALL verify that recomputed KCV (`dekKcv`, `hmacKcv`) and `binding` values remain identical to the stored values after unwrapping, and SHALL abort with `FatalCryptoException` if any mismatch is detected

#### Scenario: Post-rewrap roundtrip verification
- **WHEN** re-wrap completes for a namespace
- **THEN** the system SHALL perform a verification unwrap of the newly wrapped DEK and HMAC key using the target provider and confirm the raw key material matches the original, aborting if roundtrip fails

#### Scenario: Optimistic lock conflict during re-wrap
- **WHEN** a concurrent modification (e.g., DEK rotation) occurs on the same namespace during re-wrap
- **THEN** the system SHALL fail with a clear error indicating concurrent modification, leaving the vault document unchanged

#### Scenario: Re-wrap with same provider and same key is a no-op
- **WHEN** `rewrapVault` is called with a target provider whose `getProviderId()` equals the current `VaultDocument.cmkProvider` AND whose `getPublicReference()` equals `VaultDocument.cmkId`
- **THEN** the system SHALL skip the operation and return without modification

#### Scenario: Re-wrap with same providerId but different key proceeds
- **WHEN** `rewrapVault` is called with a target provider whose `getProviderId()` equals `VaultDocument.cmkProvider` but whose `getPublicReference()` differs from `VaultDocument.cmkId`
- **THEN** the system SHALL proceed with re-wrap (same-type key rotation), updating `cmkId` to the target's `getPublicReference()`

### Requirement: Batch re-wrap all vaults
The system SHALL provide a `rewrapAllVaults(CmkProvider targetProvider)` operation that iterates all namespaces via `VaultStore.loadAll()` and performs `rewrapVault` for each, with per-namespace error isolation.

#### Scenario: All namespaces re-wrapped successfully
- **WHEN** `rewrapAllVaults` is called and all namespaces succeed
- **THEN** the system SHALL return a summary with total count and success count

#### Scenario: Partial failure isolation
- **WHEN** re-wrap fails for one namespace (e.g., KMS timeout)
- **THEN** the system SHALL log the error, continue processing remaining namespaces, and return a summary including failed namespace names and error messages

### Requirement: Rewrap event emission
The system SHALL emit structured events via `EventBus` for re-wrap operations: `lcl.rewrap.namespace.completed` (L2) on successful per-namespace re-wrap, `lcl.rewrap.namespace.failed` (L2) on per-namespace failure, and `lcl.rewrap.batch.completed` (L2) on batch completion.

#### Scenario: Namespace re-wrap success event
- **WHEN** a single namespace re-wrap completes successfully
- **THEN** the system SHALL emit `lcl.rewrap.namespace.completed` with tier=L2, namespace, targetProviderId, keyCount, and durationMicros

#### Scenario: Namespace re-wrap failure event
- **WHEN** a single namespace re-wrap fails
- **THEN** the system SHALL emit `lcl.rewrap.namespace.failed` with tier=L2, namespace, result="failure", and errorType

#### Scenario: Batch completion event
- **WHEN** `rewrapAllVaults` finishes processing all namespaces
- **THEN** the system SHALL emit `lcl.rewrap.batch.completed` with tier=L2, totalCount, successCount, failedCount, and totalDurationMicros

### Requirement: CmkProviderRewrapRunner CommandLineRunner
The system SHALL provide a `CmkProviderRewrapRunner` implementing Spring Boot `CommandLineRunner`, disabled by default, that performs batch re-wrap at application startup when enabled via configuration.

#### Scenario: Runner disabled by default
- **WHEN** `lightcrypto.migration.rewrap.enabled` is not set or is `false`
- **THEN** the runner SHALL NOT execute any re-wrap logic

#### Scenario: Dry-run mode
- **WHEN** `lightcrypto.migration.rewrap.enabled=true` and `lightcrypto.migration.rewrap.dry-run=true`
- **THEN** the runner SHALL load all vault documents, validate that the target provider can perform wrap/unwrap roundtrip, log the namespaces that would be re-wrapped, but SHALL NOT modify any vault document

#### Scenario: Live execution
- **WHEN** `lightcrypto.migration.rewrap.enabled=true` and `lightcrypto.migration.rewrap.dry-run=false`
- **THEN** the runner SHALL resolve the target provider using three-level resolution (bean name → providerId+publicReference → providerId alone), invoke `rewrapAllVaults`, and log a summary of results

#### Scenario: Target provider resolution by bean name
- **WHEN** `lightcrypto.migration.rewrap.target-bean-name` is set
- **THEN** the runner SHALL resolve the target via `ApplicationContext.getBean(beanName)` and verify it is a `CmkProvider` instance, taking priority over providerId-based resolution

#### Scenario: Target provider resolution by providerId + publicReference
- **WHEN** `target-bean-name` is not set, `target-provider-id` is set, and `target-public-reference` is set
- **THEN** the runner SHALL match a registered CmkProvider bean where BOTH `getProviderId()` and `getPublicReference()` match the configured values

#### Scenario: Target provider not found
- **WHEN** `lightcrypto.migration.rewrap.target-provider-id` does not match any registered `CmkProvider` bean's `getProviderId()`
- **THEN** the runner SHALL log an error and abort without modifying any vault

### Requirement: Migration configuration properties
The system SHALL support the following configuration properties under `lightcrypto.migration.rewrap`:
- `enabled` (boolean, default `false`) — activates the runner
- `dry-run` (boolean, default `true`) — validation-only mode
- `target-provider-id` (String) — the `getProviderId()` of the target CMK provider
- `target-public-reference` (String, optional) — the `getPublicReference()` for disambiguation when multiple providers share the same providerId
- `target-bean-name` (String, optional) — Spring bean name of the target CmkProvider; takes highest priority over providerId/publicReference matching

#### Scenario: Default configuration is safe
- **WHEN** no `lightcrypto.migration.rewrap.*` properties are set
- **THEN** no re-wrap SHALL occur and application startup SHALL proceed normally

#### Scenario: Enabled without any target identifier
- **WHEN** `lightcrypto.migration.rewrap.enabled=true` but neither `target-bean-name` nor `target-provider-id` is set
- **THEN** the runner SHALL log a configuration error and skip execution
