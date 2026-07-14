package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.util.FluidContainerSlotInteraction;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEPatternBufferActionsTest {

    private static final String BATCH = "MEPatternBufferActions";
    private static final ResourceLocation SET_NAME_ACTION = GTCEu.id("set_me_pattern_buffer_name");
    private static final ResourceLocation REFUND_ACTION = GTCEu.id("refund_me_pattern_buffer");
    private static final ResourceLocation CLICK_SHARE_TANK_ACTION = GTCEu.id("click_me_pattern_buffer_share_tank");
    private static final ResourceLocation NAME_FIELD = SyncFieldData.key("name");
    private static final ResourceLocation TANK_FIELD = SyncFieldData.key("tank");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");
    private static final ResourceLocation EXTRA_FIELD = SyncFieldData.key("extra");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorsEncodeExactPayloadsWithoutNormalizingNames(GameTestHelper helper) {
        String spacedName = "  Processing Buffer  ";
        SyncActionData name = MEPatternBufferActions.createSetNameAction(spacedName);
        SyncFieldData nameFields = requireFields(name.payload());
        helper.assertTrue(name.actionId().equals(SET_NAME_ACTION), "name creator used the wrong action id");
        helper.assertTrue(name.sequence() == spacedName.hashCode(), "name creator used the wrong sequence");
        helper.assertTrue(nameFields.fields().size() == 1 &&
                nameFields.get(NAME_FIELD) instanceof JsonPrimitive primitive && primitive.isString() &&
                primitive.getAsString().equals(spacedName),
                "name creator trimmed or changed its exact string payload");

        SyncActionData emptyName = MEPatternBufferActions.createSetNameAction("");
        helper.assertTrue(readName(requireFields(emptyName.payload())).isEmpty(),
                "name creator rejected or changed the empty clearing value");

        SyncActionData refund = MEPatternBufferActions.createRefundAllAction();
        helper.assertTrue(refund.actionId().equals(REFUND_ACTION) && refund.sequence() == 0 &&
                refund.payload().isEmpty(), "refund creator did not use an empty command payload");

        SyncActionData tank = MEPatternBufferActions.createClickShareTankAction(2, true);
        SyncFieldData tankFields = requireFields(tank.payload());
        helper.assertTrue(tank.actionId().equals(CLICK_SHARE_TANK_ACTION) && tank.sequence() == 5,
                "share tank creator used the wrong action id or sequence");
        helper.assertTrue(tankFields.fields().size() == 2 && readTankIndex(tankFields) == 2 &&
                readShift(tankFields),
                "share tank creator did not encode the exact tank and Shift fields");

        assertCreatorRejected(helper, () -> MEPatternBufferActions.createClickShareTankAction(-1, false));
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAppliesNameRefundAndShareTankActionsExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        TestPatternBufferTarget target = new TestPatternBufferTarget(3);

        helper.assertTrue(dispatch(player, target,
                MEPatternBufferActions.createSetNameAction("  Alloy Patterns  ")),
                "valid pattern buffer name action was rejected");
        helper.assertTrue(target.name.equals("  Alloy Patterns  ") && target.nameWrites == 1,
                "name action normalized its value or executed more than once");

        helper.assertTrue(dispatch(player, target, MEPatternBufferActions.createSetNameAction("")),
                "empty pattern buffer name action was rejected");
        helper.assertTrue(target.name.isEmpty() && target.nameWrites == 2,
                "empty name action did not clear the name exactly once");

        helper.assertTrue(dispatch(player, target, MEPatternBufferActions.createRefundAllAction()),
                "valid pattern buffer refund action was rejected");
        helper.assertTrue(target.refunds == 1, "refund action did not execute exactly once");

        helper.assertTrue(dispatch(player, target,
                MEPatternBufferActions.createClickShareTankAction(2, true)),
                "valid pattern buffer share tank action was rejected");
        helper.assertTrue(target.tankClicks == 1 && target.lastPlayer == player &&
                target.lastTank == 2 && target.lastShift,
                "share tank action did not execute its exact player, tank, and Shift request once");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsUnsupportedHoldersSpectatorsAndTankBounds(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        TestPatternBufferTarget target = new TestPatternBufferTarget(3);
        SyncActionData name = MEPatternBufferActions.createSetNameAction("Rejected");
        SyncActionData refund = MEPatternBufferActions.createRefundAllAction();
        SyncActionData tank = MEPatternBufferActions.createClickShareTankAction(0, false);

        helper.assertTrue(!dispatch(player, new Object(), name),
                "pattern buffer name action accepted an unrelated holder");
        target.supported = false;
        helper.assertTrue(!dispatch(player, target, name) && !dispatch(player, target, refund) &&
                !dispatch(player, target, tank),
                "unsupported machine definition accepted a pattern buffer action");
        target.supported = true;

        player.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(!dispatch(player, target, name) && !dispatch(player, target, refund) &&
                !dispatch(player, target, tank),
                "spectator executed a pattern buffer action");
        player.setGameMode(GameType.SURVIVAL);

        helper.assertTrue(!dispatch(player, target,
                MEPatternBufferActions.createClickShareTankAction(3, false)),
                "share tank action accepted its exclusive upper bound");
        helper.assertTrue(target.nameWrites == 0 && target.refunds == 0 && target.tankClicks == 0,
                "rejected holder, permission, or bounds checks partially mutated the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedAndExtraPayloadFieldsWithoutMutation(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        TestPatternBufferTarget target = new TestPatternBufferTarget(3);

        assertRejected(helper, player, target, rawNameAction(DataComponentMap.EMPTY), "missing name payload");
        assertRejected(helper, player, target, rawNameAction(fieldsPayload(
                SyncFieldData.builder().put(NAME_FIELD, new JsonPrimitive(true)).build())),
                "boolean name payload");
        assertRejected(helper, player, target, rawNameAction(fieldsPayload(
                SyncFieldData.builder()
                        .put(NAME_FIELD, new JsonPrimitive("name"))
                        .put(EXTRA_FIELD, new JsonPrimitive(1))
                        .build())),
                "extra name field");
        assertRejected(helper, player, target, rawNameAction(fieldsPayloadWithItem(
                SyncFieldData.builder().put(NAME_FIELD, new JsonPrimitive("name")).build())),
                "extra name data component");

        assertRejected(helper, player, target, rawRefundAction(fieldsPayload(
                SyncFieldData.builder().put(EXTRA_FIELD, new JsonPrimitive(true)).build())),
                "non-empty refund payload");

        assertMalformedTankRejected(helper, player, target, new JsonPrimitive(1.5),
                new JsonPrimitive(false), "fractional tank");
        assertMalformedTankRejected(helper, player, target, new JsonPrimitive("1"),
                new JsonPrimitive(false), "string tank");
        assertMalformedTankRejected(helper, player, target, new JsonPrimitive(true),
                new JsonPrimitive(false), "boolean tank");
        assertMalformedTankRejected(helper, player, target,
                new JsonPrimitive(BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE)),
                new JsonPrimitive(false), "overflowing tank");
        assertMalformedTankRejected(helper, player, target, new JsonPrimitive(-1),
                new JsonPrimitive(false), "negative tank");
        assertMalformedTankRejected(helper, player, target, new JsonPrimitive(0),
                new JsonPrimitive("false"), "string Shift flag");
        assertRejected(helper, player, target, rawTankAction(fieldsPayload(
                SyncFieldData.builder().put(TANK_FIELD, new JsonPrimitive(0)).build())),
                "missing Shift field");
        assertRejected(helper, player, target, rawTankAction(fieldsPayload(
                SyncFieldData.builder()
                        .put(TANK_FIELD, new JsonPrimitive(0))
                        .put(SHIFT_FIELD, new JsonPrimitive(false))
                        .put(EXTRA_FIELD, new JsonPrimitive(1))
                        .build())),
                "extra share tank field");
        assertRejected(helper, player, target, rawTankAction(fieldsPayloadWithItem(
                SyncFieldData.builder()
                        .put(TANK_FIELD, new JsonPrimitive(0))
                        .put(SHIFT_FIELD, new JsonPrimitive(false))
                        .build())),
                "extra share tank data component");

        helper.assertTrue(target.nameWrites == 0 && target.refunds == 0 && target.tankClicks == 0,
                "malformed pattern buffer payload partially mutated the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fluidContainerInteractionPreservesSingleAndShiftTransactions(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        FluidTank tank = new FluidTank(4_000);
        FluidContainerSlotInteraction interaction = new FluidContainerSlotInteraction(tank, true, true);

        tank.setFluid(new FluidStack(Fluids.WATER, 3_000));
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        interaction.click(player, false);
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET) &&
                tank.getFluidAmount() == 2_000,
                "single share tank interaction did not fill exactly one carried bucket");

        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 2));
        interaction.click(player, true);
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET) &&
                player.containerMenu.getCarried().getCount() == 1 &&
                player.getInventory().countItem(Items.WATER_BUCKET) == 1 && tank.isEmpty(),
                "Shift share tank interaction did not fill the complete carried stack");

        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        interaction.click(player, false);
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET) &&
                tank.getFluidAmount() == 1_000 && tank.getFluid().is(Fluids.WATER),
                "share tank interaction did not empty one carried fluid container");

        player.containerMenu.setCarried(new ItemStack(Items.STONE));
        interaction.click(player, true);
        helper.assertTrue(player.containerMenu.getCarried().is(Items.STONE) && tank.getFluidAmount() == 1_000,
                "non-fluid cursor changed the cursor or share tank state");
        helper.succeed();
    }

    private static void assertMalformedTankRejected(GameTestHelper helper, ServerPlayer player,
                                                    TestPatternBufferTarget target, JsonPrimitive tank,
                                                    JsonPrimitive shift, String description) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(TANK_FIELD, tank)
                .put(SHIFT_FIELD, shift)
                .build();
        assertRejected(helper, player, target, rawTankAction(fieldsPayload(fields)), description);
    }

    private static void assertRejected(GameTestHelper helper, ServerPlayer player,
                                       TestPatternBufferTarget target, SyncActionData action,
                                       String description) {
        helper.assertTrue(!dispatch(player, target, action), description + " was accepted");
    }

    private static SyncActionData rawNameAction(DataComponentMap payload) {
        return new SyncActionData(SET_NAME_ACTION, 0, payload);
    }

    private static SyncActionData rawRefundAction(DataComponentMap payload) {
        return new SyncActionData(REFUND_ACTION, 0, payload);
    }

    private static SyncActionData rawTankAction(DataComponentMap payload) {
        return new SyncActionData(CLICK_SHARE_TANK_ACTION, 0, payload);
    }

    private static DataComponentMap fieldsPayload(SyncFieldData fields) {
        return fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap fieldsPayloadWithItem(SyncFieldData fields) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), new ItemStack(Items.STONE))
                .build();
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        MEPatternBufferActions.initialize();
        return SyncActionDispatchers.server().dispatch(new SyncActionContext(
                player, holder, action, BlockPos.ZERO, null, null, null));
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return player;
    }

    private static void assertCreatorRejected(GameTestHelper helper, Runnable creator) {
        boolean rejected = false;
        try {
            creator.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "share tank creator accepted tank -1");
    }

    private static SyncFieldData requireFields(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Pattern buffer action omitted sync field data.");
        }
        return fields;
    }

    private static String readName(SyncFieldData fields) {
        if (!(fields.get(NAME_FIELD) instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalStateException("Pattern buffer action omitted string field " + NAME_FIELD);
        }
        return primitive.getAsString();
    }

    private static int readTankIndex(SyncFieldData fields) {
        if (!(fields.get(TANK_FIELD) instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalStateException("Pattern buffer action omitted integer field " + TANK_FIELD);
        }
        return primitive.getAsInt();
    }

    private static boolean readShift(SyncFieldData fields) {
        if (!(fields.get(SHIFT_FIELD) instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw new IllegalStateException("Pattern buffer action omitted boolean field " + SHIFT_FIELD);
        }
        return primitive.getAsBoolean();
    }

    private static final class TestPatternBufferTarget implements MEPatternBufferActionTarget {

        private final int tankCount;
        private boolean supported = true;
        private String name = "";
        private int nameWrites;
        private int refunds;
        private int tankClicks;
        private ServerPlayer lastPlayer;
        private int lastTank = -1;
        private boolean lastShift;

        private TestPatternBufferTarget(int tankCount) {
            this.tankCount = tankCount;
        }

        @Override
        public boolean supportsMEPatternBufferActions() {
            return supported;
        }

        @Override
        public void setMEPatternBufferName(@NotNull String name) {
            this.name = name;
            nameWrites++;
        }

        @Override
        public void refundMEPatternBufferContents() {
            refunds++;
        }

        @Override
        public int getMEPatternBufferShareTankCount() {
            return tankCount;
        }

        @Override
        public void clickMEPatternBufferShareTank(@NotNull ServerPlayer player, int tankIndex, boolean shiftDown) {
            tankClicks++;
            lastPlayer = player;
            lastTank = tankIndex;
            lastShift = shiftDown;
        }
    }
}
