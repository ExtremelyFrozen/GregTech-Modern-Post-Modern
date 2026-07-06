package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter;
import com.gregtechceu.gtceu.api.gui.element.GTEnumSelectorElement;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.cover.data.TransferMode;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.pipelike.item.ItemNetHandler;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;

public class RobotArmCover extends ConveyorCover {

    private static final ResourceLocation SET_ROBOT_ARM_COVER_CONFIG_ACTION = GTCEu
            .id("set_robot_arm_cover_config");
    private static final ResourceLocation TRANSFER_MODE_FIELD = SyncFieldData.key("transferMode");
    private static final ResourceLocation TRANSFER_LIMIT_FIELD = SyncFieldData.key("transferLimit");

    static {
        SyncActionDispatchers.server().register(new RobotArmCoverConfigActionHandler());
    }

    @SaveField
    @SyncToClient
    @Getter
    protected TransferMode transferMode;

    @SaveField
    @SyncToClient
    @Getter
    protected int globalTransferLimit;
    protected int itemsTransferBuffered;

    private @Nullable GTIntInputElement stackSizeLDLib2Input;

    public RobotArmCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier,
                         int maxTransferRate) {
        super(definition, coverHolder, attachedSide, tier, maxTransferRate);
        setTransferMode(TransferMode.TRANSFER_ANY);
    }

    public RobotArmCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier) {
        this(definition, coverHolder, attachedSide, tier, CONVEYOR_SCALING.applyAsInt(tier));
    }

    @Override
    protected int doTransferItems(IItemHandler itemHandler, IItemHandler myItemHandler, int maxTransferAmount) {
        if (io == IO.OUT && itemHandler instanceof ItemNetHandler && transferMode == TransferMode.KEEP_EXACT) {
            return 0;
        }
        if (io == IO.IN && myItemHandler instanceof ItemNetHandler && transferMode == TransferMode.KEEP_EXACT) {
            return 0;
        }
        return switch (transferMode) {
            case TRANSFER_ANY -> moveInventoryItems(itemHandler, myItemHandler, maxTransferAmount);
            case TRANSFER_EXACT -> doTransferExact(itemHandler, myItemHandler, maxTransferAmount);
            case KEEP_EXACT -> doKeepExact(itemHandler, myItemHandler, maxTransferAmount);
        };
    }

    protected int doTransferExact(IItemHandler sourceInventory, IItemHandler targetInventory, int maxTransferAmount) {
        Map<ItemStack, TypeItemInfo> sourceItemAmount = countInventoryItemsByType(sourceInventory);

        Iterator<ItemStack> iterator = sourceItemAmount.keySet().iterator();
        while (iterator.hasNext()) {
            TypeItemInfo sourceInfo = sourceItemAmount.get(iterator.next());
            int itemAmount = sourceInfo.totalCount;
            int itemToMoveAmount = getFilteredItemAmount(sourceInfo.itemStack);

            if (itemAmount >= itemToMoveAmount) {
                sourceInfo.totalCount = itemToMoveAmount;
            } else {
                iterator.remove();
            }
        }

        int itemsTransferred = 0;
        int maxTotalTransferAmount = maxTransferAmount + itemsTransferBuffered;
        boolean notEnoughTransferRate = false;
        for (TypeItemInfo itemInfo : sourceItemAmount.values()) {
            if (maxTotalTransferAmount >= itemInfo.totalCount) {
                boolean result = moveInventoryItemsExact(sourceInventory, targetInventory, itemInfo);
                itemsTransferred += result ? itemInfo.totalCount : 0;
                maxTotalTransferAmount -= result ? itemInfo.totalCount : 0;
            } else {
                notEnoughTransferRate = true;
            }
        }
        // if we didn't transfer anything because of too small transfer rate, buffer it
        if (itemsTransferred == 0 && notEnoughTransferRate) {
            itemsTransferBuffered += maxTransferAmount;
        } else {
            // otherwise, if transfer succeed, empty transfer buffer value
            itemsTransferBuffered = 0;
        }
        return Math.min(itemsTransferred, maxTransferAmount);
    }

    protected int doKeepExact(IItemHandler sourceInventory, IItemHandler targetInventory, int maxTransferAmount) {
        Map<ItemStack, GroupItemInfo> targetItemAmounts = countInventoryItemsByMatchSlot(targetInventory);
        Map<ItemStack, GroupItemInfo> sourceItemAmounts = countInventoryItemsByMatchSlot(sourceInventory);

        Iterator<ItemStack> iterator = sourceItemAmounts.keySet().iterator();
        while (iterator.hasNext()) {
            ItemStack filteredItem = iterator.next();
            GroupItemInfo sourceInfo = sourceItemAmounts.get(filteredItem);
            int itemToKeepAmount = getFilteredItemAmount(sourceInfo.itemStack);

            int itemAmount = 0;
            if (targetItemAmounts.containsKey(filteredItem)) {
                GroupItemInfo destItemInfo = targetItemAmounts.get(filteredItem);
                itemAmount = destItemInfo.totalCount;
            }
            if (itemAmount < itemToKeepAmount) {
                sourceInfo.totalCount = itemToKeepAmount - itemAmount;
            } else {
                iterator.remove();
            }
        }

        return moveInventoryItems(sourceInventory, targetInventory, sourceItemAmounts, maxTransferAmount);
    }

    private int getFilteredItemAmount(ItemStack itemStack) {
        if (!filterHandler.isFilterPresent())
            return globalTransferLimit;

        ItemFilter filter = filterHandler.getFilter();
        return filter.supportsAmounts() ? filter.testItemCount(itemStack) : globalTransferLimit;
    }

    public int getBuffer() {
        return itemsTransferBuffered;
    }

    public void buffer(int amount) {
        itemsTransferBuffered += amount;
    }

    public void clearBuffer() {
        itemsTransferBuffered = 0;
    }

    public void setGlobalTransferLimit(int globalTransferLimit) {
        int clamped = Math.min(Math.max(globalTransferLimit, 1), transferMode.maxStackSize);
        if (this.globalTransferLimit != clamped) {
            this.globalTransferLimit = clamped;
            syncDataHolder.markClientSyncFieldDirty("globalTransferLimit");
        }
        configureStackSizeInput();
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    @NotNull
    protected String getUITitle() {
        return "cover.robotic_arm.title";
    }

    @Override
    protected void buildAdditionalLDLib2UI(UIElement root, Player player, UICoverHolder holder) {
        root.addChild(GTEnumSelectorElement.selectable(146, 45, 20, 20, TransferMode.values(),
                this::getTransferMode, mode -> setLDLib2TransferMode(player, holder, mode)));

        this.stackSizeLDLib2Input = new GTIntInputElement(64, 45, 80, 20,
                this::getGlobalTransferLimit, value -> setLDLib2GlobalTransferLimit(player, holder, value));
        configureStackSizeInput();
        root.addChild(this.stackSizeLDLib2Input);
    }

    public void setTransferMode(TransferMode transferMode) {
        if (this.transferMode == transferMode) {
            configureStackSizeInput();
            return;
        }
        this.transferMode = transferMode;

        configureStackSizeInput();

        if (!coverHolder.isRemote()) {
            syncDataHolder.markClientSyncFieldDirty("transferMode");
            configureFilter();
        }
    }

    @Override
    protected void configureFilter() {
        if (filterHandler.getFilter() instanceof SimpleItemFilter filter) {
            filter.setMaxStackSize(filter.isBlackList() ? 1 : transferMode.maxStackSize);
        }

        configureStackSizeInput();
    }

    private void configureStackSizeInput() {
        if (this.stackSizeLDLib2Input == null)
            return;

        this.stackSizeLDLib2Input.setVisible(shouldShowStackSize());
        this.stackSizeLDLib2Input.setMin(1);
        this.stackSizeLDLib2Input.setMax(this.transferMode.maxStackSize);
    }

    private boolean shouldShowStackSize() {
        if (this.transferMode == TransferMode.TRANSFER_ANY)
            return false;

        if (!this.filterHandler.isFilterPresent())
            return true;

        return !this.filterHandler.getFilter().supportsAmounts();
    }

    private void setLDLib2TransferMode(Player player, UICoverHolder holder, TransferMode mode) {
        setTransferMode(mode);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2GlobalTransferLimit(Player player, UICoverHolder holder, int value) {
        setGlobalTransferLimit(value);
        sendLDLib2ConfigAction(player, holder);
    }

    private void sendLDLib2ConfigAction(Player player, UICoverHolder holder) {
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, createSetRobotArmCoverConfigAction(getTransferMode(),
                    getGlobalTransferLimit()));
        }
    }

    private static SyncActionData createSetRobotArmCoverConfigAction(TransferMode transferMode, int transferLimit) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TRANSFER_MODE_FIELD, new JsonPrimitive(transferMode.ordinal()))
                        .put(TRANSFER_LIMIT_FIELD, new JsonPrimitive(transferLimit))
                        .build())
                .build();
        return new SyncActionData(SET_ROBOT_ARM_COVER_CONFIG_ACTION, 0, payload);
    }

    @Override
    public DataComponentMap copyConfig(HolderLookup.Provider registries) {
        return ConfigCopyHelper.withFields(super.copyConfig(registries), fields -> fields
                .put(SyncFieldData.key("transferMode"),
                        ConfigCopyHelper.intValue(transferMode.ordinal()))
                .put(SyncFieldData.key("transferLimit"),
                        ConfigCopyHelper.intValue(globalTransferLimit)));
    }

    @Override
    public void pasteConfig(ServerPlayer player, HolderLookup.Provider registries, DataComponentMap config) {
        setTransferMode(TransferMode.values()[ConfigCopyHelper.getInt(config, "transferMode")]);
        setGlobalTransferLimit(ConfigCopyHelper.getInt(config, "transferLimit"));
        super.pasteConfig(player, registries, config);
    }

    private static final class RobotArmCoverConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_ROBOT_ARM_COVER_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof RobotArmCover;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null &&
                    isValidOrdinal(fields, TRANSFER_MODE_FIELD, TransferMode.values().length) &&
                    isValidPositiveInt(fields, TRANSFER_LIMIT_FIELD);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof RobotArmCover cover)) {
                throw new IllegalStateException("Robot arm cover config action received a non-robot-arm cover.");
            }
            cover.setTransferMode(TransferMode.values()[requireOrdinal(context.payload(), TRANSFER_MODE_FIELD,
                    TransferMode.values().length)]);
            cover.setGlobalTransferLimit(requirePositiveInt(context.payload(), TRANSFER_LIMIT_FIELD));
        }
    }

    private static boolean isValidOrdinal(SyncFieldData fields, ResourceLocation field, int valueCount) {
        Integer ordinal = readInt(fields, field);
        return ordinal != null && ordinal >= 0 && ordinal < valueCount;
    }

    private static int requireOrdinal(DataComponentMap payload, ResourceLocation field, int valueCount) {
        int ordinal = requireNonNegativeInt(payload, field);
        if (ordinal >= valueCount) {
            throw new IllegalArgumentException("Robot arm cover config action ordinal is out of range: " + ordinal);
        }
        return ordinal;
    }

    private static boolean isValidPositiveInt(SyncFieldData fields, ResourceLocation field) {
        Integer value = readInt(fields, field);
        return value != null && value > 0;
    }

    private static int requirePositiveInt(DataComponentMap payload, ResourceLocation field) {
        int value = requireNonNegativeInt(payload, field);
        if (value <= 0) {
            throw new IllegalArgumentException("Robot arm cover config action value must be positive: " + value);
        }
        return value;
    }

    private static int requireNonNegativeInt(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Robot arm cover config action payload is missing field data.");
        }
        Integer value = readInt(fields, field);
        if (value == null) {
            throw new IllegalStateException("Robot arm cover config action payload is missing " + field + ".");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Robot arm cover config action value is negative: " + value);
        }
        return value;
    }

    private static @Nullable Integer readInt(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsInt();
        }
        return null;
    }
}
