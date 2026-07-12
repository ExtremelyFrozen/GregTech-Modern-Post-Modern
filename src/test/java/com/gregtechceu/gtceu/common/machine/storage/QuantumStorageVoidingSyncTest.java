package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class QuantumStorageVoidingSyncTest {

    private static final String BATCH = "QuantumStorageVoidingSync";
    private static final long TEST_CAPACITY = 1_000_000L;
    private static final ResourceLocation VOIDING_FIELD = SyncFieldData.key("isVoiding");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void chestAndTankUpdatesCommitAndAcknowledgeChangedAndNoOpCandidates(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestQuantumChestMachine chest = createChest(false);
        TestQuantumTankMachine tank = createTank(false);
        chest.getSyncDataHolder().serializeFullClientSyncData(registries);
        tank.getSyncDataHolder().serializeFullClientSyncData(registries);

        assertChangedEnabled(helper, chest, registries, "quantum chest");
        assertChangedEnabled(helper, tank, registries, "quantum tank");
        assertUnchangedEnabled(helper, chest, registries, "quantum chest");
        assertUnchangedEnabled(helper, tank, registries, "quantum tank");
        assertChangedDisabled(helper, chest, registries, "quantum chest");
        assertChangedDisabled(helper, tank, registries, "quantum tank");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void chestAndTankTogglesUpdateClientFieldsFlushAndConsumeEvents(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestQuantumChestMachine chest = createChest(true);
        TestQuantumTankMachine tank = createTank(true);
        GTToggleButtonElement chestToggle = chest.createLDLib2VoidingButton();
        GTToggleButtonElement tankToggle = tank.createLDLib2VoidingButton();
        UIEvent chestEvent = UIEvent.create(UIEvents.MOUSE_DOWN);
        UIEvent tankEvent = UIEvent.create(UIEvents.MOUSE_DOWN);

        chestToggle.onClick(chestEvent);
        tankToggle.onClick(tankEvent);

        assertCurrent(helper, chest, registries, true, "quantum chest toggle");
        assertCurrent(helper, tank, registries, true, "quantum tank toggle");
        helper.assertTrue(chest.syncRequests == 1 && tank.syncRequests == 1,
                "quantum storage voiding toggles did not flush each machine exactly once");
        helper.assertTrue(chestEvent.hasHandler && tankEvent.hasHandler,
                "quantum storage voiding toggle events were not consumed");
        assertField(helper, chest.getSyncDataHolder().collectServerNetworkChanges(registries), true,
                "quantum chest toggle request");
        assertField(helper, tank.getSyncDataHolder().collectServerNetworkChanges(registries), true,
                "quantum tank toggle request");
        helper.succeed();
    }

    private static void assertChangedEnabled(GameTestHelper helper, MetaMachine machine, RegistryAccess registries,
                                             String description) {
        ServerFieldUpdateResult result = apply(machine, registries, true);

        helper.assertTrue(result.getAccepted() && result.getChanged(),
                description + " enable candidate was not accepted as changed");
        assertCurrent(helper, machine, registries, true, description + " enabled state");
        assertAcknowledgement(helper, machine, registries, true,
                description + " changed candidate acknowledgement");
    }

    private static void assertUnchangedEnabled(GameTestHelper helper, MetaMachine machine, RegistryAccess registries,
                                               String description) {
        ServerFieldUpdateResult result = apply(machine, registries, true);

        helper.assertTrue(result.getAccepted() && !result.getChanged(),
                description + " repeated candidate was not accepted as unchanged");
        assertCurrent(helper, machine, registries, true, description + " unchanged state");
        assertAcknowledgement(helper, machine, registries, true,
                description + " no-op candidate acknowledgement");
    }

    private static void assertChangedDisabled(GameTestHelper helper, MetaMachine machine, RegistryAccess registries,
                                              String description) {
        ServerFieldUpdateResult result = apply(machine, registries, false);

        helper.assertTrue(result.getAccepted() && result.getChanged(),
                description + " disable candidate was not accepted as changed");
        assertCurrent(helper, machine, registries, false, description + " disabled state");
    }

    private static ServerFieldUpdateResult apply(MetaMachine machine, RegistryAccess registries, boolean candidate) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, payload(candidate));
    }

    private static void assertCurrent(GameTestHelper helper, MetaMachine machine, RegistryAccess registries,
                                      boolean expected, String description) {
        JsonElement value = machine.getSyncDataHolder().serializeToSaveFieldData(registries).get(VOIDING_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected voiding state");
    }

    private static void assertAcknowledgement(GameTestHelper helper, MetaMachine machine, RegistryAccess registries,
                                              boolean expected, String description) {
        SyncFieldData fields = machine.getSyncDataHolder().serializeToFieldData(registries, true, false);
        JsonElement value = fields.get(VOIDING_FIELD);
        helper.assertTrue(fields.fields().size() == 1 && value instanceof JsonPrimitive primitive &&
                primitive.isBoolean() && primitive.getAsBoolean() == expected,
                description + " did not contain only the authoritative voiding field");
    }

    private static void assertField(GameTestHelper helper, DataComponentMap components, boolean expected,
                                    String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        JsonElement value = fields == null ? null : fields.get(VOIDING_FIELD);
        helper.assertTrue(fields != null && fields.fields().size() == 1 &&
                value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain only the expected voiding field");
    }

    private static DataComponentMap payload(boolean voiding) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(VOIDING_FIELD, new JsonPrimitive(voiding))
                        .build())
                .build();
    }

    private static TestQuantumChestMachine createChest(boolean clientSide) {
        var definition = GTMachines.QUANTUM_CHEST[GTValues.IV];
        return new TestQuantumChestMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()),
                clientSide);
    }

    private static TestQuantumTankMachine createTank(boolean clientSide) {
        var definition = GTMachines.QUANTUM_TANK[GTValues.IV];
        return new TestQuantumTankMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()),
                clientSide);
    }

    private static final class TestQuantumChestMachine extends QuantumChestMachine {

        private final boolean clientSide;
        private int syncRequests;

        private TestQuantumChestMachine(BlockEntityCreationInfo info, boolean clientSide) {
            super(info, GTValues.IV, TEST_CAPACITY);
            this.clientSide = clientSide;
        }

        @Override
        public boolean isRemote() {
            return clientSide;
        }

        @Override
        public void sendServerSyncChanges() {
            syncRequests++;
        }
    }

    private static final class TestQuantumTankMachine extends QuantumTankMachine {

        private final boolean clientSide;
        private int syncRequests;

        private TestQuantumTankMachine(BlockEntityCreationInfo info, boolean clientSide) {
            super(info, GTValues.IV, TEST_CAPACITY);
            this.clientSide = clientSide;
        }

        @Override
        public boolean isRemote() {
            return clientSide;
        }

        @Override
        public void sendServerSyncChanges() {
            syncRequests++;
        }
    }
}
