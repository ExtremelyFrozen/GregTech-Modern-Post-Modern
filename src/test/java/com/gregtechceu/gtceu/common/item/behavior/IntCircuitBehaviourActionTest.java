package com.gregtechceu.gtceu.common.item.behavior;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class IntCircuitBehaviourActionTest {

    private static final String BATCH = "IntCircuitBehaviourAction";
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_circuit_configuration");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorPreservesBoundaryConfigurationsAndSequences(GameTestHelper helper) {
        assertCreatorAction(helper, 0);
        assertCreatorAction(helper, IntCircuitBehaviour.CIRCUIT_MAX);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorRejectsOutOfRangeConfigurations(GameTestHelper helper) {
        boolean rejectedBelowRange = false;
        boolean rejectedAboveRange = false;
        try {
            IntCircuitBehaviourActions.createSetCircuitConfigurationAction(-1);
        } catch (IllegalArgumentException exception) {
            rejectedBelowRange = true;
        }
        try {
            IntCircuitBehaviourActions.createSetCircuitConfigurationAction(IntCircuitBehaviour.CIRCUIT_MAX + 1);
        } catch (IllegalArgumentException exception) {
            rejectedAboveRange = true;
        }

        helper.assertTrue(rejectedBelowRange, "circuit action creator accepted a negative configuration");
        helper.assertTrue(rejectedAboveRange, "circuit action creator accepted a configuration above the maximum");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherChangesOnlyTheCurrentCircuitStack(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack holder = IntCircuitBehaviour.stack(4);
        ItemStack openedStack = IntCircuitBehaviour.stack(11);

        boolean result = dispatch(player, holder, openedStack,
                IntCircuitBehaviourActions.createSetCircuitConfigurationAction(23));

        helper.assertTrue(result, "valid circuit configuration action was rejected");
        helper.assertTrue(IntCircuitBehaviour.getCircuitConfiguration(holder) == 23,
                "valid circuit configuration action did not update the current holder stack");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND) == holder,
                "valid circuit configuration action replaced the current holder stack");
        helper.assertTrue(IntCircuitBehaviour.getCircuitConfiguration(openedStack) == 11,
                "valid circuit configuration action changed the opened stack snapshot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsInvalidHeldItemContexts(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = IntCircuitBehaviourActions.createSetCircuitConfigurationAction(18);
        ItemStack wrongHolderOpenedStack = IntCircuitBehaviour.stack(2);
        ItemStack missingOpenedHolder = IntCircuitBehaviour.stack(3);
        ItemStack wrongOpenedHolder = IntCircuitBehaviour.stack(5);
        ItemStack wrongHeldItem = new ItemStack(Items.STICK);
        ItemStack wrongOpenedItem = new ItemStack(Items.STICK);

        boolean wrongHolder = dispatch(player, new Object(), wrongHolderOpenedStack, action);
        boolean missingOpenedStack = dispatch(player, missingOpenedHolder, null, action);
        boolean nonCircuitHolder = dispatch(player, wrongHeldItem, IntCircuitBehaviour.stack(5), action);
        boolean nonCircuitOpenedStack = dispatch(player, wrongOpenedHolder, wrongOpenedItem, action);
        boolean matchingNonCircuits = dispatch(player, wrongHeldItem, wrongHeldItem.copy(), action);

        helper.assertTrue(!wrongHolder, "circuit action accepted a non-item holder");
        helper.assertTrue(!missingOpenedStack, "circuit action accepted a missing opened stack");
        helper.assertTrue(!nonCircuitHolder, "circuit action accepted a non-circuit current stack");
        helper.assertTrue(!nonCircuitOpenedStack, "circuit action accepted a non-circuit opened stack");
        helper.assertTrue(!matchingNonCircuits, "circuit action accepted matching non-circuit items");
        helper.assertTrue(IntCircuitBehaviour.getCircuitConfiguration(wrongHolderOpenedStack) == 2,
                "wrong holder action changed the opened circuit snapshot");
        helper.assertTrue(IntCircuitBehaviour.getCircuitConfiguration(missingOpenedHolder) == 3,
                "missing opened stack action changed the holder circuit");
        helper.assertTrue(IntCircuitBehaviour.getCircuitConfiguration(wrongOpenedHolder) == 5,
                "wrong opened item action changed the holder circuit");
        helper.assertTrue(wrongHeldItem.is(Items.STICK) &&
                IntCircuitBehaviour.getCircuitConfiguration(wrongHeldItem) == 0,
                "rejected current item was changed");
        helper.assertTrue(wrongOpenedItem.is(Items.STICK), "rejected opened item was changed");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsSpectatorWithoutChangingCircuit(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack holder = IntCircuitBehaviour.stack(7);
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, holder, holder.copy(),
                    IntCircuitBehaviourActions.createSetCircuitConfigurationAction(20));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "circuit action accepted a spectator");
        helper.assertTrue(IntCircuitBehaviour.getCircuitConfiguration(holder) == 7,
                "spectator action changed the circuit configuration");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMissingAndOutOfRangePayloads(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack holder = IntCircuitBehaviour.stack(9);
        ItemStack openedStack = holder.copy();

        boolean missing = dispatch(player, holder, openedStack, action(DataComponentMap.EMPTY));
        boolean belowRange = dispatch(player, holder, openedStack, action(payload(-1)));
        boolean aboveRange = dispatch(player, holder, openedStack,
                action(payload(IntCircuitBehaviour.CIRCUIT_MAX + 1)));

        helper.assertTrue(!missing, "circuit action accepted a missing configuration payload");
        helper.assertTrue(!belowRange, "circuit action accepted a negative configuration payload");
        helper.assertTrue(!aboveRange, "circuit action accepted a configuration payload above the maximum");
        helper.assertTrue(IntCircuitBehaviour.getCircuitConfiguration(holder) == 9,
                "rejected circuit payload changed the circuit configuration");
        helper.succeed();
    }

    private static void assertCreatorAction(GameTestHelper helper, int configuration) {
        SyncActionData action = IntCircuitBehaviourActions.createSetCircuitConfigurationAction(configuration);
        Integer encodedConfiguration = action.payload().get(GTDataComponents.CIRCUIT_CONFIG.get());

        helper.assertTrue(action.actionId().equals(ACTION_ID), "circuit action creator used the wrong action id");
        helper.assertTrue(action.sequence() == configuration, "circuit action creator used the wrong sequence");
        helper.assertTrue(encodedConfiguration != null && encodedConfiguration == configuration,
                "circuit action creator encoded the wrong configuration");
    }

    private static boolean dispatch(ServerPlayer player, Object holder, ItemStack openedStack,
                                    SyncActionData action) {
        if (holder instanceof ItemStack stack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }
        SyncActionContext context = new SyncActionContext(player, holder, action, null, null,
                InteractionHand.MAIN_HAND, openedStack);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(int configuration) {
        return DataComponentMap.builder()
                .set(GTDataComponents.CIRCUIT_CONFIG.get(), configuration)
                .build();
    }
}
