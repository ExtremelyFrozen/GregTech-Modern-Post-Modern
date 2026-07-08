package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.SteamTexture;
import com.gregtechceu.gtceu.api.gui.WidgetUtils;
import com.gregtechceu.gtceu.api.gui.editor.IEditableUI;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTDualProgressElement;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.ProgressTexture;
import com.gregtechceu.gtceu.api.gui.texture.ResourceTexture;
import com.gregtechceu.gtceu.api.gui.widget.DualProgressWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.integration.emi.recipe.GTRecipeEMICategory;
import com.gregtechceu.gtceu.integration.jei.GTJEIPlugin;
import com.gregtechceu.gtceu.integration.jei.recipe.GTLDLib2RecipeJEICategory;

import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import com.google.common.collect.Table;
import dev.emi.emi.api.EmiApi;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectArrayMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

@SuppressWarnings("UnusedReturnValue")
public class GTRecipeTypeUI {

    public static final DoubleSupplier XEI_PROGRESS = () -> Math.abs(System.currentTimeMillis() % 2000) / 2000.0;

    @Getter
    @Setter
    private Byte2ObjectMap<IGuiTexture> slotOverlays = new Byte2ObjectArrayMap<>();

    private final GTRecipeType recipeType;

    @Getter
    @Setter
    private ProgressTexture progressBarTexture = GuiTextures.progressBar(GuiTextures.PROGRESS_BAR_ARROW);
    @Setter
    private SteamTexture steamProgressBarTexture = null;
    @Setter
    private ProgressTexture.FillDirection steamMoveType = ProgressTexture.FillDirection.LEFT_TO_RIGHT;
    @Setter
    @Nullable
    protected LDLib2UiBuilder ldLib2UiBuilder;
    @Setter
    @Getter
    protected int maxTooltips = 3;

    private String customLDLib2UICache;
    private boolean customLDLib2UICacheLoaded;

    /**
     * @param recipeType the recipemap corresponding to this ui
     */
    public GTRecipeTypeUI(@NotNull GTRecipeType recipeType) {
        this.recipeType = recipeType;
    }

    public CompoundTag getCustomUI() {
        return new CompoundTag();
    }

    public boolean hasCustomUI() {
        return false;
    }

    public boolean hasCustomLDLib2UI() {
        return getCustomLDLib2UIXml() != null;
    }

    public UI createCustomLDLib2UI() {
        String xml = getCustomLDLib2UIXml();
        if (xml == null) {
            return UI.empty();
        }

        try {
            return UI.of(parseCustomLDLib2UI(xml));
        } catch (Exception e) {
            GTCEu.LOGGER.warn("Failed to parse LDLib2 recipe type UI from {}", getCustomLDLib2UILocation(), e);
            return UI.empty();
        }
    }

    @Nullable
    private String getCustomLDLib2UIXml() {
        if (!this.customLDLib2UICacheLoaded) {
            ResourceManager resourceManager = getResourceManager();
            if (resourceManager == null) {
                this.customLDLib2UICache = null;
            } else {
                ResourceLocation location = getCustomLDLib2UILocation();
                var resource = resourceManager.getResource(location);
                if (resource.isEmpty()) {
                    this.customLDLib2UICache = null;
                } else {
                    try (InputStream inputStream = resource.get().open()) {
                        this.customLDLib2UICache = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        GTCEu.LOGGER.warn("Failed to load LDLib2 recipe type UI from {}", location, e);
                        this.customLDLib2UICache = null;
                    }
                }
            }
            this.customLDLib2UICacheLoaded = true;
        }
        return this.customLDLib2UICache;
    }

    private ResourceLocation getCustomLDLib2UILocation() {
        return ResourceLocation.fromNamespaceAndPath(recipeType.registryName.getNamespace(),
                "ui/recipe_type/%s.xml".formatted(recipeType.registryName.getPath()));
    }

    @Nullable
    private ResourceManager getResourceManager() {
        if (GTCEu.isClientSide()) {
            return Minecraft.getInstance().getResourceManager();
        } else if (GTCEu.getMinecraftServer() != null) {
            return GTCEu.getMinecraftServer().getResourceManager();
        }
        return null;
    }

