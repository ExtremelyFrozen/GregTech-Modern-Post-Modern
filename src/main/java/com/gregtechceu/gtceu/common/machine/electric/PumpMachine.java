package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.AutoOutputMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.wrappers.BucketPickupHandlerWrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PumpMachine extends TieredEnergyMachine implements LDLib2MachineUIProvider {

    public static final int BASE_PUMP_RADIUS = 16;
    public static final int EXTRA_PUMP_RADIUS = 4;
    public static final int PUMP_SPEED_BASE = 80;
    private static final ResourceLocation CLICK_PUMP_FLUID_SLOT_ACTION = GTCEu.id("click_pump_machine_fluid_slot");
    private static final ResourceLocation SET_PUMP_AUTO_OUTPUT_FLUIDS_ACTION = GTCEu
            .id("set_pump_machine_auto_output_fluids");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");
    private static final ResourceLocation AUTO_OUTPUT_FLUIDS_FIELD = SyncFieldData.key("autoOutputFluids");

    static {
        SyncActionDispatchers.server().register(new PumpFluidSlotActionHandler());
        SyncActionDispatchers.server().register(new PumpAutoOutputFluidsActionHandler());
    }

    private final Set<BlockPos> forbiddenBlocks = new ObjectOpenHashSet<>();
    private @Nullable PumpQueue pumpQueue = null;
    @Getter
    @SaveField
    private int pumpHeadY;
    @SaveField
    protected final NotifiableFluidTank cache;

    @SaveField
    @SyncToClient
    public final AutoOutputTrait autoOutput;

    public PumpMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);
        this.cache = attachTrait(
                new NotifiableFluidTank(1, 16 * FluidType.BUCKET_VOLUME * Math.max(1, getTier()), IO.NONE,
                        IO.OUT));
        environmentalExplosionTrait.setEnableEnvironmentalExplosions(false);
        this.autoOutput = attachTrait(AutoOutputTrait.ofFluids(cache));
    }

    //////////////////////////////////////
    // ***** Initialization *****//
    //////////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        subscribeServerTick(this::update);
    }

    //////////////////////////////////////
    // ********* Logic **********//
    //////////////////////////////////////
    public static int getMaxPumpRadius(int tier) {
        return BASE_PUMP_RADIUS + EXTRA_PUMP_RADIUS * tier;
    }

    /**
     * Returns a list of directions, starting with Up and then horizontal directions with the directions most matching
     * the vector first.
     */
    private List<Direction> biasedInVecDirections(RandomSource randomSource, Vec3i vec, boolean goUp) {
        List<Direction> searchList = new ArrayList<>();
        if (goUp) {
            searchList.add(Direction.UP);
        }

        ObjectArrayList<Direction.Axis> axes = new ObjectArrayList<>();
        int zValue = Math.abs(vec.getZ());
        int xValue = Math.abs(vec.getX());
        if (zValue > xValue) {
            axes.add(Direction.Axis.Z);
            axes.add(Direction.Axis.X);
        } else if (zValue < xValue) {
            axes.add(Direction.Axis.X);
            axes.add(Direction.Axis.Z);
        } else {
            axes.add(Direction.Axis.Z);
            axes.add(Direction.Axis.X);
            Util.shuffle(axes, randomSource);
        }

        Direction lastDirection = null;
        for (int i = 0; i < 2; i++) {
            Direction.Axis axis = axes.get(i);
            int value;
            if (axis.equals(Direction.Axis.Z)) {
                value = vec.getZ();
            } else {
                value = vec.getX();
            }

            Direction direction;
            if (value < 0) {
                direction = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
            } else if (value > 0) {
                direction = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            } else {
                direction = Direction.fromAxisAndDirection(axis,
                        Util.getRandom(Direction.AxisDirection.values(), randomSource));
            }
            searchList.add(direction);
            if (i == 0) {
                lastDirection = direction.getOpposite();
            } else {
                searchList.add(direction.getOpposite());
            }

        }
        searchList.add(lastDirection);

        return searchList;
    }

    protected record PumpQueue(Queue<Deque<BlockPos>> queue, FluidType fluidType) {}

    protected record SearchResult(BlockPos pos, boolean isSource) {}

    /**
     * Returns the next block to search at.
     */
    @Nullable
    private SearchResult searchNext(Level level, BlockPos headPosBelow, BlockPos searchHead, FluidType fluidType,
                                    int maxPumpRange, boolean goUp, Set<BlockPos> checked) {
        // Vector from the pump head to the search head, so points in the direction away from the pump head
        Vec3i subVec = searchHead.subtract(headPosBelow);

        List<Direction> searchList = biasedInVecDirections(level.getRandom(), subVec, goUp);

        for (Direction direction : searchList) {
            BlockPos check = searchHead.relative(direction);
            // The pos at the same y-level as the spot to check, but the x and z of the pump
            // This is to compute the square distance only in the horizontal plane
            BlockPos pumpY = headPosBelow.atY(check.getY());

            // Skip if outside pump range or not loaded or already checked
            if (check.distSqr(pumpY) > maxPumpRange * maxPumpRange || checked.contains(check) ||
                    !level.isLoaded(check) || forbiddenBlocks.contains(check)) {
                continue;
            }

            // Make sure we don't look at it again
            checked.add(check);

            BlockState state = level.getBlockState(check);
            FluidState fluidState;

            // If it's not a fluid of the right type, we stop
            if ((fluidState = state.getFluidState()).getFluidType() == fluidType &&
                    state.getBlock() instanceof LiquidBlock liquidBlock) {
                // Remember all the sources we find
                boolean isSource = fluidState.isSource();
                if (isSource) {
                    var fluidHandler = new BucketPickupHandlerWrapper(null, liquidBlock, level, check);
                    FluidStack drainStack = fluidHandler.drain(Integer.MAX_VALUE, FluidAction.SIMULATE);
                    if (!drainStack.isEmpty()) {
                        return new SearchResult(check, true);
                    }
                }
                return new SearchResult(check, false);
            }
        }

        return null;
    }

    /**
     * Update the pump queue if it is empty.
     *
     * @param fluidType Use this if the pump queue must have the same fluid type because it was already decided in the
     *                  pump cycle.
     */
    private void updatePumpQueue(@Nullable FluidType fluidType) {
        if (getLevel() == null) return;

        if (pumpQueue != null && !pumpQueue.queue().isEmpty()) {
            return;
        }

        BlockPos headPos = getBlockPos().below(pumpHeadY);

        BlockPos downPos = headPos.below(1);
        var downBlock = getLevel().getBlockState(downPos);

        if (!(downBlock.getBlock() instanceof LiquidBlock)) {
            pumpQueue = null;
            return;
        }

        if (fluidType != null && downBlock.getFluidState().getFluidType() != fluidType) {
            pumpQueue = null;
            return;
        }

        pumpQueue = buildPumpQueue(getLevel(), headPos, downBlock.getFluidState().getFluidType(), queueSize(), true);
    }

    /**
     * Does a "depth-first"-ish search to find a path to a source. It prioritizes going up and away from the pump head.
     * If the path it finds only contains sources at the level below the pump head, it will keep looking until it finds
     * one that has a source at a higher location. If it cannot find one, it will return the original path.
     */
    private PumpQueue buildPumpQueue(Level level, BlockPos headPos, FluidType fluidType, int queueSourceAmount,
                                     boolean upSources) {
        Set<BlockPos> checked = new ObjectOpenHashSet<>();

        BlockPos headPosBelow = headPos.below();

        checked.add(headPos);
        checked.add(headPosBelow);

        int maxPumpRange = getMaxPumpRadius(getTier());

        List<BlockPos> pathStack = new ArrayList<>();

        Deque<BlockPos> nonSources = new ArrayDeque<>();
        Deque<BlockPos> pathToLastSource = new ArrayDeque<>();
        Deque<BlockPos> sourceStack = new ArrayDeque<>();

        pathStack.add(headPosBelow);
        nonSources.add(headPosBelow);

        int iterations = 0;
        int previousSources = 0;
        Queue<Deque<BlockPos>> paths = new ArrayDeque<>();
        List<BlockPos> sources = new ArrayList<>();
        // We do at most 1000 iterations to try and find source blocks
        while (!pathStack.isEmpty() && iterations < 1000) {
            // Peeks at the tail
            BlockPos searchHead = pathStack.get(pathStack.size() - 1);

            SearchResult next = searchNext(level, headPosBelow, searchHead, fluidType, maxPumpRange, upSources,
                    checked);

            iterations++;

            if (next == null) {
                boolean continueSearch = sources.size() < queueSourceAmount;

                int addedSources = sources.size() - previousSources;
                previousSources = sources.size();
                if (addedSources > 0) {
                    var toAdd = new ArrayDeque<>(pathToLastSource);
                    // This is always the headPosBelow, which we do not want to include
                    toAdd.removeFirst();
                    paths.add(toAdd);
                }

                if (!continueSearch) {
                    return new PumpQueue(paths, fluidType);
                }

                // Now we need to rewind our stack
                BlockPos last = pathStack.remove(pathStack.size() - 1);
                BlockPos lastSource = sourceStack.peekLast();
                if (last.equals(lastSource)) {
                    BlockPos prevSource = sourceStack.removeLast();
                    // Rebuild nonSources until previous source
                    for (int i = pathStack.size() - 1; i >= 0; i--) {
                        BlockPos p = pathStack.get(i);
                        if (!p.equals(prevSource)) {
                            nonSources.addFirst(p);
                        } else {
                            break;
                        }
                    }
                    // If the last is a source, then nonSources will be empty regardless
                } else if (!nonSources.isEmpty()) {
                    nonSources.removeLast();
                }
            } else {
                // Add the next
                pathStack.add(next.pos());
                // If we are in search up mode, we only count it as a source if it's up
                if (next.isSource() && (!upSources || next.pos().getY() > headPosBelow.getY())) {
                    sources.add(next.pos());
                    // Found a source, so add all the non-source blocks we passed since the last one
                    pathToLastSource.addAll(nonSources);
                    // Also add the source itself
                    pathToLastSource.add(next.pos());
                    // Reset non-sources because we just added them and found a source
                    nonSources.clear();
                    sources.add(next.pos());
                } else {
                    // Not a source, but we want to track it
                    nonSources.add(next.pos());
                }

            }
        }
        if (upSources) {
            // If we found none, we try again without the restriction
            if (paths.isEmpty()) {
                return buildPumpQueue(level, headPos, fluidType, queueSourceAmount, false);
            }

            return new PumpQueue(paths, fluidType);
        }

        // Only after everything except the block directly below the pipe is pumped, do we want to pump it
        // Otherwise we might advance the pump head prematurely
        if (paths.isEmpty() && level.getBlockState(headPosBelow).getFluidState().isSource()) {
            return new PumpQueue(new ArrayDeque<>(List.of(new ArrayDeque<>(List.of(headPosBelow)))), fluidType);
        }

        return new PumpQueue(paths, fluidType);
    }

    /**
     * Advances the pump head if the block below is air and the pump queue is empty.
     */
    private boolean canAdvancePumpHead() {
        // position of the pump head, i.e. the position of the lowest mining pipe
        BlockPos headPos = getBlockPos().below(pumpHeadY);

        if (pumpQueue == null || pumpQueue.queue.isEmpty()) {
            Level level;
            if ((level = getLevel()) != null) {
                BlockPos downPos = headPos.below(1);
                var downBlock = level.getBlockState(downPos);

                if (downBlock.isAir()) {
                    this.pumpHeadY++;

                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.setBlockAndUpdate(downPos, GTBlocks.MINER_PIPE.getDefaultState());
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        if (getLevel() instanceof ServerLevel serverLevel) {
            var pos = getBlockPos().relative(Direction.DOWN);
            while (serverLevel.getBlockState(pos).is(GTBlocks.MINER_PIPE.get())) {
                serverLevel.removeBlock(pos, false);
                pos = pos.relative(Direction.DOWN);
            }
        }
    }

    protected record SourceState(BlockState state, BlockPos pos) {}

    /**
     * Does a full pump cycle, trying to do the required number of pumps. It will rebuild the queue if it becomes
     * empty without having fulfilled its required number of pumps. All paths computed in the queue are checked
     * if they are still valid and consist only of the right fluid.
     */
    private void pumpCycle() {
        Level level;
        if ((level = getLevel()) == null) {
            return;
        }
        // Will only update if the queue is empty
        updatePumpQueue(null);
        int pumps = pumpsPerCycle();

        // We try to pump `pumps` amount of source blocks, using multiple paths if necessary
        boolean pumped = false;
        int iterations = 0;
        // We keep looking at paths as long as we still have pumps to go
        // We put the iterations at max 10 just to be sure
        while (pumps > 0 && pumpQueue != null && !pumpQueue.queue().isEmpty() && iterations < 10) {
            iterations++;

            Deque<BlockPos> pumpPath = pumpQueue.queue().peek();
            Deque<SourceState> states = new ArrayDeque<>();

            // We iterate through the positions to check if it is still a valid path, saving the states
            for (BlockPos pos : pumpPath) {
                // Stop once an unloaded block is found
                if (!level.isLoaded(pos)) {
                    break;
                }
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof LiquidBlock liquidBlock &&
                        liquidBlock.fluid.getFluidType() == pumpQueue.fluidType()) {
                    states.add(new SourceState(state, pos));
                } else {
                    break;
                }
            }

            // We remove from the end until we find a matching state, everything after must be no longer valid
            while (pumps > 0 && !pumpPath.isEmpty()) {
                BlockPos pos = pumpPath.removeLast();
                SourceState sourceState = states.peekLast();
                if (sourceState != null && pos.equals(sourceState.pos())) {
                    states.removeLast();
                    FluidState fluidState = sourceState.state().getFluidState();
                    if (sourceState.state().getBlock() instanceof LiquidBlock liquidBlock && fluidState.isSource()) {
                        var fluidHandler = new BucketPickupHandlerWrapper(null, liquidBlock, getLevel(), pos);
                        FluidStack drainStack = fluidHandler.drain(Integer.MAX_VALUE, FluidAction.SIMULATE);
                        if (!drainStack.isEmpty() &&
                                cache.fillInternal(drainStack, FluidAction.SIMULATE) == drainStack.getAmount()) {
                            cache.fillInternal(drainStack, FluidAction.EXECUTE);
                            fluidHandler.drain(drainStack, FluidAction.EXECUTE);
                            getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                            pumped = true;
                            pumps--;
                        } else if (!drainStack.isEmpty()) {
                            // In this case we just couldn't fill the internal tank, it's most likely full
                            // So we add back to the pump path and return
                            pumpPath.add(pos);
                            return;
                        } else {
                            // drain stack is empty even though it's a fluid source, probably something went wrong
                            // ignore block for a while
                            forbiddenBlocks.add(pos);
                            return;
                        }
                    }
                }
            }

            if (pumpPath.isEmpty()) {
                pumpQueue.queue().remove();
            }

            // If we have pumps left over and there is still more to be pumped at the current level
            // (But it wasn't in the queue because maybe it's the final source block below the pump head)
            // We still want to be able to pump
            if (pumps > 0 && pumpQueue.queue().isEmpty()) {
                updatePumpQueue(pumpQueue.fluidType());
            }
        }

        // Use energy if any pumps happened at all
        if (pumped) {
            energyContainer.changeEnergy(-GTValues.V[getTier()] * 2);
        }
    }

    public void update() {
        if (autoOutput.getFluidOutputDirection() != null) {
            cache.exportToNearby(autoOutput.getFluidOutputDirection());
        }

        // do not do anything without enough energy supplied
        if (energyContainer.getEnergyStored() < GTValues.V[getTier()] * 2) {
            return;
        }
        // Try to put 5 times as many in the queue as there are pumps in the cycle
        // In practice only EV tier has more than 1 pump per cycle
        // The queue can contain at most the y-levels at the pump head or just the y-level below, so for many oil veins
        // It will not be the ideal size
        boolean advanced = false;
        if (getOffsetTimer() % (getPumpingCycleLength() * 2L) == 0) {
            advanced = canAdvancePumpHead();
        }
        if (!advanced && getOffsetTimer() % getPumpingCycleLength() == 0) {
            pumpCycle();
        }
        if (getOffsetTimer() % (20 * 60) == 0) {
            forbiddenBlocks.clear();
        }
    }

    private int queueSize() {
        return 5 * pumpsPerCycle();
    }

    private float ticksPerPump() {
        // How many ticks pass per pump. This is the ideal amount and thus can be less than 1
        // For LV this is 80/1 = 80
        float tierMultiplier = (float) (1 << (getTier() - 1));
        return PUMP_SPEED_BASE / tierMultiplier;
    }

    private int pumpsPerCycle() {
        // The pumping cycle length can not be less than 20, so to ensure we still have the right amount of pumps
        // We need to compensate with pumps per cycle

        return (int) (getPumpingCycleLength() / ticksPerPump());
    }

    private int getPumpingCycleLength() {
        // For basic pumps this means once every 80 ticks
        // It never pumps more than once every 20 ticks, but pumps more per cycle to compensate
        return Math.max(20, (int) ticksPerPump());
    }

    //////////////////////////////////////
    // ********** Gui ***********//
    //////////////////////////////////////
    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 166);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));
        root.addChild(new GTImageElement(7, 16, 81, 55, GuiTextures.DISPLAY));
        root.addChild(createLDLib2FluidAmountLabel());
        root.addChild(createLDLib2FluidAmountValueLabel());
        root.addChild(createLDLib2TitleLabel());
        root.addChild(createLDLib2FluidSlot(player, holder));
        root.addChild(new GTToggleButtonElement(7, 53, 18, 18,
                GuiTextures.BUTTON_FLUID_OUTPUT, this.autoOutput::isAutoOutputFluids,
                enabled -> requestLDLib2FluidAutoOutput(player, holder, enabled))
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.fluid_auto_output.tooltip"));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7, 84, true));
        return UI.of(root);
    }

    private GTLabelElement createLDLib2TitleLabel() {
        GTLabelElement label = new GTLabelElement(6, 6, 164, 10,
                getBlockState().getBlock().getDescriptionId(), true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static GTLabelElement createLDLib2FluidAmountLabel() {
        GTLabelElement label = new GTLabelElement(11, 20, 73, 10, "gtpm.gui.fluid_amount", true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTLabelElement createLDLib2FluidAmountValueLabel() {
        GTLabelElement label = new GTLabelElement(11, 30, 73, 10) {

            @Override
            public void screenTick() {
                setValue(Component.literal(getLDLib2FluidAmountText()));
                super.screenTick();
            }
        };
        label.setValue(Component.literal(getLDLib2FluidAmountText()));
        label.textStyle(style -> style
                .textColor(-1)
                .textShadow(true)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private String getLDLib2FluidAmountText() {
        return String.valueOf(cache.getFluidInTank(0).getAmount());
    }

    private GTFluidSlotElement createLDLib2FluidSlot(Player player, MachineUIHolder holder) {
        GTFluidSlotElement fluidSlot = new GTFluidSlotElement()
                .setFluidTank(cache.getStorages()[0], 0)
                .setShowAmount(true)
                .setAllowClickFilled(true)
                .setAllowClickDrained(true)
                .setBackgroundTexture(GuiTextures.FLUID_SLOT);
        fluidSlot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0 && player.level().isClientSide() &&
                    FluidUtil.getFluidHandler(player.containerMenu.getCarried()).isPresent()) {
                MachineUIHelper.sendAction(holder, createClickPumpFluidSlotAction(event.isShiftDown()));
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        });
        return UITemplate.setLDLib2Bounds(fluidSlot, 90, 35, 18, 18);
    }

    private void requestLDLib2FluidAutoOutput(Player player, MachineUIHolder holder, boolean enabled) {
        autoOutput.setAllowAutoOutputFluids(enabled);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetPumpAutoOutputFluidsAction(enabled));
        }
    }

    private void clickLDLib2FluidSlot(ServerPlayer player, boolean shiftDown) {
        new LDLib2FluidClickTarget(cache.getStorages()[0], true, true).click(player, shiftDown);
    }

    private static SyncActionData createClickPumpFluidSlotAction(boolean shiftDown) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SHIFT_FIELD, new JsonPrimitive(shiftDown))
                        .build())
                .build();
        return new SyncActionData(CLICK_PUMP_FLUID_SLOT_ACTION, shiftDown ? 1 : 0, payload);
    }

    private static SyncActionData createSetPumpAutoOutputFluidsAction(boolean enabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(enabled))
                        .build())
                .build();
        return new SyncActionData(SET_PUMP_AUTO_OUTPUT_FLUIDS_ACTION, enabled ? 1 : 0, payload);
    }

    private record LDLib2FluidClickTarget(IFluidHandler fluidTank, boolean allowClickFilled,
                                          boolean allowClickDrained) {

        private void click(ServerPlayer player, boolean shiftDown) {
            ItemStack currentStack = player.containerMenu.getCarried();
            var handler = FluidUtil.getFluidHandler(currentStack).orElse(null);
            if (handler == null) {
                return;
            }
            int maxAttempts = shiftDown ? currentStack.getCount() : 1;
            FluidStack initialFluid = fluidTank.getFluidInTank(0).copy();
            if (allowClickFilled && initialFluid.getAmount() > 0 && fillContainer(player, currentStack,
                    maxAttempts, initialFluid)) {
                return;
            }
            if (allowClickDrained) {
                emptyContainer(player, currentStack, maxAttempts);
            }
        }

        private boolean fillContainer(ServerPlayer player, ItemStack currentStack, int maxAttempts,
                                      FluidStack initialFluid) {
            boolean performedFill = false;
            ItemStack filledResult = ItemStack.EMPTY;
            for (int i = 0; i < maxAttempts; i++) {
                FluidActionResult result = FluidUtil.tryFillContainer(currentStack, fluidTank,
                        Integer.MAX_VALUE, null, false);
                if (!result.isSuccess()) {
                    break;
                }
                ItemStack remainingStack = FluidUtil.tryFillContainer(currentStack, fluidTank,
                        Integer.MAX_VALUE, null, true).getResult();
                performedFill = true;
                currentStack.shrink(1);
                filledResult = mergeOrStoreResult(player, filledResult, remainingStack);
            }
            if (!performedFill) {
                return false;
            }
            SoundEvent sound = initialFluid.getFluid().getFluidType().getSound(initialFluid,
                    SoundActions.BUCKET_FILL);
            if (sound == null) {
                sound = SoundEvents.BUCKET_FILL;
            }
            player.level().playSound(null, player, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            finishContainerClick(player, currentStack, filledResult);
            return true;
        }

        private void emptyContainer(ServerPlayer player, ItemStack currentStack, int maxAttempts) {
            boolean performedEmptying = false;
            ItemStack drainedResult = ItemStack.EMPTY;
            for (int i = 0; i < maxAttempts; i++) {
                int remainingCapacity = fluidTank.getTankCapacity(0) - fluidTank.getFluidInTank(0).getAmount();
                FluidActionResult result = FluidUtil.tryEmptyContainer(currentStack, fluidTank,
                        remainingCapacity, null, false);
                if (!result.isSuccess()) {
                    break;
                }
                ItemStack remainingStack = FluidUtil.tryEmptyContainer(currentStack, fluidTank,
                        remainingCapacity, null, true).getResult();
                performedEmptying = true;
                currentStack.shrink(1);
                drainedResult = mergeOrStoreResult(player, drainedResult, remainingStack);
            }
            FluidStack filledFluid = fluidTank.getFluidInTank(0);
            if (performedEmptying) {
                SoundEvent sound = filledFluid.getFluid().getFluidType().getSound(filledFluid,
                        SoundActions.BUCKET_EMPTY);
                if (sound == null) {
                    sound = SoundEvents.BUCKET_EMPTY;
                }
                player.level().playSound(null, player, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
                finishContainerClick(player, currentStack, drainedResult);
            }
        }

        private ItemStack mergeOrStoreResult(ServerPlayer player, ItemStack storedResult, ItemStack remainingStack) {
            if (storedResult.isEmpty()) {
                return remainingStack.copy();
            }
            if (ItemStack.isSameItemSameComponents(storedResult, remainingStack)) {
                if (storedResult.getCount() < storedResult.getMaxStackSize()) {
                    storedResult.grow(1);
                } else {
                    player.getInventory().placeItemBackInInventory(remainingStack);
                }
                return storedResult;
            }
            player.getInventory().placeItemBackInInventory(storedResult);
            return remainingStack.copy();
        }

        private void finishContainerClick(ServerPlayer player, ItemStack currentStack, ItemStack resultStack) {
            if (currentStack.isEmpty()) {
                player.containerMenu.setCarried(resultStack);
            } else {
                player.containerMenu.setCarried(currentStack);
                player.getInventory().placeItemBackInInventory(resultStack);
            }
            player.containerMenu.broadcastChanges();
        }
    }

    private static final class PumpFluidSlotActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return CLICK_PUMP_FLUID_SLOT_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof PumpMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, SHIFT_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof PumpMachine machine)) {
                throw new IllegalStateException("Pump fluid slot action received a non-pump machine.");
            }
            machine.clickLDLib2FluidSlot(context.player(), requireBoolean(context.payload(), SHIFT_FIELD));
        }
    }

    private static final class PumpAutoOutputFluidsActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_PUMP_AUTO_OUTPUT_FLUIDS_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            AutoOutputMachine machine = readAutoOutputMachine(context);
            return machine != null && machine.supportsAutoOutputFluids();
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, AUTO_OUTPUT_FLUIDS_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            AutoOutputMachine machine = requireAutoOutputFluidsMachine(context);
            machine.setAllowAutoOutputFluids(requireBoolean(context.payload(), AUTO_OUTPUT_FLUIDS_FIELD));
        }
    }

    private static @Nullable AutoOutputMachine readAutoOutputMachine(SyncActionContext context) {
        if (context.holder() instanceof AutoOutputMachine autoOutputMachine) {
            return autoOutputMachine;
        }
        if (context.holder() instanceof PumpMachine machine) {
            return machine.autoOutput;
        }
        return null;
    }

    private static AutoOutputMachine requireAutoOutputFluidsMachine(SyncActionContext context) {
        AutoOutputMachine machine = readAutoOutputMachine(context);
        if (machine == null || !machine.supportsAutoOutputFluids()) {
            throw new IllegalStateException("Pump auto output action received an invalid holder.");
        }
        return machine;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Pump action payload is missing field data.");
        }
        Boolean value = readBoolean(fields, field);
        if (value == null) {
            throw new IllegalStateException("Pump action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Boolean readBoolean(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }
}
