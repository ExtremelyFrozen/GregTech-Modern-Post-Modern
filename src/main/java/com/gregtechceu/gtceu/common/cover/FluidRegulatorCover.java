package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleFluidFilter;
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
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.common.cover.data.BucketMode;
import com.gregtechceu.gtceu.common.cover.data.TransferMode;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FluidRegulatorCover extends PumpCover {

    private static final int MAX_STACK_SIZE = 2_048_000_000; // Capacity of quantum tank IX
    private static final ResourceLocation SET_FLUID_REGULATOR_COVER_CONFIG_ACTION = GTCEu
            .id("set_fluid_regulator_cover_config");
    private static final ResourceLocation TRANSFER_MODE_FIELD = SyncFieldData.key("transferMode");
    private static final ResourceLocation TRANSFER_LIMIT_FIELD = SyncFieldData.key("transferLimit");
    private static final ResourceLocation TRANSFER_BUCKET_FIELD = SyncFieldData.key("transferBucket");

    static {
        SyncActionDispatchers.server().register(new FluidRegulatorCoverConfigActionHandler());
    }

    @SaveField
    @SyncToClient
    @Getter
    private TransferMode transferMode = TransferMode.TRANSFER_ANY;

    @SaveField
    @SyncToClient
    @Getter
    private BucketMode transferBucketMode = BucketMode.MILLI_BUCKET;
    @SaveField
    @SyncToClient
    @Getter
    protected int globalTransferLimit;
    protected int fluidTransferBuffered = 0;

    private @Nullable GTIntInputElement transferSizeLDLib2Input;
    private @Nullable GTEnumSelectorElement<BucketMode> transferBucketModeLDLib2Input;

    public FluidRegulatorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier,
                               int maxTransferRate) {
        super(definition, coverHolder, attachedSide, tier, maxTransferRate);
    }

    public FluidRegulatorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier) {
        this(definition, coverHolder, attachedSide, tier, PUMP_SCALING.applyAsInt(tier));
    }

    //////////////////////////////////////
    // ***** Transfer Logic ******//
    //////////////////////////////////////

    @Override
    protected int doTransferFluidsInternal(IFluidHandlerModifiable source, IFluidHandlerModifiable destination,
                                           int platformTransferLimit) {
        return switch (transferMode) {
            case TRANSFER_ANY -> transferAny(source, destination, platformTransferLimit);
            case TRANSFER_EXACT -> transferExact(source, destination, platformTransferLimit);
            case KEEP_EXACT -> keepExact(source, destination, platformTransferLimit);
        };
    }

    private int transferExact(IFluidHandler source, IFluidHandler destination, int platformTransferLimit) {
        int fluidLeftToTransfer = platformTransferLimit;

        for (int slot = 0; slot < source.getTanks(); slot++) {
            if (fluidLeftToTransfer <= 0)
                break;

            FluidStack sourceFluid = source.getFluidInTank(slot).copy();
            int supplyAmount = getFilteredFluidAmount(sourceFluid);

            // If the remaining transferrable amount in this operation is not enough to transfer the full stack size,
            // the remaining amount for this operation will be buffered and added to the next operation's maximum.
            if (fluidLeftToTransfer + fluidTransferBuffered < supplyAmount) {
                this.fluidTransferBuffered += fluidLeftToTransfer;
                fluidLeftToTransfer = 0;
                break;
            }

            if (sourceFluid.isEmpty() || supplyAmount <= 0)
                continue;

            sourceFluid.setAmount(supplyAmount);
            FluidStack drained = source.drain(sourceFluid, FluidAction.SIMULATE);

            if (drained.isEmpty() || drained.getAmount() < supplyAmount)
                continue;

            int insertableAmount = destination.fill(drained.copy(), FluidAction.SIMULATE);
            if (insertableAmount != supplyAmount)
                continue;

            drained.setAmount(insertableAmount);
            drained = source.drain(drained, FluidAction.EXECUTE);

            if (!drained.isEmpty()) {
                destination.fill(drained, FluidAction.EXECUTE);
                fluidLeftToTransfer -= (drained.getAmount() - fluidTransferBuffered);
            }

            fluidTransferBuffered = 0;
        }

        return platformTransferLimit - fluidLeftToTransfer;
    }

    private int keepExact(IFluidHandlerModifiable source, IFluidHandlerModifiable destination,
                          int platformTransferLimit) {
        int fluidLeftToTransfer = platformTransferLimit;

        var sourceAmounts = enumerateDistinctFluids(source, TransferDirection.EXTRACT);
        var destinationAmounts = enumerateDistinctFluids(destination, TransferDirection.INSERT);

        for (FluidStack fluidStack : sourceAmounts.keySet()) {
            if (fluidLeftToTransfer <= 0) break;

            int amountToKeep = getFilteredFluidAmount(fluidStack);
            long amountInDest = destinationAmounts.getOrDefault(fluidStack, 0);
            if (amountInDest >= amountToKeep) continue;

            FluidStack fluidToMove = fluidStack.copy();
            fluidToMove.setAmount(Math.min(fluidLeftToTransfer, (int) (amountToKeep - amountInDest)));
            if (fluidToMove.getAmount() <= 0) continue;

            FluidStack drained = source.drain(fluidToMove, FluidAction.SIMULATE);
            int fillableAmount = destination.fill(drained, FluidAction.SIMULATE);
            if (fillableAmount <= 0) continue;

            fluidToMove.setAmount(Math.min(fluidToMove.getAmount(), fillableAmount));

            drained = source.drain(fluidToMove, FluidAction.EXECUTE);
            int movedAmount = destination.fill(drained, FluidAction.EXECUTE);

            fluidLeftToTransfer -= movedAmount;
        }

        return platformTransferLimit - fluidLeftToTransfer;
    }

    private void setTransferBucketMode(BucketMode transferBucketMode) {
        var oldMultiplier = this.transferBucketMode.multiplier;
        var newMultiplier = transferBucketMode.multiplier;

        if (this.transferBucketMode == transferBucketMode) {
            configureTransferBucketModeInput(oldMultiplier, newMultiplier);
            return;
        }
        this.transferBucketMode = transferBucketMode;
        syncDataHolder.markClientSyncFieldDirty("transferBucketMode");
        configureTransferBucketModeInput(oldMultiplier, newMultiplier);
    }

    private void setTransferMode(TransferMode transferMode) {
        if (this.transferMode == transferMode) {
            configureTransferSizeInput();
            return;
        }
        this.transferMode = transferMode;

        configureTransferSizeInput();

        if (!coverHolder.isRemote()) {
            syncDataHolder.markClientSyncFieldDirty("transferMode");
            configureFilter();
        }
    }

    @Override
    protected void configureFilter() {
        if (filterHandler.getFilter() instanceof SimpleFluidFilter filter) {
            filter.setMaxStackSize(transferMode == TransferMode.TRANSFER_ANY ? 1 : MAX_STACK_SIZE);
        }

        configureTransferSizeInput();
    }

    private int getFilteredFluidAmount(FluidStack fluidStack) {
        if (!filterHandler.isFilterPresent())
            return globalTransferLimit;

        FluidFilter filter = filterHandler.getFilter();
        return (filter.supportsAmounts() ? filter.testFluidAmount(fluidStack) : globalTransferLimit);
    }

    ///////////////////////////
    // ***** GUI ******//
    ///////////////////////////

    @Override
    protected @NotNull String getUITitle() {
        return "cover.fluid_regulator.title";
    }

    @Override
    protected void buildAdditionalLDLib2UI(UIElement root, Player player, UICoverHolder holder) {
        root.addChild(GTEnumSelectorElement.selectable(146, 45, 20, 20, TransferMode.values(),
                this::getTransferMode, mode -> setLDLib2TransferMode(player, holder, mode)));

        this.transferSizeLDLib2Input = new GTIntInputElement(35, 45, 84, 20,
                this::getCurrentBucketModeTransferSize,
                value -> setLDLib2CurrentBucketModeTransferSize(player, holder, value)).setMin(0)
                .setMax(Integer.MAX_VALUE);
        configureTransferSizeInput();
        root.addChild(this.transferSizeLDLib2Input);

        this.transferBucketModeLDLib2Input = GTEnumSelectorElement.selectable(121, 45, 20, 20, BucketMode.values(),
                this::getTransferBucketMode, mode -> setLDLib2TransferBucketMode(player, holder, mode));
        root.addChild(this.transferBucketModeLDLib2Input);
        configureTransferBucketModeInput(transferBucketMode.multiplier, transferBucketMode.multiplier);
        configureTransferSizeInput();
    }

    private int getCurrentBucketModeTransferSize() {
        return this.globalTransferLimit / this.transferBucketMode.multiplier;
    }

    private void setCurrentBucketModeTransferSize(int transferSize) {
        long transferLimit = (long) transferSize * this.transferBucketMode.multiplier;
        setGlobalTransferLimit((int) Math.min(transferLimit, MAX_STACK_SIZE));
    }

    private void setGlobalTransferLimit(int transferLimit) {
        int clamped = Math.min(Math.max(transferLimit, 0), MAX_STACK_SIZE);
        if (this.globalTransferLimit != clamped) {
            this.globalTransferLimit = clamped;
            syncDataHolder.markClientSyncFieldDirty("globalTransferLimit");
        }
        configureTransferSizeInput();
    }

    private void configureTransferBucketModeInput(int oldMultiplier, int newMultiplier) {
        if (transferSizeLDLib2Input == null) return;

        if (oldMultiplier > newMultiplier) {
            transferSizeLDLib2Input.setValue(getCurrentBucketModeTransferSize());
        }
        this.transferSizeLDLib2Input.setMax(MAX_STACK_SIZE / this.transferBucketMode.multiplier);
        if (newMultiplier > oldMultiplier) {
            transferSizeLDLib2Input.setValue(getCurrentBucketModeTransferSize());
        }
    }

    private void configureTransferSizeInput() {
        if (this.transferSizeLDLib2Input == null || transferBucketModeLDLib2Input == null)
            return;

        this.transferSizeLDLib2Input.setVisible(shouldShowTransferSize());
        this.transferBucketModeLDLib2Input.setVisible(shouldShowTransferSize());
    }

    private boolean shouldShowTransferSize() {
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

    private void setLDLib2TransferBucketMode(Player player, UICoverHolder holder, BucketMode mode) {
        setTransferBucketMode(mode);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2CurrentBucketModeTransferSize(Player player, UICoverHolder holder, int transferSize) {
        setCurrentBucketModeTransferSize(transferSize);
        sendLDLib2ConfigAction(player, holder);
    }

    private void sendLDLib2ConfigAction(Player player, UICoverHolder holder) {
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, createSetFluidRegulatorCoverConfigAction(getTransferMode(),
                    getGlobalTransferLimit(), getTransferBucketMode()));
        }
    }

    private static SyncActionData createSetFluidRegulatorCoverConfigAction(TransferMode transferMode,
                                                                           int transferLimit,
                                                                           BucketMode transferBucketMode) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TRANSFER_MODE_FIELD, new JsonPrimitive(transferMode.ordinal()))
                        .put(TRANSFER_LIMIT_FIELD, new JsonPrimitive(transferLimit))
                        .put(TRANSFER_BUCKET_FIELD, new JsonPrimitive(transferBucketMode.ordinal()))
                        .build())
                .build();
        return new SyncActionData(SET_FLUID_REGULATOR_COVER_CONFIG_ACTION, 0, payload);
    }

    @Override
    public DataComponentMap copyConfig(HolderLookup.Provider registries) {
        return ConfigCopyHelper.withFields(super.copyConfig(registries), fields -> fields
                .put(SyncFieldData.key("transferMode"),
                        ConfigCopyHelper.intValue(transferMode.ordinal()))
                .put(SyncFieldData.key("transferLimit"),
                        ConfigCopyHelper.intValue(globalTransferLimit))
                .put(SyncFieldData.key("transferBucket"),
                        ConfigCopyHelper.intValue(transferBucketMode.ordinal())));
    }

    @Override
    public void pasteConfig(ServerPlayer player, HolderLookup.Provider registries, DataComponentMap config) {
        setTransferMode(TransferMode.values()[ConfigCopyHelper.getInt(config, "transferMode")]);
        setGlobalTransferLimit(ConfigCopyHelper.getInt(config, "transferLimit"));
        setTransferBucketMode(BucketMode.values()[ConfigCopyHelper.getInt(config, "transferBucket")]);
        super.pasteConfig(player, registries, config);
    }

    private static final class FluidRegulatorCoverConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_FLUID_REGULATOR_COVER_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof FluidRegulatorCover;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null &&
                    isValidOrdinal(fields, TRANSFER_MODE_FIELD, TransferMode.values().length) &&
                    isValidNonNegativeInt(fields, TRANSFER_LIMIT_FIELD) &&
                    isValidOrdinal(fields, TRANSFER_BUCKET_FIELD, BucketMode.values().length);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof FluidRegulatorCover cover)) {
                throw new IllegalStateException(
                        "Fluid regulator cover config action received a non-fluid-regulator cover.");
            }
            cover.setTransferMode(TransferMode.values()[requireOrdinal(context.payload(), TRANSFER_MODE_FIELD,
                    TransferMode.values().length)]);
            cover.setGlobalTransferLimit(requireNonNegativeInt(context.payload(), TRANSFER_LIMIT_FIELD));
            cover.setTransferBucketMode(BucketMode.values()[requireOrdinal(context.payload(), TRANSFER_BUCKET_FIELD,
                    BucketMode.values().length)]);
        }
    }

    private static boolean isValidOrdinal(SyncFieldData fields, ResourceLocation field, int valueCount) {
        Integer ordinal = readInt(fields, field);
        return ordinal != null && ordinal >= 0 && ordinal < valueCount;
    }

    private static int requireOrdinal(DataComponentMap payload, ResourceLocation field, int valueCount) {
        int ordinal = requireNonNegativeInt(payload, field);
        if (ordinal >= valueCount) {
            throw new IllegalArgumentException(
                    "Fluid regulator cover config action ordinal is out of range: " + ordinal);
        }
        return ordinal;
    }

    private static boolean isValidNonNegativeInt(SyncFieldData fields, ResourceLocation field) {
        Integer value = readInt(fields, field);
        return value != null && value >= 0;
    }

    private static int requireNonNegativeInt(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Fluid regulator cover config action payload is missing field data.");
        }
        Integer value = readInt(fields, field);
        if (value == null) {
            throw new IllegalStateException(
                    "Fluid regulator cover config action payload is missing " + field + ".");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Fluid regulator cover config action value is negative: " + value);
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
