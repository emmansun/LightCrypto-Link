## Context

Java SDK 当前异常体系：

```
lcl-spi:       CryptoException (base), OptimisticLockException
lcl-core:      BootstrapTimeoutException; Encryptors throw raw CryptoException; WireFormatDecoder throws IllegalArgumentException
starter:       DecryptionException, EncryptionException, FatalCryptoException, KeyManagementException, ConfigurationException, UnsupportedTypeException
```

问题：lcl-core 的加密引擎无法抛出语义化子类型（因为 DecryptionException 在 starter），WireFormatDecoder 的 14 处 IllegalArgumentException 无法被调用方精确 catch。Node.js SDK 已完成 structured-error-taxonomy（5 子类 + 结构化上下文 + EventBus 分级），Java 侧需对齐。

模块依赖链：`lcl-spi ← lcl-core ← starter ← adapter-mongodb-core`

## Goals / Non-Goals

**Goals:**

- 建立精确异常层次，使调用方可按故障类型分别处理
- 将异常定义下沉到正确的模块层（加密引擎错误在 lcl-core，编排错误在 starter）
- 解密路径只读化：vault 缺失时抛异常而非静默创建
- 所有新异常携带结构化上下文（namespace, dekVersion, algorithm）
- 保持包路径 `io.github.emmansun.lightcrypto.exception` 不变，源码/二进制兼容

**Non-Goals:**

- 不引入机器可读错误码枚举（Java 生态惯例用异常类型 + message，非 error code string；留待未来对齐）
- 不修改 EventBus 事件分级（已有 metrics-foundation 覆盖）
- 不重构 Encryptor 的 encrypt 路径异常（本次聚焦 decrypt 路径；encrypt 路径仅做 EncryptionException 下沉）
- 不修改 lcl-spi 中的 CryptoException 基类

## Decisions

### D1: 保留 DecryptionException 为分组父类

**选择**: DecryptionException 保留，语义从"万能桶"变为"解密路径错误族父类"。

**替代方案**: 移除 DecryptionException，所有子类直接 extends CryptoException（Node.js 做法）。

**理由**: Java 生态保守，下游可能有 `@ExceptionHandler` 或 `catch (DecryptionException)` 代码。保留为父类实现零 breaking：现有 catch 仍兜底，新代码可精确 catch。

最终层次：
```
CryptoException (lcl-spi)
├── DecryptionException (lcl-core) ← 分组父类
│   ├── CryptoAuthenticationException (lcl-core)
│   └── SchemaDriftException (starter)
├── EncryptionException (lcl-core)
├── PayloadCorruptionException (lcl-core)
├── UnsupportedAlgorithmException (lcl-core)
├── KeyManagementException (starter)
│   └── KeyResolutionException (starter)
├── FatalCryptoException (starter)
├── ConfigurationException (starter)
├── UnsupportedTypeException (starter)
└── OptimisticLockException (lcl-spi)
```

### D2: 新异常放 lcl-core，非 lcl-spi

**选择**: DecryptionException、EncryptionException 下沉到 lcl-core；新增 3 个 core 异常 + 2 个 starter 异常。

**替代方案**: 全部放 lcl-spi（与 CryptoException 基类同模块）。

**理由**:
- SPI 是契约层（接口定义），应保持极简。适配器实现者不需要知道 PayloadCorruptionException。
- 这些异常描述的是加密引擎的故障模式，与 CryptoCodec/WireFormatDecoder 同层。
- lcl-core 对 starter 可见（传递依赖），starter 代码照样能 throw/catch。
- KeyResolutionException 和 SchemaDriftException 只由 starter 代码抛出，放 starter 即可（它们 extends 的父类在 core，starter 看得到）。

### D3: 解密路径只读化同步实施

**选择**: KeyVaultService 拆分 encrypt/decrypt 路径，解密路径 vault 缺失时抛 KeyResolutionException。

**替代方案**: 仅做异常重命名，不改行为（延后只读化）。

**理由**: 如果不做只读化，KeyResolutionException 是死代码——`ensureVaultInitialized()` 总会自动创建 vault。静默创建垃圾 vault 是 bug（新 vault 有新 DEK，旧密文仍无法解密），应暴露为错误。

路由规则：
| 方法 | 路径 | vault 缺失行为 |
|------|------|---------------|
| getActiveKid() | encrypt | 自动创建 |
| getDek(kid) | encrypt | 自动创建 |
| getActiveHmacKey() | encrypt | 自动创建 |
| rotateDek() | encrypt | 自动创建 |
| getDekByVersion(ns, ver) | **decrypt** | throw KeyResolutionException |
| getHmacKeyByVersion(ns, ver) | **decrypt** | throw KeyResolutionException |

### D4: PayloadCorruptionException 不继承 DecryptionException

**选择**: `PayloadCorruptionException extends CryptoException`（直接继承基类）。

**理由**: 它发生在 WireFormatDecoder 解析阶段（decryption 尚未开始），语义上是"输入数据损坏"而非"解密失败"。与 CryptoAuthenticationException（真正的解密失败）区分开。

### D5: 结构化上下文通过构造函数参数携带

**选择**: 异常类提供携带上下文的构造函数（namespace, dekVersion, algorithm），通过 getter 暴露。

**替代方案**: 使用 Map<String, Object> context 字段（Node.js 做法）。

**理由**: Java 惯例是强类型字段。Map 丧失编译期安全，且 Java 调用方更习惯 `e.getNamespace()` 而非 `e.getContext().get("namespace")`。

## Risks / Trade-offs

**[Risk] 解密路径只读化是行为变更** → Mitigation: 仅影响 vault 缺失的异常场景（正常启动后 vault 必然存在）。DekReEncryptionService 调用 getDekByVersion 时 vault 必须已存在（由 rotateDek 保证），不受影响。

**[Risk] lcl-core 新增 exception 包增加模块体积** → Mitigation: 仅 5 个简单 RuntimeException 子类，无外部依赖。

**[Risk] 现有测试中 catch IllegalArgumentException 的断言会失败** → Mitigation: PayloadCorruptionException extends CryptoException（非 IllegalArgumentException），需更新 WireFormatDecoder 相关测试。这是有意的 API 变更。

**[Trade-off] 不引入 error code string** → 跨语言对齐弱于 Node.js，但符合 Java 生态惯例。未来可通过 `getErrorCode()` 方法补充。
