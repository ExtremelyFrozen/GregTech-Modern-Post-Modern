package com.gregtechceu.gtceu.api.gui.slot;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import io.netty.buffer.Unpooled;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DynamicItemSlotOpeningTest {

    private static final String BATCH = "DynamicItemSlotOpening";
    private static final int CONTAINER_ID = 7;
    private static final int BASE_SLOT_COUNT = 36;
    private static final UUID MENU_SESSION_ID = id(1);
    private static final UUID TARGET_ID = id(10);
    private static final UUID FIRST_BINDING_ID = id(20);
    private static final UUID SECOND_BINDING_ID = id(21);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void activationAndSelectionRequireBothAcknowledgements(GameTestHelper helper) {
        DynamicItemSlotClientOpening client = clientOpening();
        DynamicItemSlotServerOpening server = serverOpening();
        DynamicItemSlotManifest manifest = initialManifest();
        DynamicItemSlotOpeningToken token = token(manifest);

        assertTransition(helper, server.beginManifest(manifest), DynamicItemSlotTransition.ACCEPTED,
                "server rejected the initial manifest");
        assertTransition(helper, client.receiveManifest(token, manifest), DynamicItemSlotTransition.ACCEPTED,
                "client rejected the initial manifest");
        helper.assertTrue(client.bindingsToAppend().equals(manifest.bindings()),
                "client did not expose the initial append-only slot range");
        assertNotInteractive(helper, client, server, FIRST_BINDING_ID,
                "manifest receipt enabled an unprepared binding");

        assertTransition(helper,
                client.completePreparation(token, Set.of(FIRST_BINDING_ID), Set.of(FIRST_BINDING_ID)),
                DynamicItemSlotTransition.ACCEPTED, "client rejected a complete disabled append");
        assertTransition(helper, server.receivePreparedAcknowledgement(token), DynamicItemSlotTransition.ACCEPTED,
                "server rejected PREPARED");
        assertTransition(helper, server.markDisabledSlotsAppended(token, Set.of(FIRST_BINDING_ID)),
                DynamicItemSlotTransition.ACCEPTED, "server rejected its disabled append");
        assertNotInteractive(helper, client, server, FIRST_BINDING_ID,
                "disabled append enabled a binding before the full snapshot");

        assertTransition(helper, server.markFullSnapshotSent(token), DynamicItemSlotTransition.ACCEPTED,
                "server rejected the completed full snapshot");
        assertTransition(helper, client.receiveActivation(token, server.selectedBindingId()),
                DynamicItemSlotTransition.ACCEPTED,
                "client rejected activation after PREPARED");
        assertNotInteractive(helper, client, server, FIRST_BINDING_ID,
                "client activation enabled a binding before ACTIVATED reached the server");

        assertTransition(helper, server.receiveActivatedAcknowledgement(token), DynamicItemSlotTransition.ACCEPTED,
                "server rejected ACTIVATED");
        assertNotInteractive(helper, client, server, FIRST_BINDING_ID,
                "layout activation enabled a binding before page selection");

        DynamicItemSlotSelection selection = client.requestSelection(FIRST_BINDING_ID).orElseThrow();
        assertNotInteractive(helper, client, server, FIRST_BINDING_ID,
                "client switched pages before the server selection ACK");
        assertTransition(helper, server.receiveSelectionRequest(token, selection, binding -> true),
                DynamicItemSlotTransition.ACCEPTED, "server rejected a valid page selection");
        helper.assertTrue(server.isBindingInteractive(FIRST_BINDING_ID),
                "server did not enable its confirmed page binding");
        helper.assertTrue(!client.isBindingInteractive(FIRST_BINDING_ID),
                "client switched pages before receiving the selection ACK");
        assertTransition(helper, client.receiveSelectionAcknowledgement(token, selection),
                DynamicItemSlotTransition.ACCEPTED, "client rejected the exact selection ACK");
        helper.assertTrue(client.isBindingInteractive(FIRST_BINDING_ID),
                "client did not enable the server-confirmed page binding");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void overviewSelectionUsesTheAcknowledgedSequenceAndDisablesEveryBinding(GameTestHelper helper) {
        DynamicItemSlotManifest manifest = new DynamicItemSlotManifest(
                0,
                1,
                id(130),
                1,
                BASE_SLOT_COUNT,
                List.of(
                        binding(FIRST_BINDING_ID, TARGET_ID, BASE_SLOT_COUNT, 9, true),
                        binding(SECOND_BINDING_ID, id(11), BASE_SLOT_COUNT + 9, 9, true)));
        DynamicItemSlotClientOpening client = clientOpening();
        DynamicItemSlotServerOpening server = serverOpening();
        completeHandshake(helper, client, server, manifest);
        DynamicItemSlotOpeningToken token = token(manifest);

        DynamicItemSlotSelection firstSelection = client.requestSelection(FIRST_BINDING_ID).orElseThrow();
        assertTransition(helper, server.receiveSelectionRequest(token, firstSelection, binding -> true),
                DynamicItemSlotTransition.ACCEPTED, "server rejected selection setup");
        assertTransition(helper, client.receiveSelectionAcknowledgement(token, firstSelection),
                DynamicItemSlotTransition.ACCEPTED, "client rejected selection setup");
        helper.assertTrue(client.isBindingInteractive(FIRST_BINDING_ID) &&
                server.isBindingInteractive(FIRST_BINDING_ID),
                "selection setup did not activate the first binding");

        DynamicItemSlotSelection overview = client.requestOverview().orElseThrow();
        helper.assertTrue(overview.sequence() == firstSelection.sequence() + 1 && overview.bindingId().isEmpty(),
                "overview request did not consume the next empty selection sequence");
        helper.assertTrue(!client.isBindingInteractive(FIRST_BINDING_ID) &&
                client.selectedBindingId().equals(Optional.of(FIRST_BINDING_ID)),
                "client changed pages instead of only disabling interaction before the overview ACK");
        helper.assertTrue(server.isBindingInteractive(FIRST_BINDING_ID),
                "client request changed authoritative interaction before reaching the server");

        assertTransition(helper, server.receiveSelectionRequest(token, overview, binding -> false),
                DynamicItemSlotTransition.ACCEPTED, "server rejected a valid overview request");
        helper.assertTrue(server.selectedBindingId().isEmpty() &&
                !server.isBindingInteractive(FIRST_BINDING_ID) &&
                !server.isBindingInteractive(SECOND_BINDING_ID),
                "server overview acknowledgement left a binding interactive");
        assertTransition(helper, server.receiveSelectionRequest(token, overview, binding -> false),
                DynamicItemSlotTransition.DUPLICATE, "server did not replay the exact overview request idempotently");
        helper.assertTrue(client.selectedBindingId().equals(Optional.of(FIRST_BINDING_ID)),
                "server acceptance changed the client page before its acknowledgement arrived");

        assertTransition(helper, client.receiveSelectionAcknowledgement(token, overview),
                DynamicItemSlotTransition.ACCEPTED, "client rejected the exact overview acknowledgement");
        helper.assertTrue(client.selectedBindingId().isEmpty() &&
                !client.isBindingInteractive(FIRST_BINDING_ID) &&
                !client.isBindingInteractive(SECOND_BINDING_ID),
                "empty overview acknowledgement left a client binding interactive");
        assertTransition(helper, client.receiveSelectionAcknowledgement(token, overview),
                DynamicItemSlotTransition.DUPLICATE, "client repeated the acknowledged overview transition");

        DynamicItemSlotSelection secondSelection = client.requestSelection(SECOND_BINDING_ID).orElseThrow();
        helper.assertTrue(secondSelection.sequence() == overview.sequence() + 1,
                "selection after overview did not retain the shared sequence");
        assertTransition(helper, server.receiveSelectionRequest(token, secondSelection, binding -> true),
                DynamicItemSlotTransition.ACCEPTED, "server rejected selection after overview");
        assertTransition(helper, client.receiveSelectionAcknowledgement(token, secondSelection),
                DynamicItemSlotTransition.ACCEPTED, "client rejected selection after overview");
        helper.assertTrue(client.isBindingInteractive(SECOND_BINDING_ID) &&
                server.isBindingInteractive(SECOND_BINDING_ID),
                "selection after overview did not reactivate the requested binding");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void duplicateAndOutOfOrderHandshakeMessagesAreIdempotentOrRejected(GameTestHelper helper) {
        DynamicItemSlotClientOpening client = clientOpening();
        DynamicItemSlotServerOpening server = serverOpening();
        DynamicItemSlotManifest manifest = initialManifest();
        DynamicItemSlotOpeningToken token = token(manifest);

        assertTransition(helper, server.beginManifest(manifest), DynamicItemSlotTransition.ACCEPTED,
                "server rejected manifest setup");
        assertTransition(helper, server.beginManifest(manifest), DynamicItemSlotTransition.DUPLICATE,
                "server did not classify a duplicate manifest idempotently");
        assertTransition(helper, client.receiveManifest(token, manifest), DynamicItemSlotTransition.ACCEPTED,
                "client rejected manifest setup");
        assertTransition(helper, client.receiveManifest(token, manifest), DynamicItemSlotTransition.DUPLICATE,
                "client appended a duplicate manifest");
        assertTransition(helper, client.receiveActivation(token, server.selectedBindingId()),
                DynamicItemSlotTransition.REJECTED,
                "client accepted ACTIVATE before PREPARED");
        assertTransition(helper, server.receiveActivatedAcknowledgement(token), DynamicItemSlotTransition.REJECTED,
                "server accepted ACTIVATED before ACTIVATE");
        assertTransition(helper, server.markFullSnapshotSent(token), DynamicItemSlotTransition.REJECTED,
                "server accepted a full snapshot before PREPARED and append");

        Set<UUID> bindingIds = Set.of(FIRST_BINDING_ID);
        assertTransition(helper, client.completePreparation(token, bindingIds, bindingIds),
                DynamicItemSlotTransition.ACCEPTED, "client rejected complete preparation");
        assertTransition(helper, client.completePreparation(token, bindingIds, bindingIds),
                DynamicItemSlotTransition.DUPLICATE, "client repeated PREPARED state mutation");
        assertTransition(helper, server.receivePreparedAcknowledgement(token), DynamicItemSlotTransition.ACCEPTED,
                "server rejected PREPARED");
        assertTransition(helper, server.receivePreparedAcknowledgement(token), DynamicItemSlotTransition.DUPLICATE,
                "server repeated PREPARED state mutation");
        assertTransition(helper, server.markDisabledSlotsAppended(token, bindingIds),
                DynamicItemSlotTransition.ACCEPTED, "server rejected append completion");
        assertTransition(helper, server.markDisabledSlotsAppended(token, bindingIds),
                DynamicItemSlotTransition.DUPLICATE, "server repeated append completion");
        assertTransition(helper, server.markFullSnapshotSent(token), DynamicItemSlotTransition.ACCEPTED,
                "server rejected full snapshot completion");
        assertTransition(helper, server.markFullSnapshotSent(token), DynamicItemSlotTransition.DUPLICATE,
                "server repeated full snapshot completion");
        assertTransition(helper, client.receiveActivation(token, server.selectedBindingId()),
                DynamicItemSlotTransition.ACCEPTED,
                "client rejected activation");
        assertTransition(helper, client.receiveActivation(token, server.selectedBindingId()),
                DynamicItemSlotTransition.DUPLICATE,
                "client repeated activation mutation");
        assertTransition(helper, server.receiveActivatedAcknowledgement(token), DynamicItemSlotTransition.ACCEPTED,
                "server rejected activation ACK");
        assertTransition(helper, server.receiveActivatedAcknowledgement(token), DynamicItemSlotTransition.DUPLICATE,
                "server repeated activation ACK mutation");
        assertTransition(helper, server.receivePreparedAcknowledgement(token), DynamicItemSlotTransition.DUPLICATE,
                "server did not retain idempotency for a delayed PREPARED ACK");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void tombstoneKeepsItsRangeAndSameTargetMayUseNewBinding(GameTestHelper helper) {
        DynamicItemSlotClientOpening client = clientOpening();
        DynamicItemSlotServerOpening server = serverOpening();
        completeHandshake(helper, client, server, initialManifest());

        DynamicItemSlotManifest rebuilt = new DynamicItemSlotManifest(
                1,
                2,
                id(102),
                2,
                BASE_SLOT_COUNT,
                List.of(
                        binding(FIRST_BINDING_ID, TARGET_ID, BASE_SLOT_COUNT, 9, false),
                        binding(SECOND_BINDING_ID, TARGET_ID, id(2_010), BASE_SLOT_COUNT + 9, 9, true)));
        DynamicItemSlotOpeningToken rebuiltToken = token(rebuilt);

        assertTransition(helper, server.beginManifest(rebuilt), DynamicItemSlotTransition.ACCEPTED,
                "server rejected a tombstone and new lifecycle");
        assertTransition(helper, client.receiveManifest(rebuiltToken, rebuilt), DynamicItemSlotTransition.ACCEPTED,
                "client rejected a tombstone and new lifecycle");
        helper.assertTrue(client.bindingsToAppend().equals(List.of(rebuilt.bindings().get(1))),
                "client re-appended a tombstone or omitted the new lifecycle");
        assertNotInteractive(helper, client, server, FIRST_BINDING_ID,
                "pending rebuild left the old lifecycle interactive");

        completePendingHandshake(helper, client, server, rebuilt,
                Set.of(FIRST_BINDING_ID, SECOND_BINDING_ID), Set.of(SECOND_BINDING_ID));
        helper.assertTrue(client.requestSelection(FIRST_BINDING_ID).isEmpty(),
                "client allowed selection of a tombstone");
        DynamicItemSlotSelection selection = client.requestSelection(SECOND_BINDING_ID).orElseThrow();
        assertTransition(helper, server.receiveSelectionRequest(rebuiltToken, selection, binding -> true),
                DynamicItemSlotTransition.ACCEPTED, "server rejected the rebuilt target lifecycle");
        assertTransition(helper, client.receiveSelectionAcknowledgement(rebuiltToken, selection),
                DynamicItemSlotTransition.ACCEPTED, "client rejected selection of the rebuilt target lifecycle");
        helper.assertTrue(client.isBindingInteractive(SECOND_BINDING_ID) &&
                server.isBindingInteractive(SECOND_BINDING_ID),
                "rebuilt target lifecycle did not become interactive after both ACKs");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void crossOpeningAndIdentityRewriteFailClosed(GameTestHelper helper) {
        DynamicItemSlotClientOpening client = clientOpening();
        DynamicItemSlotServerOpening server = serverOpening();
        DynamicItemSlotManifest manifest = initialManifest();
        DynamicItemSlotOpeningToken token = token(manifest);
        DynamicItemSlotOpeningToken otherSession = new DynamicItemSlotOpeningToken(
                CONTAINER_ID, id(999), manifest.epoch(), manifest.manifestNonce());

        assertTransition(helper, server.beginManifest(manifest), DynamicItemSlotTransition.ACCEPTED,
                "server rejected manifest setup");
        assertTransition(helper, client.receiveManifest(token, manifest), DynamicItemSlotTransition.ACCEPTED,
                "client rejected manifest setup");
        assertTransition(helper, server.receivePreparedAcknowledgement(otherSession),
                DynamicItemSlotTransition.CLOSE_OPENING, "server accepted PREPARED from another opening");
        assertTransition(helper, client.completePreparation(otherSession, Set.of(FIRST_BINDING_ID),
                Set.of(FIRST_BINDING_ID)), DynamicItemSlotTransition.CLOSE_OPENING,
                "client accepted preparation for another opening");
        helper.assertTrue(client.isClosed() && server.isClosed(),
                "cross-opening identity conflict did not permanently close both states");
        helper.assertTrue(client.pendingManifest().isEmpty() && client.activeManifest().isEmpty() &&
                client.selectedBindingId().isEmpty() && client.bindingsToAppend().isEmpty() &&
                server.pendingManifest().isEmpty() && server.activeManifest().isEmpty() &&
                server.selectedBindingId().isEmpty() && server.bindingsToAppend().isEmpty(),
                "closed opening exposed state that could be resumed");
        assertTransition(helper, server.receivePreparedAcknowledgement(token),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "closed server state resumed after an identity conflict");
        assertTransition(helper, client.completePreparation(token, Set.of(FIRST_BINDING_ID),
                Set.of(FIRST_BINDING_ID)), DynamicItemSlotTransition.CLOSE_OPENING,
                "closed client state resumed after an identity conflict");

        DynamicItemSlotClientOpening rewrittenClient = clientOpening();
        DynamicItemSlotServerOpening rewrittenServer = serverOpening();
        completeHandshake(helper, rewrittenClient, rewrittenServer, manifest);
        DynamicItemSlotManifest rewritten = new DynamicItemSlotManifest(
                1,
                2,
                id(103),
                2,
                BASE_SLOT_COUNT,
                List.of(binding(FIRST_BINDING_ID, id(11), BASE_SLOT_COUNT, 9, true)));
        assertTransition(helper, rewrittenServer.beginManifest(rewritten), DynamicItemSlotTransition.CLOSE_OPENING,
                "server accepted a rewritten binding target identity");
        assertTransition(helper, rewrittenClient.receiveManifest(token(rewritten), rewritten),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "client accepted a rewritten binding target identity");

        DynamicItemSlotClientOpening reincarnatedClient = clientOpening();
        DynamicItemSlotServerOpening reincarnatedServer = serverOpening();
        completeHandshake(helper, reincarnatedClient, reincarnatedServer, manifest);
        DynamicItemSlotManifest reincarnated = new DynamicItemSlotManifest(
                1,
                2,
                id(104),
                2,
                BASE_SLOT_COUNT,
                List.of(binding(
                        FIRST_BINDING_ID, TARGET_ID, id(2_011), BASE_SLOT_COUNT, 9, true)));
        assertTransition(helper, reincarnatedServer.beginManifest(reincarnated),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "server accepted a rewritten binding target incarnation");
        assertTransition(helper, reincarnatedClient.receiveManifest(token(reincarnated), reincarnated),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "client accepted a rewritten binding target incarnation");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void activationMustRetainTheConfirmedSelection(GameTestHelper helper) {
        DynamicItemSlotManifest initial = new DynamicItemSlotManifest(
                0,
                1,
                id(110),
                1,
                BASE_SLOT_COUNT,
                List.of(
                        binding(FIRST_BINDING_ID, TARGET_ID, BASE_SLOT_COUNT, 9, true),
                        binding(SECOND_BINDING_ID, id(11), BASE_SLOT_COUNT + 9, 9, true)));
        DynamicItemSlotClientOpening client = clientOpening();
        DynamicItemSlotServerOpening server = serverOpening();
        completeHandshake(helper, client, server, initial);
        DynamicItemSlotSelection selection = client.requestSelection(FIRST_BINDING_ID).orElseThrow();
        assertTransition(helper, server.receiveSelectionRequest(token(initial), selection, binding -> true),
                DynamicItemSlotTransition.ACCEPTED, "server rejected selection setup");
        assertTransition(helper, client.receiveSelectionAcknowledgement(token(initial), selection),
                DynamicItemSlotTransition.ACCEPTED, "client rejected selection setup");

        DynamicItemSlotManifest next = new DynamicItemSlotManifest(
                1,
                2,
                id(111),
                2,
                BASE_SLOT_COUNT,
                initial.bindings());
        DynamicItemSlotOpeningToken nextToken = token(next);
        assertTransition(helper, server.beginManifest(next), DynamicItemSlotTransition.ACCEPTED,
                "server rejected follow-up manifest setup");
        assertTransition(helper, client.receiveManifest(nextToken, next), DynamicItemSlotTransition.ACCEPTED,
                "client rejected follow-up manifest setup");
        Set<UUID> bindingIds = Set.of(FIRST_BINDING_ID, SECOND_BINDING_ID);
        assertTransition(helper, client.completePreparation(nextToken, bindingIds, bindingIds),
                DynamicItemSlotTransition.ACCEPTED, "client rejected follow-up preparation");
        assertTransition(helper, server.receivePreparedAcknowledgement(nextToken),
                DynamicItemSlotTransition.ACCEPTED, "server rejected follow-up PREPARED");
        assertTransition(helper, server.markDisabledSlotsAppended(nextToken, bindingIds),
                DynamicItemSlotTransition.ACCEPTED, "server rejected follow-up append state");
        assertTransition(helper, server.markFullSnapshotSent(nextToken), DynamicItemSlotTransition.ACCEPTED,
                "server rejected follow-up full snapshot");

        assertTransition(helper, client.receiveActivation(nextToken, Optional.of(SECOND_BINDING_ID)),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "client accepted ACTIVATE that changed the server-confirmed selection");
        helper.assertTrue(client.isClosed() && !client.isBindingInteractive(FIRST_BINDING_ID) &&
                !client.isBindingInteractive(SECOND_BINDING_ID),
                "conflicting ACTIVATE left a dynamic binding interactive");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void conflictingManifestIdentityAndRevisionFailClosed(GameTestHelper helper) {
        DynamicItemSlotManifest initial = initialManifest();
        DynamicItemSlotManifest competing = new DynamicItemSlotManifest(
                0, 1, id(120), 1, BASE_SLOT_COUNT, initial.bindings());
        DynamicItemSlotClientOpening pendingClient = clientOpening();
        DynamicItemSlotServerOpening pendingServer = serverOpening();
        assertTransition(helper, pendingServer.beginManifest(initial), DynamicItemSlotTransition.ACCEPTED,
                "server rejected competing-manifest setup");
        assertTransition(helper, pendingClient.receiveManifest(token(initial), initial),
                DynamicItemSlotTransition.ACCEPTED, "client rejected competing-manifest setup");
        assertTransition(helper, pendingServer.beginManifest(competing), DynamicItemSlotTransition.CLOSE_OPENING,
                "server accepted another nonce for the pending epoch");
        assertTransition(helper, pendingClient.receiveManifest(token(competing), competing),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "client accepted another nonce for the pending epoch");

        DynamicItemSlotClientOpening rollbackClient = clientOpening();
        DynamicItemSlotServerOpening rollbackServer = serverOpening();
        completeHandshake(helper, rollbackClient, rollbackServer, initial);
        DynamicItemSlotManifest revisionRollback = new DynamicItemSlotManifest(
                1, 2, id(121), 0, BASE_SLOT_COUNT, initial.bindings());
        assertTransition(helper, rollbackServer.beginManifest(revisionRollback),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "server accepted a source revision rollback");
        assertTransition(helper, rollbackClient.receiveManifest(token(revisionRollback), revisionRollback),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "client accepted a source revision rollback");

        DynamicItemSlotClientOpening nonceClient = clientOpening();
        DynamicItemSlotServerOpening nonceServer = serverOpening();
        completeHandshake(helper, nonceClient, nonceServer, initial);
        DynamicItemSlotManifest second = new DynamicItemSlotManifest(
                1, 2, id(122), 2, BASE_SLOT_COUNT, initial.bindings());
        completeHandshake(helper, nonceClient, nonceServer, second);
        DynamicItemSlotManifest reusedNonce = new DynamicItemSlotManifest(
                2, 3, initial.manifestNonce(), 3, BASE_SLOT_COUNT, initial.bindings());
        assertTransition(helper, nonceServer.beginManifest(reusedNonce), DynamicItemSlotTransition.CLOSE_OPENING,
                "server accepted historical nonce reuse across manifest epochs");
        assertTransition(helper, nonceClient.receiveManifest(token(reusedNonce), reusedNonce),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "client accepted historical nonce reuse across manifest epochs");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void selectionAcknowledgementMustMatchSequenceAndBinding(GameTestHelper helper) {
        DynamicItemSlotClientOpening client = clientOpening();
        DynamicItemSlotServerOpening server = serverOpening();
        DynamicItemSlotManifest manifest = new DynamicItemSlotManifest(
                0,
                1,
                id(101),
                1,
                BASE_SLOT_COUNT,
                List.of(
                        binding(FIRST_BINDING_ID, TARGET_ID, BASE_SLOT_COUNT, 9, true),
                        binding(SECOND_BINDING_ID, id(11), BASE_SLOT_COUNT + 9, 9, true)));
        completeHandshake(helper, client, server, manifest);
        DynamicItemSlotOpeningToken token = token(manifest);

        DynamicItemSlotSelection requested = client.requestSelection(FIRST_BINDING_ID).orElseThrow();
        DynamicItemSlotSelection wrongBinding = new DynamicItemSlotSelection(
                requested.sequence(), Optional.of(SECOND_BINDING_ID));
        assertTransition(helper, client.receiveSelectionAcknowledgement(token, wrongBinding),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "client accepted a selection ACK for another binding");
        helper.assertTrue(!client.isBindingInteractive(FIRST_BINDING_ID) &&
                !client.isBindingInteractive(SECOND_BINDING_ID),
                "invalid selection ACK changed the client page");

        assertTransition(helper, server.receiveSelectionRequest(token, requested, binding -> true),
                DynamicItemSlotTransition.ACCEPTED, "server rejected the requested selection");
        assertTransition(helper, server.receiveSelectionRequest(token, requested, binding -> true),
                DynamicItemSlotTransition.DUPLICATE, "server did not handle a duplicate selection idempotently");
        assertTransition(helper, server.receiveSelectionRequest(token, wrongBinding, binding -> true),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "server accepted one selection sequence for two bindings");
        assertTransition(helper, client.receiveSelectionAcknowledgement(token, requested),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "client resumed after a conflicting selection acknowledgement");

        DynamicItemSlotClientOpening validClient = clientOpening();
        DynamicItemSlotServerOpening validServer = serverOpening();
        completeHandshake(helper, validClient, validServer, manifest);
        DynamicItemSlotSelection validSelection = validClient.requestSelection(FIRST_BINDING_ID).orElseThrow();
        assertTransition(helper, validServer.receiveSelectionRequest(token, validSelection, binding -> true),
                DynamicItemSlotTransition.ACCEPTED, "server rejected the valid selection request");
        assertTransition(helper, validClient.receiveSelectionAcknowledgement(token, validSelection),
                DynamicItemSlotTransition.ACCEPTED, "client rejected the exact selection ACK");
        assertTransition(helper, validClient.receiveSelectionAcknowledgement(token, validSelection),
                DynamicItemSlotTransition.DUPLICATE, "client repeated an acknowledged page switch");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void overviewAndBindingCannotShareSelectionSequence(GameTestHelper helper) {
        DynamicItemSlotManifest manifest = initialManifest();
        DynamicItemSlotOpeningToken token = token(manifest);

        DynamicItemSlotClientOpening client = clientOpening();
        DynamicItemSlotServerOpening server = serverOpening();
        completeHandshake(helper, client, server, manifest);
        DynamicItemSlotSelection overview = client.requestOverview().orElseThrow();
        DynamicItemSlotSelection conflictingBinding = new DynamicItemSlotSelection(
                overview.sequence(), Optional.of(FIRST_BINDING_ID));

        assertTransition(helper, server.receiveSelectionRequest(token, overview, binding -> false),
                DynamicItemSlotTransition.ACCEPTED, "server rejected the overview setup");
        assertTransition(helper, server.receiveSelectionRequest(token, conflictingBinding, binding -> true),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "server accepted one selection sequence for both overview and binding");
        assertTransition(helper, client.receiveSelectionAcknowledgement(token, conflictingBinding),
                DynamicItemSlotTransition.CLOSE_OPENING,
                "client accepted a binding ACK for its pending overview sequence");
        helper.assertTrue(client.selectedBindingId().isEmpty() &&
                !client.isBindingInteractive(FIRST_BINDING_ID),
                "conflicting overview ACK changed the client interaction state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void openingTokenAndSelectionCodecsRoundTrip(GameTestHelper helper) {
        DynamicItemSlotOpeningToken token = token(initialManifest());
        DynamicItemSlotSelection selection = new DynamicItemSlotSelection(3, Optional.of(FIRST_BINDING_ID));
        DynamicItemSlotSelection overview = new DynamicItemSlotSelection(4, Optional.empty());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.OTHER);
        try {
            DynamicItemSlotOpeningToken.STREAM_CODEC.encode(buffer, token);
            DynamicItemSlotSelection.STREAM_CODEC.encode(buffer, selection);
            DynamicItemSlotSelection.STREAM_CODEC.encode(buffer, overview);

            helper.assertTrue(token.equals(DynamicItemSlotOpeningToken.STREAM_CODEC.decode(buffer)),
                    "opening token codec changed the handshake identity");
            helper.assertTrue(selection.equals(DynamicItemSlotSelection.STREAM_CODEC.decode(buffer)),
                    "selection codec changed the request identity");
            helper.assertTrue(overview.equals(DynamicItemSlotSelection.STREAM_CODEC.decode(buffer)),
                    "selection codec added a binding to the overview request");
            helper.assertTrue(!buffer.isReadable(), "opening protocol codecs left unread bytes");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static void completeHandshake(GameTestHelper helper, DynamicItemSlotClientOpening client,
                                          DynamicItemSlotServerOpening server,
                                          DynamicItemSlotManifest manifest) {
        assertTransition(helper, server.beginManifest(manifest), DynamicItemSlotTransition.ACCEPTED,
                "server rejected manifest handshake setup");
        assertTransition(helper, client.receiveManifest(token(manifest), manifest), DynamicItemSlotTransition.ACCEPTED,
                "client rejected manifest handshake setup");
        Set<UUID> allBindingIds = manifest.bindings().stream()
                .map(DynamicItemSlotBinding::bindingId)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> presentBindingIds = manifest.bindings().stream()
                .filter(DynamicItemSlotBinding::present)
                .map(DynamicItemSlotBinding::bindingId)
                .collect(Collectors.toUnmodifiableSet());
        completePendingHandshake(helper, client, server, manifest, allBindingIds, presentBindingIds);
    }

    private static void completePendingHandshake(GameTestHelper helper, DynamicItemSlotClientOpening client,
                                                 DynamicItemSlotServerOpening server,
                                                 DynamicItemSlotManifest manifest, Set<UUID> allBindingIds,
                                                 Set<UUID> presentBindingIds) {
        DynamicItemSlotOpeningToken token = token(manifest);
        assertTransition(helper, client.completePreparation(token, allBindingIds, presentBindingIds),
                DynamicItemSlotTransition.ACCEPTED, "client rejected complete preparation");
        assertTransition(helper, server.receivePreparedAcknowledgement(token), DynamicItemSlotTransition.ACCEPTED,
                "server rejected PREPARED");
        assertTransition(helper, server.markDisabledSlotsAppended(token, allBindingIds),
                DynamicItemSlotTransition.ACCEPTED, "server rejected disabled append completion");
        assertTransition(helper, server.markFullSnapshotSent(token), DynamicItemSlotTransition.ACCEPTED,
                "server rejected full snapshot completion");
        assertTransition(helper, client.receiveActivation(token, server.selectedBindingId()),
                DynamicItemSlotTransition.ACCEPTED,
                "client rejected ACTIVATE");
        assertTransition(helper, server.receiveActivatedAcknowledgement(token), DynamicItemSlotTransition.ACCEPTED,
                "server rejected ACTIVATED");
    }

    private static void assertNotInteractive(GameTestHelper helper, DynamicItemSlotClientOpening client,
                                             DynamicItemSlotServerOpening server, UUID bindingId, String message) {
        helper.assertTrue(!client.isBindingInteractive(bindingId) && !server.isBindingInteractive(bindingId), message);
    }

    private static void assertTransition(GameTestHelper helper, DynamicItemSlotTransition actual,
                                         DynamicItemSlotTransition expected, String message) {
        helper.assertTrue(actual == expected, message + ": expected " + expected + " but received " + actual);
    }

    private static DynamicItemSlotClientOpening clientOpening() {
        return new DynamicItemSlotClientOpening(CONTAINER_ID, MENU_SESSION_ID, BASE_SLOT_COUNT);
    }

    private static DynamicItemSlotServerOpening serverOpening() {
        return new DynamicItemSlotServerOpening(CONTAINER_ID, MENU_SESSION_ID, BASE_SLOT_COUNT);
    }

    private static DynamicItemSlotManifest initialManifest() {
        return new DynamicItemSlotManifest(
                0,
                1,
                id(101),
                1,
                BASE_SLOT_COUNT,
                List.of(binding(FIRST_BINDING_ID, TARGET_ID, BASE_SLOT_COUNT, 9, true)));
    }

    private static DynamicItemSlotOpeningToken token(DynamicItemSlotManifest manifest) {
        return DynamicItemSlotOpeningToken.of(CONTAINER_ID, MENU_SESSION_ID, manifest);
    }

    private static DynamicItemSlotBinding binding(UUID bindingId, UUID targetId, int firstSlotId, int slotCount,
                                                  boolean present) {
        UUID targetIncarnation = new UUID(
                targetId.getMostSignificantBits(), Math.addExact(targetId.getLeastSignificantBits(), 1_000));
        return binding(bindingId, targetId, targetIncarnation, firstSlotId, slotCount, present);
    }

    private static DynamicItemSlotBinding binding(UUID bindingId, UUID targetId, UUID targetIncarnation,
                                                  int firstSlotId, int slotCount, boolean present) {
        return new DynamicItemSlotBinding(
                bindingId, targetId, targetIncarnation, firstSlotId, slotCount, present);
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
