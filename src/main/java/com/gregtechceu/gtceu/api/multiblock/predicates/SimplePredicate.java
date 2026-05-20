package com.gregtechceu.gtceu.api.multiblock.predicates;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.multiblock.error.PatternStringError;
import com.gregtechceu.gtceu.api.multiblock.error.SinglePredicateError;
import com.gregtechceu.gtceu.data.lang.LangHandler;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class SimplePredicate {

    private static final Supplier<BlockInfo> NULL_BLOCK_INFO = () -> null;

    public static SimplePredicate ANY = new SimplePredicate(blockWorldState -> true, null, null);
    public static SimplePredicate AIR = new SimplePredicate(blockWorldState -> blockWorldState.getBlockState().isAir(),
            null, null);

    @Nullable
    public Supplier<Block[]> candidates;
    public Supplier<BlockInfo> blockInfo;
    public Predicate<MultiblockState> predicate;
    public List<Component> toolTips;
    public int minCount = -1;
    public int maxCount = -1;
    public int minLayerCount = -1;
    public int maxLayerCount = -1;
    public int previewCount = -1;
    public boolean disableRenderFormed = false;
    public IO io = IO.BOTH;
    public String slotName;
    public String nbtParser;

    public SimplePredicate() {}

    public SimplePredicate(Predicate<MultiblockState> predicate, Supplier<BlockInfo> blockInfo,
                           @Nullable Supplier<Block[]> candidates) {
        this.predicate = predicate;
        this.blockInfo = blockInfo == null ? NULL_BLOCK_INFO : blockInfo;
        this.candidates = candidates;
    }

    public SimplePredicate buildPredicate() {
        return this;
    }

    @OnlyIn(Dist.CLIENT)
    public List<Component> getToolTips(TraceabilityPredicate predicates) {
        List<Component> result = new ArrayList<>();
        if (toolTips != null) {
            result.addAll(toolTips);
        }
        if (minCount == maxCount && maxCount != -1) {
            result.add(Component.translatable("gtpm.multiblock.pattern.error.limited_exact", minCount));
        } else if (minCount != maxCount && minCount != -1 && maxCount != -1) {
            result.add(Component.translatable("gtpm.multiblock.pattern.error.limited_within", minCount, maxCount));
        } else {
            if (minCount != -1) {
                result.add(LangHandler.getFromMultiLang("gtpm.multiblock.pattern.error.limited", 1, minCount));
            }
            if (maxCount != -1) {
                result.add(LangHandler.getFromMultiLang("gtpm.multiblock.pattern.error.limited", 0, maxCount));
            }
        }
        if (predicates == null) return result;
        if (predicates.isSingle()) {
            result.add(Component.translatable("gtpm.multiblock.pattern.single"));
        }
        if (predicates.hasAir()) {
            result.add(Component.translatable("gtpm.multiblock.pattern.replaceable_air"));
        }
        return result;
    }

    public boolean test(MultiblockState blockWorldState) {
        if (predicate.test(blockWorldState)) {
            return checkInnerConditions(blockWorldState);
        }
        return false;
    }

    public boolean testLimited(MultiblockState blockWorldState) {
        if (testGlobal(blockWorldState) && testLayer(blockWorldState)) {
            return checkInnerConditions(blockWorldState);
        }
        return false;
    }

    private boolean checkInnerConditions(MultiblockState blockWorldState) {
        if (disableRenderFormed) {
            blockWorldState.getMatchContext().getOrCreate("renderMask", LongOpenHashSet::new)
                    .add(blockWorldState.getPos().asLong());
        }
        if (io != IO.BOTH) {
            if (blockWorldState.io == IO.BOTH) {
                blockWorldState.io = io;
            } else if (blockWorldState.io != io) {
                blockWorldState.io = null;
            }
        }
        if (nbtParser != null && !blockWorldState.world.isClientSide) {
            BlockEntity te = blockWorldState.getBlockEntity();
            if (te != null) {
                CompoundTag nbt = te.saveWithFullMetadata(blockWorldState.world.registryAccess());
                if (Pattern.compile(nbtParser).matcher(nbt.toString()).find()) {
                    return true;
                }
            }
            blockWorldState.setError(new PatternStringError("The NBT fails to match"));
            return false;
        }
        if (slotName != null) {
            Long2ObjectMap<Set<String>> slots = blockWorldState.getMatchContext().getOrCreate("slots",
                    Long2ObjectArrayMap::new);
            slots.computeIfAbsent(blockWorldState.getPos().asLong(), s -> new HashSet<>()).add(slotName);
            return true;
        }
        return true;
    }

    public boolean testGlobal(MultiblockState blockWorldState) {
        if (minCount == -1 && maxCount == -1) return true;
        boolean base = predicate.test(blockWorldState);
        int count = blockWorldState.getGlobalCount().mergeInt(this, base ? 1 : 0, Integer::sum);
        if (maxCount == -1 || count <= maxCount) return base;
        blockWorldState.setError(new SinglePredicateError(this, 0));
        return false;
    }

    public boolean testLayer(MultiblockState blockWorldState) {
        if (minLayerCount == -1 && maxLayerCount == -1) return true;
        boolean base = predicate.test(blockWorldState);
        int count = blockWorldState.getLayerCount().mergeInt(this, base ? 1 : 0, Integer::sum);
        if (maxLayerCount == -1 || count <= maxLayerCount) return base;
        blockWorldState.setError(new SinglePredicateError(this, 2));
        return false;
    }

    public List<ItemStack> getCandidates() {
        return candidates == null ? Collections.emptyList() : Arrays.stream(this.candidates.get())
                .map(SimplePredicate::toItem).filter(i -> i != Items.AIR).map(Item::getDefaultInstance).toList();
    }

    public static Item toItem(Block block) {
        if (block instanceof LiquidBlock liquidBlock) {
            return liquidBlock.fluid.getBucket();
        } else {
            return block.asItem();
        }
    }
}
