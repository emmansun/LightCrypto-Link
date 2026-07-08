package io.github.emmansun.lightcrypto.provider.alibaba;

import io.github.emmansun.lightcrypto.provider.CmkProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlibabaKmsCmkAutoConfigurationTest {

    private final AlibabaKmsCmkAutoConfiguration autoConfiguration = new AlibabaKmsCmkAutoConfiguration();

    @Test
    void cmkProvider_shouldCreateProviderWithoutRemoteCallsWhenVersionAndPemAreConfigured() throws Exception {
        AlibabaKmsCmkProperties properties = validProperties();
        properties.setKeyVersionId("v-test");
        properties.setPublicKey(generateRsaPem());

        CmkProvider cmkProvider = autoConfiguration.cmkProvider(properties);

        assertThat(cmkProvider).isInstanceOf(AlibabaKmsCmkProvider.class);
        assertThat(cmkProvider.getProviderId()).isEqualTo("alibaba-kms");
        assertThat(cmkProvider.getPublicReference()).isEqualTo("kms-key-1:v-test");
    }

    @Test
    void validateProperties_shouldRejectBlankAccessKeyId() throws Exception {
        AlibabaKmsCmkProperties properties = validProperties();
        properties.setAccessKeyId(" ");

        assertThatThrownBy(() -> invokePrivate("validateProperties", new Class[]{AlibabaKmsCmkProperties.class}, properties))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("lcl.crypto.alibaba.access-key-id must not be null or blank");
    }

    @Test
    void validateProperties_shouldRejectBlankAccessKeySecret() throws Exception {
        AlibabaKmsCmkProperties properties = validProperties();
        properties.setAccessKeySecret(" ");

        assertThatThrownBy(() -> invokePrivate("validateProperties", new Class[]{AlibabaKmsCmkProperties.class}, properties))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("lcl.crypto.alibaba.access-key-secret must not be null or blank");
    }

    @Test
    void resolveKeyVersionId_shouldReturnConfiguredValue() throws Exception {
        AlibabaKmsCmkProperties properties = validProperties();
        properties.setKeyVersionId("v-manual");

        Object version = invokePrivate(
                "resolveKeyVersionId",
                new Class[]{AlibabaKmsCmkProperties.class, com.aliyun.kms20160120.Client.class},
                properties,
                buildKmsClient(properties)
        );

        assertThat(version).isEqualTo("v-manual");
    }

    @Test
    void resolvePublicKey_shouldUseConfiguredPem() throws Exception {
        AlibabaKmsCmkProperties properties = validProperties();
        properties.setPublicKey(generateRsaPem());

        Object publicKey = invokePrivate(
                "resolvePublicKey",
                new Class[]{AlibabaKmsCmkProperties.class, com.aliyun.kms20160120.Client.class, String.class},
                properties,
                buildKmsClient(properties),
                "v1"
        );

        assertThat(publicKey).isInstanceOf(java.security.PublicKey.class);
        assertThat(((java.security.PublicKey) publicKey).getAlgorithm()).isEqualTo("RSA");
    }

    private Object invokePrivate(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = AlibabaKmsCmkAutoConfiguration.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(autoConfiguration, args);
    }

    private com.aliyun.kms20160120.Client buildKmsClient(AlibabaKmsCmkProperties properties) throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret())
                .setRegionId(properties.getRegionId());
        return new com.aliyun.kms20160120.Client(config);
    }

    private AlibabaKmsCmkProperties validProperties() {
        AlibabaKmsCmkProperties properties = new AlibabaKmsCmkProperties();
        properties.setRegionId("cn-hangzhou");
        properties.setKeyId("kms-key-1");
        properties.setAccessKeyId("ak");
        properties.setAccessKeySecret("sk");
        return properties;
    }

    private String generateRsaPem() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        String base64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        StringBuilder builder = new StringBuilder();
        builder.append("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            builder.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        builder.append("-----END PUBLIC KEY-----");
        return builder.toString();
    }
}
