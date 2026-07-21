package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.datacomponents.AEInputConfigCopyData;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.multiblock.IMEStockingPart;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlotList;
import com.gregtechceu.gtceu.integration.ae2.utils.AEUtil;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Predicate;

public class MEStockingHatchPartMachine extends MEInputHatchPartMachine implements IMEStockingPart {

    private static final int CONFIG_SIZE = 16;

    @SyncToClient
    @SaveField
    @Getter
    private boolean autoPull;

    @Getter
    @SaveField
    @SyncBoth
    private int minStackSize = 1;

    @Getter
    @SaveField
    @SyncBoth
    private int ticksPerCycle = 40;

    @Setter
    private Predicate<GenericStack> autoPullTest;

    public MEStockingHatchPartMachine(BlockEntityCreationInfo info) {
        super(info);
        this.autoPullTest = $ -> false;
    }

    /////////////////////////////////
    // ***** Machine LifeCycle ****//
    /////////////////////////////////

    @Override
    public void addedToController(MultiblockControllerMachine controller, String structureName) {
        super.addedToController(controller, structureName);
        IMEStockingPart.super.addedToController(controller, structureName);
    }

    @Override
    public void removedFromController(MultiblockControllerMachine controller, String structureName) {
        IMEStockingPart.super.removedFromController(controller, structureName);
        super.removedFromController(controller, structureName);
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots) {
        this.aeFluidHandler = new ExportOnlyAEStockingFluidList(this, CONFIG_SIZE);
        return this.aeFluidHandler;
    }

    /////////////////////////////////
    // ********** Sync ME *********//
    /////////////////////////////////

    @Override
    public void autoIO() {
        super.autoIO();
        if (ticksPerCycle == 0) ticksPerCycle = ConfigHolder.INSTANCE.compat.ae2.updateIntervals; // Emergency Check to
                                                                                                  // Avoid Crash loops.
        if (getOffsetTimer() % ticksPerCycle == 0) {
            if (autoPull) {
                refreshList();
            }
            syncME();
        }
    }

    @Override
    protected void syncME() {
        MEStorage networkInv = getStockingFluidNetworkStorage();
        for (ExportOnlyAEFluidSlot slot : aeFluidHandler.getInventory()) {
            var config = slot.getConfig();
            if (config != null) {
                // Try to fill the slot
                var key = config.what();
                long extracted = networkInv.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
                if (extracted >= minStackSize) {
                    slot.setStock(new GenericStack(key, extracted));
                    continue;
                }
            }
            slot.setStock(null);
        }
    }

    MEStorage getStockingFluidNetworkStorage() {
        return getMainNode().getGrid().getStorageService().getInventory();
    }

    @Override
    protected void flushInventory() {
        // no-op, nothing to send back to the network
    }

    @Override
    public IConfigurableSlotList getSlotList() {
        return aeFluidHandler;
    }

    @Override
    public void setMinStackSize(int minStackSize) {
        this.minStackSize = minStackSize;
    }

    @Override
    public void setTicksPerCycle(int ticksPerCycle) {
        this.ticksPerCycle = ticksPerCycle;
    }

    @ServerFieldNormalizer(fieldName = "minStackSize")
    private int normalizeMinStackSize(int candidate) {
        if (candidate < 1) {
            throw new IllegalArgumentException("Auto-stocking minimum stack size must be at least one.");
        }
        return candidate;
    }

    @ServerFieldNormalizer(fieldName = "ticksPerCycle")
    private int normalizeTicksPerCycle(int candidate) {
        if (candidate < ConfigHolder.INSTANCE.compat.ae2.updateIntervals) {
            throw new IllegalArgumentException("Auto-stocking ticks per cycle is below the configured minimum.");
        }
        return candidate;
    }

    @Override
    public StockingTarget getStockingTarget() {
        return StockingTarget.FLUID;
    }

