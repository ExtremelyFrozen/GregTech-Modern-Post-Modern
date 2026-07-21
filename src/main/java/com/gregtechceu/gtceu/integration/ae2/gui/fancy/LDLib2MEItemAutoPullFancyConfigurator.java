package com.gregtechceu.gtceu.integration.ae2.gui.fancy;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.integration.ae2.machine.MEItemConfigActions;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

/** Holder-scoped LDLib2 toggle for the stocking bus's automatic item configuration mode. */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class LDLib2MEItemAutoPullFancyConfigurator extends LDLib2FancyConfiguratorButton.Toggle {

    /** Creates a toggle that sends the dedicated ME action only from the matching remote opening. */
    public LDLib2MEItemAutoPullFancyConfigurator(MEStockingBusPartMachine machine, MachineUIHolder holder) {
        this(machine, holder, MachineUIHelper::sendAction, machine::isRemote);
    }

    /** Creates the same toggle with an explicit transport boundary for direct UI event verification. */
    @ApiStatus.Internal
    public LDLib2MEItemAutoPullFancyConfigurator(
                                                 MEStockingBusPartMachine machine,
                                                 MachineUIHolder holder,
                                                 BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                                 BooleanSupplier canSendAction) {
        super(GuiTextures.BUTTON_AUTO_PULL.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_AUTO_PULL.getSubTexture(0, 0.5, 1, 0.5),
                machine::isAutoPull,
                createClickHandler(machine, holder, actionSender, canSendAction));
        if (holder.getMachine() != machine) {
            throw new IllegalArgumentException("Auto-pull configurator holder must resolve the stocking bus.");
        }
        setTooltipsSupplier(pressed -> List.of(Component.translatable("gtpm.gui.me_bus.auto_pull_button")));
    }

    private static BiConsumer<UIEvent, Boolean> createClickHandler(
                                                                   MEStockingBusPartMachine machine,
                                                                   MachineUIHolder holder,
                                                                   BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                                                   BooleanSupplier canSendAction) {
        return (event, pressed) -> {
            if (holder.getMachine() == machine && canSendAction.getAsBoolean()) {
                actionSender.accept(holder, MEItemConfigActions.createSetAutoPullAction(pressed));
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        };
    }
}
