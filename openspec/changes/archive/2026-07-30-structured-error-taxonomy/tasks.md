## 1. Exception classes — lcl-core

- [x] 1.1 Create `lcl-core/src/main/java/io/github/emmansun/lightcrypto/exception/DecryptionException.java` (move from starter, extends CryptoException)
- [x] 1.2 Create `lcl-core/src/main/java/io/github/emmansun/lightcrypto/exception/EncryptionException.java` (move from starter, extends CryptoException)
- [x] 1.3 Create `lcl-core/src/main/java/io/github/emmansun/lightcrypto/exception/PayloadCorruptionException.java` (extends CryptoException, fields: namespace, rawLength)
- [x] 1.4 Create `lcl-core/src/main/java/io/github/emmansun/lightcrypto/exception/CryptoAuthenticationException.java` (extends DecryptionException, fields: namespace, dekVersion, algorithm)
- [x] 1.5 Create `lcl-core/src/main/java/io/github/emmansun/lightcrypto/exception/UnsupportedAlgorithmException.java` (extends CryptoException, fields: algorithmId, algorithmName)
- [x] 1.6 Delete `DecryptionException.java` and `EncryptionException.java` from starter exception package
- [x] 1.7 Verify `mvn -pl lcl-core compile` succeeds independently

## 2. Exception classes — starter

- [x] 2.1 Create `lcl-spring-boot-starter/src/main/java/io/github/emmansun/lightcrypto/exception/KeyResolutionException.java` (extends KeyManagementException, fields: namespace, dekVersion)
- [x] 2.2 Create `lcl-spring-boot-starter/src/main/java/io/github/emmansun/lightcrypto/exception/SchemaDriftException.java` (extends DecryptionException, fields: namespace, targetType, fieldPath)
- [x] 2.3 Fix starter imports referencing moved DecryptionException/EncryptionException (should resolve automatically via same package name + transitive dep)

## 3. WireFormatDecoder — PayloadCorruptionException

- [x] 3.1 Replace all `throw new IllegalArgumentException(...)` in WireFormatDecoder with `throw new PayloadCorruptionException(...)` carrying rawLength context
- [x] 3.2 Replace `throw new IllegalArgumentException(...)` in `AlgorithmId.fromCode()` with `throw new UnsupportedAlgorithmException(...)`
- [x] 3.3 Update WireFormatDecoder unit tests: assertThrows(PayloadCorruptionException.class, ...) and verify context getters
- [x] 3.4 Update AlgorithmId/WireFormatDecoder tests for UnsupportedAlgorithmException

## 4. Encryptors — CryptoAuthenticationException / EncryptionException

- [x] 4.1 AesGcmEncryptor: decrypt catch AEADBadTagException → throw CryptoAuthenticationException; encrypt catch GeneralSecurityException → throw EncryptionException
- [x] 4.2 Sm4GcmEncryptor: same pattern as AesGcmEncryptor
- [x] 4.3 AesCbcEncryptor: decrypt catch BadPaddingException → throw CryptoAuthenticationException; encrypt → throw EncryptionException
- [x] 4.4 Sm4CbcEncryptor: same pattern as AesCbcEncryptor
- [x] 4.5 KCV computation failures: keep as CryptoException (not decrypt/encrypt path) or wrap in EncryptionException — decide per context
- [x] 4.6 Update lcl-core Encryptor unit tests for new exception types

## 5. KeyVaultService — decrypt path read-only

- [x] 5.1 Extract `ensureCachedForDecrypt(namespace)` method: load vault → if absent throw KeyResolutionException (no auto-init)
- [x] 5.2 Route `getDekByVersion()` and `getHmacKeyByVersion()` through decrypt path
- [x] 5.3 Route `getActiveKid()`, `getDek()`, `getActiveHmacKey()`, `rotateDek()` through existing encrypt path (ensureVaultInitialized)
- [x] 5.4 Add KeyResolutionException when vault exists but requested DEK version not found
- [x] 5.5 Update KeyVaultService unit tests: verify decrypt path throws KeyResolutionException on missing vault/version
- [x] 5.6 Verify DekReEncryptionService integration tests still pass (vault pre-exists via rotateDek)

## 6. FieldCryptoService — SchemaDriftException

- [x] 6.1 In FieldCryptoService decrypt: catch type deserialization failure → throw SchemaDriftException with targetType and fieldPath
- [x] 6.2 Update FieldCryptoService unit tests for SchemaDriftException

## 7. CryptoCodec — exception propagation

- [x] 7.1 Review CryptoCodec.decrypt(): ensure PayloadCorruptionException from WireFormatDecoder propagates (not swallowed/wrapped)
- [x] 7.2 Review CryptoCodec.decrypt(): ensure CryptoAuthenticationException from Encryptor propagates
- [x] 7.3 Update CryptoCodecTest / VectorSuiteTest if they assert on old exception types

## 8. Full build verification

- [x] 8.1 `mvn clean compile` — all modules compile
- [x] 8.2 `mvn test` — all existing + new tests pass
- [x] 8.3 `mvn -pl lcl-spring-boot-starter spotbugs:check` — no warnings
- [x] 8.4 Verify adapter-mongodb-core and adapter-mongodb compile without source changes (import path unchanged)
