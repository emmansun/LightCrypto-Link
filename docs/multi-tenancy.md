# Multi-Tenancy Guide

LightCrypto-Link uses a **four-part namespace model** (`tenant.realm.entity#field`) to provide cryptographic isolation across organizational and environmental boundaries. This guide explains the design intent and recommended patterns for each multi-tenancy scenario.

## Namespace Model

Every encrypted field is bound to a unique namespace that determines which DEK/HMAC key pair encrypts it:

```
<tenant>.<realm>.<entity>#<field>
```

| Segment | Purpose | Granularity | Examples |
|---------|---------|-------------|----------|
| `tenant` | Organization/customer isolation boundary | Deployment-level | `acme-corp`, `default` |
| `realm` | Environment/domain isolation | Deployment-level | `production`, `staging` |
| `entity` | Data entity (collection) | Schema-level | `User`, `Order` |
| `field` | Encrypted field (supports dot-notation for nested paths) | Field-level | `phone`, `address.city` |

### Key Properties

- **Cryptographic isolation**: Different namespaces → different DEKs → complete isolation (including blind index non-correlation)
- **Self-describing ciphertext**: Wire Format V1 embeds the full namespace in every encrypted blob, so **decryption never requires external tenant context**
- **Blind index isolation**: HKDF-SHA256 derives a namespace-scoped HMAC key, preventing cross-tenant or cross-entity blind index correlation
- **Validation**: Segments allow `[a-zA-Z0-9_-]`; field allows additional `.` for nested paths; max 256 UTF-8 bytes canonical form

## Configuration

### Global Configuration

```yaml
lightcrypto:
  tenants:
    tenant: acme-corp      # Default: "default"
    realm: production      # Default: "default"
```

The `EntityMetadataCache` resolves each `@Encrypted` field's namespace as:

```
{tenant}.{realm}.{EntitySimpleName}#{fieldName}
```

For example, with `tenant=acme-corp`, `realm=production`, entity class `User`, field `phone`:
```
acme-corp.production.User#phone
```

### Namespace Resolution Rules

| Input Form | Example | Resolved Namespace |
|------------|---------|-------------------|
| Shorthand | `User#phone` | `default.default.User#phone` |
| Full form | `acme.prod.User#phone` | `acme.prod.User#phone` (explicit wins) |
| Two-segment (ambiguous) | `prod.User#phone` | **Rejected** (throws IllegalArgumentException) |

> **Note:** `ProgrammaticCryptoService` accepts explicit namespace strings, allowing per-request namespace resolution independent of global configuration.

## Multi-Tenancy Patterns

### Pattern 1: Deployment-per-Tenant (Recommended)

Each tenant runs a separate application instance (or Spring context) with its own `tenant`/`realm` configuration and MongoDB database.

```yaml
# application-acme.yml
lightcrypto:
  tenants:
    tenant: acme-corp
    realm: production
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/acme-db
```

**Isolation level**: Complete (separate databases, separate DEK vaults, separate namespaces).

**Advantages**:
- Zero code changes — `@Encrypted` annotations work as-is
- Full blind index support
- Independent key rotation per tenant

### Pattern 2: Shared Database, Namespace Isolation

Same database, but different tenant configurations produce different namespaces → different DEKs. Requires separate Spring contexts or profile-based configuration per tenant group.

```java
// Tenant A context
@Bean
public TenantProperties tenantPropertiesA() {
    TenantProperties props = new TenantProperties();
    props.setTenant("acme-corp");
    props.setRealm("production");
    return props;
}
```

**Isolation level**: Complete (same collection, but different DEKs per namespace).

**Limitation**: A single Spring context resolves one `TenantProperties` bean. True per-request tenant switching requires Pattern 3.

### Pattern 3: Row-Level Tenant (ProgrammaticCryptoService)

When the tenant identifier is stored as a document field (e.g., `doc.tenantId = "acme"`), use `ProgrammaticCryptoService` for per-record namespace resolution:

```java
@Service
public class TenantAwareUserService {

    private final ProgrammaticCryptoService cryptoService;
    private final MongoTemplate mongoTemplate;

    // ─── Write: encrypt with tenant-specific namespace ───
    public void saveUser(UserDto dto) {
        String namespace = dto.getTenantId() + ".production.User#phone";

        Document doc = new Document();
        doc.put("name", dto.getName());
        doc.put("tenantId", dto.getTenantId());
        doc.put("phone", cryptoService.encryptValue(dto.getPhone(), namespace));
        // → { _e: 1, _t: "STR", c: "<wire format blob with embedded namespace>" }

        mongoTemplate.getCollection("users").insertOne(doc);
    }

    // ─── Read: decrypt (no external tenant needed) ───
    public UserDto readUser(Document rawDoc) {
        Object decrypted = cryptoService.decryptValue(rawDoc.get("phone"));
        // Wire Format V1 blob embeds the full namespace — decryption is self-describing
        return new UserDto(rawDoc.getString("name"), (String) decrypted);
    }
}
```

**Isolation level**: Complete (different DEKs per tenant namespace).

**Limitation**: `ProgrammaticCryptoService` does not generate blind indexes during encryption. If blind-index exact-match queries are required for row-level multi-tenancy, consider Pattern 1 or 2 where the annotation-driven listener handles blind index generation automatically.

## Cross-Language Compatibility

The namespace is embedded in Wire Format V1 blobs, ensuring that Java and Node.js can decrypt each other's ciphertext regardless of which SDK encrypted it:

```
[0x01][algId][nsLen][namespace UTF-8 bytes][dekVersion][ivLen][IV][ciphertext+tag]
```

A document encrypted by Java with namespace `acme-corp.production.User#phone` is decryptable by Node.js `lightcrypto-link-node` using the same vault and CMK — no SDK-specific metadata is required.

**Requirement**: Both SDKs must be configured with:
- The same `tenant` and `realm` values for the same data
- The same `VaultStore` backend (shared MongoDB vault collection)
- The same CMK provider credentials

## Best Practices

1. **Use deployment-level tenant/realm** when possible — simplest configuration, full annotation support
2. **Use `ProgrammaticCryptoService`** with full-form namespaces for row-level isolation
3. **Never treat namespace as plaintext metadata** — the namespace IS the key routing identity; it determines which DEK encrypts the data
4. **Match tenant/realm between Java and Node.js** — both SDKs must resolve to the same canonical namespace for interoperability
5. **Test blind index isolation** — verify that the same plaintext produces different blind indexes for different tenant namespaces
6. **Keep vault collections shared** — all tenants' vault documents can coexist in the same `__lcl_vaults` collection (namespaces are unique keys)

## Migration

When introducing `tenant`/`realm` to an existing deployment that used `default.default.*`:

1. Existing data encrypted with `default.default.Entity#field` remains decryptable (Wire Format blob embeds the original namespace)
2. New writes will use the configured tenant/realm
3. To re-encrypt existing data under a new namespace, use DEK re-encryption after updating configuration, or write a migration script using `ProgrammaticCryptoService`:
   ```java
   // Read old blob (self-describing — decrypts with old namespace automatically)
   Object plaintext = cryptoService.decryptValue(oldPayload);
   // Re-encrypt with new namespace
   Object newPayload = cryptoService.encryptValue(plaintext, "acme-corp.production.User#phone");
   ```
