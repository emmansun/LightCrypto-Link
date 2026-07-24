#!/usr/bin/env python3
"""
Compare JMH benchmark results against a committed baseline.

Usage:
    python compare-baseline.py <results.json> <baseline.json> [--threshold 10]

Exit codes:
    0 - No regression detected (all p95 within threshold)
    1 - Regression detected (one or more benchmarks exceed threshold)
    2 - Error (missing files, invalid JSON, etc.)
"""

import json
import sys
import argparse
import io
from pathlib import Path

# Ensure UTF-8 output on Windows
if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')


def load_json(path: str) -> list:
    """Load JMH JSON results file."""
    try:
        with open(path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"ERROR: File not found: {path}", file=sys.stderr)
        sys.exit(2)
    except json.JSONDecodeError as e:
        print(f"ERROR: Invalid JSON in {path}: {e}", file=sys.stderr)
        sys.exit(2)


def extract_p95(results: list) -> dict:
    """
    Extract p95 latency (in µs) for each benchmark from JMH JSON results.
    
    JMH JSON structure:
    [
        {
            "benchmark": "io.github...AesGcmBenchmark.encryptField",
            "mode": "sample",
            "primaryMetric": {
                "scorePercentiles": {
                    "95.0": 12.345
                }
            }
        }
    ]
    """
    p95_map = {}
    for entry in results:
        benchmark = entry.get("benchmark", "unknown")
        mode = entry.get("mode", "")
        
        # Only consider sample mode (which has percentiles)
        if mode != "sample":
            continue
        
        primary = entry.get("primaryMetric", {})
        percentiles = primary.get("scorePercentiles", {})
        p95 = percentiles.get("95.0")
        
        if p95 is not None:
            # Extract class.method from full benchmark name
            # e.g., "io.github...AesGcmBenchmark.encryptField" -> "AesGcmBenchmark.encryptField"
            parts = benchmark.split(".")
            if len(parts) >= 2:
                key = f"{parts[-2]}.{parts[-1]}"
            else:
                key = benchmark
            p95_map[key] = p95
    
    return p95_map


def compare(results_p95: dict, baseline_p95: dict, threshold: float) -> list:
    """
    Compare results against baseline.
    
    Returns list of regressions: (benchmark, baseline_p95, result_p95, pct_change)
    """
    regressions = []
    
    for bench, result_val in results_p95.items():
        if bench not in baseline_p95:
            print(f"WARNING: New benchmark '{bench}' not in baseline (skipping)")
            continue
        
        baseline_val = baseline_p95[bench]
        if baseline_val <= 0:
            continue
        
        pct_change = ((result_val - baseline_val) / baseline_val) * 100
        
        if pct_change > threshold:
            regressions.append((bench, baseline_val, result_val, pct_change))
    
    return regressions


def main():
    parser = argparse.ArgumentParser(description="Compare JMH results against baseline")
    parser.add_argument("results", help="Path to benchmark-results.json")
    parser.add_argument("baseline", help="Path to baseline.json")
    parser.add_argument("--threshold", type=float, default=10.0,
                        help="Regression threshold percentage (default: 10)")
    args = parser.parse_args()

    # Load files
    results = load_json(args.results)
    baseline = load_json(args.baseline)

    # Extract p95 values
    results_p95 = extract_p95(results)
    baseline_p95 = extract_p95(baseline)

    if not results_p95:
        print("ERROR: No sample-mode benchmarks found in results", file=sys.stderr)
        sys.exit(2)

    if not baseline_p95:
        print("ERROR: No sample-mode benchmarks found in baseline", file=sys.stderr)
        sys.exit(2)

    print(f"Comparing {len(results_p95)} benchmarks against baseline (threshold: {args.threshold}%)")
    print("-" * 70)

    # Compare
    regressions = compare(results_p95, baseline_p95, args.threshold)

    # Print summary
    for bench in sorted(results_p95.keys()):
        result_val = results_p95[bench]
        baseline_val = baseline_p95.get(bench)
        
        if baseline_val is None:
            status = "NEW"
            print(f"  {bench}: {result_val:.3f}µs [{status}]")
        else:
            pct = ((result_val - baseline_val) / baseline_val) * 100
            status = "REGRESSED" if pct > args.threshold else "OK"
            symbol = "✗" if status == "REGRESSED" else "✓"
            print(f"  {symbol} {bench}: {result_val:.3f}µs (baseline: {baseline_val:.3f}µs, {pct:+.1f}%) [{status}]")

    print("-" * 70)

    if regressions:
        print(f"\nFAILED: {len(regressions)} benchmark(s) regressed >{args.threshold}%:")
        for bench, base, curr, pct in regressions:
            print(f"  - {bench}: {base:.3f}µs -> {curr:.3f}µs ({pct:+.1f}%)")
        
        # GitHub Actions annotation
        print("\n::error::Performance regression detected!")
        for bench, base, curr, pct in regressions:
            print(f"::error::{bench} regressed {pct:+.1f}% ({base:.3f}µs -> {curr:.3f}µs)")
        
        sys.exit(1)
    else:
        print(f"\nPASSED: All benchmarks within {args.threshold}% of baseline")
        sys.exit(0)


if __name__ == "__main__":
    main()
