package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.world.entity.player.Player;

/**
 * Creates an LDLib2 held item UI through GTM's stable held item holder contract.
 *
 * <p>Implementing this provider automatically opts the item into LDLib2's {@link HeldItemUIMenuType} held item menu.
 * The bridge still adapts LDLib2's native holder into GTM's {@link HeldItemUIHolder}, so item business code continues
 * to receive the GTM holder contract.
 */
public interface LDLib2HeldItemUIProvider extends HeldItemUIMenuType.HeldItemUI {

    @Override
    default ModularUI createUI(HeldItemUIMenuType.HeldItemUIHolder holder) {
        HeldItemUIHolderContext gtmHolder = new HeldItemUIHolderContext(holder.player, holder.hand, holder.itemStack);
        UI ui = createLDLib2UI(holder.player, gtmHolder);
        return ModularUI.of(ui, holder.player);
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
