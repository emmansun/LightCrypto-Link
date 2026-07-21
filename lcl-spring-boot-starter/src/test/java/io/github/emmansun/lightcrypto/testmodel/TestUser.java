package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

@Data
public class TestUser {
    private String id;

    private String name;

    @Encrypted(blindIndex = true)
    private String phone;

    private String email;
}
