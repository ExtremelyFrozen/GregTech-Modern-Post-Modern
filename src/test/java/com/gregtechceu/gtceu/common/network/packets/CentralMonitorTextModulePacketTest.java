package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CentralMonitorTextModulePacketTest {

    private static final String BATCH = "CentralMonitorTextModulePacket";
    private static final BlockPos MONITOR_POSITION = new BlockPos(1, 0, 0);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void textConfigurationUpdatesInPlaceAndPreservesServerState(GameTestHelper helper) {
        TextFixture fixture = textFixture(helper);
        ItemStack currentStack = fixture.currentStack();
        UUID serverPlaceholderId = currentStack.get(GTDataComponents.PLACEHOLDER_UUID.get());
        TextLineList serverDerivedText = currentStack.get(GTDataComponents.TEXT_LINE_LIST.get());
        UUID slotIncarnation = fixture.group().getModuleSlotIncarnation();

        ItemStack requestedStack = currentStack.copy();
        requestedStack.set(GTDataComponents.PLACEHOLDER_UUID.get(), UUID.randomUUID());
        requestedStack.set(GTDataComponents.TEXT_LINE_LIST.get(),
                new TextLineList(List.of(Component.literal("client-derived")), 9.0f));
        requestedStack.set(GTDataComponents.FORMAT_STRING_LIST.get(),
                new TextLineList(
                        List.of(Component.literal("updated <energy>").withStyle(ChatFormatting.RED)),
                        2.5f));

        helper.assertTrue(SCPacketMonitorGroupDataChange.applyServerTextConfiguration(
                fixture.machine(), 0,
                fixture.machine().getCentralMonitorActionIncarnation(),
                fixture.group().getIdentity(),
                fixture.group().getModuleSlotIncarnation(),
                requestedStack, fixture.player()),
                "valid legacy text module configuration was rejected");
        ItemStack configuredStack = fixture.group().getItemStackHandler().getStackInSlot(0);
        helper.assertTrue(configuredStack == currentStack,
                "legacy text module packet replaced the physical ItemStack instance");
        helper.assertTrue(fixture.group().getModuleSlotIncarnation().equals(slotIncarnation),
                "legacy text module configuration rotated the module-slot incarnation");
        helper.assertTrue(serverPlaceholderId.equals(configuredStack.get(GTDataComponents.PLACEHOLDER_UUID.get())),
                "legacy text module configuration replaced the server placeholder UUID");
        helper.assertTrue(serverDerivedText.equals(configuredStack.get(GTDataComponents.TEXT_LINE_LIST.get())),
                "legacy text module configuration replaced derived server text");
        helper.assertTrue(Component.literal("server-name").equals(configuredStack.get(DataComponents.CUSTOM_NAME)),
                "legacy text module configuration changed an unrelated stable component");

        TextLineList expectedConfiguration = new TextLineList(
                List.of(Component.literal("updated <energy>")), 2.5f);
        helper.assertTrue(expectedConfiguration.equals(
                configuredStack.get(GTDataComponents.FORMAT_STRING_LIST.get())),
                "legacy text module configuration did not store canonical editable text");
        DataComponentMap delta = fixture.machine().getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);
        helper.assertTrue(!delta.isEmpty(),
                "legacy text module configuration did not explicitly mark monitorGroups dirty");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void malformedSnapshotsAndNonTextSlotsAreRejected(GameTestHelper helper) {
        TextFixture fixture = textFixture(helper);
        ItemStack validRequest = fixture.currentStack().copy();
        validRequest.set(GTDataComponents.FORMAT_STRING_LIST.get(),
                new TextLineList(List.of(Component.literal("updated")), 1.0f));

        assertRejected(helper, fixture, -1, validRequest,
                "legacy text module packet accepted a negative group index");
        assertRejected(helper, fixture, 1, validRequest,
                "legacy text module packet accepted the exclusive group upper bound");

        ItemStack changedStableComponent = validRequest.copy();
        changedStableComponent.set(DataComponents.CUSTOM_NAME, Component.literal("client-name"));
        assertRejected(helper, fixture, 0, changedStableComponent,
                "legacy text module packet accepted a changed stable component");

        ItemStack changedCount = validRequest.copy();
        changedCount.setCount(2);
        assertRejected(helper, fixture, 0, changedCount,
                "legacy text module packet accepted a changed module count");

        ItemStack missingConfiguration = validRequest.copy();
        missingConfiguration.remove(GTDataComponents.FORMAT_STRING_LIST.get());
        assertRejected(helper, fixture, 0, missingConfiguration,
                "legacy text module packet accepted a missing editable configuration");

        ItemStack invalidScale = validRequest.copy();
        invalidScale.set(GTDataComponents.FORMAT_STRING_LIST.get(),
                new TextLineList(List.of(Component.literal("updated")), Float.NaN));
        assertRejected(helper, fixture, 0, invalidScale,
                "legacy text module packet accepted a non-finite text scale");

        assertRejected(helper, fixture, 0, GTItems.IMAGE_MODULE.get().getDefaultInstance(),
                "legacy text module packet accepted a non-text request snapshot");

        ItemStack imageModule = GTItems.IMAGE_MODULE.get().getDefaultInstance();
        fixture.group().getItemStackHandler().setStackInSlot(0, imageModule);
        assertRejected(helper, fixture, 0, imageModule.copy(),
                "legacy packet accepted an image module write");

        fixture.group().getItemStackHandler().clear();
        assertRejected(helper, fixture, 0, validRequest,
                "legacy text module packet accepted a slot without a text module");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void identitiesAndPlayerAuthorizationAreRequired(GameTestHelper helper) {
        TextFixture fixture = textFixture(helper);
        ItemStack validRequest = fixture.currentStack().copy();
        validRequest.set(GTDataComponents.FORMAT_STRING_LIST.get(),
                new TextLineList(List.of(Component.literal("updated")), 1.0f));

        boolean detachedGroupRejected = false;
        try {
            new SCPacketMonitorGroupDataChange(
                    fixture.currentStack(), new MonitorGroup("detached"), fixture.machine());
        } catch (IllegalArgumentException expected) {
            detachedGroupRejected = true;
        }
        helper.assertTrue(detachedGroupRejected,
                "Central Monitor packet constructor accepted a group outside its machine");

        MonitorGroup duplicateIdentity = MonitorGroup.restore(
                fixture.group().getIdentity(), UUID.randomUUID(), "duplicate",
                MonitorGroup.createModuleHandler(), new CustomItemStackHandler(8));
        fixture.machine().getMonitorGroups().add(duplicateIdentity);
        boolean duplicateIdentityRejected = false;
        try {
            new SCPacketMonitorGroupDataChange(
                    fixture.currentStack(), fixture.group(), fixture.machine());
        } catch (IllegalArgumentException expected) {
            duplicateIdentityRejected = true;
        } finally {
            fixture.machine().getMonitorGroups().remove(duplicateIdentity);
        }
        helper.assertTrue(duplicateIdentityRejected,
                "Central Monitor packet constructor accepted a duplicate group identity");

        assertRejected(helper, fixture, 0,
                UUID.randomUUID(), fixture.group().getIdentity(), fixture.group().getModuleSlotIncarnation(),
                validRequest, "legacy text module packet accepted a wrong holder identity");
        assertRejected(helper, fixture, 0,
                fixture.machine().getCentralMonitorActionIncarnation(), UUID.randomUUID(),
                fixture.group().getModuleSlotIncarnation(), validRequest,
                "legacy text module packet accepted a wrong group identity");
        assertRejected(helper, fixture, 0,
                fixture.machine().getCentralMonitorActionIncarnation(), fixture.group().getIdentity(),
                UUID.randomUUID(), validRequest,
                "legacy text module packet accepted a wrong module-slot identity");

        fixture.player().setGameMode(GameType.SPECTATOR);
        try {
            assertRejected(helper, fixture, 0, validRequest,
                    "legacy text module packet accepted a spectator");
        } finally {
            fixture.player().setGameMode(GameType.SURVIVAL);
        }

        Vec3 nearbyPosition = fixture.player().position();
        fixture.player().setPos(
                nearbyPosition.x + SCPacketMonitorGroupDataChange.MAX_INTERACTION_DISTANCE +
                        SCPacketMonitorGroupDataChange.MAX_INTERACTION_DISTANCE,
                nearbyPosition.y,
                nearbyPosition.z);
        try {
            assertRejected(helper, fixture, 0, validRequest,
                    "legacy text module packet accepted a player outside interaction range");
        } finally {
            fixture.player().setPos(nearbyPosition.x, nearbyPosition.y, nearbyPosition.z);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void clientRejectsStaleIdentityUpdates(GameTestHelper helper) {
        TextFixture fixture = textFixture(helper);
        TestCentralMonitorMachine client = new TestCentralMonitorMachine(
                true, fixture.machine().getBlockPos());
        DataComponentMap full = fixture.machine().getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), full);
        MonitorGroup openingGroup = client.getMonitorGroups().getFirst();
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), full);
        MonitorGroup clientGroup = client.getMonitorGroups().getFirst();
        helper.assertTrue(openingGroup != clientGroup,
                "full client sync reused the opening MonitorGroup test object");
        new SCPacketMonitorGroupDataChange(
                openingGroup.getItemStackHandler().getStackInSlot(0), openingGroup, client);
        ItemStack synchronizedStack = clientGroup.getItemStackHandler().getStackInSlot(0).copy();
        synchronizedStack.set(GTDataComponents.TEXT_LINE_LIST.get(),
                new TextLineList(List.of(Component.literal("synchronized")), 1.0f));

        assertClientRejected(helper, client, clientGroup,
                UUID.randomUUID(), clientGroup.getIdentity(), clientGroup.getModuleSlotIncarnation(),
                synchronizedStack, "client accepted a stale holder identity");
        assertClientRejected(helper, client, clientGroup,
                client.getCentralMonitorActionIncarnation(), UUID.randomUUID(),
                clientGroup.getModuleSlotIncarnation(), synchronizedStack,
                "client accepted a stale group identity");
        assertClientRejected(helper, client, clientGroup,
                client.getCentralMonitorActionIncarnation(), clientGroup.getIdentity(), UUID.randomUUID(),
                synchronizedStack, "client accepted a stale module-slot identity");

        helper.assertTrue(SCPacketMonitorGroupDataChange.applyClientStackUpdate(
                client, 0,
                client.getCentralMonitorActionIncarnation(),
                clientGroup.getIdentity(),
                clientGroup.getModuleSlotIncarnation(),
                synchronizedStack),
                "client rejected a current Central Monitor module update");
        helper.assertTrue(ItemStack.matches(
                synchronizedStack, clientGroup.getItemStackHandler().getStackInSlot(0)),
                "client did not apply a current Central Monitor module update");
        helper.succeed();
    }

    private static TextFixture textFixture(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        BlockPos machinePosition = helper.absolutePos(BlockPos.ZERO);
        Vec3 monitorCenter = Vec3.atCenterOf(machinePosition);
        player.setPos(monitorCenter.x, monitorCenter.y, monitorCenter.z);
        TestCentralMonitorMachine machine = new TestCentralMonitorMachine(false, machinePosition);
        machine.addMonitor(MONITOR_POSITION);
        helper.assertTrue(machine.createCentralMonitorGroup(
                0, UUID.randomUUID(), Set.of(MONITOR_POSITION)),
                "server rejected valid text module test group creation");
        MonitorGroup group = machine.getMonitorGroups().getFirst();

        ItemStack textModule = GTItems.TEXT_MODULE.get().getDefaultInstance();
        textModule.set(GTDataComponents.FORMAT_STRING_LIST.get(),
                new TextLineList(List.of(Component.literal("original")), 1.0f));
        textModule.set(GTDataComponents.PLACEHOLDER_UUID.get(), UUID.randomUUID());
        textModule.set(GTDataComponents.TEXT_LINE_LIST.get(),
                new TextLineList(List.of(Component.literal("server-derived")), 1.5f));
        textModule.set(DataComponents.CUSTOM_NAME, Component.literal("server-name"));
        helper.assertTrue(group.getItemStackHandler().insertItem(0, textModule, false).isEmpty(),
                "server module slot rejected a valid text module");
        machine.getSyncDataHolder().serializeToComponents(helper.getLevel().registryAccess(), true, false);
        return new TextFixture(player, machine, group, group.getItemStackHandler().getStackInSlot(0));
    }

    private static void assertRejected(GameTestHelper helper, TextFixture fixture, int groupIndex,
                                       ItemStack requestedStack, String message) {
        assertRejected(
                helper,
                fixture,
                groupIndex,
                fixture.machine().getCentralMonitorActionIncarnation(),
                fixture.group().getIdentity(),
                fixture.group().getModuleSlotIncarnation(),
                requestedStack,
                message);
    }

    private static void assertRejected(GameTestHelper helper, TextFixture fixture, int groupIndex,
                                       UUID holderIncarnation, UUID groupIdentity,
                                       UUID moduleSlotIncarnation, ItemStack requestedStack,
                                       String message) {
        ItemStack currentStack = fixture.group().getItemStackHandler().getStackInSlot(0);
        ItemStack before = currentStack.copy();
        UUID slotIncarnation = fixture.group().getModuleSlotIncarnation();
        fixture.machine().getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);

        helper.assertTrue(!SCPacketMonitorGroupDataChange.applyServerTextConfiguration(
                fixture.machine(), groupIndex, holderIncarnation, groupIdentity,
                moduleSlotIncarnation, requestedStack, fixture.player()), message);
        helper.assertTrue(ItemStack.matches(before, currentStack), message + " and changed the current stack");
        helper.assertTrue(fixture.group().getModuleSlotIncarnation().equals(slotIncarnation),
                message + " and rotated the module-slot incarnation");
        helper.assertTrue(fixture.machine().getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false)
                .isEmpty(), message + " and marked monitorGroups dirty");
    }

    private static void assertClientRejected(GameTestHelper helper, TestCentralMonitorMachine client,
                                             MonitorGroup group, UUID holderIncarnation,
                                             UUID groupIdentity, UUID moduleSlotIncarnation,
                                             ItemStack synchronizedStack, String message) {
        ItemStack before = group.getItemStackHandler().getStackInSlot(0).copy();
        helper.assertTrue(!SCPacketMonitorGroupDataChange.applyClientStackUpdate(
                client, 0, holderIncarnation, groupIdentity, moduleSlotIncarnation, synchronizedStack), message);
        helper.assertTrue(ItemStack.matches(before, group.getItemStackHandler().getStackInSlot(0)),
                message + " and changed the client module stack");
    }

    private static BlockEntityCreationInfo centralMonitorInfo(BlockPos position) {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), position,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private record TextFixture(ServerPlayer player, TestCentralMonitorMachine machine,
                               MonitorGroup group, ItemStack currentStack) {}

    private static final class TestCentralMonitorMachine extends CentralMonitorMachine {

        private final Map<BlockPos, IMonitorComponent> components = new HashMap<>();
        private final boolean remote;

        private TestCentralMonitorMachine(boolean remote, BlockPos position) {
            super(centralMonitorInfo(position));
            this.remote = remote;
        }

        private void addMonitor(BlockPos position) {
            components.put(position, new TestMonitorComponent(position));
        }

        @Override
        public boolean isRemote() {
            return remote;
        }

        @Override
        protected boolean isMembershipStructureAvailable() {
            return true;
        }

        @Override
        public int getCentralMonitorMembershipCapacity() {
            return components.size();
        }

        @Override
        protected Map<BlockPos, IMonitorComponent> resolveMembershipComponents() {
            return new HashMap<>(components);
        }
    }

    private record TestMonitorComponent(BlockPos position) implements IMonitorComponent {

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
