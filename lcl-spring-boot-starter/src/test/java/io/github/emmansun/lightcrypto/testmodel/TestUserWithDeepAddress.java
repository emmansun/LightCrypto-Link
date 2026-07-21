package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

@Data
public class TestUserWithDeepAddress {

    private Address address;

    @Data
    public static class Address {
        private GeoLocation geo;
    }

    @Data
    public static class GeoLocation {
        @Encrypted
        private String lat;
    }
}
