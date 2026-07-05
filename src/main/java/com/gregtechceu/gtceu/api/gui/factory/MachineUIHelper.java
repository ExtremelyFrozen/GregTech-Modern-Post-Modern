package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.network.packets.CPacketMachineActionToServer;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

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
        if (machine instanceof LDLib2MachineUIProvider && BlockUIMenuType.openUI(player, machine.getBlockPos())) {
            return true;
        }
        return MachineUIFactory.INSTANCE.openUI(machine, player);
    }

    /**
     * Creates the parallel LDLib2 UI when the machine exposes the LDLib2 provider contract.
     *
     * @return the LDLib2 UI, or {@code null} when the machine has not migrated to the parallel provider contract.
     */
    @Nullable
    public static ModularUI createLDLib2UI(MetaMachine machine, Player player) {
        if (machine instanceof LDLib2MachineUIProvider uiProvider) {
            MachineUIHolderContext holder = new MachineUIHolderContext(player, machine);
            return ModularUI.of(Objects.requireNonNull(uiProvider.createLDLib2UI(player, holder),
                    "LDLib2 machine UI provider returned null"), player);
        }
        return null;
    }

    /**
     * Sends a machine UI sync action to the server.
     */
    public static void sendAction(MachineUIHolder holder, SyncActionData action) {
        PacketDistributor.sendToServer(new CPacketMachineActionToServer(holder.getPos(), holder.getMachineDefinitionId(),
                action));
    }
}
