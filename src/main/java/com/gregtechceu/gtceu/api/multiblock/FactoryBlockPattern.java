package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.predicates.PredicateController;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;

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
    private final List<String[]> depth;
    private final List<int[]> aisleRepetitions;
    private final Char2ObjectMap<TraceabilityPredicate> symbolMap;
    private final StructureDir structureDir;
    private PatternCondition condition;
    private int aisleHeight;
    private int rowWidth;

    private FactoryBlockPattern(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir,
                                MultiblockMachineDefinition definition) {
        this.definition = definition;
        depth = new ArrayList<>();
        aisleRepetitions = new ArrayList<>();
        symbolMap = new Char2ObjectArrayMap<>();
        structureDir = new StructureDir(charDir, stringDir, aisleDir);
        structureDir.check();
        this.symbolMap.put(' ', Predicates.any());
    }

    /**
     * Adds a repeatable aisle to this pattern.
     */
    public FactoryBlockPattern aisleRepeatable(int minRepeat, int maxRepeat, String... aisle) {
        if (!ArrayUtils.isEmpty(aisle) && !StringUtils.isEmpty(aisle[0])) {
            if (this.depth.isEmpty()) {
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

                this.depth.add(aisle);
                if (minRepeat > maxRepeat)
                    throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
                aisleRepetitions.add(new int[] { minRepeat, maxRepeat });
                return this;
            }
        } else {
            throw new IllegalArgumentException("Empty pattern for aisle");
        }
    }

    /**
     * Adds a single aisle to this pattern. (so multiple calls to this will increase the aisleDir by 1)
     */
    public FactoryBlockPattern aisle(String... aisle) {
        return aisleRepeatable(1, 1, aisle);
    }

    /**
     * Set last aisle repeatable
     */
    public FactoryBlockPattern setRepeatable(int minRepeat, int maxRepeat) {
        if (minRepeat > maxRepeat)
            throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
        aisleRepetitions.set(aisleRepetitions.size() - 1, new int[] { minRepeat, maxRepeat });
        return this;
    }

    /**
     * Set last aisle repeatable
     */
    public FactoryBlockPattern setRepeatable(int repeatCount) {
        return setRepeatable(repeatCount, repeatCount);
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
        this.checkMissingPredicates();
        int size = this.depth.size();
        CenterOffset centerOffset = null;
        int[][] aisleRepetitions = this.aisleRepetitions.toArray(new int[this.aisleRepetitions.size()][]);
        TraceabilityPredicate[][][] predicate = new TraceabilityPredicate[size][][];

        for (int i = 0, minZ = 0, maxZ = 0; i <
                size; minZ += aisleRepetitions[i][0], maxZ += aisleRepetitions[i][1], i++) {
            for (int j = 0; j < this.aisleHeight; j++) {
                for (int k = 0; k < this.rowWidth; k++) {
                    var tp = this.symbolMap.get(this.depth.get(i)[j].charAt(k));
                    if (tp != null) {
                        var pi = predicate[i];
                        if (pi == null) {
                            predicate[i] = pi = new TraceabilityPredicate[this.aisleHeight][];
                        }
                        var pj = pi[j];
                        if (pj == null) {
                            pi[j] = pj = new TraceabilityPredicate[this.rowWidth];
                        }
                        pj[k] = tp;
                        if (tp instanceof PredicateController) centerOffset = new CenterOffset(k, j, i, minZ, maxZ);
                    }
                }
            }
        }

        var pattern = new BlockPattern(predicate, structureDir, aisleRepetitions, centerOffset, size, this.aisleHeight,
                this.rowWidth);
        if (condition != null) pattern.condition = condition;
        if (definition != null) {
            pattern.predicates = symbolMap.values();
            // definition.setCheckPriority(-(pattern.fingerLength * pattern.thumbLength * pattern.palmLength));
        }
        return pattern;
    }
}
