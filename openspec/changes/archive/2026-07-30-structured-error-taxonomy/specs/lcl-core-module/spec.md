## MODIFIED Requirements

### Requirement: lcl-core contains WireFormat encoder and decoder
The `lcl-core` module SHALL provide `WireFormatEncoder` and `WireFormatDecoder` classes implementing the wire-format-v1 capability. The encoder SHALL produce byte arrays conforming to the V1 layout. The decoder SHALL parse V1 blobs and reject invalid inputs by throwing `PayloadCorruptionException`.

#### Scenario: Encoder produces V1-compliant blob
- **WHEN** encoding with version=1, algorithm=AES_256_GCM, namespace, dekVersion=1, iv, ciphertext
- **THEN** the output SHALL conform to the Wire Format V1 byte layout specification

#### Scenario: Decoder rejects truncated blob
- **WHEN** decoding a blob shorter than the minimum header size (11 bytes + 1 byte namespace)
- **THEN** the decoder SHALL throw `PayloadCorruptionException` with `rawLength` set to the blob's byte length

## ADDED Requirements

### Requirement: lcl-core contains exception subtypes for crypto engine errors
The `lcl-core` module SHALL define the following exception classes in package `io.github.emmansun.lightcrypto.exception`:
- `DecryptionException extends CryptoException` — grouping parent for decrypt-path failures
- `EncryptionException extends CryptoException` — encrypt-path failures
- `PayloadCorruptionException extends CryptoException` — wire format parse failures
- `CryptoAuthenticationException extends DecryptionException` — GCM/CBC authentication failures
- `UnsupportedAlgorithmException extends CryptoException` — unknown algorithm dispatch failures

These classes SHALL have no dependency on Spring Framework or any starter-level class.

#### Scenario: Encryptor throws CryptoAuthenticationException on auth failure
- **WHEN** AesGcmEncryptor.decrypt() encounters an invalid authentication tag
- **THEN** it SHALL throw `CryptoAuthenticationException` (not raw `CryptoException`)

#### Scenario: Encryptor throws EncryptionException on encrypt failure
- **WHEN** AesGcmEncryptor.encrypt() fails due to a JCE provider error
- **THEN** it SHALL throw `EncryptionException` (not raw `CryptoException`)

#### Scenario: AlgorithmId rejects unknown code
- **WHEN** `AlgorithmId.fromCode()` receives an unregistered byte value
- **THEN** it SHALL throw `UnsupportedAlgorithmException` with `algorithmId` set to the unknown byte value
