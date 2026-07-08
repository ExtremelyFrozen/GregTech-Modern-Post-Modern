package com.gregtechceu.gtceu.integration.xei;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.RecipeData;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentListMap;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.LDLib2RecipeUISize;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gregtechceu.gtceu.common.recipe.condition.DimensionCondition;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the parallel LDLib2 recipe UI tree without touching the legacy WidgetGroup XEI path.
 */
public final class GTLDLib2RecipeUI {

    private static final int TEXT_X = 3;
    private static final int LINE_HEIGHT = GTRecipeXEIHelper.LINE_HEIGHT;

    private GTLDLib2RecipeUI() {}

    public static UI createUI(GTRecipeDefinition recipe, int recipeTier, int chanceTier) {
        var storages = Tables.newCustomTable(new EnumMap<>(IO.class), LinkedHashMap<RecipeCapability<?>, Object>::new);
        var contents = Tables.newCustomTable(new EnumMap<>(IO.class),
                LinkedHashMap<RecipeCapability<?>, List<Content>>::new);
        collectStorage(storages, contents, recipe);

        return new RecipeView(recipe, storages, contents, recipeTier, chanceTier).createUI();
    }

    public static ModularUI createModularUI(GTRecipeDefinition recipe, int recipeTier, int chanceTier) {
        return ModularUI.of(createUI(recipe, recipeTier, chanceTier));
    }

    private static final class RecipeView {

        private final GTRecipeDefinition recipe;
        private final Table<IO, RecipeCapability<?>, Object> storages;
        private final Table<IO, RecipeCapability<?>, List<Content>> contents;
        private final GTRecipeTypeUI recipeUI;
        private final LDLib2RecipeUISize rootSize;
        private final LDLib2RecipeUISize templateSize;
        private final int templateX;
        private final int minTier;
        private final int recipeTier;
        private final List<GTLabelElement> recipeParaTexts = new ArrayList<>();
        private int tier;
        private long recipeVoltageTooltipEUt;
        private OverclockingLogic overclockingLogic = OverclockingLogic.NON_PERFECT_OVERCLOCK;
        private UIElement root;
        private UIElement recipeContentRoot;
        private UIElement voltageClickArea;
        private GTLabelElement recipeVoltageText;
        private GTLabelElement voltageTextWidget;

        private RecipeView(GTRecipeDefinition recipe,
                           Table<IO, RecipeCapability<?>, Object> storages,
                           Table<IO, RecipeCapability<?>, List<Content>> contents,
                           int recipeTier,
                           int chanceTier) {
            this.recipe = recipe;
            this.storages = storages;
            this.contents = contents;
            this.recipeUI = recipe.recipeType.getRecipeUI();
            this.rootSize = recipeUI.getLDLib2XEIRecipeUISize();
            this.templateSize = recipeUI.getLDLib2RecipeUISize(false, false);
            this.templateX = Math.max((rootSize.width() - templateSize.width()) / 2, 0);
            this.minTier = RecipeHelper.getRecipeEUtTier(recipe);
            this.recipeTier = Mth.clamp(recipeTier, minTier, GTValues.MAX);
            this.tier = Mth.clamp(chanceTier, minTier, GTValues.MAX);
        }

        private UI createUI() {
            root = new UIElement();
            root.layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.width(rootSize.width());
                layout.height(rootSize.height());
            });

