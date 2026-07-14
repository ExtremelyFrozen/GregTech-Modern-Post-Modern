package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.api.sync_system.SyncDataHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Immutable GT client-sync view of the linked Pattern Buffer captured for one Proxy menu.
 *
 * @param blockState exact linked-buffer block state rendered by the menu-local world
 * @param data       full opening snapshot or a later GT client-sync delta
 */
public record MEPatternBufferProxyViewSnapshot(BlockState blockState, DataComponentMap data) {

    /** Registry-aware wire codec shared by opening data and menu-session updates. */
    public static final StreamCodec<RegistryFriendlyByteBuf, MEPatternBufferProxyViewSnapshot> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public MEPatternBufferProxyViewSnapshot decode(RegistryFriendlyByteBuf buffer) {
            BlockState blockState = BlockUIMenuType.BLOCK_STATE_STREAM_CODEC.decode(buffer);
            DataComponentMap data = SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buffer);
            return new MEPatternBufferProxyViewSnapshot(blockState, data);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MEPatternBufferProxyViewSnapshot value) {
            BlockUIMenuType.BLOCK_STATE_STREAM_CODEC.encode(buffer, value.blockState);
            SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buffer, value.data);
        }
    };

    public MEPatternBufferProxyViewSnapshot {
        if (blockState.getBlock() != GTAEMachines.ME_PATTERN_BUFFER.getBlock()) {
            throw new IllegalArgumentException("Pattern Buffer Proxy view state must use the exact linked definition.");
        }
    }

    /**
     * Captures every GT field needed by the remote page without consuming ordinary chunk-observer synchronization.
     */
    public static MEPatternBufferProxyViewSnapshot capture(MEPatternBufferPartMachine buffer,
                                                           HolderLookup.Provider registries) {
        requirePatternBuffer(buffer);
        SyncDataHolder syncData = buffer.getSyncDataHolder();
        DataComponentMap data;
        try {
            data = syncData.serializeFullClientSyncComponents(registries);
        } finally {
            syncData.resyncAllFields();
        }
        return new MEPatternBufferProxyViewSnapshot(buffer.getBlockState(), data);
    }

    /** Wraps one already-collected GT client delta with the current render state. */
    public static MEPatternBufferProxyViewSnapshot update(MEPatternBufferPartMachine buffer,
                                                          DataComponentMap changes) {
        requirePatternBuffer(buffer);
        return new MEPatternBufferProxyViewSnapshot(buffer.getBlockState(), changes);
    }

    /** Retains only the block state needed to construct the client projection in vanilla menu opening data. */
    public MEPatternBufferProxyViewSnapshot withoutFieldData() {
        return new MEPatternBufferProxyViewSnapshot(blockState, DataComponentMap.EMPTY);
    }

    /**
     * Creates a menu-local client projection that does not require the real linked chunk to be present.
     */
    public MEPatternBufferPartMachine createDetachedView(Level sourceLevel, BlockPos bufferPos) {
        DummyWorld projectionWorld = new DummyWorld(sourceLevel.registryAccess());
        if (!projectionWorld.setBlock(bufferPos, blockState, Block.UPDATE_ALL)) {
            throw new IllegalStateException("Pattern Buffer Proxy failed to create its menu-local block state.");
        }
        try {
            BlockEntity blockEntity = projectionWorld.getBlockEntity(bufferPos);
            if (!(blockEntity instanceof MEPatternBufferPartMachine view) || !view.supportsMEPatternBufferActions()) {
                throw new IllegalStateException(
                        "Pattern Buffer Proxy opening snapshot created the wrong projection type.");
            }
            applyTo(view, sourceLevel.registryAccess());
            return view;
        } catch (RuntimeException | Error exception) {
            try {
                if (!projectionWorld.removeBlock(bufferPos, false)) {
                    exception.addSuppressed(new IllegalStateException(
                            "Pattern Buffer Proxy failed to release its rejected menu-local view."));
                }
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    /** Applies this full snapshot or delta to an existing menu-local Pattern Buffer projection. */
    public void applyTo(MEPatternBufferPartMachine view, HolderLookup.Provider registries) {
        requirePatternBuffer(view);
        Level level = view.getLevel();
        if (!(level instanceof DummyWorld projectionWorld)) {
            throw new IllegalArgumentException("Pattern Buffer Proxy client view must belong to a menu-local world.");
        }
        if (!view.getBlockState().equals(blockState)) {
            if (!projectionWorld.setBlock(view.getBlockPos(), blockState, Block.UPDATE_ALL) ||
                    projectionWorld.getBlockEntity(view.getBlockPos()) != view) {
                throw new IllegalStateException(
                        "Pattern Buffer Proxy projection changed identity with its block state.");
            }
        }
        if (!data.isEmpty()) {
            view.getSyncDataHolder().deserializeComponents(registries, data, true);
        }
    }

    /** Compares this wire snapshot with a fresh full snapshot of an existing projection. */
    public boolean matchesContent(MEPatternBufferPartMachine view, HolderLookup.Provider registries) {
        return equals(capture(view, registries));
    }

    /** Releases the menu-local world owned by a detached view. */
    public static void discardDetachedView(MEPatternBufferPartMachine view) {
        if (!(view.getLevel() instanceof DummyWorld projectionWorld)) {
            throw new IllegalArgumentException("Only a menu-local Pattern Buffer view can be discarded.");
        }
        if (!projectionWorld.removeBlock(view.getBlockPos(), false)) {
            throw new IllegalStateException("Pattern Buffer Proxy menu-local view was already discarded.");
        }
    }

    private static void requirePatternBuffer(MEPatternBufferPartMachine buffer) {
        if (!buffer.supportsMEPatternBufferActions()) {
            throw new IllegalArgumentException("Pattern Buffer Proxy view requires the exact linked definition.");
        }
    }
}
