package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CentralMonitorGroupTargetActionTest {

    private static final String BATCH = "CentralMonitorGroupTargetAction";
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_central_monitor_group_target");
    private static final ResourceLocation HOLDER_INCARNATION_FIELD = SyncFieldData.key("holder_incarnation");
    private static final ResourceLocation GROUP_IDENTITY_FIELD = SyncFieldData.key("group_identity");
    private static final ResourceLocation EXPECTED_TARGET_POS_FIELD = SyncFieldData.key("expected_target_pos");
    private static final ResourceLocation EXPECTED_DATA_SLOT_FIELD = SyncFieldData.key("expected_data_slot");
    private static final ResourceLocation REQUESTED_TARGET_POS_FIELD = SyncFieldData.key("requested_target_pos");
    private static final ResourceLocation REQUESTED_DATA_SLOT_FIELD = SyncFieldData.key("requested_data_slot");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");
    private static final BlockPos MEMBER = new BlockPos(1, 0, 0);
    private static final BlockPos ORIGINAL_TARGET = new BlockPos(2, 0, 0);
    private static final BlockPos DATA_TARGET = new BlockPos(3, 0, 0);
    private static final BlockPos OTHER_TARGET = new BlockPos(4, 0, 0);
    private static final BlockPos NO_DATA_TARGET = new BlockPos(5, 0, 0);
    private static final BlockPos OUTSIDE_GRID = new BlockPos(20, 0, 0);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void validSetAndClearAreDirtyAndInvalidateCoverSide(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine server = createMonitorMachine();
        TestCentralMonitorMachine client = createMonitorMachine();
        UUID groupIdentity = UUID.randomUUID();
        server.addComponent(DATA_TARGET, new ItemStackHandler(2));
        MonitorGroup group = addGroup(server, groupIdentity, ORIGINAL_TARGET, 0);
        group.setTargetCoverSide(Direction.NORTH);

        DataComponentMap full = server.getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), full);

        CentralMonitorGroupTargetState original = state(ORIGINAL_TARGET, 0);
        CentralMonitorGroupTargetState requested = state(DATA_TARGET, 1);
        SyncActionData set = CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                server.getCentralMonitorActionIncarnation(), groupIdentity, original, requested, 0);
        helper.assertTrue(dispatch(player, server, set), "valid Central Monitor target action was rejected");
        assertTarget(helper, group, DATA_TARGET, 1, "valid target action");
        helper.assertTrue(group.getTargetCoverSide() == null,
                "target action retained the cover side derived from the previous target");
        helper.assertTrue(server.targetGridResolutions == 2,
                "target action did not use one bulk grid resolution per validation pass");

        DataComponentMap delta = server.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);
        helper.assertTrue(!delta.isEmpty(), "target action did not mark monitorGroups dirty");
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), delta);
        MonitorGroup clientGroup = client.getMonitorGroups().getFirst();
        assertTarget(helper, clientGroup, DATA_TARGET, 1, "target client delta");

        group.setTargetCoverSide(Direction.SOUTH);
        SyncActionData clear = CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                server.getCentralMonitorActionIncarnation(), groupIdentity, requested, state(null, 0), 1);
        helper.assertTrue(dispatch(player, server, clear), "valid Central Monitor target clear was rejected");
        assertTarget(helper, group, null, 0, "target clear");
        helper.assertTrue(group.getTargetCoverSide() == null, "target clear retained a stale cover side");
        helper.assertTrue(server.targetGridResolutions == 2,
                "target clear unnecessarily resolved a grid component");
        helper.assertTrue(!dispatch(player, server, clear), "replayed target clear was accepted");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void compareAndSetRejectsReplayConcurrentStateAndNoOp(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine machine = createMonitorMachine();
        machine.addComponent(DATA_TARGET, new ItemStackHandler(1));
        machine.addComponent(OTHER_TARGET, null);
        UUID groupIdentity = UUID.randomUUID();
        MonitorGroup group = addGroup(machine, groupIdentity, null, 0);
        CentralMonitorGroupTargetState expected = state(null, 0);
        SyncActionData first = CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                machine.getCentralMonitorActionIncarnation(), groupIdentity, expected, state(DATA_TARGET, 0), 0);
        SyncActionData concurrent = CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                machine.getCentralMonitorActionIncarnation(), groupIdentity, expected, state(OTHER_TARGET, 0), 1);

        helper.assertTrue(dispatch(player, machine, first), "first target CAS was rejected");
        helper.assertTrue(!dispatch(player, machine, concurrent),
                "concurrent target CAS ignored the changed server state");
        helper.assertTrue(!dispatch(player, machine, first), "replayed target CAS was accepted");
        assertTarget(helper, group, DATA_TARGET, 0, "competing target actions");

        boolean noOpRejected = false;
        try {
            CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                    machine.getCentralMonitorActionIncarnation(), groupIdentity,
                    state(DATA_TARGET, 0), state(DATA_TARGET, 0), 2);
        } catch (IllegalArgumentException expectedException) {
            noOpRejected = true;
        }
        helper.assertTrue(noOpRejected, "target action creator accepted a no-op");

        SyncActionData rawNoOp = rawAction(payload(validFields(machine, groupIdentity,
                encodePosition(DATA_TARGET), new JsonPrimitive(0),
                encodePosition(DATA_TARGET), new JsonPrimitive(0))));
        helper.assertTrue(!dispatch(player, machine, rawNoOp), "target handler accepted a no-op payload");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongReplacementSpectatorAndAmbiguousGroup(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine opened = createMonitorMachine();
        TestCentralMonitorMachine replacement = createMonitorMachine();
        UUID groupIdentity = UUID.randomUUID();
        addGroup(opened, groupIdentity, null, 0);
        MonitorGroup replacementGroup = addGroup(replacement, groupIdentity, null, 0);
        replacement.addComponent(DATA_TARGET, new ItemStackHandler(1));
        SyncActionData action = CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                opened.getCentralMonitorActionIncarnation(), groupIdentity,
                state(null, 0), state(DATA_TARGET, 0), 0);

        helper.assertTrue(!dispatch(player, new Object(), action), "target action accepted an unrelated holder");
        helper.assertTrue(!dispatch(player, replacement, action),
                "target action accepted a same-definition replacement holder");
        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorResult;
        try {
            spectatorResult = dispatch(player, opened, action);
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }
        helper.assertTrue(!spectatorResult, "target action accepted a spectator");
        assertTarget(helper, replacementGroup, null, 0, "replacement rejection");

        TestCentralMonitorMachine ambiguous = createMonitorMachine();
        ambiguous.addComponent(DATA_TARGET, new ItemStackHandler(1));
        addGroup(ambiguous, groupIdentity, null, 0);
        addGroup(ambiguous, groupIdentity, null, 0);
        helper.assertTrue(!dispatchSet(player, ambiguous, groupIdentity,
                state(null, 0), state(DATA_TARGET, 0), 0),
                "target action accepted a duplicate stored group identity");
        helper.assertTrue(!dispatchSet(player, ambiguous, UUID.randomUUID(),
                state(null, 0), state(DATA_TARGET, 0), 1),
                "target action accepted an unknown group identity");
        for (MonitorGroup group : ambiguous.getMonitorGroups()) {
            assertTarget(helper, group, null, 0, "ambiguous or unknown group rejection");
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void targetMustResolveInFormedGridAndUseCurrentSlotBounds(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine machine = createMonitorMachine();
        UUID groupIdentity = UUID.randomUUID();
        MonitorGroup group = addGroup(machine, groupIdentity, null, 0);
        machine.addComponent(DATA_TARGET, new ItemStackHandler(2));
        machine.addComponent(NO_DATA_TARGET, null);

        helper.assertTrue(!dispatchSet(player, machine, groupIdentity,
                state(null, 0), state(OUTSIDE_GRID, 0), 0),
                "target action accepted a position outside the formed grid");
        helper.assertTrue(!dispatchSet(player, machine, groupIdentity,
                state(null, 0), state(DATA_TARGET, 2), 1),
                "target action accepted the exclusive data-slot upper bound");
        helper.assertTrue(!dispatchSet(player, machine, groupIdentity,
                state(null, 0), state(NO_DATA_TARGET, 1), 2),
                "target action accepted a non-zero slot for a component without data items");
        assertTarget(helper, group, null, 0, "rejected target bounds");

        machine.structureAvailable = false;
        helper.assertTrue(!dispatchSet(player, machine, groupIdentity,
                state(null, 0), state(DATA_TARGET, 1), 3),
                "target action accepted an unformed Central Monitor");
        machine.structureAvailable = true;

        boolean negativeSlotRejected = false;
        try {
            CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                    machine.getCentralMonitorActionIncarnation(), groupIdentity,
                    state(null, 0), state(DATA_TARGET, -1), 4);
        } catch (IllegalArgumentException expected) {
            negativeSlotRejected = true;
        }
        helper.assertTrue(negativeSlotRejected, "target action creator accepted a negative data slot");

        boolean nonCanonicalClearRejected = false;
        try {
            CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                    machine.getCentralMonitorActionIncarnation(), groupIdentity,
                    state(null, 1), state(null, 1), 5);
        } catch (IllegalArgumentException expected) {
            nonCanonicalClearRejected = true;
        }
        helper.assertTrue(nonCanonicalClearRejected,
                "target action creator accepted a cleared target with a non-zero requested slot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void payloadRequiresExactTypedFieldsAndSingleComponent(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine machine = createMonitorMachine();
        UUID groupIdentity = UUID.randomUUID();
        MonitorGroup group = addGroup(machine, groupIdentity, null, 0);
        machine.addComponent(DATA_TARGET, new ItemStackHandler(1));

        SyncFieldData malformedUuid = validFields(machine, groupIdentity, JsonNull.INSTANCE,
                new JsonPrimitive(0), encodePosition(DATA_TARGET), new JsonPrimitive(0));
        malformedUuid = replaceField(malformedUuid, HOLDER_INCARNATION_FIELD, new JsonPrimitive("malformed"));
        helper.assertTrue(!dispatch(player, machine, rawAction(payload(malformedUuid))),
                "target action accepted a malformed holder UUID");

        SyncFieldData malformedPosition = validFields(machine, groupIdentity, JsonNull.INSTANCE,
                new JsonPrimitive(0), new JsonPrimitive("not-a-position"), new JsonPrimitive(0));
        helper.assertTrue(!dispatch(player, machine, rawAction(payload(malformedPosition))),
                "target action accepted a malformed BlockPos");

        helper.assertTrue(!dispatch(player, machine, rawAction(payload(validFields(machine, groupIdentity,
                JsonNull.INSTANCE, new JsonPrimitive(0), encodePosition(DATA_TARGET), new JsonPrimitive(-1))))),
                "target action accepted a negative data slot");
        helper.assertTrue(!dispatch(player, machine, rawAction(payload(validFields(machine, groupIdentity,
                JsonNull.INSTANCE, new JsonPrimitive(0), encodePosition(DATA_TARGET), new JsonPrimitive(0.5))))),
                "target action accepted a fractional data slot");
        helper.assertTrue(!dispatch(player, machine, rawAction(payload(validFields(machine, groupIdentity,
                JsonNull.INSTANCE, new JsonPrimitive(0), encodePosition(DATA_TARGET),
                new JsonPrimitive(BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE)))))),
                "target action accepted an overflowing data slot");
        helper.assertTrue(!dispatch(player, machine, rawAction(payload(validFields(machine, groupIdentity,
                JsonNull.INSTANCE, new JsonPrimitive(0), JsonNull.INSTANCE, new JsonPrimitive(1))))),
                "target action accepted a cleared target with a non-zero slot");

        SyncFieldData missingField = SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(machine.getCentralMonitorActionIncarnation()))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
                .put(EXPECTED_TARGET_POS_FIELD, JsonNull.INSTANCE)
                .put(EXPECTED_DATA_SLOT_FIELD, new JsonPrimitive(0))
                .put(REQUESTED_TARGET_POS_FIELD, encodePosition(DATA_TARGET))
                .build();
        helper.assertTrue(!dispatch(player, machine, rawAction(payload(missingField))),
                "target action accepted a missing requested slot");

        SyncFieldData extraField = SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(machine.getCentralMonitorActionIncarnation()))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
                .put(EXPECTED_TARGET_POS_FIELD, JsonNull.INSTANCE)
                .put(EXPECTED_DATA_SLOT_FIELD, new JsonPrimitive(0))
                .put(REQUESTED_TARGET_POS_FIELD, encodePosition(DATA_TARGET))
                .put(REQUESTED_DATA_SLOT_FIELD, new JsonPrimitive(0))
                .put(OTHER_FIELD, new JsonPrimitive(true))
                .build();
        helper.assertTrue(!dispatch(player, machine, rawAction(payload(extraField))),
                "target action accepted an extra field");

        DataComponentMap extraComponent = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), validFields(machine, groupIdentity,
                        JsonNull.INSTANCE, new JsonPrimitive(0), encodePosition(DATA_TARGET), new JsonPrimitive(0)))
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), new ItemStack(Items.STONE))
                .build();
        helper.assertTrue(!dispatch(player, machine, rawAction(extraComponent)),
                "target action accepted an extra data component");

        SyncActionData valid = CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                machine.getCentralMonitorActionIncarnation(), groupIdentity,
                state(null, 0), state(DATA_TARGET, 0), 0);
        helper.assertTrue(!dispatch(player, machine, new SyncActionData(valid.actionId(), -1, valid.payload())),
                "target action accepted a negative sequence");
        assertTarget(helper, group, null, 0, "malformed target payloads");
        helper.succeed();
    }

    private static boolean dispatchSet(ServerPlayer player, TestCentralMonitorMachine machine, UUID groupIdentity,
                                       CentralMonitorGroupTargetState expected,
                                       CentralMonitorGroupTargetState requested, int sequence) {
        return dispatch(player, machine, CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                machine.getCentralMonitorActionIncarnation(), groupIdentity, expected, requested, sequence));
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        CentralMonitorGroupTargetActions.initialize();
        return SyncActionDispatchers.server().dispatch(
                new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null));
    }

    private static SyncActionData rawAction(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(SyncFieldData fields) {
        return fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static SyncFieldData validFields(TestCentralMonitorMachine machine, UUID groupIdentity,
                                             JsonElement expectedPosition, JsonElement expectedSlot,
                                             JsonElement requestedPosition, JsonElement requestedSlot) {
        return SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(machine.getCentralMonitorActionIncarnation()))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
                .put(EXPECTED_TARGET_POS_FIELD, expectedPosition)
                .put(EXPECTED_DATA_SLOT_FIELD, expectedSlot)
                .put(REQUESTED_TARGET_POS_FIELD, requestedPosition)
                .put(REQUESTED_DATA_SLOT_FIELD, requestedSlot)
                .build();
    }

    private static SyncFieldData replaceField(SyncFieldData fields, ResourceLocation replaced,
                                              JsonElement replacement) {
        SyncFieldData.Builder builder = SyncFieldData.builder();
        fields.fields().forEach((field, value) -> builder.put(field, field.equals(replaced) ? replacement : value));
        return builder.build();
    }

    private static JsonElement encodeUuid(UUID value) {
        return UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
    }

    private static JsonElement encodePosition(BlockPos value) {
        return BlockPos.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
    }

    private static CentralMonitorGroupTargetState state(@Nullable BlockPos target, int dataSlot) {
        return new CentralMonitorGroupTargetState(target, dataSlot);
    }

    private static MonitorGroup addGroup(TestCentralMonitorMachine machine, UUID identity,
                                         @Nullable BlockPos target, int dataSlot) {
        MonitorGroup group = MonitorGroup.createWithIdentity(identity, "target-test");
        group.add(MEMBER);
        group.setTarget(target);
        group.setDataSlot(dataSlot);
        machine.getMonitorGroups().add(group);
        return group;
    }

    private static void assertTarget(GameTestHelper helper, MonitorGroup group, @Nullable BlockPos target,
                                     int dataSlot, String description) {
        boolean samePosition = target == null ? group.getTargetRaw() == null : target.equals(group.getTargetRaw());
        helper.assertTrue(samePosition && group.getDataSlot() == dataSlot,
                description + " stored the wrong raw target or data slot");
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static TestCentralMonitorMachine createMonitorMachine() {
        return new TestCentralMonitorMachine();
    }

    private static BlockEntityCreationInfo centralMonitorInfo() {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), BlockPos.ZERO,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private static final class TestCentralMonitorMachine extends CentralMonitorMachine {

        private final Map<BlockPos, IMonitorComponent> components = new HashMap<>();
        private boolean structureAvailable = true;
        private int targetGridResolutions;

        private TestCentralMonitorMachine() {
            super(centralMonitorInfo());
        }

        private void addComponent(BlockPos position, @Nullable IItemHandler dataItems) {
            components.put(position, new TestMonitorComponent(position, dataItems));
        }

        @Override
        protected boolean isMembershipStructureAvailable() {
            return structureAvailable;
        }

        @Override
        protected Map<BlockPos, IMonitorComponent> resolveMembershipComponents() {
            targetGridResolutions++;
            return new HashMap<>(components);
        }
    }

    private record TestMonitorComponent(BlockPos position,
                                        @Nullable IItemHandler dataItems)
            implements IMonitorComponent {

        @Override
        public IGuiTexture getComponentIcon() {
            return GuiTextures.BLANK_TRANSPARENT;
        }

        @Override
        public BlockPos getBlockPos() {
            return position;
        }

        @Override
        public @Nullable IItemHandler getDataItems() {
            return dataItems;
        }
    }
}
