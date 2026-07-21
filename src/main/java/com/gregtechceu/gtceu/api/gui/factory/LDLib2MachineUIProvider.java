package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Creates an LDLib2 machine UI through GTM's stable machine holder contract.
 *
 * <p>
 * This provider lets machine business code depend on {@link MachineUIHolder} instead of an LDLib2 menu holder.
 */
public interface LDLib2MachineUIProvider {

    /**
     * Opens this provider through its authoritative server-side menu path.
     *
     * <p>
     * Most block-backed providers use LDLib2's standard block menu. Providers whose UI requires additional immutable
     * opening data may override this method and open a GTM-owned menu type.
     */
    default boolean openLDLib2UI(MetaMachine machine, ServerPlayer player) {
        MachineUIHolder holder = new MachineUIHolderContext(player, machine);
        return canCreateLDLib2UI(player, holder) && BlockUIMenuType.openUI(player, machine.getBlockPos());
    }

    /**
     * Returns whether this machine should use LDLib2 for the provided holder context.
     */
    boolean canCreateLDLib2UI(Player player, MachineUIHolder holder);

    /**
     * Builds the LDLib2 machine UI tree for the provided player and opened machine identity.
     *
     * @param player player building the UI in the current runtime.
     * @param holder GTM holder that exposes the opened machine position, definition id, and current machine lookup.
     * @return non-null LDLib2 UI tree.
     */
    UI createLDLib2UI(Player player, MachineUIHolder holder);
}
