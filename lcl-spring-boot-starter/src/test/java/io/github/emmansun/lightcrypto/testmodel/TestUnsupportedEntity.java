package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

import java.util.Map;

@Data
public class TestUnsupportedEntity {
    private String id;

    @Encrypted
    private Map unsupportedField;
}
