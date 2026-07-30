# Cross-CMK Provider Migration

This guide describes how to migrate vault key material from one CMK provider to another (e.g., `LOCAL_SYMMETRIC` → `azure-keyvault`) without re-encrypting any business data.

## Architecture

LCL uses envelope encryption:

```
CMK (provider-managed) → wraps → DEK/HMAC keys (vault) → encrypts → business data
```

Switching CMK providers only requires **re-wrapping** the vault key material. Business data, wire format, and blind index values remain untouched.

```
┌────────────────────────────────────────────────────────────┐
│                    Re-wrap Flow                             │
├────────────────────────────────────────────────────────────┤
│  1. Load VaultDocument (all key entries)                   │
│  2. Unwrap DEK/HMAC with CURRENT provider                  │
│  3. Verify KCV + binding invariance                        │
│  4. Re-wrap DEK/HMAC with TARGET provider                  │
│  5. Post-rewrap roundtrip verification                     │
│  6. Persist atomically (optimistic lock)                   │
│  7. Evict DEK cache                                        │
└────────────────────────────────────────────────────────────┘
```

## Scenario Overview

| Scenario | Custom Code? | Restarts | Key Point |
|---|---|---|---|
| A: LOCAL → Alibaba KMS | No | 2 | Pure config, auto-coexistence |
| B: LOCAL → Azure Key Vault | No | 2 | Pure config, auto-coexistence |
| C: Azure → Alibaba (Cloud→Cloud) | Yes (`@Primary` + named bean) | 2 | No deterministic primary between clouds |
| D: LOCAL → LOCAL (key change) | Yes (simple bean + `target-bean-name`) | 2 | No wrapper needed, publicReference auto-differs |
| E: Cloud same-provider key change | Yes (simple bean + `target-bean-name`) | 2 | Same as D, cloud version |

## Prerequisites

- [ ] Both the **source** and **target** CMK providers are configured and reachable.
- [ ] The source provider can successfully unwrap existing vault keys (verify via normal app operation).
- [ ] The target provider can perform wrap/unwrap roundtrip (the runner validates this in dry-run mode).
- [ ] You have a backup of the `__lcl_keyvault` collection (or equivalent vault store).
- [ ] Application writes can be briefly paused (seconds-level for typical namespace counts).

## Configuration

### How Provider Resolution Works

LCL uses `CmkProvider` beans for vault key wrapping/unwrapping:

- **Source provider**: the primary `CmkProvider` bean injected into `KeyVaultService` (used to unwrap existing vault keys).
- **Target provider**: resolved by the runner with three-level priority:
  1. `target-bean-name` — direct Spring bean name lookup (highest priority)
  2. `target-provider-id` + `target-public-reference` — dual match
  3. `target-provider-id` alone — single match (backward compatible)

Provider bean registration rules:

| Deployment | Registered Beans | KeyVaultService uses |
|---|---|---|
| LOCAL only | `local-symmetric` | `local-symmetric` |
| Cloud only (e.g., ALI) | `alibaba-kms` | `alibaba-kms` |
| LOCAL + Cloud (migration) | `local-symmetric` + `alibaba-kms` | `local-symmetric` (source) |

When both LOCAL and a cloud module are configured, the starter creates the LOCAL provider as the primary bean, and the cloud module registers its provider as an additional non-primary bean. Both are available for migration.

### Provider IDs Reference

| Provider Module | `getProviderId()` | Config Prefix |
|---|---|---|
| LocalSymmetricCmkProvider | `local-symmetric` | `lightcrypto.kms.providers[type=LOCAL_SYMMETRIC]` |
| AlibabaKmsCmkProvider | `alibaba-kms` | `lcl.crypto.alibaba.*` |
| AzureKeyVaultCmkProvider | `azure-keyvault` | `lcl.crypto.azure.*` |

### Migration Properties

```yaml
lightcrypto:
  migration:
    rewrap:
      enabled: true
      dry-run: true                    # Step 1: validate only
      target-provider-id: alibaba-kms  # Cross-type migration
      # target-bean-name: myTargetBean # Same-type key rotation (highest priority)
      # target-public-reference: ...   # Optional disambiguation
```

## Migration Scenarios

### Scenario A: LOCAL_SYMMETRIC → Alibaba Cloud KMS

The most common migration: development/evaluation → production cloud KMS.

**Dependencies**: `lcl-spring-boot-starter` + `lcl-provider-alibaba-kms` + `lcl-adapter-mongodb`

