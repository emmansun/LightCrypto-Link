## Why

LCL has no quantitative performance data. The design roadmap (Phase 3, LCL-CORE-011) requires a performance baseline with target p95 < 250µs per field encryption/decryption. Without benchmarks, we cannot detect performance regressions across releases, validate optimization efforts, or provide users with credible throughput numbers for capacity planning.

## What Changes

- Create new module `lcl-benchmarks` — a JMH-based benchmark suite measuring encrypt/decrypt throughput and latency across all supported algorithms
- Benchmark scenarios: single-field AES-256-GCM, single-field SM4-GCM, blind index HMAC-SHA256, batch document encryption (5-10 fields), DEK cache hit vs cold path
- Add Maven profile `bench` to run benchmarks optionally (excluded from default reactor build)
- Output results in JMH JSON format for automated comparison
- Add GitHub Actions workflow for nightly benchmark runs with regression detection against committed baseline

## Capabilities

### New Capabilities
- `performance-baseline`: JMH benchmark module with standardized scenarios, JSON output, Maven profile integration, and CI nightly regression detection

### Modified Capabilities
<!-- No existing spec-level behavior changes. Encryption semantics remain identical. -->

## Impact

- **New module**: `lcl-benchmarks/` with JMH benchmark classes, `pom.xml`, and baseline JSON
- **Parent POM**: Add `lcl-benchmarks` to `<modules>` (under `bench` profile or always with `<skip>` default)
- **CI/CD**: New `.github/workflows/benchmark.yml` for nightly runs
- **Dependencies**: JMH 1.37+ (benchmark scope only, not published to Central)
- **Users**: No impact — benchmarks are internal tooling, not a published artifact
