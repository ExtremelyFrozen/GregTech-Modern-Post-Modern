package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.codecs.MonitorGroupCodec;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MonitorGroupCodecTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MonitorGroupCodec")
    public static void identityPersistsAndLegacyGroupsReceiveOne(GameTestHelper helper) {
        ContextualFieldCodec.Context<MonitorGroup> context = new ContextualFieldCodec.Context<>(
                new Object(), new TypeDeclaration(MonitorGroup.class), null, "monitorGroup", true, true,
                helper.getLevel().registryAccess(), SyncSerializationTarget.DATA_COMPONENTS);
        MonitorGroup first = new MonitorGroup("first");
        MonitorGroup second = new MonitorGroup("second");

        helper.assertTrue(!first.getIdentity().equals(second.getIdentity()),
                "new monitor groups reused the same identity");

        JsonObject encoded = MonitorGroupCodec.INSTANCE.serializeField(first, context).getAsJsonObject();
        MonitorGroup decoded = decode(encoded, context);
        helper.assertTrue(decoded.getIdentity().equals(first.getIdentity()),
                "monitor group identity changed during codec round-trip");

        JsonObject legacyJson = encoded.deepCopy();
        legacyJson.remove("identity");
        MonitorGroup legacyDecoded = decode(legacyJson, context);
        helper.assertTrue(!legacyDecoded.getIdentity().equals(first.getIdentity()),
                "legacy monitor group reused the identity removed from its payload");

        JsonObject migratedJson = MonitorGroupCodec.INSTANCE.serializeField(legacyDecoded, context).getAsJsonObject();
        helper.assertTrue(migratedJson.has("identity"),
                "re-serialized legacy monitor group omitted its generated identity");
        helper.assertTrue(decode(migratedJson, context).getIdentity().equals(legacyDecoded.getIdentity()),
                "generated legacy monitor group identity did not persist");

        JsonObject malformedJson = encoded.deepCopy();
        malformedJson.add("identity", new JsonPrimitive("malformed"));
        boolean malformedRejected = false;
        try {
            MonitorGroupCodec.INSTANCE.deserializeField(malformedJson, context);
        } catch (RuntimeException expected) {
            malformedRejected = true;
        }
        helper.assertTrue(malformedRejected, "monitor group codec accepted a malformed identity");
        helper.succeed();
    }

    private static MonitorGroup decode(JsonElement json, ContextualFieldCodec.Context<MonitorGroup> context) {
        MonitorGroup decoded = MonitorGroupCodec.INSTANCE.deserializeField(json, context);
        if (decoded == null) {
            throw new GameTestAssertException("monitor group codec returned null for an object payload");
        }
        return decoded;
    }
}
