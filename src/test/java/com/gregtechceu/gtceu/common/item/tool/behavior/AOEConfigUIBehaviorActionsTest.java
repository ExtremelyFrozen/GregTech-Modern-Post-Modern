package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.item.datacomponents.AoESymmetrical;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMaterials;

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
public class AOEConfigUIBehaviorActionsTest {

    private static final String BATCH = "AOEConfigUIBehaviorActions";
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_tool_aoe");
    private static final AoESymmetrical INITIAL_DEFINITION = new AoESymmetrical(2, 3, 4, 1, 1, 1);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorEncodesAOEProtocol(GameTestHelper helper) {
        AoESymmetrical requested = new AoESymmetrical(2, 3, 4, 0, 3, 4);
        SyncActionData action = AOEConfigUIBehaviorActions.createSetToolAOEAction(requested);

        helper.assertTrue(action.actionId().equals(ACTION_ID), "AOE creator encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "AOE creator changed the required zero sequence");
        helper.assertTrue(requested.equals(action.payload().get(GTDataComponents.AOE.get())),
                "AOE creator omitted or changed its data component payload");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAppliesValidAOEDefinition(GameTestHelper helper) {
        ItemStack holder = configurableTool(INITIAL_DEFINITION);
        ItemStack openedStack = holder.copy();
        AoESymmetrical requested = new AoESymmetrical(2, 3, 4, 2, 0, 4);

        helper.assertTrue(dispatch(helper, holder, openedStack,
                AOEConfigUIBehaviorActions.createSetToolAOEAction(requested)),
                "valid AOE configuration action was rejected");
        helper.assertTrue(requested.equals(holder.get(GTDataComponents.AOE)),
                "valid AOE configuration action did not update the tool component");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAcceptsEveryDimensionBoundary(GameTestHelper helper) {
        AoESymmetrical[] boundaries = {
                new AoESymmetrical(2, 3, 4, 0, 1, 1),
                new AoESymmetrical(2, 3, 4, 2, 1, 1),
                new AoESymmetrical(2, 3, 4, 1, 0, 1),
                new AoESymmetrical(2, 3, 4, 1, 3, 1),
                new AoESymmetrical(2, 3, 4, 1, 1, 0),
                new AoESymmetrical(2, 3, 4, 1, 1, 4),
        };
        ItemStack holder = configurableTool(INITIAL_DEFINITION);
        ItemStack openedStack = holder.copy();

        for (AoESymmetrical boundary : boundaries) {
            helper.assertTrue(dispatch(helper, holder, openedStack,
                    AOEConfigUIBehaviorActions.createSetToolAOEAction(boundary)),
                    "valid AOE dimension boundary was rejected: " + boundary);
            helper.assertTrue(boundary.equals(holder.get(GTDataComponents.AOE)),
                    "AOE dimension boundary was not applied: " + boundary);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorAndDispatcherRejectZeroAndOutOfRangeDefinitions(GameTestHelper helper) {
        AoESymmetrical[] invalidDefinitions = {
                AoESymmetrical.ZERO,
                new AoESymmetrical(-1, 3, 4, 0, 1, 1),
                new AoESymmetrical(2, -1, 4, 1, 0, 1),
                new AoESymmetrical(2, 3, -1, 1, 1, 0),
                new AoESymmetrical(2, 3, 4, -1, 1, 1),
                new AoESymmetrical(2, 3, 4, 3, 1, 1),
                new AoESymmetrical(2, 3, 4, 1, -1, 1),
                new AoESymmetrical(2, 3, 4, 1, 4, 1),
                new AoESymmetrical(2, 3, 4, 1, 1, -1),
                new AoESymmetrical(2, 3, 4, 1, 1, 5),
        };

        for (AoESymmetrical invalid : invalidDefinitions) {
            assertCreatorRejected(invalid);
            ItemStack holder = configurableTool(INITIAL_DEFINITION);
            assertRejected(helper, holder, holder.copy(), rawAction(invalid),
                    "invalid AOE definition " + invalid);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsEveryMaximumMismatch(GameTestHelper helper) {
        AoESymmetrical[] mismatchedDefinitions = {
                new AoESymmetrical(1, 3, 4, 1, 1, 1),
                new AoESymmetrical(2, 2, 4, 1, 1, 1),
                new AoESymmetrical(2, 3, 5, 1, 1, 1),
        };

        for (AoESymmetrical mismatched : mismatchedDefinitions) {
            ItemStack holder = configurableTool(INITIAL_DEFINITION);
            assertRejected(helper, holder, holder.copy(),
                    AOEConfigUIBehaviorActions.createSetToolAOEAction(mismatched),
                    "AOE definition with mismatched maximum " + mismatched);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsInvalidCurrentDefinition(GameTestHelper helper) {
        ItemStack holder = configurableTool(new AoESymmetrical(2, 3, 4, 3, 1, 1));

        assertRejected(helper, holder, holder.copy(),
                AOEConfigUIBehaviorActions.createSetToolAOEAction(INITIAL_DEFINITION),
                "valid request for a tool with invalid current AOE state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHolderOpenedStackAndItem(GameTestHelper helper) {
        SyncActionData action = AOEConfigUIBehaviorActions.createSetToolAOEAction(INITIAL_DEFINITION);
        ItemStack validTool = configurableTool(INITIAL_DEFINITION);
        ItemStack openedStack = validTool.copy();
        ItemStack openedBefore = openedStack.copy();

        helper.assertTrue(!dispatch(helper, new Object(), openedStack, action),
                "AOE action accepted a non-item holder");
        helper.assertTrue(ItemStack.matches(openedBefore, openedStack),
                "rejected non-item holder action changed the opened stack");

        assertRejected(helper, validTool, null, action, "AOE action without an opened stack");

        ItemStack mismatchedTool = ToolHelper.get(GTToolType.SPADE, GTMaterials.Steel);
        mismatchedTool.set(GTDataComponents.AOE, INITIAL_DEFINITION);
        assertRejected(helper, validTool, mismatchedTool, action, "AOE action with a different opened item");

        ItemStack nonAOETool = ToolHelper.get(GTToolType.PICKAXE, GTMaterials.Steel);
        nonAOETool.set(GTDataComponents.AOE, INITIAL_DEFINITION);
        assertRejected(helper, nonAOETool, nonAOETool.copy(), action,
                "AOE action for an item without AOEConfigUIBehavior");

        ItemStack zeroHolder = configurableTool(AoESymmetrical.ZERO);
        assertRejected(helper, zeroHolder, configurableTool(INITIAL_DEFINITION), action,
                "AOE action for a non-configurable current stack");

        ItemStack holderWithZeroOpenedStack = configurableTool(INITIAL_DEFINITION);
        assertRejected(helper, holderWithZeroOpenedStack, configurableTool(AoESymmetrical.ZERO), action,
                "AOE action for a non-configurable opened stack");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack holder = configurableTool(INITIAL_DEFINITION);
        ItemStack before = holder.copy();
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, holder, holder.copy(),
                    AOEConfigUIBehaviorActions.createSetToolAOEAction(
                            new AoESymmetrical(2, 3, 4, 2, 3, 4)));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "AOE action accepted a spectator");
        helper.assertTrue(ItemStack.matches(before, holder), "spectator AOE action changed the tool stack");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMissingPayload(GameTestHelper helper) {
        ItemStack holder = configurableTool(INITIAL_DEFINITION);

        assertRejected(helper, holder, holder.copy(),
                new SyncActionData(ACTION_ID, 0, DataComponentMap.EMPTY),
                "AOE action without its component payload");
        helper.succeed();
    }

    private static void assertCreatorRejected(AoESymmetrical definition) {
        try {
            AOEConfigUIBehaviorActions.createSetToolAOEAction(definition);
        } catch (IllegalArgumentException exception) {
            return;
        }
        throw new GameTestAssertException("AOE creator accepted an invalid definition: " + definition);
    }

    private static void assertRejected(GameTestHelper helper, ItemStack holder, ItemStack openedStack,
                                       SyncActionData action, String description) {
        ItemStack before = holder.copy();
        helper.assertTrue(!dispatch(helper, holder, openedStack, action), description + " was accepted");
        helper.assertTrue(ItemStack.matches(before, holder), description + " changed the tool stack");
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, ItemStack openedStack,
                                    SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, openedStack, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, ItemStack openedStack,
                                    SyncActionData action) {
        AOEConfigUIBehaviorActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, null, null,
                InteractionHand.MAIN_HAND, openedStack);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData rawAction(AoESymmetrical definition) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.AOE.get(), definition)
                .build();
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static ItemStack configurableTool(AoESymmetrical definition) {
        ItemStack stack = ToolHelper.get(GTToolType.MINING_HAMMER, GTMaterials.Steel);
        stack.set(GTDataComponents.AOE, definition);
        return stack;
    }
}
