package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.integration.ae2.gui.element.MEFluidOutputWaitingListElement;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import io.netty.buffer.Unpooled;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEFluidOutputHatchLDLib2UITest {

    private static final String BATCH = "MEFluidOutputHatchLDLib2UI";
    private static final int PAGE_WIDTH = 170;
    private static final int PAGE_HEIGHT = 74;
    private static final int ROW_HEIGHT = 18;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void openingLayoutAndFancySemanticsAreBoundToTheExactHatch(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputHatchPartMachine output = createOutput();
        MEOutputHatchPartMachine replacement = createOutput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);

        helper.assertTrue(output.canCreateLDLib2UI(player, holder),
                "ME fluid output rejected its matching holder");
        LDLib2FancyUIProvider standalonePage = output.createLDLib2Page(player, holder);
        LDLib2FancyUIProvider contextualPage = output.createLDLib2FancyPage(player, holder);
        helper.assertTrue(standalonePage != contextualPage,
                "standalone and contextual fluid output openings reused one page instance");

        boolean mismatchedHolderRejected = false;
        try {
            output.createLDLib2Page(player, new MutableMachineUIHolder(replacement));
        } catch (IllegalArgumentException expected) {
            mismatchedHolderRejected = true;
        }
        helper.assertTrue(mismatchedHolderRejected,
                "ME fluid output accepted another machine's holder");

        MEOutputHatchPartMachine wrongDefinition = new MEOutputHatchPartMachine(
                info(GTAEMachines.FLUID_IMPORT_HATCH_ME));
        MutableMachineUIHolder wrongDefinitionHolder = new MutableMachineUIHolder(wrongDefinition);
        helper.assertFalse(wrongDefinition.canCreateLDLib2UI(player, wrongDefinitionHolder),
                "ME fluid output page accepted a non-output definition");
        boolean wrongDefinitionRejected = false;
        try {
            wrongDefinition.createLDLib2Page(player, wrongDefinitionHolder);
        } catch (IllegalStateException expected) {
            wrongDefinitionRejected = true;
        }
        helper.assertTrue(wrongDefinitionRejected,
                "wrong-definition ME fluid output created a page directly");

        LDLib2FancyMachineUIElement shell = createShell(player, output, holder);
        UIElement root = pageRoot(shell);
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        MEFluidOutputWaitingListElement list = waitingList(root, target);
        helper.assertTrue(root.getSizeWidth() == PAGE_WIDTH && root.getSizeHeight() == PAGE_HEIGHT &&
                list.getPositionX() - root.getPositionX() == 5 &&
                list.getPositionY() - root.getPositionY() == 20 &&
                list.getSizeWidth() == 158 && list.getSizeHeight() == 3 * ROW_HEIGHT,
                "ME fluid output page lost its 170x74 body or 158x54 waiting-list viewport");
        helper.assertTrue(label(root, "me_network_status").getPositionY() - root.getPositionY() == 0 &&
                label(root, "me_output_waiting_list_label").getPositionY() - root.getPositionY() == 10,
                "ME fluid output page lost its status or waiting-list label position");
        helper.assertTrue(MEOutputWaitingListReceiver.elementId(target.pos()).equals(list.getId()),
                "ME fluid output page did not use the shared routed waiting-list element id");
        helper.assertTrue(standalonePage.getTitle().equals(
                Component.translatable(output.getDefinition().getDescriptionId())) &&
                standalonePage.getTabIcon() != IGuiTexture.EMPTY,
                "ME fluid output Fancy page lost its title or icon");
        LDLib2FancyUIProvider.PageGroupingData grouping = standalonePage.getPageGroupingData();
        helper.assertTrue(grouping != null &&
                "gtpm.multiblock.page_switcher.io.export".equals(grouping.groupKey()) &&
                grouping.groupPositionWeight() == 2 &&
                shell.getSideTabsElement().getChildren().size() == 2 &&
                shell.getConfiguratorPanel().getChildren().size() == 1,
                "ME fluid output Fancy page lost its export grouping, directional tab, or working configurator");

        MutableMachineUIHolder controllerHolder = new MutableMachineUIHolder(replacement);
        List<CapturedAction> contextualActions = new ArrayList<>();
        UIElement contextualRoot = output.createLDLib2MainElement(player, holder, controllerHolder,
                (sentHolder, action) -> contextualActions.add(new CapturedAction(sentHolder, action)), () -> true);
        MEFluidOutputWaitingListElement contextualList = waitingList(contextualRoot, target);
        requestFull(helper, contextualRoot, contextualActions, controllerHolder, target,
                contextualList.getOpeningId());

        helper.assertTrue(target.pos().equals(replacement.getWaitingListTarget().pos()) &&
                target.machineDefinitionId().equals(replacement.getWaitingListTarget().machineDefinitionId()) &&
                !target.incarnation().equals(replacement.getWaitingListTarget().incarnation()),
                "replacement fixture did not change only the fluid output incarnation");
        holder.machine = replacement;
        helper.assertFalse(list.matchesWaitingListTarget(target) || list.matchesWaitingListTarget(replacement),
                "old fluid waiting-list element rebound to a replacement hatch");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fluidRowsPreserveComponentsLongAmountsAndRejectItemKeys(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputHatchPartMachine output = createOutput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        List<CapturedAction> actions = new ArrayList<>();
        UIElement root = output.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)), () -> true);
        MEFluidOutputWaitingListElement list = waitingList(root, target);
        int requestSequence = requestFull(helper, root, actions, holder, target, list.getOpeningId());

        FluidStack namedWater = new FluidStack(Fluids.WATER, 1_000);
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Buffered Water"));
        long amount = (long) Integer.MAX_VALUE + 8_192L;
        MEOutputWaitingListEntry namedEntry = new MEOutputWaitingListEntry(
                AEFluidKey.of(namedWater), amount);
        apply(list, list.getOpeningId(), requestSequence,
                roundTrip(helper, MEOutputWaitingListUpdate.full(1, 0, 1, List.of(namedEntry))));
        list.getScroller().refreshVisibleItems(0, 3 * ROW_HEIGHT);

        GTFluidSlotElement fluid = descendants(list.getScroller()).stream()
                .filter(GTFluidSlotElement.class::isInstance)
                .map(GTFluidSlotElement.class::cast)
                .filter(slot -> FluidStack.isSameFluidSameComponents(slot.getFluid(), namedWater))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "ME fluid output row omitted the component-bearing fluid"));
        String formattedAmount = String.format("%,d", amount);
        helper.assertTrue(fluid.getFluid().getAmount() == 1 &&
                FluidStack.isSameFluidSameComponents(fluid.getFluid(), namedWater),
                "ME fluid output row lost components or rendered the long amount as a FluidStack amount");
        helper.assertTrue(labels(list.getScroller()).stream()
                .anyMatch(label -> label.getValue().getString().equals("x" + formattedAmount)),
                "ME fluid output row did not render the complete long amount");
        helper.assertTrue(tooltips(fluid).stream()
                .anyMatch(component -> component.getString().contains(formattedAmount + " mB")),
                "ME fluid output tooltip omitted the complete long amount");
        helper.assertTrue(!fluid.isAllowClickFilled() && !fluid.isAllowClickDrained(),
                "ME fluid output row exposed a bucket mutation path");
        int actionCount = actions.size();
        click(fluid, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        click(fluid, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        root.screenTick();
        helper.assertTrue(actions.size() == actionCount,
                "display-only waiting fluid sent a mutation action");

        apply(list, list.getOpeningId(), requestSequence, MEOutputWaitingListUpdate.delta(2, 0, 1,
                List.of(new MEOutputWaitingListEntry(AEItemKey.of(new ItemStack(Items.STONE)), 1))));
        root.screenTick();
        helper.assertTrue(list.getRevision() == 1 && list.getEntries().equals(List.of(namedEntry)) &&
                actions.size() == actionCount + 1,
                "fluid waiting-list accepted an item key or failed to request authoritative recovery");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fullPublicationDeadlineStartsAtItsFirstChunkAndDoesNotSlide(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEOutputHatchPartMachine output = createOutput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(output);
        MEOutputWaitingListTarget target = output.getWaitingListTarget();
        List<CapturedAction> actions = new ArrayList<>();
        UIElement root = output.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)), () -> true);
        MEFluidOutputWaitingListElement list = waitingList(root, target);
        List<MEOutputWaitingListEntry> recovered = List.of(
                new MEOutputWaitingListEntry(AEFluidKey.of(Fluids.WATER), 1_000),
                new MEOutputWaitingListEntry(AEFluidKey.of(Fluids.LAVA), 2_000));

        long previousClientTime = GTValues.CLIENT_TIME;
        try {
            GTValues.CLIENT_TIME = 5_000;
            int requestSequence = requestFull(helper, root, actions, holder, target, list.getOpeningId());

            GTValues.CLIENT_TIME = 5_099;
            apply(list, list.getOpeningId(), requestSequence,
                    MEOutputWaitingListUpdate.full(1, 0, 3, List.of(recovered.getFirst())));
            GTValues.CLIENT_TIME = 5_100;
            root.screenTick();
            helper.assertTrue(actions.size() == 1,
                    "first fluid FULL chunk retained the expired request deadline");

            GTValues.CLIENT_TIME = 5_198;
            apply(list, list.getOpeningId(), requestSequence,
                    MEOutputWaitingListUpdate.full(1, 1, 3, List.of(recovered.get(1))));
            root.screenTick();
            helper.assertTrue(actions.size() == 1,
                    "late fluid FULL chunk replaced its publication deadline");

            GTValues.CLIENT_TIME = 5_199;
            root.screenTick();
            helper.assertTrue(actions.size() == 2,
                    "incomplete fluid FULL publication did not time out from its first chunk");
            int retrySequence = actions.get(1).action().sequence();
            helper.assertTrue(retrySequence == requestSequence + 1,
                    "fluid FULL publication timeout did not advance its request sequence");

            apply(list, list.getOpeningId(), requestSequence,
                    MEOutputWaitingListUpdate.full(1, 0, 1, List.of(
                            new MEOutputWaitingListEntry(AEFluidKey.of(Fluids.WATER), 3_000))));
            helper.assertTrue(list.getRevision() == -1 && list.getEntries().isEmpty(),
                    "old fluid FULL state applied after its publication timed out");
            apply(list, list.getOpeningId(), retrySequence,
                    MEOutputWaitingListUpdate.full(1, 0, 1, recovered));
            helper.assertTrue(list.getRevision() == 1 && list.getEntries().equals(recovered),
                    "replacement fluid FULL state did not recover the timed-out publication");
        } finally {
            GTValues.CLIENT_TIME = previousClientTime;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fluidStoragePublishesExecutedComponentKeysWithinItsCapacity(GameTestHelper helper) {
        MEOutputHatchPartMachine output = createOutput();
        MEOutputWaitingListPublisher publisher = output.getWaitingListPublisher();
        FluidStack namedWater = new FluidStack(Fluids.WATER, 1_000);
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Buffered Water"));

        helper.assertTrue(output.tank.getTanks() == 128 &&
                output.tank.getTankCapacity(0) == Integer.MAX_VALUE,
                "ME fluid output migration changed its virtual tank count or capacity");
        helper.assertTrue(output.tank.fill(namedWater, IFluidHandler.FluidAction.EXECUTE) == 0,
                "ME fluid output exposed its recipe-only storage through the fluid capability");
        publisher.serverTick();
        helper.assertTrue(publisher.revision() == 0,
                "rejected capability write published a waiting-list revision");
        helper.assertTrue(output.tank.fillInternal(namedWater, IFluidHandler.FluidAction.EXECUTE) == 1_000,
                "ME fluid output did not accept an executed component-bearing fluid write");
        publisher.serverTick();
        helper.assertTrue(publisher.revision() == 1,
                "executed fluid write did not publish one waiting-list revision");

        helper.assertTrue(output.tank.fillInternal(namedWater.copyWithAmount(250),
                IFluidHandler.FluidAction.SIMULATE) == 250,
                "ME fluid output simulation changed its accepted amount");
        publisher.serverTick();
        helper.assertTrue(publisher.revision() == 1,
                "simulated fluid write published a waiting-list revision");

        int remainingCapacity = Integer.MAX_VALUE - namedWater.getAmount();
        helper.assertTrue(output.tank.fillInternal(namedWater.copyWithAmount(Integer.MAX_VALUE),
                IFluidHandler.FluidAction.EXECUTE) == remainingCapacity,
                "simulation changed storage or the component key exceeded its integer capacity");
        publisher.serverTick();
        helper.assertTrue(publisher.revision() == 2,
                "capacity-filling fluid write did not publish exactly one revision");
        helper.assertTrue(output.tank.fillInternal(namedWater.copyWithAmount(1),
                IFluidHandler.FluidAction.EXECUTE) == 0,
                "full component-bearing fluid key accepted an overflow write");
        publisher.serverTick();
        helper.assertTrue(publisher.revision() == 2,
                "rejected overflow write published a waiting-list revision");

        FluidStack plainWater = new FluidStack(Fluids.WATER, 500);
        helper.assertTrue(output.tank.fillInternal(plainWater, IFluidHandler.FluidAction.EXECUTE) == 500,
                "fluid storage collapsed distinct component-bearing and plain fluid keys");
        publisher.serverTick();
        helper.assertTrue(publisher.revision() == 3,
                "distinct plain fluid key did not publish its own revision");
        helper.succeed();
    }

    private static int requestFull(GameTestHelper helper, UIElement root, List<CapturedAction> actions,
                                   MachineUIHolder expectedHolder, MEOutputWaitingListTarget target,
                                   UUID openingId) {
        root.screenTick();
        helper.assertTrue(actions.size() == 1,
                "ME fluid output did not send exactly one initial full-state request");
        CapturedAction captured = actions.getFirst();
        int requestSequence = captured.action().sequence();
        helper.assertTrue(captured.holder() == expectedHolder && requestSequence >= 0 &&
                captured.action().equals(
                        MEOutputWaitingListActions.createRequestFullAction(target, openingId, requestSequence)),
                "ME fluid output request lost its holder, target, opening, or sequence");
        return requestSequence;
    }

    private static void apply(MEOutputWaitingListReceiver receiver, UUID openingId, int requestSequence,
                              MEOutputWaitingListUpdate update) {
        receiver.applyWaitingListUpdate(openingId, requestSequence, update);
    }

    private static MEOutputWaitingListUpdate roundTrip(GameTestHelper helper,
                                                       MEOutputWaitingListUpdate update) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            MEOutputWaitingListUpdate.STREAM_CODEC.encode(buffer, update);
            return MEOutputWaitingListUpdate.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static List<Component> tooltips(UIElement target) {
        UIEvent event = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
        event.target = target;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
        if (event.hoverTooltips == null) {
            throw new IllegalStateException("ME fluid output row omitted its hover tooltips");
        }
        return event.hoverTooltips.tooltipTexts();
    }

    private static void click(UIElement target, int button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = target;
        event.button = button;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static GTLabelElement label(UIElement root, String id) {
        return labels(root).stream()
                .filter(element -> id.equals(element.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ME fluid output page omitted label " + id));
    }

    private static List<GTLabelElement> labels(UIElement root) {
        return descendants(root).stream()
                .filter(GTLabelElement.class::isInstance)
                .map(GTLabelElement.class::cast)
                .toList();
    }

    private static MEFluidOutputWaitingListElement waitingList(UIElement root,
                                                               MEOutputWaitingListTarget target) {
        String elementId = MEOutputWaitingListReceiver.elementId(target.pos());
        if (root instanceof MEFluidOutputWaitingListElement waitingList &&
                elementId.equals(waitingList.getId())) {
            return waitingList;
        }
        return descendants(root).stream()
                .filter(MEFluidOutputWaitingListElement.class::isInstance)
                .map(MEFluidOutputWaitingListElement.class::cast)
                .filter(element -> elementId.equals(element.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "ME fluid output page omitted its waiting-list element"));
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

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player,
                                                           MEOutputHatchPartMachine output,
                                                           MachineUIHolder holder) {
        UIElement root = output.createLDLib2UI(player, holder).getRootElement();
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("ME fluid output did not create an LDLib2 Fancy shell");
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

    private static MEOutputHatchPartMachine createOutput() {
        MetaMachine machine = GTAEMachines.FLUID_EXPORT_HATCH_ME.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.FLUID_EXPORT_HATCH_ME.defaultBlockState());
        if (!(machine instanceof MEOutputHatchPartMachine output) ||
                machine.getClass() != MEOutputHatchPartMachine.class) {
            throw new IllegalStateException("ME fluid output definition created the wrong machine type");
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

    private record CapturedAction(MachineUIHolder holder, SyncActionData action) {}
}
