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
import com.gregtechceu.gtceu.api.multiblock.util.PatternMatchContext;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import it.unimi.dsi.fastutil.ints.IntObjectPair;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class BlockPattern {

    static Direction[] FACINGS = { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST, Direction.UP,
            Direction.DOWN };
    static Direction[] FACINGS_H = { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST };
    public final int[][] aisleRepetitions;
    public final StructureDir structureDir;
    protected final TraceabilityPredicate[][][] blockMatches; // [z][y][x]
    protected final int fingerLength; // z size
    protected final int thumbLength; // y size
    protected final int palmLength; // x size
    protected final CenterOffset centerOffset; // x, y, z, minZ, maxZ
    @Getter
    protected int[] formedRepetitionCount;

    public BlockPattern(TraceabilityPredicate[][][] predicatesIn, StructureDir structureDir,
                        int[][] aisleRepetitions, CenterOffset centerOffset, int fingerLength, int thumbLength,
                        int palmLength) {
        this.blockMatches = predicatesIn;
        this.structureDir = structureDir;
        this.aisleRepetitions = aisleRepetitions;
        this.formedRepetitionCount = new int[aisleRepetitions.length];
        this.centerOffset = centerOffset;
        this.fingerLength = fingerLength;
        this.thumbLength = thumbLength;
        this.palmLength = palmLength;
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
        // Checking aisles
        for (int c = 0, z = minZ++, r; c < this.fingerLength; c++) {
            // Checking repeatable slices
            int validRepetitions = 0;
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset.minZ()); r++) {
                // Checking single slice
                layerCount.clear();

                for (int b = 0, y = -centerOffset.j(); b < this.thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset.k(); a < this.palmLength; a++, x++) {
                        worldState.setError(null);
                        TraceabilityPredicate predicate = this.blockMatches[c][b][a];
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
                                if (part.isFormed() && !part.canShared() &&
                                        !part.hasController(worldState.controllerPos)) { // check part can be shared
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
                        if (!predicate.test(worldState) || !canPartShared) { // matching failed
                            if (findFirstAisle) {
                                if (r < aisleRepetitions[c][0]) {// retreat to see if the first aisle can start later
                                    r = c = 0;
                                    z = minZ++;
                                    matchContext.reset();
                                    findFirstAisle = false;
                                }
                            } else {
                                z++;// continue searching for the first aisle
                            }
                            continue loop;
                        }
                        matchContext.getOrCreate("ioMap", Long2ObjectOpenHashMap::new).put(worldState.getPos().asLong(),
                                worldState.io);
                    }
                }
                findFirstAisle = true;
                z++;

                // Check layer-local matcher predicate
                for (var entry : layerCount.object2IntEntrySet()) {
                    if (entry.getIntValue() < entry.getKey().minLayerCount) {
                        worldState.setError(new SinglePredicateError(entry.getKey(), 3));
                        return false;
                    }
                }
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

        worldState.setError(null);
        worldState.setNeededFlip(isFlipped);
        return true;
    }

    public void autoBuild(Player player, MultiblockState worldState) {
        Level world = player.level();
        int minZ = -centerOffset.maxZ();
        worldState.clean();
        MultiblockControllerMachine controller = worldState.getController();
        BlockPos centerPos = controller.getBlockPos();
        Direction facing = controller.getFrontFacing();
        Direction upwardsFacing = controller.getUpwardsFacing();
        boolean isFlipped = controller.isFlipped();
        Object2IntOpenHashMap<SimplePredicate> cacheGlobal = worldState.getGlobalCount();
        Object2IntOpenHashMap<SimplePredicate> cacheLayer = worldState.getLayerCount();
        LongOpenHashSet blocks = new LongOpenHashSet(1024, 0.5F);
        Long2ObjectOpenHashMap<MetaMachine> machines = new Long2ObjectOpenHashMap<>();
        Set<BlockPos> placeBlockPos = new HashSet<>();
        blocks.add(centerPos.asLong());
        for (int c = 0, z = minZ++, r; c < this.fingerLength; c++) {
            for (r = 0; r < aisleRepetitions[c][0]; r++) {
                cacheLayer.clear();
                for (int b = 0, y = -centerOffset.j(); b < this.thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset.k(); a < this.palmLength; a++, x++) {
                        var bc = this.blockMatches[c];
                        if (bc == null) continue;
                        var bb = bc[b];
                        if (bb == null) continue;
                        TraceabilityPredicate predicate = bb[a];
                        if (predicate == null) continue;
                        BlockPos pos = setActualRelativeOffset(x, y, z, facing, upwardsFacing, isFlipped)
                                .offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        worldState.update(pos, predicate);
                        long posLong = pos.asLong();
                        if (!world.isEmptyBlock(pos)) {
                            blocks.add(posLong);
                            for (SimplePredicate limit : predicate.limited) {
                                limit.testLimited(worldState);
                            }
                        } else {
                            boolean find = false;
                            Block[] infos = new Block[0];
                            for (SimplePredicate limit : predicate.limited) {
                                if (limit.minLayerCount > 0) {
                                    int curr = cacheLayer.getInt(limit);
                                    if (curr < limit.minLayerCount &&
                                            (limit.maxLayerCount == -1 || curr < limit.maxLayerCount)) {
                                        cacheLayer.addTo(limit, 1);
                                    } else {
                                        continue;
                                    }
                                } else {
                                    continue;
                                }
                                infos = limit.candidates == null ? null : limit.candidates.get();
                                find = true;
                                break;
                            }
                            if (!find) {
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.minCount > 0) {
                                        int curr = cacheGlobal.getInt(limit);
                                        if (curr < limit.minCount && (limit.maxCount == -1 || curr < limit.maxCount)) {
                                            cacheGlobal.addTo(limit, 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    infos = limit.candidates == null ? null : limit.candidates.get();
                                    find = true;
                                    break;
                                }
                            }
                            if (!find) { // no limited
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.maxLayerCount != -1 &&
                                            cacheLayer.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxLayerCount) {
                                        continue;
                                    }
                                    if (limit.maxCount != -1 &&
                                            cacheGlobal.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxCount) {
                                        continue;
                                    }
                                    cacheLayer.addTo(limit, 1);
                                    cacheGlobal.addTo(limit, 1);
                                    infos = ArrayUtils.addAll(infos,
                                            limit.candidates == null ? null : limit.candidates.get());
                                }
                                for (SimplePredicate common : predicate.common) {
                                    infos = ArrayUtils.addAll(infos,
                                            common.candidates == null ? null : common.candidates.get());
                                }
                            }
                            List<ItemStack> candidates = new ArrayList<>();
                            if (infos != null) {
                                for (Block info : infos) {
                                    if (info != Blocks.AIR) {
                                        candidates.add(SimplePredicate.toItem(info).getDefaultInstance());
                                    }
                                }
                            }

                            // check inventory
                            ItemStack found = null;
                            int foundSlot = -1;
                            IItemHandler handler = null;
                            if (!player.isCreative()) {
                                var foundHandler = getMatchStackWithHandler(candidates,
                                        player.getCapability(Capabilities.ItemHandler.ENTITY));
                                if (foundHandler != null) {
                                    foundSlot = foundHandler.firstInt();
                                    handler = foundHandler.second();
                                    found = handler.getStackInSlot(foundSlot).copy();
                                }
                            } else {
                                for (ItemStack candidate : candidates) {
                                    found = candidate.copy();
                                    if (!found.isEmpty() && found.getItem() instanceof BlockItem) {
                                        break;
                                    }
                                    found = null;
                                }
                            }
                            if (found == null) continue;
                            BlockItem itemBlock = (BlockItem) found.getItem();
                            BlockPlaceContext context = new BlockPlaceContext(world, player, InteractionHand.MAIN_HAND,
                                    found, BlockHitResult.miss(player.getEyePosition(0), Direction.UP, pos));
                            InteractionResult interactionResult = itemBlock.place(context);
                            if (interactionResult != InteractionResult.FAIL) {
                                if (handler != null) {
                                    handler.extractItem(foundSlot, 1, false);
                                }
                                var direction = predicate.direction.apply(worldState);
                                if (direction != null) {
                                    world.setBlock(pos,
                                            world.getBlockState(pos).setValue(DirectionalBlock.FACING, direction),
                                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                                } else {
                                    if (world.getBlockEntity(pos) instanceof MetaMachine be) {
                                        machines.put(posLong, be);
                                    }
                                }
                                blocks.add(posLong);
                            }
                        }
                    }
                }
                z++;
            }
        }
        Direction frontFacing = controller.getFrontFacing();
        machines.long2ObjectEntrySet().fastForEach(entry -> { // adjust facing
            long posLong = entry.getLongKey();
            var machine = entry.getValue();
            BlockPos pos = BlockPos.of(posLong);
            resetFacing(pos, machine.getBlockState(), frontFacing, (p, f) -> {
                if (!blocks.contains(p.relative(f).asLong())) {
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
        for (int l = 0, x = 0; l < this.fingerLength; l++) {
            for (int r = 0; r < repetition[l]; r++) {
                // Checking single slice
                Reference2IntOpenHashMap<SimplePredicate> cacheLayer = new Reference2IntOpenHashMap<>();
                for (int y = 0; y < this.thumbLength; y++) {
                    for (int z = 0; z < this.palmLength; z++) {
                        var bl = this.blockMatches[l];
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
                x++;
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

    private BlockPos setActualRelativeOffset(int x, int y, int z, Direction facing, Direction upwardsFacing,
                                             boolean isFlipped) {
        return new RelativeOffset(x, y, z, structureDir, facing, upwardsFacing, isFlipped).toBlockPos();
    }

    protected BlockPos gerPreviewOffset(int x, int y, int z) {
        int[] c0 = new int[] { x, y, z };
        int[] c1 = new int[3];
        return new BlockPos(c1[0], c1[1], c1[2]);
    }

    @Nullable
    private static IntObjectPair<IItemHandler> getMatchStackWithHandler(
                                                                        List<ItemStack> candidates,
                                                                        IItemHandler handler) {
        if (handler == null) {
            return null;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            @Nullable
            IItemHandler stackCap = stack.getCapability(Capabilities.ItemHandler.ITEM);
            // spotless:off
            if (stackCap != null) {
                var rt = getMatchStackWithHandler(candidates, stackCap);
                if (rt != null) {
                    return rt;
                }
            } else if (candidates.stream().anyMatch(candidate -> ItemStack.isSameItemSameComponents(candidate, stack)) &&
                    !stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return IntObjectPair.of(i, handler);
            }
            // spotless:on
        }
        return null;
    }
}
