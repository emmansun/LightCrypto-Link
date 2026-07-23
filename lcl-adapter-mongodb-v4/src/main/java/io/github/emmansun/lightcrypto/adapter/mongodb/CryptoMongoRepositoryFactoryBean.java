package io.github.emmansun.lightcrypto.adapter.mongodb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactoryBean;
import org.springframework.data.repository.Repository;

import java.io.Serializable;

/**
 * Encryption-aware RepositoryFactoryBean (Spring Boot 4.x variant) — creates
 * {@link CryptoMongoRepositoryFactory} instances that use the spring-data-mongodb 5.x
 * {@code ValueExpressionDelegate} API for query rewriting.
 *
 * <p>This class is intentionally duplicated in the v4 adapter module to ensure that
 * the classloader picks up the v4 variant (from {@code target/classes}) rather than
 * the SB3 adapter JAR version, which was compiled against spring-data-mongodb 4.x
 * and may have incompatible method references.
 *
 * @since 1.0.0
 */
public class CryptoMongoRepositoryFactoryBean<T extends Repository<S, ID>, S, ID extends Serializable>
        extends MongoRepositoryFactoryBean<T, S, ID> {

    private CryptoMongoQueryCreator cryptoQueryCreator;

    public CryptoMongoRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
        super(repositoryInterface);
    }

    @Autowired
    public void setCryptoQueryCreator(CryptoMongoQueryCreator cryptoQueryCreator) {
        this.cryptoQueryCreator = cryptoQueryCreator;
    }

    @Override
    protected MongoRepositoryFactory getFactoryInstance(MongoOperations operations) {
        return new CryptoMongoRepositoryFactory(operations, cryptoQueryCreator);
    }
}
