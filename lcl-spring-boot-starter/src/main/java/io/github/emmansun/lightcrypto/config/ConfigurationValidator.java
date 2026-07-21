package io.github.emmansun.lightcrypto.config;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cross-field configuration validator for KMS and cryptography properties.
 *
 * <p>Validates:
 * <ul>
 *   <li>{@code defaultAlgorithm} must be in {@code allowedAlgorithms}</li>
 *   <li>{@code LOCAL_SYMMETRIC} provider must have {@code keyHex} or {@code keyHexFile}</li>
 *   <li>Provider {@code id} values must be unique</li>
 * </ul>
 */
public class ConfigurationValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return KmsProperties.class.isAssignableFrom(clazz)
                || CryptographyProperties.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        if (target instanceof KmsProperties kms) {
            validateKmsProperties(kms, errors);
        } else if (target instanceof CryptographyProperties crypto) {
            validateCryptographyProperties(crypto, errors);
        }
    }

    private void validateKmsProperties(KmsProperties props, Errors errors) {
        List<KmsProperties.ProviderEntry> providers = props.getProviders();
        if (providers == null || providers.isEmpty()) {
            return;
        }

        // Check provider ID uniqueness
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < providers.size(); i++) {
            KmsProperties.ProviderEntry entry = providers.get(i);
            if (entry.getId() != null && !ids.add(entry.getId())) {
                errors.rejectValue("providers[" + i + "].id",
                        "duplicate.provider.id",
                        "Duplicate provider ID: '" + entry.getId() + "'");
            }

            // LOCAL_SYMMETRIC must have keyHex or keyHexFile
            if (entry.getType() == KmsProperties.ProviderType.LOCAL_SYMMETRIC) {
                boolean hasKeyHex = entry.getKeyHex() != null && !entry.getKeyHex().isBlank();
                boolean hasKeyHexFile = entry.getKeyHexFile() != null && !entry.getKeyHexFile().isBlank();
                if (!hasKeyHex && !hasKeyHexFile) {
                    errors.rejectValue("providers[" + i + "]",
                            "missing.local.symmetric.key",
                            "LOCAL_SYMMETRIC provider '" + entry.getId() + "' must have either 'keyHex' or 'keyHexFile'");
                }
            }
        }
    }

    private void validateCryptographyProperties(CryptographyProperties props, Errors errors) {
        SymmetricAlgorithm defaultAlg = props.getDefaultAlgorithm();
        List<SymmetricAlgorithm> allowed = props.getAllowedAlgorithms();
        if (defaultAlg != null && allowed != null && !allowed.contains(defaultAlg)) {
            errors.rejectValue("defaultAlgorithm",
                    "invalid.default.algorithm",
                    "Default algorithm '" + defaultAlg + "' is not in the allowed algorithms list");
        }
    }
}
