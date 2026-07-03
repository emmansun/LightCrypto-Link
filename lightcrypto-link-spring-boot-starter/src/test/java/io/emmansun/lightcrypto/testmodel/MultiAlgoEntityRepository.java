package io.emmansun.lightcrypto.testmodel;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MultiAlgoEntityRepository extends MongoRepository<MultiAlgoEntity, String> {
}
