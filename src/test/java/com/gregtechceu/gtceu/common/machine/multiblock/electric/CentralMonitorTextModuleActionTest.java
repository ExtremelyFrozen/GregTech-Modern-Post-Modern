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

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CentralMonitorTextModuleActionTest {

    private static final String BATCH = "CentralMonitorTextModuleAction";
    private static final Gson GSON = new Gson();
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_central_monitor_text_module_configuration");
    private static final ResourceLocation HOLDER_INCARNATION_FIELD = SyncFieldData.key("holder_incarnation");
    private static final ResourceLocation GROUP_IDENTITY_FIELD = SyncFieldData.key("group_identity");
    private static final ResourceLocation MODULE_SLOT_INCARNATION_FIELD = SyncFieldData.key("module_slot_incarnation");
    private static final ResourceLocation EXPECTED_CONFIGURATION_REVISION_FIELD = SyncFieldData
            .key("expected_configuration_revision");
    private static final ResourceLocation REQUESTED_CONFIGURATION_FIELD = SyncFieldData.key("requested_configuration");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");
    private static final ResourceLocation CENTRAL_MONITOR_ACTION_INCARNATION_FIELD = SyncFieldData
            .key("centralMonitorActionIncarnation");
    private static final BlockPos MONITOR_POSITION = new BlockPos(1, 0, 0);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void centralMonitorAppliesCanonicalConfigurationInPlace(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine server = preparedMachine(helper);
        MonitorGroup group = server.getMonitorGroups().getFirst();
        ItemStack module = textModule("original", 1.0f);
        module.setCount(2);
        UUID initialPlaceholder = UUID.randomUUID();
        module.set(GTDataComponents.PLACEHOLDER_UUID.get(), initialPlaceholder);
        TextLineList initialDerived = configuration("initial-derived", 1.5f);
        module.set(GTDataComponents.TEXT_LINE_LIST.get(), initialDerived);
        module.set(DataComponents.CUSTOM_NAME, Component.literal("server-name"));
        group.getItemStackHandler().setStackInSlot(0, module);
        ItemStack currentModule = group.getItemStackHandler().getStackInSlot(0);
        UUID slotIncarnation = group.getModuleSlotIncarnation();
        server.getSyncDataHolder().serializeToComponents(helper.getLevel().registryAccess(), true, false);

        TextLineList styledRequest = new TextLineList(
                List.of(Component.literal("updated <energy>").withStyle(ChatFormatting.RED)), 2.5f);
        SyncActionData action = CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                server.getCentralMonitorActionIncarnation(),
                group.getIdentity(),
                slotIncarnation,
                group.getTextConfigurationRevision(),
                currentModule,
                styledRequest,
                0);

        UUID latestPlaceholder = UUID.randomUUID();
        TextLineList latestDerived = configuration("latest-derived", 3.0f);
        currentModule.set(GTDataComponents.PLACEHOLDER_UUID.get(), latestPlaceholder);
        currentModule.set(GTDataComponents.TEXT_LINE_LIST.get(), latestDerived);

        helper.assertTrue(dispatch(player, server, action),
                "Central Monitor rejected a valid text module configuration action");
        ItemStack configuredModule = group.getItemStackHandler().getStackInSlot(0);
        helper.assertTrue(configuredModule == currentModule,
                "text module configuration action replaced the physical ItemStack instance");
        helper.assertTrue(group.getModuleSlotIncarnation().equals(slotIncarnation),
                "in-place text module configuration rotated the physical slot incarnation");
        helper.assertTrue(configuredModule.getCount() == 2,
                "text module configuration changed the module count");
        helper.assertTrue(latestPlaceholder.equals(configuredModule.get(GTDataComponents.PLACEHOLDER_UUID.get())),
                "text module configuration replaced the latest server placeholder UUID");
        helper.assertTrue(latestDerived.equals(configuredModule.get(GTDataComponents.TEXT_LINE_LIST.get())),
                "text module configuration replaced the latest derived server text");
        helper.assertTrue(Component.literal("server-name").equals(configuredModule.get(DataComponents.CUSTOM_NAME)),
                "text module configuration changed an unrelated stable component");
        helper.assertTrue(configuration("updated <energy>", 2.5f).equals(
                configuredModule.get(GTDataComponents.FORMAT_STRING_LIST.get())),
                "server did not normalize editable text to plain literal lines");
        helper.assertTrue(group.getTextConfigurationRevision() == 1,
                "text module configuration did not increment its revision exactly once");
        helper.assertTrue(!server.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false)
                .isEmpty(), "text module configuration did not mark monitorGroups dirty");
        helper.assertTrue(!dispatch(player, server, action),
                "replayed text module configuration action was accepted");
        helper.assertTrue(group.getTextConfigurationRevision() == 1,
                "replayed text module configuration changed its revision");
        helper.assertTrue(!server.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false)
                .isEmpty(), "replayed text module CAS did not request an authoritative resync");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void configurationPreflightPreservesPendingClientSyncState(GameTestHelper helper) {
        TestCentralMonitorMachine server = preparedMachine(helper);
        MonitorGroup group = server.getMonitorGroups().getFirst();
        ItemStack module = textModule("opening", 1.0f);
        group.getItemStackHandler().setStackInSlot(0, module);
        server.getSyncDataHolder().serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        server.getSyncDataHolder().markClientSyncFieldDirty("centralMonitorActionIncarnation");

        helper.assertTrue(server.canSetCentralMonitorTextModuleConfiguration(
                group.getIdentity(), group.getModuleSlotIncarnation(), group.getTextConfigurationRevision(),
                module, configuration("updated", 2.0f)),
                "Central Monitor preflight rejected a valid text configuration");

        DataComponentMap pendingComponents = server.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);
        SyncFieldData pendingFields = pendingComponents.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(pendingFields != null && pendingFields.contains(CENTRAL_MONITOR_ACTION_INCARNATION_FIELD),
                "Central Monitor text preflight consumed an unrelated pending client synchronization field");
        ItemStack retainedModule = group.getItemStackHandler().getStackInSlot(0);
        helper.assertTrue(retainedModule == module && configuration("opening", 1.0f).equals(
                retainedModule.get(GTDataComponents.FORMAT_STRING_LIST.get())) &&
                group.getTextConfigurationRevision() == 0,
                "Central Monitor text preflight changed authoritative state");

        group.setTextConfigurationRevision(Long.MAX_VALUE);
        helper.assertTrue(!server.canSetCentralMonitorTextModuleConfiguration(
                group.getIdentity(), group.getModuleSlotIncarnation(), Long.MAX_VALUE,
                module, configuration("updated", 2.0f)),
                "Central Monitor accepted a text configuration after its revision was exhausted");
        helper.assertTrue(configuration("opening", 1.0f).equals(
                module.get(GTDataComponents.FORMAT_STRING_LIST.get())) &&
                group.getTextConfigurationRevision() == Long.MAX_VALUE,
                "Central Monitor revision overflow rejection changed authoritative state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void snapshotCasAllowsOnlyDerivedStateChanges(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestTextModuleHost derivedHost = new TestTextModuleHost(textModule("opening", 1.0f));
        SyncActionData derivedAction = action(derivedHost, derivedHost.snapshot(), configuration("updated", 2.0f), 0);
        UUID derivedPlaceholder = UUID.randomUUID();
        TextLineList derivedText = configuration("derived", 4.0f);
        derivedHost.module.set(GTDataComponents.PLACEHOLDER_UUID.get(), derivedPlaceholder);
        derivedHost.module.set(GTDataComponents.TEXT_LINE_LIST.get(), derivedText);
        helper.assertTrue(dispatch(player, derivedHost, derivedAction),
                "text module CAS rejected TEXT_LINE_LIST and PLACEHOLDER_UUID changes");
        helper.assertTrue(derivedPlaceholder.equals(
                derivedHost.module.get(GTDataComponents.PLACEHOLDER_UUID.get())),
                "text module CAS replaced a derived placeholder UUID");
        helper.assertTrue(derivedText.equals(derivedHost.module.get(GTDataComponents.TEXT_LINE_LIST.get())),
                "text module CAS replaced derived text");

        TestTextModuleHost concurrentHost = new TestTextModuleHost(textModule("opening", 1.0f));
        ItemStack concurrentOpening = concurrentHost.snapshot();
        SyncActionData first = action(concurrentHost, concurrentOpening, configuration("first", 2.0f), 0);
        SyncActionData second = action(concurrentHost, concurrentOpening, configuration("second", 3.0f), 1);
        helper.assertTrue(dispatch(player, concurrentHost, first),
                "first text module configuration CAS was rejected");
        assertRejectedWithoutMutation(helper, player, concurrentHost, concurrentHost, second,
                "concurrent text module configuration overwrote FORMAT_STRING_LIST");

        TestTextModuleHost stableHost = new TestTextModuleHost(textModule("opening", 1.0f));
        SyncActionData stableAction = action(stableHost, stableHost.snapshot(), configuration("updated", 2.0f), 0);
        stableHost.module.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        assertRejectedWithoutMutation(helper, player, stableHost, stableHost, stableAction,
                "text module CAS ignored an unrelated stable component change");

        TestTextModuleHost countHost = new TestTextModuleHost(textModule("opening", 1.0f));
        SyncActionData countAction = action(countHost, countHost.snapshot(), configuration("updated", 2.0f), 0);
        countHost.module.setCount(2);
        assertRejectedWithoutMutation(helper, player, countHost, countHost, countAction,
                "text module CAS ignored a changed module count");
        helper.assertTrue(concurrentHost.resyncRequests == 1 && stableHost.resyncRequests == 1 &&
                countHost.resyncRequests == 1,
                "stale text module CAS rejection did not request exactly one authoritative resync");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void configurationRevisionRejectsAbaReplay(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestTextModuleHost host = new TestTextModuleHost(textModule("a", 1.0f));
        ItemStack openingSnapshot = host.snapshot();
        UUID openingSlotIncarnation = host.moduleSlotIncarnation;
        SyncActionData staleAtoB = action(host, openingSnapshot, configuration("b", 2.0f), 0);

        helper.assertTrue(dispatch(player, host,
                action(host, host.snapshot(), configuration("c", 3.0f), 1)),
                "text module action rejected A to C configuration change");
        helper.assertTrue(dispatch(player, host,
                action(host, host.snapshot(), configuration("a", 1.0f), 2)),
                "text module action rejected C to A configuration change");
        helper.assertTrue(host.textConfigurationRevision == 2,
                "A to C to A did not advance the configuration revision twice");
        helper.assertTrue(host.moduleSlotIncarnation.equals(openingSlotIncarnation),
                "in-place A to C to A changes replaced the physical slot incarnation");
        helper.assertTrue(CentralMonitorTextModuleActions.matchesExpectedModule(host.module, openingSnapshot),
                "A to C to A did not restore the original stable module snapshot");

        assertRejectedWithoutMutation(helper, player, host, host, staleAtoB,
                "stale A to B action was accepted after A to C to A");
        helper.assertTrue(configuration("a", 1.0f).equals(
                host.module.get(GTDataComponents.FORMAT_STRING_LIST.get())),
                "rejected ABA replay changed the text configuration");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void payloadRequiresExactRawConfigurationAndCanonicalSnapshot(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestTextModuleHost host = new TestTextModuleHost(textModule("opening", 1.0f));
        ItemStack snapshot = host.snapshot();
        JsonObject validConfiguration = encodedConfiguration(
                new TextLineList(List.of(Component.literal("updated").withStyle(ChatFormatting.RED)), 2.0f));
        SyncFieldData fields = validFields(host, validConfiguration);

        DataComponentMap extraComponent = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), snapshot)
                .set(GTDataComponents.ACTIVE.get(), true)
                .build();
        assertRejectedWithoutMutation(helper, player, host, host, rawAction(extraComponent),
                "text module action accepted an extra payload component");

        SyncFieldData missingField = SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(host.holderIncarnation))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(host.groupIdentity))
                .put(MODULE_SLOT_INCARNATION_FIELD, encodeUuid(host.moduleSlotIncarnation))
                .put(EXPECTED_CONFIGURATION_REVISION_FIELD, new JsonPrimitive(host.textConfigurationRevision))
                .build();
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(missingField, snapshot)),
                "text module action accepted a missing configuration field");

        SyncFieldData missingRevision = SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(host.holderIncarnation))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(host.groupIdentity))
                .put(MODULE_SLOT_INCARNATION_FIELD, encodeUuid(host.moduleSlotIncarnation))
                .put(REQUESTED_CONFIGURATION_FIELD, validConfiguration)
                .build();
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(missingRevision, snapshot)),
                "text module action accepted a missing configuration revision");

        SyncFieldData extraField = SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(host.holderIncarnation))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(host.groupIdentity))
                .put(MODULE_SLOT_INCARNATION_FIELD, encodeUuid(host.moduleSlotIncarnation))
                .put(EXPECTED_CONFIGURATION_REVISION_FIELD, new JsonPrimitive(host.textConfigurationRevision))
                .put(REQUESTED_CONFIGURATION_FIELD, validConfiguration)
                .put(OTHER_FIELD, new JsonPrimitive(true))
                .build();
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(extraField, snapshot)),
                "text module action accepted an extra field");

        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(replaceField(fields, HOLDER_INCARNATION_FIELD,
                        new JsonPrimitive("malformed")), snapshot)),
                "text module action accepted a malformed UUID");
        int revisionValidationCallsBefore = host.validationCalls;
        int revisionResyncRequestsBefore = host.resyncRequests;
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(replaceField(fields, EXPECTED_CONFIGURATION_REVISION_FIELD,
                        new JsonPrimitive(-1)), snapshot)),
                "text module action accepted a negative configuration revision");
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(replaceField(fields, EXPECTED_CONFIGURATION_REVISION_FIELD,
                        new JsonPrimitive(0.5)), snapshot)),
                "text module action accepted a fractional configuration revision");
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(replaceField(fields, EXPECTED_CONFIGURATION_REVISION_FIELD,
                        new JsonPrimitive("0")), snapshot)),
                "text module action accepted a string configuration revision");
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(replaceField(fields, EXPECTED_CONFIGURATION_REVISION_FIELD,
                        new JsonPrimitive(new BigInteger("9223372036854775808"))), snapshot)),
                "text module action accepted an overflowing configuration revision");
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(replaceField(fields, EXPECTED_CONFIGURATION_REVISION_FIELD,
                        new JsonPrimitive(Long.MAX_VALUE)), snapshot)),
                "text module action accepted an exhausted configuration revision");
        helper.assertTrue(host.validationCalls == revisionValidationCallsBefore &&
                host.resyncRequests == revisionResyncRequestsBefore,
                "invalid revision payload reached host validation or requested a CAS resync");

        assertInvalidScale(helper, player, host, fields, snapshot, Float.NaN,
                "text module action accepted a non-finite NaN scale");
        assertInvalidScale(helper, player, host, fields, snapshot, Float.POSITIVE_INFINITY,
                "text module action accepted an infinite scale");
        assertInvalidScale(helper, player, host, fields, snapshot, 0.0f,
                "text module action accepted a zero scale");
        assertInvalidScale(helper, player, host, fields, snapshot, 0.00001f,
                "text module action accepted a scale below the lower bound");
        assertInvalidScale(helper, player, host, fields, snapshot, 1001.0f,
                "text module action accepted a raw scale that TextLineList would clamp");
        assertInvalidScale(helper, player, host, fields, snapshot, 1000.00001,
                "text module action accepted an above-bound scale rounded down by Float");

        JsonObject extraConfigurationField = validConfiguration.deepCopy();
        extraConfigurationField.addProperty("other", true);
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(replaceField(fields, REQUESTED_CONFIGURATION_FIELD,
                        extraConfigurationField), snapshot)),
                "text module action accepted an extra configuration field");

        JsonObject nonListLines = validConfiguration.deepCopy();
        nonListLines.addProperty("lines", "not-a-list");
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(replaceField(fields, REQUESTED_CONFIGURATION_FIELD, nonListLines), snapshot)),
                "text module action accepted a malformed line list");

        ItemStack nonCanonicalSnapshot = snapshot.copy();
        nonCanonicalSnapshot.set(GTDataComponents.TEXT_LINE_LIST.get(), configuration("derived", 1.0f));
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(fields, nonCanonicalSnapshot)),
                "text module action accepted a snapshot carrying derived text");
        nonCanonicalSnapshot = snapshot.copy();
        nonCanonicalSnapshot.set(GTDataComponents.PLACEHOLDER_UUID.get(), UUID.randomUUID());
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(fields, nonCanonicalSnapshot)),
                "text module action accepted a snapshot carrying a placeholder UUID");
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(fields, new ItemStack(Items.STONE))),
                "text module action accepted a non-text expected module");

        SyncActionData valid = rawAction(payload(fields, snapshot));
        helper.assertTrue(dispatch(player, host, valid),
                "text module action rejected a valid strict payload");
        helper.assertTrue(configuration("updated", 2.0f).equals(
                host.module.get(GTDataComponents.FORMAT_STRING_LIST.get())),
                "text module action did not canonicalize styled wire components");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void identitiesSequenceAndSpectatorAreValidated(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestTextModuleHost host = new TestTextModuleHost(textModule("opening", 1.0f));
        SyncActionData valid = action(host, host.snapshot(), configuration("updated", 2.0f), 0);

        assertRejectedWithoutMutation(helper, player, new Object(), host, valid,
                "text module action accepted an unrelated holder");

        TestTextModuleHost replacement = new TestTextModuleHost(
                UUID.randomUUID(), host.groupIdentity, host.moduleSlotIncarnation,
                textModule("opening", 1.0f));
        assertRejectedWithoutMutation(helper, player, replacement, replacement, valid,
                "text module action accepted a replacement holder incarnation");

        SyncActionData wrongGroup = CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                host.holderIncarnation, UUID.randomUUID(), host.moduleSlotIncarnation,
                host.textConfigurationRevision,
                host.snapshot(), configuration("updated", 2.0f), 1);
        assertRejectedWithoutMutation(helper, player, host, host, wrongGroup,
                "text module action accepted an unknown group identity");

        SyncActionData wrongSlot = CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                host.holderIncarnation, host.groupIdentity, UUID.randomUUID(),
                host.textConfigurationRevision,
                host.snapshot(), configuration("updated", 2.0f), 2);
        assertRejectedWithoutMutation(helper, player, host, host, wrongSlot,
                "text module action accepted a replaced module-slot incarnation");

        SyncActionData negativeSequence = new SyncActionData(valid.actionId(), -1, valid.payload());
        assertRejectedWithoutMutation(helper, player, host, host, negativeSequence,
                "text module action accepted a negative sequence");

        player.setGameMode(GameType.SPECTATOR);
        try {
            assertRejectedWithoutMutation(helper, player, host, host, valid,
                    "text module action accepted a spectator");
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }
        helper.assertTrue(replacement.resyncRequests == 1,
                "replacement holder rejection did not request an authoritative resync");
        helper.assertTrue(host.resyncRequests == 2,
                "group and slot CAS rejections did not each request an authoritative resync");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void realMachineRejectsConfigurationOutsideCompleteSaveCodec(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine server = preparedMachine(helper);
        MonitorGroup group = server.getMonitorGroups().getFirst();
        ItemStack module = textModule("opening", 1.0f);
        group.getItemStackHandler().setStackInSlot(0, module);
        ItemStack currentModule = group.getItemStackHandler().getStackInSlot(0);
        ItemStack before = currentModule.copy();
        UUID slotIncarnation = group.getModuleSlotIncarnation();
        server.getSyncDataHolder().serializeToComponents(helper.getLevel().registryAccess(), true, false);

        TextLineList unwritable = CentralMonitorTextModuleActions.createConfiguration(
                List.of("x".repeat(70_000)), 1.0f);
        SyncActionData action = CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                server.getCentralMonitorActionIncarnation(), group.getIdentity(), slotIncarnation,
                group.getTextConfigurationRevision(),
                currentModule, unwritable, 0);
        helper.assertTrue(!dispatch(player, server, action),
                "Central Monitor accepted text configuration exceeding modified-UTF save encoding");
        helper.assertTrue(ItemStack.matches(before, currentModule),
                "rejected unwritable text configuration changed the authoritative module stack");
        helper.assertTrue(group.getModuleSlotIncarnation().equals(slotIncarnation),
                "save validation rotated the text module slot incarnation");
        helper.assertTrue(group.getTextConfigurationRevision() == 0,
                "save validation changed the text configuration revision");
        helper.assertTrue(!server.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false)
                .isEmpty(), "rejected unwritable configuration did not request an authoritative resync");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void realMachineRejectsConfigurationOutsideCompleteNetworkField(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine server = preparedMachine(helper);
        MonitorGroup group = server.getMonitorGroups().getFirst();
        ItemStack module = textModule("opening", 1.0f);
        group.getItemStackHandler().setStackInSlot(0, module);
        ItemStack currentModule = group.getItemStackHandler().getStackInSlot(0);
        ItemStack before = currentModule.copy();
        UUID slotIncarnation = group.getModuleSlotIncarnation();

        TextLineList networkLimitConfiguration = configurationAtNetworkFieldLimit();
        SyncActionData action = CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                server.getCentralMonitorActionIncarnation(), group.getIdentity(), slotIncarnation,
                group.getTextConfigurationRevision(), currentModule, networkLimitConfiguration, 0);
        helper.assertTrue(!dispatch(player, server, action),
                "Central Monitor accepted a configuration whose complete monitorGroups field exceeds the network limit");
        helper.assertTrue(ItemStack.matches(before, currentModule),
                "rejected network-overflow configuration changed the authoritative module stack");
        helper.assertTrue(group.getTextConfigurationRevision() == 0,
                "network validation changed the text configuration revision");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void actionFactoriesRejectInvalidOrUnchangedRequests(GameTestHelper helper) {
        assertIllegalArgument(helper,
                () -> CentralMonitorTextModuleActions.createConfiguration(List.of("text"), Float.NaN),
                "text configuration factory accepted NaN scale");
        assertIllegalArgument(helper,
                () -> CentralMonitorTextModuleActions.createConfiguration(List.of("text"), 0.0f),
                "text configuration factory accepted zero scale");
        assertIllegalArgument(helper,
                () -> CentralMonitorTextModuleActions.createConfiguration(List.of("text"), 1001.0f),
                "text configuration factory accepted a scale above the upper bound");

        TestTextModuleHost host = new TestTextModuleHost(textModule("opening", 1.0f));
        assertIllegalArgument(helper,
                () -> action(host, host.snapshot(), configuration("opening", 1.0f), 0),
                "text module action factory accepted an unchanged configuration");
        assertIllegalArgument(helper,
                () -> action(host, host.snapshot(), configuration("updated", 2.0f), -1),
                "text module action factory accepted a negative sequence");
        assertIllegalArgument(helper,
                () -> CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                        host.holderIncarnation, host.groupIdentity, host.moduleSlotIncarnation,
                        -1, host.snapshot(), configuration("updated", 2.0f), 0),
                "text module action factory accepted a negative configuration revision");
        assertIllegalArgument(helper,
                () -> CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                        host.holderIncarnation, host.groupIdentity, host.moduleSlotIncarnation,
                        Long.MAX_VALUE, host.snapshot(), configuration("updated", 2.0f), 0),
                "text module action factory accepted an exhausted configuration revision");
        assertIllegalArgument(helper,
                () -> CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                        host.holderIncarnation, host.groupIdentity, host.moduleSlotIncarnation,
                        host.textConfigurationRevision,
                        new ItemStack(Items.STONE), configuration("updated", 2.0f), 0),
                "text module action factory accepted a non-text module snapshot");

        List<String> oversizedLines = new ArrayList<>();
        for (int index = 0; index < 18; index++) {
            oversizedLines.add("x".repeat(60_000));
        }
        TextLineList networkOversized = CentralMonitorTextModuleActions.createConfiguration(oversizedLines, 1.0f);
        assertIllegalArgument(helper,
                () -> CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                        host.holderIncarnation, host.groupIdentity, host.moduleSlotIncarnation,
                        host.textConfigurationRevision, host.snapshot(), networkOversized, 0),
                "text module action factory accepted a configuration exceeding one network field");

        ServerPlayer player = preparedPlayer(helper);
        ItemStack unconfiguredModule = GTItems.TEXT_MODULE.get().getDefaultInstance();
        TestTextModuleHost unconfiguredHost = new TestTextModuleHost(unconfiguredModule);
        TextLineList defaultConfiguration = CentralMonitorTextModuleActions.createConfiguration(List.of(), 1.0f);
        helper.assertTrue(dispatch(player, unconfiguredHost,
                action(unconfiguredHost, unconfiguredHost.snapshot(), defaultConfiguration, 0)),
                "text module action rejected an explicit default for a module without editable configuration");
        helper.assertTrue(defaultConfiguration.equals(
                unconfiguredHost.module.get(GTDataComponents.FORMAT_STRING_LIST.get())),
                "text module action did not persist an explicit default configuration");

        TestTextModuleHost minimumHost = new TestTextModuleHost(textModule("opening", 1.0f));
        TextLineList minimum = configuration("minimum", 0.0001f);
        helper.assertTrue(dispatch(player, minimumHost,
                action(minimumHost, minimumHost.snapshot(), minimum, 0)),
                "text module action rejected the inclusive minimum scale");
        TestTextModuleHost maximumHost = new TestTextModuleHost(textModule("opening", 1.0f));
        TextLineList maximum = configuration("maximum", 1000.0f);
        helper.assertTrue(dispatch(player, maximumHost,
                action(maximumHost, maximumHost.snapshot(), maximum, 0)),
                "text module action rejected the inclusive maximum scale");
        helper.succeed();
    }

    private static void assertInvalidScale(GameTestHelper helper, ServerPlayer player,
                                           TestTextModuleHost host, SyncFieldData fields,
                                           ItemStack snapshot, float scale, String message) {
        assertInvalidScale(helper, player, host, fields, snapshot, new JsonPrimitive(scale), message);
    }

    private static void assertInvalidScale(GameTestHelper helper, ServerPlayer player,
                                           TestTextModuleHost host, SyncFieldData fields,
                                           ItemStack snapshot, double scale, String message) {
        assertInvalidScale(helper, player, host, fields, snapshot, new JsonPrimitive(scale), message);
    }

    private static void assertInvalidScale(GameTestHelper helper, ServerPlayer player,
                                           TestTextModuleHost host, SyncFieldData fields,
                                           ItemStack snapshot, JsonPrimitive scale, String message) {
        JsonObject configuration = encodedConfiguration(configuration("updated", 2.0f));
        configuration.add("scale", scale);
        assertRejectedWithoutMutation(helper, player, host, host,
                rawAction(payload(replaceField(fields, REQUESTED_CONFIGURATION_FIELD, configuration), snapshot)),
                message);
    }

    private static SyncActionData action(TestTextModuleHost host, ItemStack expectedModule,
                                         TextLineList requestedConfiguration, int sequence) {
        return CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                host.holderIncarnation, host.groupIdentity, host.moduleSlotIncarnation,
                host.textConfigurationRevision,
                expectedModule, requestedConfiguration, sequence);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        CentralMonitorTextModuleActions.initialize();
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

    private static SyncFieldData validFields(TestTextModuleHost host, JsonElement requestedConfiguration) {
        return SyncFieldData.builder()
                .put(HOLDER_INCARNATION_FIELD, encodeUuid(host.holderIncarnation))
                .put(GROUP_IDENTITY_FIELD, encodeUuid(host.groupIdentity))
                .put(MODULE_SLOT_INCARNATION_FIELD, encodeUuid(host.moduleSlotIncarnation))
                .put(EXPECTED_CONFIGURATION_REVISION_FIELD, new JsonPrimitive(host.textConfigurationRevision))
                .put(REQUESTED_CONFIGURATION_FIELD, requestedConfiguration)
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

    private static JsonObject encodedConfiguration(TextLineList configuration) {
        return TextLineList.CODEC.encodeStart(JsonOps.INSTANCE, configuration).getOrThrow().getAsJsonObject();
    }

    private static TextLineList configurationAtNetworkFieldLimit() {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            lines.add("x".repeat(60_000));
        }
        lines.add("");
        TextLineList base = CentralMonitorTextModuleActions.createConfiguration(lines, 1.0f);
        int remaining = SyncFieldData.MAX_NETWORK_FIELD_JSON_LENGTH -
                GSON.toJson(encodedConfiguration(base)).length();
        if (remaining <= 0 || remaining >= 65_535) {
            throw new IllegalStateException("Unable to construct a per-line-safe network boundary configuration");
        }
        lines.set(lines.size() - 1, "x".repeat(remaining));
        TextLineList boundary = CentralMonitorTextModuleActions.createConfiguration(lines, 1.0f);
        if (!SyncFieldData.isFieldValueWithinNetworkLimit(encodedConfiguration(boundary)) ||
                GSON.toJson(encodedConfiguration(boundary)).length() !=
                        SyncFieldData.MAX_NETWORK_FIELD_JSON_LENGTH) {
            throw new IllegalStateException("Text configuration did not reach the exact network field boundary");
        }
        return boundary;
    }

    private static TextLineList configuration(String line, float scale) {
        return CentralMonitorTextModuleActions.createConfiguration(List.of(line), scale);
    }

    private static ItemStack textModule(String line, float scale) {
        ItemStack module = GTItems.TEXT_MODULE.get().getDefaultInstance();
        module.set(GTDataComponents.FORMAT_STRING_LIST.get(), configuration(line, scale));
        return module;
    }

    private static void assertRejectedWithoutMutation(GameTestHelper helper, ServerPlayer player,
                                                      Object dispatchHolder, TestTextModuleHost observedHost,
                                                      SyncActionData action, String message) {
        ItemStack before = observedHost.module.copy();
        int writesBefore = observedHost.successfulWrites;
        long revisionBefore = observedHost.textConfigurationRevision;
        helper.assertTrue(!dispatch(player, dispatchHolder, action), message);
        helper.assertTrue(ItemStack.matches(before, observedHost.module),
                message + " and changed the module stack");
        helper.assertTrue(observedHost.successfulWrites == writesBefore,
                message + " and invoked the module write path");
        helper.assertTrue(observedHost.textConfigurationRevision == revisionBefore,
                message + " and changed the text configuration revision");
    }

    private static void assertIllegalArgument(GameTestHelper helper, Runnable operation, String message) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static TestCentralMonitorMachine preparedMachine(GameTestHelper helper) {
        TestCentralMonitorMachine machine = new TestCentralMonitorMachine(false);
        machine.setLevel(helper.getLevel());
        machine.addMonitor(MONITOR_POSITION);
        helper.assertTrue(machine.createCentralMonitorGroup(
                0, UUID.randomUUID(), Set.of(MONITOR_POSITION)),
                "server rejected valid text module test group creation");
        return machine;
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
        public boolean isMonitor() {
            return true;
        }

        @Override
        public IGuiTexture getComponentIcon() {
            return GuiTextures.BLANK_TRANSPARENT;
        }

        @Override
        public BlockPos getBlockPos() {
            return position;
        }
    }

    private static final class TestTextModuleHost implements CentralMonitorTextModuleActionHost {

        private final UUID holderIncarnation;
        private final UUID groupIdentity;
        private final UUID moduleSlotIncarnation;
        private final ItemStack module;
        private long textConfigurationRevision;
        private int successfulWrites;
        private int resyncRequests;
        private int validationCalls;

        private TestTextModuleHost(ItemStack module) {
            this(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), module);
        }

        private TestTextModuleHost(UUID holderIncarnation, UUID groupIdentity,
                                   UUID moduleSlotIncarnation, ItemStack module) {
            this.holderIncarnation = holderIncarnation;
            this.groupIdentity = groupIdentity;
            this.moduleSlotIncarnation = moduleSlotIncarnation;
            this.module = module.copy();
        }

        private ItemStack snapshot() {
            return CentralMonitorTextModuleActions.captureExpectedModule(module);
        }

        @Override
        public @NotNull UUID getCentralMonitorActionIncarnation() {
            return holderIncarnation;
        }

        @Override
        public void resyncCentralMonitorTextModuleState() {
            resyncRequests++;
        }

        @Override
        public boolean canSetCentralMonitorTextModuleConfiguration(
                                                                   @NotNull UUID groupIdentity,
                                                                   @NotNull UUID moduleSlotIncarnation,
                                                                   long expectedConfigurationRevision,
                                                                   @NotNull ItemStack expectedModule,
                                                                   @NotNull TextLineList requestedConfiguration) {
            validationCalls++;
            return this.groupIdentity.equals(groupIdentity) &&
                    this.moduleSlotIncarnation.equals(moduleSlotIncarnation) &&
                    this.textConfigurationRevision == expectedConfigurationRevision &&
                    CentralMonitorTextModuleActions.isTextModule(module) &&
                    CentralMonitorTextModuleActions.matchesExpectedModule(module, expectedModule) &&
                    CentralMonitorTextModuleActions.isValidScale(requestedConfiguration.scale()) &&
                    !requestedConfiguration.equals(module.get(GTDataComponents.FORMAT_STRING_LIST.get()));
        }

        @Override
        public boolean setCentralMonitorTextModuleConfiguration(
                                                                @NotNull UUID groupIdentity,
                                                                @NotNull UUID moduleSlotIncarnation,
                                                                long expectedConfigurationRevision,
                                                                @NotNull ItemStack expectedModule,
                                                                @NotNull TextLineList requestedConfiguration) {
            if (!canSetCentralMonitorTextModuleConfiguration(
                    groupIdentity, moduleSlotIncarnation, expectedConfigurationRevision,
                    expectedModule, requestedConfiguration)) {
                return false;
            }
            long nextRevision = Math.incrementExact(textConfigurationRevision);
            module.set(GTDataComponents.FORMAT_STRING_LIST.get(), requestedConfiguration);
            textConfigurationRevision = nextRevision;
            successfulWrites++;
            return true;
        }
    }
}
