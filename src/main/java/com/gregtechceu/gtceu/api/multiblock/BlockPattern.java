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
import com.gregtechceu.gtceu.api.multiblock.util.PatternMatchContext;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

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
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    public BlockInfo[][][] getPreview(int[] repetition) {
        Reference2IntOpenHashMap<SimplePredicate> cacheGlobal = new Reference2IntOpenHashMap<>();
        Long2ObjectOpenHashMap<BlockInfo> blocks = new Long2ObjectOpenHashMap<>(1024, 0.5F);
        Long2ObjectOpenHashMap<BlockInfo> machines = new Long2ObjectOpenHashMap<>();
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
                    // Checking single slice
                    Reference2IntOpenHashMap<SimplePredicate> cacheLayer = new Reference2IntOpenHashMap<>();
                    for (int y = 0; y < this.thumbLength; y++) {
                        for (int z = 0; z < this.palmLength; z++) {
                            var bl = this.blockMatches[unitStart + inner];
                            if (bl == null) continue;
                            var by = bl[y];
                            if (by == null) continue;
                            TraceabilityPredicate predicate = by[z];
                            if (predicate == null) continue;
                            BlockInfo info = null;
                            boolean find = false;
                            for (SimplePredicate limit : predicate.limited) {
                                // check layer and previewCount
                                if (limit.minLayerCount > 0) {
                                    if (cacheLayer.getInt(limit) < limit.minLayerCount) {
                                        cacheLayer.addTo(limit, 1);
                                    } else {
                                        continue;
                                    }
                                    if (cacheGlobal.getInt(limit) < limit.previewCount) {
                                        cacheGlobal.addTo(limit, 1);
                                    } else {
                                        continue;
                                    }
                                } else {
                                    continue;
                                }
                                info = limit.blockInfo.get();
                                if (info != null) {
                                    find = true;
                                    break;
                                }
                            }
                            if (!find) {
                                // check global and previewCount
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.minCount == -1 && limit.previewCount == -1) continue;
                                    if (cacheGlobal.getInt(limit) < limit.previewCount) {
                                        cacheGlobal.addTo(limit, 1);
                                    } else if (limit.minCount > 0) {
                                        if (cacheGlobal.getInt(limit) < limit.minCount) {
                                            cacheGlobal.addTo(limit, 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    info = limit.blockInfo.get();
                                    if (info != null) {
                                        find = true;
                                        break;
                                    }
                                }
                            }
                            if (!find) {
                                // check common with previewCount
                                for (SimplePredicate common : predicate.common) {
                                    if (common.previewCount > 0) {
                                        if (cacheGlobal.getInt(common) < common.previewCount) {
                                            cacheGlobal.addTo(common, 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    info = common.blockInfo.get();
                                    if (info != null) {
                                        find = true;
                                        break;
                                    }
                                }
                            }
                            if (!find) {
                                // check without previewCount
                                for (SimplePredicate common : predicate.common) {
                                    if (common.previewCount == -1) {
                                        info = common.blockInfo.get();
                                        if (info != null) {
                                            find = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!find) {
                                // check max
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.previewCount != -1) continue;
                                    if (limit.maxCount != -1 || limit.maxLayerCount != -1) {
                                        if (cacheGlobal.getOrDefault(limit, 0) < limit.maxCount) {
                                            cacheGlobal.addTo(limit, 1);
                                        } else if (cacheLayer.getOrDefault(limit, 0) < limit.maxLayerCount) {
                                            cacheLayer.addTo(limit, 1);
                                        } else {
                                            continue;
                                        }
                                    }
                                    info = limit.blockInfo.get();
                                    if (info != null) {
                                        break;
                                    }
                                }
                            }
                            if (info != null && info.getBlockState().getBlock() != Blocks.AIR) {
                                Direction direction = predicate.getPreviewDirection();
                                if (direction != null) {
                                    info = BlockInfo.fromBlockState(setDirectionalState(info.getBlockState(),
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
                }
            }
        }
        BlockInfo[][][] result = (BlockInfo[][][]) Array.newInstance(BlockInfo.class, maxX - minX + 1, maxY - minY + 1,
                maxZ - minZ + 1);
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
        int[] c0 = new int[] { x, y, z };
        int[] c1 = new int[3];
        return new BlockPos(c1[0], c1[1], c1[2]);
    }
}
