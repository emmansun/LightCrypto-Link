package io.github.emmansun.lightcrypto.annotation;

import java.lang.annotation.*;

/**
 * Marks an entity field for transparent encrypted storage.
 * <p>
 * The SDK automatically encrypts and decrypts the field within the Spring Data
 * MongoDB write/read lifecycle — no extra code is needed in business logic.
 * </p>
 *
 * <pre>
 * // Minimal usage (all defaults)
 * &#64;Encrypted
 * private String phone;
 *
 * // Enable blind index (supports findByPhone exact-match queries)
 * &#64;Encrypted(blindIndex = true)
 * private String phone;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Encrypted {

    /**
     * Symmetric encryption algorithm, defaults to AES-256-GCM.
     */
    SymmetricAlgorithm algorithm() default SymmetricAlgorithm.AES_256_GCM;

    /**
     * Whether to generate a blind index (for exact-match queries), defaults to false.
     * Most encrypted fields do not require querying; setting this to true will generate
     * an HMAC hash and store it in the "b" sub-field of the BSON document.
     */
    boolean blindIndex() default false;

    /**
     * Field-name salt (used in HMAC blind-index computation).
     * Defaults to empty string, in which case the Java field name is used as the salt.
     * Can be explicitly set to share the same blind-index space across multiple entities.
     */
    String fieldName() default "";
}
