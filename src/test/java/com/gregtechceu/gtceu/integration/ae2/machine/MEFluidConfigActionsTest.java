package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEFluidKey;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEFluidConfigActionsTest {

    private static final String BATCH = "MEFluidConfigActions";
    private static final ResourceLocation SET_AMOUNT_ACTION = GTCEu.id("set_me_fluid_config_amount");
    private static final ResourceLocation SLOT_FIELD = SyncFieldData.key("slot");
    private static final ResourceLocation AMOUNT_FIELD = SyncFieldData.key("amount");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAppliesConfigAmountClearAndAutoPullActions(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        TestMEFluidConfigTarget target = new TestMEFluidConfigTarget();

        helper.assertTrue(dispatch(player, target, MEFluidConfigActions.createSetConfigAction(
                3, new FluidStack(Fluids.WATER, 1_000))), "valid fluid config action was rejected");
        helper.assertTrue(target.configIndex == 3 && target.config.is(Fluids.WATER) &&
                target.config.getAmount() == 1_000, "fluid config action did not apply its exact slot and stack");

        helper.assertTrue(dispatch(player, target, MEFluidConfigActions.createSetAmountAction(
                3, new FluidStack(Fluids.WATER, 1), 2_000)),
                "valid fluid amount action was rejected");
        helper.assertTrue(target.amountIndex == 3 && target.amount == 2_000,
                "fluid amount action did not apply its exact slot and integer amount");

        helper.assertTrue(dispatch(player, target,
                MEFluidConfigActions.createSetConfigAction(3, FluidStack.EMPTY)),
                "valid clear config action was rejected");
        helper.assertTrue(target.config.isEmpty(), "clear config action retained the configured fluid");

        target.stocking = true;
        helper.assertTrue(dispatch(player, target, MEFluidConfigActions.createSetAutoPullAction(true)),
                "valid auto-pull action was rejected");
        helper.assertTrue(target.autoPull, "auto-pull action did not apply the requested state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedUnauthorizedAndNonAtomicMutations(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        TestMEFluidConfigTarget target = new TestMEFluidConfigTarget();
        target.config = new FluidStack(Fluids.WATER, 1_000);

        helper.assertTrue(!dispatch(player, new Object(), MEFluidConfigActions.createSetAmountAction(
                0, new FluidStack(Fluids.WATER, 1), 2)),
                "ME fluid action accepted an unrelated holder");
        helper.assertTrue(!dispatch(player, target, malformedAmountAction(0, new JsonPrimitive(1.5))),
                "ME fluid action accepted a fractional amount");
        helper.assertTrue(!dispatch(player, target, malformedAmountAction(0, new JsonPrimitive("2"))),
                "ME fluid action accepted a string amount");
        helper.assertTrue(!dispatch(player, target, malformedAmountAction(0, new JsonPrimitive(true))),
                "ME fluid action accepted a boolean amount");
        helper.assertTrue(!dispatch(player, target, malformedAmountAction(0,
                new JsonPrimitive(BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE)))),
                "ME fluid action accepted an overflowing amount");
        helper.assertTrue(!dispatch(player, target, missingAmountAction(0)),
                "ME fluid action accepted a missing amount field");
        helper.assertTrue(!dispatch(player, target, missingExpectedFluidAction(0, 2)),
                "ME fluid action accepted a missing expected fluid");
        helper.assertTrue(target.amountWrites == 0 && target.config.getAmount() == 1_000,
                "rejected fractional amount partially changed target state");
        target.config = FluidStack.EMPTY;
        helper.assertTrue(!dispatch(player, target, MEFluidConfigActions.createSetAmountAction(
                0, new FluidStack(Fluids.WATER, 1), 2)),
                "ME fluid action accepted an amount without an existing config");
        target.config = new FluidStack(Fluids.WATER, 1_000);

        target.autoPull = true;
        helper.assertTrue(!dispatch(player, target, MEFluidConfigActions.createSetConfigAction(
                0, new FluidStack(Fluids.LAVA, 1_000))),
                "ME fluid action accepted manual config while auto-pull was active");
        helper.assertTrue(target.config.is(Fluids.WATER),
                "rejected auto-pull config action partially changed target state");

        target.autoPull = false;
        target.stocking = true;
        helper.assertTrue(!dispatch(player, target, MEFluidConfigActions.createSetAmountAction(
                0, new FluidStack(Fluids.WATER, 1), 2)),
                "stocking hatch accepted a target amount");
        target.allowConfig = false;
        helper.assertTrue(!dispatch(player, target, MEFluidConfigActions.createSetConfigAction(
                0, new FluidStack(Fluids.LAVA, 1_000))),
                "stocking hatch accepted a duplicate fluid configuration");
        helper.assertTrue(target.amountWrites == 0 && target.config.is(Fluids.WATER),
                "rejected stocking actions partially changed target state");
        target.stocking = false;
        helper.assertTrue(!dispatch(player, target, MEFluidConfigActions.createSetAutoPullAction(true)),
                "ordinary ME fluid input accepted an auto-pull action");
        target.supported = false;
        helper.assertTrue(!dispatch(player, target, MEFluidConfigActions.createSetConfigAction(
                0, new FluidStack(Fluids.LAVA, 1_000))),
                "unsupported definition accepted the ME fluid action protocol");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void actionCreatorsRejectBothSlotBoundaries(GameTestHelper helper) {
        assertCreatorRejected(helper, () -> MEFluidConfigActions.createSetConfigAction(-1, FluidStack.EMPTY),
                "config creator accepted slot -1");
        assertCreatorRejected(helper, () -> MEFluidConfigActions.createSetConfigAction(16, FluidStack.EMPTY),
                "config creator accepted slot 16");
        assertCreatorRejected(helper, () -> MEFluidConfigActions.createSetAmountAction(
                -1, new FluidStack(Fluids.WATER, 1), 1),
                "amount creator accepted slot -1");
        assertCreatorRejected(helper, () -> MEFluidConfigActions.createSetAmountAction(
                16, new FluidStack(Fluids.WATER, 1), 1),
                "amount creator accepted slot 16");
        assertCreatorRejected(helper, () -> MEFluidConfigActions.createSetAmountAction(
                0, FluidStack.EMPTY, 1), "amount creator accepted an empty expected fluid");
        helper.succeed();
    }

    private static SyncActionData malformedAmountAction(int slot, JsonPrimitive amount) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(SLOT_FIELD, new JsonPrimitive(slot))
                .put(AMOUNT_FIELD, amount)
                .build();
        return new SyncActionData(SET_AMOUNT_ACTION, 0, DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .set(GTDataComponents.FLUID_CONTENT.get(),
                        SimpleFluidContent.copyOf(new FluidStack(Fluids.WATER, 1)))
                .build());
    }

    private static SyncActionData missingAmountAction(int slot) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(SLOT_FIELD, new JsonPrimitive(slot))
                .build();
        return new SyncActionData(SET_AMOUNT_ACTION, 0, DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .set(GTDataComponents.FLUID_CONTENT.get(),
                        SimpleFluidContent.copyOf(new FluidStack(Fluids.WATER, 1)))
                .build());
    }

    private static SyncActionData missingExpectedFluidAction(int slot, int amount) {
        SyncFieldData fields = SyncFieldData.builder()
                .put(SLOT_FIELD, new JsonPrimitive(slot))
                .put(AMOUNT_FIELD, new JsonPrimitive(amount))
                .build();
        return new SyncActionData(SET_AMOUNT_ACTION, 0, DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .build());
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
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

    private static void assertCreatorRejected(GameTestHelper helper, Runnable creator, String message) {
        boolean rejected = false;
        try {
            creator.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static final class TestMEFluidConfigTarget implements MEFluidConfigActionTarget {

        private boolean supported = true;
        private boolean autoPull;
        private boolean stocking;
        private boolean allowConfig = true;
        private int configIndex = -1;
        private FluidStack config = FluidStack.EMPTY;
        private int amountIndex = -1;
        private int amount;
        private int amountWrites;

        @Override
        public boolean supportsMEFluidConfigActions() {
            return supported;
        }

        @Override
        public int getMEFluidConfigSlotCount() {
            return 16;
        }

        @Override
        public boolean isMEFluidConfigAutoPull() {
            return autoPull;
        }

        @Override
        public boolean isMEFluidStocking() {
            return stocking;
        }

        @Override
        public boolean canSetMEFluidConfig(int slot, @NotNull FluidStack fluid) {
            return allowConfig;
        }

        @Override
        public void setMEFluidConfig(int slot, @NotNull FluidStack fluid) {
            configIndex = slot;
            config = fluid.copy();
        }

        @Override
        public boolean canSetMEFluidConfigAmount(int slot, @NotNull FluidStack expectedFluid, int amount) {
            AEFluidKey configuredKey = AEFluidKey.of(config);
            return !stocking && !autoPull && amount > 0 && configuredKey != null &&
                    configuredKey.equals(AEFluidKey.of(expectedFluid));
        }

        @Override
        public void setMEFluidConfigAmount(int slot, @NotNull FluidStack expectedFluid, int amount) {
            if (!canSetMEFluidConfigAmount(slot, expectedFluid, amount)) {
                throw new IllegalStateException("Test target rejected the expected fluid amount update.");
            }
            amountIndex = slot;
            this.amount = amount;
            amountWrites++;
        }

        @Override
        public void setMEFluidAutoPull(boolean autoPull) {
            this.autoPull = autoPull;
        }
    }
}
