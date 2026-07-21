package io.github.emmansun.lightcrypto.adapter.mongodb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import java.io.Serializable;

/**
 * Encryption-aware RepositoryFactoryBean — creates CryptoMongoRepositoryFactory instances.
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
    protected RepositoryFactorySupport getFactoryInstance(MongoOperations operations) {
        return new CryptoMongoRepositoryFactory(operations, cryptoQueryCreator);
    }
}
