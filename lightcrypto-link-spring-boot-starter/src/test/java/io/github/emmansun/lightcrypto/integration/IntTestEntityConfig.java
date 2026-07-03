package io.github.emmansun.lightcrypto.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.Set;

/**
 * Ensure Spring Data MongoDB mapping context knows about test entity types,
 * so _class field is written to documents.
 */
@Configuration
public class IntTestEntityConfig {

    @Bean
    public MongoMappingContextInitializer mappingContextInitializer(MongoMappingContext mappingContext) {
        mappingContext.setInitialEntitySet(Set.of(
                IntTestUser.class,
                IntTestEmployee.class
        ));
        return new MongoMappingContextInitializer();
    }

    /** Placeholder bean to trigger mapping context initialization */
    public static class MongoMappingContextInitializer {
    }
}
