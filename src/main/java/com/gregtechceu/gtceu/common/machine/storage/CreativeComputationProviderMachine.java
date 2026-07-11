package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.computation.ComputationProducer;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.trait.DirectComputationPortTrait;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class CreativeComputationProviderMachine extends MetaMachine
                                                implements LDLib2MachineUIProvider, IOpticalComputationProvider,
                                                ComputationProducer {

    @SaveField
    @SyncBoth
    private int maxCWUt;
    @SyncToClient
    private int lastRequestedCWUt;
    private int requestedCWUPerSec;
    @SaveField
    @SyncBoth
    @Getter
    private boolean active;
    @Nullable
    private TickableSubscription computationSubs;

    public CreativeComputationProviderMachine(BlockEntityCreationInfo info) {
        super(info);
        new DirectComputationPortTrait(this, true, this, null);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateComputationSubscription();
    }

    protected void updateComputationSubscription() {
        if (active) {
            this.computationSubs = subscribeServerTick(this::updateComputationTick);
        } else if (computationSubs != null) {
            computationSubs.unsubscribe();
            this.computationSubs = null;
            setLastRequestedCWUt(0);
            this.requestedCWUPerSec = 0;
        }
    }

    protected void updateComputationTick() {
        if (getOffsetTimer() % 20 == 0) {
            setLastRequestedCWUt(requestedCWUPerSec / 20);
            this.requestedCWUPerSec = 0;
        }
    }

    @Override
    public int requestCWUt(
                           int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        int requestedCWUt = active ? Math.min(cwut, maxCWUt) : 0;
        if (!simulate) {
            this.requestedCWUPerSec += requestedCWUt;
        }
        return requestedCWUt;
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return active ? maxCWUt : 0;
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return true;
    }

    @Override
    public int getOfferedCWUt() {
        return active ? maxCWUt : 0;
    }

    @Override
    public void applyProducedCWUt(int allocatedCWUt) {
        this.requestedCWUPerSec += allocatedCWUt;
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        updateComputationSubscription();
    }

    @ServerFieldChangeListener(fieldName = "active")
    private void onActiveChanged(boolean oldActive, boolean newActive) {
        updateComputationSubscription();
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 140, 95);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));
        root.addChild(createLDLib2Label(7, 7, 126, 10, Component.literal("CWUt")));
        root.addChild(createLDLib2MaxCWUtField());
        root.addChild(createLDLib2Label(7, 42, 126, 10,
                Component.translatable("gtpm.creative.computation.average")));
        root.addChild(createLDLib2LastRequestedCWUtLabel());
        root.addChild(createLDLib2ActivityButton());
        return UI.of(root);
    }

    private GTLabelElement createLDLib2Label(int x, int y, int width, int height, Component text) {
        GTLabelElement label = new GTLabelElement(x, y, width, height, text);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTTextFieldElement createLDLib2MaxCWUtField() {
        GTTextFieldElement field = new GTTextFieldElement(9, 20, 122, 16) {

            @Override
            public void screenTick() {
                if (!isFocused()) {
                    setText(Integer.toString(maxCWUt), false);
                }
                super.screenTick();
            }
        };
        field.setNumbersOnlyInt(0, Integer.MAX_VALUE);
        field.setText(Integer.toString(maxCWUt), false);
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        field.setTextResponder(this::setLDLib2MaxCWUt);
        return field;
    }

    private GTLabelElement createLDLib2LastRequestedCWUtLabel() {
        GTLabelElement label = new GTLabelElement(7, 54, 126, 10) {

            @Override
            public void screenTick() {
                setValue(Component.literal(Integer.toString(lastRequestedCWUt)));
                super.screenTick();
            }
        };
        label.setValue(Component.literal(Integer.toString(lastRequestedCWUt)));
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTButtonElement createLDLib2ActivityButton() {
        return new GTButtonElement(9, 66, 122, 20, createLDLib2ActivityButtonTexture(),
                event -> setLDLib2Active(!isActive())) {

            @Override
            public void screenTick() {
                setButtonTexture(createLDLib2ActivityButtonTexture());
                super.screenTick();
            }
        };
    }

    private IGuiTexture createLDLib2ActivityButtonTexture() {
        return GuiTextures.group(GuiTextures.BUTTON,
                GuiTextures.text(active ? "gtpm.creative.activity.on" : "gtpm.creative.activity.off"));
    }

    private void setLDLib2MaxCWUt(String value) {
        if (value.isEmpty()) {
            return;
        }
        int parsedValue;
        try {
            parsedValue = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid creative computation max CWUt input: {}", value, e);
            throw e;
        }
        setMaxCWUt(parsedValue);
        sendServerSyncChanges();
    }

    private void setLDLib2Active(boolean active) {
        setActive(active);
        sendServerSyncChanges();
    }

    private void setMaxCWUt(int maxCWUt) {
        this.maxCWUt = normalizeMaxCWUt(maxCWUt);
    }

    @ServerFieldNormalizer(fieldName = "maxCWUt")
    private int normalizeMaxCWUt(int maxCWUt) {
        if (maxCWUt < 0) {
            throw new IllegalArgumentException("Creative computation max CWUt cannot be negative.");
        }
        return maxCWUt;
    }

    private void setLastRequestedCWUt(int lastRequestedCWUt) {
        this.lastRequestedCWUt = lastRequestedCWUt;
        syncDataHolder.markClientSyncFieldDirty("lastRequestedCWUt");
    }
}
