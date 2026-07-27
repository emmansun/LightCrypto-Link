package io.github.emmansun.lightcrypto.config;

import io.github.emmansun.lightcrypto.exception.ConfigurationException;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LightCryptoLinkAutoConfigurationTest {

    @Test
    void resolveKeyHexUsesInlineValueWhenPresent() throws Exception {
        Path file = Files.createTempFile("lcl-key", ".txt");
        Files.writeString(file, "bb".repeat(32), StandardCharsets.UTF_8);

        KmsProperties.ProviderEntry entry = provider("p1", KmsProperties.ProviderType.LOCAL_SYMMETRIC);
        entry.setKeyHex("aa".repeat(32));
        entry.setKeyHexFile(file.toString());

        byte[] resolved = LightCryptoLinkAutoConfiguration.resolveKeyHex(entry);

        assertThat(resolved).hasSize(32).containsOnly((byte) 0xAA);
    }

    @Test
    void resolveKeyHexReadsFromFileWhenInlineMissing() throws Exception {
        Path file = Files.createTempFile("lcl-key", ".txt");
        Files.writeString(file, "0f".repeat(32), StandardCharsets.UTF_8);

        KmsProperties.ProviderEntry entry = provider("p1", KmsProperties.ProviderType.LOCAL_SYMMETRIC);
        entry.setKeyHexFile(file.toString());

        byte[] resolved = LightCryptoLinkAutoConfiguration.resolveKeyHex(entry);

        assertThat(resolved).hasSize(32).containsOnly((byte) 0x0F);
    }

    @Test
    void resolveKeyHexThrowsWhenBothInlineAndFileMissing() {
        KmsProperties.ProviderEntry entry = provider("p1", KmsProperties.ProviderType.LOCAL_SYMMETRIC);

        assertThatThrownBy(() -> LightCryptoLinkAutoConfiguration.resolveKeyHex(entry))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("must have either 'keyHex' or 'keyHexFile'");
    }

    @Test
    void resolveKeyHexThrowsWhenFileCannotBeRead() {
        KmsProperties.ProviderEntry entry = provider("p1", KmsProperties.ProviderType.LOCAL_SYMMETRIC);
        entry.setKeyHexFile("D:/not-exists/lcl-key.txt");

        assertThatThrownBy(() -> LightCryptoLinkAutoConfiguration.resolveKeyHex(entry))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Failed to read keyHexFile");
    }

    @Test
    void cmkProviderReturnsLocalProviderForMatchingEntry() {
        KmsProperties.ProviderEntry azure = provider("az", KmsProperties.ProviderType.AZURE);
        KmsProperties.ProviderEntry local = provider("local", KmsProperties.ProviderType.LOCAL_SYMMETRIC);
        local.setKeyHex("11".repeat(32));

        KmsProperties properties = new KmsProperties();
        properties.setProviders(List.of(azure, local));

        CmkProvider provider = new LightCryptoLinkAutoConfiguration().cmkProvider(properties);

        assertThat(provider.getProviderId()).isEqualTo("local-symmetric");
    }

    @Test
    void cmkProviderThrowsWhenNoLocalSymmetricProvider() {
        KmsProperties.ProviderEntry azure = provider("az", KmsProperties.ProviderType.AZURE);
        KmsProperties properties = new KmsProperties();
        properties.setProviders(List.of(azure));

        assertThatThrownBy(() -> new LightCryptoLinkAutoConfiguration().cmkProvider(properties))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("No LOCAL_SYMMETRIC provider found");
    }

    @Test
    void localConditionMatchesWhenFirstProviderIsLocalSymmetric() {
        Environment env = new MockEnvironment()
                .withProperty("lightcrypto.kms.providers[0].type", "LOCAL_SYMMETRIC");

        boolean matches = new LightCryptoLinkAutoConfiguration.LocalSymmetricProviderCondition()
                .matches(conditionContext(env), null);

        assertThat(matches).isTrue();
    }

    @Test
    void localConditionMatchesWhenLaterProviderIsLocalSymmetric() {
        Environment env = new MockEnvironment()
                .withProperty("lightcrypto.kms.providers[0].type", "AZURE")
                .withProperty("lightcrypto.kms.providers[1].type", "ALIYUN")
                .withProperty("lightcrypto.kms.providers[2].type", "LOCAL_SYMMETRIC");

        boolean matches = new LightCryptoLinkAutoConfiguration.LocalSymmetricProviderCondition()
                .matches(conditionContext(env), null);

        assertThat(matches).isTrue();
    }

    @Test
    void localConditionDoesNotMatchWithoutLocalSymmetricProvider() {
        Environment env = new MockEnvironment()
                .withProperty("lightcrypto.kms.providers[0].type", "AZURE")
                .withProperty("lightcrypto.kms.providers[1].type", "ALIYUN");

        boolean matches = new LightCryptoLinkAutoConfiguration.LocalSymmetricProviderCondition()
                .matches(conditionContext(env), null);

        assertThat(matches).isFalse();
    }

    private static KmsProperties.ProviderEntry provider(String id, KmsProperties.ProviderType type) {
        KmsProperties.ProviderEntry entry = new KmsProperties.ProviderEntry();
        entry.setId(id);
        entry.setType(type);
        return entry;
    }

    private static org.springframework.context.annotation.ConditionContext conditionContext(Environment environment) {
        return new org.springframework.context.annotation.ConditionContext() {
            @Override
            public BeanDefinitionRegistry getRegistry() {
                return null;
            }

            @Override
            public ConfigurableListableBeanFactory getBeanFactory() {
                return null;
            }

            @Override
            public Environment getEnvironment() {
                return environment;
            }

            @Override
            public ResourceLoader getResourceLoader() {
                return null;
            }

            @Override
            public ClassLoader getClassLoader() {
                return null;
            }
        };
    }
}
