package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemHandlerRoute;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotBinding;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTDynamicItemSlotElementTest {

    private static final String BATCH = "GTDynamicItemSlotElement";
    private static final UUID TARGET_ID = new UUID(0, 1);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void interactionRequiresExplicitActivation(GameTestHelper helper) {
        CustomItemStackHandler handler = new CustomItemStackHandler(1);
        handler.setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
        GTDynamicItemSlotElement element = new GTDynamicItemSlotElement(route(handler), 0);
        Slot slot = element.getSlot();

        assertInteractionState(helper, element, slot, false, "new dynamic slot");

        element.setInteractionEnabled(true);
        assertInteractionState(helper, element, slot, true, "acknowledged dynamic slot");

        element.setInteractionEnabled(false);
        assertInteractionState(helper, element, slot, false, "disabled dynamic slot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void capacityQueryPreservesRoutedHandlerContents(GameTestHelper helper) {
        CustomItemStackHandler handler = new CustomItemStackHandler(1);
        handler.setFilter(stack -> stack.is(Items.DIAMOND));
        handler.setNonMutatingEmptySlotCapacityQueryEnabled(true);
        AtomicInteger changes = new AtomicInteger();
        handler.setOnContentsChanged(changes::incrementAndGet);
        GTDynamicItemSlotElement element = new GTDynamicItemSlotElement(route(handler), 0);
        ItemStack candidate = new ItemStack(Items.DIAMOND, Items.DIAMOND.getDefaultMaxStackSize());

        int capacity = element.getSlot().getMaxStackSize(candidate);

        helper.assertTrue(capacity == Items.DIAMOND.getDefaultMaxStackSize(),
                "dynamic slot changed the handler's accepted empty-slot capacity");
        helper.assertTrue(handler.getStackInSlot(0).isEmpty(),
                "dynamic slot capacity query inserted the candidate into the handler");
        helper.assertTrue(changes.get() == 0,
                "dynamic slot capacity query notified a non-mutating handler change");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void hiddenOrInactiveHierarchyDisablesInteraction(GameTestHelper helper) {
        CustomItemStackHandler handler = new CustomItemStackHandler(1);
        handler.setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
        GTDynamicItemSlotElement element = new GTDynamicItemSlotElement(route(handler), 0);
        UIElement parent = new UIElement();
        parent.addChild(element);
        element.setInteractionEnabled(true);

        element.setVisible(false);
        assertInteractionState(helper, element, element.getSlot(), false, "hidden dynamic slot");
        element.setVisible(true);
        element.setActive(false);
        assertInteractionState(helper, element, element.getSlot(), false, "inactive dynamic slot");
        element.setActive(true);
        parent.setVisible(false);
        assertInteractionState(helper, element, element.getSlot(), false, "dynamic slot under a hidden page");
        parent.setVisible(true);
        parent.setActive(false);
        assertInteractionState(helper, element, element.getSlot(), false, "dynamic slot under an inactive page");
        parent.setActive(true);
        assertInteractionState(helper, element, element.getSlot(), true, "visible acknowledged dynamic slot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidHandlerSlotIndicesAreRejected(GameTestHelper helper) {
        CustomItemStackHandler handler = new CustomItemStackHandler(1);
        DynamicItemHandlerRoute route = route(handler);

        assertIllegalArgument(helper, () -> new GTDynamicItemSlotElement(route, -1),
                "dynamic element accepted a negative handler slot index");
        assertIllegalArgument(helper, () -> new GTDynamicItemSlotElement(route, 1),
                "dynamic element accepted an out-of-range handler slot index");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void bindingIdentityMustMatchTheUuidRoute(GameTestHelper helper) {
        GTDynamicItemSlotElement element = new GTDynamicItemSlotElement(
                route(new CustomItemStackHandler(1)), 0);
        DynamicItemSlotBinding matching = new DynamicItemSlotBinding(
                new UUID(0, 2), TARGET_ID, 0, 1, true);
        DynamicItemSlotBinding wrongTarget = new DynamicItemSlotBinding(
                new UUID(0, 3), new UUID(0, 4), 0, 1, true);
        DynamicItemSlotBinding wrongShape = new DynamicItemSlotBinding(
                new UUID(0, 5), TARGET_ID, 0, 2, true);

        helper.assertTrue(element.matchesBindingRoute(matching, 0),
                "dynamic element rejected its exact manifest route");
        helper.assertTrue(!element.matchesBindingRoute(wrongTarget, 0),
                "dynamic element accepted a route for another target UUID");
        helper.assertTrue(!element.matchesBindingRoute(wrongShape, 0) &&
                !element.matchesBindingRoute(matching, 1),
                "dynamic element accepted a mismatched route shape or offset");
        helper.succeed();
    }

    private static void assertInteractionState(GameTestHelper helper, GTDynamicItemSlotElement element, Slot slot,
                                               boolean expected, String phase) {
        helper.assertTrue(element.isInteractionEnabled() == expected,
                phase + " reported the wrong interaction state");
        helper.assertTrue(slot.isActive() == expected,
                phase + " exposed the wrong Vanilla active state");
        helper.assertTrue(slot.mayPlace(Items.DIAMOND.getDefaultInstance()) == expected,
                phase + " exposed the wrong placement permission");
        helper.assertTrue(slot.mayPickup(helper.makeMockPlayer()) == expected,
                phase + " exposed the wrong pickup permission");
    }

    private static DynamicItemHandlerRoute route(CustomItemStackHandler handler) {
        handler.setNonMutatingEmptySlotCapacityQueryEnabled(true);
        return new DynamicItemHandlerRoute(TARGET_ID, handler.getSlots(), ignored -> handler);
    }

    private static void assertIllegalArgument(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank(),
                    message + ": rejection did not explain the invalid index");
            return;
        }
        throw new GameTestAssertException(message);
    }
}