    @Override
    public boolean testConfiguredInOtherPart(@Nullable GenericStack config) {
        if (config == null) return false;
        if (!isFormed()) return false;

        for (MultiblockControllerMachine controller : getControllers()) {
            for (IMultiPart part : controller.getParts()) {
                if (part instanceof MEStockingHatchPartMachine hatch) {
                    if (hatch == this) continue;
                    if (hatch.aeFluidHandler.hasStackInConfig(config, false)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void setAutoPull(boolean autoPull) {
        if (this.autoPull == autoPull) {
            return;
        }
        this.autoPull = autoPull;
        if (!isRemote()) {
            syncDataHolder.markClientSyncFieldDirty("autoPull");
            if (!this.autoPull) {
                this.aeFluidHandler.clearInventory(0);
            } else if (updateMEStatus()) {
                this.refreshList();
                updateTankSubscription();
            }
            refreshFluidConfigSnapshot();
        }
    }

    @Override
    public boolean isMEFluidConfigAutoPull() {
        return autoPull;
    }

    @Override
    public boolean isMEFluidStocking() {
        return true;
    }

    @Override
    public void setMEFluidAutoPull(boolean autoPull) {
        setAutoPull(autoPull);
    }

    @Override
    protected boolean isConfiguredInOtherStockingPart(@NotNull GenericStack stack) {
        return testConfiguredInOtherPart(stack);
    }

    private void refreshList() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            aeFluidHandler.clearInventory(0);
            return;
        }

        MEStorage networkStorage = grid.getStorageService().getInventory();
        var counter = networkStorage.getAvailableStacks();

        // Use a PriorityQueue to sort the stacks on size, take the first CONFIG_SIZE
        // biggest stacks.
        PriorityQueue<Object2LongMap.Entry<AEKey>> topFluids = new PriorityQueue<>(
                Comparator.comparingLong(Object2LongMap.Entry<AEKey>::getLongValue));

        for (Object2LongMap.Entry<AEKey> entry : counter) {
            long amount = entry.getLongValue();
            AEKey what = entry.getKey();

            if (amount <= 0) continue;
            if (!(what instanceof AEFluidKey fluidKey)) continue;

            long request = networkStorage.extract(what, amount, Actionable.SIMULATE, actionSource);
            if (request == 0) continue;

            // Ensure that it is valid to configure with this stack
            if (!autoPullTest.test(new GenericStack(fluidKey, amount))) continue;
            if (amount >= minStackSize) {
                if (topFluids.size() < CONFIG_SIZE) {
                    topFluids.offer(entry);
                } else if (amount > topFluids.peek().getLongValue()) {
                    topFluids.poll();
                    topFluids.offer(entry);
                }
            }
        }

        // Now, topFluids is a PQ with CONFIG_SIZE highest amount fluids in the system.
        int index;
        int fluidAmount = topFluids.size();
        for (index = 0; index < CONFIG_SIZE; index++) {
            if (topFluids.isEmpty()) break;
            Object2LongMap.Entry<AEKey> entry = topFluids.poll();
            AEKey what = entry.getKey();
            long amount = entry.getLongValue();

            // If we get here, the fluid has already been checked by the PQ.
            long request = networkStorage.extract(what, amount, Actionable.SIMULATE, actionSource);

            // Since we want our fluids to be displayed from highest to lowest, but poll() returns
            // the lowest first, we fill in the slots starting at fluidAmount-1
            var slot = this.aeFluidHandler.getInventory()[fluidAmount - index - 1];
            slot.setConfig(new GenericStack(what, 1));
            slot.setStock(new GenericStack(what, request));
        }

        aeFluidHandler.clearInventory(index);
    }

    ////////////////////////////////
    // ******* Interaction *******//
    ////////////////////////////////

    @Override
    protected InteractionResult onScrewdriverClick(ExtendedUseOnContext context) {
        if (!isRemote()) {
            setAutoPull(!autoPull);
            if (autoPull) {
                context.getPlayer().sendSystemMessage(
                        Component.translatable("gtpm.machine.me.stocking_auto_pull_enabled"));
            } else {
                context.getPlayer().sendSystemMessage(
                        Component.translatable("gtpm.machine.me.stocking_auto_pull_disabled"));
            }
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    ////////////////////////////////
    // ****** Configuration ******//
    ////////////////////////////////

    @Override
    protected AEInputConfigCopyData writeConfigData() {
        if (!autoPull) {
            return super.writeConfigData();
        }
        byte ghostCircuit = (byte) IntCircuitBehaviour.getCircuitConfiguration(circuitInventory.getStackInSlot(0));
        return new AEInputConfigCopyData(List.of(), ghostCircuit, false, true);
    }

    @Override
    protected void readConfigData(AEInputConfigCopyData data) {
        if (data.autoPull()) {
            this.setAutoPull(true);
            circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(data.ghostCircuit()));
            return;
        }
        this.setAutoPull(false);
        super.readConfigData(data);
    }

    private class ExportOnlyAEStockingFluidList extends ExportOnlyAEFluidList {

        public ExportOnlyAEStockingFluidList(MetaMachine holder, int slots) {
            super(holder, slots, ExportOnlyAEStockingFluidSlot::new);
        }

        @Override
        public boolean isAutoPull() {
            return autoPull;
        }

        @Override
        public boolean isStocking() {
            return true;
        }

        @Override
        public boolean hasStackInConfig(GenericStack stack, boolean checkExternal) {
            boolean inThisHatch = super.hasStackInConfig(stack, false);
            if (inThisHatch) return true;
            if (checkExternal) {
                return testConfiguredInOtherPart(stack);
            }
            return false;
        }
    }

    private class ExportOnlyAEStockingFluidSlot extends ExportOnlyAEFluidSlot {

        public ExportOnlyAEStockingFluidSlot() {
            super();
        }

        public ExportOnlyAEStockingFluidSlot(@Nullable GenericStack config, @Nullable GenericStack stock) {
            super(config, stock);
        }

        @Override
        public ExportOnlyAEFluidSlot copy() {
            return new ExportOnlyAEStockingFluidSlot(
                    this.config == null ? null : copy(this.config),
                    this.stock == null ? null : copy(this.stock));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (this.stock != null && this.config != null) {
                // Extract the items from the real net to either validate (simulate)
                // or extract (modulate) when this is called
                if (!isOnline()) return FluidStack.EMPTY;
                MEStorage aeNetwork = getStockingFluidNetworkStorage();

                Actionable actionable = action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE;
                if (!(config.what() instanceof AEFluidKey key)) {
                    throw new IllegalStateException("Stocking fluid slot contained a non-fluid configuration key.");
                }
                long extracted = aeNetwork.extract(key, maxDrain, actionable, actionSource);

                if (extracted > 0) {
                    FluidStack resultStack = AEUtil.toFluidStack(key, extracted);
                    if (action.execute()) {
                        long remaining = aeNetwork.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
                        this.stock = remaining > 0 ? new GenericStack(key, remaining) : null;
                        this.onContentsChanged.run();
                    }
                    return resultStack;
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
