# Changelog

All notable changes to LightCrypto-Link are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] — 2026-07-21

### Added

- **Transparent field encryption** with `@Encrypted` annotation (Spring Data MongoDB listener)
- **Multi-algorithm support**: `AES_256_GCM`, `AES_256_CBC`, `SM4_GCM`, `SM4_CBC`
- **Wire Format V1**: self-describing encrypted blobs with version, algorithm, namespace, DEK version, IV, ciphertext, and auth tag
- **HMAC-SHA-256 blind index**: HKDF-derived, namespace-scoped, base64url-encoded for exact-match queries
- **Multi-tenant namespace model**: `tenant.realm.app#field` four-segment canonical form
- **Per-namespace multi-DEK vault** with versioned `kid` and automatic key generation
- **Key rotation** with optimistic locking (`keyVaultService.rotateDek(namespace)`)
- **Pluggable storage adapter SPI**: `VaultStore`, `StorageAdapter`, `QueryTransformer` contracts
- **MongoDB adapter module** (`lcl-adapter-mongodb`): `MongoVaultStore`, `MongoStorageAdapter`, `MongoQueryTransformer`
- **CMK providers**:
  - `LOCAL_SYMMETRIC` — 32-byte hex key (inline or file)
  - `Azure Key Vault` — RSA asymmetric wrap/unwrap
  - `Alibaba Cloud KMS` — symmetric and asymmetric (RSA-OAEP) modes
- **Structured type encryption**: DOC (nested object), COL (collection), MAP modes with `WHOLE_OBJECT` / `WHOLE_ARRAY` / `ELEMENT` encryption modes
- **Programmatic API**: `ProgrammaticCryptoService` for DTO/message encryption and migration scripts
- **Observability**:
  - `EventBus` SPI with `Slf4jEventBus` (structured JSON events)
  - Micrometer metrics (Timer + Counter for encrypt/decrypt/rotation/keyvault)
  - Spring Boot Actuator `HealthIndicator` (UP / OUT_OF_SERVICE / DOWN / UNKNOWN)
- **Bootstrap diagnostics**:
  - `BootstrapEngine` with staged checks (Config, SPI, KAT, Vault, KMS, Canary)
  - `KatRunner` — Known Answer Test for all algorithms
  - `CanaryRunner` — encrypt/decrypt roundtrip self-test
  - Actuator endpoints: `/actuator/lclhealth`, `/actuator/lclkat`
- **Hierarchical configuration model**: `lightcrypto.cryptography.*`, `lightcrypto.keyvault.*`, `lightcrypto.kms.*`, `lightcrypto.tenants.*`, `lightcrypto.runtime.*`, `lightcrypto.observability.*`
- **JSR-380 validation** at startup (fail-fast on invalid config)
- **Golden Vector Suite** (`vectors/` directory) for cross-language verification
- **Type serialization**: automatic encrypt/decrypt of `Integer`, `Long`, `BigDecimal`, `LocalDate`, `LocalDateTime`, `Boolean` and more

### Architecture

```text
LightCrypto-Link/
├── lcl-spi/                     # SPI contracts (CmkProvider, VaultStore, StorageAdapter)
├── lcl-core/                    # Pure Java core (Wire Format V1, CryptoCodec, BlindIndex, KCV)
├── lcl-adapter-mongodb/         # MongoDB storage adapter
├── lcl-spring-boot-starter/     # Core starter (annotation-driven encryption)
├── lcl-provider-azure-kms/      # Azure Key Vault CMK provider
├── lcl-provider-alibaba-kms/    # Alibaba Cloud KMS CMK provider
├── lcl-examples/                # Example applications (basic-crud, azure, alibaba, observability)
├── vectors/                     # Golden test vectors
└── docs/                        # Documentation
```

### Dependencies

- Java 17+
- Spring Boot 3.2+
- Bouncy Castle 1.85 (SM4 support)
- Micrometer (optional, metrics)
- Spring Boot Actuator (optional, health + diagnostics endpoints)

[1.0.0]: https://github.com/emmansun/LightCrypto-Link/releases/tag/v1.0.0
