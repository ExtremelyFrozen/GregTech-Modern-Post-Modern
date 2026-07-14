package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferActions;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.network.chat.Component;

import lombok.Getter;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Opening-scoped Pattern Buffer name editor that keeps draft text local until explicit confirmation.
 */
public final class MEPatternBufferNameEditorElement extends UIElement {

    private static final int WIDTH = 70;
    private static final int HEIGHT = 10;
    private static final int TEXT_WIDTH = 58;
    private static final int BUTTON_X = 60;

    private final Supplier<String> nameSupplier;
    private final MachineUIHolder holder;
    private final BiConsumer<MachineUIHolder, SyncActionData> actionSender;
    private final BooleanSupplier canSendAction;
    private final Function<String, SyncActionData> nameActionFactory;
    @Getter
    private final GTTextFieldElement textField;
    @Getter
    private final GTButtonElement toggleButton;
    @Getter
    private boolean editing;

    /**
     * Creates the legacy-sized rename control for one validated menu opening.
     */
    public MEPatternBufferNameEditorElement(int x, int y, Supplier<String> nameSupplier,
                                            MachineUIHolder holder,
                                            BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                            BooleanSupplier canSendAction) {
        this(x, y, nameSupplier, holder, actionSender, canSendAction,
                MEPatternBufferActions::createSetNameAction);
    }

    /**
     * Creates a rename control whose authoritative action is owned by the opening context.
     */
    public MEPatternBufferNameEditorElement(int x, int y, Supplier<String> nameSupplier,
                                            MachineUIHolder holder,
                                            BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                            BooleanSupplier canSendAction,
                                            Function<String, SyncActionData> nameActionFactory) {
        this.nameSupplier = nameSupplier;
        this.holder = holder;
        this.actionSender = actionSender;
        this.canSendAction = canSendAction;
        this.nameActionFactory = nameActionFactory;
        UITemplate.setLDLib2Bounds(this, x, y, WIDTH, HEIGHT);

        textField = new GTTextFieldElement(0, 0, TEXT_WIDTH, HEIGHT)
                .setAnyString()
                .setText(nameSupplier.get(), false);
        textField.setVisible(false);
        textField.setActive(false);
        addChild(textField);

        toggleButton = new GTButtonElement(BUTTON_X, 0, HEIGHT, HEIGHT,
                editTexture(), this::toggleEditing).noText();
        toggleButton.style(style -> style.tooltips(Component.translatable("gui.gtpm.rename.desc")));
        addChild(toggleButton);
    }

    @Override
    public void screenTick() {
        if (!editing) {
            textField.setText(nameSupplier.get(), false);
        }
        super.screenTick();
    }

    private void toggleEditing(UIEvent event) {
        if (!editing) {
            if (!canSendAction.getAsBoolean()) {
                return;
            }
            textField.setText(nameSupplier.get(), false);
            editing = true;
            textField.setVisible(true);
            textField.setActive(true);
            toggleButton.setButtonTexture(confirmTexture());
            return;
        }

        editing = false;
        textField.setVisible(false);
        textField.setActive(false);
        toggleButton.setButtonTexture(editTexture());
        if (canSendAction.getAsBoolean()) {
            actionSender.accept(holder, nameActionFactory.apply(textField.getText()));
        }
    }

    private static IGuiTexture editTexture() {
        return GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.text("\u270e"));
    }

    private static IGuiTexture confirmTexture() {
        return GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.text("\u2714"));
    }
}
