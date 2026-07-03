## Context

LightCrypto-Link (LCL) 是一个 Spring Boot Starter SDK，目标是为 Spring Data MongoDB 提供纯 Java 的字段级透明加密能力。当前项目处于骨架态：仅有一个 `LightCryptoLinkAutoConfiguration` 注册 BouncyCastle Provider，annotation / model / service / provider / listener / query / config 七个包均为空。

技术栈约束：Spring Boot 3.2.5 + Java 17 + BouncyCastle 1.78.1 + Spring Data MongoDB。Maven 依赖已就位（包括 optional 的 Azure KMS 和阿里云 KMS SDK），无需新增依赖。

宿主微服务的开发者是 SDK 的用户，他们只需在实体字段上加 `@Encrypted` 注解，配置 CMK，即可零侵入地获得加密能力。

## Goals / Non-Goals

**Goals:**
- 实现信封加密全链路：CMK → DEK → 业务数据加密，DEK 以包裹态存储在 MongoDB `__lcl_keyvault`
- 实现透明加解密：写入自动加密（BeforeSaveEvent），读取自动解密（BeforeConvertEvent）
- 实现盲索引 + 透明查询：`findByPhone()` 自动改写为 `phone.b` 盲索引匹配
- 实现多类型支持：String / 数值 / Boolean / LocalDate(Time) / Enum / byte[]
- 实现 CmkProvider SPI：v1 对称 CMK，架构预留 v2 非对称 KMS
- 首次启动自动初始化 vault，配置仅需 CMK + vault-id 两项

**Non-Goals:**
- v1 不实现国密 SM4 算法（放 v2）
- v1 不支持前缀/模糊/范围查询（盲索引扩展放 v2）
- v1 不支持自定义对象加密（需 serializer，放 v2）
- v1 不实现 Azure Key Vault / 阿里云 KMS Provider（放 v2）
- v1 不实现密钥轮换流程（放 v2）
- 不实现自定义 MongoTemplate 包装（透明查询通过 QueryLookup 实现）

## Decisions

### D1: 信封加密 + __lcl_keyvault 存储

**选择**: 借鉴 MongoDB CSFLE 的 `__keyVault` 架构，DEK 和 HMAC Key 被 CMK 包裹后存储在 `__lcl_keyvault` 集合中。

**替代方案**: DEK 明文存储在 application.yml（简单但安全性低，配置项多达 5 个）。

**理由**: 信封加密实现双因素分离（CMK + vault），即使配置文件泄露也无法获得 DEK；同时简化配置到仅 2 项（cmk + key-vault-id）。vault 同库部署降低运维复杂度。

### D2: BeforeSaveEvent + BeforeConvertEvent 双层拦截

**选择**: 写入用 `BeforeSaveEvent`（修改 BSON Document），读取用 `BeforeConvertEvent`（在 MappingMongoConverter 做类型映射前介入）。

**替代方案**: 纯 AfterConvertEvent 读取（太晚，Spring Data 已尝试类型映射会失败）；全局 ReadingConverter（太宽泛，会误伤非加密子文档）。

**理由**: BeforeSaveEvent 操作 Document 层面不干扰 Java 类型系统；BeforeConvertEvent 在类型映射前将加密子文档还原为原始类型值，Spring Data 后续映射正常工作。

### D3: 自定义 QueryLookup 实现透明查询

**选择**: 扩展 Spring Data 的 `MongoRepositoryFactory` → `QueryLookupStrategy` → `MongoQueryCreator` 链路，在 QueryCreator 层检测 `@Encrypted` 字段并改写 Criteria。

**替代方案**: 自定义 MongoTemplate（侵入性高）；Spring Data 方法名解析扩展（无法访问注解元数据）。

**理由**: QueryCreator 层既能访问实体元数据（判断 @Encrypted），又能修改 Criteria（改写字段路径和值），是最佳的拦截点。

### D4: _e 标记 + _t 类型标记的 BSON 子文档结构

**选择**: 加密子文档格式 `{c: Binary, b?: String, _e: 1, _t: String}`，`_e` 标识加密文档，`_t` 标识原始 Java 类型。

**替代方案**: 无标记的全局 Converter（无法区分加密文档与普通子文档）；业务层使用 EncryptedField 类型（侵入性高）。

**理由**: `_e` 标记让 BeforeConvertEvent 精准识别加密子文档；`_t` 标记让解密后能还原正确的 Java 类型；业务 POJO 始终保持原始类型（零侵入）。

### D5: blindIndex 默认 false

**选择**: `@Encrypted(blindIndex = false)` 为默认值。

**替代方案**: blindIndex 默认 true。

**理由**: 大多数加密字段不需要数据库级查询，默认关闭可减少存储空间和索引开销，需要查询的字段显式 opt-in。

### D6: CmkProvider SPI 设计

**选择**: `CmkProvider` 接口定义 `wrap(byte[])` 和 `unwrap(WrappedKey)` 两个方法，`WrappedKey` record 包含 ciphertext + algorithm 自描述字段。

**理由**: algorithm 字段让 vault 文档自描述解包方式，支持 CMK 从对称升级到非对称时旧数据仍可用旧算法解包。v1 实现 `LocalSymmetricCmkProvider`（AES-256-GCM），v2 扩展 Azure/Aliyun Provider（RSA-OAEP）。

### D7: 确定性序列化策略

**选择**: 每种支持类型定义唯一的序列化规则（String 原值、数值用 `String.valueOf`、BigDecimal 用 `toPlainString`、日期用 ISO-8601、Enum 用 `name()`、byte[] 用 HexFormat）。

**理由**: 盲索引要求同一值每次序列化结果相同，确定性序列化是 HMAC 哈希一致性的前提。BigDecimal 特别用 `toPlainString()` 避免科学计数法导致哈希不一致。

## Risks / Trade-offs

- **[风险] BeforeConvertEvent 性能开销**: 每个读取的 Document 都需要扫描所有字段检查 `_e` 标记。→ **缓解**: `EntityMetadataCache` 缓存 @Encrypted 字段元数据，只扫描已知的加密字段而非全部字段。

- **[风险] QueryLookup 覆盖不完整**: Spring Data 的查询方法名解析逻辑复杂（嵌套属性、别名等），自定义 QueryCreator 可能遗漏边界情况。→ **缓解**: v1 仅支持精确匹配类查询（eq/in/ne/nin/isNull/isNotNull），复杂查询场景要求用户使用 `@Query` 手动编写。

- **[风险] vault 首次初始化竞争**: 多实例同时首次启动可能导致并发写入 vault。→ **缓解**: 使用 `_id` 唯一索引 + upsert 语义，第二个写入者检测到已存在则跳过初始化，直接走加载流程。

- **[权衡] 同库 vault vs 独立库**: 同库部署简单但权限隔离弱。→ **接受**: v1 同库，通过配置项 `key-vault-database` 预留 v2 独立库能力。

- **[权衡] CMK 明文在配置中**: v1 CMK 仍以明文形式存在于配置源中。→ **接受**: 生产环境推荐通过环境变量或配置中心注入，v2 升级为 KMS Provider 后 CMK 私钥不离开 KMS。
