package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CreativeTankMachineActionTest {

    private static final String BATCH = "CreativeTankMachineAction";
    private static final ResourceLocation SET_CREATIVE_TANK_FLUID_ACTION = GTCEu.id("set_creative_tank_fluid");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorNormalizesStoredAmountAndSequence(GameTestHelper helper) {
        FluidStack selected = new FluidStack(Fluids.WATER, 5 * FluidType.BUCKET_VOLUME);

        SyncActionData action = CreativeTankMachineActions.createSetFluidAction(selected);
        FluidStack encoded = requireFluidPayload(action);

        helper.assertTrue(action.actionId().equals(SET_CREATIVE_TANK_FLUID_ACTION),
                "creative tank action creator used the wrong action id");
        helper.assertTrue(FluidStack.isSameFluidSameComponents(encoded, selected) &&
                encoded.getAmount() == FluidType.BUCKET_VOLUME,
                "creative tank action creator did not normalize the selected fluid amount");
        helper.assertTrue(action.sequence() ==
                FluidStack.hashFluidAndComponents(encoded) * 31 + encoded.getAmount(),
                "creative tank action creator did not preserve the fluid hash and amount sequence");
        helper.assertTrue(selected.getAmount() == 5 * FluidType.BUCKET_VOLUME,
                "creative tank action creator mutated its input stack");

        SyncActionData clearAction = CreativeTankMachineActions.createSetFluidAction(FluidStack.EMPTY);
        FluidStack encodedEmpty = requireFluidPayload(clearAction);

        helper.assertTrue(encodedEmpty.isEmpty(), "creative tank clear action encoded a non-empty fluid");
        helper.assertTrue(clearAction.sequence() ==
                FluidStack.hashFluidAndComponents(encodedEmpty) * 31 + encodedEmpty.getAmount(),
                "creative tank clear action did not preserve the empty fluid sequence");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void targetNormalizesAndCopiesStoredFluid(GameTestHelper helper) {
        CreativeTankMachine machine = createMachine();
        FluidStack selected = new FluidStack(Fluids.WATER, 7 * FluidType.BUCKET_VOLUME);

        machine.setCreativeTankFluid(selected);

        helper.assertTrue(FluidStack.isSameFluidSameComponents(machine.getStored(), selected) &&
                machine.getStored().getAmount() == FluidType.BUCKET_VOLUME,
                "creative tank target did not normalize the stored fluid amount");
        helper.assertTrue(selected.getAmount() == 7 * FluidType.BUCKET_VOLUME,
                "creative tank target mutated its input stack");

        machine.setCreativeTankFluid(FluidStack.EMPTY);

        helper.assertTrue(machine.getStored().isEmpty(), "creative tank target did not clear the stored fluid");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAppliesAndNormalizesFluidPayload(GameTestHelper helper) {
        CreativeTankMachine machine = createMachine();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        boolean selected = dispatch(player, machine, payload(new FluidStack(Fluids.LAVA, 48)));

        helper.assertTrue(selected, "valid creative tank fluid action was rejected");
        helper.assertTrue(machine.getStored().getFluid() == Fluids.LAVA &&
                machine.getStored().getAmount() == FluidType.BUCKET_VOLUME,
                "creative tank action did not normalize the server-side stored fluid amount");

        boolean cleared = dispatch(player, machine, payload(FluidStack.EMPTY));

        helper.assertTrue(cleared, "creative tank clear action was rejected");
        helper.assertTrue(machine.getStored().isEmpty(), "creative tank clear action did not clear the stored fluid");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMissingPayloadAndWrongHolder(GameTestHelper helper) {
        CreativeTankMachine machine = createMachine();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        boolean missing = dispatch(player, machine, DataComponentMap.EMPTY);
        boolean wrongHolder = dispatch(player, new Object(), payload(new FluidStack(Fluids.WATER, 1)));

        helper.assertTrue(!missing, "creative tank action without a fluid payload was accepted");
        helper.assertTrue(!wrongHolder, "creative tank action accepted a non-creative-tank holder");
        helper.assertTrue(machine.getStored().isEmpty(), "rejected creative tank actions changed stored state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        CreativeTankMachine machine = createMachine();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);

        boolean result = dispatch(player, machine, payload(new FluidStack(Fluids.WATER, 1)));
        player.setGameMode(GameType.SURVIVAL);

        helper.assertTrue(!result, "creative tank fluid action accepted a spectator");
        helper.assertTrue(machine.getStored().isEmpty(), "spectator action changed the creative tank fluid");
        helper.succeed();
    }

    private static CreativeTankMachine createMachine() {
        var definition = GTMachines.CREATIVE_FLUID;
        return new CreativeTankMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static boolean dispatch(ServerPlayer player, Object holder, DataComponentMap payload) {
        SyncActionData action = new SyncActionData(SET_CREATIVE_TANK_FLUID_ACTION, 0, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(FluidStack fluid) {
        return DataComponentMap.builder()
                .set(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(fluid))
                .build();
    }

    private static FluidStack requireFluidPayload(SyncActionData action) {
        SimpleFluidContent fluidContent = action.payload().get(GTDataComponents.FLUID_CONTENT.get());
        if (fluidContent == null) {
            throw new IllegalStateException("Creative tank action creator omitted its fluid payload.");
        }
        return fluidContent.copy();
    }
}
