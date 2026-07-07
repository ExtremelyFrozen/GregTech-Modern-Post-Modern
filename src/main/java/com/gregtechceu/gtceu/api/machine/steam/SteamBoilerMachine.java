package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.*;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SteamBoilerMachine extends SteamWorkableMachine
                                         implements LDLib2MachineUIProvider, IDataInfoProvider {

    private static final ResourceLocation CLICK_STEAM_BOILER_FLUID_SLOT_ACTION = GTCEu
            .id("click_steam_boiler_fluid_slot");
    private static final ResourceLocation FLUID_SLOT_FIELD = SyncFieldData.key("fluidSlot");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");
    protected static final int WATER_FLUID_SLOT = 0;
    protected static final int STEAM_FLUID_SLOT = 1;
    protected static final int FUEL_FLUID_SLOT = 2;

    static {
        SyncActionDispatchers.server().register(new SteamBoilerFluidSlotActionHandler());
    }

    @SaveField
    public final NotifiableFluidTank waterTank;
    @SaveField
    @SyncToClient
    @Getter
    private int currentTemperature;
    @SaveField
    @Getter
    private int timeBeforeCoolingDown;
    @Getter
    private boolean hasNoWater;
    @Nullable
    protected TickableSubscription temperatureSubs, autoOutputSubs;
    @Nullable
    protected ISubscription steamTankSubs;

    public SteamBoilerMachine(BlockEntityCreationInfo info, boolean isHighPressure) {
        super(info, isHighPressure, new RecipeLogic(),
                new NotifiableFluidTank(1, 16 * FluidType.BUCKET_VOLUME, IO.OUT));
        this.waterTank = attachTrait(createWaterTank());
        this.waterTank.setFilter(fluid -> fluid.getFluid().is(GTMaterials.Water.getFluidTag()));
    }

    //////////////////////////////////////
    // ***** Initialization *****//
    //////////////////////////////////////

    protected NotifiableFluidTank createWaterTank() {
        return new NotifiableFluidTank(1, 16 * FluidType.BUCKET_VOLUME, IO.IN);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        scheduleForNextServerTick(this::updateAutoOutputSubscription);
        scheduleForNextServerTick(this::updateSteamSubscription);
        steamTankSubs = steamTank.addChangedListener(this::updateAutoOutputSubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (steamTankSubs != null) {
            steamTankSubs.unsubscribe();
            steamTankSubs = null;
        }
    }

    @Override
    public boolean hasOutputFacing() {
        return false;
    }

    //////////////////////////////////////
    // ******* Auto Output *******//
    //////////////////////////////////////

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }

    protected void updateAutoOutputSubscription() {
        if (Direction.stream().filter(direction -> direction != getFrontFacing() && direction != Direction.DOWN)
                .anyMatch(direction -> GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getBlockPos(), direction))) {
            autoOutputSubs = subscribeServerTick(autoOutputSubs, this::autoOutput);
        } else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    protected void autoOutput() {
        if (getOffsetTimer() % 5 == 0) {
            steamTank.exportToNearby(Direction.stream()
                    .filter(direction -> direction != getFrontFacing() && direction != Direction.DOWN)
                    .filter(direction -> GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getBlockPos(), direction))
                    .toArray(Direction[]::new));
            updateAutoOutputSubscription();
        }
    }

    //////////////////////////////////////
    // ****** Recipe Logic ******//
    //////////////////////////////////////

    protected void updateSteamSubscription() {
        if (currentTemperature > 0) {
            temperatureSubs = subscribeServerTick(temperatureSubs, this::updateCurrentTemperature);
        } else if (temperatureSubs != null) {
            temperatureSubs.unsubscribe();
            temperatureSubs = null;
        }
    }

    protected void updateCurrentTemperature() {
        if (getWorkLogic().isWorking()) {
            if (getOffsetTimer() % 12 == 0) {
                if (currentTemperature < getMaxTemperature()) {
                    if (isHighPressure) {
                        currentTemperature++;
                    } else if (getOffsetTimer() % 24 == 0) {
                        currentTemperature++;
                    }
                }
            }
        } else if (timeBeforeCoolingDown == 0) {
            if (currentTemperature > 0) {
                currentTemperature -= getCoolDownRate();
                timeBeforeCoolingDown = getCooldownInterval();
            }
        } else {
            --timeBeforeCoolingDown;
        }

        if (getOffsetTimer() % 10 == 0) {
            if (currentTemperature >= 100) {
                int fillAmount = (int) getTotalSteamOutput();
                boolean hasDrainedWater = !waterTank.drainInternal(1, FluidAction.EXECUTE).isEmpty();
                var filledSteam = 0L;
                if (hasDrainedWater) {
                    filledSteam = steamTank.fillInternal(
                            GTMaterials.Steam.getFluid(fillAmount),
                            FluidAction.EXECUTE);
                }
                if (this.hasNoWater && hasDrainedWater) {
                    GTUtil.doExplosion(getLevel(), getBlockPos(), 2.0f);
                } else this.hasNoWater = !hasDrainedWater;
                if (filledSteam == 0 && hasDrainedWater && getLevel() instanceof ServerLevel serverLevel) {
                    final float x = getBlockPos().getX() + 0.5F;
                    final float y = getBlockPos().getY() + 0.5F;
                    final float z = getBlockPos().getZ() + 0.5F;

                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            x + getFrontFacing().getStepX() * 0.6,
                            y + getFrontFacing().getStepY() * 0.6,
                            z + getFrontFacing().getStepZ() * 0.6,
                            7 + GTValues.RNG.nextInt(3),
                            getFrontFacing().getStepX() / 2.0,
                            getFrontFacing().getStepY() / 2.0,
                            getFrontFacing().getStepZ() / 2.0, 0.1);

                    if (ConfigHolder.INSTANCE.machines.machineSounds) {
                        getLevel().playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0f,
                                1.0f);
                    }

                    // bypass capability check for special case behavior
                    steamTank.drainInternal(FluidType.BUCKET_VOLUME * 4, FluidAction.EXECUTE);
                }
            } else {
                this.hasNoWater = false;
            }
        }
        updateSteamSubscription();
    }

    protected int getCooldownInterval() {
        return isHighPressure ? 40 : 45;
    }

    @SuppressWarnings("MethodMayBeStatic")
    protected int getCoolDownRate() {
        return 1;
    }

    public int getMaxTemperature() {
        return isHighPressure ? 1000 : 500;
    }

    private double getTemperaturePercent() {
        return currentTemperature / (getMaxTemperature() * 1.0);
    }

    protected abstract long getBaseSteamOutput();

    /** Returns the current total steam output every 10 ticks. */
    public long getTotalSteamOutput() {
        if (currentTemperature < 100) return 0;
        return (long) (getBaseSteamOutput() * ((float) currentTemperature / getMaxTemperature()) / 2);
    }

    /**
     * Recipe Modifier for <b>Steam Boiler Machines</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Duration is multiplied by {@code 0.5} if the machine is high pressure
     *
     * @param machine a {@link SteamBoilerMachine}
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Steam Boiler
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof SteamBoilerMachine boilerMachine)) {
            return RecipeModifier.nullWrongType(SteamBoilerMachine.class, machine);
        }
        if (!boilerMachine.isHighPressure) return ModifierFunction.IDENTITY;

        return ModifierFunction.builder()
                .durationMultiplier(0.5)
                .build();
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        if (currentTemperature < getMaxTemperature()) {
            currentTemperature = Math.max(1, currentTemperature);
            updateSteamSubscription();
        }
        return value;
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        this.timeBeforeCoolingDown = getCooldownInterval();
    }

    //////////////////////////////////////
    // ******* Interaction *******//
    //////////////////////////////////////

    @Override
    protected InteractionResult onSoftMalletClick(ExtendedUseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onUseWithItem(ExtendedUseOnContext context) {
        if (!isRemote()) {
            if (FluidUtil.interactWithFluidHandler(context.getPlayer(), context.getHand(), waterTank)) {
                return InteractionResult.SUCCESS;
            }
        }
        return super.onUseWithItem(context);
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        UIElement root = createLDLib2Root(player, holder);
        addLDLib2AdditionalWidgets(root, player, holder);
        return UI.of(root);
    }

    protected UIElement createLDLib2Root(Player player, MachineUIHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 166);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_STEAM.get(isHighPressure)));
        root.addChild(createLDLib2TitleLabel());
        root.addChild(createLDLib2TemperatureProgressBar());
        root.addChild(createLDLib2FluidSlot(player, holder, waterTank.getStorages()[0], 83, 26,
                WATER_FLUID_SLOT, false, true));
        root.addChild(createLDLib2FluidSlot(player, holder, steamTank.getStorages()[0], 70, 26,
                STEAM_FLUID_SLOT, true, false));
        root.addChild(new GTImageElement(43, 44, 18, 18, GuiTextures.CANISTER_OVERLAY_STEAM.get(isHighPressure)));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(),
                GuiTextures.SLOT_STEAM.get(isHighPressure), 7, 84, true));
        return root;
    }

    protected void addLDLib2AdditionalWidgets(UIElement root, Player player, MachineUIHolder holder) {}

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

    private GTProgressBarElement createLDLib2TemperatureProgressBar() {
        var progressTexture = GuiTextures.progressBar(
                GuiTextures.PROGRESS_BAR_BOILER_EMPTY.get(isHighPressure),
                GuiTextures.PROGRESS_BAR_BOILER_HEAT);
        GTProgressBarElement progressBar = new GTProgressBarElement(this::getTemperaturePercent)
                .setProgressTexture(progressTexture.getEmptyBarArea(), progressTexture.getFilledBarArea())
                .setFillDirection(FillDirection.DOWN_TO_UP);
        progressBar.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable("gtpm.multiblock.large_boiler.temperature",
                        currentTemperature + 274, getMaxTemperature() + 274)),
                null, null, null));
        return UITemplate.setLDLib2Bounds(progressBar, 96, 26, 10, 54);
    }

    protected GTFluidSlotElement createLDLib2FluidSlot(Player player, MachineUIHolder holder, IFluidHandler storage,
                                                       int x, int y, int fluidSlot, boolean allowClickFilled,
                                                       boolean allowClickDrained) {
        GTFluidSlotElement tank = new GTFluidSlotElement()
                .setFluidTank(storage, 0)
                .setShowAmount(false)
                .setAllowClickFilled(allowClickFilled)
                .setAllowClickDrained(allowClickDrained)
                .setBackgroundTexture(GuiTextures.PROGRESS_BAR_BOILER_EMPTY.get(isHighPressure));
        if (allowClickFilled || allowClickDrained) {
            tank.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 0 && player.level().isClientSide() &&
                        FluidUtil.getFluidHandler(player.containerMenu.getCarried()).isPresent()) {
                    MachineUIHelper.sendAction(holder, createClickSteamBoilerFluidSlotAction(
                            fluidSlot, event.isShiftDown()));
                    event.stopImmediatePropagation();
                    event.hasHandler = true;
                }
            });
        }
        return UITemplate.setLDLib2Bounds(tank, x, y, 10, 54);
    }

    protected @Nullable LDLib2FluidClickTarget getLDLib2FluidClickTarget(int fluidSlot) {
        return switch (fluidSlot) {
            case WATER_FLUID_SLOT -> createLDLib2FluidClickTarget(waterTank.getStorages()[0], false, true);
            case STEAM_FLUID_SLOT -> createLDLib2FluidClickTarget(steamTank.getStorages()[0], true, false);
            default -> null;
        };
    }

    protected LDLib2FluidClickTarget createLDLib2FluidClickTarget(IFluidHandler fluidTank, boolean allowClickFilled,
                                                                  boolean allowClickDrained) {
        return new LDLib2FluidClickTarget(fluidTank, allowClickFilled, allowClickDrained);
    }

    private void clickLDLib2FluidSlot(ServerPlayer player, int fluidSlot, boolean shiftDown) {
        LDLib2FluidClickTarget target = getLDLib2FluidClickTarget(fluidSlot);
        if (target == null) {
            throw new IllegalArgumentException("Invalid steam boiler fluid slot: " + fluidSlot);
        }
        target.click(player, shiftDown);
    }

    private static SyncActionData createClickSteamBoilerFluidSlotAction(int fluidSlot, boolean shiftDown) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(FLUID_SLOT_FIELD, new JsonPrimitive(fluidSlot))
                        .put(SHIFT_FIELD, new JsonPrimitive(shiftDown))
                        .build())
                .build();
        return new SyncActionData(CLICK_STEAM_BOILER_FLUID_SLOT_ACTION, fluidSlot, payload);
    }

    protected record LDLib2FluidClickTarget(IFluidHandler fluidTank, boolean allowClickFilled,
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

    private static final class SteamBoilerFluidSlotActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return CLICK_STEAM_BOILER_FLUID_SLOT_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof SteamBoilerMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readFluidSlot(fields, FLUID_SLOT_FIELD) != null &&
                    readBoolean(fields, SHIFT_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof SteamBoilerMachine machine)) {
                throw new IllegalStateException("Steam boiler fluid slot action received a non-boiler machine.");
            }
            machine.clickLDLib2FluidSlot(context.player(), requireFluidSlot(context.payload(), FLUID_SLOT_FIELD),
                    requireBoolean(context.payload(), SHIFT_FIELD));
        }
    }

    private static int requireFluidSlot(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Steam boiler fluid slot action payload is missing field data.");
        }
        Integer value = readFluidSlot(fields, field);
        if (value == null) {
            throw new IllegalStateException("Steam boiler fluid slot action payload is missing " + field + ".");
        }
        return value;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Steam boiler fluid slot action payload is missing field data.");
        }
        Boolean value = readBoolean(fields, field);
        if (value == null) {
            throw new IllegalStateException("Steam boiler fluid slot action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Integer readFluidSlot(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            int value = primitive.getAsInt();
            if (value >= WATER_FLUID_SLOT && value <= FUEL_FLUID_SLOT) {
                return value;
            }
        }
        return null;
    }

    private static @Nullable Boolean readBoolean(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }

    //////////////////////////////////////
    // ********* Client *********//
    //////////////////////////////////////

    @Override
    public void animateTick(@NotNull RandomSource random) {
        if (isActive()) {
            final BlockPos pos = getBlockPos();
            float x = pos.getX() + 0.5F;
            float z = pos.getZ() + 0.5F;

            final var facing = getFrontFacing();
            final float horizontalOffset = random.nextFloat() * 0.6F - 0.3F;
            final float y = pos.getY() + random.nextFloat() * 0.375F;

            if (facing.getAxis() == Direction.Axis.X) {
                if (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE) x += 0.52F;
                else x -= 0.52F;
                z += horizontalOffset;
            } else if (facing.getAxis() == Direction.Axis.Z) {
                if (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE) z += 0.52F;
                else z -= 0.52F;
                x += horizontalOffset;
            }
            randomDisplayTick(random, x, y, z);
        }
    }

    protected void randomDisplayTick(RandomSource random, float x, float y, float z) {
        getLevel().addParticle(isHighPressure ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        getLevel().addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
    }

    @NotNull
    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL ||
                mode == PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO) {
            return Collections.singletonList(Component.translatable("gtpm.machine.steam_boiler.heat_amount",
                    FormattingUtil.formatNumbers((int) (getTemperaturePercent() * 100))));
        }
        return new ArrayList<>();
    }
}
