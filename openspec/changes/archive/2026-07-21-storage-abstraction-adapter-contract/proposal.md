## Why

Phase 1 完成了 lcl-core 纯函数式密码学内核和 Wire Format V1，但 `KeyVaultService` 和加密监听器仍硬编码 `MongoTemplate`/BSON API。这阻碍了 Phase 4（MySQL/PostgreSQL 适配器）和 Phase 6（多存储联邦）的演进。Phase 2 将存储关注点从密码学编排中彻底剥离，建立 thin adapter 原则——核心仅依赖 SPI 接口，数据库特定逻辑全部下沉到 `lcl-adapter-*` 模块。

## What Changes

- **VaultStore SPI 定稿**：扩展 `lcl-spi` 中的 `VaultStore` 接口，增加 optimistic-locking rotation、批量加载、TTL 感知等方法签名；`VaultDocument` record 补充 `version`/`cmkInfo`/`updatedAt` 字段。
- **StorageAdapter Contract 定义**：在 `lcl-spi` 中定稿 `StorageAdapter` 接口——`buildEncryptedPayload(blob, typeMarker, blindIndex)` 和 `extractBlob(payload)` / `extractTypeMarker(payload)` / `isEncryptedPayload(payload)` 方法族，抽象不同数据库的加密字段存储格式。
- **QueryTransformer Contract 定义**：定稿 `QueryTransformer` 接口——`rewriteFieldCriteria(field, operator, blindIndexValue)` 方法，抽象 blind index 查询重写逻辑。
- **KeyVaultService 去 Mongo 化**：重构为仅依赖 `VaultStore` SPI，不再直接引用 `MongoTemplate`/`Document`/Spring Data 类型。
- **CryptoBeforeSaveListener / CryptoMappingMongoConverter 适配**：加密字段写入/读取通过 `StorageAdapter` 接口完成，查询重写通过 `QueryTransformer` 完成。
- **lcl-adapter-mongodb 模块**：新建模块，实现 `MongoVaultStore`（基于 MongoTemplate）、`MongoStorageAdapter`（BSON 子文档格式 `{c, _e, _t, b}`）、`MongoQueryTransformer`（`field.b` 重写）。
- **Spring Boot AutoConfiguration 更新**：自动检测 adapter 实现并注入；无 adapter 时 fail-fast。
- **BREAKING**：`KeyVaultService` 构造函数签名变更（`MongoTemplate` → `VaultStore`）；starter 不再传递依赖 `spring-boot-starter-data-mongodb`（改由 adapter 模块携带）。

## Capabilities

### New Capabilities
- `vault-store-spi`: VaultStore SPI 完整契约——save/load/exists/rotate（optimistic lock）/batch-load，以及 VaultDocument 数据模型定稿
- `storage-adapter-contract`: StorageAdapter 接口契约——加密字段 payload 的构建、解析、判定；定义 MongoDB 子文档格式和通用 Base64URL 列格式两种规范化实现
- `query-transformer-contract`: QueryTransformer 接口契约——blind index 查询重写的数据库无关抽象
- `mongo-adapter`: lcl-adapter-mongodb 模块——MongoVaultStore + MongoStorageAdapter + MongoQueryTransformer 实现，含 Spring Boot auto-configuration

### Modified Capabilities
- `key-vault`: 移除 MongoDB 特定描述（collection name、Document structure），改为引用 VaultStore SPI 契约
- `multi-dek-vault`: vault 持久化操作改为通过 VaultStore 接口描述，不再绑定 MongoTemplate API
- `transparent-listener`: 加密字段写入/读取改为通过 StorageAdapter 接口描述

## Impact

- **lcl-spi**：`VaultStore`、`VaultDocument`、`StorageAdapter`、`QueryTransformer` 从 placeholder 升级为完整接口
- **lcl-spring-boot-starter**：`KeyVaultService` 构造函数变更；`CryptoBeforeSaveListener`/`CryptoMappingMongoConverter`/`CryptoMongoQueryCreator` 改为委托 adapter；pom.xml 移除 `spring-boot-starter-data-mongodb` 直接依赖
- **新模块 lcl-adapter-mongodb**：依赖 `lcl-spi` + `lcl-core` + `spring-boot-starter-data-mongodb`
- **lcl-examples**：pom.xml 新增 `lcl-adapter-mongodb` 依赖
- **测试**：KeyVaultServiceTest 改用 in-memory VaultStore；集成测试移至 adapter 模块
