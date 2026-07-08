package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
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
import com.gregtechceu.gtceu.api.machine.feature.multiblock.PumpHatch;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

public class PumpHatchPartMachine extends FluidHatchPartMachine implements LDLib2MachineUIProvider, PumpHatch {

    private static final ResourceLocation CLICK_PUMP_HATCH_FLUID_SLOT_ACTION = GTCEu
            .id("click_pump_hatch_fluid_slot");
    private static final ResourceLocation SET_PUMP_HATCH_CONFIG_ACTION = GTCEu
            .id("set_pump_hatch_config");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");

    static {
        SyncActionDispatchers.server().register(new PumpHatchFluidSlotActionHandler());
        SyncActionDispatchers.server().register(new PumpHatchConfigActionHandler());
    }

    public PumpHatchPartMachine(BlockEntityCreationInfo info) {
        super(info, 0, IO.OUT, FluidType.BUCKET_VOLUME, 1);
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots) {
        return super.createTank(initialCapacity, slots)
                .setFilter(fluidStack -> fluidStack.getFluid().is(GTMaterials.Water.getFluidTag()));
    }

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
                GuiTextures.BUTTON_FLUID_OUTPUT, this::isWorkingEnabled,
                enabled -> setLDLib2WorkingEnabled(player, holder, enabled))
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.fluid_auto_input.tooltip"));
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
        return String.valueOf(tank.getFluidInTank(0).getAmount());
    }

    private GTFluidSlotElement createLDLib2FluidSlot(Player player, MachineUIHolder holder) {
        GTFluidSlotElement fluidSlot = new GTFluidSlotElement()
                .setFluidTank(tank.getStorages()[0], 0)
                .setShowAmount(true)
                .setAllowClickFilled(true)
                .setAllowClickDrained(io.support(IO.IN))
                .setBackgroundTexture(GuiTextures.FLUID_SLOT);
        fluidSlot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0 && player.level().isClientSide() &&
                    FluidUtil.getFluidHandler(player.containerMenu.getCarried()).isPresent()) {
                MachineUIHelper.sendAction(holder, createClickPumpHatchFluidSlotAction(event.isShiftDown()));
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        });
        return UITemplate.setLDLib2Bounds(fluidSlot, 90, 35, 18, 18);
    }

    private void setLDLib2WorkingEnabled(Player player, MachineUIHolder holder, boolean enabled) {
        setWorkingEnabled(enabled);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetPumpHatchConfigAction(enabled));
        }
    }

    private void clickLDLib2FluidSlot(ServerPlayer player, boolean shiftDown) {
        new LDLib2FluidClickTarget(tank.getStorages()[0], true, io.support(IO.IN)).click(player, shiftDown);
    }

    private static SyncActionData createClickPumpHatchFluidSlotAction(boolean shiftDown) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SHIFT_FIELD, new JsonPrimitive(shiftDown))
                        .build())
                .build();
        return new SyncActionData(CLICK_PUMP_HATCH_FLUID_SLOT_ACTION, shiftDown ? 1 : 0, payload);
    }

    private static SyncActionData createSetPumpHatchConfigAction(boolean enabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(enabled))
                        .build())
                .build();
        return new SyncActionData(SET_PUMP_HATCH_CONFIG_ACTION, enabled ? 1 : 0, payload);
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

    private static final class PumpHatchFluidSlotActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return CLICK_PUMP_HATCH_FLUID_SLOT_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof PumpHatchPartMachine;
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
            if (!(context.holder() instanceof PumpHatchPartMachine machine)) {
                throw new IllegalStateException("Pump hatch fluid slot action received a non-pump-hatch machine.");
            }
            machine.clickLDLib2FluidSlot(context.player(), requireBoolean(context.payload(), SHIFT_FIELD));
        }
    }

    private static final class PumpHatchConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_PUMP_HATCH_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof PumpHatch;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, WORKING_ENABLED_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof PumpHatch pumpHatch)) {
                throw new IllegalStateException("Pump hatch config action received an invalid holder.");
            }
            pumpHatch.setWorkingEnabled(requireBoolean(context.payload(), WORKING_ENABLED_FIELD));
        }
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Pump hatch action payload is missing field data.");
        }
        Boolean value = readBoolean(fields, field);
        if (value == null) {
            throw new IllegalStateException("Pump hatch action payload is missing " + field + ".");
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

    // By returning false here, we don't allow shift-clicking
    // with a screwdriver to swap the IO.
    @Override
    public boolean swapIO() {
        return false;
    }
}
