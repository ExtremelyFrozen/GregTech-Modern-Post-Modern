package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTPhantomItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.item.datacomponents.CreativeMachineInfo;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;

import java.util.List;

public class CreativeChestMachine extends QuantumChestMachine
                                  implements LDLib2MachineUIProvider, CreativeChestItemActionTarget {

    private static final int PAGE_WIDTH = 176;
    private static final int PAGE_HEIGHT = 131;
    static {
        CreativeChestMachineActions.initialize();
    }

    @Getter
    @SaveField
    @SyncBoth
    private int itemsPerCycle;
    @Getter
    @SaveField
    @SyncBoth
    private int ticksPerCycle = 1;

    public CreativeChestMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.MAX, -1);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) autoOutput.setTicksPerCycle(ticksPerCycle);
    }

    @Override
    protected ItemCache createCacheItemHandler() {
        return new InfiniteCache();
    }

    @Override
    public void setCreativeChestItem(ItemStack item) {
        stored = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        onItemChanged();
    }

    private void setTicksPerCycle(int ticksPerCycle) {
        int normalizedTicksPerCycle = normalizeTicksPerCycle(ticksPerCycle);
        this.ticksPerCycle = normalizedTicksPerCycle;
        autoOutput.setTicksPerCycle(normalizedTicksPerCycle);
        onItemChanged();
    }

    private void setItemsPerCycle(int itemsPerCycle) {
        this.itemsPerCycle = normalizeItemsPerCycle(itemsPerCycle);
        onItemChanged();
    }

    @ServerFieldNormalizer(fieldName = "itemsPerCycle")
    private int normalizeItemsPerCycle(int candidate) {
        if (candidate <= 0) {
            throw new IllegalArgumentException("Items per cycle must be positive: " + candidate);
        }
        return candidate;
    }

    @ServerFieldNormalizer(fieldName = "ticksPerCycle")
    private int normalizeTicksPerCycle(int candidate) {
        if (candidate <= 0) {
            throw new IllegalArgumentException("Ticks per cycle must be positive: " + candidate);
        }
        return candidate;
    }

    @ServerFieldChangeListener(fieldName = "itemsPerCycle")
    private void onItemsPerCycleChanged(int oldValue, int newValue) {
        onItemChanged();
    }

    @ServerFieldChangeListener(fieldName = "ticksPerCycle")
    private void onTicksPerCycleChanged(int oldValue, int newValue) {
        autoOutput.setTicksPerCycle(newValue);
        onItemChanged();
    }

    @Override
    public InteractionResult onUseWithItem(ExtendedUseOnContext context) {
        var heldItem = context.getItemInHand();
        var player = context.getPlayer();

        if (context.getClickedFace() != getFrontFacing() || isRemote()) {
            return InteractionResult.PASS;
        }
        // Clear item if empty hand + shift-rclick
        if (player.getItemInHand(context.getHand()).isEmpty() && player.isShiftKeyDown() && !stored.isEmpty()) {
            setCreativeChestItem(ItemStack.EMPTY);
            return InteractionResult.SUCCESS;
        }
        return super.onUseWithItem(context);
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        return UI.of(new LDLib2FancyMachineUIElement(new CreativeChestLDLib2Page(player, holder),
                player.getInventory(), holder, PAGE_WIDTH, PAGE_HEIGHT));
    }

    private UIElement createLDLib2MainPage(Player player, MachineUIHolder holder) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        root.addChild(createLDLib2StoredItemSlot(player, holder));
        root.addChild(createLDLib2Label(7, 9, 162, 10, "gtpm.creative.chest.item"));
        root.addChild(new GTImageElement(7, 48, 154, 14, GuiTextures.DISPLAY));
        root.addChild(createLDLib2ItemsPerCycleField());
        root.addChild(createLDLib2Label(7, 28, 162, 10, "gtpm.creative.chest.ipc"));
        root.addChild(new GTImageElement(7, 85, 154, 14, GuiTextures.DISPLAY));
        root.addChild(createLDLib2TicksPerCycleField());
        root.addChild(createLDLib2Label(7, 65, 162, 10, "gtpm.creative.chest.tpc"));
        root.addChild(createLDLib2ActivityButton());
        return root;
    }

    private GTPhantomItemSlotElement createLDLib2StoredItemSlot(Player player, MachineUIHolder holder) {
        GTPhantomItemSlotElement slot = new GTPhantomItemSlotElement(this::getStored,
                stack -> setLDLib2StoredItem(player, holder, stack), () -> 1);
        slot.setBackgroundTexture(GuiTextures.SLOT);
        UITemplate.setLDLib2Bounds(slot, 36, 6, 18, 18);
        slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (!player.level().isClientSide()) {
                return;
            }
            if (event.button == 0) {
                ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty()) {
                    slot.setItem(carried.copyWithCount(1));
                    event.stopImmediatePropagation();
                    event.hasHandler = true;
                }
            } else if (event.button == 1 && !stored.isEmpty()) {
                slot.setItem(ItemStack.EMPTY);
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        });
        return slot;
    }

    private GTLabelElement createLDLib2Label(int x, int y, int width, int height, String translationKey) {
        GTLabelElement label = new GTLabelElement(x, y, width, height, translationKey, true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    GTTextFieldElement createLDLib2ItemsPerCycleField() {
        GTTextFieldElement field = new GTTextFieldElement(9, 50, 152, 10) {

            @Override
            public void screenTick() {
                if (!isFocused()) {
                    setText(Integer.toString(itemsPerCycle), false);
                }
                super.screenTick();
            }
        };
        field.setNumbersOnlyInt(1, Integer.MAX_VALUE);
        field.setText(Integer.toString(itemsPerCycle), false);
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        field.setTextResponder(this::setLDLib2ItemsPerCycle);
        return field;
    }

    GTTextFieldElement createLDLib2TicksPerCycleField() {
        GTTextFieldElement field = new GTTextFieldElement(9, 87, 152, 10) {

            @Override
            public void screenTick() {
                if (!isFocused()) {
                    setText(Integer.toString(ticksPerCycle), false);
                }
                super.screenTick();
            }
        };
        field.setNumbersOnlyInt(1, Integer.MAX_VALUE);
        field.setText(Integer.toString(ticksPerCycle), false);
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        field.setTextResponder(this::setLDLib2TicksPerCycle);
        return field;
    }

    private GTButtonElement createLDLib2ActivityButton() {
        return new GTButtonElement(7, 101, 162, 20, createLDLib2ActivityButtonTexture(),
                event -> setLDLib2WorkingEnabled(!isWorkingEnabled())) {

            @Override
            public void screenTick() {
                setButtonTexture(createLDLib2ActivityButtonTexture());
                super.screenTick();
            }
        };
    }

    private IGuiTexture createLDLib2ActivityButtonTexture() {
        return GuiTextures.group(GuiTextures.BUTTON,
                GuiTextures.text(isWorkingEnabled() ? "gtpm.creative.activity.on" : "gtpm.creative.activity.off"));
    }

    LDLib2FancyConfiguratorButton.Toggle createLDLib2WorkingEnabledConfigurator() {
        return new LDLib2FancyConfiguratorButton.Toggle(
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0.5, 1, 0.5),
                this::isWorkingEnabled,
                (event, pressed) -> {
                    setLDLib2WorkingEnabled(pressed);
                    event.stopImmediatePropagation();
                    event.hasHandler = true;
                })
                .setTooltipsSupplier(pressed -> List.of(Component.translatable(
                        pressed ? "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled")));
    }

    private void setLDLib2StoredItem(Player player, MachineUIHolder holder, ItemStack item) {
        setCreativeChestItem(item);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, CreativeChestMachineActions.createSetItemAction(item));
        }
    }

    private void setLDLib2ItemsPerCycle(String value) {
        if (value.isEmpty()) {
            return;
        }
        int parsedValue = parsePositiveInteger(value, "creative chest items per cycle");
        setItemsPerCycle(parsedValue);
        if (isRemote()) {
            sendServerSyncChanges();
        }
    }

    private void setLDLib2TicksPerCycle(String value) {
        if (value.isEmpty()) {
            return;
        }
        int parsedValue = parsePositiveInteger(value, "creative chest ticks per cycle");
        setTicksPerCycle(parsedValue);
        if (isRemote()) {
            sendServerSyncChanges();
        }
    }

    private void setLDLib2WorkingEnabled(boolean workingEnabled) {
        setWorkingEnabled(workingEnabled);
        if (isRemote()) {
            sendServerSyncChanges();
        }
    }

    private static int parsePositiveInteger(String value, String fieldName) {
        try {
            int parsedValue = Integer.parseInt(value);
            if (parsedValue <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive: " + parsedValue);
            }
            return parsedValue;
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid {} input: {}", fieldName, value, e);
            throw e;
        }
    }

    private final class CreativeChestLDLib2Page implements LDLib2FancyUIProvider {

        private final Player player;
        private final MachineUIHolder holder;

        private CreativeChestLDLib2Page(Player player, MachineUIHolder holder) {
            this.player = player;
            this.holder = holder;
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            return CreativeChestMachine.this.createLDLib2MainPage(player, holder);
        }

        @Override
        public IGuiTexture getTabIcon() {
            return GuiTextures.itemStack(getDefinition().getItem());
        }

        @Override
        public Component getTitle() {
            return Component.translatable(getDefinition().getDescriptionId());
        }

        @Override
        public int getLDLib2PageWidth() {
            return PAGE_WIDTH;
        }

        @Override
        public int getLDLib2PageHeight() {
            return PAGE_HEIGHT;
        }

        @Override
        public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
            configuratorPanel.attachConfigurators(createLDLib2WorkingEnabledConfigurator());
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            tooltipsPanel.attachTooltips(CreativeChestMachine.this);
            getTraitHolder().getAllTraits().stream()
                    .filter(IFancyTooltip.class::isInstance)
                    .map(IFancyTooltip.class::cast)
                    .forEach(tooltipsPanel::attachTooltips);
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(Component.translatable(getDefinition().getDescriptionId()));
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        CreativeMachineInfo info = componentInput.get(GTDataComponents.CREATIVE_MACHINE_INFO);
        if (info != null) {
            itemsPerCycle = info.outputPerCycle();
            ticksPerCycle = info.ticksPerCycle();
        }
    }

    @Override
    public void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(GTDataComponents.CREATIVE_MACHINE_INFO, new CreativeMachineInfo(itemsPerCycle, ticksPerCycle));
    }

    private class InfiniteCache extends ItemCache {

        public InfiniteCache() {
            super();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stored;
        }

        @Override
        public void setStackInSlot(int index, ItemStack stack) {
            setCreativeChestItem(stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, stack)) return ItemStack.EMPTY;
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!stored.isEmpty()) return stored.copyWithCount(itemsPerCycle);
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    }
}
