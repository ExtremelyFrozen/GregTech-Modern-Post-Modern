package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Holds the player, hand, and opened stack needed to build a held item UI.
 *
 * <p>This interface keeps GTM item and tool UI code independent from LDLib's held item factory while the
 * current held item screens still build legacy {@link ModularUI} trees.
 */
public interface HeldItemUIHolder extends IUIHolder {

    /**
     * Returns the player that opened the held item UI.
     */
    Player getPlayer();

    /**
     * Returns the hand used to open the held item UI.
     */
    InteractionHand getHand();

    /**
     * Returns the current item stack in the hand used to open the UI.
     */
    ItemStack getHeld();

    /**
     * Returns the item stack snapshot captured when the UI was opened.
     */
    ItemStack getOpenedStack();
}
