package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;

import net.minecraft.world.entity.player.Player;

/**
 * Exposes the server-captured identity and linked-buffer view owned by one Pattern Buffer Proxy menu.
 */
public interface MEPatternBufferProxyUIContext extends MachineUIHolder {

    /** Returns the immutable Proxy/link generation encoded in this menu opening. */
    MEPatternBufferProxyOpeningIdentity getOpeningIdentity();

    /** Returns the real server buffer or the menu-local client projection rendered by this opening. */
    MEPatternBufferPartMachine getPatternBufferView();

    /** Returns whether the exact opening remains usable by the provided menu player. */
    boolean isOpeningValid(Player player);
}
