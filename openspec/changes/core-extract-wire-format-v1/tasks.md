## Tasks

### Task 1: Maven 模块结构重组

**Spec:** lcl-core-module, cmk-provider-spi

- [x] 1.1 重命名 `lightcrypto-link-spi` → `lcl-spi`（artifactId 变更，保持 package 不变）
- [x] 1.2 重命名 `lightcrypto-link-alibaba-kms` → `lcl-provider-alibaba-kms`
- [x] 1.3 重命名 `lightcrypto-link-azure-kms` → `lcl-provider-azure-kms`
- [x] 1.4 重命名 `lightcrypto-link-spring-boot-starter` → `lcl-spring-boot-starter`
- [x] 1.5 创建 `lcl-core` Maven 模块（pom.xml，依赖 lcl-spi + bcprov-jdk18on）
- [x] 1.6 更新根 pom.xml modules 列表和 dependencyManagement
- [x] 1.7 更新所有 provider/starter 的 pom.xml 依赖引用
- [x] 1.8 验证 `mvn compile -pl lcl-spi,lcl-core` 通过

### Task 2: lcl-spi 预留接口

**Spec:** cmk-provider-spi

- [x] 2.1 创建 `io.github.emmansun.lightcrypto.spi.VaultStore` 接口（含 Phase 2 Javadoc）
- [x] 2.2 创建 `io.github.emmansun.lightcrypto.spi.StorageAdapter` 标记接口
- [x] 2.3 创建 `io.github.emmansun.lightcrypto.spi.QueryTransformer` 标记接口
- [x] 2.4 创建 `io.github.emmansun.lightcrypto.spi.VaultDocument` record（namespace, keys, activeKid）
- [x] 2.5 验证 lcl-spi 编译通过，无第三方依赖

### Task 3: Namespace 模型实现

**Spec:** namespace-model

- [x] 3.1 在 lcl-core 创建 `io.github.emmansun.lightcrypto.core.namespace.Namespace` record
- [x] 3.2 实现 `Namespace.parse(String raw)` — 四段解析、简写补全、2段禁止
- [x] 3.3 实现字符合法性校验 `[a-zA-Z0-9_-]`、长度限制 256B
- [x] 3.4 实现 `Namespace.canonical()` 返回 `tenant.realm.entity#field` 形式
- [x] 3.5 实现 `Namespace.of(tenant, realm, entity, field)` 工厂方法
- [x] 3.6 编写 Namespace 单元测试（合法/非法/边界）

### Task 4: Wire Format V1 编解码

**Spec:** wire-format-v1

- [x] 4.1 创建 `io.github.emmansun.lightcrypto.core.format.AlgorithmId` 枚举（0x01~0x04）
- [x] 4.2 创建 `io.github.emmansun.lightcrypto.core.format.WireFormatEncoder`
- [x] 4.3 创建 `io.github.emmansun.lightcrypto.core.format.WireFormatDecoder`（含 `DecodedBlob` record）
- [x] 4.4 实现 AAD 构建：`version ‖ algorithmId ‖ namespace_bytes ‖ dekVersion_bytes`
- [x] 4.5 实现 Base64URL(no-padding) encode/decode 封装
- [x] 4.6 编写 WireFormat 单元测试（编码/解码/截断拒绝/篡改检测）

### Task 5: SymmetricEncryptor 迁移至 lcl-core

**Spec:** lcl-core-module, envelope-encryption

- [x] 5.1 迁移 `SymmetricEncryptor` 接口至 lcl-core（新增 `byte[] aad` 参数）
- [x] 5.2 迁移 `AesGcmEncryptor`（使用 AAD）
- [x] 5.3 迁移 `AesCbcEncryptor`（忽略 AAD）
- [x] 5.4 迁移 `Sm4GcmEncryptor`（使用 AAD，BouncyCastle）
- [x] 5.5 迁移 `Sm4CbcEncryptor`（忽略 AAD，BouncyCastle）
- [x] 5.6 迁移 `SymmetricAlgorithm` 枚举（关联 AlgorithmId）
- [x] 5.7 编写 Encryptor 单元测试（含 AAD 认证验证）

### Task 6: CryptoCodec 纯函数式重构

**Spec:** lcl-core-module, envelope-encryption

- [x] 6.1 在 lcl-core 创建 `io.github.emmansun.lightcrypto.core.CryptoCodec`
- [x] 6.2 实现 `encrypt(byte[] dek, byte[] plaintext, SymmetricAlgorithm alg, Namespace ns, int dekVersion)` → Base64URL string
- [x] 6.3 实现 `decrypt(byte[] dek, String wireFormatBlob)` → byte[]
- [x] 6.4 内部流程：encrypt = 随机IV → AAD构建 → SymmetricEncryptor.encrypt → WireFormatEncoder.encode → Base64URL
- [x] 6.5 内部流程：decrypt = Base64URL decode → WireFormatDecoder.decode → AAD重建 → SymmetricEncryptor.decrypt
- [x] 6.6 编写 CryptoCodec 单元测试（roundtrip、多算法、错误输入）

