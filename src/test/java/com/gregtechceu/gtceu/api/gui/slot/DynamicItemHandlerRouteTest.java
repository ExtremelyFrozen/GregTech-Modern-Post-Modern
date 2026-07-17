package com.gregtechceu.gtceu.api.gui.slot;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotElement;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DynamicItemHandlerRouteTest {

    private static final String BATCH = "DynamicItemHandlerRoute";
    private static final UUID TARGET_ID = new UUID(0, 1);
    private static final UUID TARGET_INCARNATION = new UUID(0, 2);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void routeFollowsTheCurrentHandlerForItsTarget(GameTestHelper helper) {
        Map<UUID, IItemHandlerModifiable> handlers = new HashMap<>();
        LimitedItemHandler first = new LimitedItemHandler(2, 4, Items.IRON_INGOT);
        LimitedItemHandler replacement = new LimitedItemHandler(2, 7, Items.GOLD_INGOT);
        first.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        replacement.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 3));
        handlers.put(TARGET_ID, first);
        DynamicItemHandlerRoute route = route(2, handlers);

        helper.assertTrue(route.isResolved(), "route did not resolve its initial target handler");
        helper.assertTrue(route.getSlots() == 2, "route changed its fixed logical slot count");
        assertStack(helper, route.getStackInSlot(0), Items.IRON_INGOT, 2,
                "route did not read the initial target handler");
        helper.assertTrue(route.getSlotLimit(0) == 4, "route did not delegate the initial slot limit");
        helper.assertTrue(route.isItemValid(1, Items.IRON_INGOT.getDefaultInstance()),
                "route did not delegate initial item validation");

        handlers.put(TARGET_ID, replacement);

        helper.assertTrue(route.isResolved(), "route did not resolve its replacement target handler");
        assertStack(helper, route.getStackInSlot(0), Items.GOLD_INGOT, 3,
                "route retained the replaced target handler");
        helper.assertTrue(route.getSlotLimit(0) == 7, "route retained the replaced slot limit");
        helper.assertTrue(route.isItemValid(1, Items.GOLD_INGOT.getDefaultInstance()) &&
                !route.isItemValid(1, Items.IRON_INGOT.getDefaultInstance()),
                "route retained the replaced item validation");

        route.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 2));
        ItemStack remainder = route.insertItem(1, new ItemStack(Items.GOLD_INGOT, 6), false);
        assertStack(helper, replacement.getStackInSlot(1), Items.GOLD_INGOT, 7,
                "route did not delegate set and insertion to the replacement");
        assertStack(helper, remainder, Items.GOLD_INGOT, 1,
                "route changed the replacement insertion remainder");
        ItemStack extracted = route.extractItem(1, 3, false);
        assertStack(helper, extracted, Items.GOLD_INGOT, 3,
                "route did not delegate extraction to the replacement");
        helper.assertTrue(first.getStackInSlot(1).isEmpty(), "route mutated its former target handler");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void missingAndUndersizedTargetsRejectOperations(GameTestHelper helper) {
        Map<UUID, IItemHandlerModifiable> handlers = new HashMap<>();
        DynamicItemHandlerRoute route = route(2, handlers);
        ItemStack offered = new ItemStack(Items.DIAMOND, 3);

        helper.assertTrue(!route.isResolved(), "missing target was reported as resolved");
        helper.assertTrue(route.getStackInSlot(0).isEmpty(), "missing target exposed an item");
        helper.assertTrue(route.insertItem(0, offered, false) == offered,
                "missing target did not return the offered stack unchanged");
        helper.assertTrue(route.extractItem(0, 1, false).isEmpty(), "missing target extracted an item");
        helper.assertTrue(route.getSlotLimit(0) == 0, "missing target exposed a slot limit");
        helper.assertTrue(!route.isItemValid(0, offered), "missing target accepted an item");
        route.setStackInSlot(0, ItemStack.EMPTY);
        route.setStackInSlot(0, offered);

        CustomItemStackHandler undersized = new CustomItemStackHandler(1);
        undersized.setNonMutatingEmptySlotCapacityQueryEnabled(true);
        undersized.setStackInSlot(0, new ItemStack(Items.EMERALD, 2));
        handlers.put(TARGET_ID, undersized);

        helper.assertTrue(!route.isResolved(), "undersized target was reported as resolved");
        helper.assertTrue(route.getStackInSlot(0).isEmpty(), "undersized target exposed an item");
        helper.assertTrue(route.insertItem(0, offered, false) == offered,
                "undersized target accepted an insertion");
        helper.assertTrue(route.extractItem(0, 1, false).isEmpty(), "undersized target allowed extraction");
        helper.assertTrue(route.getSlotLimit(0) == 0 && !route.isItemValid(0, offered),
                "undersized target exposed slot capabilities");
        route.setStackInSlot(0, offered);
        assertStack(helper, undersized.getStackInSlot(0), Items.EMERALD, 2,
                "undersized target was mutated through the route");

        CustomItemStackHandler replacement = new CustomItemStackHandler(2);
        replacement.setNonMutatingEmptySlotCapacityQueryEnabled(true);
        handlers.put(TARGET_ID, replacement);
        helper.assertTrue(route.isResolved(), "route did not recover after receiving a correctly shaped replacement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void routeRejectsAReplacementTargetIncarnationImmediately(GameTestHelper helper) {
        LimitedItemHandler oldHandler = new LimitedItemHandler(1, 4, Items.IRON_INGOT);
        LimitedItemHandler newHandler = new LimitedItemHandler(1, 7, Items.GOLD_INGOT);
        oldHandler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        newHandler.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 3));
        UUID newIncarnation = new UUID(0, 3);
        AtomicReference<TargetLifecycle> current = new AtomicReference<>(
                new TargetLifecycle(TARGET_INCARNATION, oldHandler));
        DynamicItemHandlerRoute oldRoute = new DynamicItemHandlerRoute(
                TARGET_ID, TARGET_INCARNATION, 1,
                (targetId, targetIncarnation) -> resolveLifecycle(current.get(), targetId, targetIncarnation));

        current.set(new TargetLifecycle(newIncarnation, newHandler));
        ItemStack offered = new ItemStack(Items.GOLD_INGOT, 2);
        helper.assertTrue(!oldRoute.isResolved() && oldRoute.getStackInSlot(0).isEmpty(),
                "old route resolved a replacement target incarnation");
        helper.assertTrue(oldRoute.insertItem(0, offered, false) == offered &&
                oldRoute.extractItem(0, 1, false).isEmpty(),
                "old route inserted into or extracted from a replacement target incarnation");
        oldRoute.setStackInSlot(0, offered);
        assertStack(helper, newHandler.getStackInSlot(0), Items.GOLD_INGOT, 3,
                "old route wrote into a replacement target incarnation");

        DynamicItemHandlerRoute newRoute = new DynamicItemHandlerRoute(
                TARGET_ID, newIncarnation, 1,
                (targetId, targetIncarnation) -> resolveLifecycle(current.get(), targetId, targetIncarnation));
        helper.assertTrue(newRoute.isResolved(), "new route did not resolve its matching target incarnation");
        newRoute.setStackInSlot(0, offered);
        assertStack(helper, newHandler.getStackInSlot(0), Items.GOLD_INGOT, 2,
                "new route did not write through its matching target incarnation");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidLogicalSlotsFailFast(GameTestHelper helper) {
        DynamicItemHandlerRoute route = new DynamicItemHandlerRoute(
                TARGET_ID, TARGET_INCARNATION, 2,
                (ignoredTarget, ignoredIncarnation) -> new CustomItemStackHandler(2));

        assertIllegalArgument(helper, () -> route.getStackInSlot(-1),
                "route accepted a negative logical slot");
        assertIllegalArgument(helper, () -> route.insertItem(2, ItemStack.EMPTY, true),
                "route accepted a logical slot at its upper bound");
        assertIllegalArgument(helper, () -> new DynamicItemHandlerRoute(
                TARGET_ID, TARGET_INCARNATION, 0, (ignoredTarget, ignoredIncarnation) -> null),
                "route accepted zero logical slots");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void capacityQueriesRemainNonMutating(GameTestHelper helper) {
        CustomItemStackHandler handler = new CustomItemStackHandler(1);
        handler.setFilter(stack -> stack.is(Items.DIAMOND));
        handler.setNonMutatingEmptySlotCapacityQueryEnabled(true);
        ItemStack candidate = new ItemStack(Items.DIAMOND, Items.DIAMOND.getDefaultMaxStackSize());
        handler.setStackInSlot(0, candidate.copy());
        AtomicInteger changes = new AtomicInteger();
        handler.setOnContentsChanged(changes::incrementAndGet);
        Map<UUID, IItemHandlerModifiable> handlers = new HashMap<>();
        handlers.put(TARGET_ID, handler);
        DynamicItemHandlerRoute route = route(1, handlers);

        int expectedCapacity = handler.getMaxStackSizeForEmptySlot(0, candidate);
        int directCapacity = route.getMaxStackSizeForEmptySlot(0, candidate);
        int slotCapacity = new GTDynamicItemSlotElement(route, 0).getSlot().getMaxStackSize(candidate);

        helper.assertTrue(route.isNonMutatingEmptySlotCapacityQueryEnabled(),
                "route did not advertise its non-mutating capacity path");
        helper.assertTrue(expectedCapacity == directCapacity && expectedCapacity == slotCapacity,
                "route changed the handler's empty-slot capacity");
        helper.assertTrue(ItemStack.isSameItemSameComponents(handler.getStackInSlot(0), candidate) &&
                changes.get() == 0,
                "capacity query mutated the routed handler");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void unsafeCapacityTargetsRemainUnresolved(GameTestHelper helper) {
        CustomItemStackHandler handler = new CustomItemStackHandler(1);
        ItemStack occupant = new ItemStack(Items.EMERALD, 2);
        handler.setStackInSlot(0, occupant.copy());
        AtomicInteger changes = new AtomicInteger();
        handler.setOnContentsChanged(changes::incrementAndGet);
        Map<UUID, IItemHandlerModifiable> handlers = new HashMap<>();
        handlers.put(TARGET_ID, handler);
        DynamicItemHandlerRoute route = route(1, handlers);
        GTDynamicItemSlotElement element = new GTDynamicItemSlotElement(route, 0);

        helper.assertTrue(!route.isResolved(), "route accepted a handler without safe empty-slot capacity queries");
        helper.assertTrue(element.getSlot().getMaxStackSize(Items.DIAMOND.getDefaultInstance()) == 0,
                "unresolved route exposed a mutable fallback capacity");
        helper.assertTrue(ItemStack.isSameItemSameComponents(handler.getStackInSlot(0), occupant) &&
                handler.getStackInSlot(0).getCount() == occupant.getCount() &&
                changes.get() == 0,
                "unresolved capacity query cleared or restored the target handler");

        handler.setNonMutatingEmptySlotCapacityQueryEnabled(true);
        helper.assertTrue(route.isResolved(), "route did not recover after safe capacity queries were enabled");
        helper.succeed();
    }

    private static void assertStack(GameTestHelper helper, ItemStack stack, Item item, int count, String message) {
        helper.assertTrue(stack.is(item) && stack.getCount() == count, message);
    }

    private static DynamicItemHandlerRoute route(int logicalSlotCount,
                                                 Map<UUID, IItemHandlerModifiable> handlers) {
        return new DynamicItemHandlerRoute(
                TARGET_ID, TARGET_INCARNATION, logicalSlotCount,
                (targetId, targetIncarnation) -> TARGET_INCARNATION.equals(targetIncarnation) ?
                        handlers.get(targetId) : null);
    }

    private static IItemHandlerModifiable resolveLifecycle(TargetLifecycle lifecycle, UUID targetId,
                                                           UUID targetIncarnation) {
        return TARGET_ID.equals(targetId) && lifecycle.incarnation().equals(targetIncarnation) ?
                lifecycle.handler() : null;
    }

    private static void assertIllegalArgument(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank(),
                    message + ": exception did not describe the invalid route");
            return;
        }
        throw new GameTestAssertException(message);
    }

    private static final class LimitedItemHandler extends CustomItemStackHandler {

        private final int slotLimit;
        private final Item acceptedItem;

        private LimitedItemHandler(int slots, int slotLimit, Item acceptedItem) {
            super(slots);
            this.slotLimit = slotLimit;
            this.acceptedItem = acceptedItem;
            setNonMutatingEmptySlotCapacityQueryEnabled(true);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slotLimit;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(acceptedItem);
        }
    }

    private record TargetLifecycle(UUID incarnation, IItemHandlerModifiable handler) {}
}
