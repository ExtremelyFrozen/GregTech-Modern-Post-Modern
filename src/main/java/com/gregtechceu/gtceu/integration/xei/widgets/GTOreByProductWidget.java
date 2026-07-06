package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidEntryList;
import com.gregtechceu.gtceu.integration.xei.entry.item.ItemEntryList;
import com.gregtechceu.gtceu.integration.xei.handlers.fluid.CycleFluidEntryHandler;
import com.gregtechceu.gtceu.integration.xei.handlers.item.CycleItemEntryHandler;
import com.gregtechceu.gtceu.integration.xei.GTXEIHelper;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import dev.vfyjxf.taffy.style.TaffyPosition;

public class GTOreByProductWidget {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 166;
    public static final int JEI_WIDTH = 186;
    public static final int JEI_HEIGHT = 174;

    private static final int SLOT_SIZE = 18;

    // XY positions of every item and fluid, in three enormous lists
    protected final static IntImmutableList ITEM_INPUT_LOCATIONS = IntImmutableList.of(
            3, 3,       // ore
            23, 3,      // furnace (direct smelt)
            3, 24,      // macerator (ore -> crushed)
            23, 71,     // macerator (crushed -> impure)
            50, 80,     // centrifuge (impure -> dust)
            24, 25,     // ore washer
            97, 71,     // thermal centrifuge
            70, 80,     // macerator (centrifuged -> dust)
            114, 48,    // macerator (crushed purified -> purified)
            133, 71,    // centrifuge (purified -> dust)
            3, 123,     // cauldron / simple washer (crushed)
            41, 145,    // cauldron (impure)
            102, 145,   // cauldron (purified)
            24, 48,     // chem bath
            155, 71,    // electro separator
            101, 25     // sifter
    );

    protected final static IntImmutableList ITEM_OUTPUT_LOCATIONS = IntImmutableList.of(
            46, 3,      // smelt result: 0
            3, 47,      // ore -> crushed: 2
            3, 65,      // byproduct: 4
            23, 92,     // crushed -> impure: 6
            23, 110,    // byproduct: 8
            50, 101,    // impure -> dust: 10
            50, 119,    // byproduct: 12
            64, 25,     // crushed -> crushed purified (wash): 14
            82, 25,     // byproduct: 16
            97, 92,     // crushed/crushed purified -> centrifuged: 18
            97, 110,    // byproduct: 20
            70, 101,    // centrifuged -> dust: 22
            70, 119,    // byproduct: 24
            137, 47,    // crushed purified -> purified: 26
            155, 47,    // byproduct: 28
            133, 92,    // purified -> dust: 30
            133, 110,   // byproduct: 32
            3, 105,     // crushed cauldron: 34
            3, 145,     // -> purified crushed: 36
            23, 145,    // impure cauldron: 38
            63, 145,    // -> dust: 40
            84, 145,    // purified cauldron: 42
            124, 145,   // -> dust: 44
            64, 48,     // crushed -> crushed purified (chem bath): 46
            82, 48,     // byproduct: 48
            155, 92,    // purified -> dust (electro separator): 50
            155, 110,   // byproduct 1: 52
            155, 128,   // byproduct 2: 54
            119, 3,     // sifter outputs... : 56
            137, 3,     // 58
            155, 3,     // 60
            119, 21,    // 62
            137, 21,    // 64
            155, 21     // 66
    );

    protected final static IntImmutableList FLUID_LOCATIONS = IntImmutableList.of(
            42, 25, // washer in
            42, 48  // chem bath in
    );

    // Used to set intermediates as both input and output
    protected final static IntSet FINAL_OUTPUT_INDICES = IntSet.of(
            0, 4, 8, 10, 12, 16, 20, 22, 24, 28, 30, 32, 40, 44, 48, 50, 52, 54, 56, 58, 60, 62, 64, 66);

    private final Material material;

    public GTOreByProductWidget(Material material) {
        this.material = material;
    }

    public static ModularUI createModularUI(Material material) {
        return ModularUI.of(new GTOreByProductWidget(material).createUI());
    }

