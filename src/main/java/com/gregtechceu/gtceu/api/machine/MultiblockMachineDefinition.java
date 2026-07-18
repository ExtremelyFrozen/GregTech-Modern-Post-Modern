package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.MultiblockShapeInfo;
import com.gregtechceu.gtceu.data.pattern.StructurePatternRegistry;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MultiblockMachineDefinition extends MachineDefinition {

    @Getter
    @Setter
    private boolean generator;
    private final Map<String, Function<MultiblockMachineDefinition, BlockPattern>> patternFactories = new LinkedHashMap<>();
    private final Map<String, BlockPattern> patterns = new LinkedHashMap<>();
    @Setter
    @Getter
    private Supplier<List<MultiblockShapeInfo>> shapes;
    /**
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     */
    @Getter
    @Setter
    private boolean allowFlip;
    @Getter
    @Setter
    private boolean renderXEIPreview;
    @Setter
    @Getter
    @Nullable
    private Supplier<ItemStack[]> recoveryItems;
    @Setter
    @Getter
    private Function<MultiblockControllerMachine, Comparator<IMultiPart>> partSorter;
    @Getter
    @Setter
    private TriFunction<MultiblockControllerMachine, IMultiPart, Direction, BlockState> partAppearance;
    @Getter
    @Setter
    private BiConsumer<MultiblockControllerMachine, List<Component>> additionalDisplay;

    public MultiblockMachineDefinition(ResourceLocation id) {
        super(id);
    }

    public List<MultiblockShapeInfo> getMatchingShapes() {
        var designs = shapes.get();
        if (!designs.isEmpty()) return designs;
        var structurePattern = getPattern(MultiblockControllerMachine.DEFAULT_STRUCTURE);
        if (structurePattern == null) {
            throw new IllegalStateException("Missing main structure pattern for " + getId());
        }
        int[][] aisleRepetitions = structurePattern.aisleRepetitions;
        return repetitionDFS(structurePattern, new ArrayList<>(), aisleRepetitions, new IntArrayList());
    }

    public void setPatternFactory(@NotNull String structureName,
                                  @NotNull Function<MultiblockMachineDefinition, BlockPattern> patternFactory) {
        structureName = validateStructureName(structureName);
        this.patternFactories.put(structureName, patternFactory);
        StructurePatternRegistry.registerJavaDefinition(this, structureName);
    }

    public @Nullable BlockPattern getPattern(@NotNull String structureName) {
        structureName = validateStructureName(structureName);
        requirePatternFactory(structureName);
        synchronized (this.patterns) {
            BlockPattern pattern = this.patterns.get(structureName);
            if (pattern == null) {
                pattern = StructurePatternRegistry.resolvePattern(this, structureName);
                patterns.put(structureName, pattern);
            }
            return pattern;
        }
    }

    public void reloadPattern(@NotNull String structureName) {
        structureName = validateStructureName(structureName);
        requirePatternFactory(structureName);
        synchronized (this.patterns) {
            patterns.put(structureName, StructurePatternRegistry.resolvePattern(this, structureName));
        }
    }

    public BlockPattern createJavaPattern(@NotNull String structureName) {
        structureName = validateStructureName(structureName);
        return requirePatternFactory(structureName).apply(this);
    }

    public Set<String> getStructureNames() {
        return Collections.unmodifiableSet(this.patternFactories.keySet());
    }

    private Function<MultiblockMachineDefinition, BlockPattern> requirePatternFactory(String structureName) {
        Function<MultiblockMachineDefinition, BlockPattern> factory = this.patternFactories.get(structureName);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown multiblock structure '" + structureName + "' for " + getId());
        }
        return factory;
    }

    private static String validateStructureName(String structureName) {
        if (structureName.isBlank()) {
            throw new IllegalArgumentException("structureName must not be blank");
        }
        return structureName;
    }

    private List<MultiblockShapeInfo> repetitionDFS(BlockPattern pattern, List<MultiblockShapeInfo> pages,
                                                    int[][] aisleRepetitions, IntArrayList repetitionStack) {
        if (repetitionStack.size() == aisleRepetitions.length) {
            int[] repetition = new int[repetitionStack.size()];
            for (int i = 0; i < repetitionStack.size(); i++) {
                repetition[i] = repetitionStack.getInt(i);
            }
            pages.add(new MultiblockShapeInfo(pattern.getPreview(this, repetition)));
        } else {
            for (int i = aisleRepetitions[repetitionStack.size()][0]; i <=
                    aisleRepetitions[repetitionStack.size()][1]; i++) {
                repetitionStack.push(i);
                repetitionDFS(pattern, pages, aisleRepetitions, repetitionStack);
                repetitionStack.popInt();
            }
        }
        return pages;
    }
}
