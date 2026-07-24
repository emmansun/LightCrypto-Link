package io.github.emmansun.lightcrypto.observability;

import io.github.emmansun.lightcrypto.core.SdkVersion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure logic class that composes {@link ComponentHealthCheck} results into
 * overall status and details map.
 * <p>
 * This class has no Spring Health imports, making it usable by both
 * SB3 and SB4 health indicator thin shells.
 *
 * @since 1.0.0
 */
public class LclHealthCollector {

    private final Map<String, ComponentHealthCheck> checks;

    public LclHealthCollector(Map<String, ComponentHealthCheck> checks) {
        this.checks = checks != null ? checks : Map.of();
    }

    /**
     * Collects health status from all registered checks.
     *
     * @return result containing overall status and component details
     */
    public HealthResult collect() {
        LclHealthStatus overall = LclHealthStatus.READY;
        Map<String, String> componentDetails = new LinkedHashMap<>();

        for (Map.Entry<String, ComponentHealthCheck> entry : checks.entrySet()) {
            LclHealthStatus status;
            try {
                status = entry.getValue().check();
            } catch (Exception e) {
                status = LclHealthStatus.FAILED;
            }
            componentDetails.put(entry.getKey(), status.name());
            overall = LclHealthStatus.worst(overall, status);
        }

        componentDetails.put("overall", overall.name());
        componentDetails.put("sdkVersion", SdkVersion.getVersion());

        return new HealthResult(overall, componentDetails);
    }

    /**
     * Result of health collection.
     */
    public record HealthResult(LclHealthStatus overall, Map<String, String> details) {
        public HealthResult {
            details = Map.copyOf(details);
        }
    }
}
