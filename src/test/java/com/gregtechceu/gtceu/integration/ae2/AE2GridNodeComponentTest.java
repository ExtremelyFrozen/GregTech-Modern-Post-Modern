package com.gregtechceu.gtceu.integration.ae2;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.AE2GridNodeData;
import com.gregtechceu.gtceu.integration.ae2.utils.SerializableManagedGridNode;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.networking.IGridNode;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class AE2GridNodeComponentTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AE2GridNodeComponent")
    public static void gridNodeComponentsRoundTrip(GameTestHelper helper) {
        SerializableManagedGridNode node = new SerializableManagedGridNode(new Object(),
                (Object owner, IGridNode gridNode) -> {});

        DataComponentMap components = componentNetworkRoundTrip(helper, node.exportComponents());
        AE2GridNodeData data = components.get(GTDataComponents.AE2_GRID_NODE.get());
        helper.assertTrue(data != null, "AE2 grid node did not export typed component data");
        helper.assertTrue(data.payload().isJsonObject(), "AE2 grid node payload was not stored as structured JSON");

        SerializableManagedGridNode decoded = new SerializableManagedGridNode(new Object(),
                (Object owner, IGridNode gridNode) -> {});
        decoded.importComponents(components);
        helper.assertTrue(components.equals(componentNetworkRoundTrip(helper, decoded.exportComponents())),
                "AE2 grid node component payload did not round-trip");
        helper.succeed();
    }

    private static DataComponentMap componentNetworkRoundTrip(GameTestHelper helper, DataComponentMap components) {
        var json = DataComponentMap.CODEC
                .encodeStart(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                        components)
                .getOrThrow(GameTestAssertException::new);
        DataComponentMap jsonDecoded = DataComponentMap.CODEC
                .parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                        json)
                .getOrThrow(GameTestAssertException::new);

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess(), ConnectionType.OTHER);
        try {
            SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buffer, jsonDecoded);
            return SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
