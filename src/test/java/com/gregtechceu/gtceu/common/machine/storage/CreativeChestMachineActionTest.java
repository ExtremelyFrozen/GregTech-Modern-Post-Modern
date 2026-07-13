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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CreativeChestMachineActionTest {

    private static final String BATCH = "CreativeChestMachineAction";
    private static final ResourceLocation SET_CREATIVE_CHEST_ITEM_ACTION = GTCEu.id("set_creative_chest_item");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorNormalizesStoredCountAndSequence(GameTestHelper helper) {
        ItemStack selected = new ItemStack(Items.DIAMOND, 64);

        SyncActionData action = CreativeChestMachineActions.createSetItemAction(selected);
        ItemStack encoded = action.payload().get(GTDataComponents.PLACEHOLDER_ITEM_STACK.get());

        helper.assertTrue(action.actionId().equals(SET_CREATIVE_CHEST_ITEM_ACTION),
                "creative chest action creator used the wrong action id");
        helper.assertTrue(encoded != null && encoded.is(Items.DIAMOND) && encoded.getCount() == 1,
                "creative chest action creator did not normalize the selected item count");
        helper.assertTrue(action.sequence() == ItemStack.hashItemAndComponents(encoded),
                "creative chest action creator did not preserve the item hash sequence");
        helper.assertTrue(selected.getCount() == 64, "creative chest action creator mutated its input stack");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAppliesAndNormalizesItemPayload(GameTestHelper helper) {
        CreativeChestMachine machine = createMachine();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        boolean selected = dispatch(player, machine, payload(new ItemStack(Items.EMERALD, 48)));

        helper.assertTrue(selected, "valid creative chest item action was rejected");
        helper.assertTrue(machine.getStored().is(Items.EMERALD) && machine.getStored().getCount() == 1,
                "creative chest action did not normalize the server-side stored item count");

        boolean cleared = dispatch(player, machine, payload(ItemStack.EMPTY));

        helper.assertTrue(cleared, "creative chest clear action was rejected");
        helper.assertTrue(machine.getStored().isEmpty(), "creative chest clear action did not clear the stored item");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMissingPayloadAndWrongHolder(GameTestHelper helper) {
        CreativeChestMachine machine = createMachine();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        boolean missing = dispatch(player, machine, DataComponentMap.EMPTY);
        boolean wrongHolder = dispatch(player, new Object(), payload(new ItemStack(Items.DIAMOND)));

        helper.assertTrue(!missing, "creative chest action without an item payload was accepted");
        helper.assertTrue(!wrongHolder, "creative chest action accepted a non-creative-chest holder");
        helper.assertTrue(machine.getStored().isEmpty(), "rejected creative chest actions changed stored state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        CreativeChestMachine machine = createMachine();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);

        boolean result = dispatch(player, machine, payload(new ItemStack(Items.DIAMOND)));
        player.setGameMode(GameType.SURVIVAL);

        helper.assertTrue(!result, "creative chest item action accepted a spectator");
        helper.assertTrue(machine.getStored().isEmpty(), "spectator action changed the creative chest item");
        helper.succeed();
    }

    private static CreativeChestMachine createMachine() {
        var definition = GTMachines.CREATIVE_ITEM;
        return new CreativeChestMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static boolean dispatch(ServerPlayer player, Object holder, DataComponentMap payload) {
        SyncActionData action = new SyncActionData(SET_CREATIVE_CHEST_ITEM_ACTION, 0, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(ItemStack item) {
        return DataComponentMap.builder()
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), item)
                .build();
    }
}
