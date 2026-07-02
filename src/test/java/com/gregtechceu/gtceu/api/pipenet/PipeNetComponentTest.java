package com.gregtechceu.gtceu.api.pipenet;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ItemPipeProperties;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.PipeNetData;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyNet;
import com.gregtechceu.gtceu.common.pipelike.cable.LevelEnergyNet;
import com.gregtechceu.gtceu.common.pipelike.duct.DuctPipeNet;
import com.gregtechceu.gtceu.common.pipelike.duct.DuctPipeProperties;
import com.gregtechceu.gtceu.common.pipelike.duct.LevelDuctPipeNet;
import com.gregtechceu.gtceu.common.pipelike.fluidpipe.FluidPipeNet;
import com.gregtechceu.gtceu.common.pipelike.fluidpipe.LevelFluidPipeNet;
import com.gregtechceu.gtceu.common.pipelike.item.ItemPipeNet;
import com.gregtechceu.gtceu.common.pipelike.item.LevelItemPipeNet;
import com.gregtechceu.gtceu.common.pipelike.laser.LaserPipeNet;
import com.gregtechceu.gtceu.common.pipelike.laser.LaserPipeProperties;
import com.gregtechceu.gtceu.common.pipelike.laser.LevelLaserPipeNet;
import com.gregtechceu.gtceu.common.pipelike.optical.LevelOpticalPipeNet;
import com.gregtechceu.gtceu.common.pipelike.optical.OpticalPipeNet;
import com.gregtechceu.gtceu.common.pipelike.optical.OpticalPipeProperties;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class PipeNetComponentTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PipeNetComponent")
    public static void pipeNetComponentsRoundTripWithoutNbt(GameTestHelper helper) {
        LevelEnergyNet levelNet = new LevelEnergyNet(helper.getLevel());
        BlockPos first = new BlockPos(1, 2, 3);
        BlockPos second = first.east();
        WireProperties properties = new WireProperties(128, 4, 2);
        levelNet.addNode(first, properties, 7, Node.ALL_OPENED, true);
        levelNet.addNode(second, properties.copy(), 7, Node.ALL_OPENED, false);

        EnergyNet net = levelNet.getNetFromPos(first);
        DataComponentMap components = net.exportComponents(helper.getLevel().registryAccess());
        PipeNetData.Nodes nodes = components.get(GTDataComponents.PIPE_NET_NODES.get());
        helper.assertTrue(nodes != null, "pipe net did not export node data component");
        helper.assertTrue(nodes.nodes().size() == 2, "pipe net did not export all nodes");
        helper.assertTrue(nodes.properties().size() == 1, "equal pipe properties were not deduplicated");

        DataComponentMap jsonDecoded = jsonRoundTrip(helper, components);
        DataComponentMap networkDecoded = networkRoundTrip(helper, jsonDecoded);

        TestEnergyNet decodedNet = new TestEnergyNet(levelNet);
        decodedNet.importComponents(helper.getLevel().registryAccess(), networkDecoded);
        Node<WireProperties> decodedFirst = decodedNet.getNodeAt(first);
        Node<WireProperties> decodedSecond = decodedNet.getNodeAt(second);
        helper.assertTrue(decodedFirst != null && decodedSecond != null, "pipe net nodes did not round-trip");
        helper.assertTrue(decodedFirst.mark == 7, "pipe node mark did not round-trip");
        helper.assertTrue(decodedFirst.openConnections == Node.ALL_OPENED,
                "pipe node open connections did not round-trip");
        helper.assertTrue(decodedFirst.isActive, "pipe node active state did not round-trip");
        helper.assertTrue(!decodedSecond.isActive, "pipe node inactive state did not round-trip");
        helper.assertTrue(decodedFirst.data.equals(properties), "wire properties did not round-trip");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PipeNetComponent")
    public static void pipeNetComponentsRoundTripConcretePipeData(GameTestHelper helper) {
        roundTripFluidPipeNet(helper);
        roundTripItemPipeNet(helper);
        roundTripDuctPipeNet(helper);
        roundTripLaserPipeNet(helper);
        roundTripOpticalPipeNet(helper);
        helper.succeed();
    }

    private static DataComponentMap jsonRoundTrip(GameTestHelper helper, DataComponentMap components) {
        JsonElement json = DataComponentMap.CODEC
                .encodeStart(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), components)
                .getOrThrow(GameTestAssertException::new);
        return DataComponentMap.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), json)
                .getOrThrow(GameTestAssertException::new);
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

    private static DataComponentMap componentRoundTrip(GameTestHelper helper, PipeNet<?> net) {
        DataComponentMap components = net.exportComponents(helper.getLevel().registryAccess());
        return networkRoundTrip(helper, jsonRoundTrip(helper, components));
    }

    private static void roundTripFluidPipeNet(GameTestHelper helper) {
        LevelFluidPipeNet levelNet = new LevelFluidPipeNet(helper.getLevel());
        BlockPos pos = new BlockPos(4, 5, 6);
        FluidPipeProperties properties = new FluidPipeProperties(1200, 480, true, true, true, false, 4);
        levelNet.addNode(pos, properties, 3, Node.ALL_OPENED, true);

        FluidPipeNet decoded = new FluidPipeNet(new LevelFluidPipeNet(helper.getLevel()));
        decoded.importComponents(helper.getLevel().registryAccess(),
                componentRoundTrip(helper, levelNet.getNetFromPos(pos)));
        FluidPipeProperties decodedProperties = decoded.getNodeAt(pos).data;
        helper.assertTrue(decodedProperties.equals(properties), "fluid pipe properties did not round-trip");
        helper.assertTrue(decodedProperties.isAcidProof(), "fluid pipe acid proof flag did not round-trip");
        helper.assertTrue(decodedProperties.isCryoProof(), "fluid pipe cryo proof flag did not round-trip");
    }

    private static void roundTripItemPipeNet(GameTestHelper helper) {
        LevelItemPipeNet levelNet = new LevelItemPipeNet(helper.getLevel());
        BlockPos pos = new BlockPos(7, 8, 9);
        ItemPipeProperties properties = new ItemPipeProperties(12, 2.5f);
        levelNet.addNode(pos, properties, 4, Node.ALL_OPENED, false);

        ItemPipeNet decoded = new ItemPipeNet(new LevelItemPipeNet(helper.getLevel()));
        decoded.importComponents(helper.getLevel().registryAccess(),
                componentRoundTrip(helper, levelNet.getNetFromPos(pos)));
        helper.assertTrue(decoded.getNodeAt(pos).data.equals(properties), "item pipe properties did not round-trip");
        helper.assertTrue(decoded.getNodeAt(pos).mark == 4, "item pipe mark did not round-trip");
    }

    private static void roundTripDuctPipeNet(GameTestHelper helper) {
        LevelDuctPipeNet levelNet = new LevelDuctPipeNet(helper.getLevel());
        BlockPos pos = new BlockPos(10, 11, 12);
        DuctPipeProperties properties = new DuctPipeProperties(3.75f);
        levelNet.addNode(pos, properties, 5, Node.ALL_OPENED, true);

        DuctPipeNet decoded = new DuctPipeNet(new LevelDuctPipeNet(helper.getLevel()));
        decoded.importComponents(helper.getLevel().registryAccess(),
                componentRoundTrip(helper, levelNet.getNetFromPos(pos)));
        helper.assertTrue(decoded.getNodeAt(pos).data.equals(properties), "duct pipe properties did not round-trip");
    }

    private static void roundTripLaserPipeNet(GameTestHelper helper) {
        LevelLaserPipeNet levelNet = new LevelLaserPipeNet(helper.getLevel());
        BlockPos pos = new BlockPos(13, 14, 15);
        levelNet.addNode(pos, LaserPipeProperties.INSTANCE, 0, Node.ALL_OPENED, false);

        LaserPipeNet decoded = new LaserPipeNet(new LevelLaserPipeNet(helper.getLevel()));
        decoded.importComponents(helper.getLevel().registryAccess(),
                componentRoundTrip(helper, levelNet.getNetFromPos(pos)));
        helper.assertTrue(decoded.getNodeAt(pos).data == LaserPipeProperties.INSTANCE,
                "laser pipe properties did not round-trip");
    }

    private static void roundTripOpticalPipeNet(GameTestHelper helper) {
        LevelOpticalPipeNet levelNet = new LevelOpticalPipeNet(helper.getLevel());
        BlockPos pos = new BlockPos(16, 17, 18);
        levelNet.addNode(pos, OpticalPipeProperties.INSTANCE, 0, Node.ALL_OPENED, true);

        OpticalPipeNet decoded = new OpticalPipeNet(new LevelOpticalPipeNet(helper.getLevel()));
        decoded.importComponents(helper.getLevel().registryAccess(),
                componentRoundTrip(helper, levelNet.getNetFromPos(pos)));
        helper.assertTrue(decoded.getNodeAt(pos).data == OpticalPipeProperties.INSTANCE,
                "optical pipe properties did not round-trip");
    }

    private static final class TestEnergyNet extends EnergyNet {

        private TestEnergyNet(LevelPipeNet<WireProperties, ? extends EnergyNet> world) {
            super(world);
        }
    }
}
