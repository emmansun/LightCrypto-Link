package io.github.emmansun.lightcrypto.query;

import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.lang.Nullable;

import java.util.Optional;

/**
 * Encryption-aware MongoDB Repository factory — replaces the default QueryLookupStrategy
 * so that method-name queries automatically rewrite encrypted fields into blind-index queries.
 */
public class CryptoMongoRepositoryFactory extends MongoRepositoryFactory {

    private final MongoOperations mongoOperations;
    private final MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext;
    private final CryptoMongoQueryCreator cryptoQueryCreator;

    public CryptoMongoRepositoryFactory(MongoOperations mongoOperations,
                                        CryptoMongoQueryCreator cryptoQueryCreator) {
        super(mongoOperations);
        this.mongoOperations = mongoOperations;
        this.mappingContext = mongoOperations.getConverter().getMappingContext();
        this.cryptoQueryCreator = cryptoQueryCreator;
    }

    @Override
    protected Optional<QueryLookupStrategy> getQueryLookupStrategy(
            @Nullable QueryLookupStrategy.Key key,
            QueryMethodEvaluationContextProvider evaluationContextProvider) {
        return Optional.of(new CryptoQueryLookupStrategy(
                mongoOperations, evaluationContextProvider, mappingContext, cryptoQueryCreator));
    }
}
