# LightCrypto-Link (LCL)

Pure Java, lightweight application-level field encryption for Spring Boot + MongoDB.

Transparent encrypt/decrypt on write/read, HMAC blind index for exact-match queries,
multi-DEK envelope encryption with key rotation support, and no libmongocrypt dependency.

[![CI](https://github.com/emmansun/LightCrypto-Link/actions/workflows/ci.yml/badge.svg)](https://github.com/emmansun/LightCrypto-Link/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/emmansun/LightCrypto-Link/graph/badge.svg?token=d6cfb4Z53D)](https://codecov.io/gh/emmansun/LightCrypto-Link)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
![Maven Central Version](https://img.shields.io/maven-central/v/io.github.emmansun/lightcrypto-link-spring-boot-starter)

---

## TL;DR

1. Add starter dependency.
2. Configure a CMK provider (`lcl.crypto.cmk` for local, or cloud KMS module).
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
- Per-entity multi-DEK vault with versioned `kid`
- Pluggable CMK providers (local, Azure Key Vault, Alibaba Cloud KMS)

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

Prefer JDK-only starter for lighter footprint:

```xml
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lightcrypto-link-spring-boot-starter-jdk</artifactId>
  <version>${lcl.version}</version>
</dependency>
```

Or full starter:

```xml
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lightcrypto-link-spring-boot-starter</artifactId>
  <version>${lcl.version}</version>
</dependency>
```

For SM2/SM3/SM4, add Bouncy Castle explicitly:

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk18on</artifactId>
  <version>1.84</version>
</dependency>
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcpkix-jdk18on</artifactId>
  <version>1.84</version>
</dependency>
```

Cloud KMS modules:

```xml
<!-- Azure Key Vault -->
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lightcrypto-link-azure-kms</artifactId>
  <version>${lcl.version}</version>
</dependency>

<!-- Alibaba Cloud KMS -->
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lightcrypto-link-alibaba-kms</artifactId>
  <version>${lcl.version}</version>
</dependency>
```

If you need snapshot builds, use GitHub Packages:
[https://github.com/emmansun/LightCrypto-Link/packages](https://github.com/emmansun/LightCrypto-Link/packages)

### 2. Configure

Local symmetric CMK example:

```yaml
lcl:
  crypto:
    cmk: a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2
    enabled: true
    algorithm: AES_256_GCM
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
keyVaultService.rotateDek(User.class);
```

For behavior details, see [docs/architecture.md](docs/architecture.md).

## Examples

See [lightcrypto-link-examples](lightcrypto-link-examples/) for runnable demos:

```bash
cd lightcrypto-link-examples/basic-crud
mvn spring-boot:run

cd lightcrypto-link-examples/azure-keyvault
mvn spring-boot:run

cd lightcrypto-link-examples/alibaba-kms
mvn spring-boot:run
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
|- lightcrypto-link-spi/                     # SPI contracts
|- lightcrypto-link-spring-boot-starter/     # Core starter
|- lightcrypto-link-spring-boot-starter-jdk/ # JDK-only starter
|- lightcrypto-link-azure-kms/               # Azure Key Vault provider
|- lightcrypto-link-alibaba-kms/             # Alibaba Cloud KMS provider
|- lightcrypto-link-examples/                # Example applications
|  |- basic-crud/
|  |- azure-keyvault/
|  `- alibaba-kms/
`- docs/
```

## License

[Apache License 2.0](LICENSE)
