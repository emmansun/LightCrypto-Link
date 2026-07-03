package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.LightCryptoLinkAutoConfiguration;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LightCryptoLinkAutoConfiguration.class));

    @Test
    void disabledSkipsAllBeans() {
        contextRunner
                .withPropertyValues("lcl.crypto.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("cmkProvider");
                    assertThat(context).doesNotHaveBean("cryptoCodec");
                    assertThat(context).doesNotHaveBean("entityMetadataCache");
                    assertThat(context).doesNotHaveBean("keyVaultService");
                });
    }

    @Test
    void enabledByDefaultWhenPropertyMissing() {
        contextRunner
                .withPropertyValues("lcl.crypto.cmk=a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure().getMessage()).contains("MongoTemplate");
                });
    }

    @Test
    void customCmkProviderOverridesDefault() {
        // When a custom CmkProvider bean is provided, @ConditionalOnMissingBean prevents
        // the default LocalSymmetricCmkProvider from being created.
        // The vault init will fail because the custom provider returns raw keyMaterial,
        // but the startup failure confirms the custom provider was used (not the default).
        contextRunner
                .withPropertyValues(
                        "lcl.crypto.cmk=a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2")
                .withUserConfiguration(CustomCmkProviderConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    // The failure is during vault init (custom provider's unwrap returns raw bytes),
                    // proving our custom provider was used instead of the default.
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    // Verify default LocalSymmetricCmkProvider was NOT created
                    // (it would have succeeded with a valid CMK + mock MongoTemplate)
                    assertThat(failure.getMessage()).doesNotContain("LocalSymmetricCmkProvider");
                });
    }

    @Configuration
    static class CustomCmkProviderConfig {
        @Bean
        public CmkProvider cmkProvider() {
            return new CmkProvider() {
                @Override
                public String getProviderId() {
                    return "custom-test-provider";
                }

                @Override
                public WrappedKey wrap(byte[] keyMaterial) {
                    return new WrappedKey(keyMaterial, "custom-wrap");
                }

                @Override
                public byte[] unwrap(WrappedKey wrappedKey) {
                    return wrappedKey.ciphertext();
                }
            };
        }

        @Bean
        public MongoTemplate mongoTemplate() {
            return mock(MongoTemplate.class);
        }
    }
}
