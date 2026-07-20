package io.github.emmansun.lightcrypto.integration;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface IntTestUserRepository extends MongoRepository<IntTestUser, String> {
    IntTestUser findByPhone(String phone);
    List<IntTestUser> findByPhoneIn(List<String> phones);
    IntTestUser findByPhoneAndName(String phone, String name);
}
