# LightCrypto-Link (LCL)

**A pure Java, lightweight application-level field encryption library (ALFE) for Spring Boot + MongoDB.**

Transparent encrypt/decrypt on write/read, HMAC blind index for exact-match queries, multi-DEK envelope encryption with automatic key rotation — all without libmongocrypt.

[![CI](https://github.com/emmansun/LightCrypto-Link/actions/workflows/ci.yml/badge.svg)](https://github.com/emmansun/LightCrypto-Link/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)

---

## Features

- **Transparent Encryption** — annotate a field with `@Encrypted`, done. No extra code.
- **Blind Index Queries** — `findByPhone("138...")` works on encrypted fields via HMAC blind index.
- **Multi-DEK Envelope Encryption** — each DEK is independently wrapped by the CMK; automatic rotation per field.
- **Type Preservation** — encrypts `String`, `Integer`, `Long`, `LocalDate`, `BigDecimal`, `byte[]`, enums, and more.
- **Pluggable CMK** — built-in local symmetric CMK; SPI ready for Azure Key Vault, Alibaba Cloud KMS, etc.
- **Zero libmongocrypt** — pure Java crypto via Bouncy Castle; no native driver dependencies.
- **Spring Boot Starter** — drop-in auto-configuration; works with standard `MongoRepository`.

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.lcl.crypto</groupId>
    <artifactId>lightcrypto-link-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

> Until published to Maven Central, consume from [GitHub Packages](https://github.com/emmansun/LightCrypto-Link/packages).

### 2. Configure

```yaml
lcl:
  crypto:
    cmk: a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2  # 64 hex chars = 32 bytes
    enabled: true
```

> **Production**: inject `lcl.crypto.cmk` via environment variable, K8s Secret, or Config Service.

### 3. Annotate

```java
@Document
public class User {
    @Id private String id;
    private String name;                         // plain text

    @Encrypted(blindIndex = true)
    private String phone;                        // encrypted + queryable

    @Encrypted
    private Integer age;                         // encrypted, no query
}
```

### 4. Use Normally

```java
// Save — phone and age are encrypted transparently
userRepository.save(user);

// Read — decrypted automatically
User u = userRepository.findById(id).orElseThrow();

// Query — blind index intercepts findByPhone
User found = userRepository.findByPhone("13800138001");
```

## Configuration Reference

| Property | Type | Default | Description |
|---|---|---|---|
| `lcl.crypto.enabled` | `boolean` | `true` | Enable/disable encryption globally |
| `lcl.crypto.cmk` | `String` | — | 64-hex-char CMK (32 bytes) |
| `lcl.crypto.keyVaultDatabase` | `String` | *(app db)* | MongoDB database for Key Vault collection |
| `lcl.crypto.autoInit` | `boolean` | `true` | Auto-create vault on first startup |

## Architecture

```
┌─────────────┐    write     ┌──────────────────┐   wrap DEK   ┌────────────┐
│  Application ├────────────►│  LCL Interceptor  ├────────────►│  Key Vault  │
│  (your code) │    read     │  (Save/Converter) │   unwrap    │  (MongoDB)  │
└─────────────┘              └────────┬──────────┘             └────────────┘
                                      │
                                 AES-256-GCM
                                 encrypt/decrypt
                                      │
                               ┌──────▼──────┐
                               │   MongoDB    │
                               │  (encrypted  │
                               │   documents) │
                               └─────────────┘
```

### Envelope Encryption

1. On first write to a field, LCL generates a random **DEK** (Data Encryption Key).
2. The DEK is **wrapped** (encrypted) by the CMK and stored in the Key Vault collection.
3. Field values are encrypted with **AES-256-GCM** using the DEK.
4. Each encrypted field stores a base64url-encoded ciphertext with version tag.

### DEK Rotation

LCL supports per-field DEK versioning. When the CMK is rotated, new writes use the latest DEK version while old ciphertexts are lazily re-encrypted on read.

### Blind Index

When `@Encrypted(blindIndex = true)` is set:
- An HMAC-SHA256 hash is computed from the plaintext + field salt.
- The hash is stored alongside the ciphertext.
- `findByPhone(...)` is rewritten to query the HMAC index — no decryption needed.

## Supported Types

| Java Type | Encrypted | Blind Index |
|---|:---:|:---:|
| `String` | yes | yes |
| `Integer`, `Long`, `Short`, `Byte` | yes | yes |
| `Float`, `Double`, `BigDecimal` | yes | yes |
| `Boolean` | yes | yes |
| `LocalDate`, `LocalDateTime` | yes | yes |
| `byte[]` | yes | yes |
| `Enum` | yes | yes |

## Custom CmkProvider

Implement `CmkProvider` to integrate with your KMS:

```java
@Bean
@ConditionalOnMissingBean
public CmkProvider cmkProvider(CryptoProperties properties) {
    return new AzureKeyVaultCmkProvider(properties.getCmk());
}
```

The `CmkProvider` SPI requires:
- `getProviderId()` — unique identifier
- `wrap(byte[])` — encrypt a DEK with the CMK
- `unwrap(WrappedKey)` — decrypt a wrapped DEK

## Examples

See [lightcrypto-link-examples/basic-crud](lightcrypto-link-examples/basic-crud/) for a runnable demo:

```bash
cd lightcrypto-link-examples/basic-crud
mvn spring-boot:run
```

## Building from Source

```bash
git clone https://github.com/emmansun/LightCrypto-Link.git
cd LightCrypto-Link
mvn clean verify
```

### Project Structure

```
LightCrypto-Link/
├── lightcrypto-link-spring-boot-starter/   # Library (Spring Boot starter)
├── lightcrypto-link-examples/              # Example applications
│   └── basic-crud/                         # Minimal CRUD demo
└── pom.xml                                 # Parent POM
```

## License

[Apache License 2.0](LICENSE)
