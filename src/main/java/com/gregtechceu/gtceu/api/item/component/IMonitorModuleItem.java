package com.gregtechceu.gtceu.api.item.component;

import com.gregtechceu.gtceu.api.placeholder.PlaceholderContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.client.renderer.monitor.IMonitorRenderer;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Defines the rendering, ticking, and opening-local configuration surface supplied by a Central Monitor module.
 */
public interface IMonitorModuleItem extends IItemComponent {

    /** Updates module-owned display data while the module is installed in a monitor group. */
    default void tick(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {}

    /** Updates module-owned display data when the module is evaluated as a placeholder input. */
    default void tickInPlaceholder(ItemStack stack, PlaceholderContext context) {}

    /** Creates the renderer used to draw this module's current display state. */
    IMonitorRenderer getRenderer(ItemStack stack);

    /**
     * Builds the LDLib2 configuration element shown for this monitor module in the current validated opening.
     *
     * @param actionSender opening-scoped GT action transport; module state changes must use this transport
     */
    UIElement createConfigurationElement(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group,
                                         Consumer<SyncActionData> actionSender);

    /** Returns the serialized monitor-module category used by existing module dispatch logic. */
    default String getType() {
        return "unknown";
    }
}
