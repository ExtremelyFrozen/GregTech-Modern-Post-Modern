package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Machine feature for blocks that expose a reusable work-state trait.
 *
 * <p>
 * Recipe machines use this through {@link IRecipeLogicMachine}; non-recipe machines can expose the same active,
 * waiting and suspend state without pretending to execute a recipe.
 * </p>
 */
public interface IWorkLogicMachine extends IMachineFeature, IWorkable {

    /**
     * Returns the work-state trait that owns status, waiting reason and tick subscription.
     */
    @NotNull
    WorkLogic getWorkLogic();

    /**
     * Called whenever the work-state status changes so machines can update render state or secondary effects.
     */
    default void notifyWorkStatusChanged(WorkLogic.Status oldStatus, WorkLogic.Status newStatus) {
        if (this instanceof MetaMachine metaMachine &&
                metaMachine.getRenderState().hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS)) {
            metaMachine.setRenderState(metaMachine.getRenderState()
                    .setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, newStatus));
        }
    }

    /**
     * Called when the external working-enabled flag changes.
     */
    default void notifyWorkingEnabledChanged(boolean oldValue, boolean newValue) {
        if (this instanceof MetaMachine metaMachine &&
                metaMachine.getRenderState().hasProperty(GTMachineModelProperties.IS_WORKING_ENABLED)) {
            metaMachine.setRenderState(metaMachine.getRenderState()
                    .setValue(GTMachineModelProperties.IS_WORKING_ENABLED, newValue));
        }
    }

    /**
     * Returns whether the work-state trait is allowed to subscribe and run on server ticks.
     */
    default boolean isWorkLogicAvailable() {
        return true;
    }

    /**
     * Returns whether the work-state trait should stay subscribed while idle.
     */
    default boolean keepSubscribing() {
        return true;
    }

    /**
     * Runs machine-specific non-recipe work while the trait is subscribed.
     */
    default void serverRunningTick() {}

    default void setStatus(WorkLogic.Status status) {
        getWorkLogic().setStatus(status);
    }

    default void setWaiting(@Nullable Component reason) {
        getWorkLogic().setWaiting(reason);
    }

    @Override
    default boolean isWorkingEnabled() {
        return getWorkLogic().isWorkingEnabled();
    }

    @Override
    default void setWorkingEnabled(boolean isWorkingAllowed) {
        getWorkLogic().setWorkingEnabled(isWorkingAllowed);
    }

    @Override
    default void setSuspendAfterFinish(boolean suspendAfterFinish) {
        getWorkLogic().setSuspendAfterFinish(suspendAfterFinish);
    }

    @Override
    default boolean isSuspendAfterFinish() {
        return getWorkLogic().isSuspendAfterFinish();
    }

    @Override
    default boolean isActive() {
        return getWorkLogic().isActive();
    }

    @Override
    default int getProgress() {
        return 0;
    }

    @Override
    default int getMaxProgress() {
        return 0;
    }
}
