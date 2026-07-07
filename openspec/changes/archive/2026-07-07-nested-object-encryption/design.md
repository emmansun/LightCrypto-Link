## Context

LCL 的 `EntityMetadataCache` 当前仅扫描顶层实体类的直接字段。`EncryptedFieldMetadata` 使用单一 `MethodHandle getter` + `String fieldName` 表达扁平字段。整个 save/read/query 链路都假设加密字段位于 BSON Document 的顶层。

MongoDB 嵌入文档建模是主流模式（一个 `User` 文档内嵌 `Address` 子文档），嵌套对象内的 `@Encrypted` 字段被静默忽略是严重的功能缺口。

## Goals / Non-Goals

**Goals:**
- 递归发现嵌套 POJO 内部的 `@Encrypted` 字段并注册路径式元数据
- 对整个嵌套 POJO 字段打 `@Encrypted` 时，BSON 序列化整个子文档后整体加密为 Binary
- Save 路径通过 accessor 链获取嵌套值、BSON 路径导航写入加密子文档
- Read 路径通过 BSON 路径导航解密嵌套加密字段
- 查询重写支持 Spring Data 点分路径格式的 blind index 匹配
- 完全向后兼容（顶层字段 = path.size() == 1 的特例）

**Non-Goals:**
- `List<T>` / `Map<String, T>` 内元素级加密（后续独立变更）
- `@DBRef` 引用对象的递归处理（独立生命周期，不需要递归）

## Decisions

### Decision 1: EncryptedFieldMetadata 路径式扩展

**选择**：将 record 中的单一字段扩展为路径链

```java
// Before
record EncryptedFieldMetadata(
    MethodHandle getter, String fieldName, ...)

// After
record EncryptedFieldMetadata(
    List<MethodHandle> accessors,  // getter chain
    List<String> path,             // BSON path segments
    Class<?> javaType,
    SymmetricAlgorithm algorithm,
    boolean blindIndex,
    String effectiveFieldName      // full path joined, for blind index salt
)
```

**备选方案**：sealed interface 分裂为 `FlatFieldMetadata` + `NestedFieldMetadata`。
**否决原因**：增加类型复杂度，顶层字段只是 `path.size() == 1` 的特例，不需要类型分裂。

**effectveFieldName 策略**：使用完整路径（如 `"address.zipCode"`）作为 blind index HMAC salt。避免不同嵌套对象中同名字段（如 `address.city` vs `billing.city`）的 blind index 碰撞。

### Decision 2: POJO 判定 — 排除法

**选择**：通过排除已知类型来判定"是否为需要递归的 POJO"

排除列表（不递归）：
- primitive / wrapper 类型
- String、BigDecimal、byte[]
- java.time.* 类型
- Enum
- Collection / Map / Array
- 带 `@DBRef` 注解的字段
- 带 `@Transient` 注解的字段（Spring Data 不持久化）

不在排除列表中的非 null class → 视为 POJO → 递归进入。

**备选方案**：白名单（要求用户显式标注 `@Embedded` 等注解）。
**否决原因**：增加用户负担，Spring Data MongoDB 已自动处理嵌入映射，LCL 应保持一致的零注解体验。

### Decision 3: 递归安全 — 深度限制 + 访问集

**选择**：
- 最大递归深度：5 层（覆盖绝大多数实际场景）
- 同一扫描链中维护 `Set<Class<?>>` 检测循环引用
- 超限时抛出 `IllegalStateException`（启动期暴露，而非运行时）

### Decision 4: Save 路径 accessor 链式调用

```
// Java 值获取
Object current = entity;
for (MethodHandle accessor : meta.accessors()) {
    if (current == null) break;     // 中间层为 null → 跳过
    current = accessor.invoke(current);
}
// current = 叶子字段值

// BSON 导航写入
Document target = rootDoc;
for (int i = 0; i < path.size() - 1; i++) {
    Object nested = target.get(path.get(i));
    if (!(nested instanceof Document d)) return; // 中间层不存在 → 跳过
    target = d;
}
target.put(lastSegment, encryptedSubDoc);
```

**中间层 null 处理**：如果 `entity.address == null`，accessor 链返回 null，跳过加密。BSON 中 `address` 可能已经存在（包含非加密字段），不影响正确性。

### Decision 5: Read 路径 BSON 导航解密

与 Save 路径对称：通过 `path` 在 BSON Document 中逐层导航到父 Document，然后对叶子字段执行现有的解密逻辑。

