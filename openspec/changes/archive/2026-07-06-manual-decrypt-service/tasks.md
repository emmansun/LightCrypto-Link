## 1. 新建 FieldCryptoService 核心实现

- [x] 1.1 在 `io.github.emmansun.lightcrypto.service` 包中新建 `FieldCryptoService` 类，声明依赖 `EntityMetadataCache`、`CryptoCodec`、`TypeDeserializer`、`KeyVaultService`，编写构造函数
- [x] 1.2 实现 `Document decryptDocument(Document rawDocument, Class<?> entityClass)` 方法：参数 null 校验（抛 `IllegalArgumentException`）、无加密字段早返回、遍历 `@Encrypted` 字段元数据、解析子文档 `_e/_k/_a/_t/c`、通过 `keyVaultService.getDek(kid)` 查找 DEK、通过 `cryptoCodec.decrypt()` 按算法解密、通过 `typeDeserializer.deserialize()` 反序列化、就地替换字段值并返回同一 Document
- [x] 1.3 确保子文档缺少 `_k` 字段时抛出 `FatalCryptoException`，缺少 `_e: 1` 标记或非 Document 类型时跳过（幂等/兼容）

## 2. 重构 CryptoMappingMongoConverter 委托 FieldCryptoService

- [x] 2.1 修改 `CryptoMappingMongoConverter` 构造函数：移除 `CryptoCodec` 和 `TypeDeserializer` 参数，新增 `FieldCryptoService` 参数；保留 `EntityMetadataCache` 参数
- [x] 2.2 将 `decryptFields(Document, Class<?>)` 方法体改为单行委托：`fieldCryptoService.decryptDocument(document, entityClass)`；移除不再需要的字段和 import
- [x] 2.3 更新 `LightCryptoLinkAutoConfiguration.mappingMongoConverter()` Bean 方法：注入 `FieldCryptoService`，移除 `CryptoCodec` 和 `TypeDeserializer` 参数，调整构造函数调用
- [x] 2.4 在 `LightCryptoLinkAutoConfiguration` 中新增 `fieldCryptoService()` `@Bean` 方法，注入 `EntityMetadataCache`、`CryptoCodec`、`TypeDeserializer`、`KeyVaultService`

## 3. 更新现有测试适配构造参数变更

- [x] 3.1 更新 `CryptoBeforeConvertListenerTest` 中 `TestableConverter` 子类构造：将 `CryptoCodec` + `TypeDeserializer` 替换为 `FieldCryptoService` 实例
- [x] 3.2 运行全量单元测试，确认所有现有测试通过（57 个核心单元测试，0 failures）

## 4. 新增 FieldCryptoService 单元测试

- [x] 4.1 在 `src/test/java/io/github/emmansun/lightcrypto/` 中新建 `FieldCryptoServiceTest.java`，复用 `LclTestBase` + `TestKeyVaultService` 测试基础设施
- [x] 4.2 测试 `decryptDocument` 正常解密 String 字段（AES-256-GCM）：构造含 `{c: Binary, _e: 1, _t: "STR", _k: "v1-xxx", _a: "AES_256_GCM"}` 子文档的 Document，验证解密后字段为明文 String
- [x] 4.3 测试多字段解密：Document 包含两个加密字段（age + birthDate），验证均被正确解密
- [x] 4.4 测试类型化字段解密（Integer / LocalDate）：验证 `_t: "INT"` 和 `_t: "LDATE"` 场景
- [x] 4.5 测试多算法分发：验证 `_a: "SM4_GCM"` / `"AES_256_CBC"` / `"SM4_CBC"` 场景使用正确的算法解密
- [x] 4.6 测试 null 参数校验：`decryptDocument(null, User.class)` 和 `decryptDocument(doc, null)` 均抛 `IllegalArgumentException`
- [x] 4.7 测试无加密字段实体：传入无 `@Encrypted` 字段的 `TestPlainEntity`，验证 Document 不变
- [x] 4.8 测试字段缺失 / null 值 / 非 Document 类型：验证均被跳过不报错
- [x] 4.9 测试幂等性：对已解密 Document 再次调用 `decryptDocument`，验证无副作用
- [x] 4.10 测试缺少 `_k` 字段时抛出 `FatalCryptoException`
- [x] 4.11 测试 `_a` 字段缺失时默认使用 `AES_256_GCM`
- [x] 4.12 测试返回值与传入的 Document 是同一引用（in-place）
- [x] 4.13 运行全量测试，确认新增 18 个测试 + 既有 71 个测试共 89 个全部通过，0 failures
