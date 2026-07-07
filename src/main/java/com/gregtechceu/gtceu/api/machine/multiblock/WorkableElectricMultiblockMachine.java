package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.*;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.BatchModeMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class WorkableElectricMultiblockMachine extends WorkableMultiblockMachine implements IFancyUIMachine,
                                               BatchModeMachine, IDisplayUIMachine, ITieredMachine,
                                               IOverclockMachine {

    // runtime
    protected @Nullable EnergyContainerList energyContainer;
    @Getter
    protected int tier;
    @SaveField
    @SyncToClient
    @Getter
    protected boolean batchEnabled;

    public WorkableElectricMultiblockMachine(BlockEntityCreationInfo info, RecipeLogic recipeLogic) {
        super(info, recipeLogic);
    }

    public WorkableElectricMultiblockMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    @Override
    public WorkableElectricMultiblockMachine self() {
        return this;
    }

    //////////////////////////////////////
    // *** Multiblock Lifecycle ***//
    //////////////////////////////////////
    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName)) {
            this.energyContainer = null;
            this.tier = 0;
        }
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName)) {
            this.energyContainer = getEnergyContainer();
            this.tier = energyContainer.getTier();
        }
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        this.energyContainer = null;
        this.tier = 0;
    }

    @Override
    public boolean supportsBatchMode() {
        return getDefinition().getRecipeModifier() instanceof RecipeModifierList list && Arrays.stream(list.getModifiers())
                .anyMatch(modifier -> modifier == GTRecipeModifiers.BATCH_MODE);
    }

    @Override
    public void setBatchEnabled(boolean batchEnabled) {
        this.batchEnabled = batchEnabled;
        syncDataHolder.markClientSyncFieldDirty("batchEnabled");
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        var workLogic = getWorkLogic();
        int numParallels;
        int subtickParallels;
        int batchParallels;
        int totalRuns;
        boolean exact = false;
        if (workLogic.isActive() && recipeLogic.getLastRecipe() != null) {
            numParallels = recipeLogic.getLastRecipe().parallels;
            subtickParallels = recipeLogic.getLastRecipe().subtickParallels;
            batchParallels = recipeLogic.getLastRecipe().batchParallels;
            totalRuns = recipeLogic.getLastRecipe().getTotalRuns();
            exact = true;
        } else {
            numParallels = getParallelHatch()
                    .map(ParallelHatchPartMachine::getCurrentParallel)
                    .orElse(0);
            subtickParallels = 0;
            batchParallels = 0;
            totalRuns = 0;
        }

        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(workLogic.isWorkingEnabled(), workLogic.isActive())
                .addEnergyUsageLine(energyContainer)
                .addEnergyTierLine(tier)
                .addMachineModeLine(getRecipeType(), getRecipeTypes().length > 1)
                .addTotalRunsLine(totalRuns)
                .addParallelsLine(numParallels, exact)
                .addSubtickParallelsLine(subtickParallels)
                .addBatchModeLine(isBatchEnabled(), batchParallels)
                .addWorkingStatusLine()
                .addProgressLine(recipeLogic)
                .addRecipeFailReasonLine(recipeLogic)
                .addOutputLines(recipeLogic.getLastRecipe());
        getDefinition().getAdditionalDisplay().accept(this, textList);
        IDisplayUIMachine.super.addDisplayText(textList);
    }

    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 182 + 8, 117 + 8);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117).setBackground(getScreenTexture())
                .addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText)
                        .textSupplier(this.getLevel().isClientSide ? null : this::addDisplayText)
                        .setMaxWidthLimit(200)
                        .clickHandler(this::handleDisplayClick)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(198, 208, this, entityPlayer).widget(new FancyMachineUIWidget(this, 198, 208));
    }

    @Override
    public List<IFancyUIProvider> getSubTabs() {
        return getParts().stream().filter(Objects::nonNull).map(IFancyUIProvider.class::cast).toList();
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        IVoidable.attachConfigurators(configuratorPanel, this);
        if (supportsBatchMode()) {
            configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                    GuiTextures.BUTTON_BATCH.getSubTexture(0, 0, 1, 0.5),
                    GuiTextures.BUTTON_BATCH.getSubTexture(0, 0.5, 1, 0.5),
                    this::isBatchEnabled,
                    (cd, p) -> setBatchEnabled(p))
                    .setTooltipsSupplier(
                            p -> List.of(
                                    Component.translatable("gtpm.machine.batch_" + (p ? "enabled" : "disabled")))));
        }

        IFancyUIMachine.super.attachConfigurators(configuratorPanel);
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        for (IMultiPart part : getParts()) {
            part.attachFancyTooltipsToController(this, tooltipsPanel);
        }
    }

    //////////////////////////////////////
    // ******** OVERCLOCK *********//
    //////////////////////////////////////
    public int getOverclockTier() {
        return getTier();
    }

    public int getMaxOverclockTier() {
        return getTier();
    }

    public int getMinOverclockTier() {
        return getTier();
    }

    @Override
    public void setOverclockTier(int tier) {}

    @Override
    public long getOverclockVoltage() {
        if (this.energyContainer == null) {
            this.energyContainer = getEnergyContainer();
        }
        return energyContainer.getEffectiveVoltage();
    }

    //////////////////////////////////////
    // ****** RECIPE LOGIC *******//
    //////////////////////////////////////

    public EnergyContainerList getEnergyContainer() {
        List<IEnergyContainer> containers = new ArrayList<>();
        var handlers = getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP);
        if (handlers.isEmpty()) handlers = getCapabilitiesFlat(IO.OUT, EURecipeCapability.CAP);
        for (IRecipeHandler<?> handler : handlers) {
            if (handler instanceof IEnergyContainer container) {
                containers.add(container);
            }
        }
        return new EnergyContainerList(containers);
    }

    public long getMaxVoltage() {
        if (this.energyContainer == null) {
            this.energyContainer = getEnergyContainer();
        }
        if (this.isGenerator()) {
            return energyContainer.getEffectiveVoltage();
        }
        return GTValues.V[energyContainer.getTier()];
    }

    @Override
    public long getTierVoltage() {
        return getMaxVoltage();
    }

    @Override
    public long getDisplayRecipeVoltage() {
        return this.getEnergyContainer().getHighestVoltage();
    }

    /**
     * Is this multiblock a generator?
     * Used for max voltage calculations.
     */
    public boolean isGenerator() {
        return getDefinition().isGenerator();
    }
}
