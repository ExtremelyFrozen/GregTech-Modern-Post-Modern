package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.element.GTDualProgressElement;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.xei.GTLDLib2RecipeUI;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTRecipeTypeLDLib2UITest {

    private static final String BATCH = "GTRecipeTypeLDLib2UI";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void generatedTemplateParsesAndBindsStableRecipeElements(GameTestHelper helper) {
        GTRecipeTypeUI recipeUI = GTRecipeTypes.CANNER_RECIPES.getRecipeUI();
        UI documentUi = UI.of(recipeUI.createLDLib2TemplateDocument());
        UI ui = UI.of(RecipeUIXmlTemplate.parse(recipeUI.createLDLib2TemplateXml()));
        UIElement root = ui.getRootElement();

        GTRecipeTypeUI.LDLib2RecipeUISize size = GTRecipeTypeUI.getLDLib2RecipeUISize(root);
        helper.assertTrue(size.width() == 128 && size.height() == 44,
                "generated canner template did not preserve its fixed 128x44 layout");
        helper.assertTrue(size.equals(GTRecipeTypeUI.getLDLib2RecipeUISize(documentUi.getRootElement())),
                "document and serialized recipe template APIs produced different fixed dimensions");
        helper.assertTrue(root.selectRegex("^item_in_[0-9]+$", GTItemSlotElement.class).count() == 2,
                "generated canner template did not create both item inputs");
        helper.assertTrue(root.selectRegex("^item_out_[0-9]+$", GTItemSlotElement.class).count() == 2,
                "generated canner template did not create both item outputs");
        helper.assertTrue(root.selectRegex("^fluid_in_[0-9]+$", GTFluidSlotElement.class).count() == 1,
                "generated canner template did not create its fluid input");
        helper.assertTrue(root.selectRegex("^fluid_out_[0-9]+$", GTFluidSlotElement.class).count() == 1,
                "generated canner template did not create its fluid output");

        ItemStackHandler inputItems = new ItemStackHandler(2);
        ItemStackHandler outputItems = new ItemStackHandler(2);
        FluidTank inputFluid = new FluidTank(1_000);
        FluidTank outputFluid = new FluidTank(1_000);
        inputItems.setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
        inputFluid.setFluid(new FluidStack(Fluids.WATER, 750));
        Table<IO, RecipeCapability<?>, Object> storages = HashBasedTable.create();
        storages.put(IO.IN, ItemRecipeCapability.CAP, inputItems);
        storages.put(IO.OUT, ItemRecipeCapability.CAP, outputItems);
        storages.put(IO.IN, FluidRecipeCapability.CAP, inputFluid);
        storages.put(IO.OUT, FluidRecipeCapability.CAP, outputFluid);

        recipeUI.bindLDLib2RecipeUI(root, new GTRecipeTypeUI.RecipeHolder(
                () -> 0.375, storages, DataComponentMap.EMPTY, List.of(), false, false));

        GTItemSlotElement itemInput = requireElement(root, "item_in_1", GTItemSlotElement.class);
        GTItemSlotElement itemOutput = requireElement(root, "item_out_0", GTItemSlotElement.class);
        GTFluidSlotElement fluidInput = requireElement(root, "fluid_in_0", GTFluidSlotElement.class);
        GTProgressBarElement progress = requireElement(root, "progress", GTProgressBarElement.class);
        progress.screenTick();

        helper.assertTrue(itemInput.getSlot().getItem().is(Items.IRON_INGOT),
                "item_in_1 did not bind handler slot 1");
        helper.assertTrue(itemInput.getIngredientIO() == IngredientIO.INPUT &&
                itemOutput.getIngredientIO() == IngredientIO.OUTPUT,
                "item recipe elements did not retain input/output XEI roles");
        helper.assertTrue(itemInput.getSlot().mayPlace(Items.COPPER_INGOT.getDefaultInstance()) &&
                !itemOutput.getSlot().mayPlace(Items.COPPER_INGOT.getDefaultInstance()),
                "bound item recipe elements did not retain input/output placement rules");
        helper.assertTrue(FluidStack.isSameFluidSameComponents(fluidInput.getFluid(), inputFluid.getFluid()) &&
                fluidInput.getFluid().getAmount() == 750,
                "fluid_in_0 did not bind tank 0");
        helper.assertTrue(Math.abs(progress.getValue() - 0.375f) < 0.0001f,
                "progress element did not bind the recipe progress supplier");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void convertedCustomTemplatesParseWithFixedSizesAndStableIds(GameTestHelper helper) {
        assertCustomTemplate(helper, "assembly_line.xml", 154, 80, "item_in_16", GTItemSlotElement.class);
        assertCustomTemplate(helper, "distillation_tower.xml", 148, 96, "fluid_out_11",
                GTFluidSlotElement.class);
        assertCustomTemplate(helper, "forge_hammer.xml", 92, 26, "item_in_0", GTItemSlotElement.class);
        assertCustomTemplate(helper, "lathe.xml", 110, 26, "item_out_1", GTItemSlotElement.class);
        assertCustomTemplate(helper, "research_station.xml", 136, 72, "item_out_0", GTItemSlotElement.class);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void xeiBindingPreservesCatalystAndChanceSemantics(GameTestHelper helper) {
        GTRecipeDefinition recipe = GTRecipeTypes.CANNER_RECIPES
                .recipeBuilder(GTCEu.id("ldlib2_recipe_ui_semantics"))
                .notConsumable(Items.CRAFTING_TABLE)
                .circuitMeta(1)
                .chancedOutput(new ItemStack(Items.DIAMOND), 2_500, 0)
                .buildDefinition();

        UI ui = GTLDLib2RecipeUI.createUI(recipe, 0, 0);
        GTItemSlotElement tool = requireElement(ui.getRootElement(), "item_in_0", GTItemSlotElement.class);
        GTItemSlotElement circuit = requireElement(ui.getRootElement(), "item_in_1", GTItemSlotElement.class);
        GTItemSlotElement output = requireElement(ui.getRootElement(), "item_out_0", GTItemSlotElement.class);

        helper.assertTrue(tool.getIngredientIO() == IngredientIO.CATALYST &&
                circuit.getIngredientIO() == IngredientIO.CATALYST,
                "tool and programmed circuit inputs did not retain catalyst roles");
        helper.assertTrue(output.getIngredientIO() == IngredientIO.OUTPUT,
                "chanced item output did not retain its output role");
        helper.assertTrue(Math.abs(output.getXEIChance() - 0.25f) < 0.0001f,
                "chanced item output did not retain its 25 percent chance");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void bindingRejectsTemplateWithoutProgressElement(GameTestHelper helper) {
        GTRecipeTypeUI recipeUI = GTRecipeTypes.CANNER_RECIPES.getRecipeUI();
        Table<IO, RecipeCapability<?>, Object> storages = HashBasedTable.create();

        try {
            recipeUI.bindLDLib2RecipeUI(new UIElement(), new GTRecipeTypeUI.RecipeHolder(
                    () -> 0.5, storages, DataComponentMap.EMPTY, List.of(), false, false));
        } catch (IllegalStateException e) {
            helper.assertTrue(e.getMessage().contains("progress"),
                    "missing-progress rejection did not identify the invalid progress contract");
            helper.succeed();
            return;
        }
        throw new GameTestAssertException("recipe UI binding accepted a template without a progress element");
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void bindingSupportsMultipleProgressElements(GameTestHelper helper) {
        UI ui = loadCustomTemplate("assembly_line.xml");
        Table<IO, RecipeCapability<?>, Object> storages = HashBasedTable.create();
        GTRecipeTypes.ASSEMBLY_LINE_RECIPES.getRecipeUI().bindLDLib2RecipeUI(ui.getRootElement(),
                new GTRecipeTypeUI.RecipeHolder(
                        () -> 0.625, storages, DataComponentMap.EMPTY, List.of(), false, false));

        List<GTProgressBarElement> progressElements = ui.selectId("progress", GTProgressBarElement.class).toList();
        helper.assertTrue(progressElements.size() == 2,
                "assembly line template did not retain both progress elements");
        progressElements.forEach(GTProgressBarElement::screenTick);
        helper.assertTrue(
                progressElements.stream().allMatch(progress -> Math.abs(progress.getValue() - 0.625f) < 0.0001f),
                "assembly line progress elements did not bind the same progress supplier");
        helper.succeed();
    }

    private static void assertCustomTemplate(GameTestHelper helper, String fileName, int width, int height,
                                             String stableId, Class<? extends UIElement> elementType) {
        UI ui = loadCustomTemplate(fileName);
        GTRecipeTypeUI.LDLib2RecipeUISize size = GTRecipeTypeUI.getLDLib2RecipeUISize(ui.getRootElement());
        helper.assertTrue(size.width() == width && size.height() == height,
                fileName + " did not preserve its fixed dimensions");
        helper.assertTrue(ui.selectId(stableId, elementType).findAny().isPresent(),
                fileName + " did not parse stable element id " + stableId + " as " + elementType.getSimpleName());
        helper.assertTrue(ui.selectId("progress", GTProgressBarElement.class).findAny().isPresent() ||
                ui.selectId("progress", GTDualProgressElement.class).findAny().isPresent(),
                fileName + " did not parse a bindable progress element");
    }

    private static UI loadCustomTemplate(String fileName) {
        String resource = "/assets/gtpm/ui/recipe_type/" + fileName;
        try (InputStream input = GTRecipeTypeLDLib2UITest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new GameTestAssertException("missing converted recipe UI resource " + resource);
            }
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return UI.of(RecipeUIXmlTemplate.parse(xml));
        } catch (IOException e) {
            throw new GameTestAssertException("failed to read converted recipe UI resource " + resource + ": " +
                    e.getMessage());
        }
    }

    private static <T extends UIElement> T requireElement(UIElement root, String id, Class<T> elementType) {
        return root.selectId(id, elementType).findFirst()
                .orElseThrow(() -> new GameTestAssertException(
                        "missing " + elementType.getSimpleName() + " with id " + id));
    }
}
