package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
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
 * LDLib2 Fancy configurator helper for multiblock output voiding mode.
 *
 * <p>
 * The selector sends a GTM machine action for server-side mutation and leaves existing legacy Fancy call sites
 * untouched until their owning machines migrate.
 */
public final class LDLib2VoidingModeFancyConfigurator {

    private static final ResourceLocation SET_VOIDING_MODE_ACTION = GTCEu.id("set_voiding_mode");
    private static final ResourceLocation VOIDING_MODE_FIELD = SyncFieldData.key("voidingMode");

    static {
        SyncActionDispatchers.server().register(new VoidingModeActionHandler());
    }

    private LDLib2VoidingModeFancyConfigurator() {}

    /**
     * Attaches a voiding mode selector to a migrated LDLib2 Fancy configurator panel.
     */
    public static void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel, IVoidable controller) {
        configuratorPanel.attachConfigurators(new LDLib2FancySelectorConfigurator<>(
                IVoidable.VoidingMode.VALUES,
                controller::getVoidingMode,
                (event, mode) -> {
                    var machine = configuratorPanel.getHolder().getMachine();
                    if (machine != null && machine.isRemote()) {
                        MachineUIHelper.sendAction(configuratorPanel.getHolder(), createSetVoidingModeAction(mode));
                        event.stopImmediatePropagation();
                        event.hasHandler = true;
                    }
                })
                .setTooltip(mode -> List.of(Component.translatable("gtpm.gui.multiblock.voiding_mode"),
                        Component.translatable(mode.getTooltip()))));
    }

    private static SyncActionData createSetVoidingModeAction(IVoidable.VoidingMode mode) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(VOIDING_MODE_FIELD, new JsonPrimitive(mode.ordinal()))
                        .build())
                .build();
        return new SyncActionData(SET_VOIDING_MODE_ACTION, mode.ordinal(), payload);
    }

    private static final class VoidingModeActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_VOIDING_MODE_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof IVoidable &&
                    context.holder() instanceof LDLib2FancyActionMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readVoidingMode(fields) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof IVoidable voidable)) {
                throw new IllegalStateException("Voiding mode action received a non-voidable holder.");
            }
            voidable.setVoidingMode(requireVoidingMode(context.payload()));
        }
    }

    private static IVoidable.VoidingMode requireVoidingMode(DataComponentMap payload) {
        IVoidable.VoidingMode mode = readVoidingMode(payload);
        if (mode == null) {
            throw new IllegalStateException("Voiding mode action payload is missing mode.");
        }
        return mode;
    }

    private static @Nullable IVoidable.VoidingMode readVoidingMode(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            return null;
        }
        return readVoidingMode(fields);
    }

    private static @Nullable IVoidable.VoidingMode readVoidingMode(SyncFieldData fields) {
        JsonElement element = fields.get(VOIDING_MODE_FIELD);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return null;
        }
        Integer ordinal = readExactInt(primitive);
        if (ordinal == null) {
            return null;
        }
        if (ordinal < 0 || ordinal >= IVoidable.VoidingMode.VALUES.length) {
            return null;
        }
        return IVoidable.VoidingMode.VALUES[ordinal];
    }

    private static @Nullable Integer readExactInt(JsonPrimitive primitive) {
        try {
            long value = primitive.getAsBigDecimal().longValueExact();
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        } catch (ArithmeticException | NumberFormatException e) {
            GTCEu.LOGGER.warn("Invalid voiding-mode integer action payload.", e);
        }
        return null;
    }
}
