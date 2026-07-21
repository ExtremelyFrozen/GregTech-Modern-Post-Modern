package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GCYMBlocks;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MultiblockStructurePreviewTest {

    private static final String BATCH = "MultiblockStructurePreview";
    private static final BlockPos PREVIEW_ORIGIN = new BlockPos(0, 50, 0);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void jsonStructurePredicatesPopulateGeneratedPreview(GameTestHelper helper) {
        MultiblockMachineDefinition definition = GCYMMachines.LARGE_SOLIDIFIER;
        List<MultiblockShapeInfo> shapes = definition.getMatchingShapes();

        helper.assertTrue(shapes.size() == 1, "Large solidifier should have one non-repeating preview page");

        int controllerCount = 0;
        int casingCount = 0;
        int nonAirCount = 0;
        Block controllerBlock = definition.getBlock();
        for (MultiblockBlockInfo[][] aisle : shapes.getFirst().getBlocks()) {
            for (MultiblockBlockInfo[] row : aisle) {
                for (MultiblockBlockInfo blockInfo : row) {
                    if (blockInfo == null || blockInfo.getBlockState().isAir()) continue;

                    Block block = blockInfo.getBlockState().getBlock();
                    nonAirCount++;
                    if (block == controllerBlock) controllerCount++;
                    if (block == GCYMBlocks.CASING_WATERTIGHT.get()) casingCount++;
                }
            }
        }

        helper.assertTrue(nonAirCount > 1,
                "JSON structure predicates were ignored and produced only the controller preview block");
        helper.assertTrue(controllerCount == 1,
                "Generated preview must contain exactly one multiblock controller");
        helper.assertTrue(casingCount >= 45,
                "Generated preview did not satisfy the JSON minimum watertight casing count");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void everyEmiMultiblockHasGeneratedPreview(GameTestHelper helper) {
        int definitions = 0;
        for (var machine : GTRegistries.MACHINES) {
            if (!(machine instanceof MultiblockMachineDefinition definition) || !definition.isRenderXEIPreview()) {
                continue;
            }
            definitions++;
            List<MultiblockShapeInfo> shapes = definition.getMatchingShapes();
            helper.assertTrue(!shapes.isEmpty(), "No generated preview page for " + definition.getId());
            for (int page = 0; page < shapes.size(); page++) {
                MultiblockShapeInfo shape = shapes.get(page);
                int controllerCount = 0;
                int visibleBlocks = 0;
                for (MultiblockBlockInfo[][] aisle : shape.getBlocks()) {
                    for (MultiblockBlockInfo[] row : aisle) {
                        for (MultiblockBlockInfo blockInfo : row) {
                            if (blockInfo == null || blockInfo.getBlockState().isAir()) continue;
                            visibleBlocks++;
                            if (blockInfo.getBlockState().getBlock() == definition.getBlock()) {
                                controllerCount++;
                            }
                        }
                    }
                }
                helper.assertTrue(visibleBlocks > 0, "Generated preview is empty for " + definition.getId());
                helper.assertTrue(controllerCount == 1,
                        "Generated preview must contain one controller for " + definition.getId());
                assertIdentityPreviewForms(helper, definition, shape, page);
            }
        }
        helper.assertTrue(definitions > 0, "No EMI multiblock definitions were registered");
        helper.succeed();
    }

    private static void assertIdentityPreviewForms(GameTestHelper helper, MultiblockMachineDefinition definition,
                                                   MultiblockShapeInfo shape, int page) {
        List<BlockPos> placedPositions = new ArrayList<>();
        try {
            MultiblockControllerMachine controller = placeIdentityPreview(helper, definition, shape, page,
                    placedPositions);
            BlockPattern pattern = definition.getPattern(MultiblockControllerMachine.DEFAULT_STRUCTURE);
            if (pattern == null) {
                throw new IllegalStateException("Missing main pattern for " + definition.getId());
            }

            MultiblockState state = controller.getMultiblockState(MultiblockControllerMachine.DEFAULT_STRUCTURE);
            boolean matched = pattern.checkPatternAt(state, true);
            String context = definition.getId() + " page " + page + ", controller=" + controller.getBlockPos() +
                    ", front=" + controller.getFrontFacing() + ", upwards=" + controller.getUpwardsFacing();
            helper.assertTrue(matched,
                    "Identity preview coordinates did not match " + context + ": " + patternError(state));

            controller.formStructure(MultiblockControllerMachine.DEFAULT_STRUCTURE);
            helper.assertTrue(controller.isFormed() &&
                    controller.isStructureFormed(MultiblockControllerMachine.DEFAULT_STRUCTURE),
                    "Matched identity preview did not form " + context);
        } finally {
            clearPreview(helper.getLevel(), placedPositions);
        }
    }

    private static MultiblockControllerMachine placeIdentityPreview(GameTestHelper helper,
                                                                    MultiblockMachineDefinition definition,
                                                                    MultiblockShapeInfo shape, int page,
                                                                    List<BlockPos> placedPositions) {
        ServerLevel level = helper.getLevel();
        Map<BlockPos, MultiblockBlockInfo> blocksByPos = new LinkedHashMap<>();

        MultiblockBlockInfo[][][] blocks = shape.getBlocks();
        for (int x = 0; x < blocks.length; x++) {
            for (int y = 0; y < blocks[x].length; y++) {
                for (int z = 0; z < blocks[x][y].length; z++) {
                    MultiblockBlockInfo blockInfo = blocks[x][y][z];
                    if (blockInfo == null) continue;
                    BlockPos pos = helper.absolutePos(PREVIEW_ORIGIN.offset(x, y, z));
                    blockInfo.clearBlockEntityCache();
                    blocksByPos.put(pos, blockInfo);
                    placedPositions.add(pos);
                }
            }
        }

        blocksByPos.forEach((pos, blockInfo) -> level.setBlock(pos, blockInfo.getBlockState(), Block.UPDATE_CLIENTS));

        MultiblockControllerMachine controller = null;
        for (var entry : blocksByPos.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockEntity blockEntity = entry.getValue().getBlockEntity(level.registryAccess(), level, pos);
            if (blockEntity == null) continue;
            level.setBlockEntity(blockEntity);
            if (blockEntity instanceof MultiblockControllerMachine foundController) {
                if (controller != null) {
                    throw new IllegalStateException("Multiple controllers in identity preview for " +
                            definition.getId() + " page " + page);
                }
                controller = foundController;
            }
        }

        if (controller == null) {
            throw new IllegalStateException("No controller in identity preview for " + definition.getId() +
                    " page " + page);
        }
        return controller;
    }

    private static void clearPreview(ServerLevel level, List<BlockPos> placedPositions) {
        for (BlockPos pos : placedPositions) {
            level.removeBlockEntity(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static String patternError(MultiblockState state) {
        if (!state.hasError()) return "no pattern error was recorded";
        return state.error.getErrorInfo().getString() + " at " + state.getPos().toShortString();
    }
}
