package com.gregtechceu.gtceu.common.item.modules;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.item.component.IMonitorModuleItem;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.client.renderer.monitor.IMonitorRenderer;
import com.gregtechceu.gtceu.client.renderer.monitor.MonitorImageRenderer;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorImageModuleActions;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.function.Consumer;

public class ImageModuleBehaviour implements IMonitorModuleItem {

    @Override
    public IMonitorRenderer getRenderer(ItemStack stack) {
        return new MonitorImageRenderer(stack.getOrDefault(GTDataComponents.IMAGE_MODULE_URL, ""));
    }

    @Override
    public UIElement createConfigurationElement(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group,
                                                Consumer<SyncActionData> actionSender) {
        UIElement builder = new UIElement();
        UITemplate.setLDLib2Bounds(builder, 0, 0, 248, 42);

        GTTextFieldElement textField = new GTTextFieldElement(0, 0, 220, 12);
        textField.setAnyString();
        textField.setTextValidator(CentralMonitorImageModuleActions::isValidUrl);
        textField.setText(stack.getOrDefault(GTDataComponents.IMAGE_MODULE_URL, ""), false);
        ImageEditSession editSession = ImageEditSession.open(machine, group, stack);

        GTButtonElement saveButton = new GTButtonElement(228, 0, 20, 20,
                GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.BUTTON_CHECK),
                event -> {
                    if (!machine.isRemote()) return;
                    if (textField.isError()) {
                        GTCEu.LOGGER.error("Central Monitor image module URL exceeds the network limit: {} characters",
                                textField.getRawText().length());
                        return;
                    }

                    sendUrlChange(editSession, textField.getValue(), actionSender);
                });
        saveButton.noText();

        builder.addChildren(textField, saveButton);
        return builder;
    }

    private static void sendUrlChange(ImageEditSession editSession, String requestedUrl,
                                      Consumer<SyncActionData> actionSender) {
        if (!CentralMonitorImageModuleActions.isValidUrl(requestedUrl)) {
            GTCEu.LOGGER.error("Central Monitor image module URL exceeds the network limit: {} characters",
                    requestedUrl.length());
            return;
        }
        String expectedUrl = editSession.expectedModule.get(GTDataComponents.IMAGE_MODULE_URL);
        if (requestedUrl.equals(expectedUrl)) {
            return;
        }
        int nextSequence = Math.incrementExact(editSession.sequence);
        var action = CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                editSession.holderIncarnation,
                editSession.groupIdentity,
                editSession.moduleSlotIncarnation,
                editSession.expectedModule,
                requestedUrl,
                editSession.sequence);
        actionSender.accept(action);
        editSession.sequence = nextSequence;
    }

    private static final class ImageEditSession {

        private final UUID holderIncarnation;
        private final UUID groupIdentity;
        private final UUID moduleSlotIncarnation;
        private final ItemStack expectedModule;
        private int sequence;

        private ImageEditSession(UUID holderIncarnation, UUID groupIdentity, UUID moduleSlotIncarnation,
                                 ItemStack expectedModule) {
            this.holderIncarnation = holderIncarnation;
            this.groupIdentity = groupIdentity;
            this.moduleSlotIncarnation = moduleSlotIncarnation;
            this.expectedModule = expectedModule;
        }

        private static ImageEditSession open(CentralMonitorMachine machine, MonitorGroup group, ItemStack module) {
            return new ImageEditSession(
                    machine.getCentralMonitorActionIncarnation(),
                    group.getIdentity(),
                    group.getModuleSlotIncarnation(),
                    CentralMonitorImageModuleActions.captureExpectedModule(module));
        }
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
