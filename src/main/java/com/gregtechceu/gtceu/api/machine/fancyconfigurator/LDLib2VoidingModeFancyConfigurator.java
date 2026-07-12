package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * LDLib2 Fancy configurator helper for multiblock output voiding mode.
 *
 * <p>
 * The selector updates the client-side sync field and flushes it through the GTM machine sync channel.
 */
public final class LDLib2VoidingModeFancyConfigurator {

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
                    if (machine == controller.self() && machine.isRemote()) {
                        controller.setVoidingMode(mode);
                        machine.sendServerSyncChanges();
                        event.stopImmediatePropagation();
                        event.hasHandler = true;
                    }
                })
                .setTooltip(mode -> List.of(Component.translatable("gtpm.gui.multiblock.voiding_mode"),
                        Component.translatable(mode.getTooltip()))));
    }
}
