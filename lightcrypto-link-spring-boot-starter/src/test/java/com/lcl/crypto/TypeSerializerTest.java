package com.lcl.crypto;

import com.lcl.crypto.exception.UnsupportedTypeException;
import com.lcl.crypto.service.TypeDeserializer;
import com.lcl.crypto.service.TypeSerializer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.lcl.crypto.service.TypeSerializer.resolveTypeMarker;
import static org.assertj.core.api.Assertions.*;

/**
 * 4.4-4.8 Tests: TypeSerializer and TypeDeserializer
 */
class TypeSerializerTest extends LclTestBase {

    private final TypeSerializer serializer = createTestTypeSerializer();
    private final TypeDeserializer deserializer = createTestTypeDeserializer();

    @Test
    void deterministicOutputForAllTypes() {
        assertDeterministic("hello");
        assertDeterministic(42);
        assertDeterministic(42L);
        assertDeterministic((short) 7);
        assertDeterministic((byte) 3);
        assertDeterministic(3.14f);
        assertDeterministic(2.718);
        assertDeterministic(new BigDecimal("15000"));
        assertDeterministic(true);
        assertDeterministic(LocalDate.of(2024, 1, 15));
        assertDeterministic(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        assertDeterministic(new byte[]{1, 2, 3});
    }

    @Test
    void bigDecimalPlainString() {
        BigDecimal bd = new BigDecimal("15000");
        String result = serializer.serializeToString(bd);
        assertThat(result).isEqualTo("15000");
        assertThat(result).doesNotContain("E");
    }

    @Test
    void roundtripSerializeDeserialize() {
        assertRoundtrip("STR", "hello");
        assertRoundtrip("INT", 42);
        assertRoundtrip("LONG", 42L);
        assertRoundtrip("BOOL", true);
        assertRoundtrip("LDATE", LocalDate.of(2024, 6, 15));
        assertRoundtrip("LDT", LocalDateTime.of(2024, 6, 15, 12, 0, 0));
    }

    @Test
    void resolveTypeMarkerReturnsCorrectMarkers() {
        assertThat(resolveTypeMarker(String.class)).isEqualTo("STR");
        assertThat(resolveTypeMarker(Integer.class)).isEqualTo("INT");
        assertThat(resolveTypeMarker(Long.class)).isEqualTo("LONG");
        assertThat(resolveTypeMarker(LocalDate.class)).isEqualTo("LDATE");
        assertThat(resolveTypeMarker(Boolean.class)).isEqualTo("BOOL");
        assertThat(resolveTypeMarker(byte[].class)).isEqualTo("BYTES");
    }

    @Test
    void unsupportedTypeThrowsException() {
        assertThatThrownBy(() -> serializer.serializeToString(new Object()))
                .isInstanceOf(UnsupportedTypeException.class);
    }

    private void assertDeterministic(Object value) {
        String s1 = serializer.serializeToString(value);
        String s2 = serializer.serializeToString(value);
        assertThat(s1).isEqualTo(s2).isNotNull();
    }

    private void assertRoundtrip(String typeMarker, Object value) {
        String serialized = serializer.serializeToString(value);
        Object deserialized = deserializer.deserialize(typeMarker, serialized);
        assertThat(deserialized).isEqualTo(value);
    }
}
