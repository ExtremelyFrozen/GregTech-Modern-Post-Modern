package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IDataAccessMachine;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DataBankMachine extends WorkableElectricMultiblockMachine
                             implements IFancyUIMachine, IDisplayUIMachine, IControllable, IDataAccessMachine {

    public static final int EUT_PER_HATCH = GTValues.VA[GTValues.EV];
    public static final int EUT_PER_HATCH_CHAINED = GTValues.VA[GTValues.LuV];

    private IMaintenanceMachine maintenance;
    private EnergyContainerList energyContainer;
    private final List<IDataAccessMachine> dataAccesses = new ArrayList<>();
    private final List<IDataAccessMachine> receivers = new ArrayList<>();
    private final List<IDataAccessMachine> transmitters = new ArrayList<>();
    private boolean isQuerying;

    @Getter
    private int energyUsage = 0;

    @Nullable
    protected TickableSubscription tickSubs;

    public DataBankMachine(BlockEntityCreationInfo info) {
        super(info);
        this.energyContainer = EnergyContainerList.EMPTY;
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        dataAccesses.clear();
        receivers.clear();
        transmitters.clear();
        List<IEnergyContainer> energyContainers = new ArrayList<>();
        Long2ObjectMap<IO> ioMap = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().getOrDefault("ioMap",
                Long2ObjectMaps.emptyMap());
        for (IMultiPart part : getParts()) {
            IO io = ioMap.getOrDefault(part.self().getBlockPos().asLong(), IO.BOTH);
            var block = part.self().getBlockState().getBlock();
            if (part instanceof IDataAccessMachine dataAccessMachine) {
                if (PartAbility.DATA_ACCESS.isApplicable(block)) {
                    dataAccesses.add(dataAccessMachine);
                } else if (PartAbility.OPTICAL_DATA_RECEPTION.isApplicable(block)) {
                    receivers.add(dataAccessMachine);
                } else if (PartAbility.OPTICAL_DATA_TRANSMISSION.isApplicable(block)) {
                    transmitters.add(dataAccessMachine);
                }
            }
            if (part instanceof IMaintenanceMachine maintenanceMachine) {
                this.maintenance = maintenanceMachine;
            }
            if (io == IO.NONE || io == IO.OUT) continue;
            var handlerLists = part.getRecipeHandlers();
            for (var handlerList : handlerLists) {
                if (!handlerList.isValid(io)) continue;
                handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .forEach(energyContainers::add);
            }
        }
        this.energyContainer = new EnergyContainerList(energyContainers);
        this.energyUsage = calculateEnergyUsage();

        if (this.maintenance == null) {
            invalidateStructure(structureName);
            return;
        }
        updateTickSubscription();
        notifyListeners();
    }

    protected int calculateEnergyUsage() {
        int receivers = 0;
        int transmitters = 0;
        int regulars = 0;
        for (var part : this.getParts()) {
            var block = part.self().getBlockState().getBlock();
            if (PartAbility.OPTICAL_DATA_RECEPTION.isApplicable(block)) {
                ++receivers;
            }
            if (PartAbility.OPTICAL_DATA_TRANSMISSION.isApplicable(block)) {
                ++transmitters;
            }
            if (PartAbility.DATA_ACCESS.isApplicable(block)) {
                ++regulars;
            }
        }

        int dataHatches = receivers + transmitters + regulars;
        int eutPerHatch = receivers > 0 ? EUT_PER_HATCH_CHAINED : EUT_PER_HATCH;
        return eutPerHatch * dataHatches;
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName)) {
            notifyListeners();
            this.energyContainer = EnergyContainerList.EMPTY;
            this.energyUsage = 0;
            this.maintenance = null;
            this.dataAccesses.clear();
            this.receivers.clear();
            this.transmitters.clear();
        }
    }

    @Override
    public boolean isRecipeAvailable(@NotNull GTRecipeType recipeType, @NotNull ResourceLocation recipeId) {
        if (isQuerying) return false;
        isQuerying = true;
        try {
            return queryRecipe(recipeType, recipeId);
        } finally {
            isQuerying = false;
        }
    }

    private boolean queryRecipe(@NotNull GTRecipeType recipeType, @NotNull ResourceLocation recipeId) {
        if (!getWorkLogic().isWorking()) {
            return false;
        }
        for (IDataAccessMachine dataAccess : dataAccesses) {
            if (dataAccess.isRecipeAvailable(recipeType, recipeId)) {
                return true;
            }
        }
        for (IDataAccessMachine receiver : receivers) {
            if (receiver.isRecipeAvailable(recipeType, recipeId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void notifyListeners() {
        if (isQuerying) return;
        isQuerying = true;
        try {
            for (IDataAccessMachine transmitter : transmitters) {
                transmitter.notifyListeners();
            }
        } finally {
            isQuerying = false;
        }
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
        if (isFormed) {
            tickSubs = subscribeServerTick(tickSubs, this::tick);
        } else if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    public void tick() {
        boolean wasProviding = getWorkLogic().isWorking();
        int energyToConsume = this.getEnergyUsage();
        boolean hasMaintenance = ConfigHolder.INSTANCE.machines.enableMaintenance && this.maintenance != null;
        if (hasMaintenance) {
            // 10% more energy per maintenance problem
            energyToConsume += maintenance.getNumMaintenanceProblems() * energyToConsume / 10;
        }

        if (getWorkLogic().isWaiting() && energyContainer.getInputPerSec() > 19L * energyToConsume) {
            getWorkLogic().setStatus(WorkLogic.Status.IDLE);
        }

        if (this.energyContainer.getEnergyStored() >= energyToConsume) {
            if (!getWorkLogic().isWaiting()) {
                long consumed = this.energyContainer.removeEnergy(energyToConsume);
                if (consumed == energyToConsume) {
                    getWorkLogic().setStatus(WorkLogic.Status.WORKING);
                } else {
                    getWorkLogic().setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_in")
                            .append(": ").append(EURecipeCapability.CAP.getName()));
                }
            }
        } else {
            getWorkLogic().setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_in").append(": ")
                    .append(EURecipeCapability.CAP.getName()));
        }
        if (wasProviding != getWorkLogic().isWorking()) {
            notifyListeners();
        }
        updateTickSubscription();
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(true, isActive() && isWorkingEnabled()) // transform into two-state system for display
                .setWorkingStatusKeys(
                        "gtpm.multiblock.idling",
                        "gtpm.multiblock.idling",
                        "gtpm.multiblock.data_bank.providing")
                .addEnergyUsageExactLine(getEnergyUsage())
                .addWorkingStatusLine();
    }

    /*
     * @Override
     * protected void addWarningText(List<Component> textList) {
     * MultiblockDisplayText.builder(textList, isFormed(), false)
     * .addLowPowerLine(hasNotEnoughEnergy)
     * .addMaintenanceProblemLines(maintenance.getMaintenanceProblems());
     * }
     */

    @Override
    public int getProgress() {
        return 0;
    }

    @Override
    public int getMaxProgress() {
        return 0;
    }
}
