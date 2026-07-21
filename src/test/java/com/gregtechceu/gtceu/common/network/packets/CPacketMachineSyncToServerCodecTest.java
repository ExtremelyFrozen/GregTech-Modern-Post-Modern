package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonPrimitive;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;

import java.util.List;
import java.util.function.Consumer;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CPacketMachineSyncToServerCodecTest {

    private static final int MAX_BODY_LENGTH = 32_767;
    private static final int MAX_FIELD_JSON_LENGTH = 32_767;
    private static final BlockPos POSITION = new BlockPos(12, 34, -56);
    private static final ResourceLocation BLOCK_ENTITY_TYPE_ID = ResourceLocation.withDefaultNamespace("chest");
    private static final ResourceLocation FIELD_KEY = SyncFieldData.key("packet_codec_test");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketMachineSyncToServerCodec")
    public static void existingWireRoundTripsThroughBoundedPacketCodec(GameTestHelper helper) {
        DataComponentMap components = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(FIELD_KEY, new JsonPrimitive("value"))
                        .build())
                .set(GTDataComponents.ACTIVE.get(), true)
                .build();
        RegistryFriendlyByteBuf existingWire = newBuffer(helper);
        RegistryFriendlyByteBuf encoded = newBuffer(helper);
        try {
            writePacketHeader(existingWire);
            SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(existingWire, components);

            CPacketMachineSyncToServer packet = CPacketMachineSyncToServer.CODEC.decode(existingWire);
            helper.assertTrue(!existingWire.isReadable(), "bounded packet codec did not consume the existing wire");

            CPacketMachineSyncToServer.CODEC.encode(encoded, packet);
            helper.assertTrue(POSITION.equals(encoded.readBlockPos()), "packet round-trip changed the block position");
            helper.assertTrue(BLOCK_ENTITY_TYPE_ID.equals(encoded.readResourceLocation()),
                    "packet round-trip changed the block entity type");
            DataComponentMap decodedComponents = SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(encoded);
            helper.assertTrue(!encoded.isReadable(), "packet round-trip left unread bytes");
            helper.assertTrue(Boolean.TRUE.equals(decodedComponents.get(GTDataComponents.ACTIVE.get())),
                    "packet round-trip lost a regular data component");
            SyncFieldData decodedFields = decodedComponents.get(GTDataComponents.SYNC_FIELD_DATA.get());
            helper.assertTrue(decodedFields != null, "packet round-trip lost sync field data");
            helper.assertTrue("value".equals(decodedFields.get(FIELD_KEY).getAsString()),
                    "packet round-trip changed sync field JSON");
        } finally {
            existingWire.release();
            encoded.release();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketMachineSyncToServerCodec")
    public static void rejectsInvalidComponentCountsAndDuplicateTypes(GameTestHelper helper) {
        assertPacketDecodeFails(helper, "negative component count", buffer -> {
            writePacketHeader(buffer);
            buffer.writeVarInt(-1);
        });
        assertPacketDecodeFails(helper, "component count above four", buffer -> {
            writePacketHeader(buffer);
            buffer.writeVarInt(5);
        });
        assertPacketDecodeFails(helper, "duplicate component type", buffer -> {
            writePacketHeader(buffer);
            buffer.writeVarInt(2);
            writeSyncFieldComponentType(buffer);
            buffer.writeVarInt(0);
            writeSyncFieldComponentType(buffer);
            buffer.writeVarInt(0);
        });
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketMachineSyncToServerCodec")
    public static void rejectsInvalidFieldCountsAndDuplicateKeys(GameTestHelper helper) {
        assertPacketDecodeFails(helper, "negative sync field count", buffer -> writeSyncFieldPrefix(buffer, -1));
        assertPacketDecodeFails(helper, "sync field count above ten", buffer -> writeSyncFieldPrefix(buffer, 11));
        assertPacketDecodeFails(helper, "duplicate sync field key", buffer -> {
            writeSyncFieldPrefix(buffer, 2);
            ResourceLocation.STREAM_CODEC.encode(buffer, FIELD_KEY);
            buffer.writeUtf("true");
            ResourceLocation.STREAM_CODEC.encode(buffer, FIELD_KEY);
            buffer.writeUtf("false");
        });
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketMachineSyncToServerCodec")
    public static void acceptsTenUniqueFieldsAndEscapedJson(GameTestHelper helper) {
        String escapedJson = "\"line\\n" + "\\" + "u0041\"";
        RegistryFriendlyByteBuf existingWire = newBuffer(helper);
        RegistryFriendlyByteBuf encoded = newBuffer(helper);
        try {
            writeSyncFieldPrefix(existingWire, 10);
            for (int index = 0; index < 10; index++) {
                ResourceLocation.STREAM_CODEC.encode(existingWire, indexedFieldKey(index));
                existingWire.writeUtf(index == 0 ? escapedJson : Integer.toString(index));
            }

            CPacketMachineSyncToServer packet = CPacketMachineSyncToServer.CODEC.decode(existingWire);
            helper.assertTrue(!existingWire.isReadable(), "ten-field packet left unread bytes");

            CPacketMachineSyncToServer.CODEC.encode(encoded, packet);
            encoded.readBlockPos();
            encoded.readResourceLocation();
            DataComponentMap decodedComponents = SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(encoded);
            SyncFieldData decodedFields = decodedComponents.get(GTDataComponents.SYNC_FIELD_DATA.get());
            helper.assertTrue(decodedFields != null, "ten-field packet lost sync field data");
            helper.assertTrue(decodedFields.fields().size() == 10, "ten-field packet changed the field count");
            helper.assertTrue("line\nA".equals(decodedFields.get(indexedFieldKey(0)).getAsString()),
                    "escaped newline or Unicode JSON escape did not round-trip");
            helper.assertTrue(!encoded.isReadable(), "ten-field packet round-trip left unread bytes");
        } finally {
            existingWire.release();
            encoded.release();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketMachineSyncToServerCodec")
    public static void rejectsStrictlyInvalidJson(GameTestHelper helper) {
        List<InvalidJson> invalidValues = List.of(
                new InvalidJson("unquoted object key", "{unquoted:1}"),
                new InvalidJson("trailing comma", "{\"value\":1,}"),
                new InvalidJson("unclosed object", "{\"value\":1"),
                new InvalidJson("multiple top-level values", "{} {}"),
                new InvalidJson("bare newline in a quoted string", "\"line\nbreak\""),
                new InvalidJson("control character in a quoted string", "\"left" + (char) 1 + "right\""),
                new InvalidJson("leading byte order mark", (char) 0xFEFF + "true"));

        for (InvalidJson invalid : invalidValues) {
            assertPacketDecodeFails(helper, invalid.name(), buffer -> {
                writeSyncFieldPrefix(buffer, 1);
                ResourceLocation.STREAM_CODEC.encode(buffer, FIELD_KEY);
                buffer.writeUtf(invalid.value());
            });
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketMachineSyncToServerCodec")
    public static void rejectsTrailingPacketBytes(GameTestHelper helper) {
        DataComponentMap components = syncFieldPayload(new JsonPrimitive(true));
        assertPacketDecodeFails(helper, "trailing packet byte", buffer -> {
            writePacketHeader(buffer);
            SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buffer, components);
            buffer.writeByte(0x5A);
        });
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketMachineSyncToServerCodec")
    public static void enforcesFieldJsonLengthBoundary(GameTestHelper helper) {
        String acceptedJson = quotedStringJson(MAX_FIELD_JSON_LENGTH);
        RegistryFriendlyByteBuf accepted = newBuffer(helper);
        RegistryFriendlyByteBuf rejected = newBuffer(helper);
        try {
            writeSyncFieldPayload(accepted, acceptedJson);
            DataComponentMap decoded = MachineSyncPayloadCodec.INSTANCE.decode(accepted);
            SyncFieldData decodedFields = decoded.get(GTDataComponents.SYNC_FIELD_DATA.get());
            helper.assertTrue(decodedFields != null, "32767-character JSON lost sync field data");
            helper.assertTrue(decodedFields.get(FIELD_KEY).getAsString().length() == MAX_FIELD_JSON_LENGTH - 2,
                    "32767-character JSON changed at the field codec boundary");
            helper.assertTrue(!accepted.isReadable(), "32767-character JSON left unread field payload bytes");

            writeSyncFieldPayload(rejected, quotedStringJson(MAX_FIELD_JSON_LENGTH + 1));
            assertDecoderException(helper, "32768-character field JSON",
                    () -> MachineSyncPayloadCodec.INSTANCE.decode(rejected));
        } finally {
            accepted.release();
            rejected.release();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketMachineSyncToServerCodec")
    public static void enforcesPacketBodyLengthBoundary(GameTestHelper helper) {
        int seedJsonValueLength = 32_000;
        CPacketMachineSyncToServer seedPacket = packetWithJsonString(seedJsonValueLength);
        int seedBodyLength = encodedLength(helper, seedPacket);
        int boundaryJsonValueLength = seedJsonValueLength + MAX_BODY_LENGTH - seedBodyLength;
        helper.assertTrue(boundaryJsonValueLength > seedJsonValueLength,
                "packet boundary seed was not below the body limit");

        CPacketMachineSyncToServer boundaryPacket = packetWithJsonString(boundaryJsonValueLength);
        RegistryFriendlyByteBuf accepted = newBuffer(helper);
        RegistryFriendlyByteBuf rejected = newBuffer(helper);
        try {
            CPacketMachineSyncToServer.CODEC.encode(accepted, boundaryPacket);
            helper.assertTrue(accepted.readableBytes() == MAX_BODY_LENGTH,
                    "calculated packet did not reach the 32767-byte body boundary");
            CPacketMachineSyncToServer.CODEC.decode(accepted);
            helper.assertTrue(!accepted.isReadable(), "32767-byte packet body was not fully consumed");

            CPacketMachineSyncToServer.CODEC.encode(rejected, boundaryPacket);
            rejected.writeByte(0);
            DecoderException exception = assertDecoderException(helper, "32768-byte packet body",
                    () -> CPacketMachineSyncToServer.CODEC.decode(rejected));
            helper.assertTrue(exception.getMessage().contains("body length 32768"),
                    "32768-byte packet body failed for an unexpected reason: " + exception.getMessage());
        } finally {
            accepted.release();
            rejected.release();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketMachineSyncToServerCodec")
    public static void oversizeEncodeLeavesDestinationUnchanged(GameTestHelper helper) {
        CPacketMachineSyncToServer packet = packetWithRawJson(
                new JsonPrimitive("x".repeat(MAX_FIELD_JSON_LENGTH - 2)));
        EncoderException exception = assertPacketEncodeFailsWithoutChangingDestination(helper,
                "oversize packet body", packet);

        helper.assertTrue(exception.getMessage().contains("maximum body length"),
                "oversize packet encode failed for an unexpected reason: " + exception.getMessage());
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketMachineSyncToServerCodec")
    public static void rejectsNonFiniteJsonEncodeWithoutChangingDestination(GameTestHelper helper) {
        assertPacketEncodeFailsWithoutChangingDestination(helper, "NaN JSON",
                packetWithRawJson(new JsonPrimitive(Double.NaN)));
        assertPacketEncodeFailsWithoutChangingDestination(helper, "positive infinity JSON",
                packetWithRawJson(new JsonPrimitive(Double.POSITIVE_INFINITY)));
        helper.succeed();
    }

    private static RegistryFriendlyByteBuf newBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.OTHER);
    }

    private static void writePacketHeader(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(POSITION);
        buffer.writeResourceLocation(BLOCK_ENTITY_TYPE_ID);
    }

    private static void writeSyncFieldPrefix(RegistryFriendlyByteBuf buffer, int fieldCount) {
        writePacketHeader(buffer);
        buffer.writeVarInt(1);
        writeSyncFieldComponentType(buffer);
        buffer.writeVarInt(fieldCount);
    }

    private static void writeSyncFieldPayload(RegistryFriendlyByteBuf buffer, String rawJson) {
        buffer.writeVarInt(1);
        writeSyncFieldComponentType(buffer);
        buffer.writeVarInt(1);
        ResourceLocation.STREAM_CODEC.encode(buffer, FIELD_KEY);
        buffer.writeUtf(rawJson, rawJson.length());
    }

    private static void writeSyncFieldComponentType(RegistryFriendlyByteBuf buffer) {
        DataComponentType.STREAM_CODEC.encode(buffer, GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static ResourceLocation indexedFieldKey(int index) {
        return SyncFieldData.key("packet_codec_test_" + index);
    }

    private static DataComponentMap syncFieldPayload(JsonPrimitive value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(FIELD_KEY, value)
                        .build())
                .build();
    }

    private static CPacketMachineSyncToServer packetWithJsonString(int valueLength) {
        return packetWithRawJson(new JsonPrimitive("x".repeat(valueLength)));
    }

    private static CPacketMachineSyncToServer packetWithRawJson(JsonPrimitive value) {
        return new CPacketMachineSyncToServer(POSITION, BLOCK_ENTITY_TYPE_ID, syncFieldPayload(value));
    }

    private static String quotedStringJson(int rawJsonLength) {
        return "\"" + "x".repeat(rawJsonLength - 2) + "\"";
    }

    private static int encodedLength(GameTestHelper helper, CPacketMachineSyncToServer packet) {
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        try {
            CPacketMachineSyncToServer.CODEC.encode(buffer, packet);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    private static void assertPacketDecodeFails(GameTestHelper helper, String name,
                                                Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        try {
            writer.accept(buffer);
            assertDecoderException(helper, name, () -> CPacketMachineSyncToServer.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static EncoderException assertPacketEncodeFailsWithoutChangingDestination(
                                                                                      GameTestHelper helper,
                                                                                      String name,
                                                                                      CPacketMachineSyncToServer packet) {
        RegistryFriendlyByteBuf destination = newBuffer(helper);
        try {
            destination.writeByte(0x5A);
            int initialWriterIndex = destination.writerIndex();
            EncoderException exception = assertEncoderException(helper, name,
                    () -> CPacketMachineSyncToServer.CODEC.encode(destination, packet));

            helper.assertTrue(destination.writerIndex() == initialWriterIndex,
                    name + " changed the destination writer index");
            helper.assertTrue(destination.getUnsignedByte(0) == 0x5A,
                    name + " changed existing destination data");
            return exception;
        } finally {
            destination.release();
        }
    }

    private static DecoderException assertDecoderException(GameTestHelper helper, String name, Runnable action) {
        try {
            action.run();
        } catch (DecoderException exception) {
            helper.assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank(),
                    name + " produced a decoder exception without a message");
            return exception;
        }
        throw new GameTestAssertException(name + " was accepted");
    }

    private static EncoderException assertEncoderException(GameTestHelper helper, String name, Runnable action) {
        try {
            action.run();
        } catch (EncoderException exception) {
            helper.assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank(),
                    name + " produced an encoder exception without a message");
            return exception;
        }
        throw new GameTestAssertException(name + " was encoded");
    }

    private record InvalidJson(String name, String value) {}
}
