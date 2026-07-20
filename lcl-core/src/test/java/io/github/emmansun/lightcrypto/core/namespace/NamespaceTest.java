package io.github.emmansun.lightcrypto.core.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespaceTest {

    @Test
    void parseFullFourSegment() {
        Namespace ns = Namespace.parse("acme.production.users#email");
        assertThat(ns.tenant()).isEqualTo("acme");
        assertThat(ns.realm()).isEqualTo("production");
        assertThat(ns.entity()).isEqualTo("users");
        assertThat(ns.field()).isEqualTo("email");
        assertThat(ns.canonical()).isEqualTo("acme.production.users#email");
    }

    @Test
    void parseShorthandExpandsToDefault() {
        Namespace ns = Namespace.parse("User#email");
        assertThat(ns.tenant()).isEqualTo("default");
        assertThat(ns.realm()).isEqualTo("default");
        assertThat(ns.entity()).isEqualTo("User");
        assertThat(ns.field()).isEqualTo("email");
        assertThat(ns.canonical()).isEqualTo("default.default.User#email");
    }

    @Test
    void parseNestedFieldPath() {
        Namespace ns = Namespace.parse("default.default.User#address.city");
        assertThat(ns.field()).isEqualTo("address.city");
        assertThat(ns.canonical()).isEqualTo("default.default.User#address.city");
    }

    @Test
    void twoSegmentPathIsForbidden() {
        assertThatThrownBy(() -> Namespace.parse("production.User#email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiguous");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tenant app.realm.User#email",   // space in tenant
            "tenant.realm.User#em ail",      // space in field
            "tenant.realm.User#",            // empty field
            "#email",                        // empty path
            "tenant.realm.User",             // no # separator
            "",                              // empty string
    })
    void invalidNamespacesRejected(String raw) {
        assertThatThrownBy(() -> Namespace.parse(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validSpecialCharacters() {
        Namespace ns = Namespace.parse("my-tenant.prod_v2.User-Entity#field_name");
        assertThat(ns.tenant()).isEqualTo("my-tenant");
        assertThat(ns.realm()).isEqualTo("prod_v2");
        assertThat(ns.entity()).isEqualTo("User-Entity");
        assertThat(ns.field()).isEqualTo("field_name");
    }

    @Test
    void caseSensitive() {
        Namespace upper = Namespace.parse("Default.Default.User#email");
        Namespace lower = Namespace.parse("default.default.User#email");
        assertThat(upper).isNotEqualTo(lower);
        assertThat(upper.canonical()).isNotEqualTo(lower.canonical());
    }

    @Test
    void maxLengthAccepted() {
        // Build a namespace close to 256 bytes
        String entity = "E".repeat(200);
        String raw = entity + "#field";
        Namespace ns = Namespace.parse(raw);
        assertThat(ns.canonicalBytes().length).isLessThanOrEqualTo(256);
    }

    @Test
    void exceedingMaxLengthRejected() {
        String entity = "E".repeat(260);
        String raw = entity + "#field";
        assertThatThrownBy(() -> Namespace.parse(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256-byte");
    }

    @Test
    void ofFactoryMethod() {
        Namespace ns = Namespace.of("acme", "prod", "Order", "totalAmount");
        assertThat(ns.canonical()).isEqualTo("acme.prod.Order#totalAmount");
    }

    @Test
    void ofRejectsInvalidSegment() {
        assertThatThrownBy(() -> Namespace.of("has space", "prod", "Order", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void canonicalBytesMatchesUtf8() {
        Namespace ns = Namespace.parse("User#email");
        byte[] expected = "default.default.User#email".getBytes(StandardCharsets.UTF_8);
        assertThat(ns.canonicalBytes()).isEqualTo(expected);
    }

    @Test
    void toStringReturnsCanonical() {
        Namespace ns = Namespace.parse("User#email");
        assertThat(ns.toString()).isEqualTo("default.default.User#email");
    }

    @Test
    void fourOrMoreSegmentPathRejected() {
        assertThatThrownBy(() -> Namespace.parse("a.b.c.d#field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 or 3");
    }
}
