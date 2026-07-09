# Configuration

This document contains the full configuration reference for LCL.

## Core Properties

| Property | Type | Default | Description |
|---|---|---|---|
| `lcl.crypto.enabled` | `boolean` | `true` | Enable/disable encryption globally |
| `lcl.crypto.cmk` | `String` | none | 64-hex-char CMK (32 bytes) for local symmetric provider |
| `lcl.crypto.algorithm` | `SymmetricAlgorithm` | `AES_256_GCM` | Global default algorithm for `@Encrypted` |
| `lcl.crypto.keyVaultDatabase` | `String` | app database | MongoDB database holding `__lcl_keyvault` |
| `lcl.crypto.autoInit` | `boolean` | `true` | Auto-create vault on first startup |

## Local Symmetric CMK

```yaml
lcl:
  crypto:
    cmk: ${LCL_CMK_HEX}
    enabled: true
    algorithm: AES_256_GCM
```

## Azure Key Vault

Configuration prefix: `lcl.crypto.azure`

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

Configuration prefix: `lcl.crypto.alibaba`

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

## Security Guidance

- Never commit credentials or raw CMK values.
- Use environment variables, K8s Secrets, or external config services.
- Rotate DEKs periodically with `keyVaultService.rotateDek(EntityClass.class)`.
