package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.ParallelHatch;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;

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
public class ParallelHatchPartMachineActionTest {

    private static final ResourceLocation SET_PARALLEL_HATCH_CURRENT_PARALLEL_ACTION = GTCEu
            .id("set_parallel_hatch_current_parallel");
    private static final ResourceLocation CURRENT_PARALLEL_FIELD = SyncFieldData.key("currentParallel");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ParallelHatchPartMachineAction")
    public static void dispatcherExecutesCurrentParallelActionForParallelHatchHolder(GameTestHelper helper) {
        TestParallelHatch holder = new TestParallelHatch(1);
        triggerActionRegistration();

        boolean result = dispatch(helper, holder, payload(CURRENT_PARALLEL_FIELD, new JsonPrimitive(8)));

        helper.assertTrue(result, "valid parallel hatch action was rejected");
        helper.assertTrue(holder.getCurrentParallel() == 8,
                "valid parallel hatch action did not update holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ParallelHatchPartMachineAction")
    public static void dispatcherRejectsHolderWithoutParallelHatchContract(GameTestHelper helper) {
        TestNonParallelHatchHolder holder = new TestNonParallelHatchHolder(1);
        triggerActionRegistration();

        boolean result = dispatch(helper, holder, payload(CURRENT_PARALLEL_FIELD, new JsonPrimitive(8)));

        helper.assertTrue(!result, "non-parallel hatch holder was accepted");
        helper.assertTrue(holder.getCurrentParallel() == 1, "rejected holder action changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ParallelHatchPartMachineAction")
    public static void dispatcherRejectsInvalidCurrentParallelPayload(GameTestHelper helper) {
        TestParallelHatch holder = new TestParallelHatch(1);
        triggerActionRegistration();

        boolean stringResult = dispatch(helper, holder,
                payload(CURRENT_PARALLEL_FIELD, new JsonPrimitive("8")));
        boolean missingFieldResult = dispatch(helper, holder,
                payload(OTHER_FIELD, new JsonPrimitive(8)));
        boolean decimalResult = dispatch(helper, holder,
                payload(CURRENT_PARALLEL_FIELD, new JsonPrimitive(1.5)));
        boolean outOfRangeResult = dispatch(helper, holder,
                payload(CURRENT_PARALLEL_FIELD, new JsonPrimitive((long) Integer.MAX_VALUE + 1L)));

        helper.assertTrue(!stringResult, "string current parallel payload was accepted");
        helper.assertTrue(!missingFieldResult, "payload without current parallel field was accepted");
        helper.assertTrue(!decimalResult, "decimal current parallel payload was accepted");
        helper.assertTrue(!outOfRangeResult, "out-of-range current parallel payload was accepted");
        helper.assertTrue(holder.getCurrentParallel() == 1, "invalid payload changed holder state");
        helper.succeed();
    }

    private static void triggerActionRegistration() {
        var definition = GCYMMachines.PARALLEL_HATCH[GTValues.IV];
        if (definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState()) == null) {
            throw new IllegalStateException("Parallel hatch test failed to create a machine block entity.");
        }
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(SET_PARALLEL_HATCH_CURRENT_PARALLEL_ACTION, 1, payload);
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

    private static final class TestParallelHatch implements ParallelHatch {

        private int currentParallel;

        private TestParallelHatch(int currentParallel) {
            this.currentParallel = currentParallel;
        }

        @Override
        public int getCurrentParallel() {
            return currentParallel;
        }

        @Override
        public void setCurrentParallel(int parallelAmount) {
            this.currentParallel = parallelAmount;
        }
    }

    private static final class TestNonParallelHatchHolder {

        private final int currentParallel;

        private TestNonParallelHatchHolder(int currentParallel) {
            this.currentParallel = currentParallel;
        }

        private int getCurrentParallel() {
            return currentParallel;
        }
    }
}
