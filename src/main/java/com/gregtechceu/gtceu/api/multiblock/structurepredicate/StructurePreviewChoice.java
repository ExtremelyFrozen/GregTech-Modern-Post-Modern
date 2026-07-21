package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.data.lang.LangHandler;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One selectable block family for structure preview generation, together with every nested restriction it carries.
 *
 * @param candidates  ordered block variants; the generator keeps one stable variant throughout a preview page
 * @param constraints nested count constraints applied when this choice is selected
 * @param tooltips    additional candidate descriptions supplied by serialized predicates
 */
public record StructurePreviewChoice(List<MultiblockBlockInfo> candidates,
                                     List<StructurePreviewConstraint> constraints,
                                     List<Component> tooltips) {

    public StructurePreviewChoice {
        candidates = List.copyOf(candidates);
        constraints = List.copyOf(constraints);
        tooltips = List.copyOf(tooltips);
    }

    /**
     * Creates an unrestricted choice for a leaf predicate.
     */
    public static StructurePreviewChoice unrestricted(List<MultiblockBlockInfo> candidates) {
        return new StructurePreviewChoice(candidates, List.of(), List.of());
    }

    /**
     * Adds an enclosing restriction without discarding constraints owned by nested predicates.
     */
    public StructurePreviewChoice restrictedBy(StructurePreviewConstraint constraint,
                                               List<Component> additionalTooltips) {
        List<StructurePreviewConstraint> combinedConstraints = new ArrayList<>(constraints.size() + 1);
        combinedConstraints.addAll(constraints);
        combinedConstraints.add(constraint);
        List<Component> combinedTooltips = new ArrayList<>(tooltips.size() + additionalTooltips.size());
        combinedTooltips.addAll(tooltips);
        combinedTooltips.addAll(additionalTooltips);
        return new StructurePreviewChoice(candidates, combinedConstraints, combinedTooltips);
    }

    /**
     * Builds the candidate tooltip list, including serialized count rules and the usual predicate hints.
     */
    public List<Component> getTooltips(TraceabilityPredicate owner) {
        List<Component> result = new ArrayList<>(tooltips);
        for (StructurePreviewConstraint constraint : constraints) {
            int min = constraint.minCount().orElse(-1);
            int max = constraint.maxCount().orElse(-1);
            if (min == max && max >= 0) {
                result.add(Component.translatable("gtpm.multiblock.pattern.error.limited_exact", min));
            } else if (min >= 0 && max >= 0) {
                result.add(Component.translatable("gtpm.multiblock.pattern.error.limited_within", min, max));
            } else {
                if (min >= 0) {
                    result.add(LangHandler.getFromMultiLang("gtpm.multiblock.pattern.error.limited", 1, min));
                }
                if (max >= 0) {
                    result.add(LangHandler.getFromMultiLang("gtpm.multiblock.pattern.error.limited", 0, max));
                }
            }
        }
        if (owner.isSingle()) {
            result.add(Component.translatable("gtpm.multiblock.pattern.single"));
        }
        if (owner.hasAir()) {
            result.add(Component.translatable("gtpm.multiblock.pattern.replaceable_air"));
        }
        return result;
    }
}
