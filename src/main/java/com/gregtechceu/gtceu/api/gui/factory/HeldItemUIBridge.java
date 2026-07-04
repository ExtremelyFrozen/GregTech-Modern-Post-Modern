package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Centralizes held item UI opening while GTM migrates item screens from LDLib to LDLib2.
 */
public final class HeldItemUIBridge {

    private HeldItemUIBridge() {}

    /**
     * Opens the UI for the item currently held in {@code hand}.
     *
     * @return {@code true} when the menu was opened by the current bridge implementation.
     */
    public static boolean open(ServerPlayer player, InteractionHand hand) {
        return HeldItemUIFactory.INSTANCE.openUI(player, hand);
    }
}
