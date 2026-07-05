package com.gregtechceu.gtceu.api.item.tool.behavior;

import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHolder;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IToolUIBehavior<T extends IToolUIBehavior<T>> extends IToolBehavior<T> {

    @Override
    default @NotNull InteractionResultHolder<ItemStack> onItemRightClick(@NotNull Level level, @NotNull Player player,
                                                                          @NotNull InteractionHand hand) {
        var heldItem = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && (openUI(serverPlayer, hand) ||
                openLDLib2UI(serverPlayer, hand))) {
            HeldItemUIHelper.open(serverPlayer, hand);
            return InteractionResultHolder.success(heldItem);
        }
        return InteractionResultHolder.pass(heldItem);
    }

    boolean openUI(@NotNull Player player, @NotNull InteractionHand hand);

    ModularUI createUI(Player player, HeldItemUIHolder holder);

    /**
     * Returns whether this behavior has a migrated LDLib2 held-item UI for the provided context.
     */
    default boolean openLDLib2UI(@NotNull Player player, @NotNull InteractionHand hand) {
        return false;
    }

    /**
     * Builds the migrated LDLib2 held-item UI for this behavior.
     *
     * @return the LDLib2 UI tree, or {@code null} when this behavior only supports the legacy held-item UI.
     */
    @Nullable
    default UI createLDLib2UI(Player player, HeldItemUIHolder holder) {
        return null;
    }
}
