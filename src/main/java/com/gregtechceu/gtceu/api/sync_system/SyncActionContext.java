package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Server-side context passed to a registered sync action handler.
 *
 * @param player the server player that sent the action request.
 * @param holder the resolved machine, cover, or item holder for the request.
 * @param action the decoded action data.
 * @param pos    the block position for block-backed holders, or {@code null} for item actions.
 * @param side   the cover side for cover holders, or {@code null} for other holders.
 * @param hand   the interaction hand for held item holders, or {@code null} for block-backed holders.
 */
public record SyncActionContext(ServerPlayer player, Object holder, SyncActionData action, @Nullable BlockPos pos,
                                @Nullable Direction side, @Nullable InteractionHand hand) {

    /**
     * Creates context for a managed block entity action.
     */
    public static SyncActionContext machine(ServerPlayer player, ManagedSyncBlockEntity holder, SyncActionData action,
                                            BlockPos pos) {
        return new SyncActionContext(player, holder, action, pos, null, null);
    }

    /**
     * Creates context for an attached cover action.
     */
    public static SyncActionContext cover(ServerPlayer player, CoverBehavior holder, SyncActionData action, BlockPos pos,
                                          Direction side) {
        return new SyncActionContext(player, holder, action, pos, side, null);
    }

    /**
     * Creates context for an action targeting the item currently held by the player.
     */
    public static SyncActionContext item(ServerPlayer player, ItemStack holder, SyncActionData action,
                                         InteractionHand hand) {
        return new SyncActionContext(player, holder, action, null, null, hand);
    }

    /**
     * Returns the action id without forcing handlers to dereference the action record.
     */
    public ResourceLocation actionId() {
        return action.actionId();
    }

    /**
     * Returns the client sequence number without forcing handlers to dereference the action record.
     */
    public int sequence() {
        return action.sequence();
    }

    /**
     * Returns the component payload that action handlers must validate before use.
     */
    public DataComponentMap payload() {
        return action.payload();
    }
}
