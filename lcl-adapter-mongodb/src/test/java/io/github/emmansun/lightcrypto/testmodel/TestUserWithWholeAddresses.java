package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

import java.util.List;

@Data
public class TestUserWithWholeAddresses {

    @Encrypted
    private List<Address> addresses;

    @Data
    public static class Address {
        private String street;
        private String city;
    }
}
