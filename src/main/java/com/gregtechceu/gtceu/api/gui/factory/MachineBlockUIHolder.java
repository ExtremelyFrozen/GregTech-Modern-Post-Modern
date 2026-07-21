package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 block menu holder for a GTM machine block UI.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MachineBlockUIHolder extends BlockUIMenuType.BlockUIHolder implements MachineUIHolder {

    private final ResourceLocation machineDefinitionId;

    public MachineBlockUIHolder(BlockUIMenuType.BlockUI blockUI, Player player, BlockPos pos, BlockState blockState,
                                ResourceLocation machineDefinitionId) {
        super(blockUI, player, pos, blockState);
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
