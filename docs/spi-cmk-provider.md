# CMK Provider SPI

LCL provides a CMK SPI so you can integrate custom key-management systems.

## Interface

```java
public interface CmkProvider {
    String getProviderId();
    String getPublicReference();
    WrappedKey wrap(byte[] plaintextKey);
    byte[] unwrap(WrappedKey wrappedKey);
}
```

## Semantics

- `getProviderId`: stable provider identifier persisted in vault metadata.
- `getPublicReference`: non-secret key reference (for example key ARN/version id).
- `wrap`: wraps raw DEK/HMAC bytes with your CMK.
- `unwrap`: reverses wrapping and returns raw key bytes.

## Built-in Providers

- `LocalSymmetricCmkProvider`
- `AzureKeyVaultCmkProvider`
- `AlibabaKmsCmkProvider`

## Implementation Tips

- Keep `wrap/unwrap` deterministic with compatible algorithm metadata in `WrappedKey.algorithm`.
- Return actionable exceptions for unavailable KMS or invalid ciphertext.
- Never log plaintext DEK/HMAC bytes.
