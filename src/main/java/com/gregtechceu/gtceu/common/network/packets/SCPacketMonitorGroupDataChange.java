package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.item.modules.TextModuleBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SCPacketMonitorGroupDataChange implements CustomPacketPayload {

    private static final float MIN_TEXT_SCALE = 0.0001f;
    private static final float MAX_TEXT_SCALE = 1000.0f;
    static final double MAX_INTERACTION_DISTANCE = 8.0;

    public static final ResourceLocation ID = GTCEu.id("spacket_monitor_group_data_change");
    public static final Type<SCPacketMonitorGroupDataChange> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SCPacketMonitorGroupDataChange> CODEC = StreamCodec
            .ofMember(SCPacketMonitorGroupDataChange::encode, SCPacketMonitorGroupDataChange::new);

    private final ItemStack stack;
    private final int monitorGroupId;
    private final BlockPos pos;
    private final UUID holderIncarnation;
    private final UUID groupIdentity;
    private final UUID moduleSlotIncarnation;

    public SCPacketMonitorGroupDataChange(ItemStack stack, MonitorGroup group, CentralMonitorMachine machine) {
        int groupId = -1;
        MonitorGroup currentGroup = null;
        for (int index = 0; index < machine.getMonitorGroups().size(); index++) {
            MonitorGroup candidate = machine.getMonitorGroups().get(index);
            if (!candidate.getIdentity().equals(group.getIdentity())) {
                continue;
            }
            if (currentGroup != null) {
                GTCEu.LOGGER.error(
                        "Cannot create Central Monitor group data packet for duplicate group identity {} at {}",
                        group.getIdentity(), machine.getBlockPos());
                throw new IllegalArgumentException("Central Monitor packet group identity is not unique");
            }
            currentGroup = candidate;
            groupId = index;
        }
        if (currentGroup == null ||
                !currentGroup.getModuleSlotIncarnation().equals(group.getModuleSlotIncarnation())) {
            GTCEu.LOGGER.error("Cannot create Central Monitor group data packet for a stale group at {}",
                    machine.getBlockPos());
            throw new IllegalArgumentException("Central Monitor packet group no longer matches its machine");
        }
        this.stack = stack;
        this.monitorGroupId = groupId;
        this.pos = machine.getBlockPos();
        this.holderIncarnation = machine.getCentralMonitorActionIncarnation();
        this.groupIdentity = currentGroup.getIdentity();
        this.moduleSlotIncarnation = currentGroup.getModuleSlotIncarnation();
    }

    public SCPacketMonitorGroupDataChange(RegistryFriendlyByteBuf buf) {
        this.stack = ItemStack.STREAM_CODEC.decode(buf);
        this.monitorGroupId = buf.readVarInt();
        this.pos = buf.readBlockPos();
        this.holderIncarnation = buf.readUUID();
        this.groupIdentity = buf.readUUID();
        this.moduleSlotIncarnation = buf.readUUID();
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        ItemStack.STREAM_CODEC.encode(buffer, stack);
        // buffer.writeItemStack(stack, false);
        buffer.writeVarInt(monitorGroupId);
        buffer.writeBlockPos(pos);
        buffer.writeUUID(holderIncarnation);
        buffer.writeUUID(groupIdentity);
        buffer.writeUUID(moduleSlotIncarnation);
    }

    public void execute(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            applyServerPacket(player);
            return;
        }

        Level level = ClientCallWrapper.getClientLevel();
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (machine instanceof CentralMonitorMachine centralMonitor) {
            applyClientStackUpdate(centralMonitor, monitorGroupId, holderIncarnation, groupIdentity,
                    moduleSlotIncarnation, stack);
        }
    }

    private void applyServerPacket(ServerPlayer player) {
        Level level = player.level();
        if (!level.isLoaded(pos)) {
            GTCEu.LOGGER.warn("Rejecting legacy Central Monitor write from {} because {} is not loaded",
                    player.getGameProfile().getName(), pos);
            return;
        }
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (!(machine instanceof CentralMonitorMachine centralMonitor)) {
            GTCEu.LOGGER.warn("Rejecting legacy Central Monitor write from {} because holder at {} is invalid",
                    player.getGameProfile().getName(), pos);
            return;
        }
        applyServerTextConfiguration(centralMonitor, monitorGroupId, holderIncarnation, groupIdentity,
                moduleSlotIncarnation, stack, player);
    }

    static boolean applyServerTextConfiguration(CentralMonitorMachine centralMonitor, int monitorGroupId,
                                                UUID holderIncarnation, UUID groupIdentity,
                                                UUID moduleSlotIncarnation, ItemStack requestedStack,
                                                ServerPlayer player) {
        if (!isServerWriteAuthorized(centralMonitor, player)) {
            return false;
        }
        MonitorGroup group = resolveMatchingGroup(centralMonitor, monitorGroupId, holderIncarnation,
                groupIdentity, moduleSlotIncarnation);
        if (group == null) {
            return false;
        }
        IItemHandlerModifiable itemHandler = group.getItemStackHandler();
        ItemStack currentStack = itemHandler.getStackInSlot(0);
        if (!isTextModule(currentStack)) {
            GTCEu.LOGGER.warn(
                    "Rejecting non-text legacy module write from {} for group {} at {}",
                    player.getGameProfile().getName(), monitorGroupId, centralMonitor.getBlockPos());
            return false;
        }
        if (!matchesTextModuleSnapshot(currentStack, requestedStack)) {
            GTCEu.LOGGER.warn(
                    "Rejecting legacy text module write from {} because group {} at {} has a mismatched module snapshot",
                    player.getGameProfile().getName(), monitorGroupId, centralMonitor.getBlockPos());
            return false;
        }
        Optional<TextLineList> requestedConfiguration = readRequestedTextConfiguration(requestedStack);
        if (requestedConfiguration.isEmpty()) {
            GTCEu.LOGGER.warn(
                    "Rejecting legacy text module write from {} because group {} at {} has invalid text configuration",
                    player.getGameProfile().getName(), monitorGroupId, centralMonitor.getBlockPos());
            return false;
        }

        currentStack.set(GTDataComponents.FORMAT_STRING_LIST.get(), requestedConfiguration.get());
        centralMonitor.markMonitorGroupDataChanged();
        return true;
    }

    static boolean applyClientStackUpdate(CentralMonitorMachine centralMonitor, int monitorGroupId,
                                          UUID holderIncarnation, UUID groupIdentity,
                                          UUID moduleSlotIncarnation, ItemStack synchronizedStack) {
        MonitorGroup group = resolveMatchingGroup(centralMonitor, monitorGroupId, holderIncarnation,
                groupIdentity, moduleSlotIncarnation);
        if (group == null) {
            return false;
        }
        IItemHandlerModifiable itemHandler = group.getItemStackHandler();
        if (ItemStack.isSameItem(itemHandler.getStackInSlot(0), synchronizedStack)) {
            itemHandler.setStackInSlot(0, synchronizedStack);
            return true;
        }
        GTCEu.LOGGER.warn("Rejecting Central Monitor client update with a mismatched module at {}",
                centralMonitor.getBlockPos());
        return false;
    }

    private static boolean isServerWriteAuthorized(CentralMonitorMachine centralMonitor, ServerPlayer player) {
        BlockPos monitorPos = centralMonitor.getBlockPos();
        if (!player.level().isLoaded(monitorPos)) {
            GTCEu.LOGGER.warn("Rejecting legacy Central Monitor write from {} because {} is not loaded",
                    player.getGameProfile().getName(), monitorPos);
            return false;
        }
        if (player.isSpectator()) {
            GTCEu.LOGGER.warn("Rejecting legacy Central Monitor write from spectator {} at {}",
                    player.getGameProfile().getName(), monitorPos);
            return false;
        }
        if (!player.canInteractWithBlock(monitorPos, MAX_INTERACTION_DISTANCE)) {
            GTCEu.LOGGER.warn("Rejecting legacy Central Monitor write from {} outside interaction range at {}",
                    player.getGameProfile().getName(), monitorPos);
            return false;
        }
        if (!MachineOwner.canOpenOwnerMachine(player, centralMonitor)) {
            GTCEu.LOGGER.warn("Rejecting legacy Central Monitor write from {} because owner permission failed at {}",
                    player.getGameProfile().getName(), monitorPos);
            return false;
        }
        return true;
    }

    private static @Nullable MonitorGroup resolveMatchingGroup(CentralMonitorMachine centralMonitor,
                                                               int monitorGroupId, UUID holderIncarnation,
                                                               UUID groupIdentity, UUID moduleSlotIncarnation) {
        if (!centralMonitor.getCentralMonitorActionIncarnation().equals(holderIncarnation)) {
            GTCEu.LOGGER.warn("Rejecting stale Central Monitor packet with a mismatched holder identity at {}",
                    centralMonitor.getBlockPos());
            return null;
        }
        if (monitorGroupId < 0 || monitorGroupId >= centralMonitor.getMonitorGroups().size()) {
            GTCEu.LOGGER.warn("Rejecting Central Monitor packet with invalid group index {} at {}",
                    monitorGroupId, centralMonitor.getBlockPos());
            return null;
        }
        MonitorGroup group = centralMonitor.getMonitorGroups().get(monitorGroupId);
        if (!group.getIdentity().equals(groupIdentity)) {
            GTCEu.LOGGER.warn("Rejecting stale Central Monitor packet with a mismatched group identity at {}",
                    centralMonitor.getBlockPos());
            return null;
        }
        if (!group.getModuleSlotIncarnation().equals(moduleSlotIncarnation)) {
            GTCEu.LOGGER.warn("Rejecting stale Central Monitor packet with a mismatched module-slot identity at {}",
                    centralMonitor.getBlockPos());
            return null;
        }
        return group;
    }

    private static boolean isTextModule(ItemStack stack) {
        if (!(stack.getItem() instanceof IComponentItem componentItem)) {
            return false;
        }
        for (var component : componentItem.getComponents()) {
            if (component instanceof TextModuleBehaviour) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesTextModuleSnapshot(ItemStack currentStack, ItemStack requestedStack) {
        if (!isTextModule(requestedStack)) {
            return false;
        }
        ItemStack currentSnapshot = stableTextModuleSnapshot(currentStack);
        ItemStack requestedSnapshot = stableTextModuleSnapshot(requestedStack);
        return ItemStack.matches(currentSnapshot, requestedSnapshot);
    }

    private static ItemStack stableTextModuleSnapshot(ItemStack stack) {
        ItemStack snapshot = stack.copy();
        snapshot.remove(GTDataComponents.FORMAT_STRING_LIST.get());
        snapshot.remove(GTDataComponents.PLACEHOLDER_UUID.get());
        snapshot.remove(GTDataComponents.TEXT_LINE_LIST.get());
        return snapshot;
    }

    private static Optional<TextLineList> readRequestedTextConfiguration(ItemStack requestedStack) {
        TextLineList requestedConfiguration = requestedStack.get(GTDataComponents.FORMAT_STRING_LIST.get());
        if (requestedConfiguration == null || !Float.isFinite(requestedConfiguration.scale()) ||
                requestedConfiguration.scale() < MIN_TEXT_SCALE ||
                requestedConfiguration.scale() > MAX_TEXT_SCALE) {
            return Optional.empty();
        }
        List<Component> plainLines = requestedConfiguration.lines()
                .stream()
                .map(Component::getString)
                .map(Component::literal)
                .map(Component.class::cast)
                .toList();
        return Optional.of(new TextLineList(plainLines, requestedConfiguration.scale()));
    }

    private static class ClientCallWrapper {

        private static Level getClientLevel() {
            return Minecraft.getInstance().level;
        }
    }

    @Override
    public @NotNull Type<SCPacketMonitorGroupDataChange> type() {
        return TYPE;
    }
}
