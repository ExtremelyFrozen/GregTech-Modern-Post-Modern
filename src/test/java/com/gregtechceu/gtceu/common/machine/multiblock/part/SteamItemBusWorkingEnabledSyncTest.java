package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
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
import org.lwjgl.glfw.GLFW;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SteamItemBusWorkingEnabledSyncTest {

    private static final String BATCH = "SteamItemBusWorkingEnabledSync";
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void steamImportBusAcceptsRootFieldChangesAndAcknowledges(GameTestHelper helper) {
        assertRootFieldSync(helper, createSteamBus(GTMachines.STEAM_IMPORT_BUS), "steam import bus");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void steamExportBusAcceptsRootFieldChangesAndAcknowledges(GameTestHelper helper) {
        assertRootFieldSync(helper, createSteamBus(GTMachines.STEAM_EXPORT_BUS), "steam export bus");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void realTogglesUpdateBothBusDirectionsAndFlushOnce(GameTestHelper helper) {
        assertToggleSync(helper, createRemoteSteamBus(GTMachines.STEAM_IMPORT_BUS, IO.IN), "steam import bus");
        assertToggleSync(helper, createRemoteSteamBus(GTMachines.STEAM_EXPORT_BUS, IO.OUT), "steam export bus");
        helper.succeed();
    }

    private static void assertRootFieldSync(GameTestHelper helper, SteamItemBusPartMachine machine, String owner) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        ServerFieldUpdateResult changed = apply(machine, registries, false);

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                owner + " rejected a changed working-enabled root field");
        helper.assertTrue(!machine.isWorkingEnabled(),
                owner + " did not commit the changed working-enabled root field");

        ServerFieldUpdateResult noOp = apply(machine, registries, false);

        helper.assertTrue(noOp.getAccepted() && !noOp.getChanged(),
                owner + " did not accept an identical root field update as a no-op");
        SyncFieldData acknowledgement = machine.getSyncDataHolder().serializeToFieldData(registries, true, false);
        assertWorkingEnabledField(helper, acknowledgement, false,
                owner + " did not acknowledge its authoritative root field");
    }

    private static void assertToggleSync(GameTestHelper helper, TestSteamItemBusPartMachine machine, String owner) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().collectServerNetworkChanges(registries);
        GTToggleButtonElement toggle = machine.createLDLib2WorkingEnabledToggle(0, 0);
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);

        event.target = toggle;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);

        helper.assertTrue(!machine.isWorkingEnabled(), owner + " toggle did not update its local root field");
        helper.assertTrue(machine.syncRequests == 1, owner + " toggle did not flush exactly once");
        helper.assertTrue(event.hasHandler, owner + " toggle did not consume its click event");

        DataComponentMap candidates = machine.getSyncDataHolder().collectServerNetworkChanges(registries);
        SyncFieldData fields = candidates.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(fields != null && fields.fields().size() == 1,
                owner + " toggle did not collect exactly one changed root field");
        assertWorkingEnabledField(helper, fields, false,
                owner + " toggle did not expose the working-enabled C2S candidate");
    }

    private static ServerFieldUpdateResult apply(MetaMachine machine, RegistryAccess registries,
                                                 boolean workingEnabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(workingEnabled))
                        .build())
                .build();
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, payload);
    }

    private static void assertWorkingEnabledField(GameTestHelper helper, SyncFieldData fields,
                                                  boolean expectedValue, String message) {
        JsonElement workingEnabled = fields.get(WORKING_ENABLED_FIELD);
        helper.assertTrue(workingEnabled instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expectedValue, message);
    }

    private static SteamItemBusPartMachine createSteamBus(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof SteamItemBusPartMachine steamItemBus)) {
            throw new IllegalStateException("Steam item bus definition did not create a steam item bus machine.");
        }
        return steamItemBus;
    }

    private static TestSteamItemBusPartMachine createRemoteSteamBus(MachineDefinition definition, IO io) {
        return new TestSteamItemBusPartMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), io);
    }

    private static final class TestSteamItemBusPartMachine extends SteamItemBusPartMachine {

        private int syncRequests;

        private TestSteamItemBusPartMachine(BlockEntityCreationInfo info, IO io) {
            super(info, io);
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
