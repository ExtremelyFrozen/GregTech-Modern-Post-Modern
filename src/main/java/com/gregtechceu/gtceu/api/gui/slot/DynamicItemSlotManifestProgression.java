package com.gregtechceu.gtceu.api.gui.slot;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Verifies that a new cumulative manifest only appends ranges or turns an existing binding into a tombstone.
 */
final class DynamicItemSlotManifestProgression {

    private DynamicItemSlotManifestProgression() {}

    static boolean isValid(@Nullable DynamicItemSlotManifest activeManifest,
                           DynamicItemSlotManifest nextManifest, int baseSlotCount) {
        if (nextManifest.baseSlotCount() != baseSlotCount) {
            return false;
        }
        long activeEpoch = activeManifest == null ? 0 : activeManifest.epoch();
        if (nextManifest.previousEpoch() != activeEpoch) {
            return false;
        }
        if (activeManifest != null && nextManifest.sourceRevision() < activeManifest.sourceRevision()) {
            return false;
        }
        if (activeManifest != null && nextManifest.manifestNonce().equals(activeManifest.manifestNonce())) {
            return false;
        }

        List<DynamicItemSlotBinding> previousBindings = activeManifest == null ?
                List.of() : activeManifest.bindings();
        List<DynamicItemSlotBinding> nextBindings = nextManifest.bindings();
        if (nextBindings.size() < previousBindings.size()) {
            return false;
        }
        for (int index = 0; index < previousBindings.size(); index++) {
            DynamicItemSlotBinding previous = previousBindings.get(index);
            DynamicItemSlotBinding next = nextBindings.get(index);
            if (!hasStableIdentity(previous, next) || previous.tombstone() && next.present()) {
                return false;
            }
        }
        for (int index = previousBindings.size(); index < nextBindings.size(); index++) {
            if (nextBindings.get(index).tombstone()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasStableIdentity(DynamicItemSlotBinding previous, DynamicItemSlotBinding next) {
        return previous.bindingId().equals(next.bindingId()) && previous.targetId().equals(next.targetId()) &&
                previous.targetIncarnation().equals(next.targetIncarnation()) &&
                previous.firstSlotId() == next.firstSlotId() && previous.slotCount() == next.slotCount();
    }
}
