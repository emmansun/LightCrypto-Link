package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@Document
public class IntTestArticle {
    @Id
    private String id;

    private String title;

    @Encrypted(blindIndex = true)
    private List<String> tags;

    @Encrypted
    private Map<String, String> settings;
}
