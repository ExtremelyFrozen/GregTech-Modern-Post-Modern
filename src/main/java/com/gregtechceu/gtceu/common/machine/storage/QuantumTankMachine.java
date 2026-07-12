package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.ColorPattern;
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
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTraitType;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTMath;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class QuantumTankMachine extends TieredMachine implements IControllable,
                                LDLib2MachineUIProvider {

    public static Object2LongMap<MachineDefinition> TANK_CAPACITY = Util.make(new Object2LongArrayMap<>(),
            map -> map.defaultReturnValue(-1L));

    private static final int PAGE_WIDTH = 90;
    private static final int PAGE_HEIGHT = 63;
    private static final ResourceLocation CLICK_QUANTUM_TANK_FLUID_SLOT_ACTION = GTCEu
            .id("click_quantum_tank_fluid_slot");
    private static final ResourceLocation SET_QUANTUM_TANK_LOCKED_FLUID_ACTION = GTCEu
            .id("set_quantum_tank_locked_fluid");
    private static final ResourceLocation SET_QUANTUM_TANK_LOCKED_ACTION = GTCEu
            .id("set_quantum_tank_locked");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");
    private static final ResourceLocation LOCKED_FIELD = SyncFieldData.key("locked");

    static {
        SyncActionDispatchers.server().register(new QuantumTankFluidSlotActionHandler());
        SyncActionDispatchers.server().register(new QuantumTankLockedFluidActionHandler());
        SyncActionDispatchers.server().register(new QuantumTankLockedActionHandler());
    }

    @SaveField
    @SyncBoth
    private boolean isVoiding;

    @Getter
    private final long maxAmount;
    protected final FluidCache cache;
    @SyncToClient
    @SaveField
    private final CustomFluidTank lockedFluid;

    @Getter
    @SyncToClient
    @SaveField
    protected FluidStack stored = FluidStack.EMPTY;
    @Getter
    @SyncToClient
    @SaveField
    protected long storedAmount = 0;

    @SaveField
    @SyncToClient
    public final AutoOutputTrait autoOutput;

    public QuantumTankMachine(BlockEntityCreationInfo info, int tier, long maxAmount) {
        super(info, tier);
        this.maxAmount = maxAmount;
        this.cache = attachTrait(createCacheFluidHandler());
        this.lockedFluid = new CustomFluidTank(1000);
        this.autoOutput = attachTrait(AutoOutputTrait.ofFluids(cache));
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected FluidCache createCacheFluidHandler() {
        return new FluidCache();
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    protected void onFluidChanged() {
        if (!isRemote()) {
            syncDataHolder.markClientSyncFieldDirty("storedAmount");
            syncDataHolder.markClientSyncFieldDirty("stored");
        }
    }

    //////////////////////////////////////
    // ****** Capability ********//
    //////////////////////////////////////

    @Override
    public @Nullable IItemHandlerModifiable getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        if (side == getFrontFacing()) {
            return null;
        }
        return super.getItemHandlerCap(side, useCoverCapability);
    }

    @Override
    public @Nullable IFluidHandlerModifiable getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        if (side == getFrontFacing()) {
            return null;
        }
        return super.getFluidHandlerCap(side, useCoverCapability);
    }

    @Override
    public boolean isWorkingEnabled() {
        return autoOutput.isAutoOutputFluids();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        autoOutput.setAllowAutoOutputFluids(isWorkingAllowed);
    }

    //////////////////////////////////////
    // ******* Interaction *******//
    //////////////////////////////////////

    @Override
    public InteractionResult onUseWithItem(ExtendedUseOnContext context) {
        if (context.getClickedFace() == getFrontFacing() && !isRemote()) {
            if (FluidUtil.interactWithFluidHandler(context.getPlayer(), context.getHand(), cache)) {
                return InteractionResult.SUCCESS;
            }
        }
        return super.onUseWithItem(context);
    }

    public boolean isLocked() {
        return !lockedFluid.isEmpty();
    }

    protected void setLocked(boolean locked) {
        if (!stored.isEmpty() && locked) {
            var copied = stored.copyWithAmount(FluidType.BUCKET_VOLUME);
            lockedFluid.setFluid(copied);
        } else if (!locked) {
            lockedFluid.setFluid(FluidStack.EMPTY);
        }
        syncDataHolder.markClientSyncFieldDirty("lockedFluid");
    }

    protected void setLocked(FluidStack fluid) {
        if (fluid.isEmpty()) setLocked(false);
        else if (stored.isEmpty()) lockedFluid.setFluid(fluid);
        else if (stored.is(fluid.getFluid())) setLocked(true);
        syncDataHolder.markClientSyncFieldDirty("lockedFluid");
    }

    private void setVoiding(boolean voiding) {
        isVoiding = voiding;
    }

    public FluidStack getLockedFluid() {
        return lockedFluid.getFluid();
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        return UI.of(new LDLib2FancyMachineUIElement(new QuantumTankLDLib2Page(player, holder),
                player.getInventory(), holder, PAGE_WIDTH, PAGE_HEIGHT));
    }

    private UIElement createLDLib2MainPage(Player player, MachineUIHolder holder) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));
        root.addChild(new GTImageElement(4, 4, 82, 55, GuiTextures.DISPLAY));
        root.addChild(createLDLib2FluidAmountLabel());
        root.addChild(createLDLib2FluidAmountValueLabel());
        root.addChild(createLDLib2FluidSlot(player, holder));
        root.addChild(createLDLib2LockedFluidSlot(player, holder));
        root.addChild(new GTToggleButtonElement(4, 41, 18, 18,
                GuiTextures.BUTTON_FLUID_OUTPUT, this.autoOutput::isAutoOutputFluids,
                enabled -> setLDLib2AutoOutputFluids(player, enabled))
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.fluid_auto_output.tooltip"));
        root.addChild(new GTToggleButtonElement(22, 41, 18, 18,
                GuiTextures.BUTTON_LOCK, this::isLocked,
                locked -> setLDLib2Locked(player, holder, locked))
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.fluid_lock.tooltip"));
        root.addChild(createLDLib2VoidingButton());
        return root;
    }

    private static GTLabelElement createLDLib2FluidAmountLabel() {
        GTLabelElement label = new GTLabelElement(8, 8, 76, 10, "gtpm.gui.fluid_amount", true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTLabelElement createLDLib2FluidAmountValueLabel() {
        GTLabelElement label = new GTLabelElement(8, 18, 58, 10) {

            @Override
            public void screenTick() {
                setValue(Component.literal(FormattingUtil.formatBuckets(storedAmount)));
                super.screenTick();
            }
        };
        label.setValue(Component.literal(FormattingUtil.formatBuckets(storedAmount)));
        label.textStyle(style -> style
                .textColor(-1)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTFluidSlotElement createLDLib2FluidSlot(Player player, MachineUIHolder holder) {
        GTFluidSlotElement fluidSlot = new GTFluidSlotElement()
                .setFluidTank(cache, 0)
                .setShowAmount(false)
                .setAllowClickFilled(true)
                .setAllowClickDrained(true)
                .setBackgroundTexture(GuiTextures.FLUID_SLOT);
        fluidSlot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0 && player.level().isClientSide() &&
                    FluidUtil.getFluidHandler(player.containerMenu.getCarried()).isPresent()) {
                MachineUIHelper.sendAction(holder, createClickQuantumTankFluidSlotAction(event.isShiftDown()));
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        });
        return UITemplate.setLDLib2Bounds(fluidSlot, 68, 23, 18, 18);
    }

    private GTPhantomFluidSlotElement createLDLib2LockedFluidSlot(Player player, MachineUIHolder holder) {
        GTPhantomFluidSlotElement slot = new GTPhantomFluidSlotElement(this::getLockedFluid,
                fluid -> setLDLib2LockedFluid(player, holder, fluid), () -> FluidType.BUCKET_VOLUME) {

            @Override
            public void screenTick() {
                refreshFromSupplier();
                setShowAmount(false);
                super.screenTick();
            }
        };
        slot.setBackgroundTexture(ColorPattern.T_GRAY.rectTexture());
        slot.setShowAmount(false);
        UITemplate.setLDLib2Bounds(slot, 68, 41, 18, 18);
        slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (!player.level().isClientSide()) {
                return;
            }
            if (event.button == 0 || event.button == 1) {
                FluidStack fluid = FluidUtil.getFluidContained(player.containerMenu.getCarried())
                        .map(stack -> stack.copyWithAmount(FluidType.BUCKET_VOLUME))
                        .orElse(FluidStack.EMPTY);
                slot.setFluid(fluid);
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        });
        return slot;
    }

    private LDLib2FancyConfiguratorButton.Toggle createLDLib2WorkingEnabledConfigurator(Player player,
                                                                                        MachineUIHolder holder) {
        return new LDLib2FancyConfiguratorButton.Toggle(
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0.5, 1, 0.5),
                this::isWorkingEnabled,
                (event, pressed) -> {
                    setLDLib2AutoOutputFluids(player, pressed);
                    event.stopImmediatePropagation();
                    event.hasHandler = true;
                })
                .setTooltipsSupplier(pressed -> List.of(Component.translatable(
                        pressed ? "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled")));
    }

    private void setLDLib2LockedFluid(Player player, MachineUIHolder holder, FluidStack fluid) {
        setLocked(fluid);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetQuantumTankLockedFluidAction(fluid));
        }
    }

    private void setLDLib2Locked(Player player, MachineUIHolder holder, boolean locked) {
        setLocked(locked);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetQuantumTankLockedAction(locked));
        }
    }

    GTToggleButtonElement createLDLib2VoidingButton() {
        return new GTToggleButtonElement(40, 41, 18, 18,
                GuiTextures.BUTTON_VOID, () -> isVoiding, this::setLDLib2Voiding)
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.fluid_voiding_partial.tooltip");
    }

    private void setLDLib2Voiding(boolean voiding) {
        setVoiding(voiding);
        if (isRemote()) {
            sendServerSyncChanges();
        }
    }

    private void setLDLib2AutoOutputFluids(Player player, boolean enabled) {
        autoOutput.setAllowAutoOutputFluids(enabled);
        if (player.level().isClientSide()) {
            sendServerSyncChanges();
        }
    }

    private void clickLDLib2FluidSlot(ServerPlayer player, boolean shiftDown) {
        new LDLib2FluidClickTarget(cache, true, true).click(player, shiftDown);
    }

    private static SyncActionData createClickQuantumTankFluidSlotAction(boolean shiftDown) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SHIFT_FIELD, new JsonPrimitive(shiftDown))
                        .build())
                .build();
        return new SyncActionData(CLICK_QUANTUM_TANK_FLUID_SLOT_ACTION, shiftDown ? 1 : 0, payload);
    }

    private static SyncActionData createSetQuantumTankLockedFluidAction(FluidStack fluid) {
        FluidStack locked = fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(FluidType.BUCKET_VOLUME);
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(locked))
                .build();
        int sequence = FluidStack.hashFluidAndComponents(locked) * 31 + locked.getAmount();
        return new SyncActionData(SET_QUANTUM_TANK_LOCKED_FLUID_ACTION, sequence, payload);
    }

    private static SyncActionData createSetQuantumTankLockedAction(boolean locked) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(LOCKED_FIELD, new JsonPrimitive(locked))
                        .build())
                .build();
        return new SyncActionData(SET_QUANTUM_TANK_LOCKED_ACTION, locked ? 1 : 0, payload);
    }

    private final class QuantumTankLDLib2Page implements LDLib2FancyUIProvider {

        private final Player player;
        private final MachineUIHolder holder;

        private QuantumTankLDLib2Page(Player player, MachineUIHolder holder) {
            this.player = player;
            this.holder = holder;
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            return QuantumTankMachine.this.createLDLib2MainPage(player, holder);
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
            return PAGE_WIDTH;
        }

        @Override
        public int getLDLib2PageHeight() {
            return PAGE_HEIGHT;
        }

        @Override
        public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
            configuratorPanel.attachConfigurators(createLDLib2WorkingEnabledConfigurator(player, holder));
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            tooltipsPanel.attachTooltips(QuantumTankMachine.this);
            getTraitHolder().getAllTraits().stream()
                    .filter(IFancyTooltip.class::isInstance)
                    .map(IFancyTooltip.class::cast)
                    .forEach(tooltipsPanel::attachTooltips);
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(Component.translatable(getDefinition().getDescriptionId()));
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

    private abstract static class QuantumTankActionHandler implements SyncActionHandler {

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof QuantumTankMachine;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        protected QuantumTankMachine getMachine(SyncActionContext context) {
            if (!(context.holder() instanceof QuantumTankMachine machine)) {
                throw new IllegalStateException("Quantum tank action received a non-quantum-tank machine.");
            }
            return machine;
        }
    }

    private static final class QuantumTankFluidSlotActionHandler extends QuantumTankActionHandler {

        @Override
        public ResourceLocation actionId() {
            return CLICK_QUANTUM_TANK_FLUID_SLOT_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, SHIFT_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).clickLDLib2FluidSlot(context.player(), requireBoolean(context.payload(),
                    SHIFT_FIELD));
        }
    }

    private static final class QuantumTankLockedFluidActionHandler extends QuantumTankActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_QUANTUM_TANK_LOCKED_FLUID_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            return payload.has(GTDataComponents.FLUID_CONTENT.get());
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setLocked(requireFluidStack(context.payload()));
        }
    }

    private static final class QuantumTankLockedActionHandler extends QuantumTankActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_QUANTUM_TANK_LOCKED_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, LOCKED_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setLocked(requireBoolean(context.payload(), LOCKED_FIELD));
        }
    }

    private static SyncFieldData requireFieldData(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Quantum tank action payload is missing field data.");
        }
        return fields;
    }

    private static FluidStack requireFluidStack(DataComponentMap payload) {
        if (!payload.has(GTDataComponents.FLUID_CONTENT.get())) {
            throw new IllegalStateException("Quantum tank fluid action payload is missing fluid stack.");
        }
        return payload.getOrDefault(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY).copy();
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        Boolean value = readBoolean(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Quantum tank action payload is missing " + field + ".");
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

    protected class FluidCache extends MachineTrait implements IFluidHandler {

        public static final MachineTraitType<FluidCache> TYPE = new MachineTraitType<>(FluidCache.class);

        @Override
        public MachineTraitType<FluidCache> getTraitType() {
            return TYPE;
        }

        private final Predicate<FluidStack> filter = f -> !isLocked() ||
                FluidStack.isSameFluidSameComponents(getLockedFluid(), f);

        public FluidCache() {
            super();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return stored.copyWithAmount(GTMath.saturatedCast(storedAmount));
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            long free = isVoiding ? Long.MAX_VALUE : maxAmount - storedAmount;
            long canFill = 0;
            if ((stored.isEmpty() || FluidStack.isSameFluidSameComponents(resource, stored)) && filter.test(resource)) {
                canFill = Math.min(resource.getAmount(), free);
            }
            if (action.execute() && canFill > 0) {
                if (stored.isEmpty()) stored = resource.copyWithAmount(FluidType.BUCKET_VOLUME);
                storedAmount = Math.min(maxAmount, storedAmount + canFill);
                onFluidChanged();
            }
            return (int) canFill;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (stored.isEmpty()) return FluidStack.EMPTY;
            long toDrain = Math.min(storedAmount, maxDrain);
            var copy = stored.copyWithAmount((int) toDrain);
            if (action.execute() && toDrain > 0) {
                storedAmount -= toDrain;
                if (storedAmount == 0) stored = FluidStack.EMPTY;
                onFluidChanged();
            }
            return copy.isEmpty() ? FluidStack.EMPTY : copy;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!FluidStack.isSameFluidSameComponents(resource, stored)) return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public int getTankCapacity(int tank) {
            return GTMath.saturatedCast(maxAmount);
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return filter.test(stack);
        }

        public void exportToNearby(Direction... facings) {
            if (stored.isEmpty()) return;
            var level = getMachine().getLevel();
            var pos = getMachine().getBlockPos();
            for (Direction facing : facings) {
                var filter = getMachine().getFluidCapFilter(facing, IO.OUT);
                GTTransferUtils.getAdjacentFluidHandler(level, pos, facing)
                        .ifPresent(adj -> GTTransferUtils.transferFluidsFiltered(this, adj, filter));
            }
        }
    }
}
