package com.gregtechceu.gtceu.integration.ae2.gui.fancy;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.integration.ae2.machine.MEFluidConfigActions;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Holder-scoped LDLib2 toggle for the stocking hatch's automatic fluid configuration mode.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class LDLib2MEFluidAutoPullFancyConfigurator extends LDLib2FancyConfiguratorButton.Toggle {

    /**
     * Creates a toggle that sends the dedicated ME action only from the matching remote opening.
     */
    public LDLib2MEFluidAutoPullFancyConfigurator(MEStockingHatchPartMachine machine, MachineUIHolder holder) {
        super(GuiTextures.BUTTON_AUTO_PULL.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_AUTO_PULL.getSubTexture(0, 0.5, 1, 0.5),
                machine::isAutoPull, createClickHandler(machine, holder));
        if (holder.getMachine() != machine) {
            throw new IllegalArgumentException("Auto-pull configurator holder must resolve the stocking hatch.");
        }
        setTooltipsSupplier(pressed -> List.of(Component.translatable("gtpm.gui.me_bus.auto_pull_button")));
    }

    private static BiConsumer<UIEvent, Boolean> createClickHandler(MEStockingHatchPartMachine machine,
                                                                   MachineUIHolder holder) {
        return (event, pressed) -> {
            if (holder.getMachine() == machine && machine.isRemote()) {
                MachineUIHelper.sendAction(holder, MEFluidConfigActions.createSetAutoPullAction(pressed));
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        };
    }
}
