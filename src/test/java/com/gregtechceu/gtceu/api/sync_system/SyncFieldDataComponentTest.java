package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToServer;
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SyncFieldDataComponentTest {

    static {
        FieldCodecs.registerContextual(NullParsedValue.class, NullParsedValueCodec.INSTANCE);
        FieldCodecs.register(RegularNullParsedValue.class, RegularNullParsedValue.CODEC);
    }

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

        target.getSyncDataHolder().deserializeFieldData(helper.getLevel().registryAccess(), savedData, false);

        helper.assertTrue(target.savedValue == null, "saved field explicit null was skipped");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void syncDataHolderParsesNetworkExplicitNullFields(GameTestHelper helper) {
        ParsedNullSyncTarget target = new ParsedNullSyncTarget(
                new NullParsedValue("client-original"),
                new NullParsedValue("server-original"),
                new RegularNullParsedValue("client-regular-original"),
                new RegularNullParsedValue("server-regular-original"));
        SyncFieldData clientData = SyncFieldData.builder()
                .put(SyncFieldData.key("clientParsed"), JsonNull.INSTANCE)
                .put(SyncFieldData.key("clientRegularParsed"), JsonNull.INSTANCE)
                .build();
        SyncFieldData serverData = SyncFieldData.builder()
                .put(SyncFieldData.key("serverParsed"), JsonNull.INSTANCE)
                .put(SyncFieldData.key("serverRegularParsed"), JsonNull.INSTANCE)
                .build();
        DataComponentMap clientComponents = networkRoundTrip(helper, DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), clientData)
                .build());
        DataComponentMap serverComponents = networkRoundTrip(helper, DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), serverData)
                .build());

        target.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), clientComponents);
        target.getSyncDataHolder().applyServerNetworkUpdate(helper.getLevel().registryAccess(), serverComponents);

        helper.assertTrue(target.clientParsed != null, "client network explicit null was not parsed");
        helper.assertTrue(target.serverParsed != null, "server network explicit null was not parsed");
        helper.assertTrue(target.clientRegularParsed != null,
                "client network explicit null was not parsed by regular codec");
        helper.assertTrue(target.serverRegularParsed != null,
                "server network explicit null was not parsed by regular codec");
        helper.assertTrue("clientParsed:null".equals(target.clientParsed.value),
                "client network explicit null did not use the contextual field codec result");
        helper.assertTrue("serverParsed:null".equals(target.serverParsed.value),
                "server network explicit null did not use the contextual field codec result");
        helper.assertTrue("regular:null".equals(target.clientRegularParsed.value),
                "client network explicit null did not use the regular field codec result");
        helper.assertTrue("regular:null".equals(target.serverRegularParsed.value),
                "server network explicit null did not use the regular field codec result");
        helper.succeed();
    }

    private static DataComponentMap networkRoundTrip(GameTestHelper helper, DataComponentMap components) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess(), ConnectionType.OTHER);
        try {
            SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buffer, components);
            return SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
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

    private record NullParsedValue(String value) {}

    private record RegularNullParsedValue(String value) {

        private static final Codec<RegularNullParsedValue> CODEC = ExtraCodecs.JSON.xmap(
                RegularNullParsedValue::fromJson,
                RegularNullParsedValue::toJson);

        private static RegularNullParsedValue fromJson(JsonElement value) {
            if (value.isJsonNull()) {
                return new RegularNullParsedValue("regular:null");
            }
            return new RegularNullParsedValue(value.getAsString());
        }

        private JsonElement toJson() {
            return new JsonPrimitive(value);
        }
    }

    private enum NullParsedValueCodec implements ContextualFieldCodec<NullParsedValue> {

        INSTANCE;

        @Override
        public JsonElement serializeField(NullParsedValue value, Context<NullParsedValue> context) {
            return new JsonPrimitive(value.value);
        }

        @Override
        public NullParsedValue deserializeField(JsonElement value, Context<NullParsedValue> context) {
            if (value.isJsonNull()) {
                return new NullParsedValue(context.fieldName() + ":null");
            }
            return new NullParsedValue(value.getAsString());
        }
    }

    private static final class ParsedNullSyncTarget implements ISyncManaged {

        private final SyncDataHolder syncDataHolder = new SyncDataHolder(this);
        @SyncToClient
        private NullParsedValue clientParsed;
        @SyncToServer
        private NullParsedValue serverParsed;
        @SyncToClient
        private RegularNullParsedValue clientRegularParsed;
        @SyncToServer
        private RegularNullParsedValue serverRegularParsed;

        private ParsedNullSyncTarget(NullParsedValue clientParsed, NullParsedValue serverParsed,
                                     RegularNullParsedValue clientRegularParsed,
                                     RegularNullParsedValue serverRegularParsed) {
            this.clientParsed = clientParsed;
            this.serverParsed = serverParsed;
            this.clientRegularParsed = clientRegularParsed;
            this.serverRegularParsed = serverRegularParsed;
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