    private static Document parseCustomLDLib2UI(String xml) throws Exception {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        documentBuilderFactory.setXIncludeAware(false);
        documentBuilderFactory.setExpandEntityReferences(false);

        var documentBuilder = documentBuilderFactory.newDocumentBuilder();
        try (StringReader reader = new StringReader(xml)) {
            return documentBuilder.parse(new InputSource(reader));
        }
    }

    public void reloadCustomUI() {
        this.customLDLib2UICache = null;
        this.customLDLib2UICacheLoaded = false;
    }

    public record RecipeHolder(DoubleSupplier progressSupplier,
                               Table<IO, RecipeCapability<?>, Object> storages,
                               DataComponentMap data,
                               List<RecipeCondition<?>> conditions,
                               boolean isSteam,
                               boolean isHighPressure) {}

    public record LDLib2RecipeUISize(int width, int height) {}

    @FunctionalInterface
    public interface LDLib2UiBuilder {

        void accept(GTRecipeDefinition recipe, UIElement root, LDLib2RecipeUISize rootSize);
    }

    /**
     * Auto layout UI template for recipes.
     *
     * @param progressSupplier progress. To create an XEI UI, use {@link #XEI_PROGRESS}.
     */
    public WidgetGroup createUITemplate(DoubleSupplier progressSupplier,
                                        Table<IO, RecipeCapability<?>, Object> storages,
                                        DataComponentMap data,
                                        List<RecipeCondition<?>> conditions,
                                        boolean isSteam,
                                        boolean isHighPressure) {
        var template = createEditableUITemplate(isSteam, isHighPressure);
        var group = template.createDefault();
        template.setupUI(group,
                new RecipeHolder(progressSupplier, storages, data, conditions, isSteam, isHighPressure));
        return group;
    }

    public WidgetGroup createUITemplate(DoubleSupplier progressSupplier,
                                        Table<IO, RecipeCapability<?>, Object> storages,
                                        DataComponentMap data,
                                        List<RecipeCondition<?>> conditions) {
        return createUITemplate(progressSupplier, storages, data, conditions, false, false);
    }

    public UI createLDLib2UITemplate(DoubleSupplier progressSupplier,
                                     Table<IO, RecipeCapability<?>, Object> storages,
                                     DataComponentMap data,
                                     List<RecipeCondition<?>> conditions,
                                     boolean isSteam,
                                     boolean isHighPressure) {
        UI ui = !isSteam && hasCustomLDLib2UI() ? createCustomLDLib2UI() :
                createDefaultLDLib2UI(isSteam, isHighPressure);
        setupLDLib2UI(ui.rootElement,
                new RecipeHolder(progressSupplier, storages, data, conditions, isSteam, isHighPressure));
        return ui;
    }

    public UI createLDLib2UITemplate(DoubleSupplier progressSupplier,
                                     Table<IO, RecipeCapability<?>, Object> storages,
                                     DataComponentMap data,
                                     List<RecipeCondition<?>> conditions) {
        return createLDLib2UITemplate(progressSupplier, storages, data, conditions, false, false);
    }

    public LDLib2RecipeUISize getLDLib2XEIRecipeUISize() {
        LDLib2RecipeUISize rawSize = getLDLib2RecipeUISize(false, false);
        return new LDLib2RecipeUISize(Math.max(rawSize.width(), 150),
                rawSize.height() + 5 + getPropertyHeightShift());
    }

    public LDLib2RecipeUISize getLDLib2RecipeUISize(boolean isSteam, boolean isHighPressure) {
        UI ui = !isSteam && hasCustomLDLib2UI() ? createCustomLDLib2UI() :
                createDefaultLDLib2UI(isSteam, isHighPressure);
        return getLDLib2RecipeUISize(ui.rootElement);
    }

    private static LDLib2RecipeUISize getLDLib2RecipeUISize(UIElement root) {
        return new LDLib2RecipeUISize(
                fixedLDLib2RecipeUISize(getLDLib2Dimension(root, true), "width"),
                fixedLDLib2RecipeUISize(getLDLib2Dimension(root, false), "height"));
    }

