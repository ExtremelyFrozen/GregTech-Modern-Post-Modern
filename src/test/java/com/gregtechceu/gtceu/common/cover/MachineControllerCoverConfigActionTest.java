package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.cover.data.ControllerMode;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MachineControllerCoverConfigActionTest {

    private static final ResourceLocation ACTION_ID = GTCEu.id("set_machine_controller_cover_config");
    private static final ResourceLocation CONTROLLER_MODE_FIELD = SyncFieldData.key("controllerMode");
    private static final ResourceLocation MIN_REDSTONE_STRENGTH_FIELD = SyncFieldData.key("minRedstoneStrength");
    private static final ResourceLocation INVERTED_FIELD = SyncFieldData.key("inverted");
    private static final ResourceLocation PREVENT_POWER_FAIL_FIELD = SyncFieldData.key("preventPowerFail");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MachineControllerCoverConfigAction")
    public static void factoryEncodesCompleteAndNullControllerModes(GameTestHelper helper) {
        SyncActionData action = MachineControllerCoverConfigActions.createSetConfigAction(
                ControllerMode.COVER_UP, 7, true, false);
        SyncFieldData fields = requireFields(action.payload(), "machine controller factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the machine controller action sequence");
        helper.assertTrue(fields.fields().size() == 4,
                "factory encoded fields outside the machine controller protocol");
        assertIntField(helper, fields, CONTROLLER_MODE_FIELD, ControllerMode.COVER_UP.ordinal(),
                "factory controller mode");
        assertIntField(helper, fields, MIN_REDSTONE_STRENGTH_FIELD, 7, "factory redstone strength");
        assertBooleanField(helper, fields, INVERTED_FIELD, true, "factory inverted state");
        assertBooleanField(helper, fields, PREVENT_POWER_FAIL_FIELD, false, "factory power-failure state");

        SyncFieldData nullModeFields = requireFields(
                MachineControllerCoverConfigActions.createSetConfigAction(null, 1, false, true).payload(),
                "null controller mode factory");
        assertIntField(helper, nullModeFields, CONTROLLER_MODE_FIELD, -1, "factory null controller mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MachineControllerCoverConfigAction")
    public static void dispatcherPreservesSetterOrderForNullAndAllowedModes(GameTestHelper helper) {
        TrackingMachineControllerCover nullModeCover = createTrackingCover(List.of(ControllerMode.COVER_UP));
        helper.assertTrue(dispatch(helper, nullModeCover,
                MachineControllerCoverConfigActions.createSetConfigAction(null, 1, false, true)),
                "dispatcher rejected the null controller mode");
        assertState(helper, nullModeCover, null, 1, false, true, "null-mode action");
        helper.assertTrue(nullModeCover.getSetterOrder().equals("mode>redstone>inverted>prevent"),
                "handler changed the required controller setter order for the null mode");

        TrackingMachineControllerCover allowedModeCover = createTrackingCover(List.of(ControllerMode.COVER_UP));
        helper.assertTrue(dispatch(helper, allowedModeCover,
                MachineControllerCoverConfigActions.createSetConfigAction(
                        ControllerMode.COVER_UP, 15, true, false)),
                "dispatcher rejected an allowed controller mode");
        assertState(helper, allowedModeCover, ControllerMode.COVER_UP, 15, true, false,
                "allowed-mode action");
        helper.assertTrue(allowedModeCover.getSetterOrder().equals("mode>redstone>inverted>prevent"),
                "handler changed the required controller setter order for an allowed mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MachineControllerCoverConfigAction")
    public static void dispatcherEnforcesAllowedControllerModes(GameTestHelper helper) {
        TrackingMachineControllerCover cover = createTrackingCover(List.of(ControllerMode.COVER_UP));

        helper.assertTrue(!dispatch(helper, cover,
                MachineControllerCoverConfigActions.createSetConfigAction(
                        ControllerMode.COVER_DOWN, 7, false, false)),
                "dispatcher accepted a controller mode unavailable to this cover");
        helper.assertTrue(cover.getSetterOrder().isEmpty(),
                "rejected controller mode invoked machine controller setters");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MachineControllerCoverConfigAction")
    public static void factoryAndDispatcherEnforceRedstoneStrengthBoundaries(GameTestHelper helper) {
        TrackingMachineControllerCover minimumCover = createTrackingCover(List.of(ControllerMode.MACHINE));
        TrackingMachineControllerCover maximumCover = createTrackingCover(List.of(ControllerMode.MACHINE));

        helper.assertTrue(dispatch(helper, minimumCover,
                MachineControllerCoverConfigActions.createSetConfigAction(
                        ControllerMode.MACHINE, 1, false, false)),
                "dispatcher rejected redstone strength 1");
        helper.assertTrue(dispatch(helper, maximumCover,
                MachineControllerCoverConfigActions.createSetConfigAction(
                        ControllerMode.MACHINE, 15, false, false)),
                "dispatcher rejected redstone strength 15");
        helper.assertTrue(minimumCover.getAppliedRedstoneStrength() == 1,
                "minimum redstone strength was not applied");
        helper.assertTrue(maximumCover.getAppliedRedstoneStrength() == 15,
                "maximum redstone strength was not applied");

        assertFactoryRejected(() -> MachineControllerCoverConfigActions.createSetConfigAction(
                ControllerMode.MACHINE, 0, false, false),
                "factory accepted redstone strength 0");
        assertFactoryRejected(() -> MachineControllerCoverConfigActions.createSetConfigAction(
                ControllerMode.MACHINE, 16, false, false),
                "factory accepted redstone strength 16");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MachineControllerCoverConfigAction")
    public static void dispatcherRejectsMissingAndWrongTypeFields(GameTestHelper helper) {
        assertRejected(helper, action(payload(null, integer(1), bool(false), bool(false))),
                "missing controller mode");
        assertRejected(helper, action(payload(integer(-1), null, bool(false), bool(false))),
                "missing redstone strength");
        assertRejected(helper, action(payload(integer(-1), integer(1), null, bool(false))),
                "missing inverted state");
        assertRejected(helper, action(payload(integer(-1), integer(1), bool(false), null)),
                "missing power-failure state");
        assertRejected(helper, action(DataComponentMap.EMPTY), "missing field data");

        assertRejected(helper,
                action(payload(new JsonPrimitive("-1"), integer(1), bool(false), bool(false))),
                "string controller mode");
        assertRejected(helper,
                action(payload(integer(-1), new JsonPrimitive("1"), bool(false), bool(false))),
                "string redstone strength");
        assertRejected(helper,
                action(payload(integer(-1), integer(1), integer(0), bool(false))),
                "numeric inverted state");
        assertRejected(helper,
                action(payload(integer(-1), integer(1), bool(false), new JsonPrimitive("false"))),
                "string power-failure state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MachineControllerCoverConfigAction")
    public static void dispatcherRejectsNonIntegerAndOutOfRangeFields(GameTestHelper helper) {
        assertRejected(helper,
                action(payload(new JsonPrimitive(1.5D), integer(1), bool(false), bool(false))),
                "fractional controller mode");
        assertRejected(helper,
                action(payload(new JsonPrimitive(4_294_967_296L), integer(1), bool(false), bool(false))),
                "overflowing controller mode");
        assertRejected(helper, action(payload(integer(-2), integer(1), bool(false), bool(false))),
                "controller mode below null sentinel");
        assertRejected(helper,
                action(payload(integer(ControllerMode.values().length), integer(1), bool(false), bool(false))),
                "out-of-range controller mode");

        assertRejected(helper,
                action(payload(integer(-1), new JsonPrimitive(1.5D), bool(false), bool(false))),
                "fractional redstone strength");
        assertRejected(helper,
                action(payload(integer(-1), new JsonPrimitive(4_294_967_296L), bool(false), bool(false))),
                "overflowing redstone strength");
        assertRejected(helper, action(payload(integer(-1), integer(0), bool(false), bool(false))),
                "redstone strength below range");
        assertRejected(helper, action(payload(integer(-1), integer(16), bool(false), bool(false))),
                "redstone strength above range");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MachineControllerCoverConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TrackingMachineControllerCover cover = createTrackingCover(List.of(ControllerMode.COVER_UP));

        helper.assertTrue(dispatch(helper, cover, action(payloadWithUnknownField())),
                "dispatcher rejected an unknown future field");
        assertState(helper, cover, ControllerMode.COVER_UP, 7, true, false,
                "action with an unknown field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MachineControllerCoverConfigAction")
    public static void dispatcherRejectsWrongHolderAndSpectator(GameTestHelper helper) {
        SyncActionData action = MachineControllerCoverConfigActions.createSetConfigAction(
                ControllerMode.COVER_UP, 7, true, false);
        FakeActionTarget fakeTarget = new FakeActionTarget();

        helper.assertTrue(!dispatch(helper, fakeTarget, action),
                "dispatcher widened the holder scope to any action target");
        helper.assertTrue(fakeTarget.getSetterCalls() == 0, "wrong holder invoked target setters");
        helper.assertTrue(!dispatch(helper, new Object(), action),
                "dispatcher accepted an unrelated holder");

        TrackingMachineControllerCover spectatorCover = createTrackingCover(List.of(ControllerMode.COVER_UP));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, spectatorCover, action);
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }
        helper.assertTrue(!result, "dispatcher accepted a spectator");
        helper.assertTrue(spectatorCover.getSetterOrder().isEmpty(),
                "spectator action invoked machine controller setters");
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, SyncActionData action, String description) {
        TrackingMachineControllerCover cover = createTrackingCover(List.of(ControllerMode.COVER_UP));
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), description + " payload invoked a setter");
    }

    private static void assertFactoryRejected(Runnable factoryCall, String failureMessage) {
        try {
            factoryCall.run();
        } catch (IllegalArgumentException exception) {
            return;
        }
        throw new GameTestAssertException(failureMessage);
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        MachineControllerCoverConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement controllerMode, JsonElement minRedstoneStrength,
                                            JsonElement inverted, JsonElement preventPowerFail) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        if (controllerMode != null) {
            fields.put(CONTROLLER_MODE_FIELD, controllerMode);
        }
        if (minRedstoneStrength != null) {
            fields.put(MIN_REDSTONE_STRENGTH_FIELD, minRedstoneStrength);
        }
        if (inverted != null) {
            fields.put(INVERTED_FIELD, inverted);
        }
        if (preventPowerFail != null) {
            fields.put(PREVENT_POWER_FAIL_FIELD, preventPowerFail);
        }
        return fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithUnknownField() {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(CONTROLLER_MODE_FIELD, integer(ControllerMode.COVER_UP.ordinal()))
                        .put(MIN_REDSTONE_STRENGTH_FIELD, integer(7))
                        .put(INVERTED_FIELD, bool(true))
                        .put(PREVENT_POWER_FAIL_FIELD, bool(false))
                        .put(UNKNOWN_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();
    }

    private static JsonPrimitive integer(int value) {
        return new JsonPrimitive(value);
    }

    private static JsonPrimitive bool(boolean value) {
        return new JsonPrimitive(value);
    }

    private static SyncFieldData requireFields(DataComponentMap payload, String description) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new GameTestAssertException(description + " omitted sync field data");
        }
        return fields;
    }

    private static void assertIntField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                       int expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsBigDecimal().intValueExact() == expected,
                description + " was not encoded as the expected integer");
    }

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                           boolean expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " was not encoded as the expected boolean");
    }

    private static void assertState(GameTestHelper helper, TrackingMachineControllerCover cover,
                                    @Nullable ControllerMode controllerMode, int redstoneStrength,
                                    boolean inverted, boolean preventPowerFail, String description) {
        helper.assertTrue(cover.getAppliedControllerMode() == controllerMode &&
                cover.getAppliedRedstoneStrength() == redstoneStrength &&
                cover.isAppliedInverted() == inverted &&
                cover.isAppliedPreventPowerFail() == preventPowerFail,
                description + " changed the machine controller to an unexpected state");
    }

    private static TrackingMachineControllerCover createTrackingCover(List<ControllerMode> allowedModes) {
        BufferMachine machine = createBuffer();
        return new TrackingMachineControllerCover(
                GTCovers.MACHINE_CONTROLLER, machine.getCoverContainer(), Direction.WEST, allowedModes);
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof BufferMachine buffer) {
            return buffer;
        }
        throw new IllegalStateException("LV buffer definition did not create a BufferMachine.");
    }

    private static final class TrackingMachineControllerCover extends MachineControllerCover {

        private final List<ControllerMode> allowedModes;
        private final StringBuilder setterOrder = new StringBuilder();
        private @Nullable ControllerMode appliedControllerMode;
        private int appliedRedstoneStrength;
        private boolean appliedInverted;
        private boolean appliedPreventPowerFail;

        private TrackingMachineControllerCover(CoverDefinition definition, ICoverable coverHolder,
                                               Direction attachedSide, List<ControllerMode> allowedModes) {
            super(definition, coverHolder, attachedSide);
            this.allowedModes = allowedModes;
        }

        @Override
        public List<ControllerMode> getAllowedModes() {
            return allowedModes;
        }

        @Override
        public void setControllerMode(@Nullable ControllerMode controllerMode) {
            appendSetter("mode");
            appliedControllerMode = controllerMode;
        }

        @Override
        public void setMinRedstoneStrength(int minRedstoneStrength) {
            appendSetter("redstone");
            appliedRedstoneStrength = minRedstoneStrength;
        }

        @Override
        public void setInverted(boolean inverted) {
            appendSetter("inverted");
            appliedInverted = inverted;
        }

        @Override
        public void setPreventPowerFail(boolean preventPowerFail) {
            appendSetter("prevent");
            appliedPreventPowerFail = preventPowerFail;
        }

        private void appendSetter(String setter) {
            if (!setterOrder.isEmpty()) {
                setterOrder.append('>');
            }
            setterOrder.append(setter);
        }

        private String getSetterOrder() {
            return setterOrder.toString();
        }

        private @Nullable ControllerMode getAppliedControllerMode() {
            return appliedControllerMode;
        }

        private int getAppliedRedstoneStrength() {
            return appliedRedstoneStrength;
        }

        private boolean isAppliedInverted() {
            return appliedInverted;
        }

        private boolean isAppliedPreventPowerFail() {
            return appliedPreventPowerFail;
        }
    }

    private static final class FakeActionTarget implements MachineControllerCoverConfigActionTarget {

        private int setterCalls;

        @Override
        public @NotNull List<ControllerMode> getAllowedModes() {
            return List.of(ControllerMode.COVER_UP);
        }

        @Override
        public void setControllerMode(@Nullable ControllerMode controllerMode) {
            setterCalls++;
        }

        @Override
        public void setMinRedstoneStrength(int minRedstoneStrength) {
            setterCalls++;
        }

        @Override
        public void setInverted(boolean inverted) {
            setterCalls++;
        }

        @Override
        public void setPreventPowerFail(boolean preventPowerFail) {
            setterCalls++;
        }

        private int getSetterCalls() {
            return setterCalls;
        }
    }
}
