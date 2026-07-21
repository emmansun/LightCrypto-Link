## ADDED Requirements

### Requirement: BootstrapEngine sequential phase execution
The system SHALL provide a `BootstrapEngine` in `lcl-core` that executes a configurable list of `BootstrapPhase` instances in strict sequential order. Each phase SHALL be a named step with a `BootstrapCheck` function and a `FailureClass`. Phases execute in registration order; a FATAL failure in any phase SHALL abort all subsequent phases.

#### Scenario: All phases pass
- **WHEN** BootstrapEngine runs with phases [config, spi, kat, canary] and all checks return success
- **THEN** the engine SHALL return a `BootstrapResult` with status=READY and all phase results recorded

#### Scenario: Fatal phase aborts subsequent phases
- **WHEN** the KAT phase fails with FailureClass=FATAL
- **THEN** the engine SHALL abort immediately, NOT execute canary phase, and return BootstrapResult with status=FAILED

#### Scenario: Advisory phase continues execution
- **WHEN** a phase fails with FailureClass=ADVISORY
- **THEN** the engine SHALL log a warning via EventBus, record the advisory failure, and continue with subsequent phases

### Requirement: Failure classification
Each `BootstrapPhase` SHALL declare a `FailureClass`: `FATAL` (abort startup), `RECOVERABLE` (retry allowed), or `ADVISORY` (warn and continue). The engine SHALL handle each class according to the `strictMode` configuration.

#### Scenario: Recoverable failure in strict mode
- **WHEN** a RECOVERABLE phase fails and `lightcrypto.runtime.strict-mode` is true
- **THEN** the engine SHALL retry up to 3 times with exponential backoff, and if all retries fail, treat the failure as FATAL

#### Scenario: Recoverable failure in tolerant mode
- **WHEN** a RECOVERABLE phase fails and `lightcrypto.runtime.strict-mode` is false
- **THEN** the engine SHALL retry up to 3 times, and if all retries fail, downgrade to ADVISORY and continue

### Requirement: Bootstrap timeout
The BootstrapEngine SHALL enforce a total timeout (default: 15 seconds, configurable via `lightcrypto.runtime.bootstrap-timeout`). If the total execution time exceeds the timeout, the engine SHALL throw `BootstrapTimeoutException` (extending `CryptoException`).

#### Scenario: Bootstrap completes within timeout
- **WHEN** all phases complete in 300ms with a 15s timeout
- **THEN** the engine SHALL return normally with the result

#### Scenario: Bootstrap exceeds timeout
- **WHEN** phases take 16 seconds with a 15s timeout
- **THEN** the engine SHALL throw `BootstrapTimeoutException` and emit `lcl.bootstrap.timeout` event

### Requirement: Bootstrap event emission
Each phase SHALL emit structured events via EventBus: `lcl.bootstrap.<phase>.started` before execution, `lcl.bootstrap.<phase>.completed` on success, `lcl.bootstrap.<phase>.failed` on failure. The engine SHALL also emit `lcl.bootstrap.started` at the beginning and `lcl.bootstrap.ready` at the end.

#### Scenario: Phase event sequence
- **WHEN** the KAT phase executes successfully
- **THEN** the engine SHALL emit events: `lcl.bootstrap.kat.started`, `lcl.bootstrap.kat.completed` (with durationMicros)

#### Scenario: Bootstrap completion event
- **WHEN** all phases pass successfully
- **THEN** the engine SHALL emit `lcl.bootstrap.ready` with the total bootstrap duration

### Requirement: Bootstrap context
The engine SHALL accept a `BootstrapContext` object carrying all required dependencies: `CmkProvider`, `EventBus`, `RuntimeProperties`, and optional `VaultStore`. The context SHALL be immutable and constructed by the Spring auto-configuration.

#### Scenario: Context with all dependencies
- **WHEN** BootstrapContext is constructed with CmkProvider, EventBus, and VaultStore
- **THEN** all bootstrap phases SHALL have access to these dependencies

#### Scenario: Context with minimal dependencies
- **WHEN** BootstrapContext is constructed with only CmkProvider and EventBus (no VaultStore)
- **THEN** vault-dependent phases SHALL be skipped with ADVISORY status
