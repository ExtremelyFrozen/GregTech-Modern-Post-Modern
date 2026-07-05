package com.gregtechceu.gtceu.integration.xei;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.RecipeData;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentListMap;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.LDLib2RecipeUISize;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.recipe.condition.DimensionCondition;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import com.google.common.collect.Table;
import com.google.common.collect.Tables;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.vfyjxf.taffy.style.TaffyPosition;

/**
 * Builds the parallel LDLib2 recipe UI tree without touching the legacy WidgetGroup XEI path.
 */
public final class GTLDLib2RecipeUI {

    private static final int TEXT_X = 3;
    private static final int LINE_HEIGHT = GTRecipeWidget.LINE_HEIGHT;

    private GTLDLib2RecipeUI() {
    }

    public static UI createUI(GTRecipeDefinition recipe, int recipeTier, int chanceTier) {
        var storages = Tables.newCustomTable(new EnumMap<>(IO.class), LinkedHashMap<RecipeCapability<?>, Object>::new);
        var contents = Tables.newCustomTable(new EnumMap<>(IO.class),
                LinkedHashMap<RecipeCapability<?>, List<Content>>::new);
        collectStorage(storages, contents, recipe);

        var recipeUI = recipe.recipeType.getRecipeUI();
        UI ui = recipeUI.createLDLib2UITemplate(GTRecipeTypeUI.XEI_PROGRESS, storages,
                DataComponentMap.EMPTY, recipe.conditions);
        recipeUI.applyLDLib2RecipeContent(ui, contents, recipe, recipeTier, chanceTier);
        return createXEIRoot(recipe, ui);
    }

    public static ModularUI createModularUI(GTRecipeDefinition recipe, int recipeTier, int chanceTier) {
        return ModularUI.of(createUI(recipe, recipeTier, chanceTier));
    }

