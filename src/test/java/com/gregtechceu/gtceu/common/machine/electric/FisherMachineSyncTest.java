package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
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
public class FisherMachineSyncTest {

    private static final String BATCH = "FisherMachineSync";
    private static final ResourceLocation JUNK_ENABLED_FIELD = SyncFieldData.key("junkEnabled");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void serverUpdatesCommitAndAcknowledgeChangedAndNoOpCandidates(GameTestHelper helper) {
        TestFisherMachine machine = createMachine(false);
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult disabled = apply(machine, registries, false);

        helper.assertTrue(disabled.getAccepted() && disabled.getChanged(),
                "junk-mode disable candidate was not accepted as changed");
        helper.assertTrue(!machine.isJunkEnabled(), "junk-mode disable candidate did not update the field");
        assertAcknowledgement(helper, machine, registries, false,
                "changed junk-mode candidate did not request an authoritative acknowledgement");

        ServerFieldUpdateResult unchanged = apply(machine, registries, false);

        helper.assertTrue(unchanged.getAccepted() && !unchanged.getChanged(),
                "repeated junk-mode candidate was not accepted as unchanged");
        helper.assertTrue(!machine.isJunkEnabled(), "repeated junk-mode candidate changed the canonical field");
        assertAcknowledgement(helper, machine, registries, false,
                "unchanged junk-mode candidate did not request an authoritative acknowledgement");

        ServerFieldUpdateResult enabled = apply(machine, registries, true);

        helper.assertTrue(enabled.getAccepted() && enabled.getChanged(),
                "junk-mode enable candidate was not accepted as changed");
        helper.assertTrue(machine.isJunkEnabled(), "junk-mode enable candidate did not update the field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void roundTripAppliesAuthoritativeAcknowledgementWithoutClientEcho(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestFisherMachine server = createMachine(false);
        TestFisherMachine client = createMachine(true);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries,
                server.getSyncDataHolder().serializeFullClientSyncComponents(registries));

        client.setJunkEnabled(false);
        DataComponentMap request = client.getSyncDataHolder().collectServerNetworkChanges(registries);
        assertField(helper, request, false, "client junk-mode request");

        ServerFieldUpdateResult result = server.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, request);

        helper.assertTrue(result.getAccepted() && result.getChanged(),
                "client junk-mode request was not accepted as changed");
        DataComponentMap acknowledgement = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertField(helper, acknowledgement, false, "server junk-mode acknowledgement");

        client.getSyncDataHolder().applyClientNetworkUpdate(registries, acknowledgement);

        helper.assertTrue(!client.isJunkEnabled(), "authoritative acknowledgement changed the client junk mode");
        helper.assertTrue(client.getSyncDataHolder().collectServerNetworkChanges(registries).isEmpty(),
                "client echoed the authoritative junk-mode acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2ToggleChangesClientFieldFlushesSyncAndConsumesEvent(GameTestHelper helper) {
        TestFisherMachine machine = createMachine(true);
        GTToggleButtonElement toggle = machine.createLDLib2JunkButton(0, 0);
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);

        toggle.onClick(event);

        helper.assertTrue(!machine.isJunkEnabled(), "LDLib2 junk toggle did not update the client field");
        helper.assertTrue(machine.syncRequests == 1, "LDLib2 junk toggle did not flush machine field sync once");
        helper.assertTrue(event.hasHandler, "LDLib2 junk toggle event was not consumed after field sync");
        assertField(helper, machine.getSyncDataHolder().collectServerNetworkChanges(helper.getLevel().registryAccess()),
                false, "LDLib2 junk toggle request");
        helper.succeed();
    }

    private static TestFisherMachine createMachine(boolean clientSide) {
        var definition = GTMachines.FISHER[GTValues.LV];
        return new TestFisherMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()),
                GTValues.LV, clientSide);
    }

    private static ServerFieldUpdateResult apply(TestFisherMachine machine, RegistryAccess registries,
                                                 boolean candidate) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(candidate));
    }

    private static void assertAcknowledgement(GameTestHelper helper, TestFisherMachine machine,
                                              RegistryAccess registries, boolean expected, String message) {
        SyncFieldData fields = machine.getSyncDataHolder().serializeToFieldData(registries, true, false);
        JsonElement value = fields.get(JUNK_ENABLED_FIELD);
        helper.assertTrue(fields.fields().size() == 1 && value instanceof JsonPrimitive primitive &&
                primitive.isBoolean() && primitive.getAsBoolean() == expected,
                message);
    }

    private static void assertField(GameTestHelper helper, DataComponentMap components, boolean expected,
                                    String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        JsonElement value = fields == null ? null : fields.get(JUNK_ENABLED_FIELD);
        helper.assertTrue(fields != null && fields.fields().size() == 1 &&
                value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain only the expected junk-mode field");
    }

    private static DataComponentMap payload(boolean enabled) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(JUNK_ENABLED_FIELD, new JsonPrimitive(enabled))
                        .build())
                .build();
    }

    private static final class TestFisherMachine extends FisherMachine {

        private final boolean clientSide;
        private int syncRequests;

        private TestFisherMachine(BlockEntityCreationInfo info, int tier, boolean clientSide) {
            super(info, tier);
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
