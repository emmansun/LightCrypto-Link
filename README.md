# LightCrypto-Link (LCL)

**A pure Java, lightweight application-level field encryption library (ALFE) for Spring Boot + MongoDB.**

Transparent encrypt/decrypt on write/read, HMAC blind index for exact-match queries, multi-DEK envelope encryption with key rotation support — all without libmongocrypt.

[![CI](https://github.com/emmansun/LightCrypto-Link/actions/workflows/ci.yml/badge.svg)](https://github.com/emmansun/LightCrypto-Link/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)

---

## Features

- **Transparent Encryption** — annotate a field with `@Encrypted`, done. No extra code.
- **Multiple Symmetric Algorithms** — choose per-field from AES-256-GCM, AES-256-CBC, SM4-GCM, SM4-CBC (via Bouncy Castle).
- **Blind Index Queries** — `findByPhone("138...")` works on encrypted fields via HMAC blind index.
- **Nested Object Encryption** — supports recursive encrypted fields in nested POJOs.
- **Collection Encryption** — supports `List`/`Set`/`Map` element-level encryption and blind-index queries on collection elements.
- **Whole-Object Encryption** — supports whole-object encryption for POJO field (`DOC`) and POJO collections/maps (`COL`/`MAP`).
- **Multi-DEK Envelope Encryption** — each entity class has its own DEK, independently wrapped by the CMK; supports key rotation with versioned DEK entries.
- **Type Preservation** — encrypts `String`, `Integer`, `Long`, `LocalDate`, `BigDecimal`, `byte[]`, enums, and more.
- **Pluggable CMK** — built-in local symmetric CMK; cloud KMS providers for Azure Key Vault and Alibaba Cloud KMS (asymmetric RSA-OAEP).
- **Zero libmongocrypt** — pure Java crypto via Bouncy Castle; no native driver dependencies.
- **Spring Boot Starter** — drop-in auto-configuration; works with standard `MongoRepository`.

## Quick Start

### 1. Add Dependency

For new projects, prefer the JDK-only starter (lighter dependency footprint):

```xml
<dependency>
  <groupId>io.github.emmansun</groupId>
  <artifactId>lightcrypto-link-spring-boot-starter-jdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

Or use the full starter directly:

```xml
<dependency>
    <groupId>io.github.emmansun</groupId>
    <artifactId>lightcrypto-link-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

The starter marks Bouncy Castle as optional transitively. If you need SM2/SM3/SM4 algorithms, add these explicitly in your application:

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

> Until published to Maven Central, consume from [GitHub Packages](https://github.com/emmansun/LightCrypto-Link/packages).

For cloud KMS integration, add the corresponding provider module:

```xml
<!-- Azure Key Vault -->
<dependency>
    <groupId>io.github.emmansun</groupId>
    <artifactId>lightcrypto-link-azure-kms</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Alibaba Cloud KMS -->
<dependency>
    <groupId>io.github.emmansun</groupId>
    <artifactId>lightcrypto-link-alibaba-kms</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure

**Local symmetric CMK (development / simple use cases):**

```yaml
lcl:
  crypto:
    cmk: a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2  # 64 hex chars = 32 bytes
    enabled: true
    algorithm: AES_256_GCM  # global default algorithm (optional)
```

**Azure Key Vault (production):**

```yaml
lcl:
  crypto:
    azure:
      vault-uri: ${AZURE_VAULT_URI}
      key-name: ${AZURE_KEY_NAME}
      tenant-id: ${AZURE_TENANT_ID}
      client-id: ${AZURE_CLIENT_ID}
      client-secret: ${AZURE_CLIENT_SECRET}
```

**Alibaba Cloud KMS (production):**

```yaml
lcl:
  crypto:
    alibaba:
      access-key-id: ${ALIBABA_AK_ID}
      access-key-secret: ${ALIBABA_AK_SECRET}
      endpoint: ${ALIBABA_KMS_ENDPOINT:kms.cn-shenzhen.aliyuncs.com}
      key-id: ${ALIBABA_KMS_KEY_ID}
```

> **Production**: always inject secrets via environment variables, K8s Secrets, or Config Service. Never commit them.

### 3. Annotate

```java
@Document
public class User {
    @Id private String id;
    private String name;                         // plain text

    @Encrypted(blindIndex = true)
    private String phone;                        // encrypted (global default) + queryable

    @Encrypted(algorithm = SymmetricAlgorithm.SM4_GCM)
    private String idCard;                       // encrypted with SM4-GCM (per-field override)

    @Encrypted
    private Integer age;                         // encrypted, no query
}

@Document
public class AdvancedUser {
  @Id private String id;

  private Address homeAddress;             // nested POJO

  @Encrypted(blindIndex = true)
  private List<String> tags;               // element-level encryption + query

  @Encrypted
  private Map<String, String> settings;    // map value encryption

  @Encrypted(mode = EncryptionMode.WHOLE)
  private List<String> secureTags;          // whole simple list encryption (_t = "COL")

  @Encrypted(mode = EncryptionMode.WHOLE)
  private Map<String, String> secureSettings; // whole simple map encryption (_t = "MAP")

  @Encrypted
  private List<WholeAddress> secureAddresses; // whole-collection encryption (_t = "COL")

  public static class Address {
    @Encrypted
    private String street;               // recursive nested field encryption
    private String city;
  }

  public static class WholeAddress {
    private String street;
    private String city;
  }
}
```

> `@Encrypted` without `algorithm` uses the global default from `lcl.crypto.algorithm` (default: `AES_256_GCM`).
> Override per-field with `algorithm = SymmetricAlgorithm.XXX`.

**Supported algorithms:**

| Algorithm | Key Size | IV Size | Mode | Provider |
|---|---|---|---|---|
| `AES_256_GCM` *(global default)* | 32 bytes | 12 bytes | GCM/NoPadding | JDK |
| `AES_256_CBC` | 32 bytes | 16 bytes | CBC/PKCS5Padding | JDK |
| `SM4_GCM` | 16 bytes* | 12 bytes | GCM/NoPadding | Bouncy Castle |
| `SM4_CBC` | 16 bytes* | 16 bytes | CBC/PKCS5Padding | Bouncy Castle |

> *SM4 uses the first 16 bytes of the 32-byte DEK.
>
> Change the global default via `lcl.crypto.algorithm`, or override per-field with `@Encrypted(algorithm = ...)`.

### 4. Use Normally

```java
// Save — phone, idCard and age are encrypted transparently
userRepository.save(user);

// Read — decrypted automatically (algorithm detected from sub-document `_a` tag)
User u = userRepository.findById(id).orElseThrow();

// Query — blind index intercepts findByPhone
User found = userRepository.findByPhone("13800138001");

// Query on encrypted collection element via blind index rewrite
AdvancedUser au = advancedUserRepository.findByTagsContaining("java");
```

### 5. Programmatic API (Optional)

Use `ProgrammaticCryptoService` when annotation-driven persistence flow is not available
(for example: DTO/message encryption, migration scripts, native query results).

```java
@Service
public class SensitivePayloadService {

  private final ProgrammaticCryptoService programmaticCryptoService;

  public SensitivePayloadService(ProgrammaticCryptoService programmaticCryptoService) {
    this.programmaticCryptoService = programmaticCryptoService;
  }

  public Document encryptPhone(String phone) {
    // Key scope determines which entity vault/DEK is used.
    return programmaticCryptoService.encryptValue(phone, User.class);
  }

  public String decryptPhone(Document encryptedSubDoc) {
    return (String) programmaticCryptoService.decryptValue(encryptedSubDoc);
  }

  public Document decryptRawUser(Document rawUserDoc) {
    // In-place decryption of all @Encrypted fields for User mapping metadata.
    return programmaticCryptoService.decryptDocument(rawUserDoc, User.class);
  }
}
```

Encrypted value format remains consistent with repository flow (`_e`, `_k`, `_a`, `_t`, `c`).

## Nested and Collection Encryption Behavior

| Field pattern | Storage behavior |
|---|---|
| `@Encrypted String phone` | Scalar encrypted sub-document |
| `List<Address>` with `Address.street @Encrypted` | Recursive element traversal; encrypts nested leaf fields |
| `@Encrypted List<String> tags` | Element-level encrypted BSON Array |
| `@Encrypted Map<String,String> settings` | Encrypted BSON Document values |
| `@Encrypted Address address` | Whole object encrypted as `_t: "DOC"` |
| `@Encrypted List<Address> addresses` | Whole collection encrypted as `_t: "COL"` |
| `@Encrypted Map<String, Address> contacts` | Whole map encrypted as `_t: "MAP"` |

> Note: whole-object mode does not support `blindIndex = true`, and cannot be mixed with nested `@Encrypted` fields in the same object graph.

## Mode Selection Guide

Use explicit mode when you want deterministic behavior on collection/map fields.

| Goal | Recommended modeling |
|---|---|
| Need exact-match query (blind index) | `@Encrypted(blindIndex = true)` element-level mode |
| Need query on collection elements | Keep default element-level mode, for example `@Encrypted List<String> tags` |
| No query, maximize confidentiality for whole container | `@Encrypted(mode = WHOLE)` on `List<T>` / `Map<String, T>` |

`mode = WHOLE` is supported for both POJO and simple-value collections/maps (`List<String>`, `Set<Integer>`, `Map<String, String>`).

## Configuration Reference

| Property | Type | Default | Description |
|---|---|---|---|
| `lcl.crypto.enabled` | `boolean` | `true` | Enable/disable encryption globally |
| `lcl.crypto.cmk` | `String` | — | 64-hex-char CMK (32 bytes) for local symmetric provider |
| `lcl.crypto.keyVaultDatabase` | `String` | *(app db)* | MongoDB database for Key Vault collection |
| `lcl.crypto.autoInit` | `boolean` | `true` | Auto-create vault on first startup |

## Architecture

```
┌─────────────┐    write     ┌──────────────────┐   wrap DEK   ┌────────────┐
│  Application ├────────────►│  LCL Interceptor  ├────────────►│  Key Vault  │
│  (your code) │    read     │  (Save/Converter) │   unwrap    │  (MongoDB)  │
└─────────────┘              └────────┬──────────┘             └────────────┘
                                      │
                              AES-256-GCM/CBC
                              SM4-GCM/CBC
                              encrypt/decrypt
                                      │
                               ┌──────▼──────┐
                               │   MongoDB    │
                               │  (encrypted  │
                               │   documents) │
                               └─────────────┘
```

### Envelope Encryption

1. On first write for an entity class, LCL generates a random **DEK** (Data Encryption Key) and HMAC key.
2. Both keys are **wrapped** (encrypted) by the CMK and stored in the Key Vault collection (`__lcl_keyvault`).
3. Field values are encrypted using the algorithm specified in `@Encrypted` (default: **AES-256-GCM**) with the DEK.
4. Each encrypted field stores a sub-document containing ciphertext, type marker, key version ID (`_k`), and algorithm tag (`_a`).

### Sub-Document Format

Each encrypted field is stored as a BSON sub-document:

```json
{
  "_k": "v1-a3b2c1d4",       // DEK version ID
  "_a": "AES_256_GCM",       // symmetric algorithm used
  "_e": 1,                    // encryption marker
  "_t": "STR",              // type marker (e.g. STR/INT/LDATE/ENUM...)
  "c": BinData(0, "..."),    // ciphertext
  "b": "base64-hmac"         // blind index (optional)
}
```

The `_a` tag enables **backward compatibility**: if absent, the reader defaults to `AES_256_GCM` (legacy documents).

### Storage Examples (DOC/COL/MAP)

Whole-object encryption uses the same envelope fields (`_k`, `_a`, `_e`) and distinguishes payload shape by `_t`.

#### DOC: whole POJO field

```json
{
  "address": {
    "_k": "v2-9f8e7d6c",
    "_a": "AES_256_GCM",
    "_e": 1,
    "_t": "DOC",
    "c": BinData(0, "...ciphertext...")
  }
}
```

#### COL: whole collection field

```json
{
  "secureAddresses": {
    "_k": "v2-9f8e7d6c",
    "_a": "AES_256_GCM",
    "_e": 1,
    "_t": "COL",
    "c": BinData(0, "...ciphertext...")
  }
}
```

#### MAP: whole map field

```json
{
  "contacts": {
    "_k": "v2-9f8e7d6c",
    "_a": "AES_256_GCM",
    "_e": 1,
    "_t": "MAP",
    "c": BinData(0, "...ciphertext...")
  }
}
```

For scalar element-level encryption (for example `String` list elements or map `String` values), `_t` remains scalar types such as `STR`, `INT`, `LDATE`, etc.

### DEK Versioning & Rotation

LCL uses a **per-entity-class** DEK architecture:

- Each entity class with `@Encrypted` fields has its own vault document (e.g., `lcl-dek-User`).
- Each vault maintains a `keys[]` array with versioned DEK entries (`kid` = `v1-a3b2c1d4`, `v2-...`, etc.).
- Each algorithm computes an independent **KCV** (Key Check Value) for verification.
- On **write**, the active DEK version is used for encryption; the `kid` is stored in each field's `_k` sub-document.
- On **read**, the `kid` from the stored sub-document is used to look up the correct DEK version for decryption.

**Key rotation** is performed by calling `KeyVaultService.rotateKey(EntityClass.class)`:

1. The current active DEK is marked as `ROTATED` (kept in vault for decrypting old data).
2. A new DEK/HMAC key pair is generated with an incremented version (e.g., `v2-...`).
3. The new entry becomes the active `kid`.
4. Subsequent writes use the new DEK. Existing ciphertext encrypted with old versions is **re-encrypted on the next write** (save), not on read.

### Blind Index

When `@Encrypted(blindIndex = true)` is set:
- An HMAC-SHA256 hash is computed from the plaintext + field name.
- The hash is Base64-encoded and stored as a String in the `b` field.
- `findByPhone(...)` is rewritten to query the HMAC index — no decryption needed.

Migration note for existing plaintext historical data:
- See [docs/migration/introduce-lcl-to-existing-plaintext-data.md](docs/migration/introduce-lcl-to-existing-plaintext-data.md)

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
public interface CmkProvider {
    String getProviderId();           // unique identifier
    String getPublicReference();      // a non-secret public reference for the CMK used by this provider
    WrappedKey wrap(byte[] key);      // encrypt a DEK with the CMK
    byte[] unwrap(WrappedKey wrapped); // decrypt a wrapped DEK
}
```

Built-in providers:
- **LocalSymmetricCmkProvider** — AES-256-GCM wrap/unwrap (configured via `lcl.crypto.cmk`)
- **AzureKeyVaultCmkProvider** — local RSA-OAEP wrap + remote Key Vault unwrap (configured via `lcl.crypto.azure.*`)
- **AlibabaKmsCmkProvider** — local RSA-OAEP wrap + remote KMS unwrap (configured via `lcl.crypto.alibaba.*`)

## Examples

See [lightcrypto-link-examples](lightcrypto-link-examples/) for runnable demos:

```bash
# Basic CRUD with local symmetric CMK
cd lightcrypto-link-examples/basic-crud
mvn spring-boot:run

# Azure Key Vault integration
cd lightcrypto-link-examples/azure-keyvault
mvn spring-boot:run

# Alibaba Cloud KMS integration
cd lightcrypto-link-examples/alibaba-kms
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
├── lightcrypto-link-spring-boot-starter-jdk/ # JDK-only starter (lightweight)
├── lightcrypto-link-azure-kms/             # Azure Key Vault CMK Provider
├── lightcrypto-link-alibaba-kms/           # Alibaba Cloud KMS CMK Provider
├── lightcrypto-link-examples/              # Example applications
│   ├── basic-crud/                         # Minimal CRUD demo (local CMK)
│   ├── azure-keyvault/                     # Azure Key Vault demo
│   └── alibaba-kms/                        # Alibaba Cloud KMS demo
└── pom.xml                                 # Parent POM
```

## License

[Apache License 2.0](LICENSE)
