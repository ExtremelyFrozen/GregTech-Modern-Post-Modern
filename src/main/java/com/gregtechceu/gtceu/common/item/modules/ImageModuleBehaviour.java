package com.gregtechceu.gtceu.common.item.modules;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.item.component.IMonitorModuleItem;
import com.gregtechceu.gtceu.client.renderer.monitor.IMonitorRenderer;
import com.gregtechceu.gtceu.client.renderer.monitor.MonitorImageRenderer;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorImageModuleActions;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;
import com.gregtechceu.gtceu.common.network.packets.CPacketMachineActionToServer;

import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class ImageModuleBehaviour implements IMonitorModuleItem {

    @Override
    public IMonitorRenderer getRenderer(ItemStack stack) {
        return new MonitorImageRenderer(stack.getOrDefault(GTDataComponents.IMAGE_MODULE_URL, null));
    }

    @Override
    public Widget createUIWidget(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
        WidgetGroup builder = new WidgetGroup();
        TextFieldWidget textField = new TextFieldWidget(0, 0, 100, 10, null, null);
        textField.setCurrentString(stack.getOrDefault(GTDataComponents.IMAGE_MODULE_URL, ""));
        UUID openingGroupIdentity = group.getIdentity();
        UUID openingSlotIncarnation = group.getModuleSlotIncarnation();

        ButtonWidget saveButton = new ButtonWidget(-40, 22, 20, 20, click -> {
            if (!click.isRemote) return;

            sendUrlChange(machine, openingGroupIdentity, openingSlotIncarnation, textField.getCurrentString());
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
        textField.setTextValidator(CentralMonitorImageModuleActions::isValidUrl);
        textField.setText(stack.getOrDefault(GTDataComponents.IMAGE_MODULE_URL, ""), false);
        UUID openingGroupIdentity = group.getIdentity();
        UUID openingSlotIncarnation = group.getModuleSlotIncarnation();

        GTButtonElement saveButton = new GTButtonElement(-40, 22, 20, 20,
                GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.BUTTON_CHECK),
                event -> {
                    if (!machine.getLevel().isClientSide()) return;

                    sendUrlChange(machine, openingGroupIdentity, openingSlotIncarnation, textField.getValue());
                });
        saveButton.noText();

        builder.addChildren(textField, saveButton);
        return builder;
    }

    private static void sendUrlChange(CentralMonitorMachine machine, UUID openingGroupIdentity,
                                      UUID openingSlotIncarnation, String requestedUrl) {
        if (!CentralMonitorImageModuleActions.isValidUrl(requestedUrl)) {
            GTCEu.LOGGER.error("Central Monitor image module URL exceeds the network limit: {} characters",
                    requestedUrl.length());
            return;
        }
        ItemStack stack = machine.resolveCentralMonitorImageModuleForOpening(
                openingGroupIdentity, openingSlotIncarnation);
        if (stack == null) {
            GTCEu.LOGGER.warn(
                    "Central Monitor image module page no longer matches group {} slot incarnation {}",
                    openingGroupIdentity, openingSlotIncarnation);
            return;
        }
        String expectedUrl = stack.get(GTDataComponents.IMAGE_MODULE_URL);
        if (expectedUrl != null && expectedUrl.equals(requestedUrl)) {
            return;
        }
        var action = CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                machine.getCentralMonitorActionIncarnation(),
                openingGroupIdentity,
                openingSlotIncarnation,
                stack,
                requestedUrl,
                0);
        stack.set(GTDataComponents.IMAGE_MODULE_URL, requestedUrl);
        PacketDistributor.sendToServer(
                new CPacketMachineActionToServer(machine.getBlockPos(), machine.getDefinition().getId(), action));
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
