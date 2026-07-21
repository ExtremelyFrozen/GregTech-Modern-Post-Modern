package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTDualProgressElement;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.ResearchData;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentListMap;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.recipe.condition.CleanroomCondition;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gregtechceu.gtceu.gametest.util.TestUtils;
import com.gregtechceu.gtceu.integration.xei.GTLDLib2RecipeUI;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import org.w3c.dom.Document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTRecipeTypeLDLib2UITest {

    private static final String BATCH = "GTRecipeTypeLDLib2UI";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void generatedTemplateParsesAndBindsStableRecipeElements(GameTestHelper helper) {
        GTRecipeTypeUI recipeUI = GTRecipeTypes.CANNER_RECIPES.getRecipeUI();
        Document document = recipeUI.createLDLib2TemplateDocument();
        Document serializedDocument = RecipeUIXmlTemplate.parse(recipeUI.createLDLib2TemplateXml());
        UI ui = UI.of(serializedDocument);
        UIElement root = ui.getRootElement();

        GTRecipeTypeUI.LDLib2RecipeUISize size = GTRecipeTypeUI.getLDLib2RecipeUISize(serializedDocument);
        helper.assertTrue(size.width() == 128 && size.height() == 44,
                "generated canner template did not preserve its fixed 128x44 layout");
        helper.assertTrue(size.equals(GTRecipeTypeUI.getLDLib2RecipeUISize(document)),
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
    public static void recipeStaticInfoRowsStayInsideFixedRoot(GameTestHelper helper) {
        GTRecipeDefinition recipe = GTRecipeTypes.RESEARCH_STATION_RECIPES
                .recipeBuilder(GTCEu.id("ldlib2_recipe_static_info_bounds"))
                .inputItems(Items.PAPER)
                .outputItems(Items.DIAMOND)
                .EUt(GTValues.VA[GTValues.EV])
                .CWUt(16)
                .totalCWU(48)
                .buildDefinition();
        UI ui = GTLDLib2RecipeUI.createUI(recipe, GTValues.EV, GTValues.EV);
        UIElement root = ui.getRootElement();
        GTRecipeType recipeType = recipe.recipeType;
        int templateHeight = recipeType.getRecipeUI().getLDLib2RecipeUISize(false, false).height();
        int rootHeight = recipeType.getRecipeUI().getLDLib2XEIRecipeUISize().height();
        List<GTLabelElement> labels = root.getChildren().stream()
                .filter(GTLabelElement.class::isInstance)
                .map(GTLabelElement.class::cast)
                .toList();
        UITemplate.LDLib2Bounds eu = requireTranslatedLabelBounds(labels, "gtpm.recipe.eu");
        UITemplate.LDLib2Bounds voltage = requireLiteralLabelBounds(labels, GTValues.VNF[GTValues.EV]);
        UITemplate.LDLib2Bounds computationPerTick = requireTranslatedLabelBounds(labels,
                "gtpm.recipe.computation_per_tick");
        UITemplate.LDLib2Bounds totalComputation = requireTranslatedLabelBounds(labels,
                "gtpm.recipe.total_computation");

        helper.assertTrue(labels.stream().map(UITemplate::getLDLib2Bounds)
                .allMatch(bounds -> bounds.y() >= templateHeight + 5 &&
                        bounds.y() + bounds.height() <= rootHeight),
                "research recipe static info row escaped its fixed root bounds");
        helper.assertTrue(eu.y() == voltage.y(),
                "research recipe did not keep EU/t and voltage tier on the same information row");
        helper.assertTrue(computationPerTick.y() == eu.y() + 10 &&
                totalComputation.y() == computationPerTick.y() + 10,
                "research recipe did not reserve separate computation information rows");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void computationRowsStartAtFirstAvailableRowWithoutEnergyText(GameTestHelper helper) {
        GTRecipeDefinition recipe = GTRecipeTypes.RESEARCH_STATION_RECIPES
                .recipeBuilder(GTCEu.id("ldlib2_recipe_static_info_without_energy"))
                .inputItems(Items.PAPER)
                .outputItems(Items.DIAMOND)
                .CWUt(16)
                .totalCWU(48)
                .buildDefinition();
        UIElement root = GTLDLib2RecipeUI.createUI(recipe, GTValues.EV, GTValues.EV).getRootElement();
        int templateHeight = recipe.recipeType.getRecipeUI().getLDLib2RecipeUISize(false, false).height();
        List<GTLabelElement> labels = root.getChildren().stream()
                .filter(GTLabelElement.class::isInstance)
                .map(GTLabelElement.class::cast)
                .toList();
        UITemplate.LDLib2Bounds computationPerTick = requireTranslatedLabelBounds(labels,
                "gtpm.recipe.computation_per_tick");
        UITemplate.LDLib2Bounds totalComputation = requireTranslatedLabelBounds(labels,
                "gtpm.recipe.total_computation");

        helper.assertTrue(computationPerTick.y() == templateHeight + 5 &&
                totalComputation.y() == computationPerTick.y() + 10,
                "computation-only recipe rows did not start at the first available information row");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void categoryTracksConditionsFromDecodedDefinition(GameTestHelper helper) {
        GTRecipeType type = TestUtils.createRecipeType("ldlib2_condition_budget", GTRecipeTypes.CANNER_RECIPES);
        GTRecipeDefinition decodedRecipe = new GTRecipeDefinition(
                GTCEu.id("ldlib2_condition_budget_recipe"), type,
                new ContentListMap(), new ContentListMap(), new ContentListMap(), new ContentListMap(),
                Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(new CleanroomCondition(false, CleanroomType.CLEANROOM)), List.of(),
                DataComponentMap.EMPTY, 0, 1, type.getCategory(), -1);

        type.addToCategoryMap(type.getCategory(), decodedRecipe);

        helper.assertTrue(type.getMinRecipeConditions() >= decodedRecipe.conditions.size(),
                "category condition budget did not include a decoded recipe condition");
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

        GTRecipeTypeUI recipeUI = recipe.recipeType.getRecipeUI();
        Table<IO, RecipeCapability<?>, Object> storages = HashBasedTable.create();
        Table<IO, RecipeCapability<?>, List<Content>> contents = HashBasedTable.create();
        List<Content> inputContents = recipe.getInputContents(ItemRecipeCapability.CAP);
        List<Content> outputContents = recipe.getOutputContents(ItemRecipeCapability.CAP);
        contents.put(IO.IN, ItemRecipeCapability.CAP, inputContents);
        contents.put(IO.OUT, ItemRecipeCapability.CAP, outputContents);
        storages.put(IO.IN, ItemRecipeCapability.CAP, ItemRecipeCapability.CAP.createXEIContainer(
                ItemRecipeCapability.CAP.createXEIContainerContents(inputContents, recipe, IO.IN)));
        storages.put(IO.OUT, ItemRecipeCapability.CAP, ItemRecipeCapability.CAP.createXEIContainer(
                ItemRecipeCapability.CAP.createXEIContainerContents(outputContents, recipe, IO.OUT)));

        UI ui = recipeUI.createLDLib2UITemplate(GTRecipeTypeUI.XEI_PROGRESS, storages,
                DataComponentMap.EMPTY, recipe.conditions);
        recipeUI.applyLDLib2RecipeContent(ui, contents, recipe, 0, 0);
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
    public static void lightweightXEIIngredientsPreserveBoundRecipeSemantics(GameTestHelper helper) {
        GTRecipeDefinition recipe = GTRecipeTypes.MIXER_RECIPES
                .recipeBuilder(GTCEu.id("ldlib2_lightweight_xei_ingredients"))
                .inputItems(new ItemStack(Items.IRON_INGOT, 2))
                .notConsumable(Items.CRAFTING_TABLE)
                .circuitMeta(1)
                .inputFluids(new FluidStack(Fluids.WATER, 1_000))
                .chancedOutput(new ItemStack(Items.DIAMOND, 2), 2_500, 0)
                .outputFluids(new FluidStack(Fluids.LAVA, 500))
                .buildDefinition();

        List<UIElement> elements = GTLDLib2RecipeUI.createXEIIngredientElements(recipe, 0, 0);
        UIElement root = new UIElement();
        elements.forEach(root::addChild);

        List<GTItemSlotElement> itemElements = elements.stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .toList();
        helper.assertTrue(itemElements.stream()
                .filter(element -> element.getIngredientIO() == IngredientIO.INPUT).count() == 1,
                "lightweight XEI binding did not retain the consumable item input");
        helper.assertTrue(itemElements.stream()
                .filter(element -> element.getIngredientIO() == IngredientIO.CATALYST).count() == 2,
                "lightweight XEI binding did not retain the tool and circuit catalysts");
        GTItemSlotElement output = itemElements.stream()
                .filter(element -> element.getIngredientIO() == IngredientIO.OUTPUT)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("lightweight XEI binding omitted the item output"));
        helper.assertTrue(output.getSlot().getItem().is(Items.DIAMOND) && output.getSlot().getItem().getCount() == 2,
                "lightweight XEI binding did not retain the output item amount");
        helper.assertTrue(Math.abs(output.getXEIChance() - 0.25f) < 0.0001f,
                "lightweight XEI binding did not retain the chanced output probability");

        GTFluidSlotElement fluidInput = requireElement(root, "fluid_in_0", GTFluidSlotElement.class);
        GTFluidSlotElement fluidOutput = requireElement(root, "fluid_out_0", GTFluidSlotElement.class);
        var fluidInputIngredient = FluidRecipeCapability.CAP.of(
                recipe.getInputContents(FluidRecipeCapability.CAP).getFirst().getContent());
        FluidStack displayedFluidInput = fluidInput.getFluid();
        helper.assertTrue(fluidInputIngredient.ingredient().test(displayedFluidInput),
                "lightweight XEI binding displayed a fluid outside the recipe input ingredient");
        helper.assertTrue(displayedFluidInput.getAmount() == 1_000,
                "lightweight XEI binding did not retain the fluid input amount");
        var fluidOutputIngredient = FluidRecipeCapability.CAP.of(
                recipe.getOutputContents(FluidRecipeCapability.CAP).getFirst().getContent());
        FluidStack displayedFluidOutput = fluidOutput.getFluid();
        helper.assertTrue(fluidOutputIngredient.ingredient().test(displayedFluidOutput),
                "lightweight XEI binding displayed a fluid outside the recipe output ingredient");
        helper.assertTrue(displayedFluidOutput.getAmount() == 500,
                "lightweight XEI binding did not retain the fluid output amount");

        long traversedElements = root.selfAndAllChildren().count();
        helper.assertTrue(traversedElements >= elements.size() + 1 && traversedElements <= elements.size() * 3L,
                "lightweight XEI element traversal was not bounded by the constructed slot tree");

        ResearchData researchData = new ResearchData(List.of(
                new ResearchData.ResearchEntry("lightweight_xei_research", new ItemStack(Items.PAPER))));
        GTRecipeDefinition researchRecipe = GTRecipeTypes.ASSEMBLY_LINE_RECIPES
                .recipeBuilder(GTCEu.id("ldlib2_lightweight_xei_research"))
                .inputItems(Items.IRON_INGOT)
                .outputItems(Items.DIAMOND)
                .addCondition(new ResearchCondition(false, researchData))
                .buildDefinition();
        UIElement researchRoot = new UIElement();
        GTLDLib2RecipeUI.createXEIIngredientElements(researchRecipe, 0, 0).forEach(researchRoot::addChild);
        GTItemSlotElement research = requireElement(researchRoot, "item_in_16", GTItemSlotElement.class);
        helper.assertTrue(research.getIngredientIO() == IngredientIO.CATALYST &&
                research.getSlot().getItem().is(Items.PAPER),
                "lightweight XEI binding did not retain the assembly-line research catalyst");
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
        Document document = loadCustomTemplateDocument(fileName);
        GTRecipeTypeUI.LDLib2RecipeUISize size = GTRecipeTypeUI.getLDLib2RecipeUISize(document);
        UI ui = UI.of(document);
        helper.assertTrue(size.width() == width && size.height() == height,
                fileName + " did not preserve its fixed dimensions");
        helper.assertTrue(ui.selectId(stableId, elementType).findAny().isPresent(),
                fileName + " did not parse stable element id " + stableId + " as " + elementType.getSimpleName());
        helper.assertTrue(ui.selectId("progress", GTProgressBarElement.class).findAny().isPresent() ||
                ui.selectId("progress", GTDualProgressElement.class).findAny().isPresent(),
                fileName + " did not parse a bindable progress element");
    }

    private static UI loadCustomTemplate(String fileName) {
        return UI.of(loadCustomTemplateDocument(fileName));
    }

    private static Document loadCustomTemplateDocument(String fileName) {
        String resource = "/assets/gtpm/ui/recipe_type/" + fileName;
        try (InputStream input = GTRecipeTypeLDLib2UITest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new GameTestAssertException("missing converted recipe UI resource " + resource);
            }
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return RecipeUIXmlTemplate.parse(xml);
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

    private static UITemplate.LDLib2Bounds requireTranslatedLabelBounds(List<GTLabelElement> labels,
                                                                        String translationKey) {
        return labels.stream()
                .filter(label -> label.getValue().getContents() instanceof TranslatableContents contents &&
                        contents.getKey().equals(translationKey))
                .findFirst()
                .map(UITemplate::getLDLib2Bounds)
                .orElseThrow(() -> new GameTestAssertException(
                        "missing translated recipe label " + translationKey));
    }

    private static UITemplate.LDLib2Bounds requireLiteralLabelBounds(List<GTLabelElement> labels, String text) {
        return labels.stream()
                .filter(label -> label.getValue().getString().equals(text))
                .findFirst()
                .map(UITemplate::getLDLib2Bounds)
                .orElseThrow(() -> new GameTestAssertException("missing literal recipe label " + text));
    }
}
