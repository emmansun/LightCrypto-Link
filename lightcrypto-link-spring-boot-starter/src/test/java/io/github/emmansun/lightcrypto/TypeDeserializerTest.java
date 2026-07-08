package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.exception.UnsupportedTypeException;
import io.github.emmansun.lightcrypto.service.TypeDeserializer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeDeserializerTest {

    private final TypeDeserializer deserializer = new TypeDeserializer();

    private enum TestStatus {
        ACTIVE,
        INACTIVE
    }

    @Test
    void deserializeReturnsNullWhenTypeMarkerOrValueIsNull() {
        assertThat(deserializer.deserialize(null, "1")).isNull();
        assertThat(deserializer.deserialize("INT", (String) null)).isNull();
        assertThat(deserializer.deserialize("STR", (byte[]) null)).isNull();
    }

    @Test
    void deserializeEnumTypeFromTypeMarker() {
        String marker = "ENUM:" + TestStatus.class.getName();

        Object result = deserializer.deserialize(marker, "ACTIVE");

        assertThat(result).isEqualTo(TestStatus.ACTIVE);
    }

    @Test
    void deserializeEnumThrowsWhenClassCannotBeFound() {
        assertThatThrownBy(() -> deserializer.deserialize("ENUM:com.example.MissingEnum", "ACTIVE"))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("Enum class not found");
    }

    @Test
    void deserializeThrowsForUnknownTypeMarker() {
        assertThatThrownBy(() -> deserializer.deserialize("UNKNOWN", "v"))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("Unknown type marker");
    }

    @Test
    void deserializeBytesBypassesStringDecodingForBytesType() {
        byte[] raw = new byte[]{1, 2, 3};

        Object result = deserializer.deserialize("BYTES", raw);

        assertThat(result).isSameAs(raw);
    }

    @Test
    void deserializeBytesUsesUtf8ConversionForNonBytesType() {
        byte[] intBytes = "42".getBytes(StandardCharsets.UTF_8);

        Object result = deserializer.deserialize("INT", intBytes);

        assertThat(result).isEqualTo(42);
    }

    @Test
    void deserializeSupportsAllBuiltInMarkers() {
        assertThat(deserializer.deserialize("STR", "hello")).isEqualTo("hello");
        assertThat(deserializer.deserialize("INT", "42")).isEqualTo(42);
        assertThat(deserializer.deserialize("LONG", "42")).isEqualTo(42L);
        assertThat(deserializer.deserialize("SHORT", "7")).isEqualTo((short) 7);
        assertThat(deserializer.deserialize("BYTE", "3")).isEqualTo((byte) 3);
        assertThat(deserializer.deserialize("FLOAT", "3.14")).isEqualTo(3.14f);
        assertThat(deserializer.deserialize("DOUBLE", "2.718")).isEqualTo(2.718d);
        assertThat(deserializer.deserialize("DEC", "123.45")).isEqualTo(new BigDecimal("123.45"));
        assertThat(deserializer.deserialize("BOOL", "true")).isEqualTo(true);
        assertThat(deserializer.deserialize("LDATE", "2024-06-15")).isEqualTo(LocalDate.of(2024, 6, 15));
        assertThat(deserializer.deserialize("LDT", "2024-06-15T12:34:56"))
                .isEqualTo(LocalDateTime.of(2024, 6, 15, 12, 34, 56));
        assertThat((byte[]) deserializer.deserialize("BYTES", "AQID")).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }
}
