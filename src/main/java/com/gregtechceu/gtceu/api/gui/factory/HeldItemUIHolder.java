package com.gregtechceu.gtceu.api.gui.factory;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Holds the player, hand, and opened stack needed to build a held item UI.
 *
 * <p>
 * This interface keeps GTM item and tool UI code independent from LDLib2's native held item holder.
 */
public interface HeldItemUIHolder {

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
