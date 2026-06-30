package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.api.recipe.content.ContentListMap;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.NotNull;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTRecipeDefinitionTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTRecipeDefinition")
    public static void definitionRuntimeCopyDoesNotShareContent(GameTestHelper helper) {
        GTRecipeDefinition definition = GTRecipeTypes.CHEMICAL_RECIPES
                .recipeBuilder(GTCEu.id("definition_runtime_copy"))
                .inputItems(new ItemStack(Items.COBBLESTONE))
                .outputItems(new ItemStack(Items.STONE))
                .duration(40)
                .durationIsTotalCWU(true)
                .buildDefinition();

        GTRecipe runtime = definition.toRuntime();
        runtime.inputs.clear();
        runtime.outputs.clear();
        runtime.duration = 1;
        runtime.data = RecipeData.putBoolean(runtime.data, "duration_is_total_cwu", false);

        helper.assertFalse(definition.inputs.isEmpty(), "definition inputs were mutated by runtime changes");
        helper.assertFalse(definition.outputs.isEmpty(), "definition outputs were mutated by runtime changes");
        helper.assertTrue(definition.duration == 40, "definition duration was mutated by runtime changes");
        helper.assertTrue(RecipeData.getBoolean(definition.data, "duration_is_total_cwu"),
                "definition data was mutated by runtime changes");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTRecipeDefinition")
    public static void contentListMapCopyDoesNotShareLists(GameTestHelper helper) {
        GTRecipe recipe = GTRecipeTypes.CHEMICAL_RECIPES
                .recipeBuilder(GTCEu.id("content_list_map_copy"))
                .inputItems(new ItemStack(Items.COBBLESTONE))
                .outputItems(new ItemStack(Items.STONE))
                .build();
        ContentListMap copy = ContentListMap.copyOf(recipe.inputs);

        recipe.inputs.clear();

        helper.assertFalse(copy.isEmpty(), "ContentListMap copy shared the source map");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTRecipeDefinition")
    public static void onlyPerTickConditionsAreCheckedInRunningPath(GameTestHelper helper) {
        GTRecipe recipe = GTRecipeBuilder.ofRaw()
                .addCondition(new TestCondition(false, false))
                .addCondition(new TestCondition(true, true))
                .build();

        RecipeLogic recipeLogic = new RecipeLogic();
        helper.assertFalse(RecipeHelper.checkConditions(recipe, recipeLogic).isSuccess(),
                "full condition check should include non per-tick failure");
        helper.assertTrue(RecipeHelper.checkConditions(recipe, recipeLogic, true).isSuccess(),
                "per-tick condition check should ignore non per-tick failure");
        helper.succeed();
    }

    private static class TestCondition extends RecipeCondition<TestCondition> {

        private final boolean perTick;
        private final boolean result;

        private TestCondition(boolean perTick, boolean result) {
            this.perTick = perTick;
            this.result = result;
        }

        @Override
        public RecipeConditionType<TestCondition> getType() {
            return null;
        }

        @Override
        public boolean perTick() {
            return perTick;
        }

        @Override
        public Component getTooltips() {
            return Component.literal("test condition");
        }

        @Override
        protected boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
            return result;
        }

        @Override
        public TestCondition createTemplate() {
            return new TestCondition(perTick, result);
        }
    }
}