            UI template = createRecipeContent();
            attachRecipeContent(template.rootElement);
            addStaticXEIInfo();
            addRecipeIdButton(root, recipe, rootSize);
            return UI.of(root, template.stylesheets);
        }

        private UI createRecipeContent() {
            UI template = recipeUI.createLDLib2UITemplate(GTRecipeTypeUI.XEI_PROGRESS, storages,
                    DataComponentMap.EMPTY, recipe.conditions);
            recipeUI.applyLDLib2RecipeContent(template, contents, recipe, recipeTier, tier);
            template.rootElement.layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(templateX);
                layout.top(0);
            });
            return template;
        }

        private void attachRecipeContent(UIElement contentRoot) {
            if (recipeContentRoot != null) {
                root.removeChild(recipeContentRoot);
            }
            recipeContentRoot = contentRoot;
            root.addChildAt(recipeContentRoot, 0);
        }

        private void addStaticXEIInfo() {
            addRecipeParameterTexts();

            int yOffset = 5 + templateSize.height();
            if (RecipeHelper.getRealEUt(recipe) != 0) {
                yOffset += 21;
            }
            if (RecipeData.getBoolean(recipe.data, "duration_is_total_cwu")) {
                yOffset -= LINE_HEIGHT;
            }

            int[] cwuYOffset = { yOffset };
            addCWUInfo(root, rootSize, recipe, recipe.inputs.get(CWURecipeCapability.CAP), false, cwuYOffset);
            addCWUInfo(root, rootSize, recipe, recipe.tickInputs.get(CWURecipeCapability.CAP), true, cwuYOffset);
            addCWUInfo(root, rootSize, recipe, recipe.outputs.get(CWURecipeCapability.CAP), false, cwuYOffset);
            addCWUInfo(root, rootSize, recipe, recipe.tickOutputs.get(CWURecipeCapability.CAP), true, cwuYOffset);

            addConditionAndDataInfos(root, rootSize, recipe, cwuYOffset[0]);
            recipeUI.appendLDLib2XEIUI(recipe, root, rootSize);
        }

        private void addRecipeParameterTexts() {
            int textsY = templateSize.height() + 5 - LINE_HEIGHT;
            long eu = RecipeHelper.getRealEUtWithIO(recipe);
            for (Component text : GTRecipeXEIHelper.getRecipeParaText(recipe, recipe.duration, eu)) {
                textsY += LINE_HEIGHT;
                GTLabelElement label = createLabel(text, TEXT_X, textsY, rootSize.width() - 2 * TEXT_X, true);
                root.addChild(label);
                recipeParaTexts.add(label);
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
            recipeVoltageText = createLabel(text, TEXT_X, textsY, rootSize.width() - 2 * TEXT_X, true);
            recipeVoltageTooltipEUt = Math.abs(eu);
            recipeVoltageText.addEventListener(UIEvents.HOVER_TOOLTIPS,
                    event -> event.hoverTooltips = new HoverTooltips(List.of(createRecipeVoltageTooltip()), null, null,
                            null));
            root.addChild(recipeVoltageText);

            if (eu > 0) {
                addVoltageTierText();
            }
        }

        private void addVoltageTierText() {
            int x = getVoltageXOffset(tier, rootSize.width());
            int y = getVoltageY();
            voltageTextWidget = createLabel(Component.literal(GTValues.VNF[tier]), x, y, rootSize.width() - x,
                    false);
            voltageTextWidget.textStyle(textStyle -> textStyle.textColor(-1).textShadow(false));
            root.addChild(voltageTextWidget);

            voltageClickArea = new UIElement();
            voltageClickArea.layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(x);
                layout.top(y);
                layout.width(rootSize.width() - x);
                layout.height(LINE_HEIGHT);
            });
            voltageClickArea.addEventListener(UIEvents.MOUSE_DOWN, this::setRecipeOC);
            voltageClickArea.addEventListener(UIEvents.HOVER_TOOLTIPS,
                    event -> event.hoverTooltips = new HoverTooltips(getVoltageTierTooltips(), null, null, null));
            root.addChild(voltageClickArea);
        }

        private int getVoltageY() {
            if (recipe.recipeType.isOffsetVoltageText()) {
                return rootSize.height() - recipe.recipeType.getVoltageTextOffset();
            }
            return rootSize.height() - LINE_HEIGHT;
        }

        private void setRecipeOC(UIEvent event) {
            if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                setTier(tier + 1);
            } else if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                setTier(tier - 1);
            } else if (event.button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                setTier(minTier);
            } else {
                return;
            }
            overclockingLogic = event.isShiftDown() ?
                    OverclockingLogic.PERFECT_OVERCLOCK :
                    OverclockingLogic.NON_PERFECT_OVERCLOCK;
            if (recipe.recipeType == GTRecipeTypes.FUSION_RECIPES) {
                overclockingLogic = FusionReactorMachine.FUSION_OC;
            }
            updateRecipeOverclockPreview();
            event.stopPropagation();
        }

        private void setTier(int tier) {
            this.tier = Mth.clamp(tier, minTier, GTValues.MAX);
        }

        private void updateRecipeOverclockPreview() {
            OverclockPreview preview = calculateOverclockPreview();
            List<Component> texts = GTRecipeXEIHelper.getRecipeParaText(recipe, preview.duration(), preview.eut());
            for (int i = 0; i < texts.size() && i < recipeParaTexts.size(); i++) {
                recipeParaTexts.get(i).setValue(texts.get(i));
            }
            if (voltageTextWidget != null) {
                voltageTextWidget.setValue(Component.literal(preview.tierText()));
                int x = getVoltageXOffset(tier, rootSize.width());
                voltageTextWidget.layout(layout -> {
                    layout.left(x);
                    layout.width(rootSize.width() - x);
                });
                voltageClickArea.layout(layout -> {
                    layout.left(x);
                    layout.width(rootSize.width() - x);
                });
            }
            if (recipeVoltageText != null) {
                float minAmperage = (float) preview.eut() / GTValues.V[tier];
                recipeVoltageText.setValue(Component.translatable("gtpm.recipe.eu",
                        FormattingUtil.formatNumber2Places(minAmperage), GTValues.VN[tier])
                        .withStyle(ChatFormatting.UNDERLINE));
                recipeVoltageTooltipEUt = preview.eut();
            }
            attachRecipeContent(createRecipeContent().rootElement);
        }

        private OverclockPreview calculateOverclockPreview() {
            long inputEUt = recipe.getInputEUt();
            int duration = recipe.duration;
            String tierText = GTValues.VNF[tier];

            if (tier > minTier && inputEUt > 0) {
                int overclocks = tier - minTier;
                if (minTier == GTValues.ULV) {
                    overclocks--;
                }
                var params = new OverclockingLogic.OCParams(inputEUt, recipe.duration, overclocks, 1);
                var result = overclockingLogic.runOverclockingLogic(params, GTValues.V[tier]);
                duration = (int) (duration * result.durationMultiplier());
                inputEUt = (long) (inputEUt * result.eutMultiplier());
                tierText = tierText.formatted(ChatFormatting.ITALIC);
            }
            return new OverclockPreview(duration, inputEUt, tierText);
        }

        private List<Component> getVoltageTierTooltips() {
            return new ArrayList<>(LangHandler.getMultiLang("gtpm.oc.tooltip", GTValues.VNF[minTier]));
        }

        private Component createRecipeVoltageTooltip() {
            return Component.translatable("gtpm.recipe.eu.total",
                    FormattingUtil.formatNumbers(Math.abs(recipeVoltageTooltipEUt)))
                    .withStyle(ChatFormatting.UNDERLINE);
        }
    }

    private record OverclockPreview(int duration, long eut, String tierText) {}

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

    private static GTLabelElement createLabel(Component text, int x, int y, int width, boolean legacyWhiteText) {
        GTLabelElement label = new GTLabelElement(x, y, Math.max(width, 0), LINE_HEIGHT, text);
        label.textStyle(textStyle -> {
            textStyle.textWrap(TextWrap.NONE);
            if (legacyWhiteText) {
                textStyle.textColor(-1).textShadow(true);
            }
        });
        return label;
    }

    private static int getVoltageXOffset(int tier, int width) {
        int x = width - switch (tier) {
            case GTValues.ULV, GTValues.LuV, GTValues.ZPM, GTValues.UHV, GTValues.UEV, GTValues.UXV -> 20;
            case GTValues.OpV, GTValues.MAX -> 22;
            case GTValues.UIV -> 18;
            case GTValues.IV -> 12;
            default -> 14;
        };
        if (!GTCEu.Mods.isEMILoaded()) {
            x -= 3;
        }
        return x;
    }

    private static void addRecipeIdButton(UIElement root, GTRecipeDefinition recipe, LDLib2RecipeUISize rootSize) {
        if (GTCEu.isProd()) {
            return;
        }
        String recipeId = String.valueOf(recipe.id);
        GTButtonElement button = new GTButtonElement(rootSize.width() - 18, rootSize.height() - 30, 15, 15);
        button.setText(Component.literal("ID"));
        button.buttonStyle(style -> style
                .baseTexture(GuiTextures.BUTTON)
                .hoverTexture(GuiTextures.BUTTON)
                .pressedTexture(GuiTextures.BUTTON));
        button.setOnClick(event -> Minecraft.getInstance().keyboardHandler.setClipboard(recipeId));
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.literal("click to copy: " + recipeId)), null, null, null));
        root.addChild(button);
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
