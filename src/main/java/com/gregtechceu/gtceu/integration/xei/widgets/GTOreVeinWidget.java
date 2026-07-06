package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.TextTexture;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.recipe.condition.DimensionCondition;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.xei.GTXEIHelper;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;

import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import dev.vfyjxf.taffy.style.TaffyPosition;

public class GTOreVeinWidget {

    public static final int WIDTH = 120;
    public static final int JEI_HEIGHT = 120;
    public static final int HEIGHT = 160;

    private static final int TEXT_X = 5;
    private static final int TITLE_Y = 0;
    private static final int TITLE_HEIGHT = 16;
    private static final int SLOT_Y = 18;
    private static final int SLOT_SIZE = 18;
    private static final int DIMENSION_Y = 80;
    private static final int DIMENSION_SLOT_SIZE = 16;
    private static final int DIMENSION_INTERVAL = 2;
    private static final int LINE_HEIGHT = 10;

    private final Holder<GTOreDefinition> ore;
    private final int height;

    public GTOreVeinWidget(Holder<GTOreDefinition> ore, int height) {
        this.ore = ore;
        this.height = height;
    }

    public static ModularUI createModularUI(Holder<GTOreDefinition> ore, int height) {
        return ModularUI.of(new GTOreVeinWidget(ore, height).createUI());
    }

