package com.gregtechceu.gtceu.api.registry.registrate;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.CenterOffset;
import com.gregtechceu.gtceu.api.multiblock.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.MultiBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.multiblock.predicates.PredicateController;
import com.gregtechceu.gtceu.data.pattern.StructureCache;
import com.gregtechceu.gtceu.utils.memoization.GTMemoizer;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import com.fasterxml.jackson.databind.JsonNode;
import dev.latvian.mods.rhino.util.HideFromJS;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.Tolerate;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

@Accessors(chain = true, fluent = true)
public class MultiblockMachineBuilder<DEFINITION extends MultiblockMachineDefinition,
        TYPE extends MultiblockMachineBuilder<DEFINITION, TYPE>> extends MachineBuilder<DEFINITION, TYPE> {

    private boolean generator;
    private Function<MultiblockMachineDefinition, BlockPattern> pattern;
    private final List<Function<MultiblockMachineDefinition, List<MultiblockShapeInfo>>> shapeInfos = new ArrayList<>();
    /**
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     */
    private boolean allowFlip = true;
    private final List<Supplier<ItemStack[]>> recoveryItems = new ArrayList<>();
    private Function<MultiblockControllerMachine, Comparator<IMultiPart>> partSorter = (c) -> (a, b) -> 0;
    private @Nullable TriFunction<MultiblockControllerMachine, IMultiPart, Direction, BlockState> partAppearance;
    @Getter
    private BiConsumer<MultiblockControllerMachine, List<Component>> additionalDisplay = (m, l) -> {};

    public MultiblockMachineBuilder(GTRegistrate registrate, String name,
                                    BiFunction<BlockBehaviour.Properties, DEFINITION, MetaMachineBlock> blockFactory,
                                    BiFunction<MetaMachineBlock, Item.Properties, MetaMachineItem> itemFactory,
                                    Function<BlockEntityCreationInfo, MetaMachine> blockEntityFactory) {
        super(registrate, name, (loc -> (DEFINITION) new MultiblockMachineDefinition(loc)),
                blockFactory,
                itemFactory, blockEntityFactory);
        allowExtendedFacing(true);
        allowCoverOnFront(true);
        // always add the formed property to multi controllers
        modelProperty(GTMachineModelProperties.IS_FORMED, false);
    }

    public TYPE generator(boolean generator) {
        this.generator = generator;
        return getThis();
    }

    public TYPE pattern(Function<MultiblockMachineDefinition, BlockPattern> pattern) {
        this.pattern = pattern;
        return getThis();
    }

    public TYPE allowFlip(boolean allowFlip) {
        this.allowFlip = allowFlip;
        return getThis();
    }

    public TYPE partSorter(Function<MultiblockControllerMachine, Comparator<IMultiPart>> partSorter) {
        this.partSorter = partSorter;
        return getThis();
    }

    public TYPE partAppearance(@Nullable TriFunction<MultiblockControllerMachine, IMultiPart, Direction, BlockState> partAppearance) {
        this.partAppearance = partAppearance;
        return getThis();
    }

    public TYPE additionalDisplay(BiConsumer<MultiblockControllerMachine, List<Component>> additionalDisplay) {
        this.additionalDisplay = additionalDisplay;
        return getThis();
    }

    public TYPE shapeInfo(Function<MultiblockMachineDefinition, MultiblockShapeInfo> shape) {
        this.shapeInfos.add(d -> List.of(shape.apply(d)));
        return getThis();
    }

    public TYPE shapeInfos(Function<MultiblockMachineDefinition, List<MultiblockShapeInfo>> shapes) {
        this.shapeInfos.add(shapes);
        return getThis();
    }

    public TYPE recoveryItems(Supplier<ItemLike[]> items) {
        this.recoveryItems.add(() -> Arrays.stream(items.get()).map(ItemLike::asItem).map(Item::getDefaultInstance)
                .toArray(ItemStack[]::new));
        return getThis();
    }

    public TYPE recoveryStacks(Supplier<ItemStack[]> stacks) {
        this.recoveryItems.add(stacks);
        return getThis();
    }

    @Tolerate
    public TYPE partSorter(Comparator<IMultiPart> sorter) {
        this.partSorter = $ -> sorter;
        return getThis();
    }

    @Override
    @HideFromJS
    public DEFINITION register() {
        var definition = super.register();
        definition.setGenerator(generator);
        // noinspection ConstantValue it can be null by mistake.
        if (pattern == null) {
            GTCEu.LOGGER.error(
                    "missing pattern while creating multiblock {}, something's likely gone very wrong! Check the full log.",
                    name);
        }
        Supplier<BlockPattern> baselinePattern = GTMemoizer.memoize(() -> pattern.apply(definition));
        definition.setPatternFactory(() -> getReloadablePattern(definition, baselinePattern));
        definition.setShapes(() -> shapeInfos.stream().map(factory -> factory.apply(definition))
                .flatMap(Collection::stream).toList());
        definition.setAllowFlip(allowFlip);
        if (!recoveryItems.isEmpty()) {
            definition.setRecoveryItems(
                    () -> recoveryItems.stream().map(Supplier::get).flatMap(Arrays::stream).toArray(ItemStack[]::new));
        }
        definition.setPartSorter(GTMemoizer.memoizeFunctionWeakIdent(partSorter));
        if (partAppearance == null) {
            partAppearance = (controller, part, side) -> definition.getAppearance().get();
        }
        definition.setPartAppearance(partAppearance);
        definition.setAdditionalDisplay(additionalDisplay);
        return definition;
    }

    private BlockPattern getReloadablePattern(MultiblockMachineDefinition definition,
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
            return rebuildStringArrayPattern(id, baselinePattern.get(), stringArrayPattern);
        }

        return baselinePattern.get();
    }

    private BlockPattern rebuildStringArrayPattern(ResourceLocation id, BlockPattern baselinePattern,
                                                   JsonNode jsonPattern) {
        List<FactoryBlockPattern.JsonAisleUnit> jsonUnits = FactoryBlockPattern.parseJsonDefinition(id, jsonPattern);
        List<String[]> aisles = jsonUnits.stream()
                .flatMap(unit -> unit.slices().stream())
                .toList();
        MultiBlockPattern baselineDefinition = new MultiBlockPattern(baselinePattern);
        Map<Character, TraceabilityPredicate> predicates = collectPredicates(id, baselineDefinition);

        int aisleHeight = aisles.getFirst().length;
        int rowWidth = aisles.getFirst()[0].length();
        int aisleCount = aisles.size();
        int unitCount = jsonUnits.size();

        TraceabilityPredicate[][][] blockMatches = new TraceabilityPredicate[aisleCount][aisleHeight][rowWidth];
        String[][] structureSlices = new String[aisleCount][];
        int[][] aisleRepetitions = new int[unitCount][];
        int[] unitStarts = new int[unitCount];
        int[] unitDepths = new int[unitCount];
        CenterOffset centerOffset = null;

        for (int unitIndex = 0, aisleIndex = 0, minZ = 0, maxZ = 0; unitIndex < unitCount; unitIndex++) {
            FactoryBlockPattern.JsonAisleUnit unit = jsonUnits.get(unitIndex);
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

    private Map<Character, TraceabilityPredicate> collectPredicates(ResourceLocation id,
                                                                    MultiBlockPattern baselineDefinition) {
        Map<Character, TraceabilityPredicate> predicates = new HashMap<>();
        predicates.put(' ', Predicates.any());
        for (MultiBlockPattern.Unit unit : baselineDefinition.getUnits()) {
            if (unit.getPredicates() == null || unit.getPredicates().size() != unit.getSlices().size()) {
                throw new IllegalStateException("Baseline multiblock pattern for " + id +
                        " is missing predicate slices");
            }
            for (int slice = 0; slice < unit.getSlices().size(); slice++) {
                String[] rows = unit.getSlices().get(slice);
                TraceabilityPredicate[][] predicateRows = unit.getPredicates().get(slice);
                for (int row = 0; row < rows.length; row++) {
                    String rowText = rows[row];
                    for (int column = 0; column < rowText.length(); column++) {
                        char symbol = rowText.charAt(column);
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
        }
        return predicates;
    }
}
