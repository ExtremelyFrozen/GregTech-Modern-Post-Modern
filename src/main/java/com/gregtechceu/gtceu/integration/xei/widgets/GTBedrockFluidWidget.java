package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.TextTexture;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.recipe.condition.DimensionCondition;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import dev.vfyjxf.taffy.style.TaffyPosition;

public class GTBedrockFluidWidget {

    public static final int WIDTH = 120;
    public static final int JEI_HEIGHT = 120;
    public static final int HEIGHT = 140;

    private static final int TEXT_X = 5;
    private static final int TITLE_Y = 0;
    private static final int TITLE_HEIGHT = 16;
    private static final int FLUID_SLOT_X = 51;
    private static final int FLUID_SLOT_Y = 18;
    private static final int SLOT_SIZE = 18;
    private static final int DIMENSION_Y = 80;
    private static final int DIMENSION_SLOT_SIZE = 16;
    private static final int DIMENSION_INTERVAL = 2;
    private static final int LINE_HEIGHT = 10;

    private final Holder<BedrockFluidDefinition> fluid;
    private final int height;

    public GTBedrockFluidWidget(Holder<BedrockFluidDefinition> fluid, int height) {
        this.fluid = fluid;
        this.height = height;
    }

    public static ModularUI createModularUI(Holder<BedrockFluidDefinition> fluid, int height) {
        return ModularUI.of(new GTBedrockFluidWidget(fluid, height).createUI());
    }

    public UI createUI() {
        UIElement root = new UIElement();
        root.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(WIDTH);
            layout.height(height);
        });

        BedrockFluidDefinition definition = fluid.value();
        root.addChild(createTitle());
        root.addChild(createFluidSlot(definition));
        root.addChild(createLabel("gtpm.jei.bedrock_vein_diagram.yield", veinYield(definition), 40));
        root.addChild(createLabel("gtpm.jei.bedrock_vein_diagram.depleted", depletion(definition), 50));
        root.addChild(createLabel("gtpm.jei.ore_vein_diagram.weight", definition.getWeight(), 60));
        root.addChild(createLabel(Component.literal(
                LocalizationUtils.format("gtpm.jei.ore_vein_diagram.dimensions")), 70));
        setupDimensionMarkers(root, definition.getDimensionFilter());
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
        title.getStyle().backgroundTexture(GuiTextures.text(getFluidName(fluid))
                .setType(TextTexture.TextType.LEFT_ROLL)
                .setWidth(WIDTH - 2 * TEXT_X));
        return title;
    }

    private GTFluidSlotElement createFluidSlot(BedrockFluidDefinition definition) {
        Fluid storedFluid = definition.getStoredFluid();
        FluidStack stack = new FluidStack(storedFluid, 1000);
        CustomFluidTank tank = new CustomFluidTank(stack);
        GTFluidSlotElement slot = new GTFluidSlotElement();
        slot.setFluidTank(tank, 0);
        slot.setIngredientIO(IngredientIO.OUTPUT);
        slot.setBackgroundTexture(GuiTextures.FLUID_SLOT);
        slot.setShowAmount(false);
        slot.setAllowClickFilled(false);
        slot.setAllowClickDrained(false);
        slot.setXEIRecipeSlot(IngredientIO.OUTPUT, 1.0f, 1, () -> Stream.of(stack));
        slot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(FLUID_SLOT_X);
            layout.top(FLUID_SLOT_Y);
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
        });
        return slot;
    }

    private static Label createLabel(String translationKey, Object value, int y) {
        return createLabel(Component.literal(LocalizationUtils.format(translationKey, value)), y);
    }

    private static Label createLabel(Component text, int y) {
        Label label = new Label();
        label.setValue(text);
        label.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(TEXT_X);
            layout.top(y);
            layout.width(WIDTH - 2 * TEXT_X);
            layout.height(LINE_HEIGHT);
        });
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
        var handler = new CustomItemStackHandler(dimMarkers.length);
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
        slot.xeiRecipeSlot(IngredientIO.CATALYST, 1.0f, 1, stackSupplier);
        slot.xeiRecipeIngredient(IngredientIO.CATALYST, stackSupplier);
        slot.setIngredientIO(IngredientIO.CATALYST);
        if (ConfigHolder.INSTANCE.compat.showDimensionTier) {
            slot.setContentOverlay(GuiTextures.text("T" +
                            (dimMarker.tier >= DimensionMarker.MAX_TIER ? "?" : dimMarker.tier))
                    .scale(0.75f)
                    .transform(-3.0f, 5.0f));
        }
        return slot;
    }

    private static String veinYield(BedrockFluidDefinition definition) {
        return String.format("%d - %dmB/s", definition.getMinimumYield(), definition.getMaximumYield());
    }

    private static String depletion(BedrockFluidDefinition definition) {
        return String.format("%dmB/s", definition.getDepletedYield());
    }

    private static String getFluidName(Holder<BedrockFluidDefinition> fluid) {
        return fluid.getKey()
                .location()
                .toLanguageKey("bedrock_fluid");
    }
}
