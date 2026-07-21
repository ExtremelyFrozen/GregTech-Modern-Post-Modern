package com.gregtechceu.gtceu.common.data.datacomponents;

import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Typed data for GT-owned item and fluid transfer handlers.
 */
public final class TransferData {

    private TransferData() {}

    public record FluidTank(int capacity, FluidStack fluid) {

        public static final Codec<FluidTank> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("capacity").forGetter(FluidTank::capacity),
                FluidStack.OPTIONAL_CODEC.fieldOf("fluid").forGetter(FluidTank::fluid))
                .apply(instance, FluidTank::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidTank> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FluidTank::capacity,
                FluidStack.OPTIONAL_STREAM_CODEC, FluidTank::fluid,
                FluidTank::new);

        public FluidTank {
            fluid = fluid.copy();
        }
    }

    public record FluidHandlers(List<DataComponentMap> handlers) {

        public static final Codec<FluidHandlers> CODEC = DataComponentMap.CODEC.listOf()
                .xmap(FluidHandlers::new, FluidHandlers::handlers);
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidHandlers> STREAM_CODEC = SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC
                .apply(ByteBufCodecs.list())
                .map(FluidHandlers::new, FluidHandlers::handlers);

        public FluidHandlers {
            handlers = List.copyOf(handlers);
        }
    }

    public record ItemHandler(int slots, List<ItemStack> stacks) {

        public static final Codec<ItemHandler> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slots").forGetter(ItemHandler::slots),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("stacks").forGetter(ItemHandler::stacks))
                .apply(instance, ItemHandler::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemHandler> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ItemHandler::slots,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), ItemHandler::stacks,
                ItemHandler::new);

        public ItemHandler {
            stacks = stacks.stream().map(ItemStack::copy).toList();
            if (slots != stacks.size()) {
                throw new IllegalArgumentException("Item handler component encoded " + stacks.size() +
                        " stacks for " + slots + " slots");
            }
        }
    }
}
