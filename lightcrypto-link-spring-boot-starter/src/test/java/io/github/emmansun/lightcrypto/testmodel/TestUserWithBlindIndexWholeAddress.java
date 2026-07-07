package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

@Data
public class TestUserWithBlindIndexWholeAddress {

    @Encrypted(blindIndex = true)
    private Address address;

    @Data
    public static class Address {
        private String zipCode;
    }
}
