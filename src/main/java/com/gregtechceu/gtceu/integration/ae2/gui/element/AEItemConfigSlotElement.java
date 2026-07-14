package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTPhantomItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.integration.ae2.machine.AEItemConfigSnapshot;
import com.gregtechceu.gtceu.integration.ae2.machine.MEItemConfigActions;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import lombok.Getter;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

/** One 18x36 ME item configuration column with a phantom config slot above a read-only stock slot. */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class AEItemConfigSlotElement extends UIElement {

    @Getter
    private final int index;
    private final Supplier<AEItemConfigSnapshot> snapshotSupplier;
    private final Player player;
    private final MachineUIHolder holder;
    private final BiConsumer<MachineUIHolder, SyncActionData> actionSender;
    private final BooleanSupplier canSendAction;
    private final Predicate<UIEvent> ctrlDown;
    private final AEItemConfigAmountProjection amountProjection;
    private final IntConsumer selectSlot;
    private final Runnable clearSelection;
    private final ConfigItemElement configElement;
    private final StockItemElement stockElement;

    /** Creates one fixed grid column and binds every interaction to the opened machine holder. */
    AEItemConfigSlotElement(int index, int x, int y,
                            Supplier<AEItemConfigSnapshot> snapshotSupplier,
                            Player player, MachineUIHolder holder,
                            BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                            BooleanSupplier canSendAction, Predicate<UIEvent> ctrlDown,
                            AEItemConfigAmountProjection amountProjection,
                            IntConsumer selectSlot, Runnable clearSelection,
                            IntSupplier selectedSlotSupplier) {
        if (index < 0 || index >= AEItemConfigSnapshot.SLOT_COUNT) {
            throw new IllegalArgumentException("ME item configuration slot index is out of range: " + index);
        }
        this.index = index;
        this.snapshotSupplier = snapshotSupplier;
        this.player = player;
        this.holder = holder;
        this.actionSender = actionSender;
        this.canSendAction = canSendAction;
        this.ctrlDown = ctrlDown;
        this.amountProjection = amountProjection;
        this.selectSlot = selectSlot;
        this.clearSelection = clearSelection;
        UITemplate.setLDLib2Bounds(this, x, y, 18, 36);

        this.configElement = new ConfigItemElement();
        this.stockElement = new StockItemElement();
        UITemplate.setLDLib2Bounds(configElement, 0, 0, 18, 18);
        UITemplate.setLDLib2Bounds(stockElement, 0, 18, 18, 18);
        configElement.setId("me_item_config_" + index);
        stockElement.setId("me_item_stock_" + index);
        addChildren(configElement, stockElement);

        GTImageElement selection = new GTImageElement(0, 0, 18, 18, GuiTextures.SELECT_BOX)
                .setVisibleSupplier(() -> selectedSlotSupplier.getAsInt() == index);
        selection.setAllowHitTest(false);
        addChild(selection);
        addChild(createConfigAmountLabel());
        addChild(createStockAmountLabel());
    }

    /** Returns the upper phantom config element for direct event verification. */
    public GTPhantomItemSlotElement getConfigElement() {
        return configElement;
    }

    /** Returns the lower read-only stock element for direct event verification. */
    public GTItemSlotElement getStockElement() {
        return stockElement;
    }

    private GTLabelElement createConfigAmountLabel() {
        GTLabelElement label = new GTLabelElement(1, 7, 16, 10) {

            @Override
            public void screenTick() {
                GenericStack config = slotSnapshot().config();
                setValue(config == null || snapshotSupplier.get().stocking() ? Component.empty() :
                        Component.literal(FormattingUtil.formatNumberReadable(config.amount(), false)));
                super.screenTick();
            }
        }.setTextColor(-1)
                .setTextShadow(true)
                .setTextAlignHorizontal(Horizontal.RIGHT)
                .setTextAlignVertical(Vertical.BOTTOM)
                .setFontSize(4.5f);
        label.setVisibleSupplier(() -> !snapshotSupplier.get().stocking());
        label.setId("me_item_config_amount_" + index);
        label.setAllowHitTest(false);
        return label;
    }

    private GTLabelElement createStockAmountLabel() {
        GTLabelElement label = new GTLabelElement(1, 25, 16, 10) {

            @Override
            public void screenTick() {
                GenericStack stock = slotSnapshot().stock();
                setValue(stock == null ? Component.empty() : Component.literal(
                        FormattingUtil.formatNumberReadable(stock.amount(), false)));
                super.screenTick();
            }
        }.setTextColor(-1)
                .setTextShadow(true)
                .setTextAlignHorizontal(Horizontal.RIGHT)
                .setTextAlignVertical(Vertical.BOTTOM)
                .setFontSize(4.5f);
        label.setId("me_item_stock_amount_" + index);
        label.setAllowHitTest(false);
        return label;
    }

    private AEItemConfigSnapshot.Slot slotSnapshot() {
        return snapshotSupplier.get().slots().get(index);
    }

    private ItemStack displayConfigItem() {
        GenericStack config = slotSnapshot().config();
        return config == null ? ItemStack.EMPTY : displayItem(config, 1);
    }

    private ItemStack displayStockItem() {
        GenericStack stock = slotSnapshot().stock();
        return stock == null ? ItemStack.EMPTY : displayItem(stock, 1);
    }

    private ItemStack displayItem(GenericStack stack, int amount) {
        if (stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack(amount);
        }
        throw new IllegalStateException("ME item snapshot contained a non-item key.");
    }

    private ItemStack expectedStockItem() {
        GenericStack stock = slotSnapshot().stock();
        return stock == null ? ItemStack.EMPTY : displayItem(stock, 1);
    }

    private void sendSetConfig(ItemStack item) {
        if (!canSendAction.getAsBoolean() || snapshotSupplier.get().autoPull()) {
            return;
        }
        actionSender.accept(holder, MEItemConfigActions.createSetConfigAction(
                index, item.isEmpty() ? ItemStack.EMPTY : item.copy()));
    }

    private void handleConfigClick(UIEvent event) {
        if (!canSendAction.getAsBoolean() || snapshotSupplier.get().autoPull()) {
            return;
        }
        boolean handled = false;
        if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            sendSetConfig(ItemStack.EMPTY);
            clearSelection.run();
            handled = true;
        } else if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!snapshotSupplier.get().stocking()) {
                selectSlot.accept(index);
                handled = true;
            }
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                sendSetConfig(carried);
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
        ItemStack expectedItem = expectedStockItem();
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT || snapshotSupplier.get().stocking() ||
                expectedItem.isEmpty() || !canSendAction.getAsBoolean() ||
                !player.containerMenu.getCarried().isEmpty()) {
            return;
        }
        actionSender.accept(holder, MEItemConfigActions.createPickupStockAction(index, expectedItem));
        event.stopImmediatePropagation();
        event.hasHandler = true;
    }

    private final class ConfigItemElement extends GTPhantomItemSlotElement {

        private ConfigItemElement() {
            setItemSupplier(AEItemConfigSlotElement.this::displayConfigItem);
            setItemConsumer(AEItemConfigSlotElement.this::sendSetConfig);
            setMaxStackSizeSupplier(() -> Integer.MAX_VALUE);
            setCanPutItems(false);
            setCanTakeItems(false);
            setOnAddedTooltips((slot, tooltips) -> tooltips.addAll(configOperationTooltips()));
            addEventListener(UIEvents.MOUSE_DOWN, AEItemConfigSlotElement.this::handleConfigClick);
            addEventListener(UIEvents.MOUSE_WHEEL, AEItemConfigSlotElement.this::handleConfigWheel);
            refreshSnapshot();
        }

        @Override
        public void screenTick() {
            refreshSnapshot();
            super.screenTick();
        }

        @Override
        protected void onHoverTooltips(UIEvent event) {
            if (getValue().isEmpty()) {
                event.hoverTooltips = new HoverTooltips(configOperationTooltips(), null, null, null);
                return;
            }
            super.onHoverTooltips(event);
        }

        private List<Component> configOperationTooltips() {
            if (snapshotSupplier.get().autoPull()) {
                return List.of(
                        Component.translatable("gtpm.gui.config_slot"),
                        Component.translatable("gtpm.gui.config_slot.auto_pull_managed"));
            }
            if (snapshotSupplier.get().stocking()) {
                return List.of(
                        Component.translatable("gtpm.gui.config_slot"),
                        Component.translatable("gtpm.gui.config_slot.set_only"),
                        Component.translatable("gtpm.gui.config_slot.remove"));
            }
            return List.of(
                    Component.translatable("gtpm.gui.config_slot"),
                    Component.translatable("gtpm.gui.config_slot.set"),
                    Component.translatable("gtpm.gui.config_slot.scroll"),
                    Component.translatable("gtpm.gui.config_slot.remove"));
        }

        private void refreshSnapshot() {
            setBackgroundTexture(snapshotSupplier.get().autoPull() ?
                    GuiTextures.group(GuiTextures.SLOT_DARK, GuiTextures.CONFIG_ARROW_DARK) :
                    GuiTextures.group(GuiTextures.SLOT, GuiTextures.CONFIG_ARROW));
            setItem(displayConfigItem(), false);
        }
    }

    private final class StockItemElement extends GTItemSlotElement {

        private StockItemElement() {
            setBackgroundTexture(GuiTextures.SLOT_DARK);
            setCanPutItems(false);
            setCanTakeItems(false);
            setOnAddedTooltips((slot, tooltips) -> {
                GenericStack stock = slotSnapshot().stock();
                if (stock != null) {
                    tooltips.add(Component.literal(FormattingUtil.formatNumbers(stock.amount())));
                }
            });
            addEventListener(UIEvents.MOUSE_DOWN, AEItemConfigSlotElement.this::handleStockClick);
            refreshSnapshot();
        }

        @Override
        public void screenTick() {
            refreshSnapshot();
            super.screenTick();
        }

        private void refreshSnapshot() {
            setItem(displayStockItem(), false);
        }
    }
}
