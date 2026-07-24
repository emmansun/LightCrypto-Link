## Context

LightCrypto-Link is a field-level encryption library with the following performance-critical paths:
- `CryptoCodec.encrypt()` / `decrypt()` — per-field AES-256-GCM or SM4-GCM operations
- `BlindIndexComputer.compute()` — HMAC-SHA256 blind index derivation
- `KeyVaultService.getDekByVersion()` — DEK cache lookup (hot) vs CMK unwrap (cold)
- `MongoEncryptHandler.encryptDocument()` — batch document traversal + multi-field encryption

The design roadmap (LCL-CORE-011) mandates: **p95 < 250µs per field encrypt/decrypt** as the performance SLA. Currently no benchmarks exist to validate this target or detect regressions.

The project uses Maven multi-module build with Java 17+. The `lcl-core` module contains pure crypto logic (no Spring dependency), making it ideal for isolated micro-benchmarks.

## Goals / Non-Goals

**Goals:**
- Establish reproducible JMH benchmarks for all crypto hot paths
- Produce machine-readable JSON results for CI comparison
- Detect >10% performance regressions automatically in nightly CI
- Provide a committed baseline JSON that represents the "known good" state
- Keep benchmarks out of the default build (opt-in via profile)

**Non-Goals:**
- Load/stress testing (that's integration-level, not micro-benchmark)
- Benchmarking Spring context startup time (covered by diagnostics KAT)
- Benchmarking network-bound KMS calls (Alibaba/Azure) — only local crypto
- Publishing benchmark artifacts to Maven Central
- Supporting JDK versions other than 17+ for benchmarks

## Decisions

### 1. JMH as benchmark framework

**Decision**: Use JMH (Java Microbenchmark Harness) 1.37+.

**Rationale**: JMH is the industry standard for JVM micro-benchmarks. It handles warmup, fork isolation, GC noise reduction, and statistical rigor (percentile reporting). Alternatives like custom `System.nanoTime()` loops lack statistical validity.

**Alternative considered**: Gatling/JMeter — rejected because they measure throughput under load, not per-operation latency percentiles.

### 2. Dedicated `lcl-benchmarks` module

**Decision**: Create a standalone Maven module `lcl-benchmarks` that depends on `lcl-core` and `lcl-spi`.

**Rationale**: Benchmarks need access to `CryptoCodec`, `BlindIndexComputer`, and key model classes. Placing them in a separate module keeps the main artifacts clean and avoids JMH annotation processor pollution in production code.

**Alternative considered**: Adding benchmarks as test-scope in `lcl-core` — rejected because JMH requires its own annotation processor and main-class packaging, which conflicts with normal test lifecycle.

### 3. Benchmark scenarios

**Decision**: Five benchmark classes covering the critical paths:

| Benchmark | What it measures | Mode |
|-----------|-----------------|------|
| `AesGcmBenchmark` | Single-field AES-256-GCM encrypt + decrypt | `avgt`, `p95` |
| `Sm4GcmBenchmark` | Single-field SM4-GCM encrypt + decrypt | `avgt`, `p95` |
| `BlindIndexBenchmark` | HMAC-SHA256 blind index computation | `avgt`, `p95` |
| `BatchDocumentBenchmark` | 8-field document encrypt + decrypt (simulated) | `avgt`, `p95` |
| `DekCacheBenchmark` | DEK cache hit vs cold-path CMK unwrap | `avgt`, `p95` |

Parameters: `@Fork(2)`, `@Warmup(iterations=3, time=1s)`, `@Measurement(iterations=5, time=2s)`, `@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})`, `@OutputTimeUnit(MICROSECONDS)`.

### 4. Maven profile `bench` (opt-in)

**Decision**: The `lcl-benchmarks` module is included in the reactor only when `-Pbench` is activated. Default builds skip it entirely.

**Rationale**: JMH benchmarks take 5-10 minutes to run properly. Including them in every `mvn verify` would slow CI unacceptably. The profile approach keeps the default build fast while allowing explicit benchmark runs.

**Implementation**: Parent POM declares `lcl-benchmarks` in a `<profile id="bench">` section under `<modules>`.

### 5. JSON output + committed baseline

**Decision**: Benchmarks output to `lcl-benchmarks/results/benchmark-results.json` (JMH JSON format). A committed `baseline.json` represents the known-good state. A comparison script flags >10% regression on p95 metrics.

**Rationale**: JSON is machine-parseable for CI. Committing the baseline allows PRs to compare against a fixed reference without needing historical data infrastructure.

### 6. GitHub Actions nightly workflow

**Decision**: A `benchmark.yml` workflow runs on `schedule: cron '0 2 * * *'` (nightly at 2 AM UTC). It runs benchmarks, compares against baseline, and posts a comment/annotation if regression >10% is detected.

**Rationale**: Nightly runs avoid blocking PRs while still catching regressions within 24h. The 10% threshold balances sensitivity vs noise (JMH variance is typically 2-5%).

## Risks / Trade-offs

- **[JMH variance on shared CI runners]** → Mitigation: Use `@Fork(2)` with 5 measurement iterations; 10% regression threshold absorbs typical noise. Consider self-hosted runner if variance is too high.
- **[Benchmark maintenance as APIs evolve]** → Mitigation: Benchmarks depend only on `lcl-core` public API (stable, frozen by Wire Format V1 contract).
- **[SM4 requires Bouncy Castle registration]** → Mitigation: Benchmark `@Setup` registers BouncyCastleProvider explicitly.
- **[Long benchmark runtime (~8 min)]** → Mitigation: Nightly schedule, not per-PR. Manual runs via `mvn verify -Pbench` for developers.
