package io.github.emmansun.lightcrypto.diagnostics;

import io.github.emmansun.lightcrypto.core.bootstrap.BootstrapContext;
import io.github.emmansun.lightcrypto.core.bootstrap.KatRunner;
import io.github.emmansun.lightcrypto.core.bootstrap.PhaseResult;
import io.github.emmansun.lightcrypto.core.event.EventBus;
import io.github.emmansun.lightcrypto.core.event.NoOpEventBus;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot Actuator endpoint exposing KAT (Known Answer Test) results.
 * <p>
 * Accessible at {@code /actuator/lclkat}.
 * Supports GET for latest results and POST for on-demand re-run.
 *
 * @since 1.0.0
 */
@Endpoint(id = "lclkat")
public class LclKatEndpoint {

    private final LclBootstrapRunner bootstrapRunner;
    private final CmkProvider cmkProvider;

    public LclKatEndpoint(LclBootstrapRunner bootstrapRunner, CmkProvider cmkProvider) {
        this.bootstrapRunner = bootstrapRunner;
        this.cmkProvider = cmkProvider;
    }

    @ReadOperation
    public Map<String, Object> katResults() {
        return buildResponse(bootstrapRunner.getKatRunner());
    }

    @WriteOperation
    public Map<String, Object> rerunKat() {
        KatRunner freshRunner = new KatRunner();
        BootstrapContext context = BootstrapContext.builder()
                .cmkProvider(cmkProvider)
                .eventBus(NoOpEventBus.INSTANCE)
                .strictMode(true)
                .spiVersion(1)
                .bootstrapTimeout(Duration.ofSeconds(5))
                .build();
        freshRunner.check(context);
        return buildResponse(freshRunner);
    }

    private Map<String, Object> buildResponse(KatRunner katRunner) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, KatRunner.KatPrimitiveResult> results = katRunner.getLastResults();

        if (results.isEmpty()) {
            response.put("status", "NOT_RUN");
            return response;
        }

        boolean allPassed = results.values().stream().allMatch(KatRunner.KatPrimitiveResult::passed);
        response.put("status", allPassed ? "OK" : "FAILED");

        Map<String, Object> algorithms = new LinkedHashMap<>();
        for (Map.Entry<String, KatRunner.KatPrimitiveResult> entry : results.entrySet()) {
            Map<String, Object> algResult = new LinkedHashMap<>();
            algResult.put("passed", entry.getValue().passed());
            algResult.put("durationMs", entry.getValue().durationMs());
            if (entry.getValue().error() != null) {
                algResult.put("error", entry.getValue().error());
            }
            algorithms.put(entry.getKey(), algResult);
        }
        response.put("algorithms", algorithms);

        long totalMs = results.values().stream().mapToLong(KatRunner.KatPrimitiveResult::durationMs).sum();
        response.put("totalDurationMs", totalMs);
        response.put("budgetMs", KatRunner.TOTAL_BUDGET_MS);

        return response;
    }
}
