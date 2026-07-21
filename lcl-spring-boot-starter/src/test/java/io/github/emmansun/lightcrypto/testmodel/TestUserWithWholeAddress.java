package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

@Data
public class TestUserWithWholeAddress {
    private String id;

    private String name;

    @Encrypted
    private Address address;

    @Data
    public static class Address {
        private String zipCode;
        private String street;
        private String city;
    }
}
