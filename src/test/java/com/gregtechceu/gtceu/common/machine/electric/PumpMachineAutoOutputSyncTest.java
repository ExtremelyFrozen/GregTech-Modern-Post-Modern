package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.network.packets.CPacketMachineSyncToServer;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class PumpMachineAutoOutputSyncTest {

    private static final String BATCH = "PumpMachineAutoOutputSync";
    private static final BlockPos PUMP_POS = new BlockPos(1, 1, 1);
    private static final ResourceLocation AUTO_OUTPUT_ITEMS_FIELD = SyncFieldData.key("autoOutputItems");
    private static final ResourceLocation AUTO_OUTPUT_FLUIDS_FIELD = SyncFieldData.key("autoOutputFluids");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void pumpOwnerRoutesFluidAutoOutputTraitUpdateAndAcknowledges(GameTestHelper helper) {
        PumpMachine pump = setPump(helper);
        RegistryAccess registries = helper.getLevel().registryAccess();
        pump.autoOutput.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        ServerPlayer player = preparePlayer(helper, pump, "pump_auto_output");

        execute(player, CPacketMachineSyncToServer.forMachineTrait(pump, pump.autoOutput,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, true)));

        helper.assertTrue(pump.autoOutput.isAutoOutputFluids(),
                "pump owner did not route fluid auto-output update to its attached trait");
        SyncFieldData acknowledgement = pump.autoOutput.getSyncDataHolder()
                .serializeToFieldData(registries, true, false);
        assertBooleanField(helper, acknowledgement, AUTO_OUTPUT_FLUIDS_FIELD, true,
                "pump fluid auto-output update did not request its authoritative acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void pumpOwnerRejectsUnsupportedItemAutoOutputField(GameTestHelper helper) {
        PumpMachine pump = setPump(helper);
        RegistryAccess registries = helper.getLevel().registryAccess();
        pump.autoOutput.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        ServerPlayer player = preparePlayer(helper, pump, "pump_item_auto_output_reject");

        execute(player, CPacketMachineSyncToServer.forMachineTrait(pump, pump.autoOutput,
                payload(AUTO_OUTPUT_ITEMS_FIELD, true)));

        helper.assertTrue(!pump.autoOutput.isAutoOutputItems() && !pump.autoOutput.isAutoOutputFluids(),
                "unsupported item auto-output update changed the pump trait");
        SyncFieldData acknowledgement = pump.autoOutput.getSyncDataHolder()
                .serializeToFieldData(registries, true, false);
        assertBooleanField(helper, acknowledgement, AUTO_OUTPUT_ITEMS_FIELD, false,
                "rejected pump item auto-output update did not acknowledge its canonical state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2ToggleChangesFluidTraitFlushesMachineAndConsumesEvent(GameTestHelper helper) {
        TestPumpMachine pump = createClientPump();
        RegistryAccess registries = helper.getLevel().registryAccess();
        pump.autoOutput.getSyncDataHolder().collectServerNetworkChanges(registries);
        GTToggleButtonElement toggle = pump.createLDLib2FluidAutoOutputToggle();
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);

        toggle.onClick(event);

        helper.assertTrue(pump.autoOutput.isAutoOutputFluids(),
                "LDLib2 pump toggle did not update the client fluid auto-output field");
        helper.assertTrue(pump.syncRequests == 1,
                "LDLib2 pump toggle did not flush the owning machine exactly once");
        helper.assertTrue(event.hasHandler,
                "LDLib2 pump toggle did not consume its handled event");

        DataComponentMap candidates = pump.autoOutput.getSyncDataHolder().collectServerNetworkChanges(registries);
        SyncFieldData fields = candidates.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(fields != null && fields.fields().size() == 1,
                "LDLib2 pump toggle emitted fields other than fluid auto-output");
        assertBooleanField(helper, fields, AUTO_OUTPUT_FLUIDS_FIELD, true,
                "LDLib2 pump toggle did not expose fluid auto-output to the trait C2S collector");
        helper.succeed();
    }

    private static PumpMachine setPump(GameTestHelper helper) {
        return (PumpMachine) TestUtils.setMachine(helper, PUMP_POS, GTMachines.PUMP[GTValues.LV]);
    }

    private static TestPumpMachine createClientPump() {
        var definition = GTMachines.PUMP[GTValues.LV];
        return new TestPumpMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), GTValues.LV);
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper, MetaMachine machine, String name) {
        UUID profileId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(profileId, name));
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(machine.getBlockPos()));
        return player;
    }

    private static void execute(ServerPlayer player, CPacketMachineSyncToServer packet) {
        packet.execute(new ServerPayloadContext(player.connection, CPacketMachineSyncToServer.ID));
    }

    private static DataComponentMap payload(ResourceLocation field, boolean value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, new JsonPrimitive(value))
                        .build())
                .build();
    }

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                           boolean expected, String message) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected, message);
    }

    private static final class TestPumpMachine extends PumpMachine {

        private int syncRequests;

        private TestPumpMachine(BlockEntityCreationInfo info, int tier) {
            super(info, tier);
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
