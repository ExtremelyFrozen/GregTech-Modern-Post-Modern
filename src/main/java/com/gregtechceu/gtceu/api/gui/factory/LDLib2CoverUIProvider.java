package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import net.minecraft.world.entity.player.Player;

/**
 * Creates an LDLib2 cover UI through GTM's stable cover holder contract.
 *
 * <p>This provider lets cover business code depend on {@link UICoverHolder} instead of an LDLib2 menu holder. It
 * exists so LDLib2 screens can migrate in parallel while the legacy runtime remains active.
 */
public interface LDLib2CoverUIProvider {

    /**
     * Builds the LDLib2 cover UI for the provided player and opened cover identity.
     *
     * @param player player building the UI in the current runtime.
     * @param holder GTM holder that exposes the opened cover position, side, definition id, and current cover lookup.
     * @return non-null LDLib2 UI tree.
     */
    ModularUI createLDLib2UI(Player player, UICoverHolder holder);
}
