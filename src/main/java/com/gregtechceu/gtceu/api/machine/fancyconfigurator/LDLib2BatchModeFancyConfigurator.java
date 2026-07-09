package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.BatchModeMachine;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * LDLib2 Fancy configurator helper for multiblock batch mode.
 *
 * <p>
 * The button sends a GTM machine action for server-side mutation and leaves legacy Fancy controller wiring
 * untouched until each owning machine migrates to LDLib2.
 */
public final class LDLib2BatchModeFancyConfigurator {

    private static final ResourceLocation SET_BATCH_ENABLED_ACTION = GTCEu.id("set_batch_enabled");
    private static final ResourceLocation BATCH_ENABLED_FIELD = SyncFieldData.key("batchEnabled");

    static {
        SyncActionDispatchers.server().register(new BatchEnabledActionHandler());
    }

    private LDLib2BatchModeFancyConfigurator() {}

    /**
     * Attaches the batch mode toggle when the machine definition uses the batch recipe modifier.
     */
    public static void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel,
                                           BatchModeMachine machine) {
        if (!machine.supportsBatchMode()) {
            return;
        }

        configuratorPanel.attachConfigurators(new LDLib2FancyConfiguratorButton.Toggle(
                GuiTextures.BUTTON_BATCH.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_BATCH.getSubTexture(0, 0.5, 1, 0.5),
                machine::isBatchEnabled,
                (event, pressed) -> {
                    var openedMachine = configuratorPanel.getHolder().getMachine();
                    if (openedMachine != null && openedMachine.isRemote()) {
                        MachineUIHelper.sendAction(configuratorPanel.getHolder(),
                                createSetBatchEnabledAction(pressed));
                        event.stopImmediatePropagation();
                        event.hasHandler = true;
                    }
                })
                .setTooltipsSupplier(pressed -> List.of(Component.translatable(
                        "gtpm.machine.batch_" + (pressed ? "enabled" : "disabled")))));
    }

    private static SyncActionData createSetBatchEnabledAction(boolean batchEnabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(BATCH_ENABLED_FIELD, new JsonPrimitive(batchEnabled))
                        .build())
                .build();
        return new SyncActionData(SET_BATCH_ENABLED_ACTION, batchEnabled ? 1 : 0, payload);
    }

    private static final class BatchEnabledActionHandler implements SyncActionHandler {

        @Override
        public @NotNull ResourceLocation actionId() {
            return SET_BATCH_ENABLED_ACTION;
        }

        @Override
        public boolean acceptsHolder(@NotNull SyncActionContext context) {
            return context.holder() instanceof BatchModeMachine machine &&
                    context.holder() instanceof LDLib2FancyActionMachine &&
                    machine.supportsBatchMode();
        }

        @Override
        public boolean acceptsPayload(@NotNull DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBatchEnabled(fields) != null;
        }

        @Override
        public boolean mayExecute(@NotNull ServerPlayer player, @NotNull SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(@NotNull SyncActionContext context) {
            if (!(context.holder() instanceof BatchModeMachine machine) ||
                    !(context.holder() instanceof LDLib2FancyActionMachine) ||
                    !machine.supportsBatchMode()) {
                throw new IllegalStateException("Batch mode action received an invalid holder.");
            }
            machine.setBatchEnabled(requireBatchEnabled(context.payload()));
        }
    }

    private static boolean requireBatchEnabled(DataComponentMap payload) {
        Boolean batchEnabled = readBatchEnabled(payload);
        if (batchEnabled == null) {
            throw new IllegalStateException("Batch mode action payload is missing enabled state.");
        }
        return batchEnabled;
    }

    private static @Nullable Boolean readBatchEnabled(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            return null;
        }
        return readBatchEnabled(fields);
    }

    private static @Nullable Boolean readBatchEnabled(SyncFieldData fields) {
        JsonElement element = fields.get(BATCH_ENABLED_FIELD);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }
}
