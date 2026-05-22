package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.predicates.PredicateController;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;

import com.google.common.base.Joiner;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.CharArrayList;
import it.unimi.dsi.fastutil.chars.CharList;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class FactoryBlockPattern {

    private static final Joiner COMMA_JOIN = Joiner.on(",");
    private final MultiblockMachineDefinition definition;
    private final List<AisleUnit> units;
    private final Char2ObjectMap<TraceabilityPredicate> symbolMap;
    private final StructureDir structureDir;
    private List<String[]> repeatableGroup;
    private PatternCondition condition;
    private int aisleHeight;
    private int rowWidth;

    private record AisleUnit(List<String[]> slices, int minRepeat, int maxRepeat) {}

    private FactoryBlockPattern(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir,
                                MultiblockMachineDefinition definition) {
        this.definition = definition;
        units = new ArrayList<>();
        symbolMap = new Char2ObjectArrayMap<>();
        structureDir = new StructureDir(charDir, stringDir, aisleDir);
        structureDir.check();
        this.symbolMap.put(' ', Predicates.any());
    }

    /**
     * Adds a single aisle to this pattern. Multiple calls increase the aisleDir by 1.
     */
    public FactoryBlockPattern aisle(String... aisle) {
        validateAisle(aisle);
        if (repeatableGroup == null) {
            units.add(new AisleUnit(List.<String[]>of(copyAisle(aisle)), 1, 1));
        } else {
            repeatableGroup.add(copyAisle(aisle));
        }
        return this;
    }

    public FactoryBlockPattern aisleFromDefinition(String... aisle) {
        return aisle(aisle);
    }

    public FactoryBlockPattern beginRepeatable() {
        if (repeatableGroup != null) {
            throw new IllegalStateException("Cannot begin a nested repeatable aisle group");
        }
        repeatableGroup = new ArrayList<>();
        return this;
    }

    public FactoryBlockPattern endRepeatable(int minRepeat, int maxRepeat) {
        if (repeatableGroup == null) {
            throw new IllegalStateException("No repeatable aisle group has been started");
        }
        if (repeatableGroup.isEmpty()) {
            throw new IllegalStateException("Repeatable aisle group must contain at least one aisle");
        }
        if (minRepeat > maxRepeat) {
            throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
        }
        units.add(new AisleUnit(List.copyOf(repeatableGroup), minRepeat, maxRepeat));
        repeatableGroup = null;
        return this;
    }

    public FactoryBlockPattern endRepeatable(int repeatCount) {
        return endRepeatable(repeatCount, repeatCount);
    }

    private void validateAisle(String... aisle) {
        if (!ArrayUtils.isEmpty(aisle) && !StringUtils.isEmpty(aisle[0])) {
            if (this.units.isEmpty() && (repeatableGroup == null || repeatableGroup.isEmpty())) {
                this.aisleHeight = aisle.length;
                this.rowWidth = aisle[0].length();
            }

            if (aisle.length != this.aisleHeight) {
                throw new IllegalArgumentException("Expected aisle with height of " + this.aisleHeight +
                        ", but was given one with a height of " + aisle.length + ")");
            } else {
                for (String s : aisle) {
                    if (s.length() != this.rowWidth) {
                        throw new IllegalArgumentException(
                                "Not all rows in the given aisle are the correct width (expected " + this.rowWidth +
                                        ", found one with " + s.length() + ")");
                    }

                    for (char c0 : s.toCharArray()) {
                        if (!this.symbolMap.containsKey(c0)) {
                            this.symbolMap.put(c0, null);
                        }
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("Empty pattern for aisle");
        }
    }

    private String[] copyAisle(String[] aisle) {
        return aisle.clone();
    }

    public static FactoryBlockPattern start() {
        return new FactoryBlockPattern(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT, null);
    }

    public static FactoryBlockPattern start(MultiblockMachineDefinition definition) {
        return new FactoryBlockPattern(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT,
                definition);
    }

    public static FactoryBlockPattern start(RelativeDirection charDir, RelativeDirection stringDir,
                                            RelativeDirection aisleDir) {
        return new FactoryBlockPattern(charDir, stringDir, aisleDir, null);
    }

    public static FactoryBlockPattern start(MultiblockMachineDefinition definition, RelativeDirection charDir,
                                            RelativeDirection stringDir,
                                            RelativeDirection aisleDir) {
        return new FactoryBlockPattern(charDir, stringDir, aisleDir, definition);
    }

    public FactoryBlockPattern where(String symbol, TraceabilityPredicate blockMatcher) {
        return this.where(symbol.charAt(0), blockMatcher);
    }

    public FactoryBlockPattern where(char symbol, TraceabilityPredicate blockMatcher) {
        if (blockMatcher.isAny() || blockMatcher.isAir()) {
            this.symbolMap.put(symbol, blockMatcher);
        } else if (blockMatcher instanceof PredicateController) {
            this.symbolMap.put(symbol, blockMatcher.sort());
        } else {
            this.symbolMap.put(symbol, new TraceabilityPredicate(blockMatcher).sort());
        }
        return this;
    }

    public FactoryBlockPattern condition(Predicate<MultiblockState> condition) {
        return condition(condition, "gtceu.recipe_logic.condition_fails");
    }

    public FactoryBlockPattern condition(Predicate<MultiblockState> condition, String translateKey) {
        this.condition = new PatternCondition(condition, translateKey);
        return this;
    }

    private void checkMissingPredicates() {
        CharList list = new CharArrayList();

        for (var entry : this.symbolMap.char2ObjectEntrySet()) {
            if (entry.getValue() == null) {
                list.add(entry.getCharKey());
            }
        }

        if (!list.isEmpty()) {
            throw new IllegalStateException("Predicates for character(s) " + COMMA_JOIN.join(list) + " are missing");
        }
    }

    public BlockPattern build() {
        if (repeatableGroup != null) {
            throw new IllegalStateException("Repeatable aisle group must be closed before building the pattern");
        }
        this.checkMissingPredicates();
        int unitCount = this.units.size();
        int size = this.units.stream().mapToInt(unit -> unit.slices().size()).sum();
        CenterOffset centerOffset = null;
        int[][] aisleRepetitions = new int[unitCount][];
        int[] unitStarts = new int[unitCount];
        int[] unitDepths = new int[unitCount];
        String[][] structureSlices = new String[size][];
        TraceabilityPredicate[][][] predicate = new TraceabilityPredicate[size][][];

        for (int unitIndex = 0, sliceIndex = 0, minZ = 0, maxZ = 0; unitIndex < unitCount; unitIndex++) {
            AisleUnit unit = units.get(unitIndex);
            int unitDepth = unit.slices().size();
            unitStarts[unitIndex] = sliceIndex;
            unitDepths[unitIndex] = unitDepth;
            aisleRepetitions[unitIndex] = new int[] { unit.minRepeat(), unit.maxRepeat() };

            for (int inner = 0; inner < unitDepth; inner++, sliceIndex++) {
                String[] aisle = unit.slices().get(inner);
                structureSlices[sliceIndex] = aisle.clone();
                for (int j = 0; j < this.aisleHeight; j++) {
                    for (int k = 0; k < this.rowWidth; k++) {
                        var tp = this.symbolMap.get(aisle[j].charAt(k));
                        if (tp != null) {
                            var pi = predicate[sliceIndex];
                            if (pi == null) {
                                predicate[sliceIndex] = pi = new TraceabilityPredicate[this.aisleHeight][];
                            }
                            var pj = pi[j];
                            if (pj == null) {
                                pi[j] = pj = new TraceabilityPredicate[this.rowWidth];
                            }
                            pj[k] = tp;
                            if (tp instanceof PredicateController) {
                                centerOffset = new CenterOffset(k, j, sliceIndex, minZ + inner, maxZ + inner);
                            }
                        }
                    }
                }
            }

            minZ += unitDepth * unit.minRepeat();
            maxZ += unitDepth * unit.maxRepeat();
        }

        var pattern = new BlockPattern(predicate, structureDir, aisleRepetitions, unitStarts, unitDepths,
                structureSlices, centerOffset, size, this.aisleHeight, this.rowWidth);
        if (condition != null) pattern.condition = condition;
        if (definition != null) {
            pattern.predicates = symbolMap.values();
            // definition.setCheckPriority(-(pattern.fingerLength * pattern.thumbLength * pattern.palmLength));
        }
        return pattern;
    }
}
