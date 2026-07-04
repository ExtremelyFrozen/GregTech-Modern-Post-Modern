package com.gregtechceu.gtceu.integration.xei.widgets;

import com.lowdragmc.lowdraglib.gui.ingredient.IRecipeIngredientSlot;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GTRecipeIngredientSlotWidget extends Widget implements IRecipeIngredientSlot {

    private final IRecipeIngredientSlot source;
    private final IngredientIO ingredientIO;

    public GTRecipeIngredientSlotWidget(IRecipeIngredientSlot source, IngredientIO ingredientIO) {
        super(source.self().getSelfPosition(), source.self().getSize());
        this.source = source;
        this.ingredientIO = ingredientIO;
        setClientSideWidget();
    }

    @Override
    public List<Object> getXEIIngredients() {
        return source.getXEIIngredients();
    }

    @Nullable
    @Override
    public Object getXEICurrentIngredient() {
        return source.getXEICurrentIngredient();
    }

    @Nullable
    @Override
    public Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        return source.getXEIIngredientOverMouse(mouseX, mouseY);
    }

    @Override
    public float getXEIChance() {
        return source.getXEIChance();
    }

    @Override
    public IngredientIO getIngredientIO() {
        return ingredientIO;
    }

    @Override
    public List<Component> getFullTooltipTexts() {
        return source.getFullTooltipTexts();
    }
}
