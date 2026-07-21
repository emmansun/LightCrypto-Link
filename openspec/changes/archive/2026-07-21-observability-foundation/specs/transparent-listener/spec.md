## ADDED Requirements

### Requirement: Encryption operation timing
The transparent encryption listener SHALL measure and emit timing events for encrypt and decrypt operations via `EventBus`. Each field-level encrypt/decrypt SHALL emit a `lcl.crypto.encrypt.completed` or `lcl.crypto.decrypt.completed` event with the algorithm, namespace, dekVersion, and measured durationMicros.

#### Scenario: Field encrypt timing
- **WHEN** a BeforeSaveEvent triggers encryption of one `@Encrypted` field using AES_256_GCM
- **THEN** the system SHALL emit `lcl.crypto.encrypt.completed` with algorithm="AES_256_GCM", the field's namespace, dekVersion, and measured durationMicros

#### Scenario: Field decrypt timing
- **WHEN** a read operation triggers decryption of one encrypted field
- **THEN** the system SHALL emit `lcl.crypto.decrypt.completed` with the algorithm, namespace, and measured durationMicros

### Requirement: EventBus availability in listener
The transparent listener SHALL accept an `EventBus` reference for event emission. When no EventBus is configured, the listener SHALL use `NoOpEventBus` and behave identically to current behavior (no metrics overhead).

#### Scenario: Listener with EventBus
- **WHEN** the listener has an EventBus injected
- **THEN** each encrypt/decrypt operation SHALL emit the corresponding LclEvent

#### Scenario: Listener without EventBus
- **WHEN** no EventBus is available
- **THEN** the listener SHALL use NoOpEventBus with zero overhead and no behavioral change
