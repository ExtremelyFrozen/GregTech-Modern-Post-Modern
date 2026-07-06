package com.gregtechceu.gtceu.api.item.tool.behavior;

import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

public interface IToolUIBehavior<T extends IToolUIBehavior<T>> extends IToolBehavior<T> {

    @Override
    default @NotNull InteractionResultHolder<ItemStack> onItemRightClick(@NotNull Level level, @NotNull Player player,
                                                                          @NotNull InteractionHand hand) {
        var heldItem = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && openLDLib2UI(serverPlayer, hand)) {
            HeldItemUIHelper.open(serverPlayer, hand);
            return InteractionResultHolder.success(heldItem);
        }
        return InteractionResultHolder.pass(heldItem);
    }

    /**
     * Returns whether this behavior has a migrated LDLib2 held-item UI for the provided context.
     */
    boolean openLDLib2UI(@NotNull Player player, @NotNull InteractionHand hand);

    /**
     * Returns whether this behavior owns an LDLib2 UI for the currently opened held item context.
     */
    boolean canCreateLDLib2UI(Player player, HeldItemUIHolder holder);

    /**
     * Returns whether the currently held stack still belongs to this behavior's opened LDLib2 UI.
     */
    default boolean isLDLib2UIStillValid(Player player, HeldItemUIHolder holder) {
        return ItemStack.matches(holder.getHeld(), holder.getOpenedStack());
    }

    /**
     * Builds the migrated LDLib2 held-item UI for this behavior.
     */
    UI createLDLib2UI(Player player, HeldItemUIHolder holder);
}
