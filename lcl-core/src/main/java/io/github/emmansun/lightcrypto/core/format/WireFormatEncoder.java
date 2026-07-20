package io.github.emmansun.lightcrypto.core.format;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes encrypted payloads into Wire Format V1 binary blobs.
 *
 * <p>Byte layout:
 * <pre>
 * [1B version=0x01][1B algId][2B nsLen][NB namespace UTF-8]
 * [4B dekVersion][1B ivLen][IVB iv][2B aadExtLen=0][var ciphertext]
 * </pre>
 *
 * @since 1.0.0
 */
public final class WireFormatEncoder {

    /** Wire Format version byte. */
    public static final byte VERSION = 0x01;

    private WireFormatEncoder() {
    }

    /**
     * Encodes the given parameters into a Wire Format V1 binary blob.
     *
     * @param algorithm  the algorithm identifier
     * @param namespace  the canonical namespace string (UTF-8 encoded in blob)
     * @param dekVersion the DEK version (must be ≥ 1)
     * @param iv         the initialization vector
     * @param ciphertext the ciphertext (GCM: CT‖Tag; CBC: PKCS7-padded CT)
     * @return the Wire Format V1 blob as a byte array
     */
    public static byte[] encode(AlgorithmId algorithm, String namespace, int dekVersion,
                                byte[] iv, byte[] ciphertext) {
        return encode(algorithm, namespace, dekVersion, iv, new byte[0], ciphertext);
    }

    /**
     * Encodes the given parameters into a Wire Format V1 binary blob with optional AAD extension.
     *
     * @param algorithm   the algorithm identifier
     * @param namespace   the canonical namespace string
     * @param dekVersion  the DEK version (must be ≥ 1)
     * @param iv          the initialization vector
     * @param aadExt      the AAD extension bytes (V1: should be empty)
     * @param ciphertext  the ciphertext
     * @return the Wire Format V1 blob as a byte array
     */
    public static byte[] encode(AlgorithmId algorithm, String namespace, int dekVersion,
                                byte[] iv, byte[] aadExt, byte[] ciphertext) {
        byte[] nsBytes = namespace.getBytes(StandardCharsets.UTF_8);
        if (nsBytes.length == 0) {
            throw new IllegalArgumentException("namespace must not be empty");
        }
        if (nsBytes.length > 65535) {
            throw new IllegalArgumentException("namespace exceeds maximum length of 65535 bytes");
        }
        if (dekVersion < 1) {
            throw new IllegalArgumentException("dekVersion must be >= 1");
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(
                    1 + 1 + 2 + nsBytes.length + 4 + 1 + iv.length + 2 + aadExt.length + ciphertext.length);
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(VERSION);
            dos.writeByte(algorithm.id());
            dos.writeShort(nsBytes.length);
            dos.write(nsBytes);
            dos.writeInt(dekVersion);
            dos.writeByte(iv.length);
            dos.write(iv);
            dos.writeShort(aadExt.length);
            if (aadExt.length > 0) {
                dos.write(aadExt);
            }
            dos.write(ciphertext);
            dos.flush();

            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode Wire Format V1 blob", e);
        }
    }

    /**
     * Encodes and then Base64URL-encodes (no padding) for storage.
     *
     * @param algorithm  the algorithm identifier
     * @param namespace  the canonical namespace string
     * @param dekVersion the DEK version
     * @param iv         the initialization vector
     * @param ciphertext the ciphertext
     * @return Base64URL-encoded string (no padding)
     */
    public static String encodeToBase64Url(AlgorithmId algorithm, String namespace, int dekVersion,
                                           byte[] iv, byte[] ciphertext) {
        byte[] blob = encode(algorithm, namespace, dekVersion, iv, ciphertext);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(blob);
    }

    /**
     * Constructs the AAD (Additional Authenticated Data) for GCM modes.
     * AAD = version_byte ‖ algorithmId_byte ‖ namespace_bytes ‖ dekVersion_bytes(4B big-endian)
     *
     * @param algorithm  the algorithm identifier
     * @param namespace  the canonical namespace string
     * @param dekVersion the DEK version
     * @return the AAD bytes
     */
    public static byte[] buildAad(AlgorithmId algorithm, String namespace, int dekVersion) {
        byte[] nsBytes = namespace.getBytes(StandardCharsets.UTF_8);
        byte[] aad = new byte[1 + 1 + nsBytes.length + 4];
        aad[0] = VERSION;
        aad[1] = algorithm.id();
        System.arraycopy(nsBytes, 0, aad, 2, nsBytes.length);
        aad[aad.length - 4] = (byte) (dekVersion >>> 24);
        aad[aad.length - 3] = (byte) (dekVersion >>> 16);
        aad[aad.length - 2] = (byte) (dekVersion >>> 8);
        aad[aad.length - 1] = (byte) dekVersion;
        return aad;
    }
}
