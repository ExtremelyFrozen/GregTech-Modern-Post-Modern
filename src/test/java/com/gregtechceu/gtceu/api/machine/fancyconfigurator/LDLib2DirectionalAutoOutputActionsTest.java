package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.feature.DirectionalAutoOutputMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.electric.ItemCollectorMachine;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2DirectionalAutoOutputActionsTest {

    private static final ResourceLocation CONFIGURE_ITEM_OUTPUT_SIDE_ACTION = GTCEu
            .id("configure_item_output_side");
    private static final ResourceLocation CONFIGURE_FLUID_OUTPUT_SIDE_ACTION = GTCEu
            .id("configure_fluid_output_side");
    private static final ResourceLocation SET_ITEM_INPUT_FROM_OUTPUT_SIDE_ACTION = GTCEu
            .id("set_item_input_from_output_side");
    private static final ResourceLocation SET_FLUID_INPUT_FROM_OUTPUT_SIDE_ACTION = GTCEu
            .id("set_fluid_input_from_output_side");
    private static final ResourceLocation OUTPUT_DIRECTION_FIELD = SyncFieldData.key("outputDirection");
    private static final ResourceLocation ALLOW_INPUT_FIELD = SyncFieldData.key("allowInput");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherChangesItemOutputSideAndDisablesAutoOutput(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = TestFancyDirectionalHolder.supportingBoth();
        holder.itemDirection = Direction.NORTH;
        holder.autoOutputItems = true;
        registerActions();

        boolean result = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.EAST));

        helper.assertTrue(result, "valid item output side action was rejected");
        helper.assertTrue(holder.itemDirection == Direction.EAST, "item output side was not updated");
        helper.assertTrue(!holder.autoOutputItems, "changing item output side did not disable auto-output");
        helper.assertTrue(holder.fluidDirection == Direction.SOUTH, "item action changed fluid output side");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherTogglesItemAutoOutputForSelectedOutputSide(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = TestFancyDirectionalHolder.supportingBoth();
        holder.itemDirection = Direction.WEST;
        registerActions();

        boolean enabled = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.WEST));
        helper.assertTrue(enabled, "item output side action was rejected");
        helper.assertTrue(holder.autoOutputItems, "item output side action did not enable auto-output");

        boolean disabled = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.WEST));

        helper.assertTrue(disabled, "second item output side action was rejected");
        helper.assertTrue(!holder.autoOutputItems, "second item output side action did not disable auto-output");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherChangesFluidOutputSideAndDisablesAutoOutput(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = TestFancyDirectionalHolder.supportingBoth();
        holder.fluidDirection = Direction.SOUTH;
        holder.autoOutputFluids = true;
        registerActions();

        boolean result = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createConfigureFluidOutputSideAction(Direction.UP));

        helper.assertTrue(result, "valid fluid output side action was rejected");
        helper.assertTrue(holder.fluidDirection == Direction.UP, "fluid output side was not updated");
        helper.assertTrue(!holder.autoOutputFluids, "changing fluid output side did not disable auto-output");
        helper.assertTrue(holder.itemDirection == Direction.NORTH, "fluid action changed item output side");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherTogglesFluidAutoOutputForSelectedOutputSide(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = TestFancyDirectionalHolder.supportingBoth();
        holder.fluidDirection = Direction.DOWN;
        registerActions();

        boolean result = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createConfigureFluidOutputSideAction(Direction.DOWN));

        helper.assertTrue(result, "fluid output side action was rejected");
        helper.assertTrue(holder.autoOutputFluids, "fluid output side action did not enable auto-output");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherRejectsUnsupportedDirectionalOutput(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = new TestFancyDirectionalHolder(false, false);
        registerActions();

        boolean itemResult = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.EAST));
        boolean fluidResult = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createConfigureFluidOutputSideAction(Direction.UP));

        helper.assertTrue(!itemResult, "unsupported item directional output was accepted");
        helper.assertTrue(!fluidResult, "unsupported fluid directional output was accepted");
        helper.assertTrue(holder.itemDirection == Direction.NORTH, "rejected item action changed direction");
        helper.assertTrue(holder.fluidDirection == Direction.SOUTH, "rejected fluid action changed direction");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherRejectsHolderWithoutFancyActionContract(GameTestHelper helper) {
        TestDirectionalHolder holder = new TestDirectionalHolder(true, true);
        registerActions();

        boolean result = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.EAST));

        helper.assertTrue(!result, "directional holder without Fancy action contract was accepted");
        helper.assertTrue(holder.itemDirection == Direction.NORTH, "rejected holder action changed direction");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherRejectsDirectionDisallowedByHolder(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = TestFancyDirectionalHolder.supportingBoth();
        holder.allowItemDirectionChange = false;
        holder.autoOutputItems = true;
        registerActions();

        boolean result = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.EAST));

        helper.assertTrue(!result, "holder-disallowed item direction was accepted");
        helper.assertTrue(holder.itemDirection == Direction.NORTH, "disallowed action changed item direction");
        helper.assertTrue(holder.autoOutputItems, "disallowed action changed item auto-output state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherUsesMetaMachineTraitAndRejectsInvalidFaces(GameTestHelper helper) {
        ItemCollectorMachine machine = (ItemCollectorMachine) TestUtils.setMachine(helper, new BlockPos(1, 1, 1),
                GTMachines.ITEM_COLLECTOR[GTValues.LV]);
        machine.setFrontFacing(Direction.NORTH);
        AutoOutputTrait trait = machine.getTrait(AutoOutputTrait.TYPE);
        if (trait == null) {
            throw new IllegalStateException("Item collector did not expose its auto-output trait.");
        }
        trait.setItemOutputDirection(Direction.SOUTH);
        trait.setAllowAutoOutputItems(true);
        registerActions();

        boolean valid = dispatch(helper, machine,
                LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.EAST));
        helper.assertTrue(valid, "MetaMachine auto-output trait action was rejected");
        helper.assertTrue(trait.getItemOutputDirection() == Direction.EAST,
                "MetaMachine auto-output trait direction was not updated");
        helper.assertTrue(!trait.isAutoOutputItems(), "valid MetaMachine direction change did not disable auto-output");

        trait.setAllowAutoOutputItems(true);
        boolean frontFacing = dispatch(helper, machine,
                LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.NORTH));
        helper.assertTrue(!frontFacing, "machine front face was accepted as item output");
        helper.assertTrue(trait.getItemOutputDirection() == Direction.EAST,
                "front-facing rejection changed item output direction");
        helper.assertTrue(trait.isAutoOutputItems(), "front-facing rejection partially changed auto-output state");

        trait.setItemOutputDirectionValidator(direction -> direction != Direction.WEST);
        boolean customValidator = dispatch(helper, machine,
                LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.WEST));
        helper.assertTrue(!customValidator, "custom-validator-rejected item output face was accepted");
        helper.assertTrue(trait.getItemOutputDirection() == Direction.EAST,
                "custom validator rejection changed item output direction");
        helper.assertTrue(trait.isAutoOutputItems(),
                "custom validator rejection partially changed auto-output state");

        boolean allowInput = dispatch(helper, machine,
                LDLib2DirectionalAutoOutputActions.createSetItemInputFromOutputSideAction(true));
        helper.assertTrue(allowInput, "MetaMachine item output-side input action was rejected");
        helper.assertTrue(trait.allowsItemInputFromOutputSide(),
                "MetaMachine item output-side input policy was not updated");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherUpdatesOutputSideInputPolicies(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = TestFancyDirectionalHolder.supportingBoth();
        registerActions();

        boolean itemResult = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createSetItemInputFromOutputSideAction(true));
        helper.assertTrue(itemResult, "valid item output-side input action was rejected");
        helper.assertTrue(holder.allowsItemInputFromOutputSide(), "item output-side input policy was not updated");
        helper.assertTrue(!holder.allowsFluidInputFromOutputSide(), "item input policy action changed fluid state");

        boolean fluidResult = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createSetFluidInputFromOutputSideAction(true));

        helper.assertTrue(fluidResult, "valid fluid output-side input action was rejected");
        helper.assertTrue(holder.allowsFluidInputFromOutputSide(), "fluid output-side input policy was not updated");
        helper.assertTrue(holder.allowsItemInputFromOutputSide(), "fluid input policy action changed item state");

        boolean itemDisabled = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createSetItemInputFromOutputSideAction(false));
        boolean fluidDisabled = dispatch(helper, holder,
                LDLib2DirectionalAutoOutputActions.createSetFluidInputFromOutputSideAction(false));

        helper.assertTrue(itemDisabled, "item output-side input disable action was rejected");
        helper.assertTrue(fluidDisabled, "fluid output-side input disable action was rejected");
        helper.assertTrue(!holder.allowsItemInputFromOutputSide(), "item output-side input policy was not disabled");
        helper.assertTrue(!holder.allowsFluidInputFromOutputSide(),
                "fluid output-side input policy was not disabled");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherRejectsInvalidOutputSideInputPayloads(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = TestFancyDirectionalHolder.supportingBoth();
        registerActions();

        boolean itemWrongType = dispatch(helper, holder, action(SET_ITEM_INPUT_FROM_OUTPUT_SIDE_ACTION,
                payload(ALLOW_INPUT_FIELD, new JsonPrimitive("true"))));
        boolean itemMissing = dispatch(helper, holder, action(SET_ITEM_INPUT_FROM_OUTPUT_SIDE_ACTION,
                payload(OTHER_FIELD, new JsonPrimitive(true))));
        boolean fluidWrongType = dispatch(helper, holder, action(SET_FLUID_INPUT_FROM_OUTPUT_SIDE_ACTION,
                payload(ALLOW_INPUT_FIELD, new JsonPrimitive(1))));
        boolean fluidMissing = dispatch(helper, holder, action(SET_FLUID_INPUT_FROM_OUTPUT_SIDE_ACTION,
                payload(OTHER_FIELD, new JsonPrimitive(true))));

        helper.assertTrue(!itemWrongType, "string item output-side input payload was accepted");
        helper.assertTrue(!itemMissing, "item output-side input payload without state was accepted");
        helper.assertTrue(!fluidWrongType, "numeric fluid output-side input payload was accepted");
        helper.assertTrue(!fluidMissing, "fluid output-side input payload without state was accepted");
        helper.assertTrue(!holder.allowsItemInputFromOutputSide(), "invalid payload changed item input policy");
        helper.assertTrue(!holder.allowsFluidInputFromOutputSide(), "invalid payload changed fluid input policy");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherRejectsUnauthorizedOutputSideInputHolders(GameTestHelper helper) {
        TestFancyDirectionalHolder unsupported = new TestFancyDirectionalHolder(false, false);
        TestDirectionalHolder unmarked = new TestDirectionalHolder(true, true);
        registerActions();

        boolean unsupportedItem = dispatch(helper, unsupported,
                LDLib2DirectionalAutoOutputActions.createSetItemInputFromOutputSideAction(true));
        boolean unsupportedFluid = dispatch(helper, unsupported,
                LDLib2DirectionalAutoOutputActions.createSetFluidInputFromOutputSideAction(true));
        boolean unmarkedItem = dispatch(helper, unmarked,
                LDLib2DirectionalAutoOutputActions.createSetItemInputFromOutputSideAction(true));
        boolean unmarkedFluid = dispatch(helper, unmarked,
                LDLib2DirectionalAutoOutputActions.createSetFluidInputFromOutputSideAction(true));

        helper.assertTrue(!unsupportedItem, "unsupported item input policy holder was accepted");
        helper.assertTrue(!unsupportedFluid, "unsupported fluid input policy holder was accepted");
        helper.assertTrue(!unmarkedItem, "unmarked item input policy holder was accepted");
        helper.assertTrue(!unmarkedFluid, "unmarked fluid input policy holder was accepted");
        helper.assertTrue(!unsupported.allowsItemInputFromOutputSide(),
                "rejected unsupported holder changed item input policy");
        helper.assertTrue(!unsupported.allowsFluidInputFromOutputSide(),
                "rejected unsupported holder changed fluid input policy");
        helper.assertTrue(!unmarked.allowsItemInputFromOutputSide(),
                "rejected unmarked holder changed item input policy");
        helper.assertTrue(!unmarked.allowsFluidInputFromOutputSide(),
                "rejected unmarked holder changed fluid input policy");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherRejectsSpectatorOutputSideInputPolicy(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = TestFancyDirectionalHolder.supportingBoth();
        registerActions();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        GameType originalGameType = player.gameMode.getGameModeForPlayer();
        try {
            player.setGameMode(GameType.SPECTATOR);
            boolean result = dispatch(player, holder,
                    LDLib2DirectionalAutoOutputActions.createSetItemInputFromOutputSideAction(true));

            helper.assertTrue(!result, "spectator output-side input action was accepted");
            helper.assertTrue(!holder.allowsItemInputFromOutputSide(),
                    "spectator action changed output-side input policy");
        } finally {
            player.setGameMode(originalGameType);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherRejectsInvalidDirectionPayloads(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = TestFancyDirectionalHolder.supportingBoth();
        registerActions();

        boolean fractional = dispatch(helper, holder, action(CONFIGURE_ITEM_OUTPUT_SIDE_ACTION,
                payload(OUTPUT_DIRECTION_FIELD, new JsonPrimitive(0.5))));
        boolean overflow = dispatch(helper, holder, action(CONFIGURE_ITEM_OUTPUT_SIDE_ACTION,
                payload(OUTPUT_DIRECTION_FIELD, new JsonPrimitive(2147483648L))));
        boolean negative = dispatch(helper, holder, action(CONFIGURE_ITEM_OUTPUT_SIDE_ACTION,
                payload(OUTPUT_DIRECTION_FIELD, new JsonPrimitive(-1))));
        boolean outsideEnum = dispatch(helper, holder, action(CONFIGURE_FLUID_OUTPUT_SIDE_ACTION,
                payload(OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.values().length))));
        boolean wrongType = dispatch(helper, holder, action(CONFIGURE_FLUID_OUTPUT_SIDE_ACTION,
                payload(OUTPUT_DIRECTION_FIELD, new JsonPrimitive("north"))));
        boolean missing = dispatch(helper, holder, action(CONFIGURE_FLUID_OUTPUT_SIDE_ACTION,
                payload(OTHER_FIELD, new JsonPrimitive(Direction.NORTH.get3DDataValue()))));

        helper.assertTrue(!fractional, "fractional direction was accepted");
        helper.assertTrue(!overflow, "overflowing direction was accepted");
        helper.assertTrue(!negative, "negative direction was accepted");
        helper.assertTrue(!outsideEnum, "out-of-range direction was accepted");
        helper.assertTrue(!wrongType, "string direction was accepted");
        helper.assertTrue(!missing, "payload without direction was accepted");
        helper.assertTrue(holder.itemDirection == Direction.NORTH, "invalid payload changed item direction");
        helper.assertTrue(holder.fluidDirection == Direction.SOUTH, "invalid payload changed fluid direction");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalAutoOutputActions")
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        TestFancyDirectionalHolder holder = TestFancyDirectionalHolder.supportingBoth();
        registerActions();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        GameType originalGameType = player.gameMode.getGameModeForPlayer();
        try {
            player.setGameMode(GameType.SPECTATOR);
            boolean result = dispatch(player, holder,
                    LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.EAST));

            helper.assertTrue(!result, "spectator directional action was accepted");
            helper.assertTrue(holder.itemDirection == Direction.NORTH, "spectator action changed direction");
        } finally {
            player.setGameMode(originalGameType);
        }
        helper.succeed();
    }

    private static void registerActions() {
        LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(Direction.NORTH);
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(ResourceLocation actionId, DataComponentMap payload) {
        return new SyncActionData(actionId, 1, payload);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static class TestDirectionalHolder implements DirectionalAutoOutputMachine {

        private final boolean supportsItems;
        private final boolean supportsFluids;
        protected Direction itemDirection = Direction.NORTH;
        protected Direction fluidDirection = Direction.SOUTH;
        protected boolean autoOutputItems;
        protected boolean autoOutputFluids;
        private boolean allowItemInput;
        private boolean allowFluidInput;
        protected boolean allowItemDirectionChange = true;

        private TestDirectionalHolder(boolean supportsItems, boolean supportsFluids) {
            this.supportsItems = supportsItems;
            this.supportsFluids = supportsFluids;
        }

        @Override
        public boolean supportsAutoOutputItems() {
            return supportsItems;
        }

        @Override
        public boolean supportsAutoOutputFluids() {
            return supportsFluids;
        }

        @Override
        public boolean isAutoOutputItems() {
            return autoOutputItems;
        }

        @Override
        public boolean isAutoOutputFluids() {
            return autoOutputFluids;
        }

        @Override
        public void setAllowAutoOutputItems(boolean allow) {
            autoOutputItems = allow;
        }

        @Override
        public void setAllowAutoOutputFluids(boolean allow) {
            autoOutputFluids = allow;
        }

        @Override
        public @Nullable Direction getItemOutputDirection() {
            return supportsItems ? itemDirection : null;
        }

        @Override
        public @Nullable Direction getFluidOutputDirection() {
            return supportsFluids ? fluidDirection : null;
        }

        @Override
        public boolean canSetItemOutputDirection(@Nullable Direction direction) {
            return supportsItems && allowItemDirectionChange;
        }

        @Override
        public boolean canSetFluidOutputDirection(@Nullable Direction direction) {
            return supportsFluids;
        }

        @Override
        public void setItemOutputDirection(@Nullable Direction direction) {
            if (canSetItemOutputDirection(direction)) {
                itemDirection = direction;
            }
        }

        @Override
        public void setFluidOutputDirection(@Nullable Direction direction) {
            if (canSetFluidOutputDirection(direction)) {
                fluidDirection = direction;
            }
        }

        @Override
        public boolean allowsItemInputFromOutputSide() {
            return allowItemInput;
        }

        @Override
        public boolean allowsFluidInputFromOutputSide() {
            return allowFluidInput;
        }

        @Override
        public void setAllowItemInputFromOutputSide(boolean allow) {
            allowItemInput = allow;
        }

        @Override
        public void setAllowFluidInputFromOutputSide(boolean allow) {
            allowFluidInput = allow;
        }
    }

    private static final class TestFancyDirectionalHolder extends TestDirectionalHolder
                                                          implements LDLib2FancyActionMachine {

        private TestFancyDirectionalHolder(boolean supportsItems, boolean supportsFluids) {
            super(supportsItems, supportsFluids);
        }

        private static TestFancyDirectionalHolder supportingBoth() {
            return new TestFancyDirectionalHolder(true, true);
        }
    }
}
