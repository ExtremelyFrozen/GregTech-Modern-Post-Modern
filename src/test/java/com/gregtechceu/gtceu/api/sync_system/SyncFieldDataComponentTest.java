package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.annotations.ClientFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToServer;
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SyncFieldDataComponentTest {

    static {
        FieldCodecs.registerContextual(NullParsedValue.class, NullParsedValueCodec.INSTANCE);
        FieldCodecs.register(NullParsedValue.class, NullParsedValue.CODEC);
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
    public static void constructorSnapshotsNullValuesAndExposesUnmodifiableFields(GameTestHelper helper) {
        ResourceLocation presentKey = SyncFieldData.key("present");
        ResourceLocation clearedKey = SyncFieldData.key("cleared");
        ResourceLocation laterKey = SyncFieldData.key("later");
        Map<ResourceLocation, JsonElement> source = new LinkedHashMap<>();
        source.put(presentKey, new JsonPrimitive("initial"));
        source.put(clearedKey, null);

        SyncFieldData fieldData = new SyncFieldData(source);
        source.put(presentKey, new JsonPrimitive("mutated"));
        source.remove(clearedKey);
        source.put(laterKey, new JsonPrimitive("later"));

        helper.assertTrue(fieldData.get(presentKey).getAsString().equals("initial"),
                "constructor did not isolate fields from later source map changes");
        helper.assertTrue(fieldData.get(clearedKey).isJsonNull(),
                "constructor did not normalize a null map value to JsonNull");
        helper.assertTrue(!fieldData.contains(laterKey),
                "constructor exposed a field added to the source map after construction");

        boolean mutationRejected = false;
        try {
            fieldData.fields().put(laterKey, new JsonPrimitive("rejected"));
        } catch (UnsupportedOperationException exception) {
            mutationRejected = true;
        }
        helper.assertTrue(mutationRejected, "fields accessor returned a modifiable map");

        SyncFieldData equalData = new SyncFieldData(fieldData.fields());
        helper.assertTrue(fieldData.equals(equalData) && fieldData.hashCode() == equalData.hashCode(),
                "Kotlin class did not retain record equality and hash code semantics");
        helper.assertTrue(fieldData.toString().equals("SyncFieldData[fields=" + fieldData.fields() + "]"),
                "Kotlin class did not retain the record string representation");

        SyncFieldData builderNull = SyncFieldData.builder()
                .put(clearedKey, (JsonElement) null)
                .build();
        helper.assertTrue(builderNull.get(clearedKey).isJsonNull(),
                "builder did not normalize a null value to JsonNull");
        helper.assertTrue(SyncFieldData.builder().build() == SyncFieldData.EMPTY,
                "empty builder did not return the EMPTY singleton");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void keyPreservesNamespacedValuesAndEscapesInvalidPathCharacters(GameTestHelper helper) {
        ResourceLocation namespaced = ResourceLocation.parse("example:already_valid");

        helper.assertTrue(SyncFieldData.key(namespaced.toString()).equals(namespaced),
                "namespaced sync field key was changed");
        helper.assertTrue(SyncFieldData.key("Current Parallel")
                .equals(GTCEu.id("_u0043urrent_u0020_u0050arallel")),
                "unqualified sync field key did not retain UTF-16 path escaping");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void jsonAndStreamCodecsRoundTripAllFieldValueForms(GameTestHelper helper) {
        ResourceLocation stringKey = SyncFieldData.key("string");
        ResourceLocation objectKey = SyncFieldData.key("object");
        ResourceLocation clearedKey = SyncFieldData.key("cleared");
        SyncFieldData original = SyncFieldData.builder()
                .put(stringKey, new JsonPrimitive("{\"looksLikeJson\":true}"))
                .put(objectKey, JsonParser.parseString("{\"enabled\":true,\"amount\":4}"))
                .put(clearedKey, JsonNull.INSTANCE)
                .build();

        SyncFieldData jsonDecoded = SyncFieldData.fromJson(original.toJson());
        SyncFieldData streamDecoded = fieldRoundTrip(helper, original);
        DataComponentMap componentDecoded = networkRoundTrip(helper,
                original.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()));

        helper.assertTrue(jsonDecoded.equals(original), "persistent codec changed sync field values");
        helper.assertTrue(streamDecoded.equals(original), "field stream codec changed sync field values");
        helper.assertTrue(original.equals(componentDecoded.get(GTDataComponents.SYNC_FIELD_DATA.get())),
                "data component map stream codec changed sync field values");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void codecsRetainLastDuplicateFieldValue(GameTestHelper helper) {
        ResourceLocation duplicateKey = SyncFieldData.key("duplicate");
        JsonArray entries = new JsonArray();
        entries.add(fieldEntry(duplicateKey, new JsonPrimitive(1)));
        entries.add(fieldEntry(duplicateKey, new JsonPrimitive(2)));

        SyncFieldData jsonDecoded = SyncFieldData.fromJson(entries);
        SyncFieldData streamDecoded = duplicateFieldStreamDecode(helper, duplicateKey);

        helper.assertTrue(jsonDecoded.fields().size() == 1 && jsonDecoded.get(duplicateKey).getAsInt() == 2,
                "persistent codec did not retain the last duplicate field value");
        helper.assertTrue(streamDecoded.fields().size() == 1 && streamDecoded.get(duplicateKey).getAsInt() == 2,
                "field stream codec did not retain the last duplicate field value");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void syncDataHolderParsesNestedNetworkExplicitNullFields(GameTestHelper helper) {
        NestedNullSyncTarget target = new NestedNullSyncTarget(
                new ParsedNullSyncTarget(
                        new NullParsedValue("client-original"),
                        new NullParsedValue("server-original"),
                        new RegularNullParsedValue("client-regular-original"),
                        new RegularNullParsedValue("server-regular-original")));
        SyncFieldData nestedData = SyncFieldData.builder()
                .put(SyncFieldData.key("clientParsed"), JsonNull.INSTANCE)
                .build();
        JsonElement nestedComponents = DataComponentMap.CODEC
                .encodeStart(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                        DataComponentMap.builder()
                                .set(GTDataComponents.SYNC_FIELD_DATA.get(), nestedData)
                                .build())
                .getOrThrow(GameTestAssertException::new);
        SyncFieldData clientData = SyncFieldData.builder()
                .put(SyncFieldData.key("nested"), nestedComponents)
                .build();
        DataComponentMap clientComponents = networkRoundTrip(helper, DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), clientData)
                .build());

        target.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), clientComponents);

        helper.assertTrue(target.nested.clientParsed != null,
                "nested client network explicit null was not parsed");
        helper.assertTrue("clientParsed:null".equals(target.nested.clientParsed.value),
                "nested client network explicit null did not use the contextual field codec result");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void syncDataHolderSkipsSavedExplicitNullFields(GameTestHelper helper) {
        NullSyncTarget target = new NullSyncTarget("saved", "client", "both");
        SyncFieldData savedData = SyncFieldData.builder()
                .put(SyncFieldData.key("savedValue"), JsonNull.INSTANCE)
                .build();

        target.getSyncDataHolder().deserializeFieldData(helper.getLevel().registryAccess(), savedData, false);

        helper.assertTrue("saved".equals(target.savedValue), "saved field explicit null was parsed");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void ordinaryNetworkNullClearsReferencesAndAcknowledgesWithoutEcho(GameTestHelper helper) {
        NullSyncTarget server = new NullSyncTarget("server-saved", "server-client", "server-both");
        NullSyncTarget fullSyncServer = new NullSyncTarget("full-saved", null, null);
        NullSyncTarget client = new NullSyncTarget("client-saved", "client-client", "client-both");
        RegistryAccess registries = helper.getLevel().registryAccess();
        server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        DataComponentMap fullSync = fullSyncServer.getSyncDataHolder()
                .serializeFullClientSyncComponents(registries);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, fullSync, true);

        helper.assertTrue(client.clientValue == null,
                "ordinary client field codec did not restore a full-sync null");
        helper.assertTrue(client.bothValue == null,
                "ordinary SyncBoth field codec did not restore a full-sync null");
        helper.assertTrue(client.getSyncDataHolder().collectServerNetworkChanges(registries).isEmpty(),
                "client echoed the authoritative full-sync null back to the server");

        DataComponentMap serverClear = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SyncFieldData.key("bothValue"), JsonNull.INSTANCE)
                        .build())
                .build();
        ServerFieldUpdateResult result = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, serverClear);

        helper.assertTrue(result.getAccepted() && result.getChanged(),
                "ordinary server field codec rejected an explicit reference null");
        helper.assertTrue(server.bothValue == null,
                "accepted explicit reference null did not commit to the server field");
        helper.assertTrue(server.getSyncDataHolder().scanAndMarkChanges(registries),
                "accepted SyncBoth null did not request an authoritative acknowledgement");

        DataComponentMap acknowledgement = server.getSyncDataHolder().collectClientNetworkChanges(registries, false);
        SyncFieldData acknowledgementFields = acknowledgement.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(acknowledgementFields != null && acknowledgementFields.fields().size() == 1,
                "authoritative null acknowledgement included unrelated fields");
        helper.assertTrue(acknowledgementFields.get(SyncFieldData.key("bothValue")).isJsonNull(),
                "authoritative acknowledgement did not retain the explicit null");

        client.getSyncDataHolder().applyClientNetworkUpdate(registries, acknowledgement);
        helper.assertTrue(client.bothValue == null,
                "client did not apply the authoritative null acknowledgement");
        helper.assertTrue(client.getSyncDataHolder().collectServerNetworkChanges(registries).isEmpty(),
                "client echoed the authoritative null acknowledgement back to the server");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void fullSyncReplacesImmutableCollectionsAndPreservesFinalMutableIdentity(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        CollectionSyncTarget server = new CollectionSyncTarget(
                List.of("server-list"), Map.of("server-map", 2), "server-both");
        CollectionSyncTarget client = new CollectionSyncTarget(
                List.of("client-list"), Map.of("client-map", 1), "client-both");
        List<String> immutableList = client.replaceableList;
        Map<String, Integer> immutableMap = client.replaceableMap;
        Object mutableList = client.mutableListIdentity();
        Object mutableMap = client.mutableMapIdentity();
        client.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        DataComponentMap fullSync = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, fullSync, true);

        helper.assertTrue(client.replaceableList != immutableList &&
                client.replaceableList.equals(List.of("server-list")),
                "full sync did not replace an immutable list field");
        helper.assertTrue(client.replaceableMap != immutableMap &&
                client.replaceableMap.equals(Map.of("server-map", 2)),
                "full sync did not replace an immutable map field");
        helper.assertTrue(client.mutableListIdentity() == mutableList &&
                client.mutableList.equals(List.of("server-list")),
                "full sync did not preserve final mutable list identity");
        helper.assertTrue(client.mutableMapIdentity() == mutableMap &&
                client.mutableMap.equals(Map.of("server-map", 2)),
                "full sync did not preserve final mutable map identity");
        helper.assertTrue("server-both".equals(client.bothValue),
                "full sync did not apply the SyncBoth value");
        helper.assertTrue(client.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "full sync produced a redundant changed-only client delta");
        helper.assertTrue(client.getSyncDataHolder().collectServerNetworkChanges(registries).isEmpty(),
                "full sync produced a redundant SyncBoth server echo");
        helper.assertTrue(server.getSyncDataHolder().collectServerNetworkChanges(registries).isEmpty(),
                "full sync did not establish the server-side SyncBoth baseline");

        client.bothValue = "changed-both";
        DataComponentMap request = client.getSyncDataHolder().collectServerNetworkChanges(registries);
        SyncFieldData requestFields = request.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(requestFields != null && requestFields.fields().size() == 1 &&
                new JsonPrimitive("changed-both").equals(requestFields.get(SyncFieldData.key("bothValue"))),
                "changed-only SyncBoth request included unrelated fields");

        ServerFieldUpdateResult applied = server.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, request);
        helper.assertTrue(applied.getAccepted() && applied.getChanged(),
                "changed-only SyncBoth request was not applied");
        DataComponentMap acknowledgement = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, acknowledgement);
        helper.assertTrue("changed-both".equals(client.bothValue),
                "authoritative SyncBoth acknowledgement did not apply to the client");
        helper.assertTrue(client.getSyncDataHolder().collectServerNetworkChanges(registries).isEmpty(),
                "authoritative SyncBoth acknowledgement echoed back to the server");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void clientListenersAndRerendersOnlyRunForChangedValues(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        ClientChangeSyncTarget server = new ClientChangeSyncTarget(7);
        ClientChangeSyncTarget client = new ClientChangeSyncTarget(7);

        client.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        DataComponentMap unchangedFullSync = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, unchangedFullSync, true);
        helper.assertTrue(client.listenerCalls == 1 && client.renderUpdates == 1,
                "first authoritative full sync did not initialize the client listener or rerender");
        helper.assertTrue(client.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged full sync produced a changed-only client delta");

        server.value = 9;
        DataComponentMap changedFullSync = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, changedFullSync, true);
        helper.assertTrue(client.value == 9 && client.listenerCalls == 2 && client.renderUpdates == 2,
                "changed full sync did not invoke its listener and rerender exactly once");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, changedFullSync, true);
        helper.assertTrue(client.listenerCalls == 2 && client.renderUpdates == 2,
                "repeated full sync invoked a client listener or rerender for an unchanged value");

        client.getSyncDataHolder().deserializeFieldData(registries,
                SyncFieldData.builder().put(SyncFieldData.key("value"), new JsonPrimitive(9)).build(), true, true);
        helper.assertTrue(client.listenerCalls == 2 && client.renderUpdates == 2,
                "unchanged direct field deserialization invoked a client listener or rerender");
        client.getSyncDataHolder().deserializeFieldData(registries,
                SyncFieldData.builder().put(SyncFieldData.key("value"), new JsonPrimitive(11)).build(), true, true);
        helper.assertTrue(client.value == 11 && client.listenerCalls == 3 && client.renderUpdates == 3,
                "changed direct field deserialization did not invoke its listener and rerender exactly once");
        helper.assertTrue(client.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "direct changed-only field deserialization produced a redundant client delta");
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
        helper.assertTrue("ordinary:null".equals(target.serverParsed.value),
                "server network explicit null did not use the detached ordinary field codec result");
        helper.assertTrue("regular:null".equals(target.clientRegularParsed.value),
                "client network explicit null did not use the regular field codec result");
        helper.assertTrue("regular:null".equals(target.serverRegularParsed.value),
                "server network explicit null did not use the regular field codec result");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncFieldDataComponent")
    public static void serverUpdateComponentKeysRejectAliasesAndHiddenFields(GameTestHelper helper) {
        assertServerUpdateComponentKeyRejected(helper, AliasedServerUpdateFields.class,
                "AliasedServerUpdateFields.aliased", "AliasedServerUpdateFields.shared");
        assertServerUpdateComponentKeyRejected(helper, HiddenServerUpdateFields.class,
                "ParentServerUpdateFields.shared", "HiddenServerUpdateFields.shared");
        helper.succeed();
    }

    private static void assertServerUpdateComponentKeyRejected(GameTestHelper helper, Class<?> targetClass,
                                                               String firstField, String secondField) {
        try {
            ClassSyncData.getClassData(targetClass);
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            if (message == null) {
                throw new GameTestAssertException("server update component key conflict had no message");
            }
            helper.assertTrue(message.contains(SyncFieldData.key("shared").toString()),
                    "conflict message omitted the server update component key");
            helper.assertTrue(message.contains(firstField),
                    "conflict message omitted field " + firstField);
            helper.assertTrue(message.contains(secondField),
                    "conflict message omitted field " + secondField);
            return;
        }
        throw new GameTestAssertException("duplicate server update component key was accepted");
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

    private static SyncFieldData fieldRoundTrip(GameTestHelper helper, SyncFieldData fieldData) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess(), ConnectionType.OTHER);
        try {
            SyncFieldData.STREAM_CODEC.encode(buffer, fieldData);
            return SyncFieldData.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static JsonObject fieldEntry(ResourceLocation key, JsonElement value) {
        JsonObject entry = new JsonObject();
        entry.addProperty("key", key.toString());
        entry.add("value", value);
        return entry;
    }

    private static SyncFieldData duplicateFieldStreamDecode(GameTestHelper helper, ResourceLocation key) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess(), ConnectionType.OTHER);
        try {
            buffer.writeVarInt(2);
            ResourceLocation.STREAM_CODEC.encode(buffer, key);
            buffer.writeUtf("1");
            ResourceLocation.STREAM_CODEC.encode(buffer, key);
            buffer.writeUtf("2");
            return SyncFieldData.STREAM_CODEC.decode(buffer);
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

    private static final class CollectionSyncTarget implements ISyncManaged {

        private final SyncDataHolder syncDataHolder = new SyncDataHolder(this);
        @SyncToClient
        private List<String> replaceableList;
        @SyncToClient
        private Map<String, Integer> replaceableMap;
        @SyncToClient
        private final List<String> mutableList = new ArrayList<>();
        @SyncToClient
        private final Map<String, Integer> mutableMap = new LinkedHashMap<>();
        @SyncBoth
        private String bothValue;

        private CollectionSyncTarget(List<String> replaceableList, Map<String, Integer> replaceableMap,
                                     String bothValue) {
            this.replaceableList = List.copyOf(replaceableList);
            this.replaceableMap = Map.copyOf(replaceableMap);
            this.mutableList.addAll(replaceableList);
            this.mutableMap.putAll(replaceableMap);
            this.bothValue = bothValue;
        }

        private Object mutableListIdentity() {
            return mutableList;
        }

        private Object mutableMapIdentity() {
            return mutableMap;
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

    private static final class ClientChangeSyncTarget implements ISyncManaged {

        private final SyncDataHolder syncDataHolder = new SyncDataHolder(this);
        @SyncToClient
        @RerenderOnChanged
        private int value;
        private int listenerCalls;
        private int renderUpdates;

        private ClientChangeSyncTarget(int value) {
            this.value = value;
        }

        @ClientFieldChangeListener(fieldName = "value")
        private void onValueChanged() {
            listenerCalls++;
        }

        @Override
        public SyncDataHolder getSyncDataHolder() {
            return syncDataHolder;
        }

        @Override
        public @Nullable ISyncManaged getParentSyncObject() {
            return null;
        }

        @Override
        public void scheduleRenderUpdate() {
            renderUpdates++;
        }
    }

    private abstract static class MetadataSyncTarget implements ISyncManaged {

        private final SyncDataHolder syncDataHolder = new SyncDataHolder(this);

        @Override
        public final SyncDataHolder getSyncDataHolder() {
            return syncDataHolder;
        }

        @Override
        public final @Nullable ISyncManaged getParentSyncObject() {
            return null;
        }

        @Override
        public final void markAsChanged() {}

        @Override
        public final void scheduleRenderUpdate() {}
    }

    private static final class AliasedServerUpdateFields extends MetadataSyncTarget {

        @SaveField(nbtKey = "shared")
        @SyncToServer
        private int aliased;
        @SyncToServer
        private int shared;
    }

    private static class ParentServerUpdateFields extends MetadataSyncTarget {

        @SyncToServer
        private int shared;
    }

    private static final class HiddenServerUpdateFields extends ParentServerUpdateFields {

        @SyncToServer
        private int shared;
    }

    private record NullParsedValue(String value) {

        private static final Codec<NullParsedValue> CODEC = ExtraCodecs.JSON.xmap(
                NullParsedValue::fromJson,
                NullParsedValue::toJson);

        private static NullParsedValue fromJson(JsonElement value) {
            if (value.isJsonNull()) {
                return new NullParsedValue("ordinary:null");
            }
            return new NullParsedValue(value.getAsString());
        }

        private JsonElement toJson() {
            return new JsonPrimitive(value);
        }
    }

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

    private static final class NestedNullSyncTarget implements ISyncManaged {

        private final SyncDataHolder syncDataHolder = new SyncDataHolder(this);
        @SyncToClient
        private final ParsedNullSyncTarget nested;

        private NestedNullSyncTarget(ParsedNullSyncTarget nested) {
            this.nested = nested;
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
