package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.network.packets.CPacketMachineActionToServer;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Centralizes machine UI opening while GTM migrates block-backed screens to LDLib2.
 */
public final class MachineUIHelper {

    private MachineUIHelper() {}

    /**
     * Opens the UI for a machine block.
     *
     * @return {@code true} when the menu was opened by the current bridge implementation.
     */
    public static boolean open(MetaMachine machine, ServerPlayer player) {
        if (machine instanceof LDLib2MachineUIProvider uiProvider) {
            MachineUIHolderContext holder = new MachineUIHolderContext(player, machine);
            if (uiProvider.canCreateLDLib2UI(player, holder)) {
                return BlockUIMenuType.openUI(player, machine.getBlockPos());
            }
        }
        return MachineUIFactory.INSTANCE.openUI(machine, player);
    }

    /**
     * Sends a machine UI sync action to the server.
     */
    public static void sendAction(MachineUIHolder holder, SyncActionData action) {
        PacketDistributor.sendToServer(new CPacketMachineActionToServer(holder.getPos(), holder.getMachineDefinitionId(),
                action));
    }
}