    private static UI createXEIRoot(GTRecipeDefinition recipe, UI template) {
        var recipeUI = recipe.recipeType.getRecipeUI();
        LDLib2RecipeUISize rootSize = recipeUI.getLDLib2XEIRecipeUISize();
        LDLib2RecipeUISize templateSize = recipeUI.getLDLib2RecipeUISize(false, false);

        UIElement root = new UIElement();
        root.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(rootSize.width());
            layout.height(rootSize.height());
        });

        int templateX = Math.max((rootSize.width() - templateSize.width()) / 2, 0);
        template.rootElement.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(templateX);
            layout.top(0);
        });
        root.addChild(template.rootElement);

        addStaticXEIInfo(root, recipe, rootSize, templateSize);
        return UI.of(root, template.stylesheets);
    }

    private static void addStaticXEIInfo(UIElement root, GTRecipeDefinition recipe, LDLib2RecipeUISize rootSize,
                                         LDLib2RecipeUISize templateSize) {
        addRecipeParameterTexts(root, recipe, rootSize, templateSize);

        int yOffset = 5 + templateSize.height();
        if (RecipeHelper.getRealEUt(recipe) != 0) {
            yOffset += 21;
        }
        if (RecipeData.getBoolean(recipe.data, "duration_is_total_cwu")) {
            yOffset -= LINE_HEIGHT;
        }

        int[] cwuYOffset = {yOffset};
        addCWUInfo(root, rootSize, recipe, recipe.inputs.get(CWURecipeCapability.CAP), false, cwuYOffset);
        addCWUInfo(root, rootSize, recipe, recipe.tickInputs.get(CWURecipeCapability.CAP), true, cwuYOffset);
        addCWUInfo(root, rootSize, recipe, recipe.outputs.get(CWURecipeCapability.CAP), false, cwuYOffset);
        addCWUInfo(root, rootSize, recipe, recipe.tickOutputs.get(CWURecipeCapability.CAP), true, cwuYOffset);

        addConditionAndDataInfos(root, rootSize, recipe, yOffset);
    }

    @SuppressWarnings("deprecation")
    private static void addRecipeParameterTexts(UIElement root, GTRecipeDefinition recipe, LDLib2RecipeUISize rootSize,
                                                LDLib2RecipeUISize templateSize) {
        int textsY = templateSize.height() + 5 - LINE_HEIGHT;
        long eu = RecipeHelper.getRealEUtWithIO(recipe);
        for (Component text : GTRecipeWidget.getRecipeParaText(recipe, recipe.duration, eu)) {
            textsY += LINE_HEIGHT;
            root.addChild(createLabel(text, TEXT_X, textsY, rootSize.width() - 2 * TEXT_X, true));
        }

        if (eu == 0) {
            return;
        }

        textsY += LINE_HEIGHT;
        int minVoltageTier = RecipeHelper.getRecipeEUtTier(recipe);
        float minAmperage = (float) Math.abs(eu) / GTValues.V[minVoltageTier];
        Component text = Component.translatable(eu > 0 ? "gtpm.recipe.eu" : "gtpm.recipe.eu_inverted",
                        FormattingUtil.formatNumber2Places(minAmperage), GTValues.VN[minVoltageTier])
                .withStyle(ChatFormatting.UNDERLINE);
        Label label = createLabel(text, TEXT_X, textsY, rootSize.width() - 2 * TEXT_X, true);
        label.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable("gtpm.recipe.eu.total", FormattingUtil.formatNumbers(Math.abs(eu)))
                        .withStyle(ChatFormatting.UNDERLINE)),
                null, null, null));
        root.addChild(label);
    }

    private static void addCWUInfo(UIElement root, LDLib2RecipeUISize rootSize, GTRecipeDefinition recipe,
                                   List<Content> contents, boolean perTick, int[] yOffset) {
        if (contents == null) {
            return;
        }
        if (perTick) {
            int cwu = contents.stream().map(Content::getContent).mapToInt(CWURecipeCapability.CAP::of).sum();
            yOffset[0] += LINE_HEIGHT;
            root.addChild(createLabel(Component.translatable("gtpm.recipe.computation_per_tick",
                    FormattingUtil.formatNumbers(cwu)), TEXT_X, yOffset[0], rootSize.width() - 2 * TEXT_X, false));
        }
        if (RecipeData.getBoolean(recipe.data, "duration_is_total_cwu")) {
            yOffset[0] += LINE_HEIGHT;
            root.addChild(createLabel(Component.translatable("gtpm.recipe.total_computation",
                            FormattingUtil.formatNumbers(recipe.duration)), TEXT_X, yOffset[0],
                    rootSize.width() - 2 * TEXT_X, false));
        }
    }

    private static void addConditionAndDataInfos(UIElement root, LDLib2RecipeUISize rootSize,
                                                 GTRecipeDefinition recipe, int yOffset) {
        for (RecipeCondition<?> condition : recipe.conditions) {
            Component tooltip = condition.getTooltips();
            if (tooltip == null) {
                continue;
            }
            if (condition instanceof DimensionCondition dimCondition) {
                root.addChild(createDimensionMarker(dimCondition, rootSize));
            } else {
                yOffset += LINE_HEIGHT;
                root.addChild(createLabel(tooltip, TEXT_X, yOffset, rootSize.width() - 2 * TEXT_X, false));
            }
        }
        for (var dataInfo : recipe.recipeType.getDataInfos()) {
            yOffset += LINE_HEIGHT;
            root.addChild(createLabel(Component.literal(dataInfo.apply(recipe.data)), TEXT_X, yOffset,
                    rootSize.width() - 2 * TEXT_X, false));
        }
    }

    private static GTItemSlotElement createDimensionMarker(DimensionCondition condition, LDLib2RecipeUISize rootSize) {
        DimensionMarker dimMarker = GTRegistries.DIMENSION_MARKERS.getOptional(condition.getDimension().location())
                .orElse(new DimensionMarker(DimensionMarker.MAX_TIER,
                        () -> Blocks.BARRIER, DimensionCondition.getDimensionName(condition.getDimension())));
        ItemStack icon = dimMarker.getIcon();
        CustomItemStackHandler handler = new CustomItemStackHandler(1);
        GTItemSlotElement slot = new GTItemSlotElement(handler, 0);
        handler.setStackInSlot(0, icon);
        slot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(rootSize.width() - 44);
            layout.top(rootSize.height() - 32);
            layout.width(18);
            layout.height(18);
        });
        slot.setCanTakeItems(false);
        slot.setCanPutItems(false);
        slot.setIngredientIO(GTXEIHelper.input());
        slot.setBackgroundTexture(IGuiTexture.EMPTY);
        if (ConfigHolder.INSTANCE.compat.showDimensionTier) {
            slot.setContentOverlay(
                    GuiTextures.text("T" + (dimMarker.tier >= DimensionMarker.MAX_TIER ? "?" : dimMarker.tier))
                            .scale(0.75f).transform(-3.0f, 5.0f));
        }
        slot.xeiRecipeSlot();
        return slot;
    }

    private static Label createLabel(Component text, int x, int y, int width, boolean legacyWhiteText) {
        Label label = new Label();
        label.setValue(text);
        label.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(Math.max(width, 0));
            layout.height(LINE_HEIGHT);
        });
        label.textStyle(textStyle -> {
            textStyle.textWrap(TextWrap.NONE);
            if (legacyWhiteText) {
                textStyle.textColor(-1).textShadow(true);
            }
        });
        return label;
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
