## ADDED Requirements

### Requirement: LclHealthStatus four-state model
The system SHALL define `LclHealthStatus` enum with four states: `STARTING` (initialization in progress), `READY` (fully operational), `DEGRADED` (one or more non-critical components unavailable), `FAILED` (fatal error, crypto operations cannot proceed). The overall health SHALL be computed as the worst state across all registered components.

#### Scenario: All components healthy
- **WHEN** core, kms, and vault components all report READY
- **THEN** overall health SHALL be READY

#### Scenario: One component degraded
- **WHEN** core reports READY, vault reports READY, kms reports DEGRADED
- **THEN** overall health SHALL be DEGRADED

#### Scenario: Fatal failure
- **WHEN** any component reports FAILED
- **THEN** overall health SHALL be FAILED regardless of other component states

### Requirement: Spring Boot Actuator HealthIndicator integration
The system SHALL provide `LclHealthIndicator` implementing Spring Boot's `HealthIndicator` interface. It SHALL translate `LclHealthStatus` to Spring Boot `Status` mapping: READY→UP, DEGRADED→OUT_OF_SERVICE, FAILED→DOWN, STARTING→UNKNOWN. The health details SHALL include component-level status and SDK version.

#### Scenario: Actuator health endpoint reports LCL ready
- **WHEN** LCL is fully initialized and all components healthy
- **THEN** the `/actuator/health` endpoint SHALL include "lcl" with status UP and details showing component statuses

#### Scenario: Actuator health reports degraded state
- **WHEN** KMS is unreachable but vault is healthy
- **THEN** the health indicator SHALL report OUT_OF_SERVICE with detail "kms: DOWN"

### Requirement: Component health checks
The system SHALL register three built-in health checks: `CoreHealthCheck` (validates EventBus bean exists and configuration is valid), `KmsHealthCheck` (validates at least one CmkProvider bean is registered), `VaultHealthCheck` (validates KeyVaultService is initialized and vault is accessible).

#### Scenario: Core health check passes
- **WHEN** EventBus bean is registered and configuration validation passes
- **THEN** CoreHealthCheck SHALL report READY

#### Scenario: KMS health check with no providers
- **WHEN** no CmkProvider bean exists in the application context
- **THEN** KmsHealthCheck SHALL report FAILED

#### Scenario: Vault health check with inaccessible vault
- **WHEN** KeyVaultService cannot reach the vault store
- **THEN** VaultHealthCheck SHALL report DEGRADED

### Requirement: Health indicator configuration
The health indicator SHALL be controlled by `lightcrypto.observability.health.enabled` (boolean, default: true). When disabled, no `LclHealthIndicator` bean SHALL be registered. The health indicator SHALL be conditional on `spring-boot-actuator` being on the classpath.

#### Scenario: Health indicator disabled
- **WHEN** `lightcrypto.observability.health.enabled` is set to false
- **THEN** no LclHealthIndicator bean SHALL be registered

#### Scenario: No actuator on classpath
- **WHEN** Spring Boot Actuator is not on the classpath
- **THEN** the health indicator auto-configuration SHALL not activate (no error)
