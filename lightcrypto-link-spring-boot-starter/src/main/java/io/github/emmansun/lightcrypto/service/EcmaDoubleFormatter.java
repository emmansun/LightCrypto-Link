package io.github.emmansun.lightcrypto.service;

class EcmaDoubleFormatter {
    private EcmaDoubleFormatter() {}

    public static String format(float value) {
        if (Float.isNaN(value)) return "NaN";
        if (value == Float.POSITIVE_INFINITY) return "Infinity";
        if (value == Float.NEGATIVE_INFINITY) return "-Infinity";
        if (value == 0.0f) return "0";
        return formatRawString(Float.toString(value));
    }

    public static String format(double value) {
        if (Double.isNaN(value)) return "NaN";
        if (value == Double.POSITIVE_INFINITY) return "Infinity";
        if (value == Double.NEGATIVE_INFINITY) return "-Infinity";
        if (value == 0.0) return "0";
        return formatRawString(Double.toString(value));
    }

    /**
     * Highly optimized parser that normalizes Java's shortest decimal format 
     * into ECMAScript specifications, stripping trailing format-zeros on the fly.
     */
    private static String formatRawString(String raw) {
        // Fast intercept for simple trailing ".0" (e.g., "1.0" -> "1", "-5.0" -> "-5")
        if (raw.endsWith(".0")) {
            return raw.substring(0, raw.length() - 2);
        }

        int eIdx = raw.indexOf('E');
        if (eIdx == -1) {
            if (raw.equals("-0")) return "0";
            return raw;
        }

        // Deconstruct Java's scientific notation: e.g., "-1.0E-6" or "1.5E21"
        int dotIdx = raw.indexOf('.');
        String sign = raw.startsWith("-") ? "-" : "";
        int start = sign.isEmpty() ? 0 : 1;

        String firstDigit = raw.substring(start, dotIdx);
        String fractionPart = raw.substring(dotIdx + 1, eIdx);
        int exp = Integer.parseInt(raw.substring(eIdx + 1));

        String allDigits = firstDigit + fractionPart;
        
        // CRUCIAL OPTIMIZATION: Strip Java's trailing formatting zeros 
        // to prevent outputs like "0.0000010" and align with ECMAScript spec.
        int k = allDigits.length();
        while (k > 1 && allDigits.charAt(k - 1) == '0') {
            k--;
        }
        allDigits = allDigits.substring(0, k);

        // Recalculate 'n' based on the newly trimmed significant digits
        int n = exp + firstDigit.length();

        StringBuilder sb = new StringBuilder(k + 12);
        sb.append(sign);

        // --- Standard ECMAScript formatting routing (Zero-Allocation Track) ---

        if (k <= n && n <= 21) {
            // Case 1: Integer padding
            sb.append(allDigits);
            for (int i = 0; i < n - k; i++) {
                sb.append('0');
            }
            return sb.toString();
        }

        if (0 < n && n <= 21) {
            // Case 2: Standard floating point
            sb.append(allDigits, 0, n).append('.').append(allDigits, n, k);
            return sb.toString();
        }

        if (-5 <= n && n <= 0) {
            // Case 3: "0." followed by leading zeros
            sb.append("0.");
            for (int i = 0; i < -n; i++) {
                sb.append('0');
            }
            sb.append(allDigits);
            return sb.toString();
        }

        // Cases 4 & 5: Compliant Scientific Notation
        int exponent = n - 1;
        sb.append(allDigits.charAt(0));
        if (k > 1) {
            sb.append('.').append(allDigits, 1, k);
        }
        sb.append('e').append(exponent >= 0 ? "+" : "").append(exponent);

        return sb.toString();
    }
}
