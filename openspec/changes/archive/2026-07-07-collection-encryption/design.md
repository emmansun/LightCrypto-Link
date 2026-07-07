## Context

此变更依赖 `nested-object-encryption` 变更完成后的基础设施：路径式 `EncryptedFieldMetadata`、递归扫描引擎、BSON 路径导航。

bol.com 的 spring-data-mongodb-encrypt 已验证了集合类型加密的可行性和 Node 树递归遍历模式。LCL 在此基础上使用扁平路径 + PathSegmentType 实现同等能力。

## Goals / Non-Goals

**Goals:**
- `@Encrypted` 标注在标量集合字段（`List<String>`、`Set<Integer>`、`Map<String, String>` 等）时，每个元素/value 独立加密
- `@Encrypted` 标注在 POJO 集合字段（`List<Address>`、`Map<String, Address>`）时，整个集合 BSON 序列化后整体加密为单个 Binary
- 无 `@Encrypted` 的 POJO 集合字段遍历时递归扫描元素内部 `@Encrypted` 字段
- 统一的 PathSegmentType 路径模型表达所有导航语义
- Blind index 支持集合元素的精确匹配查询

**Non-Goals:**
- 原始类型数组（`int[]`、`String[]`）——Java 集合已覆盖此需求
- 嵌套集合（`List<List<String>>`）——实际场景极少，可作为后续扩展

## Decisions

### Decision 1: PathSegmentType 枚举

```java
enum PathSegmentType {
    FIELD,      // 普通字段访问
    LIST_ITER,  // 遍历 List/Set/Collection 的元素
    MAP_ITER    // 遍历 Map 的 values
}
```

`EncryptedFieldMetadata` 新增 `List<PathSegmentType> pathTypes`，与 `path` 一一对应。

**示例路径表达**：

| 场景 | path | pathTypes |
|------|------|-----------|
| `@Encrypted String phone` | `["phone"]` | `[FIELD]` |
| `@Encrypted String address.street` | `["address", "street"]` | `[FIELD, FIELD]` |
| `@Encrypted List<String> tags` | `["tags"]` | `[LIST_ITER]` |
| `List<Address>.street` (内嵌@Encrypted) | `["addresses", "street"]` | `[LIST_ITER, FIELD]` |
| `@Encrypted Map<String,String> data` | `["data"]` | `[MAP_ITER]` |
| `Map<String,Address>.street` | `["settings", "street"]` | `[MAP_ITER, FIELD]` |

### Decision 2: 泛型类型解析

扫描 Collection/Map 字段时，通过 `Field.getGenericType()` 获取 `ParameterizedType`，提取：
- Collection → `getActualTypeArguments()[0]` 为元素类型
- Map → `getActualTypeArguments()[1]` 为 value 类型

**原始类型（raw type）处理**：如果泛型信息缺失（如 `List` 无参数化），抛出 `UnsupportedTypeException`，要求用户声明具体泛型。

### Decision 3: BSON 存储格式

**Collection（List/Set）**：存储为 BSON Array，每个元素是加密子文档

```json
{
  "tags": [
    { "c": Binary(...), "_e": 1, "_t": "STR", "_k": "kid", "_a": "AES_256_GCM" },
    { "c": Binary(...), "_e": 1, "_t": "STR", "_k": "kid", "_a": "AES_256_GCM" }
  ]
}
```

**Map**：存储为 BSON Document，每个 value 是加密子文档（key 保持明文）

```json
{
  "settings": {
    "theme": { "c": Binary(...), "_e": 1, "_t": "STR", "_k": "kid", "_a": "AES_256_GCM" },
    "lang":  { "c": Binary(...), "_e": 1, "_t": "STR", "_k": "kid", "_a": "AES_256_GCM" }
  }
}
```

### Decision 4: 统一递归遍历算法

Save/Read 使用同一个递归函数，按 PathSegmentType 分发：

```
traverse(bsonNode, javaNode, depth):
    type = pathTypes[depth]
    
    if type == FIELD:
        if depth == last: encrypt/decrypt leaf
        else: recurse(bsonNode.get(name), accessor.invoke(javaNode), depth+1)
    
    if type == LIST_ITER:
        list = javaNode (or accessor.invoke)
        bsonArray = bsonNode.get(name)
        for i in 0..list.size():
            if depth == last: encrypt/decrypt list[i] → bsonArray[i]
            else: recurse(bsonArray[i], list[i], depth+1)
    
    if type == MAP_ITER:
        map = javaNode (or accessor.invoke)
        bsonDoc = bsonNode.get(name, Document.class)
        for key in map.keySet():
            if depth == last: encrypt/decrypt map[key] → bsonDoc[key]
            else: recurse(bsonDoc[key], map[key], depth+1)
```

### Decision 5: Blind Index on Collection 元素

每个集合元素独立生成 blind index，存储在对应的加密子文档中：

```json
{
  "tags": [
    { "c": Binary(...), "b": "hmac_java", "_e": 1, ... },
    { "c": Binary(...), "b": "hmac_spring", "_e": 1, ... }
  ]
}
```

查询 `findByTagsContaining("java")` 重写为 `Criteria.where("tags.b").is(hmac("tags", "java"))`。

MongoDB 的数组查询语义会自动检查 `tags` 数组中是否有任何元素的 `b` 字段匹配。

### Decision 6: @Encrypted on POJO Collection 整体加密

**选择**：支持 `@Encrypted List<Address>` 整体加密。

**流程**：
1. 扫描时检测到 `@Encrypted List<Address>` → POJO 元素类型 → 标记 `wholeObject = true`
2. **注解冲突检测**：检查 Address 内部是否有 `@Encrypted` 字段。有 → `IllegalStateException`；无 → 创建整体加密元数据
3. Save：`MappingMongoConverter` 序列化整个 List 为 BSON bytes → 加密 → 存储
4. Read：解密 → 反序列化为 `List<Address>` → 替换字段值

**BSON 存储格式**：

```json
{
  "addresses": {
    "c": Binary(encrypt(bsonBytes)), "_e": 1, "_t": "COL",
    "_k": "kid", "_a": "AES_256_GCM"
  }
}
```

`_t: "COL"` 标识整体加密的集合。Map 类型使用 `_t: "MAP"`。

**整体加密不支持 blindIndex**：整个集合被加密为单一 Binary，无字段可索引。

**三种 POJO 集合使用模式总结**：

| 模式 | 语法 | 行为 |
|------|------|------|
| 整体加密 | `@Encrypted List<Address>` | 整个集合序列化为 Binary，不透明 |
| 元素字段级加密 | `List<Address>` + Address 内有 @Encrypted | 遍历元素，每个元素内部字段独立加密 |
| 冲突 | `@Encrypted List<Address>` + Address 内有 @Encrypted | fail-fast `IllegalStateException` |

## Risks / Trade-offs

- **[风险] 大数组的加密开销** → 元素级加密时，N 个元素产生 N 次加密操作。整体加密则为一次，但密文体积等于整个集合。
- **[风险] 整体加密 POJO 集合不可查询** → `@Encrypted List<Address>` 整体加密后无法按元素内部字段查询，这是设计取舍。用户如需查询应改用元素字段级加密模式。
- **[风险] 原始类型泛型擦除** → `List` 无参数化时无法确定元素类型。通过启动期异常检测规避。
- **[取舍] 不支持嵌套集合** → `List<List<String>>` 等场景排除。实现复杂度不匹配实际使用频率。
- **[风险] Map key 明文泄露** → 元素级加密时 Map 的 key 以明文存储。整体加密时无此问题（key 在加密 blob 内）。
