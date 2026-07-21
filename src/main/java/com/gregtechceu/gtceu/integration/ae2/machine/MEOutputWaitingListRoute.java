package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Resolves the exact waiting-list receiver from an already opened LDLib2 machine menu.
 */
public final class MEOutputWaitingListRoute {

    private MEOutputWaitingListRoute() {}

    /**
     * Returns the unique receiver only when the active container, root holder, and target identity all match.
     */
    public static @Nullable MEOutputWaitingListReceiver resolve(Player player, int containerId,
                                                                MEOutputWaitingListTarget target) {
        return resolve(player, containerId, target, null);
    }

    /**
     * Resolves the receiver for an action whose packet already validated the exact controller machine holder.
     */
    public static @Nullable MEOutputWaitingListReceiver resolveForAction(Player player, MetaMachine rootMachine,
                                                                         MEOutputWaitingListTarget target) {
        if (!(player.containerMenu instanceof ModularUIContainerMenu menu)) {
            return null;
        }
        return resolve(player, menu.containerId, target, rootMachine);
    }

    /**
     * Resolves the exact receiver capability that owns the current server menu-session challenge.
     */
    public static @Nullable MEOutputWaitingListSessionReceiver resolveSessionForAction(
                                                                                       Player player,
                                                                                       MetaMachine rootMachine,
                                                                                       MEOutputWaitingListTarget target) {
        MEOutputWaitingListReceiver receiver = resolveForAction(player, rootMachine, target);
        return receiver instanceof MEOutputWaitingListSessionReceiver sessionReceiver ? sessionReceiver : null;
    }

    /**
     * Resolves a receiver that still targets the exact server machine owning a waiting-list publisher.
     */
    public static @Nullable MEOutputWaitingListReceiver resolve(Player player, int containerId, MetaMachine machine) {
        if (!(machine instanceof MEOutputWaitingListActionTarget actionTarget)) {
            return null;
        }
        MEOutputWaitingListReceiver receiver = resolve(player, containerId, actionTarget.getWaitingListTarget());
        return receiver != null && receiver.matchesWaitingListTarget(machine) ? receiver : null;
    }

    private static @Nullable MEOutputWaitingListReceiver resolve(Player player, int containerId,
                                                                 MEOutputWaitingListTarget target,
                                                                 @Nullable MetaMachine expectedRootMachine) {
        if (!(player.containerMenu instanceof ModularUIContainerMenu menu) || menu.containerId != containerId ||
                !menu.stillValid(player)) {
            return null;
        }
        if (!(menu.uiHolder instanceof MachineUIHolder rootHolder)) {
            GTCEu.LOGGER.warn("ME output waiting-list container {} has no machine root holder", containerId);
            return null;
        }
        MetaMachine rootMachine = rootHolder.getMachine();
        if (rootMachine == null || expectedRootMachine != null && rootMachine != expectedRootMachine) {
            return null;
        }

        String elementId = MEOutputWaitingListReceiver.elementId(target.pos());
        List<UIElement> elements = menu.getModularUI().getElementsById(elementId);
        if (elements.size() != 1) {
            GTCEu.LOGGER.warn("ME output waiting-list expected one receiver {} in container {}, found {}",
                    elementId, containerId, elements.size());
            return null;
        }
        UIElement element = elements.getFirst();
        if (!(element instanceof MEOutputWaitingListReceiver receiver)) {
            GTCEu.LOGGER.warn("ME output waiting-list element {} in container {} is not a receiver",
                    elementId, containerId);
            return null;
        }
        return receiver.matchesWaitingListTarget(target) ? receiver : null;
    }
}
