package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.integration.ae2.machine.AEItemConfigSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

/** Fixed 150x88 LDLib2 body for the ordinary and stocking ME item input bus variants. */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class AEItemConfigElement extends UIElement {

    private final Supplier<AEItemConfigSnapshot> snapshotSupplier;
    @Getter
    private final List<AEItemConfigSlotElement> slots;
    private final AEItemConfigAmountProjection amountProjection;
    private final AEItemConfigAmountEditorElement amountEditor;
    @Getter
    private int selectedSlot = -1;

    /** Builds the online label, sixteen two-layer columns, and ordinary-bus amount editor. */
    public AEItemConfigElement(Supplier<AEItemConfigSnapshot> snapshotSupplier,
                               Player player, MachineUIHolder holder,
                               BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                               BooleanSupplier canSendAction, Predicate<UIEvent> ctrlDown) {
        this.snapshotSupplier = snapshotSupplier;
        UITemplate.setLDLib2Bounds(this, 0, 0, 150, 88);
        addChild(createOnlineLabel());
        this.amountProjection = new AEItemConfigAmountProjection(snapshotSupplier, holder, actionSender,
                canSendAction);

        List<AEItemConfigSlotElement> createdSlots = new ArrayList<>(AEItemConfigSnapshot.SLOT_COUNT);
        for (int index = 0; index < AEItemConfigSnapshot.SLOT_COUNT; index++) {
            int x = 3 + index % 8 * 18;
            int y = 10 + index / 8 * 38;
            AEItemConfigSlotElement slot = new AEItemConfigSlotElement(index, x, y, snapshotSupplier,
                    player, holder, actionSender, canSendAction, ctrlDown,
                    amountProjection, this::selectSlot, this::clearSelection, this::getSelectedSlot);
            createdSlots.add(slot);
            addChild(slot);
        }
        this.slots = List.copyOf(createdSlots);
        this.amountEditor = new AEItemConfigAmountEditorElement(this::getSelectedSlot, amountProjection,
                canSendAction);
        addChild(amountEditor);
        addEventListener(UIEvents.MOUSE_DOWN, this::closeAmountEditorOutside, true);
    }

    @Override
    public void screenTick() {
        amountProjection.reconcile();
        AEItemConfigSnapshot snapshot = snapshotSupplier.get();
        if (snapshot.stocking() || snapshot.autoPull()) {
            clearSelection();
        }
        super.screenTick();
    }

    private GTLabelElement createOnlineLabel() {
        GTLabelElement label = new GTLabelElement(3, 0, 144, 10,
                Component.translatable(snapshotSupplier.get().online() ?
                        "gtpm.gui.me_network.online" : "gtpm.gui.me_network.offline")) {

            @Override
            public void screenTick() {
                setValue(Component.translatable(snapshotSupplier.get().online() ?
                        "gtpm.gui.me_network.online" : "gtpm.gui.me_network.offline"));
                super.screenTick();
            }
        }.setTextAlignHorizontal(Horizontal.LEFT)
                .setTextAlignVertical(Vertical.CENTER);
        label.setId("me_network_status");
        return label;
    }

    private void selectSlot(int selectedSlot) {
        this.selectedSlot = selectedSlot;
    }

    private void clearSelection() {
        selectedSlot = -1;
    }

    private void closeAmountEditorOutside(UIEvent event) {
        if (selectedSlot >= 0 && !amountEditor.isAncestorOf(event.target)) {
            clearSelection();
        }
    }
}
