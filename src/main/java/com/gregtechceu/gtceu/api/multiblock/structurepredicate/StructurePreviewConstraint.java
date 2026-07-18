package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import java.util.Optional;

/**
 * Describes the count rules that a generated structure preview must satisfy for one predicate branch.
 *
 * @param key             stable predicate instance used by the runtime pattern counters
 * @param minCount        minimum number of matches across the whole structure
 * @param maxCount        maximum number of matches across the whole structure
 * @param minCountByLayer minimum number of matches in each aisle layer containing this constraint
 * @param maxCountByLayer maximum number of matches in each aisle layer containing this constraint
 * @param previewCount    preferred number of appearances when no runtime minimum requires more
 */
public record StructurePreviewConstraint(StructurePredicate key, Optional<Integer> minCount,
                                         Optional<Integer> maxCount, Optional<Integer> minCountByLayer,
                                         Optional<Integer> maxCountByLayer, Optional<Integer> previewCount) {}
