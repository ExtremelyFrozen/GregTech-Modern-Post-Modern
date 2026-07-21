package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEOutputWaitingListProtocolTest {

    private static final String BATCH = "MEOutputWaitingListProtocol";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void menuChallengeIsOpeningBoundAndSingleUse(GameTestHelper helper) {
        MEOutputWaitingListMenuSession session = new MEOutputWaitingListMenuSession();
        UUID openingId = UUID.randomUUID();
        UUID challengeId = session.issue(openingId, 3);

        helper.assertFalse(session.consume(UUID.randomUUID(), 3, challengeId),
                "waiting-list menu challenge authenticated a different opening");
        helper.assertFalse(session.consume(openingId, 4, challengeId),
                "waiting-list menu challenge authenticated a different request sequence");
        helper.assertFalse(session.consume(openingId, 3, UUID.randomUUID()),
                "waiting-list menu challenge authenticated a different nonce");
        helper.assertTrue(session.consume(openingId, 3, challengeId),
                "exact waiting-list menu challenge response was rejected");
        helper.assertFalse(session.consume(openingId, 3, challengeId),
                "consumed waiting-list menu challenge was accepted twice");

        UUID replacementChallengeId = session.issue(openingId, 4);
        helper.assertFalse(replacementChallengeId.equals(challengeId),
                "new waiting-list menu challenge reused its consumed nonce");
        assertIllegalArgument(helper, () -> session.issue(openingId, -1),
                "waiting-list menu session issued a negative request sequence");
        assertIllegalArgument(helper, () -> session.consume(openingId, -1, replacementChallengeId),
                "waiting-list menu session consumed a negative request sequence");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void codecPreservesComponentsLongAmountsAndBoundsChunkAllocation(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        ItemStack namedDiamond = new ItemStack(Items.DIAMOND);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Waiting Diamond"));
        long amount = Long.MAX_VALUE - 32;
        MEOutputWaitingListUpdate source = MEOutputWaitingListUpdate.full(17, 0, 1,
                List.of(new MEOutputWaitingListEntry(requireKey(namedDiamond), amount)));

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            MEOutputWaitingListUpdate.STREAM_CODEC.encode(buffer, source);
            MEOutputWaitingListUpdate decoded = MEOutputWaitingListUpdate.STREAM_CODEC.decode(buffer);
            MEOutputWaitingListEntry entry = decoded.entries().getFirst();
            helper.assertTrue(entry.amount() == amount, "waiting-list codec truncated a long amount");
            helper.assertTrue(entry.key() instanceof AEItemKey itemKey &&
                    Component.literal("Waiting Diamond").equals(
                            itemKey.toStack(1).get(DataComponents.CUSTOM_NAME)),
                    "waiting-list codec discarded AE item data components");
        } finally {
            buffer.release();
        }

        RegistryFriendlyByteBuf oversized = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            oversized.writeByte(0);
            oversized.writeVarLong(0);
            oversized.writeVarInt(0);
            oversized.writeVarInt(1);
            oversized.writeVarInt(MEOutputWaitingListUpdate.MAX_CHUNK_ENTRIES + 1);
            assertRejected(helper, () -> MEOutputWaitingListUpdate.STREAM_CODEC.decode(oversized),
                    "waiting-list codec allocated an oversized chunk");
        } finally {
            oversized.release();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fullChunksApplyAtomicallyOutOfOrderAndRejectDuplicates(GameTestHelper helper) {
        List<MEOutputWaitingListEntry> entries = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            ItemStack stack = new ItemStack(Items.STONE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Waiting " + index));
            entries.add(new MEOutputWaitingListEntry(requireKey(stack), index + 1L));
        }
        MEOutputWaitingListUpdate first = MEOutputWaitingListUpdate.full(4, 0, 2,
                entries.subList(0, MEOutputWaitingListUpdate.MAX_CHUNK_ENTRIES));
        MEOutputWaitingListUpdate second = MEOutputWaitingListUpdate.full(4, 1, 2,
                entries.subList(MEOutputWaitingListUpdate.MAX_CHUNK_ENTRIES, entries.size()));
        MEOutputWaitingListClientState state = new MEOutputWaitingListClientState();

        helper.assertTrue(state.accept(second) ==
                MEOutputWaitingListClientState.ApplyResult.WAITING_FOR_CHUNKS,
                "out-of-order full tail applied before its head");
        helper.assertTrue(state.entries().isEmpty() && state.revision() == -1,
                "partial full publication leaked into client state");
        helper.assertTrue(state.accept(second) == MEOutputWaitingListClientState.ApplyResult.IGNORED,
                "exact duplicate full chunk was not rejected");
        helper.assertTrue(state.accept(first) == MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "complete out-of-order full publication did not apply");
        helper.assertTrue(state.revision() == 4 && state.entries().equals(entries),
                "full publication changed first-insertion order");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void currentRevisionFullRequiresAuthoritativeEntryPoint(GameTestHelper helper) {
        MEOutputWaitingListEntry apple = entry(Items.APPLE, 1);
        MEOutputWaitingListEntry bread = entry(Items.BREAD, 2);
        MEOutputWaitingListEntry carrot = entry(Items.CARROT, 3);
        MEOutputWaitingListEntry diamond = entry(Items.DIAMOND, 4);
        List<MEOutputWaitingListEntry> initial = List.of(apple, bread);
        List<MEOutputWaitingListEntry> replacement = List.of(carrot, diamond);
        MEOutputWaitingListClientState state = new MEOutputWaitingListClientState();
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.full(4, 0, 1, initial)) ==
                MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "initial waiting-list full publication did not apply");

        MEOutputWaitingListUpdate first = MEOutputWaitingListUpdate.full(4, 0, 2, List.of(carrot));
        MEOutputWaitingListUpdate second = MEOutputWaitingListUpdate.full(4, 1, 2, List.of(diamond));
        helper.assertTrue(state.accept(first) == MEOutputWaitingListClientState.ApplyResult.IGNORED,
                "current-revision full publication applied without explicit resync permission");
        helper.assertTrue(state.revision() == 4 && state.entries().equals(initial),
                "ignored current-revision full publication changed client state");
        helper.assertTrue(state.acceptAuthoritativeFull(first) ==
                MEOutputWaitingListClientState.ApplyResult.WAITING_FOR_CHUNKS,
                "authoritative current-revision full publication did not await its remaining chunk");
        helper.assertTrue(state.revision() == 4 && state.entries().equals(initial),
                "partial authoritative current-revision full publication leaked into client state");
        helper.assertTrue(state.acceptAuthoritativeFull(second) ==
                MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "authoritative current-revision full publication did not apply after all chunks arrived");
        helper.assertTrue(state.revision() == 4 && state.entries().equals(replacement),
                "authoritative current-revision full publication did not atomically replace client state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void authoritativeLowerFullReplacesHigherPendingPublicationAtomically(GameTestHelper helper) {
        MEOutputWaitingListEntry apple = entry(Items.APPLE, 1);
        MEOutputWaitingListEntry bread = entry(Items.BREAD, 2);
        MEOutputWaitingListEntry carrot = entry(Items.CARROT, 3);
        MEOutputWaitingListEntry diamond = entry(Items.DIAMOND, 4);
        List<MEOutputWaitingListEntry> initial = List.of(apple, bread);
        List<MEOutputWaitingListEntry> replacement = List.of(carrot, diamond);
        MEOutputWaitingListClientState state = new MEOutputWaitingListClientState();
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.full(4, 0, 1, initial)) ==
                MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "initial waiting-list full publication did not apply");

        MEOutputWaitingListUpdate stale = MEOutputWaitingListUpdate.full(3, 0, 1, replacement);
        helper.assertTrue(state.accept(stale) == MEOutputWaitingListClientState.ApplyResult.IGNORED,
                "ordinary lower-revision full publication was not ignored");
        helper.assertTrue(state.revision() == 4 && state.entries().equals(initial),
                "ignored lower-revision full publication changed client state");
        assertIllegalArgument(helper,
                () -> state.acceptAuthoritativeFull(MEOutputWaitingListUpdate.delta(5, 0, 1,
                        List.of(new MEOutputWaitingListEntry(apple.key(), 5)))),
                "authoritative full entry point accepted a delta publication");

        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.delta(5, 0, 2,
                List.of(new MEOutputWaitingListEntry(apple.key(), 5)))) ==
                MEOutputWaitingListClientState.ApplyResult.WAITING_FOR_CHUNKS,
                "higher-revision delta publication did not remain pending");
        MEOutputWaitingListUpdate first = MEOutputWaitingListUpdate.full(3, 0, 2, List.of(carrot));
        MEOutputWaitingListUpdate conflicting = MEOutputWaitingListUpdate.full(3, 0, 2,
                List.of(new MEOutputWaitingListEntry(carrot.key(), 30)));
        MEOutputWaitingListUpdate second = MEOutputWaitingListUpdate.full(3, 1, 2, List.of(diamond));
        helper.assertTrue(state.acceptAuthoritativeFull(first) ==
                MEOutputWaitingListClientState.ApplyResult.WAITING_FOR_CHUNKS,
                "authoritative lower-revision full publication did not replace the higher pending delta");
        helper.assertTrue(state.revision() == 4 && state.entries().equals(initial),
                "partial authoritative lower-revision full publication changed applied client state");
        helper.assertTrue(state.acceptAuthoritativeFull(first) ==
                MEOutputWaitingListClientState.ApplyResult.IGNORED,
                "identical authoritative full chunk was not ignored");
        helper.assertTrue(state.acceptAuthoritativeFull(conflicting) ==
                MEOutputWaitingListClientState.ApplyResult.REQUEST_FULL,
                "conflicting authoritative full chunk did not request a replacement snapshot");
        helper.assertTrue(!state.hasPendingPublication() && state.revision() == 4 &&
                state.entries().equals(initial),
                "conflicting authoritative full chunk changed applied state or remained pending");

        helper.assertTrue(state.acceptAuthoritativeFull(first) ==
                MEOutputWaitingListClientState.ApplyResult.WAITING_FOR_CHUNKS,
                "authoritative lower-revision full publication could not restart after conflict");
        helper.assertTrue(state.revision() == 4 && state.entries().equals(initial),
                "restarted authoritative publication changed state before completion");
        helper.assertTrue(state.acceptAuthoritativeFull(second) ==
                MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "complete authoritative lower-revision full publication did not apply");
        helper.assertTrue(!state.hasPendingPublication() && state.revision() == 3 &&
                state.entries().equals(replacement),
                "authoritative lower-revision full publication did not atomically replace client state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void deltaPreservesOperationOrderAndRevisionGapRequestsFull(GameTestHelper helper) {
        MEOutputWaitingListEntry a = entry(Items.APPLE, 1);
        MEOutputWaitingListEntry b = entry(Items.BREAD, 2);
        MEOutputWaitingListEntry c = entry(Items.CARROT, 3);
        MEOutputWaitingListEntry d = entry(Items.DIAMOND, 4);
        MEOutputWaitingListClientState state = new MEOutputWaitingListClientState();
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.full(0, 0, 1, List.of(a, b, c, d))) ==
                MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "initial waiting-list full publication did not apply");

        MEOutputWaitingListEntry removeB = new MEOutputWaitingListEntry(b.key(), 0);
        MEOutputWaitingListEntry removeA = new MEOutputWaitingListEntry(a.key(), 0);
        MEOutputWaitingListEntry readdA = new MEOutputWaitingListEntry(a.key(), 10);
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.delta(1, 0, 1,
                List.of(removeB, removeA, readdA))) == MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "contiguous waiting-list delta did not apply");
        helper.assertTrue(state.entries().size() == 3 && state.entries().get(0).key().equals(c.key()) &&
                state.entries().get(1).key().equals(d.key()) && state.entries().get(2).key().equals(a.key()) &&
                state.entries().get(2).amount() == 10,
                "delta did not preserve tombstone and re-add operation order");

        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.delta(3, 0, 1,
                List.of(new MEOutputWaitingListEntry(d.key(), 41)))) ==
                MEOutputWaitingListClientState.ApplyResult.REQUEST_FULL,
                "waiting-list revision gap did not request a full resync");
        helper.assertTrue(state.revision() == 1, "rejected revision gap changed the applied revision");
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.full(7, 0, 1, List.of(c))) ==
                MEOutputWaitingListClientState.ApplyResult.APPLIED && state.revision() == 7,
                "full resync did not recover from a delta revision gap");
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.delta(7, 0, 1,
                List.of(new MEOutputWaitingListEntry(c.key(), 5)))) ==
                MEOutputWaitingListClientState.ApplyResult.IGNORED,
                "stale waiting-list delta was not ignored");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void deltaChunksHandleOrderingDuplicatesConflictsAndRevisionGaps(GameTestHelper helper) {
        MEOutputWaitingListEntry apple = entry(Items.APPLE, 1);
        MEOutputWaitingListEntry bread = entry(Items.BREAD, 2);
        List<MEOutputWaitingListEntry> initial = List.of(apple, bread);
        MEOutputWaitingListClientState state = new MEOutputWaitingListClientState();
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.full(0, 0, 1, initial)) ==
                MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "initial waiting-list full publication did not apply");

        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.delta(2, 0, 1,
                List.of(new MEOutputWaitingListEntry(bread.key(), 20)))) ==
                MEOutputWaitingListClientState.ApplyResult.REQUEST_FULL,
                "non-contiguous waiting-list delta did not request a full resync");
        helper.assertTrue(state.revision() == 0 && state.entries().equals(initial),
                "rejected waiting-list revision gap changed client state");

        MEOutputWaitingListUpdate first = MEOutputWaitingListUpdate.delta(1, 0, 2,
                List.of(new MEOutputWaitingListEntry(apple.key(), 0)));
        MEOutputWaitingListUpdate second = MEOutputWaitingListUpdate.delta(1, 1, 2,
                List.of(new MEOutputWaitingListEntry(apple.key(), 10)));
        helper.assertTrue(state.accept(second) ==
                MEOutputWaitingListClientState.ApplyResult.WAITING_FOR_CHUNKS,
                "out-of-order delta tail did not await its head");
        helper.assertTrue(state.accept(second) == MEOutputWaitingListClientState.ApplyResult.IGNORED,
                "exact duplicate delta chunk was not ignored");
        helper.assertTrue(state.revision() == 0 && state.entries().equals(initial),
                "partial delta publication leaked into client state");
        helper.assertTrue(state.accept(first) == MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "complete out-of-order delta publication did not apply");
        helper.assertTrue(state.revision() == 1 && state.entries().equals(List.of(
                bread, new MEOutputWaitingListEntry(apple.key(), 10))),
                "delta chunks were not applied in chunk-index order");

        List<MEOutputWaitingListEntry> applied = state.entries();
        MEOutputWaitingListUpdate original = MEOutputWaitingListUpdate.delta(2, 0, 2,
                List.of(new MEOutputWaitingListEntry(bread.key(), 20)));
        MEOutputWaitingListUpdate conflicting = MEOutputWaitingListUpdate.delta(2, 0, 2,
                List.of(new MEOutputWaitingListEntry(bread.key(), 21)));
        helper.assertTrue(state.accept(original) ==
                MEOutputWaitingListClientState.ApplyResult.WAITING_FOR_CHUNKS,
                "partial delta publication did not await its remaining chunk");
        helper.assertTrue(state.accept(conflicting) == MEOutputWaitingListClientState.ApplyResult.REQUEST_FULL,
                "conflicting duplicate delta chunk did not request a full resync");
        helper.assertTrue(!state.hasPendingPublication(),
                "conflicting duplicate delta chunk left a partial publication pending");
        helper.assertTrue(state.revision() == 1 && state.entries().equals(applied),
                "conflicting duplicate delta chunk changed applied client state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void crossChunkFullDuplicateKeyFailsOnlyWhenPublicationCompletes(GameTestHelper helper) {
        MEOutputWaitingListEntry initial = entry(Items.APPLE, 1);
        MEOutputWaitingListEntry repeated = entry(Items.BREAD, 2);
        MEOutputWaitingListClientState state = new MEOutputWaitingListClientState();
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.full(0, 0, 1, List.of(initial))) ==
                MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "initial waiting-list full publication did not apply");

        MEOutputWaitingListUpdate first = MEOutputWaitingListUpdate.full(1, 0, 2, List.of(repeated));
        MEOutputWaitingListUpdate second = MEOutputWaitingListUpdate.full(1, 1, 2,
                List.of(new MEOutputWaitingListEntry(repeated.key(), 3)));
        helper.assertTrue(state.accept(first) ==
                MEOutputWaitingListClientState.ApplyResult.WAITING_FOR_CHUNKS,
                "cross-chunk duplicate full key was rejected before publication completion");
        assertIllegalArgument(helper, () -> state.accept(second),
                "complete full publication accepted a key repeated across chunks");
        helper.assertTrue(state.revision() == 0 && state.entries().equals(List.of(initial)),
                "rejected cross-chunk duplicate full publication changed applied client state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void discardPendingPublicationPreservesAppliedState(GameTestHelper helper) {
        MEOutputWaitingListEntry apple = entry(Items.APPLE, 1);
        MEOutputWaitingListEntry bread = entry(Items.BREAD, 2);
        List<MEOutputWaitingListEntry> initial = List.of(apple, bread);
        MEOutputWaitingListClientState state = new MEOutputWaitingListClientState();
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.full(0, 0, 1, initial)) ==
                MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "initial waiting-list full publication did not apply");
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.delta(1, 0, 2,
                List.of(new MEOutputWaitingListEntry(apple.key(), 5)))) ==
                MEOutputWaitingListClientState.ApplyResult.WAITING_FOR_CHUNKS,
                "partial delta publication did not remain pending");
        helper.assertTrue(state.hasPendingPublication(),
                "client state did not report its partial waiting-list publication");

        state.discardPendingPublication();
        helper.assertTrue(!state.hasPendingPublication(),
                "discarded waiting-list publication remained pending");
        helper.assertTrue(state.revision() == 0 && state.entries().equals(initial),
                "discarding a pending publication changed applied client state");
        MEOutputWaitingListEntry updatedBread = new MEOutputWaitingListEntry(bread.key(), 9);
        helper.assertTrue(state.accept(MEOutputWaitingListUpdate.delta(1, 0, 1, List.of(updatedBread))) ==
                MEOutputWaitingListClientState.ApplyResult.APPLIED,
                "client state could not apply a replacement publication after discarding pending chunks");
        helper.assertTrue(state.revision() == 1 && state.entries().equals(List.of(apple, updatedBread)),
                "replacement publication after pending discard produced the wrong client state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void constructorsRejectInvalidFullDeltaAndChunkContracts(GameTestHelper helper) {
        MEOutputWaitingListEntry entry = entry(Items.STONE, 1);
        assertRejected(helper,
                () -> MEOutputWaitingListUpdate.full(0, 0, 1,
                        List.of(new MEOutputWaitingListEntry(entry.key(), 0))),
                "full waiting-list update accepted a zero amount");
        assertRejected(helper,
                () -> MEOutputWaitingListUpdate.delta(1, 0, 1, List.of()),
                "delta waiting-list update accepted no real changes");
        assertRejected(helper,
                () -> MEOutputWaitingListUpdate.delta(1, 1, 2, List.of()),
                "multi-chunk delta waiting-list update accepted an empty chunk");
        MEOutputWaitingListUpdate emptyFull = MEOutputWaitingListUpdate.full(0, 0, 1, List.of());
        helper.assertTrue(emptyFull.entries().isEmpty(),
                "single full chunk did not represent an empty waiting list");
        assertRejected(helper,
                () -> MEOutputWaitingListUpdate.full(0, 0, 2, List.of()),
                "multi-chunk full waiting-list update accepted an empty chunk");
        assertRejected(helper,
                () -> MEOutputWaitingListUpdate.full(0, 1, 1, List.of(entry)),
                "waiting-list update accepted an out-of-range chunk index");

        List<MEOutputWaitingListEntry> oversized = new ArrayList<>();
        for (int index = 0; index <= MEOutputWaitingListUpdate.MAX_CHUNK_ENTRIES; index++) {
            ItemStack stack = new ItemStack(Items.STONE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Oversized " + index));
            oversized.add(new MEOutputWaitingListEntry(requireKey(stack), 1));
        }
        assertRejected(helper, () -> MEOutputWaitingListUpdate.full(0, 0, 2, oversized),
                "waiting-list update accepted more than 64 entries in one chunk");
        helper.succeed();
    }

    private static MEOutputWaitingListEntry entry(Item item, long amount) {
        return new MEOutputWaitingListEntry(requireKey(new ItemStack(item)), amount);
    }

    private static AEKey requireKey(ItemStack stack) {
        AEKey key = AEItemKey.of(stack);
        if (key == null) {
            throw new IllegalStateException("Test item did not create an AE key: " + stack);
        }
        return key;
    }

    private static void assertRejected(GameTestHelper helper, Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (RuntimeException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static void assertIllegalArgument(GameTestHelper helper, Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }
}
