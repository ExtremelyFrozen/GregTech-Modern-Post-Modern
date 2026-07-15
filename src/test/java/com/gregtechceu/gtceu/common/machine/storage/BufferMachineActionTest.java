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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class BufferMachineActionTest {

    private static final String BATCH = "BufferMachineAction";
    private static final ResourceLocation CLICK_BUFFER_FLUID_SLOT_ACTION = GTCEu.id("click_buffer_fluid_slot");
    private static final ResourceLocation TANK_FIELD = SyncFieldData.key("tank");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorPreservesTankShiftPayloadAndSequence(GameTestHelper helper) {
        SyncActionData shifted = BufferMachineActions.createClickFluidSlotAction(2, true);
        SyncFieldData shiftedFields = requireFields(shifted);

        helper.assertTrue(shifted.actionId().equals(CLICK_BUFFER_FLUID_SLOT_ACTION),
                "buffer fluid-slot creator used the wrong action id");
        helper.assertTrue(shifted.sequence() == 5,
                "buffer fluid-slot creator did not encode tank and shift in the sequence");
        helper.assertTrue(shiftedFields.get(TANK_FIELD).getAsInt() == 2,
                "buffer fluid-slot creator encoded the wrong tank index");
        helper.assertTrue(shiftedFields.get(SHIFT_FIELD).getAsBoolean(),
                "buffer fluid-slot creator omitted the shifted state");

        SyncActionData unshifted = BufferMachineActions.createClickFluidSlotAction(2, false);
        helper.assertTrue(unshifted.sequence() == 4,
                "buffer fluid-slot creator did not preserve the unshifted sequence");
        helper.assertTrue(!requireFields(unshifted).get(SHIFT_FIELD).getAsBoolean(),
                "buffer fluid-slot creator encoded the wrong unshifted state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fluidSlotActionRequiresLeftClientFluidContainer(GameTestHelper helper) {
        ItemStack bucket = new ItemStack(Items.BUCKET);
        ItemStack stone = new ItemStack(Items.STONE);

        helper.assertTrue(BufferMachine.canSendFluidSlotAction(GLFW.GLFW_MOUSE_BUTTON_LEFT, true, bucket),
                "client left click with a fluid container was rejected");
        helper.assertTrue(!BufferMachine.canSendFluidSlotAction(GLFW.GLFW_MOUSE_BUTTON_LEFT, true, stone),
                "client left click with a non-fluid item was accepted");
        helper.assertTrue(!BufferMachine.canSendFluidSlotAction(GLFW.GLFW_MOUSE_BUTTON_LEFT, false, bucket),
                "server-side fluid-container click was accepted");
        helper.assertTrue(!BufferMachine.canSendFluidSlotAction(GLFW.GLFW_MOUSE_BUTTON_RIGHT, true, bucket),
                "client right click with a fluid container was accepted");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRoutesValidClickToSelectedTankAndPlayerCursor(GameTestHelper helper) {
        BufferMachine machine = createMachine();
        machine.getTank().setFluidInTank(0, new FluidStack(Fluids.WATER, 3 * FluidType.BUCKET_VOLUME));
        machine.getTank().setFluidInTank(1, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(1), new JsonPrimitive(false)));

        helper.assertTrue(result, "valid buffer fluid-slot action was rejected");
        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == 3 * FluidType.BUCKET_VOLUME,
                "selected tank action changed a different buffer tank");
        helper.assertTrue(machine.getTank().getFluidInTank(1).getAmount() == FluidType.BUCKET_VOLUME,
                "selected tank action did not transfer exactly one bucket");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "selected tank action did not return the filled container to the opening player cursor");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherIgnoresNonFluidCursorWithoutMutation(GameTestHelper helper) {
        BufferMachine machine = createMachine();
        int initialAmount = 2 * FluidType.BUCKET_VOLUME;
        machine.getTank().setFluidInTank(0, new FluidStack(Fluids.WATER, initialAmount));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.STONE));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(0), new JsonPrimitive(false)));

        helper.assertTrue(result, "valid buffer action with a non-fluid cursor was rejected");
        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == initialAmount,
                "non-fluid cursor changed the selected buffer tank");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.STONE) &&
                player.containerMenu.getCarried().getCount() == 1,
                "non-fluid buffer action changed the opening player cursor");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void shiftedActionProcessesEveryCarriedContainer(GameTestHelper helper) {
        BufferMachine machine = createMachine();
        machine.getTank().setFluidInTank(0, new FluidStack(Fluids.WATER, 4 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET, 3));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(0), new JsonPrimitive(true)));

        int filledContainers = player.getInventory().countItem(Items.WATER_BUCKET) +
                (player.containerMenu.getCarried().is(Items.WATER_BUCKET) ?
                        player.containerMenu.getCarried().getCount() : 0);
        helper.assertTrue(result, "valid shifted buffer fluid-slot action was rejected");
        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "shifted buffer action did not process every carried container");
        helper.assertTrue(filledContainers == 3,
                "shifted buffer action did not preserve all filled container results");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsInvalidHolderPayloadAndIndexWithoutMutation(GameTestHelper helper) {
        BufferMachine machine = createMachine();
        int initialAmount = 2 * FluidType.BUCKET_VOLUME;
        machine.getTank().setFluidInTank(0, new FluidStack(Fluids.WATER, initialAmount));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));

        boolean wrongHolder = dispatch(player, new Object(),
                payload(new JsonPrimitive(0), new JsonPrimitive(false)));
        boolean missingFields = dispatch(player, machine, DataComponentMap.EMPTY);
        boolean missingShift = dispatch(player, machine, payloadWithOnlyTank(new JsonPrimitive(0)));
        boolean negativeIndex = dispatch(player, machine,
                payload(new JsonPrimitive(-1), new JsonPrimitive(false)));
        boolean overflowIndex = dispatch(player, machine,
                payload(new JsonPrimitive(machine.getTank().getTanks()), new JsonPrimitive(false)));

        helper.assertTrue(!wrongHolder, "buffer fluid-slot action accepted a non-buffer holder");
        helper.assertTrue(!missingFields, "buffer fluid-slot action accepted missing field data");
        helper.assertTrue(!missingShift, "buffer fluid-slot action accepted a missing shift field");
        helper.assertTrue(!negativeIndex, "buffer fluid-slot action accepted a negative tank index");
        helper.assertTrue(!overflowIndex, "buffer fluid-slot action accepted an out-of-range tank index");
        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == initialAmount,
                "rejected buffer fluid-slot actions changed tank state");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET) &&
                player.containerMenu.getCarried().getCount() == 1,
                "rejected buffer fluid-slot actions changed the opening player cursor");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsSpectatorWithoutMutation(GameTestHelper helper) {
        BufferMachine machine = createMachine();
        int initialAmount = 2 * FluidType.BUCKET_VOLUME;
        machine.getTank().setFluidInTank(0, new FluidStack(Fluids.WATER, initialAmount));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));
        player.setGameMode(GameType.SPECTATOR);

        boolean result = dispatch(player, machine,
                payload(new JsonPrimitive(0), new JsonPrimitive(false)));
        player.setGameMode(GameType.SURVIVAL);

        helper.assertTrue(!result, "buffer fluid-slot action accepted a spectator");
        helper.assertTrue(machine.getTank().getFluidInTank(0).getAmount() == initialAmount,
                "spectator buffer action changed tank state");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET),
                "spectator buffer action changed the opening player cursor");
        helper.succeed();
    }

    private static BufferMachine createMachine() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        return new BufferMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), GTValues.LV);
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper, ItemStack carried) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.containerMenu.setCarried(carried);
        return player;
    }

    private static boolean dispatch(ServerPlayer player, Object holder, DataComponentMap payload) {
        SyncActionData action = new SyncActionData(CLICK_BUFFER_FLUID_SLOT_ACTION, 0, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(JsonElement tankIndex, JsonElement shiftDown) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TANK_FIELD, tankIndex)
                        .put(SHIFT_FIELD, shiftDown)
                        .build())
                .build();
    }

    private static DataComponentMap payloadWithOnlyTank(JsonElement tankIndex) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TANK_FIELD, tankIndex)
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(SyncActionData action) {
        SyncFieldData fields = action.payload().get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Buffer fluid-slot creator omitted its field payload.");
        }
        return fields;
    }
}
