package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.network.packets.CPacketMachineActionToServer;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.gametest.util.TestUtils;
import com.gregtechceu.gtceu.integration.ae2.gui.element.AEPatternViewSlotElement;
import com.gregtechceu.gtceu.integration.ae2.gui.element.MEPatternBufferNameEditorElement;
import com.gregtechceu.gtceu.integration.ae2.gui.element.MEPatternBufferPageElement;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.core.definitions.AEItems;
import io.netty.buffer.Unpooled;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEPatternBufferProxyLDLib2UITest {

    private static final String BATCH = "MEPatternBufferProxyLDLib2UI";
    private static final BlockPos BUFFER_A_POS = new BlockPos(0, 1, 0);
    private static final BlockPos BUFFER_B_POS = new BlockPos(1, 1, 0);
    private static final BlockPos PROXY_POS = new BlockPos(2, 1, 0);
    private static final BlockPos REMOTE_BUFFER_POS = new BlockPos(11, 1, 0);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void contextualPreviewStaysIndependentFromLinkedBufferOpening(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        MEPatternBufferPartMachine buffer = placeBuffer(helper, BUFFER_A_POS);
        MEPatternBufferProxyPartMachine proxy = placeProxy(helper, PROXY_POS);
        MEPatternBufferProxyPartMachine replacement = placeProxy(helper, BUFFER_B_POS);
        proxy.setBuffer(buffer.getBlockPos());
        MutableProxyHolder holder = new MutableProxyHolder(proxy);
        LDLib2FancyPartUIProvider pageProvider = proxy;

        helper.assertTrue(proxy.canCreateLDLib2UI(player, holder),
                "linked Pattern Buffer Proxy rejected its standalone opening");
        UIElement standaloneRoot = proxy.createLDLib2UI(player, holder).getRootElement();
        helper.assertTrue(descendants(standaloneRoot).stream().anyMatch(MEPatternBufferPageElement.class::isInstance),
                "linked Pattern Buffer Proxy standalone opening omitted its buffer body");
        LDLib2FancyUIProvider firstPage = pageProvider.createLDLib2FancyPage(player, holder);
        LDLib2FancyUIProvider secondPage = pageProvider.createLDLib2FancyPage(player, holder);
        helper.assertTrue(firstPage != secondPage,
                "Pattern Buffer Proxy reused a contextual page across openings");
        helper.assertTrue(firstPage.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                firstPage.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Pattern Buffer Proxy contextual page did not preserve the default preview bounds");
        LDLib2FancyUIProvider.PageGroupingData grouping = firstPage.getPageGroupingData();
        helper.assertTrue(grouping != null &&
                "gtpm.multiblock.page_switcher.io.import".equals(grouping.groupKey()) &&
                grouping.groupPositionWeight() == 1,
                "Pattern Buffer Proxy contextual page lost its legacy import grouping");

        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(firstPage,
                player.getInventory(), holder, firstPage.getLDLib2PageWidth(), firstPage.getLDLib2PageHeight());
        helper.assertTrue(shell.getHolder() == holder,
                "Pattern Buffer Proxy contextual shell lost its proxy holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 1,
                "Pattern Buffer Proxy contextual preview exposed linked-buffer configurators");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Pattern Buffer Proxy contextual preview lost its directional page");
        UIElement preview = shell.getChildren().getFirst().getChildren().getFirst();
        helper.assertTrue(preview.getSizeWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                preview.getSizeHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT &&
                preview.getChildren().isEmpty() &&
                descendants(preview).stream().noneMatch(MEPatternBufferPageElement.class::isInstance),
                "Pattern Buffer Proxy contextual page reused its linked-buffer standalone body");

        boolean mismatchedHolderRejected = false;
        try {
            pageProvider.createLDLib2FancyPage(player, new MutableProxyHolder(replacement));
        } catch (IllegalArgumentException expected) {
            mismatchedHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedHolderRejected,
                "Pattern Buffer Proxy contextual page accepted another proxy holder");

        holder.setMachine(replacement);
        boolean replacementRejected = false;
        try {
            new LDLib2FancyMachineUIElement(firstPage, player.getInventory(), holder,
                    firstPage.getLDLib2PageWidth(), firstPage.getLDLib2PageHeight());
        } catch (IllegalStateException expected) {
            replacementRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(replacementRejected,
                "Pattern Buffer Proxy contextual page accepted a same-definition replacement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void openingIdentityCodecRetainsServerCapturedIdentityAfterProxyRebind(GameTestHelper helper) {
        MEPatternBufferPartMachine bufferA = placeBuffer(helper, BUFFER_A_POS);
        MEPatternBufferPartMachine bufferB = placeBuffer(helper, BUFFER_B_POS);
        MEPatternBufferProxyPartMachine proxy = placeProxy(helper, PROXY_POS);
        proxy.setBuffer(bufferA.getBlockPos());
        MEPatternBufferProxyOpeningIdentity captured = opening(proxy, bufferA);
        RegistryFriendlyByteBuf wire = newBuffer(helper);

        try {
            MEPatternBufferProxyOpeningIdentity.STREAM_CODEC.encode(wire, captured);
            proxy.setBuffer(bufferB.getBlockPos());
            MEPatternBufferProxyOpeningIdentity decoded = MEPatternBufferProxyOpeningIdentity.STREAM_CODEC
                    .decode(wire);

            helper.assertTrue(decoded.equals(captured),
                    "opening identity codec read mutable Proxy link fields instead of its server-captured payload");
            helper.assertTrue(!decoded.equals(opening(proxy, bufferB)) && wire.readableBytes() == 0,
                    "opening identity codec followed the rebound Proxy or left trailing wire data");
        } finally {
            wire.release();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void menuOpeningDataCarriesIdentityWithoutFullFieldSnapshot(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        MEPatternBufferPartMachine buffer = placeBuffer(helper, BUFFER_A_POS);
        MEPatternBufferProxyPartMachine proxy = placeProxy(helper, PROXY_POS);
        proxy.setBuffer(buffer.getBlockPos());
        configureSnapshotSource(buffer, "Opening payload", AEItems.PROCESSING_PATTERN.stack(),
                new ItemStack(Items.COPPER_INGOT, 7), IntCircuitBehaviour.stack(8),
                new FluidStack(Fluids.WATER, 750), true);
        MEPatternBufferProxyUIHolder holder = proxy.createLDLib2UIHolder(player);
        var menu = holder.createMenu(23, player.getInventory(), player);
        RegistryFriendlyByteBuf wire = newBuffer(helper);

        try {
            holder.writeClientSideData(menu, wire);
            BlockPos decodedProxyPos = wire.readBlockPos();
            ResourceLocation decodedDefinition = wire.readResourceLocation();
            MEPatternBufferProxyOpeningIdentity decodedOpening = MEPatternBufferProxyOpeningIdentity.STREAM_CODEC
                    .decode(wire);
            UUID decodedSession = wire.readUUID();
            MEPatternBufferProxyViewSnapshot decodedProjection = MEPatternBufferProxyViewSnapshot.STREAM_CODEC
                    .decode(wire);

            helper.assertTrue(decodedProxyPos.equals(proxy.getBlockPos()) &&
                    decodedDefinition.equals(proxy.getDefinition().getId()) &&
                    decodedOpening.equals(opening(proxy, buffer)) &&
                    decodedSession.equals(holder.getMenuSessionId()),
                    "Proxy menu opening data lost its server-captured identity");
            helper.assertTrue(decodedProjection.data().isEmpty() &&
                    !holder.getOpeningSnapshot().data().isEmpty() &&
                    decodedProjection.blockState().equals(holder.getOpeningSnapshot().blockState()) &&
                    wire.readableBytes() == 0,
                    "Proxy menu opening data included the full field snapshot or lost its projection state");
        } finally {
            wire.release();
            holder.close(player);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void snapshotCodecCreatesCompleteViewWithoutWorldBuffer(GameTestHelper helper) {
        MEPatternBufferPartMachine source = placeBuffer(helper, BUFFER_A_POS);
        BlockPos sourcePos = source.getBlockPos();
        ItemStack pattern = AEItems.PROCESSING_PATTERN.stack();
        ItemStack sharedItem = new ItemStack(Items.COPPER_INGOT, 12);
        ItemStack circuit = IntCircuitBehaviour.stack(17);
        FluidStack sharedFluid = new FluidStack(Fluids.WATER, 2_500);
        configureSnapshotSource(source, "Detached opening", pattern, sharedItem, circuit, sharedFluid, true);
        MEPatternBufferProxyViewSnapshot captured = MEPatternBufferProxyViewSnapshot.capture(
                source, helper.getLevel().registryAccess());
        helper.assertTrue(source.getSyncDataHolder().scanAndMarkChanges(helper.getLevel().registryAccess()),
                "capturing a Proxy opening snapshot consumed the source's ordinary client synchronization");
        MEPatternBufferProxyViewSnapshot decoded = roundTripSnapshot(helper, captured);

        helper.setBlock(BUFFER_A_POS, Blocks.AIR);
        helper.assertTrue(MetaMachine.getMachine(helper.getLevel(), sourcePos) == null,
                "detached-view fixture retained the linked Pattern Buffer in the world");
        MEPatternBufferPartMachine view = decoded.createDetachedView(helper.getLevel(), sourcePos);

        helper.assertTrue(view != source && view.getBlockPos().equals(sourcePos) &&
                MetaMachine.getMachine(helper.getLevel(), sourcePos) == null,
                "snapshot did not create an unregistered view at the linked buffer position");
        helper.assertTrue(decoded.matchesContent(view, helper.getLevel().registryAccess()),
                "detached Pattern Buffer view did not restore the complete encoded snapshot");
        assertCoreSnapshotState(helper, view, "Detached opening", pattern, sharedItem, circuit, sharedFluid, true);
        MEPatternBufferProxyViewSnapshot.discardDetachedView(view);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void laterSnapshotRefreshesExistingDetachedView(GameTestHelper helper) {
        MEPatternBufferPartMachine initialSource = placeBuffer(helper, BUFFER_A_POS);
        BlockPos initialSourcePos = initialSource.getBlockPos();
        configureSnapshotSource(initialSource, "Initial", AEItems.PROCESSING_PATTERN.stack(),
                new ItemStack(Items.IRON_INGOT, 3), IntCircuitBehaviour.stack(2),
                new FluidStack(Fluids.WATER, 1_000), false);
        MEPatternBufferProxyViewSnapshot initial = MEPatternBufferProxyViewSnapshot.capture(
                initialSource, helper.getLevel().registryAccess());
        helper.setBlock(BUFFER_A_POS, Blocks.AIR);
        MEPatternBufferPartMachine view = initial.createDetachedView(helper.getLevel(), initialSourcePos);

        MEPatternBufferPartMachine updatedSource = placeBuffer(helper, BUFFER_B_POS);
        ItemStack updatedPattern = AEItems.PROCESSING_PATTERN.stack(2);
        ItemStack updatedSharedItem = new ItemStack(Items.GOLD_INGOT, 21);
        ItemStack updatedCircuit = IntCircuitBehaviour.stack(24);
        FluidStack updatedSharedFluid = new FluidStack(Fluids.LAVA, 4_000);
        configureSnapshotSource(updatedSource, "Refreshed", updatedPattern, updatedSharedItem,
                updatedCircuit, updatedSharedFluid, true);
        MEPatternBufferProxyViewSnapshot updated = roundTripSnapshot(helper,
                MEPatternBufferProxyViewSnapshot.capture(updatedSource, helper.getLevel().registryAccess()));

        updated.applyTo(view, helper.getLevel().registryAccess());

        helper.assertTrue(updated.matchesContent(view, helper.getLevel().registryAccess()) &&
                !initial.matchesContent(view, helper.getLevel().registryAccess()),
                "later Proxy snapshot did not replace the detached view's previous client state");
        assertCoreSnapshotState(helper, view, "Refreshed", updatedPattern, updatedSharedItem,
                updatedCircuit, updatedSharedFluid, true);
        helper.assertTrue(MetaMachine.getMachine(helper.getLevel(), initialSourcePos) == null,
                "snapshot refresh registered its detached Pattern Buffer in the world");
        MEPatternBufferProxyViewSnapshot.discardDetachedView(view);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void validLinkCreatesEquivalentOpeningAndOpeningBoundActions(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        MEPatternBufferPartMachine buffer = placeBuffer(helper, BUFFER_A_POS);
        MEPatternBufferProxyPartMachine proxy = placeProxy(helper, PROXY_POS);
        proxy.setBuffer(buffer.getBlockPos());
        MachineUIHolder holder = new MachineUIHolderContext(player, proxy);

        helper.assertTrue(proxy.canCreateLDLib2UI(player, holder),
                "linked Pattern Buffer Proxy rejected its exact holder");
        helper.assertTrue(!proxy.canCreateLDLib2UI(player, new MachineUIHolderContext(player, buffer)),
                "Pattern Buffer Proxy accepted its linked buffer as the opened holder");
        helper.assertTrue(proxy.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement,
                "Pattern Buffer Proxy did not create an LDLib2 Fancy root");

        List<CapturedAction> actions = new ArrayList<>();
        LDLib2FancyUIProvider pageProvider = proxy.createLDLib2Page(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> Boolean.TRUE.equals(event.customData));
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(
                pageProvider, player.getInventory(), holder,
                pageProvider.getLDLib2PageWidth(), pageProvider.getLDLib2PageHeight());
        MEPatternBufferPageElement page = descendants(shell).stream()
                .filter(MEPatternBufferPageElement.class::isInstance)
                .map(MEPatternBufferPageElement.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Proxy shell omitted the Pattern Buffer body"));

        helper.assertTrue(page.getSizeWidth() == 178 && page.getSizeHeight() == 70 &&
                page.getPatternSlots().size() == 27,
                "Proxy did not preserve the complete 178x70 Pattern Buffer body");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 4,
                "Proxy omitted refund, circuit, shared-item, or shared-tank configurators");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Proxy omitted its linked buffer directional page");
        helper.assertTrue(sharedItemSlots(shell, buffer).size() == 9,
                "Proxy shared-item configurator did not expose nine linked slots");
        helper.assertTrue(sharedTankSlots(shell).size() == 9,
                "Proxy shared-tank configurator did not expose nine linked slots");

        MEPatternBufferProxyOpeningIdentity opening = opening(proxy, buffer);
        MEPatternBufferNameEditorElement editor = page.getNameEditor();
        click(editor.getToggleButton(), false);
        editor.getTextField().setText("Proxy opening", true);
        click(editor.getToggleButton(), false);
        assertOneAction(helper, actions, holder,
                MEPatternBufferProxyActions.createSetNameAction(opening, "Proxy opening"), "Proxy name confirmation");

        actions.clear();
        UIElement refundButton = shell.getConfiguratorPanel().getChildren().getFirst().getChildren().getFirst();
        click(refundButton, false);
        assertOneAction(helper, actions, holder,
                MEPatternBufferProxyActions.createRefundAllAction(opening), "Proxy refund");

        actions.clear();
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        click(sharedTankSlots(shell).get(2), true);
        assertOneAction(helper, actions, holder,
                MEPatternBufferProxyActions.createClickShareTankAction(opening, 2, true), "Proxy shared tank");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void rebindAndClearInvalidateActionsSlotsAndReverseLinks(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        MEPatternBufferPartMachine bufferA = placeBuffer(helper, BUFFER_A_POS);
        MEPatternBufferPartMachine bufferB = placeBuffer(helper, BUFFER_B_POS);
        MEPatternBufferProxyPartMachine proxy = placeProxy(helper, PROXY_POS);
        proxy.setBuffer(bufferA.getBlockPos());
        MachineUIHolder holder = new MachineUIHolderContext(player, proxy);
        List<CapturedAction> actions = new ArrayList<>();
        LDLib2FancyUIProvider pageProvider = proxy.createLDLib2Page(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> false);
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(
                pageProvider, player.getInventory(), holder,
                pageProvider.getLDLib2PageWidth(), pageProvider.getLDLib2PageHeight());
        MEPatternBufferProxyOpeningIdentity opening = opening(proxy, bufferA);
        long firstRevision = proxy.getLinkRevision();

        proxy.setBuffer(bufferB.getBlockPos());
        helper.assertTrue(proxy.getBuffer() == bufferB && proxy.getLinkRevision() > firstRevision,
                "Proxy rebind did not advance and resolve its link generation");
        helper.assertTrue(!bufferA.getProxies().contains(proxy) && bufferB.getProxies().contains(proxy),
                "Proxy rebind left a stale reverse link or omitted the new reverse link");

        assertServerSlotsRejected(helper, shell, bufferA, player);
        UIElement refundButton = shell.getConfiguratorPanel().getChildren().getFirst().getChildren().getFirst();
        click(refundButton, false);
        helper.assertTrue(actions.isEmpty(), "stale Proxy page emitted a refund action after rebind");
        helper.assertTrue(!dispatch(player, proxy,
                MEPatternBufferProxyActions.createRefundAllAction(opening)),
                "server accepted a queued Proxy action after rebind");

        long reboundRevision = proxy.getLinkRevision();
        proxy.setBuffer(null);
        helper.assertTrue(proxy.getBuffer() == null && proxy.getBufferPos() == null &&
                proxy.getLinkRevision() > reboundRevision,
                "Proxy clear did not remove and advance its linked state");
        helper.assertTrue(!bufferB.getProxies().contains(proxy),
                "Proxy clear left a stale reverse link on its previous buffer");
        helper.assertTrue(!proxy.canCreateLDLib2UI(player, holder),
                "unlinked Proxy still allowed an LDLib2 UI opening");
        boolean unlinkedOpeningRejected = false;
        try {
            proxy.createLDLib2UI(player, holder);
        } catch (IllegalStateException expected) {
            unlinkedOpeningRejected = true;
        }
        helper.assertTrue(unlinkedOpeningRejected, "unlinked Proxy created an LDLib2 UI directly");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void samePositionBufferAndProxyReplacementRejectQueuedOpening(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        MEPatternBufferPartMachine originalBuffer = placeBuffer(helper, BUFFER_A_POS);
        MEPatternBufferProxyPartMachine originalProxy = placeProxy(helper, PROXY_POS);
        originalProxy.setBuffer(originalBuffer.getBlockPos());
        MachineUIHolder originalHolder = new MachineUIHolderContext(player, originalProxy);
        LDLib2FancyUIProvider originalPage = originalProxy.createLDLib2Page(player, originalHolder,
                (holder, action) -> {}, () -> true, event -> false);
        LDLib2FancyMachineUIElement originalShell = new LDLib2FancyMachineUIElement(
                originalPage, player.getInventory(), originalHolder,
                originalPage.getLDLib2PageWidth(), originalPage.getLDLib2PageHeight());
        MEPatternBufferProxyOpeningIdentity originalOpening = opening(originalProxy, originalBuffer);
        SyncActionData queued = MEPatternBufferProxyActions.createSetNameAction(originalOpening, "stale");
        long originalRevision = originalProxy.getLinkRevision();

        helper.setBlock(BUFFER_A_POS, Blocks.AIR);
        MEPatternBufferPartMachine replacementBuffer = placeBuffer(helper, BUFFER_A_POS);
        helper.assertTrue(originalProxy.getBuffer() == replacementBuffer &&
                originalProxy.getLinkRevision() > originalRevision,
                "Proxy failed to detect a same-position Pattern Buffer replacement");
        helper.assertTrue(!dispatch(player, originalProxy, queued) && replacementBuffer.getCustomName().isEmpty(),
                "same-position buffer replacement accepted a queued opening action");
        assertServerSlotsRejected(helper, originalShell, originalBuffer, player);

        helper.setBlock(PROXY_POS, Blocks.AIR);
        MEPatternBufferProxyPartMachine replacementProxy = placeProxy(helper, PROXY_POS);
        replacementProxy.setBuffer(replacementBuffer.getBlockPos());
        helper.assertTrue(!replacementProxy.getProxyIncarnation().equals(originalOpening.proxyIncarnation()),
                "same-position Proxy replacement reused the previous incarnation");
        helper.assertTrue(!dispatch(player, replacementProxy, queued),
                "same-position Proxy replacement accepted a queued opening action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ownerRevocationInvalidatesActionsAndServerSlots(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        MEPatternBufferPartMachine buffer = placeBuffer(helper, BUFFER_A_POS);
        MEPatternBufferProxyPartMachine proxy = placeProxy(helper, PROXY_POS);
        proxy.setOwnerUUID(player.getUUID());
        buffer.setOwnerUUID(player.getUUID());
        proxy.setBuffer(buffer.getBlockPos());
        MachineUIHolder holder = new MachineUIHolderContext(player, proxy);
        LDLib2FancyUIProvider page = proxy.createLDLib2Page(player, holder,
                (sentHolder, action) -> {}, () -> true, event -> false);
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(
                page, player.getInventory(), holder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        MEPatternBufferProxyOpeningIdentity opening = opening(proxy, buffer);
        SyncActionData queued = MEPatternBufferProxyActions.createRefundAllAction(opening);

        boolean previousOnlyOwnerGui = ConfigHolder.INSTANCE.machines.onlyOwnerGUI;
        int previousBypass = ConfigHolder.INSTANCE.machines.ownerOPBypass;
        try {
            ConfigHolder.INSTANCE.machines.onlyOwnerGUI = true;
            ConfigHolder.INSTANCE.machines.ownerOPBypass = Commands.LEVEL_OWNERS;
            buffer.setOwnerUUID(UUID.randomUUID());
            helper.assertTrue(!dispatch(player, proxy, queued),
                    "Proxy action ignored linked buffer owner revocation");
            assertServerSlotsRejected(helper, shell, buffer, player);

            buffer.setOwnerUUID(player.getUUID());
            proxy.setOwnerUUID(UUID.randomUUID());
            helper.assertTrue(!dispatch(player, proxy, queued),
                    "Proxy action ignored Proxy owner revocation");
            assertServerSlotsRejected(helper, shell, buffer, player);
        } finally {
            ConfigHolder.INSTANCE.machines.onlyOwnerGUI = previousOnlyOwnerGui;
            ConfigHolder.INSTANCE.machines.ownerOPBypass = previousBypass;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void realProxyPacketUsesProxyAnchorAndMutatesOnlyOpenedBuffer(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        MEPatternBufferPartMachine linkedBuffer = placeBuffer(helper, REMOTE_BUFFER_POS);
        MEPatternBufferPartMachine otherBuffer = placeBuffer(helper, BUFFER_B_POS);
        MEPatternBufferProxyPartMachine proxy = placeProxy(helper, PROXY_POS);
        proxy.setBuffer(linkedBuffer.getBlockPos());
        linkedBuffer.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(0));
        otherBuffer.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(0));
        player.setPos(proxy.getBlockPos().getX() + 0.5, proxy.getBlockPos().getY() + 0.5,
                proxy.getBlockPos().getZ() + 0.5);

        helper.assertTrue(player.canInteractWithBlock(proxy.getBlockPos(), 8.0) &&
                !player.canInteractWithBlock(linkedBuffer.getBlockPos(), 8.0),
                "Proxy packet distance fixture did not separate its interaction anchor from the remote buffer");
        MEPatternBufferProxyOpeningIdentity opening = opening(proxy, linkedBuffer);
        executePacket(player, proxy, MEPatternBufferProxyActions.createSetNameAction(opening, "Remote linked"));
        executePacket(player, proxy,
                MEPatternBufferProxyActions.createSetCircuitConfigurationAction(opening, 17));
        helper.assertTrue("Remote linked".equals(linkedBuffer.getCustomName()) &&
                IntCircuitBehaviour.getCircuitConfiguration(
                        linkedBuffer.getCircuitInventory().getStackInSlot(0)) == 17,
                "real Proxy packet did not mutate the opened remote buffer exactly");
        helper.assertTrue(otherBuffer.getCustomName().isEmpty() &&
                IntCircuitBehaviour.getCircuitConfiguration(
                        otherBuffer.getCircuitInventory().getStackInSlot(0)) == 0,
                "real Proxy packet mutated an unrelated Pattern Buffer");

        MEPatternBufferPartMachine.PatternBufferPageActions pageActions = proxy.createOpeningPageActions(opening);
        helper.assertTrue(pageActions.createPlaceCoverAction(Direction.NORTH).equals(
                MEPatternBufferProxyActions.createPlaceCoverAction(opening,
                        Direction.NORTH)) &&
                pageActions.createRemoveCoverAction(Direction.SOUTH).equals(
                        MEPatternBufferProxyActions.createRemoveCoverAction(opening,
                                Direction.SOUTH)) &&
                pageActions.createOpenCoverAction(Direction.UP).equals(
                        MEPatternBufferProxyActions.createOpenCoverAction(opening,
                                Direction.UP)),
                "Proxy directional page did not route cover controls through opening-bound Proxy actions");

        executePacket(player, proxy.getBlockPos(), GTAEMachines.ME_PATTERN_BUFFER.getId(),
                MEPatternBufferProxyActions.createSetNameAction(opening, "wrong holder"));
        helper.assertTrue("Remote linked".equals(linkedBuffer.getCustomName()),
                "wrong-definition Proxy packet mutated the linked buffer");

        proxy.setBuffer(null);
        executePacket(player, proxy,
                MEPatternBufferProxyActions.createSetNameAction(opening, "unlinked"));
        helper.assertTrue("Remote linked".equals(linkedBuffer.getCustomName()),
                "unlinked Proxy accepted an action from its old opening");

        helper.setBlock(PROXY_POS, Blocks.AIR);
        MEPatternBufferProxyPartMachine replacementProxy = placeProxy(helper, PROXY_POS);
        replacementProxy.setBuffer(linkedBuffer.getBlockPos());
        executePacket(player, replacementProxy,
                MEPatternBufferProxyActions.createSetNameAction(opening, "stale proxy"));
        helper.assertTrue("Remote linked".equals(linkedBuffer.getCustomName()),
                "replacement Proxy accepted an action from the previous Proxy opening");
        helper.succeed();
    }

    private static void assertServerSlotsRejected(GameTestHelper helper, LDLib2FancyMachineUIElement shell,
                                                  MEPatternBufferPartMachine buffer, ServerPlayer player) {
        MEPatternBufferPageElement page = descendants(shell).stream()
                .filter(MEPatternBufferPageElement.class::isInstance)
                .map(MEPatternBufferPageElement.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Proxy shell omitted its Pattern Buffer body"));
        ItemStack pattern = AEItems.PROCESSING_PATTERN.stack();
        for (AEPatternViewSlotElement slot : page.getPatternSlots()) {
            assertSlotRejected(helper, slot, player, pattern, "pattern");
        }
        for (GTItemSlotElement slot : sharedItemSlots(shell, buffer)) {
            assertSlotRejected(helper, slot, player, new ItemStack(Items.STONE), "shared item");
        }
        for (GTItemSlotElement slot : circuitSlots(shell, buffer)) {
            assertSlotRejected(helper, slot, player, new ItemStack(Items.STONE), "circuit");
        }
    }

    private static void assertSlotRejected(GameTestHelper helper, GTItemSlotElement slot, ServerPlayer player,
                                           ItemStack stack, String description) {
        if (!(slot.getSlot() instanceof ItemHandlerSlot itemHandlerSlot)) {
            throw new IllegalStateException(description + " slot was not backed by ItemHandlerSlot");
        }
        helper.assertTrue(!itemHandlerSlot.getCanPlace().test(stack) &&
                !itemHandlerSlot.getCanTake().test(player),
                "stale Proxy " + description + " slot still accepted a server interaction");
    }

    private static List<GTItemSlotElement> sharedItemSlots(LDLib2FancyMachineUIElement shell,
                                                           MEPatternBufferPartMachine buffer) {
        return itemSlots(shell, buffer.getShareInventory().storage);
    }

    private static List<GTItemSlotElement> circuitSlots(LDLib2FancyMachineUIElement shell,
                                                        MEPatternBufferPartMachine buffer) {
        return itemSlots(shell, buffer.getCircuitInventory().storage);
    }

    private static List<GTItemSlotElement> itemSlots(LDLib2FancyMachineUIElement shell, Object handler) {
        return descendants(shell).stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .filter(slot -> slot.getSlot() instanceof ItemHandlerSlot itemHandlerSlot &&
                        itemHandlerSlot.getItemHandler() == handler)
                .toList();
    }

    private static List<GTFluidSlotElement> sharedTankSlots(LDLib2FancyMachineUIElement shell) {
        return descendants(shell).stream()
                .filter(GTFluidSlotElement.class::isInstance)
                .map(GTFluidSlotElement.class::cast)
                .toList();
    }

    private static List<UIElement> descendants(UIElement root) {
        List<UIElement> descendants = new ArrayList<>();
        collectDescendants(root, descendants);
        return descendants;
    }

    private static void collectDescendants(UIElement root, List<UIElement> descendants) {
        for (UIElement child : root.getChildren()) {
            descendants.add(child);
            collectDescendants(child, descendants);
        }
    }

    private static void click(UIElement target, boolean modified) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = target;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        event.customData = modified;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static void assertOneAction(GameTestHelper helper, List<CapturedAction> actions,
                                        MachineUIHolder holder, SyncActionData expected, String description) {
        helper.assertTrue(actions.size() == 1, description + " did not send exactly one action");
        CapturedAction actual = actions.getFirst();
        helper.assertTrue(actual.holder() == holder && actual.action().equals(expected),
                description + " did not retain its exact Proxy holder and opening payload");
    }

    private static boolean dispatch(ServerPlayer player, MEPatternBufferProxyPartMachine proxy,
                                    SyncActionData action) {
        return SyncActionDispatchers.server().dispatch(
                SyncActionContext.machine(player, proxy, action, proxy.getBlockPos()));
    }

    private static void executePacket(ServerPlayer player, MEPatternBufferProxyPartMachine proxy,
                                      SyncActionData action) {
        executePacket(player, proxy.getBlockPos(), proxy.getDefinition().getId(), action);
    }

    private static void executePacket(ServerPlayer player, BlockPos pos,
                                      ResourceLocation definitionId,
                                      SyncActionData action) {
        new CPacketMachineActionToServer(pos, definitionId, action)
                .execute(new ServerPayloadContext(player.connection, CPacketMachineActionToServer.ID));
    }

    private static MEPatternBufferProxyOpeningIdentity opening(MEPatternBufferProxyPartMachine proxy,
                                                               MEPatternBufferPartMachine buffer) {
        return new MEPatternBufferProxyOpeningIdentity(
                proxy.getProxyIncarnation(), buffer.getBlockPos(), proxy.getLinkRevision());
    }

    private static void configureSnapshotSource(MEPatternBufferPartMachine source, String name,
                                                ItemStack pattern, ItemStack sharedItem, ItemStack circuit,
                                                FluidStack sharedFluid, boolean online) {
        source.setCustomName(name);
        source.getPatternInventory().setStackInSlot(4, pattern.copy());
        source.getShareInventory().setStackInSlot(3, sharedItem.copy());
        source.getCircuitInventory().setStackInSlot(0, circuit.copy());
        source.getShareTank().setFluidInTank(5, sharedFluid.copy());
        source.setOnline(online);
    }

    private static MEPatternBufferProxyViewSnapshot roundTripSnapshot(
                                                                      GameTestHelper helper,
                                                                      MEPatternBufferProxyViewSnapshot snapshot) {
        RegistryFriendlyByteBuf wire = newBuffer(helper);
        try {
            MEPatternBufferProxyViewSnapshot.STREAM_CODEC.encode(wire, snapshot);
            MEPatternBufferProxyViewSnapshot decoded = MEPatternBufferProxyViewSnapshot.STREAM_CODEC.decode(wire);
            helper.assertTrue(wire.readableBytes() == 0,
                    "Pattern Buffer Proxy snapshot codec left trailing wire data");
            return decoded;
        } finally {
            wire.release();
        }
    }

    private static void assertCoreSnapshotState(GameTestHelper helper, MEPatternBufferPartMachine view,
                                                String name, ItemStack pattern, ItemStack sharedItem,
                                                ItemStack circuit, FluidStack sharedFluid, boolean online) {
        helper.assertTrue(name.equals(view.getCustomName()),
                "detached Pattern Buffer view lost its custom name");
        helper.assertTrue(ItemStack.matches(pattern, view.getPatternInventory().getStackInSlot(4)),
                "detached Pattern Buffer view lost its pattern inventory");
        helper.assertTrue(ItemStack.matches(sharedItem, view.getShareInventory().getStackInSlot(3)),
                "detached Pattern Buffer view lost its shared item inventory");
        helper.assertTrue(ItemStack.matches(circuit, view.getCircuitInventory().getStackInSlot(0)),
                "detached Pattern Buffer view lost its programmed circuit");
        helper.assertTrue(FluidStack.matches(sharedFluid, view.getShareTank().getFluidInTank(5)),
                "detached Pattern Buffer view lost its shared fluid inventory");
        helper.assertTrue(view.isOnline() == online,
                "detached Pattern Buffer view lost its ME network status");
    }

    private static RegistryFriendlyByteBuf newBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.OTHER);
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static MEPatternBufferPartMachine placeBuffer(GameTestHelper helper, BlockPos pos) {
        if (!(TestUtils.setMachine(helper, pos,
                GTAEMachines.ME_PATTERN_BUFFER) instanceof MEPatternBufferPartMachine buffer)) {
            throw new IllegalStateException("Pattern Buffer definition created the wrong machine type");
        }
        return buffer;
    }

    private static MEPatternBufferProxyPartMachine placeProxy(GameTestHelper helper, BlockPos pos) {
        if (!(TestUtils.setMachine(helper, pos,
                GTAEMachines.ME_PATTERN_BUFFER_PROXY) instanceof MEPatternBufferProxyPartMachine proxy)) {
            throw new IllegalStateException("Pattern Buffer Proxy definition created the wrong machine type");
        }
        return proxy;
    }

    private record CapturedAction(MachineUIHolder holder, SyncActionData action) {}

    private static final class MutableProxyHolder implements MachineUIHolder {

        private MEPatternBufferProxyPartMachine machine;

        private MutableProxyHolder(MEPatternBufferProxyPartMachine machine) {
            this.machine = machine;
        }

        private void setMachine(MEPatternBufferProxyPartMachine machine) {
            this.machine = machine;
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
        public MEPatternBufferProxyPartMachine getMachine() {
            return machine;
        }
    }
}
