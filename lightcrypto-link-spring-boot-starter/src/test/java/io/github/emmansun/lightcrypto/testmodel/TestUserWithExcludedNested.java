package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.util.List;
import java.util.Map;

@Data
public class TestUserWithExcludedNested {
    @DBRef
    private Address addressRef;

    private List<Address> addresses;

    private Map<String, Address> addressMap;

    @Data
    public static class Address {
        @Encrypted
        private String zipCode;
    }
}
