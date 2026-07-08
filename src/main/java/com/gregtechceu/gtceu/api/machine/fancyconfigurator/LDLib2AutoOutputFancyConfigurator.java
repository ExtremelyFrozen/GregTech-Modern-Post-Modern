package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * LDLib2 Fancy configurator helper for common auto-output item and fluid toggles.
 *
 * <p>
 * The buttons send GTM machine actions for server-side mutation and do not use LDLib2-owned business state
 * channels.
 */
public final class LDLib2AutoOutputFancyConfigurator {

    private static final ResourceLocation SET_AUTO_OUTPUT_ITEMS_ACTION = GTCEu.id("set_auto_output_items");
    private static final ResourceLocation SET_AUTO_OUTPUT_FLUIDS_ACTION = GTCEu.id("set_auto_output_fluids");
    private static final ResourceLocation AUTO_OUTPUT_ITEMS_FIELD = SyncFieldData.key("autoOutputItems");
    private static final ResourceLocation AUTO_OUTPUT_FLUIDS_FIELD = SyncFieldData.key("autoOutputFluids");

    static {
        SyncActionDispatchers.server().register(new AutoOutputItemsActionHandler());
        SyncActionDispatchers.server().register(new AutoOutputFluidsActionHandler());
    }

    private LDLib2AutoOutputFancyConfigurator() {}

