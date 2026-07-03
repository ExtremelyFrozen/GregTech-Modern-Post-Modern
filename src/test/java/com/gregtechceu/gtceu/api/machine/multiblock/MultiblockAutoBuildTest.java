package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildBlockMap;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildMaterialSources;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildOptions;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildProblem;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildRequest;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.multiblock.structurepredicate.StructurePredicate;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder("gtpm_multiblock_autobuild")
public class MultiblockAutoBuildTest {

    private static final int COKE_OVEN_BRICKS = 25;
    private static final String TEST_COKE_OVEN_CATEGORY = "test_coke_oven_bricks";

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void repeatCountResolvesMinimumRequestedAndMaximum(GameTestHelper helper) {
        BlockPattern pattern = FactoryBlockPattern.start()
                .aisle("A")
                .where('A', Predicates.blocks(Blocks.STONE))
                .beginRepeatable()
                .aisle("B")
                .aisle("C")
                .endRepeatable(2, 4)
                .where('B', Predicates.blocks(Blocks.COBBLESTONE))
                .where('C', Predicates.blocks(Blocks.DIRT))
                .build();

        helper.assertTrue(Arrays.equals(new int[] { 1, 2 },
                MultiblockAutoBuild.resolveRepetitions(pattern, options(0))),
                "repeatCount 0 did not use pattern minimum");
        helper.assertTrue(Arrays.equals(new int[] { 1, 3 },
                MultiblockAutoBuild.resolveRepetitions(pattern, options(3))),
                "repeatCount 3 did not apply within each unit min/max");
        helper.assertTrue(Arrays.equals(new int[] { 1, 4 },
                MultiblockAutoBuild.resolveRepetitions(pattern, options(10))),
                "repeatCount above max did not clamp per repeatable unit");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void controllerAutoBuildCompletesCokeOvenFromPlayerInventory(GameTestHelper helper) {
        MultiblockControllerMachine controller = placeCokeOvenController(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        clearPlayerInventory(helper, player);
        insertPlayerStack(helper, player, GTBlocks.CASING_COKE_BRICKS.asStack(COKE_OVEN_BRICKS));

        var result = controller.autoBuild(player, new AutoBuildRequest(MultiblockControllerMachine.DEFAULT_STRUCTURE,
                options(0), List.of(AutoBuildMaterialSources.playerInventory(player))));
        helper.assertTrue(result.success(), "Coke Oven auto-build failed: " + result.summary().getString());
        helper.assertTrue(result.placed() == COKE_OVEN_BRICKS, "Coke Oven auto-build placed wrong block count");
        helper.assertTrue(controller.checkPatternWithLock(MultiblockControllerMachine.DEFAULT_STRUCTURE),
                "Coke Oven auto-build did not form a valid structure");
        helper.assertBlockPresent(GTBlocks.CASING_COKE_BRICKS.get(), findCokeOvenBrick(helper));
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void controllerAutoBuildRejectsUnknownStructure(GameTestHelper helper) {
        MultiblockControllerMachine controller = placeCokeOvenController(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        var result = controller.autoBuild(player, new AutoBuildRequest("missing_structure", options(0), List.of()));
        helper.assertTrue(!result.success(), "Unknown structure auto-build succeeded");
        helper.assertTrue(result.problems().getFirst().type() == AutoBuildProblem.Type.UNKNOWN_STRUCTURE,
                "Unknown structure returned wrong problem type");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void controllerAutoBuildRejectsNegativeRepeat(GameTestHelper helper) {
        MultiblockControllerMachine controller = placeCokeOvenController(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        var result = controller.autoBuild(player, new AutoBuildRequest(MultiblockControllerMachine.DEFAULT_STRUCTURE,
                options(-1), List.of()));
        helper.assertTrue(!result.success(), "Negative repeat auto-build succeeded");
        helper.assertTrue(result.problems().getFirst().type() == AutoBuildProblem.Type.INVALID_OPTIONS,
                "Negative repeat returned wrong problem type");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void controllerAutoBuildMissingMaterialDoesNotCommitStage(GameTestHelper helper) {
        MultiblockControllerMachine controller = placeCokeOvenController(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        clearPlayerInventory(helper, player);
        insertPlayerStack(helper, player, GTBlocks.CASING_COKE_BRICKS.asStack(1));

        var result = controller.autoBuild(player, new AutoBuildRequest(MultiblockControllerMachine.DEFAULT_STRUCTURE,
                options(0), List.of(AutoBuildMaterialSources.playerInventory(player))));
        helper.assertTrue(!result.success(), "Coke Oven auto-build succeeded without enough materials");
        helper.assertTrue(!result.problems().isEmpty() &&
                result.problems().getFirst().type() == AutoBuildProblem.Type.MISSING_MATERIAL,
                "Coke Oven auto-build returned wrong missing material problem");
        helper.assertTrue(result.placed() == 0, "Failed stage committed placed blocks");
        helper.assertBlockNotPresent(GTBlocks.CASING_COKE_BRICKS.get(), new BlockPos(1, 1, 1));
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void controllerAutoBuildReplaceClearsAndRefundsDrops(GameTestHelper helper) {
        MultiblockControllerMachine controller = placeCokeOvenController(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        clearPlayerInventory(helper, player);
        BlockPos target = firstCokeOvenBrickTarget(controller);
        helper.getLevel().setBlock(target, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        insertPlayerStack(helper, player, GTBlocks.CASING_COKE_BRICKS.asStack(COKE_OVEN_BRICKS));

        var result = controller.autoBuild(player, new AutoBuildRequest(MultiblockControllerMachine.DEFAULT_STRUCTURE,
                new AutoBuildOptions(0, true, false, false, false, true, Map.of()),
                List.of(AutoBuildMaterialSources.playerInventory(player))));
        helper.assertTrue(result.success(), "Coke Oven replace build failed: " + result.summary().getString());
        helper.assertTrue(result.removed() == 1, "Replace build did not clear the blocking block");
        helper.assertTrue(helper.getLevel().getBlockState(target).is(GTBlocks.CASING_COKE_BRICKS.get()),
                "Replace build did not place structure block");
        helper.assertTrue(playerInventoryContains(player, new ItemStack(Blocks.DIRT)),
                "Replace build did not refund cleared drops");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void controllerAutoBuildTierSelectionReplacesCandidate(GameTestHelper helper) {
        registerCokeOvenTestCategory();
        MultiblockControllerMachine controller = placeCokeOvenController(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        clearPlayerInventory(helper, player);
        insertPlayerStack(helper, player, new ItemStack(Blocks.COBBLESTONE, COKE_OVEN_BRICKS));

        var result = controller.autoBuild(player, new AutoBuildRequest(MultiblockControllerMachine.DEFAULT_STRUCTURE,
                new AutoBuildOptions(0, false, false, false, false, true, Map.of(TEST_COKE_OVEN_CATEGORY, 2)),
                List.of(AutoBuildMaterialSources.playerInventory(player))));
        helper.assertTrue(!result.success(), "Tier-selected invalid structure unexpectedly formed");
        helper.assertTrue(result.placed() == COKE_OVEN_BRICKS,
                "Tier-selected candidate was not used for every Coke Oven brick position");
        helper.assertTrue(result.problems().getLast().type() == AutoBuildProblem.Type.STRUCTURE_CHECK_FAILED,
                "Tier-selected invalid structure returned wrong problem type");
        helper.assertTrue(helper.getLevel().getBlockState(firstCokeOvenBrickTarget(controller)).is(Blocks.COBBLESTONE),
                "Tier selection did not replace Coke Oven brick candidate");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void controllerAutoBuildDemolitionOnlyRemovesMatchingStructureBlocks(GameTestHelper helper) {
        MultiblockControllerMachine controller = placeCokeOvenController(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        clearPlayerInventory(helper, player);
        insertPlayerStack(helper, player, GTBlocks.CASING_COKE_BRICKS.asStack(COKE_OVEN_BRICKS));
        var buildResult = controller.autoBuild(player,
                new AutoBuildRequest(MultiblockControllerMachine.DEFAULT_STRUCTURE,
                        options(0), List.of(AutoBuildMaterialSources.playerInventory(player))));
        helper.assertTrue(buildResult.success(), "Coke Oven setup build failed: " + buildResult.summary().getString());
        BlockPos replacedBrick = findCokeOvenBrick(helper);
        helper.setBlock(replacedBrick, Blocks.DIAMOND_BLOCK);

        var result = controller.autoBuild(player, new AutoBuildRequest(MultiblockControllerMachine.DEFAULT_STRUCTURE,
                new AutoBuildOptions(0, false, true, false, false, true, Map.of()),
                List.of(AutoBuildMaterialSources.playerInventory(player))));
        helper.assertTrue(result.success(), "Coke Oven demolition failed: " + result.summary().getString());
        helper.assertTrue(result.removed() == COKE_OVEN_BRICKS - 1,
                "Coke Oven demolition removed wrong block count: " + result.removed());
        helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, replacedBrick);
        helper.assertBlockPresent(GTMultiMachines.COKE_OVEN.getBlock(), new BlockPos(2, 1, 2));
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void controllerAutoBuildDemolitionMatchesBlockMapCategory(GameTestHelper helper) {
        registerCokeOvenTestCategory();
        MultiblockControllerMachine controller = placeCokeOvenController(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        clearPlayerInventory(helper, player);
        BlockPos target = firstCokeOvenBrickTarget(controller);
        helper.getLevel().setBlock(target, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);

        var result = controller.autoBuild(player, new AutoBuildRequest(MultiblockControllerMachine.DEFAULT_STRUCTURE,
                new AutoBuildOptions(0, false, true, false, false, true, Map.of()),
                List.of(AutoBuildMaterialSources.playerInventory(player))));
        helper.assertTrue(result.success(), "Category demolition failed: " + result.summary().getString());
        helper.assertTrue(result.removed() == 1, "Category demolition did not remove same-category block");
        helper.assertTrue(helper.getLevel().getBlockState(target).isAir(),
                "Category demolition target was not cleared");
        helper.succeed();
    }

    private static AutoBuildOptions options(int repeatCount) {
        return new AutoBuildOptions(repeatCount, false, false, false, false, true, Map.of());
    }

    private static MultiblockControllerMachine placeCokeOvenController(GameTestHelper helper) {
        return (MultiblockControllerMachine) TestUtils.setMachine(helper, new BlockPos(2, 1, 2),
                GTMultiMachines.COKE_OVEN);
    }

    private static void insertPlayerStack(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
        IItemHandler inventory = player.getCapability(Capabilities.ItemHandler.ENTITY);
        helper.assertTrue(inventory != null, "Player item handler missing");
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < inventory.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = inventory.insertItem(slot, remainder, false);
        }
        helper.assertTrue(remainder.isEmpty(),
                "Could not insert player test stack " + stack.getHoverName().getString());
    }

    private static void clearPlayerInventory(GameTestHelper helper, ServerPlayer player) {
        IItemHandler inventory = player.getCapability(Capabilities.ItemHandler.ENTITY);
        helper.assertTrue(inventory != null, "Player item handler missing");
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.extractItem(slot, Integer.MAX_VALUE, false);
        }
    }

    private static BlockPos findCokeOvenBrick(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getLevel().getBlockState(helper.absolutePos(pos))
                            .is(GTBlocks.CASING_COKE_BRICKS.get())) {
                        return pos;
                    }
                }
            }
        }
        helper.fail("Coke Oven auto-build did not place any bricks");
        return BlockPos.ZERO;
    }

    private static BlockPos firstCokeOvenBrickTarget(MultiblockControllerMachine controller) {
        BlockPattern pattern = controller.getPattern(MultiblockControllerMachine.DEFAULT_STRUCTURE);
        for (int zIndex = 0, worldZ = pattern.getMinZ(); zIndex < pattern.getFingerLength(); zIndex++, worldZ++) {
            for (int yIndex = 0, y = pattern.getMinY(); yIndex < pattern.getThumbLength(); yIndex++, y++) {
                for (int xIndex = 0, x = pattern.getMinX(); xIndex < pattern.getPalmLength(); xIndex++, x++) {
                    TraceabilityPredicate predicate = pattern.getPredicate(zIndex, yIndex, xIndex);
                    if (predicate != null && predicateHasCandidate(predicate, GTBlocks.CASING_COKE_BRICKS.get())) {
                        return pattern.getActualRelativeOffset(x, y, worldZ, controller.getFrontFacing(),
                                controller.getUpwardsFacing(), false)
                                .offset(controller.getBlockPos());
                    }
                }
            }
        }
        return controller.getBlockPos();
    }

    private static boolean predicateHasCandidate(TraceabilityPredicate predicate, Block block) {
        for (SimplePredicate simplePredicate : predicate.common) {
            if (simplePredicateHasCandidate(simplePredicate, block)) {
                return true;
            }
        }
        for (SimplePredicate simplePredicate : predicate.limited) {
            if (simplePredicateHasCandidate(simplePredicate, block)) {
                return true;
            }
        }
        for (StructurePredicate structurePredicate : predicate.structurePredicates) {
            if (structurePredicate.blockCandidates().contains(block)) {
                return true;
            }
        }
        return false;
    }

    private static boolean simplePredicateHasCandidate(SimplePredicate simplePredicate, Block block) {
        if (simplePredicate.candidates == null) {
            return false;
        }
        for (Block candidate : simplePredicate.candidates.get()) {
            if (candidate == block) {
                return true;
            }
        }
        return false;
    }

    private static boolean playerInventoryContains(ServerPlayer player, ItemStack expected) {
        IItemHandler inventory = player.getCapability(Capabilities.ItemHandler.ENTITY);
        if (inventory == null) {
            return false;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, expected) && stack.getCount() >= expected.getCount()) {
                return true;
            }
        }
        return false;
    }

    private static void registerCokeOvenTestCategory() {
        AutoBuildBlockMap.registerCategory(TEST_COKE_OVEN_CATEGORY,
                new Block[] { GTBlocks.CASING_COKE_BRICKS.get(), Blocks.COBBLESTONE });
    }
}
