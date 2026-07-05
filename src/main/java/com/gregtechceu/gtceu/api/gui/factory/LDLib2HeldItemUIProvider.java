package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import net.minecraft.world.entity.player.Player;

/**
 * Creates an LDLib2 held item UI through GTM's stable held item holder contract.
 *
 * <p>This provider lets item business code depend on {@link HeldItemUIHolder} instead of an LDLib2 menu holder. It
 * exists so LDLib2 screens can migrate in parallel while the legacy runtime remains active.
 */
public interface LDLib2HeldItemUIProvider {

    /**
     * Builds the LDLib2 held item UI for the provided player and opened item identity.
     *
     * @param player player building the UI in the current runtime.
     * @param holder GTM holder that exposes the player, hand, current stack, and opened stack snapshot.
     * @return non-null LDLib2 UI tree.
     */
    ModularUI createLDLib2UI(Player player, HeldItemUIHolder holder);
}
