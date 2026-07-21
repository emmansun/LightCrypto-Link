## Context

Phase 1 交付了 `lcl-core`（纯函数密码学内核）和 `lcl-spi`（CmkProvider + placeholder VaultStore/StorageAdapter/QueryTransformer）。当前 `lcl-spring-boot-starter` 中的 `KeyVaultService` 直接依赖 `MongoTemplate` 进行 vault CRUD；`CryptoBeforeSaveListener` 直接构造 BSON `Document` 子文档；`CryptoMongoQueryCreator` 硬编码 `field.b` 查询路径。

项目采用选项 C 模块架构：`lcl-spi`（纯接口）→ `lcl-core`（密码学）→ `lcl-adapter-*`（数据库）→ `lcl-spring-boot-starter`（编排）。Phase 2 的目标是让 starter 层仅依赖 SPI 接口，MongoDB 特定实现全部迁入 `lcl-adapter-mongodb`。

约束：
- 无兼容性负担（未发布正式版）
- Java 17+
- 多租户 namespace 已在 Wire Format V1 中内建
- `lcl-spi` 不可依赖 Spring/DB 驱动

## Goals / Non-Goals

**Goals:**
- 定稿 `VaultStore`、`StorageAdapter`、`QueryTransformer` 三个 SPI 接口，使其足以支撑 MongoDB 和未来的 SQL 数据库
- 将 `KeyVaultService` 重构为仅依赖 `VaultStore`，消除对 `MongoTemplate` 的直接引用
- 将加密字段写入/读取格式逻辑抽象为 `StorageAdapter`，查询重写逻辑抽象为 `QueryTransformer`
- 创建 `lcl-adapter-mongodb` 模块作为首个参考实现
- 保持所有现有测试通过（允许测试代码适配新 API）

**Non-Goals:**
- 不实现 MySQL/PostgreSQL 适配器（Phase 4）
- 不实现 etcd/Consul VaultStore（Phase 6）
- 不改变 Wire Format V1 字节布局
- 不改变加密算法或 KCV/Binding 计算逻辑
- 不引入运行时 adapter 动态切换（编译期绑定）

## Decisions

### D1: VaultStore 接口粒度——文档级 CRUD vs 字段级操作

**选择**：文档级 CRUD（save/load/exists/rotate）

**理由**：vault 文档是原子单元（包含 keys[] 数组），字段级操作会破坏原子性且增加接口复杂度。rotation 作为独立方法（而非通用 save）是因为它需要 optimistic-locking 语义（CAS on version）。

**替代方案**：
- 通用 KV 接口（get/put/delete）：过于底层，无法表达 rotation 的 CAS 语义
- Repository 模式（JPA 风格）：引入 Spring Data 依赖，违反 lcl-spi 纯 JDK 约束

### D2: StorageAdapter payload 类型——Object vs 泛型 vs 具体类型

**选择**：`Object` 入参/出参 + `isEncryptedPayload(Object)` 判定

**理由**：不同数据库的 payload 类型不同（MongoDB 是 `Document`/BSON，SQL 是 `String`/JSON），使用 `Object` 避免泛型擦除问题和 SPI 对特定类型的依赖。Adapter 实现内部做类型检查和转换。

**替代方案**：
- `StorageAdapter<T>` 泛型：Spring 注入时类型擦除导致无法按类型区分多个 adapter
- 引入 `Payload` wrapper record：增加不必要的对象分配

### D3: lcl-adapter-mongodb 的 Spring Boot 集成方式

**选择**：独立 `@AutoConfiguration` + `@ConditionalOnClass(MongoTemplate.class)` + `@ConditionalOnMissingBean`

**理由**：
- 用户只需在 classpath 添加 `lcl-adapter-mongodb` 依赖即可自动激活
- `@ConditionalOnMissingBean` 允许用户覆盖默认实现
- 与现有 `lcl-provider-*` 模块的 auto-configuration 模式一致

### D4: starter 对 MongoDB 的依赖处理

**选择**：starter 的 `CryptoBeforeSaveListener` 等仍保留在 starter 中，但通过 `StorageAdapter`/`QueryTransformer` 接口操作；starter pom 将 `spring-boot-starter-data-mongodb` 改为 `optional`/`provided`

**理由**：
- listener 的 Spring Data 事件绑定（`AbstractMongoEventListener`）是 MongoDB 特有的，但加密编排逻辑是通用的
- 将 listener 拆分为「通用加密编排」+「MongoDB 事件绑定」两层：通用层在 starter，MongoDB 事件绑定在 adapter
- 这允许未来 JPA adapter 使用 `@PrePersist`/`@PostLoad` 而非 Mongo event listener

### D5: VaultDocument 中 version 字段的 optimistic locking

**选择**：`VaultDocument` 增加 `long version` 字段；`VaultStore.rotate(doc)` 使用 CAS（compare-and-swap on version）语义

**理由**：多实例并发 rotation 时需要防止 lost-update。version 字段由 adapter 实现映射到数据库特定机制（MongoDB 用 `findAndModify` + version 条件，SQL 用 `UPDATE ... WHERE version = ?`）。

### D6: 模块依赖关系

```
lcl-spi (JDK only)
  ↑
lcl-core (lcl-spi + BouncyCastle)
  ↑
lcl-adapter-mongodb (lcl-spi + lcl-core + spring-boot-starter-data-mongodb)
  ↑
lcl-spring-boot-starter (lcl-spi + lcl-core + spring-boot-autoconfigure)
  ↑ [runtime]
lcl-adapter-mongodb (由应用显式引入)
```

starter 编译期不依赖 adapter；运行时通过 Spring 注入获取 adapter bean。

## Risks / Trade-offs

- **[Risk] StorageAdapter Object 类型安全** → 运行时 ClassCastException 如果 adapter 与数据库不匹配 → Mitigation: adapter 的 `isEncryptedPayload` 做严格类型检查 + fail-fast 错误消息
- **[Risk] 事件监听器拆分增加复杂度** → 两层间接调用 → Mitigation: 保持 thin adapter 原则，adapter 只做格式转换，不含业务逻辑
- **[Risk] starter 移除 mongodb 传递依赖导致现有用户编译失败** → Mitigation: 无兼容性负担（未发布），且 README 明确说明需要显式引入 adapter
- **[Trade-off] VaultStore 接口不含批量 rotation** → 单 namespace rotation 足够当前需求；批量操作可后续扩展
- **[Trade-off] 保留 AbstractMongoEventListener 在 adapter 而非 starter** → starter 的测试需要 adapter 在 test scope → 可接受，adapter 是 starter 的 test 依赖
