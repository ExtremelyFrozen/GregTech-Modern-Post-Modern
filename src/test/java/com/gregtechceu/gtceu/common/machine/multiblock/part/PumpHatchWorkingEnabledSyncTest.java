package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
public class PumpHatchWorkingEnabledSyncTest {

    private static final String BATCH = "PumpHatchWorkingEnabledSync";
    private static final ResourceLocation CLICK_PUMP_HATCH_FLUID_SLOT_ACTION = GTCEu
            .id("click_pump_hatch_fluid_slot");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void pumpHatchRootAcceptsChangedAndNoOpWorkingEnabledUpdates(GameTestHelper helper) {
        PumpHatchPartMachine machine = createPumpHatch();
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult changed = apply(machine, registries, false);

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                "pump hatch rejected a changed working-enabled root update");
        helper.assertTrue(!machine.isWorkingEnabled(),
                "pump hatch did not commit the changed working-enabled root value");
        assertOnlyWorkingEnabled(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), false,
                "changed pump hatch acknowledgement");

        ServerFieldUpdateResult noOp = apply(machine, registries, false);

        helper.assertTrue(noOp.getAccepted() && !noOp.getChanged(),
                "pump hatch did not accept an identical working-enabled root update as a no-op");
        helper.assertTrue(!machine.isWorkingEnabled(),
                "pump hatch no-op update changed the working-enabled root value");
        assertOnlyWorkingEnabled(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), false,
                "no-op pump hatch acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2ToggleUpdatesRootFlushesOwnerAndConsumesEvent(GameTestHelper helper) {
        TestClientPumpHatch machine = createClientPumpHatch();
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().collectServerNetworkChanges(registries);
        GTToggleButtonElement toggle = machine.createLDLib2WorkingEnabledToggle();
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);

        toggle.onClick(event);

        helper.assertTrue(!machine.isWorkingEnabled(),
                "LDLib2 pump hatch toggle did not update the client root field");
        helper.assertTrue(machine.syncRequests == 1,
                "LDLib2 pump hatch toggle did not flush the owner exactly once");
        helper.assertTrue(event.hasHandler,
                "LDLib2 pump hatch toggle did not consume its handled event");
        SyncFieldData candidates = requireFieldData(
                machine.getSyncDataHolder().collectServerNetworkChanges(registries));
        assertOnlyWorkingEnabled(helper, candidates, false, "LDLib2 pump hatch root candidate");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fluidSlotActionRemainsRegisteredAndTransfersWater(GameTestHelper helper) {
        PumpHatchPartMachine machine = createPumpHatch();
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));

        boolean dispatched = dispatchFluidSlotAction(player, machine, false);

        helper.assertTrue(dispatched, "valid pump hatch fluid-slot action was not registered or accepted");
        helper.assertTrue(machine.tank.getFluidInTank(0).isEmpty(),
                "pump hatch fluid-slot action did not drain one bucket from the hatch");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "pump hatch fluid-slot action did not fill the carried bucket");
        helper.succeed();
    }

    private static ServerFieldUpdateResult apply(PumpHatchPartMachine machine, RegistryAccess registries,
                                                 boolean workingEnabled) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(WORKING_ENABLED_FIELD, workingEnabled));
    }

    private static boolean dispatchFluidSlotAction(ServerPlayer player, PumpHatchPartMachine machine,
                                                   boolean shiftDown) {
        DataComponentMap payload = payload(SHIFT_FIELD, shiftDown);
        SyncActionData action = new SyncActionData(CLICK_PUMP_HATCH_FLUID_SLOT_ACTION, shiftDown ? 1 : 0, payload);
        SyncActionContext context = new SyncActionContext(player, machine, action, BlockPos.ZERO,
                null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(ResourceLocation field, boolean value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, new JsonPrimitive(value))
                        .build())
                .build();
    }

    private static SyncFieldData requireFieldData(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Pump hatch working-enabled update omitted sync field data.");
        }
        return fields;
    }

    private static void assertOnlyWorkingEnabled(GameTestHelper helper, SyncFieldData fields, boolean expected,
                                                 String description) {
        JsonElement value = fields.get(WORKING_ENABLED_FIELD);
        helper.assertTrue(fields.fields().size() == 1 && value instanceof JsonPrimitive primitive &&
                primitive.isBoolean() && primitive.getAsBoolean() == expected,
                description + " did not contain only the authoritative working-enabled field");
    }

    private static PumpHatchPartMachine createPumpHatch() {
        MetaMachine machine = GTMachines.PUMP_HATCH.getBlockEntityType()
                .create(BlockPos.ZERO, GTMachines.PUMP_HATCH.defaultBlockState());
        if (!(machine instanceof PumpHatchPartMachine pumpHatch)) {
            throw new IllegalStateException("Pump hatch definition did not create a pump hatch machine.");
        }
        return pumpHatch;
    }

    private static TestClientPumpHatch createClientPumpHatch() {
        return new TestClientPumpHatch(new BlockEntityCreationInfo(
                GTMachines.PUMP_HATCH.getBlockEntityType(), BlockPos.ZERO,
                GTMachines.PUMP_HATCH.defaultBlockState()));
    }

    private static final class TestClientPumpHatch extends PumpHatchPartMachine {

        private int syncRequests;

        private TestClientPumpHatch(BlockEntityCreationInfo info) {
            super(info);
        }

        @Override
        public boolean isRemote() {
            return true;
        }

        @Override
        public void sendServerSyncChanges() {
            syncRequests++;
        }
    }
}
