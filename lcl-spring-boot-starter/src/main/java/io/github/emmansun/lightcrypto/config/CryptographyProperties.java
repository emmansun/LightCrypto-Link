package io.github.emmansun.lightcrypto.config;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.List;

/**
 * Cryptography configuration properties.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "lightcrypto.cryptography")
public class CryptographyProperties {

    /** Global default symmetric algorithm for @Encrypted fields (default: AES_256_GCM). */
    @NotNull
    private SymmetricAlgorithm defaultAlgorithm = SymmetricAlgorithm.AES_256_GCM;

    /** List of allowed symmetric algorithms (default: all four). */
    private List<SymmetricAlgorithm> allowedAlgorithms = Arrays.asList(
            SymmetricAlgorithm.AES_256_GCM,
            SymmetricAlgorithm.AES_256_CBC,
            SymmetricAlgorithm.SM4_GCM,
            SymmetricAlgorithm.SM4_CBC
    );

    /** When true, only AEAD algorithms (GCM) are permitted (default: false). */
    private boolean requireAead = false;
}
