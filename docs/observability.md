# Observability Guide

Light Crypto Link provides structured event emission, Micrometer metrics, and health check infrastructure for production monitoring. All observability features are auto-configured by `lcl-spring-boot-starter` and can be toggled via configuration properties.

## Event Catalog

All events follow the naming convention: `lcl.<subsystem>.<operation>.<status>`

### Event Tier Model

| Tier | Semantics | Delivery | Slf4j Level |
|------|-----------|----------|-------------|
| **L1** | Diagnostic | Best-effort | `DEBUG` |
| **L2** | Operational | Reliable | `INFO` |
| **L3** | Audit | Guaranteed | `INFO` |

### Bootstrap Events

| Event | Tier | Source | Description |
|-------|------|--------|-------------|
| `lcl.bootstrap.started` | L2 | BootstrapEngine | Bootstrap sequence initiated |
| `lcl.bootstrap.{phase}.started` | L2 | BootstrapEngine | Phase (config/spi/kat/canary/kms) started |
| `lcl.bootstrap.{phase}.completed` | L2 | BootstrapEngine | Phase completed successfully |
| `lcl.bootstrap.{phase}.failed` | L2 | BootstrapEngine | Phase failed |
| `lcl.bootstrap.{phase}.degraded` | L2 | BootstrapEngine | Phase degraded (non-fatal) |
| `lcl.bootstrap.{phase}.advisory` | L2 | BootstrapEngine | Phase advisory notice |
| `lcl.bootstrap.ready` | L2 | BootstrapEngine | All phases complete |
| `lcl.bootstrap.timeout` | L2 | BootstrapEngine | Bootstrap exceeded timeout |

### Key Vault Events

| Event | Tier | Source | Attributes | Description |
|-------|------|--------|------------|-------------|
| `lcl.keyvault.init.completed` | L2 | KeyVaultService | — | Vault first created for namespace |
| `lcl.keyvault.load.completed` | L2 | KeyVaultService | activeKid, dekVersion | Keys loaded and verified into cache |
| `lcl.keyvault.cache.evicted` | L1 | KeyVaultService | — | Cache flushed, key material destroyed |
| `lcl.keyvault.keys.retired` | L2 | KeyVaultService | retiredKids | ROTATED→RETIRED transition |
| `lcl.keyvault.keys.pruned` | L2 | KeyVaultService | removedCount | RETIRED entries permanently removed |

### Rotation & Re-wrap Events

| Event | Tier | Source | Attributes | Description |
|-------|------|--------|------------|-------------|
| `lcl.rotation.execute.completed` | L2 | KeyVaultService | kid | DEK rotation successful |
| `lcl.rewrap.namespace.completed` | L2 | KeyVaultService | — | Single namespace re-wrap success |
| `lcl.rewrap.namespace.failed` | L2 | KeyVaultService | errorType | Single namespace re-wrap failure |
| `lcl.rewrap.batch.completed` | L2 | KeyVaultService | totalCount, successCount, failedCount | Batch re-wrap complete |
| `lcl.rewrap.runner.completed` | L2 | CmkProviderRewrapRunner | — | Auto re-wrap runner finished |

### Re-encryption Events

| Event | Tier | Source | Attributes | Description |
|-------|------|--------|------------|-------------|
| `lcl.reencrypt.batch.completed` | L2 | DekReEncryptionService | docsProcessed, docsSkipped, docsFailed | Batch progress |
| `lcl.reencrypt.namespace.completed` | L2 | DekReEncryptionService | docsProcessed, docsSkipped, docsFailed, fieldsReEncrypted, durationMicros | Namespace complete |

### Crypto Path Events

| Event | Tier | Source | Attributes | Description |
|-------|------|--------|------------|-------------|
| `lcl.crypto.encrypt.completed` | L2 | CryptoBeforeSaveListener | algorithm, namespace, durationMicros | Field encryption completed |
| `lcl.crypto.decrypt.completed` | L2 | DecryptHandler | algorithm, namespace, durationMicros | Field decryption completed |
| `lcl.blind_index.compute.completed` | L2 | BlindIndexEngine | namespace, durationMicros | Blind index computed |

## EventBus Implementations

### NoOpEventBus (fallback)

Zero-overhead singleton. All events silently discarded. Used automatically when observability is disabled.

```java
EventBus bus = NoOpEventBus.INSTANCE;
```

### Slf4jEventBus (default)

Structured JSON output to logger `lcl.events` with tier-based log level mapping:

```json
{"event":"lcl.keyvault.init.completed","tier":"L2","timestamp":"2026-07-30T10:00:00Z","result":"success","namespace":"default.default.User#phone"}
```

### MicrometerEventBus

Routes metric-relevant events (`lcl.crypto.*`, `lcl.rotation.*`, `lcl.keyvault.load.*`, `lcl.blind_index.*`) to Micrometer Timer/Counter registrations. Non-metric events are silently ignored.

