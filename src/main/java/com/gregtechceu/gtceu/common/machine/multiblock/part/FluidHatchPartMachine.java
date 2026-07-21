package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTPhantomFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.xei.GTXEIHelper;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.ISubscription;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidHatchPartMachine extends TieredIOPartMachine
                                   implements IHasCircuitSlot, IPaintable, LDLib2MachineUIProvider,
                                   FluidHatchFluidSlotActionTarget {

    public static final int INITIAL_TANK_CAPACITY_1X = 8 * FluidType.BUCKET_VOLUME;
    public static final int INITIAL_TANK_CAPACITY_4X = 2 * FluidType.BUCKET_VOLUME;
    public static final int INITIAL_TANK_CAPACITY_9X = FluidType.BUCKET_VOLUME;

    static {
        FluidHatchPartMachineActions.initialize();
    }

    @SaveField
    public final NotifiableFluidTank tank;
    private final int slots;
    @Nullable
    protected TickableSubscription autoIOSubs;
    @Nullable
    protected ISubscription tankSubs;
    @Getter
    @SaveField
    @SyncToClient
    protected boolean circuitSlotEnabled;
    @Getter
    @SaveField
    protected final NotifiableItemStackHandler circuitInventory;

    public FluidHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io, int initialCapacity, int slots) {
        super(info, tier, io);
        this.slots = slots;
        this.tank = attachTrait(createTank(initialCapacity, slots));

        if (io == IO.IN) {
            this.circuitSlotEnabled = true;
            this.circuitInventory = attachTrait(new NotifiableItemStackHandler(1, IO.IN, IO.NONE))
                    .setFilter(IntCircuitBehaviour::isIntegratedCircuit).shouldSearchContent(false)
                    .shouldDropInventoryInWorld(!ConfigHolder.INSTANCE.machines.ghostCircuit);
        } else {
            this.circuitSlotEnabled = false;
            this.circuitInventory = attachTrait(new NotifiableItemStackHandler(0, IO.NONE)).shouldSearchContent(false);
        }
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected NotifiableFluidTank createTank(int initialCapacity, int slots) {
        return new NotifiableFluidTank(slots, getTankCapacity(initialCapacity, getTier()), io);
    }

    public static int getTankCapacity(int initialCapacity, int tier) {
        return initialCapacity * (1 << Math.min(9, tier));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleForNextServerTick(this::updateTankSubscription);
        getHandlerList().setColor(getPaintingColor());
        tankSubs = tank.addChangedListener(this::updateTankSubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (tankSubs != null) {
            tankSubs.unsubscribe();
            tankSubs = null;
        }
    }

    @Override
    public void onPaintingColorChanged(int color) {
        getHandlerList().setColor(color, true);
    }

    @Override
    public void addedToController(MultiblockControllerMachine controller, String structureName) {
        if (!controller.allowCircuitSlots()) {
            if (!ConfigHolder.INSTANCE.machines.ghostCircuit) {
                circuitInventory.dropInventoryInWorld();
            } else {
                circuitInventory.setStackInSlot(0, ItemStack.EMPTY);
            }
            setCircuitSlotEnabled(false);
        }
        super.addedToController(controller, structureName);
    }

    @Override
    public void removedFromController(MultiblockControllerMachine controller, String structureName) {
        super.removedFromController(controller, structureName);
        for (var c : controllers) {
            if (!c.allowCircuitSlots()) {
                return;
            }
        }
        setCircuitSlotEnabled(true);
    }

    @Override
    public int tintColor(int index) {
        if (index == 9) return getRealColor();
        return -1;
    }

    public void setCircuitSlotEnabled(boolean enabled) {
        circuitSlotEnabled = enabled;
    }

    //////////////////////////////////////
    // ******** Auto IO *********//
    //////////////////////////////////////

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateTankSubscription();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        updateTankSubscription(newFacing);
    }

    protected void updateTankSubscription() {
        updateTankSubscription(getFrontFacing());
    }

    protected void updateTankSubscription(Direction newFacing) {
        if (isWorkingEnabled() && ((io.support(IO.OUT) && !tank.isEmpty()) || io.support(IO.IN)) &&
                GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getBlockPos(), newFacing)) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    protected void autoIO() {
        if (getOffsetTimer() % 5 == 0) {
            if (isWorkingEnabled()) {
                if (io.support(IO.OUT)) {
                    tank.exportToNearby(getFrontFacing());
                } else if (io.support(IO.IN)) {
                    tank.importFromNearby(getFrontFacing());
                }
            }
            updateTankSubscription();
        }
    }

    @Override
    protected void onWorkingEnabledChanged() {
        super.onWorkingEnabledChanged();
        updateTankSubscription();
    }

    @Override
    protected InteractionResult onScrewdriverClick(ExtendedUseOnContext context) {
        InteractionResult superResult = super.onScrewdriverClick(context);
        if (superResult != InteractionResult.PASS) return superResult;
        if (io == IO.BOTH) return InteractionResult.PASS;
        if (context.getPlayer().isShiftKeyDown()) {
            if (swapIO()) {
                return InteractionResult.sidedSuccess(getLevel().isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    public boolean swapIO() {
        BlockPos blockPos = getBlockPos();
        MachineDefinition newDefinition = null;

        if (io.support(IO.IN)) {
            if (this.slots == 1) newDefinition = GTMachines.FLUID_EXPORT_HATCH[this.getTier()];
            else if (this.slots == 4) newDefinition = GTMachines.FLUID_EXPORT_HATCH_4X[this.getTier()];
            else if (this.slots == 9) newDefinition = GTMachines.FLUID_EXPORT_HATCH_9X[this.getTier()];
        } else if (io.support(IO.OUT)) {
            if (this.slots == 1) newDefinition = GTMachines.FLUID_IMPORT_HATCH[this.getTier()];
            else if (this.slots == 4) newDefinition = GTMachines.FLUID_IMPORT_HATCH_4X[this.getTier()];
            else if (this.slots == 9) newDefinition = GTMachines.FLUID_IMPORT_HATCH_9X[this.getTier()];
        }
        if (newDefinition == null) return false;

        BlockState newBlockState = newDefinition.getBlock().defaultBlockState();

        getLevel().setBlockAndUpdate(blockPos, newBlockState);

        if (getLevel().getBlockEntity(blockPos) instanceof FluidHatchPartMachine newMachine) {
            newMachine.setFrontFacing(this.getFrontFacing());
            newMachine.setUpwardsFacing(this.getUpwardsFacing());
            newMachine.setPaintingColor(this.getPaintingColor());
            for (int i = 0; i < this.tank.getTanks(); i++) {
                newMachine.tank.setFluidInTank(i, this.tank.getFluidInTank(i));
            }
        }
        return true;
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this && supportsGenericLDLib2Page();
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        LDLib2FancyUIProvider page = createLDLib2Page(player, holder);
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    LDLib2FancyUIProvider createLDLib2Page(Player player, MachineUIHolder holder) {
        requireMatchingLDLib2Holder(holder);
        if (!supportsGenericLDLib2Page()) {
            throw new IllegalStateException("Fluid hatch definition requires its specialized UI provider.");
        }
        return new FluidHatchLDLib2Page(player, holder);
    }

    private void requireMatchingLDLib2Holder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("Fluid hatch page holder must resolve the opened machine.");
        }
    }

    /** Determines whether this definition may reuse the generic holder-scoped LDLib2 fluid hatch page. */
    protected boolean supportsGenericLDLib2Page() {
        MachineDefinition definition = getDefinition();
        return containsDefinition(definition, GTMachines.FLUID_IMPORT_HATCH) ||
                containsDefinition(definition, GTMachines.FLUID_IMPORT_HATCH_4X) ||
                containsDefinition(definition, GTMachines.FLUID_IMPORT_HATCH_9X) ||
                containsDefinition(definition, GTMachines.FLUID_EXPORT_HATCH) ||
                containsDefinition(definition, GTMachines.FLUID_EXPORT_HATCH_4X) ||
                containsDefinition(definition, GTMachines.FLUID_EXPORT_HATCH_9X) ||
                containsDefinition(definition, GTMachines.FLUID_PASSTHROUGH_HATCH) ||
                definition == GTMachines.RESERVOIR_HATCH;
    }

    private static boolean containsDefinition(MachineDefinition definition, MachineDefinition[] definitions) {
        for (MachineDefinition candidate : definitions) {
            if (candidate == definition) {
                return true;
            }
        }
        return false;
    }

    private int getLDLib2PageWidth() {
        if (slots == 1) {
            return 89;
        }
        int rowSize = getTankRowSize();
        return 18 * rowSize + 16;
    }

    private int getLDLib2PageHeight() {
        if (slots == 1) {
            return 63;
        }
        int columnSize = getTankColumnSize();
        return 18 * columnSize + 16;
    }

    private int getTankRowSize() {
        return slots == 8 ? 4 : (int) Math.sqrt(slots);
    }

    private int getTankColumnSize() {
        return slots == 8 ? 2 : (int) Math.sqrt(slots);
    }

    private UIElement createLDLib2MainElement(Player player, MachineUIHolder holder) {
        return createLDLib2MainElement(player, holder, MachineUIHelper::sendAction,
                () -> player.level().isClientSide() && holder.getMachine() == this,
                UIEvent::isShiftDown);
    }

    UIElement createLDLib2MainElement(Player player, MachineUIHolder holder,
                                      BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                      BooleanSupplier canSendAction, Predicate<UIEvent> shiftDown) {
        requireMatchingLDLib2Holder(holder);
        if (slots == 1) {
            return createLDLib2SingleSlotElement(player, holder, actionSender, canSendAction, shiftDown,
                    () -> getSelectedLockedFluid(player));
        }
        return createLDLib2MultiSlotElement(player, holder, actionSender, canSendAction, shiftDown);
    }

    private static Optional<FluidStack> getSelectedLockedFluid(Player player) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return Optional.of(FluidStack.EMPTY);
        }
        if (FluidUtil.getFluidHandler(carried).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(FluidUtil.getFluidContained(carried)
                .map(fluid -> fluid.copyWithAmount(1))
                .orElse(FluidStack.EMPTY));
    }

    private UIElement createLDLib2SingleSlotElement(
                                                    Player player, MachineUIHolder holder,
                                                    BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                                    BooleanSupplier canSendAction, Predicate<UIEvent> shiftDown,
                                                    Supplier<Optional<FluidStack>> selectedLockedFluid) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, 89, 63);
        UITemplate.setLDLib2BackgroundTexture(root, GuiTextures.BACKGROUND_INVERSE);
        root.addChild(new GTImageElement(4, 4, 81, 55, GuiTextures.DISPLAY));

        GTFluidSlotElement fluidSlot = createLDLib2FluidSlot(player, holder, 0, 67, 22,
                actionSender, canSendAction, shiftDown);
        root.addChild(fluidSlot);
        if (supportsFluidHatchLocking()) {
            root.addChild(createLDLib2LockedFluidSlot(holder, actionSender, canSendAction, selectedLockedFluid));
            root.addChild(new GTToggleButtonElement(7, 40, 18, 18,
                    GuiTextures.BUTTON_LOCK, tank::isLocked,
                    locked -> sendSetLockedAction(holder, actionSender, canSendAction, locked))
                    .setTooltipText("gtpm.gui.fluid_lock.tooltip")
                    .setShouldUseBaseBackground());
        }

        root.addChild(createLDLib2FluidAmountLabel());
        root.addChild(createLDLib2FluidAmountValueLabel());
        root.addChild(createLDLib2FluidNameLabel());
        return root;
    }

    private static GTLabelElement createLDLib2FluidAmountLabel() {
        return new GTLabelElement(8, 8, 57, 10,
                Component.translatable("gtpm.gui.fluid_amount"))
                .setTextColor(-1)
                .setTextShadow(true)
                .setTextAlignHorizontal(Horizontal.LEFT)
                .setTextAlignVertical(Vertical.CENTER);
    }

    private GTLabelElement createLDLib2FluidAmountValueLabel() {
        return new GTLabelElement(8, 18, 57, 10) {

            @Override
            public void screenTick() {
                setValue(Component.literal(getLDLib2FluidAmountText()));
                super.screenTick();
            }
        }.setValue(Component.literal(getLDLib2FluidAmountText()))
                .setTextColor(-1)
                .setTextShadow(true)
                .setTextAlignHorizontal(Horizontal.LEFT)
                .setTextAlignVertical(Vertical.CENTER);
    }

    private GTLabelElement createLDLib2FluidNameLabel() {
        return new GTLabelElement(8, 28, 57, 10) {

            @Override
            public void screenTick() {
                setValue(getLDLib2FluidName());
                super.screenTick();
            }
        }.setValue(getLDLib2FluidName())
                .setTextColor(-1)
                .setTextShadow(true)
                .setTextAlignHorizontal(Horizontal.LEFT)
                .setTextAlignVertical(Vertical.CENTER);
    }

    private String getLDLib2FluidAmountText() {
        FluidStack fluid = tank.getFluidInTank(0);
        if (!fluid.isEmpty()) {
            return getFormattedFluidAmount(fluid);
        }
        return tank.getLockedFluid().getFluid().isEmpty() ? "" : "0";
    }

    private Component getLDLib2FluidName() {
        FluidStack fluid = tank.getFluidInTank(0);
        return (fluid.isEmpty() ? tank.getLockedFluid().getFluid() : fluid).getHoverName();
    }

    private GTPhantomFluidSlotElement createLDLib2LockedFluidSlot(
                                                                  MachineUIHolder holder,
                                                                  BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                                                  BooleanSupplier canSendAction,
                                                                  Supplier<Optional<FluidStack>> selectedLockedFluid) {
        GTPhantomFluidSlotElement slot = new GTPhantomFluidSlotElement(
                () -> tank.getLockedFluid().getFluid(),
                fluid -> {
                    if (canSendAction.getAsBoolean()) {
                        actionSender.accept(holder,
                                FluidHatchPartMachineActions.createSetLockedFluidAction(fluid));
                    }
                },
                () -> 1) {

            @Override
            public void screenTick() {
                refreshFromSupplier();
                setShowAmount(false);
                super.screenTick();
            }
        };
        slot.setBackgroundTexture(GuiTextures.FLUID_SLOT);
        slot.setShowAmount(false);
        UITemplate.setLDLib2Bounds(slot, 67, 40, 18, 18);
        slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (canSendAction.getAsBoolean() &&
                    (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT ||
                            event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                selectedLockedFluid.get().ifPresent(fluid -> {
                    actionSender.accept(holder,
                            FluidHatchPartMachineActions.createSetLockedFluidAction(fluid));
                    event.stopImmediatePropagation();
                    event.hasHandler = true;
                });
            }
        });
        return slot;
    }

    private void sendSetLockedAction(
                                     MachineUIHolder holder,
                                     BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                     BooleanSupplier canSendAction, boolean locked) {
        if (canSendAction.getAsBoolean()) {
            actionSender.accept(holder, FluidHatchPartMachineActions.createSetLockedAction(locked));
        }
    }

    private UIElement createLDLib2MultiSlotElement(
                                                   Player player, MachineUIHolder holder,
                                                   BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                                   BooleanSupplier canSendAction,
                                                   Predicate<UIEvent> shiftDown) {
        int rowSize = getTankRowSize();
        int columnSize = getTankColumnSize();
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0,
                18 * rowSize + 16, 18 * columnSize + 16);
        UIElement container = UITemplate.setLDLib2Bounds(new UIElement(), 4, 4,
                18 * rowSize + 8, 18 * columnSize + 8);
        UITemplate.setLDLib2BackgroundTexture(container, GuiTextures.BACKGROUND_INVERSE);

        int index = 0;
        for (int y = 0; y < columnSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                container.addChild(createLDLib2FluidSlot(player, holder, index++,
                        4 + x * 18, 4 + y * 18, actionSender, canSendAction, shiftDown));
            }
        }
        root.addChild(container);
        return root;
    }

    private GTFluidSlotElement createLDLib2FluidSlot(
                                                     Player player, MachineUIHolder holder, int tankIndex, int x,
                                                     int y,
                                                     BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                                     BooleanSupplier canSendAction,
                                                     Predicate<UIEvent> shiftDown) {
        GTFluidSlotElement fluidSlot = new GTFluidSlotElement()
                .setFluidTank(tank.getStorages()[tankIndex], 0)
                .setShowAmount(true)
                .setAllowClickFilled(true)
                .setAllowClickDrained(io.support(IO.IN))
                .setIngredientIO(io.support(IO.IN) ? GTXEIHelper.input() : GTXEIHelper.output())
                .setBackgroundTexture(GuiTextures.FLUID_SLOT);
        fluidSlot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT && canSendAction.getAsBoolean() &&
                    FluidUtil.getFluidHandler(player.containerMenu.getCarried()).isPresent()) {
                actionSender.accept(holder, FluidHatchPartMachineActions.createClickFluidSlotAction(
                        tankIndex, shiftDown.test(event)));
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        });
        return UITemplate.setLDLib2Bounds(fluidSlot, x, y, 18, 18);
    }

    @Override
    @ApiStatus.Internal
    public boolean supportsFluidHatchActions() {
        return supportsGenericLDLib2Page();
    }

    @Override
    @ApiStatus.Internal
    public boolean supportsFluidHatchLocking() {
        return slots == 1 && io.support(IO.OUT);
    }

    @Override
    @ApiStatus.Internal
    public int getFluidHatchTankCount() {
        return tank.getTanks();
    }

    @Override
    @ApiStatus.Internal
    public void clickFluidHatchSlot(ServerPlayer player, int tankIndex, boolean shiftDown) {
        requireFluidHatchActions();
        if (tankIndex < 0 || tankIndex >= tank.getTanks()) {
            throw new IllegalArgumentException("Invalid fluid hatch tank index: " + tankIndex);
        }
        new LDLib2FluidClickTarget(tank.getStorages()[tankIndex], true, io.support(IO.IN))
                .click(player, shiftDown);
    }

    @Override
    @ApiStatus.Internal
    public void setFluidHatchLockedFluid(FluidStack fluid) {
        requireFluidHatchLocking();
        if (!tank.getFluidInTank(0).isEmpty()) {
            return;
        }
        if (fluid.isEmpty()) {
            tank.setLocked(false);
        } else {
            tank.setLocked(true, fluid.copyWithAmount(1));
        }
    }

    @Override
    @ApiStatus.Internal
    public void setFluidHatchLocked(boolean locked) {
        requireFluidHatchLocking();
        tank.setLocked(locked);
    }

    private void requireFluidHatchActions() {
        if (!supportsFluidHatchActions()) {
            throw new IllegalStateException("Fluid hatch definition requires specialized action handling.");
        }
    }

    private void requireFluidHatchLocking() {
        requireFluidHatchActions();
        if (!supportsFluidHatchLocking()) {
            throw new IllegalStateException("Fluid hatch does not expose locked-fluid controls.");
        }
    }

    private record LDLib2FluidClickTarget(IFluidHandler fluidTank, boolean allowClickFilled,
                                          boolean allowClickDrained) {

        private void click(ServerPlayer player, boolean shiftDown) {
            ItemStack currentStack = player.containerMenu.getCarried();
            if (FluidUtil.getFluidHandler(currentStack).isEmpty()) {
                return;
            }
            int maxAttempts = shiftDown ? currentStack.getCount() : 1;
            FluidStack initialFluid = fluidTank.getFluidInTank(0).copy();
            if (allowClickFilled && initialFluid.getAmount() > 0 &&
                    fillContainer(player, currentStack, maxAttempts, initialFluid)) {
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
                filledResult = mergeOrStoreFluidContainerResult(player, filledResult, remainingStack);
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
                drainedResult = mergeOrStoreFluidContainerResult(player, drainedResult, remainingStack);
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

        private static void finishContainerClick(ServerPlayer player, ItemStack currentStack,
                                                 ItemStack resultStack) {
            if (currentStack.isEmpty()) {
                player.containerMenu.setCarried(resultStack);
            } else {
                player.containerMenu.setCarried(currentStack);
                player.getInventory().placeItemBackInInventory(resultStack);
            }
            player.containerMenu.broadcastChanges();
        }
    }

    private static ItemStack mergeOrStoreFluidContainerResult(ServerPlayer player, ItemStack storedResult,
                                                              ItemStack remainingStack) {
        if (storedResult.isEmpty()) {
            return remainingStack.copy();
        }
        if (ItemStack.isSameItemSameComponents(storedResult, remainingStack)) {
            int availableSpace = storedResult.getMaxStackSize() - storedResult.getCount();
            if (remainingStack.getCount() <= availableSpace) {
                storedResult.grow(remainingStack.getCount());
            } else {
                player.getInventory().placeItemBackInInventory(remainingStack);
            }
            return storedResult;
        }
        player.getInventory().placeItemBackInInventory(storedResult);
        return remainingStack.copy();
    }

    /**
     * Adapts this legacy multipart Fancy page to a holder-scoped LDLib2 Fancy provider.
     */
    private final class FluidHatchLDLib2Page implements LDLib2FancyUIProvider {

        private final Player player;
        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;

        private FluidHatchLDLib2Page(Player player, MachineUIHolder holder) {
            requireMatchingLDLib2Holder(holder);
            this.player = player;
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(
                    FluidHatchPartMachine.this, player, holder);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != FluidHatchPartMachine.this) {
                throw new IllegalStateException("Fluid hatch page holder no longer resolves its opened machine.");
            }
            return createLDLib2MainElement(player, holder);
        }

        @Override
        public IGuiTexture getTabIcon() {
            return GuiTextures.itemStack(getDefinition().getItem());
        }

        @Override
        public Component getTitle() {
            return Component.translatable(getDefinition().getDescriptionId());
        }

        @Override
        public int getLDLib2PageWidth() {
            return FluidHatchPartMachine.this.getLDLib2PageWidth();
        }

        @Override
        public int getLDLib2PageHeight() {
            return FluidHatchPartMachine.this.getLDLib2PageHeight();
        }

        @Override
        public void attachSideTabs(LDLib2FancyTabsElement tabs) {
            tabs.attachSubTab(directionalPage);
        }

        @Override
        public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    FluidHatchPartMachine.this, holder));
            if (isCircuitSlotEnabled() && io.support(IO.IN)) {
                configuratorPanel.attachConfigurators(new LDLib2CircuitFancyConfigurator(
                        FluidHatchPartMachine.this, holder));
            }
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            tooltipsPanel.attachTooltips(FluidHatchPartMachine.this);
            for (var trait : getTraitHolder().getAllTraits()) {
                if (trait instanceof IFancyTooltip tooltip) {
                    tooltipsPanel.attachTooltips(tooltip);
                }
            }
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(Component.translatable(getDefinition().getDescriptionId()));
        }

        @Override
        @Nullable
        public LDLib2FancyUIProvider.PageGroupingData getPageGroupingData() {
            return switch (io) {
                case IN -> new LDLib2FancyUIProvider.PageGroupingData(
                        "gtpm.multiblock.page_switcher.io.import", 1);
                case OUT -> new LDLib2FancyUIProvider.PageGroupingData(
                        "gtpm.multiblock.page_switcher.io.export", 2);
                case BOTH -> new LDLib2FancyUIProvider.PageGroupingData(
                        "gtpm.multiblock.page_switcher.io.both", 3);
                case NONE -> null;
            };
        }
    }

    public String getFormattedFluidAmount(FluidStack fluidStack) {
        return String.format("%,d", fluidStack.isEmpty() ? 0 : fluidStack.getAmount());
    }
}
