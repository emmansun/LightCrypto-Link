## 1. KeyVaultDocument 模型重构

- [x] 1.1 重构 `KeyVaultDocument`：移除单 `dek`/`hmk`/`binding` 字段，新增 `activeKid` 字段和 `List<KeyVersionEntry> keys`
- [x] 1.2 创建 `KeyVersionEntry` 内部类：`kid`、`status`（ACTIVE/ROTATED/REVOKED）、`dek`（WrappedKeyInfo）、`hmk`（WrappedKeyInfo）、`binding`、`createdAt`
- [x] 1.3 保留 `CmkInfo` 和顶层 `v`/`status`/`createdAt`/`updatedAt` 字段

## 2. KeyVaultService 多 DEK 管理重构

- [x] 2.1 新增内部类 `ResolvedKeyPair`：持有 `byte[] dek`、`byte[] hmacKey`
- [x] 2.2 新增内部类 `EntityKeyContext`：持有 `String activeKid`、`Map<String/*kid*/, ResolvedKeyPair> resolvedKeys`
- [x] 2.3 将单 key 缓存改为 `ConcurrentHashMap<String/*entityClassName*/, EntityKeyContext> entityKeyContexts`
- [x] 2.4 重构 `init()` 方法：接受 `Class<?> entityClass` 参数，按实体类加载或初始化 vault 文档
- [x] 2.5 实现 `ensureVaultInitialized(Class<?> entityClass)` 方法：懒初始化检查
- [x] 2.6 实现 `getActiveKid(Class<?> entityClass)` 方法：返回实体类当前 active kid
- [x] 2.7 重构 `getDek()` 为 `getDek(String kid)` 方法：按 kid 查找已解包 DEK
- [x] 2.8 重构 `getHmacKey()` 为 `getHmacKey(String kid)` 方法：按 kid 查找已解包 HMAC key
- [x] 2.9 重构 `initializeVault()` 方法：接受 entityClass，生成 vault ID = `lcl-dek-{entitySimpleName}`，生成 kid = `v1-{8hex随机}`
- [x] 2.10 重构 `verifyAndLoadKeys()` 方法：遍历 `keys[]`，对每个 entry 做 KCV + binding 校验，填充 EntityKeyContext
- [x] 2.11 实现 `rotateKey(Class<?> entityClass)` 方法：标记旧 active 为 ROTATED，生成新 key entry（递增版本号），更新 activeKid，持久化到 MongoDB
- [x] 2.12 生成 kid 的工具方法 `generateKid(int version)`：`v{version}-{8位hex随机后缀}`

## 3. CryptoCodec base64url 改造

- [x] 3.1 修改 `generateBlindIndex` 签名：`(byte[] hmacKey, String fieldName, byte[] serializedValue)` — 第三个参数从 String 改为 byte[]
- [x] 3.2 重构 HMAC 输入构建：拼接 `fieldName.getBytes(UTF_8)` + `0x3A`（冒号）+ `serializedValue`，直接操作 byte[]
- [x] 3.3 修改 HMAC 输出编码：从 `HexFormat.formatHex()` 改为 `Base64.getUrlEncoder().withoutPadding().encodeToString()`

## 4. TypeSerializer / TypeDeserializer byte[] 改造

- [x] 4.1 修改 `TypeSerializer.serialize(Object)`：当 value 为 byte[] 时直接返回 raw bytes，不经过 String 中转
- [x] 4.2 修改 `TypeSerializer.serializeToString(byte[])`：改用 `Base64.getEncoder().encodeToString()`（用于 BYTES 类型的存储字符串）
- [x] 4.3 修改 `TypeDeserializer.deserialize("BYTES", String)`：从 `HexFormat.parseHex()` 改为 `Base64.getDecoder().decode()`
- [x] 4.4 修改 `TypeDeserializer.deserialize("BYTES", byte[])`：适配新逻辑（先转 UTF-8 string 再 base64 decode）

## 5. CryptoBeforeSaveListener 适配

- [x] 5.1 加密前调用 `keyVaultService.ensureVaultInitialized(entityClass)`
- [x] 5.2 获取 active kid：`String activeKid = keyVaultService.getActiveKid(entityClass)`
- [x] 5.3 加密时使用实体类对应的 DEK：`keyVaultService.getDek(activeKid)`
- [x] 5.4 盲索引使用实体类对应的 HMAC key：`keyVaultService.getHmacKey(activeKid)`
- [x] 5.5 序列化改为 byte[]：`byte[] serialized = typeSerializer.serialize(value)`，直接传给 CryptoCodec
- [x] 5.6 HMAC 计算传入 byte[]：`cryptoCodec.generateBlindIndex(hmacKey, effectiveFieldName, serialized)`
- [x] 5.7 加密子文档写入 `_k` 字段：`subDoc.put("_k", activeKid)`

## 6. CryptoBeforeConvertListener 适配

- [x] 6.1 解密时从子文档读取 kid：`String kid = subDoc.getString("_k")`
- [x] 6.2 按 kid 查找 DEK：`keyVaultService.getDek(kid)`
- [x] 6.3 如果 kid 为空（旧格式），抛 `FatalCryptoException` 提示不兼容
- [x] 6.4 解密调用保持：`cryptoCodec.decrypt(dek, cipherBinary.getData())`

## 7. CryptoMongoQueryCreator 适配

- [x] 7.1 查询改写时获取实体类对应的 HMAC key（通过 activeKid）
- [x] 7.2 序列化值改为 byte[]：`byte[] serialized = typeSerializer.serialize(value)`
- [x] 7.3 HMAC 计算传入 byte[]：`cryptoCodec.generateBlindIndex(hmacKey, effectiveFieldName, serialized)`
- [x] 7.4 盲索引输出自动为 base64url 格式（由 CryptoCodec 保证）

## 8. LightCryptoLinkAutoConfiguration 适配

- [x] 8.1 移除 `init()` 时的全局 vault 初始化（改为懒初始化模式）
- [x] 8.2 确保 KeyVaultService Bean 注入正确（新增依赖参数如有）

## 9. 单元测试

- [x] 9.1 更新 `TestKeyVaultService`：支持多 kid、`getActiveKid(Class<?>)`、`getDek(String kid)`、`getHmacKey(String kid)`
- [x] 9.2 更新 `CryptoCodecTest`：验证 `generateBlindIndex` byte[] 输入和 base64url 输出
- [x] 9.3 更新 `TypeSerializerTest`：验证 byte[] → raw bytes，String/Integer 等不变
- [x] 9.4 更新 `CryptoBeforeSaveListenerTest`：验证 `_k` 字段写入、entity-class routing
- [x] 9.5 更新 `CryptoBeforeConvertListenerTest`：验证 `_k` 字段读取、kid-based 解密
- [x] 9.6 更新 `CryptoMongoQueryCreatorTest`：验证 base64url 盲索引输出
- [x] 9.7 新增 `KeyVaultServiceTest`：测试多实体类 vault 初始化、kid 生成、rotateKey
- [x] 9.8 新增 `HmacCollisionTest`：验证冒号分隔符防碰撞（`ab:cd` vs `a:bcd`）

## 10. 编译验证与回归

- [x] 10.1 执行 `mvn compile` 确保所有源文件编译通过
- [x] 10.2 执行 `mvn test` 确保所有单元测试通过
- [x] 10.3 检查并修复因 API 签名变化导致的编译错误
