## 1. EventBus SPI (lcl-core)

- [x] 1.1 Create `io.github.emmansun.lightcrypto.core.event.EventTier` enum with values `L1`, `L2`, `L3`
- [x] 1.2 Create `io.github.emmansun.lightcrypto.core.event.LclEvent` immutable class with builder pattern (fields: event, tier, timestamp, durationMicros, result, namespace, algorithm, dekVersion, errorType, attributes)
- [x] 1.3 Create `io.github.emmansun.lightcrypto.core.event.EventBus` functional interface with `void emit(LclEvent event)`
- [x] 1.4 Create `io.github.emmansun.lightcrypto.core.event.NoOpEventBus` singleton implementing EventBus
- [x] 1.5 Create `io.github.emmansun.lightcrypto.core.event.CompositeEventBus` with exception-isolating delegation
- [x] 1.6 Write unit tests for LclEvent builder (all fields, defaults, immutable verification)
- [x] 1.7 Write unit tests for CompositeEventBus (multi-listener, failure isolation, ordering)

## 2. ObservabilityProperties + Observability Auto-Configuration

- [x] 2.1 Create `ObservabilityProperties` with nested `Events`, `Metrics`, `Health` inner classes (prefix: `lightcrypto.observability`)
- [x] 2.2 Add `ObservabilityProperties.class` to `LightCryptoLinkAutoConfiguration.@EnableConfigurationProperties`
- [x] 2.3 Create `ObservabilityAutoConfiguration` class with `@ConditionalOnProperty("lightcrypto.observability.enabled", matchIfMissing=true)`
- [x] 2.4 Register EventBus bean (default: NoOpEventBus) with `@ConditionalOnMissingBean(EventBus.class)`
- [x] 2.5 Add `ObservabilityAutoConfiguration` to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 3. Slf4jEventBus Implementation (starter)

- [x] 3.1 Create `Slf4jEventBus` implementing `EventBus` — format LclEvent as JSON string, output to Slf4j logger
- [x] 3.2 Register Slf4jEventBus bean in ObservabilityAutoConfiguration with `@ConditionalOnProperty("lightcrypto.observability.events.enabled", matchIfMissing=true)`
- [x] 3.3 Update ObservabilityAutoConfiguration to compose EventBus: when both events and metrics enabled, create CompositeEventBus wrapping Slf4jEventBus + MicrometerEventBus
- [x] 3.4 Write unit tests for Slf4jEventBus (JSON format, field redaction)

## 4. Micrometer Metrics (starter)

- [x] 4.1 Add `micrometer-core` dependency to `lcl-spring-boot-starter/pom.xml` (version managed by Spring Boot BOM)
- [x] 4.2 Create `LclMetrics` class — define all Timer/Counter/Gauge meters with tags and percentiles
- [x] 4.3 Create `MicrometerEventBus` implementing `EventBus` — route metric-relevant events to LclMetrics
- [x] 4.4 Register MicrometerEventBus + LclMetrics beans with `@ConditionalOnProperty("lightcrypto.observability.metrics.enabled", matchIfMissing=true)` and `@ConditionalOnClass(MeterRegistry.class)`
- [x] 4.5 Write unit tests for MicrometerEventBus (timer recording, counter increment, non-metric event ignored)
- [x] 4.6 Write unit tests for LclMetrics (meter registration, tag correctness)

## 5. Health Indicator (starter)

- [x] 5.1 Create `LclHealthStatus` enum with STARTING, READY, DEGRADED, FAILED
- [x] 5.2 Create `ComponentHealthCheck` functional interface returning `LclHealthStatus`
- [x] 5.3 Create built-in checks: `CoreHealthCheck`, `KmsHealthCheck`, `VaultHealthCheck`
- [x] 5.4 Create `LclHealthIndicator` implementing Spring Boot `HealthIndicator` — compose component checks, map status
- [x] 5.5 Register LclHealthIndicator with `@ConditionalOnClass(HealthIndicator.class)` and `@ConditionalOnProperty("lightcrypto.observability.health.enabled", matchIfMissing=true)`
- [x] 5.6 Write unit tests for LclHealthIndicator (all-healthy, degraded, failed, actuator missing)

## 6. Refactor Existing Log Calls to EventBus

- [x] 6.1 Add `EventBus` field + constructor parameter to `KeyVaultService`, default to `NoOpEventBus`
- [x] 6.2 Replace `log.info("DEK rotated...")` in KeyVaultService with `lcl.rotation.execute.completed` event
- [x] 6.3 Replace `log.info("Initializing key vault...")` with `lcl.keyvault.init.completed` event
- [x] 6.4 Replace `log.info("Key vault loaded...")` with `lcl.keyvault.load.completed` event
- [x] 6.5 Replace `log.info("DEK cache flushed...")` with `lcl.keyvault.cache.evicted` event
- [x] 6.6 Update `MongoAdapterAutoConfiguration` to inject EventBus into KeyVaultService constructor
- [x] 6.7 Add EventBus injection to transparent listener (BeforeSaveListener / EncryptHandler)
- [x] 6.8 Add encrypt/decrypt timing in listener using `System.nanoTime()` + EventBus emit

## 7. SDK Version Utility

- [x] 7.1 Create `src/main/resources/lcl-build.properties` in `lcl-core` with `lcl.sdk.version=${project.version}` (Maven resource filtering)
- [x] 7.2 Enable resource filtering in `lcl-core/pom.xml` `<build><resources>` section
- [x] 7.3 Create `SdkVersion` utility class in `lcl-core` that reads `lcl-build.properties` and exposes `getVersion()` method
- [x] 7.4 Use `SdkVersion.getVersion()` in LclEvent envelope (when sdkVersion field is added in future) and HealthIndicator details

## 8. Tests & Verification

- [x] 8.1 Write `ObservabilityAutoConfigurationTest` using ApplicationContextRunner (events on/off, metrics on/off, health on/off)
- [x] 8.2 Write integration test: verify Micrometer metrics appear in MeterRegistry after encrypt operation
- [x] 8.3 Write integration test: verify HealthIndicator reports UP when all components healthy
- [x] 8.4 Run `mvn -B -pl lcl-core,lcl-spring-boot-starter,lcl-adapter-mongodb clean verify` — fix any failures
- [x] 8.5 Run `mvn -B -pl lcl-spring-boot-starter spotbugs:check` — fix any warnings

## 9. Documentation Sync

- [x] 9.1 Update `docs/configuration.md` — add `lightcrypto.observability.*` configuration tree with all properties
- [x] 9.2 Update `docs/architecture.md` — add EventBus SPI as new architectural component in core layer
- [x] 9.3 Update `README.md` — add observability section (metrics, health indicator, structured events)
