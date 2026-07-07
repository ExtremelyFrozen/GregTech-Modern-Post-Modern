package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.common.machine.trait.ExhaustVentMachineTrait;
import com.gregtechceu.gtceu.common.recipe.condition.VentCondition;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.entity.player.Player;

import com.google.common.collect.Tables;
import lombok.Getter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.function.BooleanSupplier;

public class SimpleSteamMachine extends SteamWorkableMachine implements LDLib2MachineUIProvider {

    @SaveField
    public final NotifiableItemStackHandler importItems;
    @SaveField
    public final NotifiableItemStackHandler exportItems;

    @Getter
    private final ExhaustVentMachineTrait exhaustVentTrait;

    public SimpleSteamMachine(BlockEntityCreationInfo info, boolean isHighPressure) {
        super(info, isHighPressure);
        this.importItems = attachTrait(createImportItemHandler());
        this.exportItems = attachTrait(createExportItemHandler());

        this.exhaustVentTrait = attachTrait(new ExhaustVentMachineTrait());
        exhaustVentTrait.setVentingDamageAmount(isHighPressure() ? 12F : 6F);
        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.VENT_DIRECTION)) {
            // outputFacing will always be opposite the front facing on init
            setRenderState(renderState.setValue(GTMachineModelProperties.VENT_DIRECTION, RelativeDirection.BACK));
        }
    }

    //////////////////////////////////////
    // ***** Initialization *****//
    //////////////////////////////////////

    protected NotifiableItemStackHandler createImportItemHandler() {
        return new NotifiableItemStackHandler(getRecipeType().getMaxInputs(ItemRecipeCapability.CAP), IO.IN);
    }

    protected NotifiableItemStackHandler createExportItemHandler() {
        return new NotifiableItemStackHandler(getRecipeType().getMaxOutputs(ItemRecipeCapability.CAP), IO.OUT);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        exhaustVentTrait.setVentingDirection(getOutputFacing());
        // Simulate an EU machine via a SteamEnergyHandler
        this.addHandlerList(RecipeHandlerList.of(IO.IN, new SteamEnergyRecipeHandler(steamTank, getConversionRate())));
    }

    //////////////////////////////////////
    // ****** Venting Logic ******//
    //////////////////////////////////////

    public void updateModelVentDirection() {
        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.VENT_DIRECTION)) {
            Direction upwardsDir = getUpwardsFacing();
            // the up facing is already rotated if extended facing is enabled for the machine
            if (getFrontFacing() == Direction.UP && !allowExtendedFacing()) {
                upwardsDir = upwardsDir.getOpposite();
            }
            var relative = RelativeDirection.findRelativeOf(getFrontFacing(), exhaustVentTrait.getVentingDirection(),
                    upwardsDir);
            setRenderState(renderState.setValue(GTMachineModelProperties.VENT_DIRECTION, relative));
        }
    }

    @Override
    public void setOutputFacing(Direction outputFacing) {
        var oldFacing = getOutputFacing();
        super.setOutputFacing(outputFacing);
        if (getOutputFacing() != oldFacing) {
            updateModelVentDirection();
            exhaustVentTrait.setVentingDirection(outputFacing);
        }
    }

    @Override
    public void setFrontFacing(Direction facing) {
        var oldFacing = getFrontFacing();
        super.setFrontFacing(facing);
        if (getFrontFacing() != oldFacing) {
            updateModelVentDirection();
        }
    }

    @Override
    public void setUpwardsFacing(Direction upwardsFacing) {
        var oldFacing = getUpwardsFacing();
        super.setUpwardsFacing(upwardsFacing);
        if (getUpwardsFacing() != oldFacing) {
            updateModelVentDirection();
        }
    }

    public double getConversionRate() {
        return isHighPressure() ? 2.0 : 1.0;
    }

    //////////////////////////////////////
    // ****** Recipe Logic ******//
    //////////////////////////////////////

    /**
     * Recipe Modifier for <b>Simple Steam Machines</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Recipe is rejected if tier is greater than LV or if machine cannot vent.<br>
     * Duration is multiplied by {@code 2} if the machine is low pressure
     * </p>
     *
     * @param machine a {@link SimpleSteamMachine}
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Steam Machine
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof SimpleSteamMachine steamMachine)) {
            return RecipeModifier.nullWrongType(SimpleSteamMachine.class, machine);
        }
        if (RecipeHelper.getRecipeEUtTier(recipe) > GTValues.LV || !steamMachine.exhaustVentTrait.checkVenting()) {
            return ModifierFunction.NULL;
        }

        var builder = ModifierFunction.builder().conditions(VentCondition.INSTANCE);
        if (!steamMachine.isHighPressure) builder.durationMultiplier(2);
        return builder.build();
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        exhaustVentTrait.setNeedsVenting(true);
        exhaustVentTrait.checkVenting();
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        var storages = Tables.newCustomTable(new EnumMap<>(IO.class), LinkedHashMap<RecipeCapability<?>, Object>::new);
        storages.put(IO.IN, ItemRecipeCapability.CAP, importItems.storage);
        storages.put(IO.OUT, ItemRecipeCapability.CAP, exportItems.storage);

        var recipeType = getRecipeType();
        var recipeUI = recipeType.getRecipeUI();
        var recipeSize = recipeUI.getLDLib2RecipeUISize(true, isHighPressure);
        var recipeTemplate = recipeUI.createLDLib2UITemplate(recipeLogic::getProgressPercent,
                storages,
                DataComponentMap.EMPTY,
                Collections.emptyList(),
                true,
                isHighPressure);
        int recipeX = (Math.max(recipeSize.width() + 4 + 8, 176) - 4 - recipeSize.width()) / 2 + 4;
        int recipeY = 32;
        UITemplate.setLDLib2Bounds(recipeTemplate.rootElement, recipeX, recipeY,
                recipeSize.width(), recipeSize.height());

        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 166);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_STEAM.get(isHighPressure)));
        root.addChild(recipeTemplate.rootElement);
        root.addChild(createLDLib2TitleLabel());
        root.addChild(createWaitingIndicator(recipeX + recipeSize.width() / 2 - 9,
                recipeY + recipeSize.height() / 2 - 9, 18, 18,
                GuiTextures.INDICATOR_NO_STEAM.get(isHighPressure), recipeLogic::isWaiting));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(),
                GuiTextures.SLOT_STEAM.get(isHighPressure), 7, 84, true));
        return UI.of(root);
    }

    private GTLabelElement createLDLib2TitleLabel() {
        GTLabelElement label = new GTLabelElement(5, 5, 166, 10,
                getBlockState().getBlock().getDescriptionId(), true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static GTImageElement createWaitingIndicator(int xPosition, int yPosition, int width, int height,
                                                         IGuiTexture texture, BooleanSupplier predicate) {
        return new GTImageElement(xPosition, yPosition, width, height, texture)
                .setVisibleSupplier(predicate);
    }
}
