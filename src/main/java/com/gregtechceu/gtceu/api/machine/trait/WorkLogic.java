package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IWorkLogicMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.sync_system.annotations.ClientFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Generic work-state trait shared by recipe and non-recipe working machines.
 */
public class WorkLogic extends MachineTrait implements IFancyTooltip {

    public static final MachineTraitType<WorkLogic> TYPE = new MachineTraitType<>(WorkLogic.class, false);

    public enum Status implements StringRepresentable {

        IDLE("idle"),
        WORKING("working"),
        WAITING("waiting"),
        SUSPEND("suspend");

        @Getter
        private final String serializedName;

        Status(String name) {
            this.serializedName = name;
        }
    }

    public static final EnumProperty<WorkLogic.Status> STATUS_PROPERTY = GTMachineModelProperties.RECIPE_LOGIC_STATUS;

    @Getter
    @SaveField
    @SyncToClient
    private Status status = Status.IDLE;

    @Getter
    @Setter
    @SaveField
    protected boolean suspendAfterFinish = false;

    @Getter
    @Nullable
    @SaveField
    @SyncToClient
    protected Component waitingReason = null;

    protected @Nullable TickableSubscription subscription;

    public WorkLogic() {}

    @Override
    public MachineTraitType<?> getTraitType() {
        return TYPE;
    }

    public IWorkLogicMachine getWorkMachine() {
        return (IWorkLogicMachine) getMachine();
    }

    @Override
    protected List<Class<?>> validMachineClasses() {
        return List.of(IWorkLogicMachine.class);
    }

    @SuppressWarnings("unused")
    @ClientFieldChangeListener(fieldName = "status")
    protected void onStatusSynced() {
        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS)) {
            setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, status));
        }
        scheduleRenderUpdate();
        updateSound();
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        updateTickSubscription();
    }

    public void updateTickSubscription() {
        if (isSuspend() || !getWorkMachine().isWorkLogicAvailable()) {
            unsubscribeTick();
        } else {
            subscription = subscribeServerTick(subscription, this::serverTick);
        }
    }

    protected void unsubscribeTick() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    public void serverTick() {
        if (!isSuspend()) {
            getWorkMachine().serverRunningTick();
        }
        if (isSuspend() || (isIdle() && !getWorkMachine().keepSubscribing())) {
            unsubscribeTick();
        }
    }

    public void setStatus(Status status) {
        if (this.status != status) {
            Status oldStatus = this.status;
            if ((status == Status.WAITING || status == Status.SUSPEND) && suspendAfterFinish) {
                status = Status.SUSPEND;
                suspendAfterFinish = false;
            }
            this.status = status;
            getWorkMachine().notifyWorkStatusChanged(oldStatus, status);
            syncDataHolder.markClientSyncFieldDirty("status");
            MachineRenderState renderState = getRenderState();
            if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS)) {
                setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, status));
            }
            updateTickSubscription();
            if (this.status != Status.WAITING) {
                waitingReason = null;
            }
        }
    }

    public void setWaiting(@Nullable Component reason) {
        setStatus(Status.WAITING);
        waitingReason = reason;
        onWaiting();
    }

    protected void onWaiting() {}

    public boolean isWorking() {
        return status == Status.WORKING;
    }

    public boolean isIdle() {
        return status == Status.IDLE;
    }

    public boolean isWaiting() {
        return status == Status.WAITING;
    }

    public boolean isSuspend() {
        return status == Status.SUSPEND;
    }

    public boolean isWorkingEnabled() {
        return !isSuspend();
    }

    public void setWorkingEnabled(boolean isWorkingAllowed) {
        setStatus(isWorkingAllowed ? Status.IDLE : Status.SUSPEND);
        getWorkMachine().notifyWorkingEnabledChanged(!isWorkingAllowed, isWorkingAllowed);
        updateTickSubscription();
    }

    public boolean isActive() {
        return isWorking() || isWaiting();
    }

    public int getProgress() {
        return 0;
    }

    public int getMaxProgress() {
        return 0;
    }

    public void reset() {
        waitingReason = null;
        if (!isSuspend()) {
            setStatus(Status.IDLE);
        }
        updateTickSubscription();
        getSyncDataHolder().resyncAllFields();
    }

    public void updateSound() {}

    @Override
    public IGuiTexture getFancyTooltipIcon() {
        if (showFancyTooltip()) {
            return GuiTextures.INSUFFICIENT_INPUT;
        }
        return IGuiTexture.EMPTY;
    }

    @Override
    public List<Component> getFancyTooltip() {
        if (isWaiting() && waitingReason != null) {
            return List.of(waitingReason);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean showFancyTooltip() {
        return waitingReason != null;
    }
}
