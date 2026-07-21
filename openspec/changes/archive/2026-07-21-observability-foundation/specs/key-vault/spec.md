## ADDED Requirements

### Requirement: KeyVaultService event emission
The system SHALL emit structured events via `EventBus` for key vault lifecycle operations. The following events SHALL be emitted: `lcl.keyvault.load.completed` (L2, on vault document load), `lcl.keyvault.init.completed` (L2, on first-time vault initialization), `lcl.rotation.execute.completed` (L2, on successful DEK rotation), `lcl.rotation.execute.failed` (L2, on failed rotation), `lcl.keyvault.cache.evicted` (L1, on cache entry eviction). The system SHALL replace direct `log.info`/`log.warn` calls for these operations with EventBus emissions.

#### Scenario: Vault load event emission
- **WHEN** KeyVaultService successfully loads a vault document for a namespace
- **THEN** the system SHALL emit `lcl.keyvault.load.completed` with tier=L2, namespace, durationMicros, and result="success"

#### Scenario: Rotation success event
- **WHEN** DEK rotation completes successfully
- **THEN** the system SHALL emit `lcl.rotation.execute.completed` with tier=L2, namespace, and durationMicros

#### Scenario: Rotation failure event
- **WHEN** DEK rotation fails
- **THEN** the system SHALL emit `lcl.rotation.execute.failed` with tier=L2, result="failure", and errorType

#### Scenario: Cache eviction event
- **WHEN** a DEK cache entry is evicted (TTL expiry or manual flush)
- **THEN** the system SHALL emit `lcl.keyvault.cache.evicted` with tier=L1 and the evicted namespace

### Requirement: EventBus injection into KeyVaultService
KeyVaultService SHALL accept an `EventBus` parameter in its constructor. When no EventBus bean is available, `NoOpEventBus` SHALL be used as the default.

#### Scenario: EventBus injected from Spring context
- **WHEN** KeyVaultService is constructed with a CompositeEventBus bean
- **THEN** all vault operations SHALL emit events through that EventBus

#### Scenario: Default NoOpEventBus
- **WHEN** no EventBus bean exists in the Spring context
- **THEN** KeyVaultService SHALL use NoOpEventBus with no behavioral change
