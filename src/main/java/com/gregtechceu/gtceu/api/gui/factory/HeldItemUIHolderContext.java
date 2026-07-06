package com.gregtechceu.gtceu.api.gui.factory;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Default GTM held item UI holder used by LDLib2 held item providers.
 */
public final class HeldItemUIHolderContext implements HeldItemUIHolder {

    private final Player player;
    private final InteractionHand hand;
    private final ItemStack openedStack;

    public HeldItemUIHolderContext(Player player, InteractionHand hand) {
        this(player, hand, player.getItemInHand(hand));
    }

    public HeldItemUIHolderContext(Player player, InteractionHand hand, ItemStack openedStack) {
        this.player = player;
        this.hand = hand;
        this.openedStack = openedStack.copy();
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public InteractionHand getHand() {
        return hand;
    }

    @Override
    public ItemStack getHeld() {
        return player.getItemInHand(hand);
    }

    @Override
    public ItemStack getOpenedStack() {
        return openedStack;
    }
}
