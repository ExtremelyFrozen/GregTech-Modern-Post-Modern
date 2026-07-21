package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandlers;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEnderRegistry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualItemStorage;
import com.gregtechceu.gtceu.api.sync_system.SyncDataHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EnderItemLinkCover extends AbstractEnderLinkCover<VirtualItemStorage> {

    @Getter
    protected final SyncDataHolder syncDataHolder = new SyncDataHolder(this);

    protected static final int TRANSFER_RATE = 8;

    @SaveField
    @SyncToClient
    protected VirtualItemStorage storage;
    protected int itemsLeftToTransferLastSecond;
    @Getter
    @SaveField
    @SyncToClient
    protected FilterHandler<ItemStack, ItemFilter> filterHandler;

    public EnderItemLinkCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        itemsLeftToTransferLastSecond = TRANSFER_RATE * 20;
        filterHandler = FilterHandlers.item(this);
        if (!coverHolder.isRemote())
            setEntry(VirtualEnderRegistry.getInstance().getOrCreateEntry(getOwner(), EntryTypes.ENDER_ITEM,
                    getChannelName()));
    }

    @Override
    public boolean canAttach() {
        return true;
    }

    @Override
    protected String identifier() {
        return "EILink#";
    }

    @Override
    protected VirtualItemStorage getEntry() {
        return storage;
    }

    @Override
    protected void setEntry(VirtualEntry entry) {
        storage = (VirtualItemStorage) entry;
        syncDataHolder.markClientSyncFieldDirty("storage");
    }

    @Override
    protected EntryTypes<VirtualItemStorage> getEntryType() {
        return EntryTypes.ENDER_ITEM;
    }

    @Override
    protected void transfer() {
        long timer = coverHolder.getOffsetTimer();
        if (itemsLeftToTransferLastSecond > 0) {
            itemsLeftToTransferLastSecond -= doTransferItems(itemsLeftToTransferLastSecond);
        }
        if (timer % 20 == 0) itemsLeftToTransferLastSecond = TRANSFER_RATE * 20;
    }

    private int doTransferItems(int max) {
        IItemHandler ownHandler = getOwnItemHandler();
        if (ownHandler == null) return 0;
        return switch (io) {
            case IN -> GTTransferUtils.transferItemsFiltered(ownHandler, storage.getHandler(),
                    filterHandler.getFilter(), max);
            case OUT -> GTTransferUtils.transferItemsFiltered(storage.getHandler(), ownHandler,
                    filterHandler.getFilter(), max);
            default -> 0;
        };
    }

    public @Nullable IItemHandler getOwnItemHandler() {
        return coverHolder.getItemHandlerCap(attachedSide, false);
    }

    @Override
    public DataComponentMap copyConfig(HolderLookup.Provider registries) {
        return ConfigCopyHelper.withFields(super.copyConfig(registries), fields -> fields
                .put(SyncFieldData.key("filter"),
                        ConfigCopyHelper.encodeItem(registries, filterHandler.getFilterItem())));
    }

    @Override
    public void pasteConfig(ServerPlayer player, HolderLookup.Provider registries, DataComponentMap config) {
        filterHandler
                .setFilterItem(ConfigCopyHelper.decodeItem(registries, ConfigCopyHelper.getField(config, "filter")));
        super.pasteConfig(player, registries, config);
    }

    @Override
    public @NotNull List<ItemStack> getAdditionalDrops() {
        var list = super.getAdditionalDrops();
        if (!filterHandler.getFilterItem().isEmpty()) {
            list.add(filterHandler.getFilterItem());
        }
        return list;
    }

    @Override
    protected UIElement addVirtualEntryLDLib2Element(VirtualEntry entry, int x, int y, int width, int height,
                                                     boolean canClick) {
        UIElement group = UITemplate.setLDLib2Bounds(new UIElement(), x, y, width, height);
        for (int i = 0; i < ((VirtualItemStorage) entry).getHandler().getSlots(); i++) {
            GTItemSlotElement slot = new GTItemSlotElement(((VirtualItemStorage) entry).getHandler(), i)
                    .setCanPutItems(canClick)
                    .setCanTakeItems(canClick)
                    .setBackgroundTexture(GuiTextures.SLOT);
            group.addChild(UITemplate.setLDLib2Bounds(slot, 8 * i, 0, 18, 18));
        }
        return group;
    }

    @Override
    protected String getUITitle() {
        return "cover.ender_item_link.title";
    }
}
