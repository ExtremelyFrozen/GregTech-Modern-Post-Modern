package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.DirectionalAutoOutputMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates and handles GT actions for item and fluid output-face interactions in LDLib2 directional pages.
 *
 * <p>
 * A face rejected by the machine validator fails before mutation so an invalid request cannot partially disable
 * auto-output while leaving the old face selected.
 */
public final class LDLib2DirectionalAutoOutputActions {

    private static final ResourceLocation CONFIGURE_ITEM_OUTPUT_SIDE_ACTION = GTCEu
            .id("configure_item_output_side");
    private static final ResourceLocation CONFIGURE_FLUID_OUTPUT_SIDE_ACTION = GTCEu
            .id("configure_fluid_output_side");
    private static final ResourceLocation SET_ITEM_INPUT_FROM_OUTPUT_SIDE_ACTION = GTCEu
            .id("set_item_input_from_output_side");
    private static final ResourceLocation SET_FLUID_INPUT_FROM_OUTPUT_SIDE_ACTION = GTCEu
            .id("set_fluid_input_from_output_side");
    private static final ResourceLocation OUTPUT_DIRECTION_FIELD = SyncFieldData.key("outputDirection");
    private static final ResourceLocation ALLOW_INPUT_FIELD = SyncFieldData.key("allowInput");

    static {
        SyncActionDispatchers.server().register(new ConfigureItemOutputSideActionHandler());
        SyncActionDispatchers.server().register(new ConfigureFluidOutputSideActionHandler());
        SyncActionDispatchers.server().register(new SetItemInputFromOutputSideActionHandler());
        SyncActionDispatchers.server().register(new SetFluidInputFromOutputSideActionHandler());
    }

    private LDLib2DirectionalAutoOutputActions() {}

    /**
     * Triggers common-side class initialization; handler registration remains owned by the static initializer.
     */
    public static void initialize() {}

    /**
     * Creates the item output-face action used by the item mode button and scene left-click shortcut.
     */
    public static SyncActionData createConfigureItemOutputSideAction(Direction direction) {
        return createConfigureOutputSideAction(CONFIGURE_ITEM_OUTPUT_SIDE_ACTION, direction);
    }

    /**
     * Creates the fluid output-face action used by the fluid mode button and scene right-click shortcut.
     */
    public static SyncActionData createConfigureFluidOutputSideAction(Direction direction) {
        return createConfigureOutputSideAction(CONFIGURE_FLUID_OUTPUT_SIDE_ACTION, direction);
    }

    /**
     * Creates an action that updates the global item input policy for the configured item output face.
     */
    public static SyncActionData createSetItemInputFromOutputSideAction(boolean allow) {
        return createSetInputFromOutputSideAction(SET_ITEM_INPUT_FROM_OUTPUT_SIDE_ACTION, allow);
    }

    /**
     * Creates an action that updates the global fluid input policy for the configured fluid output face.
     */
    public static SyncActionData createSetFluidInputFromOutputSideAction(boolean allow) {
        return createSetInputFromOutputSideAction(SET_FLUID_INPUT_FROM_OUTPUT_SIDE_ACTION, allow);
    }

