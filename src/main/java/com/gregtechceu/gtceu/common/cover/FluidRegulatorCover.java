package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleFluidFilter;
import com.gregtechceu.gtceu.api.gui.element.GTEnumSelectorElement;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.common.cover.data.BucketMode;
import com.gregtechceu.gtceu.common.cover.data.TransferMode;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FluidRegulatorCover extends PumpCover implements FluidRegulatorCoverConfigActionTarget {

    private static final int MAX_STACK_SIZE = 2_048_000_000; // Capacity of quantum tank IX

    static {
        FluidRegulatorCoverConfigActions.initialize();
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

    @Override
    public void setTransferBucketMode(BucketMode transferBucketMode) {
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

    @Override
    public void setTransferMode(TransferMode transferMode) {
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

    @Override
    public void setGlobalTransferLimit(int transferLimit) {
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
            CoverUIHelper.sendAction(holder, FluidRegulatorCoverConfigActions.createSetConfigAction(getTransferMode(),
                    getGlobalTransferLimit(), getTransferBucketMode()));
        }
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
}
