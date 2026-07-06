## Why

当前 LightCrypto-Link 的透明解密仅在 `CryptoMappingMongoConverter.read()` 和 `project()` 中触发，覆盖了通过 Spring Data Repository 或 `MongoTemplate.findOne/find()` 的标准读取路径。但在以下场景中，用户获得的是包含加密子文档的原始 BSON `Document`，无法方便地解密：

- **聚合查询**：`MongoTemplate.aggregate()` 返回原始 `Document`，不经过 `CryptoMappingMongoConverter`
- **原生驱动操作**：使用 `MongoCollection<Document>` 直接查询
- **数据迁移 / 运维脚本**：批量导出、跨集合迁移、数据校验等
- **调试排查**：开发或运维阶段需要查看、验证加密数据

这些场景要求用户自行解析加密子文档格式 (`_e`, `_k`, `_a`, `_t`, `c`)、查找 DEK、选择正确的解密算法、并反序列化类型——繁琐且容易出错，同时将用户代码与内部格式强耦合。

## What Changes

- **新增 `FieldCryptoService` Spring Bean**：提供面向实体级别的手动解密 API，注入即用
  - `Document decryptDocument(Document rawDocument, Class<?> entityClass)` — 解密 raw Document 中所有 `@Encrypted` 字段，就地替换加密子文档为明文值，返回同一 Document（链式调用友好）
- **重构 `CryptoMappingMongoConverter.decryptFields()`**：将核心解密逻辑委托给 `FieldCryptoService`，消除重复代码，Converter 仅保留"何时解密"的职责，"如何解密"下沉到 Service
- **自动配置注册**：在 `CryptoAutoConfiguration` 中声明 `FieldCryptoService` Bean

## Capabilities

### New Capabilities

- `manual-decrypt-service`: 面向实体级别的显式解密服务，封装加密子文档格式解析、DEK 查找（支持密钥轮转）、多算法分发（AES-256-GCM/CBC、SM4-GCM/CBC）和类型反序列化，供用户在绕过 Repository 获取 raw Document 后使用。

### Modified Capabilities

<!-- 无需修改现有 spec 行为要求；CryptoMappingMongoConverter 的重构属于实现细节，不改变其透明解密的外部行为。 -->

## Impact

- **代码**：
  - 新增 `FieldCryptoService`（`io.github.emmansun.lightcrypto.service` 包）
  - 修改 `CryptoMappingMongoConverter`，将 `decryptFields()` 委托给 `FieldCryptoService`
  - 修改 `CryptoAutoConfiguration`，注册新 Bean
- **API**：新增一个公开 Bean，不破坏现有 API
- **依赖**：无新增外部依赖，复用现有 `CryptoCodec`、`KeyVaultService`、`TypeDeserializer`、`EntityMetadataCache`
- **测试**：需要为 `FieldCryptoService` 新增单元测试；需确认 `CryptoMappingMongoConverter` 重构后原有测试仍通过
