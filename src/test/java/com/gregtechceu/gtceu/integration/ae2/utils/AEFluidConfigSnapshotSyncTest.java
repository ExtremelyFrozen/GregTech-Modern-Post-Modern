package com.gregtechceu.gtceu.integration.ae2.utils;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.integration.ae2.machine.AEFluidConfigSnapshot;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;

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
import net.neoforged.neoforge.fluids.FluidStack;
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
public class AEFluidConfigSnapshotSyncTest {

    private static final String BATCH = "AEFluidConfigSnapshotSync";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void snapshotCopiesSixteenFluidSlotsAndPreservesLongAmountsAndComponents(GameTestHelper helper) {
        FluidStack namedWater = new FluidStack(Fluids.WATER, 1_000);
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Configured Water"));
        GenericStack config = new GenericStack(AEFluidKey.of(namedWater), namedWater.getAmount());
        long stockAmount = (long) Integer.MAX_VALUE + 4_096L;
        GenericStack stock = new GenericStack(AEFluidKey.of(namedWater), stockAmount);

        List<AEFluidConfigSnapshot.Slot> source = emptySlots();
        source.set(0, new AEFluidConfigSnapshot.Slot(config, stock));
        AEFluidConfigSnapshot snapshot = new AEFluidConfigSnapshot(true, false, false, source);
        source.set(0, new AEFluidConfigSnapshot.Slot(null, null));

        AEFluidConfigSnapshot.Slot copied = snapshot.slots().getFirst();
        GenericStack copiedConfig = copied.config();
        GenericStack copiedStock = copied.stock();
        if (copiedConfig == null || copiedStock == null) {
            throw new IllegalStateException("snapshot discarded a populated config/stock pair");
        }
        helper.assertTrue(snapshot.slots().size() == 16, "snapshot did not retain exactly sixteen slots");
        helper.assertTrue(copiedConfig != config && copiedStock != stock,
                "snapshot retained mutable GenericStack wrapper identities");
        helper.assertTrue(copiedStock.amount() == stockAmount,
                "snapshot truncated stock amount to the FluidStack integer range");
        helper.assertTrue(copiedConfig.what() instanceof AEFluidKey fluidKey &&
                Component.literal("Configured Water").equals(fluidKey.toStack(1).get(DataComponents.CUSTOM_NAME)),
                "snapshot lost fluid components");
        helper.assertTrue(snapshot.slots().getFirst().config() != null,
                "snapshot retained the caller's mutable slot list");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void snapshotRejectsWrongSizeNonFluidKeysAndNonPositiveAmounts(GameTestHelper helper) {
        List<AEFluidConfigSnapshot.Slot> fifteenSlots = emptySlots();
        fifteenSlots.removeLast();
        assertRejected(helper, () -> new AEFluidConfigSnapshot(false, false, false, fifteenSlots),
                "snapshot accepted fifteen slots");

        assertRejected(helper, () -> new AEFluidConfigSnapshot.Slot(
                new GenericStack(AEItemKey.of(new ItemStack(Items.STONE)), 1), null),
                "snapshot accepted a non-fluid key");

        assertRejected(helper, () -> new AEFluidConfigSnapshot.Slot(
                new GenericStack(AEFluidKey.of(Fluids.WATER), 0), null),
                "snapshot accepted a non-positive amount");

        assertRejected(helper, () -> new AEFluidConfigSnapshot.Slot(
                new GenericStack(AEFluidKey.of(Fluids.WATER), (long) Integer.MAX_VALUE + 1), null),
                "snapshot accepted a config amount above the integer range");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void syncDataHolderRoundTripPreservesComponentsAndLongStock(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        MEInputHatchPartMachine server = createInput();
        MEInputHatchPartMachine client = createInput();
        FluidStack namedWater = new FluidStack(Fluids.WATER, 1_000);
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Synced Water"));
        long stockAmount = (long) Integer.MAX_VALUE + 8_192L;
        server.getMEFluidConfigSlot(5).setConfig(new GenericStack(AEFluidKey.of(namedWater), 1_000));
        server.getMEFluidConfigSlot(5).setStock(new GenericStack(AEFluidKey.of(namedWater), stockAmount));
        server.setOnline(true);

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        AEFluidConfigSnapshot synced = client.getFluidConfigSnapshot();
        GenericStack syncedConfig = synced.slots().get(5).config();
        GenericStack syncedStock = synced.slots().get(5).stock();
        if (syncedConfig == null || syncedStock == null || !(syncedConfig.what() instanceof AEFluidKey fluidKey)) {
            throw new IllegalStateException("full sync discarded the populated ME fluid snapshot slot");
        }
        helper.assertTrue(synced.online() && !synced.stocking() && !synced.autoPull(),
                "full sync changed ME fluid snapshot flags");
        helper.assertTrue(syncedStock.amount() == stockAmount,
                "full sync truncated ME fluid stock above the integer range");
        helper.assertTrue(Component.literal("Synced Water").equals(
                fluidKey.toStack(1).get(DataComponents.CUSTOM_NAME)),
                "full sync discarded ME fluid key components");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void realChangesPublishOneSnapshotPathAndEqualSlotWritesAreNoOps(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        MEInputHatchPartMachine input = createInput();
        input.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        GenericStack water = new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000);

        input.getMEFluidConfigSlot(0).setConfig(water);
        helper.assertTrue(hasSnapshotField(input.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "real config change did not publish the unified snapshot field");
        input.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));
        helper.assertTrue(!hasSnapshotField(input.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "equal config write published a redundant snapshot delta");

        input.getMEFluidConfigSlot(0).setStock(new GenericStack(AEFluidKey.of(Fluids.WATER), 2_000));
        helper.assertTrue(hasSnapshotField(input.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "real stock change did not publish the unified snapshot field");
        input.setOnline(true);
        helper.assertTrue(hasSnapshotField(input.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "online change did not publish the unified snapshot field");

        MEStockingHatchPartMachine stocking = createStocking();
        stocking.getMEFluidConfigSlot(0).setConfig(water);
        stocking.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        stocking.setAutoPull(false);
        helper.assertTrue(stocking.getMEFluidConfigSlot(0).getConfig() != null,
                "idempotent false auto-pull write cleared manual configuration");
        helper.assertTrue(!hasSnapshotField(
                stocking.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "idempotent auto-pull write published a redundant snapshot delta");
        stocking.setAutoPull(true);
        helper.assertTrue(stocking.getFluidConfigSnapshot().autoPull() && hasSnapshotField(
                stocking.getSyncDataHolder().serializeToComponents(registries, true, false)),
                "auto-pull change did not publish the unified snapshot field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void snapshotCodecRejectsWrongSlotCountItemKeysAndInvalidConfigAmounts(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        MEInputHatchPartMachine server = createInput();
        MEInputHatchPartMachine client = createInput();
        server.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));
        JsonObject snapshotJson = snapshotJson(
                server.getSyncDataHolder().serializeFullClientSyncComponents(registries));

        JsonObject wrongSize = snapshotJson.deepCopy();
        wrongSize.getAsJsonArray("slots").remove(15);
        assertMalformedSyncRejected(helper, client, registries, wrongSize,
                "snapshot codec accepted fifteen slots");

        JsonObject itemKey = snapshotJson.deepCopy();
        JsonElement encodedItem = GenericStack.CODEC.encodeStart(
                registries.createSerializationContext(JsonOps.INSTANCE),
                new GenericStack(AEItemKey.of(new ItemStack(Items.STONE)), 1)).getOrThrow();
        itemKey.getAsJsonArray("slots").get(0).getAsJsonObject().add("config", encodedItem);
        assertMalformedSyncRejected(helper, client, registries, itemKey,
                "snapshot codec accepted an item key");

        JsonObject zeroAmount = snapshotJson.deepCopy();
        zeroAmount.getAsJsonArray("slots").get(0).getAsJsonObject()
                .getAsJsonObject("config").addProperty(GenericStack.AMOUNT_FIELD, 0);
        assertMalformedSyncRejected(helper, client, registries, zeroAmount,
                "snapshot codec accepted zero config amount");

        JsonObject oversizedAmount = snapshotJson.deepCopy();
        oversizedAmount.getAsJsonArray("slots").get(0).getAsJsonObject()
                .getAsJsonObject("config").addProperty(GenericStack.AMOUNT_FIELD, (long) Integer.MAX_VALUE + 1);
        assertMalformedSyncRejected(helper, client, registries, oversizedAmount,
                "snapshot codec accepted config amount above the integer range");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void serverOnLoadRecapturesPersistedAutoPullAndConfigAfterDirectFieldRestore(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        MEStockingHatchPartMachine saved = createStocking();
        saved.setAutoPull(true);
        saved.getMEFluidConfigSlot(4).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1));
        SyncFieldData savedMachineFields = saved.getSyncDataHolder().serializeToFieldData(registries, false, false);
        SyncFieldData savedTankFields = saved.tank.getSyncDataHolder().serializeToFieldData(registries, false, false);

        MEStockingHatchPartMachine loaded = createStocking();
        loaded.setLevel(helper.getLevel());
        loaded.tank.getSyncDataHolder().deserializeFieldData(registries, savedTankFields, false);
        loaded.getSyncDataHolder().deserializeFieldData(registries, savedMachineFields, false);
        helper.assertTrue(!loaded.getFluidConfigSnapshot().autoPull() &&
                loaded.getFluidConfigSnapshot().slots().get(4).config() != null,
                "direct SaveField restore unexpectedly refreshed auto-pull before onLoad");
        loaded.onLoad();

        helper.runAfterDelay(2, () -> {
            AEFluidConfigSnapshot snapshot = loaded.getFluidConfigSnapshot();
            helper.assertTrue(snapshot.stocking() && snapshot.autoPull() &&
                    snapshot.slots().get(4).config() != null,
                    "server onLoad did not recapture persisted stocking auto-pull and config state");
            helper.succeed();
        });
    }

    private static List<AEFluidConfigSnapshot.Slot> emptySlots() {
        List<AEFluidConfigSnapshot.Slot> slots = new ArrayList<>(16);
        for (int index = 0; index < 16; index++) {
            slots.add(new AEFluidConfigSnapshot.Slot(null, null));
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
        return fields != null && fields.get(SyncFieldData.key("fluidConfigSnapshot")) != null;
    }

    private static JsonObject snapshotJson(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("full sync omitted field data");
        }
        JsonElement snapshot = fields.get(SyncFieldData.key("fluidConfigSnapshot"));
        if (snapshot == null || !snapshot.isJsonObject()) {
            throw new IllegalStateException("full sync omitted the ME fluid snapshot object");
        }
        return snapshot.getAsJsonObject();
    }

    private static void assertMalformedSyncRejected(GameTestHelper helper, MEInputHatchPartMachine client,
                                                    RegistryAccess registries, JsonObject malformed,
                                                    String message) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(SyncFieldData.key("fluidConfigSnapshot"), malformed)
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

    private static MEInputHatchPartMachine createInput() {
        MetaMachine machine = GTAEMachines.FLUID_IMPORT_HATCH_ME.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.FLUID_IMPORT_HATCH_ME.defaultBlockState());
        if (!(machine instanceof MEInputHatchPartMachine input)) {
            throw new IllegalStateException("ME fluid input definition created the wrong machine type");
        }
        return input;
    }

    private static MEStockingHatchPartMachine createStocking() {
        MetaMachine machine = GTAEMachines.STOCKING_IMPORT_HATCH_ME.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.STOCKING_IMPORT_HATCH_ME.defaultBlockState());
        if (!(machine instanceof MEStockingHatchPartMachine stocking)) {
            throw new IllegalStateException("ME stocking fluid input definition created the wrong machine type");
        }
        return stocking;
    }
}
