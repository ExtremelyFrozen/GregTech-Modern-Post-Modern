package com.gregtechceu.gtceu.integration.xei;

import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import org.jetbrains.annotations.Nullable;

/**
 * Converts GTM roles for the LDLib2 UI facade; the legacy adapter remains for unmigrated LDLib1 widget boundaries.
 */
public final class GTXEIIngredientRoleLDLib2Adapter {

    private GTXEIIngredientRoleLDLib2Adapter() {}

    public static IngredientIO toLDLib2(GTXEIIngredientRole role) {
        return switch (role) {
            case INPUT -> IngredientIO.INPUT;
            case OUTPUT -> IngredientIO.OUTPUT;
            case CATALYST -> IngredientIO.CATALYST;
            case NONE -> IngredientIO.NONE;
        };
    }

    public static GTXEIIngredientRole fromLDLib2(@Nullable IngredientIO ingredientIO) {
        if (ingredientIO == null) {
            return GTXEIIngredientRole.NONE;
        }
        return switch (ingredientIO) {
            case INPUT -> GTXEIIngredientRole.INPUT;
            case OUTPUT -> GTXEIIngredientRole.OUTPUT;
            case CATALYST -> GTXEIIngredientRole.CATALYST;
            case NONE -> GTXEIIngredientRole.NONE;
        };
    }
}
