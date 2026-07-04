package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * Default machine UI holder used by the GTM compatibility factory.
 */
public final class MachineUIHolderImpl implements MachineUIHolder {

    private final Player player;
    private final BlockPos pos;

    public MachineUIHolderImpl(Player player, MetaMachine machine) {
        this(player, machine.getBlockPos());
    }

    public MachineUIHolderImpl(Player player, BlockPos pos) {
        this.player = player;
        this.pos = pos;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    @Override
    public MetaMachine getMachine() {
        Level level = player.level();
        if (!level.isLoaded(pos)) {
            return null;
        }
        return MetaMachine.getMachine(level, pos);
    }
}
