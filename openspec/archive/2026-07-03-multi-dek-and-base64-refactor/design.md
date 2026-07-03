## Context

当前 LightCrypto-Link v1 核心框架已完成基础加解密能力（`lcl-v1-core-framework` change），核心组件包括：
- `KeyVaultDocument` — 单文档结构，存储一组 `dek` + `hmk`（wrapped）
- `KeyVaultService` — 单 key 缓存，`getDek()` / `getHmacKey()` 返回全局唯一密钥
- `CryptoCodec` — AES-256-GCM 加解密 + HMAC-SHA-256 盲索引（hex 输出）
- `TypeSerializer` — 确定性序列化，byte[] → hex string
- `CryptoBeforeSaveListener` / `CryptoBeforeConvertListener` — 透明加解密
- 加密子文档结构：`{c: Binary, b: String(hex), _e: 1, _t: String}`

本次重构需在此基础上实现多 DEK 隔离 + base64url 编码改进。

## Goals / Non-Goals

**Goals:**
- 每个实体类拥有独立的 DEK/HMAC key pair，实现密钥域隔离
- vault 文档支持 key 版本列表（`keys[]`），为 DEK Rotation 提供数据结构基础
- 加密子文档增加 `_k`（kid）字段，支持解密时按版本定位密钥
- `KeyVaultService` 提供 `rotateKey(entityClass)` 方法完成密钥轮转（旧 key → rotated，新 key → active）
- byte[] 类型序列化直接操作 raw bytes，不做字符串编码
- HMAC 计算直接接受 byte[] 输入，消除不必要的 String 中转
- 盲索引输出从 hex 改为 base64url 无填充编码

**Non-Goals:**
- 不实现后台批量重加密（rotation 后的旧数据迁移）— 留待后续迭代
- 不实现按字段粒度独立 DEK — 当前按实体类粒度已满足需求
- 不修改 CmkProvider SPI — CMK 层保持不变
- 不实现 v2 云 KMS 集成

## Decisions

### D1: Vault 文档 ID 命名规则 — `lcl-dek-{entitySimpleName}`

**选择**: vault 文档 `_id` = `lcl-dek-{entitySimpleName}`，如 `lcl-dek-User`、`lcl-dek-Order`

**备选方案**:
- A) `lcl-dek-{fullyQualifiedClassName}` — 唯一性强但 _id 过长
- B) 自动 hash 类名 — 可读性差，排查困难

**理由**: SimpleName 在典型应用中足够唯一，_id 短且可读。如果出现同名类冲突（极端罕见），启动时 DuplicateKeyException 会直接暴露。

### D2: KeyVersionEntry 结构 — 每个 entry 包含 dek + hmk + kcv + binding + status + kid

**选择**: vault 文档内部用 `keys[]` 数组，每个 entry 自包含完整的 key pair 和校验信息。

```json
{
  "_id": "lcl-dek-User",
  "activeKid": "v1-a3b2c1",
  "keys": [
    {
      "kid": "v1-a3b2c1",
      "status": "ACTIVE",
      "dek": { "wrapped": Binary, "algorithm": "AES-256-WRAP", "kcv": "..." },
      "hmk": { "wrapped": Binary, "algorithm": "AES-256-WRAP", "kcv": "..." },
      "binding": "...",
      "createdAt": ISODate("...")
    }
  ],
  "cmk": { "provider": "local", "id": "default" },
  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}
```

**理由**: 自包含结构使解密时只需根据 `_k`（kid）找到对应 entry 即可，无需跨文档查找。binding 校验也在 entry 内部完成。

### D3: kid 生成策略 — `v{n}-{randomSuffix}`

**选择**: `kid` = `v{version}-{8位hex随机后缀}`，如 `v1-a3b2c1d4`

**备选方案**:
- A) UUID — 太长（36字符）
- B) 纯递增版本号 — 并发场景可能冲突

**理由**: 短小（~12字符）、可排序（v1 < v2）、随机后缀防冲突。

### D4: KeyVaultService 缓存结构 — `ConcurrentHashMap<String/*entityClassName*/, EntityKeyContext>`

**选择**: `EntityKeyContext` 包含 `activeKid` + `Map<String/*kid*/, ResolvedKeyPair>`。`ResolvedKeyPair` 持有已解包的 dek/hmacKey 字节数组。

**理由**: 按实体类查找 active kid → 用 kid 获取密钥 → 两步寻址，既支持写入时快速定位 active key，又支持读取时按 `_k` 回溯历史版本。

### D5: HMAC 输入格式 — `fieldName + ":" + rawBytes`

**选择**: HMAC 输入 = `fieldName.getBytes(UTF_8)` + `0x3A`（冒号分隔符）+ `serializedBytes`，直接拼接为 byte[]

**备选方案**:
- A) 先 base64 编码 bytes 再做 HMAC — 增加计算量且无安全增益
- B) 保持 String 中转 — byte[] 类型仍需编码，无意义

**理由**: 消除 byte[] 类型的 String 中转，所有类型统一以 byte[] 参与 HMAC 计算。冒号分隔符防止 `field="ab", value="cd"` 和 `field="a", value="bcd"` 碰撞。

### D6: 盲索引编码 — base64url 无填充

**选择**: `Base64.getUrlEncoder().withoutPadding().encodeToString(hmacResult)`

**理由**: HMAC-SHA-256 输出 32 字节 → base64url 43 字符 vs hex 64 字符，缩短 33%。base64url 字符集 `[A-Za-z0-9_-]` 对 MongoDB B-Tree 索引友好，无 `+`/`/`/`=` 特殊字符。

### D7: 向后兼容 — 不做自动迁移

**选择**: 本次变更标记为 **BREAKING**，旧 vault 文档和新格式不兼容。

**理由**: v1 尚未正式发布，无需迁移策略。生产环境发布前需清理旧 vault 文档（手动删除 `__lcl_keyvault` 集合让系统重新初始化）。

## Risks / Trade-offs

- **[实体类名冲突]** → SimpleName 作为 vault ID 在全应用范围内可能冲突。缓解：启动时 DuplicateKeyException 会直接报错，用户需重命名实体类。
- **[Rotation 后旧数据不可读]** → rotateKey 后旧加密字段仍用旧 kid，但旧 key 标记为 rotated 不影响解密。缓解：rotated key 保留在 vault 中，解密时仍可查到。
- **[内存开销]** → 多实体类 = 多组密钥缓存在内存。缓解：典型应用 <50 个加密实体类，每组 64 字节（DEK+HMAC），总开销 < 4KB，可忽略。
- **[BREAKING 变更]** → 加密子文档和 vault 结构均变化，旧数据不兼容。缓解：v1 未正式发布，文档中明确标注。
