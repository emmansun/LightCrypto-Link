package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IntTestUserWithAddressRepository extends MongoRepository<IntTestUserWithAddress, String> {
    IntTestUserWithAddress findByAddressZipCode(String zipCode);
}
