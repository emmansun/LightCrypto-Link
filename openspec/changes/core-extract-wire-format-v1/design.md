## Context

LightCrypto-Link 当前是一个 Spring Boot + MongoDB 专用字段加密库，所有密码学逻辑（CryptoCodec、SymmetricEncryptor、BlindIndex、KCV）与 Spring/Mongo 代码混合在 `lightcrypto-link-spring-boot-starter` 单模块中。加密输出为简单的 Base64(IV‖CT)，无版本标识、无命名空间绑定、无 AAD 认证。

LCL Platform 设计文档（LCL-CORE-001~040）定义了一个跨语言、跨数据库、跨云的应用层字段加密平台。Phase 1 的目标是建立这个平台的密码学基础层：独立核心模块 + 冻结 Wire Format V1 + 多租户 Namespace + Vector Suite。

**约束：**
- 无兼容性负担（未发布正式版，无使用者）
- Java 17+（不考虑 WASM/Embedded）
- 多租户从 Day 1 引入
- 无需迁移路径

## Goals / Non-Goals

**Goals:**
- 创建零 Spring/DB 依赖的 `lcl-core` 模块，可被任何 JVM 项目独立使用
- 冻结 Wire Format V1 字节布局，为跨语言 SDK 提供字节级一致性基准
- 内建多租户 Namespace 模型（HKDF 域分离）
- 建立 Vector Suite 黄金测试向量，作为跨 SDK 正确性唯一 Oracle
- 重构 spring-boot-starter 使用新 core，保持功能完整

**Non-Goals:**
- 不实现 VaultStore SPI 的具体跨 DB 实现（Phase 2）
- 不实现 StorageAdapter / QueryTransformer 抽象（Phase 2）
- 不实现可观测性 / metrics / audit（Phase 3）
- 不实现 Node.js SDK 或 MySQL/PG 适配器（Phase 4）
- 不实现后量子 / 密码敏捷框架（Phase 5）
- 不改变 CmkProvider SPI 的 wrap/unwrap 语义

## Decisions

### D1: 模块拆分策略 — 选项 C（lcl-spi + lcl-core 分离）

**选择：** lcl-spi（纯接口）独立于 lcl-core（实现），KMS provider 仅依赖 spi。

**替代方案：**
- A: lcl-core 吸收 spi → KMS provider 被迫依赖整个 core（不需要 WireFormat）
- B: 保持现有 spi 不变 → 无法预留 VaultStore/StorageAdapter 接口

**理由：** CmkProvider 实现者（阿里云/Azure）只需 wrap/unwrap 接口，不需要知道 WireFormat 细节。Adapter 需要 core 来做加解密。分离后依赖最小化。

### D2: Wire Format 存储编码 — Base64URL (no padding)

**选择：** 加密 blob 以 Base64URL 无填充字符串形式存入数据库。

**替代方案：**
- Raw binary (BinData/BLOB) → 节省 33% 空间，但跨 DB 不一致、调试困难
- 标准 Base64 → 含 `+/=` 字符，URL/JSON 不友好

**理由：** 字符串形式在 MongoDB/MySQL/PG 中统一、可直接放入 JSON 响应、日志可读、调试友好。+33% 体积在字段级加密场景可接受。

### D3: Namespace 始终存在，不允许省略

**选择：** Wire Format 中 namespace 字段必填（nsLen ≥ 1），单租户使用 `default.default.Entity#field`。

**替代方案：**
- 允许 nsLen=0 省略 → 单租户省几字节，但后续引入多租户需格式变更

**理由：** 多租户 from Day 1 决策。省略会导致 V1 格式内出现"有/无 namespace"两种解析路径，增加复杂度和安全风险。

### D4: GCM Tag 追加在 ciphertext 末尾

**选择：** 不单独存储 tag，GCM 密文 = CT‖Tag(16B)，与 JCE `Cipher.doFinal()` 输出一致。

**替代方案：**
- 单独 tag 字段 → 更显式，但增加格式复杂度，且与 JCE 输出不一致需额外拆分

**理由：** Java/Node/Go 的 GCM 实现默认都将 tag 追加在密文末尾，保持一致最自然。

### D5: BlindIndex 使用 HKDF 派生 per-namespace key

**选择：** `derivedKey = HKDF-SHA256(masterHmacKey, salt=SHA-256(namespace), info="lcl-blind-index-v1")`

**替代方案：**
- 直接使用全局 HMAC key + fieldName 前缀 → 当前方案，无租户隔离
- 每个 namespace 独立存储 HMAC key → 密钥管理复杂度高

**理由：** HKDF 从单一 master key 确定性派生无限子密钥，无需额外存储；不同 namespace 的相同明文产生不同 blind index（密码学隔离）；与 LCL-CORE-015 多租户规范一致。

### D6: Vector Suite 放仓库根目录（非 Maven 模块）

**选择：** `vectors/` 目录在仓库根，JSON 文件按算法拆分，含 manifest.json 完整性哈希。

**替代方案：**
- lcl-core/src/test/resources/ → 埋在 Java 模块里，其他 SDK 难以引用
- 独立 Maven 模块 → Node/Go 不用 Maven，增加构建复杂度

**理由：** 向量是平台级宪法产物，语言无关。根目录最易发现，可被 git submodule / npm pack / go:embed 消费。

### D7: lcl-core 中 CryptoCodec 变为纯函数式

**选择：** CryptoCodec 不再持有状态，所有方法接收完整参数（dek, plaintext, algorithm, namespace, dekVersion）返回 Wire Format blob。

**替代方案：**
- 保持有状态（持有 KeyVaultService 引用）→ 无法脱离 Spring 使用

**理由：** 纯函数式设计使 lcl-core 可测试、可复用、无框架绑定。密钥管理由上层（starter/adapter）负责。

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| Wire Format V1 冻结后发现设计缺陷 | 预留 aadExt 扩展字段；version 字节允许未来 V2 并行存在 |
| Base64URL +33% 存储开销 | 字段级加密场景数据量有限；如未来需要可加 raw binary 模式 |
| HKDF 派生增加 BlindIndex 计算开销 | HKDF-SHA256 单次 <1µs，可缓存 derived key |
| 大规模重构引入 regression | Vector Suite 提供确定性验证；现有 296 个测试作为安全网 |
| lcl-core 与 starter 之间 API 边界不清 | 明确规则：core 只做密码学，不做 IO/框架/缓存 |
