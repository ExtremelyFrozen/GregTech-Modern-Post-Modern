package com.gregtechceu.gtceu.integration.xei;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentListMap;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.core.component.DataComponentMap;

import com.google.common.collect.Table;
import com.google.common.collect.Tables;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the parallel LDLib2 recipe UI tree without touching the legacy WidgetGroup XEI path.
 */
public final class GTLDLib2RecipeUI {

    private GTLDLib2RecipeUI() {}

    public static UI createUI(GTRecipeDefinition recipe, int recipeTier, int chanceTier) {
        var storages = Tables.newCustomTable(new EnumMap<>(IO.class), LinkedHashMap<RecipeCapability<?>, Object>::new);
        var contents = Tables.newCustomTable(new EnumMap<>(IO.class),
                LinkedHashMap<RecipeCapability<?>, List<Content>>::new);
        collectStorage(storages, contents, recipe);

        var recipeUI = recipe.recipeType.getRecipeUI();
        UI ui = recipeUI.createLDLib2UITemplate(GTRecipeTypeUI.XEI_PROGRESS, storages,
                DataComponentMap.EMPTY, recipe.conditions);
        recipeUI.applyLDLib2RecipeContent(ui, contents, recipe, recipeTier, chanceTier);
        return ui;
    }

    public static ModularUI createModularUI(GTRecipeDefinition recipe, int recipeTier, int chanceTier) {
        return ModularUI.of(createUI(recipe, recipeTier, chanceTier));
    }

    public static void collectStorage(Table<IO, RecipeCapability<?>, Object> storages,
                                      Table<IO, RecipeCapability<?>, List<Content>> contents,
                                      GTRecipeDefinition recipe) {
        collectContents(contents, recipe.inputs, IO.IN);
        collectContents(contents, recipe.tickInputs, IO.IN);
        collectContainers(storages, contents, recipe, IO.IN);

        collectContents(contents, recipe.outputs, IO.OUT);
        collectContents(contents, recipe.tickOutputs, IO.OUT);
        collectContainers(storages, contents, recipe, IO.OUT);
    }

    private static void collectContents(Table<IO, RecipeCapability<?>, List<Content>> contents,
                                        ContentListMap recipeContents,
                                        IO io) {
        for (var entry : recipeContents.entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            List<Content> entryContents = entry.getValue();
            List<Content> existing = contents.get(io, cap);
            if (existing == null) {
                contents.put(io, cap, entryContents);
            } else {
                ArrayList<Content> fullContents = new ArrayList<>(existing);
                fullContents.addAll(entryContents);
                contents.put(io, cap, fullContents);
            }
        }
    }

    private static void collectContainers(Table<IO, RecipeCapability<?>, Object> storages,
                                          Table<IO, RecipeCapability<?>, List<Content>> contents,
                                          GTRecipeDefinition recipe,
                                          IO io) {
        if (!contents.containsRow(io)) {
            return;
        }
        Map<RecipeCapability<?>, List<Object>> capabilities = new LinkedHashMap<>();
        for (var entry : contents.row(io).entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            capabilities.put(cap, cap.createXEIContainerContents(entry.getValue(), recipe, io));
        }
        for (var entry : capabilities.entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            int maxSlots = io == IO.IN ? recipe.recipeType.getMaxInputs(cap) : recipe.recipeType.getMaxOutputs(cap);
            while (entry.getValue().size() < maxSlots) {
                entry.getValue().add(null);
            }
            var container = cap.createXEIContainer(entry.getValue());
            if (container != null) {
                storages.put(io, cap, container);
            }
        }
    }
}
