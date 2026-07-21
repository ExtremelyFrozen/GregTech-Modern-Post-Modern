package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.cover.detector.ActivityDetectorCover;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CoverBehaviorCodecSyncTest {

    private static final String BATCH = "CoverBehaviorCodecSync";
    private static final Direction COVER_SIDE = Direction.NORTH;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void machineRootDetectsNestedCoverChanges(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        BufferMachine server = createBuffer();
        BufferMachine client = createBuffer();
        ActivityDetectorCover serverCover = installActivityDetector(server);

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        ActivityDetectorCover clientCover = requireActivityDetector(client);
        assertCoverIdentity(helper, clientCover, "full sync");
        helper.assertTrue(!clientCover.isInverted(), "full sync inverted the client detector cover");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged machine root produced a nested cover delta");

        serverCover.setInverted(true);
        DataComponentMap delta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        helper.assertTrue(!delta.isEmpty(), "machine root omitted the changed nested detector cover");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, delta);

        ActivityDetectorCover updatedClientCover = requireActivityDetector(client);
        helper.assertTrue(updatedClientCover == clientCover,
                "nested cover delta replaced an unchanged cover definition");
        assertCoverIdentity(helper, updatedClientCover, "changed delta");
        helper.assertTrue(updatedClientCover.isInverted(),
                "nested cover delta did not update the client detector state");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "consumed nested cover change was emitted twice");
        helper.succeed();
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof BufferMachine buffer)) {
            throw new GameTestAssertException("Buffer definition did not create a buffer machine.");
        }
        return buffer;
    }

    private static ActivityDetectorCover installActivityDetector(BufferMachine machine) {
        var cover = GTCovers.ACTIVITY_DETECTOR.createCoverBehavior(machine.getCoverContainer(), COVER_SIDE);
        if (!(cover instanceof ActivityDetectorCover detector)) {
            throw new GameTestAssertException("Activity detector definition created the wrong cover type.");
        }
        machine.getCoverContainer().setCoverAtSide(detector, COVER_SIDE);
        return detector;
    }

    private static ActivityDetectorCover requireActivityDetector(BufferMachine machine) {
        var cover = machine.getCoverContainer().getCoverAtSide(COVER_SIDE);
        if (!(cover instanceof ActivityDetectorCover detector)) {
            throw new GameTestAssertException("Machine root did not resolve the activity detector cover.");
        }
        return detector;
    }

    private static void assertCoverIdentity(GameTestHelper helper, ActivityDetectorCover cover, String description) {
        helper.assertTrue(cover.coverDefinition == GTCovers.ACTIVITY_DETECTOR && cover.attachedSide == COVER_SIDE,
                description + " changed the nested cover definition or side");
    }
}
