package com.gregtechceu.gtceu.integration.jei;

import net.minecraft.client.renderer.Rect2i;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IClickableIngredient;

/**
 * JEI clickable ingredient area used by GTM legacy widget facades during the LDLib2 UI migration.
 */
public class GTClickableIngredient<T> implements IClickableIngredient<T> {

    private final ITypedIngredient<T> ingredient;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public GTClickableIngredient(ITypedIngredient<T> ingredient, int x, int y, int width, int height) {
        this.ingredient = ingredient;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public ITypedIngredient<T> getTypedIngredient() {
        return ingredient;
    }

    @Override
    public Rect2i getArea() {
        return new Rect2i(x, y, width, height);
    }
}