    public UI createUI() {
        UIElement root = new UIElement();
        root.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(WIDTH);
            layout.height(HEIGHT);
        });
        setRecipe(root, new GTOreByProduct(material));
        return UI.of(root);
    }

    private static void setRecipe(UIElement root, GTOreByProduct recipeWrapper) {
        BooleanList itemOutputExists = new BooleanArrayList();
        boolean hasSifter = recipeWrapper.hasSifter();

        root.addChild(createTextureElement(0, 0, WIDTH, HEIGHT, GuiTextures.OREBY_BASE));
        if (recipeWrapper.hasDirectSmelt()) {
            root.addChild(createTextureElement(0, 0, WIDTH, HEIGHT, GuiTextures.OREBY_SMELT));
        }
        if (recipeWrapper.hasChemBath()) {
            root.addChild(createTextureElement(0, 0, WIDTH, HEIGHT, GuiTextures.OREBY_CHEM));
        }
        if (recipeWrapper.hasSeparator()) {
            root.addChild(createTextureElement(0, 0, WIDTH, HEIGHT, GuiTextures.OREBY_SEP));
        }
        if (hasSifter) {
            root.addChild(createTextureElement(0, 0, WIDTH, HEIGHT, GuiTextures.OREBY_SIFT));
        }
        root.addChild(createTextureElement(ITEM_INPUT_LOCATIONS.getInt(0), ITEM_INPUT_LOCATIONS.getInt(1),
                SLOT_SIZE, SLOT_SIZE, GuiTextures.SLOT));

        List<ItemEntryList> itemInputs = recipeWrapper.itemInputs;
        CycleItemEntryHandler itemInputsHandler = new CycleItemEntryHandler(itemInputs);
        UIElement itemStackGroup = createAbsoluteGroup();
        for (int i = 0; i < ITEM_INPUT_LOCATIONS.size(); i += 2) {
            int slotIndex = i / 2;
            itemStackGroup.addChild(createInputSlot(recipeWrapper, itemInputsHandler, slotIndex,
                    ITEM_INPUT_LOCATIONS.getInt(i), ITEM_INPUT_LOCATIONS.getInt(i + 1)));
        }

        NonNullList<ItemStack> itemOutputs = recipeWrapper.itemOutputs;
        CustomItemStackHandler itemOutputsHandler = new CustomItemStackHandler(itemOutputs);
        UIElement outputBackgrounds = createAbsoluteGroup();
        for (int i = 0; i < ITEM_OUTPUT_LOCATIONS.size(); i += 2) {
            int slotIndex = i / 2;
            boolean outputExists = !itemOutputs.get(slotIndex).isEmpty();
            itemOutputExists.add(outputExists);
            if (!outputExists) {
                continue;
            }

            int x = ITEM_OUTPUT_LOCATIONS.getInt(i);
            int y = ITEM_OUTPUT_LOCATIONS.getInt(i + 1);
            outputBackgrounds.addChild(createTextureElement(x, y, SLOT_SIZE, SLOT_SIZE, GuiTextures.SLOT));
            itemStackGroup.addChild(createOutputSlot(recipeWrapper, itemOutputsHandler, slotIndex, itemInputs.size(),
                    i, x, y));
        }
        addSifterOutputBackgrounds(outputBackgrounds, itemOutputExists, hasSifter);

        List<FluidEntryList> fluidInputs = recipeWrapper.fluidInputs;
        CycleFluidEntryHandler fluidInputsHandler = new CycleFluidEntryHandler(fluidInputs);
        UIElement fluidStackGroup = createAbsoluteGroup();
        for (int i = 0; i < FLUID_LOCATIONS.size(); i += 2) {
            int slotIndex = i / 2;
            if (!fluidInputs.get(slotIndex).isEmpty()) {
                fluidStackGroup.addChild(createFluidInputSlot(fluidInputsHandler, slotIndex,
                        FLUID_LOCATIONS.getInt(i), FLUID_LOCATIONS.getInt(i + 1)));
            }
        }

        root.addChild(outputBackgrounds);
        root.addChild(itemStackGroup);
        root.addChild(fluidStackGroup);
    }

    private static UIElement createAbsoluteGroup() {
        UIElement group = new UIElement();
        group.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(WIDTH);
            layout.height(HEIGHT);
        });
        return group;
    }

    private static UIElement createTextureElement(int x, int y, int width, int height, IGuiTexture texture) {
        UIElement element = new UIElement();
        element.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(width);
            layout.height(height);
        });
        element.getStyle().backgroundTexture(texture);
        return element;
    }

    private static GTItemSlotElement createInputSlot(GTOreByProduct recipeWrapper, CycleItemEntryHandler handler,
                                                     int slotIndex, int x, int y) {
        Supplier<Stream<ItemStack>> stackSupplier = () -> handler.getEntry(slotIndex).getStacks().stream()
                .filter(stack -> !stack.isEmpty());
        GTItemSlotElement slot = new GTItemSlotElement(handler, slotIndex);
        layoutSlot(slot, x, y);
        slot.setBackgroundTexture(IGuiTexture.EMPTY);
        slot.setCanTakeItems(false);
        slot.setCanPutItems(false);
        slot.setOnAddedTooltips((element, tooltips) -> recipeWrapper.getTooltip(slotIndex, tooltips));
        slot.xeiRecipeSlot(GTXEIHelper.input(), 1.0f, 1, stackSupplier);
        slot.xeiRecipeIngredient(GTXEIHelper.input(), stackSupplier);
        slot.setIngredientIO(GTXEIHelper.input());
        return slot;
    }

    private static GTItemSlotElement createOutputSlot(GTOreByProduct recipeWrapper, CustomItemStackHandler handler,
                                                      int slotIndex, int itemInputCount, int locationIndex, int x,
                                                      int y) {
        Content chance = recipeWrapper.getChance(slotIndex + itemInputCount);
        float xeiChance = 1.0f;
        IGuiTexture overlay = IGuiTexture.EMPTY;
        if (chance != null) {
            xeiChance = (float) chance.chance / chance.maxChance;
            overlay = chance.createOverlay(false, 0, 0, null);
        }

        Supplier<Stream<ItemStack>> stackSupplier = () -> Stream.of(handler.getStackInSlot(slotIndex))
                .filter(stack -> !stack.isEmpty());
        GTItemSlotElement slot = new GTItemSlotElement(handler, slotIndex);
        layoutSlot(slot, x, y);
        slot.setBackgroundTexture(IGuiTexture.EMPTY);
        slot.setCanTakeItems(false);
        slot.setCanPutItems(false);
        slot.setXEIChance(xeiChance);
        slot.setContentOverlay(overlay);
        slot.setOnAddedTooltips((element, tooltips) -> recipeWrapper.getTooltip(slotIndex + itemInputCount, tooltips));
        slot.xeiRecipeSlot(GTXEIHelper.output(), xeiChance, 1, stackSupplier);
        slot.xeiRecipeIngredient(GTXEIHelper.output(), stackSupplier);
        if (!FINAL_OUTPUT_INDICES.contains(locationIndex)) {
            slot.xeiRecipeIngredient(GTXEIHelper.input(), stackSupplier);
        }
        slot.setIngredientIO(GTXEIHelper.output());
        return slot;
    }

    private static GTFluidSlotElement createFluidInputSlot(CycleFluidEntryHandler handler, int slotIndex, int x, int y) {
        Supplier<Stream<FluidStack>> fluidSupplier = () -> handler.getEntry(slotIndex).getStacks().stream()
                .filter(fluid -> !fluid.isEmpty());
        GTFluidSlotElement slot = new GTFluidSlotElement();
        slot.setFluidTank(handler, slotIndex);
        slot.setIngredientIO(GTXEIHelper.input());
        slot.setBackgroundTexture(GuiTextures.FLUID_SLOT);
        slot.setShowAmount(false);
        slot.setAllowClickFilled(false);
        slot.setAllowClickDrained(false);
        slot.xeiRecipeSlot(GTXEIHelper.input(), 1.0f, 1, fluidSupplier);
        slot.xeiRecipeIngredient(GTXEIHelper.input(), fluidSupplier);
        layoutSlot(slot, x, y);
        return slot;
    }

    private static void addSifterOutputBackgrounds(UIElement outputBackgrounds, BooleanList itemOutputExists,
                                                   boolean hasSifter) {
        if (!hasSifter || !itemOutputExists.getBoolean(28)) {
            return;
        }
        for (int i = 29 * 2; i < ITEM_OUTPUT_LOCATIONS.size(); i += 2) {
            if (!itemOutputExists.getBoolean(i / 2)) {
                outputBackgrounds.addChild(createTextureElement(ITEM_OUTPUT_LOCATIONS.getInt(i),
                        ITEM_OUTPUT_LOCATIONS.getInt(i + 1), SLOT_SIZE, SLOT_SIZE, GuiTextures.SLOT));
            }
        }
    }

    private static void layoutSlot(UIElement slot, int x, int y) {
        slot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
        });
    }
}
