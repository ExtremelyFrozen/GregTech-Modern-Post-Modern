package com.gregtechceu.gtceu.common.cover.voiding;

import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter;
import com.gregtechceu.gtceu.api.gui.element.GTEnumSelectorElement;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.cover.data.VoidingMode;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class AdvancedItemVoidingCover extends ItemVoidingCover
                                      implements AdvancedItemVoidingCoverConfigActionTarget {

    static {
        AdvancedItemVoidingCoverConfigActions.initialize();
    }

    @SaveField
    @SyncToClient
    @Getter
    private VoidingMode voidingMode = VoidingMode.VOID_ANY;

    @SaveField
    @Getter
    protected int globalVoidingLimit = 1;

    private @Nullable GTIntInputElement stackSizeLDLib2Input;

    public AdvancedItemVoidingCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    //////////////////////////////////////////////
    // *********** COVER LOGIC ***********//

    /// ///////////////////////////////////////////

    @Override
    protected void doVoidItems() {
        IItemHandler handler = getOwnItemHandler();
        if (handler == null) {
            return;
        }

        switch (voidingMode) {
            case VOID_ANY -> voidAny(handler);
            case VOID_OVERFLOW -> voidOverflow(handler);
        }
    }

    private void voidOverflow(IItemHandler handler) {
        Map<ItemStack, TypeItemInfo> sourceItemAmounts = countInventoryItemsByType(handler);

        for (TypeItemInfo itemInfo : sourceItemAmounts.values()) {
            int itemToVoidAmount = itemInfo.totalCount - getFilteredItemAmount(itemInfo.itemStack);

            if (itemToVoidAmount <= 0) {
                continue;
            }

            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack is = handler.getStackInSlot(slot);
                if (!is.isEmpty() && ItemStack.isSameItemSameComponents(is, itemInfo.itemStack)) {
                    ItemStack extracted = handler.extractItem(slot, itemToVoidAmount, false);

                    if (!extracted.isEmpty()) {
                        itemToVoidAmount -= extracted.getCount();
                    }
                }
                if (itemToVoidAmount == 0) {
                    break;
                }
            }
        }
    }

    private int getFilteredItemAmount(ItemStack itemStack) {
        if (!filterHandler.isFilterPresent())
            return globalVoidingLimit;

        ItemFilter filter = filterHandler.getFilter();
        return filter.isBlackList() ? globalVoidingLimit : filter.testItemCount(itemStack);
    }

    @Override
    public void setVoidingMode(VoidingMode voidingMode) {
        this.voidingMode = voidingMode;

        configureStackSizeInput();

        if (!coverHolder.isRemote()) {
            syncDataHolder.markClientSyncFieldDirty("voidingMode");
            configureFilter();
        }
    }

    //////////////////////////////////////
    // *********** GUI ***********//

    /// ///////////////////////////////////

    @Override
    protected @NotNull String getUITitle() {
        return "cover.item.voiding.advanced.title";
    }

    @Override
    protected void buildAdditionalLDLib2UI(UIElement root, Player player, UICoverHolder holder) {
        root.addChild(GTEnumSelectorElement.selectable(146, 20, 20, 20, VoidingMode.values(), this::getVoidingMode,
                mode -> setLDLib2VoidingMode(player, holder, mode)));

        this.stackSizeLDLib2Input = new GTIntInputElement(64, 20, 80, 20,
                () -> globalVoidingLimit, value -> setLDLib2GlobalVoidingLimit(player, holder, value));
        configureStackSizeInput();
        root.addChild(this.stackSizeLDLib2Input);
    }

    @Override
    protected void configureFilter() {
        if (filterHandler.getFilter() instanceof SimpleItemFilter filter) {
            filter.setMaxStackSize(this.voidingMode.maxStackSize);
        }

        configureStackSizeInput();
    }

    private void configureStackSizeInput() {
        if (this.stackSizeLDLib2Input == null)
            return;

        this.stackSizeLDLib2Input.setVisible(shouldShowStackSize());
        this.stackSizeLDLib2Input.setMin(1);
        this.stackSizeLDLib2Input.setMax(this.voidingMode.maxStackSize);
    }

    private boolean shouldShowStackSize() {
        if (this.voidingMode == VoidingMode.VOID_ANY)
            return false;

        if (!this.filterHandler.isFilterPresent())
            return true;

        return this.filterHandler.getFilter().isBlackList();
    }

    private void setLDLib2VoidingMode(Player player, UICoverHolder holder, VoidingMode mode) {
        setVoidingMode(mode);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2GlobalVoidingLimit(Player player, UICoverHolder holder, int value) {
        setGlobalVoidingLimit(value);
        sendLDLib2ConfigAction(player, holder);
    }

    @Override
    public void setGlobalVoidingLimit(int value) {
        this.globalVoidingLimit = Math.max(value, 1);
        configureStackSizeInput();
    }

    private void sendLDLib2ConfigAction(Player player, UICoverHolder holder) {
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, AdvancedItemVoidingCoverConfigActions.createSetConfigAction(
                    getVoidingMode(), getGlobalVoidingLimit()));
        }
    }

    @Override
    public DataComponentMap copyConfig(HolderLookup.Provider registries) {
        return ConfigCopyHelper.withFields(super.copyConfig(registries), fields -> fields
                .put(SyncFieldData.key("voidingMode"),
                        ConfigCopyHelper.intValue(getVoidingMode().ordinal()))
                .put(SyncFieldData.key("voidSize"),
                        ConfigCopyHelper.intValue(getGlobalVoidingLimit())));
    }

    @Override
    public void pasteConfig(ServerPlayer player, HolderLookup.Provider registries, DataComponentMap config) {
        setVoidingMode(VoidingMode.values()[ConfigCopyHelper.getInt(config, "voidingMode")]);
        setGlobalVoidingLimit(ConfigCopyHelper.getInt(config, "voidSize"));
        super.pasteConfig(player, registries, config);
    }
}
