package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.lowdragmc.lowdraglib2.editor.ui.menu.MenuTab;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TemplateTab extends MenuTab {

    private static final String MACHINE_MENU_KEY = "gtpm.gui.editor.templates.machine";
    private static final String RECIPE_TYPE_MENU_KEY = "gtpm.gui.editor.templates.recipe_type";
    private static final String TEMPLATE_TAB_KEY = "gtpm.gui.editor.templates";

    public TemplateTab(GTUIEditor editor) {
        super(editor);
    }

    @Override
    protected TreeBuilder.Menu createDefaultMenu() {
        return TreeBuilder.Menu.start()
                .branch(MACHINE_MENU_KEY, this::appendMachineTemplates)
                .branch(RECIPE_TYPE_MENU_KEY, this::appendRecipeTypeTemplates);
    }

    @Override
    protected Component getComponent() {
        return Component.translatable(TEMPLATE_TAB_KEY);
    }

    private void appendMachineTemplates(TreeBuilder.Menu menu) {
        Map<String, List<MachineTemplate>> groups = new LinkedHashMap<>();
        for (MachineTemplate template : collectMachineTemplates()) {
            groups.computeIfAbsent(template.editableUI().getGroupName(), ignored -> new ArrayList<>()).add(template);
        }
        groups.forEach((group, templates) -> menu.branch(Component.literal(group), branch -> {
            for (MachineTemplate template : templates) {
                branch.leaf(
                        new ItemStackTexture(template.definition().asStack()),
                        Component.translatable(template.definition().getDescriptionId()),
                        () -> editor.loadProject(createMachineProject(template), null));
            }
        }));
    }

    private void appendRecipeTypeTemplates(TreeBuilder.Menu menu) {
        Map<String, List<GTRecipeType>> groups = new LinkedHashMap<>();
        for (GTRecipeType recipeType : collectRecipeTypeTemplates()) {
            groups.computeIfAbsent(recipeType.group, ignored -> new ArrayList<>()).add(recipeType);
        }
        groups.forEach((group, recipeTypes) -> menu.branch(Component.literal(group), branch -> {
            for (GTRecipeType recipeType : recipeTypes) {
                var icon = recipeType.getIconSupplier() == null ? Items.BARRIER.getDefaultInstance() :
                        recipeType.getIconSupplier().get();
                branch.leaf(
                        new ItemStackTexture(icon),
                        recipeType.getName(),
                        () -> editor.loadProject(createRecipeTypeProject(recipeType), null));
            }
        }));
    }

    static List<MachineTemplate> collectMachineTemplates() {
        List<MachineTemplate> templates = new ArrayList<>();
        Set<EditableMachineUI> added = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MachineDefinition definition : GTRegistries.MACHINES) {
            EditableMachineUI editableUI = definition.getEditableUI();
            if (editableUI != null && added.add(editableUI)) {
                templates.add(new MachineTemplate(definition, editableUI));
            }
        }
        return templates;
    }

    static List<GTRecipeType> collectRecipeTypeTemplates() {
        List<GTRecipeType> templates = new ArrayList<>();
        for (var recipeType : BuiltInRegistries.RECIPE_TYPE) {
            if (recipeType instanceof GTRecipeType gtRecipeType) {
                templates.add(gtRecipeType);
            }
        }
        return templates;
    }

    static GTUIXmlProject createMachineProject(MachineTemplate template) {
        return new GTUIXmlProject().useTemplate(
                GTUIXmlTarget.machine(template.editableUI().getUiPath()),
                template.editableUI().getDefaultXml());
    }

    static GTUIXmlProject createRecipeTypeProject(GTRecipeType recipeType) {
        return new GTUIXmlProject().useTemplate(
                GTUIXmlTarget.recipeType(recipeType.registryName),
                recipeType.getRecipeUI().createLDLib2TemplateXml());
    }

    record MachineTemplate(MachineDefinition definition, EditableMachineUI editableUI) {}
}
