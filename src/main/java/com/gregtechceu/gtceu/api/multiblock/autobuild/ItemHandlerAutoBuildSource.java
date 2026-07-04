package com.gregtechceu.gtceu.api.multiblock.autobuild;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class ItemHandlerAutoBuildSource implements AutoBuildMaterialSource {

    private final IItemHandler root;
    private final ItemStack excludedContainer;

    ItemHandlerAutoBuildSource(@Nullable IItemHandler root) {
        this(root, ItemStack.EMPTY);
    }

    ItemHandlerAutoBuildSource(@Nullable IItemHandler root, ItemStack excludedContainer) {
        this.root = root;
        this.excludedContainer = excludedContainer;
    }

    @Override
    public Session openSession() {
        return new ItemHandlerSession(root, excludedContainer);
    }

    private static final class ItemHandlerSession implements Session {

        private final IItemHandler root;
        private final List<SlotRef> slots;
        private final List<InsertionHandler> insertionHandlers;
        private final ItemStack excludedContainer;
        private final Map<IItemHandler, Boolean> visitedHandlers = new IdentityHashMap<>();
        private final Map<SlotRef, Integer> reservations = new IdentityHashMap<>();

        private ItemHandlerSession(@Nullable IItemHandler root, ItemStack excludedContainer) {
            this.root = root;
            this.slots = new ArrayList<>();
            this.insertionHandlers = new ArrayList<>();
            this.excludedContainer = excludedContainer;
            collectSlots(root);
        }

        @Override
        public @Nullable Reservation reserve(List<ItemStack> candidates) {
            if (root == null || candidates.isEmpty()) {
                return null;
            }
            for (SlotRef slot : slots) {
                ItemStack stack = slot.handler().getStackInSlot(slot.slot());
                if (stack.isEmpty() || !matches(candidates, stack)) continue;
                int reserved = reservations.getOrDefault(slot, 0);
                if (stack.getCount() <= reserved) continue;
                ItemStack simulated = slot.handler().extractItem(slot.slot(), reserved + 1, true);
                if (simulated.getCount() <= reserved) continue;
                reservations.put(slot, reserved + 1);
                ItemStack reservedStack = stack.copyWithCount(1);
                return new ItemHandlerReservation(slot, reservedStack);
            }
            return null;
        }

        @Override
        public ItemStack insert(ItemStack stack, boolean simulate) {
            if (root == null || stack.isEmpty()) {
                return stack;
            }
            ItemStack remainder = stack.copy();
            for (InsertionHandler handler : insertionHandlers) {
                for (int slot = 0; slot < handler.handler().getSlots() && !remainder.isEmpty(); slot++) {
                    if (handler.shouldSkip(slot)) continue;
                    remainder = handler.handler().insertItem(slot, remainder, simulate);
                }
            }
            return remainder;
        }

        private void collectSlots(@Nullable IItemHandler handler) {
            if (handler == null || visitedHandlers.put(handler, Boolean.TRUE) != null) {
                return;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                if (!excludedContainer.isEmpty() && ItemStack.isSameItemSameComponents(stack, excludedContainer)) {
                    continue;
                }
                IItemHandler child = stack.getCapability(Capabilities.ItemHandler.ITEM);
                if (child != null) {
                    collectSlots(child);
                } else {
                    slots.add(new SlotRef(handler, slot));
                }
            }
            insertionHandlers.add(new InsertionHandler(handler, excludedContainer));
        }

        private static boolean matches(List<ItemStack> candidates, ItemStack stack) {
            for (ItemStack candidate : candidates) {
                if (ItemStack.isSameItemSameComponents(candidate, stack)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record SlotRef(IItemHandler handler, int slot) {}

    private record InsertionHandler(IItemHandler handler, ItemStack excludedContainer) {

        private boolean shouldSkip(int slot) {
            return !excludedContainer.isEmpty() &&
                    ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), excludedContainer);
        }
    }

    private record ItemHandlerReservation(SlotRef slot, ItemStack stack) implements Reservation {

        @Override
        public boolean commit() {
            return !slot.handler().extractItem(slot.slot(), 1, false).isEmpty();
        }
    }
}
