## 1. SPI 接口定稿 (lcl-spi)

- [x] 1.1 扩展 `VaultStore` 接口：添加 `rotate(VaultDocument)` 和 `loadAll()` 方法签名，更新 Javadoc
- [x] 1.2 扩展 `VaultDocument` record：添加 `version`(long)、`cmkProvider`(String)、`cmkId`(String)、`createdAt`(Instant)、`updatedAt`(Instant) 字段
- [x] 1.3 定稿 `StorageAdapter` 接口：添加 `buildEncryptedPayload`、`extractBlob`、`extractTypeMarker`、`extractBlindIndex`、`isEncryptedPayload` 方法
- [x] 1.4 定稿 `QueryTransformer` 接口：添加 `rewriteFieldName`、`rewriteQueryValue`、`supportsField` 方法
- [x] 1.5 新增 `OptimisticLockException extends CryptoException`
- [x] 1.6 编译验证 lcl-spi 模块

## 2. lcl-adapter-mongodb 模块创建

- [x] 2.1 创建 `lcl-adapter-mongodb/pom.xml`（依赖 lcl-spi、lcl-core、spring-boot-starter-data-mongodb）
- [x] 2.2 在父 pom.xml 中添加 `lcl-adapter-mongodb` module
- [x] 2.3 实现 `MongoVaultStore`（save/load/exists/rotate/loadAll，基于 MongoTemplate，collection `__lcl_keyvault`）
- [x] 2.4 实现 `MongoStorageAdapter`（BSON Document 格式：`{c, _e, _t, b?}`）
- [x] 2.5 实现 `MongoQueryTransformer`（field.b 重写 + BlindIndexEngine 委托）
- [x] 2.6 实现 `MongoCryptoEventListener`（AbstractMongoEventListener，onBeforeSave 加密 + onBeforeConvert 解密，委托 FieldCryptoService + MongoStorageAdapter）
- [x] 2.7 创建 `MongoAdapterAutoConfiguration`（@ConditionalOnClass + @ConditionalOnBean + @ConditionalOnMissingBean）
- [x] 2.8 创建 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [x] 2.9 编译验证 lcl-adapter-mongodb 模块

## 3. KeyVaultService 去 Mongo 化 (lcl-spring-boot-starter)

- [x] 3.1 重构 `KeyVaultService` 构造函数：`MongoTemplate` 参数替换为 `VaultStore`
- [x] 3.2 重构 `ensureVaultInitialized`：使用 `VaultStore.load()`/`save()` 替代 MongoTemplate 直接操作
- [x] 3.3 重构 `rotateDek`：使用 `VaultStore.rotate()` 替代 MongoTemplate findAndModify
- [x] 3.4 重构 vault 文档读写：使用 `VaultDocument` record 替代 BSON Document 手工拼装
- [x] 3.5 移除 KeyVaultService 中所有 `org.bson`/`org.springframework.data.mongodb` import
- [x] 3.6 编译验证 starter 模块（不含测试）

## 4. Listener/Converter 适配 StorageAdapter

- [x] 4.1 重构 `CryptoBeforeSaveListener`（或等效逻辑）：加密字段写入通过 `StorageAdapter.buildEncryptedPayload()` 完成
- [x] 4.2 重构 `CryptoMappingMongoConverter`（或等效解密逻辑）：payload 判定通过 `StorageAdapter.isEncryptedPayload()`，blob 提取通过 `extractBlob()`
- [x] 4.3 重构 `CryptoMongoQueryCreator`：查询重写通过 `QueryTransformer.rewriteFieldName()`/`rewriteQueryValue()` 完成
- [x] 4.4 将 MongoDB 事件绑定（AbstractMongoEventListener）迁移至 adapter 模块的 `MongoCryptoEventListener`
- [x] 4.5 starter pom.xml 将 `spring-boot-starter-data-mongodb` 改为 `optional`
- [x] 4.6 编译验证 starter 模块（不含测试）

## 5. AutoConfiguration 更新

- [x] 5.1 更新 starter 的 `LclAutoConfiguration`：注入 `VaultStore`、`StorageAdapter`、`QueryTransformer` bean
- [x] 5.2 添加 fail-fast 校验：无 VaultStore bean 时抛出明确错误信息
- [x] 5.3 更新 `TestKeyVaultService` 和测试基础设施：使用 in-memory VaultStore 实现
- [x] 5.4 编译验证

## 6. 测试适配

- [x] 6.1 创建 `InMemoryVaultStore` 测试实现（ConcurrentHashMap 支撑）
- [x] 6.2 更新 `KeyVaultServiceTest`：使用 InMemoryVaultStore 替代 null MongoTemplate
- [x] 6.3 更新 `KeyVaultCorruptionTest`：适配 VaultStore API
- [x] 6.4 更新 `FieldCryptoServiceTest`：适配 StorageAdapter 接口
- [x] 6.5 更新 `LclEndToEndTest`：添加 lcl-adapter-mongodb test 依赖，适配新 API
- [x] 6.6 为 `MongoVaultStore` 编写单元测试（mock MongoTemplate 或 embedded Mongo）
- [x] 6.7 为 `MongoStorageAdapter` 编写单元测试
- [x] 6.8 运行全量单元测试验证

## 7. Examples & 文档更新

- [x] 7.1 更新 lcl-examples 各模块 pom.xml：添加 `lcl-adapter-mongodb` 依赖
- [x] 7.2 更新 README.md 模块结构（添加 lcl-adapter-mongodb）
- [x] 7.3 更新 docs/architecture.md（Adapter 层描述）
- [x] 7.4 更新 docs/configuration.md（adapter 配置说明）

## 8. 全量构建验证

- [x] 8.1 执行 `mvn clean install -DskipTests` 全模块编译
- [x] 8.2 执行全量单元测试 `mvn test`
- [x] 8.3 验证 lcl-spi 无 Spring/DB 依赖（dependency:tree 检查）
- [x] 8.4 验证 lcl-adapter-mongodb 不依赖 lcl-spring-boot-starter（依赖方向正确）
