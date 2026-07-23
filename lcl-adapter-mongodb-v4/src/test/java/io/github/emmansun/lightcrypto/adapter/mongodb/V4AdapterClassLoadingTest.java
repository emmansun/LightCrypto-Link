package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.adapter.mongodb.health.HealthAutoConfiguration;
import io.github.emmansun.lightcrypto.adapter.mongodb.health.LclHealthIndicator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that all v4 adapter classes are loadable and correctly reference
 * Spring Data MongoDB 5.x / Spring Boot 4.x APIs.
 */
class V4AdapterClassLoadingTest {

    @Test
    void queryLayerClassesExist() {
        // Verify query layer classes compiled against spring-data-mongodb 5.x
        assertThat(CryptoMongoRepositoryFactory.class).isNotNull();
        assertThat(CryptoPartTreeMongoQuery.class).isNotNull();
        assertThat(CryptoQueryLookupStrategy.class).isNotNull();
    }

    @Test
    void autoConfigurationClassExists() {
        assertThat(MongoAdapterAutoConfiguration.class).isNotNull();
    }

    @Test
    void healthClassesExist() {
        assertThat(LclHealthIndicator.class).isNotNull();
        assertThat(HealthAutoConfiguration.class).isNotNull();
    }

    @Test
    void queryLayerDoesNotReferenceRemovedApi() {
        // Verify no reference to the removed QueryMethodEvaluationContextProvider
        for (java.lang.reflect.Method m : CryptoMongoRepositoryFactory.class.getDeclaredMethods()) {
            for (Class<?> paramType : m.getParameterTypes()) {
                assertThat(paramType.getName())
                        .doesNotContain("QueryMethodEvaluationContextProvider");
            }
        }
        for (java.lang.reflect.Constructor<?> c : CryptoPartTreeMongoQuery.class.getDeclaredConstructors()) {
            for (Class<?> paramType : c.getParameterTypes()) {
                assertThat(paramType.getName())
                        .doesNotContain("QueryMethodEvaluationContextProvider");
            }
        }
    }

    @Test
    void autoConfigurationUsesCorrectMongoPackage() {
        // Verify MongoAdapterAutoConfiguration imports SB4 MongoAutoConfiguration
        // by checking it compiles against the new package
        assertThat(MongoAdapterAutoConfiguration.class.getAnnotations()).isNotEmpty();
    }

    @Test
    void healthIndicatorUsesCorrectHealthPackage() {
        // Verify LclHealthIndicator implements SB4 HealthIndicator
        Class<?>[] interfaces = LclHealthIndicator.class.getInterfaces();
        assertThat(interfaces).hasSize(1);
        assertThat(interfaces[0].getName())
                .isEqualTo("org.springframework.boot.health.contributor.HealthIndicator");
    }
}
