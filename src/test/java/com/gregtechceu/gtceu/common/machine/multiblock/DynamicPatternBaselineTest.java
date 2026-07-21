package com.gregtechceu.gtceu.common.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.multiblock.predicates.PredicateController;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DynamicPatternBaselineTest {

    private static final String BATCH = "DynamicPatternBaseline";

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = BATCH)
    public static void cleanroomPatternBindsControllerPredicate(GameTestHelper helper) {
        MultiblockControllerMachine machine = placeController(helper, GTMultiMachines.CLEANROOM);
        assertControllerPredicate(helper, machine, requireDefaultPattern(machine));
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = BATCH)
    public static void charcoalPilePatternBindsControllerPredicate(GameTestHelper helper) {
        MultiblockControllerMachine machine = placeController(helper, GTMultiMachines.CHARCOAL_PILE_IGNITER);
        assertControllerPredicate(helper, machine, requireDefaultPattern(machine));
        helper.succeed();
    }

    private static MultiblockControllerMachine placeController(GameTestHelper helper,
                                                               MachineDefinition definition) {
        MetaMachine machine = TestUtils.setMachine(helper, new BlockPos(2, 1, 2), definition);
        if (!(machine instanceof MultiblockControllerMachine controller)) {
            throw new IllegalStateException("Expected a multiblock controller for " + definition.getId());
        }
        return controller;
    }

    private static BlockPattern requireDefaultPattern(MultiblockControllerMachine machine) {
        BlockPattern pattern = machine.getPattern(MultiblockControllerMachine.DEFAULT_STRUCTURE);
        if (pattern == null) {
            throw new IllegalStateException("Default pattern was not built for " + machine.getDefinition().getId());
        }
        return pattern;
    }

    private static void assertControllerPredicate(GameTestHelper helper, MultiblockControllerMachine machine,
                                                  BlockPattern pattern) {
        int controllerCount = 0;
        for (int z = 0; z < pattern.getFingerLength(); z++) {
            int relativeZ = relativeZAtMaxRepetition(pattern, z);
            for (int y = 0; y < pattern.getThumbLength(); y++) {
                int relativeY = pattern.getMinY() + y;
                for (int x = 0; x < pattern.getPalmLength(); x++) {
                    int relativeX = pattern.getMinX() + x;
                    TraceabilityPredicate predicate = pattern.getPredicate(z, y, x);
                    if (!(predicate instanceof PredicateController controllerPredicate)) continue;

                    controllerCount++;
                    helper.assertTrue(relativeX == 0 && relativeY == 0 && relativeZ == 0,
                            "Controller predicate is not centered at the machine origin");
                    helper.assertTrue(hasCandidate(controllerPredicate, machine.getDefinition().getBlock()),
                            "Controller predicate does not accept its machine block");
                }
            }
        }
        helper.assertTrue(controllerCount == 1,
                "Expected exactly one centered controller predicate, found " + controllerCount);
    }

    private static int relativeZAtMaxRepetition(BlockPattern pattern, int aisleIndex) {
        int relativeZ = pattern.getMinZ();
        for (int unit = 0; unit < pattern.unitStarts.length; unit++) {
            int unitStart = pattern.unitStarts[unit];
            int unitDepth = pattern.unitDepths[unit];
            if (aisleIndex >= unitStart && aisleIndex < unitStart + unitDepth) {
                return relativeZ + aisleIndex - unitStart;
            }
            relativeZ += unitDepth * pattern.aisleRepetitions[unit][1];
        }
        throw new IllegalArgumentException("Aisle index is outside the pattern: " + aisleIndex);
    }

    private static boolean hasCandidate(PredicateController predicate, Block expected) {
        for (SimplePredicate simplePredicate : predicate.common) {
            if (simplePredicate.candidates != null && contains(simplePredicate.candidates.get(), expected)) {
                return true;
            }
        }
        for (SimplePredicate simplePredicate : predicate.limited) {
            if (simplePredicate.candidates != null && contains(simplePredicate.candidates.get(), expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(Block[] candidates, Block expected) {
        for (Block candidate : candidates) {
            if (candidate == expected) return true;
        }
        return false;
    }
}
