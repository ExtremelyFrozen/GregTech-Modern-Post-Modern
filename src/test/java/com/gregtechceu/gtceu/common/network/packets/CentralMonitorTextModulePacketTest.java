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

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import io.netty.buffer.Unpooled;

import java.util.Arrays;
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
    public static void clientAppliesOnlyCurrentSynchronizedModuleIdentity(GameTestHelper helper) {
        BlockPos machinePosition = helper.absolutePos(BlockPos.ZERO);
        TestCentralMonitorMachine server = new TestCentralMonitorMachine(false, machinePosition);
        server.setLevel(helper.getLevel());
        server.addMonitor(MONITOR_POSITION);
        helper.assertTrue(server.createCentralMonitorGroup(
                0, UUID.randomUUID(), Set.of(MONITOR_POSITION)),
                "server rejected valid text module packet test group creation");
        MonitorGroup serverGroup = server.getMonitorGroups().getFirst();
        serverGroup.getItemStackHandler().setStackInSlot(0, GTItems.TEXT_MODULE.get().getDefaultInstance());
        serverGroup.applyTextConfiguration(
                new TextLineList(List.of(Component.literal("opening")), 1.0f));

        TestCentralMonitorMachine client = new TestCentralMonitorMachine(true, machinePosition);
        client.setLevel(helper.getLevel());
        DataComponentMap full = server.getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), full);
        MonitorGroup openingGroup = client.getMonitorGroups().getFirst();
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), full);
        MonitorGroup currentGroup = client.getMonitorGroups().getFirst();
        helper.assertTrue(openingGroup != currentGroup,
                "full client sync reused the opening MonitorGroup test object");
        helper.assertTrue(currentGroup.getTextConfigurationRevision() == 1,
                "full client sync did not restore the text configuration revision");

        serverGroup.applyTextConfiguration(
                new TextLineList(List.of(Component.literal("synchronized-format")), 2.0f));
        ItemStack synchronizedStack = serverGroup.getItemStackHandler().getStackInSlot(0).copy();
        synchronizedStack.set(GTDataComponents.TEXT_LINE_LIST.get(),
                new TextLineList(List.of(Component.literal("synchronized")), 1.0f));

        assertClientRejected(helper, client, currentGroup,
                UUID.randomUUID(), currentGroup.getIdentity(), currentGroup.getModuleSlotIncarnation(),
                serverGroup.getTextConfigurationRevision(), synchronizedStack,
                "client accepted a stale holder identity");
        assertClientRejected(helper, client, currentGroup,
                client.getCentralMonitorActionIncarnation(), UUID.randomUUID(),
                currentGroup.getModuleSlotIncarnation(), serverGroup.getTextConfigurationRevision(), synchronizedStack,
                "client accepted a stale group identity");
        assertClientRejected(helper, client, currentGroup,
                client.getCentralMonitorActionIncarnation(), currentGroup.getIdentity(), UUID.randomUUID(),
                serverGroup.getTextConfigurationRevision(), synchronizedStack,
                "client accepted a stale module-slot identity");
        assertClientRejected(helper, client, currentGroup,
                client.getCentralMonitorActionIncarnation(), currentGroup.getIdentity(),
                currentGroup.getModuleSlotIncarnation(), serverGroup.getTextConfigurationRevision(),
                GTItems.IMAGE_MODULE.get().getDefaultInstance(),
                "client accepted a synchronized stack with a different module item");
        assertClientRejected(helper, client, currentGroup,
                client.getCentralMonitorActionIncarnation(), currentGroup.getIdentity(),
                currentGroup.getModuleSlotIncarnation(), -1, synchronizedStack,
                "client accepted a negative text configuration revision");

        UUID slotIncarnation = currentGroup.getModuleSlotIncarnation();
        helper.assertTrue(SCPacketMonitorGroupDataChange.applyClientStackUpdate(
                client, 0,
                client.getCentralMonitorActionIncarnation(),
                currentGroup.getIdentity(),
                slotIncarnation,
                serverGroup.getTextConfigurationRevision(),
                synchronizedStack),
                "client rejected a current Central Monitor module update");
        helper.assertTrue(ItemStack.matches(
                synchronizedStack, currentGroup.getItemStackHandler().getStackInSlot(0)),
                "client did not apply a current Central Monitor module update");
        helper.assertTrue(currentGroup.getModuleSlotIncarnation().equals(slotIncarnation),
                "client module update rotated the physical slot incarnation");
        helper.assertTrue(currentGroup.getTextConfigurationRevision() == 2,
                "client module update did not apply the authoritative text configuration revision");

        ItemStack derivedUpdate = currentGroup.getItemStackHandler().getStackInSlot(0).copy();
        derivedUpdate.set(GTDataComponents.TEXT_LINE_LIST.get(),
                new TextLineList(List.of(Component.literal("derived-update")), 1.0f));
        helper.assertTrue(SCPacketMonitorGroupDataChange.applyClientStackUpdate(
                client, 0, client.getCentralMonitorActionIncarnation(), currentGroup.getIdentity(),
                slotIncarnation, 2, derivedUpdate),
                "client rejected a derived module update at the current text configuration revision");
        helper.assertTrue(ItemStack.matches(
                derivedUpdate, currentGroup.getItemStackHandler().getStackInSlot(0)) &&
                currentGroup.getTextConfigurationRevision() == 2,
                "current-revision derived module update changed or lost authoritative state");
        assertClientRejected(helper, client, currentGroup,
                client.getCentralMonitorActionIncarnation(), currentGroup.getIdentity(), slotIncarnation,
                1, currentGroup.getItemStackHandler().getStackInSlot(0).copy(),
                "client accepted a stale text configuration revision");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void serverPacketConstructorRequiresCurrentUniqueGroupIdentity(GameTestHelper helper) {
        BlockPos machinePosition = helper.absolutePos(BlockPos.ZERO);
        TestCentralMonitorMachine server = new TestCentralMonitorMachine(false, machinePosition);
        server.setLevel(helper.getLevel());
        server.addMonitor(MONITOR_POSITION);
        helper.assertTrue(server.createCentralMonitorGroup(
                0, UUID.randomUUID(), Set.of(MONITOR_POSITION)),
                "server rejected valid packet-constructor test group creation");
        MonitorGroup group = server.getMonitorGroups().getFirst();
        ItemStack module = GTItems.TEXT_MODULE.get().getDefaultInstance();
        group.getItemStackHandler().setStackInSlot(0, module);

        boolean detachedGroupRejected = false;
        try {
            new SCPacketMonitorGroupDataChange(module, new MonitorGroup("detached"), server);
        } catch (IllegalArgumentException expected) {
            detachedGroupRejected = true;
        }
        helper.assertTrue(detachedGroupRejected,
                "Central Monitor S2C packet constructor accepted a group outside its machine");

        MonitorGroup duplicateIdentity = MonitorGroup.restore(
                group.getIdentity(), UUID.randomUUID(), UUID.randomUUID(), "duplicate",
                MonitorGroup.createModuleHandler(), new CustomItemStackHandler(8));
        server.getMonitorGroups().add(duplicateIdentity);
        boolean duplicateIdentityRejected = false;
        try {
            new SCPacketMonitorGroupDataChange(module, group, server);
        } catch (IllegalArgumentException expected) {
            duplicateIdentityRejected = true;
        } finally {
            server.getMonitorGroups().remove(duplicateIdentity);
        }
        helper.assertTrue(duplicateIdentityRejected,
                "Central Monitor S2C packet constructor accepted a duplicate group identity");

        new SCPacketMonitorGroupDataChange(module, group, server);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void serverPacketFreezesStackAtConstruction(GameTestHelper helper) {
        BlockPos machinePosition = helper.absolutePos(BlockPos.ZERO);
        TestCentralMonitorMachine server = new TestCentralMonitorMachine(false, machinePosition);
        server.setLevel(helper.getLevel());
        server.addMonitor(MONITOR_POSITION);
        helper.assertTrue(server.createCentralMonitorGroup(
                0, UUID.randomUUID(), Set.of(MONITOR_POSITION)),
                "server rejected packet snapshot test group creation");
        MonitorGroup group = server.getMonitorGroups().getFirst();
        ItemStack module = GTItems.TEXT_MODULE.get().getDefaultInstance();
        module.set(GTDataComponents.FORMAT_STRING_LIST.get(),
                new TextLineList(List.of(Component.literal("opening")), 1.0f));
        group.getItemStackHandler().setStackInSlot(0, module);

        SCPacketMonitorGroupDataChange packet = new SCPacketMonitorGroupDataChange(module, group, server);
        RegistryFriendlyByteBuf openingWire = newBuffer(helper);
        RegistryFriendlyByteBuf changedWire = newBuffer(helper);
        try {
            packet.encode(openingWire);
            group.applyTextConfiguration(new TextLineList(List.of(Component.literal("changed")), 2.0f));
            packet.encode(changedWire);

            helper.assertTrue(Arrays.equals(readableBytes(openingWire), readableBytes(changedWire)),
                    "Central Monitor S2C packet changed after its source module stack was mutated");
        } finally {
            openingWire.release();
            changedWire.release();
        }
        helper.succeed();
    }

    private static void assertClientRejected(GameTestHelper helper, TestCentralMonitorMachine client,
                                             MonitorGroup group, UUID holderIncarnation,
                                             UUID groupIdentity, UUID moduleSlotIncarnation,
                                             long textConfigurationRevision, ItemStack synchronizedStack,
                                             String message) {
        ItemStack before = group.getItemStackHandler().getStackInSlot(0).copy();
        long revisionBefore = group.getTextConfigurationRevision();
        helper.assertTrue(!SCPacketMonitorGroupDataChange.applyClientStackUpdate(
                client, 0, holderIncarnation, groupIdentity, moduleSlotIncarnation,
                textConfigurationRevision, synchronizedStack), message);
        helper.assertTrue(ItemStack.matches(before, group.getItemStackHandler().getStackInSlot(0)),
                message + " and changed the client module stack");
        helper.assertTrue(group.getTextConfigurationRevision() == revisionBefore,
                message + " and changed the client text configuration revision");
    }

    private static BlockEntityCreationInfo centralMonitorInfo(BlockPos position) {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), position,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private static RegistryFriendlyByteBuf newBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.OTHER);
    }

    private static byte[] readableBytes(RegistryFriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }

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
        public boolean isMonitor() {
            return true;
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
