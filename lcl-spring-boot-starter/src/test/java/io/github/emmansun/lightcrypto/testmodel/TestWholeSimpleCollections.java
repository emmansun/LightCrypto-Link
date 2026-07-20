package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import io.github.emmansun.lightcrypto.annotation.EncryptionMode;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TestWholeSimpleCollections {

    @Encrypted(mode = EncryptionMode.WHOLE)
    private List<String> tags;

    @Encrypted(mode = EncryptionMode.WHOLE)
    private Map<String, String> settings;
}
