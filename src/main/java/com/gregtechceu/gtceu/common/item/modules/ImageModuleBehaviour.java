package com.gregtechceu.gtceu.common.item.modules;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.item.component.IMonitorModuleItem;
import com.gregtechceu.gtceu.client.renderer.monitor.IMonitorRenderer;
import com.gregtechceu.gtceu.client.renderer.monitor.MonitorImageRenderer;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;
import com.gregtechceu.gtceu.common.network.packets.SCPacketMonitorGroupDataChange;

import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ImageModuleBehaviour implements IMonitorModuleItem {

    @Override
    public IMonitorRenderer getRenderer(ItemStack stack) {
        return new MonitorImageRenderer(stack.getOrDefault(GTDataComponents.IMAGE_MODULE_URL, null));
    }

    @Override
    public Widget createUIWidget(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
        WidgetGroup builder = new WidgetGroup();
        TextFieldWidget textField = new TextFieldWidget(0, 0, 100, 10, null, null);
        textField.setCurrentString(stack.getOrDefault(GTDataComponents.IMAGE_MODULE_URL, null));

        ButtonWidget saveButton = new ButtonWidget(-40, 22, 20, 20, click -> {
            if (!click.isRemote) return;

            stack.set(GTDataComponents.IMAGE_MODULE_URL, textField.getCurrentString());
            PacketDistributor.sendToServer(new SCPacketMonitorGroupDataChange(stack, group, machine));
        });
        saveButton.setButtonTexture(GuiTextures.BUTTON_CHECK);
        builder.addWidget(textField);
        builder.addWidget(saveButton);
        return builder;
    }

    @Override
    public UIElement createLDLib2UIWidget(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
        UIElement builder = new UIElement();
        UITemplate.setLDLib2Bounds(builder, 0, 0, 100, 42);

        GTTextFieldElement textField = new GTTextFieldElement(0, 0, 100, 10);
        textField.setAnyString();
        textField.setText(stack.getOrDefault(GTDataComponents.IMAGE_MODULE_URL, ""), false);

        GTButtonElement saveButton = new GTButtonElement(-40, 22, 20, 20,
                GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.BUTTON_CHECK),
                event -> {
                    if (!machine.getLevel().isClientSide()) return;

                    stack.set(GTDataComponents.IMAGE_MODULE_URL, textField.getValue());
                    PacketDistributor.sendToServer(new SCPacketMonitorGroupDataChange(stack, group, machine));
                });
        saveButton.noText();

        builder.addChildren(textField, saveButton);
        return builder;
    }

    @Override
    public String getType() {
        return "image";
    }

    public String getUrl(ItemStack stack) {
        return stack.get(GTDataComponents.IMAGE_MODULE_URL);
    }

    public void setUrl(ItemStack stack, String url) {
        stack.set(GTDataComponents.IMAGE_MODULE_URL, url);
    }
}
