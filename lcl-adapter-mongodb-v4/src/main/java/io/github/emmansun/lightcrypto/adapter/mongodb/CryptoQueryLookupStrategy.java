package io.github.emmansun.lightcrypto.adapter.mongodb;

import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.repository.query.MongoQueryMethod;
import org.springframework.data.mongodb.repository.query.StringBasedAggregation;
import org.springframework.data.mongodb.repository.query.StringBasedMongoQuery;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ValueExpressionDelegate;

import java.lang.reflect.Method;

/**
 * Encryption-aware query lookup strategy (Spring Boot 4.x variant) — uses
 * CryptoPartTreeMongoQuery for method-name queries, and passes through
 * @Query-annotated and @Aggregation queries to the original implementation.
 *
 * <p>Adapted to use {@link ValueExpressionDelegate} instead of the removed
 * {@code QueryMethodEvaluationContextProvider} + {@code ExpressionParser} pair.
 *
 * @since 1.0.0
 */
public class CryptoQueryLookupStrategy implements QueryLookupStrategy {

    private final MongoOperations operations;
    private final ValueExpressionDelegate valueExpressionDelegate;
    private final MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext;
    private final CryptoMongoQueryCreator cryptoQueryCreator;

    public CryptoQueryLookupStrategy(MongoOperations operations,
                                     ValueExpressionDelegate valueExpressionDelegate,
                                     MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext,
                                     CryptoMongoQueryCreator cryptoQueryCreator) {
        this.operations = operations;
        this.valueExpressionDelegate = valueExpressionDelegate;
        this.mappingContext = mappingContext;
        this.cryptoQueryCreator = cryptoQueryCreator;
    }

    @Override
    public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata,
                                        ProjectionFactory factory, NamedQueries namedQueries) {
        MongoQueryMethod queryMethod = new MongoQueryMethod(method, metadata, factory, mappingContext);
        queryMethod.verify();

        String namedQueryName = queryMethod.getNamedQueryName();
        if (namedQueries.hasQuery(namedQueryName)) {
            String namedQuery = namedQueries.getQuery(namedQueryName);
            return new StringBasedMongoQuery(namedQuery, queryMethod, operations, valueExpressionDelegate);
        }

        if (queryMethod.hasAnnotatedAggregation()) {
            return new StringBasedAggregation(queryMethod, operations, valueExpressionDelegate);
        }

        if (queryMethod.hasAnnotatedQuery()) {
            return new StringBasedMongoQuery(queryMethod, operations, valueExpressionDelegate);
        }

        // Method-name query — use encryption-aware version
        return new CryptoPartTreeMongoQuery(queryMethod, operations,
                valueExpressionDelegate, cryptoQueryCreator);
    }
}
