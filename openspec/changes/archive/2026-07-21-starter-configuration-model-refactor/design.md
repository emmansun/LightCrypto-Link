## Context

The LightCrypto-Link starter currently uses a single `CryptoProperties` class (prefix `lcl.crypto`) with 8 flat properties: `enabled`, `cmk`, `algorithm`, `tenant`, `realm`, `keyVaultDatabase`, `autoInit`, `cacheTtl`. This was adequate for the initial single-provider, single-tenant, MongoDB-only architecture. After Phase 1 (Core Extract & Wire Format V1) and Phase 2 (Storage Abstraction & Adapter Contract), the platform now supports multiple KMS providers, multiple storage adapters, and namespace-scoped multi-tenancy — but the configuration model has not evolved to match.

LCL-CORE-009 defines a hierarchical configuration tree rooted at `lightcrypto.*` with dedicated namespaces for runtime, cryptography, keyvault, kms, adapters, observability, and tenants. This change aligns the implementation with that design.

**Constraints:**
- Spring Boot 3.x `@ConfigurationProperties` binding with `@Validated` support
- No breaking changes to SPI interfaces (only configuration layer)
- Four key premises from platform strategy: no compatibility burden, no published release yet, free to restructure

## Goals / Non-Goals

**Goals:**
- Replace flat `CryptoProperties` with hierarchical `@ConfigurationProperties` classes per LCL-CORE-009
- Add startup validation (`@Validated`) for security-critical configuration values
- Support multi-provider KMS configuration with type-based dispatch
- Protect sensitive values via `keyHexFile` and environment variable references
- Isolate adapter configuration from core properties

**Non-Goals:**
- Observable/metrics configuration (deferred to Phase 3 Batch 3, paired with Micrometer/OTEL implementation)
- Dynamic tenant resolution (`HEADER`/`CONTEXT`/`JWT` resolver) — only static tenant/realm for now
- Config schema versioning (`lightcrypto.configVersion`) — deferred until after first release
- Standalone (non-Spring) bootstrap model — current scope is Spring Boot only
- Runtime reconfiguration / hot-reload

## Decisions

### Decision 1: Prefix migration `lcl.crypto` → `lightcrypto`

**Choice**: Migrate to `lightcrypto.*` as the root prefix.

**Rationale**: LCL-CORE-009 specifies `lightcrypto:` as the canonical root. Since there is no published release and no external users, the migration cost is internal only (examples + tests). Keeping `lcl.crypto` would create permanent divergence from the platform spec.

**Alternative considered**: Keep `lcl.crypto` as a deprecated alias with `@DeprecatedConfigurationProperty`. Rejected — adds complexity with no user benefit at this stage.

### Decision 2: Six `@ConfigurationProperties` classes

**Choice**: Split into 6 classes, each with its own prefix:

| Class | Prefix | Responsibility |
|-------|--------|---------------|
| `RuntimeProperties` | `lightcrypto.runtime` | SPI version, mode, strict mode |
| `CryptographyProperties` | `lightcrypto.cryptography` | Default algorithm, allowed algorithms, AEAD requirement |
| `KeyVaultProperties` | `lightcrypto.keyvault` | Backend type, cache TTL, cache max entries |
| `KmsProperties` | `lightcrypto.kms` | Provider list with type-based dispatch |
| `TenantProperties` | `lightcrypto.tenants` | Static tenant + realm identifiers |
| `MongoAdapterProperties` | `lightcrypto.adapters.mongodb` | MongoDB adapter-specific settings |

**Rationale**: Each class maps to a single concern (SRP), enabling targeted injection — `EntityMetadataCache` only needs `CryptographyProperties`, `KeyVaultService` only needs `KeyVaultProperties` + `TenantProperties`. This prevents the "god object" anti-pattern and enforces LCL-CORE-009 principle P6 (Adapter Isolation).

### Decision 3: KMS provider list with type-based dispatch

**Choice**: `KmsProperties` holds a `List<ProviderEntry>` where each entry has `id`, `type` (enum: `LOCAL_SYMMETRIC`, `ALIYUN`, `AZURE`), and type-specific config map. `LightCryptoLinkAutoConfiguration` iterates providers and creates the matching `CmkProvider` bean.

```yaml
lightcrypto:
  kms:
    providers:
      - id: local
        type: LOCAL_SYMMETRIC
        keyHex: "<64-char-hex>"
      - id: aliyun
        type: ALIYUN
        config:
          regionId: cn-hangzhou
          keyId: alias/lcl-master
```

**Rationale**: The current `lcl.crypto.cmk` only supports local symmetric keys. Cloud KMS providers (Alibaba, Azure) currently require manual bean definition. A provider list enables declarative multi-provider setup while remaining extensible for future provider types.

**Alternative considered**: Separate `lightcrypto.kms.local`, `lightcrypto.kms.aliyun`, `lightcrypto.kms.azure` sub-keys. Rejected — not extensible, each new provider type requires schema change.

### Decision 4: Sensitive value protection via `keyHexFile`

**Choice**: Each provider entry optionally supports `keyHexFile` (path to file containing hex key) as an alternative to inline `keyHex`. Spring Boot's standard `${ENV_VAR}` interpolation handles environment variables natively.

**Rationale**: Aligns with LCL-CORE-009 §3.3 sensitive value handling. `keyHexFile` enables Docker/K8s secret mounts without env var exposure. Spring Boot already supports `${LIGHTCRYPTO_KMS_PROVIDERS_0_KEYHEX}` for env override — no custom logic needed.

### Decision 5: Adapter properties in dedicated namespace

**Choice**: `MongoAdapterProperties` at `lightcrypto.adapters.mongodb` holds adapter-specific settings (`keyVaultCollection`, `autoInit`, `keyVaultDatabase`). `MongoAdapterAutoConfiguration` injects `MongoAdapterProperties` instead of `CryptoProperties`.

**Rationale**: Enforces LCL-CORE-009 P6 (Adapter Isolation). When MySQL/Postgres adapters are added, each gets its own `lightcrypto.adapters.mysql` / `lightcrypto.adapters.postgres` namespace.

### Decision 6: Validation with `@Validated` + custom validator

**Choice**: Annotate properties with JSR-380 constraints (`@NotNull`, `@NotBlank`, `@Min`, `@Pattern`) and add a `ConfigurationValidator` for cross-field validation (e.g., `defaultAlgorithm` must appear in `allowedAlgorithms`).

**Rationale**: Spring Boot's `@Validated` on `@ConfigurationProperties` provides automatic startup validation. Cross-field rules need a custom `Validator` registered via `@ConfigurationPropertiesBinding`.

## Risks / Trade-offs

- **[Breaking change for examples]** → All `application.yml` files must be updated. Mitigation: update all examples in the same change; provide clear before/after mapping in migration notes.
- **[KMS provider dispatch complexity]** → Type-based dispatch adds conditional logic in auto-configuration. Mitigation: each provider type maps to a simple factory method; keep dispatch flat and readable.
- **[Property name collision]** → `lightcrypto.tenants.tenant` vs `lightcrypto.tenants.default` naming. Mitigation: keep `tenant` and `realm` as simple string fields for now; rename when dynamic resolution is added.
- **[Lombok `@Data` with `@Validated`]** → Lombok-generated setters bypass JSR-380 validation in some edge cases. Mitigation: verify validation works with Spring Boot 3.x + Lombok in tests; switch to records if needed.
