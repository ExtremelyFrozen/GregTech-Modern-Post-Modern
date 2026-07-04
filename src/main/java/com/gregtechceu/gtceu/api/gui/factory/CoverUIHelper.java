package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;

import net.minecraft.server.level.ServerPlayer;

/**
 * Centralizes cover UI opening while GTM migrates cover screens to an LDLib2 holder.
 */
public final class CoverUIHelper {

    private CoverUIHelper() {}

    /**
     * Opens the UI for a cover attached to a block face.
     *
     * @return {@code true} when the menu was opened by the current bridge implementation.
     */
    public static boolean open(CoverBehavior cover, ServerPlayer player) {
        return CoverUIFactory.INSTANCE.openUI(cover, player);
    }
}
