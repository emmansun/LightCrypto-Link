package io.github.emmansun.lightcrypto.integration;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IntTestUserWithAddressesRepository extends MongoRepository<IntTestUserWithAddresses, String> {
}