```yaml
lightcrypto:
  kms:
    providers:
      - id: local
        type: LOCAL_SYMMETRIC
        key-hex: ${LCL_CMK_HEX}  # Current CMK (source)
  migration:
    rewrap:
      enabled: true
      dry-run: true  # Set to false for live migration
      target-provider-id: alibaba-kms

lcl:
  crypto:
    alibaba:
      region-id: cn-shenzhen
      endpoint: ${ALIBABA_KMS_ENDPOINT}
      key-id: ${ALIBABA_KMS_KEY_ID}
      access-key-id: ${ALIBABA_AK_ID}
      access-key-secret: ${ALIBABA_AK_SECRET}
      # mode: ASYMMETRIC (default) or SYMMETRIC
```

**What happens**:
1. Starter creates `local-symmetric` CmkProvider (primary) → KeyVaultService uses it to unwrap existing vaults.
2. Alibaba module detects `lcl.crypto.alibaba.key-id` is set AND another CmkProvider exists → registers `alibaba-kms` as non-primary bean.
3. Runner resolves `alibaba-kms` from bean list → performs re-wrap.

**After migration completes**, remove LOCAL config and switch to cloud-only:

```yaml
lightcrypto:
  kms:
    providers: []  # Remove LOCAL_SYMMETRIC entry
  migration:
    rewrap:
      enabled: false

lcl:
  crypto:
    alibaba:
      region-id: cn-shenzhen
      endpoint: ${ALIBABA_KMS_ENDPOINT}
      key-id: ${ALIBABA_KMS_KEY_ID}
      access-key-id: ${ALIBABA_AK_ID}
      access-key-secret: ${ALIBABA_AK_SECRET}
```

Now the Alibaba module creates the primary CmkProvider (no other provider exists).

### Scenario B: LOCAL_SYMMETRIC → Azure Key Vault

**Dependencies**: `lcl-spring-boot-starter` + `lcl-provider-azure-kms` + `lcl-adapter-mongodb`

```yaml
lightcrypto:
  kms:
    providers:
      - id: local
        type: LOCAL_SYMMETRIC
        key-hex: ${LCL_CMK_HEX}
  migration:
    rewrap:
      enabled: true
      dry-run: true
      target-provider-id: azure-keyvault

lcl:
  crypto:
    azure:
      vault-uri: ${AZURE_VAULT_URI}
      key-name: ${AZURE_KEY_NAME}
      tenant-id: ${AZURE_TENANT_ID}
      client-id: ${AZURE_CLIENT_ID}
      client-secret: ${AZURE_CLIENT_SECRET}
```

### Scenario C: Azure Key Vault → Alibaba Cloud KMS (Cloud → Cloud)

Cloud-to-cloud migration **requires a custom `@Configuration` class**. Reason: the starter only makes LOCAL_SYMMETRIC primary; when no LOCAL entry exists and two cloud modules are both configured, neither has deterministic primary precedence.

**Approach**: Define both providers explicitly — source as `@Primary`, target as named non-primary bean:

```java
@Configuration
public class CloudToCloudMigrationConfig {

    /**
     * Source provider (primary): KeyVaultService uses this to unwrap existing vault keys.
     */
    @Bean
    @Primary
    public CmkProvider azureSourceProvider(AzureKeyVaultCmkProperties props, KeyClient keyClient) {
        KeyVaultKey key = keyClient.getKey(props.getKeyName());
        String version = key.getProperties().getVersion();
        PublicKey pub = JsonWebKeyToPublicKey.convert(key.getKey());
        return new AzureKeyVaultCmkProvider(pub, keyClient, "RSA-OAEP-256", props.getKeyName(), version);
    }

    /**
     * Target provider (non-primary): rewrap runner resolves this by bean name.
     */
    @Bean("alibabaMigrationTarget")
    public CmkProvider alibabaTargetProvider(AlibabaKmsCmkProperties props,
                                             com.aliyun.kms20160120.Client kmsClient) {
        // For SYMMETRIC mode:
        return new AlibabaKmsCmkProvider(props.getKeyId(), props.getEncryptionContext(), kmsClient);
        // For ASYMMETRIC mode, resolve keyVersionId + publicKey first (see AlibabaKmsCmkAutoConfiguration)
    }
}
```

**Configuration** (cloud module auto-configs still create the `KeyClient` / KMS `Client` beans):

```yaml
lightcrypto:
  kms:
    providers: []  # No LOCAL entry — avoid interfering with custom beans
  migration:
    rewrap:
      enabled: true
      dry-run: true
      target-bean-name: alibabaMigrationTarget  # Resolve by bean name

lcl:
  crypto:
    azure:
      vault-uri: ${AZURE_VAULT_URI}
      key-name: ${AZURE_KEY_NAME}
      tenant-id: ${AZURE_TENANT_ID}
      client-id: ${AZURE_CLIENT_ID}
      client-secret: ${AZURE_CLIENT_SECRET}
    alibaba:
      region-id: cn-shenzhen
      endpoint: ${ALIBABA_KMS_ENDPOINT}
      key-id: ${ALIBABA_KMS_KEY_ID}
      access-key-id: ${ALIBABA_AK_ID}
      access-key-secret: ${ALIBABA_AK_SECRET}
```

