package com.gregtechceu.gtceu.integration.xei;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeData;
import com.gregtechceu.gtceu.api.recipe.chance.boost.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class GTRecipeXEIHelper {

    public static final int LINE_HEIGHT = 10;

    private GTRecipeXEIHelper() {}

    @NotNull
    public static List<Component> getRecipeParaText(GTRecipeDefinition recipe, int duration, long eu) {
        List<Component> texts = new ArrayList<>();
        if (!RecipeData.getBoolean(recipe.data, "hide_duration")) {
            texts.add(Component.translatable("gtpm.recipe.duration", FormattingUtil.formatNumbers(duration / 20f)));
        }
        if (eu != 0) {
            long euTotal = Math.abs(eu) * duration;
            // Computation recipes store duration as total CWU, so total EU needs the minimum CWUt divisor.
            if (RecipeData.getBoolean(recipe.data, "duration_is_total_cwu") &&
                    recipe.tickInputs.containsKey(CWURecipeCapability.CAP)) {
                int minimumCWUt = Math.max(recipe.tickInputs.get(CWURecipeCapability.CAP).stream()
                        .map(Content::getContent).mapToInt(CWURecipeCapability.CAP::of).sum(), 1);
                texts.add(Component.translatable("gtpm.recipe.max_eu",
                        FormattingUtil.formatNumbers(euTotal / minimumCWUt)));
            } else {
                texts.add(Component.translatable("gtpm.recipe.total", FormattingUtil.formatNumbers(euTotal)));
            }
        }

        return texts;
    }

    public static void setConsumedChance(Content content, ChanceLogic logic, List<Component> tooltips, int recipeTier,
                                         int chanceTier, ChanceBoostFunction function) {
        if (content.chance >= ChanceLogic.getMaxChancedValue()) {
            return;
        }

        int boostedChance = function.getBoostedChance(content, recipeTier, chanceTier);
        if (boostedChance == 0) {
            tooltips.add(Component.translatable("gtpm.gui.content.chance_nc"));
            return;
        }

        float baseChanceFloat = 100f * content.chance / content.maxChance;
        if (content.tierChanceBoost != 0) {
            float boostedChanceFloat = 100f * boostedChance / content.maxChance;

            if (logic != ChanceLogic.NONE && logic != ChanceLogic.OR) {
                tooltips.add(Component.translatable("gtpm.gui.content.chance_base_logic",
                        FormattingUtil.formatNumber2Places(baseChanceFloat), logic.getTranslation())
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                tooltips.add(FormattingUtil.formatPercentage2Places("gtpm.gui.content.chance_base",
                        baseChanceFloat));
            }

            String key = "gtpm.gui.content.chance_tier_boost_" +
                    ((content.tierChanceBoost > 0) ? "plus" : "minus");
            tooltips.add(FormattingUtil.formatPercentage2Places(key,
                    Math.abs(100f * content.tierChanceBoost / content.maxChance)));

            if (logic != ChanceLogic.NONE && logic != ChanceLogic.OR) {
                tooltips.add(Component.translatable("gtpm.gui.content.chance_boosted_logic",
                        FormattingUtil.formatNumber2Places(boostedChanceFloat), logic.getTranslation())
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                tooltips.add(FormattingUtil.formatPercentage2Places("gtpm.gui.content.chance_boosted",
                        boostedChanceFloat));
            }
        } else if (logic != ChanceLogic.NONE && logic != ChanceLogic.OR) {
            tooltips.add(Component.translatable("gtpm.gui.content.chance_no_boost_logic",
                    FormattingUtil.formatNumber2Places(baseChanceFloat), logic.getTranslation())
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            tooltips.add(FormattingUtil.formatPercentage2Places("gtpm.gui.content.chance_no_boost",
                    baseChanceFloat));
        }
    }
}
