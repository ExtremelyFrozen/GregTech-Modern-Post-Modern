package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
public class LDLib2CircuitFancyConfiguratorActionTest {

    private static final String BATCH = "LDLib2CircuitFancyConfiguratorAction";
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_machine_circuit_configuration");
    private static final ResourceLocation CIRCUIT_CONFIGURATION_FIELD = SyncFieldData.key("circuitConfig");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");
    private static final int NO_CONFIG = -1;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorEncodesBoundaryConfigurationsAndSequences(GameTestHelper helper) {
        assertCreatorAction(helper, NO_CONFIG);
        assertCreatorAction(helper, 0);
        assertCreatorAction(helper, IntCircuitBehaviour.CIRCUIT_MAX);

        boolean rejectedBelowRange = false;
        boolean rejectedAboveRange = false;
        try {
            LDLib2CircuitFancyConfiguratorActions.createSetMachineCircuitConfigurationAction(NO_CONFIG - 1);
        } catch (IllegalArgumentException exception) {
            rejectedBelowRange = true;
        }
        try {
            LDLib2CircuitFancyConfiguratorActions.createSetMachineCircuitConfigurationAction(
                    IntCircuitBehaviour.CIRCUIT_MAX + 1);
        } catch (IllegalArgumentException exception) {
            rejectedAboveRange = true;
        }

        helper.assertTrue(rejectedBelowRange, "machine circuit action creator accepted a value below NO_CONFIG");
        helper.assertTrue(rejectedAboveRange, "machine circuit action creator accepted a value above the maximum");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherExecutesEveryCircuitConfiguration(GameTestHelper helper) {
        boolean previousGhostCircuit = ConfigHolder.INSTANCE.machines.ghostCircuit;
        try {
            ConfigHolder.INSTANCE.machines.ghostCircuit = true;
            TestFancyCircuitHolder holder = TestFancyCircuitHolder.empty(true);

            for (int configuration = 0; configuration <= IntCircuitBehaviour.CIRCUIT_MAX; configuration++) {
                boolean result = dispatch(helper, holder,
                        LDLib2CircuitFancyConfiguratorActions
                                .createSetMachineCircuitConfigurationAction(configuration));
                helper.assertTrue(result, "valid machine circuit configuration was rejected: " + configuration);
                helper.assertTrue(circuitConfiguration(holder) == configuration,
                        "machine circuit action wrote the wrong configuration: " + configuration);
            }
        } finally {
            ConfigHolder.INSTANCE.machines.ghostCircuit = previousGhostCircuit;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void noConfigClearsGhostCircuitAndNormalizesPhysicalCircuit(GameTestHelper helper) {
        boolean previousGhostCircuit = ConfigHolder.INSTANCE.machines.ghostCircuit;
        try {
            ConfigHolder.INSTANCE.machines.ghostCircuit = true;
            TestFancyCircuitHolder ghostHolder = TestFancyCircuitHolder.configured(true, 14);
            helper.assertTrue(dispatch(helper, ghostHolder,
                    LDLib2CircuitFancyConfiguratorActions.createSetMachineCircuitConfigurationAction(NO_CONFIG)),
                    "NO_CONFIG ghost circuit action was rejected");
            helper.assertTrue(circuitStack(ghostHolder).isEmpty(),
                    "NO_CONFIG did not clear the ghost circuit slot");

            ConfigHolder.INSTANCE.machines.ghostCircuit = false;
            TestFancyCircuitHolder physicalHolder = TestFancyCircuitHolder.configured(true, 14);
            helper.assertTrue(dispatch(helper, physicalHolder,
                    LDLib2CircuitFancyConfiguratorActions.createSetMachineCircuitConfigurationAction(NO_CONFIG)),
                    "NO_CONFIG physical circuit action was rejected");
            helper.assertTrue(IntCircuitBehaviour.isIntegratedCircuit(circuitStack(physicalHolder)) &&
                    circuitConfiguration(physicalHolder) == 0,
                    "NO_CONFIG did not normalize the physical circuit to configuration 0");

            TestFancyCircuitHolder emptyPhysicalHolder = TestFancyCircuitHolder.empty(true);
            helper.assertTrue(dispatch(helper, emptyPhysicalHolder,
                    LDLib2CircuitFancyConfiguratorActions.createSetMachineCircuitConfigurationAction(NO_CONFIG)),
                    "NO_CONFIG empty physical slot action was rejected");
            helper.assertTrue(circuitStack(emptyPhysicalHolder).isEmpty(),
                    "NO_CONFIG populated an empty physical circuit slot");
        } finally {
            ConfigHolder.INSTANCE.machines.ghostCircuit = previousGhostCircuit;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherPreservesGhostAndPhysicalCircuitWriteSemantics(GameTestHelper helper) {
        boolean previousGhostCircuit = ConfigHolder.INSTANCE.machines.ghostCircuit;
        try {
            ConfigHolder.INSTANCE.machines.ghostCircuit = false;
            TestFancyCircuitHolder emptyPhysicalHolder = TestFancyCircuitHolder.empty(true);
            helper.assertTrue(dispatch(helper, emptyPhysicalHolder,
                    LDLib2CircuitFancyConfiguratorActions.createSetMachineCircuitConfigurationAction(8)),
                    "valid empty physical slot action was rejected");
            helper.assertTrue(circuitStack(emptyPhysicalHolder).isEmpty(),
                    "valid action created a circuit in an empty physical slot");

            TestFancyCircuitHolder populatedPhysicalHolder = TestFancyCircuitHolder.configured(true, 3);
            helper.assertTrue(dispatch(helper, populatedPhysicalHolder,
                    LDLib2CircuitFancyConfiguratorActions.createSetMachineCircuitConfigurationAction(8)),
                    "valid populated physical slot action was rejected");
            helper.assertTrue(circuitConfiguration(populatedPhysicalHolder) == 8,
                    "valid action did not update the existing physical circuit");

            ConfigHolder.INSTANCE.machines.ghostCircuit = true;
            TestFancyCircuitHolder emptyGhostHolder = TestFancyCircuitHolder.empty(true);
            helper.assertTrue(dispatch(helper, emptyGhostHolder,
                    LDLib2CircuitFancyConfiguratorActions.createSetMachineCircuitConfigurationAction(8)),
                    "valid empty ghost slot action was rejected");
            helper.assertTrue(IntCircuitBehaviour.isIntegratedCircuit(circuitStack(emptyGhostHolder)) &&
                    circuitConfiguration(emptyGhostHolder) == 8,
                    "valid action did not create the configured ghost circuit");
        } finally {
            ConfigHolder.INSTANCE.machines.ghostCircuit = previousGhostCircuit;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRequiresFancyCircuitHolderWithUsableSlot(GameTestHelper helper) {
        SyncActionData action = LDLib2CircuitFancyConfiguratorActions.createSetMachineCircuitConfigurationAction(7);
        TestCircuitHolder circuitOnly = TestCircuitHolder.configured(true, 2);
        TestFancyActionOnlyHolder fancyOnly = new TestFancyActionOnlyHolder();
        TestFancyCircuitHolder disabledSlot = TestFancyCircuitHolder.configured(false, 3);
        TestFancyCircuitHolder missingSlot = TestFancyCircuitHolder.withoutSlots(true);

        boolean unrelatedResult = dispatch(helper, new Object(), action);
        boolean circuitOnlyResult = dispatch(helper, circuitOnly, action);
        boolean fancyOnlyResult = dispatch(helper, fancyOnly, action);
        boolean disabledSlotResult = dispatch(helper, disabledSlot, action);
        boolean missingSlotResult = dispatch(helper, missingSlot, action);

        helper.assertTrue(!unrelatedResult, "unrelated holder was accepted");
        helper.assertTrue(!circuitOnlyResult, "circuit-only holder was accepted");
        helper.assertTrue(!fancyOnlyResult, "Fancy-action-only holder was accepted");
        helper.assertTrue(!disabledSlotResult, "disabled circuit slot was accepted");
        helper.assertTrue(!missingSlotResult, "holder without circuit slot index 0 was accepted");
        helper.assertTrue(circuitConfiguration(circuitOnly) == 2,
                "rejected circuit-only holder action changed its circuit");
        helper.assertTrue(circuitConfiguration(disabledSlot) == 3,
                "rejected disabled-slot action changed its circuit");
        helper.assertTrue(missingSlot.getCircuitInventory().getSlots() == 0,
                "rejected missing-slot action changed the circuit inventory");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TestFancyCircuitHolder holder = TestFancyCircuitHolder.configured(true, 4);
        SyncActionData action = action(payloadWithUnknownField(9));

        boolean result = dispatch(helper, holder, action);

        helper.assertTrue(result, "machine circuit action rejected an unknown payload field");
        helper.assertTrue(circuitConfiguration(holder) == 9,
                "action with an unknown payload field wrote the wrong configuration");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsSpectatorWithoutChangingCircuit(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestFancyCircuitHolder holder = TestFancyCircuitHolder.configured(true, 5);
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, holder,
                    LDLib2CircuitFancyConfiguratorActions.createSetMachineCircuitConfigurationAction(10));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "machine circuit action accepted a spectator");
        helper.assertTrue(circuitConfiguration(holder) == 5,
                "spectator action changed the machine circuit");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedCircuitPayloadsWithoutChangingCircuit(GameTestHelper helper) {
        TestFancyCircuitHolder holder = TestFancyCircuitHolder.configured(true, 6);

        assertRejected(helper, holder, action(payload(new JsonPrimitive("7"))), "string configuration");
        assertRejected(helper, holder, action(payload(new JsonPrimitive(true))), "boolean configuration");
        assertRejected(helper, holder, action(payload(new JsonPrimitive(7.5D))), "fractional configuration");
        assertRejected(helper, holder, action(payload(new JsonPrimitive(Long.MAX_VALUE))),
                "overflowing positive configuration");
        assertRejected(helper, holder, action(payload(new JsonPrimitive(Long.MIN_VALUE))),
                "overflowing negative configuration");
        assertRejected(helper, holder, action(payload(new JsonPrimitive(NO_CONFIG - 1))),
                "configuration below NO_CONFIG");
        assertRejected(helper, holder,
                action(payload(new JsonPrimitive(IntCircuitBehaviour.CIRCUIT_MAX + 1))),
                "configuration above the maximum");
        assertRejected(helper, holder, action(payload(OTHER_FIELD, new JsonPrimitive(7))),
                "missing circuitConfig field");
        assertRejected(helper, holder, action(DataComponentMap.EMPTY), "missing sync field data");
        helper.succeed();
    }

    private static void assertCreatorAction(GameTestHelper helper, int configuration) {
        SyncActionData action = LDLib2CircuitFancyConfiguratorActions
                .createSetMachineCircuitConfigurationAction(configuration);
        SyncFieldData fields = requireFields(action.payload());
        JsonElement element = fields.get(CIRCUIT_CONFIGURATION_FIELD);

        helper.assertTrue(action.actionId().equals(ACTION_ID), "machine circuit creator used the wrong action id");
        helper.assertTrue(action.sequence() == configuration, "machine circuit creator used the wrong sequence");
        helper.assertTrue(fields.fields().size() == 1,
                "machine circuit creator encoded fields outside the action protocol");
        helper.assertTrue(element instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == configuration,
                "machine circuit creator encoded the wrong configuration");
    }

    private static void assertRejected(GameTestHelper helper, TestFancyCircuitHolder holder,
                                       SyncActionData action, String description) {
        ItemStack before = circuitStack(holder).copy();
        boolean result = dispatch(helper, holder, action);

        helper.assertTrue(!result, description + " payload was accepted");
        helper.assertTrue(ItemStack.matches(circuitStack(holder), before),
                description + " payload changed the machine circuit");
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        LDLib2CircuitFancyConfiguratorActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement configuration) {
        return payload(CIRCUIT_CONFIGURATION_FIELD, configuration);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static DataComponentMap payloadWithUnknownField(int configuration) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(CIRCUIT_CONFIGURATION_FIELD, new JsonPrimitive(configuration))
                        .put(OTHER_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Machine circuit action creator omitted sync field data.");
        }
        return fields;
    }

    private static ItemStack circuitStack(IHasCircuitSlot holder) {
        return holder.getCircuitInventory().getStackInSlot(0);
    }

    private static int circuitConfiguration(IHasCircuitSlot holder) {
        return IntCircuitBehaviour.getCircuitConfiguration(circuitStack(holder));
    }

    private static class TestCircuitHolder implements IHasCircuitSlot {

        private final NotifiableItemStackHandler inventory;
        private final boolean circuitSlotEnabled;

        private TestCircuitHolder(int slots, boolean circuitSlotEnabled, ItemStack initialStack) {
            this.inventory = new NotifiableItemStackHandler(slots, IO.IN, IO.NONE);
            this.circuitSlotEnabled = circuitSlotEnabled;
            if (slots > 0 && !initialStack.isEmpty()) {
                inventory.storage.setStackInSlot(0, initialStack);
            }
        }

        private static TestCircuitHolder configured(boolean circuitSlotEnabled, int configuration) {
            return new TestCircuitHolder(1, circuitSlotEnabled, IntCircuitBehaviour.stack(configuration));
        }

        @Override
        public boolean isCircuitSlotEnabled() {
            return circuitSlotEnabled;
        }

        @Override
        public NotifiableItemStackHandler getCircuitInventory() {
            return inventory;
        }
    }

    private static final class TestFancyCircuitHolder extends TestCircuitHolder
                                                      implements LDLib2FancyActionMachine {

        private TestFancyCircuitHolder(int slots, boolean circuitSlotEnabled, ItemStack initialStack) {
            super(slots, circuitSlotEnabled, initialStack);
        }

        private static TestFancyCircuitHolder configured(boolean circuitSlotEnabled, int configuration) {
            return new TestFancyCircuitHolder(1, circuitSlotEnabled, IntCircuitBehaviour.stack(configuration));
        }

        private static TestFancyCircuitHolder empty(boolean circuitSlotEnabled) {
            return new TestFancyCircuitHolder(1, circuitSlotEnabled, ItemStack.EMPTY);
        }

        private static TestFancyCircuitHolder withoutSlots(boolean circuitSlotEnabled) {
            return new TestFancyCircuitHolder(0, circuitSlotEnabled, ItemStack.EMPTY);
        }
    }

    private static final class TestFancyActionOnlyHolder implements LDLib2FancyActionMachine {}
}
