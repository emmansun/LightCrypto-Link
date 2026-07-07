## 1. EncryptedFieldMetadata 路径式扩展

- [x] 1.1 重构 `EncryptedFieldMetadata` record：`MethodHandle getter` → `List<MethodHandle> accessors`，`String fieldName` → `List<String> path`，移除旧 `effectiveFieldName` 改为基于 `String.join(".", path)` 的计算属性（或保留为显式字段）
- [x] 1.2 新增 `boolean wholeObject` 字段，标识整体加密模式
- [x] 1.3 新增辅助方法 `bsonFieldName()` 返回 `String.join(".", path)` 用于 blind index salt 和查询匹配

## 2. EntityMetadataCache 递归扫描

- [x] 2.1 实现 POJO 判定方法 `isNestedPojo(Field)`：排除 primitive/wrapper、String、BigDecimal、byte[]、java.time.*、Enum、Collection、Map、Array、@DBRef、@Transient
- [x] 2.2 重构 `scanFields()` 为递归方法 `scanFields(Class<?> clazz, List<MethodHandle> accessorPrefix, List<String> pathPrefix, Set<Class<?>> visited, int depth)`，最大深度 5
- [x] 2.3 实现循环引用检测：在递归扫描前检查 `visited` 集合，发现重复 class 时抛出 `IllegalStateException`
- [x] 2.4 实现深度超限检测：`depth > 5` 时抛出 `IllegalStateException`
- [x] 2.5 `@Encrypted` 标注在 POJO 类型字段上时：检查内部是否有 `@Encrypted` 字段，有则抛 `IllegalStateException`（冲突检测），无则标记 `wholeObject = true` 并创建整体加密元数据
- [x] 2.6 `@Encrypted(blindIndex = true)` + POJO 字段 → 抛 `UnsupportedTypeException`（整体加密不支持 blindIndex）
- [x] 2.7 确保顶层字段向后兼容：`path.size() == 1` 时行为与变更前完全一致

## 3. TypeSerializer 整体加密 BSON 序列化

- [x] 3.1 新增 `DOC` 类型标识（整体加密的嵌套文档）
- [x] 3.2 实现 POJO → `byte[]` 序列化：通过 `MappingMongoConverter.write()` + `BsonBinaryWriter` 将 POJO 转为 BSON bytes
- [x] 3.3 实现 `byte[]` → POJO 反序列化：`byte[]` → `BsonDocument` → `Document` → `MappingMongoConverter.read()` 还原 POJO
- [x] 3.4 注入 `MappingMongoConverter` 依赖到 TypeSerializer

## 4. CryptoBeforeSaveListener 嵌套加密写入

- [x] 4.1 实现 accessor 链式调用获取嵌套 Java 值：遍历 `meta.accessors()` 逐级 `invoke()`，中间层 null 时跳过
- [x] 4.2 实现 BSON 路径导航写入：通过 `path` 前 N-1 段导航到嵌套 Document，将加密子文档写入叶子位置
- [x] 4.3 整体加密模式：POJO 值通过 TypeSerializer 序列化为 BSON bytes 后加密，写入 `_t: "DOC"` 加密子文档
- [x] 4.4 更新 blind index 计算使用 `effectiveFieldName`（完整路径）作为 salt

## 5. FieldCryptoService 嵌套解密读取

- [x] 5.1 实现 BSON 路径导航：`decryptField()` 通过 `path` 前 N-1 段导航到嵌套 Document
- [x] 5.2 在嵌套 Document 上执行现有解密逻辑（_e/_k/_a/_t/c 解析 + 解密 + 反序列化 + 写回）
- [x] 5.3 整体加密解密：`_t: "DOC"` 时解密后通过 TypeSerializer 反序列化为 POJO，通过 setter 写回实体
- [x] 5.4 处理路径不存在情况：中间段缺失或非 Document 时跳过，不报错

## 6. CryptoMongoQueryCreator 嵌套查询重写

- [x] 6.1 修改 `findEncryptedField()` 匹配逻辑：从 `meta.fieldName().equals(key)` 改为 `String.join(".", meta.path()).equals(key)`
- [x] 6.2 更新查询 key 重写格式：`meta.bsonFieldName() + ".b"` 支持嵌套路径（如 `address.zipCode.b`）

## 7. 测试模型与单元测试

- [x] 7.1 新增嵌套测试模型：`TestUserWithAddress`（含 `Address` 嵌套类，内部有 `@Encrypted` 字段）
- [x] 7.2 新增整体加密测试模型：`TestUserWithWholeAddress`（`@Encrypted Address address`，Address 内部无 `@Encrypted`）
- [x] 7.3 新增深层嵌套测试模型：3 层嵌套（User → Address → GeoLocation）
- [x] 7.4 新增循环引用测试模型：A ↔ B 互相引用
- [x] 7.5 更新 `EntityMetadataCacheTest`：验证嵌套字段扫描、路径正确性、MethodHandle accessor 链、wholeObject 标记
- [x] 7.6 新增递归安全测试：深度超限、循环引用检测、@DBRef 跳过、Collection/Map 跳过
- [x] 7.7 新增注解冲突测试：`@Encrypted` on POJO + 内部 `@Encrypted` → `IllegalStateException`
- [x] 7.8 新增 TypeSerializer DOC 序列化/反序列化单元测试

## 8. 集成测试

- [x] 8.1 新增 `CryptoBeforeSaveListener` 嵌套写入测试：验证 BSON 嵌套位置加密子文档格式
- [x] 8.2 新增整体加密写入测试：`@Encrypted Address` → BSON 中 `address` 为 `{c:Binary, _e:1, _t:"DOC", ...}` 格式
- [x] 8.3 新增 `FieldCryptoService` 嵌套解密测试：验证嵌套路径导航和解密正确性
- [x] 8.4 新增整体加密解密测试：`_t: "DOC"` 解密后还原为 POJO，字段值正确
- [x] 8.5 新增 null 中间层测试：address=null 时跳过加密，BSON 中 address 缺失时跳过解密
- [x] 8.6 新增 `CryptoMongoQueryCreator` 嵌套 blind index 查询重写测试
- [x] 8.7 新增端到端集成测试：save → 验证 BSON 嵌套加密 → find → 验证解密还原
- [x] 8.8 新增整体加密端到端测试：save → 验证 `_t:"DOC"` → find → 验证 POJO 还原

## 9. 全量验证

- [x] 9.1 运行 `mvn clean verify` 确保所有现有测试通过（向后兼容性验证）
- [x] 9.2 确认 SpotBugs 无新增告警
