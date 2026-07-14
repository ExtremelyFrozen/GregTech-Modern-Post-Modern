package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachineActions;
import com.gregtechceu.gtceu.core.mixins.FluidStackAccessor;
import com.gregtechceu.gtceu.integration.ae2.machine.AEFluidConfigSnapshot;
import com.gregtechceu.gtceu.integration.ae2.machine.MEFluidConfigActions;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTMath;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import lombok.Getter;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * One 18x36 ME fluid configuration column with a phantom config slot above a read-only stock slot.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class AEFluidConfigSlotElement extends UIElement {

    @Getter
    private final int index;
    private final Supplier<AEFluidConfigSnapshot> snapshotSupplier;
    private final Player player;
    private final MachineUIHolder holder;
    private final BiConsumer<MachineUIHolder, SyncActionData> actionSender;
    private final BooleanSupplier canSendAction;
    private final Predicate<UIEvent> ctrlDown;
    private final Predicate<UIEvent> shiftDown;
    private final AEFluidConfigAmountProjection amountProjection;
    private final IntConsumer selectSlot;
    private final Runnable clearSelection;
    private final ConfigFluidElement configElement;
    private final StockFluidElement stockElement;

    /**
     * Creates one fixed grid column and binds every interaction to the opened machine holder.
     */
    AEFluidConfigSlotElement(int index, int x, int y,
                             Supplier<AEFluidConfigSnapshot> snapshotSupplier,
                             Player player, MachineUIHolder holder,
                             BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                             BooleanSupplier canSendAction, Predicate<UIEvent> ctrlDown,
                             Predicate<UIEvent> shiftDown,
                             AEFluidConfigAmountProjection amountProjection,
                             IntConsumer selectSlot, Runnable clearSelection,
                             IntSupplier selectedSlotSupplier) {
        if (index < 0 || index >= AEFluidConfigSnapshot.SLOT_COUNT) {
            throw new IllegalArgumentException("ME fluid configuration slot index is out of range: " + index);
        }
        this.index = index;
        this.snapshotSupplier = snapshotSupplier;
        this.player = player;
        this.holder = holder;
        this.actionSender = actionSender;
        this.canSendAction = canSendAction;
        this.ctrlDown = ctrlDown;
        this.shiftDown = shiftDown;
        this.amountProjection = amountProjection;
        this.selectSlot = selectSlot;
        this.clearSelection = clearSelection;
        UITemplate.setLDLib2Bounds(this, x, y, 18, 36);

        this.configElement = new ConfigFluidElement();
        this.stockElement = new StockFluidElement();
        UITemplate.setLDLib2Bounds(configElement, 0, 0, 18, 18);
        UITemplate.setLDLib2Bounds(stockElement, 0, 18, 18, 18);
        configElement.setId("me_fluid_config_" + index);
        stockElement.setId("me_fluid_stock_" + index);
        addChildren(configElement, stockElement);

        GTImageElement selection = new GTImageElement(0, 0, 18, 18, GuiTextures.SELECT_BOX)
                .setVisibleSupplier(() -> selectedSlotSupplier.getAsInt() == index);
        selection.setAllowHitTest(false);
        addChild(selection);
        addChild(createStockAmountLabel());
    }

    /** Returns the upper phantom config element for direct event verification. */
    public GTFluidSlotElement getConfigElement() {
        return configElement;
    }

    /** Returns the lower read-only stock element for direct event verification. */
    public GTFluidSlotElement getStockElement() {
        return stockElement;
    }

    private GTLabelElement createStockAmountLabel() {
        GTLabelElement label = new GTLabelElement(1, 25, 16, 10) {

            @Override
            public void screenTick() {
                GenericStack stock = slotSnapshot().stock();
                setValue(stock == null ? Component.empty() : Component.literal(
                        FormattingUtil.formatNumberReadable(stock.amount(), true,
                                FormattingUtil.DECIMAL_FORMAT_0F, "B")));
                super.screenTick();
            }
        }.setTextColor(-1)
                .setTextShadow(true)
                .setTextAlignHorizontal(Horizontal.RIGHT)
                .setTextAlignVertical(Vertical.BOTTOM)
                .setFontSize(4.5f);
        label.setAllowHitTest(false);
        return label;
    }

    private AEFluidConfigSnapshot.Slot slotSnapshot() {
        return snapshotSupplier.get().slots().get(index);
    }

    private FluidStack displayFluid(GenericStack stack) {
        if (stack.what() instanceof AEFluidKey fluidKey) {
            return fluidKey.toStack(GTMath.saturatedCast(stack.amount()));
        }
        throw new IllegalStateException("ME fluid snapshot contained a non-fluid key.");
    }

    private void sendSetConfig(FluidStack fluid) {
        if (!canSendAction.getAsBoolean() || snapshotSupplier.get().autoPull()) {
            return;
        }
        FluidStack normalized = normalizeGhostFluid(fluid);
        actionSender.accept(holder, MEFluidConfigActions.createSetConfigAction(index, normalized));
    }

    private FluidStack normalizeGhostFluid(FluidStack fluid) {
        if (((FluidStackAccessor) (Object) fluid).getRawFluid() != Fluids.EMPTY && fluid.getAmount() <= 0) {
            FluidStack normalized = fluid.copy();
            normalized.setAmount(1_000);
            return normalized;
        }
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
    }

    private void handleConfigClick(UIEvent event) {
        if (!canSendAction.getAsBoolean() || snapshotSupplier.get().autoPull()) {
            return;
        }
        boolean handled = false;
        if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            sendSetConfig(FluidStack.EMPTY);
            clearSelection.run();
            handled = true;
        } else if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!snapshotSupplier.get().stocking()) {
                selectSlot.accept(index);
                handled = true;
            }
            var carriedFluid = FluidUtil.getFluidContained(player.containerMenu.getCarried());
            if (carriedFluid.isPresent()) {
                sendSetConfig(carriedFluid.get());
                handled = true;
            }
        }
        if (handled) {
            event.stopImmediatePropagation();
            event.hasHandler = true;
        }
    }

    private void handleConfigWheel(UIEvent event) {
        var projectedAmount = amountProjection.projectedAmount(index);
        if (projectedAmount.isEmpty() || event.deltaY == 0) {
            return;
        }
        long currentAmount = projectedAmount.getAsInt();
        long amount;
        if (ctrlDown.test(event)) {
            amount = event.deltaY > 0 ? currentAmount * 2 : currentAmount / 2;
        } else {
            amount = event.deltaY > 0 ? currentAmount + 1 : currentAmount - 1;
        }
        if (amount <= 0 || amount > Integer.MAX_VALUE ||
                !amountProjection.requestAmount(index, (int) amount)) {
            return;
        }
        event.stopImmediatePropagation();
        event.hasHandler = true;
    }

    private void handleStockClick(UIEvent event) {
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT || snapshotSupplier.get().stocking() ||
                slotSnapshot().stock() == null || !canSendAction.getAsBoolean() ||
                FluidUtil.getFluidHandler(player.containerMenu.getCarried()).isEmpty()) {
            return;
        }
        actionSender.accept(holder, FluidHatchPartMachineActions.createClickFluidSlotAction(
                index, shiftDown.test(event)));
        event.stopImmediatePropagation();
        event.hasHandler = true;
    }

    private final class ConfigFluidElement extends GTFluidSlotElement {

        private boolean refreshing;

        private ConfigFluidElement() {
            setAllowClickFilled(false);
            setAllowClickDrained(false);
            setOnAddedTooltips((slot, tooltips) -> {
                tooltips.add(Component.translatable("gtpm.gui.config_slot"));
                if (snapshotSupplier.get().autoPull()) {
                    tooltips.add(Component.translatable("gtpm.gui.config_slot.auto_pull_managed"));
                } else if (snapshotSupplier.get().stocking()) {
                    tooltips.add(Component.translatable("gtpm.gui.config_slot.set_only"));
                    tooltips.add(Component.translatable("gtpm.gui.config_slot.remove"));
                } else {
                    tooltips.add(Component.translatable("gtpm.gui.config_slot.set"));
                    tooltips.add(Component.translatable("gtpm.gui.config_slot.scroll"));
                    tooltips.add(Component.translatable("gtpm.gui.config_slot.remove"));
                }
            });
            addEventListener(UIEvents.MOUSE_DOWN, AEFluidConfigSlotElement.this::handleConfigClick);
            addEventListener(UIEvents.MOUSE_WHEEL, AEFluidConfigSlotElement.this::handleConfigWheel);
            xeiPhantom();
            refreshSnapshot();
        }

        @Override
        public ConfigFluidElement setFluid(FluidStack fluid) {
            if (refreshing) {
                super.setFluid(fluid);
            } else {
                sendSetConfig(fluid);
            }
            return this;
        }

        @Override
        public void screenTick() {
            refreshSnapshot();
            super.screenTick();
        }

        private void refreshSnapshot() {
            AEFluidConfigSnapshot snapshot = snapshotSupplier.get();
            GenericStack config = slotSnapshot().config();
            setBackgroundTexture(snapshot.autoPull() ?
                    GuiTextures.group(GuiTextures.SLOT_DARK, GuiTextures.CONFIG_ARROW_DARK) :
                    GuiTextures.group(GuiTextures.FLUID_SLOT, GuiTextures.CONFIG_ARROW));
            setShowAmount(!snapshot.stocking());
            refreshing = true;
            super.setFluid(config == null ? FluidStack.EMPTY : displayFluid(config));
            refreshing = false;
        }
    }

    private final class StockFluidElement extends GTFluidSlotElement {

        private StockFluidElement() {
            setBackgroundTexture(GuiTextures.SLOT_DARK);
            setAllowClickFilled(false);
            setAllowClickDrained(false);
            setOnAddedTooltips((slot, tooltips) -> {
                GenericStack stock = slotSnapshot().stock();
                if (stock != null) {
                    tooltips.add(Component.literal(FormattingUtil.formatNumbers(stock.amount()) + " B"));
                }
            });
            addEventListener(UIEvents.MOUSE_DOWN, AEFluidConfigSlotElement.this::handleStockClick);
            refreshSnapshot();
        }

        @Override
        public void screenTick() {
            refreshSnapshot();
            super.screenTick();
        }

        private void refreshSnapshot() {
            GenericStack stock = slotSnapshot().stock();
            setFluid(stock == null ? FluidStack.EMPTY : displayFluid(stock));
        }
    }
}
