package com.gregtechceu.gtceu.common.cover.detector;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyInfoProvider;
import com.gregtechceu.gtceu.api.capability.IEnergyInfoProvider.EnergyInfo;
import com.gregtechceu.gtceu.api.gui.element.GTLongInputElement;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
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

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class AdvancedEnergyDetectorConfigActionTest {

    private static final long DEFAULT_MIN = 33L;
    private static final long DEFAULT_MAX = 66L;
    private static final long FIXED_CAPACITY = 1000L;
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_advanced_energy_detector_config");
    private static final ResourceLocation MIN_FIELD = SyncFieldData.key("min");
    private static final ResourceLocation MAX_FIELD = SyncFieldData.key("max");
    private static final ResourceLocation USE_PERCENT_FIELD = SyncFieldData.key("usePercent");
    private static final ResourceLocation INVERTED_FIELD = SyncFieldData.key("inverted");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedEnergyDetectorConfigAction")
    public static void factoryEncodesAndDispatcherExecutesLongConfig(GameTestHelper helper) {
        TestEnergyDetectorCover cover = createEnergyCover();
        long minimumAboveIntRange = (long) Integer.MAX_VALUE + 1L;
        SyncActionData action = AdvancedEnergyDetectorConfigActions.createSetConfigAction(
                minimumAboveIntRange, Long.MAX_VALUE, false, true);
        SyncFieldData fields = requireFields(action.payload(), "advanced energy detector config factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the advanced energy detector action sequence");
        helper.assertTrue(fields.fields().size() == 4, "factory encoded fields outside the energy config protocol");
        assertLongField(helper, fields, MIN_FIELD, minimumAboveIntRange, "factory minimum");
        assertLongField(helper, fields, MAX_FIELD, Long.MAX_VALUE, "factory maximum");
        assertBooleanField(helper, fields, USE_PERCENT_FIELD, false, "factory percent mode");
        assertBooleanField(helper, fields, INVERTED_FIELD, true, "factory inverted state");

        helper.assertTrue(dispatch(helper, cover, action), "valid advanced energy detector action was rejected");
        assertState(helper, cover, minimumAboveIntRange, Long.MAX_VALUE, false, true, "valid long action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedEnergyDetectorConfigAction")
    public static void factoryRejectsNegativeThresholds(GameTestHelper helper) {
        helper.assertTrue(factoryRejects(-1L, 1L), "factory accepted a negative minimum");
        helper.assertTrue(factoryRejects(0L, -1L), "factory accepted a negative maximum");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedEnergyDetectorConfigAction")
    public static void dispatcherAppliesModeBeforeThresholdsWithOpenUi(GameTestHelper helper) {
        TestEnergyDetectorCover cover = createEnergyCover();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        UICoverHolder holder = new TestCoverUIHolder(cover);
        List<GTLongInputElement> inputs = cover.createLDLib2UI(player, holder).getRootElement().getChildren().stream()
                .filter(GTLongInputElement.class::isInstance)
                .map(GTLongInputElement.class::cast)
                .toList();
        helper.assertTrue(inputs.size() == 2, "advanced energy detector UI did not create two threshold inputs");

        SyncActionData action = AdvancedEnergyDetectorConfigActions.createSetConfigAction(330L, 660L, false, true);
        helper.assertTrue(dispatch(player, cover, action), "absolute energy config action was rejected");
        assertState(helper, cover, 330L, 660L, false, true, "mode-first action");

        inputs.get(1).setValue(900L);
        helper.assertTrue(cover.getMaxValue() == 900L,
                "mode-first action did not refresh the maximum input to the fixed energy capacity");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedEnergyDetectorConfigAction")
    public static void dispatcherRejectsNonEnergyDetectorHolders(GameTestHelper helper) {
        SyncActionData action = AdvancedEnergyDetectorConfigActions.createSetConfigAction(128L, 512L, false, true);
        AdvancedFluidDetectorCover fluidCover = createFluidCover();

        helper.assertTrue(!dispatch(helper, new Object(), action),
                "advanced energy detector action accepted an unrelated holder");
        helper.assertTrue(!dispatch(helper, fluidCover, action),
                "advanced energy detector action accepted an advanced fluid detector holder");
        helper.assertTrue(fluidCover.getMinValue() == 64 && fluidCover.getMaxValue() == 512 &&
                !fluidCover.isLatched() && !fluidCover.isInverted(),
                "rejected energy action changed the advanced fluid detector");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedEnergyDetectorConfigAction")
    public static void dispatcherRejectsMalformedConfigPayloads(GameTestHelper helper) {
        TestEnergyDetectorCover cover = createEnergyCover();
        JsonPrimitive validMin = new JsonPrimitive(128L);
        JsonPrimitive validMax = new JsonPrimitive(512L);
        JsonPrimitive validPercent = new JsonPrimitive(false);
        JsonPrimitive validInverted = new JsonPrimitive(true);
        JsonPrimitive fractional = new JsonPrimitive(128.5D);
        JsonPrimitive overflowing = new JsonPrimitive(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));

        assertRejected(helper, cover, action(payload(new JsonPrimitive("128"), validMax, validPercent,
                validInverted)), "string minimum");
        assertRejected(helper, cover, action(payload(fractional, validMax, validPercent, validInverted)),
                "fractional minimum");
        assertRejected(helper, cover, action(payload(overflowing, validMax, validPercent, validInverted)),
                "overflowing minimum");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(-1L), validMax, validPercent,
                validInverted)), "negative minimum");
        assertRejected(helper, cover, action(payload(validMin, new JsonPrimitive("512"), validPercent,
                validInverted)), "string maximum");
        assertRejected(helper, cover, action(payload(validMin, fractional, validPercent, validInverted)),
                "fractional maximum");
        assertRejected(helper, cover, action(payload(validMin, overflowing, validPercent, validInverted)),
                "overflowing maximum");
        assertRejected(helper, cover, action(payload(validMin, new JsonPrimitive(-1L), validPercent,
                validInverted)), "negative maximum");
        assertRejected(helper, cover, action(payload(validMin, validMax, new JsonPrimitive(0), validInverted)),
                "numeric percent mode");
        assertRejected(helper, cover, action(payload(validMin, validMax, validPercent, new JsonPrimitive("true"))),
                "string inverted state");
        assertRejected(helper, cover, action(payloadWithout(MIN_FIELD)), "missing minimum");
        assertRejected(helper, cover, action(payloadWithout(MAX_FIELD)), "missing maximum");
        assertRejected(helper, cover, action(payloadWithout(USE_PERCENT_FIELD)), "missing percent mode");
        assertRejected(helper, cover, action(payloadWithout(INVERTED_FIELD)), "missing inverted state");
        assertRejected(helper, cover, action(DataComponentMap.EMPTY), "missing field data");

        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedEnergyDetectorConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TestEnergyDetectorCover cover = createEnergyCover();

        helper.assertTrue(dispatch(helper, cover, action(payloadWithUnknownField())),
                "advanced energy detector action rejected an unknown payload field");
        assertState(helper, cover, 128L, 512L, false, true, "action with unknown payload field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedEnergyDetectorConfigAction")
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        TestEnergyDetectorCover cover = createEnergyCover();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover,
                    AdvancedEnergyDetectorConfigActions.createSetConfigAction(128L, 512L, false, true));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "advanced energy detector action accepted a spectator");
        assertState(helper, cover, DEFAULT_MIN, DEFAULT_MAX, true, false, "spectator action");
        helper.succeed();
    }

    private static boolean factoryRejects(long min, long max) {
        try {
            AdvancedEnergyDetectorConfigActions.createSetConfigAction(min, max, false, false);
            return false;
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private static void assertRejected(GameTestHelper helper, TestEnergyDetectorCover cover,
                                       SyncActionData action, String description) {
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        assertState(helper, cover, DEFAULT_MIN, DEFAULT_MAX, true, false, description + " payload");
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        AdvancedEnergyDetectorConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement min, JsonElement max, JsonElement usePercent,
                                            JsonElement inverted) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(MIN_FIELD, min)
                        .put(MAX_FIELD, max)
                        .put(USE_PERCENT_FIELD, usePercent)
                        .put(INVERTED_FIELD, inverted)
                        .build())
                .build();
    }

    private static DataComponentMap payloadWithout(ResourceLocation omittedField) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        if (!omittedField.equals(MIN_FIELD)) {
            fields.put(MIN_FIELD, new JsonPrimitive(128L));
        }
        if (!omittedField.equals(MAX_FIELD)) {
            fields.put(MAX_FIELD, new JsonPrimitive(512L));
        }
        if (!omittedField.equals(USE_PERCENT_FIELD)) {
            fields.put(USE_PERCENT_FIELD, new JsonPrimitive(false));
        }
        if (!omittedField.equals(INVERTED_FIELD)) {
            fields.put(INVERTED_FIELD, new JsonPrimitive(true));
        }
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields.build())
                .build();
    }

    private static DataComponentMap payloadWithUnknownField() {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(MIN_FIELD, new JsonPrimitive(128L))
                        .put(MAX_FIELD, new JsonPrimitive(512L))
                        .put(USE_PERCENT_FIELD, new JsonPrimitive(false))
                        .put(INVERTED_FIELD, new JsonPrimitive(true))
                        .put(UNKNOWN_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(DataComponentMap payload, String description) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new GameTestAssertException(description + " omitted sync field data");
        }
        return fields;
    }

    private static void assertLongField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                        long expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsBigInteger().equals(BigInteger.valueOf(expected)),
                description + " was not encoded as the expected long");
    }

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                           boolean expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected, description + " was not encoded as the expected boolean");
    }

    private static void assertState(GameTestHelper helper, AdvancedEnergyDetectorCover cover, long min, long max,
                                    boolean usePercent, boolean inverted, String description) {
        helper.assertTrue(cover.getMinValue() == min && cover.getMaxValue() == max &&
                cover.isUsePercent() == usePercent && cover.isInverted() == inverted,
                description + " changed the cover to an unexpected state");
    }

    private static TestEnergyDetectorCover createEnergyCover() {
        return new TestEnergyDetectorCover(createBuffer());
    }

    private static AdvancedFluidDetectorCover createFluidCover() {
        BufferMachine machine = createBuffer();
        return new AdvancedFluidDetectorCover(GTCovers.FLUID_DETECTOR_ADVANCED, machine.getCoverContainer(),
                Direction.WEST);
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof BufferMachine buffer) {
            return buffer;
        }
        throw new IllegalStateException("LV buffer definition did not create a BufferMachine.");
    }

    private static final class TestEnergyDetectorCover extends AdvancedEnergyDetectorCover {

        private static final IEnergyInfoProvider ENERGY_INFO_PROVIDER = new FixedEnergyInfoProvider();

        private TestEnergyDetectorCover(BufferMachine machine) {
            super(GTCovers.ENERGY_DETECTOR_ADVANCED, machine.getCoverContainer(), Direction.WEST);
        }

        @Override
        protected IEnergyInfoProvider getEnergyInfoProvider() {
            return ENERGY_INFO_PROVIDER;
        }
    }

    private static final class FixedEnergyInfoProvider implements IEnergyInfoProvider {

        private static final EnergyInfo ENERGY_INFO = new EnergyInfo(BigInteger.valueOf(FIXED_CAPACITY),
                BigInteger.valueOf(FIXED_CAPACITY / 2L));

        @Override
        public EnergyInfo getEnergyInfo() {
            return ENERGY_INFO;
        }

        @Override
        public long getInputPerSec() {
            return 0L;
        }

        @Override
        public long getOutputPerSec() {
            return 0L;
        }

        @Override
        public boolean supportsBigIntEnergyValues() {
            return false;
        }
    }

    private static final class TestCoverUIHolder implements UICoverHolder {

        private static final UUID ACTION_SESSION_ID = new UUID(0L, 0L);

        private final AdvancedEnergyDetectorCover cover;

        private TestCoverUIHolder(AdvancedEnergyDetectorCover cover) {
            this.cover = cover;
        }

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        @Override
        public Direction getSide() {
            return Direction.WEST;
        }

        @Override
        public ResourceLocation getCoverDefinitionId() {
            return GTCovers.ENERGY_DETECTOR_ADVANCED.getId();
        }

        @Override
        public UUID getActionSessionId() {
            return ACTION_SESSION_ID;
        }

        @Override
        public AdvancedEnergyDetectorCover getCover() {
            return cover;
        }
    }
}
