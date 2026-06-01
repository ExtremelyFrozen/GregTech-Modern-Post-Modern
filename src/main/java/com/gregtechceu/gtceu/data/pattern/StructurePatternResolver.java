package com.gregtechceu.gtceu.data.pattern;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.CenterOffset;
import com.gregtechceu.gtceu.api.multiblock.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.MultiBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.multiblock.predicates.PredicateController;
import com.gregtechceu.gtceu.api.pattern.structurepredicate.StructurePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class StructurePatternResolver {

    private StructurePatternResolver() {}

    public static BlockPattern resolveCachedPattern(MultiblockMachineDefinition definition,
                                                    Supplier<BlockPattern> baselinePattern) {
        ResourceLocation id = definition.getId();
        MultiBlockPattern serializedPattern = StructureCache.getSerializedBlockPattern(id);
        if (serializedPattern != null) {
            BlockPattern pattern = serializedPattern.toBlockPattern();
            pattern.condition = baselinePattern.get().condition;
            return pattern;
        }

        JsonNode stringArrayPattern = StructureCache.getStringArrayPattern(id);
        if (stringArrayPattern != null) {
            return rebuildStringArrayPattern(definition, id, baselinePattern.get(), parseStringArrayDefinition(id,
                    stringArrayPattern));
        }

        return baselinePattern.get();
    }

    public static FactoryBlockPattern applyStringArrayDefinition(FactoryBlockPattern builder, ResourceLocation id) {
        return loadStringArrayDefinition(id).applyTo(builder);
    }

    public static StringArrayDefinition loadStringArrayDefinition(ResourceLocation id) {
        JsonNode jsonPattern = StructureCache.getStringArrayPattern(id);
        if (jsonPattern == null) {
            throw new IllegalStateException("Json structure definition for " + id +
                    " was not found in structure cache");
        }
        return parseStringArrayDefinition(id, jsonPattern);
    }

    public static StringArrayDefinition parseStringArrayDefinition(ResourceLocation id, JsonNode jsonPattern) {
        JsonNode predicatesNode = null;
        if (jsonPattern.isObject()) {
            predicatesNode = jsonPattern.get("predicates");
            jsonPattern = jsonPattern.get("aisles");
        }

        if (jsonPattern == null || !jsonPattern.isArray() || jsonPattern.isEmpty()) {
            throw new IllegalArgumentException("Json structure definition for " + id +
                    " must be a non-empty string array, array of string arrays, array of structure units, " +
                    "or an object with 'aisles'");
        }

        Map<Character, TraceabilityPredicate> predicates = parsePredicates(id, predicatesNode);
        JsonNode first = jsonPattern.get(0);
        if (first.isTextual()) {
            List<String[]> aisles = new ArrayList<>();
            aisles.add(parseAisle(id, jsonPattern));
            validateAisles(id, aisles);
            return new StringArrayDefinition(List.of(new Unit(aisles, 1, 1)), predicates);
        }

        List<Unit> units = new ArrayList<>();
        for (JsonNode unitNode : jsonPattern) {
            if (unitNode.isArray()) {
                List<String[]> aisles = new ArrayList<>();
                aisles.add(parseAisle(id, unitNode));
                validateAisles(id, aisles);
                units.add(new Unit(aisles, 1, 1));
            } else if (unitNode.isObject()) {
                units.add(parseRepeatUnit(id, unitNode));
            } else {
                throw new IllegalArgumentException("Json structure definition for " + id +
                        " must contain string-array aisles or repeat objects");
            }
        }
        validateAisles(id, units.stream().flatMap(unit -> unit.slices().stream()).toList());
        return new StringArrayDefinition(List.copyOf(units), predicates);
    }

    private static Unit parseRepeatUnit(ResourceLocation id, JsonNode unitNode) {
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
        return new Unit(List.copyOf(aisles), minRepeat, maxRepeat);
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

    private static BlockPattern rebuildStringArrayPattern(MultiblockMachineDefinition owner, ResourceLocation id,
                                                          BlockPattern baselinePattern,
                                                          StringArrayDefinition definition) {
        List<String[]> aisles = definition.aisles();
        MultiBlockPattern baselineDefinition = new MultiBlockPattern(baselinePattern);
        Map<Character, TraceabilityPredicate> baselinePredicates = collectPredicates(id, baselineDefinition);
        Map<Character, TraceabilityPredicate> predicates = collectPredicates(owner, id, aisles, definition.predicates(),
                baselinePredicates);

        int aisleHeight = aisles.getFirst().length;
        int rowWidth = aisles.getFirst()[0].length();
        int aisleCount = aisles.size();
        int unitCount = definition.units().size();

        TraceabilityPredicate[][][] blockMatches = new TraceabilityPredicate[aisleCount][aisleHeight][rowWidth];
        String[][] structureSlices = new String[aisleCount][];
        int[][] aisleRepetitions = new int[unitCount][];
        int[] unitStarts = new int[unitCount];
        int[] unitDepths = new int[unitCount];
        CenterOffset centerOffset = null;

        for (int unitIndex = 0, aisleIndex = 0, minZ = 0, maxZ = 0; unitIndex < unitCount; unitIndex++) {
            Unit unit = definition.units().get(unitIndex);
            int unitDepth = unit.slices().size();

            unitStarts[unitIndex] = aisleIndex;
            unitDepths[unitIndex] = unitDepth;
            aisleRepetitions[unitIndex] = new int[] { unit.minRepeat(), unit.maxRepeat() };

            for (int inner = 0; inner < unitDepth; inner++, aisleIndex++) {
                String[] aisle = aisles.get(aisleIndex);
                structureSlices[aisleIndex] = aisle.clone();
                for (int row = 0; row < aisleHeight; row++) {
                    for (int column = 0; column < rowWidth; column++) {
                        char symbol = aisle[row].charAt(column);
                        TraceabilityPredicate predicate = predicates.get(symbol);
                        if (predicate == null) {
                            throw new IllegalArgumentException("Unknown structure symbol '" + symbol +
                                    "' in json structure definition for " + id);
                        }
                        blockMatches[aisleIndex][row][column] = predicate;
                        if (predicate instanceof PredicateController) {
                            centerOffset = new CenterOffset(column, row, aisleIndex, minZ + inner, maxZ + inner);
                        }
                    }
                }
            }

            minZ += unitDepth * unit.minRepeat();
            maxZ += unitDepth * unit.maxRepeat();
        }

        if (centerOffset == null) {
            throw new IllegalArgumentException("Json structure definition for " + id +
                    " does not contain a controller predicate symbol");
        }

        BlockPattern pattern = new BlockPattern(blockMatches, baselineDefinition.getStructureDir(), aisleRepetitions,
                unitStarts, unitDepths, structureSlices, centerOffset, aisleCount, aisleHeight, rowWidth);
        pattern.condition = baselinePattern.condition;
        pattern.predicates = List.copyOf(predicates.values());
        return pattern;
    }

    private static Map<Character, TraceabilityPredicate> collectPredicates(ResourceLocation id,
                                                                           MultiBlockPattern baselineDefinition) {
        Map<Character, TraceabilityPredicate> predicates = new LinkedHashMap<>();
        predicates.put(' ', Predicates.any());
        for (MultiBlockPattern.Unit unit : baselineDefinition.getUnits()) {
            if (unit.getPredicates() == null || unit.getPredicates().size() != unit.getSlices().size()) {
                throw new IllegalStateException("Baseline multiblock pattern for " + id +
                        " is missing predicate slices");
            }
            for (int slice = 0; slice < unit.getSlices().size(); slice++) {
                collectPredicatesFromSlice(id, predicates, unit.getSlices().get(slice),
                        unit.getPredicates().get(slice));
            }
        }
        return predicates;
    }

    private static Map<Character, TraceabilityPredicate> collectPredicates(MultiblockMachineDefinition owner,
                                                                           ResourceLocation id, List<String[]> aisles,
                                                                           Map<Character, TraceabilityPredicate> jsonPredicates,
                                                                           Map<Character, TraceabilityPredicate> baselinePredicates) {
        if (jsonPredicates.isEmpty()) {
            return baselinePredicates;
        }

        Map<Character, TraceabilityPredicate> predicates = new LinkedHashMap<>();
        predicates.put(' ', Predicates.any());
        for (String[] aisle : aisles) {
            for (String row : aisle) {
                for (int column = 0; column < row.length(); column++) {
                    char symbol = row.charAt(column);
                    if (symbol == ' ' || predicates.containsKey(symbol)) {
                        continue;
                    }
                    if (symbol == '~') {
                        predicates.put(symbol, defaultControllerPredicate(owner));
                        continue;
                    }
                    TraceabilityPredicate baselinePredicate = baselinePredicates.get(symbol);
                    if (baselinePredicate instanceof PredicateController) {
                        predicates.put(symbol, baselinePredicate);
                        continue;
                    }
                    TraceabilityPredicate jsonPredicate = jsonPredicates.get(symbol);
                    if (jsonPredicate != null) {
                        predicates.put(symbol, jsonPredicate);
                        continue;
                    }
                    throw new IllegalArgumentException("Json structure definition for " + id +
                            " uses symbol '" + symbol + "' without a serialized predicate");
                }
            }
        }
        return predicates;
    }

    private static TraceabilityPredicate defaultControllerPredicate(MultiblockMachineDefinition owner) {
        return Predicates.controller(Predicates.blocks(owner.getBlock()));
    }

    private static void collectPredicatesFromSlice(ResourceLocation id,
                                                   Map<Character, TraceabilityPredicate> predicates,
                                                   String[] rows, TraceabilityPredicate[][] predicateRows) {
        if (predicateRows == null || predicateRows.length != rows.length) {
            throw new IllegalStateException("Baseline multiblock pattern for " + id +
                    " has malformed predicate rows");
        }

        for (int row = 0; row < rows.length; row++) {
            String rowText = rows[row];
            if (predicateRows[row] == null || predicateRows[row].length != rowText.length()) {
                throw new IllegalStateException("Baseline multiblock pattern for " + id +
                        " has malformed predicate columns");
            }
            for (int column = 0; column < rowText.length(); column++) {
                char symbol = rowText.charAt(column);
                if (symbol == ' ') {
                    continue;
                }
                TraceabilityPredicate predicate = predicateRows[row][column];
                if (predicate == null) {
                    throw new IllegalStateException("Baseline multiblock pattern for " + id +
                            " has no predicate for symbol '" + symbol + "'");
                }
                TraceabilityPredicate previous = predicates.putIfAbsent(symbol, predicate);
                if (previous != null && previous != predicate) {
                    throw new IllegalStateException("Baseline multiblock pattern for " + id +
                            " maps symbol '" + symbol + "' to multiple predicates");
                }
            }
        }
    }

    private static Map<Character, TraceabilityPredicate> parsePredicates(ResourceLocation id, JsonNode predicatesNode) {
        if (predicatesNode == null || predicatesNode.isNull()) {
            return Map.of();
        }
        if (!predicatesNode.isObject()) {
            throw new IllegalArgumentException("Json structure definition for " + id +
                    " must define 'predicates' as an object");
        }

        Map<Character, TraceabilityPredicate> predicates = new LinkedHashMap<>();
        var ops = RegistryOps.create(JsonOps.INSTANCE, GTRegistries.builtinRegistry());
        predicatesNode.fields().forEachRemaining(entry -> {
            String symbol = entry.getKey();
            if (symbol.length() != 1) {
                throw new IllegalArgumentException("Json structure definition for " + id +
                        " has predicate key '" + symbol + "', expected a single character");
            }
            StructurePredicate structurePredicate = StructurePredicate.CODEC
                    .parse(ops, JsonParser.parseString(entry.getValue().toString()))
                    .getOrThrow(error -> new IllegalArgumentException("Failed to parse structure predicate '" +
                            symbol + "' for " + id + ": " + error));
            predicates.put(symbol.charAt(0), new TraceabilityPredicate(structurePredicate.asLegacy()));
        });
        return Map.copyOf(predicates);
    }

    public record StringArrayDefinition(List<Unit> units, Map<Character, TraceabilityPredicate> predicates) {

        public StringArrayDefinition {
            units = List.copyOf(units);
            predicates = Map.copyOf(predicates);
        }

        public List<String[]> aisles() {
            return units.stream()
                    .flatMap(unit -> unit.slices().stream())
                    .toList();
        }

        public FactoryBlockPattern applyTo(FactoryBlockPattern builder) {
            for (Unit unit : units) {
                if (unit.minRepeat() == 1 && unit.maxRepeat() == 1 && unit.slices().size() == 1) {
                    builder.aisle(unit.slices().getFirst());
                } else {
                    builder.beginRepeatable();
                    for (String[] slice : unit.slices()) {
                        builder.aisle(slice);
                    }
                    builder.endRepeatable(unit.minRepeat(), unit.maxRepeat());
                }
            }
            predicates.forEach(builder::where);
            return builder;
        }
    }

    public record Unit(List<String[]> slices, int minRepeat, int maxRepeat) {

        public Unit {
            slices = List.copyOf(slices);
        }
    }
}
