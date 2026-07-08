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
import com.gregtechceu.gtceu.api.gui.texture.ResourceBorderTexture;
import com.gregtechceu.gtceu.api.item.datacomponents.CreativeMachineInfo;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CreativeChestMachine extends QuantumChestMachine implements LDLib2MachineUIProvider {

    private static final int PAGE_WIDTH = 176;
    private static final int PAGE_HEIGHT = 131;
    private static final ResourceLocation SET_CREATIVE_CHEST_ITEM_ACTION = GTCEu.id("set_creative_chest_item");
    private static final ResourceLocation SET_CREATIVE_CHEST_ITEMS_PER_CYCLE_ACTION = GTCEu
            .id("set_creative_chest_items_per_cycle");
    private static final ResourceLocation SET_CREATIVE_CHEST_TICKS_PER_CYCLE_ACTION = GTCEu
            .id("set_creative_chest_ticks_per_cycle");
    private static final ResourceLocation SET_CREATIVE_CHEST_WORKING_ENABLED_ACTION = GTCEu
            .id("set_creative_chest_working_enabled");
    private static final ResourceLocation ITEMS_PER_CYCLE_FIELD = SyncFieldData.key("itemsPerCycle");
    private static final ResourceLocation TICKS_PER_CYCLE_FIELD = SyncFieldData.key("ticksPerCycle");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");

    static {
        SyncActionDispatchers.server().register(new CreativeChestItemActionHandler());
        SyncActionDispatchers.server().register(new CreativeChestItemsPerCycleActionHandler());
        SyncActionDispatchers.server().register(new CreativeChestTicksPerCycleActionHandler());
        SyncActionDispatchers.server().register(new CreativeChestWorkingEnabledActionHandler());
    }

    @Getter
    @SaveField
    @SyncToClient
    private int itemsPerCycle;
    @Getter
    @SaveField
    @SyncToClient
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

    private void updateStored(ItemStack item) {
        stored = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        onItemChanged();
    }

    private void setTicksPerCycle(int ticksPerCycle) {
        if (ticksPerCycle <= 0) {
            throw new IllegalArgumentException("Ticks per cycle must be positive: " + ticksPerCycle);
        }
        this.ticksPerCycle = ticksPerCycle;
        autoOutput.setTicksPerCycle(ticksPerCycle);
        syncDataHolder.markClientSyncFieldDirty("ticksPerCycle");
        onItemChanged();
    }

    private void setItemsPerCycle(int itemsPerCycle) {
        if (itemsPerCycle <= 0) {
            throw new IllegalArgumentException("Items per cycle must be positive: " + itemsPerCycle);
        }
        this.itemsPerCycle = itemsPerCycle;
        syncDataHolder.markClientSyncFieldDirty("itemsPerCycle");
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
            updateStored(ItemStack.EMPTY);
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
        root.addChild(createLDLib2ItemsPerCycleField(player, holder));
        root.addChild(createLDLib2Label(7, 28, 162, 10, "gtpm.creative.chest.ipc"));
        root.addChild(new GTImageElement(7, 85, 154, 14, GuiTextures.DISPLAY));
        root.addChild(createLDLib2TicksPerCycleField(player, holder));
        root.addChild(createLDLib2Label(7, 65, 162, 10, "gtpm.creative.chest.tpc"));
        root.addChild(createLDLib2ActivityButton(player, holder));
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

    private GTTextFieldElement createLDLib2ItemsPerCycleField(Player player, MachineUIHolder holder) {
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
        field.setTextResponder(value -> setLDLib2ItemsPerCycle(player, holder, value));
        return field;
    }

    private GTTextFieldElement createLDLib2TicksPerCycleField(Player player, MachineUIHolder holder) {
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
        field.setTextResponder(value -> setLDLib2TicksPerCycle(player, holder, value));
        return field;
    }

    private GTButtonElement createLDLib2ActivityButton(Player player, MachineUIHolder holder) {
        return new GTButtonElement(7, 101, 162, 20, createLDLib2ActivityButtonTexture(),
                event -> setLDLib2WorkingEnabled(player, holder, !isWorkingEnabled())) {

            @Override
            public void screenTick() {
                setButtonTexture(createLDLib2ActivityButtonTexture());
                super.screenTick();
            }
        };
    }

    private IGuiTexture createLDLib2ActivityButtonTexture() {
        return GuiTextures.group(ResourceBorderTexture.BUTTON_COMMON,
                GuiTextures.text(isWorkingEnabled() ? "gtpm.creative.activity.on" : "gtpm.creative.activity.off"));
    }

    private LDLib2FancyConfiguratorButton.Toggle createLDLib2WorkingEnabledConfigurator(Player player,
                                                                                        MachineUIHolder holder) {
        return new LDLib2FancyConfiguratorButton.Toggle(
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0.5, 1, 0.5),
                this::isWorkingEnabled,
                (event, pressed) -> {
                    setLDLib2WorkingEnabled(player, holder, pressed);
                    event.stopImmediatePropagation();
                    event.hasHandler = true;
                })
                .setTooltipsSupplier(pressed -> List.of(Component.translatable(
                        pressed ? "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled")));
    }

    private void setLDLib2StoredItem(Player player, MachineUIHolder holder, ItemStack item) {
        updateStored(item);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeChestItemAction(item));
        }
    }

    private void setLDLib2ItemsPerCycle(Player player, MachineUIHolder holder, String value) {
        if (value.isEmpty()) {
            return;
        }
        int parsedValue = parsePositiveInteger(value, "creative chest items per cycle");
        setItemsPerCycle(parsedValue);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeChestItemsPerCycleAction(parsedValue));
        }
    }

    private void setLDLib2TicksPerCycle(Player player, MachineUIHolder holder, String value) {
        if (value.isEmpty()) {
            return;
        }
        int parsedValue = parsePositiveInteger(value, "creative chest ticks per cycle");
        setTicksPerCycle(parsedValue);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeChestTicksPerCycleAction(parsedValue));
        }
    }

    private void setLDLib2WorkingEnabled(Player player, MachineUIHolder holder, boolean workingEnabled) {
        setWorkingEnabled(workingEnabled);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeChestWorkingEnabledAction(workingEnabled));
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

    private static SyncActionData createSetCreativeChestItemAction(ItemStack item) {
        ItemStack storedItem = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), storedItem)
                .build();
        return new SyncActionData(SET_CREATIVE_CHEST_ITEM_ACTION,
                ItemStack.hashItemAndComponents(storedItem), payload);
    }

    private static SyncActionData createSetCreativeChestItemsPerCycleAction(int itemsPerCycle) {
        return createSetCreativeChestIntAction(SET_CREATIVE_CHEST_ITEMS_PER_CYCLE_ACTION, ITEMS_PER_CYCLE_FIELD,
                itemsPerCycle);
    }

    private static SyncActionData createSetCreativeChestTicksPerCycleAction(int ticksPerCycle) {
        return createSetCreativeChestIntAction(SET_CREATIVE_CHEST_TICKS_PER_CYCLE_ACTION, TICKS_PER_CYCLE_FIELD,
                ticksPerCycle);
    }

    private static SyncActionData createSetCreativeChestWorkingEnabledAction(boolean workingEnabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(workingEnabled))
                        .build())
                .build();
        return new SyncActionData(SET_CREATIVE_CHEST_WORKING_ENABLED_ACTION, workingEnabled ? 1 : 0, payload);
    }

    private static SyncActionData createSetCreativeChestIntAction(ResourceLocation actionId, ResourceLocation field,
                                                                  int value) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, new JsonPrimitive(value))
                        .build())
                .build();
        return new SyncActionData(actionId, value, payload);
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
            configuratorPanel.attachConfigurators(createLDLib2WorkingEnabledConfigurator(player, holder));
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

    private abstract static class CreativeChestActionHandler implements SyncActionHandler {

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof CreativeChestMachine;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        protected CreativeChestMachine getMachine(SyncActionContext context) {
            if (!(context.holder() instanceof CreativeChestMachine machine)) {
                throw new IllegalStateException("Creative chest action received a non-creative-chest machine.");
            }
            return machine;
        }
    }

    private static final class CreativeChestItemActionHandler extends CreativeChestActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_CHEST_ITEM_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            return payload.has(GTDataComponents.PLACEHOLDER_ITEM_STACK.get());
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).updateStored(requireItemStack(context.payload()));
        }
    }

    private static final class CreativeChestItemsPerCycleActionHandler extends CreativeChestActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_CHEST_ITEMS_PER_CYCLE_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readPositiveInteger(fields, ITEMS_PER_CYCLE_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setItemsPerCycle(requirePositiveInteger(context.payload(), ITEMS_PER_CYCLE_FIELD));
        }
    }

    private static final class CreativeChestTicksPerCycleActionHandler extends CreativeChestActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_CHEST_TICKS_PER_CYCLE_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readPositiveInteger(fields, TICKS_PER_CYCLE_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setTicksPerCycle(requirePositiveInteger(context.payload(), TICKS_PER_CYCLE_FIELD));
        }
    }

    private static final class CreativeChestWorkingEnabledActionHandler extends CreativeChestActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_CHEST_WORKING_ENABLED_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, WORKING_ENABLED_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setWorkingEnabled(requireBoolean(context.payload(), WORKING_ENABLED_FIELD));
        }
    }

    private static SyncFieldData requireFieldData(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Creative chest action payload is missing field data.");
        }
        return fields;
    }

    private static ItemStack requireItemStack(DataComponentMap payload) {
        if (!payload.has(GTDataComponents.PLACEHOLDER_ITEM_STACK.get())) {
            throw new IllegalStateException("Creative chest item action payload is missing item stack.");
        }
        return payload.getOrDefault(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), ItemStack.EMPTY);
    }

    private static int requirePositiveInteger(DataComponentMap payload, ResourceLocation field) {
        Integer value = readPositiveInteger(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Creative chest action payload is missing " + field + ".");
        }
        return value;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        Boolean value = readBoolean(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Creative chest action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Integer readPositiveInteger(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            long value = primitive.getAsLong();
            if (value > 0L && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        }
        return null;
    }

    private static @Nullable Boolean readBoolean(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
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
            updateStored(stack);
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
