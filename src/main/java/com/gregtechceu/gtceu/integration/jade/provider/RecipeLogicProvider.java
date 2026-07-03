package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.client.util.TooltipHelper;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class RecipeLogicProvider extends MachineTraitProvider<RecipeLogic, CompoundTag> {

    public RecipeLogicProvider() {
        super(GTCEu.id("recipe_logic_provider"), RecipeLogic.TYPE);
    }

    @Override
    protected CompoundTag write(RecipeLogic capability) {
        var data = new CompoundTag();
        data.putBoolean("Working", capability.isWorking());
        var recipeInfo = new CompoundTag();
        var recipe = capability.getLastRecipe();
        if (recipe != null) {
            var EUt = RecipeHelper.getRealEUtWithIO(recipe);

            recipeInfo.putLong("EUt", Math.abs(EUt));
            recipeInfo.putLong("voltage", getVoltage(capability));
            recipeInfo.putBoolean("isInput", EUt > 0);
        }

        if (!recipeInfo.isEmpty()) {
            data.put("Recipe", recipeInfo);
        }
        if (!capability.getFailureReasonsMap().isEmpty()) {
            var failureReasons = new ListTag();
            for (Component reason : capability.getFailureReasonsMap().values()) {
                failureReasons.add(StringTag.valueOf(Component.Serializer.toJson(reason, capability.getMachine()
                        .getLevel().registryAccess())));
            }
            data.put("FailureReasons", failureReasons);
        }
        return data;
    }

    public static long getVoltage(RecipeLogic capability) {
        long voltage = capability.getRLMachine().getDisplayRecipeVoltage();

        // default display as LV, this shouldn't happen because a machine is either electric or steam
        return voltage == -1 ? GTValues.V[GTValues.LV] : voltage;
    }

    @Override
    protected void addTooltip(CompoundTag capData, ITooltip tooltip, Player player, BlockAccessor block,
                              BlockEntity blockEntity, IPluginConfig config) {
        if (capData.getBoolean("Working")) {
            var recipeInfo = capData.getCompound("Recipe");
            if (!recipeInfo.isEmpty()) {
                var EUt = recipeInfo.getLong("EUt");
                var isInput = recipeInfo.getBoolean("isInput");
                boolean isSteam = false;

                if (EUt > 0) {
                    if (blockEntity instanceof SimpleSteamMachine ssm) {
                        EUt = (long) Math.ceil(EUt * ssm.getConversionRate());
                        isSteam = true;
                    } else if (blockEntity instanceof SteamParallelMultiblockMachine smb) {
                        EUt = (long) Math.ceil(EUt * smb.getConversionRate());
                        isSteam = true;
                    }

                    MutableComponent text;

                    if (isSteam) {
                        text = Component.translatable("gtpm.jade.fluid_use", FormattingUtil.formatNumbers(EUt))
                                .withStyle(ChatFormatting.GREEN);
                    } else {
                        var voltage = recipeInfo.getLong("voltage");
                        var tier = GTUtil.getTierByVoltage(voltage);
                        float minAmperage = (float) EUt / voltage;

                        text = Component
                                .translatable("gtpm.jade.amperage_use",
                                        FormattingUtil.formatNumber2Places(minAmperage))
                                .withStyle(ChatFormatting.RED)
                                .append(Component.translatable("gtpm.jade.at").withStyle(ChatFormatting.GREEN));
                        if (tier < GTValues.TIER_COUNT) {
                            text = text.append(Component.literal(GTValues.VNF[tier])
                                    .withStyle(style -> style.withColor(GTValues.VC[tier])));
                        } else {
                            int speed = Mth.clamp(tier - GTValues.TIER_COUNT - 1, 0, GTValues.TIER_COUNT);
                            text = text.append(Component.literal("MAX")
                                    .withStyle(style -> style.withColor(TooltipHelper.rainbowColor(speed)))
                                    .append(Component.literal("+")
                                            .withStyle(style -> style.withColor(GTValues.VC[speed]))
                                            .append(FormattingUtil.formatNumbers(speed))));

                        }
                        text.append(Component.translatable("gtpm.universal.padded_parentheses",
                                (Component.translatable("gtpm.recipe.eu.total",
                                        FormattingUtil.formatNumbers(EUt))))
                                .withStyle(ChatFormatting.WHITE));
                    }

                    if (isInput) {
                        tooltip.add(Component.translatable("gtpm.top.energy_consumption").append(" ").append(text));
                    } else {
                        tooltip.add(Component.translatable("gtpm.top.energy_production").append(" ").append(text));
                    }
                }
            }
        } else if (blockEntity instanceof IRecipeLogicMachine rlm) {
            var logic = rlm.getRecipeLogic();

            if (logic.isWaiting() && logic.getWaitingReason() != null && logic.isWorkingEnabled()) {
                tooltip.add(Component.translatable("gtpm.recipe_logic.recipe_waiting")
                        .withStyle(ChatFormatting.YELLOW));
                tooltip.add(logic.getWaitingReason());
                return;
            }

            if (logic.isWorkingEnabled() && capData.contains("FailureReasons", Tag.TAG_LIST)) {
                ListTag failureReasons = capData.getList("FailureReasons", Tag.TAG_STRING);
                if (!failureReasons.isEmpty()) {
                    tooltip.add(Component.translatable("gtpm.recipe_logic.setup_fail").withStyle(ChatFormatting.RED));
                    for (Tag tag : failureReasons) {
                        Component reason = Component.Serializer.fromJson(tag.getAsString(),
                                block.getLevel().registryAccess());
                        if (reason != null) {
                            tooltip.add(Component.literal(" - ").append(reason));
                        }
                    }
                }
            }
        }
    }
}
