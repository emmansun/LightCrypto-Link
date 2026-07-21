## Context

LCL-CORE-020 定义了 BOOT-0 到 BOOT-15 共 16 个启动阶段（1282 行），涵盖配置验证、SPI 版本检查、KAT、canary、multi-tenant 等。本次变更仅落地**核心骨架**阶段，为后续 Phase 4（Multi-DB Adapters）和 Phase 5（Post-Quantum）预留扩展点。

本次变更**依赖** `observability-foundation` change 提供的 `EventBus` SPI。Bootstrap 事件通过 EventBus 发射，与 LCL-CORE-020 §15 的结构化事件契约对齐。

## Goals / Non-Goals

**Goals:**
- Bootstrap 引擎骨架：阶段门控 + 失败分类 + 超时 + EventBus 发射
- KAT 运行器：启动时验证核心密码原语（AES-GCM、SM4-GCM、HMAC-SHA256、HKDF）
- Canary self-test：启动时 + 持续运行的 canary encrypt/decrypt
- KMS/Vault 可达性检查
- Spring Boot Actuator 自定义端点

**Non-Goals:**
- 完整的 16 个 BOOT 阶段（仅实现核心 8 个）
- CLI diagnostics 工具（`lcl diagnostics` 命令推迟）
- 跨 SDK interop 验证（需要 Node.js SDK）
- Multi-tenant sanity 检查（需要多租户 Phase）
- Adapter self-test 完整流程（推迟到各 adapter 独立实现）
- 持续健康检查定时任务（仅启动时运行）

## Decisions

### Decision 1: BootstrapEngine 位置 — lcl-core

**选择**: `BootstrapEngine` 放在 `lcl-core` 的 `io.github.emmansun.lightcrypto.core.bootstrap` 包中。

**理由**:
- Bootstrap 是平台级关注点，不应绑定 Spring 框架
- KAT 运行器依赖 `CryptoCodec`（core 层），不需要 Spring
- 未来 standalone 模式（无 Spring）也能运行 Bootstrap

**核心模型**:

```java
public final class BootstrapEngine {
    private final List<BootstrapPhase> phases;
    private final EventBus eventBus;
    private final Duration timeout;

    public BootstrapResult run(BootstrapContext context) {
        // 顺序执行各阶段，任何 Fatal 失败中断
    }
}

public record BootstrapPhase(
    String name,          // e.g. "BOOT-4 KAT"
    BootstrapCheck check, // 函数式接口
    FailureClass failureClass  // FATAL / RECOVERABLE / ADVISORY
) {}
```

### Decision 2: Bootstrap 阶段子集

**选择**: 实现以下 8 个核心阶段（对照 LCL-CORE-020 BOOT-0~15）：

```text
BOOT-1  Configuration validation     ← 已由 @Validated 完成，Bootstrap 只验证结果
BOOT-2  SPI version check            ← 验证 spiVersion == 1
BOOT-4  Cryptographic KAT            ← AES-256-GCM, SM4-GCM, AES-256-CBC, SM4-CBC
BOOT-5  HKDF KAT                     ← HKDF-SHA-256 向量验证
BOOT-6  Blind index KAT              ← HMAC-SHA-256 向量验证
BOOT-7  Metadata format KAT          ← Wire Format V1 roundtrip
BOOT-8  Vault reachability           ← VaultStore.load() 不抛异常
BOOT-9  KMS reachability             ← CmkProvider 存在且 wrap/unwrap canary
BOOT-10 Canary encrypt/decrypt       ← 端到端 encrypt → decrypt → 验证
```

**跳过的阶段**:
- BOOT-0（环境扫描）：Spring Boot 已完成
- BOOT-3（Provider registration）：Auto-configuration 已完成
- BOOT-11~13（Blind index canary、Adapter self-test、Multi-tenant）：推迟
- BOOT-14~15（Health readiness、LCL_READY event）：由 HealthIndicator 替代

### Decision 3: KAT 向量打包策略

**选择**: 将 `vectors/` 目录的 JSON 文件复制到 `lcl-core/src/main/resources/kat/`，打包进 JAR。

