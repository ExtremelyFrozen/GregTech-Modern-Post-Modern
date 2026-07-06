package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.world.entity.player.Player;

/**
 * Creates an LDLib2 machine UI through GTM's stable machine holder contract.
 *
 * <p>This provider lets machine business code depend on {@link MachineUIHolder} instead of an LDLib2 menu holder.
 * It exists so LDLib2 screens can migrate in parallel while the legacy runtime remains active.
 */
public interface LDLib2MachineUIProvider {

    /**
     * Returns whether this machine should use LDLib2 for the provided holder context.
     *
     * <p>Machine implementations can expose the provider while keeping selected instances on the legacy UI path until
     * their screen tree has been migrated.
     */
    default boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return true;
    }

    /**
     * Builds the LDLib2 machine UI tree for the provided player and opened machine identity.
     *
     * @param player player building the UI in the current runtime.
     * @param holder GTM holder that exposes the opened machine position, definition id, and current machine lookup.
     * @return non-null LDLib2 UI tree.
     */
    UI createLDLib2UI(Player player, MachineUIHolder holder);
}
