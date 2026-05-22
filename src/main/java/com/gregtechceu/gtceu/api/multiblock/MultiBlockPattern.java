package com.gregtechceu.gtceu.api.multiblock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * File-backed multiblock definition DTO.
 * <p>
 * This class intentionally focuses on storage shape only, so structure definitions can be
 * serialized to or deserialized from {@code pattern/<modid>/json/*.json}.
 * Runtime conversion to {@link BlockPattern} is expected to be handled separately during the
 * structure-definition refactor.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MultiBlockPattern {

    public MultiBlockPattern() {}

    private StructureDir structureDir;
    private CenterOffset centerOffset;
    private int thumbLength;
    private int palmLength;
    private List<Unit> units = new ArrayList<>();

    public MultiBlockPattern(BlockPattern pattern) {
        this.structureDir = pattern.structureDir;
        this.centerOffset = pattern.centerOffset;
        this.thumbLength = pattern.thumbLength;
        this.palmLength = pattern.palmLength;
        for (int i = 0; i < pattern.aisleRepetitions.length; i++) {
            int start = pattern.unitStarts[i];
            int depth = pattern.unitDepths[i];
            List<String[]> slices = new ArrayList<>(depth);
            List<TraceabilityPredicate[][]> predicates = pattern.blockMatches == null ? null : new ArrayList<>(depth);
            for (int inner = 0; inner < depth; inner++) {
                slices.add(pattern.structureSlices == null ? null : pattern.structureSlices[start + inner]);
                if (predicates != null) {
                    predicates.add(pattern.blockMatches[start + inner]);
                }
            }
            int[] repetition = pattern.aisleRepetitions[i];
            units.add(new Unit(slices, predicates, new Repeat(repetition[0], repetition[1])));
        }
    }

    public BlockPattern toBlockPattern() {
        int size = units.stream().mapToInt(unit -> unit.getSlices().size()).sum();
        TraceabilityPredicate[][][] blockMatches = new TraceabilityPredicate[size][][];
        String[][] structureSlices = new String[size][];
        int[][] aisleRepetitions = new int[units.size()][];
        int[] unitStarts = new int[units.size()];
        int[] unitDepths = new int[units.size()];

        int sliceIndex = 0;
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (unit.getPredicates() == null || unit.getPredicates().size() != unit.getSlices().size()) {
                throw new IllegalStateException("Serialized binary multiblock pattern is missing predicate slices");
            }
            Repeat repeat = unit.getRepeat() == null ? new Repeat(1, 1) : unit.getRepeat();
            if (repeat.getMin() > repeat.getMax()) {
                throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
            }
            unitStarts[i] = sliceIndex;
            unitDepths[i] = unit.getSlices().size();
            aisleRepetitions[i] = new int[] { repeat.getMin(), repeat.getMax() };
            for (int inner = 0; inner < unit.getSlices().size(); inner++) {
                structureSlices[sliceIndex] = unit.getSlices().get(inner);
                if (unit.getPredicates() != null) {
                    blockMatches[sliceIndex] = unit.getPredicates().get(inner);
                }
                sliceIndex++;
            }
        }

        return new BlockPattern(blockMatches, structureDir, aisleRepetitions, unitStarts, unitDepths, structureSlices,
                centerOffset, size, thumbLength, palmLength);
    }

    public FactoryBlockPattern applyTo(FactoryBlockPattern builder) {
        for (Unit unit : units) {
            Repeat repeat = unit.getRepeat() == null ? new Repeat(1, 1) : unit.getRepeat();
            if (repeat.getMin() == 1 && repeat.getMax() == 1 && unit.getSlices().size() == 1) {
                builder.aisle(unit.getSlices().get(0));
            } else {
                builder.beginRepeatable();
                for (String[] slice : unit.getSlices()) {
                    builder.aisle(slice);
                }
                builder.endRepeatable(repeat.getMin(), repeat.getMax());
            }
        }
        return builder;
    }

    public MultiBlockPattern withoutPredicates() {
        for (Unit unit : units) {
            unit.setPredicates(null);
        }
        return this;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Unit {

        private List<String[]> slices = new ArrayList<>();
        private List<TraceabilityPredicate[][]> predicates;
        private Repeat repeat;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Repeat {

        private int min = 1;
        private int max = 1;
    }
}
