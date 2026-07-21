## 1. Bootstrap Engine Core (lcl-core)

- [x] 1.1 Create `io.github.emmansun.lightcrypto.core.bootstrap.FailureClass` enum: FATAL, RECOVERABLE, ADVISORY
- [x] 1.2 Create `BootstrapCheck` functional interface: `BootstrapResult check(BootstrapContext context)`
- [x] 1.3 Create `BootstrapPhase` record with fields: name, check, failureClass
- [x] 1.4 Create `BootstrapResult` record with fields: status (READY/FAILED/DEGRADED), phaseResults (list), durationMs, failedPhase (nullable), errorDetails (nullable)
- [x] 1.5 Create `BootstrapContext` immutable class carrying: CmkProvider, EventBus, RuntimeProperties, optional VaultStore
- [x] 1.6 Create `BootstrapTimeoutException` extending CryptoException
- [x] 1.7 Create `BootstrapEngine` class: sequential phase execution, timeout enforcement, EventBus event emission, retry logic for RECOVERABLE phases
- [x] 1.8 Write unit tests for BootstrapEngine (all-pass, fatal-abort, advisory-continue, timeout, recoverable-retry)

## 2. KAT Runner (lcl-core)

- [x] 2.1 Copy KAT vector JSON files from project `vectors/` to `lcl-core/src/main/resources/kat/` (encryption/, blind-index/, kcv/)
- [x] 2.2 Create `KatVectorLoader` — load JSON vectors from classpath `/kat/` resources
- [x] 2.3 Create `KatRunner` implementing `BootstrapCheck` — run encryption KAT for AES-256-GCM, SM4-GCM, AES-256-CBC, SM4-CBC
- [x] 2.4 Add HKDF-SHA-256 KAT execution to KatRunner
- [x] 2.5 Add HMAC-SHA-256 blind index KAT execution to KatRunner
- [x] 2.6 Implement KAT timing enforcement (total ≤ 200ms, per-primitive ≤ 30ms)
- [x] 2.7 Write unit tests for KatRunner (all-pass, single-algorithm-fail, timeout, missing-vectors)

## 3. Canary Self-Test (lcl-core)

- [x] 3.1 Create `CanaryRunner` implementing `BootstrapCheck` — canary encrypt/decrypt roundtrip using embedded canary DEK + namespace
- [x] 3.2 Add multi-algorithm canary (iterate over allowed algorithms)
- [x] 3.3 Add metadata roundtrip canary (encrypt → binary encode → decode → verify)
- [x] 3.4 Write unit tests for CanaryRunner (success, encrypt-fail, decrypt-fail, metadata-fail)

## 4. Bootstrap Phase Implementations (lcl-core)

- [x] 4.1 Create `ConfigValidationCheck` — verify RuntimeProperties are valid (BOOT-1)
- [x] 4.2 Create `SpiVersionCheck` — verify spiVersion == 1 (BOOT-2)
- [x] 4.3 Create `VaultReachabilityCheck` — verify VaultStore.load() does not throw (BOOT-8), RECOVERABLE class
- [x] 4.4 Create `KmsReachabilityCheck` — verify CmkProvider can wrap/unwrap a canary key (BOOT-9), RECOVERABLE class
- [x] 4.5 Write unit tests for each check implementation

## 5. Bootstrap Integration in Starter

- [x] 5.1 Add `bootstrap-enabled` and `bootstrap-timeout` fields to `RuntimeProperties`
- [x] 5.2 Create `DiagnosticsAutoConfiguration` with `@ConditionalOnProperty("lightcrypto.runtime.bootstrap-enabled", matchIfMissing=true)`
- [x] 5.3 Create `LclBootstrapRunner` implementing `ApplicationRunner` — constructs BootstrapContext, registers phases, runs BootstrapEngine
- [x] 5.4 Register LclBootstrapRunner bean in DiagnosticsAutoConfiguration
- [x] 5.5 Wire BootstrapResult into LclHealthIndicator (from observability-foundation) for status reporting
- [x] 5.6 Add DiagnosticsAutoConfiguration to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 6. Actuator Diagnostics Endpoints

- [x] 6.1 Add `spring-boot-starter-actuator` as optional dependency to `lcl-spring-boot-starter/pom.xml`
- [x] 6.2 Create `LclHealthEndpoint` with `@Endpoint(id = "lclhealth")` + `@ReadOperation` returning diagnostic JSON
- [x] 6.3 Create `LclKatEndpoint` with `@Endpoint(id = "lclkat")` + `@ReadOperation` returning KAT results, `@WriteOperation` for on-demand re-run
- [x] 6.4 Implement response redaction (CMK ID hashing, secret masking)
- [x] 6.5 Register endpoints in DiagnosticsAutoConfiguration with `@ConditionalOnClass(Endpoint.class)`
- [x] 6.6 Write unit tests for endpoint responses (ready state, degraded state, redaction verification)

## 7. Tests & Verification

- [x] 7.1 Write integration test: full bootstrap sequence with mock KMS + vault, verify all events emitted
- [x] 7.2 Write integration test: KAT failure aborts bootstrap, verify FATAL status
- [x] 7.3 Write integration test: canary roundtrip during bootstrap, verify success event
- [x] 7.4 Write integration test: actuator endpoints return correct JSON
- [x] 7.5 Run `mvn -B -pl lcl-core,lcl-spring-boot-starter clean verify` — fix any failures
- [x] 7.6 Run `mvn -B -pl lcl-spring-boot-starter spotbugs:check` — fix any warnings

## 8. Documentation Sync

- [x] 8.1 Update `docs/configuration.md` — add `lightcrypto.runtime.bootstrap-enabled` and `lightcrypto.runtime.bootstrap-timeout` properties
- [x] 8.2 Update `README.md` — add bootstrap diagnostics section (KAT, canary, actuator endpoints, failure behavior)
- [x] 8.3 Ensure diagnostics endpoint `sdkVersion` field uses `SdkVersion.getVersion()` (from observability-foundation) instead of hardcoded value
