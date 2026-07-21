package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.PowerSubstationMachine;
import com.gregtechceu.gtceu.common.network.packets.SPacketMEOutputWaitingListSessionToClient;
import com.gregtechceu.gtceu.common.network.packets.SPacketMEOutputWaitingListToClient;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEOutputWaitingListServerProtocolTest {

    private static final String BATCH = "MEOutputWaitingListServerProtocol";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void publisherBoundsChunksAndPreservesDeltaOperationOrder(GameTestHelper helper) {
        List<MEOutputWaitingListEntry> entries = entries(130);
        List<MEOutputWaitingListUpdate> full = MEOutputWaitingListPublisher.chunkUpdates(
                MEOutputWaitingListUpdate.Mode.FULL, 9, entries);
        helper.assertTrue(full.size() == 3 && full.get(0).entries().size() == 64 &&
                full.get(1).entries().size() == 64 && full.get(2).entries().size() == 2,
                "waiting-list publisher did not enforce 64-entry chunks");
        helper.assertTrue(full.get(0).revision() == 9 && full.get(2).chunkIndex() == 2 &&
                full.get(2).chunkCount() == 3,
                "waiting-list publisher changed full revision or chunk identity");

        MEOutputWaitingListEntry tombstone = new MEOutputWaitingListEntry(entries.getFirst().key(), 0);
        MEOutputWaitingListEntry readded = new MEOutputWaitingListEntry(entries.getFirst().key(), 500);
        List<MEOutputWaitingListUpdate> delta = MEOutputWaitingListPublisher.chunkUpdates(
                MEOutputWaitingListUpdate.Mode.DELTA, 10, List.of(tombstone, readded));
        helper.assertTrue(delta.size() == 1 && delta.getFirst().entries().equals(List.of(tombstone, readded)),
                "waiting-list publisher coalesced ordered remove-readd operations");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void publisherPreservesRemoveReaddAppendOrderAcrossFlushes(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wait-delta");
        PowerSubstationMachine rootMachine = createController(new BlockPos(4, 0, 0));
        MEOutputBusPartMachine owner = createOutput();
        KeyStorage storage = new KeyStorage();
        AEItemKey apple = itemKey(Items.APPLE);
        AEItemKey bread = itemKey(Items.BREAD);
        storage.put(apple, 5);
        List<SPacketMEOutputWaitingListToClient> sent = new ArrayList<>();
        MEOutputWaitingListPublisher publisher = new MEOutputWaitingListPublisher(
                owner, storage, (recipient, packet) -> sent.add(packet), 2);
        TestReceiver receiver = new TestReceiver(owner, publisher);
        player.containerMenu = menu(10, player, holderWithReceivers(rootMachine, receiver));
        UUID openingId = UUID.randomUUID();
        helper.assertTrue(publisher.requestFull(player, openingId, 0),
                "valid waiting-list opening did not receive its initial full publication");
        sent.clear();

        storage.remove(apple);
        storage.put(apple, 7);
        storage.put(bread, 2);

        publisher.flushPendingChanges();
        helper.assertTrue(publisher.revision() == 1 && sent.size() == 1,
                "first bounded flush did not produce exactly one delta revision");
        MEOutputWaitingListUpdate first = sent.getFirst().update();
        helper.assertTrue(first.mode() == MEOutputWaitingListUpdate.Mode.DELTA &&
                first.entries().equals(List.of(
                        new MEOutputWaitingListEntry(apple, 0),
                        new MEOutputWaitingListEntry(apple, 7))),
                "first bounded flush did not preserve remove-readd order");

        publisher.flushPendingChanges();
        helper.assertTrue(publisher.revision() == 2 && sent.size() == 2,
                "second bounded flush did not commit the remaining delta revision");
        MEOutputWaitingListUpdate second = sent.get(1).update();
        helper.assertTrue(second.mode() == MEOutputWaitingListUpdate.Mode.DELTA &&
                second.entries().equals(List.of(new MEOutputWaitingListEntry(bread, 2))),
                "second bounded flush did not preserve the appended key");
        publisher.flushPendingChanges();
        helper.assertTrue(publisher.revision() == 2 && sent.size() == 2,
                "empty publisher flush emitted a redundant revision");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fullRequestsKeepLatestPendingSequenceAndCommittedRevision(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wait-pending");
        PowerSubstationMachine rootMachine = createController(new BlockPos(4, 0, 0));
        MEOutputBusPartMachine owner = createOutput();
        KeyStorage storage = new KeyStorage();
        AEItemKey apple = itemKey(Items.APPLE);
        AEItemKey bread = itemKey(Items.BREAD);
        AEItemKey carrot = itemKey(Items.CARROT);
        storage.put(apple, 5);
        List<SPacketMEOutputWaitingListToClient> sent = new ArrayList<>();
        MEOutputWaitingListPublisher publisher = new MEOutputWaitingListPublisher(
                owner, storage, (recipient, packet) -> sent.add(packet), 2);
        TestReceiver receiver = new TestReceiver(owner, publisher);
        player.containerMenu = menu(11, player, holderWithReceivers(rootMachine, receiver));
        UUID openingId = UUID.randomUUID();

        helper.assertTrue(publisher.requestFull(player, openingId, 0) && sent.size() == 1,
                "initial waiting-list full request was rejected");
        helper.assertTrue(publisher.requestFull(player, openingId, 0) && sent.size() == 1,
                "replayed waiting-list request emitted another full publication");

        helper.runAfterDelay(20, () -> {
            storage.remove(apple);
            storage.put(apple, 7);
            storage.put(bread, 8);
            storage.put(carrot, 9);
            helper.assertTrue(publisher.requestFull(player, openingId, 3) && sent.size() == 1,
                    "full request bypassed an uncommitted delta backlog after cooldown expiry");
            helper.assertTrue(publisher.requestFull(player, openingId, 2) &&
                    publisher.requestFull(player, openingId, 3) && sent.size() == 1,
                    "replayed or descending request sequence changed pending publication state");
            publisher.serverTick();
            helper.assertTrue(publisher.revision() == 1 && sent.size() == 1,
                    "publisher sent a full snapshot before committing the complete delta backlog");
            publisher.serverTick();
            helper.assertTrue(publisher.revision() == 2 && sent.size() == 2,
                    "publisher did not emit one full after committing the complete delta backlog");
            SPacketMEOutputWaitingListToClient pending = sent.get(1);
            helper.assertTrue(pending.openingId().equals(openingId) && pending.requestSequence() == 3,
                    "pending full publication did not retain only the latest request sequence");
            helper.assertTrue(pending.update().mode() == MEOutputWaitingListUpdate.Mode.FULL &&
                    pending.update().revision() == 2 && pending.update().entries().equals(List.of(
                            new MEOutputWaitingListEntry(apple, 7),
                            new MEOutputWaitingListEntry(bread, 8),
                            new MEOutputWaitingListEntry(carrot, 9))),
                    "pending full publication did not match the fully committed remove-readd revision and order");
            helper.succeed();
        });
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void successfulFullCooldownExpiresAfterTwentyTicks(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wait-cooldown");
        PowerSubstationMachine rootMachine = createController(new BlockPos(4, 0, 0));
        MEOutputBusPartMachine owner = createOutput();
        List<SPacketMEOutputWaitingListToClient> sent = new ArrayList<>();
        MEOutputWaitingListPublisher publisher = new MEOutputWaitingListPublisher(
                owner, new KeyStorage(), (recipient, packet) -> sent.add(packet));
        player.containerMenu = menu(17, player,
                holderWithReceivers(rootMachine, new TestReceiver(owner, publisher)));

        helper.assertTrue(publisher.requestFull(player, UUID.randomUUID(), 0) && sent.size() == 1 &&
                publisher.cooldownCount() == 1,
                "successful full publication did not create one cooldown entry");
        player.closeContainer();
        publisher.serverTick();
        helper.assertTrue(publisher.subscriptionCount() == 0 && publisher.cooldownCount() == 1,
                "closing the menu removed cooldown state before its TTL elapsed");

        helper.runAfterDelay(19, () -> {
            publisher.serverTick();
            helper.assertTrue(publisher.cooldownCount() == 1,
                    "full-publication cooldown expired before tick 20");
        });
        helper.runAfterDelay(20, () -> {
            publisher.serverTick();
            helper.assertTrue(publisher.cooldownCount() == 0,
                    "full-publication cooldown survived its 20-tick TTL");
            helper.succeed();
        });
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void newMenuInstanceWithSameContainerIdReplacesOpening(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wait-menu");
        PowerSubstationMachine rootMachine = createController(new BlockPos(4, 0, 0));
        MEOutputBusPartMachine owner = createOutput();
        List<SPacketMEOutputWaitingListToClient> sent = new ArrayList<>();
        MEOutputWaitingListPublisher publisher = new MEOutputWaitingListPublisher(
                owner, new KeyStorage(), (recipient, packet) -> sent.add(packet));
        UUID firstOpening = UUID.randomUUID();
        UUID secondOpening = UUID.randomUUID();

        ModularUIContainerMenu firstMenu = menu(12, player,
                holderWithReceivers(rootMachine, new TestReceiver(owner, publisher)));
        player.containerMenu = firstMenu;
        helper.assertTrue(publisher.requestFull(player, firstOpening, 0) && sent.size() == 1,
                "first menu opening did not receive its full publication");

        ModularUIContainerMenu secondMenu = menu(12, player,
                holderWithReceivers(rootMachine, new TestReceiver(owner, publisher)));
        player.containerMenu = secondMenu;
        helper.assertTrue(firstMenu != secondMenu && publisher.requestFull(player, secondOpening, 0),
                "new menu instance reusing the container id did not replace the opening");
        helper.assertTrue(publisher.subscriptionCount() == 1 && sent.size() == 1,
                "menu replacement retained two subscriptions or bypassed full-request cooldown");

        helper.runAfterDelay(20, () -> {
            publisher.serverTick();
            helper.assertTrue(sent.size() == 2 && sent.get(1).openingId().equals(secondOpening),
                    "replacement menu did not receive its pending full publication");
            helper.assertTrue(sent.get(1).requestSequence() == 0 &&
                    !sent.get(1).openingId().equals(firstOpening),
                    "replacement menu publication retained the old opening identity");
            helper.succeed();
        });
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void routeUsesControllerRootAndExactPartIdentity(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wait-route");
        PowerSubstationMachine rootMachine = createController(new BlockPos(4, 0, 0));
        MEOutputBusPartMachine firstOwner = createOutput(BlockPos.ZERO);
        MEOutputBusPartMachine secondOwner = createOutput(new BlockPos(1, 0, 0));
        TestReceiver firstReceiver = new TestReceiver(firstOwner, firstOwner.getWaitingListPublisher());
        TestReceiver secondReceiver = new TestReceiver(secondOwner, secondOwner.getWaitingListPublisher());
        player.containerMenu = menu(13, player,
                holderWithReceivers(rootMachine, firstReceiver, secondReceiver));
        MEOutputWaitingListTarget firstTarget = firstOwner.getWaitingListTarget();
        MEOutputWaitingListTarget secondTarget = secondOwner.getWaitingListTarget();

        MEOutputWaitingListReceiver resolved = MEOutputWaitingListRoute.resolve(player, 13, firstTarget);
        helper.assertTrue(resolved == firstReceiver &&
                MEOutputWaitingListRoute.resolve(player, 13, secondOwner) == secondReceiver,
                "controller menu did not distinguish two position-scoped part receivers");
        helper.assertTrue(MEOutputWaitingListRoute.resolveForAction(player, rootMachine, firstTarget) == firstReceiver,
                "action route did not separate the controller root from its part target");
        helper.assertTrue(MEOutputWaitingListRoute.resolveSessionForAction(
                player, rootMachine, firstTarget) == firstReceiver,
                "action route did not retain the exact menu-session receiver capability");
        helper.assertTrue(MEOutputWaitingListRoute.resolve(player, 14, firstTarget) == null,
                "waiting-list route accepted a different container id");

        MEOutputWaitingListTarget wrongPosition = new MEOutputWaitingListTarget(
                new BlockPos(2, 0, 0), firstTarget.machineDefinitionId(), firstTarget.incarnation());
        MEOutputWaitingListTarget wrongDefinition = new MEOutputWaitingListTarget(
                firstTarget.pos(), GTAEMachines.ITEM_IMPORT_BUS_ME.getId(), firstTarget.incarnation());
        MEOutputWaitingListTarget wrongIncarnation = new MEOutputWaitingListTarget(
                firstTarget.pos(), firstTarget.machineDefinitionId(), UUID.randomUUID());
        helper.assertTrue(MEOutputWaitingListRoute.resolve(player, 13, wrongPosition) == null &&
                MEOutputWaitingListRoute.resolve(player, 13, wrongDefinition) == null &&
                MEOutputWaitingListRoute.resolve(player, 13, wrongIncarnation) == null,
                "waiting-list route accepted an incorrect target identity field");

        MEOutputBusPartMachine replacement = createOutput(BlockPos.ZERO);
        helper.assertTrue(!replacement.getWaitingListTarget().incarnation().equals(firstTarget.incarnation()) &&
                MEOutputWaitingListRoute.resolve(player, 13, replacement) == null,
                "waiting-list route accepted a replacement machine at the same position");
        PowerSubstationMachine wrongRoot = createController(new BlockPos(5, 0, 0));
        helper.assertTrue(MEOutputWaitingListRoute.resolveForAction(player, wrongRoot, firstTarget) == null,
                "waiting-list action route accepted a different controller root");

        UUID openingId = UUID.randomUUID();
        MEOutputWaitingListUpdate update = MEOutputWaitingListUpdate.full(0, 0, 1, List.of());
        resolved.applyWaitingListUpdate(openingId, 7, update);
        helper.assertTrue(openingId.equals(firstReceiver.receivedOpeningId) &&
                firstReceiver.receivedRequestSequence == 7 && update.equals(firstReceiver.receivedUpdate),
                "resolved receiver did not receive the exact opening, sequence, and update");
        UUID sessionId = UUID.randomUUID();
        firstReceiver.applyWaitingListMenuSession(openingId, 8, sessionId);
        helper.assertTrue(openingId.equals(firstReceiver.receivedOpeningId) &&
                firstReceiver.receivedRequestSequence == 8 && sessionId.equals(firstReceiver.receivedSessionId),
                "session receiver did not retain the exact opening, sequence, and challenge nonce");

        TestReceiver duplicateA = new TestReceiver(firstOwner, firstOwner.getWaitingListPublisher());
        TestReceiver duplicateB = new TestReceiver(firstOwner, firstOwner.getWaitingListPublisher());
        player.containerMenu = menu(14, player, holderWithReceivers(rootMachine, duplicateA, duplicateB));
        helper.assertTrue(MEOutputWaitingListRoute.resolve(player, 14, firstTarget) == null,
                "waiting-list route accepted duplicate receiver element ids");

        player.containerMenu = menu(15, player, new TestMachineHolder(rootMachine));
        helper.assertTrue(MEOutputWaitingListRoute.resolve(player, 15, firstTarget) == null,
                "waiting-list route accepted a menu without the requested receiver");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void actionAndPacketIdentityAreDeterministicAndBounded(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wait-action");
        PowerSubstationMachine rootMachine = createController(new BlockPos(4, 0, 0));
        MEOutputBusPartMachine owner = createOutput();
        List<SPacketMEOutputWaitingListToClient> sent = new ArrayList<>();
        MEOutputWaitingListPublisher publisher = new MEOutputWaitingListPublisher(
                owner, new KeyStorage(), (recipient, packet) -> sent.add(packet));
        TestReceiver firstReceiver = new TestReceiver(owner, publisher);
        player.containerMenu = menu(16, player, holderWithReceivers(rootMachine, firstReceiver));
        MEOutputWaitingListTarget target = owner.getWaitingListTarget();
        UUID openingId = UUID.randomUUID();
        SyncActionData first = MEOutputWaitingListActions.createRequestFullAction(target, openingId, 0);
        SyncActionData second = MEOutputWaitingListActions.createRequestFullAction(target, openingId, 0);
        helper.assertTrue(first.equals(second) && first.sequence() == 0,
                "full-state action changed payload or correlation sequence for one target opening");

        SyncActionContext validContext = SyncActionContext.machine(
                player, rootMachine, first, rootMachine.getBlockPos());
        helper.assertTrue(SyncActionDispatchers.server().dispatch(validContext) && sent.isEmpty() &&
                publisher.subscriptionCount() == 0,
                "unauthenticated request-full action registered before the server menu challenge");
        UUID firstChallengeId = firstReceiver.issuedChallengeId;
        SyncActionData authenticated = MEOutputWaitingListActions.createRequestFullAction(
                target, openingId, 0, firstChallengeId);
        SyncActionContext authenticatedContext = SyncActionContext.machine(
                player, rootMachine, authenticated, rootMachine.getBlockPos());
        helper.assertTrue(SyncActionDispatchers.server().dispatch(authenticatedContext) && sent.size() == 1 &&
                firstReceiver.requestCount == 1,
                "server-menu challenge response did not execute through the real controller context");
        SPacketMEOutputWaitingListToClient dispatched = sent.getFirst();
        helper.assertTrue(dispatched.target().equals(target) && dispatched.openingId().equals(openingId) &&
                dispatched.requestSequence() == 0,
                "dispatcher publication changed the target or request identity");
        helper.assertTrue(SyncActionDispatchers.server().dispatch(authenticatedContext) && sent.size() == 1 &&
                firstReceiver.requestCount == 1,
                "replayed authenticated request-full action reached the waiting-list publisher");
        UUID replayChallengeId = firstReceiver.issuedChallengeId;
        helper.assertFalse(replayChallengeId.equals(firstChallengeId),
                "replayed authenticated request did not receive a fresh menu challenge");
        UUID wrongOpeningId = UUID.randomUUID();
        SyncActionData wrongOpening = MEOutputWaitingListActions.createRequestFullAction(
                target, wrongOpeningId, 0, replayChallengeId);
        helper.assertTrue(SyncActionDispatchers.server().dispatch(SyncActionContext.machine(
                player, rootMachine, wrongOpening, rootMachine.getBlockPos())) && firstReceiver.requestCount == 1,
                "menu challenge nonce authenticated a different opening");
        UUID wrongOpeningChallengeId = firstReceiver.issuedChallengeId;
        helper.assertFalse(wrongOpeningChallengeId.equals(replayChallengeId),
                "wrong-opening response did not receive a replacement menu challenge");
        SyncActionData wrongSequence = MEOutputWaitingListActions.createRequestFullAction(
                target, wrongOpeningId, 1, wrongOpeningChallengeId);
        helper.assertTrue(SyncActionDispatchers.server().dispatch(SyncActionContext.machine(
                player, rootMachine, wrongSequence, rootMachine.getBlockPos())) && firstReceiver.requestCount == 1,
                "menu challenge nonce authenticated a different request sequence");
        helper.assertFalse(firstReceiver.issuedChallengeId.equals(wrongOpeningChallengeId),
                "wrong-sequence response did not receive a replacement menu challenge");
        SyncActionData recoveredChallenge = MEOutputWaitingListActions.createRequestFullAction(
                target, wrongOpeningId, 1, firstReceiver.issuedChallengeId);
        helper.assertTrue(SyncActionDispatchers.server().dispatch(SyncActionContext.machine(
                player, rootMachine, recoveredChallenge, rootMachine.getBlockPos())) &&
                firstReceiver.requestCount == 2 && publisher.subscriptionCount() == 1 && sent.size() == 1,
                "replacement menu challenge did not recover through the publisher identity boundary");

        UUID replacementOpeningId = UUID.randomUUID();
        TestReceiver replacementReceiver = new TestReceiver(owner, publisher);
        player.containerMenu = menu(16, player, holderWithReceivers(rootMachine, replacementReceiver));
        helper.assertTrue(SyncActionDispatchers.server().dispatch(authenticatedContext),
                "stale menu-session action did not reach its rejecting handler");
        publisher.serverTick();
        helper.assertTrue(publisher.subscriptionCount() == 0 && sent.size() == 1,
                "stale menu-session action registered its old opening in the replacement menu");
        SyncActionData replacementChallenge = MEOutputWaitingListActions.createRequestFullAction(
                target, replacementOpeningId, 1);
        helper.assertTrue(SyncActionDispatchers.server().dispatch(SyncActionContext.machine(
                player, rootMachine, replacementChallenge, rootMachine.getBlockPos())) &&
                publisher.subscriptionCount() == 0,
                "replacement menu registered before completing its own challenge");
        SyncActionData replacementAuthenticated = MEOutputWaitingListActions.createRequestFullAction(
                target, replacementOpeningId, 1,
                replacementReceiver.issuedChallengeId);
        helper.assertTrue(SyncActionDispatchers.server().dispatch(SyncActionContext.machine(
                player, rootMachine, replacementAuthenticated, rootMachine.getBlockPos())) &&
                publisher.subscriptionCount() == 1,
                "replacement menu could not register with its own server challenge");

        SyncActionContext partContext = SyncActionContext.machine(player, owner, first, owner.getBlockPos());
        helper.assertFalse(SyncActionDispatchers.server().dispatch(partContext),
                "request-full action accepted the receiver part as the menu root context");
        SyncActionData invalidPayload = new SyncActionData(
                first.actionId(), first.sequence(), DataComponentMap.EMPTY);
        SyncActionContext invalidContext = SyncActionContext.machine(
                player, rootMachine, invalidPayload, rootMachine.getBlockPos());
        helper.assertFalse(SyncActionDispatchers.server().dispatch(invalidContext),
                "request-full handler accepted an empty payload");
        helper.assertTrue(sent.size() == 1,
                "rejected request-full action still emitted a waiting-list packet");

        MEOutputWaitingListUpdate update = MEOutputWaitingListUpdate.full(0, 0, 1, List.of());
        SPacketMEOutputWaitingListToClient packet = new SPacketMEOutputWaitingListToClient(
                16, target, openingId, 7, update);
        helper.assertTrue(packet.target().equals(target) && packet.requestSequence() == 7 &&
                packet.update().equals(update),
                "packet constructor changed the target, sequence, or update");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SPacketMEOutputWaitingListToClient.CODEC.encode(buffer, packet);
            SPacketMEOutputWaitingListToClient decoded = SPacketMEOutputWaitingListToClient.CODEC.decode(buffer);
            helper.assertTrue(decoded.equals(packet),
                    "packet codec changed the container, target, opening, sequence, or update");
        } finally {
            buffer.release();
        }
        SPacketMEOutputWaitingListSessionToClient sessionPacket = new SPacketMEOutputWaitingListSessionToClient(
                16, target, openingId, 7, UUID.randomUUID());
        RegistryFriendlyByteBuf sessionBuffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SPacketMEOutputWaitingListSessionToClient.CODEC.encode(sessionBuffer, sessionPacket);
            SPacketMEOutputWaitingListSessionToClient decoded = SPacketMEOutputWaitingListSessionToClient.CODEC
                    .decode(sessionBuffer);
            helper.assertTrue(decoded.equals(sessionPacket),
                    "menu-session packet codec changed its container, target, opening, sequence, or nonce");
        } finally {
            sessionBuffer.release();
        }
        assertRejected(() -> MEOutputWaitingListActions.createRequestFullAction(target, openingId, -1));
        assertRejected(() -> new SPacketMEOutputWaitingListSessionToClient(
                -1, target, openingId, 0, UUID.randomUUID()));
        assertRejected(() -> new SPacketMEOutputWaitingListSessionToClient(
                16, target, openingId, -1, UUID.randomUUID()));
        assertRejected(() -> new SPacketMEOutputWaitingListToClient(
                -1, target, openingId, 0, update));
        assertRejected(() -> new SPacketMEOutputWaitingListToClient(
                16, target, openingId, -1, update));
        helper.succeed();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static ModularUIContainerMenu menu(int containerId, ServerPlayer player, TestMachineHolder holder) {
        MenuType<ModularUIContainerMenu> menuType = (MenuType) MenuType.GENERIC_9x1;
        return new ModularUIContainerMenu(menuType, containerId, player.getInventory(), holder);
    }

    private static List<MEOutputWaitingListEntry> entries(int count) {
        List<MEOutputWaitingListEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ItemStack stack = new ItemStack(Items.STONE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("server-" + index));
            entries.add(new MEOutputWaitingListEntry(itemKey(stack), index + 1L));
        }
        return List.copyOf(entries);
    }

    private static AEItemKey itemKey(Item item) {
        return itemKey(new ItemStack(item));
    }

    private static AEItemKey itemKey(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            throw new IllegalStateException("Test item did not create an AE item key: " + stack);
        }
        return key;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(
                helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.closeContainer();
        return player;
    }

    private static MEOutputBusPartMachine createOutput() {
        return createOutput(BlockPos.ZERO);
    }

    private static MEOutputBusPartMachine createOutput(BlockPos pos) {
        MetaMachine machine = GTAEMachines.ITEM_EXPORT_BUS_ME.getBlockEntityType().create(
                pos, GTAEMachines.ITEM_EXPORT_BUS_ME.defaultBlockState());
        if (!(machine instanceof MEOutputBusPartMachine output)) {
            throw new IllegalStateException("ME item output definition created the wrong machine type.");
        }
        return output;
    }

    private static PowerSubstationMachine createController(BlockPos pos) {
        MetaMachine machine = GTMultiMachines.POWER_SUBSTATION.getBlockEntityType().create(
                pos, GTMultiMachines.POWER_SUBSTATION.defaultBlockState());
        if (!(machine instanceof PowerSubstationMachine controller)) {
            throw new IllegalStateException("Power substation definition created the wrong machine type.");
        }
        return controller;
    }

    private static TestMachineHolder holderWithReceivers(MetaMachine rootMachine, TestReceiver... receivers) {
        UIElement root = new UIElement();
        root.addChildren(receivers);
        return new TestMachineHolder(rootMachine, root);
    }

    private static void assertRejected(Runnable operation) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected) {
            throw new IllegalStateException("Waiting-list protocol accepted an invalid identity boundary.");
        }
    }

    private static final class TestMachineHolder implements IContainerUIHolder, MachineUIHolder {

        private final MetaMachine machine;
        private final UIElement root;

        private TestMachineHolder(MetaMachine machine) {
            this(machine, new UIElement());
        }

        private TestMachineHolder(MetaMachine machine, UIElement root) {
            this.machine = machine;
            this.root = root;
        }

        @Override
        public ModularUI createUI(Player player) {
            return ModularUI.of(UI.of(root), player);
        }

        @Override
        public boolean isStillValid(Player player) {
            return true;
        }

        @Override
        public BlockPos getPos() {
            return machine.getBlockPos();
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return machine.getDefinition().getId();
        }

        @Override
        public MetaMachine getMachine() {
            return machine;
        }
    }

    private static final class TestReceiver extends UIElement implements MEOutputWaitingListSessionReceiver {

        private final MEOutputBusPartMachine target;
        private final MEOutputWaitingListPublisher publisher;
        private final MEOutputWaitingListMenuSession waitingListMenuSession = new MEOutputWaitingListMenuSession();
        private UUID receivedOpeningId;
        private int receivedRequestSequence = -1;
        private MEOutputWaitingListUpdate receivedUpdate;
        private UUID receivedSessionId;
        private UUID issuedChallengeId;
        private int requestCount;

        private TestReceiver(MEOutputBusPartMachine target, MEOutputWaitingListPublisher publisher) {
            this.target = target;
            this.publisher = publisher;
            setId(MEOutputWaitingListReceiver.elementId(target.getBlockPos()));
        }

        @Override
        public boolean matchesWaitingListTarget(MEOutputWaitingListTarget waitingListTarget) {
            return target.getWaitingListTarget().equals(waitingListTarget);
        }

        @Override
        public boolean matchesWaitingListTarget(MetaMachine machine) {
            return target == machine;
        }

        @Override
        public MEOutputWaitingListMenuSession getWaitingListMenuSession() {
            return waitingListMenuSession;
        }

        @Override
        public UUID issueWaitingListMenuSessionChallenge(UUID openingId, int requestSequence) {
            issuedChallengeId = MEOutputWaitingListSessionReceiver.super.issueWaitingListMenuSessionChallenge(
                    openingId, requestSequence);
            return issuedChallengeId;
        }

        @Override
        public boolean canRequestFull(ServerPlayer player) {
            return publisher.canRequestFull(player);
        }

        @Override
        public boolean requestFull(ServerPlayer player, UUID openingId, int requestSequence) {
            requestCount++;
            return publisher.requestFull(player, openingId, requestSequence);
        }

        @Override
        public void applyWaitingListMenuSession(UUID openingId, int requestSequence, UUID menuSessionId) {
            receivedOpeningId = openingId;
            receivedRequestSequence = requestSequence;
            receivedSessionId = menuSessionId;
        }

        @Override
        public void applyWaitingListUpdate(UUID openingId, int requestSequence,
                                           MEOutputWaitingListUpdate update) {
            receivedOpeningId = openingId;
            receivedRequestSequence = requestSequence;
            receivedUpdate = update;
        }
    }
}
