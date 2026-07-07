package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * LDLib2 Fancy toggle for the common machine working-enabled state.
 *
 * <p>The button sends a GTM machine action for server-side mutation and does not use LDLib2-owned business state
 * channels.
 */
public class LDLib2WorkingEnabledFancyConfigurator extends LDLib2FancyConfiguratorButton.Toggle {

    private static final ResourceLocation SET_WORKING_ENABLED_ACTION = GTCEu.id("set_working_enabled");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");

    static {
        SyncActionDispatchers.server().register(new WorkingEnabledActionHandler());
    }

    /**
     * Creates a working-enabled toggle bound to the opened machine holder.
     */
    public LDLib2WorkingEnabledFancyConfigurator(IControllable controllable, MachineUIHolder holder) {
        super(GuiTextures.BUTTON_POWER.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0.5, 1, 0.5),
                controllable::isWorkingEnabled,
                (event, pressed) -> {
                    var machine = holder.getMachine();
                    if (machine != null && machine.isRemote()) {
                        MachineUIHelper.sendAction(holder, createSetWorkingEnabledAction(pressed));
                        event.stopImmediatePropagation();
                        event.hasHandler = true;
                    }
                });
        setTooltipsSupplier(pressed -> List.of(Component.translatable(
                pressed ? "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled")));
    }

    private static SyncActionData createSetWorkingEnabledAction(boolean workingEnabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(workingEnabled))
                        .build())
                .build();
        return new SyncActionData(SET_WORKING_ENABLED_ACTION, workingEnabled ? 1 : 0, payload);
    }

    private static final class WorkingEnabledActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_WORKING_ENABLED_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof IControllable &&
                    context.holder() instanceof LDLib2FancyUIMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readWorkingEnabled(fields) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof IControllable controllable)) {
                throw new IllegalStateException("Working-enabled action received a non-controllable holder.");
            }
            controllable.setWorkingEnabled(requireWorkingEnabled(context.payload()));
        }
    }

    private static boolean requireWorkingEnabled(DataComponentMap payload) {
        Boolean workingEnabled = readWorkingEnabled(payload);
        if (workingEnabled == null) {
            throw new IllegalStateException("Working-enabled action payload is missing enabled state.");
        }
        return workingEnabled;
    }

    private static @Nullable Boolean readWorkingEnabled(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            return null;
        }
        return readWorkingEnabled(fields);
    }

    private static @Nullable Boolean readWorkingEnabled(SyncFieldData fields) {
        JsonElement element = fields.get(WORKING_ENABLED_FIELD);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }
}
