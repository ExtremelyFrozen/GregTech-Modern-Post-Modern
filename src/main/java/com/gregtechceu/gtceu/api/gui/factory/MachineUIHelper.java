package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.network.packets.CPacketMachineActionToServer;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Centralizes LDLib2 machine UI opening for block-backed machines.
 */
public final class MachineUIHelper {

    private MachineUIHelper() {}

    /**
     * Opens the UI for a machine block.
     *
     * @return {@code true} when the machine provider accepted and opened the menu.
     */
    public static boolean open(MetaMachine machine, ServerPlayer player) {
        if (!(machine instanceof LDLib2MachineUIProvider uiProvider)) {
            return false;
        }
        MachineUIHolderContext holder = new MachineUIHolderContext(player, machine);
        if (!uiProvider.canCreateLDLib2UI(player, holder)) {
            return false;
        }
        return uiProvider.openLDLib2UI(machine, player);
    }

    /**
     * Sends a machine UI sync action to the server.
     */
    public static void sendAction(MachineUIHolder holder, SyncActionData action) {
        PacketDistributor
                .sendToServer(new CPacketMachineActionToServer(holder.getPos(), holder.getMachineDefinitionId(),
                        action));
    }
}
