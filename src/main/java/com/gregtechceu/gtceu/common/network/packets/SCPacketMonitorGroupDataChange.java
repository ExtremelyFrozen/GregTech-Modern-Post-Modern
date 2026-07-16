package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class SCPacketMonitorGroupDataChange implements CustomPacketPayload {

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
    private final long textConfigurationRevision;

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
        this.stack = stack.copy();
        this.monitorGroupId = groupId;
        this.pos = machine.getBlockPos();
        this.holderIncarnation = machine.getCentralMonitorActionIncarnation();
        this.groupIdentity = currentGroup.getIdentity();
        this.moduleSlotIncarnation = currentGroup.getModuleSlotIncarnation();
        this.textConfigurationRevision = currentGroup.getTextConfigurationRevision();
    }

    public SCPacketMonitorGroupDataChange(RegistryFriendlyByteBuf buf) {
        this.stack = ItemStack.STREAM_CODEC.decode(buf);
        this.monitorGroupId = buf.readVarInt();
        this.pos = buf.readBlockPos();
        this.holderIncarnation = buf.readUUID();
        this.groupIdentity = buf.readUUID();
        this.moduleSlotIncarnation = buf.readUUID();
        this.textConfigurationRevision = buf.readVarLong();
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        ItemStack.STREAM_CODEC.encode(buffer, stack);
        // buffer.writeItemStack(stack, false);
        buffer.writeVarInt(monitorGroupId);
        buffer.writeBlockPos(pos);
        buffer.writeUUID(holderIncarnation);
        buffer.writeUUID(groupIdentity);
        buffer.writeUUID(moduleSlotIncarnation);
        buffer.writeVarLong(textConfigurationRevision);
    }

    public void execute(IPayloadContext context) {
        Level level = ClientCallWrapper.getClientLevel();
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (machine instanceof CentralMonitorMachine centralMonitor) {
            applyClientStackUpdate(centralMonitor, monitorGroupId, holderIncarnation, groupIdentity,
                    moduleSlotIncarnation, textConfigurationRevision, stack);
        }
    }

    static boolean applyClientStackUpdate(CentralMonitorMachine centralMonitor, int monitorGroupId,
                                          UUID holderIncarnation, UUID groupIdentity,
                                          UUID moduleSlotIncarnation, long textConfigurationRevision,
                                          ItemStack synchronizedStack) {
        MonitorGroup group = resolveMatchingGroup(centralMonitor, monitorGroupId, holderIncarnation,
                groupIdentity, moduleSlotIncarnation);
        if (group == null) {
            return false;
        }
        if (textConfigurationRevision < 0 || textConfigurationRevision < group.getTextConfigurationRevision()) {
            GTCEu.LOGGER.warn("Rejecting stale Central Monitor text configuration revision {} at {}",
                    textConfigurationRevision, centralMonitor.getBlockPos());
            return false;
        }
        IItemHandlerModifiable itemHandler = group.getItemStackHandler();
        if (ItemStack.isSameItem(itemHandler.getStackInSlot(0), synchronizedStack)) {
            itemHandler.setStackInSlot(0, synchronizedStack);
            group.setTextConfigurationRevision(textConfigurationRevision);
            return true;
        }
        GTCEu.LOGGER.warn("Rejecting Central Monitor client update with a mismatched module at {}",
                centralMonitor.getBlockPos());
        return false;
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
