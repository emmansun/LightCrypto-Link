## Context

`lcl-spring-boot-starter` 当前承载了 31 个 Java 源文件，其中约 12 个直接依赖 MongoDB/BSON API。`lcl-adapter-mongodb` 模块已存在（Phase 1 产物），包含 `MongoVaultStore`、`MongoStorageAdapter`、`MongoQueryTransformer`、`MongoCryptoEventListener` 和 `MongoAdapterAutoConfiguration` 五个类。目标是将所有 MongoDB 特有代码迁入 adapter 模块，使 starter 成为存储无关的通用加密 starter。

**当前耦合热点：**
- `FieldCryptoService` / `ProgrammaticCryptoService`：编排逻辑通用，但 `Document.get/put`、`RawBsonDocument`、`DocumentCodec` 硬编码
- `CryptoBeforeSaveListener`：整个类绑定 `BeforeSaveEvent` + `Document` 遍历
- `MongoEncryptHandler` / `MongoDecryptHandler`：100% BSON 特有
- `LightCryptoLinkAutoConfiguration`：混合了通用 Bean 和 MongoDB 特有 Bean

**约束：**
- 不为假设的 JPA 适配器过度设计 SPI
- 保持现有公开 API 向后兼容（`FieldCryptoService.decryptDocument`、`ProgrammaticCryptoService` 全部方法签名）
- `lcl-spi` 仅新增最小必要接口

## Goals / Non-Goals

**Goals:**
- starter POM 移除 `spring-boot-starter-data-mongodb` 直接依赖
- 所有 import `org.bson.*` / `org.springframework.data.mongodb.*` 的类迁入 `lcl-adapter-mongodb`
- starter 仅保留存储无关的通用加密编排（`KeyVaultService`、`EntityMetadataCache`、`TypeSerializer/Deserializer`、`FieldCryptoService`、`ProgrammaticCryptoService`）
- 引入最小 SPI 使 `FieldCryptoService` / `ProgrammaticCryptoService` 不再直接引用 BSON
- 现有 MongoDB 用户引入 starter + adapter-mongodb 后行为完全不变

**Non-Goals:**
- 不设计 JPA adapter（Phase 4 按需设计）
- 不新增 `QueryRewriteEngine`、`LifecycleBinding` 等深层 SPI
- 不改变加密线格式（Wire Format V1）
- 不改变 `@Encrypted` 注解语义

## Decisions

### Decision 1: 新增 `StructuredValueCodec` SPI

**选择：** 在 `lcl-spi` 新增 `StructuredValueCodec` 接口：
```java
public interface StructuredValueCodec {
    byte[] encode(Object structuredValue, String typeMarker);
    Object decode(byte[] data, String typeMarker);
}
```

**理由：** `FieldCryptoService.decodeStructuredValue()` 和 `ProgrammaticCryptoService.decodeStructuredValue()` 都硬编码了 `RawBsonDocument` + `DocumentCodec`。这是唯一需要在 starter 中调用 BSON API 的热点。引入该 SPI 后，starter 委托给 codec，adapter 提供 BSON 实现。

**替代方案：**
- (A) 将 `decodeStructuredValue` 完全移入 `StorageAdapter` — 拒绝：`StorageAdapter` 职责是叶节点 payload 格式，不应承担结构化值序列化
- (B) 让 `FieldCryptoService` 直接返回 `byte[]` 由调用方解码 — 拒绝：破坏公开 API

### Decision 2: 引入 `DocumentAccessor` SPI 解耦 Document 遍历

**选择：** 在 `lcl-spi` 新增 `DocumentAccessor` 接口：
```java
public interface DocumentAccessor {
    Object getField(Object document, String field);
    void setField(Object document, String field, Object value);
    boolean isDocumentLike(Object value);
    Iterable<?> asList(Object value);
    Iterable<Map.Entry<String, Object>> asMap(Object value);
}
```

**理由：** `FieldCryptoService` 的 `getFieldValue`/`setFieldValue`/`isDocumentLike` 方法全部 `instanceof org.bson.Document`。引入 `DocumentAccessor` 后，`FieldCryptoService` 通过该接口操作文档，不再直接引用 `org.bson.Document`。

**替代方案：**
- (A) 将 `FieldCryptoService` 整个移入 adapter — 拒绝：该服务的编排逻辑（DEK lookup、wire format 解析）是存储无关的
- (B) 使用 `Map<String, Object>` 统一抽象 — 拒绝：JPA 场景下实体不是 Map

### Decision 3: 类迁移清单

