package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
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

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CentralMonitorImageModuleActionTest {

    private static final String BATCH = "CentralMonitorImageModuleAction";
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_central_monitor_image_module_url");
    private static final ResourceLocation HOLDER_INCARNATION_FIELD = SyncFieldData.key("holder_incarnation");
    private static final ResourceLocation GROUP_IDENTITY_FIELD = SyncFieldData.key("group_identity");
    private static final ResourceLocation MODULE_SLOT_INCARNATION_FIELD = SyncFieldData.key("module_slot_incarnation");
    private static final ResourceLocation EXPECTED_URL_FIELD = SyncFieldData.key("expected_url");
    private static final ResourceLocation REQUESTED_URL_FIELD = SyncFieldData.key("requested_url");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");
    private static final int MAX_URL_LENGTH = 32_767;
    private static final BlockPos MONITOR_POSITION = new BlockPos(1, 0, 0);
    private static final BlockPos SECOND_MONITOR_POSITION = new BlockPos(2, 0, 0);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void centralMonitorAppliesStackedUrlInPlaceAndPublishesDirtyDelta(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine server = new TestCentralMonitorMachine(false);
        server.setLevel(helper.getLevel());
        server.addMonitor(MONITOR_POSITION);
        UUID groupIdentity = UUID.randomUUID();
        helper.assertTrue(server.createCentralMonitorGroup(0, groupIdentity, Set.of(MONITOR_POSITION)),
                "server rejected valid image module test group creation");
        MonitorGroup serverGroup = server.getMonitorGroups().getFirst();
        ItemStack stackedModule = imageModule(null);
        stackedModule.setCount(2);
        helper.assertTrue(serverGroup.getItemStackHandler().insertItem(0, stackedModule, false).isEmpty(),
                "server module slot rejected a valid stacked image module");
        UUID slotIncarnation = serverGroup.getModuleSlotIncarnation();

        TestCentralMonitorMachine client = new TestCentralMonitorMachine(true);
        DataComponentMap full = server.getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), full);
        server.getSyncDataHolder().serializeToComponents(helper.getLevel().registryAccess(), true, false);

        SyncActionData action = CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                server.getCentralMonitorActionIncarnation(),
                groupIdentity,
                slotIncarnation,
                CentralMonitorImageModuleActions.captureExpectedModule(
                        serverGroup.getItemStackHandler().getStackInSlot(0)),
                "https://example.invalid/authoritative.png",
                0);
        helper.assertTrue(dispatch(player, server, action),
                "Central Monitor rejected a valid stacked image module URL action");
        helper.assertTrue(serverGroup.getItemStackHandler().getStackInSlot(0).getCount() == 2,
                "stacked image module URL action changed the server module count");
        helper.assertTrue(serverGroup.getModuleSlotIncarnation().equals(slotIncarnation),
                "in-place image module URL change rotated the physical slot incarnation");

        DataComponentMap delta = server.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);
        helper.assertTrue(!delta.isEmpty(), "image module URL action did not mark monitorGroups dirty");
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), delta);
        MonitorGroup clientGroup = client.getMonitorGroups().getFirst();
        helper.assertTrue("https://example.invalid/authoritative.png".equals(
                clientGroup.getItemStackHandler().getStackInSlot(0).get(GTDataComponents.IMAGE_MODULE_URL.get())),
                "image module URL action did not publish the current module configuration");
        helper.assertTrue(clientGroup.getItemStackHandler().getStackInSlot(0).getCount() == 2,
                "stacked image module URL action changed the synchronized module count");
        helper.assertTrue(clientGroup.getModuleSlotIncarnation().equals(slotIncarnation),
                "image module URL delta changed the client slot incarnation");
        helper.assertTrue(!dispatch(player, server, action),
                "replayed Central Monitor image module action was accepted");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void realMachineRejectsImageUrlsThatCannotUseTheCompleteSaveCodec(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine unicodeMachine = new TestCentralMonitorMachine(false);
        unicodeMachine.setLevel(helper.getLevel());
        unicodeMachine.addMonitor(MONITOR_POSITION);
        UUID unicodeGroupIdentity = UUID.randomUUID();
        helper.assertTrue(unicodeMachine.createCentralMonitorGroup(
                0, unicodeGroupIdentity, Set.of(MONITOR_POSITION)),
                "server rejected the unicode save-budget test group");
        MonitorGroup unicodeGroup = unicodeMachine.getMonitorGroups().getFirst();
        unicodeGroup.getItemStackHandler().setStackInSlot(0, imageModule(null));
        unicodeMachine.getSyncDataHolder().serializeToComponents(helper.getLevel().registryAccess(), true, false);
        ItemStack unicodeBefore = unicodeGroup.getItemStackHandler().getStackInSlot(0).copy();
        UUID unicodeSlotIncarnation = unicodeGroup.getModuleSlotIncarnation();
        String unwritableUnicodeUrl = "界".repeat(22_000);
        SyncActionData unicodeAction = CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                unicodeMachine.getCentralMonitorActionIncarnation(),
                unicodeGroupIdentity,
                unicodeSlotIncarnation,
                unicodeBefore,
                unwritableUnicodeUrl,
                0);

        helper.assertTrue(!dispatch(player, unicodeMachine, unicodeAction),
                "Central Monitor accepted an image URL that exceeds modified-UTF save encoding");
        helper.assertTrue(ItemStack.matches(
                unicodeBefore, unicodeGroup.getItemStackHandler().getStackInSlot(0)),
                "rejected unicode URL changed the authoritative module stack");
        helper.assertTrue(unicodeGroup.getModuleSlotIncarnation().equals(unicodeSlotIncarnation),
                "save validation rotated the unicode module slot incarnation");
        helper.assertTrue(!unicodeMachine.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false)
                .isEmpty(), "rejected unicode URL did not request an authoritative group resync");

        TestCentralMonitorMachine aggregateMachine = new TestCentralMonitorMachine(false);
        aggregateMachine.setLevel(helper.getLevel());
        aggregateMachine.addMonitor(MONITOR_POSITION);
        aggregateMachine.addMonitor(SECOND_MONITOR_POSITION);
        UUID firstGroupIdentity = UUID.randomUUID();
        UUID secondGroupIdentity = UUID.randomUUID();
        helper.assertTrue(aggregateMachine.createCentralMonitorGroup(
                0, firstGroupIdentity, Set.of(MONITOR_POSITION)),
                "server rejected the first aggregate save-budget test group");
        helper.assertTrue(aggregateMachine.createCentralMonitorGroup(
                1, secondGroupIdentity, Set.of(SECOND_MONITOR_POSITION)),
                "server rejected the second aggregate save-budget test group");
        MonitorGroup firstGroup = aggregateMachine.getMonitorGroups().getFirst();
        MonitorGroup secondGroup = aggregateMachine.getMonitorGroups().getLast();
        firstGroup.getItemStackHandler().setStackInSlot(0, imageModule(null));
        secondGroup.getItemStackHandler().setStackInSlot(0, imageModule(null));
        aggregateMachine.getSyncDataHolder().serializeToComponents(helper.getLevel().registryAccess(), true, false);
        String boundaryUrl = "x".repeat(MAX_URL_LENGTH);

        SyncActionData firstBoundary = CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                aggregateMachine.getCentralMonitorActionIncarnation(),
                firstGroupIdentity,
                firstGroup.getModuleSlotIncarnation(),
                firstGroup.getItemStackHandler().getStackInSlot(0),
                boundaryUrl,
                0);
        helper.assertTrue(dispatch(player, aggregateMachine, firstBoundary),
                "Central Monitor rejected a network-boundary URL that still fits complete save data");
        aggregateMachine.getSyncDataHolder().serializeToComponents(
                helper.getLevel().registryAccess(), true, false);

        ItemStack secondBefore = secondGroup.getItemStackHandler().getStackInSlot(0).copy();
        UUID secondSlotIncarnation = secondGroup.getModuleSlotIncarnation();
        SyncActionData secondBoundary = CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                aggregateMachine.getCentralMonitorActionIncarnation(),
                secondGroupIdentity,
                secondSlotIncarnation,
                secondBefore,
                boundaryUrl,
                1);
        helper.assertTrue(!dispatch(player, aggregateMachine, secondBoundary),
                "Central Monitor accepted aggregate image URLs that exceed complete save encoding");
        helper.assertTrue(ItemStack.matches(
                secondBefore, secondGroup.getItemStackHandler().getStackInSlot(0)),
                "rejected aggregate URL changed the second module stack");
        helper.assertTrue(secondGroup.getModuleSlotIncarnation().equals(secondSlotIncarnation),
                "aggregate save validation rotated the second module slot incarnation");
        helper.assertTrue(boundaryUrl.equals(firstGroup.getItemStackHandler().getStackInSlot(0)
                .get(GTDataComponents.IMAGE_MODULE_URL.get())),
                "rejected aggregate URL changed the previously accepted module");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void rejectedCasResyncsUiResolverWithoutRedirectingReplacement(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine server = new TestCentralMonitorMachine(false);
        server.setLevel(helper.getLevel());
        server.addMonitor(MONITOR_POSITION);
        UUID groupIdentity = UUID.randomUUID();
        helper.assertTrue(server.createCentralMonitorGroup(0, groupIdentity, Set.of(MONITOR_POSITION)),
                "server rejected the image UI resync test group");
        MonitorGroup serverGroup = server.getMonitorGroups().getFirst();
        serverGroup.getItemStackHandler().setStackInSlot(0, imageModule("opening"));

        TestCentralMonitorMachine client = new TestCentralMonitorMachine(true);
        client.setLevel(helper.getLevel());
        DataComponentMap full = server.getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), full);
        server.getSyncDataHolder().serializeToComponents(helper.getLevel().registryAccess(), true, false);
        MonitorGroup openingGroup = client.getMonitorGroups().getFirst();
        UUID openingSlotIncarnation = openingGroup.getModuleSlotIncarnation();
        ItemStack openingStack = openingGroup.getItemStackHandler().getStackInSlot(0);
        SyncActionData staleClientAction = CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                server.getCentralMonitorActionIncarnation(),
                groupIdentity,
                openingSlotIncarnation,
                serverGroup.getItemStackHandler().getStackInSlot(0),
                "client-request",
                0);
        openingStack.set(GTDataComponents.IMAGE_MODULE_URL.get(), "client-request");

        SyncActionData authoritativeAction = CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                server.getCentralMonitorActionIncarnation(),
                groupIdentity,
                openingSlotIncarnation,
                serverGroup.getItemStackHandler().getStackInSlot(0),
                "authoritative",
                1);
        helper.assertTrue(dispatch(player, server, authoritativeAction),
                "server rejected the authoritative image UI resync update");
        server.getSyncDataHolder().serializeToComponents(helper.getLevel().registryAccess(), true, false);

        helper.assertTrue(!dispatch(player, server, staleClientAction),
                "server accepted an image UI CAS created before the authoritative update");
        DataComponentMap resync = server.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);
        helper.assertTrue(!resync.isEmpty(), "rejected image UI CAS did not publish authoritative groups");
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), resync);
        ItemStack resolved = client.resolveCentralMonitorImageModuleForOpening(
                groupIdentity, openingSlotIncarnation);
        helper.assertTrue(resolved != null && resolved != openingStack,
                "image UI resolver retained the stale opening stack after authoritative resync");
        helper.assertTrue("authoritative".equals(resolved.get(GTDataComponents.IMAGE_MODULE_URL.get())),
                "image UI resolver did not use the resynchronized module stack");

        serverGroup.getItemStackHandler().setStackInSlot(0, imageModule("replacement"));
        DataComponentMap replacement = server.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), replacement);
        helper.assertTrue(client.resolveCentralMonitorImageModuleForOpening(
                groupIdentity, openingSlotIncarnation) == null,
                "image UI resolver redirected an opening to a replacement slot occupant");
        helper.assertTrue("replacement".equals(client.getMonitorGroups().getFirst().getItemStackHandler()
                .getStackInSlot(0).get(GTDataComponents.IMAGE_MODULE_URL.get())),
                "replacement sync stored the wrong current image module");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void validUpdatesAcceptEmptyNonemptyAndCodecBoundary(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestImageModuleHost host = new TestImageModuleHost(imageModule(null));

        SyncActionData empty = action(host, host.snapshot(), "", 0);
        helper.assertTrue(dispatch(player, host, empty), "image module null-to-empty URL action was rejected");
        assertUrl(helper, host, "", "null-to-empty URL action");

        String nonemptyUrl = "https://example.invalid/monitor.png";
        SyncActionData nonempty = action(host, host.snapshot(), nonemptyUrl, 1);
        helper.assertTrue(dispatch(player, host, nonempty), "image module nonempty URL action was rejected");
        assertUrl(helper, host, nonemptyUrl, "nonempty URL action");

        String boundaryUrl = "x".repeat(MAX_URL_LENGTH);
        SyncActionData boundary = action(host, host.snapshot(), boundaryUrl, 2);
        helper.assertTrue(dispatch(player, host, boundary), "image module 32767-character URL action was rejected");
        assertUrl(helper, host, boundaryUrl, "32767-character URL action");
        helper.assertTrue(host.successfulWrites == 3, "valid image module actions did not execute exactly once each");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void oversizedUrlsAreRejectedByCreatorAndRawHandler(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestImageModuleHost host = new TestImageModuleHost(imageModule(null));
        String oversizedUrl = "x".repeat(MAX_URL_LENGTH + 1);

        boolean creatorRejected = false;
        try {
            action(host, host.snapshot(), oversizedUrl, 0);
        } catch (IllegalArgumentException expected) {
            creatorRejected = true;
        }
        helper.assertTrue(creatorRejected, "image module action creator accepted a 32768-character requested URL");

        SyncFieldData oversizedRequested = validFields(host, JsonNull.INSTANCE, new JsonPrimitive(oversizedUrl));
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(oversizedRequested, host.snapshot())),
                "image module handler accepted a 32768-character requested URL");

        ItemStack oversizedSnapshot = imageModule(oversizedUrl);
        SyncFieldData oversizedExpected = validFields(host, new JsonPrimitive(oversizedUrl),
                new JsonPrimitive("replacement"));
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(oversizedExpected, oversizedSnapshot)),
                "image module handler accepted a 32768-character expected URL");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void compareAndSetRejectsReplayAndConcurrentExpectedUrl(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestImageModuleHost host = new TestImageModuleHost(imageModule(null));
        ItemStack openingSnapshot = host.snapshot();
        SyncActionData first = action(host, openingSnapshot, "first", 0);
        SyncActionData concurrent = action(host, openingSnapshot, "concurrent", 1);

        helper.assertTrue(dispatch(player, host, first), "first image module URL CAS was rejected");
        assertUrl(helper, host, "first", "first image module URL CAS");
        assertRejectedWithoutMutation(helper, player, host, host, first,
                "replayed image module URL CAS was accepted");
        assertRejectedWithoutMutation(helper, player, host, host, concurrent,
                "concurrent image module URL CAS ignored the changed expected URL");
        assertUrl(helper, host, "first", "replayed and concurrent URL rejection");
        helper.assertTrue(host.resyncRequests == 2,
                "replayed and concurrent image module CAS rejections did not each request an authoritative resync");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void identitiesAndSpectatorAreValidatedBeforeMutation(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestImageModuleHost host = new TestImageModuleHost(imageModule(null));
        SyncActionData valid = action(host, host.snapshot(), "updated", 0);

        assertRejectedWithoutMutation(helper, player, new Object(), host, valid,
                "image module action accepted an unrelated holder");

        TestImageModuleHost replacement = new TestImageModuleHost(
                UUID.randomUUID(), host.groupIdentity, host.moduleSlotIncarnation, imageModule(null));
        assertRejectedWithoutMutation(helper, player, replacement, replacement, valid,
                "image module action accepted a replacement holder incarnation");

        SyncActionData wrongGroup = CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                host.holderIncarnation, UUID.randomUUID(), host.moduleSlotIncarnation,
                host.snapshot(), "wrong-group", 1);
        assertRejectedWithoutMutation(helper, player, host, host, wrongGroup,
                "image module action accepted an unknown group identity");

        SyncActionData wrongSlot = CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                host.holderIncarnation, host.groupIdentity, UUID.randomUUID(),
                host.snapshot(), "wrong-slot", 2);
        assertRejectedWithoutMutation(helper, player, host, host, wrongSlot,
                "image module action accepted a replaced module slot incarnation");

        player.setGameMode(GameType.SPECTATOR);
        try {
            assertRejectedWithoutMutation(helper, player, host, host, valid,
                    "image module action accepted a spectator");
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void snapshotCasRejectsMismatchButIgnoresDerivedText(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestImageModuleHost host = new TestImageModuleHost(imageModule(null));
        SyncFieldData validFields = validFields(host, JsonNull.INSTANCE, new JsonPrimitive("updated"));

        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(validFields, new ItemStack(Items.STONE))),
                "image module action accepted a non-image expected snapshot");

        ItemStack stackedOpeningModule = imageModule(null);
        stackedOpeningModule.setCount(2);
        TestImageModuleHost changedCountHost = new TestImageModuleHost(stackedOpeningModule);
        SyncActionData changedCountCas = action(changedCountHost, changedCountHost.snapshot(), "updated", 0);
        changedCountHost.module.setCount(1);
        assertRejectedWithoutMutation(helper, player, changedCountHost, changedCountHost, changedCountCas,
                "image module action ignored a changed module count after opening");

        SyncActionData stableComponentCas = action(host, host.snapshot(), "updated", 0);
        host.module.set(GTDataComponents.PLACEHOLDER_UUID.get(), UUID.randomUUID());
        assertRejectedWithoutMutation(helper, player, host, host, stableComponentCas,
                "image module action ignored a changed stable snapshot component");

        SyncFieldData mismatchedExpectedUrl = validFields(host, new JsonPrimitive("not-the-snapshot-url"),
                new JsonPrimitive("updated"));
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(mismatchedExpectedUrl, imageModule(null))),
                "image module action accepted an expected URL inconsistent with its snapshot");

        TestImageModuleHost derivedTextHost = new TestImageModuleHost(imageModule(null));
        SyncActionData derivedTextCas = action(derivedTextHost, derivedTextHost.snapshot(), "updated", 1);
        TextLineList derivedText = new TextLineList(List.of(Component.literal("volatile")), 2.0f);
        derivedTextHost.module.set(GTDataComponents.TEXT_LINE_LIST.get(), derivedText);
        helper.assertTrue(dispatch(player, derivedTextHost, derivedTextCas),
                "image module snapshot CAS rejected a TEXT_LINE_LIST-only change");
        assertUrl(helper, derivedTextHost, "updated", "TEXT_LINE_LIST-only snapshot CAS");
        helper.assertTrue(derivedText.equals(derivedTextHost.module.get(GTDataComponents.TEXT_LINE_LIST.get())),
                "image module URL action replaced the derived text component");

        ItemStack nonCanonicalSnapshot = imageModule(null);
        nonCanonicalSnapshot.set(GTDataComponents.TEXT_LINE_LIST.get(), derivedText);
        TestImageModuleHost nonCanonicalHost = new TestImageModuleHost(imageModule(null));
        assertRejectedWithoutMutation(helper, player, nonCanonicalHost, nonCanonicalHost,
                rawAction(payload(validFields(nonCanonicalHost, JsonNull.INSTANCE, new JsonPrimitive("updated")),
                        nonCanonicalSnapshot)),
                "image module action accepted a non-canonical snapshot carrying derived text");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void payloadRequiresExactComponentsAndTypedFields(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestImageModuleHost host = new TestImageModuleHost(imageModule(null));
        ItemStack snapshot = host.snapshot();
        SyncFieldData fields = validFields(host, JsonNull.INSTANCE, new JsonPrimitive("updated"));

        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get())),
                "image module action accepted a missing expected snapshot component");

        DataComponentMap snapshotOnly = DataComponentMap.builder()
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), snapshot)
                .build();
        assertRejectedWithoutMutation(helper, player, host, host, rawAction(snapshotOnly),
                "image module action accepted a missing field component");

        DataComponentMap extraComponent = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), snapshot)
                .set(GTDataComponents.ACTIVE.get(), true)
                .build();
        assertRejectedWithoutMutation(helper, player, host, host, rawAction(extraComponent),
                "image module action accepted an extra data component");

        SyncFieldData missingField = SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(host.holderIncarnation))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(host.groupIdentity))
                .put(MODULE_SLOT_INCARNATION_FIELD, encodeUuid(host.moduleSlotIncarnation))
                .put(EXPECTED_URL_FIELD, JsonNull.INSTANCE)
                .build();
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(missingField, snapshot)),
                "image module action accepted a missing requested URL field");

        SyncFieldData extraField = SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(host.holderIncarnation))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(host.groupIdentity))
                .put(MODULE_SLOT_INCARNATION_FIELD, encodeUuid(host.moduleSlotIncarnation))
                .put(EXPECTED_URL_FIELD, JsonNull.INSTANCE)
                .put(REQUESTED_URL_FIELD, new JsonPrimitive("updated"))
                .put(OTHER_FIELD, new JsonPrimitive(true))
                .build();
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(extraField, snapshot)),
                "image module action accepted an extra field");

        SyncFieldData malformedUuid = replaceField(fields, HOLDER_INCARNATION_FIELD,
                new JsonPrimitive("malformed"));
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(malformedUuid, snapshot)),
                "image module action accepted a malformed UUID");

        SyncFieldData typedExpectedUrl = replaceField(fields, EXPECTED_URL_FIELD, new JsonPrimitive(1));
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(typedExpectedUrl, snapshot)),
                "image module action accepted a non-string expected URL");

        SyncFieldData typedRequestedUrl = replaceField(fields, REQUESTED_URL_FIELD, new JsonPrimitive(false));
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(typedRequestedUrl, snapshot)),
                "image module action accepted a non-string requested URL");

        SyncActionData valid = action(host, snapshot, "updated", 0);
        SyncActionData negativeSequence = new SyncActionData(valid.actionId(), -1, valid.payload());
        assertRejectedWithoutMutation(helper, player, host, host, negativeSequence,
                "image module action accepted a negative sequence");
        helper.succeed();
    }

    private static SyncActionData action(TestImageModuleHost host, ItemStack expectedModule,
                                         String requestedUrl, int sequence) {
        return CentralMonitorImageModuleActions.createSetImageModuleUrlAction(
                host.holderIncarnation, host.groupIdentity, host.moduleSlotIncarnation,
                expectedModule, requestedUrl, sequence);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        CentralMonitorImageModuleActions.initialize();
        return SyncActionDispatchers.server().dispatch(
                new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null));
    }

    private static SyncActionData rawAction(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(SyncFieldData fields, ItemStack expectedModule) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), expectedModule)
                .build();
    }

    private static SyncFieldData validFields(TestImageModuleHost host, JsonElement expectedUrl,
                                             JsonElement requestedUrl) {
        return SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(host.holderIncarnation))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(host.groupIdentity))
                .put(MODULE_SLOT_INCARNATION_FIELD, encodeUuid(host.moduleSlotIncarnation))
                .put(EXPECTED_URL_FIELD, expectedUrl)
                .put(REQUESTED_URL_FIELD, requestedUrl)
                .build();
    }

    private static SyncFieldData replaceField(SyncFieldData fields, ResourceLocation replaced,
                                              JsonElement replacement) {
        SyncFieldData.Builder builder = SyncFieldData.builder();
        fields.fields().forEach((field, value) -> builder.put(field, field.equals(replaced) ? replacement : value));
        return builder.build();
    }

    private static JsonElement encodeUuid(UUID value) {
        return UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
    }

    private static ItemStack imageModule(@Nullable String url) {
        ItemStack module = GTItems.IMAGE_MODULE.get().getDefaultInstance();
        if (url != null) {
            module.set(GTDataComponents.IMAGE_MODULE_URL.get(), url);
        }
        return module;
    }

    private static void assertRejectedWithoutMutation(GameTestHelper helper, ServerPlayer player,
                                                      Object dispatchHolder, TestImageModuleHost observedHost,
                                                      SyncActionData action, String message) {
        ItemStack before = observedHost.module.copy();
        int writesBefore = observedHost.successfulWrites;
        helper.assertTrue(!dispatch(player, dispatchHolder, action), message);
        helper.assertTrue(ItemStack.matches(before, observedHost.module), message + " and changed the module stack");
        helper.assertTrue(observedHost.successfulWrites == writesBefore,
                message + " and invoked the module write path");
    }

    private static void assertUrl(GameTestHelper helper, TestImageModuleHost host,
                                  @Nullable String expected, String description) {
        String actual = host.module.get(GTDataComponents.IMAGE_MODULE_URL.get());
        boolean matches = expected == null ? actual == null : expected.equals(actual);
        helper.assertTrue(matches, description + " stored the wrong image module URL");
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static BlockEntityCreationInfo centralMonitorInfo() {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), BlockPos.ZERO,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private static final class TestCentralMonitorMachine extends CentralMonitorMachine {

        private final Map<BlockPos, IMonitorComponent> components = new HashMap<>();
        private final boolean remote;

        private TestCentralMonitorMachine(boolean remote) {
            super(centralMonitorInfo());
            this.remote = remote;
        }

        private void addMonitor(BlockPos position) {
            components.put(position, new TestMonitorComponent(position));
        }

        @Override
        public boolean isRemote() {
            return remote;
        }

        @Override
        protected boolean isMembershipStructureAvailable() {
            return true;
        }

        @Override
        public int getCentralMonitorMembershipCapacity() {
            return components.size();
        }

        @Override
        protected Map<BlockPos, IMonitorComponent> resolveMembershipComponents() {
            return new HashMap<>(components);
        }
    }

    private record TestMonitorComponent(BlockPos position) implements IMonitorComponent {

        @Override
        public IGuiTexture getComponentIcon() {
            return GuiTextures.BLANK_TRANSPARENT;
        }

        @Override
        public BlockPos getBlockPos() {
            return position;
        }
    }

    private static final class TestImageModuleHost implements CentralMonitorImageModuleActionHost {

        private final UUID holderIncarnation;
        private final UUID groupIdentity;
        private final UUID moduleSlotIncarnation;
        private final ItemStack module;
        private int successfulWrites;
        private int resyncRequests;

        private TestImageModuleHost(ItemStack module) {
            this(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), module);
        }

        private TestImageModuleHost(UUID holderIncarnation, UUID groupIdentity,
                                    UUID moduleSlotIncarnation, ItemStack module) {
            this.holderIncarnation = holderIncarnation;
            this.groupIdentity = groupIdentity;
            this.moduleSlotIncarnation = moduleSlotIncarnation;
            this.module = module.copy();
        }

        private ItemStack snapshot() {
            return CentralMonitorImageModuleActions.captureExpectedModule(module);
        }

        @Override
        public @NotNull UUID getCentralMonitorActionIncarnation() {
            return holderIncarnation;
        }

        @Override
        public void resyncCentralMonitorImageModuleState() {
            resyncRequests++;
        }

        @Override
        public boolean canSetCentralMonitorImageModuleUrl(@NotNull UUID groupIdentity,
                                                          @NotNull UUID moduleSlotIncarnation,
                                                          @NotNull ItemStack expectedModule,
                                                          @Nullable String expectedUrl,
                                                          @NotNull String requestedUrl) {
            if (!this.groupIdentity.equals(groupIdentity) ||
                    !this.moduleSlotIncarnation.equals(moduleSlotIncarnation) ||
                    !CentralMonitorImageModuleActions.isImageModule(module) ||
                    !CentralMonitorImageModuleActions.matchesExpectedModule(module, expectedModule)) {
                return false;
            }
            String currentUrl = module.get(GTDataComponents.IMAGE_MODULE_URL.get());
            boolean expectedMatches = expectedUrl == null ? currentUrl == null : expectedUrl.equals(currentUrl);
            return expectedMatches && !requestedUrl.equals(currentUrl);
        }

        @Override
        public boolean setCentralMonitorImageModuleUrl(@NotNull UUID groupIdentity,
                                                       @NotNull UUID moduleSlotIncarnation,
                                                       @NotNull ItemStack expectedModule,
                                                       @Nullable String expectedUrl,
                                                       @NotNull String requestedUrl) {
            if (!canSetCentralMonitorImageModuleUrl(
                    groupIdentity, moduleSlotIncarnation, expectedModule, expectedUrl, requestedUrl)) {
                return false;
            }
            module.set(GTDataComponents.IMAGE_MODULE_URL.get(), requestedUrl);
            successfulWrites++;
            return true;
        }
    }
}
