package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.world.entity.player.Player;

/**
 * Creates an LDLib2 cover UI through GTM's stable cover holder contract.
 *
 * <p>This provider lets cover business code depend on {@link UICoverHolder} instead of an LDLib2 menu holder.
 */
public interface LDLib2CoverUIProvider {

    /**
     * Returns whether this cover should use LDLib2 for the provided holder context.
     */
    boolean canCreateLDLib2UI(Player player, UICoverHolder holder);

    /**
     * Builds the LDLib2 cover UI tree for the provided player and opened cover identity.
     *
     * @param player player building the UI in the current runtime.
     * @param holder GTM holder that exposes the opened cover position, side, definition id, and current cover lookup.
     * @return non-null LDLib2 UI tree.
     */
    UI createLDLib2UI(Player player, UICoverHolder holder);
}
