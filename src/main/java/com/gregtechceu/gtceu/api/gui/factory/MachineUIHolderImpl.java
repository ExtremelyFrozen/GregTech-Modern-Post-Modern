package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * Default machine UI holder used by the GTM compatibility factory.
 */
public final class MachineUIHolderImpl implements MachineUIHolder {

    private final Player player;
    private final BlockPos pos;
    private final ResourceLocation machineDefinitionId;

    public MachineUIHolderImpl(Player player, MetaMachine machine) {
        this(player, machine.getBlockPos(), machine.getDefinition().getId());
    }

    public MachineUIHolderImpl(Player player, BlockPos pos, ResourceLocation machineDefinitionId) {
        this.player = player;
        this.pos = pos;
        this.machineDefinitionId = machineDefinitionId;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public ResourceLocation getMachineDefinitionId() {
        return machineDefinitionId;
    }

    @Nullable
    @Override
    public MetaMachine getMachine() {
        Level level = player.level();
        if (!level.isLoaded(pos)) {
            return null;
        }
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (machine == null || !machine.getDefinition().getId().equals(machineDefinitionId)) {
            return null;
        }
        return machine;
    }
}
