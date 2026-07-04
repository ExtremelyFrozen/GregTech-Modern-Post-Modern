package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

/**
 * Creates a held item UI for items whose behavior is owned by GTM.
 */
public interface HeldItemUIProvider {

    /**
     * Builds the held item UI for the provided player and holder context.
     */
    @Nullable
    ModularUI createUI(Player player, HeldItemUIHolder holder);
}
