package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
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
public class CreativeStorageWorkingEnabledSyncTest {

    private static final String BATCH = "CreativeStorageWorkingEnabledSync";
    private static final BlockPos CHEST_POS = new BlockPos(1, 1, 1);
    private static final BlockPos TANK_POS = new BlockPos(3, 1, 1);
    private static final ResourceLocation AUTO_OUTPUT_ITEMS_FIELD = SyncFieldData.key("autoOutputItems");
    private static final ResourceLocation AUTO_OUTPUT_FLUIDS_FIELD = SyncFieldData.key("autoOutputFluids");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creativeChestOwnerRoutesWorkingEnabledItemTraitUpdate(GameTestHelper helper) {
        CreativeChestMachine chest = setCreativeChest(helper);
        RegistryAccess registries = helper.getLevel().registryAccess();
        chest.autoOutput.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        ServerPlayer player = preparePlayer(helper, chest, "creative_chest_working_enabled");

        execute(player, CPacketMachineSyncToServer.forMachineTrait(chest, chest.autoOutput,
                payload(AUTO_OUTPUT_ITEMS_FIELD, true)));

        helper.assertTrue(chest.isWorkingEnabled() && chest.autoOutput.isAutoOutputItems(),
                "creative chest owner did not route working-enabled update to its item auto-output trait");
        assertOnlyBooleanField(helper,
                chest.autoOutput.getSyncDataHolder().serializeToFieldData(registries, true, false),
                AUTO_OUTPUT_ITEMS_FIELD, true,
                "creative chest working-enabled update acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creativeTankOwnerRoutesWorkingEnabledFluidTraitUpdate(GameTestHelper helper) {
        CreativeTankMachine tank = setCreativeTank(helper);
        RegistryAccess registries = helper.getLevel().registryAccess();
        tank.autoOutput.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        ServerPlayer player = preparePlayer(helper, tank, "creative_tank_working_enabled");

        execute(player, CPacketMachineSyncToServer.forMachineTrait(tank, tank.autoOutput,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, true)));

        helper.assertTrue(tank.isWorkingEnabled() && tank.autoOutput.isAutoOutputFluids(),
                "creative tank owner did not route working-enabled update to its fluid auto-output trait");
        assertOnlyBooleanField(helper,
                tank.autoOutput.getSyncDataHolder().serializeToFieldData(registries, true, false),
                AUTO_OUTPUT_FLUIDS_FIELD, true,
                "creative tank working-enabled update acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creativeOwnersRejectUnsupportedWorkingEnabledCounterparts(GameTestHelper helper) {
        CreativeChestMachine chest = setCreativeChest(helper);
        CreativeTankMachine tank = setCreativeTank(helper);
        RegistryAccess registries = helper.getLevel().registryAccess();
        chest.autoOutput.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        tank.autoOutput.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        ServerPlayer player = preparePlayer(helper, chest, "creative_working_enabled_reject");

        execute(player, CPacketMachineSyncToServer.forMachineTrait(chest, chest.autoOutput,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, true)));
        player.moveTo(Vec3.atCenterOf(tank.getBlockPos()));
        execute(player, CPacketMachineSyncToServer.forMachineTrait(tank, tank.autoOutput,
                payload(AUTO_OUTPUT_ITEMS_FIELD, true)));

        helper.assertTrue(!chest.isWorkingEnabled() && !chest.autoOutput.isAutoOutputFluids(),
                "unsupported fluid working-enabled update changed the creative chest trait");
        helper.assertTrue(!tank.isWorkingEnabled() && !tank.autoOutput.isAutoOutputItems(),
                "unsupported item working-enabled update changed the creative tank trait");
        assertOnlyBooleanField(helper,
                chest.autoOutput.getSyncDataHolder().serializeToFieldData(registries, true, false),
                AUTO_OUTPUT_FLUIDS_FIELD, false,
                "creative chest rejected fluid counterpart acknowledgement");
        assertOnlyBooleanField(helper,
                tank.autoOutput.getSyncDataHolder().serializeToFieldData(registries, true, false),
                AUTO_OUTPUT_ITEMS_FIELD, false,
                "creative tank rejected item counterpart acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2WorkingTogglesUpdateTraitsFlushOwnersAndConsumeEvents(GameTestHelper helper) {
        TestCreativeChestMachine chest = createClientChest();
        TestCreativeTankMachine tank = createClientTank();
        RegistryAccess registries = helper.getLevel().registryAccess();
        chest.autoOutput.getSyncDataHolder().collectServerNetworkChanges(registries);
        tank.autoOutput.getSyncDataHolder().collectServerNetworkChanges(registries);
        LDLib2FancyConfiguratorButton.Toggle chestToggle = chest.createLDLib2WorkingEnabledConfigurator();
        LDLib2FancyConfiguratorButton.Toggle tankToggle = tank.createLDLib2WorkingEnabledConfigurator();
        UIEvent chestEvent = UIEvent.create(UIEvents.MOUSE_DOWN);
        UIEvent tankEvent = UIEvent.create(UIEvents.MOUSE_DOWN);

        chestToggle.onClick(chestEvent);
        tankToggle.onClick(tankEvent);

        helper.assertTrue(chest.isWorkingEnabled() && chest.autoOutput.isAutoOutputItems(),
                "LDLib2 creative chest toggle did not update the item auto-output trait");
        helper.assertTrue(tank.isWorkingEnabled() && tank.autoOutput.isAutoOutputFluids(),
                "LDLib2 creative tank toggle did not update the fluid auto-output trait");
        helper.assertTrue(chest.syncRequests == 1 && tank.syncRequests == 1,
                "LDLib2 creative working toggles did not flush each owning machine exactly once");
        helper.assertTrue(chestEvent.hasHandler && tankEvent.hasHandler,
                "LDLib2 creative working toggle events were not consumed");
        assertOnlyBooleanField(helper,
                requireFieldData(chest.autoOutput.getSyncDataHolder().collectServerNetworkChanges(registries)),
                AUTO_OUTPUT_ITEMS_FIELD, true, "creative chest toggle candidate");
        assertOnlyBooleanField(helper,
                requireFieldData(tank.autoOutput.getSyncDataHolder().collectServerNetworkChanges(registries)),
                AUTO_OUTPUT_FLUIDS_FIELD, true, "creative tank toggle candidate");
        helper.succeed();
    }

    private static CreativeChestMachine setCreativeChest(GameTestHelper helper) {
        return (CreativeChestMachine) TestUtils.setMachine(helper, CHEST_POS, GTMachines.CREATIVE_ITEM);
    }

    private static CreativeTankMachine setCreativeTank(GameTestHelper helper) {
        return (CreativeTankMachine) TestUtils.setMachine(helper, TANK_POS, GTMachines.CREATIVE_FLUID);
    }

    private static TestCreativeChestMachine createClientChest() {
        var definition = GTMachines.CREATIVE_ITEM;
        return new TestCreativeChestMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static TestCreativeTankMachine createClientTank() {
        var definition = GTMachines.CREATIVE_FLUID;
        return new TestCreativeTankMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
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

    private static SyncFieldData requireFieldData(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Creative storage working-enabled update omitted sync field data.");
        }
        return fields;
    }

    private static void assertOnlyBooleanField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                               boolean expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(fields.fields().size() == 1 && value instanceof JsonPrimitive primitive &&
                primitive.isBoolean() && primitive.getAsBoolean() == expected,
                description + " did not contain only the expected boolean field");
    }

    private static final class TestCreativeChestMachine extends CreativeChestMachine {

        private int syncRequests;

        private TestCreativeChestMachine(BlockEntityCreationInfo info) {
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

    private static final class TestCreativeTankMachine extends CreativeTankMachine {

        private int syncRequests;

        private TestCreativeTankMachine(BlockEntityCreationInfo info) {
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
