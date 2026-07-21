package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.error.PatternError;
import com.gregtechceu.gtceu.api.multiblock.error.PatternStringError;
import com.gregtechceu.gtceu.api.multiblock.error.SinglePredicateError;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.multiblock.structurepredicate.RestrictedPredicate;
import com.gregtechceu.gtceu.api.multiblock.structurepredicate.StructurePredicate;
import com.gregtechceu.gtceu.api.multiblock.structurepredicate.StructurePreviewChoice;
import com.gregtechceu.gtceu.api.multiblock.structurepredicate.StructurePreviewConstraint;
import com.gregtechceu.gtceu.api.multiblock.util.PatternMatchContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class BlockPattern {

    static Direction[] FACINGS = { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST, Direction.UP,
            Direction.DOWN };
    static Direction[] FACINGS_H = { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST };
    public final int[][] aisleRepetitions;
    public final int[] unitStarts;
    public final int[] unitDepths;
    public final StructureDir structureDir;
    public final String[][] structureSlices;
    protected final TraceabilityPredicate[][][] blockMatches; // [z][y][x]
    @Getter
    protected final int fingerLength; // z size
    @Getter
    protected final int thumbLength; // y size
    @Getter
    protected final int palmLength; // x size
    protected final CenterOffset centerOffset; // x, y, z, minZ, maxZ
    @Getter
    protected int[] formedRepetitionCount;
    public Collection<TraceabilityPredicate> predicates;
    public PatternCondition condition;

    private record DecodedPattern(TraceabilityPredicate[][][] predicates, StructureDir structureDir,
                                  int[][] aisleRepetitions, int[] unitStarts, int[] unitDepths,
                                  String[][] structureSlices, CenterOffset centerOffset, int fingerLength,
                                  int thumbLength, int palmLength) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Unit(List<String[]> slices, List<TraceabilityPredicate[][]> predicates, Repeat repeat) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Repeat(int min, int max) {}

    @JsonCreator
    public BlockPattern(@JsonProperty("structureDir") StructureDir structureDir,
                        @JsonProperty("centerOffset") CenterOffset centerOffset,
                        @JsonProperty("thumbLength") int thumbLength,
                        @JsonProperty("palmLength") int palmLength,
                        @JsonProperty("units") List<Unit> units) {
        this(decodeSerializedPattern(structureDir, centerOffset, thumbLength, palmLength, units));
    }

    private BlockPattern(DecodedPattern pattern) {
        this(pattern.predicates, pattern.structureDir, pattern.aisleRepetitions, pattern.unitStarts,
                pattern.unitDepths, pattern.structureSlices, pattern.centerOffset, pattern.fingerLength,
                pattern.thumbLength, pattern.palmLength);
    }

    public BlockPattern(TraceabilityPredicate[][][] predicatesIn, StructureDir structureDir,
                        int[][] aisleRepetitions, CenterOffset centerOffset, int fingerLength, int thumbLength,
                        int palmLength) {
        this(predicatesIn, structureDir, aisleRepetitions, createUnitStarts(predicatesIn.length),
                createUnitDepths(predicatesIn.length), null, centerOffset, fingerLength, thumbLength, palmLength);
    }

    public BlockPattern(TraceabilityPredicate[][][] predicatesIn, StructureDir structureDir,
                        int[][] aisleRepetitions, int[] unitStarts, int[] unitDepths, CenterOffset centerOffset,
                        int fingerLength, int thumbLength, int palmLength) {
        this(predicatesIn, structureDir, aisleRepetitions, unitStarts, unitDepths, null, centerOffset, fingerLength,
                thumbLength, palmLength);
    }

    public BlockPattern(TraceabilityPredicate[][][] predicatesIn, StructureDir structureDir,
                        int[][] aisleRepetitions, int[] unitStarts, int[] unitDepths, String[][] structureSlices,
                        CenterOffset centerOffset, int fingerLength, int thumbLength, int palmLength) {
        this.blockMatches = predicatesIn;
        this.structureDir = structureDir;
        this.aisleRepetitions = aisleRepetitions;
        this.unitStarts = unitStarts;
        this.unitDepths = unitDepths;
        this.structureSlices = structureSlices;
        this.formedRepetitionCount = new int[aisleRepetitions.length];
        this.centerOffset = centerOffset;
        this.fingerLength = fingerLength;
        this.thumbLength = thumbLength;
        this.palmLength = palmLength;
    }

    private static DecodedPattern decodeSerializedPattern(StructureDir structureDir, CenterOffset centerOffset,
                                                          int thumbLength, int palmLength, List<Unit> units) {
        if (units == null || units.isEmpty()) {
            throw new IllegalStateException("Serialized binary multiblock pattern is missing units");
        }

        int size = units.stream().mapToInt(unit -> unit.slices().size()).sum();
        TraceabilityPredicate[][][] blockMatches = new TraceabilityPredicate[size][][];
        String[][] structureSlices = new String[size][];
        int[][] aisleRepetitions = new int[units.size()][];
        int[] unitStarts = new int[units.size()];
        int[] unitDepths = new int[units.size()];

        int sliceIndex = 0;
        for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
            Unit unit = units.get(unitIndex);
            if (unit.slices() == null || unit.slices().isEmpty()) {
                throw new IllegalStateException("Serialized binary multiblock pattern is missing unit slices");
            }
            if (unit.predicates() == null || unit.predicates().size() != unit.slices().size()) {
                throw new IllegalStateException("Serialized binary multiblock pattern is missing predicate slices");
            }

            Repeat repeat = unit.repeat() == null ? new Repeat(1, 1) : unit.repeat();
            if (repeat.min() > repeat.max()) {
                throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
            }

            unitStarts[unitIndex] = sliceIndex;
            unitDepths[unitIndex] = unit.slices().size();
            aisleRepetitions[unitIndex] = new int[] { repeat.min(), repeat.max() };

            for (int inner = 0; inner < unit.slices().size(); inner++) {
                structureSlices[sliceIndex] = unit.slices().get(inner);
                blockMatches[sliceIndex] = unit.predicates().get(inner);
                sliceIndex++;
            }
        }

        return new DecodedPattern(blockMatches, structureDir, aisleRepetitions, unitStarts, unitDepths,
                structureSlices, centerOffset, size, thumbLength, palmLength);
    }

    public List<Unit> getUnits() {
        List<Unit> units = new ArrayList<>(aisleRepetitions.length);
        for (int unitIndex = 0; unitIndex < aisleRepetitions.length; unitIndex++) {
            int start = unitStarts[unitIndex];
            int depth = unitDepths[unitIndex];
            List<String[]> slices = new ArrayList<>(depth);
            List<TraceabilityPredicate[][]> predicates = blockMatches == null ? null : new ArrayList<>(depth);
            for (int inner = 0; inner < depth; inner++) {
                slices.add(structureSlices == null ? null : structureSlices[start + inner]);
                if (predicates != null) {
                    predicates.add(blockMatches[start + inner]);
                }
            }
            int[] repetition = aisleRepetitions[unitIndex];
            units.add(new Unit(slices, predicates, new Repeat(repetition[0], repetition[1])));
        }
        return units;
    }

    private static int[] createUnitStarts(int size) {
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = i;
        }
        return result;
    }

    private static int[] createUnitDepths(int size) {
        int[] result = new int[size];
        Arrays.fill(result, 1);
        return result;
    }

    public boolean checkPatternAt(MultiblockState worldState, boolean savePredicate) {
        MultiblockControllerMachine controller = worldState.getController();
        if (controller == null) {
            worldState.setError(new PatternStringError("no controller found"));
            return false;
        }
        BlockPos centerPos = controller.getBlockPos();
        Direction frontFacing = controller.getFrontFacing();
        Direction[] facings = controller.hasFrontFacing() ? new Direction[] { frontFacing } :
                new Direction[] { Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST };
        Direction upwardsFacing = controller.getUpwardsFacing();
        boolean allowsFlip = controller.allowFlip();
        for (Direction direction : facings) {
            boolean result = checkPatternAt(worldState, centerPos, direction, upwardsFacing, false, savePredicate);
            if (result) {
                return true;
            } else if (allowsFlip) {
                return checkPatternAt(worldState, centerPos, direction, upwardsFacing, true, savePredicate);
            }
        }
        return false;
    }

    @Deprecated(forRemoval = true, since = "7.0")
    public int[] getDimensions() {
        return new int[] { fingerLength, thumbLength, palmLength };
    }

    public boolean checkPatternAt(MultiblockState worldState, BlockPos centerPos, Direction frontFacing,
                                  Direction upwardsFacing, boolean isFlipped, boolean savePredicate) {
        boolean findFirstAisle = false;
        int minZ = -centerOffset.maxZ();
        worldState.clean();
        PatternMatchContext matchContext = worldState.getMatchContext();
        Object2IntMap<SimplePredicate> globalCount = worldState.getGlobalCount();
        Object2IntMap<SimplePredicate> layerCount = worldState.getLayerCount();
        Object2IntMap<StructurePredicate> structureGlobalCount = worldState.getStructureGlobalCount();
        Object2IntMap<StructurePredicate> structureLayerCount = worldState.getStructureLayerCount();
        // Checking aisle units
        for (int c = 0, z = minZ++, r; c < this.aisleRepetitions.length; c++) {
            int unitStart = this.unitStarts[c];
            int unitDepth = this.unitDepths[c];
            // Checking repeatable unit
            int validRepetitions = 0;
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset.minZ()); r++) {
                int repeatStartZ = z;
                for (int inner = 0; inner < unitDepth; inner++, z++) {
                    // Checking single slice
                    layerCount.clear();
                    structureLayerCount.clear();

                    for (int b = 0, y = -centerOffset.j(); b < this.thumbLength; b++, y++) {
                        for (int a = 0, x = -centerOffset.k(); a < this.palmLength; a++, x++) {
                            worldState.setError(null);
                            TraceabilityPredicate predicate = this.blockMatches[unitStart + inner][b][a];
                            BlockPos pos = setActualRelativeOffset(x, y, z, frontFacing, upwardsFacing, isFlipped)
                                    .offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                            if (!worldState.update(pos, predicate)) {
                                return false;
                            }
                            if (predicate.addCache()) {
                                worldState.addPosCache(pos);
                                if (savePredicate) {
                                    matchContext.getOrCreate("predicates", HashMap::new).put(pos, predicate);
                                }
                            }
                            boolean canPartShared = true;
                            if (worldState.getBlockEntity() instanceof IMultiPart part) { // add detected parts
                                if (!predicate.isAny()) {
                                    if (part.isFormed() &&
                                            !part.hasController(worldState.controllerPos,
                                                    worldState.getStructureName()) &&
                                            !part.canShared(worldState.lastController, worldState.getStructureName())) { // check
                                                                                                                         // part
                                                                                                                         // can
                                                                                                                         // be
                                                                                                                         // shared
                                        canPartShared = false;
                                        worldState.setError(new PatternStringError("multiblocked.pattern.error.share"));
                                    } else {
                                        matchContext.getOrCreate("parts", HashSet::new).add(part);
                                    }
                                }
                            }
                            if (worldState.getBlockState().getBlock() instanceof ActiveBlock) {
                                matchContext.getOrCreate("vaBlocks", LongOpenHashSet::new)
                                        .add(worldState.getPos().asLong());
                            }
                            if (!predicate.test(worldState) || !canPartShared ||
                                    !matchesDirectionalPredicate(predicate, worldState, frontFacing, upwardsFacing,
                                            isFlipped)) { // matching failed
                                if (findFirstAisle) {
                                    if (r < aisleRepetitions[c][0]) {// retreat to see if the first aisle can start
                                                                     // later
                                        r = c = 0;
                                        z = minZ++;
                                        matchContext.reset();
                                        findFirstAisle = false;
                                    } else {
                                        z = repeatStartZ;
                                    }
                                } else {
                                    z = repeatStartZ + 1;// continue searching for the first aisle
                                }
                                continue loop;
                            }
                            matchContext.getOrCreate("ioMap", Long2ObjectOpenHashMap::new)
                                    .put(worldState.getPos().asLong(), worldState.io);
                        }
                    }

                    // Check layer-local matcher predicate
                    for (var entry : layerCount.object2IntEntrySet()) {
                        if (entry.getIntValue() < entry.getKey().minLayerCount) {
                            worldState.setError(new SinglePredicateError(entry.getKey(), 3));
                            return false;
                        }
                    }
                    for (var entry : structureLayerCount.object2IntEntrySet()) {
                        if (entry.getKey() instanceof RestrictedPredicate predicate &&
                                predicate.minCountByLayer().isPresent() &&
                                entry.getIntValue() < predicate.minCountByLayer().get()) {
                            worldState.setError(new PatternStringError("gtpm.multiblock.pattern.error.limited"));
                            return false;
                        }
                    }
                }
                findFirstAisle = true;
                validRepetitions++;
            }
            // Repetitions out of range
            if (r < aisleRepetitions[c][0] || worldState.hasError() || !findFirstAisle) {
                if (!worldState.hasError()) {
                    worldState.setError(new PatternError());
                }
                return false;
            }

            // finished checking the aisle, so store the repetitions
            formedRepetitionCount[c] = validRepetitions;
        }

        // Check count matches amount
        for (var entry : globalCount.object2IntEntrySet()) {
            if (entry.getIntValue() < entry.getKey().minCount) {
                worldState.setError(new SinglePredicateError(entry.getKey(), 1));
                return false;
            }
        }
        for (var entry : structureGlobalCount.object2IntEntrySet()) {
            if (entry.getKey() instanceof RestrictedPredicate predicate && predicate.minCount().isPresent() &&
                    entry.getIntValue() < predicate.minCount().get()) {
                worldState.setError(new PatternStringError("gtpm.multiblock.pattern.error.limited"));
                return false;
            }
        }

        worldState.setError(null);
        worldState.setNeededFlip(isFlipped);
        return true;
    }

    public int getMinZ() {
        return -centerOffset.maxZ();
    }

    public int getMinY() {
        return -centerOffset.j();
    }

    public int getMinX() {
        return -centerOffset.k();
    }

    public TraceabilityPredicate getPredicate(int z, int y, int x) {
        return blockMatches[z][y][x];
    }

    public BlockPos getActualRelativeOffset(int x, int y, int z, Direction facing, Direction upwardsFacing,
                                            boolean isFlipped) {
        return setActualRelativeOffset(x, y, z, facing, upwardsFacing, isFlipped);
    }

    public boolean matchesDirectionalPredicate(TraceabilityPredicate predicate, MultiblockState worldState,
                                               Direction frontFacing, Direction upwardsFacing, boolean isFlipped) {
        return matchesDirectionalPredicateInternal(predicate, worldState, frontFacing, upwardsFacing, isFlipped);
    }

    public static BlockState applyDirectionalState(BlockState state, Direction direction) {
        return setDirectionalState(state, direction);
    }

    public void resetPlacedMachineFacings(Level world, Direction frontFacing, LongOpenHashSet occupiedBlocks,
                                          Long2ObjectOpenHashMap<MetaMachine> machines) {
        machines.long2ObjectEntrySet().fastForEach(entry -> {
            long posLong = entry.getLongKey();
            MetaMachine machine = entry.getValue();
            BlockPos pos = BlockPos.of(posLong);
            resetFacing(pos, machine.getBlockState(), frontFacing, (p, f) -> {
                if (!occupiedBlocks.contains(p.relative(f).asLong())) {
                    return machine.isFacingValid(f);
                }
                return false;
            }, state -> world.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        });
    }

    public MultiblockBlockInfo[][][] getPreview(MultiblockMachineDefinition definition, int[] repetition) {
        Object2IntOpenHashMap<Object> cacheGlobal = new Object2IntOpenHashMap<>();
        Map<Object, PreviewLimit> globalLimits = new LinkedHashMap<>();
        Long2ObjectOpenHashMap<MultiblockBlockInfo> blocks = new Long2ObjectOpenHashMap<>(1024, 0.5F);
        Long2ObjectOpenHashMap<MultiblockBlockInfo> machines = new Long2ObjectOpenHashMap<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int unit = 0, x = 0; unit < this.aisleRepetitions.length; unit++) {
            int unitStart = this.unitStarts[unit];
            int unitDepth = this.unitDepths[unit];
            for (int r = 0; r < repetition[unit]; r++) {
                for (int inner = 0; inner < unitDepth; inner++, x++) {
                    Object2IntOpenHashMap<Object> cacheLayer = new Object2IntOpenHashMap<>();
                    Map<Object, PreviewLimit> layerLimits = new LinkedHashMap<>();
                    for (int y = 0; y < this.thumbLength; y++) {
                        for (int z = 0; z < this.palmLength; z++) {
                            var bl = this.blockMatches[unitStart + inner];
                            if (bl == null) continue;
                            var by = bl[y];
                            if (by == null) continue;
                            TraceabilityPredicate predicate = by[z];
                            if (predicate == null) continue;
                            List<PreviewOption> options = previewOptions(predicate, definition);
                            registerLimits(options, globalLimits, layerLimits);
                            PreviewOption option = selectPreviewOption(options, cacheGlobal, cacheLayer);
                            if (option == null) {
                                if (predicate.isAny() || predicate.hasAir()) continue;
                                throw previewGenerationError(definition,
                                        "no candidate remained at pattern cell " + x + "," + y + "," + z);
                            }
                            incrementCounts(option, cacheGlobal, cacheLayer);
                            MultiblockBlockInfo info = option.blockInfo();
                            if (info.getBlockState().getBlock() != Blocks.AIR) {
                                Direction direction = predicate.getPreviewDirection();
                                if (direction != null) {
                                    info = MultiblockBlockInfo.fromBlockState(setDirectionalState(info.getBlockState(),
                                            direction));
                                }
                                BlockPos pos = gerPreviewOffset(z, y, x);
                                if (info.getBlockState().getBlock() instanceof MetaMachineBlock) {
                                    machines.put(pos.asLong(), info);
                                } else {
                                    blocks.put(pos.asLong(), info);
                                }
                                minX = Math.min(pos.getX(), minX);
                                minY = Math.min(pos.getY(), minY);
                                minZ = Math.min(pos.getZ(), minZ);
                                maxX = Math.max(pos.getX(), maxX);
                                maxY = Math.max(pos.getY(), maxY);
                                maxZ = Math.max(pos.getZ(), maxZ);
                            }
                        }
                    }
                    verifyMinimums(definition, layerLimits, cacheLayer, true);
                }
            }
        }
        verifyMinimums(definition, globalLimits, cacheGlobal, false);
        if (blocks.isEmpty() && machines.isEmpty()) {
            throw previewGenerationError(definition, "the generated page did not contain any visible blocks");
        }
        MultiblockBlockInfo[][][] result = new MultiblockBlockInfo[maxX - minX + 1][maxY - minY + 1][maxZ - minZ + 1];
        int finalMinX = minX;
        int finalMinY = minY;
        int finalMinZ = minZ;
        machines.long2ObjectEntrySet().fastForEach(entry -> {
            var pos = BlockPos.of(entry.getLongKey());
            var info = entry.getValue();
            var blockState = info.getBlockState();
            if (blockState.getBlock() instanceof MetaMachineBlock machineBlock) {
                resetFacing(pos, blockState, null, (p, f) -> {
                    long relativePos = p.relative(f).asLong();
                    if (blocks.get(relativePos) != null || machines.get(relativePos) != null) {
                        return false;
                    }
                    if (machineBlock.getDefinition() instanceof MultiblockMachineDefinition) {
                        return false;
                    }
                    if (machineBlock.newBlockEntity(BlockPos.ZERO, blockState) instanceof MetaMachine machine) {
                        return machine.isFacingValid(f);
                    }
                    return false;
                }, info::setBlockState);
            }
            result[pos.getX() - finalMinX][pos.getY() - finalMinY][pos.getZ() - finalMinZ] = info;
        });
        blocks.long2ObjectEntrySet().fastForEach(entry -> {
            var pos = BlockPos.of(entry.getLongKey());
            var info = entry.getValue();
            result[pos.getX() - finalMinX][pos.getY() - finalMinY][pos.getZ() - finalMinZ] = info;
        });
        return result;
    }

    private List<PreviewOption> previewOptions(TraceabilityPredicate predicate,
                                               MultiblockMachineDefinition definition) {
        List<PreviewOption> options = new ArrayList<>();
        for (SimplePredicate simplePredicate : predicate.limited) {
            MultiblockBlockInfo info = simplePredicate.blockInfo.get();
            if (info == null) continue;
            PreviewLimit limit = PreviewLimit.fromSimple(simplePredicate);
            boolean fallback = simplePredicate.previewCount == -1;
            options.add(new PreviewOption(info, List.of(limit), fallback, 1));
        }
        for (SimplePredicate simplePredicate : predicate.common) {
            MultiblockBlockInfo info = simplePredicate.blockInfo.get();
            if (info == null) continue;
            PreviewLimit limit = PreviewLimit.fromSimple(simplePredicate);
            options.add(new PreviewOption(info, List.of(limit), simplePredicate.previewCount == -1, 0));
        }
        for (StructurePredicate structurePredicate : predicate.structurePredicates) {
            for (StructurePreviewChoice choice : structurePredicate.previewChoices(definition)) {
                if (choice.candidates().isEmpty()) continue;
                List<PreviewLimit> limits = choice.constraints().stream().map(PreviewLimit::fromStructure).toList();
                boolean fallback = limits.stream().noneMatch(PreviewLimit::hasPreviewTarget);
                int fallbackPriority = limits.isEmpty() ? 0 : 1;
                options.add(new PreviewOption(choice.candidates().getFirst(), limits, fallback, fallbackPriority));
            }
        }
        return options;
    }

    private static void registerLimits(List<PreviewOption> options, Map<Object, PreviewLimit> globalLimits,
                                       Map<Object, PreviewLimit> layerLimits) {
        for (PreviewOption option : options) {
            for (PreviewLimit limit : option.limits()) {
                globalLimits.putIfAbsent(limit.key(), limit);
                if (limit.minCountByLayer() >= 0 || limit.maxCountByLayer() >= 0) {
                    layerLimits.putIfAbsent(limit.key(), limit);
                }
            }
        }
    }

    private static PreviewOption selectPreviewOption(List<PreviewOption> options,
                                                     Object2IntMap<Object> globalCounts,
                                                     Object2IntMap<Object> layerCounts) {
        PreviewOption selected = selectTarget(options, globalCounts, layerCounts, PreviewTarget.LAYER_MINIMUM);
        if (selected == null) {
            selected = selectTarget(options, globalCounts, layerCounts, PreviewTarget.GLOBAL);
        }
        if (selected != null) return selected;

        for (int priority = 0; priority <= 1; priority++) {
            for (PreviewOption option : options) {
                if (option.fallback() && option.fallbackPriority() == priority &&
                        canSelect(option, globalCounts, layerCounts)) {
                    return option;
                }
            }
        }
        return null;
    }

    private static PreviewOption selectTarget(List<PreviewOption> options, Object2IntMap<Object> globalCounts,
                                              Object2IntMap<Object> layerCounts, PreviewTarget target) {
        PreviewOption selected = null;
        int smallestRemaining = Integer.MAX_VALUE;
        for (PreviewOption option : options) {
            if (!canSelect(option, globalCounts, layerCounts)) continue;
            int remaining = target.remaining(option, globalCounts, layerCounts);
            if (remaining > 0 && remaining < smallestRemaining) {
                selected = option;
                smallestRemaining = remaining;
            }
        }
        return selected;
    }

    private static boolean canSelect(PreviewOption option, Object2IntMap<Object> globalCounts,
                                     Object2IntMap<Object> layerCounts) {
        for (PreviewLimit limit : option.limits()) {
            if (limit.maxCount() >= 0 && globalCounts.getInt(limit.key()) >= limit.maxCount()) return false;
            if (limit.maxCountByLayer() >= 0 &&
                    layerCounts.getInt(limit.key()) >= limit.maxCountByLayer())
                return false;
        }
        return true;
    }

    private static void incrementCounts(PreviewOption option, Object2IntMap<Object> globalCounts,
                                        Object2IntMap<Object> layerCounts) {
        for (PreviewLimit limit : option.limits()) {
            globalCounts.put(limit.key(), globalCounts.getInt(limit.key()) + 1);
            layerCounts.put(limit.key(), layerCounts.getInt(limit.key()) + 1);
        }
    }

    private static void verifyMinimums(MultiblockMachineDefinition definition, Map<Object, PreviewLimit> limits,
                                       Object2IntMap<Object> counts, boolean layer) {
        for (PreviewLimit limit : limits.values()) {
            int minimum = layer ? limit.minCountByLayer() : limit.minCount();
            if (minimum >= 0 && counts.getInt(limit.key()) < minimum) {
                String scope = layer ? "layer" : "global";
                throw previewGenerationError(definition,
                        scope + " minimum " + minimum + " could not be satisfied for " + limit.key());
            }
        }
    }

    private static IllegalStateException previewGenerationError(MultiblockMachineDefinition definition,
                                                                String detail) {
        return new IllegalStateException("Unable to generate multiblock preview for " + definition.getId() + ": " +
                detail);
    }

    private enum PreviewTarget {

        LAYER_MINIMUM {

            @Override
            int remaining(PreviewOption option, Object2IntMap<Object> globalCounts,
                          Object2IntMap<Object> layerCounts) {
                return option.limits().stream()
                        .mapToInt(limit -> limit.minCountByLayer() - layerCounts.getInt(limit.key()))
                        .filter(value -> value > 0)
                        .min()
                        .orElse(0);
            }
        },
        GLOBAL {

            @Override
            int remaining(PreviewOption option, Object2IntMap<Object> globalCounts,
                          Object2IntMap<Object> layerCounts) {
                return option.limits().stream()
                        .mapToInt(limit -> smallestPositive(
                                limit.minCount() - globalCounts.getInt(limit.key()),
                                limit.previewCount() - globalCounts.getInt(limit.key())))
                        .filter(value -> value > 0)
                        .min()
                        .orElse(0);
            }
        };

        abstract int remaining(PreviewOption option, Object2IntMap<Object> globalCounts,
                               Object2IntMap<Object> layerCounts);

        private static int smallestPositive(int first, int second) {
            if (first <= 0) return Math.max(second, 0);
            if (second <= 0) return first;
            return Math.min(first, second);
        }
    }

    private record PreviewOption(MultiblockBlockInfo blockInfo, List<PreviewLimit> limits, boolean fallback,
                                 int fallbackPriority) {}

    private record PreviewLimit(Object key, int minCount, int maxCount, int minCountByLayer,
                                int maxCountByLayer, int previewCount) {

        private static PreviewLimit fromSimple(SimplePredicate predicate) {
            return new PreviewLimit(predicate, predicate.minCount, predicate.maxCount, predicate.minLayerCount,
                    predicate.maxLayerCount, predicate.previewCount);
        }

        private static PreviewLimit fromStructure(StructurePreviewConstraint constraint) {
            return new PreviewLimit(constraint.key(), constraint.minCount().orElse(-1),
                    constraint.maxCount().orElse(-1), constraint.minCountByLayer().orElse(-1),
                    constraint.maxCountByLayer().orElse(-1), constraint.previewCount().orElse(-1));
        }

        private boolean hasPreviewTarget() {
            return previewCount >= 0;
        }
    }

    private void resetFacing(BlockPos pos, BlockState blockState, Direction facing,
                             BiPredicate<BlockPos, Direction> checker, Consumer<BlockState> consumer) {
        if (blockState.hasProperty(BlockStateProperties.FACING)) {
            tryFacings(blockState, pos, checker, consumer, BlockStateProperties.FACING,
                    facing == null ? FACINGS : ArrayUtils.addAll(new Direction[] { facing }, FACINGS));
        } else if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            tryFacings(blockState, pos, checker, consumer, BlockStateProperties.HORIZONTAL_FACING,
                    facing == null || facing.getAxis() == Direction.Axis.Y ? FACINGS_H :
                            ArrayUtils.addAll(new Direction[] { facing }, FACINGS_H));
        }
    }

    private void tryFacings(BlockState blockState, BlockPos pos, BiPredicate<BlockPos, Direction> checker,
                            Consumer<BlockState> consumer, Property<Direction> property, Direction[] facings) {
        Direction found = null;
        for (Direction facing : facings) {
            if (checker.test(pos, facing)) {
                found = facing;
                break;
            }
        }
        if (found == null) {
            found = Direction.NORTH;
        }
        consumer.accept(blockState.setValue(property, found));
    }

    private boolean matchesDirectionalPredicateInternal(TraceabilityPredicate predicate, MultiblockState worldState,
                                                        Direction frontFacing, Direction upwardsFacing,
                                                        boolean isFlipped) {
        Direction direction = predicate.getDirection(worldState, frontFacing, upwardsFacing, isFlipped);
        return direction == null || matchesDirectionalState(worldState.getBlockState(), direction);
    }

    private static boolean matchesDirectionalState(BlockState state, Direction direction) {
        Optional<DirectionProperty> property = findDirectionProperty(state, direction);
        return property.filter(directionProperty -> state.getValue(directionProperty) == direction).isPresent();
    }

    @Nullable
    private static Direction getDirectionalState(BlockState state) {
        return findDirectionProperty(state, null)
                .map(state::getValue)
                .orElse(null);
    }

    private static BlockState setDirectionalState(BlockState state, Direction direction) {
        return findDirectionProperty(state, direction)
                .map(property -> state.setValue(property, direction))
                .orElse(state);
    }

    private static Optional<DirectionProperty> findDirectionProperty(BlockState state, @Nullable Direction direction) {
        return state.getProperties().stream()
                .filter(DirectionProperty.class::isInstance)
                .map(DirectionProperty.class::cast)
                .filter(property -> direction == null || property.getPossibleValues().contains(direction))
                .min(Comparator.comparingInt(BlockPattern::directionPropertyPriority));
    }

    private static int directionPropertyPriority(DirectionProperty property) {
        String name = property.getName();
        if ("facing".equals(name)) return 0;
        if ("horizontal_facing".equals(name)) return 1;
        if (name.endsWith("_facing")) return 2;
        if (name.contains("facing")) return 3;
        return 4;
    }

    private BlockPos setActualRelativeOffset(int x, int y, int z, Direction facing, Direction upwardsFacing,
                                             boolean isFlipped) {
        return new RelativeOffset(x, y, z, structureDir, facing, upwardsFacing, isFlipped).toBlockPos();
    }

    protected BlockPos gerPreviewOffset(int x, int y, int z) {
        return new RelativeOffset(
                x, y, z, structureDir,
                Direction.NORTH, Direction.NORTH, false).toBlockPos();
    }
}
