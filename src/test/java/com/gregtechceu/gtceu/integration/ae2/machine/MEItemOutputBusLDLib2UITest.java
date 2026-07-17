package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.integration.ae2.gui.element.MEItemOutputWaitingListElement;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEItemOutputBusLDLib2UITest {

    private static final String BATCH = "MEItemOutputBusLDLib2UI";
    private static final int PAGE_WIDTH = 170;
    private static final int PAGE_HEIGHT = 74;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 3;
    private static final int MAX_CHUNK_ENTRIES = 64;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void openingAndFancySemanticsAreBoundToTheExactOutputBus(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputBusPartMachine output = createOutput();
        MEOutputBusPartMachine replacement = createOutput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);

        helper.assertTrue(output.canCreateLDLib2UI(player, holder),
                "ME item output rejected its matching holder");
        LDLib2FancyUIProvider standalonePage = output.createLDLib2Page(player, holder);
        LDLib2FancyUIProvider contextualPage = output.createLDLib2FancyPage(player, holder);
        helper.assertTrue(standalonePage != contextualPage,
                "standalone and contextual openings reused one page instance");

        boolean mismatchedHolderRejected = false;
        try {
            output.createLDLib2Page(player, new MutableMachineUIHolder(replacement));
        } catch (IllegalArgumentException expected) {
            mismatchedHolderRejected = true;
        }
        helper.assertTrue(mismatchedHolderRejected,
                "ME item output accepted another machine's holder");

        MEOutputBusPartMachine wrongDefinition = new MEOutputBusPartMachine(
                info(GTAEMachines.ITEM_IMPORT_BUS_ME));
        MutableMachineUIHolder wrongDefinitionHolder = new MutableMachineUIHolder(wrongDefinition);
        helper.assertFalse(wrongDefinition.canCreateLDLib2UI(player, wrongDefinitionHolder),
                "ME item output page accepted a non-output definition");
        boolean wrongDefinitionRejected = false;
        try {
            wrongDefinition.createLDLib2Page(player, wrongDefinitionHolder);
        } catch (IllegalStateException expected) {
            wrongDefinitionRejected = true;
        }
        helper.assertTrue(wrongDefinitionRejected,
                "wrong-definition ME item output created a page directly");

        LDLib2FancyMachineUIElement staleShell = new LDLib2FancyMachineUIElement(
                standalonePage, player.getInventory(), holder,
                standalonePage.getLDLib2PageWidth(), standalonePage.getLDLib2PageHeight());
        holder.machine = replacement;
        boolean staleHolderRejected = false;
        try {
            standalonePage.createLDLib2MainPage(staleShell);
        } catch (IllegalStateException expected) {
            staleHolderRejected = true;
        }
        helper.assertTrue(staleHolderRejected,
                "opened ME item output page accepted a stale holder");

        holder.machine = output;
        LDLib2FancyMachineUIElement standaloneShell = createShell(player, output, holder);
        MutableMachineUIHolder controllerHolder = new MutableMachineUIHolder(replacement);
        LDLib2FancyMachineUIElement contextualShell = new LDLib2FancyMachineUIElement(
                contextualPage, player.getInventory(), controllerHolder,
                contextualPage.getLDLib2PageWidth(), contextualPage.getLDLib2PageHeight());
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        MEItemOutputWaitingListElement standaloneList = waitingList(pageRoot(standaloneShell), target);
        MEItemOutputWaitingListElement contextualList = waitingList(pageRoot(contextualShell), target);
        String waitingListId = MEOutputWaitingListReceiver.elementId(target.pos());

        helper.assertTrue(standaloneList != contextualList &&
                waitingListId.equals(standaloneList.getId()) &&
                waitingListId.equals(contextualList.getId()),
                "standalone and contextual pages did not create isolated copies of the same waiting-list body");
        helper.assertTrue(standalonePage.getTitle().equals(
                Component.translatable(output.getDefinition().getDescriptionId())),
                "ME item output Fancy page lost its machine title");
        helper.assertTrue(standalonePage.getTabIcon() != IGuiTexture.EMPTY,
                "ME item output Fancy page omitted its machine icon");
        LDLib2FancyUIProvider.PageGroupingData grouping = standalonePage.getPageGroupingData();
        helper.assertTrue(grouping != null &&
                "gtpm.multiblock.page_switcher.io.export".equals(grouping.groupKey()) &&
                grouping.groupPositionWeight() == 2,
                "ME item output page lost export grouping key or weight two");
        helper.assertTrue(standaloneShell.getSideTabsElement().getChildren().size() == 2,
                "ME item output page omitted its directional side tab");
        helper.assertTrue(standaloneShell.getConfiguratorPanel().getChildren().size() == 1,
                "ME item output page did not expose exactly one working configurator");

        standaloneShell.getTooltipsPanel().screenTick();
        int expectedTooltipCount = output.showFancyTooltip() ? 1 : 0;
        for (var trait : output.getTraitHolder().getAllTraits()) {
            if (trait instanceof IFancyTooltip tooltip && tooltip.showFancyTooltip()) {
                expectedTooltipCount++;
            }
        }
        helper.assertTrue(standaloneShell.getTooltipsPanel().getChildren().size() == expectedTooltipCount,
                "ME item output page omitted a visible machine or trait tooltip");

        List<CapturedAction> contextualActions = new ArrayList<>();
        UIElement contextualRoot = output.createLDLib2MainElement(player, holder, controllerHolder,
                (sentHolder, action) -> contextualActions.add(new CapturedAction(sentHolder, action)), () -> true);
        MEItemOutputWaitingListElement contextualActionList = waitingList(contextualRoot, target);
        requestFull(helper, contextualRoot, contextualActions, controllerHolder, target,
                contextualActionList.getOpeningId(), "contextual controller-root holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void intendedThreeRowLayoutUsesTheFullSeventyFourPixelBody(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputBusPartMachine output = createOutput();
        output.setOnline(true);
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);
        UIElement root = output.createLDLib2MainElement(player, holder, (sentHolder, action) -> {}, () -> false);
        root.screenTick();

        UITemplate.LDLib2Bounds rootBounds = UITemplate.getLDLib2Bounds(root);
        helper.assertTrue(rootBounds.width() == PAGE_WIDTH && rootBounds.height() == PAGE_HEIGHT,
                "ME item output retained the clipped 170x65 legacy body instead of 170x74");
        GTLabelElement online = label(root, "me_network_status");
        GTLabelElement title = label(root, "me_output_waiting_list_label");
        UITemplate.LDLib2Bounds onlineBounds = UITemplate.getLDLib2Bounds(online);
        UITemplate.LDLib2Bounds titleBounds = UITemplate.getLDLib2Bounds(title);
        helper.assertTrue(onlineBounds.x() == 5 && onlineBounds.y() == 0 &&
                online.getValue().equals(Component.translatable("gtpm.gui.me_network.online")),
                "ME item output online label lost its position or synchronized value");
        helper.assertTrue(titleBounds.x() == 5 && titleBounds.y() == 10 &&
                title.getValue().equals(Component.translatable("gtpm.gui.waiting_list")),
                "ME item output waiting-list label lost its position or translation");

        MEItemOutputWaitingListElement list = waitingList(root, output.getWaitingListTarget());
        UITemplate.LDLib2Bounds listBounds = UITemplate.getLDLib2Bounds(list);
        helper.assertTrue(listBounds.x() == 5 && listBounds.y() == 20 &&
                listBounds.width() == 158 && listBounds.height() == VISIBLE_ROWS * ROW_HEIGHT,
                "ME item output list did not preserve its 158x54 three-row viewport");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void menuSessionChallengeAuthenticatesOnlyThePendingOpening(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputBusPartMachine output = createOutput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        List<CapturedAction> actions = new ArrayList<>();
        UIElement root = output.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)), () -> true);
        MEItemOutputWaitingListElement list = waitingList(root, target);
        UUID openingId = list.getOpeningId();
        int requestSequence = requestFull(
                helper, root, actions, holder, target, openingId, "menu-session challenge");
        UUID menuSessionId = UUID.randomUUID();

        list.applyWaitingListMenuSession(UUID.randomUUID(), requestSequence, menuSessionId);
        list.applyWaitingListMenuSession(openingId, requestSequence + 1, menuSessionId);
        helper.assertTrue(actions.size() == 1,
                "challenge for another opening or request generation retried the action");
        list.applyWaitingListMenuSession(openingId, requestSequence, menuSessionId);
        helper.assertTrue(actions.size() == 2 && actions.get(1).holder() == holder &&
                actions.get(1).action().equals(MEOutputWaitingListActions.createRequestFullAction(
                        target, openingId, requestSequence, menuSessionId)),
                "matching server challenge did not retry the same request with its menu nonce");
        list.applyWaitingListMenuSession(openingId, requestSequence, menuSessionId);
        helper.assertTrue(actions.size() == 2,
                "duplicate server challenge retried an already authenticated request");

        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.full(1, 0, 1, List.of()));
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.delta(3, 0, 1,
                List.of(entry(Items.STONE.getDefaultInstance(), 1))));
        int resyncIndex = actions.size();
        root.screenTick();
        helper.assertTrue(actions.size() == resyncIndex + 1 &&
                actions.get(resyncIndex).action().equals(MEOutputWaitingListActions.createRequestFullAction(
                        target, openingId, requestSequence + 1, menuSessionId)),
                "same menu did not reuse its authenticated nonce for a later resync generation");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fullChunksRenderEmptySmallAndVirtualizedLargeLists(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputBusPartMachine output = createOutput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        List<CapturedAction> actions = new ArrayList<>();
        UIElement root = output.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)), () -> true);
        MEItemOutputWaitingListElement list = waitingList(root, target);
        UUID openingId = list.getOpeningId();
        int requestSequence = requestFull(helper, root, actions, holder, target, openingId, "empty initial list");

        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.full(1, 0, 1, List.of()));
        helper.assertTrue(list.getRevision() == 1 && list.getEntries().isEmpty() &&
                list.getScroller().getItemCount() == 0,
                "empty full update did not publish an initialized empty view");

        List<MEOutputWaitingListEntry> three = entries(3, "three");
        requestSequence = requestFullAfterGap(helper, root, actions, holder, target, list, requestSequence, 3,
                "three-entry replacement");
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.full(2, 0, 1, three));
        helper.assertTrue(list.getRevision() == 2 && list.getEntries().equals(three) &&
                list.getScroller().getItemCount() == 3,
                "three-entry full update did not replace the empty view");

        List<MEOutputWaitingListEntry> four = entries(4, "four");
        requestSequence = requestFullAfterGap(helper, root, actions, holder, target, list, requestSequence, 4,
                "four-entry replacement");
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.full(3, 0, 2, four.subList(0, 2)));
        helper.assertTrue(list.getRevision() == 2 && list.getEntries().equals(three),
                "partial full update leaked incomplete rows into the active view");
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.full(3, 1, 2, four.subList(2, 4)));
        helper.assertTrue(list.getRevision() == 3 && list.getEntries().equals(four) &&
                list.getScroller().getItemCount() == 4,
                "four-entry chunked full update was not applied atomically");

        List<MEOutputWaitingListEntry> many = entries(130, "many");
        requestSequence = requestFullAfterGap(helper, root, actions, holder, target, list, requestSequence, 5,
                "large virtualized replacement");
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.full(4, 0, 3, many.subList(0, 64)));
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.full(4, 1, 3, many.subList(64, 128)));
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.full(4, 2, 3, many.subList(128, 130)));
        VirtualScrollerView<MEOutputWaitingListEntry> scroller = list.getScroller();
        scroller.refreshVisibleItems(0, VISIBLE_ROWS * ROW_HEIGHT);
        helper.assertTrue(list.getRevision() == 4 && list.getEntries().equals(many) &&
                scroller.getItemCount() == many.size(),
                "large chunked full update did not publish every entry");
        helper.assertTrue(scroller.getMountedItemCount() > 0 &&
                scroller.getMountedItemCount() <= 8 &&
                scroller.getMountedItemCount() < many.size(),
                "VirtualScroller mounted an unbounded number of waiting-list rows");

        scroller.refreshVisibleItems(scroller.getTotalVirtualHeight() - VISIBLE_ROWS * ROW_HEIGHT,
                VISIBLE_ROWS * ROW_HEIGHT);
        helper.assertTrue(scroller.getFirstMountedIndex() > 0,
                "large waiting list fixture did not move away from its first rows");
        float previousMaxOffset = scroller.getTotalVirtualHeight() - VISIBLE_ROWS * ROW_HEIGHT;
        scroller.verticalScroller.setNormalizedValue(0.5f, false);
        scroller.refreshVisibleItems(previousMaxOffset * 0.5f, VISIBLE_ROWS * ROW_HEIGHT);
        List<MEOutputWaitingListEntry> ten = entries(10, "ten");
        requestSequence = requestFullAfterGap(helper, root, actions, holder, target, list, requestSequence, 6,
                "ten-entry scroll clamp");
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.full(5, 0, 1, ten));
        helper.assertTrue(scroller.getLastMountedIndex() == ten.size() - 1,
                "shrinking the waiting list preserved a proportional offset instead of clamping its old pixels");
        requestSequence = requestFullAfterGap(helper, root, actions, holder, target, list, requestSequence, 7,
                "sub-viewport scroll reset");
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.full(6, 0, 1, three));
        helper.assertTrue(scroller.getFirstMountedIndex() == 0 && scroller.getLastMountedIndex() == 2 &&
                scroller.getMountedItemCount() == 3 && scroller.verticalScroller.getNormalizedValue() == 0,
                "shrinking below one viewport did not reset its obsolete scroll offset");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void itemRowsPreserveComponentsLongAmountsTooltipsAndDisplayOnlyXeiRole(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputBusPartMachine output = createOutput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        List<CapturedAction> actions = new ArrayList<>();
        UIElement root = output.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)), () -> true);
        MEItemOutputWaitingListElement list = waitingList(root, target);
        int requestSequence = requestFull(helper, root, actions, holder, target, list.getOpeningId(),
                "component-bearing row");

        ItemStack namedDiamond = new ItemStack(Items.DIAMOND, 37);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Buffered Diamond"));
        long amount = (long) Integer.MAX_VALUE + 8_192L;
        MEOutputWaitingListEntry namedEntry = new MEOutputWaitingListEntry(AEItemKey.of(namedDiamond), amount);
        apply(list, list.getOpeningId(), requestSequence, MEOutputWaitingListUpdate.full(1, 0, 1,
                List.of(namedEntry, entry(Items.STONE.getDefaultInstance(), 3))));
        list.getScroller().refreshVisibleItems(0, VISIBLE_ROWS * ROW_HEIGHT);

        GTItemSlotElement item = descendants(list.getScroller()).stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .filter(slot -> ItemStack.isSameItemSameComponents(slot.getValue(), namedDiamond))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "ME item output row omitted the component-bearing item"));
        String formattedAmount = String.format("%,d", amount);
        helper.assertTrue(item.getValue().getCount() == 1 &&
                ItemStack.isSameItemSameComponents(item.getValue(), namedDiamond),
                "ME item output row lost components or rendered the long amount as an item count");
        helper.assertTrue(item.getIngredientIO() == IngredientIO.NONE,
                "display-only waiting item was exposed as an XEI recipe role");
        helper.assertTrue(labels(list.getScroller()).stream()
                .anyMatch(label -> label.getValue().getString().equals("x" + formattedAmount)),
                "ME item output row did not render the complete long amount");
        helper.assertTrue(item.getFullTooltipTexts().stream()
                .anyMatch(component -> component.getString().contains(formattedAmount)),
                "ME item output tooltip omitted the complete long amount");

        int actionCount = actions.size();
        click(item, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        click(item, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        root.screenTick();
        helper.assertTrue(actions.size() == actionCount,
                "display-only waiting row sent a mutation action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void deltaBatchesRejectOldOpeningsStaleRevisionsAndMissingChunks(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputBusPartMachine output = createOutput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        List<CapturedAction> actions = new ArrayList<>();
        UIElement root = output.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)), () -> true);
        MEItemOutputWaitingListElement list = waitingList(root, target);
        UUID openingId = list.getOpeningId();
        List<MEOutputWaitingListEntry> initial = entries(3, "initial");
        int requestSequence = requestFull(helper, root, actions, holder, target, openingId, "initial delta state");
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.full(1, 0, 1, initial));

        List<MEOutputWaitingListEntry> foreign = entries(1, "foreign");
        apply(list, UUID.randomUUID(), requestSequence, MEOutputWaitingListUpdate.delta(2, 0, 1, foreign));
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.delta(1, 0, 1,
                List.of(new MEOutputWaitingListEntry(initial.getFirst().key(), 999))));
        helper.assertTrue(list.getRevision() == 1 && list.getEntries().equals(initial),
                "old opening UUID or stale revision polluted the active waiting list");

        MEOutputWaitingListEntry changedFirst = new MEOutputWaitingListEntry(initial.getFirst().key(), 400);
        MEOutputWaitingListEntry removedSecond = new MEOutputWaitingListEntry(initial.get(1).key(), 0);
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.delta(2, 0, 2, List.of(changedFirst)));
        helper.assertTrue(list.getRevision() == 1 && list.getEntries().equals(initial),
                "partial delta batch leaked into the active waiting list");
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.delta(2, 1, 2, List.of(removedSecond)));
        helper.assertTrue(list.getRevision() == 2 && list.getEntries().size() == 2 &&
                list.getEntries().contains(changedFirst) &&
                list.getEntries().stream().noneMatch(entry -> entry.key().equals(initial.get(1).key())),
                "complete delta batch did not atomically update and remove entries");

        List<MEOutputWaitingListEntry> beforeGap = List.copyOf(list.getEntries());
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.delta(4, 0, 1,
                List.of(new MEOutputWaitingListEntry(initial.get(2).key(), 700))));
        helper.assertTrue(list.getRevision() == 2 && list.getEntries().equals(beforeGap),
                "revision gap mutated the waiting list before a full resync");

        requestSequence = requestFull(helper, root, actions, holder, target, openingId, "revision gap");
        root.screenTick();
        helper.assertTrue(actions.size() == 2,
                "revision gap requested a full update on every tick");

        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.full(5, 0, 1, initial));
        long previousClientTime = GTValues.CLIENT_TIME;
        try {
            GTValues.CLIENT_TIME = 1_000;
            apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.delta(6, 0, 3,
                    List.of(new MEOutputWaitingListEntry(initial.getFirst().key(), 800))));
            helper.assertTrue(list.getRevision() == 5 && list.getEntries().equals(initial),
                    "partial delta batch mutated the active waiting list");

            GTValues.CLIENT_TIME = 1_099;
            apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.delta(6, 1, 3,
                    List.of(new MEOutputWaitingListEntry(initial.get(1).key(), 801))));
            helper.assertTrue(list.getRevision() == 5 && list.getEntries().equals(initial),
                    "late delta chunk mutated the active waiting list before publication completion");

            int actionsBeforeTimeout = actions.size();
            list.setActive(false);
            GTValues.CLIENT_TIME = 1_100;
            root.screenTick();
            helper.assertTrue(actions.size() == actionsBeforeTimeout,
                    "inactive waiting-list page processed its missing-chunk timeout while hidden");
            list.setActive(true);
            root.screenTick();
            int retrySequence = assertFullRequest(helper, actions, actionsBeforeTimeout, holder, target, openingId,
                    "missing delta chunk timeout after restoring the page");
            helper.assertTrue(retrySequence > requestSequence,
                    "missing delta chunk retry did not advance the request sequence");

            List<MEOutputWaitingListEntry> recovered = List.of(
                    new MEOutputWaitingListEntry(initial.getFirst().key(), 801), initial.get(1), initial.get(2));
            apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.full(4, 0, 1, recovered));
            helper.assertTrue(list.getEntries().equals(initial),
                    "old request sequence replaced state after the missing-chunk retry");
            apply(list, openingId, retrySequence, MEOutputWaitingListUpdate.full(4, 0, 1, recovered));
            helper.assertTrue(list.getRevision() == 4 && list.getEntries().equals(recovered),
                    "lower-revision authoritative full state did not recover the timed-out publication");
        } finally {
            list.setActive(true);
            GTValues.CLIENT_TIME = previousClientTime;
        }

        assertRejected(helper, () -> MEOutputWaitingListUpdate.delta(8, 0, 1,
                entries(MAX_CHUNK_ENTRIES + 1, "oversized")),
                "delta update accepted more than 64 entries in one chunk");
        assertRejected(helper, () -> MEOutputWaitingListUpdate.full(8, 0, 1,
                entries(MAX_CHUNK_ENTRIES + 1, "oversized-full")),
                "full update accepted more than 64 entries in one chunk");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void chunkAssemblyPreservesDeltaOrderAndRejectsDuplicateFullKeysAndConflicts(
                                                                                               GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputBusPartMachine output = createOutput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        List<CapturedAction> actions = new ArrayList<>();
        UIElement root = output.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)), () -> true);
        MEItemOutputWaitingListElement list = waitingList(root, target);
        UUID openingId = list.getOpeningId();
        List<MEOutputWaitingListEntry> initial = entries(3, "ordered");
        int requestSequence = requestFull(helper, root, actions, holder, target, openingId,
                "ordered initial state");
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.full(1, 0, 1, initial));

        MEOutputWaitingListEntry duplicateKey = new MEOutputWaitingListEntry(initial.getFirst().key(), 50);
        requestSequence = requestFullAfterGap(helper, root, actions, holder, target, list, requestSequence, 3,
                "duplicate full keys");
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.full(2, 0, 2, List.of(initial.getFirst())));
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.full(2, 1, 2, List.of(duplicateKey)));
        helper.assertTrue(list.getRevision() == 1 && list.getEntries().equals(initial),
                "FULL batch accepted one key more than once across chunks");

        requestSequence = requestFull(helper, root, actions, holder, target, openingId,
                "recovery after duplicate full keys");
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.full(3, 0, 1, initial));
        MEOutputWaitingListEntry removedMiddle = new MEOutputWaitingListEntry(initial.get(1).key(), 0);
        MEOutputWaitingListEntry revivedMiddle = new MEOutputWaitingListEntry(initial.get(1).key(), 99);
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.delta(4, 1, 2, List.of(revivedMiddle)));
        helper.assertTrue(list.getRevision() == 3 && list.getEntries().equals(initial),
                "out-of-order partial DELTA batch leaked into the active view");
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.delta(4, 0, 2, List.of(removedMiddle)));
        helper.assertTrue(list.getRevision() == 4 && list.getEntries().equals(List.of(
                initial.getFirst(), initial.get(2), revivedMiddle)),
                "DELTA chunks were not applied in chunk-index order or failed to append a revived key");

        MEOutputWaitingListEntry changedLast = new MEOutputWaitingListEntry(initial.get(2).key(), 120);
        MEOutputWaitingListUpdate repeatedChunk = MEOutputWaitingListUpdate.delta(
                5, 0, 2, List.of(changedLast));
        apply(list, openingId, requestSequence, repeatedChunk);
        apply(list, openingId, requestSequence, repeatedChunk);
        helper.assertTrue(list.getRevision() == 4,
                "identical duplicate DELTA chunk applied an incomplete batch");
        MEOutputWaitingListEntry changedFirst = new MEOutputWaitingListEntry(initial.getFirst().key(), 121);
        apply(list, openingId, requestSequence,
                MEOutputWaitingListUpdate.delta(5, 1, 2, List.of(changedFirst)));
        helper.assertTrue(list.getRevision() == 5 && list.getEntries().contains(changedLast) &&
                list.getEntries().contains(changedFirst),
                "identical duplicate chunk was not handled idempotently");

        List<MEOutputWaitingListEntry> beforeConflict = List.copyOf(list.getEntries());
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.delta(6, 0, 2,
                List.of(new MEOutputWaitingListEntry(initial.getFirst().key(), 200))));
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.delta(6, 0, 2,
                List.of(new MEOutputWaitingListEntry(initial.getFirst().key(), 201))));
        apply(list, openingId, requestSequence, MEOutputWaitingListUpdate.delta(6, 1, 2,
                List.of(new MEOutputWaitingListEntry(initial.get(2).key(), 202))));
        helper.assertTrue(list.getRevision() == 5 && list.getEntries().equals(beforeConflict),
                "conflicting duplicate chunk partially mutated the active waiting list");

        requestFull(helper, root, actions, holder, target, openingId, "conflicting duplicate chunk");
        int actionCount = actions.size();
        root.screenTick();
        helper.assertTrue(actions.size() == actionCount,
                "conflicting duplicate chunk requested full state on every tick");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fullRequestsRequireClientAuthorityAndRemainPendingUntilStateArrives(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputBusPartMachine output = createOutput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        List<CapturedAction> actions = new ArrayList<>();
        AtomicBoolean canSendAction = new AtomicBoolean(false);
        UIElement root = output.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)), canSendAction::get);
        MEItemOutputWaitingListElement list = waitingList(root, target);

        root.screenTick();
        helper.assertTrue(actions.isEmpty(),
                "server-side or otherwise unauthorized page requested waiting-list state");
        long previousClientTime = GTValues.CLIENT_TIME;
        try {
            GTValues.CLIENT_TIME = 2_000;
            canSendAction.set(true);
            int firstSequence = requestFull(helper, root, actions, holder, target, list.getOpeningId(),
                    "initial missing state");
            list.setVisible(false);
            GTValues.CLIENT_TIME = 2_100;
            root.screenTick();
            helper.assertTrue(actions.size() == 1,
                    "hidden waiting-list page processed its full-state timeout while not displayed");
            list.setVisible(true);
            root.screenTick();
            int retrySequence = assertFullRequest(helper, actions, 1, holder, target, list.getOpeningId(),
                    "timed-out initial state after restoring the page");
            helper.assertTrue(retrySequence == firstSequence + 1,
                    "timed-out initial state did not increment its request sequence");

            apply(list, list.getOpeningId(), firstSequence, MEOutputWaitingListUpdate.full(1, 0, 1,
                    entries(1, "obsolete-initial")));
            helper.assertTrue(list.getRevision() == -1 && list.getEntries().isEmpty(),
                    "old request sequence applied a full state after retry");
            apply(list, list.getOpeningId(), retrySequence,
                    MEOutputWaitingListUpdate.full(1, 0, 1, List.of()));
            root.screenTick();
            helper.assertTrue(actions.size() == 2 && list.getRevision() == 1,
                    "initialized empty waiting list requested redundant state");
        } finally {
            list.setVisible(true);
            GTValues.CLIENT_TIME = previousClientTime;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void replacementIncarnationRejectsTheOldElementAndPacketRoute(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputBusPartMachine output = createOutput();
        RoutedMachineUIHolder holder = new RoutedMachineUIHolder(output);
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        List<CapturedAction> actions = new ArrayList<>();
        UIElement root = output.createLDLib2MainElement(player, holder, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)), () -> true);
        holder.modularUI = ModularUI.of(UI.of(root), player);
        ModularUIContainerMenu menu = menu(37, player, holder);
        player.containerMenu = menu;

        MEItemOutputWaitingListElement list = waitingList(root, target);
        int requestSequence = requestFull(helper, root, actions, holder, target, list.getOpeningId(),
                "incarnation route baseline");
        List<MEOutputWaitingListEntry> initial = entries(2, "incarnation");
        apply(list, list.getOpeningId(), requestSequence,
                MEOutputWaitingListUpdate.full(1, 0, 1, initial));
        helper.assertTrue(MEOutputWaitingListRoute.resolve(player, menu.containerId, target) == list,
                "packet route did not resolve the live output bus element before replacement");

        MEOutputBusPartMachine replacement = createOutput();
        MEOutputWaitingListTarget replacementTarget = replacement.getWaitingListTarget();
        helper.assertTrue(target.pos().equals(replacementTarget.pos()) &&
                target.machineDefinitionId().equals(replacementTarget.machineDefinitionId()) &&
                !target.incarnation().equals(replacementTarget.incarnation()),
                "replacement fixture did not change only the output bus incarnation");
        holder.machine = replacement;

        helper.assertFalse(list.matchesWaitingListTarget(target),
                "old element still accepted its captured target after machine replacement");
        helper.assertFalse(list.matchesWaitingListTarget(replacement),
                "old element rebound itself to the replacement incarnation");
        MEOutputWaitingListReceiver routed = MEOutputWaitingListRoute.resolve(player, menu.containerId, target);
        if (routed != null) {
            routed.applyWaitingListUpdate(list.getOpeningId(), requestSequence,
                    MEOutputWaitingListUpdate.delta(2, 0, 1,
                            List.of(new MEOutputWaitingListEntry(initial.getFirst().key(), 999))));
        }
        helper.assertTrue(routed == null && list.getRevision() == 1 && list.getEntries().equals(initial),
                "packet route delivered an update to an element from the replaced incarnation");
        helper.succeed();
    }

    private static int requestFull(GameTestHelper helper, UIElement root, List<CapturedAction> actions,
                                   MachineUIHolder expectedHolder, MEOutputWaitingListTarget target,
                                   UUID openingId, String description) {
        int actionIndex = actions.size();
        root.screenTick();
        return assertFullRequest(helper, actions, actionIndex, expectedHolder, target, openingId, description);
    }

    private static int assertFullRequest(GameTestHelper helper, List<CapturedAction> actions, int actionIndex,
                                         MachineUIHolder expectedHolder, MEOutputWaitingListTarget target,
                                         UUID openingId, String description) {
        helper.assertTrue(actions.size() == actionIndex + 1,
                description + " did not send exactly one full-state request");
        CapturedAction captured = actions.get(actionIndex);
        int requestSequence = captured.action().sequence();
        helper.assertTrue(captured.holder() == expectedHolder && requestSequence >= 0 &&
                captured.action().equals(
                        MEOutputWaitingListActions.createRequestFullAction(target, openingId, requestSequence)),
                description + " did not retain the action holder, target, UUID, and request sequence");
        return requestSequence;
    }

    private static int requestFullAfterGap(GameTestHelper helper, UIElement root, List<CapturedAction> actions,
                                           MachineUIHolder expectedHolder, MEOutputWaitingListTarget target,
                                           MEItemOutputWaitingListElement list, int activeRequestSequence,
                                           long gapRevision, String description) {
        apply(list, list.getOpeningId(), activeRequestSequence,
                MEOutputWaitingListUpdate.delta(gapRevision, 0, 1,
                        List.of(entry(Items.COBBLESTONE.getDefaultInstance(), gapRevision))));
        return requestFull(helper, root, actions, expectedHolder, target, list.getOpeningId(), description);
    }

    private static void apply(MEOutputWaitingListReceiver receiver, UUID openingId, int requestSequence,
                              MEOutputWaitingListUpdate update) {
        receiver.applyWaitingListUpdate(openingId, requestSequence, update);
    }

    private static MEOutputWaitingListEntry entry(ItemStack stack, long amount) {
        return new MEOutputWaitingListEntry(AEItemKey.of(stack), amount);
    }

    private static List<MEOutputWaitingListEntry> entries(int count, String prefix) {
        List<MEOutputWaitingListEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ItemStack stack = new ItemStack(Items.STONE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(prefix + '-' + index));
            entries.add(entry(stack, index + 1L));
        }
        return List.copyOf(entries);
    }

    private static GTLabelElement label(UIElement root, String id) {
        return labels(root).stream()
                .filter(element -> id.equals(element.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ME item output page omitted label " + id));
    }

    private static List<GTLabelElement> labels(UIElement root) {
        return descendants(root).stream()
                .filter(GTLabelElement.class::isInstance)
                .map(GTLabelElement.class::cast)
                .toList();
    }

    private static MEItemOutputWaitingListElement waitingList(UIElement root,
                                                              MEOutputWaitingListTarget target) {
        String elementId = MEOutputWaitingListReceiver.elementId(target.pos());
        if (root instanceof MEItemOutputWaitingListElement waitingList &&
                elementId.equals(waitingList.getId())) {
            return waitingList;
        }
        return descendants(root).stream()
                .filter(MEItemOutputWaitingListElement.class::isInstance)
                .map(MEItemOutputWaitingListElement.class::cast)
                .filter(element -> elementId.equals(element.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "ME item output page omitted its waiting-list element"));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static ModularUIContainerMenu menu(int containerId, ServerPlayer player,
                                               IContainerUIHolder holder) {
        MenuType<ModularUIContainerMenu> menuType = (MenuType) MenuType.GENERIC_9x1;
        return new ModularUIContainerMenu(menuType, containerId, player.getInventory(), holder);
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

    private static void click(UIElement target, int button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = target;
        event.button = button;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static void assertRejected(GameTestHelper helper, Runnable operation, String message) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player,
                                                           MEOutputBusPartMachine output,
                                                           MachineUIHolder holder) {
        UIElement root = output.createLDLib2UI(player, holder).getRootElement();
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("ME item output did not create an LDLib2 Fancy shell");
        }
        return shell;
    }

    private static UIElement pageRoot(LDLib2FancyMachineUIElement shell) {
        return shell.getChildren().getFirst().getChildren().getFirst();
    }

    private static ServerPlayer testPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return player;
    }

    private static MEOutputBusPartMachine createOutput() {
        MetaMachine machine = GTAEMachines.ITEM_EXPORT_BUS_ME.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.ITEM_EXPORT_BUS_ME.defaultBlockState());
        if (!(machine instanceof MEOutputBusPartMachine output) ||
                machine.getClass() != MEOutputBusPartMachine.class) {
            throw new IllegalStateException("ME item output definition created the wrong machine type");
        }
        return output;
    }

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
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
        public MetaMachine getMachine() {
            return machine;
        }
    }

    private static final class RoutedMachineUIHolder implements IContainerUIHolder, MachineUIHolder {

        private MetaMachine machine;
        private ModularUI modularUI;

        private RoutedMachineUIHolder(MetaMachine machine) {
            this.machine = machine;
        }

        @Override
        public ModularUI createUI(Player player) {
            if (modularUI == null) {
                throw new IllegalStateException("Routed test holder requires its menu UI before construction.");
            }
            return modularUI;
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

    private record CapturedAction(MachineUIHolder holder, SyncActionData action) {}
}
