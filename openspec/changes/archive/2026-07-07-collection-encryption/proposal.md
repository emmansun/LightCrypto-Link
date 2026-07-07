## Why

LCL 当前不支持 Collection 和 Map 类型字段的加密。现实场景中 `tags`、`phones`、`emails` 等列表字段和 `settings`、`attributes` 等 Map 字段极为常见。此变更在 `nested-object-encryption` 变更基础上，为 LCL 补全集合类型的透明加密支持。

## What Changes

- **标量集合加密**：`@Encrypted List<String>` / `Set<Integer>` / `Map<String, String>` 等，每个元素/value 独立加密，存储为 BSON Array（Collection）或嵌套 Document（Map）中的加密子文档数组
- **POJO 集合递归**：`List<Address>` / `Map<String, Address>`（无 @Encrypted 标注在容器上），遍历每个元素递归扫描内部 `@Encrypted` 字段
- **路径段类型**：`EncryptedFieldMetadata` 新增 `PathSegmentType` 枚举（FIELD / LIST_ITER / MAP_ITER），表达路径中每段的导航语义
- **泛型类型解析**：扫描时通过 `ParameterizedType` 提取集合元素类型或 Map value 类型
- **统一遍历算法**：Save/Read 路径使用统一的递归算法处理混合 FIELD/LIST_ITER/MAP_ITER 路径段
- **Blind Index on 集合**：集合元素级 blind index 天然兼容 MongoDB 数组查询语义（`tags.b = hash` 自动匹配数组中任何元素）
- **POJO 集合整体加密**：`@Encrypted List<Address>`（POJO 集合）时，通过 BSON 序列化整个集合后加密为单个 Binary（`_t: "COL"`）；若内部元素字段也有 `@Encrypted`，启动期 fail-fast 抛 `IllegalStateException`

## Capabilities

### New Capabilities
- `collection-element-encryption`: Collection/Map 类型字段的元素级加密、泛型参数解析、PathSegmentType 路径模型、集合遍历加密/解密算法

### Modified Capabilities
- `encrypted-annotation`: EntityMetadataCache 扫描行为扩展，识别 Collection/Map 类型并解析泛型参数
- `transparent-listener`: Save/Read 路径支持 LIST_ITER/MAP_ITER 路径段的遍历加密/解密
- `transparent-query`: 查询重写支持集合元素 blind index 匹配（数组查询语义）
- `manual-decrypt-service`: FieldCryptoService.decryptDocument 支持集合路径导航解密

## Impact

- **依赖**：依赖 `nested-object-encryption` 变更完成（共享 PathSegmentType、递归扫描基础设施）
- **元数据模型**：EncryptedFieldMetadata 新增 pathTypes 字段
- **扫描引擎**：EntityMetadataCache 需解析泛型参数、识别 Collection/Map 类型
- **写入/读取链路**：从简单路径导航升级为递归遍历算法
- **查询链路**：盲索引匹配扩展至集合元素
- **向后兼容**：所有现有标量字段为 `pathTypes = [FIELD]`，完全兼容
