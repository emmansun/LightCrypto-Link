package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.config.ConfigurationValidator;
import io.github.emmansun.lightcrypto.config.CryptographyProperties;
import io.github.emmansun.lightcrypto.config.KmsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConfigurationValidator}.
 */
class ConfigurationValidatorTest {

    private final ConfigurationValidator validator = new ConfigurationValidator();

    // ===== KmsProperties validation =====

    @Test
    void localSymmetricWithoutKeyHexOrKeyHexFileIsRejected() {
        KmsProperties props = new KmsProperties();
        KmsProperties.ProviderEntry entry = new KmsProperties.ProviderEntry();
        entry.setId("local");
        entry.setType(KmsProperties.ProviderType.LOCAL_SYMMETRIC);
        // no keyHex or keyHexFile
        props.setProviders(List.of(entry));

        Errors errors = new BeanPropertyBindingResult(props, "kmsProperties");
        validator.validate(props, errors);

        assertThat(errors.hasErrors()).isTrue();
        assertThat(errors.getAllErrors().stream().anyMatch(e ->
                e.getDefaultMessage().contains("must have either 'keyHex' or 'keyHexFile'"))).isTrue();
    }

    @Test
    void localSymmetricWithKeyHexIsValid() {
        KmsProperties props = new KmsProperties();
        KmsProperties.ProviderEntry entry = new KmsProperties.ProviderEntry();
        entry.setId("local");
        entry.setType(KmsProperties.ProviderType.LOCAL_SYMMETRIC);
        entry.setKeyHex("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2");
        props.setProviders(List.of(entry));

        Errors errors = new BeanPropertyBindingResult(props, "kmsProperties");
        validator.validate(props, errors);

        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    void duplicateProviderIdsAreRejected() {
        KmsProperties props = new KmsProperties();
        KmsProperties.ProviderEntry entry1 = new KmsProperties.ProviderEntry();
        entry1.setId("dup");
        entry1.setType(KmsProperties.ProviderType.ALIYUN);
        entry1.setConfig(Map.of("keyId", "k1"));
        KmsProperties.ProviderEntry entry2 = new KmsProperties.ProviderEntry();
        entry2.setId("dup");
        entry2.setType(KmsProperties.ProviderType.AZURE);
        entry2.setConfig(Map.of("vaultUri", "v1"));
        props.setProviders(List.of(entry1, entry2));

        Errors errors = new BeanPropertyBindingResult(props, "kmsProperties");
        validator.validate(props, errors);

        assertThat(errors.hasErrors()).isTrue();
        assertThat(errors.getAllErrors().stream().anyMatch(e ->
                e.getDefaultMessage().contains("Duplicate provider ID"))).isTrue();
    }

    @Test
    void emptyProvidersListIsValid() {
        KmsProperties props = new KmsProperties();
        // providers is empty by default

        Errors errors = new BeanPropertyBindingResult(props, "kmsProperties");
        validator.validate(props, errors);

        assertThat(errors.hasErrors()).isFalse();
    }

    // ===== CryptographyProperties validation =====

    @Test
    void defaultAlgorithmNotInAllowedListIsRejected() {
        CryptographyProperties props = new CryptographyProperties();
        props.setDefaultAlgorithm(SymmetricAlgorithm.SM4_GCM);
        props.setAllowedAlgorithms(List.of(SymmetricAlgorithm.AES_256_GCM, SymmetricAlgorithm.AES_256_CBC));

        Errors errors = new BeanPropertyBindingResult(props, "cryptographyProperties");
        validator.validate(props, errors);

        assertThat(errors.hasErrors()).isTrue();
        assertThat(errors.getAllErrors().stream().anyMatch(e ->
                e.getDefaultMessage().contains("not in the allowed algorithms list"))).isTrue();
    }

    @Test
    void defaultAlgorithmInAllowedListIsValid() {
        CryptographyProperties props = new CryptographyProperties();
        props.setDefaultAlgorithm(SymmetricAlgorithm.SM4_GCM);
        props.setAllowedAlgorithms(List.of(SymmetricAlgorithm.AES_256_GCM, SymmetricAlgorithm.SM4_GCM));

        Errors errors = new BeanPropertyBindingResult(props, "cryptographyProperties");
        validator.validate(props, errors);

        assertThat(errors.hasErrors()).isFalse();
    }
}
