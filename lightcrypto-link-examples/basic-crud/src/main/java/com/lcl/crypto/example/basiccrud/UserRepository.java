package com.lcl.crypto.example.basiccrud;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * MongoRepository — LightCrypto-Link automatically intercepts findByPhone()
 * and translates the query to use the HMAC blind index stored in MongoDB.
 */
public interface UserRepository extends MongoRepository<User, String> {
    User findByPhone(String phone);
}
