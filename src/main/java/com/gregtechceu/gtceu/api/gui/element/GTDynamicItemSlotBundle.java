package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotBinding;

import java.util.List;

/**
 * Keeps one immutable binding range paired with the LDLib2 elements registered at those exact Vanilla slot ids.
 *
 * @param binding  lifecycle identity and reserved slot range
 * @param elements dynamic slot elements in ascending slot-id order
 */
public record GTDynamicItemSlotBundle(DynamicItemSlotBinding binding,
                                      List<GTDynamicItemSlotElement> elements) {

    public GTDynamicItemSlotBundle {
        if (binding == null) {
            throw new IllegalArgumentException("binding must not be null");
        }
        if (elements == null) {
            throw new IllegalArgumentException("elements must not be null");
        }
        if (elements.size() != binding.slotCount()) {
            throw new IllegalArgumentException(
                    "binding " + binding.bindingId() + " requires " + binding.slotCount() +
                            " elements but received " + elements.size());
        }
        if (elements.stream().anyMatch(element -> element == null)) {
            throw new IllegalArgumentException("dynamic slot bundle must not contain null elements");
        }
        elements = List.copyOf(elements);
    }

    /** Enables or disables every physical slot reserved by this binding. */
    public void setInteractionEnabled(boolean interactionEnabled) {
        elements.forEach(element -> element.setInteractionEnabled(interactionEnabled));
    }

    /** Verifies that a later manifest retained this bundle's immutable lifecycle identity and range. */
    public boolean matchesIdentity(DynamicItemSlotBinding candidate) {
        return binding.bindingId().equals(candidate.bindingId()) && binding.targetId().equals(candidate.targetId()) &&
                binding.targetIncarnation().equals(candidate.targetIncarnation()) &&
                binding.firstSlotId() == candidate.firstSlotId() && binding.slotCount() == candidate.slotCount();
    }
}
