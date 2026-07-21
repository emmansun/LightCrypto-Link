## 1. SPI 接口新增 (lcl-spi)

- [x] 1.1 在 `lcl-spi` 新增 `StructuredValueCodec` 接口 (`io.github.emmansun.lightcrypto.spi`)，定义 `byte[] encode(Object, String)` 和 `Object decode(byte[], String)`
- [x] 1.2 在 `lcl-spi` 新增 `DocumentAccessor` 接口 (`io.github.emmansun.lightcrypto.spi`)，定义 `getField`/`setField`/`isDocumentLike`/`asList`/`asMap` 方法

## 2. SPI BSON 实现 (lcl-adapter-mongodb)

- [x] 2.1 在 `lcl-adapter-mongodb` 新增 `BsonStructuredValueCodec`，使用 `DocumentCodec` + `RawBsonDocument` 实现 encode/decode
- [x] 2.2 在 `lcl-adapter-mongodb` 新增 `BsonDocumentAccessor`，基于 `org.bson.Document` 实现字段访问

## 3. 100% MongoDB 类迁移 (starter → adapter-mongodb)

- [x] 3.1 迁移 `MongoEncryptHandler` 到 `lcl-adapter-mongodb` (包名调整为 `io.github.emmansun.lightcrypto.adapter.mongodb`)
- [x] 3.2 迁移 `MongoDecryptHandler` 到 `lcl-adapter-mongodb`
- [x] 3.3 迁移 `CryptoBeforeSaveListener` 到 `lcl-adapter-mongodb`
- [x] 3.4 迁移 `CryptoMappingMongoConverter` 到 `lcl-adapter-mongodb`
- [x] 3.5 迁移 `CryptoMongoRepositoryFactory` 到 `lcl-adapter-mongodb`
- [x] 3.6 迁移 `CryptoMongoRepositoryFactoryBean` 到 `lcl-adapter-mongodb`
- [x] 3.7 迁移 `CryptoPartTreeMongoQuery` 到 `lcl-adapter-mongodb`
- [x] 3.8 迁移 `CryptoQueryLookupStrategy` 到 `lcl-adapter-mongodb`
- [x] 3.9 迁移 `CryptoMongoQueryCreator` 到 `lcl-adapter-mongodb`
- [x] 3.10 删除 starter 中已迁移的 9 个类源文件

## 4. Starter 服务层去 BSON 化

- [x] 4.1 重构 `FieldCryptoService`：注入 `DocumentAccessor` + `StructuredValueCodec`，移除所有 `org.bson.*` import
- [x] 4.2 重构 `ProgrammaticCryptoService`：`encryptValue` 返回类型改为 `Object`，`decodeStructuredValue` 委托给 `StructuredValueCodec`，移除所有 `org.bson.*` import
- [x] 4.3 验证 `EntityMetadataCache` 无 MongoDB import（如有则修复）

## 5. AutoConfiguration 拆分

- [x] 5.1 重构 `LightCryptoLinkAutoConfiguration`：移除 `@ConditionalOnClass(MongoTemplate.class)`、`@EnableMongoRepositories`、所有 MongoDB 特有 Bean 定义，新增 `DocumentAccessor`/`StructuredValueCodec` 注入传递给 `FieldCryptoService`/`ProgrammaticCryptoService`
- [x] 5.2 扩展 `MongoAdapterAutoConfiguration`：注册所有迁入的 MongoDB Bean（listener、converter、repository factory、handlers、query creator、`BsonDocumentAccessor`、`BsonStructuredValueCodec`），添加 `@EnableMongoRepositories` 和 `@AutoConfiguration(after = LightCryptoLinkAutoConfiguration.class)`
- [x] 5.3 更新 starter 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（移除 MongoDB 特有引用）
- [x] 5.4 更新 adapter-mongodb 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（确保 `MongoAdapterAutoConfiguration` 注册）

## 6. POM 依赖调整

- [x] 6.1 `lcl-spring-boot-starter/pom.xml`：移除 `spring-boot-starter-data-mongodb` compile 依赖；添加 `lcl-spi` 依赖（如未声明）
- [x] 6.2 `lcl-adapter-mongodb/pom.xml`：确保依赖 `lcl-spring-boot-starter`（获取 `FieldCryptoService` 等）和 `lcl-spi`
- [x] 6.3 验证 starter 编译通过（`mvn compile -pl lcl-spring-boot-starter` 无 MongoDB on classpath 错误）

## 7. 测试迁移与修复

- [x] 7.1 迁移 starter 中 MongoDB 相关测试类到 adapter-mongodb 的 test 目录
- [x] 7.2 修复所有测试中的 import 路径（旧包名 → 新包名）
- [x] 7.3 补充 `BsonStructuredValueCodec` 和 `BsonDocumentAccessor` 单元测试
- [x] 7.4 补充 `FieldCryptoService` 使用 SPI 后的单元测试（mock `DocumentAccessor` + `StructuredValueCodec`）
- [x] 7.5 运行全量测试 `mvn test` 确保回归零失败

## 8. Examples 模块兼容

- [x] 8.1 更新 `lcl-examples/basic-crud/pom.xml` 添加 `lcl-adapter-mongodb` 依赖
- [x] 8.2 更新 examples 中所有 import 路径（迁入类的包名更新）
- [x] 8.3 验证 examples 编译通过
