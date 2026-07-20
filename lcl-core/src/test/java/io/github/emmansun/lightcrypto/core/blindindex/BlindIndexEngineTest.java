package io.github.emmansun.lightcrypto.core.blindindex;

import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlindIndexEngineTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private BlindIndexEngine engine;
    private byte[] masterKey;

    @BeforeEach
    void setUp() {
        masterKey = new byte[32];
        RANDOM.nextBytes(masterKey);
        engine = new BlindIndexEngine(masterKey);
    }

    @Test
    void deterministicOutput() {
        Namespace ns = Namespace.parse("default.default.User#phone");
        String index1 = engine.computeBlindIndex(ns, "phone", "13800138000");
        String index2 = engine.computeBlindIndex(ns, "phone", "13800138000");
        assertThat(index1).isEqualTo(index2);
    }

    @Test
    void outputIsBase64UrlNoPadding43Chars() {
        Namespace ns = Namespace.parse("User#email");
        String index = engine.computeBlindIndex(ns, "email", "test@example.com");
        // HMAC-SHA-256 = 32 bytes → Base64URL no padding = 43 chars
        assertThat(index).hasSize(43);
        assertThat(index).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void tenantIsolation() {
        Namespace nsA = Namespace.parse("tenantA.app.User#email");
        Namespace nsB = Namespace.parse("tenantB.app.User#email");

        String indexA = engine.computeBlindIndex(nsA, "email", "same@example.com");
        String indexB = engine.computeBlindIndex(nsB, "email", "same@example.com");

        assertThat(indexA).isNotEqualTo(indexB);
    }

    @Test
    void differentFieldsProduceDifferentIndexes() {
        Namespace ns = Namespace.parse("default.default.User#email");
        String indexEmail = engine.computeBlindIndex(ns, "email", "test@example.com");
        String indexPhone = engine.computeBlindIndex(ns, "phone", "test@example.com");
        assertThat(indexEmail).isNotEqualTo(indexPhone);
    }

    @Test
    void stringNormalization() {
        Namespace ns = Namespace.parse("User#name");
        // " Hello " → trim → "Hello" → lowercase → "hello"
        String index1 = engine.computeBlindIndex(ns, "name", "  Hello  ");
        String index2 = engine.computeBlindIndex(ns, "name", "hello");
        assertThat(index1).isEqualTo(index2);
    }

    @Test
    void byteArrayNoNormalization() {
        Namespace ns = Namespace.parse("User#avatar");
        byte[] data1 = new byte[]{0x01, 0x02};
        byte[] data2 = new byte[]{0x01, 0x02};
        byte[] data3 = new byte[]{0x01, 0x03};

        String index1 = engine.computeBlindIndex(ns, "avatar", data1);
        String index2 = engine.computeBlindIndex(ns, "avatar", data2);
        String index3 = engine.computeBlindIndex(ns, "avatar", data3);

        assertThat(index1).isEqualTo(index2);
        assertThat(index1).isNotEqualTo(index3);
    }

    @Test
    void differentMasterKeysProduceDifferentIndexes() {
        byte[] otherKey = new byte[32];
        RANDOM.nextBytes(otherKey);
        BlindIndexEngine otherEngine = new BlindIndexEngine(otherKey);

        Namespace ns = Namespace.parse("User#email");
        String index1 = engine.computeBlindIndex(ns, "email", "test@example.com");
        String index2 = otherEngine.computeBlindIndex(ns, "email", "test@example.com");

        assertThat(index1).isNotEqualTo(index2);
    }

    @Test
    void nullMasterKeyRejected() {
        assertThatThrownBy(() -> new BlindIndexEngine(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyMasterKeyRejected() {
        assertThatThrownBy(() -> new BlindIndexEngine(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cachedDerivedKeyProducesSameResult() {
        Namespace ns = Namespace.parse("acme.prod.Order#total");
        // First call derives and caches
        String index1 = engine.computeBlindIndex(ns, "total", "100.00");
        // Second call uses cache
        String index2 = engine.computeBlindIndex(ns, "total", "100.00");
        assertThat(index1).isEqualTo(index2);
    }
}
