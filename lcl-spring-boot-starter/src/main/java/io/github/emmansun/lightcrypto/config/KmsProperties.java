package io.github.emmansun.lightcrypto.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KMS provider configuration properties.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "lightcrypto.kms")
public class KmsProperties {

    /** List of KMS provider entries. */
    @Valid
    private List<ProviderEntry> providers = new ArrayList<>();

    /**
     * A single KMS provider entry.
     */
    @Data
    public static class ProviderEntry {

        /** Unique provider identifier. */
        @NotBlank
        private String id;

        /** Provider type (LOCAL_SYMMETRIC, ALIYUN, AZURE). */
        @NotNull
        private ProviderType type;

        /** Inline hex-encoded symmetric key (for LOCAL_SYMMETRIC). */
        private String keyHex;

        /** Path to file containing hex-encoded symmetric key (alternative to keyHex). */
        private String keyHexFile;

        /** Provider-specific configuration (e.g., regionId, keyId for cloud KMS). */
        private Map<String, String> config;
    }

    /** Supported KMS provider types. */
    public enum ProviderType {
        LOCAL_SYMMETRIC,
        ALIYUN,
        AZURE
    }
}
