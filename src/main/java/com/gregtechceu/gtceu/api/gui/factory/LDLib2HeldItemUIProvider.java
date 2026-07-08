package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Creates an LDLib2 held item UI through GTM's stable held item holder contract.
 *
 * <p>
 * Implementing this provider automatically opts the item into LDLib2's {@link HeldItemUIMenuType} held item menu.
 * The bridge still adapts LDLib2's native holder into GTM's {@link HeldItemUIHolder}, so item business code continues
 * to receive the GTM holder contract.
 */
public interface LDLib2HeldItemUIProvider extends HeldItemUIMenuType.HeldItemUI {

    @Override
    default HeldItemUIMenuType.HeldItemUIHolder createUIHolder(Player player, InteractionHand hand,
                                                               ItemStack itemStack) {
        return new HeldItemUIMenuType.HeldItemUIHolder(this, player, hand, itemStack.copy());
    }

    @Override
    default boolean stillValid(HeldItemUIMenuType.HeldItemUIHolder holder) {
        HeldItemUIHolderContext gtmHolder = new HeldItemUIHolderContext(holder.player, holder.hand, holder.itemStack);
        return isLDLib2UIStillValid(holder.player, gtmHolder);
    }

    @Override
    default ModularUI createUI(HeldItemUIMenuType.HeldItemUIHolder holder) {
        HeldItemUIHolderContext gtmHolder = new HeldItemUIHolderContext(holder.player, holder.hand, holder.itemStack);
        if (!canCreateLDLib2UI(holder.player, gtmHolder)) {
            throw new IllegalStateException("Held item does not expose an LDLib2 UI for the opened stack.");
        }
        UI ui = createLDLib2UI(holder.player, gtmHolder);
        if (ui == null) {
            throw new IllegalStateException("Held item LDLib2 UI provider returned null.");
        }
        return ModularUI.of(ui, holder.player);
    }

    /**
     * Returns whether this item should use LDLib2 for the provided held item context.
     *
     * <p>
     * Composite items can implement the provider globally while using this method to route only components that
     * own a held item UI for the opened stack.
     */
    default boolean canCreateLDLib2UI(Player player, HeldItemUIHolder holder) {
        return true;
    }

    /**
     * Returns whether the currently held stack still belongs to this opened LDLib2 UI.
     *
     * <p>
     * The default keeps strict stack matching. Mutable item UIs can override this through their component provider
     * and validate action payloads server-side.
     */
    default boolean isLDLib2UIStillValid(Player player, HeldItemUIHolder holder) {
        return ItemStack.matches(holder.getHeld(), holder.getOpenedStack());
    }

    /**
     * Builds the LDLib2 held item UI tree for the provided player and opened item identity.
     *
     * @param player player building the UI in the current runtime.
     * @param holder GTM holder that exposes the player, hand, current stack, and opened stack snapshot.
     * @return non-null LDLib2 UI tree.
     */
    UI createLDLib2UI(Player player, HeldItemUIHolder holder);
}
