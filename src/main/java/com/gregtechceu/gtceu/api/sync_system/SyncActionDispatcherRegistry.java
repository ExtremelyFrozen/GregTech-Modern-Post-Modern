package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default server dispatcher backed by action id registrations.
 */
public class SyncActionDispatcherRegistry implements SyncActionDispatcher {

    /**
     * Stores the registered handler for each action id.
     */
    private final Map<ResourceLocation, SyncActionHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public void register(SyncActionHandler handler) {
        ResourceLocation actionId = handler.actionId();

        SyncActionHandler previous = handlers.putIfAbsent(actionId, handler);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate sync action handler for " + actionId);
        }
    }

    @Override
    public boolean dispatch(SyncActionContext context) {
        ResourceLocation actionId = context.actionId();
        SyncActionHandler handler = handlers.get(actionId);
        if (handler == null) {
            GTCEu.LOGGER.warn("Sync action: rejecting unknown action {} from {}", actionId,
                    context.player().getGameProfile().getName());
            return false;
        }

        if (!handler.acceptsHolder(context)) {
            GTCEu.LOGGER.warn("Sync action: rejecting invalid holder for action {} from {}", actionId,
                    context.player().getGameProfile().getName());
            return false;
        }

        if (context.payload().isEmpty()) {
            GTCEu.LOGGER.warn("Sync action: rejecting action {} from {} because payload is missing", actionId,
                    context.player().getGameProfile().getName());
            return false;
        }

        try {
            if (!handler.acceptsPayload(context.payload())) {
                GTCEu.LOGGER.warn("Sync action: rejecting invalid payload for action {} from {}", actionId,
                        context.player().getGameProfile().getName());
                return false;
            }

            if (!handler.mayExecute(context.player(), context)) {
                GTCEu.LOGGER.warn("Sync action: rejecting permission failure for action {} from {}", actionId,
                        context.player().getGameProfile().getName());
                return false;
            }

            handler.execute(context);
            return true;
        } catch (RuntimeException e) {
            GTCEu.LOGGER.error("Sync action: failed to execute action {} from {}", actionId,
                    context.player().getGameProfile().getName(), e);
            return false;
        }
    }
}
