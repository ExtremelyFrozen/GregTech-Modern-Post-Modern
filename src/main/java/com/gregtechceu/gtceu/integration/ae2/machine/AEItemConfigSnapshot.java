package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable client view of one ME item input bus configuration and its displayed network stock.
 *
 * <p>
 * The snapshot keeps AE keys so item components and stock values above the integer range remain lossless. Every
 * construction copies the stack wrappers and enforces the fixed sixteen-slot page contract.
 *
 * @param online   whether the connected ME network is currently usable
 * @param stocking whether this snapshot belongs to the stocking bus variant
 * @param autoPull whether automatic configuration currently owns all slots
 * @param slots    exactly sixteen immutable config/stock pairs
 */
public record AEItemConfigSnapshot(boolean online, boolean stocking, boolean autoPull, List<Slot> slots) {

    /**
     * Number of item configuration slots exposed by both ME input bus definitions.
     */
    public static final int SLOT_COUNT = 16;

    /**
     * Validates and isolates one complete snapshot from mutable source collections and stack wrappers.
     */
    public AEItemConfigSnapshot {
        if (slots.size() != SLOT_COUNT) {
            throw new IllegalArgumentException("ME item configuration snapshot must contain exactly " +
                    SLOT_COUNT + " slots, but contained " + slots.size() + '.');
        }

        List<Slot> copiedSlots = new ArrayList<>(SLOT_COUNT);
        for (int index = 0; index < SLOT_COUNT; index++) {
            Slot slot = slots.get(index);
            copiedSlots.add(new Slot(slot.config(), slot.stock()));
        }
        slots = List.copyOf(copiedSlots);
    }

    /**
     * Creates the initial empty page state before the server publishes live slot values.
     */
    public static AEItemConfigSnapshot empty(boolean online, boolean stocking, boolean autoPull) {
        List<Slot> slots = new ArrayList<>(SLOT_COUNT);
        for (int index = 0; index < SLOT_COUNT; index++) {
            slots.add(new Slot(null, null));
        }
        return new AEItemConfigSnapshot(online, stocking, autoPull, slots);
    }

    /**
     * Captures the authoritative export-only item slots without exposing their mutable wrappers to the client view.
     */
    public static AEItemConfigSnapshot capture(boolean online, boolean stocking, boolean autoPull,
                                               ExportOnlyAEItemSlot[] inventory) {
        if (inventory.length != SLOT_COUNT) {
            throw new IllegalArgumentException("ME item configuration inventory must contain exactly " +
                    SLOT_COUNT + " slots, but contained " + inventory.length + '.');
        }
        List<Slot> slots = new ArrayList<>(SLOT_COUNT);
        for (ExportOnlyAEItemSlot slot : inventory) {
            slots.add(new Slot(slot.getConfig(), slot.getStock()));
        }
        return new AEItemConfigSnapshot(online, stocking, autoPull, slots);
    }

    /**
     * Immutable config/stock pair for one visual column in the item bus page.
     *
     * @param config optional requested item with an integer-range target amount
     * @param stock  optional displayed network stock with a positive long amount
     */
    public record Slot(@Nullable GenericStack config, @Nullable GenericStack stock) {

        /**
         * Copies and validates both optional item stacks at the snapshot boundary.
         */
        public Slot {
            config = copyItemStack(config, "config", true);
            stock = copyItemStack(stock, "stock", false);
        }

        private static @Nullable GenericStack copyItemStack(@Nullable GenericStack stack, String field,
                                                            boolean boundedAmount) {
            if (stack == null) {
                return null;
            }
            if (!(stack.what() instanceof AEItemKey)) {
                throw new IllegalArgumentException("ME item configuration " + field + " key must be an AE item.");
            }
            if (stack.amount() <= 0) {
                throw new IllegalArgumentException("ME item configuration " + field + " amount must be positive.");
            }
            if (boundedAmount && stack.amount() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("ME item configuration config amount exceeds the integer range.");
            }
            return new GenericStack(stack.what(), stack.amount());
        }
    }
}
