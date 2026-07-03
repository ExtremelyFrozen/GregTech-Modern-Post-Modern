package com.gregtechceu.gtceu.integration.ae2.autobuild;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildMaterialSource;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildMaterialSources;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildProblem;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class MEAutoBuildSource implements AutoBuildMaterialSource {

    private static final int MAX_CONTAINER_DEPTH = 4;

    private final MEStorage inventory;
    private final IActionSource actionSource;

    private MEAutoBuildSource(MEStorage inventory, IActionSource actionSource) {
        this.inventory = inventory;
        this.actionSource = actionSource;
    }

    public static AutoBuildMaterialSource create(ServerPlayer player) {
        MEContext context = findContext(player);
        if (context.problem() != null) {
            GTCEu.LOGGER.warn("Cannot use ME for multiblock auto-build: {}", context.problem().message().getString());
            return AutoBuildMaterialSources.unavailable(context.problem());
        }
        return new MEAutoBuildSource(context.grid().getStorageService().getInventory(), IActionSource.ofPlayer(player));
    }

    @Override
    public Session openSession() {
        return new MESession(inventory, actionSource);
    }

    private static MEContext findContext(ServerPlayer player) {
        IItemHandler inventory = player.getCapability(Capabilities.ItemHandler.ENTITY);
        if (inventory == null) {
            return MEContext.failed("gtpm.multiblock.autobuild.me_no_player_inventory");
        }
        MEContext context = findContext(player, inventory, new IdentityHashMap<>(), 0);
        if (context.grid() != null || context.problem() != null) {
            return context;
        }
        return MEContext.failed("gtpm.multiblock.autobuild.me_no_linked_terminal");
    }

    private static MEContext findContext(ServerPlayer player, IItemHandler handler,
                                         Map<IItemHandler, Boolean> visitedHandlers, int depth) {
        if (depth > MAX_CONTAINER_DEPTH || visitedHandlers.put(handler, Boolean.TRUE) != null) {
            return MEContext.empty();
        }
        MEContext lastFailure = MEContext.empty();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            GlobalPos linkedPos = stack.get(AEComponents.WIRELESS_LINK_TARGET);
            if (linkedPos != null) {
                MEContext context = validateLinkedGrid(player, linkedPos);
                if (context.grid() != null) {
                    return context;
                }
                lastFailure = context;
            }
            IItemHandler childHandler = stack.getCapability(Capabilities.ItemHandler.ITEM);
            if (childHandler != null) {
                MEContext childContext = findContext(player, childHandler, visitedHandlers, depth + 1);
                if (childContext.grid() != null) {
                    return childContext;
                }
                if (childContext.problem() != null) {
                    lastFailure = childContext;
                }
            }
        }
        return lastFailure;
    }

    private static MEContext validateLinkedGrid(ServerPlayer player, GlobalPos linkedPos) {
        ServerLevel playerLevel = player.serverLevel();
        ServerLevel linkedLevel = player.serverLevel().getServer().getLevel(linkedPos.dimension());
        if (linkedLevel == null) {
            return MEContext.failed("gtpm.multiblock.autobuild.me_linked_level_missing");
        }
        if (linkedLevel.dimension() != playerLevel.dimension()) {
            return MEContext.failed("gtpm.multiblock.autobuild.me_wrong_dimension");
        }
        BlockEntity blockEntity = linkedLevel.getBlockEntity(linkedPos.pos());
        if (!(blockEntity instanceof IWirelessAccessPoint accessPoint)) {
            return MEContext.failed("gtpm.multiblock.autobuild.me_access_point_missing");
        }
        if (!accessPoint.isActive()) {
            return MEContext.failed("gtpm.multiblock.autobuild.me_access_point_inactive");
        }
        double range = accessPoint.getRange();
        double distance = player.distanceToSqr(Vec3.atCenterOf(linkedPos.pos()));
        if (distance > range * range) {
            return MEContext.failed("gtpm.multiblock.autobuild.me_out_of_range");
        }
        IGrid grid = accessPoint.getGrid();
        if (grid == null) {
            return MEContext.failed("gtpm.multiblock.autobuild.me_grid_missing");
        }
        return MEContext.available(grid);
    }

    private record MEContext(@Nullable IGrid grid, @Nullable AutoBuildProblem problem) {

        private static MEContext available(IGrid grid) {
            return new MEContext(grid, null);
        }

        private static MEContext failed(String key) {
            return new MEContext(null, new AutoBuildProblem(AutoBuildProblem.Type.ME_UNAVAILABLE, null,
                    Component.translatable(key)));
        }

        private static MEContext empty() {
            return new MEContext(null, null);
        }
    }

    private static final class MESession implements Session {

        private final MEStorage inventory;
        private final IActionSource actionSource;
        private final Object2LongOpenHashMap<AEItemKey> reservations = new Object2LongOpenHashMap<>();

        private MESession(MEStorage inventory, IActionSource actionSource) {
            this.inventory = inventory;
            this.actionSource = actionSource;
        }

        @Override
        public @Nullable Reservation reserve(List<ItemStack> candidates) {
            for (ItemStack candidate : candidates) {
                if (candidate.isEmpty()) {
                    continue;
                }
                AEItemKey key = AEItemKey.of(candidate);
                if (key == null) {
                    continue;
                }
                long reserved = reservations.getLong(key);
                long available = inventory.extract(key, reserved + 1, Actionable.SIMULATE, actionSource);
                if (available > reserved) {
                    reservations.addTo(key, 1);
                    return new MEReservation(inventory, actionSource, key);
                }
            }
            return null;
        }

        @Override
        public ItemStack insert(ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                return stack;
            }
            long inserted = inventory.insert(key, stack.getCount(), simulate ? Actionable.SIMULATE :
                    Actionable.MODULATE, actionSource);
            if (inserted <= 0) {
                return stack;
            }
            ItemStack remainder = stack.copy();
            remainder.shrink((int) inserted);
            return remainder;
        }
    }

    private record MEReservation(MEStorage inventory, IActionSource actionSource,
                                 AEItemKey key)
            implements Reservation {

        @Override
        public ItemStack stack() {
            return key.toStack(1);
        }

        @Override
        public boolean commit() {
            return inventory.extract(key, 1, Actionable.MODULATE, actionSource) == 1;
        }
    }
}
