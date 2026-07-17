package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEItemConfigActionsTest {

    private static final String BATCH = "MEItemConfigActions";
    private static final ResourceLocation SET_CONFIG_ACTION = GTCEu.id("set_me_item_config");
    private static final ResourceLocation SET_AMOUNT_ACTION = GTCEu.id("set_me_item_config_amount");
    private static final ResourceLocation PICKUP_STOCK_ACTION = GTCEu.id("pickup_me_item_config_stock");
    private static final ResourceLocation SET_AUTO_PULL_ACTION = GTCEu.id("set_me_item_auto_pull");
    private static final ResourceLocation SLOT_FIELD = SyncFieldData.key("slot");
    private static final ResourceLocation AMOUNT_FIELD = SyncFieldData.key("amount");
    private static final ResourceLocation AUTO_PULL_FIELD = SyncFieldData.key("autoPull");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAppliesOrdinaryConfigAmountPickupAndClearActions(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        MEInputBusPartMachine input = createInput();
        ItemStack configuredItem = new ItemStack(Items.DIAMOND, 12);
        configuredItem.set(DataComponents.CUSTOM_NAME, Component.literal("Configured Diamond"));

        helper.assertTrue(dispatch(player, input,
                MEItemConfigActions.createSetConfigAction(3, configuredItem)),
                "valid item config action was rejected");
        GenericStack configured = input.aeItemHandler.getInventory()[3].getConfig();
        if (configured == null || !(configured.what() instanceof AEItemKey configuredKey)) {
            throw new IllegalStateException("item config action discarded the configured AE item key");
        }
        helper.assertTrue(configured.amount() == 12 && configuredKey.equals(AEItemKey.of(configuredItem)),
                "item config action did not preserve count and components");

        helper.assertTrue(dispatch(player, input, MEItemConfigActions.createSetAmountAction(
                3, configuredItem.copyWithCount(1), 2_000)),
                "valid item amount action was rejected");
        GenericStack resized = input.aeItemHandler.getInventory()[3].getConfig();
        helper.assertTrue(resized != null && resized.amount() == 2_000 && resized.what().equals(configuredKey),
                "item amount action changed the expected key or lost its amount");

        long initialStock = (long) Integer.MAX_VALUE + 37;
        input.aeItemHandler.getInventory()[3].setStock(new GenericStack(configuredKey, initialStock));
        helper.assertTrue(dispatch(player, input, MEItemConfigActions.createPickupStockAction(
                3, configuredItem.copyWithCount(1))),
                "valid item stock pickup action was rejected");
        ItemStack carried = player.containerMenu.getCarried();
        GenericStack remaining = input.aeItemHandler.getInventory()[3].getStock();
        boolean saturatedCursor = ItemStack.isSameItemSameComponents(carried, configuredItem) &&
                carried.getCount() == Integer.MAX_VALUE;
        boolean exactRemainder = remaining != null && remaining.amount() == 37;
        player.containerMenu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(saturatedCursor,
                "item stock pickup did not preserve the legacy saturated cursor amount");
        helper.assertTrue(exactRemainder,
                "item stock pickup did not subtract the exact cursor amount");

        helper.assertTrue(dispatch(player, input,
                MEItemConfigActions.createSetConfigAction(3, ItemStack.EMPTY)),
                "valid item config clear action was rejected");
        helper.assertTrue(input.aeItemHandler.getInventory()[3].getConfig() == null,
                "item config clear action retained the configured item");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherEnforcesStockingDuplicateAutoPullAmountAndPickupRules(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        MEStockingBusPartMachine stocking = createStocking();
        ItemStack diamond = new ItemStack(Items.DIAMOND);

        helper.assertTrue(dispatch(player, stocking,
                MEItemConfigActions.createSetConfigAction(0, diamond)),
                "stocking bus rejected its first manual configuration");
        helper.assertTrue(!dispatch(player, stocking,
                MEItemConfigActions.createSetConfigAction(1, diamond)),
                "stocking bus accepted a duplicate manual configuration");
        helper.assertTrue(stocking.aeItemHandler.getInventory()[1].getConfig() == null,
                "rejected duplicate item configuration partially changed the second slot");

        helper.assertTrue(!dispatch(player, stocking,
                MEItemConfigActions.createSetAmountAction(0, diamond, 2)),
                "stocking bus accepted a target amount action");
        stocking.aeItemHandler.getInventory()[0].setStock(new GenericStack(AEItemKey.of(diamond), 64));
        helper.assertTrue(!dispatch(player, stocking,
                MEItemConfigActions.createPickupStockAction(0, diamond)),
                "stocking bus accepted a stock pickup action");
        helper.assertTrue(player.containerMenu.getCarried().isEmpty() &&
                stocking.aeItemHandler.getInventory()[0].getStock().amount() == 64,
                "rejected stocking pickup partially changed cursor or stock state");

        helper.assertTrue(dispatch(player, stocking,
                MEItemConfigActions.createSetAutoPullAction(true)),
                "stocking bus rejected its auto-pull action");
        helper.assertTrue(stocking.isAutoPull(), "stocking auto-pull action did not apply the requested state");
        helper.assertTrue(!dispatch(player, stocking,
                MEItemConfigActions.createSetConfigAction(2, new ItemStack(Items.IRON_INGOT))),
                "stocking bus accepted manual configuration while auto-pull was active");
        helper.assertTrue(stocking.aeItemHandler.getInventory()[2].getConfig() == null,
                "rejected auto-pull configuration partially changed slot state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedUnauthorizedAndNonAtomicMutations(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        TestMEItemConfigTarget target = new TestMEItemConfigTarget();
        target.config = new ItemStack(Items.DIAMOND, 10);
        target.stockKey = AEItemKey.of(target.config);
        target.stockAmount = 64;

        helper.assertTrue(!dispatch(player, new Object(),
                MEItemConfigActions.createSetConfigAction(0, new ItemStack(Items.IRON_INGOT))),
                "ME item action accepted an unrelated holder");
        helper.assertTrue(!dispatch(player, target, malformedAmountAction(0, new JsonPrimitive(1.5))),
                "ME item action accepted a fractional amount");
        helper.assertTrue(!dispatch(player, target, malformedAmountAction(0, new JsonPrimitive("2"))),
                "ME item action accepted a string amount");
        helper.assertTrue(!dispatch(player, target, malformedAmountAction(0, new JsonPrimitive(true))),
                "ME item action accepted a boolean amount");
        helper.assertTrue(!dispatch(player, target, malformedAmountAction(0,
                new JsonPrimitive(BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE)))),
                "ME item action accepted an overflowing amount");
        helper.assertTrue(!dispatch(player, target, missingAmountAction(0)),
                "ME item action accepted a missing amount field");
        helper.assertTrue(!dispatch(player, target, missingExpectedItemAction(0, 2)),
                "ME item action accepted a missing expected item");
        helper.assertTrue(!dispatch(player, target, nonCanonicalExpectedItemAction(0, 2)),
                "ME item action accepted an expected item count other than one");
        helper.assertTrue(!dispatch(player, target, malformedPickupAction(0, ItemStack.EMPTY)),
                "ME item action accepted an empty expected pickup item");
        helper.assertTrue(!dispatch(player, target, malformedAutoPullAction(new JsonPrimitive("true"))),
                "ME item action accepted a string auto-pull field");
        helper.assertTrue(target.configWrites == 0 && target.amountWrites == 0 && target.pickupWrites == 0 &&
                target.autoPullWrites == 0,
                "rejected malformed actions partially changed target state");

        ItemStack staleDiamond = new ItemStack(Items.DIAMOND);
        staleDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Stale Diamond"));
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createSetAmountAction(
                0, staleDiamond, 2)),
                "ME item action accepted an amount for a stale component identity");
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createPickupStockAction(
                0, staleDiamond)),
                "ME item action accepted pickup for a stale component identity");
        player.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createPickupStockAction(
                0, new ItemStack(Items.DIAMOND))),
                "ME item action accepted pickup while the server cursor was occupied");
        player.containerMenu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(target.amountWrites == 0 && target.pickupWrites == 0,
                "rejected stale or occupied-cursor actions partially changed target state");

        target.allowAmount = false;
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createSetAmountAction(
                0, new ItemStack(Items.DIAMOND), 2)),
                "ME item action ignored the target's stale-key amount gate");
        target.allowPickup = false;
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createPickupStockAction(
                0, new ItemStack(Items.DIAMOND))),
                "ME item action ignored the target's cursor or stale-key pickup gate");
        target.allowConfig = false;
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createSetConfigAction(
                0, new ItemStack(Items.IRON_INGOT))),
                "ME item action ignored the target's duplicate configuration gate");
        helper.assertTrue(target.configWrites == 0 && target.amountWrites == 0 && target.pickupWrites == 0,
                "rejected target-gated actions partially changed target state");

        target.allowConfig = true;
        target.autoPull = true;
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createSetConfigAction(
                0, new ItemStack(Items.IRON_INGOT))),
                "ME item action accepted manual config while auto-pull was active");
        target.autoPull = false;
        target.stocking = true;
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createSetAmountAction(
                0, new ItemStack(Items.DIAMOND), 2)),
                "ME item action accepted an amount for a stocking target");
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createPickupStockAction(
                0, new ItemStack(Items.DIAMOND))),
                "ME item action accepted pickup for a stocking target");

        target.stocking = false;
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createSetAutoPullAction(true)),
                "ordinary ME item input accepted an auto-pull action");
        target.supported = false;
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createSetConfigAction(
                0, new ItemStack(Items.IRON_INGOT))),
                "unsupported definition accepted the ME item action protocol");
        target.supported = true;
        player.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(!dispatch(player, target, MEItemConfigActions.createSetConfigAction(
                0, new ItemStack(Items.IRON_INGOT))),
                "spectator executed an ME item action");
        helper.assertTrue(target.configWrites == 0 && target.amountWrites == 0 && target.pickupWrites == 0 &&
                target.autoPullWrites == 0,
                "rejected authorization actions partially changed target state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void actionCreatorsRejectSlotBoundariesInvalidAmountsAndEmptyExpectedItems(GameTestHelper helper) {
        assertCreatorRejected(helper, () -> MEItemConfigActions.createSetConfigAction(
                -1, ItemStack.EMPTY), "config creator accepted slot -1");
        assertCreatorRejected(helper, () -> MEItemConfigActions.createSetConfigAction(
                AEItemConfigSnapshot.SLOT_COUNT, ItemStack.EMPTY), "config creator accepted slot 16");
        assertCreatorRejected(helper, () -> MEItemConfigActions.createSetAmountAction(
                -1, new ItemStack(Items.DIAMOND), 1), "amount creator accepted slot -1");
        assertCreatorRejected(helper, () -> MEItemConfigActions.createSetAmountAction(
                AEItemConfigSnapshot.SLOT_COUNT, new ItemStack(Items.DIAMOND), 1),
                "amount creator accepted slot 16");
        assertCreatorRejected(helper, () -> MEItemConfigActions.createSetAmountAction(
                0, ItemStack.EMPTY, 1), "amount creator accepted an empty expected item");
        assertCreatorRejected(helper, () -> MEItemConfigActions.createSetAmountAction(
                0, new ItemStack(Items.DIAMOND), 0), "amount creator accepted zero");
        assertCreatorRejected(helper, () -> MEItemConfigActions.createPickupStockAction(
                -1, new ItemStack(Items.DIAMOND)), "pickup creator accepted slot -1");
        assertCreatorRejected(helper, () -> MEItemConfigActions.createPickupStockAction(
                AEItemConfigSnapshot.SLOT_COUNT, new ItemStack(Items.DIAMOND)),
                "pickup creator accepted slot 16");
        assertCreatorRejected(helper, () -> MEItemConfigActions.createPickupStockAction(
                0, ItemStack.EMPTY), "pickup creator accepted an empty expected item");
        helper.succeed();
    }

    private static SyncActionData malformedAmountAction(int slot, JsonPrimitive amount) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(SLOT_FIELD, new JsonPrimitive(slot))
                .put(AMOUNT_FIELD, amount)
                .build();
        return itemAction(SET_AMOUNT_ACTION, fields, new ItemStack(Items.DIAMOND));
    }

    private static SyncActionData missingAmountAction(int slot) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(SLOT_FIELD, new JsonPrimitive(slot))
                .build();
        return itemAction(SET_AMOUNT_ACTION, fields, new ItemStack(Items.DIAMOND));
    }

    private static SyncActionData missingExpectedItemAction(int slot, int amount) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(SLOT_FIELD, new JsonPrimitive(slot))
                .put(AMOUNT_FIELD, new JsonPrimitive(amount))
                .build();
        return new SyncActionData(SET_AMOUNT_ACTION, 0,
                fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()));
    }

    private static SyncActionData nonCanonicalExpectedItemAction(int slot, int amount) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(SLOT_FIELD, new JsonPrimitive(slot))
                .put(AMOUNT_FIELD, new JsonPrimitive(amount))
                .build();
        return itemAction(SET_AMOUNT_ACTION, fields, new ItemStack(Items.DIAMOND, 2));
    }

    private static SyncActionData malformedPickupAction(int slot, ItemStack expectedItem) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(SLOT_FIELD, new JsonPrimitive(slot))
                .build();
        return itemAction(PICKUP_STOCK_ACTION, fields, expectedItem);
    }

    private static SyncActionData malformedAutoPullAction(JsonPrimitive autoPull) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(AUTO_PULL_FIELD, autoPull)
                .build();
        return new SyncActionData(SET_AUTO_PULL_ACTION, 0,
                fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()));
    }

    private static SyncActionData itemAction(ResourceLocation actionId, SyncFieldData fields, ItemStack item) {
        return new SyncActionData(actionId, 0, DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), item)
                .build());
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        return SyncActionDispatchers.server().dispatch(new SyncActionContext(
                player, holder, action, BlockPos.ZERO, null, null, null));
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return player;
    }

    private static void assertCreatorRejected(GameTestHelper helper, Runnable creator, String message) {
        boolean rejected = false;
        try {
            creator.run();
        } catch (IllegalArgumentException expected) {
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

    private static final class TestMEItemConfigTarget implements MEItemConfigActionTarget {

        private boolean supported = true;
        private boolean autoPull;
        private boolean stocking;
        private boolean allowConfig = true;
        private boolean allowAmount = true;
        private boolean allowPickup = true;
        private ItemStack config = ItemStack.EMPTY;
        private AEItemKey stockKey;
        private long stockAmount;
        private int configWrites;
        private int amountWrites;
        private int pickupWrites;
        private int autoPullWrites;

        @Override
        public boolean supportsMEItemConfigActions() {
            return supported;
        }

        @Override
        public int getMEItemConfigSlotCount() {
            return AEItemConfigSnapshot.SLOT_COUNT;
        }

        @Override
        public boolean isMEItemConfigAutoPull() {
            return autoPull;
        }

        @Override
        public boolean isMEItemStocking() {
            return stocking;
        }

        @Override
        public boolean canSetMEItemConfig(int slot, @NotNull ItemStack item) {
            return allowConfig;
        }

        @Override
        public void setMEItemConfig(int slot, @NotNull ItemStack item) {
            config = item.copy();
            configWrites++;
        }

        @Override
        public boolean canSetMEItemConfigAmount(int slot, @NotNull ItemStack expectedItem, int amount) {
            return allowAmount && amount > 0 && !config.isEmpty() &&
                    ItemStack.isSameItemSameComponents(config, expectedItem);
        }

        @Override
        public void setMEItemConfigAmount(int slot, @NotNull ItemStack expectedItem, int amount) {
            if (!canSetMEItemConfigAmount(slot, expectedItem, amount)) {
                throw new IllegalStateException("Test target rejected the expected item amount update.");
            }
            config.setCount(amount);
            amountWrites++;
        }

        @Override
        public boolean canPickupMEItemConfigStock(@NotNull ServerPlayer player, int slot,
                                                  @NotNull ItemStack expectedItem) {
            return allowPickup && !stocking && player.containerMenu.getCarried().isEmpty() && stockKey != null &&
                    stockKey.equals(AEItemKey.of(expectedItem)) && stockAmount > 0;
        }

        @Override
        public void pickupMEItemConfigStock(@NotNull ServerPlayer player, int slot,
                                            @NotNull ItemStack expectedItem) {
            if (!canPickupMEItemConfigStock(player, slot, expectedItem)) {
                throw new IllegalStateException("Test target rejected the expected item stock pickup.");
            }
            int pickedUp = (int) Math.min(stockAmount, Integer.MAX_VALUE);
            player.containerMenu.setCarried(stockKey.toStack(pickedUp));
            stockAmount -= pickedUp;
            pickupWrites++;
        }

        @Override
        public void setMEItemAutoPull(boolean autoPull) {
            this.autoPull = autoPull;
            autoPullWrites++;
        }
    }
}
