package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTPhantomFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
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
import com.gregtechceu.gtceu.api.gui.texture.ResourceBorderTexture;
import com.gregtechceu.gtceu.api.item.datacomponents.CreativeMachineInfo;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CreativeTankMachine extends QuantumTankMachine implements LDLib2MachineUIProvider {

    private static final int PAGE_WIDTH = 176;
    private static final int PAGE_HEIGHT = 131;
    private static final ResourceLocation SET_CREATIVE_TANK_FLUID_ACTION = GTCEu.id("set_creative_tank_fluid");
    private static final ResourceLocation SET_CREATIVE_TANK_MB_PER_CYCLE_ACTION = GTCEu
            .id("set_creative_tank_mb_per_cycle");
    private static final ResourceLocation SET_CREATIVE_TANK_TICKS_PER_CYCLE_ACTION = GTCEu
            .id("set_creative_tank_ticks_per_cycle");
    private static final ResourceLocation SET_CREATIVE_TANK_WORKING_ENABLED_ACTION = GTCEu
            .id("set_creative_tank_working_enabled");
    private static final ResourceLocation MB_PER_CYCLE_FIELD = SyncFieldData.key("mBPerCycle");
    private static final ResourceLocation TICKS_PER_CYCLE_FIELD = SyncFieldData.key("ticksPerCycle");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");

    static {
        SyncActionDispatchers.server().register(new CreativeTankFluidActionHandler());
        SyncActionDispatchers.server().register(new CreativeTankMBPerCycleActionHandler());
        SyncActionDispatchers.server().register(new CreativeTankTicksPerCycleActionHandler());
        SyncActionDispatchers.server().register(new CreativeTankWorkingEnabledActionHandler());
    }

    @Getter
    @SaveField
    @SyncToClient
    private int mBPerCycle = 1000;
    @Getter
    @SaveField
    @SyncToClient
    private int ticksPerCycle = 1;

    public CreativeTankMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.MAX, 1);
    }

    protected FluidCache createCacheFluidHandler() {
        return new InfiniteCache();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            autoOutput.setTicksPerCycle(ticksPerCycle);
        }
    }

    @Override
    public long getStoredAmount() {
        return (long) Math.ceil(1d * mBPerCycle / ticksPerCycle);
    }

    private InteractionResult updateStored(FluidStack fluid) {
        stored = fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(FluidType.BUCKET_VOLUME);
        onFluidChanged();
        return InteractionResult.SUCCESS;
    }

    private void setTicksPerCycle(int ticksPerCycle) {
        if (ticksPerCycle <= 0) {
            throw new IllegalArgumentException("Ticks per cycle must be positive: " + ticksPerCycle);
        }
        this.ticksPerCycle = ticksPerCycle;
        autoOutput.setTicksPerCycle(ticksPerCycle);
        syncDataHolder.markClientSyncFieldDirty("ticksPerCycle");
        onFluidChanged();
    }

    private void setMillibucketsPerCycle(int mBPerCycle) {
        if (mBPerCycle <= 0) {
            throw new IllegalArgumentException("Millibuckets per cycle must be positive: " + mBPerCycle);
        }
        this.mBPerCycle = mBPerCycle;
        syncDataHolder.markClientSyncFieldDirty("mBPerCycle");
        onFluidChanged();
    }

    @Override
    public InteractionResult onUseWithItem(ExtendedUseOnContext context) {
        var heldItem = context.getItemInHand();
        var player = context.getPlayer();
        if (context.getClickedFace() == getFrontFacing() && !isRemote()) {
            if (stored.isEmpty()) {
                return FluidUtil.getFluidContained(heldItem)
                        .map(this::updateStored)
                        .orElse(InteractionResult.PASS);
            }

            CustomFluidTank source = new CustomFluidTank(stored.copyWithAmount(Integer.MAX_VALUE));
            ItemStack result = FluidUtil.tryFillContainer(heldItem, source, Integer.MAX_VALUE, player, true)
                    .getResult();
            if (!result.isEmpty() && heldItem.getCount() > 1) {
                ItemHandlerHelper.giveItemToPlayer(player, result);
                result = heldItem.copy();
                result.shrink(1);
            }

            if (!result.isEmpty()) {
                player.setItemInHand(context.getHand(), result);
                return InteractionResult.SUCCESS;
            }
            return FluidUtil.getFluidContained(heldItem)
                    .map(this::updateStored)
                    .orElse(InteractionResult.PASS);
        }
        return super.onUseWithItem(context);
    }

    @Override
    public InteractionResult onUse(ExtendedUseOnContext context) {
        if (context.getClickedFace() == getFrontFacing() && !isRemote()) {
            if (context.getPlayer().isCrouching() && !stored.isEmpty()) {
                return updateStored(FluidStack.EMPTY);
            }
            return InteractionResult.PASS;
        }
        return super.onUse(context);
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        return UI.of(new LDLib2FancyMachineUIElement(new CreativeTankLDLib2Page(player, holder),
                player.getInventory(), holder, PAGE_WIDTH, PAGE_HEIGHT));
    }

    private UIElement createLDLib2MainPage(Player player, MachineUIHolder holder) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        root.addChild(createLDLib2StoredFluidSlot(player, holder));
        root.addChild(createLDLib2Label(7, 9, 162, 10, "gtpm.creative.tank.fluid"));
        root.addChild(new GTImageElement(7, 45, 154, 14, GuiTextures.DISPLAY));
        root.addChild(createLDLib2MillibucketsPerCycleField(player, holder));
        root.addChild(createLDLib2Label(7, 28, 162, 10, "gtpm.creative.tank.mbpc"));
        root.addChild(new GTImageElement(7, 82, 154, 14, GuiTextures.DISPLAY));
        root.addChild(createLDLib2TicksPerCycleField(player, holder));
        root.addChild(createLDLib2Label(7, 65, 162, 10, "gtpm.creative.tank.tpc"));
        root.addChild(createLDLib2ActivityButton(player, holder));
        return root;
    }

    private GTPhantomFluidSlotElement createLDLib2StoredFluidSlot(Player player, MachineUIHolder holder) {
        GTPhantomFluidSlotElement slot = new GTPhantomFluidSlotElement(this::getStored,
                fluid -> setLDLib2StoredFluid(player, holder, fluid), () -> FluidType.BUCKET_VOLUME) {

            @Override
            public void screenTick() {
                refreshFromSupplier();
                setShowAmount(false);
                super.screenTick();
            }
        };
        slot.setBackgroundTexture(GuiTextures.FLUID_SLOT);
        slot.setShowAmount(false);
        UITemplate.setLDLib2Bounds(slot, 36, 6, 18, 18);
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

    private GTLabelElement createLDLib2Label(int x, int y, int width, int height, String translationKey) {
        GTLabelElement label = new GTLabelElement(x, y, width, height, translationKey, true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTTextFieldElement createLDLib2MillibucketsPerCycleField(Player player, MachineUIHolder holder) {
        GTTextFieldElement field = new GTTextFieldElement(9, 47, 152, 10) {

            @Override
            public void screenTick() {
                if (!isFocused()) {
                    setText(Integer.toString(mBPerCycle), false);
                }
                super.screenTick();
            }
        };
        field.setNumbersOnlyInt(1, Integer.MAX_VALUE);
        field.setText(Integer.toString(mBPerCycle), false);
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        field.setTextResponder(value -> setLDLib2MillibucketsPerCycle(player, holder, value));
        return field;
    }

    private GTTextFieldElement createLDLib2TicksPerCycleField(Player player, MachineUIHolder holder) {
        GTTextFieldElement field = new GTTextFieldElement(9, 84, 152, 10) {

            @Override
            public void screenTick() {
                if (!isFocused()) {
                    setText(Integer.toString(ticksPerCycle), false);
                }
                super.screenTick();
            }
        };
        field.setNumbersOnlyInt(1, Integer.MAX_VALUE);
        field.setText(Integer.toString(ticksPerCycle), false);
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        field.setTextResponder(value -> setLDLib2TicksPerCycle(player, holder, value));
        return field;
    }

    private GTButtonElement createLDLib2ActivityButton(Player player, MachineUIHolder holder) {
        return new GTButtonElement(7, 101, 162, 20, createLDLib2ActivityButtonTexture(),
                event -> setLDLib2WorkingEnabled(player, holder, !isWorkingEnabled())) {

            @Override
            public void screenTick() {
                setButtonTexture(createLDLib2ActivityButtonTexture());
                super.screenTick();
            }
        };
    }

    private IGuiTexture createLDLib2ActivityButtonTexture() {
        return GuiTextures.group(ResourceBorderTexture.BUTTON_COMMON,
                GuiTextures.text(isWorkingEnabled() ? "gtpm.creative.activity.on" : "gtpm.creative.activity.off"));
    }

    private LDLib2FancyConfiguratorButton.Toggle createLDLib2WorkingEnabledConfigurator(Player player,
                                                                                        MachineUIHolder holder) {
        return new LDLib2FancyConfiguratorButton.Toggle(
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0.5, 1, 0.5),
                this::isWorkingEnabled,
                (event, pressed) -> {
                    setLDLib2WorkingEnabled(player, holder, pressed);
                    event.stopImmediatePropagation();
                    event.hasHandler = true;
                })
                .setTooltipsSupplier(pressed -> List.of(Component.translatable(
                        pressed ? "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled")));
    }

    private void setLDLib2StoredFluid(Player player, MachineUIHolder holder, FluidStack fluid) {
        updateStored(fluid);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeTankFluidAction(fluid));
        }
    }

    private void setLDLib2MillibucketsPerCycle(Player player, MachineUIHolder holder, String value) {
        if (value.isEmpty()) {
            return;
        }
        int parsedValue = parsePositiveInteger(value, "creative tank millibuckets per cycle");
        setMillibucketsPerCycle(parsedValue);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeTankMBPerCycleAction(parsedValue));
        }
    }

    private void setLDLib2TicksPerCycle(Player player, MachineUIHolder holder, String value) {
        if (value.isEmpty()) {
            return;
        }
        int parsedValue = parsePositiveInteger(value, "creative tank ticks per cycle");
        setTicksPerCycle(parsedValue);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeTankTicksPerCycleAction(parsedValue));
        }
    }

    private void setLDLib2WorkingEnabled(Player player, MachineUIHolder holder, boolean workingEnabled) {
        setWorkingEnabled(workingEnabled);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeTankWorkingEnabledAction(workingEnabled));
        }
    }

    private static int parsePositiveInteger(String value, String fieldName) {
        try {
            int parsedValue = Integer.parseInt(value);
            if (parsedValue <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive: " + parsedValue);
            }
            return parsedValue;
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid {} input: {}", fieldName, value, e);
            throw e;
        }
    }

    private static SyncActionData createSetCreativeTankFluidAction(FluidStack fluid) {
        FluidStack storedFluid = fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(FluidType.BUCKET_VOLUME);
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(storedFluid))
                .build();
        int sequence = FluidStack.hashFluidAndComponents(storedFluid) * 31 + storedFluid.getAmount();
        return new SyncActionData(SET_CREATIVE_TANK_FLUID_ACTION, sequence, payload);
    }

    private static SyncActionData createSetCreativeTankMBPerCycleAction(int mBPerCycle) {
        return createSetCreativeTankIntAction(SET_CREATIVE_TANK_MB_PER_CYCLE_ACTION, MB_PER_CYCLE_FIELD, mBPerCycle);
    }

    private static SyncActionData createSetCreativeTankTicksPerCycleAction(int ticksPerCycle) {
        return createSetCreativeTankIntAction(SET_CREATIVE_TANK_TICKS_PER_CYCLE_ACTION, TICKS_PER_CYCLE_FIELD,
                ticksPerCycle);
    }

    private static SyncActionData createSetCreativeTankWorkingEnabledAction(boolean workingEnabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(workingEnabled))
                        .build())
                .build();
        return new SyncActionData(SET_CREATIVE_TANK_WORKING_ENABLED_ACTION, workingEnabled ? 1 : 0, payload);
    }

    private static SyncActionData createSetCreativeTankIntAction(ResourceLocation actionId, ResourceLocation field,
                                                                 int value) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, new JsonPrimitive(value))
                        .build())
                .build();
        return new SyncActionData(actionId, value, payload);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        CreativeMachineInfo info = componentInput.get(GTDataComponents.CREATIVE_MACHINE_INFO);
        if (info != null) {
            mBPerCycle = info.outputPerCycle();
            ticksPerCycle = info.ticksPerCycle();
        }
    }

    @Override
    public void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(GTDataComponents.CREATIVE_MACHINE_INFO, new CreativeMachineInfo(mBPerCycle, ticksPerCycle));
    }

    private final class CreativeTankLDLib2Page implements LDLib2FancyUIProvider {

        private final Player player;
        private final MachineUIHolder holder;

        private CreativeTankLDLib2Page(Player player, MachineUIHolder holder) {
            this.player = player;
            this.holder = holder;
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            return CreativeTankMachine.this.createLDLib2MainPage(player, holder);
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
            tooltipsPanel.attachTooltips(CreativeTankMachine.this);
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

    private abstract static class CreativeTankActionHandler implements SyncActionHandler {

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof CreativeTankMachine;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        protected CreativeTankMachine getMachine(SyncActionContext context) {
            if (!(context.holder() instanceof CreativeTankMachine machine)) {
                throw new IllegalStateException("Creative tank action received a non-creative-tank machine.");
            }
            return machine;
        }
    }

    private static final class CreativeTankFluidActionHandler extends CreativeTankActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_TANK_FLUID_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            return payload.has(GTDataComponents.FLUID_CONTENT.get());
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).updateStored(requireFluidStack(context.payload()));
        }
    }

    private static final class CreativeTankMBPerCycleActionHandler extends CreativeTankActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_TANK_MB_PER_CYCLE_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readPositiveInteger(fields, MB_PER_CYCLE_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setMillibucketsPerCycle(requirePositiveInteger(context.payload(), MB_PER_CYCLE_FIELD));
        }
    }

    private static final class CreativeTankTicksPerCycleActionHandler extends CreativeTankActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_TANK_TICKS_PER_CYCLE_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readPositiveInteger(fields, TICKS_PER_CYCLE_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setTicksPerCycle(requirePositiveInteger(context.payload(), TICKS_PER_CYCLE_FIELD));
        }
    }

    private static final class CreativeTankWorkingEnabledActionHandler extends CreativeTankActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_TANK_WORKING_ENABLED_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, WORKING_ENABLED_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setWorkingEnabled(requireBoolean(context.payload(), WORKING_ENABLED_FIELD));
        }
    }

    private static SyncFieldData requireFieldData(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Creative tank action payload is missing field data.");
        }
        return fields;
    }

    private static FluidStack requireFluidStack(DataComponentMap payload) {
        if (!payload.has(GTDataComponents.FLUID_CONTENT.get())) {
            throw new IllegalStateException("Creative tank fluid action payload is missing fluid stack.");
        }
        return payload.getOrDefault(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY).copy();
    }

    private static int requirePositiveInteger(DataComponentMap payload, ResourceLocation field) {
        Integer value = readPositiveInteger(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Creative tank action payload is missing " + field + ".");
        }
        return value;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        Boolean value = readBoolean(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Creative tank action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Integer readPositiveInteger(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            long value = primitive.getAsLong();
            if (value > 0L && value <= Integer.MAX_VALUE) {
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

    private class InfiniteCache extends FluidCache {

        public InfiniteCache() {
            super();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return stored;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!stored.isEmpty() && FluidStack.isSameFluidSameComponents(stored, resource)) {
                return resource.getAmount();
            }
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (!stored.isEmpty()) {
                return stored.copyWithAmount(mBPerCycle);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (!stored.isEmpty() && FluidStack.isSameFluidSameComponents(stored, resource)) {
                return resource.copyWithAmount(mBPerCycle);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int getTankCapacity(int tank) {
            return FluidType.BUCKET_VOLUME;
        }
    }
}
