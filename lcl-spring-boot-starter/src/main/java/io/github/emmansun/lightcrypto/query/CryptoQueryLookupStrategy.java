package io.github.emmansun.lightcrypto.query;

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
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;

/**
 * Encryption-aware query lookup strategy — uses CryptoPartTreeMongoQuery for method-name
 * queries, and passes through @Query-annotated and @Aggregation queries to the original
 * implementation.
 */
public class CryptoQueryLookupStrategy implements QueryLookupStrategy {

    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    private final MongoOperations operations;
    private final QueryMethodEvaluationContextProvider evaluationContextProvider;
    private final MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext;
    private final ExpressionParser expressionParser;
    private final CryptoMongoQueryCreator cryptoQueryCreator;

    public CryptoQueryLookupStrategy(MongoOperations operations,
                                     QueryMethodEvaluationContextProvider evaluationContextProvider,
                                     MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext,
                                     CryptoMongoQueryCreator cryptoQueryCreator) {
        this.operations = operations;
        this.evaluationContextProvider = evaluationContextProvider;
        this.mappingContext = mappingContext;
        this.expressionParser = EXPRESSION_PARSER;
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
            return new StringBasedMongoQuery(namedQuery, queryMethod, operations,
                    expressionParser, evaluationContextProvider);
        }

        if (queryMethod.hasAnnotatedAggregation()) {
            return new StringBasedAggregation(queryMethod, operations,
                    expressionParser, evaluationContextProvider);
        }

        if (queryMethod.hasAnnotatedQuery()) {
            return new StringBasedMongoQuery(queryMethod, operations,
                    expressionParser, evaluationContextProvider);
        }

        // Method-name query — use encryption-aware version
        return new CryptoPartTreeMongoQuery(queryMethod, operations,
                expressionParser, evaluationContextProvider, cryptoQueryCreator);
    }
}
