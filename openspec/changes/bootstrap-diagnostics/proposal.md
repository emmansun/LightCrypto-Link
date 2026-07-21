## Why

LightCrypto-Link 目前没有启动阶段的自诊断机制。如果 KMS provider 配置错误、KAT 向量被篡改、或 vault store 不可达，错误只会在运行时加密操作失败时才暴露——此时用户数据可能已经写入失败或产生不一致。LCL-CORE-020 定义了 BOOT-0 到 BOOT-15 的完整启动阶段门控体系，本次变更落地其核心子集：Bootstrap 引擎骨架 + KAT 运行器 + Canary 自测 + KMS/Vault 可达性检查 + Actuator 健康端点。

## What Changes

- **新增 BootstrapEngine**：阶段式启动流程（BOOT-1 到 BOOT-13），每个阶段有超时、分类（Fatal/Recoverable/Advisory）、结构化事件发射
- **新增 KAT Runner**：启动时运行 Known Answer Tests，复用 `vectors/` 目录下的黄金向量，总预算 ≤ 200ms
- **新增 Canary Self-Test**：启动 + 持续运行 canary encrypt/decrypt + blind index 校验，确认端到端加密路径正确
- **新增 KMS/Vault 可达性检查**：启动时验证 KMS provider 可用 + vault store 可访问
- **新增 Actuator 端点**：`/actuator/lcl/health`（详细诊断信息）、`/actuator/lcl/kat`（KAT 结果）
- **依赖 observability-foundation**：Bootstrap 事件通过 EventBus 发射

## Capabilities

### New Capabilities
- `bootstrap-engine`: 阶段式启动引擎 + 失败分类 + 超时门控 + 结构化事件序列
- `kat-runner`: KAT 向量加载 + 多算法验证 + 200ms 预算 + 确定性保障
- `canary-self-test`: Canary encrypt/decrypt + blind index 校验 + 多算法覆盖
- `diagnostics-endpoints`: Spring Boot Actuator 自定义端点（health/kat/diagnostics）

### Modified Capabilities

（无修改现有 spec 的需求。Bootstrap 诊断是全新基础设施，不改变已有行为的规范。）

## Impact

- **lcl-core**: 新增 `bootstrap` 包（BootstrapEngine, BootstrapPhase, BootstrapResult, KatRunner, CanaryRunner）
- **lcl-core**: 打包 KAT 向量资源文件到 `src/main/resources/kat/`（从项目 `vectors/` 目录复制或引用）
- **lcl-spring-boot-starter**: 新增 `diagnostics` 包（DiagnosticsAutoConfiguration, actuator endpoints）
- **lcl-spring-boot-starter**: 新增 `spring-boot-starter-actuator` optional 依赖
- **依赖**: bootstrap-diagnostics 依赖 observability-foundation 提供的 EventBus SPI
- **启动时间**: 增加 ≤ 500ms（KAT 200ms + canary 50ms + 可达性检查 250ms）
