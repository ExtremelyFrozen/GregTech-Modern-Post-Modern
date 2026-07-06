package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
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
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerDelegate;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.api.transfer.fluid.ModifiableFluidHandlerWrapper;
import com.gregtechceu.gtceu.common.cover.data.BucketMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class PumpCover extends CoverBehavior implements IIOCover, LDLib2CoverUIProvider, IControllable {

    // .5b 2b 8b
    public static final Int2IntFunction PUMP_SCALING = tier -> 64 * (int) Math.pow(4, Math.min(tier - 1, GTValues.IV));
    private static final ResourceLocation SET_PUMP_COVER_CONFIG_ACTION = GTCEu.id("set_pump_cover_config");
    private static final ResourceLocation TRANSFER_RATE_FIELD = SyncFieldData.key("transferRate");
    private static final ResourceLocation IO_FIELD = SyncFieldData.key("io");
    private static final ResourceLocation BUCKET_MODE_FIELD = SyncFieldData.key("bucketMode");
    private static final ResourceLocation MANUAL_IO_FIELD = SyncFieldData.key("manualIO");

    static {
        SyncActionDispatchers.server().register(new PumpCoverConfigActionHandler());
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

    public void setTransferRate(int milliBucketsPerTick) {
        int clamped = Math.min(Math.max(milliBucketsPerTick, 0), maxFluidTransferRate);
        if (this.transferRate != clamped) {
            this.transferRate = clamped;
            syncDataHolder.markClientSyncFieldDirty("transferRate");
        }
    }

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

    protected void setManualIOMode(ManualIOMode manualIOMode) {
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

        root.addChild(new GTEnumSelectorElement<>(146, 20, 20, 20, getAvailableBucketModes(), this::getBucketMode,
                mode -> setLDLib2BucketMode(player, holder, mode), BucketMode::getIcon, BucketMode::getTooltip)
                .setTooltipSupplier(this::getBucketModeTooltip));
        root.addChild(new GTEnumSelectorElement<>(10, 45, 20, 20, List.of(IO.IN, IO.OUT), this::getIo,
                mode -> setLDLib2Io(player, holder, mode), IO::getIcon, IO::getTooltip));
        root.addChild(new GTEnumSelectorElement<>(146, 107, 20, 20, ManualIOMode.VALUES, this::getManualIOMode,
                mode -> setLDLib2ManualIOMode(player, holder, mode), ManualIOMode::getIcon,
                ManualIOMode::getTooltip));

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
            CoverUIHelper.sendAction(holder, createSetPumpCoverConfigAction(getTransferRate(), getIo(),
                    getBucketMode(), getManualIOMode()));
        }
    }

    private static SyncActionData createSetPumpCoverConfigAction(int transferRate, IO io, BucketMode bucketMode,
                                                                 ManualIOMode manualIOMode) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TRANSFER_RATE_FIELD, new JsonPrimitive(transferRate))
                        .put(IO_FIELD, new JsonPrimitive(io.ordinal()))
                        .put(BUCKET_MODE_FIELD, new JsonPrimitive(bucketMode.ordinal()))
                        .put(MANUAL_IO_FIELD, new JsonPrimitive(manualIOMode.ordinal()))
                        .build())
                .build();
        return new SyncActionData(SET_PUMP_COVER_CONFIG_ACTION, 0, payload);
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

    private static final class PumpCoverConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_PUMP_COVER_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof PumpCover;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null &&
                    isValidNonNegativeInt(fields, TRANSFER_RATE_FIELD) &&
                    isValidIOOrdinal(fields, IO_FIELD) &&
                    isValidOrdinal(fields, BUCKET_MODE_FIELD, BucketMode.values().length) &&
                    isValidOrdinal(fields, MANUAL_IO_FIELD, ManualIOMode.VALUES.length);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            if (player.isSpectator()) {
                return false;
            }
            if (!(context.holder() instanceof PumpCover cover)) {
                return false;
            }
            int bucketModeOrdinal = requireOrdinal(context.payload(), BUCKET_MODE_FIELD, BucketMode.values().length);
            return BucketMode.values()[bucketModeOrdinal].multiplier <= cover.maxFluidTransferRate;
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof PumpCover cover)) {
                throw new IllegalStateException("Pump cover config action received a non-pump cover.");
            }
            cover.setTransferRate(requireNonNegativeInt(context.payload(), TRANSFER_RATE_FIELD));
            cover.setIo(IO.values()[requireIOOrdinal(context.payload(), IO_FIELD)]);
            cover.setBucketMode(BucketMode.values()[requireOrdinal(context.payload(), BUCKET_MODE_FIELD,
                    BucketMode.values().length)]);
            cover.setManualIOMode(ManualIOMode.VALUES[requireOrdinal(context.payload(), MANUAL_IO_FIELD,
                    ManualIOMode.VALUES.length)]);
        }
    }

    private static boolean isValidNonNegativeInt(SyncFieldData fields, ResourceLocation field) {
        Integer value = readInt(fields, field);
        return value != null && value >= 0;
    }

    private static boolean isValidIOOrdinal(SyncFieldData fields, ResourceLocation field) {
        Integer ordinal = readInt(fields, field);
        return ordinal != null && isImportExportOrdinal(ordinal);
    }

    private static int requireIOOrdinal(DataComponentMap payload, ResourceLocation field) {
        int ordinal = requireNonNegativeInt(payload, field);
        if (!isImportExportOrdinal(ordinal)) {
            throw new IllegalArgumentException("Pump cover config action IO ordinal is out of range: " + ordinal);
        }
        return ordinal;
    }

    private static boolean isImportExportOrdinal(int ordinal) {
        return ordinal == IO.IN.ordinal() || ordinal == IO.OUT.ordinal();
    }

    private static boolean isValidOrdinal(SyncFieldData fields, ResourceLocation field, int valueCount) {
        Integer ordinal = readInt(fields, field);
        return ordinal != null && ordinal >= 0 && ordinal < valueCount;
    }

    private static int requireOrdinal(DataComponentMap payload, ResourceLocation field, int valueCount) {
        int ordinal = requireNonNegativeInt(payload, field);
        if (ordinal >= valueCount) {
            throw new IllegalArgumentException("Pump cover config action ordinal is out of range: " + ordinal);
        }
        return ordinal;
    }

    private static int requireNonNegativeInt(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Pump cover config action payload is missing field data.");
        }
        Integer value = readInt(fields, field);
        if (value == null) {
            throw new IllegalStateException("Pump cover config action payload is missing " + field + ".");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Pump cover config action value is negative: " + value);
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