**理由**:
- `vectors/` 在项目根目录，当前仅供测试（`VectorSuiteTest`）使用
- 生产 Bootstrap 需要在运行时加载这些向量
- 复制到 classpath 资源是最简单可靠的方式
- 未来可添加 SHA-256 校验防止向量被篡改

**加载方式**:
```java
// KatRunner 从 classpath 加载
InputStream is = getClass().getResourceAsStream("/kat/encryption/aes-256-gcm.json");
```

### Decision 4: KAT 预算与超时

**选择**: 
- 总 KAT 预算 ≤ 200ms（对齐 LCL-CORE-020 §2.4）
- 单原语 ≤ 30ms
- Bootstrap 总超时 15s（可通过 `lightcrypto.runtime.bootstrap-timeout` 配置）

**超时行为**:
- KAT 超时 = Fatal（LCL-CORE-020 §2.4: "Timeout → fatal"）
- Bootstrap 总超时 = `BootstrapTimeoutException`

### Decision 5: Failure Classification

**选择**: 三级失败分类，直接映射到 LCL-CORE-020 §17 的模型：

| Class | 行为 | 示例 |
|-------|------|------|
| `FATAL` | 立即终止启动 | KAT 失败、SPI 版本不匹配 |
| `RECOVERABLE` | 重试（最多 3 次 + backoff），超限则降级为 Advisory 或 Fatal | KMS 不可达、Vault 不可达 |
| `ADVISORY` | 记录警告，继续启动 | 已弃用算法使用、配置建议 |

**STRICT vs TOLERANT 模式**:
- `lightcrypto.runtime.strict-mode=true`（默认）：RECOVERABLE 超限 → Fatal
- `lightcrypto.runtime.strict-mode=false`：RECOVERABLE 超限 → Advisory（降级运行）

### Decision 6: Actuator 自定义端点

**选择**: 使用 Spring Boot Actuator `@Endpoint` + `@ReadOperation` 注解。

```java
@Endpoint(id = "lclhealth")    // → /actuator/lclhealth
@Endpoint(id = "lclkat")       // → /actuator/lclkat
```

**注意**: Spring Boot Actuator 端点 ID 不支持点号，使用 `lclhealth`、`lclkat` 而非 `lcl.health`、`lcl.kat`。

**端点响应格式**（对齐 LCL-CORE-020 Appendix C）:

```json
{
  "status": "READY",
  "sdkLanguage": "java",
  "sdkVersion": "1.0.0-SNAPSHOT",
  "spiVersion": 1,
  "wireFormatVersion": 1,
  "components": {
    "kat": "OK",
    "kms": "OK",
    "vault": "OK",
    "canary": "OK"
  },
  "lastBootstrap": "2026-07-21T11:30:00.000Z",
  "bootstrapDurationMs": 142
}
```

### Decision 7: Bootstrap 触发时机

**选择**: 在 `LightCryptoLinkAutoConfiguration` 的 `@PostConstruct` 或 `ApplicationRunner` 中触发 Bootstrap。

**推荐 ApplicationRunner**: 在 Spring context 完全初始化后运行，确保所有 bean 已就绪。

```text
Spring Boot 启动
  → Auto-configuration 注册所有 beans
  → ApplicationRunner 触发 BootstrapEngine.run()
  → Bootstrap 各阶段依次执行
  → 每个阶段发射 LclEvent 到 EventBus
  → 失败分类 + 超时处理
  → HealthIndicator 状态更新为 READY 或 FAILED
```

## Risks / Trade-offs

- **[启动延迟]** → Bootstrap 增加 ≤ 500ms 启动时间。Mitigation: 可配置 `lightcrypto.runtime.bootstrap-enabled=false` 跳过（不推荐生产）。
- **[KAT 向量同步]** → classpath 向量可能与项目根目录不同步。Mitigation: CI 中添加 SHA-256 校验步骤。
- **[Vault 可达性检查需要网络]** → 启动时如果 vault DB 尚未就绪，RECOVERABLE 重试可能延迟启动。Mitigation: 重试次数上限 3 次，指数退避。
