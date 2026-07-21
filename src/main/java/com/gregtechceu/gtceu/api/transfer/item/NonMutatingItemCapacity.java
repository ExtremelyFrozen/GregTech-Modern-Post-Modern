package com.gregtechceu.gtceu.api.transfer.item;

import net.minecraft.world.item.ItemStack;

/**
 * Calculates an item's empty-slot capacity without removing or restoring the slot's current occupant.
 */
public interface NonMutatingItemCapacity {

    /**
     * Returns whether this handler currently supports the non-mutating capacity calculation.
     */
    boolean isNonMutatingEmptySlotCapacityQueryEnabled();

    /**
     * Returns the amount accepted by an empty slot without changing the handler or firing change callbacks.
     */
    int getMaxStackSizeForEmptySlot(int slot, ItemStack stack);
}