```
Document target = rootDoc;
for (int i = 0; i < path.size() - 1; i++) {
    Object nested = target.get(path.get(i));
    if (!(nested instanceof Document d)) return; // 路径不存在 → 跳过
    target = d;
}
// 对 target.get(lastSegment) 执行解密
```

### Decision 6: 查询重写路径匹配

`CryptoMongoQueryCreator.findEncryptedField()` 当前按 `fieldName` 精确匹配。嵌套后 Spring Data 会将 `findByAddressZipCode` 解析为 BSON key `"address.zipCode"`。

**选择**：`findEncryptedField()` 改为按 `String.join(".", meta.path())` 匹配查询 key。对于顶层字段，`path` 只有一个元素，`join` 结果与 `fieldName` 一致，完全向后兼容。

重写后的 BSON key 格式：`"address.zipCode.b"`（嵌套路径 + `.b` 后缀）。

## Risks / Trade-offs

- **[风险] 深层嵌套的性能开销** → accessor 链式调用比单一 MethodHandle 多出 N-1 次方法派发。但 N 通常 ≤ 3，且 MethodHandle 可被 JIT 内联，实际开销可忽略。
- **[风险] 中间层 POJO 字段被重命名** → `path` 基于 Java 字段名，如果嵌套类重构字段名，需同步更新数据库中的 BSON 结构。这与当前顶层字段的行为一致（无新增风险）。
- **[取舍] 不支持 Collection/Map 内元素加密** → 简化了实现复杂度，避免 BSON Array 遍历和元素级路径表达。作为后续独立变更处理。
- **[风险] 非 POJO 的 class 字段被误判为 POJO** → 排除法可能将未知 class 误判。通过深度限制 + 循环检测兜底，且在扫描时如果嵌套字段有 `@Encrypted` 但其类型不在 `isSupported()` 中，会抛出 `UnsupportedTypeException`，与现有行为一致。

### Decision 7: @Encrypted on POJO 整体加密

**选择**：支持对整个 POJO 字段打 `@Encrypted` 进行整体序列化加密。

**流程**：
1. 扫描时检测到 `@Encrypted Address address` → 标记为 `wholeObject = true` 的元数据
2. **注解冲突检测**：扫描时检查 POJO 内部字段是否也有 `@Encrypted`。如果有 → 抛 `IllegalStateException`，要求用户明确选择一种模式（整体加密 vs 字段级加密）
3. Save：`MappingMongoConverter.write(address)` → Document → 序列化为 `byte[]` → 加密 → 存储
4. Read：解密 → `byte[]` → 反序列化为 Document → `MappingMongoConverter.read(Address.class, doc)` → POJO

**BSON 存储格式**：

```json
{
  "address": {
    "c": Binary(encrypt(bsonBytes)), "_e": 1, "_t": "DOC",
    "_k": "kid", "_a": "AES_256_GCM"
  }
}
```

`_t: "DOC"` 标识整体加密的嵌套文档。TypeSerializer 新增 `DOC` 类型，序列化/反序列化使用 `MappingMongoConverter`。

**BSON 序列化实现**：复用 Spring Data 的 `MappingMongoConverter`：
- 序列化：`converter.write(address, document)` → 转 `BsonBinaryWriter` → `byte[]`
- 反序列化：`byte[]` → `BsonDocument` → `Document` → `converter.read(clazz, doc, target)`

**整体加密不支持 blindIndex**：`@Encrypted(blindIndex = true)` 打在 POJO 上无意义（整个子文档被加密为单一 Binary，无字段可索引），扫描时抛 `UnsupportedTypeException`。

### Decision 8: 注解冲突检测（fail-fast）

当 `@Encrypted` 打在 POJO 字段上（整体加密），但该 POJO 内部字段也有 `@Encrypted`（字段级加密），存在语义冲突。

**选择**：启动期扫描时 fail-fast，抛 `IllegalStateException`。

```java
if (field.isAnnotationPresent(Encrypted.class) && isPojo(field)) {
    // 检查内部是否有 @Encrypted 字段
    if (hasNestedEncryptedFields(field.getType())) {
        throw new IllegalStateException(
            "Field '" + fieldName + "' has @Encrypted for whole-object encryption, " +
            "but its type " + type + " also has @Encrypted fields. " +
            "Use either whole-object encryption OR field-level encryption, not both."
        );
    }
}
```

**备选方案**：静默忽略内部 @Encrypted。
**否决原因**：用户意图不明确，静默行为容易导致数据未按预期加密。fail-fast 更安全。

