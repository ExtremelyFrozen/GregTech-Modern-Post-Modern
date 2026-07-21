package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.item.datacomponents.SimpleEnergyContent;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class BatteryBufferStateSyncTest {

    private static final String BATCH = "BatteryBufferStateSync";
    private static final int TIER = GTValues.LV;
    private static final int INVENTORY_SIZE = 4;
    private static final long VOLTAGE = GTValues.V[TIER];
    private static final long BATTERY_CAPACITY = VOLTAGE * 4L;
    private static final ResourceLocation STATE_FIELD = SyncFieldData.key("state");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void stateUsesChangedOnlyServerOwnedSync(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestBatteryBufferMachine server = createBatteryBuffer();
        TestBatteryBufferMachine client = createBatteryBuffer();

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertStateField(helper, full, BatteryBufferMachine.State.IDLE, "battery buffer full sync");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        helper.assertTrue(client.getState() == BatteryBufferMachine.State.IDLE,
                "battery buffer full sync did not initialize the client state");

        server.getBatteryInventory().setStackInSlot(0, battery(0L));
        long accepted = server.energyContainer.acceptEnergyFromNetwork(Direction.UP, VOLTAGE, 1L);
        helper.assertTrue(accepted == 1L && server.getState() == BatteryBufferMachine.State.RUNNING,
                "battery buffer charging did not enter the running state");

        DataComponentMap changed = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        SyncFieldData changedFields = requireFields(changed, "battery buffer running delta");
        helper.assertTrue(changedFields.fields().size() == 1,
                "battery buffer running delta contained fields other than state");
        assertStateField(helper, changedFields, BatteryBufferMachine.State.RUNNING,
                "battery buffer running delta");

        client.resetRenderUpdates();
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, changed);
        helper.assertTrue(client.getState() == BatteryBufferMachine.State.RUNNING,
                "battery buffer running delta did not update the client state");
        helper.assertTrue(client.renderUpdates == 1,
                "battery buffer running delta did not rerender the state exactly once");

        accepted = server.energyContainer.acceptEnergyFromNetwork(Direction.UP, VOLTAGE, 1L);
        helper.assertTrue(accepted == 1L && server.getState() == BatteryBufferMachine.State.RUNNING,
                "battery buffer repeated charging changed its running state");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged battery buffer state produced a redundant delta");

        ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, statePayload(BatteryBufferMachine.State.FINISHED));
        helper.assertTrue(!rejected.getAccepted(),
                "battery buffer accepted a client write to its server-owned state");
        helper.assertTrue(server.getState() == BatteryBufferMachine.State.RUNNING,
                "rejected battery buffer client write changed the server state");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "rejected battery buffer client write produced an acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void chargerTransitionsKeepRenderStateAligned(GameTestHelper helper) {
        TestBatteryBufferMachine charger = createCharger();
        charger.getBatteryInventory().setStackInSlot(0, battery(0L));
        assertChargerState(helper, charger, BatteryBufferMachine.State.IDLE, "empty charger battery");

        long accepted = charger.energyContainer.acceptEnergyFromNetwork(Direction.UP, VOLTAGE, 1L);
        helper.assertTrue(accepted == 1L, "charger did not accept its first energy packet");
        assertChargerState(helper, charger, BatteryBufferMachine.State.RUNNING, "charging battery");

        charger.getBatteryInventory().setStackInSlot(0, battery(BATTERY_CAPACITY));
        assertChargerState(helper, charger, BatteryBufferMachine.State.FINISHED, "full charger battery");

        charger.getBatteryInventory().setStackInSlot(0, ItemStack.EMPTY);
        assertChargerState(helper, charger, BatteryBufferMachine.State.IDLE, "charger without battery");
        helper.succeed();
    }

    private static TestBatteryBufferMachine createBatteryBuffer() {
        var definition = GTMachines.BATTERY_BUFFER_4[TIER];
        return new TestBatteryBufferMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), false);
    }

    private static TestBatteryBufferMachine createCharger() {
        var definition = GTMachines.CHARGER_4[TIER];
        return new TestBatteryBufferMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), true);
    }

    private static ItemStack battery(long charge) {
        ItemStack stack = GTItems.BATTERY_LV_LITHIUM.asStack();
        stack.set(GTDataComponents.ENERGY_CONTENT, new SimpleEnergyContent(BATTERY_CAPACITY, charge));
        return stack;
    }

    private static DataComponentMap statePayload(BatteryBufferMachine.State state) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(STATE_FIELD, new JsonPrimitive(state.getSerializedName()))
                        .build())
                .build();
    }

    private static void assertChargerState(GameTestHelper helper, BatteryBufferMachine charger,
                                           BatteryBufferMachine.State expected, String description) {
        helper.assertTrue(charger.getState() == expected,
                description + " did not set the expected charger state");
        helper.assertTrue(charger.getRenderState().getValue(BatteryBufferMachine.STATE_PROPERTY) == expected,
                description + " did not set the matching charger render state");
    }

    private static void assertStateField(GameTestHelper helper, DataComponentMap components,
                                         BatteryBufferMachine.State expected, String description) {
        assertStateField(helper, requireFields(components, description), expected, description);
    }

    private static void assertStateField(GameTestHelper helper, SyncFieldData fields,
                                         BatteryBufferMachine.State expected, String description) {
        JsonElement value = fields.get(STATE_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isString() &&
                primitive.getAsString().equals(expected.getSerializedName()),
                description + " did not contain the expected state");
    }

    private static SyncFieldData requireFields(DataComponentMap components, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException(description + " omitted sync field data");
        }
        return fields;
    }

    private static final class TestBatteryBufferMachine extends BatteryBufferMachine {

        private int renderUpdates;

        private TestBatteryBufferMachine(BlockEntityCreationInfo info, boolean charger) {
            super(info, TIER, INVENTORY_SIZE,
                    charger ? AMPS_PER_BATTERY_CHARGER : AMPS_PER_BATTERY_NORMAL,
                    charger ? 0L : INVENTORY_SIZE);
        }

        @Override
        public void scheduleRenderUpdate() {
            super.scheduleRenderUpdate();
            renderUpdates++;
        }

        private void resetRenderUpdates() {
            renderUpdates = 0;
        }
    }
}