    public UI createUI() {
        UIElement root = new UIElement();
        root.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(WIDTH);
            layout.height(height);
        });

        GTOreDefinition definition = ore.value();
        root.addChild(createTitle());
        setupOreSlots(root, definition);
        root.addChild(createLabel(Component.literal(
                LocalizationUtils.format("gtpm.jei.ore_vein_diagram.spawn_range")), 40));
        root.addChild(createLabel(Component.literal(range(definition)), 50));
        root.addChild(createLabel(Component.literal(
                LocalizationUtils.format("gtpm.jei.ore_vein_diagram.weight", definition.weight())), 60));
        root.addChild(createLabel(Component.literal(
                LocalizationUtils.format("gtpm.jei.ore_vein_diagram.dimensions")), 70));
        setupDimensionMarkers(root, definition.dimensionFilter());
        return UI.of(root);
    }

    private UIElement createTitle() {
        UIElement title = new UIElement();
        title.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(TEXT_X);
            layout.top(TITLE_Y);
            layout.width(WIDTH - 2 * TEXT_X);
            layout.height(TITLE_HEIGHT);
        });
        title.getStyle().backgroundTexture(GuiTextures.text(getOreName(ore))
                .setType(TextTexture.TextType.LEFT_ROLL)
                .setWidth(WIDTH - 2 * TEXT_X));
        return title;
    }

    private static void setupOreSlots(UIElement root, GTOreDefinition definition) {
        List<ItemStack> itemStacks = getRawMaterialList(definition);
        IntList chances = definition.veinGenerator().getAllChances();
        CustomItemStackHandler handler = new CustomItemStackHandler(itemStacks.size());
        int x = (WIDTH - SLOT_SIZE * itemStacks.size()) / 2;
        for (int i = 0; i < itemStacks.size(); i++) {
            handler.setStackInSlot(i, itemStacks.get(i));
            root.addChild(createOreSlot(handler, i, chances.getInt(i), x + SLOT_SIZE * i));
        }
    }

    private static GTItemSlotElement createOreSlot(CustomItemStackHandler handler, int index, int chance, int x) {
        Supplier<Stream<ItemStack>> stackSupplier = () -> Stream.of(handler.getStackInSlot(index));
        GTItemSlotElement slot = new GTItemSlotElement(handler, index);
        slot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(SLOT_Y);
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
        });
        slot.setBackgroundTexture(GuiTextures.SLOT);
        slot.setCanTakeItems(false);
        slot.setCanPutItems(false);
        slot.setOnAddedTooltips((element, tooltips) -> tooltips.add(
                Component.translatable("gtpm.jei.ore_vein_diagram.chance", chance)));
        slot.xeiRecipeSlot(GTXEIHelper.output(), 1.0f, 1, stackSupplier);
        slot.xeiRecipeIngredient(GTXEIHelper.output(), stackSupplier);
        slot.setIngredientIO(GTXEIHelper.output());
        return slot;
    }

    private static GTLabelElement createLabel(Component text, int y) {
        GTLabelElement label = new GTLabelElement(TEXT_X, y, WIDTH - 2 * TEXT_X, LINE_HEIGHT);
        label.setValue(text);
        return label;
    }

    private static void setupDimensionMarkers(UIElement root, Set<ResourceKey<Level>> dimensionFilter) {
        if (dimensionFilter == null) {
            root.addChild(createLabel(Component.literal("Any"), DIMENSION_Y));
            return;
        }

        int rowSlots = (WIDTH - 2 * TEXT_X + DIMENSION_INTERVAL) / (DIMENSION_SLOT_SIZE + DIMENSION_INTERVAL);
        DimensionMarker[] dimMarkers = dimensionFilter.stream()
                .map(dimension -> GTRegistries.DIMENSION_MARKERS.getOptional(dimension.location())
                        .orElseGet(() -> new DimensionMarker(DimensionMarker.MAX_TIER, () -> Blocks.BARRIER,
                                DimensionCondition.getDimensionName(dimension))))
                .sorted(Comparator.comparingInt(DimensionMarker::getTier))
                .toArray(DimensionMarker[]::new);
        CustomItemStackHandler handler = new CustomItemStackHandler(dimMarkers.length);
        for (int i = 0; i < dimMarkers.length; i++) {
            DimensionMarker dimMarker = dimMarkers[i];
            ItemStack icon = dimMarker.getIcon();
            handler.setStackInSlot(i, icon);
            root.addChild(createDimensionMarker(handler, i, dimMarker, rowSlots));
        }
    }

    private static GTItemSlotElement createDimensionMarker(CustomItemStackHandler handler, int index,
                                                           DimensionMarker dimMarker, int rowSlots) {
        int row = Math.floorDiv(index, rowSlots);
        int column = index - row * rowSlots;
        Supplier<Stream<ItemStack>> stackSupplier = () -> Stream.of(handler.getStackInSlot(index));
        GTItemSlotElement slot = new GTItemSlotElement(handler, index);
        slot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(TEXT_X + (DIMENSION_SLOT_SIZE + DIMENSION_INTERVAL) * column);
            layout.top(DIMENSION_Y + SLOT_SIZE * row);
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
        });
        slot.setBackgroundTexture(IGuiTexture.EMPTY);
        slot.setCanTakeItems(false);
        slot.setCanPutItems(false);
        slot.xeiRecipeSlot(GTXEIHelper.catalyst(), 1.0f, 1, stackSupplier);
        slot.xeiRecipeIngredient(GTXEIHelper.catalyst(), stackSupplier);
        slot.setIngredientIO(GTXEIHelper.catalyst());
        if (ConfigHolder.INSTANCE.compat.showDimensionTier) {
            slot.setContentOverlay(GuiTextures.text("T" +
                            (dimMarker.tier >= DimensionMarker.MAX_TIER ? "?" : dimMarker.tier))
                    .scale(0.75f)
                    .transform(-3.0f, 5.0f));
        }
        return slot;
    }

    @SuppressWarnings("DataFlowIssue")
    private static String range(GTOreDefinition definition) {
        HeightProvider heightProvider = definition.heightRange().height;
        int minHeight = 0;
        int maxHeight = 0;
        if (heightProvider instanceof UniformHeight uniformHeight) {
            minHeight = uniformHeight.minInclusive.resolveY(null);
            maxHeight = uniformHeight.maxInclusive.resolveY(null);
        }
        return String.format("%d - %d", minHeight, maxHeight);
    }

    public static List<ItemStack> getContainedOresAndBlocks(GTOreDefinition definition) {
        return definition.veinGenerator().getAllEntries().stream()
                .flatMap(entry -> entry.map(state -> Stream.of(state.getBlock().asItem().getDefaultInstance()),
                        material -> {
                            Set<ItemStack> ores = new HashSet<>();
                            ores.add(ChemicalHelper.get(TagPrefix.rawOre, material));
                            for (TagPrefix prefix : TagPrefix.ORES.keySet()) {
                                ores.add(ChemicalHelper.get(prefix, material));
                            }
                            return ores.stream();
                        }))
                .toList();
    }

    public static List<ItemStack> getRawMaterialList(GTOreDefinition definition) {
        return definition.veinGenerator().getAllEntries().stream()
                .map(entry -> entry.map(state -> state.getBlock().asItem().getDefaultInstance(),
                        material -> ChemicalHelper.get(TagPrefix.rawOre, material)))
                .toList();
    }

    public static String getOreName(Holder<GTOreDefinition> ore) {
        return ore.getKey()
                .location()
                .toLanguageKey("ore_vein");
    }
}
