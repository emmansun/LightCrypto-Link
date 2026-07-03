## Why

MongoDB 官方 CSFLE 依赖 C++ 的 libmongocrypt 二进制库和企业版功能，在跨平台部署、容器化交付和国产化适配场景中存在严重的兼容性障碍。LightCrypto-Link (LCL) 旨在提供一个纯 Java、零外部链接库的 Spring Boot Starter，通过信封加密 + 盲索引双轨制，在不依赖 libmongocrypt 的前提下实现字段级全随机加密与高性能精确匹配查询，满足等保/密评合规要求。

## What Changes

- 新增 `@Encrypted` 注解，业务层在 POJO 字段上标记即可启用透明加密，零侵入（字段保持原始 Java 类型）
- 实现信封加密架构：CMK（v1 对称 AES-256）包裹 DEK，DEK 加密业务数据；DEK 和 HMAC 密钥存储在 MongoDB `__lcl_keyvault` 集合中
- 实现盲索引机制：HMAC-SHA-256 确定性哈希，支持 `blindIndex=true` 的字段可被 Spring Data 方法名查询透明命中
- 实现 Spring Data MongoDB 事件监听器：`BeforeSaveEvent` 透明加密写入，`BeforeConvertEvent` 透明解密读取
- 实现自定义 `QueryLookup` 管线：`findByPhone(String)` 自动改写为 `phone.b` 盲索引精确匹配查询
- 实现 `CmkProvider` SPI：v1 提供 `LocalSymmetricCmkProvider`，v2 可扩展 Azure Key Vault / 阿里云 KMS（对称/非对称）
- 实现 `KeyVaultService`：首次启动自动生成 DEK 并入库，后续启动从 vault 加载、解包、校验 KCV 和双密钥绑定
- 重写 `LightCryptoLinkAutoConfiguration`，装配全部组件并注册 Spring Boot 自动配置
- 新增 `query/` 包，包含 `CryptoMongoRepositoryFactory`、`CryptoQueryLookupStrategy`、`CryptoMongoQueryCreator`
- 新增 `resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## Capabilities

### New Capabilities
- `encrypted-annotation`: `@Encrypted` 注解定义与元数据扫描，支持 algorithm / blindIndex / fieldName 属性及默认值
- `envelope-encryption`: 信封加密核心引擎，CMK 包裹/解包 DEK，AES-256-GCM 加密/解密，KCV 校验与双密钥绑定
- `blind-index`: HMAC-SHA-256 盲索引生成与管理，字段名盐机制，确定性哈希保证精确匹配
- `key-vault`: MongoDB `__lcl_keyvault` 集合管理，DEK 持久化存储，首次启动自动初始化，vault 文档读写
- `transparent-listener`: Spring Data MongoDB 事件监听，BeforeSaveEvent 加密拦截，BeforeConvertEvent 解密拦截，类型序列化/还原
- `transparent-query`: 自定义 QueryLookup 管线，方法名查询自动改写为盲索引 Criteria，支持 eq / in / ne / nin / isNull / isNotNull
- `cmk-provider-spi`: CMK Provider 抽象接口（wrap/unwrap），v1 LocalSymmetricCmkProvider 实现，v2 可扩展 AKV/Aliyun KMS
- `type-serialization`: 多类型确定性序列化器，支持 String / 数值 / Boolean / LocalDate(Time) / Enum / byte[]

### Modified Capabilities
<!-- No existing capabilities to modify -->

## Impact

- **代码**: 项目从骨架态进入完整实现，新增约 15-20 个 Java 类，分布在 annotation / model / service / provider / listener / query / config 七个包中
- **配置**: 宿主微服务需在 `application.yml` 中配置 `lcl.crypto.cmk`（32字节 hex）和 `lcl.crypto.key-vault-id`
- **数据库**: SDK 自动在业务同库创建 `__lcl_keyvault` 集合；加密字段的 BSON 结构变更为嵌套子文档 `{c, b?, _e, _t}`
- **依赖**: 无新增 Maven 依赖，复用现有的 Spring Boot 3.2.5 + BouncyCastle 1.78.1 + Spring Data MongoDB + Lombok
- **兼容性**: 仅面向 Spring Boot 3.x + Java 17+ 环境；不支持 Java 8 / javax 命名空间
