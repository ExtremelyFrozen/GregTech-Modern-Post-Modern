package com.gregtechceu.gtceu.integration.ae2.utils;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.integration.ae2.machine.AEItemConfigSnapshot;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class AEItemConfigSnapshotSyncTest {

    private static final String BATCH = "AEItemConfigSnapshotSync";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void snapshotCopiesSixteenItemSlotsAndPreservesLongAmountsAndComponents(GameTestHelper helper) {
        ItemStack namedDiamond = new ItemStack(Items.DIAMOND, 32);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Configured Diamond"));
        GenericStack config = new GenericStack(AEItemKey.of(namedDiamond), namedDiamond.getCount());
        long stockAmount = (long) Integer.MAX_VALUE + 4_096L;
        GenericStack stock = new GenericStack(AEItemKey.of(namedDiamond), stockAmount);

        List<AEItemConfigSnapshot.Slot> source = emptySlots();
        source.set(0, new AEItemConfigSnapshot.Slot(config, stock));
        AEItemConfigSnapshot snapshot = new AEItemConfigSnapshot(true, false, false, source);
        source.set(0, new AEItemConfigSnapshot.Slot(null, null));

        AEItemConfigSnapshot.Slot copied = snapshot.slots().getFirst();
        GenericStack copiedConfig = copied.config();
        GenericStack copiedStock = copied.stock();
        if (copiedConfig == null || copiedStock == null) {
            throw new IllegalStateException("snapshot discarded a populated item config/stock pair");
        }
        helper.assertTrue(snapshot.slots().size() == AEItemConfigSnapshot.SLOT_COUNT,
                "snapshot did not retain exactly sixteen item slots");
        helper.assertTrue(copiedConfig != config && copiedStock != stock,
                "snapshot retained mutable GenericStack wrapper identities");
        helper.assertTrue(copiedStock.amount() == stockAmount,
                "snapshot truncated item stock amount to the integer range");
        helper.assertTrue(copiedConfig.what() instanceof AEItemKey itemKey &&
                Component.literal("Configured Diamond").equals(
                        itemKey.toStack(1).get(DataComponents.CUSTOM_NAME)),
                "snapshot lost item components");
        helper.assertTrue(snapshot.slots().getFirst().config() != null,
                "snapshot retained the caller's mutable item slot list");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void snapshotRejectsWrongSizeNonItemKeysAndInvalidAmounts(GameTestHelper helper) {
        List<AEItemConfigSnapshot.Slot> fifteenSlots = emptySlots();
        fifteenSlots.removeLast();
        assertRejected(helper, () -> new AEItemConfigSnapshot(false, false, false, fifteenSlots),
                "snapshot accepted fifteen item slots");

        assertRejected(helper, () -> new AEItemConfigSnapshot.Slot(
                new GenericStack(AEFluidKey.of(Fluids.WATER), 1), null),
                "snapshot accepted a non-item key");

        assertRejected(helper, () -> new AEItemConfigSnapshot.Slot(
                new GenericStack(AEItemKey.of(new ItemStack(Items.STONE)), 0), null),
                "snapshot accepted a non-positive item config amount");

        assertRejected(helper, () -> new AEItemConfigSnapshot.Slot(
                new GenericStack(AEItemKey.of(new ItemStack(Items.STONE)), (long) Integer.MAX_VALUE + 1), null),
                "snapshot accepted an item config amount above the integer range");

        assertRejected(helper, () -> new AEItemConfigSnapshot.Slot(null,
                new GenericStack(AEItemKey.of(new ItemStack(Items.STONE)), 0)),
                "snapshot accepted a non-positive item stock amount");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void syncDataHolderRoundTripPreservesComponentsAndLongStock(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        MEInputBusPartMachine server = createInput();
        MEInputBusPartMachine client = createInput();
        ItemStack namedDiamond = new ItemStack(Items.DIAMOND, 16);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Synced Diamond"));
        long stockAmount = (long) Integer.MAX_VALUE + 8_192L;
        server.getMEItemConfigSlot(5).setConfig(new GenericStack(AEItemKey.of(namedDiamond), 16));
        server.getMEItemConfigSlot(5).setStock(new GenericStack(AEItemKey.of(namedDiamond), stockAmount));
        server.setOnline(true);

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        AEItemConfigSnapshot synced = client.getItemConfigSnapshot();
        GenericStack syncedConfig = synced.slots().get(5).config();
        GenericStack syncedStock = synced.slots().get(5).stock();
        if (syncedConfig == null || syncedStock == null || !(syncedConfig.what() instanceof AEItemKey itemKey)) {
            throw new IllegalStateException("full sync discarded the populated ME item snapshot slot");
        }
        helper.assertTrue(synced.online() && !synced.stocking() && !synced.autoPull(),
                "full sync changed ME item snapshot flags");
        helper.assertTrue(syncedStock.amount() == stockAmount,
                "full sync truncated ME item stock above the integer range");
        helper.assertTrue(Component.literal("Synced Diamond").equals(
                itemKey.toStack(1).get(DataComponents.CUSTOM_NAME)),
                "full sync discarded ME item key components");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void realChangesPublishOneSnapshotPathAndEqualSlotWritesAreNoOps(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        MEInputBusPartMachine input = createInput();
        input.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        GenericStack diamond = new GenericStack(AEItemKey.of(new ItemStack(Items.DIAMOND)), 16);

        input.getMEItemConfigSlot(0).setConfig(diamond);
        helper.assertTrue(hasSnapshotField(input.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "real item config change did not publish the unified snapshot field");
        input.getMEItemConfigSlot(0).setConfig(
                new GenericStack(AEItemKey.of(new ItemStack(Items.DIAMOND)), 16));
        helper.assertTrue(!hasSnapshotField(
                input.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "equal item config write published a redundant snapshot delta");

        input.getMEItemConfigSlot(0).setStock(
                new GenericStack(AEItemKey.of(new ItemStack(Items.DIAMOND)), 32));
        helper.assertTrue(hasSnapshotField(input.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "real item stock change did not publish the unified snapshot field");
        input.setOnline(true);
        helper.assertTrue(hasSnapshotField(input.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "online change did not publish the unified item snapshot field");

        MEStockingBusPartMachine stocking = createStocking();
        stocking.getMEItemConfigSlot(0).setConfig(diamond);
        stocking.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        stocking.setAutoPull(false);
        helper.assertTrue(stocking.getMEItemConfigSlot(0).getConfig() != null,
                "idempotent false item auto-pull write cleared manual configuration");
        helper.assertTrue(!hasSnapshotField(
                stocking.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "idempotent item auto-pull write published a redundant snapshot delta");
        stocking.setAutoPull(true);
        helper.assertTrue(stocking.getItemConfigSnapshot().autoPull() && hasSnapshotField(
                stocking.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "item auto-pull change did not publish the unified snapshot field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void snapshotCodecRejectsWrongSlotCountFluidKeysAndInvalidConfigAmounts(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        MEInputBusPartMachine server = createInput();
        MEInputBusPartMachine client = createInput();
        server.getMEItemConfigSlot(0).setConfig(
                new GenericStack(AEItemKey.of(new ItemStack(Items.DIAMOND)), 16));
        JsonObject snapshotJson = snapshotJson(
                server.getSyncDataHolder().serializeFullClientSyncComponents(registries));

        JsonObject wrongSize = snapshotJson.deepCopy();
        wrongSize.getAsJsonArray("slots").remove(AEItemConfigSnapshot.SLOT_COUNT - 1);
        assertMalformedSyncRejected(helper, client, registries, wrongSize,
                "item snapshot codec accepted fifteen slots");

        JsonObject fluidKey = snapshotJson.deepCopy();
        JsonElement encodedFluid = GenericStack.CODEC.encodeStart(
                registries.createSerializationContext(JsonOps.INSTANCE),
                new GenericStack(AEFluidKey.of(Fluids.WATER), 1)).getOrThrow();
        fluidKey.getAsJsonArray("slots").get(0).getAsJsonObject().add("config", encodedFluid);
        assertMalformedSyncRejected(helper, client, registries, fluidKey,
                "item snapshot codec accepted a fluid key");

        JsonObject zeroAmount = snapshotJson.deepCopy();
        zeroAmount.getAsJsonArray("slots").get(0).getAsJsonObject()
                .getAsJsonObject("config").addProperty("amount", 0);
        assertMalformedSyncRejected(helper, client, registries, zeroAmount,
                "item snapshot codec accepted zero config amount");

        JsonObject oversizedAmount = snapshotJson.deepCopy();
        oversizedAmount.getAsJsonArray("slots").get(0).getAsJsonObject()
                .getAsJsonObject("config").addProperty("amount", (long) Integer.MAX_VALUE + 1);
        assertMalformedSyncRejected(helper, client, registries, oversizedAmount,
                "item snapshot codec accepted a config amount above the integer range");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void serverOnLoadRecapturesPersistedAutoPullAndConfigAfterDirectFieldRestore(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        MEStockingBusPartMachine saved = createStocking();
        saved.setAutoPull(true);
        saved.getMEItemConfigSlot(4).setConfig(
                new GenericStack(AEItemKey.of(new ItemStack(Items.DIAMOND)), 1));
        SyncFieldData savedMachineFields = saved.getSyncDataHolder().serializeToFieldData(registries, false, false);
        SyncFieldData savedInventoryFields = saved.getInventory().getSyncDataHolder()
                .serializeToFieldData(registries, false, false);

        MEStockingBusPartMachine loaded = createStocking();
        loaded.setLevel(helper.getLevel());
        loaded.getInventory().getSyncDataHolder().deserializeFieldData(registries, savedInventoryFields, false);
        loaded.getSyncDataHolder().deserializeFieldData(registries, savedMachineFields, false);
        helper.assertTrue(!loaded.getItemConfigSnapshot().autoPull() &&
                loaded.getItemConfigSnapshot().slots().get(4).config() != null,
                "direct SaveField restore unexpectedly refreshed item auto-pull before onLoad");
        loaded.onLoad();

        helper.runAfterDelay(2, () -> {
            AEItemConfigSnapshot snapshot = loaded.getItemConfigSnapshot();
            helper.assertTrue(snapshot.stocking() && snapshot.autoPull() &&
                    snapshot.slots().get(4).config() != null,
                    "server onLoad did not recapture persisted stocking item auto-pull and config state");
            helper.succeed();
        });
    }

    private static List<AEItemConfigSnapshot.Slot> emptySlots() {
        List<AEItemConfigSnapshot.Slot> slots = new ArrayList<>(AEItemConfigSnapshot.SLOT_COUNT);
        for (int index = 0; index < AEItemConfigSnapshot.SLOT_COUNT; index++) {
            slots.add(new AEItemConfigSnapshot.Slot(null, null));
        }
        return slots;
    }

    private static void assertRejected(GameTestHelper helper, Runnable operation, String message) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static boolean hasSnapshotField(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        return fields != null && fields.get(SyncFieldData.key("itemConfigSnapshot")) != null;
    }

    private static JsonObject snapshotJson(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("full sync omitted item field data");
        }
        JsonElement snapshot = fields.get(SyncFieldData.key("itemConfigSnapshot"));
        if (snapshot == null || !snapshot.isJsonObject()) {
            throw new IllegalStateException("full sync omitted the ME item snapshot object");
        }
        return snapshot.getAsJsonObject();
    }

    private static void assertMalformedSyncRejected(GameTestHelper helper, MEInputBusPartMachine client,
                                                    RegistryAccess registries, JsonObject malformed,
                                                    String message) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(SyncFieldData.key("itemConfigSnapshot"), malformed)
                .build();
        DataComponentMap components = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .build();
        boolean rejected = false;
        try {
            client.getSyncDataHolder().applyClientNetworkUpdate(registries, components);
        } catch (RuntimeException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static MEInputBusPartMachine createInput() {
        MetaMachine machine = GTAEMachines.ITEM_IMPORT_BUS_ME.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.ITEM_IMPORT_BUS_ME.defaultBlockState());
        if (!(machine instanceof MEInputBusPartMachine input)) {
            throw new IllegalStateException("ME item input definition created the wrong machine type");
        }
        return input;
    }

    private static MEStockingBusPartMachine createStocking() {
        MetaMachine machine = GTAEMachines.STOCKING_IMPORT_BUS_ME.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.STOCKING_IMPORT_BUS_ME.defaultBlockState());
        if (!(machine instanceof MEStockingBusPartMachine stocking)) {
            throw new IllegalStateException("ME stocking item input definition created the wrong machine type");
        }
        return stocking;
    }
}
