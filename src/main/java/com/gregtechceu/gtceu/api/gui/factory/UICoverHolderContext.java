package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.IUICover;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * Default cover UI holder used by the GTM compatibility factory.
 */
public final class UICoverHolderContext implements UICoverHolder {

    private final Player player;
    private final BlockPos pos;
    private final Direction side;
    private final ResourceLocation coverDefinitionId;

    public UICoverHolderContext(Player player, CoverBehavior cover) {
        this(player, cover.coverHolder.getBlockPos(), cover.attachedSide, cover.coverDefinition.getId());
    }

    public UICoverHolderContext(Player player, BlockPos pos, Direction side, ResourceLocation coverDefinitionId) {
        this.player = player;
        this.pos = pos;
        this.side = side;
        this.coverDefinitionId = coverDefinitionId;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public Direction getSide() {
        return side;
    }

    @Override
    public ResourceLocation getCoverDefinitionId() {
        return coverDefinitionId;
    }

    @Nullable
    @Override
    public CoverBehavior getCover() {
        Level level = player.level();
        if (!level.isLoaded(pos)) {
            return null;
        }

        var coverable = GTCapabilityHelper.getCoverable(level, pos, side);
        if (coverable == null) {
            return null;
        }

        CoverBehavior cover = coverable.getCoverAtSide(side);
        if (cover == null || !cover.coverDefinition.getId().equals(coverDefinitionId)) {
            return null;
        }
        return cover;
    }

    @Nullable
    public ModularUI createUI(Player entityPlayer) {
        if (!(getCover() instanceof IUICover cover)) {
            return null;
        }
        return cover.createUI(entityPlayer);
    }
}