### CompositeEventBus (auto-configured)

Multi-cast to all registered buses with failure isolation. Auto-configured as the `@Primary` EventBus bean combining Slf4j + Micrometer.

### Custom EventBus

Implement the `EventBus` interface and register as a Spring bean:

```java
@Component
public class OtelEventBus implements EventBus {
    @Override
    public void emit(LclEvent event) {
        // Bridge to OpenTelemetry, Prometheus, Datadog, etc.
        meter.counter("lcl.events")
             .tag("event", event.event())
             .tag("tier", event.tier().name())
             .increment();
    }
}
```

## Micrometer Metrics

### Timers (duration with percentiles)

| Metric Name | Tags | Description |
|-------------|------|-------------|
| `lcl.crypto.encrypt.duration` | algorithm, namespace | Encrypt operation latency |
| `lcl.crypto.decrypt.duration` | algorithm, namespace | Decrypt operation latency |
| `lcl.blind_index.compute.duration` | namespace | Blind index computation latency |
| `lcl.keyvault.load.duration` | namespace | Vault load + KCV verification latency |
| `lcl.rotation.duration` | namespace | DEK rotation operation latency |

Percentiles published: **p50, p95, p99** (configurable).

### Counters

| Metric Name | Tags | Description |
|-------------|------|-------------|
| `lcl.crypto.encrypt.total` | algorithm, result | Encrypt operation count |
| `lcl.crypto.decrypt.total` | algorithm, result | Decrypt operation count |
| `lcl.rotation.total` | result | Rotation operation count |

### Actuator Access

```
GET /actuator/metrics/lcl.crypto.encrypt.duration
GET /actuator/metrics/lcl.crypto.decrypt.total
```

## Health Model

### LclHealthStatus

Four-state model with severity ordering:

```
READY (0) < STARTING (1) < DEGRADED (2) < FAILED (3)
```

| Status | Meaning |
|--------|---------|
| `READY` | Fully operational |
| `STARTING` | Initialization in progress |
| `DEGRADED` | Non-critical component unavailable |
| `FAILED` | Fatal — crypto operations cannot proceed |

Overall health = worst state across all registered components.

### Spring Boot Actuator Integration

LCL registers a `HealthIndicator` bean (SB3: `org.springframework.boot.actuate.health.HealthIndicator`, SB4: `org.springframework.boot.health.contributor.HealthIndicator`):

```
GET /actuator/health
```

Response includes `lcl` component:
```json
{
  "status": "UP",
  "components": {
    "lcl": {
      "status": "UP",
      "details": {
        "bootstrap": "READY",
        "vault": "READY"
      }
    }
  }
}
```

### Custom Diagnostics Endpoint

A dedicated `@Endpoint(id = "lcl-health")` provides detailed bootstrap diagnostics:

```
GET /actuator/lcl-health
```

Response:
```json
{
  "status": "READY",
  "sdkLanguage": "java",
  "sdkVersion": "1.0.0",
  "spiVersion": 1,
  "wireFormatVersion": 1,
  "components": { "kat": "OK", "kms": "OK" },
  "lastBootstrap": "2026-07-30T10:00:00Z",
  "bootstrapDurationMs": 245
}
```

### Custom ComponentHealthCheck

Register additional health checks as Spring beans:

```java
@Bean
public ComponentHealthCheck kmsHealthCheck(CmkProvider cmkProvider) {
    return () -> cmkProvider.isReachable()
            ? LclHealthStatus.READY
            : LclHealthStatus.DEGRADED;
}
```

## Configuration Reference

All properties under prefix `lightcrypto.observability`:

| Property | Default | Description |
|----------|---------|-------------|
| `lightcrypto.observability.enabled` | `true` | Master switch for all observability |
| `lightcrypto.observability.events.enabled` | `true` | Enable Slf4jEventBus |
| `lightcrypto.observability.metrics.enabled` | `true` | Enable Micrometer metrics |
| `lightcrypto.observability.metrics.publish-percentiles` | `true` | Publish p50/p95/p99 on Timers |
| `lightcrypto.observability.health.enabled` | `true` | Enable LclHealthIndicator |

### Disable All Observability

```yaml
lightcrypto:
  observability:
    enabled: false
```

### Disable Metrics Only

```yaml
lightcrypto:
  observability:
    metrics:
      enabled: false
```

## Security Constraint

`LclEvent` instances **MUST NOT** contain: IV, auth tag, ciphertext, wrapped DEK, CMK material, plaintext values, query values, or personal data. Events carry only metadata (namespace, algorithm, duration, error type).

The diagnostics endpoint applies automatic redaction of sensitive patterns (`key=***`, `secret=***`, `password=***`, `token=***`) in error details.
