package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@Document
public class TestUnsupportedEntity {
    @Id
    private String id;

    @Encrypted
    private Map<String, String> unsupportedField;
}
