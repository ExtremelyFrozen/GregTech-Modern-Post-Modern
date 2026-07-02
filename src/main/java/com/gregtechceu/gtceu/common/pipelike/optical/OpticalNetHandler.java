package com.gregtechceu.gtceu.common.pipelike.optical;

import com.gregtechceu.gtceu.api.capability.IDataAccessMachine;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalDataAccessHatch;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class OpticalNetHandler implements IDataAccessMachine, IOpticalComputationProvider {

    private final OpticalPipeBlockEntity pipe;
    private final Level world;
    private final Direction facing;

    @Getter
    private OpticalPipeNet net;

    public OpticalNetHandler(OpticalPipeNet net, @NotNull OpticalPipeBlockEntity pipe, @Nullable Direction facing) {
        this.net = net;
        this.pipe = pipe;
        this.facing = facing;
        this.world = pipe.getLevel();
    }

    public void updateNetwork(OpticalPipeNet net) {
        this.net = net;
    }

    @Override
    public boolean isRecipeAvailable(@NotNull GTRecipeType recipeType, @NotNull ResourceLocation recipeId) {
        boolean isAvailable = traverseRecipeAvailable(recipeType, recipeId);
        if (isAvailable) setPipesActive();
        return isAvailable;
    }

    @Override
    public void notifyListeners() {
        IOpticalDataAccessHatch hatch = getDataHatch();
        if (hatch != null && !hatch.isTransmitter()) {
            hatch.notifyListeners();
        }
    }

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        if (cwut == 0) return 0;
        int provided = traverseRequestCWUt(cwut, simulate, seen);
        if (provided > 0) setPipesActive();
        return provided;
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        return traverseMaxCWUt(seen);
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        return traverseCanBridge(seen);
    }

    private void setPipesActive() {
        for (BlockPos pos : net.getAllNodes().keySet()) {
            if (world.getBlockEntity(pos) instanceof OpticalPipeBlockEntity opticalPipe) {
                opticalPipe.setActive(true, 100);
            }
        }
    }

    private boolean isNetInvalidForTraversal() {
        return net == null || pipe == null || pipe.isRemoved();
    }

    private boolean traverseRecipeAvailable(@NotNull GTRecipeType recipeType, @NotNull ResourceLocation recipeId) {
        IOpticalDataAccessHatch hatch = getDataHatch();
        return hatch != null && hatch.isTransmitter() && hatch.isRecipeAvailable(recipeType, recipeId);
    }

    private int traverseRequestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        IOpticalComputationProvider provider = getComputationProvider(seen);
        if (provider == null) return 0;
        return provider.requestCWUt(cwut, simulate, seen);
    }

    private int traverseMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        IOpticalComputationProvider provider = getComputationProvider(seen);
        if (provider == null) return 0;
        return provider.getMaxCWUt(seen);
    }

    private boolean traverseCanBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        IOpticalComputationProvider provider = getComputationProvider(seen);
        if (provider == null) return true; // nothing found, so don't report a problem, just pass quietly
        return provider.canBridge(seen);
    }

    @Nullable
    private IOpticalComputationProvider getComputationProvider(@NotNull Collection<IOpticalComputationProvider> seen) {
        if (isNetInvalidForTraversal()) return null;

        OpticalRoutePath inv = net.getNetData(pipe.getBlockPos(), facing);
        if (inv == null) return null;

        IOpticalComputationProvider hatch = inv.getComputationHatch();
        if (hatch == null || seen.contains(hatch)) return null;
        return hatch;
    }

    @Nullable
    private IOpticalDataAccessHatch getDataHatch() {
        if (isNetInvalidForTraversal()) return null;

        OpticalRoutePath routePath = net.getNetData(pipe.getBlockPos(), facing);
        if (routePath == null) return null;

        return routePath.getDataHatch();
    }
}
