## ADDED Requirements

### Requirement: Micrometer Timer registration
The system SHALL register Micrometer Timers for core cryptographic operations: `lcl.crypto.encrypt.duration`, `lcl.crypto.decrypt.duration`, `lcl.blind_index.compute.duration`, `lcl.keyvault.load.duration`, `lcl.rotation.duration`. Each Timer SHALL be tagged with relevant dimensions (algorithm, namespace). Timers SHALL publish percentiles at p50, p95, p99.

#### Scenario: Encrypt timer recording
- **WHEN** an encrypt operation completes with AES_256_GCM algorithm on namespace "app.users#email"
- **THEN** the `lcl.crypto.encrypt.duration` Timer SHALL record the operation duration with tags algorithm="AES_256_GCM"

#### Scenario: Timer percentile publication
- **WHEN** metrics are scraped by a monitoring system
- **THEN** Timer metrics SHALL include p50, p95, and p99 percentile values

### Requirement: Micrometer Counter registration
The system SHALL register Micrometer Counters for operation counts: `lcl.crypto.encrypt.total`, `lcl.crypto.decrypt.total`, `lcl.rotation.total`. Each Counter SHALL be tagged with `result` (success/failure) and `algorithm` (for crypto operations).

#### Scenario: Successful encrypt counter increment
- **WHEN** an encrypt operation completes successfully
- **THEN** `lcl.crypto.encrypt.total` counter SHALL increment with tag result="success"

#### Scenario: Failed decrypt counter increment
- **WHEN** a decrypt operation fails
- **THEN** `lcl.crypto.decrypt.total` counter SHALL increment with tag result="failure"

### Requirement: Micrometer Gauge for cache statistics
The system SHALL register Micrometer Gauges for key vault cache: `lcl.keyvault.cache.size` (current entry count) and `lcl.keyvault.cache.hit.ratio` (hit/miss ratio). Gauges SHALL read directly from the cache implementation.

#### Scenario: Cache size gauge
- **WHEN** the DEK cache contains 42 entries
- **THEN** `lcl.keyvault.cache.size` gauge SHALL report 42

#### Scenario: Cache hit ratio gauge
- **WHEN** the cache has 90% hit rate
- **THEN** `lcl.keyvault.cache.hit.ratio` gauge SHALL report 0.9

### Requirement: MicrometerEventBus adapter
The system SHALL provide a `MicrometerEventBus` implementation that listens for LclEvents matching metric-relevant patterns (`lcl.crypto.*`, `lcl.rotation.*`, `lcl.keyvault.*`, `lcl.blind_index.*`) and updates the corresponding Micrometer meters.

#### Scenario: Event-driven timer update
- **WHEN** a `LclEvent` with name "lcl.crypto.encrypt.completed" and durationMicros=240 is emitted
- **THEN** the `lcl.crypto.encrypt.duration` Timer SHALL record 240 microseconds

#### Scenario: Non-metric event ignored
- **WHEN** a `LclEvent` with name "lcl.health.check.completed" is emitted
- **THEN** no Micrometer meter SHALL be updated

### Requirement: Metrics configuration
Metrics SHALL be controlled by `lightcrypto.observability.metrics.enabled` (boolean, default: true) and `lightcrypto.observability.metrics.publish-percentiles` (boolean, default: true). When disabled, no Micrometer meters SHALL be registered.

#### Scenario: Metrics disabled
- **WHEN** `lightcrypto.observability.metrics.enabled` is set to false
- **THEN** no Timer, Counter, or Gauge meters SHALL be registered with the MeterRegistry

#### Scenario: Percentiles disabled
- **WHEN** `lightcrypto.observability.metrics.publish-percentiles` is set to false
- **THEN** Timers SHALL record counts and totals but SHALL NOT publish percentile histograms
