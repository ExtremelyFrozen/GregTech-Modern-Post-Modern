package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Default held item UI holder used by the GTM compatibility factory.
 */
public final class HeldItemUIHolderContext implements HeldItemUIHolder {

    private final Player player;
    private final InteractionHand hand;
    private final ItemStack openedStack;

    public HeldItemUIHolderContext(Player player, InteractionHand hand) {
        this.player = player;
        this.hand = hand;
        this.openedStack = player.getItemInHand(hand).copy();
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

    @Nullable
    @Override
    public ModularUI createUI(Player player) {
        if (getHeld().getItem() instanceof HeldItemUIProvider uiProvider) {
            return uiProvider.createUI(player, this);
        }
        return null;
    }

    @Override
    public boolean isInvalid() {
        return !ItemStack.matches(player.getItemInHand(hand), openedStack);
    }

    @Override
    public boolean isRemote() {
        return player.level().isClientSide;
    }

    @Override
    public void markAsDirty() {
        // Required by legacy IUIHolder; GTM item UI state must use automatic sync or action packets.
    }
}
