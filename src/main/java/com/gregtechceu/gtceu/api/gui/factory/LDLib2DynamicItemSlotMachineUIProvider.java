package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotSessionElement;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.server.level.ServerPlayer;

/**
 * Opens an LDLib2 machine UI through GTM's session-bound dynamic item-slot menu.
 *
 * <p>
 * The returned UI tree must contain exactly one {@link GTDynamicItemSlotSessionElement}. That element owns every
 * runtime slot append and keeps removed ranges as opening-local tombstones.
 * </p>
 */
public interface LDLib2DynamicItemSlotMachineUIProvider extends LDLib2MachineUIProvider {

    /**
     * Uses the dedicated menu so runtime slot bindings can be acknowledged before they become interactive.
     */
    @Override
    default boolean openLDLib2UI(MetaMachine machine, ServerPlayer player) {
        return DynamicItemSlotMachineUIMenuType.openUI(machine, player);
    }
}
