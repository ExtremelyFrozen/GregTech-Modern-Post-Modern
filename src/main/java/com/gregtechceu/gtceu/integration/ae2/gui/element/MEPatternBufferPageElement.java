package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Fixed-pixel LDLib2 body for one ME Pattern Buffer menu opening.
 */
public final class MEPatternBufferPageElement extends UIElement {

    public static final int WIDTH = 178;
    public static final int HEIGHT = 70;

    @Getter
    private final List<AEPatternViewSlotElement> patternSlots;
    @Getter
    private final GTLabelElement networkStatusLabel;
    @Getter
    private final MEPatternBufferNameEditorElement nameEditor;

    /**
     * Builds the 9x3 pattern grid and opening-local rename state around synchronized machine data.
     */
    public MEPatternBufferPageElement(MEPatternBufferPartMachine machine, Level level, MachineUIHolder holder,
                                      BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                      BooleanSupplier canSendAction) {
        if (holder.getMachine() != machine) {
            throw new IllegalArgumentException("Pattern Buffer page holder must resolve the opened machine.");
        }
        UITemplate.setLDLib2Bounds(this, 0, 0, WIDTH, HEIGHT);
        style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        int patternSlotCount = machine.getPatternInventory().getSlots();
        List<AEPatternViewSlotElement> slots = new ArrayList<>(patternSlotCount);
        for (int index = 0; index < patternSlotCount; index++) {
            int patternIndex = index;
            AEPatternViewSlotElement slot = new AEPatternViewSlotElement(
                    machine.getPatternInventory(), patternIndex, level, () -> machine.onPatternChange(patternIndex));
            UITemplate.setLDLib2Bounds(slot, 8 + index % 9 * 18, 14 + index / 9 * 18, 18, 18);
            slots.add(slot);
            addChild(slot);
        }
        patternSlots = List.copyOf(slots);

        networkStatusLabel = new GTLabelElement(8, 2, 90, 10,
                networkStatus(machine)) {

            @Override
            public void screenTick() {
                setValue(networkStatus(machine));
                super.screenTick();
            }
        };
        networkStatusLabel.setId("me_network_status");
        networkStatusLabel.setTextAlignHorizontal(Horizontal.LEFT)
                .setTextAlignVertical(Vertical.CENTER);
        addChild(networkStatusLabel);

        nameEditor = new MEPatternBufferNameEditorElement(100, 2, machine::getCustomName,
                holder, actionSender, canSendAction);
        addChild(nameEditor);
    }

    private static Component networkStatus(MEPatternBufferPartMachine machine) {
        return Component.translatable(machine.isOnline() ?
                "gtpm.gui.me_network.online" : "gtpm.gui.me_network.offline");
    }
}
