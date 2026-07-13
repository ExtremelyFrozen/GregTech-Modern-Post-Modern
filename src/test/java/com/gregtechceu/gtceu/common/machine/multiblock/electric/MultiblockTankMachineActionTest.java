package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

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

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MultiblockTankMachineActionTest {

    private static final String BATCH = "MultiblockTankMachineAction";
    private static final ResourceLocation ACTION_ID = GTCEu.id("click_multiblock_tank_fluid_slot");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");
    private static final ResourceLocation UNKNOWN_TANK_FIELD = SyncFieldData.key("tank");
    private static final int CAPACITY = 4 * FluidType.BUCKET_VOLUME;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorPreservesShiftPayloadAndSequence(GameTestHelper helper) {
        assertCreatedAction(helper, false, 0);
        assertCreatedAction(helper, true, 1);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherFillsCarriedContainerFromOnlyTank(GameTestHelper helper) {
        MultiblockTankMachine machine = createMachine();
        machine.getTank().setFluidInTank(0, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(false)));

        helper.assertTrue(result, "valid multiblock tank fluid-slot action was rejected");
        helper.assertTrue(machine.getTank().getTanks() == 1,
                "multiblock tank action target no longer exposes exactly one tank");
        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "unshifted action did not transfer exactly one bucket from tank zero");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "unshifted action did not return the filled container to the opening player's cursor");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void targetLogicEmptiesCarriedContainerIntoOnlyTank(GameTestHelper helper) {
        MultiblockTankMachine machine = createMachine();
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.WATER_BUCKET));

        machine.clickMultiblockTankFluidSlot(player, false);

        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "direct target logic did not empty the carried fluid into tank zero");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET),
                "direct target logic did not return the drained container to the player's cursor");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void shiftedActionProcessesEveryCarriedContainer(GameTestHelper helper) {
        MultiblockTankMachine machine = createMachine();
        machine.getTank().setFluidInTank(0, new FluidStack(Fluids.WATER, CAPACITY));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET, 3));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(true)));

        int filledContainers = player.getInventory().countItem(Items.WATER_BUCKET) +
                (player.containerMenu.getCarried().is(Items.WATER_BUCKET) ?
                        player.containerMenu.getCarried().getCount() : 0);
        helper.assertTrue(result, "valid shifted multiblock tank action was rejected");
        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "shifted action did not process every carried container");
        helper.assertTrue(filledContainers == 3,
                "shifted action did not preserve all filled container results");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHolderAndMalformedPayloadsWithoutMutation(GameTestHelper helper) {
        MultiblockTankMachine machine = createMachine();
        int initialAmount = 2 * FluidType.BUCKET_VOLUME;
        machine.getTank().setFluidInTank(0, new FluidStack(Fluids.WATER, initialAmount));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));

        boolean wrongHolder = dispatch(player, new Object(), payload(new JsonPrimitive(false)));
        boolean missingFields = dispatch(player, machine, DataComponentMap.EMPTY);
        boolean missingShift = dispatch(player, machine,
                payloadWithField(OTHER_FIELD, new JsonPrimitive(false)));
        boolean numericShift = dispatch(player, machine, payload(new JsonPrimitive(0)));
        boolean stringShift = dispatch(player, machine, payload(new JsonPrimitive("false")));

        helper.assertTrue(!wrongHolder, "multiblock tank action accepted an unrelated holder");
        helper.assertTrue(!missingFields, "multiblock tank action accepted missing field data");
        helper.assertTrue(!missingShift, "multiblock tank action accepted a missing shift field");
        helper.assertTrue(!numericShift, "multiblock tank action accepted a numeric shift field");
        helper.assertTrue(!stringShift, "multiblock tank action accepted a string shift field");
        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == initialAmount,
                "rejected multiblock tank actions changed tank state");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET) &&
                player.containerMenu.getCarried().getCount() == 1,
                "rejected multiblock tank actions changed the opening player's cursor");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherPreservesProtocolTreatmentOfUnknownIndexField(GameTestHelper helper) {
        MultiblockTankMachine machine = createMachine();
        machine.getTank().setFluidInTank(0, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));

        boolean result = dispatch(player, machine, payloadWithUnknownTankIndex(-1));

        helper.assertTrue(result, "multiblock tank protocol rejected an unknown payload field");
        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "unknown index field changed routing away from the protocol's fixed tank zero");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "action with an unknown field did not preserve the fluid-container interaction");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsSpectatorWithoutMutation(GameTestHelper helper) {
        MultiblockTankMachine machine = createMachine();
        int initialAmount = 2 * FluidType.BUCKET_VOLUME;
        machine.getTank().setFluidInTank(0, new FluidStack(Fluids.WATER, initialAmount));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));
        player.setGameMode(GameType.SPECTATOR);

        boolean result;
        try {
            result = dispatch(player, machine, payload(new JsonPrimitive(false)));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "multiblock tank action accepted a spectator");
        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == initialAmount,
                "spectator multiblock tank action changed tank state");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET),
                "spectator multiblock tank action changed the opening player's cursor");
        helper.succeed();
    }

    private static void assertCreatedAction(GameTestHelper helper, boolean shiftDown, int expectedSequence) {
        SyncActionData action = MultiblockTankMachineActions
                .createClickMultiblockTankFluidSlotAction(shiftDown);
        SyncFieldData fields = requireFields(action);
        JsonElement shift = fields.get(SHIFT_FIELD);

        helper.assertTrue(action.actionId().equals(ACTION_ID),
                "multiblock tank creator used the wrong action id");
        helper.assertTrue(action.sequence() == expectedSequence,
                "multiblock tank creator used the wrong sequence");
        helper.assertTrue(fields.fields().size() == 1,
                "multiblock tank creator encoded fields outside its protocol");
        helper.assertTrue(shift instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == shiftDown,
                "multiblock tank creator encoded the wrong shift state");
    }

    private static MultiblockTankMachine createMachine() {
        var definition = GTMultiMachines.STEEL_MULTIBLOCK_TANK;
        return new MultiblockTankMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), CAPACITY, null);
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper, ItemStack carried) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.containerMenu.setCarried(carried);
        return player;
    }

    private static boolean dispatch(ServerPlayer player, Object holder, DataComponentMap payload) {
        MultiblockTankMachineActions.initialize();
        SyncActionData action = new SyncActionData(ACTION_ID, 0, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(JsonElement shiftDown) {
        return payloadWithField(SHIFT_FIELD, shiftDown);
    }

    private static DataComponentMap payloadWithField(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static DataComponentMap payloadWithUnknownTankIndex(int tankIndex) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SHIFT_FIELD, new JsonPrimitive(false))
                        .put(UNKNOWN_TANK_FIELD, new JsonPrimitive(tankIndex))
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(SyncActionData action) {
        SyncFieldData fields = action.payload().get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Multiblock tank action creator omitted field data.");
        }
        return fields;
    }
}
