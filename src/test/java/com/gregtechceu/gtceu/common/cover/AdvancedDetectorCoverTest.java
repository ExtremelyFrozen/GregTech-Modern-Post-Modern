package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyInfoProvider;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.element.GTLongInputElement;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncDataHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.cover.detector.AdvancedEnergyDetectorCover;
import com.gregtechceu.gtceu.common.cover.detector.AdvancedFluidDetectorCover;
import com.gregtechceu.gtceu.common.cover.detector.AdvancedItemDetectorCover;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;
import com.gregtechceu.gtceu.gametest.util.TestUtils;
import com.gregtechceu.gtceu.utils.RedstoneUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.mutable.MutableInt;

import java.math.BigInteger;
import java.util.UUID;

/**
 * The "electrolyzer" template contains a creative tank with water,
 * that is set to auto-output into an electrolyzer when supplied with a redstone signal
 * The redstone lamp is connected to the covers that are placed in the tests in this class.
 * The creative tank's rate of output is equal to the electrolyzer's rate of processing
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class AdvancedDetectorCoverTest {

    private static final int DEFAULT_MIN = 64;
    private static final int DEFAULT_MAX = 512;
    private static final int CHANGED_MIN = 128;
    private static final int CHANGED_MAX = 1024;
    private static final long DEFAULT_ENERGY_MIN = 33L;
    private static final long DEFAULT_ENERGY_MAX = 66L;
    private static final long CHANGED_ENERGY_MIN = 100L;
    private static final long CHANGED_ENERGY_MAX = 900L;
    private static final long FIXED_ENERGY_CAPACITY = 1000L;
    private static final ResourceLocation MIN_VALUE_FIELD = SyncFieldData.key("minValue");
    private static final ResourceLocation MAX_VALUE_FIELD = SyncFieldData.key("maxValue");
    private static final ResourceLocation LATCHED_FIELD = SyncFieldData.key("isLatched");
    private static final ResourceLocation USE_PERCENT_FIELD = SyncFieldData.key("usePercent");

    @TestHolder()
    @GameTest(template = "electrolyzer", batch = "coverTests")
    public static void testAdvancedActivityDetectorCoverWithActivity(GameTestHelper helper) {
        helper.pullLever(new BlockPos(2, 2, 2));
        MetaMachine machine = ((MetaMachine) helper.getBlockEntity(new BlockPos(1, 2, 1)));
        TestUtils.placeCover(helper, machine, GTItems.COVER_ACTIVITY_DETECTOR_ADVANCED.asStack(), Direction.WEST);
        MutableInt expected = new MutableInt();
        helper.runAtTickTime(40 - machine.getOffsetTimer() % 20, () -> {
            IWorkable workable = (IWorkable) machine;
            expected.setValue(Math.round(15f * workable.getProgress() / workable.getMaxProgress()));
        });
        helper.runAtTickTime(41 - machine.getOffsetTimer() % 20, () -> {
            // due to this cover updating only once every 20 ticks, we need to check multiple values
            TestUtils.assertRedstoneEither(helper, new BlockPos(0, 2, 1),
                    (expected.intValue() + 13) % 15,
                    (expected.intValue() + 14) % 15,
                    expected.intValue());
            helper.succeed();
        });
    }

    @TestHolder()
    @GameTest(template = "electrolyzer", batch = "coverTests")
    public static void testAdvancedActivityDetectorCoverWithoutActivity(GameTestHelper helper) {
        helper.pullLever(new BlockPos(2, 2, 2));
        SimpleTieredMachine machine = ((SimpleTieredMachine) helper.getBlockEntity(new BlockPos(1, 2, 1)));
        TestUtils.placeCover(helper, machine, GTItems.COVER_ACTIVITY_DETECTOR_ADVANCED.asStack(), Direction.WEST);
        int offset = (int) (machine.getOffsetTimer() % 20L);
        helper.runAtTickTime(20 - offset, () -> {
            // Stop the fluid input
            helper.pullLever(2, 2, 2);
            // Also clear out the tank
            NotifiableFluidTank tank = (NotifiableFluidTank) machine
                    .getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP).get(0);
            tank.setFluidInTank(0, FluidStack.EMPTY);
        });
        // 20 ticks for the cover to update, 11 ticks for the recipe to finish, 1 tick for the cover to update
        helper.runAtTickTime(52 - offset,
                () -> helper.succeedWhen(() -> TestUtils.assertLampOff(helper, new BlockPos(0, 2, 1))));
    }

    @TestHolder()
    @GameTest(template = "electrolyzer", batch = "coverTests")
    public static void testAdvancedFluidDetectorCover(GameTestHelper helper) {
        helper.pullLever(new BlockPos(2, 2, 2));
        MetaMachine machine = ((MetaMachine) helper.getBlockEntity(new BlockPos(1, 2, 1)));
        AdvancedFluidDetectorCover cover = (AdvancedFluidDetectorCover) TestUtils.placeCover(helper, machine,
                GTItems.COVER_FLUID_DETECTOR_ADVANCED.asStack(), Direction.WEST);
        cover.setMaxValue(100000);
        cover.setMinValue(1);
        cover.setLatched(false);
        MutableInt expected = new MutableInt();
        int offset = (int) (machine.getOffsetTimer() % 20L);
        helper.runAtTickTime(80 - offset, () -> {
            // Actually pull in the value at the time, since offset might change the amount
            var handler = machine.getFluidHandlerCap(null, false);
            long storedFluid = 0;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                storedFluid += handler.getFluidInTank(tank).getAmount();
            }
            expected.setValue(RedstoneUtil.computeRedstoneBetweenValues(storedFluid,
                    cover.getMaxValue(), cover.getMinValue(), cover.isInverted()));
        });

        helper.runAtTickTime(81 - offset, () -> {
            int value = expected.intValue();
            TestUtils.assertRedstoneEither(helper, new BlockPos(0, 2, 1),
                    value,
                    Math.max(0, value - 1),
                    Math.min(15, value + 1));
            helper.succeed();
        });
    }

    @TestHolder()
    @GameTest(template = "electrolyzer", batch = "coverTests")
    public static void testAdvancedItemDetectorCover(GameTestHelper helper) {
        helper.pullLever(new BlockPos(2, 2, 2));
        MetaMachine machine = ((MetaMachine) helper.getBlockEntity(new BlockPos(1, 2, 1)));
        AdvancedItemDetectorCover cover = (AdvancedItemDetectorCover) TestUtils.placeCover(helper, machine,
                GTItems.COVER_ITEM_DETECTOR_ADVANCED.asStack(), Direction.WEST);
        cover.setLatched(true);
        helper.runAtTickTime(40, () -> {
            TestUtils.assertLampOn(helper, new BlockPos(0, 2, 1));
            helper.succeed();
        });
    }

    @TestHolder()
    @GameTest(template = "electrolyzer", batch = "coverTests")
    public static void testAdvancedItemDetectorCoverBelowThreshold(GameTestHelper helper) {
        helper.pullLever(new BlockPos(2, 2, 2));
        MetaMachine machine = ((MetaMachine) helper.getBlockEntity(new BlockPos(1, 2, 1)));
        AdvancedItemDetectorCover cover = (AdvancedItemDetectorCover) TestUtils.placeCover(helper, machine,
                GTItems.COVER_ITEM_DETECTOR_ADVANCED.asStack(), Direction.WEST);
        cover.setMinValue(1);
        cover.setMaxValue(4);
        helper.runAtTickTime(40, () -> {
            TestUtils.assertLampOff(helper, new BlockPos(0, 2, 1));
            helper.succeed();
        });
    }

    @TestHolder()
    @GameTest(template = "electrolyzer", batch = "coverTests")
    public static void testAdvancedItemDetectorCoverAboveThreshold(GameTestHelper helper) {
        helper.pullLever(new BlockPos(2, 2, 2));
        MetaMachine machine = ((MetaMachine) helper.getBlockEntity(new BlockPos(1, 2, 1)));
        machine.getItemHandlerCap(null, false).setStackInSlot(0, new ItemStack(Items.DIRT, 5));
        AdvancedItemDetectorCover cover = (AdvancedItemDetectorCover) TestUtils.placeCover(helper, machine,
                GTItems.COVER_ITEM_DETECTOR_ADVANCED.asStack(), Direction.WEST);
        cover.setMinValue(1);
        cover.setMaxValue(4);
        cover.setLatched(true);
        helper.runAtTickTime(40, () -> {
            TestUtils.assertLampOff(helper, new BlockPos(0, 2, 1));
            helper.succeed();
        });
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "coverTests")
    public static void advancedFluidDetectorUsesChangedOnlyScalarSync(GameTestHelper helper) {
        assertAdvancedDetectorScalarSync(helper,
                new FluidDetectorSyncProbe(createBuffer()),
                new FluidDetectorSyncProbe(createBuffer()),
                new FluidDetectorSyncProbe(createBuffer()),
                "advanced fluid detector");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "coverTests")
    public static void advancedItemDetectorUsesChangedOnlyScalarSync(GameTestHelper helper) {
        assertAdvancedDetectorScalarSync(helper,
                new ItemDetectorSyncProbe(createBuffer()),
                new ItemDetectorSyncProbe(createBuffer()),
                new ItemDetectorSyncProbe(createBuffer()),
                "advanced item detector");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "coverTests")
    public static void advancedEnergyDetectorUsesChangedOnlyScalarSync(GameTestHelper helper) {
        assertAdvancedEnergyDetectorScalarSync(helper,
                new EnergyDetectorSyncProbe(createBuffer()),
                new EnergyDetectorSyncProbe(createBuffer()),
                new EnergyDetectorSyncProbe(createBuffer()));
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "coverTests")
    public static void advancedEnergyDetectorSameModeRefreshesInputBounds(GameTestHelper helper) {
        EnergyDetectorSyncProbe cover = new EnergyDetectorSyncProbe(createBuffer());
        cover.setUsePercent(false);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        UICoverHolder holder = new TestCoverUIHolder(cover);
        var inputs = cover.createLDLib2UI(player, holder).getRootElement().getChildren().stream()
                .filter(element -> element instanceof GTLongInputElement)
                .map(element -> (GTLongInputElement) element)
                .toList();
        helper.assertTrue(inputs.size() == 2,
                "advanced energy detector UI did not create its two threshold inputs");

        GTLongInputElement maxInput = inputs.get(1);
        maxInput.setMax(100L);
        cover.setUsePercent(false);
        maxInput.setValue(CHANGED_ENERGY_MAX);

        helper.assertTrue(!cover.isUsePercent() && cover.getMaxValue() == CHANGED_ENERGY_MAX,
                "identical use-percent setter call did not restore the input bound from fixed capacity");
        helper.succeed();
    }

    private static void assertAdvancedEnergyDetectorScalarSync(GameTestHelper helper,
                                                               EnergyDetectorSyncProbe server,
                                                               EnergyDetectorSyncProbe client,
                                                               EnergyDetectorSyncProbe loaded) {
        RegistryAccess registries = helper.getLevel().registryAccess();

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertEnergyScalarFields(helper, full, DEFAULT_ENERGY_MIN, DEFAULT_ENERGY_MAX, true,
                "advanced energy detector full sync");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        assertEnergyScalarState(helper, client, DEFAULT_ENERGY_MIN, DEFAULT_ENERGY_MAX, true,
                "advanced energy detector full sync client");

        server.setMinValue(DEFAULT_ENERGY_MIN);
        server.setMaxValue(DEFAULT_ENERGY_MAX);
        server.setUsePercent(true);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "advanced energy detector default-equivalent setters produced a redundant delta");

        server.setMinValue(CHANGED_ENERGY_MIN);
        server.setMaxValue(CHANGED_ENERGY_MAX);
        server.setUsePercent(false);
        DataComponentMap delta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        SyncFieldData changedFields = requireFields(delta, "advanced energy detector changed delta");
        helper.assertTrue(changedFields.fields().size() == 3,
                "advanced energy detector changed delta contained fields other than minValue, maxValue, and " +
                        "usePercent");
        assertEnergyScalarFields(helper, changedFields, CHANGED_ENERGY_MIN, CHANGED_ENERGY_MAX, false,
                "advanced energy detector changed delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, delta);
        assertEnergyScalarState(helper, client, CHANGED_ENERGY_MIN, CHANGED_ENERGY_MAX, false,
                "advanced energy detector changed delta client");

        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertEnergyScalarFields(helper, saved, CHANGED_ENERGY_MIN, CHANGED_ENERGY_MAX, false,
                "advanced energy detector saved state");
        loaded.getSyncDataHolder().deserializeFieldData(registries, saved, false);
        assertEnergyScalarState(helper, loaded, CHANGED_ENERGY_MIN, CHANGED_ENERGY_MAX, false,
                "advanced energy detector loaded state");

        server.setMinValue(CHANGED_ENERGY_MIN);
        server.setMaxValue(CHANGED_ENERGY_MAX);
        server.setUsePercent(false);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "advanced energy detector changed-equivalent setters produced a redundant delta");

        assertRejectedEnergyClientField(helper, server, registries, MIN_VALUE_FIELD,
                new JsonPrimitive(DEFAULT_ENERGY_MIN), "advanced energy detector minValue client write");
        assertRejectedEnergyClientField(helper, server, registries, MAX_VALUE_FIELD,
                new JsonPrimitive(DEFAULT_ENERGY_MAX), "advanced energy detector maxValue client write");
        assertRejectedEnergyClientField(helper, server, registries, USE_PERCENT_FIELD,
                new JsonPrimitive(true), "advanced energy detector usePercent client write");
    }

    private static void assertRejectedEnergyClientField(GameTestHelper helper, EnergyDetectorSyncProbe server,
                                                        RegistryAccess registries, ResourceLocation field,
                                                        JsonElement value, String description) {
        ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, scalarPayload(field, value));
        helper.assertTrue(!rejected.getAccepted(), description + " was accepted");
        assertEnergyScalarState(helper, server, CHANGED_ENERGY_MIN, CHANGED_ENERGY_MAX, false, description);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                description + " produced an acknowledgement");
    }

    private static void assertEnergyScalarState(GameTestHelper helper, EnergyDetectorSyncProbe probe,
                                                long minValue, long maxValue, boolean usePercent, String description) {
        helper.assertTrue(probe.getMinValue() == minValue && probe.getMaxValue() == maxValue &&
                probe.isUsePercent() == usePercent,
                description + " did not contain the expected scalar state");
    }

    private static void assertEnergyScalarFields(GameTestHelper helper, DataComponentMap components,
                                                 long minValue, long maxValue, boolean usePercent,
                                                 String description) {
        assertEnergyScalarFields(helper, requireFields(components, description), minValue, maxValue, usePercent,
                description);
    }

    private static void assertEnergyScalarFields(GameTestHelper helper, SyncFieldData fields,
                                                 long minValue, long maxValue, boolean usePercent,
                                                 String description) {
        assertLongField(helper, fields, MIN_VALUE_FIELD, minValue, description);
        assertLongField(helper, fields, MAX_VALUE_FIELD, maxValue, description);
        JsonElement usePercentValue = fields.get(USE_PERCENT_FIELD);
        helper.assertTrue(usePercentValue instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == usePercent,
                description + " did not contain the expected usePercent field");
    }

    private static void assertLongField(GameTestHelper helper, SyncFieldData fields,
                                        ResourceLocation field, long expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsLong() == expected,
                description + " did not contain the expected " + field.getPath() + " field");
    }

    private static void assertAdvancedDetectorScalarSync(GameTestHelper helper,
                                                         AdvancedDetectorSyncProbe server,
                                                         AdvancedDetectorSyncProbe client,
                                                         AdvancedDetectorSyncProbe loaded,
                                                         String description) {
        RegistryAccess registries = helper.getLevel().registryAccess();

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertScalarFields(helper, full, DEFAULT_MIN, DEFAULT_MAX, false, description + " full sync");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        assertScalarState(helper, client, DEFAULT_MIN, DEFAULT_MAX, false,
                description + " full sync client");

        server.setMinValue(DEFAULT_MIN);
        server.setMaxValue(DEFAULT_MAX);
        server.setLatched(false);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                description + " default-equivalent setters produced a redundant delta");

        server.setMaxValue(CHANGED_MAX);
        server.setMinValue(CHANGED_MIN);
        server.setLatched(true);
        DataComponentMap delta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        SyncFieldData changedFields = requireFields(delta, description + " changed delta");
        helper.assertTrue(changedFields.fields().size() == 3,
                description + " changed delta contained fields other than minValue, maxValue, and isLatched");
        assertScalarFields(helper, changedFields, CHANGED_MIN, CHANGED_MAX, true,
                description + " changed delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, delta);
        assertScalarState(helper, client, CHANGED_MIN, CHANGED_MAX, true,
                description + " changed delta client");

        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertScalarFields(helper, saved, CHANGED_MIN, CHANGED_MAX, true, description + " saved state");
        loaded.getSyncDataHolder().deserializeFieldData(registries, saved, false);
        assertScalarState(helper, loaded, CHANGED_MIN, CHANGED_MAX, true,
                description + " loaded state");

        server.setMaxValue(CHANGED_MAX);
        server.setMinValue(CHANGED_MIN);
        server.setLatched(true);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                description + " changed-equivalent setters produced a redundant delta");

        assertRejectedClientField(helper, server, registries, MIN_VALUE_FIELD, new JsonPrimitive(DEFAULT_MIN),
                description + " minValue client write");
        assertRejectedClientField(helper, server, registries, MAX_VALUE_FIELD, new JsonPrimitive(DEFAULT_MAX),
                description + " maxValue client write");
        assertRejectedClientField(helper, server, registries, LATCHED_FIELD, new JsonPrimitive(false),
                description + " isLatched client write");
    }

    private static void assertRejectedClientField(GameTestHelper helper, AdvancedDetectorSyncProbe server,
                                                  RegistryAccess registries, ResourceLocation field, JsonElement value,
                                                  String description) {
        ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, scalarPayload(field, value));
        helper.assertTrue(!rejected.getAccepted(), description + " was accepted");
        assertScalarState(helper, server, CHANGED_MIN, CHANGED_MAX, true, description);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                description + " produced an acknowledgement");
    }

    private static DataComponentMap scalarPayload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof BufferMachine buffer) {
            return buffer;
        }
        throw new IllegalStateException("LV buffer definition did not create a BufferMachine.");
    }

    private static void assertScalarState(GameTestHelper helper, AdvancedDetectorSyncProbe probe,
                                          int minValue, int maxValue, boolean latched, String description) {
        helper.assertTrue(probe.getMinValue() == minValue && probe.getMaxValue() == maxValue &&
                probe.isLatched() == latched,
                description + " did not contain the expected scalar state");
    }

    private static void assertScalarFields(GameTestHelper helper, DataComponentMap components,
                                           int minValue, int maxValue, boolean latched, String description) {
        assertScalarFields(helper, requireFields(components, description), minValue, maxValue, latched, description);
    }

    private static void assertScalarFields(GameTestHelper helper, SyncFieldData fields,
                                           int minValue, int maxValue, boolean latched, String description) {
        assertIntField(helper, fields, MIN_VALUE_FIELD, minValue, description);
        assertIntField(helper, fields, MAX_VALUE_FIELD, maxValue, description);
        JsonElement latchedValue = fields.get(LATCHED_FIELD);
        helper.assertTrue(latchedValue instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == latched,
                description + " did not contain the expected isLatched field");
    }

    private static void assertIntField(GameTestHelper helper, SyncFieldData fields,
                                       ResourceLocation field, int expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == expected,
                description + " did not contain the expected " + field.getPath() + " field");
    }

    private static SyncFieldData requireFields(DataComponentMap components, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new GameTestAssertException(description + " omitted sync field data");
        }
        return fields;
    }

    /**
     * Defines the shared scalar synchronization contract of advanced item and fluid detectors.
     */
    private interface AdvancedDetectorSyncProbe {

        /**
         * Returns the synchronization holder owned by the detector cover.
         */
        SyncDataHolder getSyncDataHolder();

        /**
         * Returns the lower detector threshold.
         */
        int getMinValue();

        /**
         * Changes the lower detector threshold.
         */
        void setMinValue(int minValue);

        /**
         * Returns the upper detector threshold.
         */
        int getMaxValue();

        /**
         * Changes the upper detector threshold.
         */
        void setMaxValue(int maxValue);

        /**
         * Returns whether the detector retains its threshold state.
         */
        boolean isLatched();

        /**
         * Changes whether the detector retains its threshold state.
         */
        void setLatched(boolean latched);
    }

    private static final class FluidDetectorSyncProbe extends AdvancedFluidDetectorCover
                                                      implements AdvancedDetectorSyncProbe {

        private FluidDetectorSyncProbe(BufferMachine machine) {
            super(GTCovers.FLUID_DETECTOR_ADVANCED, machine.getCoverContainer(), Direction.WEST);
        }
    }

    private static final class ItemDetectorSyncProbe extends AdvancedItemDetectorCover
                                                     implements AdvancedDetectorSyncProbe {

        private ItemDetectorSyncProbe(BufferMachine machine) {
            super(GTCovers.ITEM_DETECTOR_ADVANCED, machine.getCoverContainer(), Direction.WEST);
        }
    }

    private static final class EnergyDetectorSyncProbe extends AdvancedEnergyDetectorCover {

        private static final IEnergyInfoProvider ENERGY_INFO_PROVIDER = new FixedEnergyInfoProvider();

        private EnergyDetectorSyncProbe(BufferMachine machine) {
            super(GTCovers.ENERGY_DETECTOR_ADVANCED, machine.getCoverContainer(), Direction.WEST);
        }

        @Override
        protected IEnergyInfoProvider getEnergyInfoProvider() {
            return ENERGY_INFO_PROVIDER;
        }
    }

    private static final class FixedEnergyInfoProvider implements IEnergyInfoProvider {

        private static final EnergyInfo ENERGY_INFO = new EnergyInfo(BigInteger.valueOf(FIXED_ENERGY_CAPACITY),
                BigInteger.valueOf(FIXED_ENERGY_CAPACITY / 2));

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
            return cover.coverHolder.getBlockPos();
        }

        @Override
        public Direction getSide() {
            return cover.attachedSide;
        }

        @Override
        public ResourceLocation getCoverDefinitionId() {
            return cover.coverDefinition.getId();
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
