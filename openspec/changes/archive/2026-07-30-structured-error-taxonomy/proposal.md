## Why

当前 Java SDK 的异常体系存在三个结构性问题：(1) 解密路径所有失败统一抛 `DecryptionException`，调用方无法区分"密文损坏"与"密钥错误"，运维无法精确定位根因；(2) `lcl-core` 的 `WireFormatDecoder` 和 Encryptor 类抛裸 `IllegalArgumentException` / `CryptoException`，缺乏语义；(3) `DecryptionException`、`EncryptionException` 定义在 starter 中，但 lcl-core 的加密引擎无法引用，模块归属与职责不匹配。Node.js SDK 已完成 structured-error-taxonomy 变更，Java 侧需对齐以保持跨语言错误语义一致性。

## What Changes

- 将 `DecryptionException`、`EncryptionException` 从 `lcl-spring-boot-starter` 下沉到 `lcl-core`，使加密引擎层可抛出精确子类型
- 新增 5 个精确异常子类：`PayloadCorruptionException`、`CryptoAuthenticationException`、`UnsupportedAlgorithmException`（lcl-core）；`KeyResolutionException`、`SchemaDriftException`（starter）
- `WireFormatDecoder` 解析失败从 `IllegalArgumentException` 改为 `PayloadCorruptionException`
- 4 个 Encryptor 的 decrypt 认证失败从裸 `CryptoException` 改为 `CryptoAuthenticationException`
- `KeyVaultService` 拆分 encrypt/decrypt 路径：解密路径（`getDekByVersion`、`getHmacKeyByVersion`）对缺失 vault 抛 `KeyResolutionException` 而非静默创建
- 所有新异常携带结构化上下文（namespace、dekVersion、algorithm 等）
- 保留 `DecryptionException` 为分组父类，现有 `catch (DecryptionException)` 代码零改动兼容

## Capabilities

### New Capabilities

- `error-taxonomy`: 结构化异常层次定义、结构化上下文携带、机器可读错误码、跨语言对齐

### Modified Capabilities

- `lcl-core-module`: 异常类归属变更（DecryptionException/EncryptionException 移入），WireFormatDecoder 和 Encryptor 抛出精确异常类型
- `key-vault`: 解密路径只读化——getDekByVersion/getHmacKeyByVersion 不再自动创建 vault
- `wire-format-v1`: WireFormatDecoder 异常类型从 IllegalArgumentException 变更为 PayloadCorruptionException

## Impact

- **lcl-spi**: 无变更（CryptoException 基类不动）
- **lcl-core**: 新增 `exception` 包（5 个类移入/新增）；WireFormatDecoder、4 个 Encryptor、AlgorithmId 的 throw 语句变更
- **lcl-spring-boot-starter**: 删除已下沉的异常类；KeyVaultService 拆分路径；FieldCryptoService 抛 SchemaDriftException；新增 KeyResolutionException
- **lcl-adapter-mongodb-core**: import 路径不变（包名不变），无源码改动
- **下游兼容性**: 包路径 `io.github.emmansun.lightcrypto.exception` 不变，Maven 传递依赖链不变，源码/二进制兼容
- **行为变更**: vault 缺失时解密路径不再静默创建（改为抛异常），这是有意的 breaking behavior fix