### Task 7: HKDF BlindIndexEngine

**Spec:** hkdf-blind-index, base64-blind-index

- [x] 7.1 在 lcl-core 创建 `io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine`
- [x] 7.2 实现 HKDF-SHA256 派生：`IKM=masterKey, Salt=SHA-256(namespace), Info="lcl-blind-index-v1", L=32`
- [x] 7.3 实现 `computeBlindIndex(Namespace ns, String fieldName, byte[] value)` → Base64URL string
- [x] 7.4 实现值规范化：String trim+lowercase；byte[] 跳过
- [x] 7.5 实现 derived key 缓存（ConcurrentHashMap<Namespace, byte[]>）
- [x] 7.6 编写 BlindIndexEngine 单元测试（确定性、租户隔离、规范化）

### Task 8: KCV 工具迁移

**Spec:** lcl-core-module

- [x] 8.1 迁移 KCV 计算逻辑至 `io.github.emmansun.lightcrypto.core.kcv.KeyCheckValue`
- [x] 8.2 实现 `computeKcv(byte[] key)` → 3-byte KCV（AES/SM4 加密全零块取前3字节）
- [x] 8.3 实现 `computeHmacKcv(byte[] hmacKey)` → 3-byte KCV（HMAC-SHA256 固定消息取前3字节）
- [x] 8.4 实现 binding hash 计算
- [x] 8.5 编写 KCV 单元测试

### Task 9: Vector Suite 基础设施

**Spec:** vector-suite

- [x] 9.1 创建 `vectors/` 目录结构（encryption/, blind-index/, kcv/, roundtrip/）
- [x] 9.2 创建 `vectors/manifest.json`（版本、文件列表、SHA-256 哈希）
- [x] 9.3 使用 lcl-core 生成加密黄金向量（每算法 ≥5 条，共 ≥20 条）
- [x] 9.4 生成 blind-index 黄金向量（≥5 条，含多 namespace）
- [x] 9.5 生成 KCV 黄金向量（≥4 条，AES-256/SM4 各 DEK+HMAC）
- [x] 9.6 生成 roundtrip 向量（≥4 条，每算法一条）
- [x] 9.7 在 lcl-core 测试中创建 `VectorSuiteTest` 验证所有向量通过

### Task 10: spring-boot-starter 适配重构

**Spec:** multi-dek-vault, envelope-encryption, base64-blind-index

- [x] 10.1 `lcl-spring-boot-starter` pom.xml 新增 lcl-core 依赖
- [x] 10.2 删除 starter 中已迁移至 lcl-core 的类（SymmetricEncryptor 系列、旧 CryptoCodec）
- [x] 10.3 重构 `KeyVaultService`：`getActiveKid(Class<?>)` → `getActiveKid(String namespace)`
- [x] 10.4 重构 vault document `_id` 生成逻辑：`lcl-dek-{namespace}`
- [x] 10.5 重构 `CryptoBeforeSaveListener`：构建 Namespace → 调用 lcl-core CryptoCodec
- [x] 10.6 重构 `CryptoAfterLoadListener`：调用 lcl-core CryptoCodec.decrypt
- [x] 10.7 重构 `CryptoMongoQueryCreator`：使用 BlindIndexEngine（HKDF 派生）
- [x] 10.8 更新 `@Encrypted` 注解元数据 → Namespace 解析逻辑
- [x] 10.9 更新 `DecryptService`（manual decrypt）适配新格式
- [x] 10.10 更新所有 starter 单元测试和集成测试

### Task 11: Examples 和文档适配

**Spec:** all

- [x] 11.1 更新 `lightcrypto-link-examples` 模块依赖引用
- [x] 11.2 验证 basic-crud example 运行正常
- [x] 11.3 更新 `docs/` 中涉及加密格式的描述
- [x] 11.4 更新根 README.md 模块结构描述

### Task 12: 全量构建验证

**Spec:** all

- [x] 12.1 `mvn clean verify` 全模块通过
- [x] 12.2 验证 lcl-core 无 Spring/DB 依赖（`mvn dependency:tree -pl lcl-core`）
- [x] 12.3 验证 Vector Suite 测试全部通过
- [x] 12.4 验证所有 provider 模块编译通过（仅依赖 lcl-spi）
- [x] 12.5 确认测试覆盖率不低于重构前水平
