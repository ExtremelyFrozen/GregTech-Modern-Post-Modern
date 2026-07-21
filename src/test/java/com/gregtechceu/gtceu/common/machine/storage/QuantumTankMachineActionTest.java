package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

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
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class QuantumTankMachineActionTest {

    private static final String BATCH = "QuantumTankMachineAction";
    private static final ResourceLocation CLICK_FLUID_SLOT_ACTION = GTCEu.id("click_quantum_tank_fluid_slot");
    private static final ResourceLocation SET_LOCKED_FLUID_ACTION = GTCEu.id("set_quantum_tank_locked_fluid");
    private static final ResourceLocation SET_LOCKED_ACTION = GTCEu.id("set_quantum_tank_locked");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");
    private static final ResourceLocation LOCKED_FIELD = SyncFieldData.key("locked");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");
    private static final long CAPACITY = 4L * FluidType.BUCKET_VOLUME;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fluidSlotCreatorPreservesShiftPayloadAndSequence(GameTestHelper helper) {
        assertCreatedBooleanAction(helper,
                QuantumTankMachineActions.createClickQuantumTankFluidSlotAction(false),
                CLICK_FLUID_SLOT_ACTION, SHIFT_FIELD, false, 0, "unshifted fluid-slot");
        assertCreatedBooleanAction(helper,
                QuantumTankMachineActions.createClickQuantumTankFluidSlotAction(true),
                CLICK_FLUID_SLOT_ACTION, SHIFT_FIELD, true, 1, "shifted fluid-slot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void lockedFluidCreatorNormalizesPayloadWithoutMutatingInput(GameTestHelper helper) {
        FluidStack selected = new FluidStack(Fluids.WATER, 5 * FluidType.BUCKET_VOLUME);

        SyncActionData action = QuantumTankMachineActions.createSetQuantumTankLockedFluidAction(selected);
        FluidStack encoded = requireFluid(action);

        helper.assertTrue(action.actionId().equals(SET_LOCKED_FLUID_ACTION),
                "quantum tank locked-fluid creator used the wrong action id");
        helper.assertTrue(FluidStack.isSameFluidSameComponents(encoded, selected) &&
                encoded.getAmount() == FluidType.BUCKET_VOLUME,
                "quantum tank locked-fluid creator did not normalize its payload to one bucket");
        helper.assertTrue(action.sequence() ==
                FluidStack.hashFluidAndComponents(encoded) * 31 + encoded.getAmount(),
                "quantum tank locked-fluid creator used the wrong sequence");
        helper.assertTrue(selected.getAmount() == 5 * FluidType.BUCKET_VOLUME,
                "quantum tank locked-fluid creator mutated its input stack");

        SyncActionData clearAction = QuantumTankMachineActions
                .createSetQuantumTankLockedFluidAction(FluidStack.EMPTY);
        FluidStack empty = requireFluid(clearAction);
        helper.assertTrue(empty.isEmpty(), "quantum tank locked-fluid clear action encoded a non-empty fluid");
        helper.assertTrue(clearAction.sequence() ==
                FluidStack.hashFluidAndComponents(empty) * 31 + empty.getAmount(),
                "quantum tank locked-fluid clear action used the wrong sequence");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void lockedToggleCreatorPreservesBooleanPayloadAndSequence(GameTestHelper helper) {
        assertCreatedBooleanAction(helper,
                QuantumTankMachineActions.createSetQuantumTankLockedAction(false),
                SET_LOCKED_ACTION, LOCKED_FIELD, false, 0, "unlocked toggle");
        assertCreatedBooleanAction(helper,
                QuantumTankMachineActions.createSetQuantumTankLockedAction(true),
                SET_LOCKED_ACTION, LOCKED_FIELD, true, 1, "locked toggle");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fluidSlotTargetEmptiesContainerAndDispatcherFillsItAgain(GameTestHelper helper) {
        QuantumTankMachine machine = createMachine();
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.WATER_BUCKET));

        machine.clickQuantumTankFluidSlot(player, false);

        helper.assertTrue(machine.getStoredAmount() == FluidType.BUCKET_VOLUME &&
                machine.getStored().getFluid() == Fluids.WATER,
                "direct quantum tank fluid-slot target did not empty the carried container");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET),
                "direct quantum tank fluid-slot target did not return the empty container to the cursor");

        boolean result = dispatch(player, machine, CLICK_FLUID_SLOT_ACTION,
                booleanPayload(SHIFT_FIELD, new JsonPrimitive(false)));

        helper.assertTrue(result, "valid quantum tank fluid-slot action was rejected");
        helper.assertTrue(machine.getStoredAmount() == 0 && machine.getStored().isEmpty(),
                "quantum tank fluid-slot dispatcher did not drain tank zero");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "quantum tank fluid-slot dispatcher did not return the filled container to the cursor");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void shiftedFluidSlotActionProcessesEveryCarriedContainer(GameTestHelper helper) {
        QuantumTankMachine machine = createMachine();
        fill(machine, new FluidStack(Fluids.WATER, (int) CAPACITY));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET, 3));

        boolean result = dispatch(player, machine, CLICK_FLUID_SLOT_ACTION,
                booleanPayload(SHIFT_FIELD, new JsonPrimitive(true)));

        int filledContainers = player.getInventory().countItem(Items.WATER_BUCKET) +
                (player.containerMenu.getCarried().is(Items.WATER_BUCKET) ?
                        player.containerMenu.getCarried().getCount() : 0);
        helper.assertTrue(result, "valid shifted quantum tank fluid-slot action was rejected");
        helper.assertTrue(machine.getStoredAmount() == FluidType.BUCKET_VOLUME,
                "shifted quantum tank action did not process every carried container");
        helper.assertTrue(filledContainers == 3,
                "shifted quantum tank action did not preserve all filled container results");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void lockedFluidTargetAndDispatcherPreserveSelectionSemantics(GameTestHelper helper) {
        QuantumTankMachine targetMachine = createMachine();
        FluidStack directSelection = new FluidStack(Fluids.LAVA, 37);

        targetMachine.setQuantumTankLockedFluid(directSelection);

        helper.assertTrue(FluidStack.isSameFluidSameComponents(targetMachine.getLockedFluid(), directSelection) &&
                targetMachine.getLockedFluid().getAmount() == 37,
                "direct quantum tank locked-fluid target changed the selected amount");
        targetMachine.setQuantumTankLockedFluid(FluidStack.EMPTY);
        helper.assertTrue(!targetMachine.isLocked(),
                "direct quantum tank locked-fluid target did not clear the lock");

        fill(targetMachine, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        targetMachine.setQuantumTankLockedFluid(directSelection);
        helper.assertTrue(!targetMachine.isLocked(),
                "direct quantum tank locked-fluid target accepted a fluid different from storage");
        targetMachine.setQuantumTankLockedFluid(new FluidStack(Fluids.WATER, 37));
        helper.assertTrue(targetMachine.isLocked() &&
                targetMachine.getLockedFluid().getAmount() == FluidType.BUCKET_VOLUME,
                "direct quantum tank locked-fluid target did not lock matching storage to one bucket");

        QuantumTankMachine dispatcherMachine = createMachine();
        FluidStack rawSelection = new FluidStack(Fluids.WATER, 73);
        ServerPlayer player = preparePlayer(helper, ItemStack.EMPTY);
        boolean selected = dispatch(player, dispatcherMachine, SET_LOCKED_FLUID_ACTION,
                fluidPayload(rawSelection));

        helper.assertTrue(selected, "valid quantum tank locked-fluid action was rejected");
        helper.assertTrue(FluidStack.isSameFluidSameComponents(dispatcherMachine.getLockedFluid(), rawSelection) &&
                dispatcherMachine.getLockedFluid().getAmount() == 73,
                "quantum tank dispatcher did not preserve a raw locked-fluid payload amount");

        boolean cleared = dispatch(player, dispatcherMachine, SET_LOCKED_FLUID_ACTION,
                fluidPayload(FluidStack.EMPTY));
        helper.assertTrue(cleared && !dispatcherMachine.isLocked(),
                "quantum tank locked-fluid clear action did not clear the server lock");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void lockedToggleTargetAndDispatcherUseStoredFluid(GameTestHelper helper) {
        QuantumTankMachine machine = createMachine();
        fill(machine, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));

        machine.setQuantumTankLocked(true);

        helper.assertTrue(machine.isLocked() && machine.getLockedFluid().getFluid() == Fluids.WATER &&
                machine.getLockedFluid().getAmount() == FluidType.BUCKET_VOLUME,
                "direct quantum tank lock target did not lock the currently stored fluid");
        machine.setQuantumTankLocked(false);
        helper.assertTrue(!machine.isLocked(), "direct quantum tank lock target did not unlock the tank");

        ServerPlayer player = preparePlayer(helper, ItemStack.EMPTY);
        boolean locked = dispatch(player, machine, SET_LOCKED_ACTION,
                booleanPayload(LOCKED_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(locked && machine.isLocked() &&
                machine.getLockedFluid().getAmount() == FluidType.BUCKET_VOLUME,
                "quantum tank lock-toggle dispatcher did not lock the stored fluid");

        boolean unlocked = dispatch(player, machine, SET_LOCKED_ACTION,
                booleanPayload(LOCKED_FIELD, new JsonPrimitive(false)));

        helper.assertTrue(unlocked, "valid quantum tank unlock action was rejected");
        helper.assertTrue(!machine.isLocked(), "quantum tank lock-toggle dispatcher did not finish unlocked");
        helper.assertTrue(machine.getStoredAmount() == 2 * FluidType.BUCKET_VOLUME,
                "quantum tank lock-toggle actions changed the stored fluid amount");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHoldersAndMalformedPayloadsWithoutMutation(GameTestHelper helper) {
        QuantumTankMachine machine = createMachine();
        fill(machine, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));

        boolean wrongClickHolder = dispatch(player, new Object(), CLICK_FLUID_SLOT_ACTION,
                booleanPayload(SHIFT_FIELD, new JsonPrimitive(false)));
        boolean wrongFluidHolder = dispatch(player, new Object(), SET_LOCKED_FLUID_ACTION,
                fluidPayload(new FluidStack(Fluids.LAVA, 1)));
        boolean wrongLockHolder = dispatch(player, new Object(), SET_LOCKED_ACTION,
                booleanPayload(LOCKED_FIELD, new JsonPrimitive(true)));
        boolean missingClickPayload = dispatch(player, machine, CLICK_FLUID_SLOT_ACTION, DataComponentMap.EMPTY);
        boolean numericShift = dispatch(player, machine, CLICK_FLUID_SLOT_ACTION,
                booleanPayload(SHIFT_FIELD, new JsonPrimitive(0)));
        boolean missingFluid = dispatch(player, machine, SET_LOCKED_FLUID_ACTION, DataComponentMap.EMPTY);
        boolean missingLock = dispatch(player, machine, SET_LOCKED_ACTION,
                booleanPayload(OTHER_FIELD, new JsonPrimitive(true)));
        boolean numericLock = dispatch(player, machine, SET_LOCKED_ACTION,
                booleanPayload(LOCKED_FIELD, new JsonPrimitive(1)));

        helper.assertTrue(!wrongClickHolder && !wrongFluidHolder && !wrongLockHolder,
                "a quantum tank action accepted an unrelated holder");
        helper.assertTrue(!missingClickPayload && !numericShift,
                "quantum tank fluid-slot action accepted a malformed payload");
        helper.assertTrue(!missingFluid, "quantum tank locked-fluid action accepted a missing payload");
        helper.assertTrue(!missingLock && !numericLock,
                "quantum tank lock-toggle action accepted a malformed payload");
        assertUnchanged(helper, machine, player, 2 * FluidType.BUCKET_VOLUME,
                "rejected quantum tank actions");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsSpectatorForAllActionsWithoutMutation(GameTestHelper helper) {
        QuantumTankMachine machine = createMachine();
        fill(machine, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));
        player.setGameMode(GameType.SPECTATOR);

        boolean clickResult;
        boolean fluidResult;
        boolean lockResult;
        try {
            clickResult = dispatch(player, machine, CLICK_FLUID_SLOT_ACTION,
                    booleanPayload(SHIFT_FIELD, new JsonPrimitive(false)));
            fluidResult = dispatch(player, machine, SET_LOCKED_FLUID_ACTION,
                    fluidPayload(new FluidStack(Fluids.LAVA, 1)));
            lockResult = dispatch(player, machine, SET_LOCKED_ACTION,
                    booleanPayload(LOCKED_FIELD, new JsonPrimitive(true)));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!clickResult && !fluidResult && !lockResult,
                "a quantum tank action accepted a spectator");
        assertUnchanged(helper, machine, player, 2 * FluidType.BUCKET_VOLUME,
                "spectator quantum tank actions");
        helper.succeed();
    }

    private static void assertCreatedBooleanAction(GameTestHelper helper, SyncActionData action,
                                                   ResourceLocation expectedActionId, ResourceLocation field,
                                                   boolean expectedValue, int expectedSequence, String description) {
        SyncFieldData fields = requireFields(action);
        JsonElement value = fields.get(field);

        helper.assertTrue(action.actionId().equals(expectedActionId),
                description + " creator used the wrong action id");
        helper.assertTrue(action.sequence() == expectedSequence,
                description + " creator used the wrong sequence");
        helper.assertTrue(fields.fields().size() == 1,
                description + " creator encoded fields outside its protocol");
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expectedValue,
                description + " creator encoded the wrong boolean value");
    }

    private static QuantumTankMachine createMachine() {
        var definition = GTMachines.QUANTUM_TANK[GTValues.IV];
        return new QuantumTankMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()),
                GTValues.IV, CAPACITY);
    }

    private static void fill(QuantumTankMachine machine, FluidStack fluid) {
        int filled = machine.cache.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        if (filled != fluid.getAmount()) {
            throw new IllegalStateException("Quantum tank test setup could not insert the requested fluid.");
        }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return FakePlayerFactory.getMinecraft(helper.getLevel());
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper, ItemStack carried) {
        ServerPlayer player = player(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.containerMenu.setCarried(carried);
        return player;
    }

    private static boolean dispatch(ServerPlayer player, Object holder, ResourceLocation actionId,
                                    DataComponentMap payload) {
        QuantumTankMachineActions.initialize();
        SyncActionData action = new SyncActionData(actionId, 0, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap booleanPayload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static DataComponentMap fluidPayload(FluidStack fluid) {
        return DataComponentMap.builder()
                .set(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(fluid))
                .build();
    }

    private static SyncFieldData requireFields(SyncActionData action) {
        SyncFieldData fields = action.payload().get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Quantum tank action creator omitted field data.");
        }
        return fields;
    }

    private static FluidStack requireFluid(SyncActionData action) {
        SimpleFluidContent content = action.payload().get(GTDataComponents.FLUID_CONTENT.get());
        if (content == null) {
            throw new IllegalStateException("Quantum tank action creator omitted fluid content.");
        }
        return content.copy();
    }

    private static void assertUnchanged(GameTestHelper helper, QuantumTankMachine machine, ServerPlayer player,
                                        long expectedAmount, String description) {
        helper.assertTrue(machine.getStoredAmount() == expectedAmount && machine.getStored().getFluid() == Fluids.WATER,
                description + " changed stored fluid state");
        helper.assertTrue(!machine.isLocked(), description + " changed locked-fluid state");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET) &&
                player.containerMenu.getCarried().getCount() == 1,
                description + " changed the opening player's cursor");
    }
}
