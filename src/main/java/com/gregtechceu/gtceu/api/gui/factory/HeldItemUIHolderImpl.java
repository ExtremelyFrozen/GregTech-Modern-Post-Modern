package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Default held item UI holder used by the GTM compatibility factory.
 */
public final class HeldItemUIHolderImpl implements HeldItemUIHolder {

    private final Player player;
    private final InteractionHand hand;
    private final ItemStack held;

    public HeldItemUIHolderImpl(Player player, InteractionHand hand) {
        this.player = player;
        this.hand = hand;
        this.held = player.getItemInHand(hand);
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
        return held;
    }

    @Nullable
    @Override
    public ModularUI createUI(Player player) {
        if (held.getItem() instanceof HeldItemUIProvider uiProvider) {
            return uiProvider.createUI(player, this);
        }
        return null;
    }

    @Override
    public boolean isInvalid() {
        return !ItemStack.isSameItemSameComponents(player.getItemInHand(hand), held);
    }

    @Override
    public boolean isRemote() {
        return player.level().isClientSide;
    }

    @Override
    public void markAsDirty() {
        // Held item UI changes update the opened stack directly.
    }
}
