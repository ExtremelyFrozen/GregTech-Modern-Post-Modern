package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SyncFieldDataComponentTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void syncFieldDataComponentCodecRoundTripsExplicitNull(GameTestHelper helper) {
        SyncFieldData fieldData = SyncFieldData.builder()
                .put(SyncFieldData.key("present"), new JsonPrimitive("value"))
                .put(SyncFieldData.key("cleared"), JsonNull.INSTANCE)
                .build();
        DataComponentMap components = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fieldData)
                .build();

        JsonElement json = DataComponentMap.CODEC
                .encodeStart(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), components)
                .getOrThrow(GameTestAssertException::new);
        DataComponentMap decodedComponents = DataComponentMap.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), json)
                .getOrThrow(GameTestAssertException::new);
        SyncFieldData decoded = decodedComponents.get(GTDataComponents.SYNC_FIELD_DATA.get());

        helper.assertTrue(decoded != null, "sync field data did not round-trip");
        helper.assertTrue(decoded.get(SyncFieldData.key("present")).getAsString().equals("value"),
                "non-null field did not round-trip");
        helper.assertTrue(decoded.get(SyncFieldData.key("cleared")).isJsonNull(),
                "explicit null field did not round-trip");
        helper.succeed();
    }
}