> **Why not two-hop (AZURE→LOCAL→ALI)?** If LOCAL is configured, it becomes the primary bean (source). KeyVaultService would try to unwrap Azure-wrapped vault keys with the LOCAL key — immediate cryptographic failure. You'd still need a custom `@Primary` bean for Azure, making two-hop strictly more work than the direct approach.

**After migration completes**: remove the custom `@Configuration` class, remove Azure config, keep only Alibaba config. The Alibaba module auto-configures as the sole primary provider.

### Scenario D: LOCAL_SYMMETRIC → LOCAL_SYMMETRIC (key change)

Rotating the CMK itself (e.g., compromised key, policy requirement). Requires a **custom bean** for the new key, resolved via `target-bean-name`:

```java
@Configuration
public class LocalKeyMigrationConfig {

    @Bean("newLocalCmkProvider")
    public CmkProvider newLocalCmkProvider() {
        byte[] newCmk = HexFormat.of().parseHex(System.getenv("NEW_LCL_CMK_HEX"));
        return new LocalSymmetricCmkProvider(newCmk);  // No wrapper needed!
    }
}
```

```yaml
lightcrypto:
  kms:
    providers:
      - id: local
        type: LOCAL_SYMMETRIC
        key-hex: ${OLD_LCL_CMK_HEX}  # Current key (source, primary)
  migration:
    rewrap:
      enabled: true
      dry-run: true
      target-bean-name: newLocalCmkProvider  # Direct bean name resolution
```

**Why this works**: The same-provider skip check compares BOTH `providerId` AND `publicReference`. Old and new LOCAL keys have the same providerId (`"local-symmetric"`) but different publicReferences (different SHA-256 fingerprints), so re-wrap proceeds correctly. After re-wrap, vault metadata stores `cmkProvider="local-symmetric"` + `cmkId=<new fingerprint>` — fully consistent.

**After migration**: remove the custom `@Configuration`, update `key-hex` to the new key. Restart.

### Scenario E: Cloud KMS CMK Key Change (same provider, different key)

Applies to: Alibaba KMS key rotation, Azure Key Vault key replacement, etc.

Same approach as Scenario D — use `target-bean-name` to resolve the new-key provider:

**Alibaba KMS example**:

```java
@Configuration
public class AliKeyRotationConfig {

    @Bean("newAliKmsTarget")
    public CmkProvider newAliKmsProvider(com.aliyun.kms20160120.Client kmsClient) {
        String newKeyId = System.getenv("NEW_ALIBABA_KMS_KEY_ID");
        return new AlibabaKmsCmkProvider(newKeyId, Map.of(), kmsClient);  // No wrapper!
    }
}
```

```yaml
lightcrypto:
  migration:
    rewrap:
      enabled: true
      dry-run: true
      target-bean-name: newAliKmsTarget

lcl:
  crypto:
    alibaba:
      key-id: ${OLD_ALIBABA_KMS_KEY_ID}  # Current key (source, auto-config primary)
      # ... credentials (shared between old and new key) ...
```

**Azure Key Vault example**:

```java
@Bean("newAzureTarget")
public CmkProvider newAzureProvider(KeyClient keyClient) {
    String newKeyName = System.getenv("NEW_AZURE_KEY_NAME");
    KeyVaultKey key = keyClient.getKey(newKeyName);
    String version = key.getProperties().getVersion();
    PublicKey pub = JsonWebKeyToPublicKey.convert(key.getKey());
    return new AzureKeyVaultCmkProvider(pub, keyClient, "RSA-OAEP-256", newKeyName, version);
}
```

Then set `target-bean-name: newAzureTarget`.

**After migration**: remove the custom `@Configuration`, update `lcl.crypto.alibaba.key-id` (or `lcl.crypto.azure.key-name`) to the new key. Restart. Vault metadata remains consistent (`cmkProvider="alibaba-kms"`, `cmkId=<new key reference>`).

> **Alternative**: For cloud KMS where the new key's public reference is a known config value (e.g., keyId), you can use `target-provider-id` + `target-public-reference` instead of `target-bean-name`:
> ```yaml
> lightcrypto:
>   migration:
>     rewrap:
>       target-provider-id: alibaba-kms
>       target-public-reference: ${NEW_ALIBABA_KMS_KEY_ID}
> ```

---

## Provider ID Validation at Runtime

The `VaultDocument.cmkProvider` field (stored provider ID) is **NOT validated** during normal encrypt/decrypt operations. The runtime safety model:

