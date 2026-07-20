## Why

当前 LightCrypto-Link 的密码学核心（CryptoCodec、SymmetricEncryptor、BlindIndex）与 Spring Boot / MongoDB 深度耦合在 `lightcrypto-link-spring-boot-starter` 单一模块中，无法被其他 JVM 框架复用，也无法为未来跨语言 SDK（Node.js / Go）提供字节级一致性基准。加密输出缺乏正式的 Wire Format 规范（仅为 Base64(IV‖CT)），没有命名空间绑定、AAD 认证、版本标识，不具备多租户隔离和后量子演进能力。

Phase 1 是 LCL Platform 六阶段演进的基础层（L1），一旦 Wire Format V1 冻结，后续所有 SDK、适配器、治理文档都以此为"字节宪法"。现在没有正式用户和发布版本，是重构的最佳窗口。

## What Changes

- **新增 `lcl-core` Maven 模块**：纯 Java 密码学核心，零 Spring/DB 依赖（仅 JDK 17 + BouncyCastle + lcl-spi），包含 WireFormat 编解码、CryptoCodec、BlindIndexEngine、Namespace 解析、AAD 构建、KCV 计算
- **新增 Wire Format V1 二进制格式**：`[version][algId][nsLen][namespace][dekVersion][ivLen][iv][aadExtLen][aadExt][ciphertext]`，存储为 Base64URL(no-padding) 字符串
- **新增多租户 Namespace 模型**：`<tenant>.<realm>.<entity>#<field>` 四段结构，从 Day 1 内建于 Wire Format 和 BlindIndex 中
- **新增 HKDF tenant-scoped BlindIndex**：使用 HKDF-SHA256 从 master HMAC key + namespace 派生 per-field HMAC key，替代当前直接使用全局 HMAC key 的方式
- **新增 Vector Suite 基础设施**：仓库根目录 `vectors/`（非 Maven 模块），JSON 格式黄金测试向量，按算法拆分文件，为跨语言验证提供唯一正确性基准
- **重构 `lightcrypto-link-spi` 为 `lcl-spi`**：保持 CmkProvider 接口，新增 VaultStore / StorageAdapter / QueryTransformer 接口预留
- **重构 `lightcrypto-link-spring-boot-starter`**：依赖 lcl-core，KeyVaultService / CryptoBeforeSaveListener / CryptoMongoQueryCreator 适配新 Wire Format 和 Namespace 模型
- **BREAKING**：加密输出格式从 Base64(IV‖CT) 变为 Base64URL(WireFormatV1 blob)，不兼容旧数据（无用户，无需迁移）

## Capabilities

### New Capabilities
- `wire-format-v1`: Wire Format V1 二进制编解码规范 — 字节布局、算法 ID 注册、AAD 隐式绑定规则、Base64URL 存储编码
- `namespace-model`: 多租户命名空间模型 — 四段结构解析、字符合法性校验、默认值补全、长度限制
- `hkdf-blind-index`: HKDF tenant-scoped 盲索引引擎 — 从 master key + namespace 派生 per-field HMAC key，确定性索引计算
- `vector-suite`: 黄金测试向量基础设施 — JSON schema、文件组织、manifest 完整性、跨语言消费模式
- `lcl-core-module`: 独立密码学核心模块 — 模块边界、依赖规则、API 设计、与 starter 的集成方式

### Modified Capabilities
- `envelope-encryption`: 加密输出从 raw IV‖CT 变为 Wire Format V1 blob（含 namespace/dekVersion/AAD 绑定）
- `base64-blind-index`: HMAC key 派生方式从直接使用全局 key 变为 HKDF(namespace) 派生
- `multi-dek-vault`: Vault 文档增加 namespace 字段，DEK 按 namespace 隔离
- `cmk-provider-spi`: SPI 模块重命名为 lcl-spi，预留 VaultStore/StorageAdapter 接口

## Impact

- **模块结构**：新增 `lcl-core` 模块；`lightcrypto-link-spi` 演进为 `lcl-spi`（artifactId 变更）
- **依赖关系**：starter 新增对 lcl-core 的依赖；lcl-core 依赖 lcl-spi + BouncyCastle
- **加密格式**：所有加密字段输出格式变更（Base64URL Wire Format V1），BlindIndex 输出变更（HKDF 派生）
- **API**：CryptoCodec 公开 API 变更（新增 namespace/dekVersion 参数）；KeyVaultService 内部重构
- **测试**：所有现有测试需适配新格式；新增 Vector Suite 验证测试
- **构建**：根 pom.xml 新增 lcl-core 模块；CI 需验证 lcl-core 零 Spring 依赖
