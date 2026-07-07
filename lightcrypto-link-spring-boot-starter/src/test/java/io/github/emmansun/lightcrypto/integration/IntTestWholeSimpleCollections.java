package io.github.emmansun.lightcrypto.integration;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import io.github.emmansun.lightcrypto.annotation.EncryptionMode;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@Document
public class IntTestWholeSimpleCollections {
    @Id
    private String id;

    private String name;

    @Encrypted(mode = EncryptionMode.WHOLE)
    private List<String> tags;

    @Encrypted(mode = EncryptionMode.WHOLE)
    private Map<String, String> settings;
}
