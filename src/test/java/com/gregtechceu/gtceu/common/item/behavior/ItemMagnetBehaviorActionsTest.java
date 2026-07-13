package com.gregtechceu.gtceu.common.item.behavior;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.behavior.ItemMagnetBehavior.Filter;
import com.gregtechceu.gtceu.common.item.behavior.ItemMagnetBehavior.MagnetComponent;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ItemMagnetBehaviorActionsTest {

    private static final String BATCH = "ItemMagnetBehaviorActions";
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_magnet_filter");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorCopiesActiveAndUsesFilterOrdinal(GameTestHelper helper) {
        assertCreatedAction(helper, magnet(true, Filter.SIMPLE), Filter.TAG, true);
        assertCreatedAction(helper, magnet(false, Filter.TAG), Filter.SIMPLE, false);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorRejectsMissingMagnetComponent(GameTestHelper helper) {
        ItemStack magnet = GTItems.ITEM_MAGNET_LV.asStack();
        magnet.remove(GTDataComponents.MAGNET);

        try {
            ItemMagnetBehaviorActions.createSetMagnetFilterAction(magnet, Filter.TAG);
        } catch (IllegalStateException exception) {
            helper.succeed();
            return;
        }
        throw new GameTestAssertException("magnet action creator accepted a stack without its magnet component");
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAppliesBothFiltersWithoutTrustingPayloadActive(GameTestHelper helper) {
        ItemStack activeHolder = magnet(true, Filter.SIMPLE);

        helper.assertTrue(dispatch(helper, activeHolder, activeHolder.copy(), rawAction(false, Filter.TAG)),
                "valid TAG magnet action was rejected");
        assertComponent(helper, activeHolder, true, Filter.TAG,
                "TAG action trusted the payload's false active state");

        ItemStack inactiveHolder = magnet(false, Filter.TAG);
        helper.assertTrue(dispatch(helper, inactiveHolder, inactiveHolder.copy(), rawAction(true, Filter.SIMPLE)),
                "valid SIMPLE magnet action was rejected");
        assertComponent(helper, inactiveHolder, false, Filter.SIMPLE,
                "SIMPLE action trusted the payload's true active state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHolderOpenedStackAndItem(GameTestHelper helper) {
        SyncActionData action = rawAction(false, Filter.TAG);
        ItemStack openedMagnet = magnet(true, Filter.SIMPLE);
        ItemStack openedBefore = openedMagnet.copy();

        helper.assertTrue(!dispatch(helper, new Object(), openedMagnet, action),
                "magnet action accepted a non-item holder");
        helper.assertTrue(ItemStack.matches(openedBefore, openedMagnet),
                "rejected non-item holder action changed the opened magnet");

        ItemStack validMagnet = magnet(true, Filter.SIMPLE);
        assertRejected(helper, validMagnet, null, action, "magnet action without an opened stack");

        ItemStack differentMagnet = GTItems.ITEM_MAGNET_HV.asStack();
        differentMagnet.set(GTDataComponents.MAGNET, new MagnetComponent(true, Filter.SIMPLE));
        assertRejected(helper, validMagnet, differentMagnet, action,
                "magnet action with a different magnet item");

        ItemStack nonMagnet = GTItems.TOOL_DATA_STICK.asStack();
        nonMagnet.set(GTDataComponents.MAGNET, new MagnetComponent(true, Filter.SIMPLE));
        assertRejected(helper, nonMagnet, nonMagnet.copy(), action,
                "magnet action for a non-magnet holder");

        ItemStack holderWithWrongOpenedItem = magnet(true, Filter.SIMPLE);
        assertRejected(helper, holderWithWrongOpenedItem, GTItems.TOOL_DATA_STICK.asStack(), action,
                "magnet action with a non-magnet opened item");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack holder = magnet(true, Filter.SIMPLE);
        ItemStack before = holder.copy();
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, holder, holder.copy(), rawAction(false, Filter.TAG));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "magnet action accepted a spectator");
        helper.assertTrue(ItemStack.matches(before, holder), "spectator magnet action changed the holder stack");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMissingAndNullFilterPayload(GameTestHelper helper) {
        ItemStack missingPayloadHolder = magnet(true, Filter.SIMPLE);
        assertRejected(helper, missingPayloadHolder, missingPayloadHolder.copy(),
                new SyncActionData(ACTION_ID, 0, DataComponentMap.EMPTY),
                "magnet action without a payload");

        ItemStack nullFilterHolder = magnet(true, Filter.SIMPLE);
        assertRejected(helper, nullFilterHolder, nullFilterHolder.copy(), rawAction(false, null),
                "magnet action with a null filter");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsHolderWithoutCurrentMagnetComponent(GameTestHelper helper) {
        ItemStack holder = GTItems.ITEM_MAGNET_LV.asStack();
        holder.remove(GTDataComponents.MAGNET);

        assertRejected(helper, holder, holder.copy(), rawAction(false, Filter.TAG),
                "magnet action for a holder without its current magnet component");
        helper.succeed();
    }

    private static void assertCreatedAction(GameTestHelper helper, ItemStack stack, Filter filter,
                                            boolean expectedActive) {
        SyncActionData action = ItemMagnetBehaviorActions.createSetMagnetFilterAction(stack, filter);
        MagnetComponent payload = action.payload().get(GTDataComponents.MAGNET.get());

        helper.assertTrue(action.actionId().equals(ACTION_ID), "magnet creator encoded the wrong action id");
        helper.assertTrue(action.sequence() == filter.ordinal(),
                "magnet creator did not encode the filter ordinal as its sequence");
        helper.assertTrue(payload != null && payload.active() == expectedActive && payload.filterType() == filter,
                "magnet creator did not copy active or encode the requested filter");
    }

    private static void assertRejected(GameTestHelper helper, ItemStack holder, ItemStack openedStack,
                                       SyncActionData action, String description) {
        ItemStack holderBefore = holder.copy();
        ItemStack openedBefore = openedStack == null ? null : openedStack.copy();

        helper.assertTrue(!dispatch(helper, holder, openedStack, action), description + " was accepted");
        helper.assertTrue(ItemStack.matches(holderBefore, holder), description + " changed the holder stack");
        if (openedBefore != null) {
            helper.assertTrue(ItemStack.matches(openedBefore, openedStack),
                    description + " changed the opened stack");
        }
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, ItemStack openedStack,
                                    SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, openedStack, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, ItemStack openedStack,
                                    SyncActionData action) {
        ItemMagnetBehaviorActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, null, null,
                InteractionHand.MAIN_HAND, openedStack);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData rawAction(boolean active, Filter filter) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.MAGNET.get(), new MagnetComponent(active, filter))
                .build();
        int sequence = filter == null ? -1 : filter.ordinal();
        return new SyncActionData(ACTION_ID, sequence, payload);
    }

    private static ItemStack magnet(boolean active, Filter filter) {
        ItemStack stack = GTItems.ITEM_MAGNET_LV.asStack();
        stack.set(GTDataComponents.MAGNET, new MagnetComponent(active, filter));
        return stack;
    }

    private static void assertComponent(GameTestHelper helper, ItemStack stack, boolean active, Filter filter,
                                        String failureMessage) {
        MagnetComponent component = stack.get(GTDataComponents.MAGNET);
        helper.assertTrue(component != null && component.active() == active && component.filterType() == filter,
                failureMessage);
    }
}
