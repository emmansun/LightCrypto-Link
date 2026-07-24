package io.github.emmansun.lightcrypto.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Main entry point for running LCL benchmarks with JSON output.
 *
 * <p>Usage:
 * <pre>
 * java -jar benchmarks.jar
 * </pre>
 *
 * <p>Results are written to {@code results/benchmark-results.json} in JMH JSON format.
 */
public final class BenchmarkRunner {

    private static final String RESULTS_DIR = "results";
    private static final String RESULTS_FILE = RESULTS_DIR + "/benchmark-results.json";

    private BenchmarkRunner() {
    }

    public static void main(String[] args) throws RunnerException {
        Options opts = new OptionsBuilder()
                .include("io.github.emmansun.lightcrypto.benchmark.*")
                .resultFormat(ResultFormatType.JSON)
                .result(RESULTS_FILE)
                .build();

        new Runner(opts).run();
    }
}
