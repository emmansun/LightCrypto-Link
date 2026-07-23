package io.github.emmansun.lightcrypto.adapter.mongodb;

import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.lang.Nullable;

import java.util.Optional;

/**
 * Encryption-aware MongoDB Repository factory (Spring Boot 4.x variant) —
 * replaces the default QueryLookupStrategy so that method-name queries
 * automatically rewrite encrypted fields into blind-index queries.
 *
 * <p>This class adapts the {@code getQueryLookupStrategy()} signature to use
 * {@link ValueExpressionDelegate} (spring-data-mongodb 5.x / Spring Data 2025.1+),
 * replacing the removed {@code QueryMethodEvaluationContextProvider}.
 *
 * @since 1.0.0
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
            ValueExpressionDelegate valueExpressionDelegate) {
        return Optional.of(new CryptoQueryLookupStrategy(
                mongoOperations, valueExpressionDelegate, mappingContext, cryptoQueryCreator));
    }
}
