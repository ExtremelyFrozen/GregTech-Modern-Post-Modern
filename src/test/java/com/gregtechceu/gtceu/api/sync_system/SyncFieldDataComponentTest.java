package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged;
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
import org.jetbrains.annotations.Nullable;

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

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void syncDataHolderDeserializesExplicitNullFields(GameTestHelper helper) {
        NullSyncTarget target = new NullSyncTarget("saved", "client", "both");
        SyncFieldData savedData = SyncFieldData.builder()
                .put(SyncFieldData.key("savedValue"), JsonNull.INSTANCE)
                .build();
        SyncFieldData clientData = SyncFieldData.builder()
                .put(SyncFieldData.key("clientValue"), JsonNull.INSTANCE)
                .put(SyncFieldData.key("bothValue"), JsonNull.INSTANCE)
                .build();
        DataComponentMap clientComponents = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), clientData)
                .build();

        target.getSyncDataHolder().deserializeFieldData(helper.getLevel().registryAccess(), savedData, false);
        target.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), clientComponents);

        helper.assertTrue(target.savedValue == null, "saved field explicit null was skipped");
        helper.assertTrue(target.clientValue == null, "client field explicit null was skipped");
        helper.assertTrue(target.bothValue == null, "client network explicit null was skipped");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void syncDataHolderDeserializesServerNetworkExplicitNull(GameTestHelper helper) {
        NullSyncTarget target = new NullSyncTarget("saved", "client", "both");
        SyncFieldData serverData = SyncFieldData.builder()
                .put(SyncFieldData.key("bothValue"), JsonNull.INSTANCE)
                .build();
        DataComponentMap serverComponents = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), serverData)
                .build();

        target.getSyncDataHolder().applyServerNetworkUpdate(helper.getLevel().registryAccess(), serverComponents);

        helper.assertTrue(target.bothValue == null, "server network explicit null was skipped");
        helper.succeed();
    }

    private static final class NullSyncTarget implements ISyncManaged {

        private final SyncDataHolder syncDataHolder = new SyncDataHolder(this);
        @SaveField
        private String savedValue;
        @SyncToClient
        private String clientValue;
        @SyncBoth
        private String bothValue;

        private NullSyncTarget(String savedValue, String clientValue, String bothValue) {
            this.savedValue = savedValue;
            this.clientValue = clientValue;
            this.bothValue = bothValue;
        }

        @Override
        public SyncDataHolder getSyncDataHolder() {
            return syncDataHolder;
        }

        @Override
        public @Nullable ISyncManaged getParentSyncObject() {
            return null;
        }
    }
}
