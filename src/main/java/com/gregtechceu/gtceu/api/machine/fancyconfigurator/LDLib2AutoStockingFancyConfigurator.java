package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfigurator;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.multiblock.AutoStockingPart;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 Fancy page for advanced auto-stocking item and fluid thresholds.
 *
 * <p>
 * The page updates managed machine fields and flushes their pending client-to-server changes.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class LDLib2AutoStockingFancyConfigurator implements LDLib2FancyConfigurator {

    private static final int CONFIGURATOR_WIDTH = 90;
    private static final int CONFIGURATOR_HEIGHT = 70;

    private final AutoStockingPart machine;
    private final MachineUIHolder holder;

    /**
     * Creates an advanced auto-stocking configurator bound to the opened machine holder.
     */
    public LDLib2AutoStockingFancyConfigurator(AutoStockingPart machine, MachineUIHolder holder) {
        this.machine = machine;
        this.holder = holder;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtpm.gui.adv_stocking_config.title");
    }

    @Override
    public IGuiTexture getIcon() {
        return GuiTextures.itemStack(GTItems.TOOL_DATA_STICK.asStack());
    }

    @Override
    public UIElement createLDLib2Configurator() {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, CONFIGURATOR_WIDTH, CONFIGURATOR_HEIGHT);

        String suffix = switch (machine.getStockingTarget()) {
            case ITEM -> "min_item_count";
            case FLUID -> "min_fluid_count";
        };
        root.addChild(new GTLabelElement(4, 2, 81, 10,
                "gtpm.gui.title.adv_stocking_config." + suffix, true));

        GTIntInputElement minStackSizeInput = new GTIntInputElement(4, 12, 81, 14, machine::getMinStackSize,
                this::setLDLib2MinStackSize);
        minStackSizeInput.setMin(1);
        minStackSizeInput.style(style -> style.tooltips(Component.translatable(
                "gtpm.gui.adv_stocking_config." + suffix)));
        root.addChild(minStackSizeInput);

        root.addChild(new GTLabelElement(4, 36, 81, 10,
                "gtpm.gui.title.adv_stocking_config.ticks_per_cycle", true));

        GTIntInputElement ticksPerCycleInput = new GTIntInputElement(4, 46, 81, 14, machine::getTicksPerCycle,
                this::setLDLib2TicksPerCycle);
        ticksPerCycleInput.setMin(ConfigHolder.INSTANCE.compat.ae2.updateIntervals);
        ticksPerCycleInput.style(style -> style.tooltips(Component.translatable(
                "gtpm.gui.adv_stocking_config.ticks_per_cycle")));
        root.addChild(ticksPerCycleInput);

        return root;
    }

    @Override
    public int getLDLib2ConfiguratorWidth() {
        return CONFIGURATOR_WIDTH;
    }

    @Override
    public int getLDLib2ConfiguratorHeight() {
        return CONFIGURATOR_HEIGHT;
    }

    public void setLDLib2MinStackSize(int value) {
        machine.setMinStackSize(value);
        var currentMachine = holder.getMachine();
        if (currentMachine != null && currentMachine.isRemote()) {
            currentMachine.sendServerSyncChanges();
        }
    }

    public void setLDLib2TicksPerCycle(int value) {
        machine.setTicksPerCycle(value);
        var currentMachine = holder.getMachine();
        if (currentMachine != null && currentMachine.isRemote()) {
            currentMachine.sendServerSyncChanges();
        }
    }
}
