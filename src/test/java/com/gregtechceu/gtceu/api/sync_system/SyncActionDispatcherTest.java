package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SyncActionDispatcherTest {

    private static final ResourceLocation ACTION_ID = ResourceLocation.fromNamespaceAndPath(
            GTCEu.MOD_ID, "sync_action_dispatcher_test");
    private static final ResourceLocation UNKNOWN_ACTION_ID = ResourceLocation.fromNamespaceAndPath(
            GTCEu.MOD_ID, "sync_action_dispatcher_unknown");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncActionDispatcher")
    public static void dispatcherExecutesRegisteredAction(GameTestHelper helper) {
        RecordingSyncActionHandler handler = new RecordingSyncActionHandler(ACTION_ID, true, true, true);
        SyncActionDispatcherRegistry dispatcher = dispatcherWith(handler);

        boolean result = dispatcher.dispatch(context(helper, ACTION_ID, payload("accepted")));

        helper.assertTrue(result, "registered action was not accepted");
        helper.assertTrue(handler.executions == 1, "registered action did not execute once");
        helper.assertTrue(handler.holderChecks == 1, "holder was not checked once");
        helper.assertTrue(handler.payloadChecks == 1, "payload was not checked once");
        helper.assertTrue(handler.permissionChecks == 1, "permission was not checked once");
        helper.assertTrue(handler.phases.equals(List.of("holder", "payload", "permission", "execute")),
                "registered action stages ran in the wrong order");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncActionDispatcher")
    public static void dispatcherRejectsUnknownAction(GameTestHelper helper) {
        RecordingSyncActionHandler handler = new RecordingSyncActionHandler(ACTION_ID, true, true, true);
        SyncActionDispatcherRegistry dispatcher = dispatcherWith(handler);

        boolean result = dispatcher.dispatch(context(helper, UNKNOWN_ACTION_ID, payload("unknown")));

        helper.assertTrue(!result, "unknown action was accepted");
        helper.assertTrue(handler.executions == 0, "handler executed for an unknown action");
        helper.assertTrue(handler.phases.isEmpty(), "registered handler was consulted for an unknown action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncActionDispatcher")
    public static void dispatcherRejectsEmptyPayloadWithoutExecuting(GameTestHelper helper) {
        RecordingSyncActionHandler handler = new RecordingSyncActionHandler(ACTION_ID, true, true, true);
        SyncActionDispatcherRegistry dispatcher = dispatcherWith(handler);

        boolean result = dispatcher.dispatch(context(helper, ACTION_ID, DataComponentMap.builder().build()));

        helper.assertTrue(!result, "empty payload was accepted");
        helper.assertTrue(handler.executions == 0, "empty payload executed");
        helper.assertTrue(handler.holderChecks == 1, "holder was not checked before empty payload rejection");
        helper.assertTrue(handler.payloadChecks == 0, "empty payload reached payload validation");
        helper.assertTrue(handler.permissionChecks == 0, "empty payload reached permission validation");
        helper.assertTrue(handler.phases.equals(List.of("holder")), "empty payload ran unexpected stages");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncActionDispatcher")
    public static void dispatcherRejectsInvalidHolderWithoutExecuting(GameTestHelper helper) {
        RecordingSyncActionHandler handler = new RecordingSyncActionHandler(ACTION_ID, false, true, true);
        SyncActionDispatcherRegistry dispatcher = dispatcherWith(handler);

        boolean result = dispatcher.dispatch(context(helper, ACTION_ID, payload("bad_holder")));

        helper.assertTrue(!result, "invalid holder was accepted");
        helper.assertTrue(handler.executions == 0, "invalid holder executed");
        helper.assertTrue(handler.holderChecks == 1, "holder was not checked once");
        helper.assertTrue(handler.payloadChecks == 0, "invalid holder reached payload validation");
        helper.assertTrue(handler.permissionChecks == 0, "invalid holder reached permission validation");
        helper.assertTrue(handler.phases.equals(List.of("holder")), "invalid holder ran unexpected stages");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncActionDispatcher")
    public static void dispatcherRejectsInvalidPayloadWithoutExecuting(GameTestHelper helper) {
        RecordingSyncActionHandler handler = new RecordingSyncActionHandler(ACTION_ID, true, false, true);
        SyncActionDispatcherRegistry dispatcher = dispatcherWith(handler);

        boolean result = dispatcher.dispatch(context(helper, ACTION_ID, payload("bad_payload")));

        helper.assertTrue(!result, "invalid payload was accepted");
        helper.assertTrue(handler.executions == 0, "invalid payload executed");
        helper.assertTrue(handler.holderChecks == 1, "holder was not checked once");
        helper.assertTrue(handler.payloadChecks == 1, "payload was not checked once");
        helper.assertTrue(handler.permissionChecks == 0, "invalid payload reached permission validation");
        helper.assertTrue(handler.phases.equals(List.of("holder", "payload")),
                "invalid payload ran unexpected stages");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncActionDispatcher")
    public static void dispatcherRejectsPermissionFailureWithoutExecuting(GameTestHelper helper) {
        RecordingSyncActionHandler handler = new RecordingSyncActionHandler(ACTION_ID, true, true, false);
        SyncActionDispatcherRegistry dispatcher = dispatcherWith(handler);

        boolean result = dispatcher.dispatch(context(helper, ACTION_ID, payload("denied")));

        helper.assertTrue(!result, "permission failure was accepted");
        helper.assertTrue(handler.executions == 0, "permission failure executed");
        helper.assertTrue(handler.holderChecks == 1, "holder was not checked once");
        helper.assertTrue(handler.payloadChecks == 1, "payload was not checked once");
        helper.assertTrue(handler.permissionChecks == 1, "permission was not checked once");
        helper.assertTrue(handler.phases.equals(List.of("holder", "payload", "permission")),
                "permission failure ran unexpected stages");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SyncActionDispatcher")
    public static void dispatcherRejectsDuplicateRegistration(GameTestHelper helper) {
        SyncActionDispatcherRegistry dispatcher = new SyncActionDispatcherRegistry();
        dispatcher.register(new RecordingSyncActionHandler(ACTION_ID, true, true, true));

        try {
            dispatcher.register(new RecordingSyncActionHandler(ACTION_ID, true, true, true));
            helper.fail("duplicate action handler registration was accepted");
        } catch (IllegalArgumentException ignored) {
            helper.succeed();
        }
    }

    private static SyncActionDispatcherRegistry dispatcherWith(RecordingSyncActionHandler handler) {
        SyncActionDispatcherRegistry dispatcher = new SyncActionDispatcherRegistry();
        dispatcher.register(handler);
        return dispatcher;
    }

    private static SyncActionContext context(GameTestHelper helper, ResourceLocation actionId,
                                             DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack holder = new ItemStack(Items.STICK);
        SyncActionData action = new SyncActionData(actionId, 1, payload);
        return SyncActionContext.item(player, holder, action, InteractionHand.MAIN_HAND);
    }

    private static DataComponentMap payload(String name) {
        return DataComponentMap.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal(name))
                .build();
    }

    private static final class RecordingSyncActionHandler implements SyncActionHandler {

        private final ResourceLocation actionId;
        private final boolean acceptsHolder;
        private final boolean acceptsPayload;
        private final boolean mayExecute;
        private final List<String> phases = new ArrayList<>();
        private int holderChecks;
        private int payloadChecks;
        private int permissionChecks;
        private int executions;

        private RecordingSyncActionHandler(ResourceLocation actionId, boolean acceptsHolder, boolean acceptsPayload,
                                           boolean mayExecute) {
            this.actionId = actionId;
            this.acceptsHolder = acceptsHolder;
            this.acceptsPayload = acceptsPayload;
            this.mayExecute = mayExecute;
        }

        @Override
        public ResourceLocation actionId() {
            return actionId;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            holderChecks++;
            phases.add("holder");
            return acceptsHolder;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            payloadChecks++;
            phases.add("payload");
            return acceptsPayload;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            permissionChecks++;
            phases.add("permission");
            return mayExecute;
        }

        @Override
        public void execute(SyncActionContext context) {
            executions++;
            phases.add("execute");
        }
    }
}
