## 1. PathSegmentType 枚举与元数据扩展

- [x] 1.1 新增 `PathSegmentType` 枚举（FIELD / LIST_ITER / MAP_ITER）
- [x] 1.2 `EncryptedFieldMetadata` 新增 `List<PathSegmentType> pathTypes` 字段
- [x] 1.3 确保现有标量字段自动填充 `pathTypes = [FIELD]`，向后兼容

## 2. EntityMetadataCache 泛型解析与集合扫描

- [x] 2.1 实现泛型类型解析方法：从 `ParameterizedType` 提取 Collection 元素类型和 Map value 类型
- [x] 2.2 扩展 POJO 判定排除列表：识别 `Collection`、`Map` 类型不直接递归，先解析泛型参数
- [x] 2.3 `@Encrypted` + 标量 Collection/Map → 创建 LIST_ITER/MAP_ITER 元数据，javaType 为元素类型
- [x] 2.4 无 `@Encrypted` + POJO Collection/Map → 遍历概念上每个元素，递归扫描内部 @Encrypted，路径插入 LIST_ITER/MAP_ITER 段
- [x] 2.5 `@Encrypted` + POJO Collection/Map → 支持整体加密（wholeObject）并进行冲突/限制校验
- [x] 2.6 原始类型（raw type）Collection/Map → 抛 `UnsupportedTypeException`

## 3. CryptoBeforeSaveListener 集合遍历加密

- [x] 3.1 实现统一递归遍历算法：按 pathTypes 分发 FIELD/LIST_ITER/MAP_ITER
- [x] 3.2 LIST_ITER：遍历 Java List/Set，对每个元素加密，写入 BSON Array
- [x] 3.3 MAP_ITER：遍历 Java Map values，对每个 value 加密，写入 BSON Document（key 保持明文）
- [x] 3.4 处理空集合、null 集合、null 元素的边界情况
- [x] 3.5 集合元素的 blind index 生成（每个元素独立计算 HMAC）
- [x] 3.6 整体加密写入：`@Encrypted` 的 POJO 集合/对象写入 `_t: "COL"/"MAP"/"DOC"` 结构

## 4. FieldCryptoService / CryptoMappingMongoConverter 集合遍历解密

- [x] 4.1 实现统一递归遍历算法：按 pathTypes 分发
- [x] 4.2 LIST_ITER：遍历 BSON Array，解密每个子文档，重建 Java List/Set
- [x] 4.3 MAP_ITER：遍历 BSON Document，解密每个 value 子文档，重建 Java Map
- [x] 4.4 处理路径不存在、Array 为空、Document 缺失等边界情况
- [x] 4.5 整体加密解密：`_t: "COL" / "MAP" / "DOC"` 解密后恢复结构化值

## 5. CryptoMongoQueryCreator 集合盲索引查询

- [x] 5.1 扩展 `findEncryptedField()` 匹配逻辑支持集合字段路径
- [x] 5.2 集合字段的查询 key 格式：`fieldName.b`（利用 MongoDB 数组查询语义）
- [x] 5.3 支持 `findByXxxContaining` 和 `findByXxxContainingIn` 查询模式

## 6. 测试模型与单元测试

- [x] 6.1 新增标量集合测试模型：`TestArticle`（含 `@Encrypted List<String> tags`、`@Encrypted Map<String,String> settings`）
- [x] 6.2 新增 POJO 集合递归测试模型：`TestUserWithAddresses`（含 `List<Address> addresses`，Address 内有 @Encrypted 字段）
- [x] 6.3 新增整体加密测试模型：`TestUserWithWholeAddresses`（`@Encrypted List<Address> addresses`，Address 内部无 `@Encrypted`）
- [x] 6.4 EntityMetadataCache 扫描测试：验证 LIST_ITER/MAP_ITER pathTypes、泛型解析、wholeObject 标记
- [x] 6.5 异常测试：raw type、冲突注解、`blindIndex=true` on POJO 集合
- [x] 6.6 CryptoBeforeSaveListener 集合加密测试：验证 BSON Array/Document 格式
- [x] 6.7 FieldCryptoService 集合解密测试：验证遍历解密和集合重建
- [x] 6.8 CryptoMongoQueryCreator 集合盲索引查询重写测试

## 7. 集成测试

- [x] 7.1 端到端：collection save → 验证 BSON 集合加密 → find → 验证解密还原 → 查询验证
- [x] 7.2 整体加密写入测试：`@Encrypted List<Address>` → BSON `addresses` 为 `{c:Binary, _e:1, _t:"COL", ...}`
- [x] 7.3 整体加密解密测试：`_t: "COL"` 解密后还原为 List，字段值正确
- [x] 7.4 整体嵌套对象测试：`@Encrypted Address` 写入 `_t:"DOC"` 并可读回
- [x] 7.5 POJO 集合递归测试：`List<Address>.street` 的集成层写入与读取

## 8. 全量验证

- [x] 8.1 运行 `mvn clean verify` 确保所有现有测试通过（向后兼容性验证）
- [x] 8.2 确认 SpotBugs 无新增告警
