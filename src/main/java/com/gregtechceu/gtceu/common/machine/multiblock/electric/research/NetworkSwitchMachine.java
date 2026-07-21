package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.common.computation.ComputationNetworkManager;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NetworkSwitchMachine extends WorkableElectricMultiblockMachine implements IControllable {

    public static final int EUT_PER_HATCH = GTValues.VA[GTValues.IV];

    private int energyUsage = 0;
    private boolean computationBridgeActive;

    @Nullable
    protected TickableSubscription tickSubs;

    public NetworkSwitchMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    protected int calculateEnergyUsage() {
        int receivers = 0;
        int transmitters = 0;
        for (var part : this.getParts()) {
            var block = part.self().getBlockState().getBlock();
            if (PartAbility.COMPUTATION_DATA_RECEPTION.isApplicable(block)) {
                ++receivers;
            }
            if (PartAbility.COMPUTATION_DATA_TRANSMISSION.isApplicable(block)) {
                ++transmitters;
            }
        }
        return EUT_PER_HATCH * (receivers + transmitters);
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        energyUsage = calculateEnergyUsage();
        updateTickSubscription();
        markComputationTopologyDirty();
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        energyUsage = 0;
        updateComputationBridgeActive(false);
        updateTickSubscription();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleForNextServerTick(this::updateTickSubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    protected void updateTickSubscription() {
        if (isFormed() && isWorkingEnabled()) {
            tickSubs = subscribeServerTick(tickSubs, this::tick);
        } else if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    public void tick() {
        if (energyContainer == null) {
            updateComputationBridgeActive(false);
            getWorkLogic().setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_in").append(": ")
                    .append(EURecipeCapability.CAP.getName()));
            updateTickSubscription();
            return;
        }

        int energyToConsume = getEnergyUsage();
        if (energyContainer.getEnergyStored() >= energyToConsume &&
                energyContainer.removeEnergy(energyToConsume) >= energyToConsume) {
            getWorkLogic().setStatus(WorkLogic.Status.WORKING);
            updateComputationBridgeActive(true);
        } else {
            getWorkLogic().setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_in").append(": ")
                    .append(EURecipeCapability.CAP.getName()));
            updateComputationBridgeActive(false);
        }
        updateTickSubscription();
    }

    private void updateComputationBridgeActive(boolean active) {
        if (computationBridgeActive == active) return;
        computationBridgeActive = active;
        markComputationTopologyDirty();
    }

    private void markComputationTopologyDirty() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            ComputationNetworkManager.get(serverLevel).markTopologyDirty();
        }
    }

    private int getMaxCWUt() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        for (IMultiPart part : getParts()) {
            if (part instanceof OpticalComputationHatchMachine opticalHatch) {
                return ComputationNetworkManager.get(serverLevel)
                        .getNetWorkMaxCWUt(opticalHatch.getComputationPort());
            }
        }
        return 0;
    }

    private int getUsedCWUt() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        for (IMultiPart part : getParts()) {
            if (part instanceof OpticalComputationHatchMachine opticalHatch) {
                return ComputationNetworkManager.get(serverLevel)
                        .getNetWorkUsedCWUt(opticalHatch.getComputationPort());
            }
        }
        return 0;
    }

    public int getEnergyUsage() {
        return isFormed() ? energyUsage : 0;
    }

    @Override
    public boolean isWorkingEnabled() {
        return !getWorkLogic().isSuspend();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (isWorkingAllowed) {
            getWorkLogic().setStatus(WorkLogic.Status.IDLE);
        } else {
            getWorkLogic().setStatus(WorkLogic.Status.SUSPEND);
            updateComputationBridgeActive(false);
        }
        updateTickSubscription();
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(true, isActive() && isWorkingEnabled())
                .setWorkingStatusKeys(
                        "gtpm.multiblock.idling",
                        "gtpm.multiblock.idling",
                        "gtpm.multiblock.data_bank.providing")
                .addEnergyUsageExactLine(getEnergyUsage())
                .addComputationUsageLine(getMaxCWUt())
                .addComputationUsageExactLine(getUsedCWUt())
                .addWorkingStatusLine();
    }
}
