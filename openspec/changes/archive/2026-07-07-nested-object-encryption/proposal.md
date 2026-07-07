## Why

LCL 当前仅支持顶层标量字段的 `@Encrypted` 加密。当实体包含嵌套 POJO（如 `User` 内嵌 `Address`），嵌套对象内部的 `@Encrypted` 字段被**静默忽略**——不报错、不加密、不解密。这是架构级的能力缺口，也是 MongoDB 嵌入文档建模模式下最核心的加密场景。

## What Changes

- **递归扫描**：`EntityMetadataCache` 在扫描实体时，遇到嵌套 POJO 字段（无 `@Encrypted`、非标量、非集合/Map、非 `@DBRef`）自动递归进入其内部字段，发现并注册 `@Encrypted` 注解
- **路径式元数据**：`EncryptedFieldMetadata` 从单一 `fieldName`/`getter` 扩展为 `path`（字段路径链）/`accessors`（MethodHandle getter 链），支持任意深度的嵌套访问
- **嵌套加密写入**：`CryptoBeforeSaveListener` 通过 accessor 链获取嵌套 Java 值，通过 BSON 路径导航将加密子文档写入嵌套位置
- **嵌套解密读取**：`FieldCryptoService` / `CryptoMappingMongoConverter` 通过 BSON 路径导航到嵌套位置进行解密
- **嵌套查询重写**：`CryptoMongoQueryCreator` 支持 Spring Data 的点分路径格式（如 `address.zipCode`）匹配嵌套加密字段的 blind index
- **安全保护**：最大递归深度限制 + 循环引用检测
- **整体加密**：对整个嵌套 POJO 字段打 `@Encrypted` 时，通过 BSON 序列化整个子文档后加密为 Binary（`_t: "DOC"`）；若内部字段也有 `@Encrypted`，启动期 fail-fast 抛 `IllegalStateException`

## Capabilities

### New Capabilities
- `nested-object-scanning`: 嵌套 POJO 字段的递归扫描、POJO 判定逻辑、字段路径元数据模型、循环检测与深度限制

### Modified Capabilities
- `encrypted-annotation`: EntityMetadataCache 扫描行为从"仅顶层字段"扩展为"递归扫描嵌套 POJO 内部字段"
- `transparent-listener`: Save 路径支持 accessor 链式取值 + BSON 嵌套路径写入；Read 路径支持 BSON 嵌套路径导航解密
- `transparent-query`: 查询重写支持点分路径匹配嵌套加密字段的 blind index
- `manual-decrypt-service`: FieldCryptoService.decryptDocument 支持嵌套路径导航解密

## Impact

- **核心模型**：`EncryptedFieldMetadata` record 结构变更（所有消费者需适配）
- **扫描引擎**：`EntityMetadataCache.scanFields()` 从扁平遍历改为递归扫描
- **写入链路**：`CryptoBeforeSaveListener.onBeforeSave()` 增加嵌套 BSON 导航
- **读取链路**：`FieldCryptoService.decryptField()` 增加嵌套 BSON 导航
- **查询链路**：`CryptoMongoQueryCreator` 路径匹配逻辑适配
- **向后兼容**：顶层字段为 `path.size() == 1` 的特例，逻辑完全兼容
- **测试**：需新增嵌套对象测试模型和全链路集成测试
