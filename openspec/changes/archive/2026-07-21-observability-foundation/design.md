## Context

LightCrypto-Link 当前的可观测性仅限于 `@Slf4j` + 散落的 `log.info()`/`log.debug()` 调用。LCL-CORE-010 定义了完整的事件/指标/追踪/审计体系（983 行），本次变更仅落地其**核心子集**：EventBus SPI + Micrometer metrics + Health 状态模型。OTEL tracing、L3 审计持久化、CLI diagnostics 推迟到后续阶段。

## Goals / Non-Goals

**Goals:**
- 在 `lcl-core` 中定义 EventBus SPI，使事件发射成为平台契约
- 在 starter 中注册 Micrometer metrics，覆盖 encrypt/decrypt/rotation/keyvault 核心操作
- 在 starter 中暴露 Spring Boot Actuator HealthIndicator，报告 LCL 四态健康状态
- 新增 `lightcrypto.observability.*` 配置子树

**Non-Goals:**
- OTEL tracing 集成（推迟到 Phase 4+）
- L3 审计事件持久化（需要 WORM/Kafka 等外部 sink）
- CLI diagnostics 工具
- JMH benchmark（独立 change）
- Actuator 自定义端点（`/lcl/kat`、`/lcl/diagnostics` 等推迟到 bootstrap-diagnostics）

## Decisions

### Decision 1: EventBus 接口位置 — lcl-core

**选择**: `EventBus` 接口 + `LclEvent` 模型放在 `lcl-core` 的 `io.github.emmansun.lightcrypto.core.event` 包中。

**理由**:
- `lcl-core` 的定位是"纯 Java、零框架依赖"，EventBus 接口仅依赖 JDK 类型
- 跨 SDK（Java/Node.js 未来）共享同一事件契约
- `CryptoCodec` 等核心类可以在不引入 Spring 的情况下发射事件

**接口设计**:
```java
// 极简接口，无框架依赖
public interface EventBus {
    void emit(LclEvent event);
}
```

**默认实现**: `NoOpEventBus.INSTANCE`（无操作单例），确保不配置 EventBus 时零开销。

**替代方案**: 放在 `lcl-spi`。否决原因：事件是 core 层的行为抽象，不是 SPI 扩展点。

### Decision 2: LclEvent 模型 — Immutable Builder

**选择**: `LclEvent` 使用不可变 Builder 模式，仅包含 LCL-CORE-010 信封的核心子集字段。

```java
public final class LclEvent {
    private final String event;         // e.g. "lcl.crypto.encrypt.completed"
    private final EventTier tier;       // L1/L2/L3
    private final Instant timestamp;
    private final long durationMicros;  // -1 if not applicable
    private final String result;        // "success" / "failure"
    private final String namespace;     // nullable
    private final String algorithm;     // nullable
    private final int dekVersion;       // -1 if not applicable
    private final String errorType;     // nullable
    private final Map<String, String> attributes;  // extensible
}
```

**不包含的字段**（推迟到后续）:
- `traceId`/`spanId`（需要 OTEL）
- `correlationId`（需要请求上下文传播）
- `sdkLanguage`/`sdkVersion`（SDK identity 在 bootstrap-diagnostics 中添加）
- `actor`/`actorType`/`sequence`（L3 审计字段）

### Decision 3: EventBus 实现分层

**选择**: 在 starter 层提供两个实现 + 一个组合器：

```text
┌─────────────────────────────────────────────┐
│ CompositeEventBus                           │
│   ├─ Slf4jEventBus (structured JSON log)    │
│   └─ MicrometerEventBus (metrics adapter)   │
└─────────────────────────────────────────────┘
```

- **Slf4jEventBus**: 将 `LclEvent` 格式化为 JSON 字符串输出到 Slf4j logger，保持与 LCL-CORE-010 §7 的 JSON 结构化日志纪律一致
- **MicrometerEventBus**: 监听特定事件名模式（`lcl.crypto.*`、`lcl.rotation.*`），自动更新对应 Timer/Counter
- **CompositeEventBus**: 组合多个 EventBus，遍历发射