    private static TaffyDimension getLDLib2Dimension(UIElement root, boolean width) {
        TaffyDimension dimension = width ? root.getLayout().getWidth() : root.getLayout().getHeight();
        if (dimension != null && dimension.isLength()) {
            return dimension;
        }
        TaffyDimension styleDimension = root.getStyleBag()
                .computeCandidate(width ? LayoutProperties.WIDTH : LayoutProperties.HEIGHT);
        return styleDimension == null ? dimension : styleDimension;
    }

    private static int fixedLDLib2RecipeUISize(TaffyDimension dimension, String axis) {
        if (dimension == null || !dimension.isLength()) {
            GTCEu.LOGGER.error("LDLib2 recipe UI {} must use a fixed pixel size, got {}", axis, dimension);
            throw new IllegalArgumentException("LDLib2 recipe UI " + axis + " must use a fixed pixel size");
        }
        return Math.round(dimension.getValue());
    }

    /**
     * Auto layout UI template for recipes.
     */
    public IEditableUI<WidgetGroup, RecipeHolder> createEditableUITemplate(final boolean isSteam,
                                                                           final boolean isHighPressure) {
        return new IEditableUI.Normal<>(() -> {
            var inputs = addInventorySlotGroup(false, isSteam, isHighPressure);
            var outputs = addInventorySlotGroup(true, isSteam, isHighPressure);
            var maxWidth = Math.max(inputs.getSize().width, outputs.getSize().width);
            var group = new WidgetGroup(0, 0, 2 * maxWidth + 40,
                    Math.max(inputs.getSize().height, outputs.getSize().height));
            var size = group.getSize();

            inputs.addSelfPosition((maxWidth - inputs.getSize().width) / 2,
                    (size.height - inputs.getSize().height) / 2);
            outputs.addSelfPosition(maxWidth + 40 + (maxWidth - outputs.getSize().width) / 2,
                    (size.height - outputs.getSize().height) / 2);
            group.addWidget(inputs);
            group.addWidget(outputs);

            var progressWidget = new ProgressWidget(XEI_PROGRESS, maxWidth + 10, size.height / 2 - 10, 20,
                    20, progressBarTexture);
            progressWidget.setId("progress");
            group.addWidget(progressWidget);

            progressWidget.setProgressTexture(getProgressTexture(isSteam, isHighPressure));

            return group;
        }, (template, recipeHolder) -> {
            var isJEI = recipeHolder.progressSupplier == XEI_PROGRESS;

            // bind progress
            List<Widget> progress = new ArrayList<>();
            // First set the progress suppliers separately.
            WidgetUtils.widgetByIdForEach(template, "^progress$", ProgressWidget.class, progressWidget -> {
                progressWidget.setProgressSupplier(recipeHolder.progressSupplier);
                progress.add(progressWidget);
            });
            // Then set the dual-progress widgets, to override their builtin ones' suppliers, in case someone forgot to
            // remove the id from the internal ones.
            WidgetUtils.widgetByIdForEach(template, "^progress$", DualProgressWidget.class, dualProgressWidget -> {
                dualProgressWidget.setProgressSupplier(recipeHolder.progressSupplier);
                progress.add(dualProgressWidget);
            });
            // add recipe button
            if (!isJEI && GTCEu.Mods.isAnyRecipeViewerLoaded()) {
                for (Widget widget : progress) {
                    template.addWidget(new ButtonWidget(widget.getPosition().x, widget.getPosition().y,
                            widget.getSize().width, widget.getSize().height, IGuiTexture.EMPTY, cd -> {
                                if (cd.isRemote) {
                                    if (GTCEu.Mods.isEMILoaded()) {
                                        EmiApi.displayRecipeCategory(
                                                GTRecipeEMICategory.machineCategory(recipeType.getCategory()));
                                    } else if (GTCEu.Mods.isJEILoaded()) {
                                        GTJEIPlugin.jeiRuntime.getRecipesGui().showTypes(
                                                recipeType.getCategories().stream()
                                                        .filter(GTRecipeCategory::isXEIVisible)
                                                        .map(GTLDLib2RecipeJEICategory::machineType)
                                                        .collect(Collectors.toList()));
                                    }
                                }
                            }).setHoverTooltips("gtpm.recipe_type.show_recipes"));
                }
            }

            // Bind I/O
            for (var capabilityEntry : recipeHolder.storages.rowMap().entrySet()) {
                IO io = capabilityEntry.getKey();
                for (var storagesEntry : capabilityEntry.getValue().entrySet()) {
                    RecipeCapability<?> cap = storagesEntry.getKey();
                    Object storage = storagesEntry.getValue();
                    // bind overlays
                    var widgetClass = cap.getWidgetClass();
                    if (widgetClass != null) {
                        WidgetUtils.widgetByIdForEach(template, "^%s_[0-9]+$".formatted(cap.slotName(io)), widgetClass,
                                widget -> {
                                    var index = WidgetUtils.widgetIdIndex(widget);
                                    cap.applyWidgetInfo(widget, index, isJEI, io, recipeHolder, recipeType, null, null,
                                            storage, 0, 0);
                                });
                    }
                }
            }
        });
    }

