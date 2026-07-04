package com.gregtechceu.gtceu.api.multiblock;

import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Predicate;

public class MultiblockPreviewLevel extends TrackedDummyWorld {

    public MultiblockPreviewLevel() {
        super();
    }

    public MultiblockPreviewLevel(Level level) {
        super(level);
    }

    public Level getLevel() {
        Level proxy = proxyWorld.get();
        return proxy == null ? this : proxy;
    }

    public void setInnerBlockEntity(BlockEntity blockEntity) {
        blockEntity.setLevel(this);
        setBlockEntity(blockEntity);
    }

    public void setRenderFilter(Predicate<BlockPos> renderFilter) {
        setBlockFilter(renderFilter);
    }
}
