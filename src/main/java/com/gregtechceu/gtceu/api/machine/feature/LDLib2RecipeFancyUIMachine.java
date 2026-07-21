package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI.MachineUISize;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.RecipeHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.packs.resources.ResourceManager;

import com.google.common.collect.Table;
import com.google.common.collect.Tables;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;

/**
 * Loads and binds editable LDLib2 XML pages for tiered recipe machines.
 */
public interface LDLib2RecipeFancyUIMachine extends LDLib2FancyUIMachine {

    /**
     * Loads the active machine XML and binds it to the concrete recipe machine.
     *
     * @param shell Fancy shell that owns this page.
     * @return parsed and bound machine XML root.
     */
    @Override
    default UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        WorkableTieredMachine machine = getLDLib2RecipeMachine();
        return getLDLib2MachineUITemplate(machine)
                .createUI(getLDLib2MachineUIResourceManager(machine), machine).rootElement;
    }

    /**
     * Reads the active machine XML width used by the shared Fancy shell.
     *
     * @return fixed machine page width in pixels.
     */
    @Override
    default int getLDLib2PageWidth() {
        return getLDLib2MachineUISize(getLDLib2RecipeMachine()).width();
    }

    /**
     * Reads the active machine XML height used by the shared Fancy shell.
     *
     * @return fixed machine page height in pixels.
     */
    @Override
    default int getLDLib2PageHeight() {
        return getLDLib2MachineUISize(getLDLib2RecipeMachine()).height();
    }

    /**
     * Resolves the tiered recipe machine that owns this XML page.
     *
     * @return this provider as a tiered recipe machine.
     */
    WorkableTieredMachine getLDLib2RecipeMachine();

    /**
     * Binds recipe progress and capability elements already present in a parsed machine XML tree.
     *
     * @param root    parsed machine XML root.
     * @param machine tiered recipe machine backing the elements.
     */
    default void bindLDLib2RecipeElements(UIElement root, WorkableTieredMachine machine) {
        machine.getRecipeType().getRecipeUI().bindLDLib2RecipeUI(root,
                new RecipeHolder(machine.getRecipeLogic()::getProgressPercent,
                        createLDLib2RecipeStorages(machine),
                        DataComponentMap.EMPTY,
                        Collections.emptyList(),
                        false,
                        false));
    }

    /**
     * Creates the storage table bound into the recipe portion of the machine XML.
     *
     * @param machine tiered recipe machine whose handlers back the template slots.
     * @return item, fluid, and computation input/output storages.
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
     * Reads the active XML dimensions for a concrete machine.
     *
     * @param machine tiered recipe machine selecting the metadata and resource manager.
     * @return fixed active machine XML dimensions.
     */
    default MachineUISize getLDLib2MachineUISize(WorkableTieredMachine machine) {
        return getLDLib2MachineUITemplate(machine).getSize(getLDLib2MachineUIResourceManager(machine));
    }

    /**
     * Resolves the XML template metadata installed on the machine definition.
     *
     * @param machine tiered recipe machine selecting its registered definition.
     * @return required editable machine XML metadata.
     */
    default EditableMachineUI getLDLib2MachineUITemplate(WorkableTieredMachine machine) {
        EditableMachineUI template = machine.getDefinition().getEditableUI();
        if (template == null) {
            GTCEu.LOGGER.error("Recipe machine {} has no LDLib2 machine XML template",
                    machine.getDefinition().getId());
            throw new IllegalStateException("Recipe machine has no LDLib2 machine XML template: " +
                    machine.getDefinition().getId());
        }
        return template;
    }

    /**
     * Selects the logical-side resource manager used to load the machine XML.
     *
     * @param machine machine whose level identifies the logical side.
     * @return resource manager for that side.
     */
    default ResourceManager getLDLib2MachineUIResourceManager(WorkableTieredMachine machine) {
        var level = machine.getLevel();
        if (level == null) {
            GTCEu.LOGGER.error("Cannot load machine UI XML for unloaded machine {}", machine.getDefinition().getId());
            throw new IllegalStateException("Cannot load machine UI XML for an unloaded machine");
        }
        if (level.isClientSide()) {
            return Minecraft.getInstance().getResourceManager();
        }
        var server = level.getServer();
        if (server == null) {
            GTCEu.LOGGER.error("Cannot load server machine UI XML without a server for {}",
                    machine.getDefinition().getId());
            throw new IllegalStateException("Cannot load server machine UI XML without a server");
        }
        return server.getResourceManager();
    }
}
