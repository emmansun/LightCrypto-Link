package io.github.emmansun.lightcrypto.adapter.mongodb;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.query.MongoQueryMethod;
import org.springframework.data.mongodb.repository.query.PartTreeMongoQuery;
import org.springframework.data.mongodb.repository.query.ConvertingParameterAccessor;
import org.springframework.data.repository.query.ValueExpressionDelegate;

/**
 * Encryption-aware PartTree query (Spring Boot 4.x variant) — extends the Spring Data
 * method-name query to automatically rewrite encrypted fields into blind-index queries.
 *
 * <p>Adapted to use {@link ValueExpressionDelegate} instead of the removed
 * {@code ExpressionParser} + {@code QueryMethodEvaluationContextProvider} pair.
 *
 * @since 1.0.0
 */
public class CryptoPartTreeMongoQuery extends PartTreeMongoQuery {

    private final CryptoMongoQueryCreator cryptoQueryCreator;

    public CryptoPartTreeMongoQuery(MongoQueryMethod method,
                                    MongoOperations mongoOperations,
                                    ValueExpressionDelegate valueExpressionDelegate,
                                    CryptoMongoQueryCreator cryptoQueryCreator) {
        super(method, mongoOperations, valueExpressionDelegate);
        this.cryptoQueryCreator = cryptoQueryCreator;
    }

    @Override
    protected Query createQuery(ConvertingParameterAccessor accessor) {
        Query original = super.createQuery(accessor);
        Class<?> entityClass = getQueryMethod().getEntityInformation().getJavaType();
        return cryptoQueryCreator.rewrite(original, entityClass);
    }
}
