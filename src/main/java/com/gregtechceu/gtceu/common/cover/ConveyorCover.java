package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IIOCover;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandlers;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTEnumSelectorElement;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.ItemHandlerDelegate;
import com.gregtechceu.gtceu.common.blockentity.ItemPipeBlockEntity;
import com.gregtechceu.gtceu.common.cover.data.DistributionMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class ConveyorCover extends CoverBehavior
                           implements IIOCover, LDLib2CoverUIProvider, IControllable,
                           ConveyorCoverConfigActionTarget {

    // 8 32 128 512 1024
    public static final Int2IntFunction CONVEYOR_SCALING = tier -> 2 * (int) Math.pow(4, Math.min(tier, GTValues.LuV));
    static {
        ConveyorCoverConfigActions.initialize();
    }

    public final int tier;
    public final int maxItemTransferRate;
    @SaveField
    @SyncToClient
    @Getter
    protected int transferRate;
    @SaveField
    @SyncToClient
    @Getter
    @RerenderOnChanged
    protected IO io;
    @SaveField
    @SyncToClient
    @Getter
    protected DistributionMode distributionMode;
    @SaveField
    @SyncToClient
    @Getter
    protected ManualIOMode manualIOMode = ManualIOMode.DISABLED;
    @SaveField
    @SyncToClient
    @Getter
    protected boolean isWorkingEnabled = true;
    protected int itemsLeftToTransferLastSecond;

    @SaveField
    @SyncToClient
    @Getter
    protected final FilterHandler<ItemStack, ItemFilter> filterHandler;
    protected final ConditionalSubscriptionHandler subscriptionHandler;

    public ConveyorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier,
                         int maxTransferRate) {
        super(definition, coverHolder, attachedSide);
        this.tier = tier;
        this.maxItemTransferRate = maxTransferRate;
        this.transferRate = maxItemTransferRate;
        this.itemsLeftToTransferLastSecond = transferRate;
        this.io = IO.OUT;
        this.distributionMode = DistributionMode.INSERT_FIRST;

        subscriptionHandler = new ConditionalSubscriptionHandler(coverHolder, this::update, this::isSubscriptionActive);
        filterHandler = FilterHandlers.item(this)
                .onFilterLoaded(f -> configureFilter())
                .onFilterUpdated(f -> configureFilter())
                .onFilterRemoved(f -> configureFilter());
    }

    public ConveyorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier) {
        this(definition, coverHolder, attachedSide, tier, CONVEYOR_SCALING.applyAsInt(tier));
    }

    protected boolean isSubscriptionActive() {
        return isWorkingEnabled() && getAdjacentItemHandler() != null;
    }

    protected @Nullable IItemHandlerModifiable getOwnItemHandler() {
        return coverHolder.getItemHandlerCap(attachedSide, false);
    }

    protected @Nullable IItemHandler getAdjacentItemHandler() {
        return GTTransferUtils.getAdjacentItemHandler(coverHolder.getLevel(), coverHolder.getBlockPos(), attachedSide)
                .orElse(null);
    }

    @Override
    public void setDistributionMode(DistributionMode mode) {
        if (distributionMode != mode) {
            distributionMode = mode;
            syncDataHolder.markClientSyncFieldDirty("distributionMode");
        }
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public boolean canAttach() {
        return super.canAttach() && getOwnItemHandler() != null;
    }

    @Override
    public void setTransferRate(int transferRate) {
        int clamped = Math.min(Math.max(transferRate, 1), maxItemTransferRate);
        if (this.transferRate != clamped) {
            this.transferRate = clamped;
            syncDataHolder.markClientSyncFieldDirty("transferRate");
        }
    }

    @Override
    public void setIo(IO io) {
        if (io == IO.IN || io == IO.OUT) {
            if (this.io != io) {
                this.io = io;
                syncDataHolder.markClientSyncFieldDirty("io");
            }
        }
        subscriptionHandler.updateSubscription();
    }

    @Override
    public void setManualIOMode(ManualIOMode manualIOMode) {
        if (this.manualIOMode != manualIOMode) {
            this.manualIOMode = manualIOMode;
            syncDataHolder.markClientSyncFieldDirty("manualIOMode");
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        subscriptionHandler.initialize(coverHolder.getLevel());
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        subscriptionHandler.unsubscribe();
    }

    @Override
    public List<ItemStack> getAdditionalDrops() {
        var list = super.getAdditionalDrops();
        if (!filterHandler.getFilterItem().isEmpty()) {
            list.add(filterHandler.getFilterItem());
        }
        return list;
    }

    //////////////////////////////////////
    // ***** Transfer Logic *****//
    //////////////////////////////////////

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        subscriptionHandler.updateSubscription();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (this.isWorkingEnabled != isWorkingAllowed) {
            this.isWorkingEnabled = isWorkingAllowed;
            syncDataHolder.markClientSyncFieldDirty("isWorkingEnabled");
            subscriptionHandler.updateSubscription();
        }
    }

    protected void update() {
        long timer = coverHolder.getOffsetTimer();
        if (timer % 5 == 0) {
            if (itemsLeftToTransferLastSecond > 0) {
                var adjacent = getAdjacentItemHandler();
                var self = getOwnItemHandler();

                if (adjacent != null && self != null) {
                    int totalTransferred = switch (io) {
                        case IN -> doTransferItems(adjacent, self, itemsLeftToTransferLastSecond);
                        case OUT -> doTransferItems(self, adjacent, itemsLeftToTransferLastSecond);
                        default -> 0;
                    };
                    this.itemsLeftToTransferLastSecond -= totalTransferred;
                }
            }
            if (timer % 20 == 0) {
                this.itemsLeftToTransferLastSecond = transferRate;
            }
            subscriptionHandler.updateSubscription();
        }
    }

    protected int doTransferItems(IItemHandler sourceInventory, IItemHandler targetInventory, int maxTransferAmount) {
        return moveInventoryItems(sourceInventory, targetInventory, maxTransferAmount);
    }

    protected int moveInventoryItems(IItemHandler sourceInventory, IItemHandler targetInventory,
                                     int maxTransferAmount) {
        ItemFilter filter = filterHandler.getFilter();
        int itemsLeftToTransfer = maxTransferAmount;

        for (int srcIndex = 0; srcIndex < sourceInventory.getSlots(); srcIndex++) {
            ItemStack sourceStack = sourceInventory.extractItem(srcIndex, itemsLeftToTransfer, true);
            if (sourceStack.isEmpty()) {
                continue;
            }

            if (!filter.test(sourceStack)) {
                continue;
            }

            ItemStack remainder = ItemHandlerHelper.insertItem(targetInventory, sourceStack, true);
            int amountToInsert = sourceStack.getCount() - remainder.getCount();

            if (amountToInsert > 0) {
                sourceStack = sourceInventory.extractItem(srcIndex, amountToInsert, false);
                if (!sourceStack.isEmpty()) {
                    ItemHandlerHelper.insertItem(targetInventory, sourceStack, false);
                    itemsLeftToTransfer -= sourceStack.getCount();

                    if (itemsLeftToTransfer == 0) {
                        break;
                    }
                }
            }
        }
        return maxTransferAmount - itemsLeftToTransfer;
    }

    protected static boolean moveInventoryItemsExact(IItemHandler sourceInventory, IItemHandler targetInventory,
                                                     TypeItemInfo itemInfo) {
        // first, compute how much can we extract in reality from the machine,
        // because totalCount is based on what getStackInSlot returns, which may differ from what
        // extractItem() will return
        ItemStack resultStack = itemInfo.itemStack.copy();
        int totalExtractedCount = 0;
        int itemsLeftToExtract = itemInfo.totalCount;

        for (int i = 0; i < itemInfo.slots.size(); i++) {
            int slotIndex = itemInfo.slots.getInt(i);
            ItemStack extractedStack = sourceInventory.extractItem(slotIndex, itemsLeftToExtract, true);
            if (!extractedStack.isEmpty() &&
                    ItemStack.isSameItemSameComponents(resultStack, extractedStack)) {
                totalExtractedCount += extractedStack.getCount();
                itemsLeftToExtract -= extractedStack.getCount();
            }
            if (itemsLeftToExtract == 0) {
                break;
            }
        }
        // if amount of items extracted is not equal to the amount of items we
        // wanted to extract, abort item extraction
        if (totalExtractedCount != itemInfo.totalCount) {
            return false;
        }
        // adjust size of the result stack accordingly
        resultStack.setCount(totalExtractedCount);

        // now, see how much we can insert into destination inventory
        // if we can't insert as much as itemInfo requires, and remainder is empty, abort, abort
        ItemStack remainder = ItemHandlerHelper.insertItem(targetInventory, resultStack, true);
        if (!remainder.isEmpty()) {
            return false;
        }

        // otherwise, perform real insertion and then remove items from the source inventory
        ItemHandlerHelper.insertItem(targetInventory, resultStack, false);

        // perform real extraction of the items from the source inventory now
        itemsLeftToExtract = itemInfo.totalCount;
        for (int i = 0; i < itemInfo.slots.size(); i++) {
            int slotIndex = itemInfo.slots.getInt(i);
            ItemStack extractedStack = sourceInventory.extractItem(slotIndex, itemsLeftToExtract, false);
            if (!extractedStack.isEmpty() &&
                    ItemStack.isSameItemSameComponents(resultStack, extractedStack)) {
                itemsLeftToExtract -= extractedStack.getCount();
            }
            if (itemsLeftToExtract == 0) {
                break;
            }
        }
        return true;
    }

    protected int moveInventoryItems(IItemHandler sourceInventory, IItemHandler targetInventory,
                                     Map<ItemStack, GroupItemInfo> itemInfos, int maxTransferAmount) {
        ItemFilter filter = filterHandler.getFilter();
        int itemsLeftToTransfer = maxTransferAmount;

        for (int i = 0; i < sourceInventory.getSlots(); i++) {
            ItemStack itemStack = sourceInventory.getStackInSlot(i);
            if (itemStack.isEmpty() || !filter.test(itemStack) || !itemInfos.containsKey(itemStack)) {
                continue;
            }

            GroupItemInfo itemInfo = itemInfos.get(itemStack);

            ItemStack extractedStack = sourceInventory.extractItem(i,
                    Math.min(itemInfo.totalCount, itemsLeftToTransfer), true);

            ItemStack remainderStack = ItemHandlerHelper.insertItemStacked(targetInventory, extractedStack, true);
            int amountToInsert = extractedStack.getCount() - remainderStack.getCount();

            if (amountToInsert > 0) {
                extractedStack = sourceInventory.extractItem(i, amountToInsert, false);

                if (!extractedStack.isEmpty()) {

                    ItemHandlerHelper.insertItemStacked(targetInventory, extractedStack, false);
                    itemsLeftToTransfer -= extractedStack.getCount();
                    itemInfo.totalCount -= extractedStack.getCount();

                    if (itemInfo.totalCount == 0) {
                        itemInfos.remove(itemStack);
                        if (itemInfos.isEmpty()) {
                            break;
                        }
                    }
                    if (itemsLeftToTransfer == 0) {
                        break;
                    }
                }
            }
        }
        return maxTransferAmount - itemsLeftToTransfer;
    }

    @NotNull
    protected Map<ItemStack, TypeItemInfo> countInventoryItemsByType(@NotNull IItemHandler inventory) {
        ItemFilter filter = filterHandler.getFilter();
        Map<ItemStack, TypeItemInfo> result = new Object2ObjectOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());

        for (int srcIndex = 0; srcIndex < inventory.getSlots(); srcIndex++) {
            ItemStack itemStack = inventory.getStackInSlot(srcIndex);
            if (itemStack.isEmpty() || !filter.test(itemStack)) {
                continue;
            }

            var itemInfo = result.computeIfAbsent(itemStack, s -> new TypeItemInfo(s, new IntArrayList(), 0));

            itemInfo.totalCount += itemStack.getCount();
            itemInfo.slots.add(srcIndex);
        }

        return result;
    }

    @NotNull
    protected Map<ItemStack, GroupItemInfo> countInventoryItemsByMatchSlot(@NotNull IItemHandler inventory) {
        ItemFilter filter = filterHandler.getFilter();
        Map<ItemStack, GroupItemInfo> result = new Object2ObjectOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());

        for (int srcIndex = 0; srcIndex < inventory.getSlots(); srcIndex++) {
            ItemStack itemStack = inventory.getStackInSlot(srcIndex);
            if (itemStack.isEmpty() || !filter.test(itemStack)) {
                continue;
            }

            var itemInfo = result.computeIfAbsent(itemStack, s -> new GroupItemInfo(s, 0));

            itemInfo.totalCount += itemStack.getCount();
        }
        return result;
    }

    @AllArgsConstructor
    protected static class TypeItemInfo {

        public final ItemStack itemStack;
        public final IntList slots;
        public int totalCount;
    }

    @AllArgsConstructor
    protected static class GroupItemInfo {

        public final ItemStack itemStack;
        public int totalCount;
    }

    public boolean shouldRespectDistributionMode() {
        return ((io == IO.IN) ?
                (coverHolder.getLevel().getBlockEntity(coverHolder.getBlockPos()) instanceof ItemPipeBlockEntity) :
                (coverHolder.getLevel().getBlockEntity(coverHolder.getBlockPos()
                        .relative(attachedSide)) instanceof ItemPipeBlockEntity));
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public boolean canCreateLDLib2UI(Player player, UICoverHolder holder) {
        return holder.getCover() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, UICoverHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 219);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));

        root.addChild(createLDLib2Label());
        root.addChild(new GTIntInputElement(10, 20, 156, 20, this::getTransferRate,
                value -> setLDLib2TransferRate(player, holder, value))
                .setMin(1)
                .setMax(maxItemTransferRate));
        root.addChild(GTEnumSelectorElement.selectable(10, 45, 20, 20, List.of(IO.IN, IO.OUT), this::getIo,
                mode -> setLDLib2Io(player, holder, mode)));

        if (shouldDisplayDistributionMode()) {
            root.addChild(GTEnumSelectorElement.selectable(146, 67, 20, 20, DistributionMode.VALUES,
                    this::getDistributionMode, mode -> setLDLib2DistributionMode(player, holder, mode)));
        }

        root.addChild(GTEnumSelectorElement.selectable(146, 107, 20, 20, ManualIOMode.VALUES,
                this::getManualIOMode, mode -> setLDLib2ManualIOMode(player, holder, mode)));

        root.addChild(filterHandler.createFilterSlotLDLib2UI(125, 108));
        root.addChild(filterHandler.createFilterConfigLDLib2UI(10, 72, 156, 60));
        buildAdditionalLDLib2UI(root, player, holder);
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7, 137, true));
        return UI.of(root);
    }

    private boolean shouldDisplayDistributionMode() {
        return coverHolder.getLevel().getBlockEntity(coverHolder.getBlockPos()) instanceof ItemPipeBlockEntity ||
                coverHolder.getLevel()
                        .getBlockEntity(
                                coverHolder.getBlockPos().relative(attachedSide)) instanceof ItemPipeBlockEntity;
    }

    @NotNull
    protected String getUITitle() {
        return "cover.conveyor.title";
    }

    protected void buildAdditionalLDLib2UI(UIElement root, Player player, UICoverHolder holder) {
        // Do nothing in the base implementation. This is intended to be overridden by subclasses.
    }

    protected void configureFilter() {
        // Do nothing in the base implementation. This is intended to be overridden by subclasses.
    }

    private GTLabelElement createLDLib2Label() {
        GTLabelElement label = new GTLabelElement(10, 5, 156, 10, Component.translatable(getUITitle(),
                GTValues.VN[tier]));
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private void setLDLib2TransferRate(Player player, UICoverHolder holder, int value) {
        setTransferRate(value);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2Io(Player player, UICoverHolder holder, IO io) {
        setIo(io);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2DistributionMode(Player player, UICoverHolder holder, DistributionMode mode) {
        setDistributionMode(mode);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2ManualIOMode(Player player, UICoverHolder holder, ManualIOMode mode) {
        setManualIOMode(mode);
        sendLDLib2ConfigAction(player, holder);
    }

    private void sendLDLib2ConfigAction(Player player, UICoverHolder holder) {
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, ConveyorCoverConfigActions.createSetConfigAction(getTransferRate(),
                    getIo(), getDistributionMode(), getManualIOMode()));
        }
    }

    /////////////////////////////////////
    // *** CAPABILITY OVERRIDE ***//
    /////////////////////////////////////

    private CoverableItemHandlerWrapper itemHandlerWrapper;

    @Nullable
    @Override
    public IItemHandlerModifiable getItemHandlerCap(@Nullable IItemHandlerModifiable defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        if (itemHandlerWrapper == null || itemHandlerWrapper.delegate != defaultValue) {
            this.itemHandlerWrapper = new CoverableItemHandlerWrapper(defaultValue);
        }
        return itemHandlerWrapper;
    }

    private class CoverableItemHandlerWrapper extends ItemHandlerDelegate {

        public CoverableItemHandlerWrapper(IItemHandlerModifiable delegate) {
            super(delegate);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (io == IO.OUT) {
                if (manualIOMode == ManualIOMode.DISABLED) {
                    return stack;
                }
                if (manualIOMode == ManualIOMode.UNFILTERED) {
                    return super.insertItem(slot, stack, simulate);
                }
            }
            if (!filterHandler.test(stack)) {
                return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (io == IO.IN) {
                if (manualIOMode == ManualIOMode.DISABLED) {
                    return ItemStack.EMPTY;
                }
                if (manualIOMode == ManualIOMode.UNFILTERED) {
                    return super.extractItem(slot, amount, simulate);
                }
            }
            ItemStack result = super.extractItem(slot, amount, true);
            if (result.isEmpty() || !filterHandler.test(result)) {
                return ItemStack.EMPTY;
            }
            return simulate ? result : super.extractItem(slot, amount, false);
        }
    }

    @Override
    public DataComponentMap copyConfig(HolderLookup.Provider registries) {
        return ConfigCopyHelper.withFields(super.copyConfig(registries), fields -> fields
                .put(SyncFieldData.key("transferRate"),
                        ConfigCopyHelper.intValue(getTransferRate()))
                .put(SyncFieldData.key("io"),
                        ConfigCopyHelper.intValue(getIo().ordinal()))
                .put(SyncFieldData.key("distributionMode"),
                        ConfigCopyHelper.intValue(getDistributionMode().ordinal()))
                .put(SyncFieldData.key("manualIO"),
                        ConfigCopyHelper.intValue(getManualIOMode().ordinal()))
                .put(SyncFieldData.key("filter"),
                        ConfigCopyHelper.encodeItem(registries, filterHandler.getFilterItem())));
    }

    @Override
    public void pasteConfig(ServerPlayer player, HolderLookup.Provider registries, DataComponentMap config) {
        setTransferRate(ConfigCopyHelper.getInt(config, "transferRate"));
        setIo(IO.values()[ConfigCopyHelper.getInt(config, "io")]);
        setDistributionMode(DistributionMode.values()[ConfigCopyHelper.getInt(config, "distributionMode")]);
        setManualIOMode(ManualIOMode.values()[ConfigCopyHelper.getInt(config, "manualIO")]);
        filterHandler
                .setFilterItem(ConfigCopyHelper.decodeItem(registries, ConfigCopyHelper.getField(config, "filter")));
        super.pasteConfig(player, registries, config);
    }
}
