package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.integration.jade.GTElementHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.util.FluidTextHelper;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class RecipeOutputProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    private static final Gson GSON = new Gson();
    private static final String OUTPUT_ITEMS = "OutputItems";
    private static final String OUTPUT_FLUIDS = "OutputFluids";

    @Override
    public ResourceLocation getUid() {
        return GTCEu.id("recipe_output_info");
    }

    private RecipeOutputData write(RecipeLogic recipeLogic) {
        if (!recipeLogic.isWorking()) {
            return RecipeOutputData.EMPTY;
        }
        GTRecipe recipe = recipeLogic.getLastRecipe();
        if (recipe == null) {
            return RecipeOutputData.WORKING_EMPTY;
        }

        int recipeTier = RecipeHelper.getPreOCRecipeEuTier(recipe);
        int chanceTier = recipeTier + recipe.ocLevel;
        var function = recipe.getType().getChanceFunction();
        var itemContents = recipe.getOutputContents(ItemRecipeCapability.CAP);
        var fluidContents = recipe.getOutputContents(FluidRecipeCapability.CAP);
        int runs = recipe.getTotalRuns();

        var ops = recipeLogic.getMachine().getLevel()
                .registryAccess().createSerializationContext(JsonOps.INSTANCE);

        JsonArray itemPayload = new JsonArray();
        for (Content item : itemContents) {
            JsonElement itemData;
            SizedIngredient content = ItemRecipeCapability.CAP.of(item.content);
            if (content.ingredient().getCustomIngredient() instanceof IntProviderIngredient provider) {
                IntProviderIngredient chanced = provider;
                if (item.chance < item.maxChance) {
                    double countD = (double) runs *
                            function.getBoostedChance(item, recipeTier, chanceTier) / item.maxChance;
                    chanced = ItemRecipeCapability.CAP.copyWithModifier(provider,
                            ContentModifier.multiplier(countD));
                }
                itemData = chanced.getType().codec().codec().encodeStart(ops, chanced).getOrThrow();
            } else {
                ItemStack[] stacks = content.getItems();
                if (stacks.length == 0 || stacks[0].isEmpty()) continue;
                ItemStack stack = stacks[0].copy();
                if (item.chance < item.maxChance) {
                    int count = stack.getCount();
                    double countD = (double) count * runs *
                            function.getBoostedChance(item, recipeTier, chanceTier) / item.maxChance;
                    count = Math.max(1, (int) Math.round(countD));
                    stack.setCount(count);
                }
                itemData = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            }
            itemPayload.add(itemData);
        }

        JsonArray fluidPayload = new JsonArray();
        for (var fluid : fluidContents) {
            FluidIngredient ingredient = FluidRecipeCapability.CAP.of(fluid.getContent()).ingredient();
            JsonElement fluidData;
            if (ingredient instanceof IntProviderFluidIngredient provider) {
                IntProviderFluidIngredient chanced = provider;
                if (fluid.chance < fluid.maxChance) {
                    double countD = (double) runs *
                            function.getBoostedChance(fluid, recipeTier, chanceTier) / fluid.maxChance;
                    chanced = (IntProviderFluidIngredient) FluidRecipeCapability.CAP.copyWithModifier(provider,
                            ContentModifier.multiplier(countD));
                }
                fluidData = IntProviderFluidIngredient.CODEC.codec().encodeStart(ops, chanced).getOrThrow();
            } else {
                FluidStack[] stacks = FluidRecipeCapability.CAP.of(fluid.content).getFluids();
                if (stacks.length == 0) continue;
                if (stacks[0].isEmpty()) continue;
                var stack = stacks[0].copy();

                if (fluid.chance < fluid.maxChance) {
                    int amount = stack.getAmount();
                    double amountD = (double) amount * runs *
                            function.getBoostedChance(fluid, recipeTier, chanceTier) / fluid.maxChance;
                    amount = Math.max(1, (int) Math.round(amountD));
                    stack.setAmount(amount);
                }
                fluidData = FluidStack.CODEC.encodeStart(ops, stack).getOrThrow();
            }
            fluidPayload.add(fluidData);
        }

        return new RecipeOutputData(true, itemPayload, fluidPayload);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor block, IPluginConfig config) {
        var blockEntity = block.getBlockEntity();
        if (blockEntity == null || !block.getServerData().contains(getUid().toString(), CompoundTag.TAG_COMPOUND)) {
            return;
        }
        RecipeOutputData data = RecipeOutputData.fromTag(block.getServerData().getCompound(getUid().toString()));
        addTooltip(data, tooltip, block.getPlayer(), block, blockEntity, config);
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        var blockEntity = blockAccessor.getBlockEntity();
        if (blockEntity instanceof MetaMachine machine) {
            RecipeLogic recipeLogic = machine.getTrait(RecipeLogic.TYPE);
            if (recipeLogic != null) {
                data.put(getUid().toString(), write(recipeLogic).toTag());
            }
        }
    }

    private void addTooltip(RecipeOutputData data, ITooltip tooltip, Player player, BlockAccessor block,
                            BlockEntity blockEntity, IPluginConfig config) {
        if (!data.working()) {
            return;
        }
        var ops = block.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);

        List<SizedIngredient> outputItems = new ArrayList<>();
        for (JsonElement itemData : data.items()) {
            if (isRangedIngredient(itemData)) {
                var ingredient = IntProviderIngredient.CODEC.codec().parse(ops, itemData).getOrThrow();
                outputItems.add(new SizedIngredient(ingredient.toVanilla(), 1));
            } else {
                ItemStack stack = ItemStack.CODEC.parse(ops, itemData).getOrThrow();
                if (!stack.isEmpty()) {
                    outputItems.add(RecipeHelper.makeSizedIngredient(stack));
                }
            }
        }
        List<SizedFluidIngredient> outputFluids = new ArrayList<>();
        for (JsonElement fluidData : data.fluids()) {
            if (isRangedIngredient(fluidData)) {
                var ingredient = IntProviderFluidIngredient.CODEC.codec().parse(ops, fluidData).getOrThrow();
                outputFluids.add(new SizedFluidIngredient(ingredient, 1));
            } else {
                FluidStack stack = FluidStack.CODEC.parse(ops, fluidData).getOrThrow();
                if (!stack.isEmpty()) {
                    outputFluids.add(RecipeHelper.makeSizedFluidIngredient(stack));
                }
            }
        }
        if (!outputItems.isEmpty() || !outputFluids.isEmpty()) {
            tooltip.add(Component.translatable("gtpm.top.recipe_output"));
        }
        addItemTooltips(tooltip, outputItems);
        addFluidTooltips(tooltip, outputFluids);
    }

    private void addItemTooltips(ITooltip tooltip, List<SizedIngredient> outputItems) {
        IElementHelper helper = IElementHelper.get();
        for (SizedIngredient itemOutput : outputItems) {
            if (itemOutput == null || itemOutput.ingredient().hasNoItems()) {
                continue;
            }
            ItemStack item = itemOutput.getItems()[0];
            int count = item.getCount();
            item.setCount(1);

            tooltip.add(helper.smallItem(item));
            MutableComponent text = CommonComponents.space();
            item = itemOutput.getItems()[0];
            text.append(String.valueOf(item.getCount()));
            item.setCount(1);
            text.append(Component.translatable("gtpm.gui.content.times_item",
                    getItemName(item))
                    .withStyle(ChatFormatting.WHITE));

            tooltip.add(helper.smallItem(item));
            tooltip.append(text);
        }
    }

    private void addFluidTooltips(ITooltip tooltip, List<SizedFluidIngredient> outputFluids) {
        for (SizedFluidIngredient fluidOutput : outputFluids) {
            if (fluidOutput == null || fluidOutput.ingredient().hasNoFluids()) {
                continue;
            }
            FluidStack fluid = fluidOutput.getFluids()[0];

            tooltip.add(GTElementHelper.smallFluid(getFluid(fluid)));
            MutableComponent text = CommonComponents.space();
            if (fluidOutput.ingredient() instanceof IntProviderFluidIngredient provider) {
                text.append(Component.translatable("gtpm.gui.content.range",
                        FluidTextHelper.getUnicodeMillibuckets(provider.getCountProvider().getMinValue(), true),
                        FluidTextHelper.getUnicodeMillibuckets(provider.getCountProvider().getMaxValue(), true)));
            } else {
                text.append(FluidTextHelper.getUnicodeMillibuckets(fluidOutput.amount(), true));
            }
            text.append(CommonComponents.space())
                    .append(getFluidName(fluid))
                    .withStyle(ChatFormatting.WHITE);

            tooltip.append(text);
        }
    }

    private Component getItemName(ItemStack stack) {
        return stack.getDisplayName().copy().withStyle(ChatFormatting.WHITE);
    }

    private Component getFluidName(FluidStack stack) {
        return ComponentUtils.wrapInSquareBrackets(stack.getHoverName()).withStyle(ChatFormatting.WHITE);
    }

    private JadeFluidObject getFluid(FluidStack stack) {
        return JadeFluidObject.of(stack.getFluid(), stack.getAmount());
    }

    private static boolean isRangedIngredient(JsonElement data) {
        return data.isJsonObject() && data.getAsJsonObject().has("count_provider");
    }

    private record RecipeOutputData(boolean working, JsonArray items, JsonArray fluids) {

        private static final RecipeOutputData EMPTY = new RecipeOutputData(false, new JsonArray(), new JsonArray());
        private static final RecipeOutputData WORKING_EMPTY = new RecipeOutputData(true, new JsonArray(),
                new JsonArray());

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Working", working);
            if (!items.isEmpty()) {
                tag.putString(OUTPUT_ITEMS, GSON.toJson(items));
            }
            if (!fluids.isEmpty()) {
                tag.putString(OUTPUT_FLUIDS, GSON.toJson(fluids));
            }
            return tag;
        }

        private static RecipeOutputData fromTag(CompoundTag tag) {
            JsonArray items = tag.contains(OUTPUT_ITEMS, CompoundTag.TAG_STRING) ?
                    GSON.fromJson(tag.getString(OUTPUT_ITEMS), JsonArray.class) : new JsonArray();
            JsonArray fluids = tag.contains(OUTPUT_FLUIDS, CompoundTag.TAG_STRING) ?
                    GSON.fromJson(tag.getString(OUTPUT_FLUIDS), JsonArray.class) : new JsonArray();
            return new RecipeOutputData(tag.getBoolean("Working"), items, fluids);
        }
    }
}
