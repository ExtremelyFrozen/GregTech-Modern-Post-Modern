package com.gregtechceu.gtceu.api.item.component;

import com.gregtechceu.gtceu.api.placeholder.PlaceholderContext;
import com.gregtechceu.gtceu.client.renderer.monitor.IMonitorRenderer;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.item.ItemStack;

public interface IMonitorModuleItem extends IItemComponent {

    default void tick(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {}

    default void tickInPlaceholder(ItemStack stack, PlaceholderContext context) {}

    IMonitorRenderer getRenderer(ItemStack stack);

    Widget createUIWidget(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group);

    /**
     * Returns whether this monitor module can build an LDLib2 configuration element without the legacy widget API.
     */
    default boolean supportsLDLib2UIWidget(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
        return false;
    }

    /**
     * Builds the parallel LDLib2 configuration element for this monitor module.
     *
     * @throws UnsupportedOperationException when the module has not migrated its configuration element yet.
     */
    default UIElement createLDLib2UIWidget(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
        throw new UnsupportedOperationException("LDLib2 monitor module UI is not supported.");
    }

    default String getType() {
        return "unknown";
    }
}
