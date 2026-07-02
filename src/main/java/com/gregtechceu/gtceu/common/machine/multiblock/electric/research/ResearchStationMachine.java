package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NetworkedComputationContainer;
import com.gregtechceu.gtceu.common.computation.ComputationNetworkManager;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ObjectHolderMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ResearchStationMachine extends WorkableElectricMultiblockMachine
                                    implements IDisplayUIMachine {

    @Getter
    private final NetworkedComputationContainer importComputation;
    @Getter
    private @Nullable ObjectHolderMachine objectHolder;

    public ResearchStationMachine(BlockEntityCreationInfo info) {
        super(info);
        this.importComputation = attachTrait(new NetworkedComputationContainer(IO.IN));
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        for (IMultiPart part : getParts()) {
            if (part instanceof ObjectHolderMachine holder) {
                if (holder.getFrontFacing() != getFrontFacing().getOpposite()) {
                    invalidateStructure(structureName);
                    return;
                }
                this.objectHolder = holder;
            }
        }

        if (objectHolder == null) {
            invalidateStructure(structureName);
        }
    }

    @Override
    public boolean checkPattern(String structureName) {
        boolean isFormed = super.checkPattern(structureName);
        if (isFormed && objectHolder != null && objectHolder.getFrontFacing() != getFrontFacing().getOpposite()) {
            invalidateStructure(structureName);
        }
        return isFormed;
    }

    @Override
    public void invalidateStructure(String structureName) {
        if (DEFAULT_STRUCTURE.equals(structureName)) {
            for (IMultiPart part : getParts()) {
                if (part instanceof ObjectHolderMachine holder) {
                    if (holder == objectHolder) {
                        objectHolder.setLocked(false);
                    }
                }
            }
            objectHolder = null;
        }
        super.invalidateStructure(structureName);
    }

    @Override
    public boolean regressWhenWaiting() {
        return false;
    }

    private int getMaxComputation() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        for (IMultiPart part : getParts()) {
            if (part instanceof OpticalComputationHatchMachine opticalMachine) {
                return ComputationNetworkManager.get(serverLevel)
                        .getNetWorkAvailableCWUt(opticalMachine.getComputationPort());
            }
        }
        return 0;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        var workLogic = getWorkLogic();
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(workLogic.isWorkingEnabled(), workLogic.isActive())
                .setWorkingStatusKeys("gtpm.multiblock.idling", "gtpm.multiblock.work_paused",
                        "gtpm.multiblock.research_station.researching")
                .addEnergyUsageLine(energyContainer)
                .addEnergyTierLine(tier)
                .addWorkingStatusLine()
                .addComputationUsageLine(getMaxComputation())
                .addProgressLineOnlyPercent(recipeLogic.getProgressPercent());
    }
}
