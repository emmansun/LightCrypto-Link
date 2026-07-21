package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.exception.OptimisticLockException;
import io.github.emmansun.lightcrypto.spi.VaultDocument;
import io.github.emmansun.lightcrypto.spi.VaultStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory implementation of {@link VaultStore} for testing purposes.
 */
public class InMemoryVaultStore implements VaultStore {

    private final ConcurrentMap<String, VaultDocument> store = new ConcurrentHashMap<>();

    @Override
    public void save(VaultDocument doc) {
        store.put(doc.namespace(), doc);
    }

    @Override
    public Optional<VaultDocument> load(String namespace) {
        return Optional.ofNullable(store.get(namespace));
    }

    @Override
    public boolean exists(String namespace) {
        return store.containsKey(namespace);
    }

    @Override
    public VaultDocument rotate(VaultDocument updatedDoc) {
        String namespace = updatedDoc.namespace();
        VaultDocument existing = store.get(namespace);

        if (existing == null) {
            throw new OptimisticLockException(
                    "Vault document not found for namespace: " + namespace);
        }

        long expectedVersion = updatedDoc.version() - 1;
        if (existing.version() != expectedVersion) {
            throw new OptimisticLockException(
                    "Concurrent vault rotation detected for namespace: " + namespace
                            + ". Expected version " + expectedVersion
                            + " but stored version is " + existing.version());
        }

        store.put(namespace, updatedDoc);
        return updatedDoc;
    }

    @Override
    public List<VaultDocument> loadAll() {
        return new ArrayList<>(store.values());
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
