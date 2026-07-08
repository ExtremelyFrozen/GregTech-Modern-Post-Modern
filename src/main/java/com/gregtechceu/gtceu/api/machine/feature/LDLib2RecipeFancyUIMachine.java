package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.LDLib2RecipeUISize;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.component.DataComponentMap;

import com.google.common.collect.Table;
import com.google.common.collect.Tables;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;

/**
 * Supplies the default LDLib2 Fancy recipe page for tiered recipe machines.
 *
 * <p>
 * This contract is intentionally limited to the generated recipe template and the machine's normal recipe
 * storages. It does not load legacy editable UI definitions or wire concrete machines into the LDLib2 Fancy opening
 * path.
 */
public interface LDLib2RecipeFancyUIMachine extends LDLib2FancyUIMachine {

    /**
     * Keeps the recipe page tall enough for the legacy electric-machine battery-slot layout.
     */
    int MIN_RECIPE_PAGE_HEIGHT = 78;

    /**
     * Builds a fixed-size LDLib2 page containing the active recipe type's LDLib2 template.
     *
     * @param shell Fancy shell that owns this page; current default layout does not need shell state.
     * @return page root sized to this provider's LDLib2 page dimensions.
     */
    @Override
    default UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        WorkableTieredMachine machine = getLDLib2RecipeMachine();
        LDLib2RecipeUISize recipeSize = getLDLib2RecipeUISize(machine);
        int pageWidth = getLDLib2PageWidth();
        int pageHeight = getLDLib2PageHeight();

        UI recipeTemplate = machine.getRecipeType().getRecipeUI().createLDLib2UITemplate(
                machine.getRecipeLogic()::getProgressPercent,
                createLDLib2RecipeStorages(machine),
                DataComponentMap.EMPTY,
                Collections.emptyList(),
                false,
                false);
        UITemplate.setLDLib2Bounds(recipeTemplate.rootElement,
                (pageWidth - recipeSize.width()) / 2,
                (pageHeight - recipeSize.height()) / 2,
                recipeSize.width(),
                recipeSize.height());

        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, pageWidth, pageHeight);
        root.addChild(recipeTemplate.rootElement);
        attachLDLib2RecipePageElements(root, machine, recipeSize);
        return root;
    }

    /**
     * Uses the generated recipe template width as the Fancy page width.
     *
     * @return active recipe template width in pixels.
     */
    @Override
    default int getLDLib2PageWidth() {
        return getLDLib2RecipeUISize(getLDLib2RecipeMachine()).width();
    }

    /**
     * Uses the generated recipe template height, with the legacy simple-machine minimum height.
     *
     * @return active recipe page height in pixels.
     */
    @Override
    default int getLDLib2PageHeight() {
        return Math.max(getLDLib2RecipeUISize(getLDLib2RecipeMachine()).height(), MIN_RECIPE_PAGE_HEIGHT);
    }

    /**
     * Resolves the tiered recipe machine that owns the default LDLib2 recipe page.
     *
     * @return this provider as a {@link WorkableTieredMachine}.
     */
    WorkableTieredMachine getLDLib2RecipeMachine();

    /**
     * Reads the active recipe type's LDLib2 template size.
     *
     * @param machine tiered recipe machine providing the active recipe type.
     * @return fixed LDLib2 recipe UI dimensions for a non-steam machine.
     */
    default LDLib2RecipeUISize getLDLib2RecipeUISize(WorkableTieredMachine machine) {
        return machine.getRecipeType().getRecipeUI().getLDLib2RecipeUISize(false, false);
    }

    /**
     * Creates the storage table bound into the LDLib2 recipe template.
     *
     * @param machine tiered recipe machine whose handlers back the template slots.
     * @return item, fluid, and computation input/output storages for normal electric recipe machines.
     */
    default Table<IO, RecipeCapability<?>, Object> createLDLib2RecipeStorages(WorkableTieredMachine machine) {
        Table<IO, RecipeCapability<?>, Object> storages = Tables.newCustomTable(new EnumMap<>(IO.class),
                LinkedHashMap::new);
        storages.put(IO.IN, ItemRecipeCapability.CAP, machine.importItems.storage);
        storages.put(IO.OUT, ItemRecipeCapability.CAP, machine.exportItems.storage);
        storages.put(IO.IN, FluidRecipeCapability.CAP, machine.importFluids);
        storages.put(IO.OUT, FluidRecipeCapability.CAP, machine.exportFluids);
        storages.put(IO.IN, CWURecipeCapability.CAP, machine.importComputation);
        storages.put(IO.OUT, CWURecipeCapability.CAP, machine.exportComputation);
        return storages;
    }

    /**
     * Lets concrete machine pages add fixed-position LDLib2 elements around the generated recipe template.
     *
     * @param root       page root receiving additional elements.
     * @param machine    tiered recipe machine that owns this page.
     * @param recipeSize active recipe template dimensions.
     */
    default void attachLDLib2RecipePageElements(UIElement root, WorkableTieredMachine machine,
                                                LDLib2RecipeUISize recipeSize) {}
}
