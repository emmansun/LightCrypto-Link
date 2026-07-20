package io.github.emmansun.lightcrypto.query;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.query.MongoQueryMethod;
import org.springframework.data.mongodb.repository.query.PartTreeMongoQuery;
import org.springframework.data.mongodb.repository.query.ConvertingParameterAccessor;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.expression.ExpressionParser;

/**
 * Encryption-aware PartTree query — extends the Spring Data method-name query
 * to automatically rewrite encrypted fields into blind-index queries.
 */
public class CryptoPartTreeMongoQuery extends PartTreeMongoQuery {

    private final CryptoMongoQueryCreator cryptoQueryCreator;

    public CryptoPartTreeMongoQuery(MongoQueryMethod method,
                                    MongoOperations mongoOperations,
                                    ExpressionParser expressionParser,
                                    QueryMethodEvaluationContextProvider evaluationContextProvider,
                                    CryptoMongoQueryCreator cryptoQueryCreator) {
        super(method, mongoOperations, expressionParser, evaluationContextProvider);
        this.cryptoQueryCreator = cryptoQueryCreator;
    }

    @Override
    protected Query createQuery(ConvertingParameterAccessor accessor) {
        Query original = super.createQuery(accessor);
        Class<?> entityClass = getQueryMethod().getEntityInformation().getJavaType();
        return cryptoQueryCreator.rewrite(original, entityClass);
    }
}
