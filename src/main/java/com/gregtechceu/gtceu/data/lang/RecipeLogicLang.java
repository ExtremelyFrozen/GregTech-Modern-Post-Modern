package com.gregtechceu.gtceu.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class RecipeLogicLang {

    public static void init(RegistrateLangProvider provider) {
        initGenericLang(provider);
        initModifierLang(provider);
    }

    private static void initGenericLang(RegistrateLangProvider provider) {
        provider.add("gtpm.recipe_logic.setup_fail", "Fail to setup recipe: ");
        provider.add("gtpm.recipe_logic.recipe_waiting", "Recipe Waiting: ");

        provider.add("gtpm.recipe_logic.insufficient_fuel", "Insufficient Fuel");
        provider.add("gtpm.recipe_logic.insufficient_in", "Insufficient Inputs");
        provider.add("gtpm.recipe_logic.insufficient_out", "Insufficient Outputs");
        provider.add("gtpm.recipe_logic.condition_fails", "Condition Fails");
        provider.add("gtpm.recipe_logic.no_contents", "Recipe has no Contents");
        provider.add("gtpm.recipe_logic.no_capabilities", "Machine has no Capabilities");
    }

    private static void initModifierLang(RegistrateLangProvider provider) {
        provider.add("gtpm.recipe_modifier.default_fail", "Recipe Modifier Fail");
        provider.add("gtpm.recipe_modifier.insufficient_voltage", "Voltage Tier Too Low");
        provider.add("gtpm.recipe_modifier.insufficient_eu_to_start_fusion",
                "Insufficient Energy to Initiate Fusion Reaction");
        provider.add("gtpm.recipe_modifier.coil_temperature_too_low", "Coil Temperature Too Low");
    }
}
