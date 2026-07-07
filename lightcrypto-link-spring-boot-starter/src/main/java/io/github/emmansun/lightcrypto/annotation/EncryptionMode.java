package io.github.emmansun.lightcrypto.annotation;

/**
 * Encryption traversal mode for container and object fields.
 */
public enum EncryptionMode {
    /**
     * Keep backward-compatible behavior:
     * - scalar/simple fields: field-level encryption
     * - POJO fields: whole-object encryption
     * - collection/map of simple values: element-level encryption
     * - collection/map of POJO values: whole-object encryption
     */
    AUTO,

    /**
     * Encrypt each container element/map value independently.
     */
    ELEMENT,

    /**
     * Encrypt the entire field payload as a single blob.
     */
    WHOLE
}
