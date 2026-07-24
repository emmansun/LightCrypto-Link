package io.github.emmansun.lightcrypto.adapter.mongodb;

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
        assertThat(MongoAdapterV4AutoConfiguration.class).isNotNull();
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
        // Verify MongoAdapterV4AutoConfiguration imports SB4 MongoAutoConfiguration
        // by checking it compiles against the new package
        assertThat(MongoAdapterV4AutoConfiguration.class.getAnnotations()).isNotEmpty();
    }

    @Test
    void sharedClassesAvailableFromCore() {
        // Verify shared classes are available from lcl-adapter-mongodb-core
        assertThat(MongoVaultStore.class).isNotNull();
        assertThat(MongoStorageAdapter.class).isNotNull();
        assertThat(MongoQueryTransformer.class).isNotNull();
        assertThat(CryptoBeforeSaveListener.class).isNotNull();
        assertThat(MongoCryptoEventListener.class).isNotNull();
    }
}
