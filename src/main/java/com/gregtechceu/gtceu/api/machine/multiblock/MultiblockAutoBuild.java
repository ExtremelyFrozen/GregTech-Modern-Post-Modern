package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildBlockMap;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildMaterialSource;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildOptions;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildProblem;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildRequest;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildResult;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.multiblock.structurepredicate.RestrictedPredicate;
import com.gregtechceu.gtceu.api.multiblock.structurepredicate.StructurePredicate;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.CommonHooks;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class MultiblockAutoBuild {

    private final MultiblockControllerMachine controller;
    private final ServerPlayer player;
    private final ServerLevel level;
    private final String structureName;
    private final BlockPattern pattern;
    private final AutoBuildOptions options;
    private final List<AutoBuildMaterialSource> materialSources;
    private final MultiblockState worldState;
    private final Direction frontFacing;
    private final Direction upwardsFacing;
    private final boolean flipped;
    private final List<PartAbility> noHatchAbilities = List.of(
            PartAbility.EXPORT_ITEMS,
            PartAbility.IMPORT_ITEMS,
            PartAbility.EXPORT_FLUIDS,
            PartAbility.IMPORT_FLUIDS,
            PartAbility.EXPORT_FLUIDS_1X,
            PartAbility.IMPORT_FLUIDS_1X,
            PartAbility.EXPORT_FLUIDS_4X,
            PartAbility.IMPORT_FLUIDS_4X,
            PartAbility.EXPORT_FLUIDS_9X,
            PartAbility.IMPORT_FLUIDS_9X,
            PartAbility.INPUT_ENERGY,
            PartAbility.OUTPUT_ENERGY,
            PartAbility.SUBSTATION_INPUT_ENERGY,
            PartAbility.SUBSTATION_OUTPUT_ENERGY,
            PartAbility.PUMP_FLUID_HATCH,
            PartAbility.STEAM_IMPORT_ITEMS,
            PartAbility.STEAM_EXPORT_ITEMS,
            PartAbility.COKE_OVEN_HATCH,
            PartAbility.MAINTENANCE,
            PartAbility.MUFFLER,
            PartAbility.PASSTHROUGH_HATCH,
            PartAbility.PARALLEL_HATCH,
            PartAbility.INPUT_LASER,
            PartAbility.OUTPUT_LASER,
            PartAbility.STEAM,
            PartAbility.COMPUTATION_DATA_RECEPTION,
            PartAbility.COMPUTATION_DATA_TRANSMISSION,
            PartAbility.OPTICAL_DATA_RECEPTION,
            PartAbility.OPTICAL_DATA_TRANSMISSION,
            PartAbility.DATA_ACCESS);
    private final LongOpenHashSet occupiedBlocks = new LongOpenHashSet(1024, 0.5F);
    private final Long2ObjectOpenHashMap<MetaMachine> placedMachines = new Long2ObjectOpenHashMap<>();
    private final List<AutoBuildProblem> problems = new ArrayList<>();

    private int placed;
    private int removed;
    private int completedStages;

    private MultiblockAutoBuild(MultiblockControllerMachine controller, ServerPlayer player, String structureName,
                                BlockPattern pattern, AutoBuildRequest request) {
        this.controller = controller;
        this.player = player;
        this.level = (ServerLevel) player.level();
        this.structureName = structureName;
        this.pattern = pattern;
        this.options = request.options();
        this.materialSources = request.materialSources();
        this.worldState = controller.getMultiblockState(structureName);
        this.frontFacing = controller.getFrontFacing();
        this.upwardsFacing = controller.getUpwardsFacing();
        this.flipped = options.flipMode();
    }

    static AutoBuildResult execute(MultiblockControllerMachine controller, ServerPlayer player, String structureName,
                                   BlockPattern pattern, AutoBuildRequest request) {
        return new MultiblockAutoBuild(controller, player, structureName, pattern, request).execute();
    }

    private AutoBuildResult execute() {
        AutoBuildProblem sourceProblem = firstUnavailableSourceProblem();
        if (sourceProblem != null) {
            return AutoBuildResult.failed(sourceProblem);
        }
        AutoBuildProblem optionProblem = validateOptions();
        if (optionProblem != null) {
            return AutoBuildResult.failed(optionProblem);
        }

        int[] repetitions = resolveRepetitions(pattern, options);
        worldState.clean();
        occupiedBlocks.add(controller.getBlockPos().asLong());

        Object2IntOpenHashMap<SimplePredicate> globalCount = worldState.getGlobalCount();
        int z = pattern.getMinZ();
        for (int unit = 0; unit < pattern.aisleRepetitions.length; unit++) {
            int unitStart = pattern.unitStarts[unit];
            int unitDepth = pattern.unitDepths[unit];
            for (int repeat = 0; repeat < repetitions[unit]; repeat++) {
                for (int inner = 0; inner < unitDepth; inner++, z++) {
                    StagePlan stage = planStage(unitStart + inner, z, globalCount);
                    if (stage == null) {
                        return result(false);
                    }
                    if (!commitStage(stage)) {
                        return result(false);
                    }
                    completedStages++;
                }
            }
        }

        pattern.resetPlacedMachineFacings(level, frontFacing, occupiedBlocks, placedMachines);
        return result(true);
    }

    private @Nullable AutoBuildProblem firstUnavailableSourceProblem() {
        for (AutoBuildMaterialSource source : materialSources) {
            AutoBuildProblem problem = source.unavailableProblem();
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private @Nullable AutoBuildProblem validateOptions() {
        if (options.repeatCount() < 0) {
            return problem(AutoBuildProblem.Type.INVALID_OPTIONS, null,
                    Component.translatable("gtpm.multiblock.autobuild.invalid_repeat", options.repeatCount()));
        }
        if (options.flipMode() && !controller.allowFlip()) {
            return problem(AutoBuildProblem.Type.INVALID_OPTIONS, controller.getBlockPos(),
                    Component.translatable("gtpm.multiblock.autobuild.flip_not_allowed"));
        }
        for (Map.Entry<String, Integer> entry : options.tierSelections().entrySet()) {
            Block[] blocks = AutoBuildBlockMap.categoryBlocks(entry.getKey());
            if (blocks == null) {
                return problem(AutoBuildProblem.Type.INVALID_OPTIONS, null,
                        Component.translatable("gtpm.multiblock.autobuild.unknown_tier_category", entry.getKey()));
            }
            int tier = entry.getValue();
            if (tier < 1 || tier > blocks.length) {
                return problem(AutoBuildProblem.Type.INVALID_OPTIONS, null,
                        Component.translatable("gtpm.multiblock.autobuild.invalid_tier", entry.getKey(), tier));
            }
        }
        return null;
    }

    static int[] resolveRepetitions(BlockPattern pattern, AutoBuildOptions options) {
        int[] repetitions = new int[pattern.aisleRepetitions.length];
        for (int index = 0; index < pattern.aisleRepetitions.length; index++) {
            int min = pattern.aisleRepetitions[index][0];
            int max = pattern.aisleRepetitions[index][1];
            int requested = options.repeatCount() == 0 ? min : options.repeatCount();
            repetitions[index] = Math.max(min, Math.min(max, requested));
        }
        return repetitions;
    }

    private @Nullable StagePlan planStage(int patternZ, int worldZ,
                                          Object2IntOpenHashMap<SimplePredicate> globalCount) {
        List<AutoBuildMaterialSource.Session> sessions = openSessions();
        Object2IntOpenHashMap<SimplePredicate> layerCount = worldState.getLayerCount();
        layerCount.clear();
        worldState.getStructureLayerCount().clear();
        StagePlan stage = new StagePlan(sessions);

        for (int yIndex = 0, y = pattern.getMinY(); yIndex < pattern.getThumbLength(); yIndex++, y++) {
            for (int xIndex = 0, x = pattern.getMinX(); xIndex < pattern.getPalmLength(); xIndex++, x++) {
                TraceabilityPredicate predicate = pattern.getPredicate(patternZ, yIndex, xIndex);
                if (predicate == null || predicate.isAny()) {
                    continue;
                }
                BlockPos pos = pattern.getActualRelativeOffset(x, y, worldZ, frontFacing, upwardsFacing, flipped)
                        .offset(controller.getBlockPos());
                if (!worldState.update(pos, predicate)) {
                    problems.add(problem(AutoBuildProblem.Type.UNLOADED, pos,
                            Component.translatable("gtpm.multiblock.autobuild.unloaded", pos.toShortString())));
                    return null;
                }
                if (pos.equals(controller.getBlockPos())) {
                    occupiedBlocks.add(pos.asLong());
                    continue;
                }

                BlockState state = level.getBlockState(pos);
                if (options.demolitionMode()) {
                    if (!planDemolition(stage, pos, state, predicate)) {
                        return null;
                    }
                } else if (!planBuildPosition(stage, pos, state, predicate, layerCount, globalCount)) {
                    return null;
                }
            }
        }
        return stage;
    }

    private List<AutoBuildMaterialSource.Session> openSessions() {
        List<AutoBuildMaterialSource.Session> sessions = new ArrayList<>(materialSources.size());
        for (AutoBuildMaterialSource source : materialSources) {
            sessions.add(source.openSession());
        }
        return sessions;
    }

    private boolean planDemolition(StagePlan stage, BlockPos pos, BlockState state, TraceabilityPredicate predicate) {
        if (state.isAir() || predicate.isAir()) {
            return true;
        }
        if (matchesDemolitionCandidate(state.getBlock(), predicate)) {
            AutoBuildProblem clearProblem = validateClear(pos, state);
            if (clearProblem == null) {
                stage.clears().add(new ClearTask(pos));
            } else {
                problems.add(clearProblem);
                return false;
            }
        }
        return true;
    }

    private boolean planBuildPosition(StagePlan stage, BlockPos pos, BlockState state, TraceabilityPredicate predicate,
                                      Object2IntOpenHashMap<SimplePredicate> layerCount,
                                      Object2IntOpenHashMap<SimplePredicate> globalCount) {
        if (predicate.isAir()) {
            return planAirPosition(stage, pos, state);
        }

        boolean replaceable = state.isAir() || state.canBeReplaced();
        if (!replaceable && predicate.test(worldState)) {
            Direction direction = predicate.getDirection(worldState, frontFacing, upwardsFacing, flipped);
            if (direction == null || pattern.matchesDirectionalPredicate(predicate, worldState, frontFacing,
                    upwardsFacing, flipped)) {
                occupiedBlocks.add(pos.asLong());
                return true;
            }
            BlockState directedState = BlockPattern.applyDirectionalState(state, direction);
            if (directedState != state && directedState.is(state.getBlock())) {
                AutoBuildProblem directionProblem = validateClear(pos, state);
                if (directionProblem != null) {
                    problems.add(directionProblem);
                    return false;
                }
                stage.directions().add(new DirectionTask(pos, direction));
                occupiedBlocks.add(pos.asLong());
                return true;
            }
        }

        if (!replaceable && !options.replaceMode()) {
            problems.add(problem(AutoBuildProblem.Type.BLOCKED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.blocked", pos.toShortString())));
            return false;
        }

        CandidateSelection selection = selectCandidates(predicate, layerCount, globalCount);
        if (selection.problem() != null) {
            problems.add(selection.problem());
            return false;
        }
        if (selection.stacks().isEmpty()) {
            problems.add(problem(AutoBuildProblem.Type.MISSING_MATERIAL, pos,
                    Component.translatable("gtpm.multiblock.autobuild.no_candidate", pos.toShortString())));
            return false;
        }

        AutoBuildMaterialSource.Reservation reservation = reserveMaterial(selection.stacks(), stage.sessions());
        if (reservation == null) {
            problems.add(problem(AutoBuildProblem.Type.MISSING_MATERIAL, pos,
                    Component.translatable("gtpm.multiblock.autobuild.missing_material", pos.toShortString())));
            return false;
        }
        ItemStack reservedStack = reservation.stack();
        if (!(reservedStack.getItem() instanceof BlockItem)) {
            problems.add(problem(AutoBuildProblem.Type.PLACE_FAILED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.not_placeable", reservedStack.getHoverName())));
            return false;
        }
        boolean willClear = !replaceable;
        if (willClear) {
            AutoBuildProblem clearProblem = validateClear(pos, state);
            if (clearProblem != null) {
                problems.add(clearProblem);
                return false;
            }
            stage.clears().add(new ClearTask(pos));
        }
        AutoBuildProblem placeProblem = validatePlacement(pos, reservedStack.copyWithCount(1), willClear);
        if (placeProblem != null) {
            problems.add(placeProblem);
            return false;
        }
        stage.placements().add(new PlaceTask(pos, predicate, reservation));
        occupiedBlocks.add(pos.asLong());
        return true;
    }

    private boolean planAirPosition(StagePlan stage, BlockPos pos, BlockState state) {
        if (state.isAir() || state.canBeReplaced()) {
            return true;
        }
        if (!options.replaceMode()) {
            problems.add(problem(AutoBuildProblem.Type.BLOCKED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.blocked", pos.toShortString())));
            return false;
        }
        AutoBuildProblem clearProblem = validateClear(pos, state);
        if (clearProblem != null) {
            problems.add(clearProblem);
            return false;
        }
        stage.clears().add(new ClearTask(pos));
        return true;
    }

    private CandidateSelection selectCandidates(TraceabilityPredicate predicate,
                                                Object2IntOpenHashMap<SimplePredicate> layerCount,
                                                Object2IntOpenHashMap<SimplePredicate> globalCount) {
        for (SimplePredicate limited : predicate.limited) {
            if (limited.minLayerCount <= 0) {
                continue;
            }
            int count = layerCount.getInt(limited);
            if (count < limited.minLayerCount && (limited.maxLayerCount == -1 || count < limited.maxLayerCount)) {
                CandidateSelection selection = toCandidateStacks(predicate, limited);
                if (!selection.stacks().isEmpty() || selection.problem() != null) {
                    layerCount.addTo(limited, 1);
                    return selection;
                }
            }
        }
        for (StructurePredicate structurePredicate : predicate.structurePredicates) {
            if (needsStructureLayerMinimum(structurePredicate)) {
                CandidateSelection selection = toCandidateStacks(predicate, structurePredicate.blockCandidates());
                if (!selection.stacks().isEmpty() || selection.problem() != null) {
                    addStructureCount(structurePredicate);
                    return selection;
                }
            }
        }

        for (SimplePredicate limited : predicate.limited) {
            if (limited.minCount <= 0) {
                continue;
            }
            int count = globalCount.getInt(limited);
            if (count < limited.minCount && (limited.maxCount == -1 || count < limited.maxCount)) {
                CandidateSelection selection = toCandidateStacks(predicate, limited);
                if (!selection.stacks().isEmpty() || selection.problem() != null) {
                    globalCount.addTo(limited, 1);
                    return selection;
                }
            }
        }
        for (StructurePredicate structurePredicate : predicate.structurePredicates) {
            if (needsStructureGlobalMinimum(structurePredicate)) {
                CandidateSelection selection = toCandidateStacks(predicate, structurePredicate.blockCandidates());
                if (!selection.stacks().isEmpty() || selection.problem() != null) {
                    addStructureCount(structurePredicate);
                    return selection;
                }
            }
        }

        ArrayList<ItemStack> stacks = new ArrayList<>();
        for (SimplePredicate limited : predicate.limited) {
            if (limited.maxLayerCount != -1 && layerCount.getInt(limited) >= limited.maxLayerCount) {
                continue;
            }
            if (limited.maxCount != -1 && globalCount.getInt(limited) >= limited.maxCount) {
                continue;
            }
            CandidateSelection selection = toCandidateStacks(predicate, limited);
            if (selection.problem() != null) {
                return selection;
            }
            if (!selection.stacks().isEmpty()) {
                layerCount.addTo(limited, 1);
                globalCount.addTo(limited, 1);
                stacks.addAll(selection.stacks());
            }
        }
        for (SimplePredicate common : predicate.common) {
            CandidateSelection selection = toCandidateStacks(predicate, common);
            if (selection.problem() != null) {
                return selection;
            }
            stacks.addAll(selection.stacks());
        }
        for (StructurePredicate structurePredicate : predicate.structurePredicates) {
            if (exceedsStructureMax(structurePredicate)) {
                continue;
            }
            CandidateSelection selection = toCandidateStacks(predicate, structurePredicate.blockCandidates());
            if (selection.problem() != null) {
                return selection;
            }
            if (!selection.stacks().isEmpty()) {
                addStructureCount(structurePredicate);
                stacks.addAll(selection.stacks());
            }
        }
        return new CandidateSelection(stacks, null);
    }

    private CandidateSelection toCandidateStacks(TraceabilityPredicate traceabilityPredicate,
                                                 SimplePredicate simplePredicate) {
        if (simplePredicate.candidates == null) {
            return new CandidateSelection(List.of(), null);
        }
        Block[] blocks = simplePredicate.candidates.get();
        if (blocks == null || blocks.length == 0) {
            return new CandidateSelection(List.of(), null);
        }
        return toCandidateStacks(traceabilityPredicate, Arrays.asList(blocks));
    }

    private CandidateSelection toCandidateStacks(TraceabilityPredicate traceabilityPredicate, Iterable<Block> blocks) {
        LinkedHashSet<Block> resolvedBlocks = new LinkedHashSet<>();
        for (Block block : blocks) {
            if (block == null || block == Blocks.AIR) {
                continue;
            }
            Block resolved = resolveTierBlock(block);
            if (shouldFilterNoHatch(traceabilityPredicate, resolved)) {
                continue;
            }
            resolvedBlocks.add(resolved);
        }

        ArrayList<ItemStack> stacks = new ArrayList<>();
        for (Block block : resolvedBlocks) {
            if (block instanceof LiquidBlock) {
                return new CandidateSelection(List.of(), problem(AutoBuildProblem.Type.UNSUPPORTED, null,
                        Component.translatable("gtpm.multiblock.autobuild.unsupported_liquid_candidate",
                                block.getName())));
            }
            Item item = SimplePredicate.toItem(block);
            if (item != Items.AIR) {
                stacks.add(item.getDefaultInstance());
            }
        }
        return new CandidateSelection(stacks, null);
    }

    private boolean needsStructureLayerMinimum(StructurePredicate structurePredicate) {
        if (structurePredicate instanceof RestrictedPredicate restricted &&
                restricted.minCountByLayer().isPresent()) {
            int count = worldState.getStructureLayerCount().getInt(restricted);
            return count < restricted.minCountByLayer().get() &&
                    (restricted.maxCountByLayer().isEmpty() || count < restricted.maxCountByLayer().get());
        }
        return false;
    }

    private boolean needsStructureGlobalMinimum(StructurePredicate structurePredicate) {
        if (structurePredicate instanceof RestrictedPredicate restricted && restricted.minCount().isPresent()) {
            int count = worldState.getStructureGlobalCount().getInt(restricted);
            return count < restricted.minCount().get() &&
                    (restricted.maxCount().isEmpty() || count < restricted.maxCount().get());
        }
        return false;
    }

    private boolean exceedsStructureMax(StructurePredicate structurePredicate) {
        if (structurePredicate instanceof RestrictedPredicate restricted) {
            if (restricted.maxCountByLayer().isPresent() &&
                    worldState.getStructureLayerCount().getInt(restricted) >= restricted.maxCountByLayer().get()) {
                return true;
            }
            return restricted.maxCount().isPresent() &&
                    worldState.getStructureGlobalCount().getInt(restricted) >= restricted.maxCount().get();
        }
        return false;
    }

    private void addStructureCount(StructurePredicate structurePredicate) {
        if (structurePredicate instanceof RestrictedPredicate restricted) {
            if (restricted.minCountByLayer().isPresent() || restricted.maxCountByLayer().isPresent()) {
                worldState.getStructureLayerCount().addTo(restricted, 1);
            }
            if (restricted.minCount().isPresent() || restricted.maxCount().isPresent()) {
                worldState.getStructureGlobalCount().addTo(restricted, 1);
            }
        }
    }

    private Block resolveTierBlock(Block block) {
        String category = AutoBuildBlockMap.category(block);
        if (category == null) {
            return block;
        }
        if (!options.tierSelections().containsKey(category)) {
            category = selectedTierCategory(category);
        }
        if (category == null) {
            return block;
        }
        Block[] blocks = AutoBuildBlockMap.categoryBlocks(category);
        int tier = options.tierSelections().get(category);
        return blocks[tier - 1];
    }

    private @Nullable String selectedTierCategory(String category) {
        for (String selectedCategory : options.tierSelections().keySet()) {
            if (category.equals(AutoBuildBlockMap.canonicalCategory(selectedCategory))) {
                return selectedCategory;
            }
        }
        return null;
    }

    private boolean shouldFilterNoHatch(TraceabilityPredicate predicate, Block block) {
        if (!options.noHatchMode() || predicate.isSingle()) {
            return false;
        }
        if (block instanceof MetaMachineBlock) {
            return noHatchAbilities.stream().anyMatch(ability -> ability.isApplicable(block));
        }
        return false;
    }

    private @Nullable AutoBuildMaterialSource.Reservation reserveMaterial(List<ItemStack> candidates,
                                                                          List<AutoBuildMaterialSource.Session> sessions) {
        if (player.isCreative()) {
            for (ItemStack candidate : candidates) {
                if (!candidate.isEmpty() && candidate.getItem() instanceof BlockItem) {
                    return new CreativeReservation(candidate.copyWithCount(1));
                }
            }
            return null;
        }
        for (AutoBuildMaterialSource.Session session : sessions) {
            AutoBuildMaterialSource.Reservation reservation = session.reserve(candidates);
            if (reservation != null) {
                return reservation;
            }
        }
        return null;
    }

    private boolean commitStage(StagePlan stage) {
        if (!approveClears(stage)) {
            return false;
        }
        ArrayList<ItemStack> committedStacks = new ArrayList<>();
        for (PlaceTask placement : stage.placements()) {
            if (!placement.reservation().commit()) {
                refundCommittedStacks(stage.sessions(), committedStacks, placement.pos());
                problems.add(problem(AutoBuildProblem.Type.MISSING_MATERIAL, placement.pos(),
                        Component.translatable("gtpm.multiblock.autobuild.commit_material_failed",
                                placement.pos().toShortString())));
                return false;
            }
            committedStacks.add(placement.reservation().stack().copyWithCount(1));
        }

        for (ClearTask clear : stage.clears()) {
            if (!clearBlock(stage.sessions(), clear.pos())) {
                refundCommittedStacks(stage.sessions(), committedStacks, clear.pos());
                return false;
            }
        }
        for (DirectionTask direction : stage.directions()) {
            applyDirection(direction);
        }
        for (int index = 0; index < stage.placements().size(); index++) {
            PlaceTask placement = stage.placements().get(index);
            if (!placeBlock(stage.sessions(), placement, committedStacks.get(index))) {
                refundCommittedStacks(stage.sessions(), committedStacks.subList(index + 1, committedStacks.size()),
                        placement.pos());
                return false;
            }
        }
        return true;
    }

    private boolean approveClears(StagePlan stage) {
        for (ClearTask clear : stage.clears()) {
            BlockPos pos = clear.pos();
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player,
                    pos, state).isCanceled()) {
                problems.add(problem(AutoBuildProblem.Type.PERMISSION_DENIED, pos,
                        Component.translatable("gtpm.multiblock.autobuild.permission_denied", pos.toShortString())));
                return false;
            }
        }
        return true;
    }

    private void refundCommittedStacks(List<AutoBuildMaterialSource.Session> sessions, List<ItemStack> stacks,
                                       BlockPos pos) {
        for (ItemStack stack : stacks) {
            insertOrDrop(sessions, stack, pos);
        }
    }

    private boolean clearBlock(List<AutoBuildMaterialSource.Session> sessions, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player,
                player.getMainHandItem());
        if (!level.destroyBlock(pos, false, player)) {
            problems.add(problem(AutoBuildProblem.Type.DEMOLITION_FAILED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.demolition_failed", pos.toShortString())));
            return false;
        }
        for (ItemStack drop : drops) {
            insertOrDrop(sessions, drop, pos);
        }
        removed++;
        return true;
    }

    private @Nullable AutoBuildProblem validatePlacement(BlockPos pos, ItemStack stack, boolean willClear) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return problem(AutoBuildProblem.Type.PLACE_FAILED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.not_placeable", stack.getHoverName()));
        }
        if (!blockItem.getBlock().isEnabled(level.enabledFeatures())) {
            return problem(AutoBuildProblem.Type.PLACE_FAILED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.place_failed", pos.toShortString()));
        }
        if (!player.mayUseItemAt(pos.relative(Direction.UP), Direction.UP, stack)) {
            return problem(AutoBuildProblem.Type.PERMISSION_DENIED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.permission_denied", pos.toShortString()));
        }
        BlockPlaceContext context = placementContext(pos, stack, willClear);
        if (!willClear && !context.canPlace()) {
            return problem(AutoBuildProblem.Type.PLACE_FAILED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.place_failed", pos.toShortString()));
        }
        BlockPlaceContext updatedContext = blockItem.updatePlacementContext(context);
        if (updatedContext == null) {
            return problem(AutoBuildProblem.Type.PLACE_FAILED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.place_failed", pos.toShortString()));
        }
        BlockState placementState = blockItem.getBlock().getStateForPlacement(updatedContext);
        if (placementState == null) {
            return problem(AutoBuildProblem.Type.PLACE_FAILED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.place_failed", pos.toShortString()));
        }
        if (!placementState.canSurvive(level, updatedContext.getClickedPos())) {
            return problem(AutoBuildProblem.Type.PLACE_FAILED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.place_failed", pos.toShortString()));
        }
        CollisionContext collisionContext = CollisionContext.of(player);
        if (!level.isUnobstructed(placementState, updatedContext.getClickedPos(), collisionContext)) {
            return problem(AutoBuildProblem.Type.PLACE_FAILED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.place_failed", pos.toShortString()));
        }
        return null;
    }

    private void insertOrDrop(List<AutoBuildMaterialSource.Session> sessions, ItemStack stack, BlockPos pos) {
        ItemStack remainder = stack.copy();
        for (AutoBuildMaterialSource.Session session : sessions) {
            if (remainder.isEmpty()) {
                return;
            }
            remainder = session.insert(remainder, false);
        }
        if (!remainder.isEmpty()) {
            Block.popResource(level, pos, remainder);
        }
    }

    private void applyDirection(DirectionTask task) {
        BlockState state = level.getBlockState(task.pos());
        BlockState directedState = BlockPattern.applyDirectionalState(state, task.direction());
        if (directedState != state) {
            level.setBlock(task.pos(), directedState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private boolean placeBlock(List<AutoBuildMaterialSource.Session> sessions, PlaceTask placement,
                               ItemStack committedStack) {
        ItemStack stack = committedStack.copyWithCount(1);
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            insertOrDrop(sessions, stack, placement.pos());
            problems.add(problem(AutoBuildProblem.Type.PLACE_FAILED, placement.pos(),
                    Component.translatable("gtpm.multiblock.autobuild.not_placeable", stack.getHoverName())));
            return false;
        }

        BlockPlaceContext context = placementContext(placement.pos(), stack, true);
        InteractionResult result = blockItem.place(context);
        if (result == InteractionResult.FAIL) {
            insertOrDrop(sessions, stack, placement.pos());
            problems.add(problem(AutoBuildProblem.Type.PLACE_FAILED, placement.pos(),
                    Component.translatable("gtpm.multiblock.autobuild.place_failed",
                            placement.pos().toShortString())));
            return false;
        }

        worldState.update(placement.pos(), placement.predicate());
        Direction direction = placement.predicate().getDirection(worldState, frontFacing, upwardsFacing, flipped);
        if (direction != null) {
            BlockState directedState = BlockPattern.applyDirectionalState(level.getBlockState(placement.pos()),
                    direction);
            if (directedState != level.getBlockState(placement.pos())) {
                level.setBlock(placement.pos(), directedState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        if (level.getBlockEntity(placement.pos()) instanceof MetaMachine machine) {
            placedMachines.put(placement.pos().asLong(), machine);
        }
        placed++;
        return true;
    }

    private BlockPlaceContext placementContext(BlockPos pos, ItemStack stack, boolean forceTargetPos) {
        return new AutoBuildPlaceContext(level, player, stack, pos, forceTargetPos);
    }

    private static final class AutoBuildPlaceContext extends BlockPlaceContext {

        private AutoBuildPlaceContext(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos,
                                      boolean forceTargetPos) {
            super(level, player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            if (forceTargetPos) {
                replaceClicked = true;
            }
        }
    }

    private @Nullable AutoBuildProblem validateClear(BlockPos pos, BlockState state) {
        if (pos.equals(controller.getBlockPos())) {
            return problem(AutoBuildProblem.Type.PERMISSION_DENIED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.controller_protected"));
        }
        if (!level.isLoaded(pos)) {
            return problem(AutoBuildProblem.Type.UNLOADED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.unloaded", pos.toShortString()));
        }
        if (state.hasBlockEntity() && level.getBlockEntity(pos) instanceof MetaMachine machine &&
                !MachineOwner.canBreakOwnerMachine(player, machine)) {
            return problem(AutoBuildProblem.Type.PERMISSION_DENIED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.permission_denied", pos.toShortString()));
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return problem(AutoBuildProblem.Type.DEMOLITION_FAILED, pos,
                    Component.translatable("gtpm.multiblock.autobuild.unbreakable", pos.toShortString()));
        }
        return null;
    }

    private boolean matchesDemolitionCandidate(Block currentBlock, TraceabilityPredicate predicate) {
        String currentCategory = AutoBuildBlockMap.category(currentBlock);
        for (SimplePredicate simplePredicate : predicate.limited) {
            if (matchesDemolitionCandidate(currentBlock, currentCategory, simplePredicate)) {
                return true;
            }
        }
        for (SimplePredicate simplePredicate : predicate.common) {
            if (matchesDemolitionCandidate(currentBlock, currentCategory, simplePredicate)) {
                return true;
            }
        }
        for (StructurePredicate structurePredicate : predicate.structurePredicates) {
            if (matchesDemolitionCandidate(currentBlock, currentCategory, structurePredicate.blockCandidates())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDemolitionCandidate(Block currentBlock, @Nullable String currentCategory,
                                               SimplePredicate simplePredicate) {
        if (simplePredicate.candidates == null) {
            return false;
        }
        Block[] candidates = simplePredicate.candidates.get();
        if (candidates == null) {
            return false;
        }
        for (Block candidate : candidates) {
            if (candidate == currentBlock) {
                return true;
            }
            if (currentCategory != null && currentCategory.equals(AutoBuildBlockMap.category(candidate))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDemolitionCandidate(Block currentBlock, @Nullable String currentCategory,
                                               Iterable<Block> candidates) {
        for (Block candidate : candidates) {
            if (candidate == currentBlock) {
                return true;
            }
            if (currentCategory != null && currentCategory.equals(AutoBuildBlockMap.category(candidate))) {
                return true;
            }
        }
        return false;
    }

    private AutoBuildProblem problem(AutoBuildProblem.Type type, @Nullable BlockPos pos, Component message) {
        if (type == AutoBuildProblem.Type.UNKNOWN_STRUCTURE || type == AutoBuildProblem.Type.INVALID_OPTIONS ||
                type == AutoBuildProblem.Type.PATTERN_UNAVAILABLE ||
                type == AutoBuildProblem.Type.ME_UNAVAILABLE) {
            GTCEu.LOGGER.warn("Multiblock auto-build problem for {}: {}", controller.getDefinition().getId(),
                    message.getString());
        }
        return new AutoBuildProblem(type, pos, message);
    }

    private AutoBuildResult result(boolean success) {
        return new AutoBuildResult(success && problems.isEmpty(), placed, removed, completedStages, problems);
    }

    private record StagePlan(List<AutoBuildMaterialSource.Session> sessions, List<ClearTask> clears,
                             List<DirectionTask> directions, List<PlaceTask> placements) {

        private StagePlan(List<AutoBuildMaterialSource.Session> sessions) {
            this(sessions, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    private record ClearTask(BlockPos pos) {}

    private record DirectionTask(BlockPos pos, Direction direction) {}

    private record PlaceTask(BlockPos pos, TraceabilityPredicate predicate,
                             AutoBuildMaterialSource.Reservation reservation) {}

    private record CandidateSelection(List<ItemStack> stacks, @Nullable AutoBuildProblem problem) {}

    private record CreativeReservation(ItemStack stack) implements AutoBuildMaterialSource.Reservation {

        @Override
        public boolean commit() {
            return true;
        }
    }
}