### Decision 4: Metrics 注册策略 — LclMetrics 集中注册

**选择**: 创建 `LclMetrics` 类集中管理所有 Meter 定义，通过 `MicrometerEventBus` 在事件发射时更新。

**核心 metrics 清单**:

| Metric Name | Type | Tags | 来源事件 |
|---|---|---|---|
| `lcl.crypto.encrypt.duration` | Timer | algorithm, namespace | lcl.crypto.encrypt.completed |
| `lcl.crypto.decrypt.duration` | Timer | algorithm, namespace | lcl.crypto.decrypt.completed |
| `lcl.blind_index.compute.duration` | Timer | namespace | lcl.blind_index.compute.completed |
| `lcl.keyvault.load.duration` | Timer | namespace | lcl.keyvault.load.completed |
| `lcl.rotation.duration` | Timer | namespace | lcl.rotation.execute.completed |
| `lcl.crypto.encrypt.total` | Counter | algorithm, result | lcl.crypto.encrypt.* |
| `lcl.crypto.decrypt.total` | Counter | algorithm, result | lcl.crypto.decrypt.* |
| `lcl.rotation.total` | Counter | result | lcl.rotation.execute.* |
| `lcl.keyvault.cache.size` | Gauge | — | 直接读取 cache |
| `lcl.keyvault.cache.hit.ratio` | Gauge | — | 直接读取 cache stats |

**Timer 百分位**: 发布 `publishPercentiles(0.5, 0.95, 0.99)`，对齐 LCL-CORE-011 p95 < 250µs 目标。

### Decision 5: Health 状态模型 — 组合式四态

**选择**: 定义 `LclHealthStatus` 枚举 + `LclHealthIndicator` 实现 Spring Boot Actuator `HealthIndicator`。

```text
Health(runtime) = worst(Health(core), Health(kms), Health(vault), Health(adapters))

状态转移:
STARTING → READY     (bootstrap 完成)
STARTING → FAILED    (fatal 错误)
READY    → DEGRADED  (组件降级)
DEGRADED → READY     (恢复)
*        → FAILED    (fatal)
```

**LclHealthIndicator 组件检查**:
- `CoreHealthCheck`: EventBus 是否已注册、配置是否有效
- `KmsHealthCheck`: 至少一个 CmkProvider bean 存在
- `VaultHealthCheck`: KeyVaultService 是否已初始化

各组件独立报告，取最差状态作为总体。

### Decision 6: ObservabilityProperties 配置结构

**选择**: 新增 `ObservabilityProperties`，prefix = `lightcrypto.observability`。

```yaml
lightcrypto:
  observability:
    enabled: true              # 总开关
    events:
      enabled: true            # EventBus 开关
    metrics:
      enabled: true            # Micrometer 开关
      publish-percentiles: true # Timer 百分位
    health:
      enabled: true            # HealthIndicator 开关
```

**条件装配**:
- `@ConditionalOnProperty("lightcrypto.observability.enabled", matchIfMissing = true)`
- 各子系统独立可控：`events.enabled`、`metrics.enabled`、`health.enabled`

## Risks / Trade-offs

- **[EventBus 性能开销]** → CompositeEventBus 遍历多个 listener 有额外开销。Mitigation: 默认仅 Slf4j + Micrometer 两个，且 EventBus 调用在热路径上应该是非阻塞的。
- **[Metrics 基数爆炸]** → `namespace` tag 可能导致高基数。Mitigation: 文档中明确建议生产环境限制 namespace 数量，或使用 `lcl.crypto.encrypt.total`（不带 namespace tag）作为默认看板。
- **[Slf4jEventBus 格式化成本]** → 每次事件发射都 JSON 序列化。Mitigation: 仅在 DEBUG/INFO 级别启用，生产环境 WARN 以上自动跳过。
