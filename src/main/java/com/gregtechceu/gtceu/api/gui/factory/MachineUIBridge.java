package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;

import net.minecraft.server.level.ServerPlayer;

/**
 * Centralizes machine UI opening while GTM migrates block-backed screens to LDLib2.
 */
public final class MachineUIBridge {

    private MachineUIBridge() {}

    /**
     * Opens the UI for a machine block.
     *
     * @return {@code true} when the menu was opened by the current bridge implementation.
     */
    public static boolean open(MetaMachine machine, ServerPlayer player) {
        if (BlockUIMenuType.openUI(player, machine.getBlockPos())) {
            return true;
        }
        return MachineUIFactory.INSTANCE.openUI(machine, player);
    }
}
