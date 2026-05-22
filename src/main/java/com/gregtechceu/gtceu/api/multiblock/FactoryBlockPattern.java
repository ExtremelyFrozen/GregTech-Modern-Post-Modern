package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.predicates.PredicateController;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.data.pattern.StructureCache;

import net.minecraft.resources.ResourceLocation;

import com.fasterxml.jackson.databind.JsonNode;
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
    private int definitionAisleIndex;
    private int aisleHeight;
    private int rowWidth;

    private record AisleUnit(List<String[]> slices, int minRepeat, int maxRepeat) {}

    public record JsonAisleUnit(List<String[]> slices, int minRepeat, int maxRepeat) {}

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

    public FactoryBlockPattern aisleFromDefinition() {
        if (definition == null) {
            throw new IllegalStateException("No multiblock definition was bound to this pattern builder");
        }
        return aisleFromDefinition(definition.getId(), definitionAisleIndex++);
    }

    public FactoryBlockPattern aisleFromDefinition(int index) {
        if (definition == null) {
            throw new IllegalStateException("No multiblock definition was bound to this pattern builder");
        }
        return aisleFromDefinition(definition.getId(), index);
    }

    public FactoryBlockPattern aisleFromDefinition(MultiblockMachineDefinition definition) {
        return aisleFromDefinition(definition.getId(), definitionAisleIndex++);
    }

    public FactoryBlockPattern aisleFromDefinition(MultiblockMachineDefinition definition, int index) {
        return aisleFromDefinition(definition.getId(), index);
    }

    public FactoryBlockPattern aisleFromDefinition(ResourceLocation id, int index) {
        List<String[]> aisles = parsePlainDefinitionAisles(id, readDefinitionJson(id));
        if (index < 0 || index >= aisles.size()) {
            throw new IllegalArgumentException("Json structure definition for " + id + " does not contain aisle " +
                    index + "; found " + aisles.size() + " aisles");
        }
        return aisle(aisles.get(index));
    }

    public FactoryBlockPattern aislesFromDefinition() {
        if (definition == null) {
            throw new IllegalStateException("No multiblock definition was bound to this pattern builder");
        }
        return aislesFromDefinition(definition.getId());
    }

    public FactoryBlockPattern aislesFromDefinition(MultiblockMachineDefinition definition) {
        return aislesFromDefinition(definition.getId());
    }

    public FactoryBlockPattern aislesFromDefinition(ResourceLocation id) {
        for (JsonAisleUnit unit : parseJsonDefinition(id, readDefinitionJson(id))) {
            addUnit(unit.slices(), unit.minRepeat(), unit.maxRepeat());
        }
        return this;
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

    private FactoryBlockPattern addUnit(List<String[]> slices, int minRepeat, int maxRepeat) {
        if (repeatableGroup != null) {
            throw new IllegalStateException("Cannot add a json structure unit inside a repeatable aisle group");
        }
        if (slices.isEmpty()) {
            throw new IllegalArgumentException("Repeatable aisle group must contain at least one aisle");
        }
        if (minRepeat > maxRepeat) {
            throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
        }

        List<String[]> copiedSlices = new ArrayList<>(slices.size());
        for (String[] slice : slices) {
            validateAisle(slice);
            copiedSlices.add(copyAisle(slice));
        }
        units.add(new AisleUnit(List.copyOf(copiedSlices), minRepeat, maxRepeat));
        return this;
    }

    private JsonNode readDefinitionJson(ResourceLocation id) {
        JsonNode jsonPattern = StructureCache.getStringArrayPattern(id);
        if (jsonPattern == null) {
            throw new IllegalStateException(
                    "Json structure definition for " + id + " was not found in structure cache");
        }
        return jsonPattern;
    }

    public static List<JsonAisleUnit> parseJsonDefinition(ResourceLocation id, JsonNode jsonPattern) {
        if (!jsonPattern.isArray() || jsonPattern.isEmpty()) {
            throw new IllegalArgumentException("Json structure definition for " + id +
                    " must be a non-empty string array, array of string arrays, or array of structure units");
        }

        JsonNode first = jsonPattern.get(0);
        if (first.isTextual()) {
            List<String[]> aisles = new ArrayList<>();
            aisles.add(parseAisle(id, jsonPattern));
            validateAisles(id, aisles);
            return List.of(new JsonAisleUnit(aisles, 1, 1));
        }

        List<JsonAisleUnit> units = new ArrayList<>();
        for (JsonNode unitNode : jsonPattern) {
            if (unitNode.isArray()) {
                List<String[]> aisles = new ArrayList<>();
                aisles.add(parseAisle(id, unitNode));
                validateAisles(id, aisles);
                units.add(new JsonAisleUnit(aisles, 1, 1));
            } else if (unitNode.isObject()) {
                units.add(parseRepeatUnit(id, unitNode));
            } else {
                throw new IllegalArgumentException("Json structure definition for " + id +
                        " must contain string-array aisles or repeat objects");
            }
        }
        validateAisles(id, units.stream().flatMap(unit -> unit.slices().stream()).toList());
        return List.copyOf(units);
    }

    private static List<String[]> parsePlainDefinitionAisles(ResourceLocation id, JsonNode jsonPattern) {
        List<String[]> aisles = parseJsonDefinition(id, jsonPattern).stream()
                .flatMap(unit -> unit.slices().stream())
                .toList();
        validateAisles(id, aisles);
        return aisles;
    }

    private static JsonAisleUnit parseRepeatUnit(ResourceLocation id, JsonNode unitNode) {
        JsonNode aislesNode = unitNode.get("aisles");
        if (aislesNode == null || !aislesNode.isArray() || aislesNode.isEmpty()) {
            throw new IllegalArgumentException("Json repeat unit for " + id +
                    " must contain a non-empty 'aisles' array");
        }

        List<String[]> aisles = new ArrayList<>();
        JsonNode first = aislesNode.get(0);
        if (first.isTextual()) {
            aisles.add(parseAisle(id, aislesNode));
        } else {
            for (JsonNode aisleNode : aislesNode) {
                aisles.add(parseAisle(id, aisleNode));
            }
        }
        validateAisles(id, aisles);

        int minRepeat = 1;
        int maxRepeat = 1;
        JsonNode repeatNode = unitNode.get("repeat");
        if (repeatNode != null) {
            if (repeatNode.isInt()) {
                minRepeat = maxRepeat = repeatNode.asInt();
            } else if (repeatNode.isArray() && repeatNode.size() == 2 &&
                    repeatNode.get(0).isInt() && repeatNode.get(1).isInt()) {
                        minRepeat = repeatNode.get(0).asInt();
                        maxRepeat = repeatNode.get(1).asInt();
                    } else {
                        throw new IllegalArgumentException("Json repeat unit for " + id +
                                " must use integer repeat or [min, max] repeat");
                    }
        } else {
            JsonNode minNode = unitNode.get("min");
            JsonNode maxNode = unitNode.get("max");
            if (minNode != null || maxNode != null) {
                if (minNode == null || maxNode == null || !minNode.isInt() || !maxNode.isInt()) {
                    throw new IllegalArgumentException("Json repeat unit for " + id +
                            " must define integer 'min' and 'max' together");
                }
                minRepeat = minNode.asInt();
                maxRepeat = maxNode.asInt();
            }
        }
        if (minRepeat > maxRepeat) {
            throw new IllegalArgumentException("Json repeat unit for " + id +
                    " has repeat min greater than repeat max");
        }
        return new JsonAisleUnit(List.copyOf(aisles), minRepeat, maxRepeat);
    }

    private static String[] parseAisle(ResourceLocation id, JsonNode aisleNode) {
        if (!aisleNode.isArray() || aisleNode.isEmpty()) {
            throw new IllegalArgumentException("Json structure aisle for " + id + " must be a non-empty string array");
        }

        String[] aisle = new String[aisleNode.size()];
        for (int row = 0; row < aisleNode.size(); row++) {
            JsonNode rowNode = aisleNode.get(row);
            if (!rowNode.isTextual()) {
                throw new IllegalArgumentException("Json structure aisle row " + row + " for " + id +
                        " must be a string");
            }
            aisle[row] = rowNode.asText();
        }
        return aisle;
    }

    private static void validateAisles(ResourceLocation id, List<String[]> aisles) {
        int aisleHeight = aisles.getFirst().length;
        int rowWidth = aisles.getFirst()[0].length();
        if (rowWidth == 0) {
            throw new IllegalArgumentException("Json structure definition for " + id + " must not contain empty rows");
        }

        for (int aisleIndex = 0; aisleIndex < aisles.size(); aisleIndex++) {
            String[] aisle = aisles.get(aisleIndex);
            if (aisle.length != aisleHeight) {
                throw new IllegalArgumentException("Json structure aisle " + aisleIndex + " for " + id +
                        " has height " + aisle.length + ", expected " + aisleHeight);
            }
            for (int row = 0; row < aisle.length; row++) {
                String rowText = aisle[row];
                if (rowText.length() != rowWidth) {
                    throw new IllegalArgumentException("Json structure aisle " + aisleIndex + ", row " + row +
                            " for " + id + " has width " + rowText.length() + ", expected " + rowWidth);
                }
            }
        }
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
