package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
public class WorldAcceleratorSyncTest {

    private static final String BATCH = "WorldAcceleratorSync";
    private static final int TIER = GTValues.LV;
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("isWorkingEnabled");
    private static final ResourceLocation RANDOM_TICK_MODE_FIELD = SyncFieldData.key("isRandomTickMode");
    private static final ResourceLocation ACTIVE_FIELD = SyncFieldData.key("active");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fullAndDeltaSyncAllServerOwnedFields(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestWorldAcceleratorMachine server = createMachine(false);
        TestWorldAcceleratorMachine client = createMachine(true);
        server.energyContainer.setEnergyStored(GTValues.V[TIER] * 4L);
        server.updateSubscription();
        server.setRandomTickMode(false);

        DataComponentMap initial = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertBooleanField(helper, initial, WORKING_ENABLED_FIELD, true, "world accelerator full sync");
        assertBooleanField(helper, initial, RANDOM_TICK_MODE_FIELD, false, "world accelerator full sync");
        assertBooleanField(helper, initial, ACTIVE_FIELD, true, "world accelerator full sync");
        assertBooleanField(helper, saved, WORKING_ENABLED_FIELD, true, "world accelerator saved state");
        assertBooleanField(helper, saved, RANDOM_TICK_MODE_FIELD, false, "world accelerator saved state");
        assertBooleanField(helper, saved, ACTIVE_FIELD, true, "world accelerator saved state");
        client.resetTracking();
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, initial);
        assertMachineState(helper, client, true, false, true, "world accelerator full sync");
        helper.assertTrue(client.renderUpdates > 0,
                "world accelerator full sync did not invoke the client rerender hook");

