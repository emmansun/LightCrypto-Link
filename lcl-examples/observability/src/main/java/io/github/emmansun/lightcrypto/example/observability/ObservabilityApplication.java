package io.github.emmansun.lightcrypto.example.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Observability demo application.
 * <p>
 * Demonstrates:
 * <ul>
 *   <li>EventBus structured events (logged via Slf4jEventBus)</li>
 *   <li>Micrometer metrics (Timer/Counter for encrypt operations)</li>
 *   <li>Actuator health indicator (/actuator/health shows LCL status)</li>
 * </ul>
 * <p>
 * Run with: {@code mvn spring-boot:run}
 * <p>
 * Then check:
 * <ul>
 *   <li>Console output for structured events</li>
 *   <li>GET /actuator/health for LCL health status</li>
 *   <li>GET /actuator/metrics/lcl.crypto.encrypt for encryption metrics</li>
 * </ul>
 */
@SpringBootApplication
public class ObservabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApplication.class, args);
    }
}
