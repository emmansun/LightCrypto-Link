package io.github.emmansun.lightcrypto.integration;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IntTestUserWithWholeAddressesRepository extends MongoRepository<IntTestUserWithWholeAddresses, String> {
}
