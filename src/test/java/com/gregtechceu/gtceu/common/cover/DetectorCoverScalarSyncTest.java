package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.cover.detector.ActivityDetectorCover;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DetectorCoverScalarSyncTest {

    private static final String BATCH = "DetectorCoverScalarSync";
    private static final Direction COVER_SIDE = Direction.NORTH;
    private static final ResourceLocation INVERTED_FIELD = SyncFieldData.key("isInverted");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void inversionUsesAutomaticDirectCoverSyncAndServerOwnedPersistence(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        ActivityDetectorCover server = installActivityDetector(createBuffer());
        ActivityDetectorCover client = installActivityDetector(createBuffer());
        ActivityDetectorCover loaded = installActivityDetector(createBuffer());

        server.setInverted(true);
        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertInvertedField(helper, full, true, "detector full sync");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        helper.assertTrue(client.isInverted(), "detector full sync did not update the client inversion state");

        server.setInverted(false);
        DataComponentMap delta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertOnlyInvertedField(helper, delta, false, "changed detector delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, delta);
        helper.assertTrue(!client.isInverted(), "changed detector delta did not update the client inversion state");

        server.setInverted(false);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "identical detector inversion setter call produced a redundant client delta");

        server.setInverted(true);
        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertInvertedField(helper, saved, true, "saved detector inversion state");
        loaded.getSyncDataHolder().deserializeFieldData(registries, saved, false);
        helper.assertTrue(loaded.isInverted(), "detector did not load its saved inversion state");

        server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, invertedPayload(false));
        helper.assertTrue(!rejected.getAccepted(), "detector accepted a client inversion write");
        helper.assertTrue(server.isInverted(), "rejected client write changed the server detector inversion state");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "rejected detector client write produced a client acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void machineRootDetectsAutomaticNestedInversionChanges(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        BufferMachine server = createBuffer();
        BufferMachine client = createBuffer();
        ActivityDetectorCover serverCover = installActivityDetector(server);

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        ActivityDetectorCover clientCover = requireActivityDetector(client);
        assertCoverIdentity(helper, clientCover, "machine-root full sync");
        helper.assertTrue(!clientCover.isInverted(),
                "machine-root full sync changed the initial client inversion state");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged machine root produced a detector delta after full sync");

        serverCover.setInverted(true);
        DataComponentMap delta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        helper.assertTrue(!delta.isEmpty(),
                "machine root omitted the automatically detected nested detector inversion change");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, delta);

        ActivityDetectorCover updatedClientCover = requireActivityDetector(client);
        helper.assertTrue(updatedClientCover == clientCover,
                "machine-root detector delta replaced an unchanged cover definition");
        assertCoverIdentity(helper, updatedClientCover, "machine-root changed delta");
        helper.assertTrue(updatedClientCover.isInverted(),
                "machine-root detector delta did not update the client inversion state");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "machine root emitted the consumed nested detector change twice");
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

    private static DataComponentMap invertedPayload(boolean inverted) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(INVERTED_FIELD, new JsonPrimitive(inverted))
                        .build())
                .build();
    }

    private static void assertOnlyInvertedField(GameTestHelper helper, DataComponentMap components,
                                                boolean expected, String description) {
        SyncFieldData fields = requireFields(components, description);
        helper.assertTrue(fields.fields().size() == 1,
                description + " contained fields other than the detector inversion state");
        assertInvertedField(helper, fields, expected, description);
    }

    private static void assertInvertedField(GameTestHelper helper, DataComponentMap components,
                                            boolean expected, String description) {
        assertInvertedField(helper, requireFields(components, description), expected, description);
    }

    private static void assertInvertedField(GameTestHelper helper, SyncFieldData fields,
                                            boolean expected, String description) {
        JsonElement value = fields.get(INVERTED_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected detector inversion state");
    }

    private static SyncFieldData requireFields(DataComponentMap components, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new GameTestAssertException(description + " omitted sync field data");
        }
        return fields;
    }

    private static void assertCoverIdentity(GameTestHelper helper, ActivityDetectorCover cover,
                                            String description) {
        helper.assertTrue(cover.coverDefinition == GTCovers.ACTIVITY_DETECTOR && cover.attachedSide == COVER_SIDE,
                description + " changed the nested cover definition or side");
    }
}
