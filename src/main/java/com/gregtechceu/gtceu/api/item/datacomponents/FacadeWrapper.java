package com.gregtechceu.gtceu.api.item.datacomponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.serialization.Codec;

public record FacadeWrapper(BlockState state) {

    public static final Codec<FacadeWrapper> CODEC = BlockState.CODEC.xmap(FacadeWrapper::new, FacadeWrapper::state);
    public static final StreamCodec<RegistryFriendlyByteBuf, FacadeWrapper> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public FacadeWrapper decode(RegistryFriendlyByteBuf buffer) {
            return new FacadeWrapper(buffer.readById(Block.BLOCK_STATE_REGISTRY::byId));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, FacadeWrapper value) {
            buffer.writeById(Block.BLOCK_STATE_REGISTRY::getId, value.state());
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof FacadeWrapper(BlockState thatState)))
            return false;

        return state == thatState;
    }
}
