package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.BatchModeMachine;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * LDLib2 Fancy configurator helper for multiblock batch mode.
 *
 * <p>
 * The button updates the client field and flushes GTM annotation sync.
 */
public final class LDLib2BatchModeFancyConfigurator {

    private LDLib2BatchModeFancyConfigurator() {}

    /**
     * Attaches the batch mode toggle when the machine definition uses the batch recipe modifier.
     */
    public static void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel,
                                           BatchModeMachine machine) {
        if (!machine.supportsBatchMode()) {
            return;
        }

        configuratorPanel.attachConfigurators(createBatchModeConfigurator(configuratorPanel, machine));
    }

    static LDLib2FancyConfiguratorButton.Toggle createBatchModeConfigurator(
                                                                            LDLib2ConfiguratorPanelElement configuratorPanel,
                                                                            BatchModeMachine machine) {
        return new LDLib2FancyConfiguratorButton.Toggle(
                GuiTextures.BUTTON_BATCH.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_BATCH.getSubTexture(0, 0.5, 1, 0.5),
                machine::isBatchEnabled,
                (event, pressed) -> {
                    var openedMachine = configuratorPanel.getHolder().getMachine();
                    if (openedMachine != null && openedMachine.isRemote()) {
                        machine.setBatchEnabled(pressed);
                        openedMachine.sendServerSyncChanges();
                        event.stopImmediatePropagation();
                        event.hasHandler = true;
                    }
                })
                .setTooltipsSupplier(pressed -> List.of(Component.translatable(
                        "gtpm.machine.batch_" + (pressed ? "enabled" : "disabled"))));
    }
}
