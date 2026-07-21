## Why

LightCrypto-Link 目前只有零散的 Slf4j `log.info`/`log.debug` 调用（分布在 KeyVaultService、FieldCryptoService、CmkProvider 中），缺乏结构化的事件模型、度量指标和健康状态管理。作为面向生产的加密平台，没有可观测性等于没有运维能力——无法回答"加密延迟是多少"、"DEK 缓存命中率如何"、"rotation 失败了几次"等关键运维问题。Phase 3 配置模型完成后，可观测性是运维成熟度的第一块基石。

## What Changes

- **新增 EventBus SPI**：在 `lcl-core` 中定义 `EventBus` 接口和 `LclEvent` 模型，使事件发射成为平台契约而非散落的 log 语句
- **新增 Micrometer metrics**：在 `lcl-spring-boot-starter` 中注册核心 Timer/Counter/Gauge，覆盖 encrypt/decrypt/rotation/keyvault/blind-index 操作
- **新增 Health 状态模型**：定义 `STARTING`/`READY`/`DEGRADED`/`FAILED` 四态，通过 Spring Boot Actuator `HealthIndicator` 暴露
- **重构现有 log 调用**：将 `KeyVaultService`、`FieldCryptoService`、`CmkProvider` 等中的散落 log 替换为 EventBus 发射
- **新增 ObservabilityProperties**：`lightcrypto.observability.*` 配置子树，支持 `enabled`/`events.enabled`/`metrics.enabled` 开关

## Capabilities

### New Capabilities
- `event-bus-spi`: EventBus 接口 + LclEvent 模型 + NoOpEventBus 默认实现（lcl-core 层，零框架依赖）
- `metrics-foundation`: Micrometer metrics 注册 + 核心操作 Timer/Counter/Gauge（starter 层）
- `health-state-model`: Health 状态枚举 + LclHealthIndicator + Spring Boot Actuator 集成（starter 层）

### Modified Capabilities
- `key-vault`: KeyVaultService 增加结构化事件发射（rotation/load/cache-evict 通过 EventBus 发射而非直接 log）
- `transparent-listener`: CryptoBeforeSaveListener 增加 encrypt 操作的 Timer 计时

## Impact

- **lcl-core**: 新增 `event` 包（EventBus, LclEvent, EventTier, NoOpEventBus），无新增依赖
- **lcl-spring-boot-starter**: 新增 `observability` 包，新增 `micrometer-core` 依赖（Spring Boot 已 BOM 管理）
- **lcl-spring-boot-starter**: 新增 `ObservabilityProperties`，扩展 `@EnableConfigurationProperties`
- **现有代码**: KeyVaultService（~5 处 log → EventBus）、FieldCryptoService（~1 处）、CmkProvider auto-config（~2 处）
- **测试**: EventBus 相关单元测试 + AutoConfiguration 条件测试
