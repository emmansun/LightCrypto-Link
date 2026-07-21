package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IntTestUserWithWholeAddressRepository extends MongoRepository<IntTestUserWithWholeAddress, String> {
}
