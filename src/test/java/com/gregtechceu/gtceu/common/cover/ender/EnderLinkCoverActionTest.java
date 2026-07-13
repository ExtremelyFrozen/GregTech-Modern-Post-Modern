package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualItemStorage;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class EnderLinkCoverActionTest {

    private static final ResourceLocation CONFIG_ACTION_ID = GTCEu.id("set_ender_link_cover_config");
    private static final ResourceLocation DESCRIPTION_ACTION_ID = GTCEu.id("set_ender_link_channel_description");
    private static final ResourceLocation REQUEST_ACTION_ID = GTCEu.id("request_ender_link_channels");
    private static final ResourceLocation CLEAR_ACTION_ID = GTCEu.id("clear_ender_link_channel_description");
    private static final ResourceLocation CHANNEL_COLOR_FIELD = SyncFieldData.key("channelColor");
    private static final ResourceLocation PERMISSION_FIELD = SyncFieldData.key("permission");
    private static final ResourceLocation IO_FIELD = SyncFieldData.key("io");
    private static final ResourceLocation MANUAL_IO_FIELD = SyncFieldData.key("manualIO");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");
    private static final ResourceLocation DESCRIPTION_FIELD = SyncFieldData.key("description");
    private static final ResourceLocation REQUEST_CHANNELS_FIELD = SyncFieldData.key("requestChannels");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EnderLinkCoverAction")
    public static void factoriesPreserveAllFourWireContracts(GameTestHelper helper) {
        SyncActionData config = EnderLinkCoverActions.createSetConfigAction(
                "aB", AbstractEnderLinkCover.Permissions.PRIVATE, IO.IN, ManualIOMode.FILTERED, false);
        SyncFieldData configFields = requireFields(config.payload(), "config factory");
        assertAction(helper, config, CONFIG_ACTION_ID, "config factory");
        helper.assertTrue(configFields.fields().size() == 5, "config factory encoded extra fields");
        assertStringField(helper, configFields, CHANNEL_COLOR_FIELD, "aB", "config color");
        assertIntField(helper, configFields, PERMISSION_FIELD,
                AbstractEnderLinkCover.Permissions.PRIVATE.ordinal(), "config permission");
        assertIntField(helper, configFields, IO_FIELD, IO.IN.ordinal(), "config IO");
        assertIntField(helper, configFields, MANUAL_IO_FIELD, ManualIOMode.FILTERED.ordinal(),
                "config manual IO");
        assertBooleanField(helper, configFields, WORKING_ENABLED_FIELD, false, "config working state");

        SyncActionData description = EnderLinkCoverActions.createSetDescriptionAction("scope/not-a-color", "");
        SyncFieldData descriptionFields = requireFields(description.payload(), "description factory");
        assertAction(helper, description, DESCRIPTION_ACTION_ID, "description factory");
        helper.assertTrue(descriptionFields.fields().size() == 2, "description factory encoded extra fields");
        assertStringField(helper, descriptionFields, CHANNEL_COLOR_FIELD, "scope/not-a-color",
                "description channel");
        assertStringField(helper, descriptionFields, DESCRIPTION_FIELD, "", "description value");

        SyncActionData request = EnderLinkCoverActions.createRequestChannelsAction();
        SyncFieldData requestFields = requireFields(request.payload(), "request factory");
        assertAction(helper, request, REQUEST_ACTION_ID, "request factory");
        helper.assertTrue(requestFields.fields().size() == 1, "request factory encoded extra fields");
        assertBooleanField(helper, requestFields, REQUEST_CHANNELS_FIELD, true, "request flag");

        SyncActionData clear = EnderLinkCoverActions.createClearDescriptionAction(" channel ");
        SyncFieldData clearFields = requireFields(clear.payload(), "clear factory");
        assertAction(helper, clear, CLEAR_ACTION_ID, "clear factory");
        helper.assertTrue(clearFields.fields().size() == 1, "clear factory encoded extra fields");
        assertStringField(helper, clearFields, CHANNEL_COLOR_FIELD, " channel ", "clear channel");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EnderLinkCoverAction")
    public static void configDispatcherCoversEveryStateAndColorBoundaryInOrder(GameTestHelper helper) {
        TrackingEnderLinkCover cover = createTrackingCover();
        String[] colors = { "", "a", "abcdef12" };
        String[] normalizedColors = { "FFFFFFFF", "AFFFFFFF", "ABCDEF12" };
        IO[] directions = { IO.IN, IO.OUT };

        for (int colorIndex = 0; colorIndex < colors.length; colorIndex++) {
            for (AbstractEnderLinkCover.Permissions permission : AbstractEnderLinkCover.Permissions.values()) {
                for (IO io : directions) {
                    for (ManualIOMode manualIOMode : ManualIOMode.VALUES) {
                        for (boolean workingEnabled : new boolean[] { false, true }) {
                            cover.resetActionTracking();
                            SyncActionData action = EnderLinkCoverActions.createSetConfigAction(
                                    colors[colorIndex], permission, io, manualIOMode, workingEnabled);

                            helper.assertTrue(dispatch(helper, cover, action),
                                    "config dispatcher rejected a valid Ender Link state");
                            cover.assertConfigState(helper, normalizedColors[colorIndex], permission, io,
                                    manualIOMode, workingEnabled);
                            helper.assertTrue(cover.getSetterOrder().equals(
                                    "channel>permission>io>manual>working>changed"),
                                    "config handler changed the required mutation order");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EnderLinkCoverAction")
    public static void configFactoryAndDispatcherRejectInvalidFieldsBeforeMutation(GameTestHelper helper) {
        assertFactoryRejected(() -> EnderLinkCoverActions.createSetConfigAction(
                "123456789", AbstractEnderLinkCover.Permissions.PUBLIC, IO.IN, ManualIOMode.DISABLED, true),
                "config factory accepted a nine-digit color");
        assertFactoryRejected(() -> EnderLinkCoverActions.createSetConfigAction(
                "GG", AbstractEnderLinkCover.Permissions.PUBLIC, IO.IN, ManualIOMode.DISABLED, true),
                "config factory accepted a non-hex color");
        assertFactoryRejected(() -> EnderLinkCoverActions.createSetConfigAction(
                "FF", AbstractEnderLinkCover.Permissions.PUBLIC, IO.BOTH, ManualIOMode.DISABLED, true),
                "config factory accepted bidirectional IO");
        assertFactoryRejected(() -> EnderLinkCoverActions.createSetConfigAction(
                "FF", AbstractEnderLinkCover.Permissions.PUBLIC, IO.NONE, ManualIOMode.DISABLED, true),
                "config factory accepted disabled IO");

        TrackingEnderLinkCover cover = createTrackingCover();
        assertConfigRejected(helper, cover,
                configAction(configPayload(null, integer(0), integer(0), integer(0), bool(true))),
                "missing color");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), null, integer(0), integer(0), bool(true))),
                "missing permission");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), null, integer(0), bool(true))),
                "missing IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(0), null, bool(true))),
                "missing manual IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(0), integer(0), null)),
                "missing working state");
        assertConfigRejected(helper, cover, configAction(DataComponentMap.EMPTY), "missing field data");

        assertConfigRejected(helper, cover,
                configAction(configPayload(integer(255), integer(0), integer(0), integer(0), bool(true))),
                "numeric color");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("GG"), integer(0), integer(0), integer(0), bool(true))),
                "non-hex color");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), string("0"), integer(0), integer(0), bool(true))),
                "string permission");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), new JsonPrimitive(0.5D), integer(0), integer(0),
                        bool(true))),
                "fractional permission");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), new JsonPrimitive(4_294_967_296L), integer(0),
                        integer(0), bool(true))),
                "overflowing permission");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(-1), integer(0), integer(0), bool(true))),
                "negative permission");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"),
                        integer(AbstractEnderLinkCover.Permissions.values().length), integer(0), integer(0),
                        bool(true))),
                "out-of-range permission");

        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(IO.BOTH.ordinal()), integer(0),
                        bool(true))),
                "bidirectional IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(IO.NONE.ordinal()), integer(0),
                        bool(true))),
                "disabled IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), string("0"), integer(0), bool(true))),
                "string IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), new JsonPrimitive(0.5D), integer(0),
                        bool(true))),
                "fractional IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), new JsonPrimitive(4_294_967_296L),
                        integer(0), bool(true))),
                "overflowing IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(-1), integer(0), bool(true))),
                "negative IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(0),
                        integer(ManualIOMode.VALUES.length), bool(true))),
                "out-of-range manual IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(0), string("0"), bool(true))),
                "string manual IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(0), new JsonPrimitive(0.5D),
                        bool(true))),
                "fractional manual IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(0),
                        new JsonPrimitive(4_294_967_296L), bool(true))),
                "overflowing manual IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(0), integer(-1), bool(true))),
                "negative manual IO");
        assertConfigRejected(helper, cover,
                configAction(configPayload(string("FF"), integer(0), integer(0), integer(0), integer(1))),
                "numeric working state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EnderLinkCoverAction")
    public static void descriptionUsesTargetScopedRegistryWithoutExtraPlayerOwnershipGate(GameTestHelper helper) {
        TrackingEnderLinkCover cover = createTrackingCover();
        String scopedChannel = "private/owner/" + "channel".repeat(512);
        VirtualEntry ownedEntry = cover.addChannel(scopedChannel, "01020304", "old");
        String longDescription = "description".repeat(512);

        helper.assertTrue(dispatch(helper, cover,
                EnderLinkCoverActions.createSetDescriptionAction(scopedChannel, longDescription)),
                "description dispatcher rejected an owner-scoped channel for a survival player");
        helper.assertTrue(ownedEntry.getDescription().equals(longDescription),
                "description dispatcher truncated or changed a long description");
        helper.assertTrue(cover.getChangedCalls() == 1, "description mutation did not mark the UI changed");

        cover.resetActionTracking();
        helper.assertTrue(dispatch(helper, cover,
                EnderLinkCoverActions.createSetDescriptionAction(scopedChannel, "")),
                "description dispatcher rejected an empty description");
        helper.assertTrue(ownedEntry.getDescription().isEmpty(), "empty description was not preserved");

        cover.resetActionTracking();
        helper.assertTrue(!dispatch(helper, cover,
                EnderLinkCoverActions.createSetDescriptionAction("other-owner/channel", "forbidden")),
                "description dispatcher escaped the target's owner-scoped registry");
        helper.assertTrue(ownedEntry.getDescription().isEmpty(),
                "missing owner-scoped channel changed an existing entry");
        helper.assertTrue(cover.getChangedCalls() == 0, "failed description lookup marked the UI changed");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EnderLinkCoverAction")
    public static void channelRequestSortsScopedEntriesAndRequiresCoverLocation(GameTestHelper helper) {
        TrackingEnderLinkCover cover = createTrackingCover();

        helper.assertTrue(dispatch(helper, cover, EnderLinkCoverActions.createRequestChannelsAction()),
                "channel request rejected an empty owner-scoped list");
        helper.assertTrue(cover.getSendCalls() == 1 && cover.getSentEntries().isEmpty(),
                "empty channel request did not preserve the empty list");

        cover.addChannel("second", "FFFFFFFF", "second");
        cover.addChannel("first", "00000000", "first");
        cover.resetActionTracking();

        helper.assertTrue(dispatch(helper, cover, EnderLinkCoverActions.createRequestChannelsAction()),
                "channel request rejected a valid cover context");
        helper.assertTrue(cover.getSendCalls() == 1, "channel request did not send one snapshot");
        helper.assertTrue(cover.getSentEntries().stream().map(VirtualEntry::getColorStr).toList()
                .equals(List.of("00000000", "FFFFFFFF")),
                "channel request did not sort entries by color string");
        helper.assertTrue(BlockPos.ZERO.equals(cover.getSentPos()) && cover.getSentSide() == Direction.WEST,
                "channel request changed its target position or side");

        cover.resetActionTracking();
        helper.assertTrue(!dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), cover,
                EnderLinkCoverActions.createRequestChannelsAction(), null, Direction.WEST),
                "channel request accepted a missing cover position");
        helper.assertTrue(cover.getSendCalls() == 0, "missing-position request sent a channel snapshot");

        cover.resetActionTracking();
        helper.assertTrue(!dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), cover,
                EnderLinkCoverActions.createRequestChannelsAction(), BlockPos.ZERO, null),
                "channel request accepted a missing cover side");
        helper.assertTrue(cover.getSendCalls() == 0, "missing-side request sent a channel snapshot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EnderLinkCoverAction")
    public static void clearDescriptionMutatesThenRefreshesOnlyValidScopedChannel(GameTestHelper helper) {
        TrackingEnderLinkCover cover = createTrackingCover();
        VirtualEntry entry = cover.addChannel("scope/not-a-color", "ABCDEF12", "remove me");

        helper.assertTrue(dispatch(helper, cover,
                EnderLinkCoverActions.createClearDescriptionAction("scope/not-a-color")),
                "clear dispatcher rejected a non-color registry key");
        helper.assertTrue(entry.getDescription().isEmpty(), "clear dispatcher did not clear the description");
        helper.assertTrue(cover.getSendCalls() == 1, "clear dispatcher did not refresh the channel list");
        helper.assertTrue(cover.getSentDescriptions().equals(List.of("")),
                "clear dispatcher refreshed the channel list before clearing its description");
        helper.assertTrue(cover.getChangedCalls() == 0, "clear dispatcher added an unrequested UI dirty mutation");

        VirtualEntry nonBreakingSpaceEntry = cover.addChannel("\u00A0", "01010101", "clear me too");
        cover.resetActionTracking();
        helper.assertTrue(dispatch(helper, cover,
                EnderLinkCoverActions.createClearDescriptionAction("\u00A0")),
                "clear dispatcher changed Java String.isBlank semantics for non-breaking space");
        helper.assertTrue(nonBreakingSpaceEntry.getDescription().isEmpty(),
                "non-breaking-space channel description was not cleared");

        entry.setDescription("keep after missing position");
        cover.resetActionTracking();
        helper.assertTrue(!dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), cover,
                EnderLinkCoverActions.createClearDescriptionAction("scope/not-a-color"), null, Direction.WEST),
                "clear dispatcher accepted a missing cover position");
        helper.assertTrue(entry.getDescription().equals("keep after missing position") && cover.getSendCalls() == 0,
                "missing-position clear mutated or refreshed the channel");

        entry.setDescription("keep after missing side");
        cover.resetActionTracking();
        helper.assertTrue(!dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), cover,
                EnderLinkCoverActions.createClearDescriptionAction("scope/not-a-color"), BlockPos.ZERO, null),
                "clear dispatcher accepted a missing cover side");
        helper.assertTrue(entry.getDescription().equals("keep after missing side") && cover.getSendCalls() == 0,
                "missing-side clear mutated or refreshed the channel");

        entry.setDescription("restored");
        cover.resetActionTracking();
        helper.assertTrue(!dispatch(helper, cover,
                EnderLinkCoverActions.createClearDescriptionAction("other-owner/channel")),
                "clear dispatcher escaped the target's owner-scoped registry");
        helper.assertTrue(entry.getDescription().equals("restored"),
                "missing clear target changed an existing description");
        helper.assertTrue(cover.getSendCalls() == 0, "failed clear lookup sent a channel snapshot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EnderLinkCoverAction")
    public static void nonConfigPayloadValidatorsRejectBeforeMutation(GameTestHelper helper) {
        TrackingEnderLinkCover cover = createTrackingCover();
        VirtualEntry entry = cover.addChannel("channel", "FFFFFFFF", "unchanged");

        assertNoActionMutation(helper, cover,
                action(DESCRIPTION_ACTION_ID, descriptionPayload(null, string("description"))),
                "missing description channel");
        assertNoActionMutation(helper, cover,
                action(DESCRIPTION_ACTION_ID, descriptionPayload(string("channel"), null)),
                "missing description");
        assertNoActionMutation(helper, cover,
                action(DESCRIPTION_ACTION_ID, descriptionPayload(integer(1), string("description"))),
                "numeric description channel");
        assertNoActionMutation(helper, cover,
                action(DESCRIPTION_ACTION_ID, descriptionPayload(string("channel"), bool(true))),
                "boolean description");

        assertNoActionMutation(helper, cover, action(REQUEST_ACTION_ID, requestPayload(null)),
                "missing request flag");
        assertNoActionMutation(helper, cover, action(REQUEST_ACTION_ID, requestPayload(bool(false))),
                "false request flag");
        assertNoActionMutation(helper, cover, action(REQUEST_ACTION_ID, requestPayload(string("true"))),
                "string request flag");

        assertNoActionMutation(helper, cover, action(CLEAR_ACTION_ID, clearPayload(null)),
                "missing clear channel");
        assertNoActionMutation(helper, cover, action(CLEAR_ACTION_ID, clearPayload(integer(1))),
                "numeric clear channel");
        assertNoActionMutation(helper, cover, action(CLEAR_ACTION_ID, clearPayload(string(""))),
                "empty clear channel");
        assertNoActionMutation(helper, cover, action(CLEAR_ACTION_ID, clearPayload(string(" \t"))),
                "blank clear channel");

        helper.assertTrue(entry.getDescription().equals("unchanged"),
                "invalid non-config payload changed the registry entry");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EnderLinkCoverAction")
    public static void everyHandlerAcceptsUnknownFields(GameTestHelper helper) {
        TrackingEnderLinkCover cover = createTrackingCover();
        VirtualEntry descriptionEntry = cover.addChannel("description", "11111111", "old");
        VirtualEntry clearEntry = cover.addChannel("clear", "22222222", "old");

        helper.assertTrue(dispatch(helper, cover, action(CONFIG_ACTION_ID, configPayloadWithUnknownField())),
                "config action rejected an unknown field");
        cover.resetActionTracking();
        helper.assertTrue(dispatch(helper, cover,
                action(DESCRIPTION_ACTION_ID, descriptionPayloadWithUnknownField())),
                "description action rejected an unknown field");
        helper.assertTrue(descriptionEntry.getDescription().equals("new"),
                "description action with unknown field changed the wrong value");

        cover.resetActionTracking();
        helper.assertTrue(dispatch(helper, cover, action(REQUEST_ACTION_ID, requestPayloadWithUnknownField())),
                "request action rejected an unknown field");
        helper.assertTrue(cover.getSendCalls() == 1, "request with unknown field did not send a snapshot");

        cover.resetActionTracking();
        helper.assertTrue(dispatch(helper, cover, action(CLEAR_ACTION_ID, clearPayloadWithUnknownField())),
                "clear action rejected an unknown field");
        helper.assertTrue(clearEntry.getDescription().isEmpty(),
                "clear action with unknown field did not clear its channel");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EnderLinkCoverAction")
    public static void everyHandlerRejectsWrongHolderAndSpectatorWithoutMutation(GameTestHelper helper) {
        List<SyncActionData> actions = List.of(
                EnderLinkCoverActions.createSetConfigAction("FF", AbstractEnderLinkCover.Permissions.PRIVATE,
                        IO.IN, ManualIOMode.FILTERED, false),
                EnderLinkCoverActions.createSetDescriptionAction("channel", "new"),
                EnderLinkCoverActions.createRequestChannelsAction(),
                EnderLinkCoverActions.createClearDescriptionAction("channel"));
        FakeActionTargetImpl fakeTarget = new FakeActionTargetImpl();
        for (SyncActionData action : actions) {
            helper.assertTrue(!dispatch(helper, fakeTarget, action),
                    "handler accepted an interface-only holder for " + action.actionId());
            helper.assertTrue(!dispatch(helper, new Object(), action),
                    "handler accepted an unrelated holder for " + action.actionId());
        }
        helper.assertTrue(fakeTarget.getCalls() == 0, "wrong holder invoked an Ender Link target method");

        TrackingEnderLinkCover cover = createTrackingCover();
        VirtualEntry entry = cover.addChannel("channel", "FFFFFFFF", "old");
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        try {
            for (SyncActionData action : actions) {
                helper.assertTrue(!dispatch(player, cover, action, BlockPos.ZERO, Direction.WEST),
                        "handler accepted a spectator for " + action.actionId());
            }
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }
        helper.assertTrue(cover.getSetterOrder().isEmpty() && cover.getChangedCalls() == 0 &&
                cover.getSendCalls() == 0 && entry.getDescription().equals("old"),
                "spectator action mutated Ender Link state");
        helper.succeed();
    }

    private static void assertConfigRejected(GameTestHelper helper, TrackingEnderLinkCover cover,
                                             SyncActionData action, String description) {
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), description + " payload invoked a config mutation");
    }

    private static void assertNoActionMutation(GameTestHelper helper, TrackingEnderLinkCover cover,
                                               SyncActionData action, String description) {
        int changedCalls = cover.getChangedCalls();
        int sendCalls = cover.getSendCalls();
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        helper.assertTrue(cover.getChangedCalls() == changedCalls && cover.getSendCalls() == sendCalls,
                description + " payload invoked an action side effect");
    }

    private static void assertFactoryRejected(Runnable factoryCall, String failureMessage) {
        try {
            factoryCall.run();
        } catch (IllegalArgumentException exception) {
            return;
        }
        throw new GameTestAssertException(failureMessage);
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action,
                BlockPos.ZERO, Direction.WEST);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action,
                                    @Nullable BlockPos pos, @Nullable Direction side) {
        EnderLinkCoverActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, pos, side, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData configAction(DataComponentMap payload) {
        return action(CONFIG_ACTION_ID, payload);
    }

    private static SyncActionData action(ResourceLocation actionId, DataComponentMap payload) {
        return new SyncActionData(actionId, 0, payload);
    }

    private static DataComponentMap configPayload(JsonElement channelColor, JsonElement permission, JsonElement io,
                                                  JsonElement manualIO, JsonElement workingEnabled) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        put(fields, CHANNEL_COLOR_FIELD, channelColor);
        put(fields, PERMISSION_FIELD, permission);
        put(fields, IO_FIELD, io);
        put(fields, MANUAL_IO_FIELD, manualIO);
        put(fields, WORKING_ENABLED_FIELD, workingEnabled);
        return fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap descriptionPayload(JsonElement channelName, JsonElement description) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        put(fields, CHANNEL_COLOR_FIELD, channelName);
        put(fields, DESCRIPTION_FIELD, description);
        return fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap requestPayload(JsonElement request) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        put(fields, REQUEST_CHANNELS_FIELD, request);
        return fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap clearPayload(JsonElement channelName) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        put(fields, CHANNEL_COLOR_FIELD, channelName);
        return fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static void put(SyncFieldData.Builder fields, ResourceLocation field, @Nullable JsonElement value) {
        if (value != null) {
            fields.put(field, value);
        }
    }

    private static DataComponentMap configPayloadWithUnknownField() {
        return SyncFieldData.builder()
                .put(CHANNEL_COLOR_FIELD, string("12"))
                .put(PERMISSION_FIELD, integer(AbstractEnderLinkCover.Permissions.PUBLIC.ordinal()))
                .put(IO_FIELD, integer(IO.OUT.ordinal()))
                .put(MANUAL_IO_FIELD, integer(ManualIOMode.UNFILTERED.ordinal()))
                .put(WORKING_ENABLED_FIELD, bool(true))
                .put(UNKNOWN_FIELD, string("ignored"))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap descriptionPayloadWithUnknownField() {
        return SyncFieldData.builder()
                .put(CHANNEL_COLOR_FIELD, string("description"))
                .put(DESCRIPTION_FIELD, string("new"))
                .put(UNKNOWN_FIELD, string("ignored"))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap requestPayloadWithUnknownField() {
        return SyncFieldData.builder()
                .put(REQUEST_CHANNELS_FIELD, bool(true))
                .put(UNKNOWN_FIELD, string("ignored"))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap clearPayloadWithUnknownField() {
        return SyncFieldData.builder()
                .put(CHANNEL_COLOR_FIELD, string("clear"))
                .put(UNKNOWN_FIELD, string("ignored"))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static JsonPrimitive string(String value) {
        return new JsonPrimitive(value);
    }

    private static JsonPrimitive integer(int value) {
        return new JsonPrimitive(value);
    }

    private static JsonPrimitive bool(boolean value) {
        return new JsonPrimitive(value);
    }

    private static SyncFieldData requireFields(DataComponentMap payload, String description) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new GameTestAssertException(description + " omitted sync field data");
        }
        return fields;
    }

    private static void assertAction(GameTestHelper helper, SyncActionData action, ResourceLocation expectedId,
                                     String description) {
        helper.assertTrue(action.actionId().equals(expectedId), description + " encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, description + " changed the action sequence");
    }

    private static void assertStringField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                          String expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isString() &&
                primitive.getAsString().equals(expected), description + " was not encoded as the expected string");
    }

    private static void assertIntField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                       int expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsBigDecimal().intValueExact() == expected,
                description + " was not encoded as the expected integer");
    }

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                           boolean expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " was not encoded as the expected boolean");
    }

    private static TrackingEnderLinkCover createTrackingCover() {
        BufferMachine machine = createBuffer();
        return new TrackingEnderLinkCover(
                GTCovers.ENDER_ITEM_LINK, machine.getCoverContainer(), Direction.WEST);
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof BufferMachine buffer) {
            return buffer;
        }
        throw new IllegalStateException("LV buffer definition did not create a BufferMachine.");
    }

    private static final class TrackingEnderLinkCover extends AbstractEnderLinkCover<VirtualItemStorage> {

        private final Map<String, VirtualEntry> actionChannels = new LinkedHashMap<>();
        private final List<VirtualEntry> sentEntries = new ArrayList<>();
        private final List<String> sentDescriptions = new ArrayList<>();
        private final StringBuilder setterOrder = new StringBuilder();
        private VirtualItemStorage entry = EntryTypes.ENDER_ITEM.createInstance();
        private String appliedChannel = VirtualEntry.DEFAULT_COLOR;
        private Permissions appliedPermission = Permissions.PUBLIC;
        private IO appliedIo = IO.OUT;
        private ManualIOMode appliedManualIO = ManualIOMode.DISABLED;
        private boolean appliedWorkingEnabled = true;
        private int changedCalls;
        private int sendCalls;
        private @Nullable BlockPos sentPos;
        private @Nullable Direction sentSide;

        private TrackingEnderLinkCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
            super(definition, coverHolder, attachedSide);
        }

        @Override
        public boolean canAttach() {
            return true;
        }

        @Override
        protected String identifier() {
            return "Test#";
        }

        @Override
        protected VirtualItemStorage getEntry() {
            return entry;
        }

        @Override
        protected void setEntry(VirtualEntry entry) {
            this.entry = (VirtualItemStorage) entry;
        }

        @Override
        protected EntryTypes<VirtualItemStorage> getEntryType() {
            return EntryTypes.ENDER_ITEM;
        }

        @Override
        protected void transfer() {}

        @Override
        protected UIElement addVirtualEntryLDLib2Element(VirtualEntry entry, int x, int y, int width, int height,
                                                         boolean canClick) {
            return new UIElement();
        }

        @Override
        protected String getUITitle() {
            return "test.ender_link";
        }

        @Override
        public void setChannelName(@NotNull String channelColor) {
            appendSetter("channel");
            appliedChannel = channelColor;
        }

        @Override
        public void setPermission(@NotNull Permissions permission) {
            appendSetter("permission");
            appliedPermission = permission;
        }

        @Override
        public void setIo(@NotNull IO io) {
            appendSetter("io");
            appliedIo = io;
        }

        @Override
        public void setManualIOMode(@NotNull ManualIOMode manualIOMode) {
            appendSetter("manual");
            appliedManualIO = manualIOMode;
        }

        @Override
        public void setWorkingEnabled(boolean workingEnabled) {
            appendSetter("working");
            appliedWorkingEnabled = workingEnabled;
        }

        @Override
        public void markEnderLinkUIChanged() {
            appendSetter("changed");
            changedCalls++;
        }

        @Override
        public @NotNull List<String> getEnderLinkActionChannelNames() {
            return List.copyOf(actionChannels.keySet());
        }

        @Nullable
        @Override
        public VirtualEntry findEnderLinkActionChannel(@NotNull String channelName) {
            return actionChannels.get(channelName);
        }

        @Override
        public void sendEnderLinkActionChannelList(@NotNull ServerPlayer player, @NotNull BlockPos pos,
                                                   @NotNull Direction side,
                                                   @NotNull List<? extends VirtualEntry> entries) {
            sendCalls++;
            sentPos = pos;
            sentSide = side;
            sentEntries.clear();
            sentEntries.addAll(entries);
            sentDescriptions.clear();
            entries.stream().map(VirtualEntry::getDescription).forEach(sentDescriptions::add);
        }

        private VirtualEntry addChannel(String channelName, String color, String description) {
            VirtualEntry channel = EntryTypes.ENDER_ITEM.createInstance();
            channel.setColor(color);
            channel.setDescription(description);
            actionChannels.put(channelName, channel);
            return channel;
        }

        private void appendSetter(String setter) {
            if (!setterOrder.isEmpty()) {
                setterOrder.append('>');
            }
            setterOrder.append(setter);
        }

        private void resetActionTracking() {
            setterOrder.setLength(0);
            changedCalls = 0;
            sendCalls = 0;
            sentEntries.clear();
            sentDescriptions.clear();
            sentPos = null;
            sentSide = null;
        }

        private void assertConfigState(GameTestHelper helper, String channel, Permissions permission, IO io,
                                       ManualIOMode manualIOMode, boolean workingEnabled) {
            helper.assertTrue(appliedChannel.equals(channel) && appliedPermission == permission && appliedIo == io &&
                    appliedManualIO == manualIOMode && appliedWorkingEnabled == workingEnabled,
                    "config handler applied an unexpected Ender Link state");
        }

        private String getSetterOrder() {
            return setterOrder.toString();
        }

        private int getChangedCalls() {
            return changedCalls;
        }

        private int getSendCalls() {
            return sendCalls;
        }

        private List<VirtualEntry> getSentEntries() {
            return sentEntries;
        }

        private List<String> getSentDescriptions() {
            return sentDescriptions;
        }

        private @Nullable BlockPos getSentPos() {
            return sentPos;
        }

        private @Nullable Direction getSentSide() {
            return sentSide;
        }
    }

    private static final class FakeActionTargetImpl implements EnderLinkCoverActionTarget {

        private int calls;

        @Override
        public void setChannelName(@NotNull String channelColor) {
            calls++;
        }

        @Override
        public void setPermission(@NotNull AbstractEnderLinkCover.Permissions permission) {
            calls++;
        }

        @Override
        public void setIo(@NotNull IO io) {
            calls++;
        }

        @Override
        public void setManualIOMode(@NotNull ManualIOMode manualIOMode) {
            calls++;
        }

        @Override
        public void setWorkingEnabled(boolean workingEnabled) {
            calls++;
        }

        @Override
        public void markEnderLinkUIChanged() {
            calls++;
        }

        @Override
        public @NotNull List<String> getEnderLinkActionChannelNames() {
            calls++;
            return List.of();
        }

        @Nullable
        @Override
        public VirtualEntry findEnderLinkActionChannel(@NotNull String channelName) {
            calls++;
            return null;
        }

        @Override
        public void setEnderLinkActionChannelDescription(@NotNull VirtualEntry entry,
                                                         @NotNull String description) {
            calls++;
        }

        @Override
        public @NotNull String getEnderLinkActionChannelColor(@NotNull VirtualEntry entry) {
            calls++;
            return entry.getColorStr();
        }

        @Override
        public void sendEnderLinkActionChannelList(@NotNull ServerPlayer player, @NotNull BlockPos pos,
                                                   @NotNull Direction side,
                                                   @NotNull List<? extends VirtualEntry> entries) {
            calls++;
        }

        private int getCalls() {
            return calls;
        }
    }
}
