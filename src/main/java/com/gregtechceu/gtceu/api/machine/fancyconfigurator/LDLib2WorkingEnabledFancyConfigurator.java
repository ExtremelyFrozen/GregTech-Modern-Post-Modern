package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * LDLib2 Fancy toggle for the common machine working-enabled state.
 *
 * <p>
 * The button sends a GTM machine action for server-side mutation and does not use LDLib2-owned business state
 * channels.
 */
public class LDLib2WorkingEnabledFancyConfigurator extends LDLib2FancyConfiguratorButton.Toggle {

    static {
        LDLib2WorkingEnabledFancyConfiguratorActions.initialize();
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
                        MachineUIHelper.sendAction(holder,
                                LDLib2WorkingEnabledFancyConfiguratorActions
                                        .createSetWorkingEnabledAction(pressed));
                        event.stopImmediatePropagation();
                        event.hasHandler = true;
                    }
                });
        setTooltipsSupplier(pressed -> List.of(Component.translatable(
                pressed ? "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled")));
    }
}
