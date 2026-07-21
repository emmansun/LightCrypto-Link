package io.github.emmansun.lightcrypto.example.observability;

import io.github.emmansun.lightcrypto.observability.LclHealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Demonstrates LightCrypto-Link observability features:
 * <ul>
 *   <li>EventBus events — emitted on encrypt/decrypt, logged via Slf4jEventBus</li>
 *   <li>Micrometer metrics — Timer/Counter registered for crypto operations</li>
 *   <li>Health indicator — reports LCL component status</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "lcl.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final MeterRegistry meterRegistry;
    private final LclHealthIndicator healthIndicator;

    public DemoRunner(UserRepository userRepository,
                      MongoTemplate mongoTemplate,
                      MeterRegistry meterRegistry,
                      LclHealthIndicator healthIndicator) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.meterRegistry = meterRegistry;
        this.healthIndicator = healthIndicator;
    }

    @Override
    public void run(String... args) {
        // Clean up previous runs
        mongoTemplate.dropCollection(User.class);

        System.out.println("\n=== LightCrypto-Link Observability Demo ===\n");

        // 1. Perform encrypted operations — triggers EventBus events and metrics
        System.out.println("[1] Performing encrypted CRUD operations...");
        System.out.println("    (Watch console for structured EventBus events)\n");

        for (int i = 1; i <= 3; i++) {
            User user = new User();
            user.setName("User" + i);
            user.setPhone("1380013800" + i);
            user.setAge(20 + i);
            userRepository.save(user);
            System.out.println("    Saved User" + i + " — encrypt event emitted");
        }

        // Query triggers decrypt
        User found = userRepository.findByPhone("13800138001");
        System.out.println("    Queried by phone — decrypt event emitted");
        System.out.println("    Found: " + found.getName() + "\n");

        // 2. Show Micrometer metrics
        System.out.println("[2] Micrometer Metrics (lcl.crypto.encrypt):");
        Search.in(meterRegistry).name("lcl.crypto.encrypt").timers().forEach(timer -> {
            System.out.println("    Timer: " + timer.getId());
            System.out.println("    Count: " + timer.count());
            System.out.println("    Total time: " + timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) + " ms");
            System.out.println("    Mean: " + timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) + " ms\n");
        });

        Search.in(meterRegistry).name("lcl.crypto.encrypt").counters().forEach(counter -> {
            System.out.println("    Counter: " + counter.getId());
            System.out.println("    Count: " + counter.count() + "\n");
        });

        // 3. Show health indicator status
        System.out.println("[3] LCL Health Indicator:");
        var health = healthIndicator.health();
        System.out.println("    Status: " + health.getStatus());
        health.getDetails().forEach((key, value) ->
            System.out.println("    " + key + ": " + value));

        System.out.println("\n[4] Actuator Endpoints (when web server is running):");
        System.out.println("    GET /actuator/health     — includes 'lcl' component");
        System.out.println("    GET /actuator/metrics    — lists all metrics");
        System.out.println("    GET /actuator/metrics/lcl.crypto.encrypt — encryption timer");

        System.out.println("\n=== Demo complete ===\n");
    }
}
