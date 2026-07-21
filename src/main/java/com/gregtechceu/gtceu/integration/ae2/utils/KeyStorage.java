package com.gregtechceu.gtceu.integration.ae2.utils;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Used to store {@link GenericStack} values in stable first-insertion order.
 * Provides methods for serialization and deserialization.
 */
public class KeyStorage implements Iterable<Object2LongMap.Entry<AEKey>> {

    /**
     * Mutable authoritative storage retained for existing machine and persistence callers.
     */
    public final Object2LongLinkedOpenHashMap<AEKey> storage = new Object2LongLinkedOpenHashMap<>();

    private final Object2LongLinkedOpenHashMap<AEKey> published = new Object2LongLinkedOpenHashMap<>();
    private Runnable onContentsChanged = () -> {};
    private Consumer<ChangeBatch> onViewChanged = changes -> {};

    /**
     * Installs the machine-trait listener used to publish inventory capability changes.
     */
    public void setOnContentsChanged(Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Key storage contents listener must be present.");
        }
        onContentsChanged = listener;
    }

    /**
     * Installs the independent waiting-list listener used by the opening-scoped view publisher.
     */
    public void setOnViewChanged(Consumer<ChangeBatch> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Key storage view listener must be present.");
        }
        onViewChanged = listener;
    }

    /**
     * Insert the stacks into the inventory as much as possible
     *
     * @param inventory the inventory into which stacks will be inserted
     * @param source    the source of the action
     */
    public void insertInventory(MEStorage inventory, IActionSource source) {
        var it = iterator();
        List<Change> changes = new ArrayList<>();
        while (it.hasNext()) {
            var entry = it.next();
            var key = entry.getKey();
            var amount = entry.getLongValue();
            long inserted = inventory.insert(key, amount, Actionable.MODULATE,
                    source);
            if (inserted > 0) {
                if (inserted >= amount) {
                    it.remove();
                    changes.add(new Change(key, 0));
                } else {
                    long remaining = amount - inserted;
                    entry.setValue(remaining);
                    changes.add(new Change(key, remaining));
                }
            }
        }
        publish(new ChangeBatch(changes));
    }

    /**
     * Writes one positive amount and directly publishes the exact operation when the value changed.
     */
    public boolean put(AEKey key, long amount) {
        requirePositive(key, amount);
        if (storage.containsKey(key) && storage.getLong(key) == amount) {
            return false;
        }
        storage.put(key, amount);
        publish(new ChangeBatch(List.of(new Change(key, amount))));
        return true;
    }

    /**
     * Removes one key and directly publishes its tombstone when the key existed.
     */
    public boolean remove(AEKey key) {
        if (key == null) {
            throw new IllegalArgumentException("Key storage removal key must be present.");
        }
        if (!storage.containsKey(key)) {
            return false;
        }
        storage.removeLong(key);
        publish(new ChangeBatch(List.of(new Change(key, 0))));
        return true;
    }

    public void onChanged() {
        publish(collectChanges());
    }

    /**
     * Returns an immutable positive snapshot in first-insertion order for a full publication.
     */
    public List<Change> snapshot() {
        List<Change> snapshot = new ArrayList<>(storage.size());
        for (Object2LongMap.Entry<AEKey> entry : storage.object2LongEntrySet()) {
            long amount = requirePositive(entry.getKey(), entry.getLongValue());
            snapshot.add(new Change(entry.getKey(), amount));
        }
        return List.copyOf(snapshot);
    }

    /**
     * Atomically replaces persisted contents and publishes at most one real change batch.
     */
    public void replaceContents(List<Change> replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("Key storage replacement must be present.");
        }
        Object2LongLinkedOpenHashMap<AEKey> validated = new Object2LongLinkedOpenHashMap<>();
        Set<AEKey> keys = new HashSet<>(replacement.size());
        for (Change entry : replacement) {
            if (entry == null) {
                throw new IllegalArgumentException("Key storage replacement contains a missing entry.");
            }
            requirePositive(entry.key(), entry.amount());
            if (!keys.add(entry.key())) {
                throw new IllegalArgumentException("Key storage replacement repeats AE key " + entry.key() + '.');
            }
            validated.put(entry.key(), entry.amount());
        }
        storage.clear();
        storage.putAll(validated);
        onChanged();
    }

    @Override
    public Iterator<Object2LongMap.Entry<AEKey>> iterator() {
        return storage.object2LongEntrySet().iterator();
    }

    public boolean isEmpty() {
        return storage.isEmpty();
    }

    private ChangeBatch collectChanges() {
        List<AEKey> previousOrder = keyOrder(published);
        List<AEKey> currentOrder = keyOrder(storage);
        int commonPrefix = 0;
        int sharedLength = Math.min(previousOrder.size(), currentOrder.size());
        while (commonPrefix < sharedLength &&
                previousOrder.get(commonPrefix).equals(currentOrder.get(commonPrefix))) {
            commonPrefix++;
        }

        List<Change> changes = new ArrayList<>();
        for (int index = 0; index < commonPrefix; index++) {
            AEKey key = currentOrder.get(index);
            long amount = requirePositive(key, storage.getLong(key));
            if (published.getLong(key) != amount) {
                changes.add(new Change(key, amount));
            }
        }
        for (int index = commonPrefix; index < previousOrder.size(); index++) {
            changes.add(new Change(previousOrder.get(index), 0));
        }
        for (int index = commonPrefix; index < currentOrder.size(); index++) {
            AEKey key = currentOrder.get(index);
            changes.add(new Change(key, requirePositive(key, storage.getLong(key))));
        }
        return new ChangeBatch(changes);
    }

    private void copyCurrentToPublished() {
        published.clear();
        published.putAll(storage);
    }

    private void publish(ChangeBatch changes) {
        if (changes.isEmpty()) {
            return;
        }
        copyCurrentToPublished();
        onContentsChanged.run();
        onViewChanged.accept(changes);
    }

    private static List<AEKey> keyOrder(Object2LongLinkedOpenHashMap<AEKey> values) {
        return List.copyOf(values.keySet());
    }

    private static long requirePositive(AEKey key, long amount) {
        if (key == null) {
            throw new IllegalArgumentException("Key storage contains a missing AE key.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Key storage amount must be positive for " + key + ": " + amount);
        }
        return amount;
    }

    /**
     * One ordered positive write or zero-amount tombstone emitted by the storage.
     */
    public record Change(AEKey key, long amount) {

        /**
         * Validates the operation without forbidding the delta tombstone value.
         */
        public Change {
            if (key == null) {
                throw new IllegalArgumentException("Key storage change key must be present.");
            }
            if (amount < 0) {
                throw new IllegalArgumentException("Key storage change amount must be non-negative: " + amount);
            }
        }
    }

    /**
     * Immutable ordered operations produced by one logical storage notification.
     */
    public record ChangeBatch(List<Change> changes) {

        /**
         * Isolates the batch from mutable source collections.
         */
        public ChangeBatch {
            if (changes == null) {
                throw new IllegalArgumentException("Key storage change batch must be present.");
            }
            changes = List.copyOf(changes);
        }

        /**
         * Returns whether no authoritative content or order changed.
         */
        public boolean isEmpty() {
            return changes.isEmpty();
        }
    }
}
