package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2RecipeFancyUIMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.LDLib2RecipeUISize;
import com.gregtechceu.gtceu.common.data.GTMedicalConditions;
import com.gregtechceu.gtceu.common.machine.trait.hazard.EnvironmentalHazardEmitterTrait;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.Util;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;

import com.google.common.collect.Tables;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;

public class SimpleGeneratorMachine extends WorkableTieredMachine
                                    implements LDLib2RecipeFancyUIMachine {

    private static final int ENERGY_BAR_WIDTH = 18;
    private static final int ENERGY_BAR_HEIGHT = 60;
    private static final int RECIPE_ENERGY_GAP = 4;
    private static final int PAGE_HORIZONTAL_PADDING = 8;
    private static final int PAGE_VERTICAL_PADDING = 8;
    private static final int MIN_PAGE_WIDTH = 172;
    private static final int ENERGY_BAR_X = 3;

    @Getter
    private final EnvironmentalHazardEmitterTrait hazardEmitter;

    public SimpleGeneratorMachine(BlockEntityCreationInfo info, int tier,
                                  float hazardStrengthPerOperation, Int2IntFunction tankScalingFunction) {
        super(info, tier, tankScalingFunction);

        energyContainer.setSideOutputCondition(side -> !hasFrontFacing() || side == getFrontFacing());
        this.hazardEmitter = attachTrait(
                new EnvironmentalHazardEmitterTrait(GTMedicalConditions.CARBON_MONOXIDE_POISONING,
                        hazardStrengthPerOperation));
    }

    public SimpleGeneratorMachine(BlockEntityCreationInfo info, int tier, Int2IntFunction tankScalingFunction) {
        this(info, tier, 0.25f, tankScalingFunction);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    protected boolean isEnergyEmitter() {
        return true;
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    //////////////////////////////////////
    // ****** RECIPE LOGIC *******//
    //////////////////////////////////////

    /**
     * Recipe Modifier for <b>Simple Generator Machines</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Recipe is fast parallelized up to {@code desiredEUt / recipeEUt} times.
     * </p>
     *
     * @param machine a {@link SimpleGeneratorMachine}
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Simple Generator
     */
    public static ModifierFunction recipeModifier(@NotNull MetaMachine machine, @NotNull GTRecipe recipe) {
        if (!(machine instanceof SimpleGeneratorMachine generator)) {
            return RecipeModifier.nullWrongType(SimpleGeneratorMachine.class, machine);
        }
        long EUt = recipe.getOutputEUt();
        if (EUt <= 0) return ModifierFunction.NULL;

        int maxParallel = (int) (generator.getOverclockVoltage() / EUt);
        int parallels = ParallelLogic.getParallelAmountFast(generator, recipe, maxParallel);

        return ModifierFunction.builder()
                .inputModifier(ContentModifier.multiplier(parallels))
                .outputModifier(ContentModifier.multiplier(parallels))
                .eutMultiplier(parallels)
                .parallels(parallels)
                .build();
    }

    @Override
    public boolean regressWhenWaiting() {
        return false;
    }

    @Override
    public boolean canVoidRecipeOutputs(RecipeCapability<?> capability) {
        return capability != EURecipeCapability.CAP;
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        hazardEmitter.emitHazard();
    }

    @Override
    public long getDisplayRecipeVoltage() {
        return GTValues.V[this.tier];
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public SimpleGeneratorMachine getLDLib2RecipeMachine() {
        return this;
    }

    @Override
    public int getLDLib2PageWidth() {
        LDLib2RecipeUISize recipeSize = getLDLib2RecipeUISize(this);
        return Math.max(ENERGY_BAR_WIDTH + RECIPE_ENERGY_GAP + recipeSize.width() + PAGE_HORIZONTAL_PADDING,
                MIN_PAGE_WIDTH);
    }

    @Override
    public int getLDLib2PageHeight() {
        LDLib2RecipeUISize recipeSize = getLDLib2RecipeUISize(this);
        return Math.max(recipeSize.height() + PAGE_VERTICAL_PADDING,
                ENERGY_BAR_HEIGHT + PAGE_VERTICAL_PADDING);
    }

    @Override
    public int getLDLib2RecipeTemplateX(WorkableTieredMachine machine, LDLib2RecipeUISize recipeSize) {
        return (getLDLib2PageWidth() - ENERGY_BAR_WIDTH - RECIPE_ENERGY_GAP - recipeSize.width()) / 2 +
                ENERGY_BAR_WIDTH + RECIPE_ENERGY_GAP;
    }

    @Override
    public void attachLDLib2RecipePageElements(UIElement root, WorkableTieredMachine machine,
                                               LDLib2RecipeUISize recipeSize) {
        GTProgressBarElement energyBar = createLDLib2EnergyBar();
        UITemplate.setLDLib2Bounds(energyBar, ENERGY_BAR_X,
                (getLDLib2PageHeight() - ENERGY_BAR_HEIGHT) / 2,
                ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT);
        root.addChild(energyBar);
    }

    @Override
    public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
        configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                this, configuratorPanel.getHolder()));
    }

    @SuppressWarnings("UnstableApiUsage")
    public static BiFunction<ResourceLocation, GTRecipeType, EditableMachineUI> EDITABLE_UI_CREATOR = Util
            .memoize((path, recipeType) -> new EditableMachineUI("generator", path, () -> {
                WidgetGroup template = recipeType.getRecipeUI().createEditableUITemplate(false, false).createDefault();
                WidgetGroup group = new WidgetGroup(0, 0, template.getSize().width + 4 + 8,
                        template.getSize().height + 8);
                Size size = group.getSize();
                template.setSelfPosition(new Position(
                        (size.width - 4 - template.getSize().width) / 2 + 4,
                        (size.height - template.getSize().height) / 2));
                group.addWidget(template);
                return group;
            }, (template, machine) -> {
                if (machine instanceof SimpleGeneratorMachine generatorMachine) {
                    var storages = Tables.newCustomTable(new EnumMap<>(IO.class),
                            LinkedHashMap<RecipeCapability<?>, Object>::new);
                    storages.put(IO.IN, ItemRecipeCapability.CAP, generatorMachine.importItems.storage);
                    storages.put(IO.OUT, ItemRecipeCapability.CAP, generatorMachine.exportItems.storage);
                    storages.put(IO.IN, FluidRecipeCapability.CAP, generatorMachine.importFluids);
                    storages.put(IO.OUT, FluidRecipeCapability.CAP, generatorMachine.exportFluids);

                    generatorMachine.getRecipeType().getRecipeUI().createEditableUITemplate(false, false).setupUI(
                            template,
                            new GTRecipeTypeUI.RecipeHolder(generatorMachine.recipeLogic::getProgressPercent,
                                    storages,
                                    DataComponentMap.EMPTY,
                                    Collections.emptyList(),
                                    false, false));
                    createEnergyBar().setupUI(template, generatorMachine);
                }
            }));
}
