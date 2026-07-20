package io.github.emmansun.lightcrypto.core.format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WireFormatTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @ParameterizedTest
    @EnumSource(AlgorithmId.class)
    void encodeDecodeRoundtrip(AlgorithmId alg) {
        String namespace = "default.default.User#email";
        int dekVersion = 1;
        byte[] iv = new byte[alg.ivLength()];
        RANDOM.nextBytes(iv);
        byte[] ciphertext = new byte[48]; // simulated CT+tag
        RANDOM.nextBytes(ciphertext);

        byte[] blob = WireFormatEncoder.encode(alg, namespace, dekVersion, iv, ciphertext);
        WireFormatDecoder.DecodedBlob decoded = WireFormatDecoder.decode(blob);

        assertThat(decoded.version()).isEqualTo((byte) 0x01);
        assertThat(decoded.algorithm()).isEqualTo(alg);
        assertThat(decoded.namespace()).isEqualTo(namespace);
        assertThat(decoded.dekVersion()).isEqualTo(dekVersion);
        assertThat(decoded.iv()).isEqualTo(iv);
        assertThat(decoded.aadExt()).isEmpty();
        assertThat(decoded.ciphertext()).isEqualTo(ciphertext);
    }

    @Test
    void base64UrlRoundtrip() {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        byte[] ct = "Hello World".getBytes(StandardCharsets.UTF_8);

        String encoded = WireFormatEncoder.encodeToBase64Url(
                AlgorithmId.AES_256_GCM, "default.default.User#email", 1, iv, ct);

        // Verify URL-safe (no +, /, =)
        assertThat(encoded).matches("[A-Za-z0-9_-]+");

        WireFormatDecoder.DecodedBlob decoded = WireFormatDecoder.decodeFromBase64Url(encoded);
        assertThat(decoded.ciphertext()).isEqualTo(ct);
        assertThat(decoded.iv()).isEqualTo(iv);
    }

    @Test
    void blobStartsWithVersionAndAlgId() {
        byte[] iv = new byte[12];
        byte[] ct = new byte[16];
        byte[] blob = WireFormatEncoder.encode(AlgorithmId.AES_256_GCM, "t.r.E#f", 1, iv, ct);

        assertThat(blob[0]).isEqualTo((byte) 0x01); // version
        assertThat(blob[1]).isEqualTo((byte) 0x01); // AES_256_GCM
    }

    @Test
    void allAlgorithmIdsEncodeCorrectly() {
        assertThat(AlgorithmId.AES_256_GCM.id()).isEqualTo((byte) 0x01);
        assertThat(AlgorithmId.AES_256_CBC.id()).isEqualTo((byte) 0x02);
        assertThat(AlgorithmId.SM4_GCM.id()).isEqualTo((byte) 0x03);
        assertThat(AlgorithmId.SM4_CBC.id()).isEqualTo((byte) 0x04);
    }

    @Test
    void unknownAlgorithmIdRejected() {
        assertThatThrownBy(() -> AlgorithmId.fromByte((byte) 0xFF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown algorithm ID");
    }

    @Test
    void invalidVersionRejected() {
        byte[] iv = new byte[12];
        byte[] ct = new byte[16];
        byte[] blob = WireFormatEncoder.encode(AlgorithmId.AES_256_GCM, "t.r.E#f", 1, iv, ct);
        blob[0] = 0x02; // tamper version

        assertThatThrownBy(() -> WireFormatDecoder.decode(blob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported wire format version");
    }

    @Test
    void truncatedBlobRejected() {
        assertThatThrownBy(() -> WireFormatDecoder.decode(new byte[5]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void zeroNamespaceLengthRejected() {
        // Manually craft a blob with nsLen=0
        byte[] blob = new byte[]{0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x0C,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00, 0x00, 0x01};
        assertThatThrownBy(() -> WireFormatDecoder.decode(blob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace");
    }

    @Test
    void aadConstruction() {
        byte[] aad = WireFormatEncoder.buildAad(AlgorithmId.AES_256_GCM, "default.default.User#email", 1);
        // AAD = 0x01 ‖ 0x01 ‖ "default.default.User#email" ‖ 0x00000001
        assertThat(aad[0]).isEqualTo((byte) 0x01);
        assertThat(aad[1]).isEqualTo((byte) 0x01);
        byte[] nsBytes = "default.default.User#email".getBytes(StandardCharsets.UTF_8);
        assertThat(aad.length).isEqualTo(2 + nsBytes.length + 4);
        // Last 4 bytes = dekVersion big-endian
        assertThat(aad[aad.length - 1]).isEqualTo((byte) 1);
    }

    @Test
    void decodedBlobReconstructsAad() {
        byte[] iv = new byte[12];
        byte[] ct = new byte[16];
        byte[] blob = WireFormatEncoder.encode(AlgorithmId.SM4_GCM, "acme.prod.Order#total", 3, iv, ct);

        WireFormatDecoder.DecodedBlob decoded = WireFormatDecoder.decode(blob);
        byte[] aad = decoded.reconstructAad();
        byte[] expectedAad = WireFormatEncoder.buildAad(AlgorithmId.SM4_GCM, "acme.prod.Order#total", 3);
        assertThat(aad).isEqualTo(expectedAad);
    }

    @Test
    void nullBlobRejected() {
        assertThatThrownBy(() -> WireFormatDecoder.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyBase64UrlRejected() {
        assertThatThrownBy(() -> WireFormatDecoder.decodeFromBase64Url(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
