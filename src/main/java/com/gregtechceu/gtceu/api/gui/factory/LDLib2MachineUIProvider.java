package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import net.minecraft.world.entity.player.Player;

/**
 * Creates an LDLib2 machine UI through GTM's stable machine holder contract.
 *
 * <p>This provider lets machine business code depend on {@link MachineUIHolder} instead of an LDLib2 menu holder.
 * It exists so LDLib2 screens can migrate in parallel while the legacy runtime remains active.
 */
public interface LDLib2MachineUIProvider {

    /**
     * Builds the LDLib2 machine UI for the provided player and opened machine identity.
     *
     * @param player player building the UI in the current runtime.
     * @param holder GTM holder that exposes the opened machine position, definition id, and current machine lookup.
     * @return non-null LDLib2 UI tree.
     */
    ModularUI createLDLib2UI(Player player, MachineUIHolder holder);
}
