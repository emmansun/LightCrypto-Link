## Why

`lcl-spring-boot-starter` 当前包含 31 个源文件，其中至少 12 个直接依赖 MongoDB/BSON API（`Document`、`MappingMongoConverter`、`MongoRepositoryFactory`、`BeforeSaveEvent` 等）。这使得 starter 的 POM 必须引入 `spring-boot-starter-data-mongodb`，导致非 MongoDB 项目（如未来 MySQL/JPA 适配器）无法复用 starter 中的通用加密编排逻辑（`KeyVaultService`、`TypeSerializer`、`EntityMetadataCache` 等）。需要立即将 MongoDB 特有类迁入已有的 `lcl-adapter-mongodb` 模块，使 starter 成为存储无关的通用加密 starter。

## What Changes

- 将 7 个 100% MongoDB 特有的类从 starter 迁入 `lcl-adapter-mongodb`：
  - `CryptoMappingMongoConverter`、`CryptoMongoRepositoryFactory`、`CryptoMongoRepositoryFactoryBean`
  - `CryptoPartTreeMongoQuery`、`CryptoQueryLookupStrategy`
  - `MongoEncryptHandler`、`MongoDecryptHandler`
- 将 `CryptoBeforeSaveListener`（绑定 `BeforeSaveEvent` + `Document` 遍历）迁入 `lcl-adapter-mongodb`
- 将查询基础设施（`CryptoMongoQueryCreator`）迁入 `lcl-adapter-mongodb`
- 在 starter 中引入 `StructuredValueCodec` SPI，将 `FieldCryptoService` 和 `ProgrammaticCryptoService` 中的 BSON 序列化/反序列化委托给该 SPI，adapter 模块提供 BSON 实现
- 拆分 `LightCryptoLinkAutoConfiguration`：starter 保留通用 Bean（`KeyVaultService`、`EntityMetadataCache`、`FieldCryptoService` 等），adapter 模块提供 MongoDB 特有的 AutoConfiguration（listener、converter、repository factory）
- starter 的 POM 移除 `spring-boot-starter-data-mongodb` 直接依赖，改为 `optional`/`provided` 或完全移除

## Capabilities

### New Capabilities
- `structured-value-codec`: 新增 SPI 接口，用于加密/解密过程中结构化值（嵌套对象、集合）的序列化与反序列化，解耦 BSON 特定实现
- `starter-mongo-separation`: starter 模块与 MongoDB 解耦后的模块职责划分与 AutoConfiguration 拆分规则

### Modified Capabilities
- `transparent-listener`: 实现位置从 starter 迁移到 adapter-mongodb，行为不变
- `transparent-query`: 实现位置从 starter 迁移到 adapter-mongodb，行为不变
- `manual-decrypt-service`: `FieldCryptoService` 中 BSON 操作委托给 `StructuredValueCodec`，公开 API 不变
- `type-serialization`: 无行为变更，但反序列化的调用路径调整（从直接操作 Document 改为通过 codec）

## Impact

- **代码位置**：约 12 个类从 `lcl-spring-boot-starter` 迁移到 `lcl-adapter-mongodb`，包名相应调整
- **依赖变更**：starter POM 移除 `spring-boot-starter-data-mongodb`；adapter-mongodb 承担所有 MongoDB 依赖
- **AutoConfiguration**：拆为 starter 通用部分 + adapter-mongodb 的 `MongoAdapterAutoConfiguration` 扩展
- **现有用户**：使用 MongoDB 的用户需额外引入 `lcl-adapter-mongodb` 依赖（**BREAKING** 如果不自动传递）
- **测试**：现有集成测试需调整 import 路径，测试覆盖范围不变
- **SPI 扩展点**：新增 `StructuredValueCodec` 接口到 `lcl-spi` 模块
