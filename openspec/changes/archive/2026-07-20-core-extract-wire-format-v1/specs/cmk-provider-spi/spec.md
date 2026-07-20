## MODIFIED Requirements

### Requirement: CmkProvider interface (unchanged semantics)
The system SHALL define a `CmkProvider` interface in `io.github.emmansun.lightcrypto.provider` with the following methods:
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

### Requirement: Module renamed to lcl-spi
The Maven module `lightcrypto-link-spi` SHALL be renamed to `lcl-spi` (artifactId: `lcl-spi`). The module SHALL remain a pure-interface module with zero implementation dependencies beyond JDK 17. All existing interfaces (`CmkProvider`, `WrappedKey`, `CryptoException`) SHALL retain their package `io.github.emmansun.lightcrypto.*` to minimize downstream changes.

#### Scenario: lcl-spi has no compile dependencies beyond JDK
- **WHEN** inspecting lcl-spi's effective POM
- **THEN** no third-party compile-scope dependencies SHALL be present

#### Scenario: Existing provider modules compile against lcl-spi
- **WHEN** `lcl-provider-alibaba-kms` and `lcl-provider-azure-kms` declare dependency on `lcl-spi`
- **THEN** compilation SHALL succeed with unchanged source code (same package names)

### Requirement: Reserved SPI interfaces for Phase 2
The `lcl-spi` module SHALL include the following placeholder interfaces with Javadoc documenting their Phase 2 intent. These interfaces SHALL have no implementations in Phase 1.

- `VaultStore`: abstraction for key vault persistence (MongoDB/MySQL/PG/etcd)
- `StorageAdapter`: abstraction for database-specific field storage (BSON sub-document, JSON column, etc.)
- `QueryTransformer`: abstraction for query rewrite across different databases

#### Scenario: VaultStore interface exists
- **WHEN** inspecting lcl-spi source
- **THEN** `io.github.emmansun.lightcrypto.spi.VaultStore` interface SHALL exist with methods: `save(VaultDocument doc)`, `Optional<VaultDocument> load(String namespace)`, `boolean exists(String namespace)`

#### Scenario: StorageAdapter interface exists
- **WHEN** inspecting lcl-spi source
- **THEN** `io.github.emmansun.lightcrypto.spi.StorageAdapter` interface SHALL exist as a marker with Javadoc describing Phase 2 field-storage abstraction

#### Scenario: QueryTransformer interface exists
- **WHEN** inspecting lcl-spi source
- **THEN** `io.github.emmansun.lightcrypto.spi.QueryTransformer` interface SHALL exist as a marker with Javadoc describing Phase 2 query-rewrite abstraction

#### Scenario: Reserved interfaces have no implementations
- **WHEN** searching for classes implementing VaultStore/StorageAdapter/QueryTransformer in Phase 1
- **THEN** no implementations SHALL be found (they are Phase 2 deliverables)

### Requirement: LocalSymmetricCmkProvider implementation (unchanged)
The system SHALL provide `LocalSymmetricCmkProvider` as the v1 default implementation. It SHALL use the CMK (32-byte symmetric key from `lcl.crypto.cmk` configuration) to wrap/unwrap using AES-256-GCM with a random 12-byte IV. The wrapped ciphertext SHALL be formatted as `IV (12 bytes) || GCM-ciphertext`.

#### Scenario: Wrap produces unique output
- **WHEN** the same key material is wrapped twice
- **THEN** the two wrapped results SHALL differ (due to random IV)

#### Scenario: Invalid CMK length
- **WHEN** the CMK configuration value is not exactly 32 bytes (64 hex characters)
- **THEN** the provider SHALL throw `IllegalArgumentException` at construction time

### Requirement: Provider auto-configuration (unchanged)
The system SHALL auto-configure `LocalSymmetricCmkProvider` as the default `CmkProvider` bean when no other `CmkProvider` bean is present in the application context (`@ConditionalOnMissingBean`).

#### Scenario: Default provider active
- **WHEN** no custom `CmkProvider` bean is defined
- **THEN** the auto-configured `LocalSymmetricCmkProvider` SHALL be used

#### Scenario: Custom provider override
- **WHEN** the application defines a custom `CmkProvider` bean
- **THEN** the custom bean SHALL take precedence and `LocalSymmetricCmkProvider` SHALL not be created
