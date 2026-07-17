package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.gametest.util.TestUtils;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CrateTapedSyncTest {

    private static final String BATCH = "CrateTapedSync";
    private static final BlockPos CRATE_POS = new BlockPos(1, 1, 1);
    private static final ResourceLocation TAPED_FIELD = SyncFieldData.key("isTaped");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void tapedCrateFullSyncPreservesRerenderSaveAndItemComponents(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        CrateMachine server = setCrate(helper);
        TestCrateMachine client = createClientCrate();
        ItemStack stored = new ItemStack(Items.DIAMOND, 3);
        server.inventory.setStackInSlot(0, stored);
        ServerPlayer player = preparePlayer(helper, server, GTItems.DUCT_TAPE.asStack());

        InteractionResult result = useHeldItem(server, player);

        helper.assertTrue(result.consumesAction(), "duct tape interaction was not handled by the crate");
        helper.assertTrue(server.isTaped(), "duct tape interaction did not mark the crate as taped");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "duct tape interaction did not consume tape from a survival player");
        helper.assertTrue(!server.inventory.shouldDropInventoryInWorld(),
                "taped crate still allowed its inventory to drop separately");
        helper.assertTrue(server.getRenderState().getValue(GTMachineModelProperties.IS_TAPED),
                "duct tape interaction did not update the crate render state");

        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertTapedField(helper, saved, true, "taped crate saved state");
        assertItemComponents(helper, server, stored);

        DataComponentMap initial = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertTapedField(helper, initial, true, "taped crate full sync");
        client.resetRenderUpdates();
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, initial);
        helper.assertTrue(client.isTaped(), "taped crate full sync did not update the client field");
        helper.assertTrue(client.renderUpdates > 0,
                "taped crate full sync did not invoke the client rerender hook");
        helper.assertTrue(client.getRenderState().getValue(GTMachineModelProperties.IS_TAPED),
                "taped crate full sync did not update the client render state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void tapeInteractionProducesChangedDeltaAndRepeatedUseProducesNoDelta(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        CrateMachine server = setCrate(helper);
        TestCrateMachine client = createClientCrate();
        client.getSyncDataHolder().applyClientNetworkUpdate(registries,
                server.getSyncDataHolder().serializeFullClientSyncComponents(registries));
        client.resetRenderUpdates();
        ServerPlayer player = preparePlayer(helper, server, GTItems.BASIC_TAPE.asStack(2));

        InteractionResult result = useHeldItem(server, player);
        DataComponentMap changed = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertTapedField(helper, changed, true, "taped crate delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, changed);

        helper.assertTrue(result.consumesAction(), "basic tape interaction was not handled by the crate");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                "basic tape interaction consumed the wrong tape count");
        helper.assertTrue(client.isTaped(), "taped crate delta did not update the client field");
        helper.assertTrue(client.renderUpdates > 0,
                "taped crate delta did not invoke the client rerender hook");
        helper.assertTrue(client.getRenderState().getValue(GTMachineModelProperties.IS_TAPED),
                "taped crate delta did not update the client render state");

        useHeldItem(server, player);
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                "repeated tape interaction consumed tape from an already taped crate");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "repeated tape interaction produced a redundant client delta");
        helper.succeed();
    }

    private static CrateMachine setCrate(GameTestHelper helper) {
        return (CrateMachine) TestUtils.setMachine(helper, CRATE_POS, GTMachines.BRONZE_CRATE);
    }

    private static TestCrateMachine createClientCrate() {
        var definition = GTMachines.BRONZE_CRATE;
        return new TestCrateMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper, CrateMachine crate, ItemStack heldItem) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.moveTo(Vec3.atCenterOf(crate.getBlockPos()));
        player.setItemInHand(InteractionHand.MAIN_HAND, heldItem);
        return player;
    }

    private static InteractionResult useHeldItem(CrateMachine crate, ServerPlayer player) {
        BlockPos pos = crate.getBlockPos();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return crate.onUseWithItem(new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static void assertItemComponents(GameTestHelper helper, CrateMachine crate, ItemStack expected) {
        DataComponentMap.Builder builder = DataComponentMap.builder();
        crate.collectImplicitComponents(builder);
        DataComponentMap components = builder.build();
        ItemContainerContents contents = components.get(DataComponents.CONTAINER);
        NonNullList<ItemStack> savedItems = NonNullList.withSize(crate.getInventorySize(), ItemStack.EMPTY);
        if (contents != null) {
            contents.copyInto(savedItems);
        }
        helper.assertTrue(components.get(GTDataComponents.TAPED.get()) != null,
                "taped crate item components omitted the taped marker");
        helper.assertTrue(ItemStack.isSameItemSameComponents(savedItems.getFirst(), expected) &&
                savedItems.getFirst().getCount() == expected.getCount(),
                "taped crate item components did not preserve the stored inventory");
    }

    private static void assertTapedField(GameTestHelper helper, DataComponentMap components,
                                         boolean expected, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        assertTapedField(helper, fields, expected, description);
    }

    private static void assertTapedField(GameTestHelper helper, SyncFieldData fields,
                                         boolean expected, String description) {
        JsonElement value = fields == null ? null : fields.get(TAPED_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected taped state");
    }

    private static final class TestCrateMachine extends CrateMachine {

        private int renderUpdates;

        private TestCrateMachine(BlockEntityCreationInfo info) {
            super(info, GTMaterials.Bronze, 54);
        }

        @Override
        public void scheduleRenderUpdate() {
            super.scheduleRenderUpdate();
            renderUpdates++;
        }

        private void resetRenderUpdates() {
            renderUpdates = 0;
        }
    }
}
