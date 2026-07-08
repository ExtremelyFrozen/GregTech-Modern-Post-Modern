package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfigurator;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.multiblock.AutoStockingPart;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 Fancy page for advanced auto-stocking item and fluid thresholds.
 *
 * <p>
 * The page sends GTM machine actions for server-side mutation instead of LDLib2-owned business state channels.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class LDLib2AutoStockingFancyConfigurator implements LDLib2FancyConfigurator {

    private static final int CONFIGURATOR_WIDTH = 90;
    private static final int CONFIGURATOR_HEIGHT = 70;
    private static final ResourceLocation SET_AUTO_STOCKING_MIN_STACK_SIZE_ACTION = GTCEu
            .id("set_auto_stocking_min_stack_size");
    private static final ResourceLocation SET_AUTO_STOCKING_TICKS_PER_CYCLE_ACTION = GTCEu
            .id("set_auto_stocking_ticks_per_cycle");
    private static final ResourceLocation MIN_STACK_SIZE_FIELD = SyncFieldData.key("minStackSize");
    private static final ResourceLocation TICKS_PER_CYCLE_FIELD = SyncFieldData.key("ticksPerCycle");

    private final AutoStockingPart machine;
    private final MachineUIHolder holder;

    static {
        SyncActionDispatchers.server().register(new MinStackSizeActionHandler());
        SyncActionDispatchers.server().register(new TicksPerCycleActionHandler());
    }

    /**
     * Creates an advanced auto-stocking configurator bound to the opened machine holder.
     */
    public LDLib2AutoStockingFancyConfigurator(AutoStockingPart machine, MachineUIHolder holder) {
        this.machine = machine;
        this.holder = holder;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtpm.gui.adv_stocking_config.title");
    }

    @Override
    public IGuiTexture getIcon() {
        return GuiTextures.itemStack(GTItems.TOOL_DATA_STICK.asStack());
    }

    @Override
    public UIElement createLDLib2Configurator() {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, CONFIGURATOR_WIDTH, CONFIGURATOR_HEIGHT);

        String suffix = switch (machine.getStockingTarget()) {
            case ITEM -> "min_item_count";
            case FLUID -> "min_fluid_count";
        };
        root.addChild(new GTLabelElement(4, 2, 81, 10,
                "gtpm.gui.title.adv_stocking_config." + suffix, true));

        GTIntInputElement minStackSizeInput = new GTIntInputElement(4, 12, 81, 14, machine::getMinStackSize,
                this::setLDLib2MinStackSize);
        minStackSizeInput.setMin(1);
        minStackSizeInput.style(style -> style.tooltips(Component.translatable(
                "gtpm.gui.adv_stocking_config." + suffix)));
        root.addChild(minStackSizeInput);

        root.addChild(new GTLabelElement(4, 36, 81, 10,
                "gtpm.gui.title.adv_stocking_config.ticks_per_cycle", true));

        GTIntInputElement ticksPerCycleInput = new GTIntInputElement(4, 46, 81, 14, machine::getTicksPerCycle,
                this::setLDLib2TicksPerCycle);
        ticksPerCycleInput.setMin(ConfigHolder.INSTANCE.compat.ae2.updateIntervals);
        ticksPerCycleInput.style(style -> style.tooltips(Component.translatable(
                "gtpm.gui.adv_stocking_config.ticks_per_cycle")));
        root.addChild(ticksPerCycleInput);

        return root;
    }

    @Override
    public int getLDLib2ConfiguratorWidth() {
        return CONFIGURATOR_WIDTH;
    }

    @Override
    public int getLDLib2ConfiguratorHeight() {
        return CONFIGURATOR_HEIGHT;
    }

    public void setLDLib2MinStackSize(int value) {
        machine.setMinStackSize(value);
        var currentMachine = holder.getMachine();
        if (currentMachine != null && currentMachine.isRemote()) {
            MachineUIHelper.sendAction(holder, createSetMinStackSizeAction(value));
        }
    }

    public void setLDLib2TicksPerCycle(int value) {
        machine.setTicksPerCycle(value);
        var currentMachine = holder.getMachine();
        if (currentMachine != null && currentMachine.isRemote()) {
            MachineUIHelper.sendAction(holder, createSetTicksPerCycleAction(value));
        }
    }

    private static SyncActionData createSetMinStackSizeAction(int minStackSize) {
        return createSetAutoStockingIntAction(SET_AUTO_STOCKING_MIN_STACK_SIZE_ACTION, MIN_STACK_SIZE_FIELD,
                minStackSize);
    }

    private static SyncActionData createSetTicksPerCycleAction(int ticksPerCycle) {
        return createSetAutoStockingIntAction(SET_AUTO_STOCKING_TICKS_PER_CYCLE_ACTION, TICKS_PER_CYCLE_FIELD,
                ticksPerCycle);
    }

    private static SyncActionData createSetAutoStockingIntAction(ResourceLocation actionId, ResourceLocation field,
                                                                 int value) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, new JsonPrimitive(value))
                        .build())
                .build();
        return new SyncActionData(actionId, value, payload);
    }

    private static final class MinStackSizeActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_AUTO_STOCKING_MIN_STACK_SIZE_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof AutoStockingPart &&
                    context.holder() instanceof LDLib2FancyUIMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            Integer value = readIntPayload(payload, MIN_STACK_SIZE_FIELD);
            return value != null && value >= 1;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof AutoStockingPart stockingPart)) {
                throw new IllegalStateException("Auto-stocking min stack size action received an invalid holder.");
            }
            Integer value = readIntPayload(context.payload(), MIN_STACK_SIZE_FIELD);
            if (value == null || value < 1) {
                throw new IllegalStateException("Auto-stocking min stack size action payload is invalid.");
            }
            stockingPart.setMinStackSize(value);
        }
    }

    private static final class TicksPerCycleActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_AUTO_STOCKING_TICKS_PER_CYCLE_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof AutoStockingPart &&
                    context.holder() instanceof LDLib2FancyUIMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            Integer value = readIntPayload(payload, TICKS_PER_CYCLE_FIELD);
            return value != null && value >= ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof AutoStockingPart stockingPart)) {
                throw new IllegalStateException("Auto-stocking ticks per cycle action received an invalid holder.");
            }
            Integer value = readIntPayload(context.payload(), TICKS_PER_CYCLE_FIELD);
            if (value == null || value < ConfigHolder.INSTANCE.compat.ae2.updateIntervals) {
                throw new IllegalStateException("Auto-stocking ticks per cycle action payload is invalid.");
            }
            stockingPart.setTicksPerCycle(value);
        }
    }

    private static @Nullable Integer readIntPayload(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            return null;
        }
        return readIntPayload(fields, field);
    }

    private static @Nullable Integer readIntPayload(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            try {
                long value = primitive.getAsBigDecimal().longValueExact();
                if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                    return (int) value;
                }
            } catch (ArithmeticException | NumberFormatException e) {
                GTCEu.LOGGER.warn("Invalid auto-stocking integer action payload.", e);
            }
        }
        return null;
    }
}
