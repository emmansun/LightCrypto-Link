package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.model.PathSegmentType;
import io.github.emmansun.lightcrypto.service.KeyVaultService;
import io.github.emmansun.lightcrypto.service.TypeSerializer;
import io.github.emmansun.lightcrypto.spi.BlindIndexFieldChecker;
import io.github.emmansun.lightcrypto.spi.HmacKeyProvider;
import io.github.emmansun.lightcrypto.spi.QueryTransformer;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.repository.Repository;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoAdapterV4AutoConfigurationTest {

    @Test
    void createsCoreMongoBeansAndQueryTransformer() {
        MongoAdapterV4AutoConfiguration autoConfiguration = new MongoAdapterV4AutoConfiguration();

        assertThat(autoConfiguration.mongoStorageAdapter()).isInstanceOf(MongoStorageAdapter.class);
        assertThat(autoConfiguration.bsonDocumentAccessor()).isInstanceOf(BsonDocumentAccessor.class);
        assertThat(autoConfiguration.bsonStructuredValueCodec()).isInstanceOf(BsonStructuredValueCodec.class);

        KeyVaultService keyVaultService = mock(KeyVaultService.class);
        byte[] hmacKey = new byte[32];
        hmacKey[0] = 42;
        when(keyVaultService.getActiveHmacKey("default.default.TestUser#phone")).thenReturn(hmacKey);

        HmacKeyProvider hmacKeyProvider = autoConfiguration.hmacKeyProvider(keyVaultService);
        BlindIndexFieldChecker checker = (field, entityType) -> true;

        QueryTransformer transformer = autoConfiguration.mongoQueryTransformer(
                hmacKeyProvider, checker, new TypeSerializer());

        assertThat(transformer.rewriteFieldName("phone")).isEqualTo("phone.b");
        assertThat(transformer.supportsField("phone", TestUser.class)).isTrue();

        Object rewritten = transformer.rewriteQueryValue("13800138000", "default.default.TestUser#phone");
        assertThat(rewritten).isInstanceOf(String.class);
        assertThat((String) rewritten).isNotBlank();
    }

    @Test
    void blindIndexFieldCheckerReadsEncryptedMetadata() {
        MongoAdapterV4AutoConfiguration autoConfiguration = new MongoAdapterV4AutoConfiguration();
        EntityMetadataCache metadataCache = mock(EntityMetadataCache.class);

        when(metadataCache.getEncryptedFields(TestUser.class)).thenReturn(List.of(
                metadata("phone", true),
                metadata("email", false)
        ));

        BlindIndexFieldChecker checker = autoConfiguration.blindIndexFieldChecker(metadataCache);
        assertThat(checker.hasBlindIndex("phone", TestUser.class)).isTrue();
        assertThat(checker.hasBlindIndex("email", TestUser.class)).isFalse();
        assertThat(checker.hasBlindIndex("missing", TestUser.class)).isFalse();
    }

    @Test
    void repositoryFactoryBeanCreatesCryptoMongoRepositoryFactory() throws Exception {
        CryptoMongoRepositoryFactoryBean<TestUserRepository, TestUser, String> factoryBean =
                new CryptoMongoRepositoryFactoryBean<>(TestUserRepository.class);
        factoryBean.setCryptoQueryCreator(mock(CryptoMongoQueryCreator.class));

        MongoOperations operations = mock(MongoOperations.class);
        MappingMongoConverter converter = mock(MappingMongoConverter.class);
        @SuppressWarnings("rawtypes")
        MappingContext mappingContext = mock(MappingContext.class);

        when(operations.getConverter()).thenReturn(converter);
        when(converter.getMappingContext()).thenReturn(mappingContext);

        Method method = CryptoMongoRepositoryFactoryBean.class
                .getDeclaredMethod("getFactoryInstance", MongoOperations.class);
        method.setAccessible(true);
        Object factory = method.invoke(factoryBean, operations);

        assertThat(factory).isInstanceOf(CryptoMongoRepositoryFactory.class);
    }

    @Test
    void repositoryFactoryProvidesCryptoLookupStrategy() {
        MongoOperations operations = mock(MongoOperations.class);
        MappingMongoConverter converter = mock(MappingMongoConverter.class);
        @SuppressWarnings("rawtypes")
        MappingContext mappingContext = mock(MappingContext.class);

        when(operations.getConverter()).thenReturn(converter);
        when(converter.getMappingContext()).thenReturn(mappingContext);

        ExposedFactory factory = new ExposedFactory(operations, mock(CryptoMongoQueryCreator.class));
        Optional<?> strategy = factory.exposeGetQueryLookupStrategy();

        assertThat(strategy).isPresent();
        assertThat(strategy.get()).isInstanceOf(CryptoQueryLookupStrategy.class);
    }

    private static EncryptedFieldMetadata metadata(String fieldName, boolean blindIndex) {
        return new EncryptedFieldMetadata(
                List.<MethodHandle>of(),
                List.of(fieldName),
                List.of(PathSegmentType.FIELD),
                String.class,
                SymmetricAlgorithm.AES_256_GCM,
                blindIndex,
                false,
                null,
                Namespace.parse("default.default.TestUser#" + fieldName)
        );
    }

    private static final class ExposedFactory extends CryptoMongoRepositoryFactory {

        private ExposedFactory(MongoOperations mongoOperations, CryptoMongoQueryCreator cryptoQueryCreator) {
            super(mongoOperations, cryptoQueryCreator);
        }

        Optional<?> exposeGetQueryLookupStrategy() {
            return super.getQueryLookupStrategy(null, null);
        }
    }

    interface TestUserRepository extends Repository<TestUser, String>, Serializable {
    }

    static final class TestUser {
        String id;
        String phone;
        String email;

        Document toDocument() {
            return new Document("_id", id)
                    .append("phone", phone)
                    .append("email", email);
        }
    }
}
