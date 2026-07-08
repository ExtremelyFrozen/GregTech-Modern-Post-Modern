package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.feature.AutoOutputMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class PumpMachineActionTest {

    private static final ResourceLocation SET_PUMP_AUTO_OUTPUT_FLUIDS_ACTION = GTCEu
            .id("set_pump_machine_auto_output_fluids");
    private static final ResourceLocation AUTO_OUTPUT_FLUIDS_FIELD = SyncFieldData.key("autoOutputFluids");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpMachineAction")
    public static void dispatcherExecutesFluidAutoOutputActionForAutoOutputHolder(GameTestHelper helper) {
        TestAutoOutputHolder holder = new TestAutoOutputHolder(false, true, false, false);
        triggerActionRegistration();

        boolean result = dispatch(helper, holder, payload(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(result, "valid pump fluid auto-output action was rejected");
        helper.assertTrue(holder.isAutoOutputFluids(), "valid pump fluid auto-output action did not update fluid state");
        helper.assertTrue(!holder.isAutoOutputItems(), "pump fluid auto-output action changed item state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpMachineAction")
    public static void dispatcherRejectsUnsupportedAutoOutputHolder(GameTestHelper helper) {
        TestAutoOutputHolder holder = new TestAutoOutputHolder(false, false, false, false);
        triggerActionRegistration();

        boolean result = dispatch(helper, holder, payload(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!result, "unsupported pump fluid auto-output holder was accepted");
        helper.assertTrue(!holder.isAutoOutputFluids(), "unsupported holder action changed fluid state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpMachineAction")
    public static void dispatcherRejectsNonAutoOutputHolder(GameTestHelper helper) {
        TestHolderWithoutAutoOutput holder = new TestHolderWithoutAutoOutput();
        triggerActionRegistration();

        boolean result = dispatch(helper, holder, payload(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!result, "non-auto-output pump holder was accepted");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpMachineAction")
    public static void dispatcherRejectsInvalidFluidAutoOutputPayload(GameTestHelper helper) {
        TestAutoOutputHolder holder = new TestAutoOutputHolder(false, true, false, false);
        triggerActionRegistration();

        boolean stringResult = dispatch(helper, holder,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive("true")));
        boolean missingFieldResult = dispatch(helper, holder,
                payload(OTHER_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!stringResult, "string pump fluid auto-output payload was accepted");
        helper.assertTrue(!missingFieldResult, "payload without pump fluid auto-output field was accepted");
        helper.assertTrue(!holder.isAutoOutputFluids(), "invalid payload changed fluid state");
        helper.succeed();
    }

    private static void triggerActionRegistration() {
        var definition = GTMachines.PUMP[GTValues.LV];
        if (definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState()) == null) {
            throw new IllegalStateException("Pump machine test failed to create a machine block entity.");
        }
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(SET_PUMP_AUTO_OUTPUT_FLUIDS_ACTION, 1, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static final class TestAutoOutputHolder implements AutoOutputMachine {

        private final boolean supportsItems;
        private final boolean supportsFluids;
        private boolean autoOutputItems;
        private boolean autoOutputFluids;

        private TestAutoOutputHolder(boolean supportsItems, boolean supportsFluids, boolean autoOutputItems,
                                     boolean autoOutputFluids) {
            this.supportsItems = supportsItems;
            this.supportsFluids = supportsFluids;
            this.autoOutputItems = autoOutputItems;
            this.autoOutputFluids = autoOutputFluids;
        }

        @Override
        public boolean supportsAutoOutputItems() {
            return supportsItems;
        }

        @Override
        public boolean supportsAutoOutputFluids() {
            return supportsFluids;
        }

        @Override
        public boolean isAutoOutputItems() {
            return autoOutputItems;
        }

        @Override
        public boolean isAutoOutputFluids() {
            return autoOutputFluids;
        }

        @Override
        public void setAllowAutoOutputItems(boolean allow) {
            autoOutputItems = allow;
        }

        @Override
        public void setAllowAutoOutputFluids(boolean allow) {
            autoOutputFluids = allow;
        }
    }

    private static final class TestHolderWithoutAutoOutput {}
}
