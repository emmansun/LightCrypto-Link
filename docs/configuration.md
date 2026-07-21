# Configuration

This document contains the full configuration reference for LCL.

## Global Switch

| Property | Type | Default | Description |
|---|---|---|---|
| `lightcrypto.enabled` | `boolean` | `true` | Enable/disable encryption globally. When `false`, no LCL beans are registered. |

## Cryptography (`lightcrypto.cryptography.*`)

| Property | Type | Default | Description |
|---|---|---|---|
| `default-algorithm` | `SymmetricAlgorithm` | `AES_256_GCM` | Global default algorithm for `@Encrypted` fields that don't specify one |
| `allowed-algorithms` | `List<SymmetricAlgorithm>` | all four | Algorithms permitted for decryption of existing ciphertext |
| `require-aead` | `boolean` | `false` | When `true`, only AEAD algorithms (GCM modes) are allowed |

## Key Vault (`lightcrypto.keyvault.*`)

| Property | Type | Default | Description |
|---|---|---|---|
| `cache.ttl` | `Duration` | `PT1H` (1 hour) | TTL for the in-memory DEK/HMAC key cache. After expiry, keys are securely zeroed and reloaded. Set `PT0S` to disable caching. |
| `cache.max-entries` | `int` | `10000` | Maximum number of namespace entries in the DEK cache |

## KMS Providers (`lightcrypto.kms.*`)

| Property | Type | Default | Description |
|---|---|---|---|
| `providers` | `List<ProviderEntry>` | empty | List of CMK provider entries |

Each `ProviderEntry`:

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `String` | yes | Unique provider identifier (used in vault metadata) |
| `type` | `ProviderType` | yes | `LOCAL_SYMMETRIC`, `ALIYUN`, or `AZURE` |
| `key-hex` | `String` | conditional | 64-char hex string (32 bytes) — required for `LOCAL_SYMMETRIC` |
| `key-hex-file` | `String` | conditional | Path to a UTF-8 file containing the hex key (alternative to `key-hex`) |
| `config` | `Map<String,String>` | no | Provider-specific settings (reserved for future use) |

## Tenant (`lightcrypto.tenants.*`)

| Property | Type | Default | Description |
|---|---|---|---|
| `tenant` | `String` | `default` | Tenant segment of the namespace (multi-tenant isolation) |
| `realm` | `String` | `default` | Realm segment of the namespace (key domain isolation) |

## Runtime (`lightcrypto.runtime.*`)

| Property | Type | Default | Description |
|---|---|---|---|
| `spi-version` | `int` | `1` | SPI compatibility version |
| `mode` | `RuntimeMode` | `SPRING_BOOT` | `SPRING_BOOT`, `STANDALONE`, or `MIGRATION` |
| `strict-mode` | `boolean` | `true` | When `true`, fail fast on configuration issues |

## Adapter Configuration

LCL requires a storage adapter module on the classpath. The adapter provides `VaultStore`, `StorageAdapter`, and `QueryTransformer` beans.

### MongoDB Adapter (`lightcrypto.adapters.mongodb.*`)

Add the dependency:

```xml
<dependency>
    <groupId>io.github.emmansun</groupId>
    <artifactId>lcl-adapter-mongodb</artifactId>
</dependency>
```

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Enable/disable the MongoDB adapter |
| `key-vault-collection` | `String` | `__lcl_keyvault` | MongoDB collection name for key vault storage |
| `auto-init` | `boolean` | `true` | Auto-create vault on first startup |
| `key-vault-database` | `String` | app database | MongoDB database holding the key vault (optional) |

The adapter auto-configures when `MongoTemplate` is available:
- `MongoVaultStore` — persists key vaults in the configured collection
- `MongoStorageAdapter` — BSON `Document` format for encrypted fields
- `MongoQueryTransformer` — blind index query rewriting

All beans use `@ConditionalOnMissingBean`, so you can override any of them with custom implementations.

### Custom Adapter

To use a different database, implement the SPI interfaces from `lcl-spi`:
- `VaultStore` — key vault persistence
- `StorageAdapter` — encrypted field format
- `QueryTransformer` — blind index query rewriting

Register your implementations as Spring beans.

## Local Symmetric CMK

```yaml
lightcrypto:
  kms:
    providers:
      - id: local
        type: LOCAL_SYMMETRIC
        key-hex: ${LCL_CMK_HEX}
  cryptography:
    default-algorithm: AES_256_GCM
  keyvault:
    cache:
      ttl: PT1H  # DEK cache TTL (use PT0S to disable caching)
```

### Using key-hex-file

For sensitive environments, store the hex key in a file and reference it:

```yaml
lightcrypto:
  kms:
    providers:
      - id: local
        type: LOCAL_SYMMETRIC
        key-hex-file: /etc/lcl/cmk.hex
```

The file must contain exactly 64 hex characters (32 bytes). Leading/trailing whitespace is trimmed.

## Azure Key Vault

The Azure provider module uses its own configuration prefix (`lcl.crypto.azure`):

```yaml
lcl:
  crypto:
    azure:
      vault-uri: ${AZURE_VAULT_URI}
      key-name: ${AZURE_KEY_NAME}
      tenant-id: ${AZURE_TENANT_ID}
      client-id: ${AZURE_CLIENT_ID}
      client-secret: ${AZURE_CLIENT_SECRET}
      # algorithm: RSA
      # public-key: |-
      #   -----BEGIN PUBLIC KEY-----
      #   ...
      #   -----END PUBLIC KEY-----
```

Notes:
- `vault-uri` and `key-name` are required.
- If `tenant-id/client-id/client-secret` are all present, service principal auth is used.
- If none are set, `DefaultAzureCredential` is used.

## Alibaba Cloud KMS

The Alibaba provider module uses its own configuration prefix (`lcl.crypto.alibaba`):

```yaml
lcl:
  crypto:
    alibaba:
      region-id: cn-hangzhou
      endpoint: ${ALIBABA_KMS_ENDPOINT:kms.cn-shenzhen.aliyuncs.com}
      key-id: ${ALIBABA_KMS_KEY_ID}
      access-key-id: ${ALIBABA_AK_ID}
      access-key-secret: ${ALIBABA_AK_SECRET}
      # mode: SYMMETRIC | ASYMMETRIC, default is ASYMMETRIC  
      # key-version-id: optional
      # public-key: optional PEM
```

## Validation

LCL validates configuration at startup using JSR-380 (Bean Validation):

- `LOCAL_SYMMETRIC` providers must have either `key-hex` or `key-hex-file` set.
- `default-algorithm` must be present in `allowed-algorithms`.
- Provider `id` values must be unique across the list.

Invalid configuration causes the application to fail fast at startup.

## Security Guidance

- Never commit credentials or raw CMK values.
- Use environment variables, K8s Secrets, or external config services.
- Prefer `key-hex-file` over inline `key-hex` for production deployments.
- Rotate DEKs periodically with `keyVaultService.rotateDek(namespace)`.
