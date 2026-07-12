package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.feature.AutoOutputMachine;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * LDLib2 Fancy configurator helper for common auto-output item and fluid toggles.
 *
 * <p>
 * The buttons update the client field and flush GTM annotation sync; they do not use LDLib2-owned business state.
 */
public final class LDLib2AutoOutputFancyConfigurator {

    private LDLib2AutoOutputFancyConfigurator() {}

    /**
     * Attaches supported auto-output toggles to a migrated LDLib2 Fancy configurator panel.
     */
    public static void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel,
                                           AutoOutputMachine autoOutputMachine) {
        if (autoOutputMachine.supportsAutoOutputFluids()) {
            configuratorPanel.attachConfigurators(createAutoOutputFluidConfigurator(configuratorPanel,
                    autoOutputMachine));
        }
        if (autoOutputMachine.supportsAutoOutputItems()) {
            configuratorPanel.attachConfigurators(createAutoOutputItemConfigurator(configuratorPanel,
                    autoOutputMachine));
        }
    }

    static LDLib2FancyConfiguratorButton.Toggle createAutoOutputFluidConfigurator(
                                                                                  LDLib2ConfiguratorPanelElement panel,
                                                                                  AutoOutputMachine autoOutputMachine) {
        return createAutoOutputConfigurator(
                GuiTextures.group(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0, 1, 0.5),
                        GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON.getSubTexture(0, 1 / 3f, 1, 1 / 3f)),
                GuiTextures.group(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0.5, 1, 0.5),
                        GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON.getSubTexture(0, 2 / 3f, 1, 1 / 3f)),
                autoOutputMachine::isAutoOutputFluids,
                "gtpm.gui.fluid_auto_output",
                (event, enabled) -> {
                    var machine = panel.getHolder().getMachine();
                    if (machine != null && machine.isRemote()) {
                        autoOutputMachine.setAllowAutoOutputFluids(enabled);
                        machine.sendServerSyncChanges();
                        event.stopImmediatePropagation();
                        event.hasHandler = true;
                    }
                });
    }

    static LDLib2FancyConfiguratorButton.Toggle createAutoOutputItemConfigurator(
                                                                                 LDLib2ConfiguratorPanelElement panel,
                                                                                 AutoOutputMachine autoOutputMachine) {
        return createAutoOutputConfigurator(
                GuiTextures.group(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0, 1, 0.5),
                        GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 1 / 3f, 1, 1 / 3f)),
                GuiTextures.group(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0.5, 1, 0.5),
                        GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 2 / 3f, 1, 1 / 3f)),
                autoOutputMachine::isAutoOutputItems,
                "gtpm.gui.item_auto_output",
                (event, enabled) -> {
                    var machine = panel.getHolder().getMachine();
                    if (machine != null && machine.isRemote()) {
                        autoOutputMachine.setAllowAutoOutputItems(enabled);
                        machine.sendServerSyncChanges();
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
}
