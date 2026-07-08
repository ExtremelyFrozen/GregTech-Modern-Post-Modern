package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BufferMachine extends TieredMachine implements LDLib2FancyUIMachine {

    public static final int TANK_SIZE = 64000;
    private static final ResourceLocation CLICK_BUFFER_FLUID_SLOT_ACTION = GTCEu.id("click_buffer_fluid_slot");
    private static final ResourceLocation TANK_FIELD = SyncFieldData.key("tank");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");

    static {
        SyncActionDispatchers.server().register(new BufferFluidSlotActionHandler());
    }

    @SaveField
    @Getter
    protected final NotifiableItemStackHandler inventory;

    @SaveField
    @Getter
    protected final NotifiableFluidTank tank;
    @SaveField
    @SyncToClient
    public final AutoOutputTrait autoOutput;

    public BufferMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);
        this.inventory = attachTrait(new NotifiableItemStackHandler(getInventorySize(tier), IO.BOTH));
        this.tank = attachTrait(new NotifiableFluidTank(getTankSize(tier), TANK_SIZE, IO.BOTH));
        this.autoOutput = attachTrait(new AutoOutputTrait(List.of(inventory), List.of(tank)));
    }

    ////////////////////////////////
    // ***** Initialization ******//
    ////////////////////////////////

    public static int getInventorySize(int tier) {
        return (int) Math.pow(tier + 2, 2);
    }

    public static int getTankSize(int tier) {
        return tier + 2;
    }

    ////////////////////////////////
    // ********** GUI *********** //
    ////////////////////////////////

    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        int invTier = getTankSize(tier);
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0,
                getLDLib2PageWidth(), getLDLib2PageHeight());
        UIElement container = UITemplate.setLDLib2Bounds(new UIElement(), 4, 4,
                18 * (invTier + 1) + 8, 18 * invTier + 8);
        container.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        int index = 0;
        for (int y = 0; y < invTier; y++) {
            for (int x = 0; x < invTier; x++) {
                container.addChild(createLDLib2ItemSlot(index++, 4 + x * 18, 4 + y * 18));
            }
        }

        index = 0;
        for (int y = 0; y < invTier; y++) {
            container.addChild(createLDLib2FluidSlot(shell, index++, 4 + invTier * 18, 4 + y * 18));
        }

        root.addChild(container);
        return root;
    }

    @Override
    public int getLDLib2PageWidth() {
        return 18 * (getTankSize(tier) + 1) + 16;
    }

    @Override
    public int getLDLib2PageHeight() {
        return 18 * getTankSize(tier) + 16;
    }

    private GTItemSlotElement createLDLib2ItemSlot(int slot, int x, int y) {
        GTItemSlotElement slotElement = new GTItemSlotElement(getInventory().storage, slot)
                .setCanPutItems(true)
                .setCanTakeItems(true)
                .setBackgroundTexture(GuiTextures.SLOT);
        return UITemplate.setLDLib2Bounds(slotElement, x, y, 18, 18);
    }

    private GTFluidSlotElement createLDLib2FluidSlot(LDLib2FancyMachineUIElement shell, int tankIndex, int x, int y) {
        GTFluidSlotElement fluidSlot = new GTFluidSlotElement()
                .setFluidTank(tank.getStorages()[tankIndex], 0)
                .setShowAmount(true)
                .setAllowClickFilled(true)
                .setAllowClickDrained(true)
                .setBackgroundTexture(GuiTextures.FLUID_SLOT);
        fluidSlot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0 && isRemote()) {
                MachineUIHelper.sendAction(shell.getHolder(), createClickBufferFluidSlotAction(tankIndex,
                        event.isShiftDown()));
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        });
        return UITemplate.setLDLib2Bounds(fluidSlot, x, y, 18, 18);
    }

    private void clickLDLib2FluidSlot(ServerPlayer player, int tankIndex, boolean shiftDown) {
        if (tankIndex < 0 || tankIndex >= tank.getStorages().length) {
            throw new IllegalArgumentException("Invalid buffer fluid tank index: " + tankIndex);
        }
        new LDLib2FluidClickTarget(tank.getStorages()[tankIndex], true, true).click(player, shiftDown);
    }

    private static SyncActionData createClickBufferFluidSlotAction(int tankIndex, boolean shiftDown) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TANK_FIELD, new JsonPrimitive(tankIndex))
                        .put(SHIFT_FIELD, new JsonPrimitive(shiftDown))
                        .build())
                .build();
        return new SyncActionData(CLICK_BUFFER_FLUID_SLOT_ACTION, tankIndex * 2 + (shiftDown ? 1 : 0), payload);
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

    private static final class BufferFluidSlotActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return CLICK_BUFFER_FLUID_SLOT_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof BufferMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readNonNegativeInteger(fields, TANK_FIELD) != null &&
                    readBoolean(fields, SHIFT_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof BufferMachine machine)) {
                throw new IllegalStateException("Buffer fluid slot action received a non-buffer machine.");
            }
            machine.clickLDLib2FluidSlot(context.player(), requireNonNegativeInteger(context.payload(), TANK_FIELD),
                    requireBoolean(context.payload(), SHIFT_FIELD));
        }
    }

    private static SyncFieldData requireFieldData(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Buffer fluid slot action payload is missing field data.");
        }
        return fields;
    }

    private static int requireNonNegativeInteger(DataComponentMap payload, ResourceLocation field) {
        Integer value = readNonNegativeInteger(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Buffer fluid slot action payload is missing " + field + ".");
        }
        return value;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        Boolean value = readBoolean(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Buffer fluid slot action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Integer readNonNegativeInteger(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            long value = primitive.getAsLong();
            if (value >= 0L && value <= Integer.MAX_VALUE) {
                return (int) value;
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
}
