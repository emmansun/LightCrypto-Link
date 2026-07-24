# LightCrypto-Link (LCL)

Pure Java, lightweight application-level field encryption for Spring Boot.

Transparent encrypt/decrypt on write/read, HMAC blind index for exact-match queries,
multi-DEK envelope encryption with key rotation support, pluggable storage adapters (MongoDB included), and no libmongocrypt dependency.

[![CI](https://github.com/emmansun/LightCrypto-Link/actions/workflows/ci.yml/badge.svg)](https://github.com/emmansun/LightCrypto-Link/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/emmansun/LightCrypto-Link/graph/badge.svg?token=d6cfb4Z53D)](https://codecov.io/gh/emmansun/LightCrypto-Link)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B%20%7C%204.0%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
![Maven Central Version](https://img.shields.io/maven-central/v/io.github.emmansun/lcl-spring-boot-starter)

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
- Or open: https://central.sonatype.com/artifact/io.github.emmansun/lcl-spring-boot-starter

Add the starter dependency:

```xml
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lcl-spring-boot-starter</artifactId>
  <version>${lcl.version}</version>
</dependency>
<!-- Storage adapter (required — choose one matching your Spring Boot version) -->
<!-- Spring Boot 3.x -->
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lcl-adapter-mongodb</artifactId>
  <version>${lcl.version}</version>
</dependency>
<!-- Spring Boot 4.x (use -v4 variant) -->
<!--
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lcl-adapter-mongodb-v4</artifactId>
  <version>${lcl.version}</version>
</dependency>
-->
```

> **Adapter selection**: Use `lcl-adapter-mongodb` for Spring Boot 3.x, or `lcl-adapter-mongodb-v4` for Spring Boot 4.x. Do **not** include both — only one adapter should be on the classpath.

#### Spring Boot 4.x notes

The `-v4` adapter transitively pulls in the SB3 adapter JAR (for shared classes). To avoid conflicts, add the following to your SB4 application:

```properties
# application.properties (SB4)
spring.autoconfigure.exclude=io.github.emmansun.lightcrypto.adapter.mongodb.MongoAdapterAutoConfiguration
spring.main.allow-bean-definition-overriding=true
# SB4 renamed the MongoDB URI property (was spring.data.mongodb.uri)
spring.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/mydb}
```

If your parent POM manages Spring Boot 3.x versions, override in your module's `<dependencyManagement>`:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.data</groupId>
      <artifactId>spring-data-commons</artifactId>
      <version>4.0.5</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-mongodb</artifactId>
      <version>4.0.7</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>4.0.7</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
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

## Bootstrap Diagnostics

LCL runs a self-diagnostic sequence at startup to verify cryptographic integrity before serving traffic:

| Phase | Check | Failure Class |
|-------|-------|---------------|
| BOOT-1 | Configuration validation | FATAL |
| BOOT-2 | SPI version check | FATAL |
| BOOT-4 | KAT (Known Answer Test) — AES-256-GCM, SM4-GCM, AES-256-CBC, SM4-CBC, HMAC-SHA-256, HKDF | FATAL |
| BOOT-8 | Vault reachability | RECOVERABLE |
| BOOT-9 | KMS reachability (wrap/unwrap canary key) | RECOVERABLE |
| BOOT-10 | Canary encrypt/decrypt roundtrip | FATAL |

### Actuator Endpoints

When Spring Boot Actuator is on the classpath:

- `GET /actuator/lclhealth` — bootstrap status, SDK version, component health
- `GET /actuator/lclkat` — KAT results with per-algorithm timing
- `POST /actuator/lclkat` — on-demand KAT re-run

### Configuration

```yaml
lightcrypto:
  runtime:
    bootstrap-enabled: true   # enable/disable bootstrap diagnostics
    bootstrap-timeout: 15s    # max time for full bootstrap sequence
```

## Examples

See [lcl-examples](lcl-examples/) for runnable demos:

```bash
# Spring Boot 3.x
cd lcl-examples/basic-crud
mvn spring-boot:run

# Spring Boot 4.x (requires MongoDB — inject URI via env)
cd lcl-examples/basic-crud-v4
set MONGODB_URI=mongodb://user:pass@host:27017/db?authSource=admin   # Windows
export MONGODB_URI=mongodb://user:pass@host:27017/db?authSource=admin # Linux/macOS
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
|- lcl-adapter-mongodb/                  # MongoDB storage adapter — Spring Boot 3.x
|- lcl-adapter-mongodb-v4/               # MongoDB storage adapter — Spring Boot 4.x (ValueExpressionDelegate)
|- lcl-spring-boot-starter/              # Core starter (annotation-driven encryption)
|- lcl-provider-azure-kms/               # Azure Key Vault CMK provider
|- lcl-provider-alibaba-kms/             # Alibaba Cloud KMS CMK provider
|- lcl-examples/                         # Example applications
|  |- basic-crud/                        #   Spring Boot 3.x
|  |- basic-crud-v4/                     #   Spring Boot 4.x
|  |- azure-keyvault/
|  |- alibaba-kms/
|  `- observability/
|- vectors/                              # Golden test vectors (cross-language verification)
`- docs/
```

## License

[Apache License 2.0](LICENSE)
