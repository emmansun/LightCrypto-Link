## Purpose

Define the behavioral contract for the cmk-provider-spi capability to match current implementation behavior.

## Requirements
### Requirement: CmkProvider interface
The system SHALL define a `CmkProvider` interface in `com.lcl.crypto.provider` with the following methods:
- `String getProviderId()`: returns the provider identifier (e.g., "local", "azure", "aliyun")
- `WrappedKey wrap(byte[] keyMaterial)`: wraps a raw key (DEK or HMAC key) using the CMK
- `byte[] unwrap(WrappedKey wrappedKey)`: unwraps a previously wrapped key

The `WrappedKey` SHALL be a record containing `byte[] ciphertext` and `String algorithm` (e.g., "AES-256-GCM", "RSA-OAEP-256") for self-describing unwrap operations.

#### Scenario: Wrap and unwrap roundtrip
- **WHEN** a 32-byte key is wrapped and then unwrapped using the same provider
- **THEN** the unwrapped key SHALL be identical to the original

#### Scenario: WrappedKey self-describes algorithm
- **WHEN** a key is wrapped with `LocalSymmetricCmkProvider`
- **THEN** the returned `WrappedKey.algorithm` SHALL be `"AES-256-GCM"`

### Requirement: LocalSymmetricCmkProvider implementation
The system SHALL provide `LocalSymmetricCmkProvider` as the v1 default implementation. It SHALL use the CMK (32-byte symmetric key from `lcl.crypto.cmk` configuration) to wrap/unwrap using AES-256-GCM with a random 12-byte IV. The wrapped ciphertext SHALL be formatted as `IV (12 bytes) || GCM-ciphertext`.

#### Scenario: Wrap produces unique output
- **WHEN** the same key material is wrapped twice
- **THEN** the two wrapped results SHALL differ (due to random IV)

#### Scenario: Invalid CMK length
- **WHEN** the CMK configuration value is not exactly 32 bytes (64 hex characters)
- **THEN** the provider SHALL throw `IllegalArgumentException` at construction time

### Requirement: Provider auto-configuration
The system SHALL auto-configure `LocalSymmetricCmkProvider` as the default `CmkProvider` bean when no other `CmkProvider` bean is present in the application context (`@ConditionalOnMissingBean`).

#### Scenario: Default provider active
- **WHEN** no custom `CmkProvider` bean is defined
- **THEN** the auto-configured `LocalSymmetricCmkProvider` SHALL be used

#### Scenario: Custom provider override
- **WHEN** the application defines a custom `CmkProvider` bean
- **THEN** the custom bean SHALL take precedence and `LocalSymmetricCmkProvider` SHALL not be created


