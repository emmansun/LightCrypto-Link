package io.github.emmansun.lightcrypto.benchmark;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for batch document encryption/decryption.
 *
 * <p>Simulates an 8-field document with mixed types (String, Integer, nested object).
 * Target: per-field p95 < 250µs (total / 8).
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
public class BatchDocumentBenchmark {

    private static final int FIELD_COUNT = 8;

    private byte[] dek;
    private Namespace namespace;
    private List<byte[]> plaintextFields;
    private List<String> encryptedFields;

    @Setup(Level.Trial)
    public void setup() {
        SecureRandom random = new SecureRandom();
        dek = new byte[32];
        random.nextBytes(dek);

        namespace = Namespace.of("bench", "test", "Document", "field");

        // Simulate 8 fields: 5 Strings, 2 Integers, 1 nested object (as JSON string)
        plaintextFields = new ArrayList<>(FIELD_COUNT);
        plaintextFields.add("John Doe".getBytes(StandardCharsets.UTF_8));           // String: name
        plaintextFields.add("john@example.com".getBytes(StandardCharsets.UTF_8));   // String: email
        plaintextFields.add("+1-555-0123".getBytes(StandardCharsets.UTF_8));        // String: phone
        plaintextFields.add("123 Main St, City".getBytes(StandardCharsets.UTF_8));  // String: address
        plaintextFields.add("SSN-123-45-6789".getBytes(StandardCharsets.UTF_8));    // String: ssn
        plaintextFields.add(String.valueOf(30).getBytes(StandardCharsets.UTF_8));   // Integer: age
        plaintextFields.add(String.valueOf(75000).getBytes(StandardCharsets.UTF_8)); // Integer: salary
        plaintextFields.add("{\"city\":\"NYC\",\"zip\":\"10001\"}".getBytes(StandardCharsets.UTF_8)); // nested

        // Pre-encrypt for decrypt benchmark
        encryptedFields = new ArrayList<>(FIELD_COUNT);
        for (byte[] field : plaintextFields) {
            encryptedFields.add(CryptoCodec.encrypt(dek, field, AlgorithmId.AES_256_GCM, namespace, 1));
        }
    }

    @Benchmark
    public List<String> encryptDocument() {
        List<String> results = new ArrayList<>(FIELD_COUNT);
        for (byte[] field : plaintextFields) {
            results.add(CryptoCodec.encrypt(dek, field, AlgorithmId.AES_256_GCM, namespace, 1));
        }
        return results;
    }

    @Benchmark
    public List<byte[]> decryptDocument() {
        List<byte[]> results = new ArrayList<>(FIELD_COUNT);
        for (String blob : encryptedFields) {
            results.add(CryptoCodec.decrypt(dek, blob));
        }
        return results;
    }
}
