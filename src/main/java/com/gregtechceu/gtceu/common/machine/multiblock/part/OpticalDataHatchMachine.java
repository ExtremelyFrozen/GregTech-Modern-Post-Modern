package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.capability.IDataAccessMachine;
import com.gregtechceu.gtceu.api.capability.IOpticalDataAccessHatch;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public class OpticalDataHatchMachine extends MultiblockPartMachine
                                     implements IOpticalDataAccessHatch, LDLib2FancyPartUIProvider {

    @Getter
    private final boolean isTransmitter;

    public OpticalDataHatchMachine(BlockEntityCreationInfo info, boolean isTransmitter) {
        super(info);
        this.isTransmitter = isTransmitter;
    }

    @Override
    public boolean isRecipeAvailable(@NotNull GTRecipeType recipeType, @NotNull ResourceLocation recipeId) {
        if (!isFormed()) {
            return false;
        }

        if (isTransmitter()) {
            MultiblockControllerMachine controller = getControllers().first();
            return controller instanceof IDataAccessMachine dataAccessMachine &&
                    dataAccessMachine.isRecipeAvailable(recipeType, recipeId);
        } else {
            IDataAccessMachine cap = getLevel().getCapability(GTCapability.CAPABILITY_DATA_ACCESS,
                    getBlockPos().relative(getFrontFacing()), getFrontFacing().getOpposite());
            return cap != null && cap.isRecipeAvailable(recipeType, recipeId);
        }
    }

    @Override
    public void notifyListeners() {
        if (isTransmitter()) {
            IDataAccessMachine cap = getLevel().getCapability(GTCapability.CAPABILITY_DATA_ACCESS,
                    getBlockPos().relative(getFrontFacing()), getFrontFacing().getOpposite());
            if (cap != null) {
                cap.notifyListeners();
            }
            return;
        }
        for (MultiblockControllerMachine controller : getControllers()) {
            if (controller instanceof IDataAccessMachine dataAccessMachine) {
                dataAccessMachine.notifyListeners();
            } else if (controller instanceof IRecipeLogicMachine recipeLogicMachine) {
                recipeLogicMachine.getRecipeLogic().onRecipeHandlerChanged();
            }
        }
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    /** Creates the holder-scoped default preview used by a surrounding multiblock controller. */
    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        return new LDLib2FancyPreviewPage(this, player, holder, null);
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String structureName) {
        return false;
    }
}