    private UI createDefaultLDLib2UI(boolean isSteam, boolean isHighPressure) {
        var inputs = addLDLib2InventorySlotGroup(false, isSteam, isHighPressure);
        var outputs = addLDLib2InventorySlotGroup(true, isSteam, isHighPressure);
        var maxWidth = Math.max(inputs.width(), outputs.width());
        var width = 2 * maxWidth + 40;
        var height = Math.max(inputs.height(), outputs.height());
        var root = new UIElement();
        root.layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.positionType(TaffyPosition.ABSOLUTE);
        });

        inputs.element().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left((maxWidth - inputs.width()) / 2f);
            layout.top((height - inputs.height()) / 2f);
        });
        outputs.element().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(maxWidth + 40 + (maxWidth - outputs.width()) / 2f);
            layout.top((height - outputs.height()) / 2f);
        });
        root.addChildren(inputs.element(), outputs.element());

        var progress = new GTProgressBarElement();
        progress.setId("progress");
        progress.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(maxWidth + 10);
            layout.top(height / 2f - 10);
            layout.width(20);
            layout.height(20);
        });
        configureLDLib2ProgressTexture(progress, isSteam, isHighPressure);
        root.addChild(progress);
        return UI.of(root);
    }

    private void setupLDLib2UI(UIElement root, RecipeHolder recipeHolder) {
        var isXEI = recipeHolder.progressSupplier == XEI_PROGRESS;
        List<UIElement> progressElements = new ArrayList<>();
        root.selectId("progress", GTProgressBarElement.class)
                .forEach(progress -> {
                    progress.setProgressSupplier(recipeHolder.progressSupplier);
                    progressElements.add(progress);
                });
        root.selectId("progress", GTDualProgressElement.class)
                .forEach(progress -> {
                    progress.setProgressSupplier(recipeHolder.progressSupplier);
                    progressElements.add(progress);
                });

        if (!isXEI && GTCEu.Mods.isAnyRecipeViewerLoaded()) {
            progressElements.forEach(this::addLDLib2RecipeViewerButton);
        }

        for (var capabilityEntry : recipeHolder.storages.rowMap().entrySet()) {
            IO io = capabilityEntry.getKey();
            for (var storagesEntry : capabilityEntry.getValue().entrySet()) {
                RecipeCapability<?> cap = storagesEntry.getKey();
                Object storage = storagesEntry.getValue();
                var elementClass = cap.getLDLib2ElementClass();
                if (elementClass != null) {
                    root.selectRegex("^%s_[0-9]+$".formatted(cap.slotName(io)), elementClass)
                            .forEach(element -> {
                                var index = ldLib2ElementIdIndex(element);
                                cap.applyLDLib2ElementInfo(element, index, isXEI, io, recipeHolder, recipeType, null,
                                        null, storage, 0, 0);
                            });
                }
            }
        }
    }

    private void addLDLib2RecipeViewerButton(UIElement progress) {
        GTButtonElement button = new GTButtonElement();
        button.noText();
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
        button.buttonStyle(style -> style
                .baseTexture(IGuiTexture.EMPTY)
                .hoverTexture(IGuiTexture.EMPTY)
                .pressedTexture(IGuiTexture.EMPTY));
        button.setOnClick(event -> showRecipeViewerCategory());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable("gtpm.recipe_type.show_recipes")), null, null, null));
        progress.addChild(button);
    }

    private void showRecipeViewerCategory() {
        if (GTCEu.Mods.isEMILoaded()) {
            EmiApi.displayRecipeCategory(GTRecipeEMICategory.machineCategory(recipeType.getCategory()));
        } else if (GTCEu.Mods.isJEILoaded()) {
            GTJEIPlugin.jeiRuntime.getRecipesGui().showTypes(
                    recipeType.getCategories().stream()
                            .filter(GTRecipeCategory::isXEIVisible)
                            .map(GTLDLib2RecipeJEICategory::machineType)
                            .collect(Collectors.toList()));
        }
    }

    public void applyLDLib2RecipeContent(UI ui,
                                         Table<IO, RecipeCapability<?>, List<Content>> contentTable,
                                         GTRecipeDefinition recipe,
                                         int recipeTier,
                                         int chanceTier) {
        applyLDLib2RecipeContent(ui.rootElement, contentTable, recipe, recipeTier, chanceTier);
    }

    public void applyLDLib2RecipeContent(UIElement root,
                                         Table<IO, RecipeCapability<?>, List<Content>> contentTable,
                                         GTRecipeDefinition recipe,
                                         int recipeTier,
                                         int chanceTier) {
        for (var capabilityEntry : contentTable.rowMap().entrySet()) {
            IO io = capabilityEntry.getKey();
            for (var contentsEntry : capabilityEntry.getValue().entrySet()) {
                RecipeCapability<?> cap = contentsEntry.getKey();
                var elementClass = cap.getLDLib2ElementClass();
                if (elementClass == null) {
                    continue;
                }
                List<Content> contents = contentsEntry.getValue();
                int nonTickCount = (io == IO.IN ? recipe.getInputContents(cap) : recipe.getOutputContents(cap))
                        .size();
                root.selectRegex("^%s_[0-9]+$".formatted(cap.slotName(io)), elementClass)
                        .forEach(element -> {
                            int index = ldLib2ElementIdIndex(element);
                            if (index >= 0 && index < contents.size()) {
                                Content content = contents.get(index);
                                cap.applyLDLib2ElementInfo(element, index, true, io, null, recipe.getType(), recipe,
                                        content, null, recipeTier, chanceTier);
                                setLDLib2ContentOverlay(element,
                                        content.createOverlay(index >= nonTickCount, recipeTier, chanceTier,
                                                recipe.getType().getChanceFunction()));
                            }
                        });
            }
        }
    }

    private LDLib2ElementGroup addLDLib2InventorySlotGroup(boolean isOutputs, boolean isSteam,
                                                           boolean isHighPressure) {
        int maxCount = 0;
        int totalR = 0;
        Object2IntSortedMap<RecipeCapability<?>> map = new Object2IntAVLTreeMap<>(RecipeCapability.COMPARATOR);
        if (isOutputs) {
            for (var value : recipeType.maxOutputs.object2IntEntrySet()) {
                if (value.getKey().doRenderSlot) {
                    int val = value.getIntValue();
                    if (val > maxCount) {
                        maxCount = Math.min(val, 3);
                    }
                    totalR += (val + 2) / 3;
                    map.put(value.getKey(), val);
                }
            }
        } else {
            for (var value : recipeType.maxInputs.object2IntEntrySet()) {
                if (value.getKey().doRenderSlot) {
                    int val = value.getIntValue();
                    if (val > maxCount) {
                        maxCount = Math.min(val, 3);
                    }
                    totalR += (val + 2) / 3;
                    map.put(value.getKey(), val);
                }
            }
        }
        int width = maxCount * 18 + 8;
        int height = totalR * 18 + 8;
        UIElement group = new UIElement();
        group.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(width);
            layout.height(height);
        });
        int index = 0;
        for (var entry : map.object2IntEntrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            int capCount = entry.getIntValue();
            for (int slotIndex = 0; slotIndex < capCount; slotIndex++) {
                var slot = cap.createLDLib2Element();
                if (slot == null) {
                    continue;
                }
                int slotX = (index % 3) * 18 + 4;
                int slotY = (index / 3) * 18 + 4;
                slot.layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.left(slotX);
                    layout.top(slotY);
                    layout.width(18);
                    layout.height(18);
                });
                setLDLib2SlotBackground(slot,
                        getOverlaysForSlot(isOutputs, cap, slotIndex == capCount - 1, isSteam, isHighPressure));
                slot.setId(cap.slotName(isOutputs ? IO.OUT : IO.IN, slotIndex));
                group.addChild(slot);
                index++;
            }
            index += (3 - (index % 3)) % 3;
        }
        return new LDLib2ElementGroup(group, width, height);
    }

    private void configureLDLib2ProgressTexture(GTProgressBarElement progress, boolean isSteam,
                                                boolean isHighPressure) {
        ProgressTexture texture = getProgressTexture(isSteam, isHighPressure);
        progress.barBackground.style(style -> style.backgroundTexture(texture.getEmptyBarArea()));
        progress.bar.style(style -> style.backgroundTexture(texture.getFilledBarArea()));
        progress.progressBarStyle(style -> style.fillDirection(toLDLib2FillDirection(texture.getFillDirection())));
    }

    private ProgressTexture getProgressTexture(boolean isSteam, boolean isHighPressure) {
        if (!isSteam || steamProgressBarTexture == null) {
            return progressBarTexture;
        }
        IGuiTexture steamTexture = steamProgressBarTexture.get(isHighPressure);
        if (steamTexture instanceof ResourceTexture resourceTexture) {
            return GuiTextures.progressBar(resourceTexture, steamMoveType);
        }
        GTCEu.LOGGER.error("Steam progress bar texture must be a resource texture, got {}",
                steamTexture.getClass().getName());
        throw new IllegalArgumentException("Unsupported steam progress bar texture: " +
                steamTexture.getClass().getName());
    }

    private static FillDirection toLDLib2FillDirection(ProgressTexture.FillDirection fillDirection) {
        return switch (fillDirection) {
            case RIGHT_TO_LEFT -> FillDirection.RIGHT_TO_LEFT;
            case UP_TO_DOWN -> FillDirection.UP_TO_DOWN;
            case DOWN_TO_UP -> FillDirection.DOWN_TO_UP;
            case LEFT_TO_RIGHT, ALWAYS_FULL -> FillDirection.LEFT_TO_RIGHT;
        };
    }

    private static void setLDLib2SlotBackground(UIElement element, IGuiTexture texture) {
        if (element instanceof GTItemSlotElement slot) {
            slot.setBackgroundTexture(texture);
        } else if (element instanceof GTFluidSlotElement tank) {
            tank.setBackgroundTexture(texture);
        } else {
            element.getStyle().backgroundTexture(texture);
        }
    }

    private static void setLDLib2ContentOverlay(UIElement element, IGuiTexture texture) {
        if (element instanceof GTItemSlotElement slot) {
            slot.setContentOverlay(texture);
        } else if (element instanceof GTFluidSlotElement tank) {
            tank.setContentOverlay(texture);
        } else {
            GTCEu.LOGGER.error("LDLib2 recipe element {} does not support content overlays",
                    element.getClass().getName());
            throw new IllegalArgumentException("Unsupported LDLib2 recipe element: " + element.getClass().getName());
        }
    }

    private static int ldLib2ElementIdIndex(UIElement element) {
        var id = element.getId();
        var separator = id.lastIndexOf('_');
        if (separator < 0 || separator == id.length() - 1) {
            GTCEu.LOGGER.error("Invalid LDLib2 recipe element id '{}'", id);
            throw new IllegalArgumentException("Invalid LDLib2 recipe element id: " + id);
        }
        try {
            return Integer.parseInt(id.substring(separator + 1));
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid LDLib2 recipe element id '{}'", id, e);
            throw e;
        }
    }

    private record LDLib2ElementGroup(UIElement element, int width, int height) {}

    protected WidgetGroup addInventorySlotGroup(boolean isOutputs, boolean isSteam, boolean isHighPressure) {
        int maxCount = 0;
        int totalR = 0;
        Object2IntSortedMap<RecipeCapability<?>> map = new Object2IntAVLTreeMap<>(RecipeCapability.COMPARATOR);
        if (isOutputs) {
            for (var value : recipeType.maxOutputs.object2IntEntrySet()) {
                if (value.getKey().doRenderSlot) {
                    int val = value.getIntValue();
                    if (val > maxCount) {
                        maxCount = Math.min(val, 3);
                    }
                    totalR += (val + 2) / 3;
                    map.put(value.getKey(), val);
                }
            }
        } else {
            for (var value : recipeType.maxInputs.object2IntEntrySet()) {
                if (value.getKey().doRenderSlot) {
                    int val = value.getIntValue();
                    if (val > maxCount) {
                        maxCount = Math.min(val, 3);
                    }
                    totalR += (val + 2) / 3;
                    map.put(value.getKey(), val);
                }
            }
        }
        WidgetGroup group = new WidgetGroup(0, 0, maxCount * 18 + 8, totalR * 18 + 8);
        int index = 0;
        for (var entry : map.object2IntEntrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            var widgetClass = cap.getWidgetClass();
            if (widgetClass == null) {
                continue;
            }
            int capCount = entry.getIntValue();
            for (int slotIndex = 0; slotIndex < capCount; slotIndex++) {
                var slot = cap.createWidget();
                // noinspection DataFlowIssue
                slot.setSelfPosition(new Position((index % 3) * 18 + 4, (index / 3) * 18 + 4));
                slot.setBackground(
                        getOverlaysForSlot(isOutputs, cap, slotIndex == capCount - 1, isSteam, isHighPressure));
                slot.setId(cap.slotName(isOutputs ? IO.OUT : IO.IN, slotIndex));
                group.addWidget(slot);
                index++;
            }
            // move to new row
            index += (3 - (index % 3)) % 3;
        }
        return group;
    }

    protected IGuiTexture getOverlaysForSlot(boolean isOutput, RecipeCapability<?> capability, boolean isLast,
                                             boolean isSteam, boolean isHighPressure) {
        IGuiTexture base = capability == FluidRecipeCapability.CAP ? GuiTextures.FLUID_SLOT :
                (isSteam ? GuiTextures.SLOT_STEAM.get(isHighPressure) : GuiTextures.SLOT);
        byte overlayKey = (byte) ((isOutput ? 2 : 0) + (capability == FluidRecipeCapability.CAP ? 1 : 0) +
                (isLast ? 4 : 0));
        if (slotOverlays.containsKey(overlayKey)) {
            return GuiTextures.group(base, slotOverlays.get(overlayKey));
        }
        return base;
    }

    /**
     * @return the height used to determine size of background texture in JEI
     */
    public int getPropertyHeightShift() {
        int maxPropertyCount = maxTooltips + recipeType.getDataInfos().size() + recipeType.getMinRecipeConditions();
        return maxPropertyCount * 10; // GTRecipeXEIHelper#LINE_HEIGHT
    }

    public void appendLDLib2XEIUI(GTRecipeDefinition recipe, UIElement root) {
        appendLDLib2XEIUI(recipe, root, getLDLib2XEIRecipeUISize());
    }

    public void appendLDLib2XEIUI(GTRecipeDefinition recipe, UIElement root, LDLib2RecipeUISize rootSize) {
        if (ldLib2UiBuilder != null) {
            ldLib2UiBuilder.accept(recipe, root, rootSize);
        }
    }

    public GTRecipeTypeUI setSlotOverlay(boolean isOutput, boolean isFluid, IGuiTexture slotOverlay) {
        return this.setSlotOverlay(isOutput, isFluid, false, slotOverlay).setSlotOverlay(isOutput, isFluid, true,
                slotOverlay);
    }

    public GTRecipeTypeUI setSlotOverlay(boolean isOutput, boolean isFluid, boolean isLast, IGuiTexture slotOverlay) {
        this.slotOverlays.put((byte) ((isOutput ? 2 : 0) + (isFluid ? 1 : 0) + (isLast ? 4 : 0)), slotOverlay);
        return this;
    }

    public GTRecipeTypeUI setProgressBar(ProgressTexture progressBar) {
        this.progressBarTexture = progressBar;
        return this;
    }
}
