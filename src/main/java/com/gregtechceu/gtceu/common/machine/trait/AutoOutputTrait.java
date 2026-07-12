package com.gregtechceu.gtceu.common.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.GridHighlightTexture;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.DirectionalAutoOutputMachine;
import com.gregtechceu.gtceu.api.machine.trait.*;
import com.gregtechceu.gtceu.api.machine.trait.feature.IFrontFacingTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IInteractionTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IRenderingTrait;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class AutoOutputTrait extends MachineTrait implements DirectionalAutoOutputMachine, IRenderingTrait,
                             IInteractionTrait,
                             IFrontFacingTrait {

    public static final MachineTraitType<AutoOutputTrait> TYPE = new MachineTraitType<>(AutoOutputTrait.class);

    @Getter
    protected final List<IItemHandler> itemHandlers;
    @Getter
    protected final List<IFluidHandler> fluidHandlers;

    @SaveField
    @SyncToClient
    @RerenderOnChanged
    protected @Nullable Direction itemOutputDirection, fluidOutputDirection;
    @Getter
    @SaveField
    @SyncBoth
    @RerenderOnChanged
    protected boolean autoOutputItems = false;
    @Getter
    @SaveField
    @SyncBoth
    @RerenderOnChanged
    protected boolean autoOutputFluids = false;
    @SaveField
    @SyncBoth
    protected boolean allowItemInputFromOutputSide = false;
    @SaveField
    @SyncBoth
    protected boolean allowFluidInputFromOutputSide = false;

    @Setter
    @Getter
    protected int ticksPerCycle = 5;
    @Setter
    protected Predicate<@Nullable Direction> itemOutputDirectionValidator = $ -> true;
    @Setter
    protected Predicate<@Nullable Direction> fluidOutputDirectionValidator = $ -> true;
    protected @Nullable TickableSubscription itemOutputSub, fluidOutputSub;
    protected List<ISubscription> itemSubs = new ArrayList<>();
    protected List<ISubscription> fluidSubs = new ArrayList<>();
    private final boolean useDefaultToolHandlers;

    public AutoOutputTrait(List<IItemHandler> itemHandlers, List<IFluidHandler> fluidHandlers,
                           boolean useDefaultToolHandlers) {
        super();

        this.itemHandlers = itemHandlers.stream().filter(h -> {
            if (h.getSlots() == 0) return false;
            if (h instanceof ICapabilityTrait cap) return cap.canCapOutput();
            return true;
        }).toList();
        this.fluidHandlers = fluidHandlers.stream().filter(h -> {
            if (h.getTanks() == 0) return false;
            if (h instanceof ICapabilityTrait cap) return cap.canCapOutput();
            return true;
        }).toList();
        this.useDefaultToolHandlers = useDefaultToolHandlers;
    }

    public AutoOutputTrait(List<IItemHandler> itemHandlers, List<IFluidHandler> fluidHandlers) {
        this(itemHandlers, fluidHandlers, true);
    }

    @Override
    public MachineTraitType<AutoOutputTrait> getTraitType() {
        return TYPE;
    }

    public static AutoOutputTrait ofItems(IItemHandler... itemHandlers) {
        return new AutoOutputTrait(Arrays.asList(itemHandlers), List.of());
    }

    public static AutoOutputTrait ofFluids(IFluidHandler... fluidHandlers) {
        return new AutoOutputTrait(List.of(), Arrays.asList(fluidHandlers));
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();

        Direction defaultOutputDirection = getMachine().hasFrontFacing() ? getMachine().getFrontFacing().getOpposite() :
                Direction.UP;
        if (itemOutputDirection == null) {
            this.itemOutputDirection = defaultOutputDirection;
        }
        if (fluidOutputDirection == null) {
            this.fluidOutputDirection = defaultOutputDirection;
        }

        getMachine().scheduleForNextServerTick(this::updateFluidOutputSubscription);
        getMachine().scheduleForNextServerTick(this::updateItemOutputSubscription);
        for (var handler : itemHandlers) {
            if (handler instanceof NotifiableItemStackHandler notifiable)
                itemSubs.add(notifiable.addChangedListener(this::updateItemOutputSubscription));
        }

        for (var handler : fluidHandlers) {
            if (handler instanceof NotifiableFluidTank notifiable)
                fluidSubs.add(notifiable.addChangedListener(this::updateFluidOutputSubscription));
        }
    }

    @Override
    public void onMachineUnload() {
        if (itemOutputSub != null) {
            itemOutputSub.unsubscribe();
            itemOutputSub = null;
        }
        if (fluidOutputSub != null) {
            fluidOutputSub.unsubscribe();
            fluidOutputSub = null;
        }
        itemSubs.forEach(ISubscription::unsubscribe);
        itemSubs.clear();
        fluidSubs.forEach(ISubscription::unsubscribe);
        fluidSubs.clear();
        super.onMachineUnload();
    }

    @Override
    public void onMachineNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        updateItemOutputSubscription();
        updateFluidOutputSubscription();
    }

    @Override
    public boolean supportsAutoOutputItems() {
        return !itemHandlers.isEmpty();
    }

    @Override
    public boolean supportsAutoOutputFluids() {
        return !fluidHandlers.isEmpty();
    }

    @Override
    public @Nullable Direction getItemOutputDirection() {
        return supportsAutoOutputItems() ? itemOutputDirection : null;
    }

    @Override
    public @Nullable Direction getFluidOutputDirection() {
        return supportsAutoOutputFluids() ? fluidOutputDirection : null;
    }

    @Override
    public boolean allowsItemInputFromOutputSide() {
        return allowItemInputFromOutputSide;
    }

    @Override
    public boolean allowsFluidInputFromOutputSide() {
        return allowFluidInputFromOutputSide;
    }

    @Override
    public void setAllowItemInputFromOutputSide(boolean allow) {
        this.allowItemInputFromOutputSide = allow;
    }

    @Override
    public void setAllowFluidInputFromOutputSide(boolean allow) {
        this.allowFluidInputFromOutputSide = allow;
    }

    @Override
    public void setAllowAutoOutputItems(boolean allow) {
        if (supportsAutoOutputItems()) {
            this.autoOutputItems = allow;
            updateItemOutputSubscription();
        }
    }

    @Override
    public void setAllowAutoOutputFluids(boolean allow) {
        if (supportsAutoOutputFluids()) {
            this.autoOutputFluids = allow;
            updateFluidOutputSubscription();
        }
    }

    @ServerFieldNormalizer(fieldName = "autoOutputItems")
    private boolean normalizeAutoOutputItems(boolean candidate) {
        if (!supportsAutoOutputItems()) {
            throw new IllegalArgumentException("Machine trait does not support item auto-output.");
        }
        return candidate;
    }

    @ServerFieldNormalizer(fieldName = "autoOutputFluids")
    private boolean normalizeAutoOutputFluids(boolean candidate) {
        if (!supportsAutoOutputFluids()) {
            throw new IllegalArgumentException("Machine trait does not support fluid auto-output.");
        }
        return candidate;
    }

    @ServerFieldNormalizer(fieldName = "allowItemInputFromOutputSide")
    private boolean normalizeAllowItemInputFromOutputSide(boolean candidate) {
        if (!supportsAutoOutputItems()) {
            throw new IllegalArgumentException("Machine trait does not support item output-side input.");
        }
        return candidate;
    }

    @ServerFieldNormalizer(fieldName = "allowFluidInputFromOutputSide")
    private boolean normalizeAllowFluidInputFromOutputSide(boolean candidate) {
        if (!supportsAutoOutputFluids()) {
            throw new IllegalArgumentException("Machine trait does not support fluid output-side input.");
        }
        return candidate;
    }

    @ServerFieldChangeListener(fieldName = "autoOutputItems")
    private void onAutoOutputItemsChanged(boolean oldValue, boolean newValue) {
        updateItemOutputSubscription();
    }

    @ServerFieldChangeListener(fieldName = "autoOutputFluids")
    private void onAutoOutputFluidsChanged(boolean oldValue, boolean newValue) {
        updateFluidOutputSubscription();
    }

    @Override
    public boolean canSetFluidOutputDirection(@Nullable Direction outputFacing) {
        return supportsAutoOutputFluids() && fluidOutputDirectionValidator.test(outputFacing) &&
                (!getMachine().hasFrontFacing() || getMachine().getFrontFacing() != outputFacing);
    }

    @Override
    public void setFluidOutputDirection(@Nullable Direction outputFacing) {
        if (canSetFluidOutputDirection(outputFacing)) {
            this.fluidOutputDirection = outputFacing;
            syncDataHolder.markClientSyncFieldDirty("outputFacingFluids");
            updateFluidOutputSubscription();
        }
    }

    @Override
    public boolean canSetItemOutputDirection(@Nullable Direction outputFacing) {
        return supportsAutoOutputItems() && itemOutputDirectionValidator.test(outputFacing) &&
                (!getMachine().hasFrontFacing() || getMachine().getFrontFacing() != outputFacing);
    }

    @Override
    public void setItemOutputDirection(@Nullable Direction outputFacing) {
        if (canSetItemOutputDirection(outputFacing)) {
            this.itemOutputDirection = outputFacing;
            syncDataHolder.markClientSyncFieldDirty("outputFacingItems");
            updateItemOutputSubscription();
        }
    }

    private boolean shouldKeepItemSubscription() {
        if (!supportsAutoOutputItems()) return false;

        if (!isAutoOutputItems() || getItemOutputDirection() == null ||
                !GTTransferUtils.hasAdjacentItemHandler(getLevel(), getBlockPos(), getItemOutputDirection()))
            return false;
        return true;
    }

    private boolean shouldKeepFluidSubscription() {
        if (!supportsAutoOutputFluids()) return false;
        if (!isAutoOutputFluids() || getFluidOutputDirection() == null ||
                !GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getBlockPos(), getFluidOutputDirection()))
            return false;
        return true;
    }

    protected void updateItemOutputSubscription() {
        if (shouldKeepItemSubscription()) {
            itemOutputSub = subscribeServerTick(itemOutputSub, this::autoOutputItems);
        } else if (itemOutputSub != null) {
            itemOutputSub.unsubscribe();
            itemOutputSub = null;
        }
    }

    protected void updateFluidOutputSubscription() {
        if (shouldKeepFluidSubscription()) {
            fluidOutputSub = subscribeServerTick(fluidOutputSub, this::autoOutputFluids);
        } else if (fluidOutputSub != null) {
            fluidOutputSub.unsubscribe();
            fluidOutputSub = null;
        }
    }

    protected void autoOutputItems() {
        if (getMachine().getOffsetTimer() % ticksPerCycle == 0 && getItemOutputDirection() != null) {
            itemHandlers.forEach(this::exportItemToNearby);
        }
        updateItemOutputSubscription();
    }

    protected void autoOutputFluids() {
        if (getMachine().getOffsetTimer() % ticksPerCycle == 0 && getFluidOutputDirection() != null) {
            fluidHandlers.forEach(this::exportFluidToNearby);
        }
        updateFluidOutputSubscription();
    }

    private void exportFluidToNearby(IFluidHandler handler) {
        var filter = getMachine().getFluidCapFilter(getFluidOutputDirection(), IO.OUT);
        GTTransferUtils.getAdjacentFluidHandler(getLevel(), getBlockPos(), getFluidOutputDirection())
                .ifPresent(adj -> GTTransferUtils.transferFluidsFiltered(handler, adj, filter));
    }

    private void exportItemToNearby(IItemHandler handler) {
        var filter = getMachine().getItemCapFilter(getItemOutputDirection(), IO.OUT);
        GTTransferUtils.getAdjacentItemHandler(getLevel(), getBlockPos(), getItemOutputDirection())
                .ifPresent(adj -> GTTransferUtils.transferItemsFiltered(handler, adj, filter));
    }

    @Override
    public boolean isValidFrontFace(Direction direction) {
        return direction != getItemOutputDirection() && direction != getFluidOutputDirection();
    }

    @Override
    public boolean shouldRenderGridOverlay(Player player, BlockPos pos, BlockState state, ItemStack held,
                                           Set<GTToolType> toolTypes) {
        return toolTypes.contains(GTToolType.SCREWDRIVER) || toolTypes.contains(GTToolType.WRENCH);
    }

    @Override
    public @Nullable GridHighlightTexture getGridOverlayIcon(Player player, BlockPos pos, BlockState state,
                                                             Set<GTToolType> toolTypes, ItemStack held,
                                                             Direction side) {
        if (toolTypes.contains(GTToolType.WRENCH)) {
            if (!player.isShiftKeyDown()) {
                if (!getMachine().hasFrontFacing() || side != getMachine().getFrontFacing()) {
                    var canSwitchItemOutputToSide = supportsAutoOutputItems() &&
                            itemOutputDirectionValidator.test(side) && side != getItemOutputDirection();
                    var canSwitchFluidOutputToSide = supportsAutoOutputFluids() &&
                            fluidOutputDirectionValidator.test(side) && side != getFluidOutputDirection();
                    if (canSwitchItemOutputToSide || canSwitchFluidOutputToSide)
                        return GridHighlightTexture.TOOL_IO_FACING_ROTATION;
                }
            }
        }
        if (toolTypes.contains(GTToolType.SCREWDRIVER)) {
            if (side == getItemOutputDirection() || side == getFluidOutputDirection()) {
                if (player.isShiftKeyDown()) return GridHighlightTexture.TOOL_ALLOW_INPUT;
                return GridHighlightTexture.TOOL_AUTO_OUTPUT;
            }
        }
        return null;
    }

    @Override
    public Pair<GTToolType, InteractionResult> onToolClick(ExtendedUseOnContext context) {
        var toolType = context.getToolType();
        if (useDefaultToolHandlers) {
            if (toolType.contains(GTToolType.WRENCH)) {
                return Pair.of(GTToolType.WRENCH, onWrenchClick(context));
            }
            if (toolType.contains(GTToolType.SCREWDRIVER)) {
                return Pair.of(GTToolType.SCREWDRIVER, onScrewdriverClick(context));
            }
        }
        return IInteractionTrait.super.onToolClick(context);
    }

    private InteractionResult onWrenchClick(ExtendedUseOnContext context) {
        var gridSide = context.getGridSide();

        boolean hasChanged = false;
        if (!itemHandlers.isEmpty()) {
            if ((!getMachine().hasFrontFacing() || gridSide != getMachine().getFrontFacing()) &&
                    itemOutputDirectionValidator.test(gridSide)) {
                setItemOutputDirection(gridSide);
                hasChanged = true;
            }
        }
        if (!fluidHandlers.isEmpty()) {
            if ((!getMachine().hasFrontFacing() || gridSide != getMachine().getFrontFacing()) &&
                    fluidOutputDirectionValidator.test(gridSide)) {
                setFluidOutputDirection(gridSide);
                hasChanged = true;
            }
        }
        return hasChanged ? InteractionResult.sidedSuccess(isRemote()) : InteractionResult.PASS;
    }

    private InteractionResult onScrewdriverClick(ExtendedUseOnContext context) {
        var player = context.getPlayer();
        var gridSide = context.getGridSide();

        boolean hasChanged = false;
        if (player.isShiftKeyDown()) {
            if (getItemOutputDirection() == gridSide) {
                setAllowItemInputFromOutputSide(!allowsItemInputFromOutputSide());
                player.displayClientMessage(Component
                        .translatable("gtpm.machine.basic.input_from_output_side." +
                                (allowsItemInputFromOutputSide() ? "allow" : "disallow"))
                        .append(Component.translatable("gtpm.creative.chest.item")), true);
                hasChanged = true;
            }

            if (getFluidOutputDirection() == gridSide) {
                setAllowFluidInputFromOutputSide(!allowsFluidInputFromOutputSide());
                player.displayClientMessage(Component
                        .translatable("gtpm.machine.basic.input_from_output_side." +
                                (allowsFluidInputFromOutputSide() ? "allow" : "disallow"))
                        .append(Component.translatable("gtpm.creative.tank.fluid")), true);
                hasChanged = true;
            }

        } else {
            if (getItemOutputDirection() == gridSide) {
                setAllowAutoOutputItems(!isAutoOutputItems());
                hasChanged = true;
            }
            if (getFluidOutputDirection() == gridSide) {
                setAllowAutoOutputFluids(!isAutoOutputFluids());
                hasChanged = true;
            }
        }
        return hasChanged ? InteractionResult.sidedSuccess(player.level().isClientSide) : InteractionResult.PASS;
    }
}
