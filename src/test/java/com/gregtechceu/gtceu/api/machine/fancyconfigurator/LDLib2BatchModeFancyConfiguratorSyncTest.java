package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2BatchModeFancyConfiguratorSyncTest {

    private static final String BATCH = "LDLib2BatchModeFancyConfiguratorSync";
    private static final ResourceLocation BATCH_ENABLED_FIELD = SyncFieldData.key("batchEnabled");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("unknown");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void supportedUpdatesCommitAndAcknowledgeChangedAndNoOpCandidates(GameTestHelper helper) {
        TestBatchModeMachine machine = createMachine(true, false);
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult changed = apply(machine, registries, new JsonPrimitive(true));

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                "supported batch-mode candidate was not accepted as changed");
        helper.assertTrue(machine.isBatchEnabled(), "supported batch-mode candidate did not update the field");
        assertAcknowledgement(helper, machine, registries, true,
                "changed batch-mode candidate did not request an authoritative acknowledgement");

        ServerFieldUpdateResult unchanged = apply(machine, registries, new JsonPrimitive(true));

        helper.assertTrue(unchanged.getAccepted() && !unchanged.getChanged(),
                "repeated batch-mode candidate was not accepted as unchanged");
        helper.assertTrue(machine.isBatchEnabled(), "repeated batch-mode candidate changed the canonical field");
        assertAcknowledgement(helper, machine, registries, true,
                "unchanged batch-mode candidate did not request an authoritative acknowledgement");

        ServerFieldUpdateResult disabled = apply(machine, registries, new JsonPrimitive(false));

        helper.assertTrue(disabled.getAccepted() && disabled.getChanged(),
                "supported batch-mode disable candidate was not accepted as changed");
        helper.assertTrue(!machine.isBatchEnabled(), "supported batch-mode disable candidate did not update the field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void unsupportedAndMalformedCandidatesAreRejectedWithoutChangingState(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestBatchModeMachine unsupported = createMachine(false, false);
        unsupported.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult unsupportedResult = apply(unsupported, registries, new JsonPrimitive(true));

        helper.assertTrue(!unsupportedResult.getAccepted(), "unsupported machine accepted a batch-mode candidate");
        helper.assertTrue(!unsupported.isBatchEnabled(), "unsupported batch-mode candidate changed the field");
        assertAcknowledgement(helper, unsupported, registries, false,
                "unsupported batch-mode candidate did not request the canonical acknowledgement");

        TestBatchModeMachine supported = createMachine(true, false);
        assertRejectedDisabled(helper, supported, registries,
                payload(BATCH_ENABLED_FIELD, new JsonPrimitive("true")), "string candidate");
        assertRejectedDisabled(helper, supported, registries,
                payload(BATCH_ENABLED_FIELD, JsonNull.INSTANCE), "null candidate");
        assertRejectedDisabled(helper, supported, registries,
                payload(UNKNOWN_FIELD, new JsonPrimitive(true)), "unknown field");
        assertRejectedDisabled(helper, supported, registries,
                DataComponentMap.builder()
                        .set(DataComponents.CUSTOM_NAME, Component.literal("missing sync field data"))
                        .build(),
                "missing sync component");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void roundTripAppliesAuthoritativeAcknowledgementWithoutClientEcho(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestBatchModeMachine server = createMachine(true, false);
        TestBatchModeMachine client = createMachine(true, true);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries,
                server.getSyncDataHolder().serializeFullClientSyncComponents(registries));

        client.setBatchEnabled(true);
        DataComponentMap request = client.getSyncDataHolder().collectServerNetworkChanges(registries);
        assertField(helper, request, true, "client batch-mode request");

        ServerFieldUpdateResult result = server.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, request);

        helper.assertTrue(result.getAccepted() && result.getChanged(),
                "client batch-mode request was not accepted as changed");
        DataComponentMap acknowledgement = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertField(helper, acknowledgement, true, "server batch-mode acknowledgement");

        client.getSyncDataHolder().applyClientNetworkUpdate(registries, acknowledgement);

        helper.assertTrue(client.isBatchEnabled(), "authoritative acknowledgement did not preserve client batch mode");
        helper.assertTrue(client.getSyncDataHolder().collectServerNetworkChanges(registries).isEmpty(),
                "client echoed the authoritative batch-mode acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2ToggleChangesClientFieldFlushesSyncAndConsumesEvent(GameTestHelper helper) {
        TestBatchModeMachine machine = createMachine(true, true);
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);
        LDLib2ConfiguratorPanelElement panel = new LDLib2ConfiguratorPanelElement(new TestMachineUIHolder(machine), 0,
                0);
        LDLib2FancyConfiguratorButton.Toggle toggle = LDLib2BatchModeFancyConfigurator
                .createBatchModeConfigurator(panel, machine);
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);

        toggle.onClick(event);

        helper.assertTrue(machine.isBatchEnabled(), "LDLib2 batch-mode toggle did not update the client field");
        helper.assertTrue(machine.syncRequests == 1, "LDLib2 batch-mode toggle did not flush machine field sync once");
        helper.assertTrue(event.hasHandler, "LDLib2 batch-mode toggle event was not consumed after field sync");
        assertField(helper, machine.getSyncDataHolder().collectServerNetworkChanges(registries),
                true, "LDLib2 batch-mode toggle request");
        helper.succeed();
    }

    private static TestBatchModeMachine createMachine(boolean supportsBatchMode, boolean clientSide) {
        var definition = GTMultiMachines.ELECTRIC_BLAST_FURNACE;
        return new TestBatchModeMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()),
                supportsBatchMode, clientSide);
    }

    private static ServerFieldUpdateResult apply(TestBatchModeMachine machine, RegistryAccess registries,
                                                 JsonElement candidate) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(BATCH_ENABLED_FIELD, candidate));
    }

    private static void assertRejectedDisabled(GameTestHelper helper, TestBatchModeMachine machine,
                                               RegistryAccess registries, DataComponentMap components,
                                               String description) {
        ServerFieldUpdateResult result = machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                components);

        helper.assertTrue(!result.getAccepted(), description + " was accepted");
        helper.assertTrue(!machine.isBatchEnabled(), description + " changed batch mode");
    }

    private static void assertAcknowledgement(GameTestHelper helper, TestBatchModeMachine machine,
                                              RegistryAccess registries, boolean expected, String message) {
        SyncFieldData fields = machine.getSyncDataHolder().serializeToFieldData(registries, true, false);
        JsonElement value = fields.get(BATCH_ENABLED_FIELD);
        helper.assertTrue(fields.fields().size() == 1 && value instanceof JsonPrimitive primitive &&
                primitive.isBoolean() && primitive.getAsBoolean() == expected,
                message);
    }

    private static void assertField(GameTestHelper helper, DataComponentMap components, boolean expected,
                                    String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        JsonElement value = fields == null ? null : fields.get(BATCH_ENABLED_FIELD);
        helper.assertTrue(fields != null && fields.fields().size() == 1 &&
                value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain only the expected batch-mode field");
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static final class TestBatchModeMachine extends WorkableElectricMultiblockMachine {

        private final boolean supportsBatchMode;
        private final boolean clientSide;
        private int syncRequests;

        private TestBatchModeMachine(BlockEntityCreationInfo info, boolean supportsBatchMode, boolean clientSide) {
            super(info);
            this.supportsBatchMode = supportsBatchMode;
            this.clientSide = clientSide;
        }

        @Override
        public boolean supportsBatchMode() {
            return supportsBatchMode;
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

    private record TestMachineUIHolder(TestBatchModeMachine machine) implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return machine.getBlockPos();
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return machine.getDefinition().getId();
        }

        @Override
        public MetaMachine getMachine() {
            return machine;
        }
    }
}
