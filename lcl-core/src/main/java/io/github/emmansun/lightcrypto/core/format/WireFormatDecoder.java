package io.github.emmansun.lightcrypto.core.format;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Decodes Wire Format V1 binary blobs back into their constituent fields.
 *
 * @since 1.0.0
 */
public final class WireFormatDecoder {

    /** Minimum blob size: version(1) + algId(1) + nsLen(2) + ns(1) + dekVer(4) + ivLen(1) + iv(0) + aadExtLen(2) + ct(0) = 12 */
    private static final int MIN_BLOB_SIZE = 12;

    private WireFormatDecoder() {
    }

    /**
     * Decoded representation of a Wire Format V1 blob.
     *
     * @param version    the wire format version (always 0x01 for V1)
     * @param algorithm  the algorithm identifier
     * @param namespace  the canonical namespace string
     * @param dekVersion the DEK version
     * @param iv         the initialization vector
     * @param aadExt     the AAD extension bytes (empty in V1)
     * @param ciphertext the ciphertext (GCM: CT‖Tag; CBC: PKCS7-padded CT)
     */
    public record DecodedBlob(
            byte version,
            AlgorithmId algorithm,
            String namespace,
            int dekVersion,
            byte[] iv,
            byte[] aadExt,
            byte[] ciphertext
    ) {
        /**
         * Reconstructs the AAD from the blob's metadata fields.
         *
         * @return the AAD bytes for GCM authentication
         */
        public byte[] reconstructAad() {
            return WireFormatEncoder.buildAad(algorithm, namespace, dekVersion);
        }
    }

    /**
     * Decodes a Wire Format V1 binary blob.
     *
     * @param blob the raw bytes
     * @return the decoded blob fields
     * @throws IllegalArgumentException if the blob is malformed
     */
    public static DecodedBlob decode(byte[] blob) {
        if (blob == null || blob.length < MIN_BLOB_SIZE) {
            throw new IllegalArgumentException(
                    "blob too short: expected at least " + MIN_BLOB_SIZE + " bytes, got "
                            + (blob == null ? 0 : blob.length));
        }

        ByteBuffer buf = ByteBuffer.wrap(blob);

        // Version
        byte version = buf.get();
        if (version != WireFormatEncoder.VERSION) {
            throw new IllegalArgumentException(
                    String.format("unsupported wire format version: 0x%02X (expected 0x01)", version & 0xFF));
        }

        // Algorithm ID
        byte algByte = buf.get();
        AlgorithmId algorithm = AlgorithmId.fromByte(algByte);

        // Namespace
        int nsLen = buf.getShort() & 0xFFFF;
        if (nsLen == 0) {
            throw new IllegalArgumentException("namespace length must be >= 1 (namespace is required)");
        }
        if (buf.remaining() < nsLen) {
            throw new IllegalArgumentException("blob truncated: namespace extends beyond blob");
        }
        byte[] nsBytes = new byte[nsLen];
        buf.get(nsBytes);
        String namespace = new String(nsBytes, StandardCharsets.UTF_8);

        // DEK version
        if (buf.remaining() < 4) {
            throw new IllegalArgumentException("blob truncated: missing dekVersion");
        }
        int dekVersion = buf.getInt();
        if (dekVersion < 1) {
            throw new IllegalArgumentException("dekVersion must be >= 1, got " + dekVersion);
        }

        // IV
        if (buf.remaining() < 1) {
            throw new IllegalArgumentException("blob truncated: missing ivLength");
        }
        int ivLen = buf.get() & 0xFF;
        if (buf.remaining() < ivLen) {
            throw new IllegalArgumentException("blob truncated: iv extends beyond blob");
        }
        byte[] iv = new byte[ivLen];
        buf.get(iv);

        // AAD extension
        if (buf.remaining() < 2) {
            throw new IllegalArgumentException("blob truncated: missing aadExtLength");
        }
        int aadExtLen = buf.getShort() & 0xFFFF;
        if (buf.remaining() < aadExtLen) {
            throw new IllegalArgumentException("blob truncated: aadExt extends beyond blob");
        }
        byte[] aadExt = new byte[aadExtLen];
        if (aadExtLen > 0) {
            buf.get(aadExt);
        }

        // Ciphertext (remaining bytes)
        if (buf.remaining() == 0) {
            throw new IllegalArgumentException("blob contains no ciphertext");
        }
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        return new DecodedBlob(version, algorithm, namespace, dekVersion, iv, aadExt, ciphertext);
    }

    /**
     * Decodes a Base64URL-encoded (no padding) Wire Format V1 string.
     *
     * @param base64Url the Base64URL-encoded string
     * @return the decoded blob fields
     * @throws IllegalArgumentException if the string is malformed or the blob is invalid
     */
    public static DecodedBlob decodeFromBase64Url(String base64Url) {
        if (base64Url == null || base64Url.isEmpty()) {
            throw new IllegalArgumentException("base64Url string must not be null or empty");
        }
        byte[] blob;
        try {
            blob = Base64.getUrlDecoder().decode(base64Url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid Base64URL encoding: " + e.getMessage(), e);
        }
        return decode(blob);
    }
}
