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
The system SHALL provide `LocalSymmetricCmkProvider` as the v1 default implementation. It SHALL use the CMK (32-byte symmetric key) to wrap/unwrap using AES-256-GCM with a random 12-byte IV. The wrapped ciphertext SHALL be formatted as `IV (12 bytes) || GCM-ciphertext`. The CMK key source SHALL be determined by `KmsProperties` provider entry: either `keyHex` (inline 64-char hex string) or `keyHexFile` (path to file containing hex key).

#### Scenario: Wrap produces unique output
- **WHEN** the same key material is wrapped twice
- **THEN** the two wrapped results SHALL differ (due to random IV)

#### Scenario: Invalid CMK length
- **WHEN** the CMK configuration value is not exactly 32 bytes (64 hex characters)
- **THEN** the provider SHALL throw `IllegalArgumentException` at construction time

#### Scenario: CMK loaded from keyHex
- **WHEN** a `LOCAL_SYMMETRIC` provider entry has `keyHex` set to a valid 64-char hex string
- **THEN** the `LocalSymmetricCmkProvider` SHALL be constructed with the decoded key bytes

### Requirement: Provider auto-configuration
The system SHALL auto-configure `CmkProvider` beans based on `KmsProperties.providers[]` list. For each entry:
- `LOCAL_SYMMETRIC` type → creates `LocalSymmetricCmkProvider`
- `ALIYUN` type → delegates to Alibaba KMS provider auto-configuration
- `AZURE` type → delegates to Azure Key Vault provider auto-configuration

When no providers are configured and no custom `CmkProvider` bean exists, the system SHALL NOT create a default provider (fail-closed: CMK must be explicitly configured).

#### Scenario: Provider created from KmsProperties
- **WHEN** `lightcrypto.kms.providers` contains one `LOCAL_SYMMETRIC` entry
- **THEN** a `LocalSymmetricCmkProvider` bean SHALL be created from that entry's key

#### Scenario: No default provider when unconfigured
- **WHEN** `lightcrypto.kms.providers` is empty or not configured
- **THEN** no `CmkProvider` bean SHALL be auto-created

#### Scenario: Custom provider override
- **WHEN** the application defines a custom `CmkProvider` bean
- **THEN** the custom bean SHALL take precedence and provider list SHALL be ignored
