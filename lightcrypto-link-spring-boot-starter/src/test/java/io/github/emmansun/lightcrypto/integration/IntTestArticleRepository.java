package io.github.emmansun.lightcrypto.integration;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IntTestArticleRepository extends MongoRepository<IntTestArticle, String> {
    IntTestArticle findByTagsContaining(String tag);
}
