package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTPhantomItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
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
import com.gregtechceu.gtceu.api.item.datacomponents.LargeItemContent;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.GridHighlightTexture;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTraitType;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTMath;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class QuantumChestMachine extends TieredMachine implements IControllable,
                                 LDLib2MachineUIProvider, QuantumChestActionTarget {

    /**
     * Sourced from FunctionalStorage's
     * <a
     * href=https://github.com/Buuz135/FunctionalStorage/blob/1.21/src/main/java/com/buuz135/functionalstorage/block/tile/ItemControllableDrawerTile.java>
     * ItemControllerDrawerTile</a>
     */
    public static final Object2LongOpenHashMap<UUID> INTERACTION_LOGGER = new Object2LongOpenHashMap<>();

    private static final int PAGE_WIDTH = 109;
    private static final int PAGE_HEIGHT = 63;

    static {
        QuantumChestMachineActions.initialize();
    }

    @SaveField
    @SyncBoth
    private boolean isVoiding;

    private final long maxAmount;
    protected final ItemCache cache;
    @SyncToClient
    @SaveField
    private final CustomItemStackHandler lockedItem;

    @Getter
    @SyncToClient
    @SaveField
    protected ItemStack stored = ItemStack.EMPTY;
    @Getter
    @SyncToClient
    @SaveField
    protected long storedAmount = 0;

    @SaveField
    @SyncToClient
    public final AutoOutputTrait autoOutput;

    public QuantumChestMachine(BlockEntityCreationInfo info, int tier, long maxAmount) {
        super(info, tier);
        this.maxAmount = maxAmount;
        this.cache = attachTrait(createCacheItemHandler());
        this.lockedItem = new CustomItemStackHandler();
        this.autoOutput = attachTrait(AutoOutputTrait.ofItems(cache));
        lockedItem.setOnContentsChanged(() -> syncDataHolder.markClientSyncFieldDirty("lockedItem"));
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected ItemCache createCacheItemHandler() {
        return new ItemCache();
    }

    protected void onItemChanged() {
        if (!isRemote()) {
            syncDataHolder.markClientSyncFieldDirty("storedAmount");
            syncDataHolder.markClientSyncFieldDirty("stored");
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        LargeItemContent storage = componentInput.getOrDefault(GTDataComponents.LARGE_ITEM_CONTENT,
                LargeItemContent.EMPTY);
        stored = storage.stored();
        storedAmount = storage.amount();
    }

    @Override
    public void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!stored.isEmpty())
            components.set(GTDataComponents.LARGE_ITEM_CONTENT, new LargeItemContent(stored, storedAmount));
    }

    //////////////////////////////////////
    // ****** Capability ********//
    //////////////////////////////////////

    @Override
    public @Nullable IItemHandlerModifiable getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        if (side == getFrontFacing()) {
            return null;
        }
        return super.getItemHandlerCap(side, useCoverCapability);
    }

    @Override
    public @Nullable IFluidHandlerModifiable getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        if (side == getFrontFacing()) {
            return null;
        }
        return super.getFluidHandlerCap(side, useCoverCapability);
    }

    //////////////////////////////////////
    // ******* Auto Output *******//
    //////////////////////////////////////

    @Override
    public boolean isWorkingEnabled() {
        return autoOutput.isAutoOutputItems();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        autoOutput.setAllowAutoOutputItems(isWorkingAllowed);
    }

    //////////////////////////////////////
    // ******* Interaction *******//
    //////////////////////////////////////

    @Override
    public InteractionResult onUseWithItem(ExtendedUseOnContext context) {
        if (context.getClickedFace() == getFrontFacing() && !isRemote()) {
            var hit = context.getHitResult();

            var aabb = new AABB(hit.getBlockPos()).deflate(0.12);
            var hitVector = hit.getLocation().relative(getFrontFacing(), -0.5);
            if (!aabb.contains(hitVector)) return InteractionResult.PASS;

            var held = context.getItemInHand();
            var player = context.getPlayer();

            if (cache.canInsert(held)) { // push
                var remaining = cache.insertItem(0, held, false);
                player.setItemInHand(InteractionHand.MAIN_HAND, remaining);
                return InteractionResult.SUCCESS;
            } else if (isDoubleHit(player.getUUID())) {
                for (var stack : player.getInventory().items) {
                    if (!stack.isEmpty() && cache.canInsert(stack)) {
                        stack.setCount(cache.insertItem(0, stack, false).getCount());
                    }
                }
            }
            INTERACTION_LOGGER.put(player.getUUID(), System.currentTimeMillis());
            return InteractionResult.SUCCESS;

        }

        return super.onUseWithItem(context);
    }

    private static boolean isDoubleHit(UUID uuid) {
        return (System.currentTimeMillis() - INTERACTION_LOGGER.getLong(uuid)) < 300;
    }

    @Override
    public boolean onLeftClick(Player player, InteractionHand hand,
                               @Nullable Direction direction) {
        if (direction == getFrontFacing() && !isRemote()) {
            if (GTToolType.WRENCH.matchTags.stream().anyMatch(player.getItemInHand(hand)::is)) return false;
            if (!stored.isEmpty()) { // pull
                var drained = cache.extractItem(0, player.isShiftKeyDown() ? stored.getMaxStackSize() : 1, false);
                if (!drained.isEmpty()) {
                    if (!player.addItem(drained)) {
                        Block.popResourceFromFace(getLevel(), getBlockPos(), getFrontFacing(), drained);
                    }
                }
            }
        }
        return super.onLeftClick(player, hand, direction);
    }

    public boolean isLocked() {
        return !lockedItem.getStackInSlot(0).isEmpty();
    }

    protected void setLocked(boolean locked) {
        if (!stored.isEmpty() && locked) {
            var copied = stored.copyWithCount(1);
            lockedItem.setStackInSlot(0, copied);
        } else if (!locked) {
            lockedItem.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    protected void setLocked(ItemStack stack) {
        if (stack.isEmpty()) {
            setLocked(false);
        } else if (canLockItem(stack)) {
            lockedItem.setStackInSlot(0, stack.copyWithCount(1));
        }
    }

    @Override
    public void setQuantumChestLockedItem(ItemStack stack) {
        if (stack.isEmpty()) {
            setLocked(false);
            return;
        }
        if (!canLockItem(stack)) {
            throw new IllegalArgumentException("Quantum chest locked item does not match stored item.");
        }
        lockedItem.setStackInSlot(0, stack.copyWithCount(1));
    }

    @Override
    public void setQuantumChestLocked(boolean locked) {
        setLocked(locked);
    }

    private boolean canLockItem(ItemStack stack) {
        return stored.isEmpty() || ItemStack.isSameItemSameComponents(stack, stored);
    }

    private void setVoiding(boolean voiding) {
        isVoiding = voiding;
    }

    public ItemStack getLockedItem() {
        return lockedItem.getStackInSlot(0);
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        return UI.of(new LDLib2FancyMachineUIElement(new QuantumChestLDLib2Page(player, holder),
                player.getInventory(), holder, PAGE_WIDTH, PAGE_HEIGHT));
    }

    private UIElement createLDLib2MainPage(Player player, MachineUIHolder holder) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));
        root.addChild(new GTImageElement(4, 4, 81, 55, GuiTextures.DISPLAY));
        root.addChild(createLDLib2StoredAmountLabel());
        root.addChild(createLDLib2StoredAmountValueLabel());
        root.addChild(createLDLib2ImportSlot(player, holder));
        root.addChild(createLDLib2StoredDisplaySlot());
        root.addChild(createLDLib2ExportButton(player, holder));
        root.addChild(createLDLib2LockedItemSlot(player, holder));
        root.addChild(new GTToggleButtonElement(4, 41, 18, 18,
                GuiTextures.BUTTON_ITEM_OUTPUT, this.autoOutput::isAutoOutputItems,
                enabled -> setLDLib2AutoOutputItems(player, enabled))
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.item_auto_output.tooltip"));
        root.addChild(new GTToggleButtonElement(22, 41, 18, 18,
                GuiTextures.BUTTON_LOCK, this::isLocked,
                locked -> setLDLib2Locked(player, holder, locked))
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.item_lock.tooltip"));
        root.addChild(createLDLib2VoidingButton());
        return root;
    }

    private static GTLabelElement createLDLib2StoredAmountLabel() {
        GTLabelElement label = new GTLabelElement(8, 8, 76, 10,
                "gtpm.machine.quantum_chest.items_stored", true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTLabelElement createLDLib2StoredAmountValueLabel() {
        GTLabelElement label = new GTLabelElement(8, 18, 58, 10) {

            @Override
            public void screenTick() {
                setValue(Component.literal(FormattingUtil.formatNumbers(storedAmount)));
                super.screenTick();
            }
        };
        label.setValue(Component.literal(FormattingUtil.formatNumbers(storedAmount)));
        label.textStyle(style -> style
                .textColor(-1)
                .textShadow(true)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTItemSlotElement createLDLib2ImportSlot(Player player, MachineUIHolder holder) {
        GTItemSlotElement slot = new GTItemSlotElement()
                .setCanPutItems(false)
                .setCanTakeItems(false)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.IN_SLOT_OVERLAY));
        slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (!player.level().isClientSide()) {
                return;
            }
            ItemStack carried = player.containerMenu.getCarried();
            if ((event.button == 0 || event.button == 1) && !carried.isEmpty()) {
                MachineUIHelper.sendAction(holder,
                        QuantumChestMachineActions.createClickQuantumChestImportSlotAction(carried,
                                event.button == 1));
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        });
        return UITemplate.setLDLib2Bounds(slot, 87, 5, 18, 18);
    }

    private GTItemSlotElement createLDLib2StoredDisplaySlot() {
        GTItemSlotElement slot = new GTItemSlotElement() {

            @Override
            public void screenTick() {
                setValue(getLDLib2DisplayedStoredItem(), false);
                super.screenTick();
            }
        };
        slot.setValue(getLDLib2DisplayedStoredItem(), false);
        slot.setCanPutItems(false)
                .setCanTakeItems(false)
                .setBackgroundTexture(GuiTextures.SLOT);
        return UITemplate.setLDLib2Bounds(slot, 87, 23, 18, 18);
    }

    private ItemStack getLDLib2DisplayedStoredItem() {
        if (stored.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return stored.copyWithCount((int) Math.min(storedAmount, stored.getMaxStackSize()));
    }

    private GTButtonElement createLDLib2ExportButton(Player player, MachineUIHolder holder) {
        return new GTButtonElement(87, 42, 18, 18,
                GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.ICON_DOWN.copy().scale(0.7f)),
                event -> {
                    if (player.level().isClientSide()) {
                        MachineUIHelper.sendAction(holder,
                                QuantumChestMachineActions.createExportQuantumChestItemAction());
                    }
                    event.stopImmediatePropagation();
                    event.hasHandler = true;
                });
    }

    private GTPhantomItemSlotElement createLDLib2LockedItemSlot(Player player, MachineUIHolder holder) {
        GTPhantomItemSlotElement slot = new GTPhantomItemSlotElement(this::getLockedItem,
                stack -> setLDLib2LockedItem(player, holder, stack), () -> 1) {

            @Override
            public void screenTick() {
                refreshFromSupplier();
                super.screenTick();
            }
        };
        UITemplate.setLDLib2Bounds(slot, 58, 41, 18, 18);
        slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (!player.level().isClientSide()) {
                return;
            }
            if (event.button == 0 || event.button == 1) {
                ItemStack carried = player.containerMenu.getCarried();
                if (event.button == 0 && !carried.isEmpty() && canLockItem(carried)) {
                    slot.setItem(carried.copyWithCount(1));
                } else if (event.button == 1) {
                    slot.setItem(ItemStack.EMPTY);
                }
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        });
        return slot;
    }

    private LDLib2FancyConfiguratorButton.Toggle createLDLib2WorkingEnabledConfigurator(Player player,
                                                                                        MachineUIHolder holder) {
        return new LDLib2FancyConfiguratorButton.Toggle(
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0.5, 1, 0.5),
                this::isWorkingEnabled,
                (event, pressed) -> {
                    setLDLib2AutoOutputItems(player, pressed);
                    event.stopImmediatePropagation();
                    event.hasHandler = true;
                })
                .setTooltipsSupplier(pressed -> List.of(Component.translatable(
                        pressed ? "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled")));
    }

    private void setLDLib2LockedItem(Player player, MachineUIHolder holder, ItemStack item) {
        setLocked(item);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder,
                    QuantumChestMachineActions.createSetQuantumChestLockedItemAction(item));
        }
    }

    private void setLDLib2Locked(Player player, MachineUIHolder holder, boolean locked) {
        setLocked(locked);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder,
                    QuantumChestMachineActions.createSetQuantumChestLockedAction(locked));
        }
    }

    GTToggleButtonElement createLDLib2VoidingButton() {
        return new GTToggleButtonElement(40, 41, 18, 18,
                GuiTextures.BUTTON_VOID, () -> isVoiding, this::setLDLib2Voiding)
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.item_voiding_partial.tooltip");
    }

    private void setLDLib2Voiding(boolean voiding) {
        setVoiding(voiding);
        if (isRemote()) {
            sendServerSyncChanges();
        }
    }

    private void setLDLib2AutoOutputItems(Player player, boolean enabled) {
        setWorkingEnabled(enabled);
        if (player.level().isClientSide()) {
            sendServerSyncChanges();
        }
    }

    @Override
    public void clickQuantumChestImportSlot(ServerPlayer player, ItemStack requestedItem, boolean rightClick) {
        ItemStack carried = player.containerMenu.getCarried();
        if (requestedItem.isEmpty() || carried.isEmpty() ||
                !ItemStack.isSameItemSameComponents(requestedItem, carried)) {
            return;
        }
        int requestedCount = rightClick ? 1 : Math.min(requestedItem.getCount(), carried.getCount());
        ItemStack item = carried.copyWithCount(requestedCount);
        if (!cache.canInsert(item)) {
            return;
        }
        ItemStack remainder = cache.insertItem(0, item, false);
        int inserted = item.getCount() - remainder.getCount();
        if (inserted > 0) {
            carried.shrink(inserted);
            player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            player.containerMenu.broadcastChanges();
        }
    }

    @Override
    public void exportQuantumChestItem(ServerPlayer player) {
        if (stored.isEmpty()) {
            return;
        }
        ItemStack extracted = cache.extractItem(0, (int) Math.min(storedAmount, stored.getMaxStackSize()), false);
        if (extracted.isEmpty()) {
            return;
        }
        if (!player.addItem(extracted)) {
            Block.popResource(player.level(), player.getOnPos(), extracted);
        }
        player.containerMenu.broadcastChanges();
    }

    //////////////////////////////////////
    // ******* Rendering ********//
    //////////////////////////////////////

    @Override
    public @Nullable GridHighlightTexture sideTips(Player player, BlockPos pos, BlockState state,
                                                   Set<GTToolType> toolTypes, ItemStack held, Direction side) {
        if (toolTypes.contains(GTToolType.SOFT_MALLET)) {
            if (side == getFrontFacing()) return null;
        }
        return super.sideTips(player, pos, state, toolTypes, held, side);
    }

    private final class QuantumChestLDLib2Page implements LDLib2FancyUIProvider {

        private final Player player;
        private final MachineUIHolder holder;

        private QuantumChestLDLib2Page(Player player, MachineUIHolder holder) {
            this.player = player;
            this.holder = holder;
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            return QuantumChestMachine.this.createLDLib2MainPage(player, holder);
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
            tooltipsPanel.attachTooltips(QuantumChestMachine.this);
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

    protected class ItemCache extends MachineTrait implements IItemHandlerModifiable {

        public static final MachineTraitType<ItemCache> TYPE = new MachineTraitType<>(ItemCache.class);

        @Override
        public MachineTraitType<ItemCache> getTraitType() {
            return TYPE;
        }

        private final Predicate<ItemStack> filter = i -> !isLocked() ||
                ItemStack.isSameItemSameComponents(i, getLockedItem());

        public ItemCache() {
            super();
        }

        @Override
        public void setStackInSlot(int index, ItemStack stack) {
            stored = stack.copyWithCount(1);
            storedAmount = stack.getCount();
            onItemChanged();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stored.copyWithCount(GTMath.saturatedCast(storedAmount));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            long free = isVoiding ? Long.MAX_VALUE : maxAmount - storedAmount;
            long canStore = 0;
            if ((stored.isEmpty() || ItemStack.isSameItemSameComponents(stored, stack)) && filter.test(stack)) {
                canStore = Math.min(stack.getCount(), free);
            }
            if (!simulate && canStore > 0) {
                if (stored.isEmpty()) stored = stack.copyWithCount(1);
                storedAmount = Math.min(maxAmount, storedAmount + canStore);
                onItemChanged();
            }
            return stack.copyWithCount((int) (stack.getCount() - canStore));
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (stored.isEmpty()) return ItemStack.EMPTY;
            long toExtract = Math.min(storedAmount, amount);
            var copy = stored.copyWithCount((int) toExtract);
            if (!simulate && toExtract > 0) {
                storedAmount -= toExtract;
                if (storedAmount == 0) stored = ItemStack.EMPTY;
                onItemChanged();
            }
            return copy;
        }

        @Override
        public int getSlotLimit(int slot) {
            return GTMath.saturatedCast(maxAmount);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return filter.test(stack);
        }

        public void exportToNearby(Direction... facings) {
            if (stored.isEmpty()) return;
            var level = getMachine().getLevel();
            var pos = getMachine().getBlockPos();
            for (Direction facing : facings) {
                var filter = getMachine().getItemCapFilter(facing, IO.OUT);
                GTTransferUtils.getAdjacentItemHandler(level, pos, facing)
                        .ifPresent(adj -> GTTransferUtils.transferItemsFiltered(this, adj, filter));
            }
        }

        public boolean canInsert(ItemStack stack) {
            return filter.test(stack) && (insertItem(0, stack, true).getCount() != stack.getCount());
        }
    }
}
