package com.gregtechceu.gtceu.api.item.datacomponents;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import io.netty.buffer.Unpooled;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class FacadeWrapperComponentTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FacadeWrapperComponent")
    public static void facadeWrapperDataComponentStreamRoundTripsBlockState(GameTestHelper helper) {
        BlockState state = Blocks.GLASS.defaultBlockState();
        DataComponentMap components = DataComponentMap.builder()
                .set(GTDataComponents.FACADE.get(), new FacadeWrapper(state))
                .build();

        DataComponentMap decoded = networkRoundTrip(helper, components);
        FacadeWrapper facade = decoded.get(GTDataComponents.FACADE.get());

        helper.assertTrue(facade != null, "facade component did not round-trip");
        helper.assertTrue(facade.state() == state, "facade block state did not round-trip");
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
}
