package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraft.world.level.block.Block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Expands recipe IO requirements into hatch predicates for the owning controller.
 *
 * <p>
 * This is the JSON equivalent of {@code Predicates.autoAbilities(recipeTypes, ...)}. It derives recipe types from
 * the controller machine definition at match time so static JSON structures do not need to duplicate recipe metadata.
 */
public record AutoRecipeAbilityPredicate(boolean checkEnergyIn, boolean checkEnergyOut, boolean checkItemIn,
                                         boolean checkItemOut, boolean checkFluidIn, boolean checkFluidOut)
        implements StructurePredicate {

    private static final Map<PartAbility, StructurePredicate> ABILITY_PREDICATES = createAbilityPredicates();

    public static final MapCodec<AutoRecipeAbilityPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    Codec.BOOL.optionalFieldOf("checkEnergyIn", true)
                            .forGetter(AutoRecipeAbilityPredicate::checkEnergyIn),
                    Codec.BOOL.optionalFieldOf("checkEnergyOut", true)
                            .forGetter(AutoRecipeAbilityPredicate::checkEnergyOut),
                    Codec.BOOL.optionalFieldOf("checkItemIn", true)
                            .forGetter(AutoRecipeAbilityPredicate::checkItemIn),
                    Codec.BOOL.optionalFieldOf("checkItemOut", true)
                            .forGetter(AutoRecipeAbilityPredicate::checkItemOut),
                    Codec.BOOL.optionalFieldOf("checkFluidIn", true)
                            .forGetter(AutoRecipeAbilityPredicate::checkFluidIn),
                    Codec.BOOL.optionalFieldOf("checkFluidOut", true)
                            .forGetter(AutoRecipeAbilityPredicate::checkFluidOut))
            .apply(instance, AutoRecipeAbilityPredicate::new));

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.AUTO_RECIPE_ABILITIES;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        boolean matched = false;
        for (StructurePredicate predicate : collectPredicates(multiblockState)) {
            matched |= predicate.test(multiblockState, mutateCount);
        }
        return matched;
    }

    @Override
    public @Unmodifiable List<MultiblockBlockInfo> candidates() {
        return List.of();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return List.of();
    }

    private List<StructurePredicate> collectPredicates(MultiblockState multiblockState) {
        MultiblockControllerMachine controller = multiblockState.getController();
        if (controller == null) {
            throw new IllegalStateException("Auto recipe ability predicates require a multiblock controller");
        }
        GTRecipeType[] recipeTypes = controller.getDefinition().getRecipeTypes();
        List<StructurePredicate> predicates = new ArrayList<>();
        addRecipePredicate(predicates, checkEnergyIn, recipeTypes,
                type -> type.getMaxInputs(EURecipeCapability.CAP) > 0, PartAbility.INPUT_ENERGY, true);
        addRecipePredicate(predicates, checkEnergyOut, recipeTypes,
                type -> type.getMaxOutputs(EURecipeCapability.CAP) > 0, PartAbility.OUTPUT_ENERGY, true);
        addRecipePredicate(predicates, checkItemIn, recipeTypes,
                type -> type.getMaxInputs(ItemRecipeCapability.CAP) > 0, PartAbility.IMPORT_ITEMS, false);
        addRecipePredicate(predicates, checkItemOut, recipeTypes,
                type -> type.getMaxOutputs(ItemRecipeCapability.CAP) > 0, PartAbility.EXPORT_ITEMS, false);
        addRecipePredicate(predicates, checkFluidIn, recipeTypes,
                type -> type.getMaxInputs(FluidRecipeCapability.CAP) > 0, PartAbility.IMPORT_FLUIDS, false);
        addRecipePredicate(predicates, checkFluidOut, recipeTypes,
                type -> type.getMaxOutputs(FluidRecipeCapability.CAP) > 0, PartAbility.EXPORT_FLUIDS, false);
        return predicates;
    }

    private static void addRecipePredicate(List<StructurePredicate> predicates, boolean enabled,
                                           GTRecipeType[] recipeTypes,
                                           Predicate<GTRecipeType> capabilityMatcher,
                                           PartAbility ability,
                                           boolean limitedEnergy) {
        if (!enabled || !hasAny(recipeTypes, capabilityMatcher)) {
            return;
        }
        predicates.add(ABILITY_PREDICATES.get(ability));
    }

    private static boolean hasAny(GTRecipeType[] recipeTypes, Predicate<GTRecipeType> capabilityMatcher) {
        for (GTRecipeType recipeType : recipeTypes) {
            if (capabilityMatcher.test(recipeType)) {
                return true;
            }
        }
        return false;
    }

    private static Map<PartAbility, StructurePredicate> createAbilityPredicates() {
        return Map.of(
                PartAbility.INPUT_ENERGY, limitedEnergyPredicate(PartAbility.INPUT_ENERGY),
                PartAbility.OUTPUT_ENERGY, limitedEnergyPredicate(PartAbility.OUTPUT_ENERGY),
                PartAbility.IMPORT_ITEMS, previewPredicate(PartAbility.IMPORT_ITEMS),
                PartAbility.EXPORT_ITEMS, previewPredicate(PartAbility.EXPORT_ITEMS),
                PartAbility.IMPORT_FLUIDS, previewPredicate(PartAbility.IMPORT_FLUIDS),
                PartAbility.EXPORT_FLUIDS, previewPredicate(PartAbility.EXPORT_FLUIDS));
    }

    private static RestrictedPredicate limitedEnergyPredicate(PartAbility ability) {
        return RestrictedPredicate.builder()
                .base(new AbilityPredicate(List.of(ability)))
                .minCount(1)
                .maxCount(2)
                .previewCount(1)
                .build();
    }

    private static RestrictedPredicate previewPredicate(PartAbility ability) {
        return RestrictedPredicate.builder()
                .base(new AbilityPredicate(List.of(ability)))
                .previewCount(1)
                .build();
    }
}