    private static SyncActionData createConfigureOutputSideAction(ResourceLocation actionId, Direction direction) {
        int directionId = direction.get3DDataValue();
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(OUTPUT_DIRECTION_FIELD, new JsonPrimitive(directionId))
                        .build())
                .build();
        return new SyncActionData(actionId, directionId, payload);
    }

    private static SyncActionData createSetInputFromOutputSideAction(ResourceLocation actionId, boolean allow) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(ALLOW_INPUT_FIELD, new JsonPrimitive(allow))
                        .build())
                .build();
        return new SyncActionData(actionId, allow ? 1 : 0, payload);
    }

    private abstract static class ConfigureOutputSideActionHandler implements SyncActionHandler {

        @Override
        public boolean acceptsHolder(@NotNull SyncActionContext context) {
            DirectionalAutoOutputMachine machine = readDirectionalAutoOutputMachine(context);
            return machine != null && supportsOutput(machine);
        }

        @Override
        public boolean acceptsPayload(@NotNull DataComponentMap payload) {
            return readDirection(payload) != null;
        }

        @Override
        public boolean mayExecute(@NotNull ServerPlayer player, @NotNull SyncActionContext context) {
            DirectionalAutoOutputMachine machine = readDirectionalAutoOutputMachine(context);
            Direction direction = readDirection(context.payload());
            return !player.isSpectator() && machine != null && direction != null &&
                    (getOutputDirection(machine) == direction || canSetOutputDirection(machine, direction));
        }

        @Override
        public void execute(@NotNull SyncActionContext context) {
            DirectionalAutoOutputMachine machine = requireDirectionalAutoOutputMachine(context);
            Direction direction = requireDirection(context.payload());
            if (getOutputDirection(machine) == direction) {
                setAutoOutput(machine, !isAutoOutput(machine));
                return;
            }
            setAutoOutput(machine, false);
            setOutputDirection(machine, direction);
        }

        protected abstract boolean supportsOutput(DirectionalAutoOutputMachine machine);

        @Nullable
        protected abstract Direction getOutputDirection(DirectionalAutoOutputMachine machine);

        protected abstract boolean canSetOutputDirection(DirectionalAutoOutputMachine machine, Direction direction);

        protected abstract void setOutputDirection(DirectionalAutoOutputMachine machine, Direction direction);

        protected abstract boolean isAutoOutput(DirectionalAutoOutputMachine machine);

        protected abstract void setAutoOutput(DirectionalAutoOutputMachine machine, boolean enabled);
    }

    private static final class ConfigureItemOutputSideActionHandler extends ConfigureOutputSideActionHandler {

        @Override
        public @NotNull ResourceLocation actionId() {
            return CONFIGURE_ITEM_OUTPUT_SIDE_ACTION;
        }

        @Override
        protected boolean supportsOutput(DirectionalAutoOutputMachine machine) {
            return machine.supportsAutoOutputItems();
        }

        @Override
        protected @Nullable Direction getOutputDirection(DirectionalAutoOutputMachine machine) {
            return machine.getItemOutputDirection();
        }

        @Override
        protected boolean canSetOutputDirection(DirectionalAutoOutputMachine machine, Direction direction) {
            return machine.canSetItemOutputDirection(direction);
        }

        @Override
        protected void setOutputDirection(DirectionalAutoOutputMachine machine, Direction direction) {
            machine.setItemOutputDirection(direction);
        }

        @Override
        protected boolean isAutoOutput(DirectionalAutoOutputMachine machine) {
            return machine.isAutoOutputItems();
        }

        @Override
        protected void setAutoOutput(DirectionalAutoOutputMachine machine, boolean enabled) {
            machine.setAllowAutoOutputItems(enabled);
        }
    }

    private static final class ConfigureFluidOutputSideActionHandler extends ConfigureOutputSideActionHandler {

        @Override
        public @NotNull ResourceLocation actionId() {
            return CONFIGURE_FLUID_OUTPUT_SIDE_ACTION;
        }

        @Override
        protected boolean supportsOutput(DirectionalAutoOutputMachine machine) {
            return machine.supportsAutoOutputFluids();
        }

        @Override
        protected @Nullable Direction getOutputDirection(DirectionalAutoOutputMachine machine) {
            return machine.getFluidOutputDirection();
        }

        @Override
        protected boolean canSetOutputDirection(DirectionalAutoOutputMachine machine, Direction direction) {
            return machine.canSetFluidOutputDirection(direction);
        }

        @Override
        protected void setOutputDirection(DirectionalAutoOutputMachine machine, Direction direction) {
            machine.setFluidOutputDirection(direction);
        }

        @Override
        protected boolean isAutoOutput(DirectionalAutoOutputMachine machine) {
            return machine.isAutoOutputFluids();
        }

        @Override
        protected void setAutoOutput(DirectionalAutoOutputMachine machine, boolean enabled) {
            machine.setAllowAutoOutputFluids(enabled);
        }
    }

    private abstract static class SetInputFromOutputSideActionHandler implements SyncActionHandler {

        @Override
        public boolean acceptsHolder(@NotNull SyncActionContext context) {
            DirectionalAutoOutputMachine machine = readDirectionalAutoOutputMachine(context);
            return machine != null && supportsOutput(machine);
        }

        @Override
        public boolean acceptsPayload(@NotNull DataComponentMap payload) {
            return readAllowInput(payload) != null;
        }

        @Override
        public boolean mayExecute(@NotNull ServerPlayer player, @NotNull SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(@NotNull SyncActionContext context) {
            DirectionalAutoOutputMachine machine = requireDirectionalAutoOutputMachine(context);
            if (!supportsOutput(machine)) {
                throw new IllegalStateException("Output-side input action received an unsupported holder.");
            }
            setAllowInput(machine, requireAllowInput(context.payload()));
        }

        protected abstract boolean supportsOutput(DirectionalAutoOutputMachine machine);

        protected abstract void setAllowInput(DirectionalAutoOutputMachine machine, boolean allow);
    }

    private static final class SetItemInputFromOutputSideActionHandler extends SetInputFromOutputSideActionHandler {

        @Override
        public @NotNull ResourceLocation actionId() {
            return SET_ITEM_INPUT_FROM_OUTPUT_SIDE_ACTION;
        }

        @Override
        protected boolean supportsOutput(DirectionalAutoOutputMachine machine) {
            return machine.supportsAutoOutputItems();
        }

        @Override
        protected void setAllowInput(DirectionalAutoOutputMachine machine, boolean allow) {
            machine.setAllowItemInputFromOutputSide(allow);
        }
    }

    private static final class SetFluidInputFromOutputSideActionHandler extends SetInputFromOutputSideActionHandler {

        @Override
        public @NotNull ResourceLocation actionId() {
            return SET_FLUID_INPUT_FROM_OUTPUT_SIDE_ACTION;
        }

        @Override
        protected boolean supportsOutput(DirectionalAutoOutputMachine machine) {
            return machine.supportsAutoOutputFluids();
        }

        @Override
        protected void setAllowInput(DirectionalAutoOutputMachine machine, boolean allow) {
            machine.setAllowFluidInputFromOutputSide(allow);
        }
    }

    private static @Nullable DirectionalAutoOutputMachine readDirectionalAutoOutputMachine(
                                                                                           SyncActionContext context) {
        if (!(context.holder() instanceof LDLib2FancyActionMachine)) {
            return null;
        }
        if (context.holder() instanceof DirectionalAutoOutputMachine directionalAutoOutputMachine) {
            return directionalAutoOutputMachine;
        }
        if (context.holder() instanceof MetaMachine machine) {
            return machine.getTrait(AutoOutputTrait.TYPE);
        }
        return null;
    }

    private static DirectionalAutoOutputMachine requireDirectionalAutoOutputMachine(SyncActionContext context) {
        DirectionalAutoOutputMachine machine = readDirectionalAutoOutputMachine(context);
        if (machine == null) {
            throw new IllegalStateException("Directional auto-output action received an invalid holder.");
        }
        return machine;
    }

    private static Direction requireDirection(DataComponentMap payload) {
        Direction direction = readDirection(payload);
        if (direction == null) {
            throw new IllegalStateException("Directional auto-output action payload is missing a valid direction.");
        }
        return direction;
    }

    private static boolean requireAllowInput(DataComponentMap payload) {
        Boolean value = readAllowInput(payload);
        if (value == null) {
            throw new IllegalStateException("Output-side input action payload is missing a valid boolean state.");
        }
        return value;
    }

    private static @Nullable Boolean readAllowInput(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            return null;
        }
        JsonElement element = fields.get(ALLOW_INPUT_FIELD);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }

    private static @Nullable Direction readDirection(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            return null;
        }
        JsonElement element = fields.get(OUTPUT_DIRECTION_FIELD);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return null;
        }

        int directionId;
        try {
            directionId = primitive.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            GTCEu.LOGGER.warn("Directional auto-output action received a non-integral direction {}", element,
                    exception);
            return null;
        }
        if (directionId < 0 || directionId >= Direction.values().length) {
            return null;
        }
        return Direction.from3DDataValue(directionId);
    }
}
