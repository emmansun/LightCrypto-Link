package io.github.emmansun.lightcrypto.diagnostics;

import io.github.emmansun.lightcrypto.core.SdkVersion;
import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapResult;
import io.github.emmansun.lightcrypto.core.bootstrap.PhaseResult;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot Actuator endpoint exposing LCL health diagnostics.
 * <p>
 * Accessible at {@code /actuator/lclhealth}.
 *
 * @since 1.0.0
 */
@Endpoint(id = "lclhealth")
public class LclHealthEndpoint {

    private final LclBootstrapRunner bootstrapRunner;

    public LclHealthEndpoint(LclBootstrapRunner bootstrapRunner) {
        this.bootstrapRunner = bootstrapRunner;
    }

    @ReadOperation
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        BootstrapResult result = bootstrapRunner.getLastResult();

        if (result == null) {
            response.put("status", "STARTING");
            response.put("sdkLanguage", "java");
            response.put("sdkVersion", SdkVersion.getVersion());
            response.put("spiVersion", 1);
            response.put("wireFormatVersion", 1);
            return response;
        }

        response.put("status", result.status().name());
        response.put("sdkLanguage", "java");
        response.put("sdkVersion", SdkVersion.getVersion());
        response.put("spiVersion", 1);
        response.put("wireFormatVersion", 1);

        // Component statuses from phase results
        Map<String, String> components = new LinkedHashMap<>();
        for (PhaseResult pr : result.phaseResults()) {
            String componentName = extractComponentName(pr.phaseName());
            components.put(componentName, pr.success() ? "OK" : "DOWN");
        }
        response.put("components", components);

        if (result.timestamp() != null) {
            response.put("lastBootstrap", DateTimeFormatter.ISO_INSTANT.format(result.timestamp()));
        }
        response.put("bootstrapDurationMs", result.durationMs());

        if (result.failedPhase() != null) {
            response.put("failedPhase", result.failedPhase());
            response.put("errorDetails", redact(result.errorDetails()));
        }

        return response;
    }

    private static String extractComponentName(String phaseName) {
        // "BOOT-4 KAT" -> "kat", "BOOT-9 KMS" -> "kms"
        String[] parts = phaseName.split("\\s+");
        return parts[parts.length - 1].toLowerCase();
    }

    /**
     * Redacts sensitive information from error details.
     */
    static String redact(String input) {
        if (input == null) {
            return null;
        }
        // Mask potential key material or identifiers
        return input.replaceAll("(?i)(key|secret|password|token)=\\S+", "$1=***");
    }
}
