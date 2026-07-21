package com.gregtechceu.gtceu.data.pattern;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.CenterOffset;
import com.gregtechceu.gtceu.api.multiblock.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.multiblock.predicates.PredicateController;
import com.gregtechceu.gtceu.api.multiblock.structurepredicate.StructurePredicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class StructurePatternResolver {

    private StructurePatternResolver() {}

    public static FactoryBlockPattern applyStringArrayDefinition(FactoryBlockPattern builder, StructurePatternKey key) {
        return loadStringArrayDefinition(key).applyTo(builder);
    }

    public static StringArrayDefinition loadStringArrayDefinition(StructurePatternKey key) {
        StringArrayDefinition definition = StructureCache.getStringArrayPattern(key);
        if (definition == null) {
            throw new IllegalStateException("Json structure definition for " + key +
                    " was not found in structure cache");
        }
        return definition;
    }

    public static BlockPattern rebuildRuntimeStringArrayPattern(MultiblockMachineDefinition owner,
                                                                StructurePatternKey key,
                                                                BlockPattern baselinePattern,
                                                                List<Unit> runtimeUnits) {
        StringArrayDefinition definition = loadStringArrayDefinition(key);
        if (definition.predicates().isEmpty()) {
            throw new IllegalStateException("Json structure definition for " + key +
                    " must define predicates for runtime pattern rebuild");
        }
        return rebuildStringArrayPattern(owner, key, baselinePattern,
                new StringArrayDefinition(runtimeUnits, definition.predicates()));
    }

    public static StringArrayDefinition decodeStringArrayDefinition(String id, JsonNode jsonPattern) {
        return StringArrayDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(jsonPattern.toString()))
                .getOrThrow(error -> new IllegalArgumentException("Failed to parse json structure definition for " +
                        id + ": " + error));
    }

    public static StringArrayDefinition parseStringArrayDefinition(StructurePatternKey key, JsonNode jsonPattern) {
        return decodeStringArrayDefinition(key.toString(), jsonPattern);
    }

    private static DataResult<StringArrayDefinition> parseStringArrayDefinition(JsonElement jsonPattern) {
        try {
            if (!jsonPattern.isJsonObject()) {
                return DataResult.error(() -> "Json structure definition must be an object");
            }

            JsonObject patternObject = jsonPattern.getAsJsonObject();
            JsonElement predicatesElement = patternObject.get("predicates");
            JsonElement aislesElement = patternObject.get("aisles");
            if (aislesElement == null || !aislesElement.isJsonArray() || aislesElement.getAsJsonArray().isEmpty()) {
                return DataResult.error(() -> "Json structure definition must define a non-empty 'aisles' array");
            }

            Map<Character, StructurePredicate> predicates = parsePredicates(predicatesElement);
            JsonArray jsonArray = aislesElement.getAsJsonArray();
            List<Unit> units = new ArrayList<>();
            for (JsonElement unitElement : jsonArray) {
                if (unitElement.isJsonObject()) {
                    units.add(parseRepeatUnit(unitElement.getAsJsonObject()));
                } else {
                    return DataResult.error(() -> "Json structure definition 'aisles' must contain repeat objects");
                }
            }
            validateAisles(units.stream().flatMap(unit -> unit.slices().stream()).toList());
            return DataResult.success(new StringArrayDefinition(List.copyOf(units), predicates));
        } catch (RuntimeException e) {
            return DataResult.error(e::getMessage);
        }
    }

    private static Unit parseRepeatUnit(JsonObject unitObject) {
        JsonElement slicesElement = unitObject.get("slices");
        if (slicesElement == null || !slicesElement.isJsonArray() || slicesElement.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("Json repeat unit must contain a non-empty 'slices' array");
        }

        List<String[]> aisles = new ArrayList<>();
        JsonArray slicesArray = slicesElement.getAsJsonArray();
        for (JsonElement aisleElement : slicesArray) {
            if (!aisleElement.isJsonArray()) {
                throw new IllegalArgumentException("Json repeat unit must contain string-array slices");
            }
            aisles.add(parseAisle(aisleElement.getAsJsonArray()));
        }
        validateAisles(aisles);

        int minRepeat = 1;
        int maxRepeat = 1;
        JsonElement repeatElement = unitObject.get("repeat");
        if (repeatElement != null && repeatElement.isJsonObject()) {
            JsonObject repeatObject = repeatElement.getAsJsonObject();
            JsonElement minElement = repeatObject.get("min");
            JsonElement maxElement = repeatObject.get("max");
            if (minElement != null) {
                minRepeat = minElement.getAsInt();
            }
            if (maxElement != null) {
                maxRepeat = maxElement.getAsInt();
            }
        }
        if (minRepeat > maxRepeat) {
            throw new IllegalArgumentException("Json repeat unit has repeat min greater than repeat max");
        }
        return new Unit(List.copyOf(aisles), minRepeat, maxRepeat);
    }

    private static String[] parseAisle(JsonArray aisleArray) {
        if (aisleArray.isEmpty()) {
            throw new IllegalArgumentException("Json structure aisle must be a non-empty string array");
        }

        String[] aisle = new String[aisleArray.size()];
        for (int row = 0; row < aisleArray.size(); row++) {
            JsonElement rowElement = aisleArray.get(row);
            if (!rowElement.isJsonPrimitive() || !rowElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Json structure aisle row " + row + " must be a string");
            }
            aisle[row] = rowElement.getAsString();
        }
        return aisle;
    }

    private static void validateAisles(List<String[]> aisles) {
        int aisleHeight = aisles.getFirst().length;
        int rowWidth = aisles.getFirst()[0].length();
        if (rowWidth == 0) {
            throw new IllegalArgumentException("Json structure definition must not contain empty rows");
        }

        for (int aisleIndex = 0; aisleIndex < aisles.size(); aisleIndex++) {
            String[] aisle = aisles.get(aisleIndex);
            if (aisle.length != aisleHeight) {
                throw new IllegalArgumentException("Json structure aisle " + aisleIndex + " has height " +
                        aisle.length + ", expected " + aisleHeight);
            }
            for (int row = 0; row < aisle.length; row++) {
                String rowText = aisle[row];
                if (rowText.length() != rowWidth) {
                    throw new IllegalArgumentException("Json structure aisle " + aisleIndex + ", row " + row +
                            " has width " + rowText.length() + ", expected " + rowWidth);
                }
            }
        }
    }

    static BlockPattern rebuildStringArrayPattern(MultiblockMachineDefinition owner, StructurePatternKey key,
                                                  BlockPattern baselinePattern,
                                                  StringArrayDefinition definition) {
        List<String[]> aisles = definition.aisles();
        Map<Character, TraceabilityPredicate> predicates = collectPredicates(owner, key, aisles,
                definition.predicates());

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
                                    "' in json structure definition for " + key);
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
            throw new IllegalArgumentException("Json structure definition for " + key +
                    " does not contain a controller predicate symbol");
        }

        BlockPattern pattern = new BlockPattern(blockMatches, baselinePattern.structureDir, aisleRepetitions,
                unitStarts, unitDepths, structureSlices, centerOffset, aisleCount, aisleHeight, rowWidth);
        pattern.condition = baselinePattern.condition;
        pattern.predicates = List.copyOf(predicates.values());
        return pattern;
    }

    private static Map<Character, TraceabilityPredicate> collectPredicates(MultiblockMachineDefinition owner,
                                                                           StructurePatternKey key,
                                                                           List<String[]> aisles,
                                                                           Map<Character, StructurePredicate> jsonPredicates) {
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
                    StructurePredicate jsonPredicate = jsonPredicates.get(symbol);
                    if (jsonPredicate != null) {
                        predicates.put(symbol, new TraceabilityPredicate(jsonPredicate));
                        continue;
                    }
                    throw new IllegalArgumentException("Json structure definition for " + key +
                            " uses symbol '" + symbol + "' without a serialized predicate");
                }
            }
        }
        return predicates;
    }

    private static TraceabilityPredicate defaultControllerPredicate(MultiblockMachineDefinition owner) {
        return Predicates.controller(Predicates.blocks(owner.getBlock()));
    }

    private static Map<Character, StructurePredicate> parsePredicates(JsonElement predicatesElement) {
        if (predicatesElement == null || predicatesElement.isJsonNull()) {
            return Map.of();
        }
        if (!predicatesElement.isJsonObject()) {
            throw new IllegalArgumentException("Json structure definition must define 'predicates' as an object");
        }

        Map<Character, StructurePredicate> predicates = new LinkedHashMap<>();
        predicatesElement.getAsJsonObject().entrySet().forEach(entry -> {
            String symbol = entry.getKey();
            if (symbol.length() != 1) {
                throw new IllegalArgumentException("Json structure definition has predicate key '" + symbol +
                        "', expected a single character");
            }
            StructurePredicate structurePredicate = StructurePredicate.CODEC
                    .parse(JsonOps.INSTANCE, entry.getValue())
                    .getOrThrow(error -> new IllegalArgumentException("Failed to parse structure predicate '" +
                            symbol + "': " + error));
            predicates.put(symbol.charAt(0), structurePredicate);
        });
        return Map.copyOf(predicates);
    }

    public record StringArrayDefinition(List<Unit> units, Map<Character, StructurePredicate> predicates) {

        public static final Codec<StringArrayDefinition> CODEC = new Codec<>() {

            @Override
            public <T> DataResult<Pair<StringArrayDefinition, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                return parseStringArrayDefinition(json)
                        .map(definition -> Pair.of(definition, input));
            }

            @Override
            public <T> DataResult<T> encode(StringArrayDefinition input, DynamicOps<T> ops, T prefix) {
                return DataResult.error(() -> "String array structure definition encoding is not supported");
            }
        };

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
            predicates.forEach((symbol, predicate) -> builder.where(symbol, new TraceabilityPredicate(predicate)));
            return builder;
        }
    }

    public record Unit(List<String[]> slices, int minRepeat, int maxRepeat) {

        public Unit {
            slices = List.copyOf(slices);
        }
    }
}
