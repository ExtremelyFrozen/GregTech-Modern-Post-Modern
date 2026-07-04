package com.gregtechceu.gtceu.api.sync_system;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side business handler for a single sync action id.
 */
public interface SyncActionHandler {

    /**
     * Returns the action identifier used by clients and the dispatcher.
     */
    ResourceLocation actionId();

    /**
     * Checks that the resolved holder in the current context is valid for this action.
     */
    boolean acceptsHolder(SyncActionContext context);

    /**
     * Checks that the component payload contains the typed data required by this action.
     */
    boolean acceptsPayload(DataComponentMap payload);

    /**
     * Checks player permissions after the packet has resolved a valid holder.
     */
    boolean mayExecute(ServerPlayer player, SyncActionContext context);

    /**
     * Executes the action after id, holder, payload, and permission validation pass.
     */
    void execute(SyncActionContext context);
}
