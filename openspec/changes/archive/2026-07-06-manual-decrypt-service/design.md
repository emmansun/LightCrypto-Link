## Context

LightCrypto-Link 当前通过 `CryptoMappingMongoConverter` 在 `read()` / `project()` 中透明解密 `@Encrypted` 字段，覆盖了标准 Repository/MongoTemplate 读取路径。但聚合查询、原生驱动、数据迁移等场景绕过了 Converter，用户获得的是包含加密子文档 `{c, _e, _k, _a, _t}` 的原始 `Document`，缺少便捷的解密入口。

现有解密核心逻辑位于 `CryptoMappingMongoConverter.decryptFields()`（`protected` 方法），包含子文档解析、kid 查找、算法分发、类型反序列化，是完整的可复用实现。

相关组件：
- `EntityMetadataCache` — 扫描 `@Encrypted` 字段元数据
- `KeyVaultService` — DEK 管理，`getDek(kid)` 按 kid 查找（含 ROTATED 状态）
- `CryptoCodec` — 多算法加解密引擎
- `TypeDeserializer` — 按 `_t` 类型标记反序列化

## Goals / Non-Goals

**Goals:**
- 提供一个 Spring Bean `FieldCryptoService`，用户注入后即可对 raw Document 执行 `decryptDocument(doc, EntityClass.class)`
- 复用 `CryptoMappingMongoConverter.decryptFields()` 的核心逻辑，消除重复代码
- 保持 Converter 的透明解密行为完全不变（外部行为零差异）
- API 封装内部格式细节，用户无需了解 `_e/_k/_a/_t/c` 子文档结构

**Non-Goals:**
- 不提供字段级别的单字段解密 API（当前阶段只支持文档级别）
- 不提供手动加密方法（写路径仍由 `CryptoBeforeSaveListener` 透明处理）
- 不暴露 `CryptoCodec`、`KeyVaultService` 等内部组件给用户
- 不支持嵌套文档/数组内的加密字段解密（当前架构不支持）

## Decisions

### Decision 1: 新建 `FieldCryptoService` 类，而非扩展 `KeyVaultService`

**选择**：在 `io.github.emmansun.lightcrypto.service` 包中新建 `FieldCryptoService`。

**理由**：
- `KeyVaultService` 的职责是密钥管理（DEK 生成、wrap/unwrap、kid 查找），添加文档解密方法会造成职责混乱
- `FieldCryptoService` 明确表达"字段加解密服务"的语义，未来可扩展加密方法
- 依赖注入清晰：`FieldCryptoService` 依赖 `EntityMetadataCache`、`CryptoCodec`、`TypeDeserializer`、`KeyVaultService`

**替代方案**：在 `KeyVaultService` 上添加 `decryptDocument` → 拒绝，职责不匹配

### Decision 2: 将 `decryptFields` 逻辑下沉到 `FieldCryptoService`，Converter 委托调用

**选择**：`FieldCryptoService` 拥有 `decryptDocument()` 核心实现；`CryptoMappingMongoConverter.decryptFields()` 改为单行委托 `fieldCryptoService.decryptDocument(document, entityClass)`。

**理由**：
- 单一真相源：解密逻辑只在 `FieldCryptoService` 中维护
- Converter 保留"何时解密"的编排职责（在 `read()`/`project()` 中判断 `hasEncryptedFields`）
- 原有 Converter 测试行为不变，只需更新构造参数

**替代方案**：`FieldCryptoService` 复制 `decryptFields` 代码 → 拒绝，产生重复代码

### Decision 3: `CryptoMappingMongoConverter` 构造参数变更

**变更**：将 `CryptoCodec` + `TypeDeserializer` 两个参数替换为单个 `FieldCryptoService` 参数。`EntityMetadataCache` 保留（Converter 需要 `hasEncryptedFields()` 判断）。

**影响**：
- `LightCryptoLinkAutoConfiguration.mappingMongoConverter()` 方法签名更新
- `CryptoMappingMongoConverterTest` 中 `TestableConverter` 子类构造更新
- Converter 内部不再直接持有 `CryptoCodec` 和 `TypeDeserializer`

### Decision 4: `decryptDocument` 就地修改并返回同一 Document

**选择**：`decryptDocument(Document rawDocument, Class<?> entityClass)` 就地修改 `rawDocument`，返回 `rawDocument` 引用。

**理由**：
- 与 `CryptoMappingMongoConverter.decryptFields()` 的行为一致（就地修改）
- 避免大文档的深拷贝开销
- 返回同一引用支持链式调用 `fieldCryptoService.decryptDocument(doc, User.class).get("phone")`

### Decision 5: 参数校验策略

- `rawDocument == null` → 抛出 `IllegalArgumentException`
- `entityClass == null` → 抛出 `IllegalArgumentException`
- `entityClass` 没有 `@Encrypted` 字段 → 直接返回 `rawDocument`（no-op）
- 字段值不是含 `_e: 1` 的子文档 → 跳过（保证幂等性和兼容性）
- 子文档缺少 `_k` → 抛出 `FatalCryptoException`（与当前 Converter 行为一致）

## Risks / Trade-offs

**[Risk] Converter 重构破坏现有行为** → 现有 `CryptoBeforeConvertListenerTest`、`CryptoMappingMongoConverterTest` 覆盖了解密场景，重构后必须全量通过。Converter 只替换 `decryptFields()` 实现，`read()` / `project()` 编排逻辑不变。

**[Risk] FieldCryptoService 被注入到不恰当的生命周期** → 作为普通 `@Bean` 注册，无状态，线程安全（依赖的组件均为线程安全），可被任意作用域注入。

**[Trade-off] 仅提供文档级别 API，不提供字段级别 API** → 降低 API 表面积，简化首版实现。如果用户后续有单字段解密需求，可在 `FieldCryptoService` 上扩展 `decryptField()` 方法，无需破坏现有 API。
