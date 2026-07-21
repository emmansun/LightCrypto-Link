package io.github.emmansun.lightcrypto.example.observability;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * User repository with blind index query support.
 */
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Query by phone using HMAC blind index (not plaintext).
     */
    User findByPhone(String phone);
}
