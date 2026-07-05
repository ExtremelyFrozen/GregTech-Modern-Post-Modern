package com.gregtechceu.gtceu.api.item.component;

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

import org.jetbrains.annotations.Nullable;

public interface IItemUIFactory extends IInteractionItem {

    ModularUI createUI(HeldItemUIHolder holder, Player entityPlayer);

    /**
     * Returns whether this item component has a migrated LDLib2 held-item UI for the provided context.
     */
    default boolean canCreateLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        return false;
    }

    /**
     * Returns whether the currently held stack still belongs to this component's opened LDLib2 UI.
     */
    default boolean isLDLib2UIStillValid(HeldItemUIHolder holder, Player entityPlayer) {
        return ItemStack.matches(holder.getHeld(), holder.getOpenedStack());
    }

    /**
     * Builds the migrated LDLib2 held-item UI for this component.
     *
     * @return the LDLib2 UI tree, or {@code null} when this component only supports the legacy held-item UI.
     */
    @Nullable
    default UI createLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        return null;
    }

    @Override
    default InteractionResultHolder<ItemStack> use(ItemStack item, Level level, Player player,
                                                   InteractionHand usedHand) {
        if (player instanceof ServerPlayer serverPlayer) {
            HeldItemUIHelper.open(serverPlayer, usedHand);
        }
        return InteractionResultHolder.sidedSuccess(item, level.isClientSide());
    }
}
