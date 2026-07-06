package com.gregtechceu.gtceu.integration.xei;

import com.lowdragmc.lowdraglib.jei.IngredientIO;

/**
 * Converts GTM roles only for unmigrated widgets that still expose the LDLib1 widget contract.
 * LDLib2 UI facade code should use {@link GTXEIIngredientRoleLDLib2Adapter}.
 */
public final class GTXEIIngredientRoleLDLib1Adapter {

    private GTXEIIngredientRoleLDLib1Adapter() {}

    public static IngredientIO toLegacy(GTXEIIngredientRole role) {
        return switch (role) {
            case INPUT -> IngredientIO.INPUT;
            case OUTPUT -> IngredientIO.OUTPUT;
            case CATALYST -> IngredientIO.CATALYST;
            case NONE -> IngredientIO.RENDER_ONLY;
        };
    }
}
