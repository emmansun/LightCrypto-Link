package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Disables flapdoodle embedded MongoDB when running on GitHub Actions.
 * On CI, a real MongoDB is provided by the service container on port 27017.
 * <p>
 * This initializer runs before context refresh, ensuring properties are set
 * before any auto-configuration is evaluated.
 */
public class MongoCiInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        if ("true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
            ctx.getEnvironment().getSystemProperties().put(
                    "spring.autoconfigure.exclude",
                    "de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration");
            ctx.getEnvironment().getSystemProperties().put(
                    "spring.data.mongodb.uri",
                    "mongodb://localhost:27017/test");
        }
    }
}
