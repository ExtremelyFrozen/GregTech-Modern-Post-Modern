package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TraceabilityPredicate {

    public List<SimplePredicate> common = new ArrayList<>();
    public List<SimplePredicate> limited = new ArrayList<>();
    public Function<MultiblockState, Direction> direction = o -> null;
    public Direction fixedDirection;
    public RelativeDirection relativeDirection;

    public TraceabilityPredicate() {}

    public TraceabilityPredicate(Predicate<MultiblockState> predicate, Supplier<BlockInfo> blockInfo,
                                 @Nullable Supplier<Block[]> candidates) {
        this();
        common.add(new SimplePredicate(predicate, blockInfo, candidates));
    }

    public TraceabilityPredicate(Predicate<MultiblockState> predicate, Supplier<BlockInfo[]> candidates) {
        this(predicate, () -> {
            BlockInfo[] infos = candidates.get();
            return infos.length == 0 ? BlockInfo.EMPTY : infos[0];
        }, () -> Arrays.stream(candidates.get())
                .map(BlockInfo::getBlockState)
                .map(BlockState::getBlock)
                .toArray(Block[]::new));
    }

    public TraceabilityPredicate(SimplePredicate simplePredicate) {
        this();
        if (simplePredicate.minCount != -1 || simplePredicate.maxCount != -1) {
            limited.add(simplePredicate);
        } else {
            common.add(simplePredicate);
        }
    }

    protected TraceabilityPredicate(TraceabilityPredicate predicate) {
        common.addAll(predicate.common);
        limited.addAll(predicate.limited);
        this.direction = predicate.direction;
        this.fixedDirection = predicate.fixedDirection;
        this.relativeDirection = predicate.relativeDirection;
    }

    public TraceabilityPredicate sort() {
        limited.sort(Comparator.comparingInt(a -> a.minCount));
        return this;
    }

    public TraceabilityPredicate setDirection(Direction direction) {
        this.fixedDirection = direction;
        this.relativeDirection = null;
        this.direction = state -> direction;
        return this;
    }

    public TraceabilityPredicate setRelativeDirection(RelativeDirection direction) {
        this.fixedDirection = null;
        this.relativeDirection = direction;
        return this;
    }

    public Direction getDirection(MultiblockState state, Direction frontFacing, Direction upwardsFacing,
                                  boolean isFlipped) {
        if (relativeDirection != null) {
            return relativeDirection.getRelative(frontFacing, upwardsFacing, isFlipped);
        }
        if (fixedDirection != null) {
            return fixedDirection;
        }
        return direction.apply(state);
    }

    public Direction getPreviewDirection() {
        if (relativeDirection != null) {
            return relativeDirection.global;
        }
        return fixedDirection;
    }

    /**
     * Add tooltips for candidates. They are shown in JEI Pages.
     */
    public TraceabilityPredicate addTooltips(Component... tips) {
        if (tips.length > 0) {
            List<Component> tooltips = Arrays.stream(tips).toList();
            common.forEach(predicate -> {
                if (predicate.candidates == null) return;
                if (predicate.toolTips == null) {
                    predicate.toolTips = new ArrayList<>();
                }
                predicate.toolTips.addAll(tooltips);
            });
            limited.forEach(predicate -> {
                if (predicate.candidates == null) return;
                if (predicate.toolTips == null) {
                    predicate.toolTips = new ArrayList<>();
                }
                predicate.toolTips.addAll(tooltips);
            });
        }
        return this;
    }

    /**
     * Set the minimum number of candidate blocks.
     */
    public TraceabilityPredicate setMinGlobalLimited(int min) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.minCount = min;
        }
        return this;
    }

    public TraceabilityPredicate setMinGlobalLimited(int min, int previewCount) {
        return this.setMinGlobalLimited(min).setPreviewCount(previewCount);
    }

    /**
     * Set the maximum number of candidate blocks.
     */
    public TraceabilityPredicate setMaxGlobalLimited(int max) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.maxCount = max;
        }
        return this;
    }

    public TraceabilityPredicate setMaxGlobalLimited(int max, int previewCount) {
        return this.setMaxGlobalLimited(max).setPreviewCount(previewCount);
    }

    /**
     * Set the minimum number of candidate blocks for each aisle layer.
     */
    public TraceabilityPredicate setMinLayerLimited(int min) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.minLayerCount = min;
        }
        return this;
    }

    public TraceabilityPredicate setMinLayerLimited(int min, int previewCount) {
        return this.setMinLayerLimited(min).setPreviewCount(previewCount);
    }

    /**
     * Set the maximum number of candidate blocks for each aisle layer.
     */
    public TraceabilityPredicate setMaxLayerLimited(int max) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.maxLayerCount = max;
        }
        return this;
    }

    public TraceabilityPredicate setMaxLayerLimited(int max, int previewCount) {
        return this.setMaxLayerLimited(max).setPreviewCount(previewCount);
    }

    /**
     * Sets the Minimum and Maximum limit to the passed value
     *
     * @param limit The Maximum and Minimum limit
     */
    public TraceabilityPredicate setExactLimit(int limit) {
        return this.setMinGlobalLimited(limit).setMaxGlobalLimited(limit);
    }

    /**
     * Set the number of it appears in JEI pages. It only affects JEI preview. (The specific number)
     */
    public TraceabilityPredicate setPreviewCount(int count) {
        common.forEach(predicate -> predicate.previewCount = count);
        limited.forEach(predicate -> predicate.previewCount = count);
        return this;
    }

    /**
     * Set renderMask.
     */
    public TraceabilityPredicate disableRenderFormed() {
        common.forEach(predicate -> predicate.disableRenderFormed = true);
        limited.forEach(predicate -> predicate.disableRenderFormed = true);
        return this;
    }

    /**
     * Set io.
     */
    public TraceabilityPredicate setIO(IO io) {
        common.forEach(predicate -> predicate.io = io);
        limited.forEach(predicate -> predicate.io = io);
        return this;
    }

    public TraceabilityPredicate setNBTParser(String nbtParser) {
        common.forEach(predicate -> predicate.nbtParser = nbtParser);
        limited.forEach(predicate -> predicate.nbtParser = nbtParser);
        return this;
    }

    public TraceabilityPredicate setSlotName(String slotName) {
        common.forEach(predicate -> predicate.slotName = slotName);
        limited.forEach(predicate -> predicate.slotName = slotName);
        return this;
    }

    public boolean test(MultiblockState blockWorldState) {
        blockWorldState.io = IO.BOTH;
        boolean flag = false;
        for (SimplePredicate predicate : limited) {
            if (predicate.testLimited(blockWorldState)) {
                flag = true;
            }
        }
        flag = flag || common.stream().anyMatch(predicate -> predicate.test(blockWorldState));
        if (flag) {
            blockWorldState.setError(null);
        }
        return flag;
    }

    public TraceabilityPredicate or(TraceabilityPredicate other) {
        if (other != null) {
            TraceabilityPredicate newPredicate = new TraceabilityPredicate(this);
            newPredicate.common.addAll(other.common);
            newPredicate.limited.addAll(other.limited);
            return newPredicate;
        }
        return this;
    }

    public boolean isAny() {
        return this.common.size() == 1 && this.limited.isEmpty() && this.common.getFirst() == SimplePredicate.ANY;
    }

    public boolean addCache() {
        return !isAny();
    }

    public boolean isAir() {
        return this.common.size() == 1 && this.limited.isEmpty() && this.common.getFirst() == SimplePredicate.AIR;
    }

    public boolean isSingle() {
        return !isAny() && !isAir() && this.common.size() + this.limited.size() == 1;
    }

    public boolean hasAir() {
        return this.common.contains(SimplePredicate.AIR);
    }
}
