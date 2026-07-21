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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable GT client-sync view of the linked Pattern Buffer captured for one Proxy menu.
 *
 * @param blockState   exact linked-buffer block state rendered by the menu-local world
 * @param data         full opening snapshot or a later GT client-sync delta
 * @param sharedFluids omitted for vanilla opening data, otherwise the complete indexed shared-fluid container
 */
public record MEPatternBufferProxyViewSnapshot(BlockState blockState, DataComponentMap data,
                                               List<FluidStack> sharedFluids) {

    private static final int SHARED_TANK_COUNT = 9;

    /** Registry-aware wire codec shared by opening data and menu-session updates. */
    public static final StreamCodec<RegistryFriendlyByteBuf, MEPatternBufferProxyViewSnapshot> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public MEPatternBufferProxyViewSnapshot decode(RegistryFriendlyByteBuf buffer) {
            BlockState blockState = BlockUIMenuType.BLOCK_STATE_STREAM_CODEC.decode(buffer);
            DataComponentMap data = SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buffer);
            int fluidCount = buffer.readVarInt();
            if (fluidCount != 0 && fluidCount != SHARED_TANK_COUNT) {
                throw new IllegalArgumentException(
                        "Pattern Buffer Proxy shared-fluid snapshot has an invalid tank count: " + fluidCount);
            }
            List<FluidStack> sharedFluids = new ArrayList<>(fluidCount);
            for (int i = 0; i < fluidCount; i++) {
                sharedFluids.add(FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer));
            }
            return new MEPatternBufferProxyViewSnapshot(blockState, data, sharedFluids);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MEPatternBufferProxyViewSnapshot value) {
            BlockUIMenuType.BLOCK_STATE_STREAM_CODEC.encode(buffer, value.blockState);
            SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buffer, value.data);
            buffer.writeVarInt(value.sharedFluids.size());
            for (FluidStack fluid : value.sharedFluids) {
                FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, fluid);
            }
        }
    };

    public MEPatternBufferProxyViewSnapshot {
        if (blockState.getBlock() != GTAEMachines.ME_PATTERN_BUFFER.getBlock()) {
            throw new IllegalArgumentException("Pattern Buffer Proxy view state must use the exact linked definition.");
        }
        if (!sharedFluids.isEmpty() && sharedFluids.size() != SHARED_TANK_COUNT) {
            throw new IllegalArgumentException(
                    "Pattern Buffer Proxy shared-fluid snapshot must contain every shared tank.");
        }
        sharedFluids = copySharedFluids(sharedFluids);
    }

    @Override
    public List<FluidStack> sharedFluids() {
        return copySharedFluids(sharedFluids);
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
        return new MEPatternBufferProxyViewSnapshot(
                buffer.getBlockState(), data, captureSharedFluids(buffer));
    }

    /** Wraps one already-collected GT client delta with the current render state and complete shared tank contents. */
    public static MEPatternBufferProxyViewSnapshot update(MEPatternBufferPartMachine buffer,
                                                          DataComponentMap changes) {
        requirePatternBuffer(buffer);
        return new MEPatternBufferProxyViewSnapshot(
                buffer.getBlockState(), changes, captureSharedFluids(buffer));
    }

    /** Retains only the block state needed to construct the client projection in vanilla menu opening data. */
    public MEPatternBufferProxyViewSnapshot withoutFieldData() {
        return new MEPatternBufferProxyViewSnapshot(blockState, DataComponentMap.EMPTY, List.of());
    }

    /**
     * Creates a menu-local client projection that does not require the real linked chunk to be present.
     */
    public MEPatternBufferPartMachine createDetachedView(Level sourceLevel, BlockPos bufferPos) {
        if (sourceLevel.isClientSide) {
            return ClientProjection.create(sourceLevel, bufferPos, this);
        }
        MEPatternBufferPartMachine view = createProjectionMachine(bufferPos, blockState);
        view.setLevel(sourceLevel);
        try {
            applyTo(view, sourceLevel.registryAccess());
            return view;
        } catch (RuntimeException | Error exception) {
            try {
                view.setRemoved();
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
        if (level == null) {
            throw new IllegalStateException("Pattern Buffer Proxy projection must be attached to its source level.");
        }
        if (level.isClientSide) {
            ClientProjection.requireWorld(view);
        } else if (level.getBlockEntity(view.getBlockPos()) == view) {
            throw new IllegalArgumentException(
                    "Pattern Buffer Proxy snapshot cannot overwrite a world-owned Pattern Buffer.");
        }
        if (!view.getBlockState().equals(blockState)) {
            if (level.isClientSide) {
                ClientProjection.updateBlockState(view, blockState);
            } else {
                view.setBlockState(blockState);
            }
        }
        if (!data.isEmpty()) {
            view.getSyncDataHolder().deserializeComponents(registries, data, true);
        }
        if (!sharedFluids.isEmpty()) {
            requireSharedTankCount(view);
            for (int i = 0; i < sharedFluids.size(); i++) {
                view.getShareTank().setFluidInTank(i, sharedFluids.get(i).copy());
            }
        }
    }

    /** Compares this wire snapshot with a fresh full snapshot of an existing projection. */
    public boolean matchesContent(MEPatternBufferPartMachine view, HolderLookup.Provider registries) {
        MEPatternBufferProxyViewSnapshot current = capture(view, registries);
        if (!blockState.equals(current.blockState) || !data.equals(current.data) ||
                sharedFluids.size() != current.sharedFluids.size()) {
            return false;
        }
        for (int i = 0; i < sharedFluids.size(); i++) {
            if (!FluidStack.matches(sharedFluids.get(i), current.sharedFluids.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Releases the menu-local world owned by a detached view. */
    public static void discardDetachedView(MEPatternBufferPartMachine view) {
        Level level = view.getLevel();
        if (level == null || view.isRemoved()) {
            throw new IllegalArgumentException("Only a live menu-local Pattern Buffer view can be discarded.");
        }
        if (level.isClientSide) {
            ClientProjection.discard(view);
            return;
        }
        if (level.getBlockEntity(view.getBlockPos()) == view) {
            throw new IllegalArgumentException("A world-owned Pattern Buffer cannot be discarded as a UI projection.");
        }
        view.setRemoved();
    }

    private static void requirePatternBuffer(MEPatternBufferPartMachine buffer) {
        if (!buffer.supportsMEPatternBufferActions()) {
            throw new IllegalArgumentException("Pattern Buffer Proxy view requires the exact linked definition.");
        }
    }

    private static List<FluidStack> captureSharedFluids(MEPatternBufferPartMachine buffer) {
        requireSharedTankCount(buffer);
        List<FluidStack> fluids = new ArrayList<>(SHARED_TANK_COUNT);
        for (int i = 0; i < SHARED_TANK_COUNT; i++) {
            fluids.add(buffer.getShareTank().getFluidInTank(i).copy());
        }
        return fluids;
    }

    private static void requireSharedTankCount(MEPatternBufferPartMachine buffer) {
        int tankCount = buffer.getMEPatternBufferShareTankCount();
        if (tankCount != SHARED_TANK_COUNT) {
            throw new IllegalStateException(
                    "Pattern Buffer Proxy expected " + SHARED_TANK_COUNT + " shared tanks, found " + tankCount);
        }
    }

    private static List<FluidStack> copySharedFluids(List<FluidStack> fluids) {
        return fluids.stream().map(FluidStack::copy).toList();
    }

    private static MEPatternBufferPartMachine createProjectionMachine(BlockPos bufferPos, BlockState blockState) {
        var machine = GTAEMachines.ME_PATTERN_BUFFER.getBlockEntityType().create(bufferPos, blockState);
        if (!(machine instanceof MEPatternBufferPartMachine view) || !view.supportsMEPatternBufferActions()) {
            throw new IllegalStateException("Pattern Buffer Proxy opening snapshot created the wrong projection type.");
        }
        return view;
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientProjection {

        private static MEPatternBufferPartMachine create(Level sourceLevel, BlockPos bufferPos,
                                                         MEPatternBufferProxyViewSnapshot snapshot) {
            DummyWorld projectionWorld = new DummyWorld(sourceLevel.registryAccess());
            if (!projectionWorld.setBlock(bufferPos, snapshot.blockState, Block.UPDATE_ALL)) {
                throw new IllegalStateException("Pattern Buffer Proxy failed to create its menu-local block state.");
            }
            try {
                if (!(projectionWorld.getBlockEntity(bufferPos) instanceof MEPatternBufferPartMachine view) ||
                        !view.supportsMEPatternBufferActions()) {
                    throw new IllegalStateException(
                            "Pattern Buffer Proxy opening snapshot created the wrong projection type.");
                }
                snapshot.applyTo(view, sourceLevel.registryAccess());
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

        private static void updateBlockState(MEPatternBufferPartMachine view, BlockState blockState) {
            DummyWorld projectionWorld = requireWorld(view);
            if (!projectionWorld.setBlock(view.getBlockPos(), blockState, Block.UPDATE_ALL) ||
                    projectionWorld.getBlockEntity(view.getBlockPos()) != view) {
                throw new IllegalStateException(
                        "Pattern Buffer Proxy projection changed identity with its block state.");
            }
        }

        private static void discard(MEPatternBufferPartMachine view) {
            if (!requireWorld(view).removeBlock(view.getBlockPos(), false)) {
                throw new IllegalStateException("Pattern Buffer Proxy menu-local view was already discarded.");
            }
        }

        private static DummyWorld requireWorld(MEPatternBufferPartMachine view) {
            if (!(view.getLevel() instanceof DummyWorld projectionWorld)) {
                throw new IllegalArgumentException(
                        "Pattern Buffer Proxy client view must belong to a menu-local world.");
            }
            return projectionWorld;
        }
    }
}
