## Why

当前 v1 核心框架采用全局单 DEK 架构（整个 `__lcl_keyvault` 集合仅一个 vault 文档），所有加密字段共享同一组 DEK/HMAC key。这带来三个问题：
1. **密钥隔离性不足** — 爆破一个 DEK 等于所有实体的所有加密字段全部暴露
2. **无法做 DEK Rotation** — 没有 key version 概念，换密钥需全量重加密且无平滑过渡机制
3. **byte[] 处理低效** — `TypeSerializer` 对 `byte[]` 做 hex 编码（膨胀 100%），盲索引输出也用 hex，不够紧凑

## What Changes

- **按实体类独立 DEK**：每个含 `@Encrypted` 字段的实体类拥有独立的 vault 文档（`_id` = `lcl-dek-{entityClass}`），各自管理独立的 DEK/HMAC key pair
- **Key Versioning**：vault 文档从单 key 结构改为 key 列表结构（`keys[]`），每个 key entry 包含 `kid`（key version ID）、`status`（active/rotated/revoked）
- **加密子文档增加 `_k` 字段**：标识该字段用哪个 kid 加密，解密时按 `_k` 查找对应版本 DEK
- **DEK Rotation 基础能力**：`KeyVaultService` 支持 `rotateKey(entityClass)` 方法，将旧 key 标记为 rotated、生成新 active key
- **BREAKING — byte[] 不再 hex 编码**：`TypeSerializer` 对 `byte[]` 直接返回 raw bytes，HMAC 计算接受 `byte[]` 输入
- **BREAKING — 盲索引输出改用 base64url 无填充**：HMAC 输出从 hex 改为 base64url（短 33%，索引友好）
- **BREAKING — 加密子文档结构变化**：新增 `_k` 字段，`b` 字段值从 hex 改为 base64url

## Capabilities

### New Capabilities
- `multi-dek-vault`: 按实体类独立 vault 管理，key 版本化存储，DEK Rotation 生命周期
- `base64-blind-index`: byte[] 直操作 + base64url 盲索引编码，替代 hex 方案

### Modified Capabilities

（无已有 spec 需要修改，v1 core framework 尚无 openspec spec）

## Impact

- **KeyVaultDocument** — 数据结构从单 `dek`/`hmk` 改为 `keys[]` 列表，新增 `KeyVersionEntry` 内部类
- **KeyVaultService** — 从单 key 缓存改为 `Map<String/*kid*/, KeyPair>` 多 key 缓存，API 签名变化：`getDek()` → `getDek(String kid)`，新增 `getActiveKid(Class<?> entityClass)`、`rotateKey(Class<?> entityClass)`
- **CryptoCodec** — `generateBlindIndex` 签名从 `(byte[], String, String)` 改为 `(byte[], String, byte[])`，输出从 hex 改为 base64url
- **TypeSerializer** — `serializeToString(byte[])` hex 逻辑移除，新增 `serializeToBytes(Object)` 方法直接返回 byte[]
- **TypeDeserializer** — `BYTES` 类型从 `HexFormat.parseHex` 改为 `Base64.getDecoder().decode`
- **CryptoBeforeSaveListener** — 加密时写入 `_k` 字段、调用 `getActiveKid()` 获取当前 kid
- **CryptoBeforeConvertListener** — 解密时从 `_k` 读取 kid 并调用 `getDek(kid)`
- **CryptoMongoQueryCreator** — 盲索引查询值格式从 hex 改为 base64url
- **所有测试** — 需要适配新的 vault 结构和编码格式
