## ADDED Requirements

### Requirement: LCL Health Actuator endpoint
The system SHALL expose a Spring Boot Actuator endpoint at `/actuator/lclhealth` that returns a JSON diagnostic response containing: `status` (READY/DEGRADED/FAILED), `sdkLanguage` ("java"), `sdkVersion`, `spiVersion`, `wireFormatVersion`, `components` (map of component name to status), `lastBootstrap` (ISO-8601 timestamp), `bootstrapDurationMs`.

#### Scenario: Health endpoint when ready
- **WHEN** all bootstrap phases passed and system is healthy
- **THEN** GET `/actuator/lclhealth` SHALL return status="READY" with all components "OK"

#### Scenario: Health endpoint when degraded
- **WHEN** KMS is unreachable but other components are healthy
- **THEN** GET `/actuator/lclhealth` SHALL return status="DEGRADED" with kms="DOWN"

### Requirement: LCL KAT Actuator endpoint
The system SHALL expose a Spring Boot Actuator endpoint at `/actuator/lclkat` that returns the latest KAT results including: per-algorithm status, execution duration, and vector IDs tested.

#### Scenario: KAT endpoint after successful bootstrap
- **WHEN** KAT passed during bootstrap
- **THEN** GET `/actuator/lclkat` SHALL return results showing all algorithms passed with durations

#### Scenario: KAT endpoint triggers on-demand re-run
- **WHEN** a POST request is sent to `/actuator/lclkat` (if write enabled)
- **THEN** the system SHALL re-run KAT and return fresh results

### Requirement: Diagnostics endpoint configuration
All diagnostics endpoints SHALL be controlled by `lightcrypto.observability.health.enabled` (boolean, default: true) and SHALL be conditional on `spring-boot-starter-actuator` being on the classpath. Endpoints SHALL be exposed via web (HTTP) and JMX when available.

#### Scenario: Endpoints disabled
- **WHEN** `lightcrypto.observability.health.enabled` is set to false
- **THEN** no `lclhealth` or `lclkat` endpoints SHALL be registered

#### Scenario: No actuator on classpath
- **WHEN** Spring Boot Actuator is not on the classpath
- **THEN** the diagnostics endpoints auto-configuration SHALL not activate (no error)

### Requirement: Endpoint response redaction
Diagnostics endpoints MUST redact sensitive information: CMK identifiers SHALL be shown as prefix + hash only, vault record contents SHALL be omitted, configuration secrets SHALL be masked, and tenant PII SHALL not appear.

#### Scenario: CMK identifier redaction
- **WHEN** the KMS provider is configured with keyId="alias/lcl-master"
- **THEN** the endpoint response SHALL show cmkId="alias/lc***" (redacted) not the full identifier
