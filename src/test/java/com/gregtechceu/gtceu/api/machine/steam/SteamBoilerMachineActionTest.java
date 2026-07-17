package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.steam.SteamLiquidBoilerMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SteamBoilerMachineActionTest {

    private static final String BATCH = "SteamBoilerMachineAction";
    private static final ResourceLocation ACTION_ID = GTCEu.id("click_steam_boiler_fluid_slot");
    private static final ResourceLocation FLUID_SLOT_FIELD = SyncFieldData.key("fluidSlot");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");
    private static final int WATER_FLUID_SLOT = 0;
    private static final int STEAM_FLUID_SLOT = 1;
    private static final int FUEL_FLUID_SLOT = 2;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorPreservesActionIdPayloadAndSlotOnlySequence(GameTestHelper helper) {
        for (int fluidSlot = WATER_FLUID_SLOT; fluidSlot <= FUEL_FLUID_SLOT; fluidSlot++) {
            assertCreatedAction(helper, fluidSlot, false);
            assertCreatedAction(helper, fluidSlot, true);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherInvokesValidatedTargetOnceWithExactArguments(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestSteamBoilerTarget target = new TestSteamBoilerTarget();

        boolean waterResult = dispatch(player, target,
                SteamBoilerMachineActions.createClickSteamBoilerFluidSlotAction(WATER_FLUID_SLOT, false));
        helper.assertTrue(waterResult, "valid water-slot action was rejected");
        helper.assertTrue(target.invocations == 1 && target.lastPlayer == player &&
                target.lastFluidSlot == WATER_FLUID_SLOT && !target.lastShiftDown,
                "water-slot action invoked the target with incorrect arguments");

        boolean fuelResult = dispatch(player, target,
                SteamBoilerMachineActions.createClickSteamBoilerFluidSlotAction(FUEL_FLUID_SLOT, true));
        helper.assertTrue(fuelResult, "valid fuel-slot action was rejected");
        helper.assertTrue(target.invocations == 2 && target.lastPlayer == player &&
                target.lastFluidSlot == FUEL_FLUID_SLOT && target.lastShiftDown,
                "fuel-slot action did not invoke the target exactly once with shift enabled");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedPayloadsAndInvalidProtocolIndices(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestSteamBoilerTarget target = new TestSteamBoilerTarget();

        helper.assertTrue(!dispatch(player, target, rawAction(DataComponentMap.EMPTY)),
                "action without field data was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payloadWithOnly(
                FLUID_SLOT_FIELD, new JsonPrimitive(WATER_FLUID_SLOT)))),
                "action without shift state was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payloadWithOnly(
                SHIFT_FIELD, new JsonPrimitive(false)))),
                "action without fluid slot was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(
                new JsonPrimitive("0"), new JsonPrimitive(false)))),
                "action with a string fluid slot was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(
                new JsonPrimitive(WATER_FLUID_SLOT), new JsonPrimitive(0)))),
                "action with a numeric shift state was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(
                new JsonPrimitive(-1), new JsonPrimitive(false)))),
                "action below the protocol slot range was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(
                new JsonPrimitive(FUEL_FLUID_SLOT + 1), new JsonPrimitive(false)))),
                "action above the protocol slot range was accepted");
        helper.assertTrue(target.invocations == 0, "rejected payload invoked the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAcceptsUnknownPayloadFieldsWithoutChangingArguments(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestSteamBoilerTarget target = new TestSteamBoilerTarget();
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(FLUID_SLOT_FIELD, new JsonPrimitive(STEAM_FLUID_SLOT))
                        .put(SHIFT_FIELD, new JsonPrimitive(true))
                        .put(OTHER_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();

        boolean result = dispatch(player, target, rawAction(payload));

        helper.assertTrue(result, "valid action with an unknown field was rejected");
        helper.assertTrue(target.invocations == 1 && target.lastFluidSlot == STEAM_FLUID_SLOT &&
                target.lastShiftDown,
                "unknown field changed the validated action arguments");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHolderAndSpectatorWithoutInvokingTarget(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestSteamBoilerTarget target = new TestSteamBoilerTarget();
        SyncActionData action = SteamBoilerMachineActions.createClickSteamBoilerFluidSlotAction(
                WATER_FLUID_SLOT, true);

        helper.assertTrue(!dispatch(player, new Object(), action),
                "steam boiler action accepted an unrelated holder");

        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorResult;
        try {
            spectatorResult = dispatch(player, target, action);
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!spectatorResult, "steam boiler action accepted a spectator");
        helper.assertTrue(target.invocations == 0, "rejected holder or spectator action invoked the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void waterSlotOnlyDrainsTheCarriedContainerIntoItsTank(GameTestHelper helper) {
        SteamBoilerMachine boiler = createBoiler(GTMachines.STEAM_SOLID_BOILER.left());
        ServerPlayer player = preparedPlayer(helper);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));

        boiler.clickSteamBoilerFluidSlot(player, WATER_FLUID_SLOT, false);

        helper.assertTrue(boiler.waterTank.getFluidInTank(0).is(Fluids.WATER),
                "water slot did not receive the carried water");
        helper.assertTrue(boiler.waterTank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "water slot did not receive exactly one bucket");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET),
                "water slot did not return the emptied container to the cursor");

        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        boiler.clickSteamBoilerFluidSlot(player, WATER_FLUID_SLOT, false);

        helper.assertTrue(boiler.waterTank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "water slot filled a carried container despite disallowing tank-to-cursor transfer");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET),
                "water slot replaced an empty cursor container without a permitted transfer");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void steamSlotOnlyFillsTheCarriedContainerFromItsTank(GameTestHelper helper) {
        SteamBoilerMachine boiler = createBoiler(GTMachines.STEAM_SOLID_BOILER.left());
        boiler.steamTank.setFluidInTank(0,
                new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparedPlayer(helper);
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));

        boiler.clickSteamBoilerFluidSlot(player, STEAM_FLUID_SLOT, false);

        helper.assertTrue(boiler.steamTank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "steam slot did not transfer exactly one bucket from the selected tank");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "steam slot did not return the filled container to the cursor");

        boiler.steamTank.setFluidInTank(0, FluidStack.EMPTY);
        boiler.clickSteamBoilerFluidSlot(player, STEAM_FLUID_SLOT, false);

        helper.assertTrue(boiler.steamTank.getFluidInTank(0).isEmpty(),
                "steam slot drained the carried container despite disallowing cursor-to-tank transfer");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "steam slot consumed a full cursor container without a permitted transfer");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void shiftedSteamSlotProcessesEveryCarriedContainer(GameTestHelper helper) {
        SteamBoilerMachine boiler = createBoiler(GTMachines.STEAM_SOLID_BOILER.left());
        boiler.steamTank.setFluidInTank(0,
                new FluidStack(Fluids.WATER, 4 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparedPlayer(helper);
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 3));

        boiler.clickSteamBoilerFluidSlot(player, STEAM_FLUID_SLOT, true);

        helper.assertTrue(boiler.steamTank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "shift click did not process every carried container");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET) &&
                player.containerMenu.getCarried().getCount() == 1,
                "shift click did not return one non-stackable filled container to the cursor");
        helper.assertTrue(player.getInventory().countItem(Items.WATER_BUCKET) == 2,
                "shift click did not store the remaining non-stackable filled containers");
        helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 0,
                "shift click left an empty container in the inventory");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void liquidBoilerFuelSlotUsesTheConcreteFuelTankAtProtocolBoundary(GameTestHelper helper) {
        SteamLiquidBoilerMachine boiler = createLiquidBoiler();
        boiler.fuelTank.setFluidInTank(0, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparedPlayer(helper);
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));

        boolean result = dispatch(player, boiler,
                SteamBoilerMachineActions.createClickSteamBoilerFluidSlotAction(FUEL_FLUID_SLOT, false));

        helper.assertTrue(result, "liquid boiler rejected the protocol boundary fuel slot");
        helper.assertTrue(boiler.fuelTank.getFluidInTank(0).isEmpty(),
                "fuel-slot action did not drain the concrete fuel tank");
        helper.assertTrue(boiler.waterTank.getFluidInTank(0).isEmpty() &&
                boiler.steamTank.getFluidInTank(0).isEmpty(),
                "fuel-slot action changed an unrelated boiler tank");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "fuel-slot action did not replace the cursor with the filled container");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void baseBoilerFuelSlotFailureReturnsFalseAndLeavesStateUnchanged(GameTestHelper helper) {
        SteamBoilerMachine boiler = createBoiler(GTMachines.STEAM_SOLID_BOILER.left());
        boiler.waterTank.setFluidInTank(0, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        boiler.steamTank.setFluidInTank(0, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparedPlayer(helper);
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));

        boolean result = dispatch(player, boiler,
                SteamBoilerMachineActions.createClickSteamBoilerFluidSlotAction(FUEL_FLUID_SLOT, false));

        helper.assertTrue(!result, "base boiler executed its unsupported fuel slot");
        helper.assertTrue(boiler.waterTank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME &&
                boiler.steamTank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "unsupported fuel-slot action changed a boiler tank before failing");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET),
                "unsupported fuel-slot action changed the cursor before failing");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void directTargetRejectsOutOfRangeSlotsAndIgnoresNonFluidCursor(GameTestHelper helper) {
        SteamBoilerMachine boiler = createBoiler(GTMachines.STEAM_SOLID_BOILER.left());
        boiler.steamTank.setFluidInTank(0, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparedPlayer(helper);
        player.containerMenu.setCarried(new ItemStack(Items.STONE));

        boiler.clickSteamBoilerFluidSlot(player, STEAM_FLUID_SLOT, true);
        helper.assertTrue(boiler.steamTank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "non-fluid cursor changed the selected tank");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.STONE),
                "non-fluid cursor was replaced by the selected tank");

        assertInvalidDirectSlot(helper, boiler, player, -1);
        assertInvalidDirectSlot(helper, boiler, player, FUEL_FLUID_SLOT + 1);
        helper.assertTrue(boiler.steamTank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "invalid direct slot changed the boiler tank");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.STONE),
                "invalid direct slot changed the cursor");
        helper.succeed();
    }

    private static void assertCreatedAction(GameTestHelper helper, int fluidSlot, boolean shiftDown) {
        SyncActionData action = SteamBoilerMachineActions.createClickSteamBoilerFluidSlotAction(
                fluidSlot, shiftDown);
        SyncFieldData fields = requireFields(action.payload());
        JsonElement slot = fields.get(FLUID_SLOT_FIELD);
        JsonElement shift = fields.get(SHIFT_FIELD);

        helper.assertTrue(action.actionId().equals(ACTION_ID), "creator used the wrong steam boiler action id");
        helper.assertTrue(action.sequence() == fluidSlot,
                "creator changed the slot-only steam boiler action sequence");
        helper.assertTrue(fields.fields().size() == 2,
                "creator encoded fields outside the steam boiler protocol");
        helper.assertTrue(slot instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == fluidSlot,
                "creator encoded the wrong fluid slot");
        helper.assertTrue(shift instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == shiftDown,
                "creator encoded the wrong shift state");
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return player;
    }

    private static SteamBoilerMachine createBoiler(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof SteamBoilerMachine boiler)) {
            throw new IllegalStateException("Steam boiler definition did not create a steam boiler machine.");
        }
        return boiler;
    }

    private static SteamLiquidBoilerMachine createLiquidBoiler() {
        SteamBoilerMachine boiler = createBoiler(GTMachines.STEAM_LIQUID_BOILER.left());
        if (!(boiler instanceof SteamLiquidBoilerMachine liquidBoiler)) {
            throw new IllegalStateException("Steam liquid boiler definition created the wrong machine type.");
        }
        return liquidBoiler;
    }

    private static void assertInvalidDirectSlot(GameTestHelper helper, SteamBoilerMachine boiler,
                                                ServerPlayer player, int fluidSlot) {
        try {
            boiler.clickSteamBoilerFluidSlot(player, fluidSlot, false);
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage().equals("Invalid steam boiler fluid slot: " + fluidSlot),
                    "invalid direct slot reported an unexpected failure");
            return;
        }
        helper.fail("direct target accepted invalid fluid slot " + fluidSlot);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        SteamBoilerMachineActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData rawAction(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement fluidSlot, JsonElement shiftDown) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(FLUID_SLOT_FIELD, fluidSlot)
                        .put(SHIFT_FIELD, shiftDown)
                        .build())
                .build();
    }

    private static DataComponentMap payloadWithOnly(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Steam boiler action creator omitted field data.");
        }
        return fields;
    }

    private static final class TestSteamBoilerTarget implements SteamBoilerFluidSlotActionTarget {

        private int invocations;
        private ServerPlayer lastPlayer;
        private int lastFluidSlot;
        private boolean lastShiftDown;

        @Override
        public void clickSteamBoilerFluidSlot(@NotNull ServerPlayer player, int fluidSlot, boolean shiftDown) {
            invocations++;
            lastPlayer = player;
            lastFluidSlot = fluidSlot;
            lastShiftDown = shiftDown;
        }
    }
}
