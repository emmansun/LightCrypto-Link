package io.github.emmansun.lightcrypto.integration;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IntTestUserWithAddressRepository extends MongoRepository<IntTestUserWithAddress, String> {
    IntTestUserWithAddress findByAddressZipCode(String zipCode);
}
