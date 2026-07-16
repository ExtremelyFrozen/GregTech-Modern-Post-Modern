package com.gregtechceu.gtceu.api.gui.slot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds opening-local manifests while preserving every slot range already assigned to that opening.
 */
public final class DynamicItemSlotManifestSequence {

    private DynamicItemSlotManifestSequence() {}

    /**
     * Advances the manifest from the last activated layout to an ordered source snapshot.
     *
     * <p>
     * Existing ranges remain in place, removed or reshaped targets become tombstones, and every new lifecycle is
     * appended with a fresh binding id.
     * </p>
     */
    public static DynamicItemSlotManifest advance(Optional<DynamicItemSlotManifest> activeManifest,
                                                  long sourceRevision, int baseSlotCount,
                                                  List<DynamicItemSlotDefinition> definitions) {
        if (activeManifest == null) {
            throw new IllegalArgumentException("activeManifest must not be null");
        }
        if (definitions == null) {
            throw new IllegalArgumentException("definitions must not be null");
        }

        DynamicItemSlotManifest active = activeManifest.orElse(null);
        if (active != null) {
            if (active.baseSlotCount() != baseSlotCount) {
                throw new IllegalArgumentException("baseSlotCount changed inside one menu opening");
            }
            if (sourceRevision < active.sourceRevision()) {
                throw new IllegalArgumentException("sourceRevision must not move backwards");
            }
            if (active.epoch() == Long.MAX_VALUE) {
                throw new IllegalStateException("dynamic item slot epoch exhausted");
            }
        }

        Map<UUID, DynamicItemSlotDefinition> remainingDefinitions = orderedDefinitions(definitions);
        List<DynamicItemSlotBinding> bindings = new ArrayList<>();
        Set<UUID> allocatedBindingIds = new HashSet<>();
        if (active != null) {
            for (DynamicItemSlotBinding binding : active.bindings()) {
                allocatedBindingIds.add(binding.bindingId());
                DynamicItemSlotDefinition definition = remainingDefinitions.get(binding.targetId());
                boolean remainsPresent = binding.present() && definition != null &&
                        definition.slotCount() == binding.slotCount();
                bindings.add(new DynamicItemSlotBinding(
                        binding.bindingId(), binding.targetId(), binding.firstSlotId(), binding.slotCount(),
                        remainsPresent));
                if (remainsPresent) {
                    remainingDefinitions.remove(binding.targetId());
                }
            }
        }

        int nextSlotId = bindings.isEmpty() ? baseSlotCount :
                bindings.getLast().firstSlotId() + bindings.getLast().slotCount();
        for (DynamicItemSlotDefinition definition : remainingDefinitions.values()) {
            UUID bindingId = uniqueId(allocatedBindingIds);
            bindings.add(new DynamicItemSlotBinding(
                    bindingId, definition.targetId(), nextSlotId, definition.slotCount(), true));
            nextSlotId += definition.slotCount();
        }

        long previousEpoch = active == null ? 0 : active.epoch();
        return new DynamicItemSlotManifest(
                previousEpoch,
                previousEpoch + 1,
                UUID.randomUUID(),
                sourceRevision,
                baseSlotCount,
                bindings);
    }

    private static Map<UUID, DynamicItemSlotDefinition> orderedDefinitions(
                                                                           List<DynamicItemSlotDefinition> definitions) {
        Map<UUID, DynamicItemSlotDefinition> ordered = new LinkedHashMap<>();
        for (DynamicItemSlotDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("definitions must not contain null");
            }
            if (ordered.putIfAbsent(definition.targetId(), definition) != null) {
                throw new IllegalArgumentException("duplicate dynamic item slot target: " + definition.targetId());
            }
        }
        return ordered;
    }

    private static UUID uniqueId(Set<UUID> allocatedBindingIds) {
        UUID bindingId;
        do {
            bindingId = UUID.randomUUID();
        } while (!allocatedBindingIds.add(bindingId));
        return bindingId;
    }
}
