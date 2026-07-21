# LightCrypto-Link (LCL)

Pure Java, lightweight application-level field encryption for Spring Boot.

Transparent encrypt/decrypt on write/read, HMAC blind index for exact-match queries,
multi-DEK envelope encryption with key rotation support, pluggable storage adapters (MongoDB included), and no libmongocrypt dependency.

[![CI](https://github.com/emmansun/LightCrypto-Link/actions/workflows/ci.yml/badge.svg)](https://github.com/emmansun/LightCrypto-Link/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/emmansun/LightCrypto-Link/graph/badge.svg?token=d6cfb4Z53D)](https://codecov.io/gh/emmansun/LightCrypto-Link)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
![Maven Central Version](https://img.shields.io/maven-central/v/io.github.emmansun/lightcrypto-link-spring-boot-starter)

---

## TL;DR

1. Add starter dependency.
2. Configure a CMK provider (`lightcrypto.kms.providers` for local, or cloud KMS module).
3. Annotate fields with `@Encrypted`.
4. Use repository methods normally; blind-index queries work for `blindIndex=true` fields.

Deep docs are in [docs](docs/):

- [Configuration](docs/configuration.md)
- [Encryption Behavior (nested/collection/mode/types)](docs/encryption-behavior.md)
- [Architecture, Envelope, Storage Format, Rotation](docs/architecture.md)
- [CMK Provider SPI](docs/spi-cmk-provider.md)
- [Migration: introduce LCL to existing plaintext data](docs/migration/introduce-lcl-to-existing-plaintext-data.md)
- [Wiki](https://github.com/emmansun/LightCrypto-Link/wiki)

## Features

- Transparent field encryption with `@Encrypted`
- Symmetric algorithms: `AES_256_GCM`, `AES_256_CBC`, `SM4_GCM`, `SM4_CBC`
- Blind index query rewrite for exact-match queries
- Nested object and collection/map encryption
- Whole-object mode for container confidentiality
- Per-namespace multi-DEK vault with versioned `kid` and Wire Format V1 self-describing blobs
- Pluggable storage adapters (MongoDB adapter included; SPI for JDBC, Elasticsearch, etc.)
- Pluggable CMK providers (local, Azure Key Vault, Alibaba Cloud KMS)
- **Observability**: structured events, Micrometer metrics, Spring Boot Actuator health indicator

## Quick Start

### 1. Add dependency

Define a single version placeholder in your pom first:

```xml
<properties>
  <lcl.version>x.y.z</lcl.version>
</properties>
```

How to get latest stable version:

- Check the Maven Central badge at the top of this README.
- Or open: https://central.sonatype.com/artifact/io.github.emmansun/lightcrypto-link-spring-boot-starter

Add the starter dependency:

```xml
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lcl-spring-boot-starter</artifactId>
  <version>${lcl.version}</version>
</dependency>
<!-- Storage adapter (required — choose one) -->
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lcl-adapter-mongodb</artifactId>
  <version>${lcl.version}</version>
</dependency>
```

Cloud KMS modules:

```xml
<!-- Azure Key Vault -->
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lcl-provider-azure-kms</artifactId>
  <version>${lcl.version}</version>
</dependency>

<!-- Alibaba Cloud KMS -->
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lcl-provider-alibaba-kms</artifactId>
  <version>${lcl.version}</version>
</dependency>
```

If you need snapshot builds, use GitHub Packages:
[https://github.com/emmansun/LightCrypto-Link/packages](https://github.com/emmansun/LightCrypto-Link/packages)

### 2. Configure

Local symmetric CMK example:

```yaml
lightcrypto:
  kms:
    providers:
      - id: local
        type: LOCAL_SYMMETRIC
        key-hex: a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2
  cryptography:
    default-algorithm: AES_256_GCM
```

See full property references and cloud KMS examples:
[docs/configuration.md](docs/configuration.md)

### 3. Annotate

```java
@Document
public class User {
    @Id
    private String id;

    private String name;

    @Encrypted(blindIndex = true)
    private String phone;

    @Encrypted(algorithm = SymmetricAlgorithm.SM4_GCM)
    private String idCard;

    @Encrypted
    private Integer age;
}
```

### 4. Use normally

```java
userRepository.save(user);

User u = userRepository.findById(id).orElseThrow();

User found = userRepository.findByPhone("13800138001");
```

## Programmatic API (optional)

Use `ProgrammaticCryptoService` for DTO/message encryption, migration scripts,
or manual decryption of raw query results.

```java
Document encrypted = programmaticCryptoService.encryptValue("13800138000", User.class);
Object plain = programmaticCryptoService.decryptValue(encrypted);
```

## Rotation

Key rotation API is:

```java
keyVaultService.rotateDek("default.default.User#phone");
```

For behavior details, see [docs/architecture.md](docs/architecture.md).

## Observability

LCL provides built-in observability features (enabled by default):

### Structured Events

All crypto operations emit structured events via `EventBus`:

```json
{"event":"lcl.crypto.encrypt.completed","tier":"L2","result":"success","algorithm":"AES_256_GCM","durationMicros":240}
```

### Micrometer Metrics

When Micrometer is on the classpath, LCL registers timers and counters:

- `lcl.crypto.encrypt.duration` / `lcl.crypto.decrypt.duration`
- `lcl.rotation.duration` / `lcl.keyvault.load.duration`
- `lcl.crypto.encrypt.total` / `lcl.crypto.decrypt.total`

### Health Indicator

When Spring Boot Actuator is on the classpath, LCL registers a `HealthIndicator`:

- **UP** — all components healthy
- **OUT_OF_SERVICE** — degraded
- **DOWN** — fatal error

### Configuration

```yaml
lightcrypto:
  observability:
    enabled: true          # master switch
    events.enabled: true   # structured logging
    metrics.enabled: true  # Micrometer metrics
    health.enabled: true   # Actuator health
```

See [docs/configuration.md](docs/configuration.md) for full reference.

## Examples

See [lcl-examples](lcl-examples/) for runnable demos:

```bash
cd lcl-examples/basic-crud
mvn spring-boot:run

cd lcl-examples/azure-keyvault
mvn spring-boot:run

cd lcl-examples/alibaba-kms
mvn spring-boot:run

cd lcl-examples/observability
mvn spring-boot:run
# Then check: GET /actuator/health, GET /actuator/metrics/lcl.crypto.encrypt
```

## Building from source

```bash
git clone https://github.com/emmansun/LightCrypto-Link.git
cd LightCrypto-Link
mvn clean verify
```

### Project Structure

```text
LightCrypto-Link/
|- lcl-spi/                              # SPI contracts (CmkProvider, WrappedKey, VaultStore, StorageAdapter, QueryTransformer)
|- lcl-core/                             # Pure crypto core (Wire Format V1, CryptoCodec, BlindIndexEngine, KCV)
|- lcl-adapter-mongodb/                  # MongoDB storage adapter (MongoVaultStore, MongoStorageAdapter, MongoQueryTransformer)
|- lcl-spring-boot-starter/              # Core starter (annotation-driven encryption)
|- lcl-provider-azure-kms/               # Azure Key Vault CMK provider
|- lcl-provider-alibaba-kms/             # Alibaba Cloud KMS CMK provider
|- lcl-examples/                         # Example applications
|  |- basic-crud/
|  |- azure-keyvault/
|  |- alibaba-kms/
|  `- observability/
|- vectors/                              # Golden test vectors (cross-language verification)
`- docs/
```

## License

[Apache License 2.0](LICENSE)
