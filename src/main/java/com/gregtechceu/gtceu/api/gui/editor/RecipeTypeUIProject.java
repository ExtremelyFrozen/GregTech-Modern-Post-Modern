package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.editor.data.Resources;
import com.lowdragmc.lowdraglib.gui.editor.data.UIProject;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.editor.ui.tool.WidgetToolBox;
import com.lowdragmc.lowdraglib.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@LDLRegister(name = "rtui", group = "editor.gtpm")
public class RecipeTypeUIProject extends UIProject {

    @Nullable
    @Getter
    @Setter
    protected GTRecipeType recipeType;

    private RecipeTypeUIProject() {
        this(null, null);
    }

    public RecipeTypeUIProject(Resources resources, WidgetGroup root) {
        super(resources, root);
    }

    public RecipeTypeUIProject(CompoundTag tag) {
        super(tag);
    }

    @Override
    public String getTranslateKey() {
        return GTCEu.MOD_ID + ".gui.editor.register.editor.gtpm.rtui";
    }

    @Override
    public RecipeTypeUIProject newEmptyProject() {
        return new RecipeTypeUIProject(Resources.defaultResource(), new WidgetGroup(30, 30, 200, 200));
    }

    @Override
    public UIProject loadProject(Path file) {
        try {
            var tag = NbtIo.read(file);
            if (tag != null) {
                return new RecipeTypeUIProject(tag);
            }
        } catch (IOException e) {
            GTCEu.LOGGER.warn("Failed to load recipe type UI project from {}", file, e);
        }
        return null;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var tag = super.serializeNBT(provider);
        if (recipeType != null) {
            tag.putString("recipe_type", recipeType.registryName.toString());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        if (tag.contains("recipe_type")) {
            var type = BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse(tag.getString("recipe_type")));
            recipeType = (GTRecipeType) type;
        }
    }

    @Override
    public void onLoad(Editor editor) {
        editor.getResourcePanel().loadResource(getResources(), false);
        editor.getTabPages().addTab(new TabButton(50, 16, 60, 14).setTexture(
                GuiTextures.group(ColorPattern.T_GREEN.rectTexture().setBottomRadius(10).transform(0, 0.4f),
                        GuiTextures.text("Main")),
                GuiTextures.group(ColorPattern.T_RED.rectTexture().setBottomRadius(10).transform(0, 0.4f),
                        GuiTextures.text("Main"))),
                new UIMainPanel(editor, root, recipeType == null ? null : recipeType.getTranslationKey()));
        for (WidgetToolBox.Default tab : WidgetToolBox.Default.TABS) {
            if (tab == WidgetToolBox.Default.CONTAINER) {
                continue;
            }
            editor.getToolPanel().addNewToolBox(GTUIEditor.getToolBoxTranslateKey(tab), tab.icon,
                    tab::createToolBox);
        }
    }

    @Override
    public void attachMenu(Editor editor, String name, TreeBuilder.Menu menu) {
        if (name.equals("file")) {
            if (recipeType == null) {
                menu.remove("gtpm.gui.editor.menu.save");
            } else {
                menu.remove("gtpm.gui.editor.menu.save");
                menu.leaf(Icons.SAVE, "gtpm.gui.editor.menu.save", () -> {
                    GTUIEditor.createWorkspaceDirectory(
                            "assets/%s/ui/recipe_type".formatted(recipeType.registryName.getNamespace()));
                    saveProject(Path.of(recipeType.registryName.getPath(), ".", this.getRegisterUI().name()));
                    recipeType.getRecipeUI().reloadCustomUI();
                });
            }
        } else if (name.equals("template_tab")) {
            Map<String, List<GTRecipeType>> categories = new LinkedHashMap<>();
            for (RecipeType<?> recipeType : BuiltInRegistries.RECIPE_TYPE) {
                if (!(recipeType instanceof GTRecipeType gtType)) {
                    continue;
                }
                categories.computeIfAbsent(gtType.group, group -> new ArrayList<>()).add(gtType);
            }
            categories.forEach((groupName, recipeTypes) -> menu.branch(groupName, m -> {
                for (GTRecipeType recipeType : recipeTypes) {
                    IGuiTexture icon;
                    if (recipeType.getIconSupplier() != null) {
                        icon = GuiTextures.itemStack(recipeType.getIconSupplier().get());
                    } else {
                        icon = GuiTextures.itemStack(Items.BARRIER);
                    }
                    m.leaf(icon, recipeType.getTranslationKey(), () -> {
                        root.clearAllWidgets();
                        if (recipeType.getRecipeUI().hasCustomUI()) {
                            var nbt = recipeType.getRecipeUI().getCustomUI();
                            IConfigurableWidget.deserializeNBT(root, nbt.getCompound("root"),
                                    Resources.fromNBT(nbt.getCompound("resources")), false,
                                    GTRegistries.builtinRegistry());
                        } else {
                            var widget = recipeType.getRecipeUI().createEditableUITemplate(false, false)
                                    .createDefault();
                            root.setSize(widget.getSize());
                            for (Widget children : widget.widgets) {
                                root.addWidget(children);
                            }
                        }
                        setRecipeType(recipeType);
                    });
                }
            }));
        }
    }
}