        server.setWorkingEnabled(false);
        DataComponentMap changed = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertBooleanField(helper, changed, WORKING_ENABLED_FIELD, false, "world accelerator disabled delta");
        assertBooleanField(helper, changed, ACTIVE_FIELD, false, "world accelerator disabled delta");
        client.resetTracking();
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, changed);
        assertMachineState(helper, client, false, false, false, "world accelerator disabled delta");
        helper.assertTrue(client.renderUpdates > 0,
                "world accelerator disabled delta did not invoke the client rerender hook");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void changedOnlyWritesAvoidDuplicateSubscriptionsAndDeltas(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestWorldAcceleratorMachine machine = createMachine(false);
        machine.energyContainer.setEnergyStored(GTValues.V[TIER] * 4L);
        machine.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        machine.resetTracking();

        machine.updateSubscription();
        helper.assertTrue(machine.isActive(), "powered world accelerator did not become active");
        helper.assertTrue(machine.subscriptionRequests == 1,
                "powered world accelerator did not create exactly one tick subscription");
        helper.assertTrue(machine.renderUpdates == 1,
                "powered world accelerator did not update its active render state exactly once");
        machine.getSyncDataHolder().serializeToComponents(registries, true, false);

        machine.updateSubscription();
        helper.assertTrue(machine.subscriptionRequests == 1,
                "unchanged active world accelerator created a duplicate tick subscription");
        helper.assertTrue(machine.renderUpdates == 1,
                "unchanged active world accelerator repeated its render-state side effect");
        helper.assertTrue(machine.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged active world accelerator produced a redundant client delta");

        TickableSubscription firstSubscription = machine.lastSubscription;
        machine.setWorkingEnabled(false);
        helper.assertTrue(!machine.isWorkingEnabled() && !machine.isActive(),
                "disabled world accelerator retained working or active state");
        helper.assertTrue(firstSubscription != null && !firstSubscription.isStillSubscribed(),
                "disabled world accelerator did not cancel its tick subscription");
        machine.getSyncDataHolder().serializeToComponents(registries, true, false);
        int disabledRenderUpdates = machine.renderUpdates;

        machine.setWorkingEnabled(false);
        helper.assertTrue(machine.renderUpdates == disabledRenderUpdates,
                "unchanged disabled world accelerator repeated its side effects");
        helper.assertTrue(machine.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged disabled world accelerator produced a redundant client delta");

        machine.setRandomTickMode(false);
        DataComponentMap modeDelta = machine.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertBooleanField(helper, modeDelta, RANDOM_TICK_MODE_FIELD, false, "world accelerator mode delta");
        int modeRenderUpdates = machine.renderUpdates;
        machine.setRandomTickMode(false);
        helper.assertTrue(machine.renderUpdates == modeRenderUpdates,
                "unchanged world accelerator mode repeated its render-state side effect");
        helper.assertTrue(machine.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged world accelerator mode produced a redundant client delta");

        machine.energyContainer.setEnergyStored(GTValues.V[TIER] * 7L);
        machine.setWorkingEnabled(true);
        helper.assertTrue(machine.isWorkingEnabled() && machine.isActive(),
                "re-enabled world accelerator did not resume with sufficient energy");
        helper.assertTrue(machine.subscriptionRequests == 2,
                "re-enabled world accelerator did not create one replacement tick subscription");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void screwdriverModeChangePreservesCurrentSubscriptionTiming(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestWorldAcceleratorMachine machine = createMachine(false);
        machine.energyContainer.setEnergyStored(GTValues.V[TIER] * 4L);
        machine.updateSubscription();
        machine.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        machine.resetRenderUpdates();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));

        InteractionResult result = machine.useScrewdriver(context(player));
        DataComponentMap changed = machine.getSyncDataHolder().serializeToComponents(registries, true, false);

        helper.assertTrue(result == InteractionResult.CONSUME,
                "world accelerator screwdriver interaction was not consumed");
        helper.assertTrue(!machine.isRandomTickMode(),
                "world accelerator screwdriver interaction did not change its mode");
        helper.assertTrue(machine.isActive(),
                "world accelerator mode change unexpectedly retimed its active state");
        helper.assertTrue(machine.subscriptionRequests == 1 && machine.lastSubscription != null &&
                machine.lastSubscription.isStillSubscribed(),
                "world accelerator mode change unexpectedly replaced its tick subscription");
        helper.assertTrue(machine.renderUpdates == 2,
                "world accelerator screwdriver interaction did not preserve its render-update side effects");
        assertBooleanField(helper, changed, RANDOM_TICK_MODE_FIELD, false,
                "world accelerator screwdriver delta");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void clientUpdatesCannotWriteServerOwnedFields(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestWorldAcceleratorMachine machine = createMachine(false);
        machine.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        ServerFieldUpdateResult result = machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, serverUpdatePayload(false, false, true));

        helper.assertTrue(!result.getAccepted(),
                "world accelerator accepted a client update for server-owned fields");
        assertMachineState(helper, machine, true, true, false, "rejected world accelerator client update");
        helper.assertTrue(machine.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "rejected world accelerator client update produced a client acknowledgement");
        helper.succeed();
    }

    private static TestWorldAcceleratorMachine createMachine(boolean clientSide) {
        var definition = GTMachines.WORLD_ACCELERATOR[TIER];
        return new TestWorldAcceleratorMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), clientSide);
    }

    private static ExtendedUseOnContext context(ServerPlayer player) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(BlockPos.ZERO), Direction.UP, BlockPos.ZERO, false);
        return new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit);
    }

    private static DataComponentMap serverUpdatePayload(boolean workingEnabled, boolean randomTickMode,
                                                        boolean active) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(workingEnabled))
                        .put(RANDOM_TICK_MODE_FIELD, new JsonPrimitive(randomTickMode))
                        .put(ACTIVE_FIELD, new JsonPrimitive(active))
                        .build())
                .build();
    }

    private static void assertMachineState(GameTestHelper helper, WorldAcceleratorMachine machine,
                                           boolean workingEnabled, boolean randomTickMode, boolean active,
                                           String description) {
        helper.assertTrue(machine.isWorkingEnabled() == workingEnabled &&
                machine.isRandomTickMode() == randomTickMode && machine.isActive() == active,
                description + " did not contain the expected field values");
        helper.assertTrue(machine.getRenderState().getValue(GTMachineModelProperties.IS_WORKING_ENABLED) ==
                workingEnabled &&
                machine.getRenderState().getValue(GTMachineModelProperties.IS_RANDOM_TICK_MODE) == randomTickMode &&
                machine.getRenderState().getValue(GTMachineModelProperties.IS_ACTIVE) == active,
                description + " did not contain the expected render-state values");
    }

    private static void assertBooleanField(GameTestHelper helper, DataComponentMap components,
                                           ResourceLocation field, boolean expected, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        assertBooleanField(helper, fields, field, expected, description);
    }

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields,
                                           ResourceLocation field, boolean expected, String description) {
        JsonElement value = fields == null ? null : fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected " + field.getPath() + " field");
    }

    private static final class TestWorldAcceleratorMachine extends WorldAcceleratorMachine {

        private final boolean clientSide;
        private int subscriptionRequests;
        private int renderUpdates;
        private TickableSubscription lastSubscription;

        private TestWorldAcceleratorMachine(BlockEntityCreationInfo info, boolean clientSide) {
            super(info, TIER);
            this.clientSide = clientSide;
        }

        @Override
        public boolean isRemote() {
            return clientSide;
        }

        @Override
        public TickableSubscription subscribeServerTick(Runnable runnable) {
            subscriptionRequests++;
            lastSubscription = new TickableSubscription(runnable);
            return lastSubscription;
        }

        @Override
        public void scheduleRenderUpdate() {
            super.scheduleRenderUpdate();
            renderUpdates++;
        }

        private InteractionResult useScrewdriver(ExtendedUseOnContext context) {
            return super.onScrewdriverClick(context);
        }

        private void resetTracking() {
            subscriptionRequests = 0;
            renderUpdates = 0;
            lastSubscription = null;
        }

        private void resetRenderUpdates() {
            renderUpdates = 0;
        }
    }
}
