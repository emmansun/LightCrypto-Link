package io.github.emmansun.lightcrypto.exception;

/**
 * Thrown when an optimistic-locking (CAS) conflict is detected during vault rotation.
 *
 * <p>This indicates that a concurrent modification has occurred — the stored document's
 * version does not match the expected version. The caller should retry the operation.
 *
 * @since 1.0.0
 */
public class OptimisticLockException extends CryptoException {

    public OptimisticLockException(String message) {
        super(message);
    }

    public OptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
