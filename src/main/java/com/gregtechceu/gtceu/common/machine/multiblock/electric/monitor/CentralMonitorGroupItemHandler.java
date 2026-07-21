package com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.NonMutatingItemCapacity;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Presents one resolved Central Monitor group as its legacy-ordered nine-slot inventory.
 *
 * <p>
 * Offsets {@code 0..7} address the placeholder slots and offset {@code 8} addresses the module slot. Each instance
 * belongs to one resolved group snapshot; dynamic menu resolvers must create a fresh instance whenever they resolve a
 * group UUID so client field synchronization cannot leave a route attached to replaced handlers.
 * </p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class CentralMonitorGroupItemHandler implements IItemHandlerModifiable, NonMutatingItemCapacity {

    public static final int PLACEHOLDER_SLOT_COUNT = 8;
    public static final int MODULE_SLOT_OFFSET = PLACEHOLDER_SLOT_COUNT;
    public static final int SLOT_COUNT = MODULE_SLOT_OFFSET + 1;

    private final CustomItemStackHandler placeholderHandler;
    private final CustomItemStackHandler moduleHandler;

    /**
     * Captures the two handlers of one currently resolved group while preserving their existing menu order.
     */
    public CentralMonitorGroupItemHandler(MonitorGroup group) {
        placeholderHandler = group.getPlaceholderSlotsHandler();
        moduleHandler = group.getItemStackHandler();
        requireSlotCount(placeholderHandler, PLACEHOLDER_SLOT_COUNT, "placeholder");
        requireSlotCount(moduleHandler, 1, "module");
    }

    @Override
    public int getSlots() {
        return SLOT_COUNT;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        CustomItemStackHandler handler = handlerFor(slot);
        return handler.getStackInSlot(localSlot(slot));
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        CustomItemStackHandler handler = handlerFor(slot);
        handler.setStackInSlot(localSlot(slot), stack);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        CustomItemStackHandler handler = handlerFor(slot);
        return handler.insertItem(localSlot(slot), stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        CustomItemStackHandler handler = handlerFor(slot);
        return handler.extractItem(localSlot(slot), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        CustomItemStackHandler handler = handlerFor(slot);
        return handler.getSlotLimit(localSlot(slot));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        CustomItemStackHandler handler = handlerFor(slot);
        return handler.isItemValid(localSlot(slot), stack);
    }

    @Override
    public boolean isNonMutatingEmptySlotCapacityQueryEnabled() {
        return placeholderHandler.isNonMutatingEmptySlotCapacityQueryEnabled() &&
                moduleHandler.isNonMutatingEmptySlotCapacityQueryEnabled();
    }

    @Override
    public int getMaxStackSizeForEmptySlot(int slot, ItemStack stack) {
        CustomItemStackHandler handler = handlerFor(slot);
        return handler.getMaxStackSizeForEmptySlot(localSlot(slot), stack);
    }

    private CustomItemStackHandler handlerFor(int slot) {
        validateSlotIndex(slot);
        return slot < PLACEHOLDER_SLOT_COUNT ? placeholderHandler : moduleHandler;
    }

    private static int localSlot(int slot) {
        return slot < PLACEHOLDER_SLOT_COUNT ? slot : 0;
    }

    private static void requireSlotCount(CustomItemStackHandler handler, int expectedSlots, String role) {
        int actualSlots = handler.getSlots();
        if (actualSlots != expectedSlots) {
            GTCEu.LOGGER.error("Central Monitor group {} handler exposes {} slots instead of {}", role, actualSlots,
                    expectedSlots);
            throw new IllegalArgumentException(
                    "Central Monitor group " + role + " handler must expose " + expectedSlots + " slots");
        }
    }

    private static void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            GTCEu.LOGGER.error("Central Monitor group item handler rejected slot {} outside [0, {})", slot,
                    SLOT_COUNT);
            throw new IllegalArgumentException("Invalid Central Monitor group slot: " + slot);
        }
    }
}
