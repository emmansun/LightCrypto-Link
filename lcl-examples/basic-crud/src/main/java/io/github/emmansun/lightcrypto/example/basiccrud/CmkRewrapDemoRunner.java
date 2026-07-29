package io.github.emmansun.lightcrypto.example.basiccrud;

import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.RewrapResult;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Demonstrates cross-CMK provider re-wrap using the programmatic API.
 * <p>
 * This example simulates migrating from LOCAL_SYMMETRIC to a "cloud" provider
 * (represented here by a simple identity provider for demonstration purposes).
 * </p>
 * <p>
 * Enable via: {@code lcl.demo.rewrap.enabled=true}
 * </p>
 */
@Component
@ConditionalOnProperty(name = "lcl.demo.rewrap.enabled", havingValue = "true")
public class CmkRewrapDemoRunner implements CommandLineRunner {

    private final KeyVaultService keyVaultService;

    public CmkRewrapDemoRunner(KeyVaultService keyVaultService) {
        this.keyVaultService = keyVaultService;
    }

    @Override
    public void run(String... args) {
        System.out.println("[REWRAP-DEMO] Starting cross-CMK re-wrap demonstration");

        // Simulated target provider (in production, this would be Azure Key Vault or Alibaba KMS)
        CmkProvider simulatedCloudProvider = new SimulatedCloudProvider();

        System.out.println("[REWRAP-DEMO] Target provider: " + simulatedCloudProvider.getProviderId());
        System.out.println("[REWRAP-DEMO] Calling rewrapAllVaults...");

        List<RewrapResult> results = keyVaultService.rewrapAllVaults(simulatedCloudProvider);

        long successCount = results.stream().filter(RewrapResult::success).count();
        long failedCount = results.size() - successCount;

        System.out.println("[REWRAP-DEMO] Results: total=" + results.size()
                + ", success=" + successCount + ", failed=" + failedCount);

        for (RewrapResult result : results) {
            if (result.success()) {
                System.out.println("[REWRAP-DEMO]   OK: " + result.namespace()
                        + " (" + result.keyCount() + " keys, " + result.durationMicros() + " micros)");
            } else {
                System.out.println("[REWRAP-DEMO]   FAIL: " + result.namespace()
                        + " — " + result.errorMessage());
            }
        }

        System.out.println("[REWRAP-DEMO] Demonstration complete.");
    }

    /**
     * Simulated cloud CMK provider for demonstration.
     * Uses identity wrapping (no real encryption) — replace with a real provider in production.
     */
    private static class SimulatedCloudProvider implements CmkProvider {

        @Override
        public String getProviderId() {
            return "simulated-cloud";
        }

        @Override
        public String getPublicReference() {
            return "simulated-cloud-key-v1";
        }

        @Override
        public boolean supportsAlgorithm(String lclAlgorithm) {
            return "SIMULATED-WRAP".equals(lclAlgorithm);
        }

        @Override
        public String mapAlgorithm(String lclAlgorithm) {
            return "SIMULATED-WRAP";
        }

        @Override
        public WrappedKey wrap(byte[] plaintextKey) {
            // In production, this would call Azure Key Vault or Alibaba KMS
            return new WrappedKey(plaintextKey.clone(), "SIMULATED-WRAP");
        }

        @Override
        public byte[] unwrap(WrappedKey wrappedKey) {
            // In production, this would call the cloud KMS unwrap API
            return wrappedKey.ciphertext().clone();
        }
    }
}
