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
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CentralMonitorMembershipActionTest {

    private static final String BATCH = "CentralMonitorMembershipAction";
    private static final ResourceLocation CREATE_ACTION_ID = GTCEu.id("create_central_monitor_group");
    private static final ResourceLocation HOLDER_INCARNATION_FIELD = SyncFieldData.key("holder_incarnation");
    private static final ResourceLocation MEMBERSHIP_REVISION_FIELD = SyncFieldData.key("membership_revision");
    private static final ResourceLocation GROUP_IDENTITY_FIELD = SyncFieldData.key("group_identity");
    private static final ResourceLocation POSITIONS_FIELD = SyncFieldData.key("positions");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");
    private static final BlockPos FIRST_MONITOR = new BlockPos(1, 0, 0);
    private static final BlockPos SECOND_MONITOR = new BlockPos(2, 0, 0);
    private static final BlockPos THIRD_MONITOR = new BlockPos(3, 0, 0);
    private static final BlockPos NON_MONITOR = new BlockPos(4, 0, 0);
    private static final BlockPos OUTSIDE_GRID = new BlockPos(20, 0, 0);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void validCreatePublishesStableIdentityAndRejectsReplay(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine server = createMonitorMachine();
        TestCentralMonitorMachine client = createMonitorMachine();
        server.addComponent(FIRST_MONITOR, true);
        server.addComponent(SECOND_MONITOR, true);

        DataComponentMap full = server.getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), full);
        helper.assertTrue(client.getCentralMonitorActionIncarnation()
                .equals(server.getCentralMonitorActionIncarnation()),
                "full sync did not publish the exact central monitor incarnation");
        helper.assertTrue(client.getCentralMonitorMembershipRevision() == 0,
                "full sync did not publish the initial membership revision");

        UUID groupIdentity = UUID.randomUUID();
        SyncActionData action = CentralMonitorMembershipActions.createGroupAction(
                server.getCentralMonitorActionIncarnation(), server.getCentralMonitorMembershipRevision(),
                groupIdentity,
                linkedSet(FIRST_MONITOR, SECOND_MONITOR), 0);

        helper.assertTrue(dispatch(player, server, action), "valid central monitor create action was rejected");
        helper.assertTrue(server.getMonitorGroups().size() == 1,
                "valid create action did not append exactly one group");
        MonitorGroup created = server.getMonitorGroups().getFirst();
        helper.assertTrue(created.getIdentity().equals(groupIdentity),
                "create action changed the client-proposed group identity");
        helper.assertTrue(created.getMonitorPositions().equals(linkedSet(FIRST_MONITOR, SECOND_MONITOR)),
                "create action stored the wrong monitor positions");
        helper.assertTrue(server.getCentralMonitorMembershipRevision() == 1,
                "create action did not advance the membership revision exactly once");
        helper.assertTrue(server.membershipGridResolutions == 2,
                "create action did not use one bulk grid resolution per validation pass");

        DataComponentMap delta = server.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);
        helper.assertTrue(!delta.isEmpty(), "create action did not mark monitorGroups dirty");
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), delta);
        helper.assertTrue(client.getMonitorGroups().size() == 1 &&
                client.getMonitorGroups().getFirst().getIdentity().equals(groupIdentity),
                "client sync did not preserve the created group identity");
        helper.assertTrue(client.getCentralMonitorMembershipRevision() == 1,
                "client delta did not publish the advanced membership revision");

        TestCentralMonitorMachine restored = createMonitorMachine();
        SyncFieldData saved = server.getSyncDataHolder()
                .serializeToSaveFieldData(helper.getLevel().registryAccess());
        restored.getSyncDataHolder().deserializeFieldData(
                helper.getLevel().registryAccess(), saved, false);
        helper.assertTrue(restored.getCentralMonitorActionIncarnation()
                .equals(server.getCentralMonitorActionIncarnation()),
                "saved central monitor incarnation did not survive restoration");
        helper.assertTrue(restored.getCentralMonitorMembershipRevision() == 1,
                "saved membership revision did not survive restoration");
        helper.assertTrue(restored.getMonitorGroups().size() == 1 &&
                restored.getMonitorGroups().getFirst().getIdentity().equals(groupIdentity),
                "saved monitor group did not survive restoration");

        helper.assertTrue(!dispatch(player, server, action), "replayed create action was accepted");
        helper.assertTrue(server.getMonitorGroups().size() == 1,
                "replayed create action duplicated the group");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void createRejectsInvalidMembershipAtomically(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine machine = createMonitorMachine();
        machine.addComponent(FIRST_MONITOR, true);
        machine.addComponent(SECOND_MONITOR, true);
        machine.addComponent(THIRD_MONITOR, true);
        machine.addComponent(NON_MONITOR, false);

        MonitorGroup occupied = MonitorGroup.createWithIdentity(UUID.randomUUID(), "occupied");
        occupied.add(THIRD_MONITOR);
        machine.getMonitorGroups().add(occupied);

        helper.assertTrue(!dispatchCreate(player, machine, UUID.randomUUID(), linkedSet(FIRST_MONITOR, NON_MONITOR)),
                "create action accepted a non-monitor component");
        helper.assertTrue(!dispatchCreate(player, machine, UUID.randomUUID(), linkedSet(FIRST_MONITOR, OUTSIDE_GRID)),
                "create action accepted a position outside the current monitor grid");
        helper.assertTrue(!dispatchCreate(player, machine, UUID.randomUUID(), linkedSet(FIRST_MONITOR, THIRD_MONITOR)),
                "create action accepted a monitor already owned by another group");
        helper.assertTrue(machine.getMonitorGroups().size() == 1 && !occupied.isEmpty() &&
                !occupied.contains(FIRST_MONITOR),
                "rejected create action partially mutated membership");

        boolean emptyPositionsRejected = false;
        try {
            CentralMonitorMembershipActions.createGroupAction(machine.getCentralMonitorActionIncarnation(),
                    machine.getCentralMonitorMembershipRevision(), UUID.randomUUID(), Set.of(), 0);
        } catch (IllegalArgumentException expected) {
            emptyPositionsRejected = true;
        }
        helper.assertTrue(emptyPositionsRejected, "create action creator accepted empty positions");

        UUID duplicateIdentity = occupied.getIdentity();
        helper.assertTrue(!dispatchCreate(player, machine, duplicateIdentity, linkedSet(FIRST_MONITOR)),
                "create action accepted an existing group identity");
        helper.assertTrue(machine.getMonitorGroups().size() == 1,
                "duplicate group identity changed the group collection");

        machine.structureAvailable = false;
        helper.assertTrue(!dispatchCreate(player, machine, UUID.randomUUID(), linkedSet(FIRST_MONITOR)),
                "create action accepted an unformed Central Monitor");
        machine.structureAvailable = true;

        boolean negativeSequenceRejected = false;
        try {
            CentralMonitorMembershipActions.createGroupAction(machine.getCentralMonitorActionIncarnation(),
                    machine.getCentralMonitorMembershipRevision(), UUID.randomUUID(), linkedSet(FIRST_MONITOR), -1);
        } catch (IllegalArgumentException expected) {
            negativeSequenceRejected = true;
        }
        helper.assertTrue(negativeSequenceRejected, "create action creator accepted a negative sequence");

        boolean negativeRevisionRejected = false;
        try {
            CentralMonitorMembershipActions.createGroupAction(machine.getCentralMonitorActionIncarnation(),
                    -1, UUID.randomUUID(), linkedSet(FIRST_MONITOR), 0);
        } catch (IllegalArgumentException expected) {
            negativeRevisionRejected = true;
        }
        helper.assertTrue(negativeRevisionRejected, "create action creator accepted a negative membership revision");

        machine.membershipCapacity = 1;
        helper.assertTrue(!dispatchCreate(player, machine, UUID.randomUUID(),
                linkedSet(FIRST_MONITOR, SECOND_MONITOR)),
                "create action exceeded the current Central Monitor membership capacity");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void removeIsAtomicAndDropsAnEmptyGroupOnce(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine machine = createMonitorMachine();
        machine.addComponent(FIRST_MONITOR, true);
        machine.addComponent(SECOND_MONITOR, true);
        UUID groupIdentity = UUID.randomUUID();
        SyncActionData create = CentralMonitorMembershipActions.createGroupAction(
                machine.getCentralMonitorActionIncarnation(), machine.getCentralMonitorMembershipRevision(),
                groupIdentity, linkedSet(FIRST_MONITOR, SECOND_MONITOR), 0);
        helper.assertTrue(dispatch(player, machine, create), "group setup action was rejected");
        MonitorGroup group = machine.getMonitorGroups().getFirst();

        helper.assertTrue(!dispatchRemove(player, machine, groupIdentity,
                linkedSet(FIRST_MONITOR, OUTSIDE_GRID), 0),
                "remove action accepted a mixed valid and invalid position set");
        helper.assertTrue(group.contains(FIRST_MONITOR) && group.contains(SECOND_MONITOR),
                "rejected remove action partially removed group members");
        helper.assertTrue(machine.getCentralMonitorMembershipRevision() == 1,
                "rejected remove action changed the membership revision");

        SyncActionData removeFirst = CentralMonitorMembershipActions.createRemoveGroupMembersAction(
                machine.getCentralMonitorActionIncarnation(), machine.getCentralMonitorMembershipRevision(),
                groupIdentity, linkedSet(FIRST_MONITOR), 1);
        helper.assertTrue(dispatch(player, machine, removeFirst), "valid partial remove action was rejected");
        helper.assertTrue(!group.contains(FIRST_MONITOR) && group.contains(SECOND_MONITOR),
                "partial remove action changed the wrong membership");
        helper.assertTrue(machine.getCentralMonitorMembershipRevision() == 2,
                "partial remove action did not advance the membership revision exactly once");
        helper.assertTrue(machine.droppedGroupInventories == 0,
                "partial remove action dropped a non-empty group inventory");
        helper.assertTrue(!dispatch(player, machine, removeFirst), "replayed partial remove action was accepted");

        SyncActionData removeLast = CentralMonitorMembershipActions.createRemoveGroupMembersAction(
                machine.getCentralMonitorActionIncarnation(), machine.getCentralMonitorMembershipRevision(),
                groupIdentity, linkedSet(SECOND_MONITOR), 2);
        helper.assertTrue(dispatch(player, machine, removeLast), "valid final remove action was rejected");
        helper.assertTrue(machine.getMonitorGroups().isEmpty(),
                "final remove action retained an empty group");
        helper.assertTrue(machine.getCentralMonitorMembershipRevision() == 3,
                "final remove action did not advance the membership revision exactly once");
        helper.assertTrue(machine.droppedGroupInventories == 1,
                "final remove action did not drop the group inventory exactly once");
        helper.assertTrue(!dispatch(player, machine, removeLast), "replayed final remove action was accepted");
        helper.assertTrue(!dispatch(player, machine, create),
                "old create action was accepted after its group had been deleted");
        helper.assertTrue(machine.getMonitorGroups().isEmpty(),
                "replayed old create action recreated a deleted group");
        helper.assertTrue(machine.droppedGroupInventories == 1,
                "replayed membership actions dropped inventory more than once");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void failedInventoryDropDoesNotPartiallyRemoveMembership(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine machine = createMonitorMachine();
        machine.addComponent(FIRST_MONITOR, true);
        UUID groupIdentity = UUID.randomUUID();
        helper.assertTrue(dispatchCreate(player, machine, groupIdentity, linkedSet(FIRST_MONITOR)),
                "group setup action was rejected");
        MonitorGroup group = machine.getMonitorGroups().getFirst();
        long revisionBeforeRemove = machine.getCentralMonitorMembershipRevision();

        machine.failGroupDrop = true;
        helper.assertTrue(!dispatchRemove(player, machine, groupIdentity, linkedSet(FIRST_MONITOR), 1),
                "remove action reported success after inventory drop failed");
        helper.assertTrue(machine.getMonitorGroups().size() == 1 && group.contains(FIRST_MONITOR),
                "failed inventory drop partially removed group membership");
        helper.assertTrue(machine.getCentralMonitorMembershipRevision() == revisionBeforeRemove,
                "failed inventory drop advanced the membership revision");
        helper.assertTrue(machine.droppedGroupInventories == 0,
                "failed inventory drop was counted as completed");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHolderSpectatorAndReplacement(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine opened = createMonitorMachine();
        TestCentralMonitorMachine replacement = createMonitorMachine();
        replacement.addComponent(FIRST_MONITOR, true);
        SyncActionData action = CentralMonitorMembershipActions.createGroupAction(
                opened.getCentralMonitorActionIncarnation(), opened.getCentralMonitorMembershipRevision(),
                UUID.randomUUID(), linkedSet(FIRST_MONITOR), 0);

        helper.assertTrue(!dispatch(player, new Object(), action),
                "membership action accepted an unrelated holder");
        helper.assertTrue(!dispatch(player, replacement, action),
                "membership action accepted a same-definition replacement machine");

        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorResult;
        try {
            spectatorResult = dispatch(player, opened, action);
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }
        helper.assertTrue(!spectatorResult, "membership action accepted a spectator");
        helper.assertTrue(opened.getMonitorGroups().isEmpty() && replacement.getMonitorGroups().isEmpty(),
                "rejected holder, replacement, or spectator action mutated a machine");

        SyncActionData negativeSequence = new SyncActionData(action.actionId(), -1, action.payload());
        helper.assertTrue(!dispatch(player, opened, negativeSequence),
                "membership action accepted a negative sequence");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void payloadRequiresExactStructuredFields(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine machine = createMonitorMachine();
        machine.addComponent(FIRST_MONITOR, true);
        UUID groupIdentity = UUID.randomUUID();

        SyncFieldData malformedUuid = SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, new JsonPrimitive("malformed"))
                .put(MEMBERSHIP_REVISION_FIELD, new JsonPrimitive(machine.getCentralMonitorMembershipRevision()))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
                .put(POSITIONS_FIELD, positionArray(FIRST_MONITOR))
                .build();
        helper.assertTrue(!dispatch(player, machine, rawCreate(payload(malformedUuid))),
                "create action accepted a non-UUID holder incarnation");

        SyncFieldData extraField = SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(machine.getCentralMonitorActionIncarnation()))
                .put(MEMBERSHIP_REVISION_FIELD, new JsonPrimitive(machine.getCentralMonitorMembershipRevision()))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
                .put(POSITIONS_FIELD, positionArray(FIRST_MONITOR))
                .put(OTHER_FIELD, new JsonPrimitive(true))
                .build();
        helper.assertTrue(!dispatch(player, machine, rawCreate(payload(extraField))),
                "create action accepted an unknown field");

        JsonArray duplicatePositions = positionArray(FIRST_MONITOR);
        duplicatePositions.add(encodePosition(FIRST_MONITOR));
        SyncFieldData duplicates = SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(machine.getCentralMonitorActionIncarnation()))
                .put(MEMBERSHIP_REVISION_FIELD, new JsonPrimitive(machine.getCentralMonitorMembershipRevision()))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
                .put(POSITIONS_FIELD, duplicatePositions)
                .build();
        helper.assertTrue(!dispatch(player, machine, rawCreate(payload(duplicates))),
                "create action accepted duplicate positions");

        helper.assertTrue(!dispatch(player, machine, rawCreate(payload(fieldsWithRevision(
                machine, groupIdentity, new JsonPrimitive("0"), positionArray(FIRST_MONITOR))))),
                "create action accepted a string membership revision");
        helper.assertTrue(!dispatch(player, machine, rawCreate(payload(fieldsWithRevision(
                machine, groupIdentity, new JsonPrimitive(0.5), positionArray(FIRST_MONITOR))))),
                "create action accepted a fractional membership revision");
        helper.assertTrue(!dispatch(player, machine, rawCreate(payload(fieldsWithRevision(
                machine, groupIdentity, new JsonPrimitive(-1), positionArray(FIRST_MONITOR))))),
                "create action accepted a negative membership revision");

        JsonArray oversizedPositions = new JsonArray();
        for (int index = 0; index <= 65_536; index++) {
            oversizedPositions.add(JsonNull.INSTANCE);
        }
        helper.assertTrue(!dispatch(player, machine, rawCreate(payload(fieldsWithRevision(
                machine, groupIdentity, new JsonPrimitive(machine.getCentralMonitorMembershipRevision()),
                oversizedPositions)))),
                "create action accepted more positions than the membership protocol limit");

        DataComponentMap extraComponent = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), validFields(machine, groupIdentity, FIRST_MONITOR))
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), new ItemStack(Items.STONE))
                .build();
        helper.assertTrue(!dispatch(player, machine, rawCreate(extraComponent)),
                "create action accepted an unrelated data component");
        helper.assertTrue(machine.getMonitorGroups().isEmpty(),
                "malformed membership payload mutated the machine");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void duplicateStoredIdentityAndDefaultNameReuseAreHandled(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine machine = createMonitorMachine();
        machine.addComponent(FIRST_MONITOR, true);
        machine.addComponent(SECOND_MONITOR, true);
        machine.addComponent(THIRD_MONITOR, true);

        UUID duplicatedIdentity = UUID.randomUUID();
        MonitorGroup firstDuplicate = MonitorGroup.createWithIdentity(duplicatedIdentity, "duplicate-a");
        MonitorGroup secondDuplicate = MonitorGroup.createWithIdentity(duplicatedIdentity, "duplicate-b");
        firstDuplicate.add(FIRST_MONITOR);
        secondDuplicate.add(SECOND_MONITOR);
        machine.getMonitorGroups().add(firstDuplicate);
        machine.getMonitorGroups().add(secondDuplicate);

        helper.assertTrue(!dispatchRemove(player, machine, duplicatedIdentity, linkedSet(FIRST_MONITOR), 0),
                "remove action accepted an ambiguous stored group identity");
        helper.assertTrue(firstDuplicate.contains(FIRST_MONITOR) && secondDuplicate.contains(SECOND_MONITOR),
                "ambiguous identity removal partially mutated a group");

        TestCentralMonitorMachine namingMachine = createMonitorMachine();
        namingMachine.addComponent(FIRST_MONITOR, true);
        namingMachine.getMonitorGroups().add(MonitorGroup.createWithIdentity(UUID.randomUUID(), defaultName(1)));
        namingMachine.getMonitorGroups().add(MonitorGroup.createWithIdentity(UUID.randomUUID(), defaultName(3)));
        helper.assertTrue(dispatchCreate(player, namingMachine, UUID.randomUUID(), linkedSet(FIRST_MONITOR)),
                "create action rejected a valid gap in default group names");
        helper.assertTrue(namingMachine.getMonitorGroups().getLast().getName().equals(defaultName(2)),
                "create action reused an occupied default name instead of the first free suffix");
        helper.succeed();
    }

    private static boolean dispatchCreate(ServerPlayer player, TestCentralMonitorMachine machine, UUID groupIdentity,
                                          Set<BlockPos> positions) {
        return dispatch(player, machine, CentralMonitorMembershipActions.createGroupAction(
                machine.getCentralMonitorActionIncarnation(), machine.getCentralMonitorMembershipRevision(),
                groupIdentity, positions, 0));
    }

    private static boolean dispatchRemove(ServerPlayer player, TestCentralMonitorMachine machine, UUID groupIdentity,
                                          Set<BlockPos> positions, int sequence) {
        return dispatch(player, machine, CentralMonitorMembershipActions.createRemoveGroupMembersAction(
                machine.getCentralMonitorActionIncarnation(), machine.getCentralMonitorMembershipRevision(),
                groupIdentity, positions, sequence));
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        CentralMonitorMembershipActions.initialize();
        return SyncActionDispatchers.server().dispatch(
                new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null));
    }

    private static SyncActionData rawCreate(DataComponentMap payload) {
        return new SyncActionData(CREATE_ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(SyncFieldData fields) {
        return fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static SyncFieldData validFields(TestCentralMonitorMachine machine, UUID groupIdentity,
                                             BlockPos position) {
        return fieldsWithRevision(machine, groupIdentity,
                new JsonPrimitive(machine.getCentralMonitorMembershipRevision()), positionArray(position));
    }

    private static SyncFieldData fieldsWithRevision(TestCentralMonitorMachine machine, UUID groupIdentity,
                                                    JsonElement revision, JsonArray positions) {
        return SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(machine.getCentralMonitorActionIncarnation()))
                .put(MEMBERSHIP_REVISION_FIELD, revision)
                .put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
                .put(POSITIONS_FIELD, positions)
                .build();
    }

    private static JsonElement encodeUuid(UUID value) {
        return UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
    }

    private static JsonElement encodePosition(BlockPos position) {
        return BlockPos.CODEC.encodeStart(JsonOps.INSTANCE, position).getOrThrow();
    }

    private static JsonArray positionArray(BlockPos... positions) {
        JsonArray array = new JsonArray();
        for (BlockPos position : positions) {
            array.add(encodePosition(position));
        }
        return array;
    }

    private static LinkedHashSet<BlockPos> linkedSet(BlockPos... positions) {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        Collections.addAll(result, positions);
        return result;
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static TestCentralMonitorMachine createMonitorMachine() {
        return new TestCentralMonitorMachine();
    }

    private static String defaultName(int suffix) {
        return Component.translatable("gtpm.gui.central_monitor.group_default_name", suffix).getString();
    }

    private static BlockEntityCreationInfo centralMonitorInfo() {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), BlockPos.ZERO,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private static final class TestCentralMonitorMachine extends CentralMonitorMachine {

        private final Map<BlockPos, IMonitorComponent> components = new HashMap<>();
        private boolean structureAvailable = true;
        private int membershipCapacity = 65_536;
        private int membershipGridResolutions;
        private int droppedGroupInventories;
        private boolean failGroupDrop;

        private TestCentralMonitorMachine() {
            super(centralMonitorInfo());
        }

        private void addComponent(BlockPos position, boolean monitor) {
            components.put(position, new TestMonitorComponent(position, monitor));
        }

        @Override
        protected boolean isMembershipStructureAvailable() {
            return structureAvailable;
        }

        @Override
        public int getCentralMonitorMembershipCapacity() {
            return Math.min(membershipCapacity, components.size());
        }

        @Override
        protected Map<BlockPos, IMonitorComponent> resolveMembershipComponents() {
            membershipGridResolutions++;
            return new HashMap<>(components);
        }

        @Override
        protected void dropMonitorGroupInventory(@NotNull MonitorGroup group) {
            if (failGroupDrop) {
                throw new IllegalStateException("Injected Central Monitor inventory drop failure.");
            }
            droppedGroupInventories++;
        }
    }

    private record TestMonitorComponent(BlockPos position, boolean monitor) implements IMonitorComponent {

        @Override
        public boolean isMonitor() {
            return monitor;
        }

        @Override
        public IGuiTexture getComponentIcon() {
            return GuiTextures.BLANK_TRANSPARENT;
        }

        @Override
        public BlockPos getBlockPos() {
            return position;
        }
    }
}
