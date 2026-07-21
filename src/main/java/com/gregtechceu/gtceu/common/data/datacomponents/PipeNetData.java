package com.gregtechceu.gtceu.common.data.datacomponents;

import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Typed persistent data for pipe networks.
 *
 * <p>
 * Pipe networks mutate frequently at runtime, so the network structure is stored as a grouped
 * {@link DataComponentMap}. The world saved-data boundary is responsible for encoding this component data to NBT.
 * </p>
 */
public final class PipeNetData {

    private PipeNetData() {}

    public record Nodes(List<NodeEntry> nodes, List<DataComponentMap> properties) {

        public static final Codec<Nodes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NodeEntry.CODEC.listOf().fieldOf("nodes").forGetter(Nodes::nodes),
                DataComponentMap.CODEC.listOf().fieldOf("properties").forGetter(Nodes::properties))
                .apply(instance, Nodes::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Nodes> STREAM_CODEC = StreamCodec.composite(
                NodeEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), Nodes::nodes,
                SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.apply(ByteBufCodecs.list()), Nodes::properties,
                Nodes::new);

        public Nodes {
            nodes = List.copyOf(nodes);
            properties = List.copyOf(properties);
        }
    }

    public record NodeEntry(BlockPos pos, int propertyIndex, int openConnections, int mark, boolean active) {

        public static final Codec<NodeEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(NodeEntry::pos),
                Codec.INT.fieldOf("property_index").forGetter(NodeEntry::propertyIndex),
                Codec.INT.optionalFieldOf("open_connections", 0).forGetter(NodeEntry::openConnections),
                Codec.INT.optionalFieldOf("mark", 0).forGetter(NodeEntry::mark),
                Codec.BOOL.optionalFieldOf("active", false).forGetter(NodeEntry::active))
                .apply(instance, NodeEntry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, NodeEntry> STREAM_CODEC = new StreamCodec<>() {

            @Override
            public NodeEntry decode(RegistryFriendlyByteBuf buffer) {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
                int propertyIndex = buffer.readVarInt();
                int openConnections = buffer.readVarInt();
                int mark = buffer.readVarInt();
                boolean active = buffer.readBoolean();
                return new NodeEntry(pos, propertyIndex, openConnections, mark, active);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, NodeEntry value) {
                BlockPos.STREAM_CODEC.encode(buffer, value.pos);
                buffer.writeVarInt(value.propertyIndex);
                buffer.writeVarInt(value.openConnections);
                buffer.writeVarInt(value.mark);
                buffer.writeBoolean(value.active);
            }
        };
    }

    public record Wire(long voltage, int amperage, int lossPerBlock) {

        public static final Codec<Wire> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("voltage").forGetter(Wire::voltage),
                Codec.INT.fieldOf("amperage").forGetter(Wire::amperage),
                Codec.INT.fieldOf("loss_per_block").forGetter(Wire::lossPerBlock))
                .apply(instance, Wire::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Wire> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Wire::voltage,
                ByteBufCodecs.VAR_INT, Wire::amperage,
                ByteBufCodecs.VAR_INT, Wire::lossPerBlock,
                Wire::new);
    }

    public record FluidPipe(int maxTemperature, int throughput, boolean gasProof, boolean acidProof, boolean cryoProof,
                            boolean plasmaProof, int channels) {

        public static final Codec<FluidPipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("max_temperature").forGetter(FluidPipe::maxTemperature),
                Codec.INT.fieldOf("throughput").forGetter(FluidPipe::throughput),
                Codec.BOOL.fieldOf("gas_proof").forGetter(FluidPipe::gasProof),
                Codec.BOOL.fieldOf("acid_proof").forGetter(FluidPipe::acidProof),
                Codec.BOOL.fieldOf("cryo_proof").forGetter(FluidPipe::cryoProof),
                Codec.BOOL.fieldOf("plasma_proof").forGetter(FluidPipe::plasmaProof),
                Codec.INT.fieldOf("channels").forGetter(FluidPipe::channels))
                .apply(instance, FluidPipe::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidPipe> STREAM_CODEC = new StreamCodec<>() {

            @Override
            public FluidPipe decode(RegistryFriendlyByteBuf buffer) {
                int maxTemperature = buffer.readVarInt();
                int throughput = buffer.readVarInt();
                boolean gasProof = buffer.readBoolean();
                boolean acidProof = buffer.readBoolean();
                boolean cryoProof = buffer.readBoolean();
                boolean plasmaProof = buffer.readBoolean();
                int channels = buffer.readVarInt();
                return new FluidPipe(maxTemperature, throughput, gasProof, acidProof, cryoProof, plasmaProof,
                        channels);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, FluidPipe value) {
                buffer.writeVarInt(value.maxTemperature);
                buffer.writeVarInt(value.throughput);
                buffer.writeBoolean(value.gasProof);
                buffer.writeBoolean(value.acidProof);
                buffer.writeBoolean(value.cryoProof);
                buffer.writeBoolean(value.plasmaProof);
                buffer.writeVarInt(value.channels);
            }
        };
    }

    public record ItemPipe(int priority, float transferRate) {

        public static final Codec<ItemPipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("priority").forGetter(ItemPipe::priority),
                Codec.FLOAT.fieldOf("transfer_rate").forGetter(ItemPipe::transferRate))
                .apply(instance, ItemPipe::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemPipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ItemPipe::priority,
                ByteBufCodecs.FLOAT, ItemPipe::transferRate,
                ItemPipe::new);
    }

    public record DuctPipe(float transferRate) {

        public static final Codec<DuctPipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("transfer_rate").forGetter(DuctPipe::transferRate))
                .apply(instance, DuctPipe::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, DuctPipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, DuctPipe::transferRate,
                DuctPipe::new);
    }
}
