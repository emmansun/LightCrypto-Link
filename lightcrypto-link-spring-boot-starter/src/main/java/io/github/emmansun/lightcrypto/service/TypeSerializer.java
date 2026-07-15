package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.exception.UnsupportedTypeException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic serializer — serializes Java values to byte arrays for encryption
 * and blind indexing. Repeated serialization of the same value always produces
 * the same output (deterministic), which is a prerequisite for HMAC blind indexes.
 */
public class TypeSerializer {
    static final DateTimeFormatter ISO_WITH_3MS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final Map<Class<?>, String> TYPE_MARKERS = new ConcurrentHashMap<>();

    static {
        TYPE_MARKERS.put(String.class, "STR");
        TYPE_MARKERS.put(Integer.class, "INT");
        TYPE_MARKERS.put(Long.class, "LONG");
        TYPE_MARKERS.put(Short.class, "SHORT");
        TYPE_MARKERS.put(Byte.class, "BYTE");
        TYPE_MARKERS.put(Float.class, "FLOAT");
        TYPE_MARKERS.put(Double.class, "DOUBLE");
        TYPE_MARKERS.put(BigDecimal.class, "DEC");
        TYPE_MARKERS.put(Boolean.class, "BOOL");
        TYPE_MARKERS.put(LocalDate.class, "LDATE");
        TYPE_MARKERS.put(LocalDateTime.class, "LDT");
        TYPE_MARKERS.put(byte[].class, "BYTES");
    }

    /**
     * Serialize value to a byte array. For byte[] input, returns raw bytes directly.
     */
    public byte[] serialize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) return bytes;
        String str = serializeToString(value);
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Serialize value to a string (deterministic output).
     * For byte[] values, returns standard Base64 encoded string.
     */
    public String serializeToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) return s;
        if (value instanceof Integer i) return Integer.toString(i);
        if (value instanceof Long l) return Long.toString(l);
        if (value instanceof Short s) return Short.toString(s);
        if (value instanceof Byte b) return Byte.toString(b);
        if (value instanceof Float f) return EcmaDoubleFormatter.format(f);
        if (value instanceof Double d) return EcmaDoubleFormatter.format(d);
        if (value instanceof BigDecimal bd) return bd.toPlainString();
        if (value instanceof Boolean b) return Boolean.toString(b);
        if (value instanceof LocalDate ld) return ld.format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (value instanceof LocalDateTime ldt) return ldt.format(ISO_WITH_3MS);
        if (value instanceof Enum<?> e) return e.getDeclaringClass().getName() + ":" + e.name();
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        throw new UnsupportedTypeException("Unsupported type for serialization: " + value.getClass().getName());
    }

    /**
     * Resolve the type marker for the given Java type.
     */
    public static String resolveTypeMarker(Class<?> type) {
        // Check predefined map first
        String marker = TYPE_MARKERS.get(type);
        if (marker != null) return marker;
        // Special handling for Enum types
        if (Enum.class.isAssignableFrom(type)) {
            return "ENUM:" + type.getName();
        }
        throw new UnsupportedTypeException("Unsupported type for encryption: " + type.getName());
    }

    /**
     * Check whether the given type is supported.
     */
    public static boolean isSupported(Class<?> type) {
        if (TYPE_MARKERS.containsKey(type)) return true;
        return Enum.class.isAssignableFrom(type);
    }
}
