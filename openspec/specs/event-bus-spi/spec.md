## ADDED Requirements

### Requirement: EventBus interface contract
The system SHALL provide an `EventBus` interface in `lcl-core` with a single method `emit(LclEvent event)`. The interface SHALL have zero framework dependencies (only JDK types). A `NoOpEventBus` singleton SHALL be provided as the default no-operation implementation.

#### Scenario: EventBus emit method
- **WHEN** `eventBus.emit(event)` is called with a valid `LclEvent`
- **THEN** the implementation SHALL process the event without throwing exceptions to the caller

#### Scenario: NoOpEventBus default behavior
- **WHEN** no EventBus implementation is configured
- **THEN** the system SHALL use `NoOpEventBus` which silently discards all events with zero overhead

### Requirement: LclEvent immutable model
The system SHALL provide `LclEvent` as an immutable value object with the following fields: `event` (String, required), `tier` (EventTier enum: L1/L2/L3, required), `timestamp` (Instant, auto-populated), `durationMicros` (long, -1 if not applicable), `result` (String: "success"/"failure", required), `namespace` (String, nullable), `algorithm` (String, nullable), `dekVersion` (int, -1 if not applicable), `errorType` (String, nullable), `attributes` (Map<String, String>, extensible). Construction SHALL use a builder pattern.

#### Scenario: Building a crypto event
- **WHEN** building an event for AES-256-GCM encryption completion
- **THEN** the builder SHALL produce an LclEvent with event="lcl.crypto.encrypt.completed", tier=L2, result="success", algorithm="AES_256_GCM", dekVersion set, and durationMicros measured

#### Scenario: Building a failure event
- **WHEN** building an event for a decryption failure
- **THEN** the builder SHALL produce an LclEvent with result="failure" and errorType set to the error classification (e.g., "TAG_MISMATCH")

### Requirement: EventTier classification
The system SHALL classify events into three tiers: L1 (Diagnostic, best-effort delivery), L2 (Operational, reliable delivery for monitoring), L3 (Audit, guaranteed delivery for compliance). The EventTier SHALL be an enum with values `L1`, `L2`, `L3`.

#### Scenario: Operational event tier
- **WHEN** emitting a crypto encryption completion event
- **THEN** the event tier SHALL be L2 (Operational)

#### Scenario: Diagnostic event tier
- **WHEN** emitting a cache eviction event
- **THEN** the event tier SHALL be L1 (Diagnostic)

### Requirement: Event naming convention
Event names SHALL be lowercase, dot-separated, follow the pattern `lcl.<subsystem>.<operation>.<status>`, and not exceed 96 characters.

#### Scenario: Valid event name
- **WHEN** emitting an event for successful key vault load
- **THEN** the event name SHALL be "lcl.keyvault.load.completed"

#### Scenario: Failure event name
- **WHEN** emitting an event for failed rotation
- **THEN** the event name SHALL be "lcl.rotation.execute.failed"

### Requirement: CompositeEventBus
The system SHALL provide a `CompositeEventBus` that accepts a list of `EventBus` implementations and delegates `emit()` to each in order. If any delegate throws an exception, the CompositeEventBus SHALL catch it and continue with remaining delegates.

#### Scenario: Multi-listener emission
- **WHEN** CompositeEventBus has Slf4jEventBus and MicrometerEventBus registered
- **THEN** both SHALL receive the same LclEvent instance

#### Scenario: Listener failure isolation
- **WHEN** one EventBus delegate throws an exception during emit
- **THEN** remaining delegates SHALL still receive the event, and the exception SHALL be logged but not propagated

### Requirement: Prohibited event content
Events MUST NEVER contain: IV, Tag, Ciphertext, Wrapped DEK, CMK material, Plaintext values, Query values, or Personal data of any kind.

#### Scenario: Event sanitization
- **WHEN** building an LclEvent for a crypto operation
- **THEN** the event SHALL NOT include the plaintext, ciphertext, IV, or any cryptographic key material in any field