    /**
     * Attaches supported auto-output toggles to a migrated LDLib2 Fancy configurator panel.
     */
    public static void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel, AutoOutputTrait trait) {
        if (trait.supportsAutoOutputFluids()) {
            configuratorPanel.attachConfigurators(createAutoOutputFluidConfigurator(configuratorPanel, trait));
        }
        if (trait.supportsAutoOutputItems()) {
            configuratorPanel.attachConfigurators(createAutoOutputItemConfigurator(configuratorPanel, trait));
        }
    }

    private static LDLib2FancyConfiguratorButton.Toggle createAutoOutputFluidConfigurator(
                                                                                          LDLib2ConfiguratorPanelElement panel,
                                                                                          AutoOutputTrait trait) {
        return createAutoOutputConfigurator(
                GuiTextures.group(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0, 1, 0.5),
                        GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON.getSubTexture(0, 1 / 3f, 1, 1 / 3f)),
                GuiTextures.group(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0.5, 1, 0.5),
                        GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON.getSubTexture(0, 2 / 3f, 1, 1 / 3f)),
                trait::isAutoOutputFluids,
                "gtpm.gui.fluid_auto_output",
                (event, enabled) -> {
                    var machine = panel.getHolder().getMachine();
                    if (machine != null && machine.isRemote()) {
                        MachineUIHelper.sendAction(panel.getHolder(), createSetAutoOutputFluidsAction(enabled));
                        event.stopImmediatePropagation();
                        event.hasHandler = true;
                    }
                });
    }

    private static LDLib2FancyConfiguratorButton.Toggle createAutoOutputItemConfigurator(
                                                                                         LDLib2ConfiguratorPanelElement panel,
                                                                                         AutoOutputTrait trait) {
        return createAutoOutputConfigurator(
                GuiTextures.group(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0, 1, 0.5),
                        GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 1 / 3f, 1, 1 / 3f)),
                GuiTextures.group(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0.5, 1, 0.5),
                        GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 2 / 3f, 1, 1 / 3f)),
                trait::isAutoOutputItems,
                "gtpm.gui.item_auto_output",
                (event, enabled) -> {
                    var machine = panel.getHolder().getMachine();
                    if (machine != null && machine.isRemote()) {
                        MachineUIHelper.sendAction(panel.getHolder(), createSetAutoOutputItemsAction(enabled));
                        event.stopImmediatePropagation();
                        event.hasHandler = true;
                    }
                });
    }

    private static LDLib2FancyConfiguratorButton.Toggle createAutoOutputConfigurator(
                                                                                     IGuiTexture disabledIcon,
                                                                                     IGuiTexture enabledIcon,
                                                                                     BooleanSupplier stateSupplier,
                                                                                     String tooltipBaseLangKey,
                                                                                     BiConsumer<UIEvent, Boolean> clickHandler) {
        return new LDLib2FancyConfiguratorButton.Toggle(disabledIcon, enabledIcon, stateSupplier, clickHandler)
                .setTooltipsSupplier(enabled -> List.of(Component.translatable(
                        tooltipBaseLangKey + "." + (enabled ? "enabled" : "disabled"))));
    }

    private static SyncActionData createSetAutoOutputItemsAction(boolean enabled) {
        return createSetAutoOutputAction(SET_AUTO_OUTPUT_ITEMS_ACTION, AUTO_OUTPUT_ITEMS_FIELD, enabled);
    }

    private static SyncActionData createSetAutoOutputFluidsAction(boolean enabled) {
        return createSetAutoOutputAction(SET_AUTO_OUTPUT_FLUIDS_ACTION, AUTO_OUTPUT_FLUIDS_FIELD, enabled);
    }

    private static SyncActionData createSetAutoOutputAction(ResourceLocation actionId, ResourceLocation field,
                                                            boolean enabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, new JsonPrimitive(enabled))
                        .build())
                .build();
        return new SyncActionData(actionId, enabled ? 1 : 0, payload);
    }

    private static final class AutoOutputItemsActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_AUTO_OUTPUT_ITEMS_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            AutoOutputTrait trait = readAutoOutputTrait(context);
            return trait != null && trait.supportsAutoOutputItems();
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, AUTO_OUTPUT_ITEMS_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            AutoOutputTrait trait = requireAutoOutputItemsTrait(context);
            trait.setAllowAutoOutputItems(requireBoolean(context.payload(), AUTO_OUTPUT_ITEMS_FIELD,
                    "Auto-output items action payload is missing enabled state."));
        }
    }

    private static final class AutoOutputFluidsActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_AUTO_OUTPUT_FLUIDS_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            AutoOutputTrait trait = readAutoOutputTrait(context);
            return trait != null && trait.supportsAutoOutputFluids();
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, AUTO_OUTPUT_FLUIDS_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            AutoOutputTrait trait = requireAutoOutputFluidsTrait(context);
            trait.setAllowAutoOutputFluids(requireBoolean(context.payload(), AUTO_OUTPUT_FLUIDS_FIELD,
                    "Auto-output fluids action payload is missing enabled state."));
        }
    }

    private static @Nullable AutoOutputTrait readAutoOutputTrait(SyncActionContext context) {
        if (!(context.holder() instanceof MetaMachine machine) ||
                !(context.holder() instanceof LDLib2FancyUIMachine)) {
            return null;
        }
        return machine.getTrait(AutoOutputTrait.TYPE);
    }

    private static AutoOutputTrait requireAutoOutputItemsTrait(SyncActionContext context) {
        AutoOutputTrait trait = readAutoOutputTrait(context);
        if (trait == null || !trait.supportsAutoOutputItems()) {
            throw new IllegalStateException("Auto-output items action received an invalid holder or trait.");
        }
        return trait;
    }

    private static AutoOutputTrait requireAutoOutputFluidsTrait(SyncActionContext context) {
        AutoOutputTrait trait = readAutoOutputTrait(context);
        if (trait == null || !trait.supportsAutoOutputFluids()) {
            throw new IllegalStateException("Auto-output fluids action received an invalid holder or trait.");
        }
        return trait;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field, String failureMessage) {
        Boolean value = readBoolean(payload, field);
        if (value == null) {
            throw new IllegalStateException(failureMessage);
        }
        return value;
    }

    private static @Nullable Boolean readBoolean(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            return null;
        }
        return readBoolean(fields, field);
    }

    private static @Nullable Boolean readBoolean(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }
}
