package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.exception.UnsupportedTypeException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Type deserializer — restores a string value to its original Java type based on the _t type marker.
 */
public class TypeDeserializer {

    /**
     * Deserialize a string value to its original Java object based on the type marker.
     *
     * @param typeMarker type marker (e.g. "STR", "INT", "ENUM:com.example.Status")
     * @param value      serialized string value
     * @return deserialized Java object
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object deserialize(String typeMarker, String value) {
        if (value == null || typeMarker == null) return null;

        return switch (typeMarker) {
            case "STR" -> value;
            case "INT" -> Integer.parseInt(value);
            case "LONG" -> Long.parseLong(value);
            case "SHORT" -> Short.parseShort(value);
            case "BYTE" -> Byte.parseByte(value);
            case "FLOAT" -> Float.parseFloat(value);
            case "DOUBLE" -> Double.parseDouble(value);
            case "DEC" -> new BigDecimal(value);
            case "BOOL" -> Boolean.parseBoolean(value);
            case "LDATE" -> LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            case "LDT" -> LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "BYTES" -> Base64.getDecoder().decode(value);
            default -> {
                if (typeMarker.startsWith("ENUM:")) {
                    String fqcn = typeMarker.substring(5);
                    String enumName = value;
                    try {
                        Class enumClass = Class.forName(fqcn);
                        yield Enum.valueOf(enumClass, enumName);
                    } catch (ClassNotFoundException e) {
                        throw new UnsupportedTypeException("Enum class not found: " + fqcn);
                    }
                }
                throw new UnsupportedTypeException("Unknown type marker: " + typeMarker);
            }
        };
    }

    /**
     * Deserialize from a byte array. For BYTES type, returns raw bytes directly.
     * For other types, converts to UTF-8 string first then deserializes.
     */
    public Object deserialize(String typeMarker, byte[] bytes) {
        if (bytes == null) return null;
        // BYTES type: raw bytes are the actual value, no string conversion needed
        if ("BYTES".equals(typeMarker)) return bytes;
        return deserialize(typeMarker, new String(bytes, StandardCharsets.UTF_8));
    }
}
