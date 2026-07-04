package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Holds the stable block position used to open and validate a machine UI.
 *
 * <p>The holder keeps machine screens from depending on a {@link MetaMachine} instance as the legacy UI factory
 * payload while the screen family is moved to LDLib2 block menu types.
 */
public interface MachineUIHolder {

    /**
     * Returns the block position of the machine that owned the UI when it opened.
     */
    BlockPos getPos();

    /**
     * Resolves the current machine instance at the opened block position.
     */
    @Nullable
    MetaMachine getMachine();
}
