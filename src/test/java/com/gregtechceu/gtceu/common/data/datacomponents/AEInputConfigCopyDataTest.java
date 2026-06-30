package com.gregtechceu.gtceu.common.data.datacomponents;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

import java.util.Arrays;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class AEInputConfigCopyDataTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AEInputConfigCopyData")
    public static void codecKeepsNullableSlotsAndAutoPull(GameTestHelper helper) {
        AEInputConfigCopyData original = new AEInputConfigCopyData(Arrays.asList(null, null, null), (byte) 7, true,
                true);

        JsonElement json = AEInputConfigCopyData.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(GameTestAssertException::new);
        AEInputConfigCopyData decoded = AEInputConfigCopyData.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(GameTestAssertException::new);

        assertCopyData(helper, decoded, "codec");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AEInputConfigCopyData")
    public static void streamCodecKeepsNullableSlotsAndAutoPull(GameTestHelper helper) {
        AEInputConfigCopyData original = new AEInputConfigCopyData(Arrays.asList(null, null, null), (byte) 7, true,
                true);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess(),
                ConnectionType.OTHER);
        try {
            AEInputConfigCopyData.STREAM_CODEC.encode(buffer, original);
            AEInputConfigCopyData decoded = AEInputConfigCopyData.STREAM_CODEC.decode(buffer);
            assertCopyData(helper, decoded, "stream codec");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static void assertCopyData(GameTestHelper helper, AEInputConfigCopyData data, String path) {
        helper.assertTrue(data.stacks().size() == 3, path + " did not keep empty slot count");
        helper.assertTrue(data.stacks().stream().allMatch(stack -> stack == null), path + " did not keep null slots");
        helper.assertTrue(data.ghostCircuit() == 7, path + " did not keep ghost circuit");
        helper.assertTrue(data.distinctBuses(), path + " did not keep distinct buses");
        helper.assertTrue(data.autoPull(), path + " did not keep auto-pull");
    }
}