**100% 迁入 adapter-mongodb（无改造）：**
| 类 | 原因 |
|---|------|
| `CryptoMappingMongoConverter` | extends `MappingMongoConverter` |
| `CryptoMongoRepositoryFactory` | extends `MongoRepositoryFactory` |
| `CryptoMongoRepositoryFactoryBean` | extends `MongoRepositoryFactoryBean` |
| `CryptoPartTreeMongoQuery` | extends `PartTreeMongoQuery` |
| `CryptoQueryLookupStrategy` | 依赖 `MongoOperations`、`MongoQueryMethod` |
| `MongoEncryptHandler` | 100% BSON Document 操作 |
| `MongoDecryptHandler` | 100% BSON Document 操作 |
| `CryptoBeforeSaveListener` | extends `AbstractMongoEventListener` |
| `CryptoMongoQueryCreator` | 返回 `BasicQuery`、操作 `Document` criteria |

**保留在 starter（去除 BSON 依赖后）：**
| 类 | 改造方式 |
|---|---------|
| `FieldCryptoService` | `Document.get/put` → `DocumentAccessor`；`decodeStructuredValue` → `StructuredValueCodec` |
| `ProgrammaticCryptoService` | `Document` 返回类型改为 `Object`（或保持 `Map<String,Object>`）；`decodeStructuredValue` → `StructuredValueCodec` |
| `EntityMetadataCache` | 无 BSON 依赖，保留 |
| `KeyVaultService` | 无 BSON 依赖，保留 |
| `TypeSerializer`/`TypeDeserializer` | 无 BSON 依赖，保留 |

### Decision 4: AutoConfiguration 拆分策略

**选择：**
- `LightCryptoLinkAutoConfiguration`（starter）：移除 `@ConditionalOnClass(MongoTemplate.class)` 和 `@EnableMongoRepositories`。仅注册通用 Bean：`CmkProvider`、`TypeSerializer`、`TypeDeserializer`、`EntityMetadataCache`、`KeyVaultService`、`BlindIndexEngine`、`FieldCryptoService`、`ProgrammaticCryptoService`
- `MongoAdapterAutoConfiguration`（adapter-mongodb）：扩展为注册所有 MongoDB 特有 Bean：listener、converter、repository factory、`MongoEncryptHandler`、`MongoDecryptHandler`、`CryptoMongoQueryCreator`、`DocumentAccessor` (BSON impl)、`StructuredValueCodec` (BSON impl)

**`ProgrammaticCryptoService.encryptValue` 返回类型：**
- 改为返回 `Object`（运行时仍为 `Document`），由 `StorageAdapter.buildEncryptedPayload` 构建 — 这样 starter 不需要知道 `Document` 类型
- 或者让 `ProgrammaticCryptoService` 接收 `StorageAdapter` 来构建 payload

### Decision 5: `ProgrammaticCryptoService` API 兼容策略

**选择：** `encryptValue` 方法返回类型改为 `Object`（**BREAKING** 小范围）。理由：
- 该方法返回的是"加密子文档"，在不同存储中类型不同（BSON Document vs JSON Map）
- 现有用户转型：`Document subDoc = (Document) pcs.encryptValue(...)` 即可

**替代方案：**
- (A) 保持返回 `Document` 不变，让 starter 依赖 BSON — 拒绝：违背目标
- (B) 提供两个重载：一个返回 `Map<String,Object>` 一个返回 `Object` — 拒绝：过度设计

## Risks / Trade-offs

- **[Breaking Change] `ProgrammaticCryptoService.encryptValue` 返回 `Object`** → 现有用户需添加类型转换。影响范围小（该方法主要用于测试/迁移脚本）
- **[Breaking Change] MongoDB 用户必须引入 adapter-mongodb** → starter 不再自动传递 MongoDB 依赖。可通过文档说明 + BOM 缓解
- **[迁移风险] 包名变更导致 `import` 全部修改** → 所有迁移类的旧包名 import 需更新。通过 IDE 重构工具自动化
- **[风险] `DocumentAccessor` SPI 设计过窄** → Phase 4 JPA adapter 时可能需要扩展。届时再迭代，不过度设计
- **[风险] AutoConfiguration 顺序** → adapter-mongodb 的 `MongoAdapterAutoConfiguration` 依赖 starter 的 `LightCryptoLinkAutoConfiguration`（需要 `KeyVaultService` 等 Bean）。通过 `@AutoConfiguration(after = LightCryptoLinkAutoConfiguration.class)` 保证顺序
