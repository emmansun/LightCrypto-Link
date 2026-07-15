package io.github.emmansun.lightcrypto.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EcmaDoubleFormatterTest {

    @ParameterizedTest(name = "Double value {0} should serialize to \"{1}\"")
    @CsvSource({
        // Standard integers (Java normally adds ".0", Node.js does not)
        "1.0, 1",
        "0.0, 0",
        "123.0, 123",
        "10000000.0, 10000000",
        
        // Negative zero edge case
        "-0.0, 0",
        
        // Negative values
        "-1.5, -1.5",
        "-100.0, -100",

        // Scientific notation threshold for small numbers:
        // Node.js switches to exponent format at x < 1e-6 (i.e. from 1e-7 onwards)
        "0.0001, 0.0001",
        "0.00001, 0.00001",
        "0.000001, 0.000001",     // Crucial fix: n = -6 must output as decimal string
        "0.0000001, 1e-7",        // n = -7 switches to scientific notation
        "0.000000015, 1.5e-8",

        // Scientific notation threshold for large numbers:
        // Node.js switches to exponent format at x >= 1e21
        "100000000000000000000.0, 100000000000000000000", // 1e20 (Decimal)
        "1000000000000000000000.0, 1e+21",               // 1e21 (Scientific)
        "1500000000000000000000.0, 1.5e+21",

        // Ordinary floating point numbers
        "3.141592653589793, 3.141592653589793",
        "0.123456, 0.123456"
    })
    @DisplayName("Verify Double serialization matches ECMAScript Number.prototype.toString()")
    void testDoubleFormat(double input, String expected) {
        assertEquals(expected, EcmaDoubleFormatter.format(input));
    }

    @ParameterizedTest(name = "Float value {0}f should serialize to \"{1}\"")
    @CsvSource({
        // Single precision integers
        "1.0f, 1",
        "0.0f, 0",
        "-0.0f, 0",
        "500.0f, 500",
        
        // Precision widening protection (Ensures 0.1f doesn't bleed garbage digits like 0.1000000014...)
        "0.1f, 0.1",
        "0.5f, 0.5",
        "1.23f, 1.23",
        
        // Float small/large boundary thresholds
        "0.000001f, 0.000001",
        "0.0000001f, 1e-7"
    })
    @DisplayName("Verify Float serialization prevents precision bleeding and aligns with Node.js")
    void testFloatFormat(float input, String expected) {
        // If you implemented the Float overload approach:
        assertEquals(expected, EcmaDoubleFormatter.format(input));
        
        // If you used the TypeSerializer routing approach instead, fallback to:
        // assertEquals(expected, EcmaDoubleFormatter.format(Double.parseDouble(Float.toString(input))));
    }

    @Test
    @DisplayName("Verify Special IEEE 754 Floating-Point Literals")
    void testSpecialValues() {
        assertEquals("NaN", EcmaDoubleFormatter.format(Double.NaN));
        assertEquals("Infinity", EcmaDoubleFormatter.format(Double.POSITIVE_INFINITY));
        assertEquals("-Infinity", EcmaDoubleFormatter.format(Double.NEGATIVE_INFINITY));
        
        assertEquals("NaN", EcmaDoubleFormatter.format(Float.NaN));
        assertEquals("Infinity", EcmaDoubleFormatter.format(Float.POSITIVE_INFINITY));
        assertEquals("-Infinity", EcmaDoubleFormatter.format(Float.NEGATIVE_INFINITY));
    }
}
