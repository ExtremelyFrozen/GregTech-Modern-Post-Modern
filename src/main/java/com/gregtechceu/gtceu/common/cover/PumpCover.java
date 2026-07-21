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
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
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
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerDelegate;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.api.transfer.fluid.ModifiableFluidHandlerWrapper;
import com.gregtechceu.gtceu.common.cover.data.BucketMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class PumpCover extends CoverBehavior
                       implements IIOCover, LDLib2CoverUIProvider, IControllable, PumpCoverConfigActionTarget {

    // .5b 2b 8b
    public static final Int2IntFunction PUMP_SCALING = tier -> 64 * (int) Math.pow(4, Math.min(tier - 1, GTValues.IV));

    static {
        PumpCoverConfigActions.initialize();
    }

    public final int tier;
    public final int maxFluidTransferRate;
    @SaveField
    @SyncToClient
    @Getter
    protected int transferRate;
    @SaveField
    @SyncToClient
    @Getter
    @RerenderOnChanged
    protected IO io = IO.OUT;
    @SaveField
    @SyncToClient
    @Getter
    protected BucketMode bucketMode = BucketMode.MILLI_BUCKET;
    @SaveField
    @SyncToClient
    @Getter
    protected ManualIOMode manualIOMode = ManualIOMode.DISABLED;

    @SaveField
    @SyncToClient
    @Getter
    protected boolean isWorkingEnabled = true;
    protected int mBLeftToTransferLastSecond;

    @SaveField
    @SyncToClient
    protected final FilterHandler<FluidStack, FluidFilter> filterHandler;
    protected final ConditionalSubscriptionHandler subscriptionHandler;
    private @Nullable GTIntInputElement transferRateLDLib2Input;

    public PumpCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier,
                     int maxTransferRate) {
        super(definition, coverHolder, attachedSide);
        this.tier = tier;

        this.maxFluidTransferRate = maxTransferRate;
        this.transferRate = maxFluidTransferRate;
        this.mBLeftToTransferLastSecond = transferRate * 20;

        subscriptionHandler = new ConditionalSubscriptionHandler(coverHolder, this::update, this::isSubscriptionActive);
        filterHandler = FilterHandlers.fluid(this)
                .onFilterLoaded(f -> configureFilter())
                .onFilterUpdated(f -> configureFilter())
                .onFilterRemoved(f -> configureFilter());
    }

    public PumpCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier) {
        this(definition, coverHolder, attachedSide, tier, PUMP_SCALING.applyAsInt(tier));
    }

    @Override
    public int getMaxFluidTransferRate() {
        return maxFluidTransferRate;
    }

    protected boolean isSubscriptionActive() {
        return isWorkingEnabled() && getAdjacentFluidHandler() != null;
    }

    protected @Nullable IFluidHandlerModifiable getOwnFluidHandler() {
        return coverHolder.getFluidHandlerCap(attachedSide, false);
    }

    protected @Nullable IFluidHandler getAdjacentFluidHandler() {
        return GTTransferUtils.getAdjacentFluidHandler(coverHolder.getLevel(), coverHolder.getBlockPos(), attachedSide)
                .orElse(null);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public boolean canAttach() {
        return super.canAttach() && getOwnFluidHandler() != null;
    }

    @Override
    public void setIo(IO io) {
        if (io == IO.IN || io == IO.OUT) {
            if (this.io != io) {
                this.io = io;
                syncDataHolder.markClientSyncFieldDirty("io");
            }
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

    //////////////////////////////////////
    // ***** Transfer Logic *****//
    //////////////////////////////////////

    @Override
    public void setTransferRate(int milliBucketsPerTick) {
        int clamped = Math.min(Math.max(milliBucketsPerTick, 0), maxFluidTransferRate);
        if (this.transferRate != clamped) {
            this.transferRate = clamped;
            syncDataHolder.markClientSyncFieldDirty("transferRate");
        }
    }

    @Override
    public void setBucketMode(BucketMode bucketMode) {
        var oldMultiplier = this.bucketMode.multiplier;
        var newMultiplier = bucketMode.multiplier;

        if (this.bucketMode == bucketMode) {
            configureTransferRateLDLib2Input(oldMultiplier, newMultiplier);
            return;
        }
        this.bucketMode = bucketMode;
        syncDataHolder.markClientSyncFieldDirty("bucketMode");
        configureTransferRateLDLib2Input(oldMultiplier, newMultiplier);
    }

    @Override
    public void setManualIOMode(ManualIOMode manualIOMode) {
        if (this.manualIOMode != manualIOMode) {
            this.manualIOMode = manualIOMode;
            syncDataHolder.markClientSyncFieldDirty("manualIOMode");
        }
    }

    protected void update() {
        long timer = coverHolder.getOffsetTimer();
        if (timer % 5 != 0)
            return;

        if (mBLeftToTransferLastSecond > 0) {
            int platformTransferredFluid = doTransferFluids(mBLeftToTransferLastSecond);
            this.mBLeftToTransferLastSecond -= platformTransferredFluid;
        }

        if (timer % 20 == 0) {
            this.mBLeftToTransferLastSecond = transferRate * 20;
        }

        subscriptionHandler.updateSubscription();
    }

    private int doTransferFluids(int platformTransferLimit) {
        var adjacent = getAdjacentFluidHandler();
        var adjacentModifiable = adjacent instanceof IFluidHandlerModifiable modifiable ? modifiable :
                new ModifiableFluidHandlerWrapper(adjacent);
        var ownFluidHandler = getOwnFluidHandler();

        if (adjacent != null && ownFluidHandler != null) {
            return switch (io) {
                case IN -> doTransferFluidsInternal(adjacentModifiable, ownFluidHandler, platformTransferLimit);
                case OUT -> doTransferFluidsInternal(ownFluidHandler, adjacentModifiable, platformTransferLimit);
                default -> 0;
            };
        }
        return 0;
    }

    protected int doTransferFluidsInternal(IFluidHandlerModifiable source, IFluidHandlerModifiable destination,
                                           int platformTransferLimit) {
        return transferAny(source, destination, platformTransferLimit);
    }

    protected int transferAny(IFluidHandlerModifiable source, IFluidHandlerModifiable destination,
                              int platformTransferLimit) {
        return GTTransferUtils.transferFluidsFiltered(source, destination, filterHandler.getFilter(),
                platformTransferLimit);
    }

    protected enum TransferDirection {
        INSERT,
        EXTRACT
    }

    protected Object2LongMap<FluidStack> enumerateDistinctFluids(IFluidHandlerModifiable fluidHandler,
                                                                 TransferDirection direction) {
        // Long map because we could have multiple tanks of the same fluid summing up to > Integer.MAX_VALUE
        var summedFluids = new Object2LongOpenHashMap<FluidStack>();
        for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
            if (!canTransfer(fluidHandler, direction, tank)) continue;

            FluidStack fluidStack = fluidHandler.getFluidInTank(tank);
            if (fluidStack.isEmpty()) continue;

            summedFluids.addTo(fluidStack, fluidStack.getAmount());
        }

        return summedFluids;
    }

    private static boolean canTransfer(IFluidHandlerModifiable fluidHandler, TransferDirection direction, int tank) {
        return switch (direction) {
            case INSERT -> fluidHandler.supportsFill(tank);
            case EXTRACT -> fluidHandler.supportsDrain(tank);
        };
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
        transferRateLDLib2Input = new GTIntInputElement(10, 20, 134, 20,
                this::getCurrentBucketModeTransferRate,
                value -> setLDLib2CurrentBucketModeTransferRate(player, holder, value))
                .setMin(0);
        configureTransferRateLDLib2Input(bucketMode.multiplier, bucketMode.multiplier);
        root.addChild(transferRateLDLib2Input);

        root.addChild(GTEnumSelectorElement.selectable(146, 20, 20, 20, getAvailableBucketModes(),
                this::getBucketMode, mode -> setLDLib2BucketMode(player, holder, mode))
                .setTooltipSupplier(this::getBucketModeTooltip));
        root.addChild(GTEnumSelectorElement.selectable(10, 45, 20, 20, List.of(IO.IN, IO.OUT), this::getIo,
                mode -> setLDLib2Io(player, holder, mode)));
        root.addChild(GTEnumSelectorElement.selectable(146, 107, 20, 20, ManualIOMode.VALUES,
                this::getManualIOMode, mode -> setLDLib2ManualIOMode(player, holder, mode)));

        root.addChild(filterHandler.createFilterSlotLDLib2UI(125, 108));
        root.addChild(filterHandler.createFilterConfigLDLib2UI(10, 72, 156, 60));
        buildAdditionalLDLib2UI(root, player, holder);
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7, 137, true));
        return UI.of(root);
    }

    private List<Component> getBucketModeTooltip(BucketMode mode, String langKey) {
        return List.of(
                Component.translatable(langKey).append(Component.translatable("gtpm.gui.content.units.per_tick")));
    }

    private List<BucketMode> getAvailableBucketModes() {
        return Arrays.stream(BucketMode.values()).filter(mode -> mode.multiplier <= maxFluidTransferRate).toList();
    }

    private int getCurrentBucketModeTransferRate() {
        return this.transferRate / this.bucketMode.multiplier;
    }

    private void setCurrentBucketModeTransferRate(int transferRate) {
        long milliBucketsPerTick = (long) transferRate * this.bucketMode.multiplier;
        this.setTransferRate((int) Math.min(milliBucketsPerTick, maxFluidTransferRate));
    }

    private void configureTransferRateLDLib2Input(int oldMultiplier, int newMultiplier) {
        if (transferRateLDLib2Input == null) return;

        if (oldMultiplier > newMultiplier) {
            transferRateLDLib2Input.setValue(getCurrentBucketModeTransferRate());
        }

        transferRateLDLib2Input.setMax(maxFluidTransferRate / bucketMode.multiplier);

        if (newMultiplier > oldMultiplier) {
            transferRateLDLib2Input.setValue(getCurrentBucketModeTransferRate());
        }
    }

    @NotNull
    protected String getUITitle() {
        return "cover.pump.title";
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

    private void setLDLib2CurrentBucketModeTransferRate(Player player, UICoverHolder holder, int transferRate) {
        setCurrentBucketModeTransferRate(transferRate);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2BucketMode(Player player, UICoverHolder holder, BucketMode mode) {
        setBucketMode(mode);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2Io(Player player, UICoverHolder holder, IO io) {
        setIo(io);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2ManualIOMode(Player player, UICoverHolder holder, ManualIOMode mode) {
        setManualIOMode(mode);
        sendLDLib2ConfigAction(player, holder);
    }

    private void sendLDLib2ConfigAction(Player player, UICoverHolder holder) {
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, PumpCoverConfigActions.createSetConfigAction(getTransferRate(), getIo(),
                    getBucketMode(), getManualIOMode()));
        }
    }

    /////////////////////////////////////
    // *** CAPABILITY OVERRIDE ***//
    /////////////////////////////////////

    private CoverableFluidHandlerWrapper fluidHandlerWrapper;

    @Nullable
    @Override
    public IFluidHandlerModifiable getFluidHandlerCap(@Nullable IFluidHandlerModifiable defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        if (fluidHandlerWrapper == null || fluidHandlerWrapper.delegate != defaultValue) {
            this.fluidHandlerWrapper = new CoverableFluidHandlerWrapper(defaultValue);
        }
        return fluidHandlerWrapper;
    }

    private class CoverableFluidHandlerWrapper extends FluidHandlerDelegate {

        public CoverableFluidHandlerWrapper(IFluidHandlerModifiable delegate) {
            super(delegate);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (io == IO.OUT) {
                if (manualIOMode == ManualIOMode.DISABLED) {
                    return 0;
                }
                if (manualIOMode == ManualIOMode.UNFILTERED) {
                    return super.fill(resource, action);
                }
            }
            if (!filterHandler.test(resource)) {
                return 0;
            }
            return super.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (io == IO.IN) {
                if (manualIOMode == ManualIOMode.DISABLED) {
                    return FluidStack.EMPTY;
                }
                if (manualIOMode == ManualIOMode.UNFILTERED) {
                    return super.drain(resource, action);
                }
            }
            if (!filterHandler.test(resource)) {
                return FluidStack.EMPTY;
            }
            return super.drain(resource, action);
        }
    }

    @Override
    public DataComponentMap copyConfig(HolderLookup.Provider registries) {
        return ConfigCopyHelper.withFields(super.copyConfig(registries), fields -> fields
                .put(SyncFieldData.key("transferRate"),
                        ConfigCopyHelper.intValue(getTransferRate()))
                .put(SyncFieldData.key("io"),
                        ConfigCopyHelper.intValue(getIo().ordinal()))
                .put(SyncFieldData.key("manualIO"),
                        ConfigCopyHelper.intValue(getManualIOMode().ordinal()))
                .put(SyncFieldData.key("filter"),
                        ConfigCopyHelper.encodeItem(registries, filterHandler.getFilterItem()))
                .put(SyncFieldData.key("bucketMode"),
                        ConfigCopyHelper.intValue(getBucketMode().ordinal())));
    }

    @Override
    public void pasteConfig(ServerPlayer player, HolderLookup.Provider registries, DataComponentMap config) {
        setTransferRate(ConfigCopyHelper.getInt(config, "transferRate"));
        setIo(IO.values()[ConfigCopyHelper.getInt(config, "io")]);
        setManualIOMode(ManualIOMode.values()[ConfigCopyHelper.getInt(config, "manualIO")]);
        filterHandler
                .setFilterItem(ConfigCopyHelper.decodeItem(registries, ConfigCopyHelper.getField(config, "filter")));
        setBucketMode(BucketMode.values()[ConfigCopyHelper.getInt(config, "bucketMode")]);
        super.pasteConfig(player, registries, config);
    }
}
