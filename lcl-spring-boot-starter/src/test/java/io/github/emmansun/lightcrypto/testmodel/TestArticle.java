package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TestArticle {

    @Encrypted(blindIndex = true)
    private List<String> tags;

    @Encrypted
    private Map<String, String> settings;
}
