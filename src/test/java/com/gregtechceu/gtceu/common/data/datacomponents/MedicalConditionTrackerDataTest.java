package com.gregtechceu.gtceu.common.data.datacomponents;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.capability.MedicalConditionTracker;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMedicalConditions;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MedicalConditionTrackerDataTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MedicalConditionTrackerData")
    public static void codecKeepsConditionProgressAndPermanents(GameTestHelper helper) {
        MedicalConditionTrackerData original = new MedicalConditionTrackerData(
                List.of(new MedicalConditionTrackerData.Entry(GTMedicalConditions.POISON, 37.5f)),
                List.of(GTMedicalConditions.CARCINOGEN));

        JsonElement json = MedicalConditionTrackerData.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(GameTestAssertException::new);
        MedicalConditionTrackerData decoded = MedicalConditionTrackerData.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(GameTestAssertException::new);

        assertData(helper, decoded, "codec");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MedicalConditionTrackerData")
    public static void streamCodecKeepsConditionProgressAndPermanents(GameTestHelper helper) {
        MedicalConditionTrackerData original = new MedicalConditionTrackerData(
                List.of(new MedicalConditionTrackerData.Entry(GTMedicalConditions.POISON, 37.5f)),
                List.of(GTMedicalConditions.CARCINOGEN));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess(),
                ConnectionType.OTHER);
        try {
            MedicalConditionTrackerData.STREAM_CODEC.encode(buffer, original);
            MedicalConditionTrackerData decoded = MedicalConditionTrackerData.STREAM_CODEC.decode(buffer);
            assertData(helper, decoded, "stream codec");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MedicalConditionTrackerData")
    public static void trackerExportsAndImportsDataComponents(GameTestHelper helper) {
        MedicalConditionTracker tracker = new MedicalConditionTracker(helper.makeMockPlayer(GameType.SURVIVAL));
        tracker.progressCondition(GTMedicalConditions.POISON, 37.5f);
        tracker.progressCondition(GTMedicalConditions.WEAK_POISON, 12.25f);

        DataComponentMap components = tracker.exportComponents();
        MedicalConditionTracker imported = new MedicalConditionTracker(helper.makeMockPlayer(GameType.SURVIVAL));
        imported.importComponents(components);

        helper.assertTrue(components.has(GTDataComponents.MEDICAL_CONDITION_TRACKER.get()),
                "tracker did not export typed medical condition data");
        helper.assertTrue(imported.getMedicalConditions().getFloat(GTMedicalConditions.POISON) == 37.5f,
                "tracker component import lost poison progression");
        helper.assertTrue(imported.getMedicalConditions().getFloat(GTMedicalConditions.WEAK_POISON) == 12.25f,
                "tracker component import lost weak poison progression");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MedicalConditionTrackerData")
    public static void dataComponentMapStreamCodecKeepsTrackerComponent(GameTestHelper helper) {
        MedicalConditionTrackerData data = new MedicalConditionTrackerData(
                List.of(new MedicalConditionTrackerData.Entry(GTMedicalConditions.POISON, 37.5f)),
                List.of(GTMedicalConditions.CARCINOGEN));
        DataComponentMap components = DataComponentMap.builder()
                .set(GTDataComponents.MEDICAL_CONDITION_TRACKER.get(), data)
                .build();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess(),
                ConnectionType.OTHER);
        try {
            SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buffer, components);
            DataComponentMap decoded = SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buffer);
            MedicalConditionTrackerData decodedData = decoded.get(GTDataComponents.MEDICAL_CONDITION_TRACKER.get());
            helper.assertTrue(decodedData != null, "component map stream lost tracker data component");
            assertData(helper, decodedData, "component map stream");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static void assertData(GameTestHelper helper, MedicalConditionTrackerData data, String path) {
        helper.assertTrue(data.medicalConditions().size() == 1, path + " did not keep condition entry count");
        helper.assertTrue(data.medicalConditions().getFirst().condition() == GTMedicalConditions.POISON,
                path + " did not keep condition identity");
        helper.assertTrue(data.medicalConditions().getFirst().progression() == 37.5f,
                path + " did not keep condition progression");
        helper.assertTrue(data.permanentConditions().size() == 1, path + " did not keep permanent entry count");
        helper.assertTrue(data.permanentConditions().getFirst() == GTMedicalConditions.CARCINOGEN,
                path + " did not keep permanent condition identity");
    }
}
