package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class QuantumChestMachineActionTest {

    private static final String BATCH = "QuantumChestMachineAction";
    private static final ResourceLocation IMPORT_ACTION = GTCEu.id("click_quantum_chest_import_slot");
    private static final ResourceLocation EXPORT_ACTION = GTCEu.id("export_quantum_chest_item");
    private static final ResourceLocation LOCKED_ITEM_ACTION = GTCEu.id("set_quantum_chest_locked_item");
    private static final ResourceLocation LOCKED_ACTION = GTCEu.id("set_quantum_chest_locked");
    private static final ResourceLocation RIGHT_CLICK_FIELD = SyncFieldData.key("rightClick");
    private static final ResourceLocation LOCKED_FIELD = SyncFieldData.key("locked");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");
    private static final long CAPACITY = 128;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void importCreatorCopiesItemAndPreservesClickSequence(GameTestHelper helper) {
        ItemStack carried = new ItemStack(Items.DIAMOND, 17);

        SyncActionData leftAction = QuantumChestMachineActions
                .createClickQuantumChestImportSlotAction(carried, false);
        ItemStack leftItem = requireItem(leftAction);
        SyncFieldData leftFields = requireFields(leftAction);
        int itemSequence = ItemStack.hashItemAndComponents(leftItem) * 31 + leftItem.getCount();

        helper.assertTrue(leftAction.actionId().equals(IMPORT_ACTION),
                "quantum chest import creator used the wrong action id");
        helper.assertTrue(leftAction.sequence() == itemSequence * 31,
                "quantum chest left-import creator used the wrong sequence");
        helper.assertTrue(leftItem.is(Items.DIAMOND) && leftItem.getCount() == 17,
                "quantum chest import creator changed the requested item");
        assertBooleanField(helper, leftFields, RIGHT_CLICK_FIELD, false, "left-import creator");

        SyncActionData rightAction = QuantumChestMachineActions
                .createClickQuantumChestImportSlotAction(carried, true);
        helper.assertTrue(rightAction.sequence() == itemSequence * 31 + 1,
                "quantum chest right-import creator used the wrong sequence");
        assertBooleanField(helper, requireFields(rightAction), RIGHT_CLICK_FIELD, true,
                "right-import creator");

        carried.setCount(1);
        helper.assertTrue(leftItem.getCount() == 17,
                "quantum chest import creator retained its mutable input stack");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void exportCreatorUsesStrictZeroArgumentProtocol(GameTestHelper helper) {
        SyncActionData action = QuantumChestMachineActions.createExportQuantumChestItemAction();

        helper.assertTrue(action.actionId().equals(EXPORT_ACTION),
                "quantum chest export creator used the wrong action id");
        helper.assertTrue(action.sequence() == 0, "quantum chest export creator used a nonzero sequence");
        helper.assertTrue(action.payload().isEmpty(), "quantum chest export creator encoded a payload");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void lockedItemCreatorNormalizesCountWithoutMutatingInput(GameTestHelper helper) {
        ItemStack selected = new ItemStack(Items.EMERALD, 48);

        SyncActionData action = QuantumChestMachineActions.createSetQuantumChestLockedItemAction(selected);
        ItemStack encoded = requireItem(action);

        helper.assertTrue(action.actionId().equals(LOCKED_ITEM_ACTION),
                "quantum chest locked-item creator used the wrong action id");
        helper.assertTrue(encoded.is(Items.EMERALD) && encoded.getCount() == 1,
                "quantum chest locked-item creator did not normalize its payload count");
        helper.assertTrue(action.sequence() == ItemStack.hashItemAndComponents(encoded),
                "quantum chest locked-item creator used the wrong sequence");
        helper.assertTrue(selected.getCount() == 48,
                "quantum chest locked-item creator mutated its input stack");

        SyncActionData clearAction = QuantumChestMachineActions
                .createSetQuantumChestLockedItemAction(ItemStack.EMPTY);
        ItemStack empty = requireItem(clearAction);
        helper.assertTrue(empty.isEmpty(), "quantum chest locked-item clear action encoded an item");
        helper.assertTrue(clearAction.sequence() == ItemStack.hashItemAndComponents(empty),
                "quantum chest locked-item clear action used the wrong sequence");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void lockedToggleCreatorPreservesBooleanPayloadAndSequence(GameTestHelper helper) {
        assertToggleCreator(helper, false, 0);
        assertToggleCreator(helper, true, 1);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void importTargetAndDispatcherPreserveRightAndLeftClickCounts(GameTestHelper helper) {
        QuantumChestMachine machine = createMachine();
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.IRON_INGOT, 5));

        machine.clickQuantumChestImportSlot(player, new ItemStack(Items.IRON_INGOT, 5), true);

        helper.assertTrue(machine.getStoredAmount() == 1 && machine.getStored().is(Items.IRON_INGOT),
                "direct quantum chest right-import target did not insert exactly one item");
        helper.assertTrue(cursorIs(player, Items.IRON_INGOT, 4),
                "direct quantum chest right-import target changed the cursor incorrectly");

        boolean result = dispatch(player, machine, IMPORT_ACTION,
                importPayload(new ItemStack(Items.IRON_INGOT, 4), new JsonPrimitive(false)));

        helper.assertTrue(result, "valid quantum chest left-import action was rejected");
        helper.assertTrue(machine.getStoredAmount() == 5,
                "quantum chest left-import dispatcher did not insert the requested cursor count");
        helper.assertTrue(player.containerMenu.getCarried().isEmpty(),
                "quantum chest left-import dispatcher did not clear the consumed cursor stack");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void importTargetRejectsStaleIdentityAndCapsStaleCountToCursor(GameTestHelper helper) {
        QuantumChestMachine machine = createMachine();
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.IRON_INGOT, 3));

        machine.clickQuantumChestImportSlot(player, new ItemStack(Items.DIAMOND, 3), false);

        helper.assertTrue(machine.getStoredAmount() == 0 && cursorIs(player, Items.IRON_INGOT, 3),
                "quantum chest import target accepted a stale item identity");

        boolean result = dispatch(player, machine, IMPORT_ACTION,
                importPayload(new ItemStack(Items.IRON_INGOT, 64), new JsonPrimitive(false)));

        helper.assertTrue(result, "quantum chest import action rejected a stale oversized request");
        helper.assertTrue(machine.getStoredAmount() == 3,
                "quantum chest import target did not cap the request to the current cursor count");
        helper.assertTrue(player.containerMenu.getCarried().isEmpty(),
                "quantum chest import target left consumed items on the cursor");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void exportTargetAndDispatcherMoveAtMostOneStackPerAction(GameTestHelper helper) {
        QuantumChestMachine machine = createMachine();
        fill(machine, new ItemStack(Items.IRON_INGOT, 70));
        ServerPlayer player = preparePlayer(helper, ItemStack.EMPTY);

        machine.exportQuantumChestItem(player);

        helper.assertTrue(machine.getStoredAmount() == 6,
                "direct quantum chest export target removed more than one stack");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 64,
                "direct quantum chest export target did not give one full stack to the player");

        boolean result = dispatch(player, machine, EXPORT_ACTION, DataComponentMap.EMPTY);

        helper.assertTrue(result, "valid quantum chest export action was rejected");
        helper.assertTrue(machine.getStoredAmount() == 0 && machine.getStored().isEmpty(),
                "quantum chest export dispatcher did not remove the remaining stored items");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 70,
                "quantum chest export dispatcher did not preserve all exported items");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void lockedItemTargetAndDispatcherNormalizeAndRejectMismatch(GameTestHelper helper) {
        QuantumChestMachine machine = createMachine();
        ItemStack selected = new ItemStack(Items.DIAMOND, 32);

        machine.setQuantumChestLockedItem(selected);

        helper.assertTrue(machine.isLocked() && machine.getLockedItem().is(Items.DIAMOND) &&
                machine.getLockedItem().getCount() == 1,
                "direct quantum chest locked-item target did not normalize the selected count");
        helper.assertTrue(selected.getCount() == 32,
                "direct quantum chest locked-item target mutated its input stack");
        machine.setQuantumChestLockedItem(ItemStack.EMPTY);
        helper.assertTrue(!machine.isLocked(), "direct quantum chest locked-item target did not clear the lock");

        fill(machine, new ItemStack(Items.IRON_INGOT, 10));
        try {
            machine.setQuantumChestLockedItem(new ItemStack(Items.DIAMOND));
            helper.fail("direct quantum chest locked-item target accepted an incompatible item");
        } catch (IllegalArgumentException exception) {
            helper.assertTrue("Quantum chest locked item does not match stored item."
                    .equals(exception.getMessage()),
                    "direct quantum chest locked-item target changed its mismatch exception");
        }
        helper.assertTrue(!machine.isLocked() && machine.getStoredAmount() == 10,
                "failed direct quantum chest lock changed machine state");

        ServerPlayer player = preparePlayer(helper, ItemStack.EMPTY);
        boolean mismatch = dispatch(player, machine, LOCKED_ITEM_ACTION,
                itemPayload(new ItemStack(Items.DIAMOND, 12)));
        helper.assertTrue(!mismatch && !machine.isLocked() && machine.getStoredAmount() == 10,
                "dispatcher did not reject an incompatible quantum chest locked item safely");

        boolean locked = dispatch(player, machine, LOCKED_ITEM_ACTION,
                itemPayload(new ItemStack(Items.IRON_INGOT, 12)));
        helper.assertTrue(locked && machine.isLocked() && machine.getLockedItem().getCount() == 1,
                "valid quantum chest locked-item action did not lock the stored item");

        boolean cleared = dispatch(player, machine, LOCKED_ITEM_ACTION, itemPayload(ItemStack.EMPTY));
        helper.assertTrue(cleared && !machine.isLocked(),
                "quantum chest locked-item clear action did not unlock the machine");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void lockedToggleTargetAndDispatcherUseStoredItem(GameTestHelper helper) {
        QuantumChestMachine emptyMachine = createMachine();
        emptyMachine.setQuantumChestLocked(true);
        helper.assertTrue(!emptyMachine.isLocked(),
                "direct quantum chest lock target locked an empty machine");

        QuantumChestMachine machine = createMachine();
        fill(machine, new ItemStack(Items.IRON_INGOT, 10));

        machine.setQuantumChestLocked(true);
        helper.assertTrue(machine.isLocked() && machine.getLockedItem().is(Items.IRON_INGOT) &&
                machine.getLockedItem().getCount() == 1,
                "direct quantum chest lock target did not lock the stored item");
        machine.setQuantumChestLocked(false);
        helper.assertTrue(!machine.isLocked(), "direct quantum chest lock target did not unlock the machine");

        ServerPlayer player = preparePlayer(helper, ItemStack.EMPTY);
        boolean locked = dispatch(player, machine, LOCKED_ACTION,
                booleanPayload(LOCKED_FIELD, new JsonPrimitive(true)));
        helper.assertTrue(locked && machine.isLocked(),
                "quantum chest lock-toggle dispatcher did not lock the stored item");

        boolean unlocked = dispatch(player, machine, LOCKED_ACTION,
                booleanPayload(LOCKED_FIELD, new JsonPrimitive(false)));
        helper.assertTrue(unlocked && !machine.isLocked(),
                "quantum chest lock-toggle dispatcher did not unlock the machine");
        helper.assertTrue(machine.getStoredAmount() == 10,
                "quantum chest lock-toggle actions changed the stored amount");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void handlersPreserveUnknownFieldAndStrictExportSemantics(GameTestHelper helper) {
        QuantumChestMachine machine = createMachine();
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.IRON_INGOT, 3));

        boolean imported = dispatch(player, machine, IMPORT_ACTION,
                importPayloadWithExtra(new ItemStack(Items.IRON_INGOT, 2), false));
        boolean lockedItem = dispatch(player, machine, LOCKED_ITEM_ACTION,
                itemPayloadWithExtra(new ItemStack(Items.IRON_INGOT, 64)));

        helper.assertTrue(lockedItem && machine.isLocked(),
                "quantum chest locked-item handler rejected an unknown payload field");

        boolean unlocked = dispatch(player, machine, LOCKED_ACTION,
                booleanPayloadWithExtra(LOCKED_FIELD, false));
        boolean exportWithExtra = dispatch(player, machine, EXPORT_ACTION,
                booleanPayload(OTHER_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(imported && machine.getStoredAmount() == 2 && cursorIs(player, Items.IRON_INGOT, 1),
                "quantum chest import handler changed its unknown-field semantics");
        helper.assertTrue(unlocked && !machine.isLocked(),
                "quantum chest lock-toggle handler changed its unknown-field semantics");
        helper.assertTrue(!exportWithExtra && machine.getStoredAmount() == 2,
                "quantum chest export handler accepted a non-empty payload");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHoldersAndMalformedPayloadsWithoutMutation(GameTestHelper helper) {
        QuantumChestMachine machine = createMachine();
        fill(machine, new ItemStack(Items.IRON_INGOT, 10));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.IRON_INGOT, 4));

        boolean wrongImportHolder = dispatch(player, new Object(), IMPORT_ACTION,
                importPayload(new ItemStack(Items.IRON_INGOT, 4), new JsonPrimitive(false)));
        boolean wrongExportHolder = dispatch(player, new Object(), EXPORT_ACTION, DataComponentMap.EMPTY);
        boolean wrongItemHolder = dispatch(player, new Object(), LOCKED_ITEM_ACTION,
                itemPayload(new ItemStack(Items.IRON_INGOT)));
        boolean wrongLockHolder = dispatch(player, new Object(), LOCKED_ACTION,
                booleanPayload(LOCKED_FIELD, new JsonPrimitive(true)));
        boolean missingImportItem = dispatch(player, machine, IMPORT_ACTION,
                booleanPayload(RIGHT_CLICK_FIELD, new JsonPrimitive(false)));
        boolean missingRightClick = dispatch(player, machine, IMPORT_ACTION,
                itemAndFieldPayload(new ItemStack(Items.IRON_INGOT), OTHER_FIELD, new JsonPrimitive(false)));
        boolean numericRightClick = dispatch(player, machine, IMPORT_ACTION,
                importPayload(new ItemStack(Items.IRON_INGOT), new JsonPrimitive(1)));
        boolean nonEmptyExport = dispatch(player, machine, EXPORT_ACTION,
                itemPayload(new ItemStack(Items.IRON_INGOT)));
        boolean missingLockedItem = dispatch(player, machine, LOCKED_ITEM_ACTION, DataComponentMap.EMPTY);
        boolean missingLocked = dispatch(player, machine, LOCKED_ACTION,
                booleanPayload(OTHER_FIELD, new JsonPrimitive(true)));
        boolean numericLocked = dispatch(player, machine, LOCKED_ACTION,
                booleanPayload(LOCKED_FIELD, new JsonPrimitive(1)));

        helper.assertTrue(!wrongImportHolder && !wrongExportHolder && !wrongItemHolder && !wrongLockHolder,
                "a quantum chest action accepted an unrelated holder");
        helper.assertTrue(!missingImportItem && !missingRightClick && !numericRightClick,
                "quantum chest import action accepted a malformed payload");
        helper.assertTrue(!nonEmptyExport,
                "quantum chest export action accepted a non-empty payload");
        helper.assertTrue(!missingLockedItem,
                "quantum chest locked-item action accepted a missing payload");
        helper.assertTrue(!missingLocked && !numericLocked,
                "quantum chest lock-toggle action accepted a malformed payload");
        assertUnchanged(helper, machine, player, "rejected quantum chest actions");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsSpectatorForAllActionsWithoutMutation(GameTestHelper helper) {
        QuantumChestMachine machine = createMachine();
        fill(machine, new ItemStack(Items.IRON_INGOT, 10));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.IRON_INGOT, 4));
        player.setGameMode(GameType.SPECTATOR);

        boolean importResult;
        boolean exportResult;
        boolean itemResult;
        boolean lockResult;
        try {
            importResult = dispatch(player, machine, IMPORT_ACTION,
                    importPayload(new ItemStack(Items.IRON_INGOT, 4), new JsonPrimitive(false)));
            exportResult = dispatch(player, machine, EXPORT_ACTION, DataComponentMap.EMPTY);
            itemResult = dispatch(player, machine, LOCKED_ITEM_ACTION,
                    itemPayload(new ItemStack(Items.IRON_INGOT)));
            lockResult = dispatch(player, machine, LOCKED_ACTION,
                    booleanPayload(LOCKED_FIELD, new JsonPrimitive(true)));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!importResult && !exportResult && !itemResult && !lockResult,
                "a quantum chest action accepted a spectator");
        assertUnchanged(helper, machine, player, "spectator quantum chest actions");
        helper.succeed();
    }

    private static void assertToggleCreator(GameTestHelper helper, boolean locked, int expectedSequence) {
        SyncActionData action = QuantumChestMachineActions.createSetQuantumChestLockedAction(locked);

        helper.assertTrue(action.actionId().equals(LOCKED_ACTION),
                "quantum chest lock-toggle creator used the wrong action id");
        helper.assertTrue(action.sequence() == expectedSequence,
                "quantum chest lock-toggle creator used the wrong sequence");
        assertBooleanField(helper, requireFields(action), LOCKED_FIELD, locked,
                "quantum chest lock-toggle creator");
    }

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields,
                                           ResourceLocation field, boolean expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(fields.fields().size() == 1 &&
                value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " encoded the wrong field data");
    }

    private static QuantumChestMachine createMachine() {
        var definition = GTMachines.QUANTUM_CHEST[GTValues.IV];
        return new QuantumChestMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()),
                GTValues.IV, CAPACITY);
    }

    private static void fill(QuantumChestMachine machine, ItemStack stack) {
        ItemStack remainder = machine.cache.insertItem(0, stack, false);
        if (!remainder.isEmpty()) {
            throw new IllegalStateException("Quantum chest test setup could not insert the requested items.");
        }
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper, ItemStack carried) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.containerMenu.setCarried(carried);
        return player;
    }

    private static boolean dispatch(ServerPlayer player, Object holder, ResourceLocation actionId,
                                    DataComponentMap payload) {
        QuantumChestMachineActions.initialize();
        SyncActionData action = new SyncActionData(actionId, 0, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap importPayload(ItemStack item, JsonElement rightClick) {
        return itemAndFieldPayload(item, RIGHT_CLICK_FIELD, rightClick);
    }

    private static DataComponentMap itemAndFieldPayload(ItemStack item, ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), item)
                .build();
    }

    private static DataComponentMap importPayloadWithExtra(ItemStack item, boolean rightClick) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(RIGHT_CLICK_FIELD, new JsonPrimitive(rightClick))
                        .put(OTHER_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), item)
                .build();
    }

    private static DataComponentMap itemPayload(ItemStack item) {
        return DataComponentMap.builder()
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), item)
                .build();
    }

    private static DataComponentMap itemPayloadWithExtra(ItemStack item) {
        return DataComponentMap.builder()
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), item)
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(OTHER_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();
    }

    private static DataComponentMap booleanPayload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static DataComponentMap booleanPayloadWithExtra(ResourceLocation field, boolean value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, new JsonPrimitive(value))
                        .put(OTHER_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(SyncActionData action) {
        SyncFieldData fields = action.payload().get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Quantum chest action creator omitted field data.");
        }
        return fields;
    }

    private static ItemStack requireItem(SyncActionData action) {
        ItemStack item = action.payload().get(GTDataComponents.PLACEHOLDER_ITEM_STACK.get());
        if (item == null) {
            throw new IllegalStateException("Quantum chest action creator omitted its item payload.");
        }
        return item;
    }

    private static boolean cursorIs(ServerPlayer player, Item item, int count) {
        ItemStack carried = player.containerMenu.getCarried();
        return carried.is(item) && carried.getCount() == count;
    }

    private static void assertUnchanged(GameTestHelper helper, QuantumChestMachine machine, ServerPlayer player,
                                        String description) {
        helper.assertTrue(machine.getStoredAmount() == 10 && machine.getStored().is(Items.IRON_INGOT),
                description + " changed stored item state");
        helper.assertTrue(!machine.isLocked(), description + " changed locked-item state");
        helper.assertTrue(cursorIs(player, Items.IRON_INGOT, 4),
                description + " changed the opening player's cursor");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 0,
                description + " exported items into the player's inventory");
    }
}
