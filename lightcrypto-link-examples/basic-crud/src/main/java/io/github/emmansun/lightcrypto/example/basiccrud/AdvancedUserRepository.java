package io.github.emmansun.lightcrypto.example.basiccrud;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdvancedUserRepository extends MongoRepository<AdvancedUser, String> {
    AdvancedUser findByTagsContaining(String tag);
}