| Layer | Mechanism | Failure mode |
|---|---|---|
| Unwrap | CMK cryptographic operation | Wrong key → GCM tag mismatch / RSA padding error |
| KCV | 3-byte key check value comparison | Wrong DEK → `FatalCryptoException` |
| Binding | HMAC-DEK pair hash | Swapped keys → `FatalCryptoException` |

The stored `cmkProvider` + `cmkId` fields are used ONLY for:
1. Re-wrap same-provider skip logic (`rewrapVault` compares target providerId + publicReference vs stored values)
2. Dry-run diagnostic reporting
3. Audit / observability

This means: after migration, if you accidentally revert to the old provider config, the application will **fail fast** at startup (KCV mismatch), not silently produce wrong plaintext.

---

## Step-by-Step Procedure

### Step 1: Dry-Run Validation

Deploy with `dry-run: true`. The runner will:
1. Load all vault documents.
2. Perform a canary wrap/unwrap roundtrip with the target provider.
3. Log which namespaces would be re-wrapped and which would be skipped.
4. **NOT modify any vault document.**

Check application logs for `[REWRAP] DRY-RUN` messages. Verify:
- Target provider canary roundtrip succeeded.
- All expected namespaces are listed.
- No errors reported.

### Step 2: Live Re-wrap

Once dry-run passes, switch to live mode:

```yaml
lightcrypto:
  migration:
    rewrap:
      enabled: true
      dry-run: false
      target-provider-id: azure-keyvault
```

Deploy and restart. The runner will:
1. Iterate all namespaces via `VaultStore.loadAll()`.
2. Re-wrap each namespace atomically with per-namespace error isolation.
3. Log a summary: total, success, failed counts.

Monitor logs for `[REWRAP] Live re-wrap complete` and verify `failed=0`.

### Step 3: Configuration Switch

After successful re-wrap, update configuration to use the new provider as the sole primary:

```yaml
lightcrypto:
  kms:
    providers: []  # Remove LOCAL entry (if migrating from LOCAL)
  migration:
    rewrap:
      enabled: false  # Disable the runner

# Cloud provider config remains (e.g., lcl.crypto.alibaba.* or lcl.crypto.azure.*)
# The cloud module now auto-configures as the sole primary CmkProvider.
```

Restart the application. All new vault operations will use the target provider.

## Transition Window

- During re-wrap, each namespace is updated atomically. There is no partial state within a namespace.
- If the application runs in a cluster, ensure only **one instance** runs the re-wrap (use `enabled=true` on one node only, or use a distributed lock).
- Concurrent DEK rotation during re-wrap will cause an optimistic lock conflict — the re-wrap fails cleanly for that namespace. Retry after rotation completes.

## Rollback Strategy

If issues arise after partial migration:

1. **Un-migrated namespaces** still use the old provider — no action needed.
2. **Already-migrated namespaces** require a reverse re-wrap:
   - Set `target-bean-name` (or `target-provider-id`) to the old provider.
   - Ensure the old provider bean is still registered.
   - Run the re-wrap again (dry-run first, then live).
3. Revert the main KMS configuration to the old provider.

Vaults are independent — partial migration is safe. Each VaultDocument stores `cmkProvider` + `cmkId` metadata for diagnostics and re-wrap skip logic; runtime safety is guaranteed by cryptographic verification (KCV + binding), not by provider ID matching.

## Programmatic API

For custom tooling or scheduled jobs:

```java
@Autowired
private KeyVaultService keyVaultService;

@Autowired
private CmkProvider targetProvider;

// Single namespace
RewrapResult result = keyVaultService.rewrapVault("default.default.User#phone", targetProvider);

// All namespaces
List<RewrapResult> results = keyVaultService.rewrapAllVaults(targetProvider);
```

## Observability Events

| Event | Tier | Emitted When |
|---|---|---|
| `lcl.rewrap.namespace.completed` | L2 | Per-namespace re-wrap succeeds |
| `lcl.rewrap.namespace.failed` | L2 | Per-namespace re-wrap fails |
| `lcl.rewrap.batch.completed` | L2 | `rewrapAllVaults` finishes |
| `lcl.rewrap.runner.completed` | L2 | CommandLineRunner finishes |

## Checklist

- [ ] Backup vault store
- [ ] Both providers configured and reachable
- [ ] Dry-run passes with no errors
- [ ] Live re-wrap completes with `failed=0`
- [ ] Application restarts cleanly with new provider config
- [ ] Encrypt/decrypt operations verified post-migration
- [ ] Blind index queries verified post-migration
- [ ] Runner disabled (`enabled: false`) after completion
- [ ] Old provider credentials removed (after confirming no rollback needed)

## Related Documentation

- [Key Lifecycle Guide](../key-lifecycle.md) — Understanding CMK re-wrap vs DEK rotation vs DEK re-encryption
