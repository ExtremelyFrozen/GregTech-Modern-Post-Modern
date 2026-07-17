package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotBundle;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotSessionElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemHandlerRoute;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotBinding;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotClientOpening;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotDefinition;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotManifest;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotOpeningToken;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotServerOpening;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotTransition;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.network.packets.CPacketDynamicItemSlotSelectionToServer;
import com.gregtechceu.gtceu.common.network.packets.SPacketDynamicItemSlotSelectionToClient;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("unused")
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTDynamicItemSlotContainerMenuTest {

    private static final String BATCH = "GTDynamicItemSlotContainerMenu";
    private static final UUID TARGET_ID = new UUID(0, 1);
    private static final UUID TARGET_INCARNATION = new UUID(0, 3);
    private static final UUID BINDING_ID = new UUID(0, 2);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidSlotIdsAreRejectedForEveryClickType(GameTestHelper helper) {
        MenuFixture fixture = createFixture(helper);

        for (ClickType clickType : ClickType.values()) {
            fixture.setInteractionBaseline();
            MenuState before = MenuState.capture(fixture);
            fixture.menu().clicked(fixture.menu().slots.size(), clickButton(clickType), clickType, fixture.player());
            assertStateUnchanged(helper, fixture, before,
                    "future slot id changed menu state for " + clickType);

            fixture.setInteractionBaseline();
            before = MenuState.capture(fixture);
            fixture.menu().clicked(-1, clickButton(clickType), clickType, fixture.player());
            assertStateUnchanged(helper, fixture, before,
                    "negative slot id changed menu state for " + clickType);
        }

        fixture.setInteractionBaseline();
        MenuState before = MenuState.capture(fixture);
        fixture.menu().quickMoveStack(fixture.player(), -1);
        fixture.menu().quickMoveStack(fixture.player(), fixture.menu().slots.size());
        assertStateUnchanged(helper, fixture, before,
                "direct quick-move accepted an out-of-range source slot id");

        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void disabledSlotsRejectEveryVanillaInteractionPath(GameTestHelper helper) {
        MenuFixture fixture = createFixture(helper);
        int dynamicSlotId = fixture.dynamicElement().getSlot().index;

        for (ClickType clickType : ClickType.values()) {
            fixture.setInteractionBaseline();
            MenuState before = MenuState.capture(fixture);
            fixture.menu().clicked(dynamicSlotId, clickButton(clickType), clickType, fixture.player());
            assertStateUnchanged(helper, fixture, before,
                    "disabled dynamic slot changed menu state for " + clickType);
        }

        fixture.setInteractionBaseline();
        ItemStack disabledSource = fixture.machine().dynamicHandler.getStackInSlot(0).copy();
        fixture.menu().quickMoveStack(fixture.player(), dynamicSlotId);
        helper.assertTrue(ItemStack.matches(
                disabledSource, fixture.machine().dynamicHandler.getStackInSlot(0)),
                "quick-move extracted from a disabled dynamic source slot");

        fixture.dynamicElement().setInteractionEnabled(true);
        fixture.player().getInventory().setItem(0, ItemStack.EMPTY);
        fixture.menu().quickMoveStack(fixture.player(), dynamicSlotId);
        helper.assertTrue(fixture.machine().dynamicHandler.getStackInSlot(0).isEmpty() &&
                ItemStack.matches(disabledSource, fixture.player().getInventory().getItem(0)),
                "enabled dynamic source did not expose a working quick-move path");
        fixture.dynamicElement().setInteractionEnabled(false);

        fixture.machine().fixedElement.setCanPutItems(false);
        fixture.machine().fixedHandler.setStackInSlot(0, ItemStack.EMPTY);
        fixture.machine().dynamicHandler.setStackInSlot(0, ItemStack.EMPTY);
        ItemStack playerSource = new ItemStack(Items.DIAMOND, 8);
        fixture.player().getInventory().setItem(0, playerSource.copy());
        fixture.dynamicElement().setInteractionEnabled(true);
        helper.assertTrue(fixture.menu().isValidQuickMoveDestination(
                fixture.dynamicElement().getSlot(), playerSource, true),
                "enabled dynamic destination did not expose a working quick-move path");
        fixture.menu().quickMoveStack(fixture.player(), fixture.machine().playerElement.getSlot().index);
        helper.assertTrue(fixture.player().getInventory().getItem(0).isEmpty() &&
                ItemStack.matches(playerSource, fixture.machine().dynamicHandler.getStackInSlot(0)),
                "enabled dynamic destination did not receive a quick-moved stack");

        fixture.dynamicElement().setInteractionEnabled(false);
        fixture.machine().dynamicHandler.setStackInSlot(0, ItemStack.EMPTY);
        fixture.player().getInventory().setItem(0, playerSource.copy());
        fixture.menu().quickMoveStack(fixture.player(), fixture.machine().playerElement.getSlot().index);
        helper.assertTrue(ItemStack.matches(playerSource, fixture.player().getInventory().getItem(0)) &&
                fixture.machine().dynamicHandler.getStackInSlot(0).isEmpty(),
                "quick-move inserted into a disabled dynamic destination slot");
        helper.assertFalse(fixture.menu().isValidQuickMoveDestination(
                fixture.dynamicElement().getSlot(), playerSource, true),
                "disabled dynamic slot was exposed as a quick-move destination");

        Slot fixedSlot = fixture.machine().fixedElement.getSlot();
        Slot dynamicSlot = fixture.dynamicElement().getSlot();
        fixture.dynamicElement().setInteractionEnabled(true);
        helper.assertTrue(fixture.menu().canDragTo(dynamicSlot),
                "enabled dynamic slot did not expose a working drag path");
        helper.assertTrue(fixture.menu().canTakeItemForPickAll(
                Items.IRON_INGOT.getDefaultInstance(), dynamicSlot),
                "enabled dynamic slot did not expose a working pickup-all path");
        fixture.dynamicElement().setInteractionEnabled(false);
        helper.assertTrue(fixture.menu().canDragTo(fixedSlot),
                "fixture's enabled fixed slot unexpectedly rejected drag interaction");
        helper.assertFalse(fixture.menu().canDragTo(dynamicSlot),
                "disabled dynamic slot accepted drag interaction");
        helper.assertTrue(fixture.menu().canTakeItemForPickAll(Items.IRON_INGOT.getDefaultInstance(), fixedSlot),
                "fixture's enabled fixed slot unexpectedly rejected pickup-all interaction");
        helper.assertFalse(fixture.menu().canTakeItemForPickAll(
                Items.IRON_INGOT.getDefaultInstance(), dynamicSlot),
                "disabled dynamic slot accepted pickup-all interaction");

        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void rejectedQuickCraftTargetsResetVanillaState(GameTestHelper helper) {
        MenuFixture fixture = createFixture(helper);

        assertQuickCraftReset(helper, fixture, fixture.dynamicElement().getSlot().index,
                "disabled dynamic quick-craft target");
        assertQuickCraftReset(helper, fixture, fixture.menu().slots.size(),
                "future quick-craft target");

        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void targetAndOverviewSelectionApplyOnlyAfterServerAcknowledgement(GameTestHelper helper) {
        MenuFixture fixture = createFixture(helper);
        OpeningFixture opening = activateOpening(helper, fixture);

        CPacketDynamicItemSlotSelectionToServer targetRequest = fixture.menu()
                .prepareTargetSelectionRequest(opening.client(), TARGET_ID)
                .orElseThrow();
        helper.assertTrue(targetRequest.selection().bindingId().equals(Optional.of(BINDING_ID)),
                "logical target did not resolve to its unique active binding");
        helper.assertFalse(fixture.dynamicElement().isInteractionEnabled(),
                "target request enabled its binding before the server ACK");
        helper.assertTrue(fixture.machine().selectedBindings.isEmpty(),
                "target request changed the business page before the server ACK");

        helper.assertTrue(opening.server().receiveSelectionRequest(
                targetRequest.token(), targetRequest.selection(),
                binding -> binding.targetId().equals(TARGET_ID)) == DynamicItemSlotTransition.ACCEPTED,
                "server rejected the target request produced by the menu");
        SPacketDynamicItemSlotSelectionToClient targetAcknowledgement = new SPacketDynamicItemSlotSelectionToClient(
                targetRequest.token(), targetRequest.selection());
        helper.assertTrue(fixture.menu().applySelectionAcknowledgement(
                opening.client(), targetAcknowledgement.token(), targetAcknowledgement.selection()) ==
                DynamicItemSlotTransition.ACCEPTED,
                "menu rejected the server target acknowledgement");
        helper.assertTrue(fixture.dynamicElement().isInteractionEnabled() &&
                fixture.machine().selectedBindings.equals(List.of(opening.binding())),
                "target acknowledgement did not enable and publish the confirmed binding");

        CPacketDynamicItemSlotSelectionToServer overviewRequest = fixture.menu()
                .prepareOverviewRequest(opening.client())
                .orElseThrow();
        helper.assertTrue(overviewRequest.selection().bindingId().isEmpty(),
                "overview request unexpectedly carried a binding");
        helper.assertFalse(fixture.dynamicElement().isInteractionEnabled(),
                "overview request left the old binding interactive before the server ACK");
        helper.assertTrue(fixture.machine().selectedBindings.equals(List.of(opening.binding())),
                "overview request changed the business page before the server ACK");

        helper.assertTrue(opening.server().receiveSelectionRequest(
                overviewRequest.token(), overviewRequest.selection(), binding -> false) ==
                DynamicItemSlotTransition.ACCEPTED,
                "server rejected the overview request produced by the menu");
        SPacketDynamicItemSlotSelectionToClient overviewAcknowledgement = new SPacketDynamicItemSlotSelectionToClient(
                overviewRequest.token(), overviewRequest.selection());
        helper.assertTrue(fixture.menu().applySelectionAcknowledgement(
                opening.client(), overviewAcknowledgement.token(), overviewAcknowledgement.selection()) ==
                DynamicItemSlotTransition.ACCEPTED,
                "menu rejected the server overview acknowledgement");
        helper.assertFalse(fixture.dynamicElement().isInteractionEnabled(),
                "overview acknowledgement enabled a dynamic binding");
        helper.assertTrue(fixture.machine().selectedBindings.isEmpty(),
                "overview acknowledgement did not publish an empty selection");
        helper.succeed();
    }

    private static void assertQuickCraftReset(GameTestHelper helper, MenuFixture fixture,
                                              int rejectedSlotId, String rejectedTarget) {
        fixture.machine().fixedHandler.setStackInSlot(0, ItemStack.EMPTY);
        fixture.machine().dynamicHandler.setStackInSlot(0, ItemStack.EMPTY);
        ItemStack carried = new ItemStack(Items.DIAMOND, 4);
        fixture.menu().setCarried(carried.copy());

        fixture.menu().clicked(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_START,
                        AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE),
                ClickType.QUICK_CRAFT, fixture.player());
        fixture.menu().clicked(fixture.machine().fixedElement.getSlot().index,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE,
                        AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE),
                ClickType.QUICK_CRAFT, fixture.player());
        fixture.menu().clicked(rejectedSlotId,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE,
                        AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE),
                ClickType.QUICK_CRAFT, fixture.player());
        fixture.menu().clicked(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_END,
                        AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE),
                ClickType.QUICK_CRAFT, fixture.player());

        helper.assertTrue(fixture.machine().fixedHandler.getStackInSlot(0).isEmpty() &&
                ItemStack.matches(carried, fixture.menu().getCarried()),
                rejectedTarget + " did not clear the pending quick-craft slot set");
    }

    private static MenuFixture createFixture(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);

        MachineDefinition definition = GTMachines.WOODEN_CRATE;
        BlockPos machinePos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(machinePos, definition.defaultBlockState());
        helper.getLevel().removeBlockEntity(machinePos);
        TestDynamicSlotMachine machine = new TestDynamicSlotMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), machinePos, definition.defaultBlockState()));
        machine.setLevel(helper.getLevel());
        helper.getLevel().setBlockEntity(machine);
        helper.assertTrue(MetaMachine.getMachine(helper.getLevel(), machinePos) == machine,
                "test world did not install the dynamic-slot provider machine");

        DynamicItemSlotMachineUIHolder holder = new DynamicItemSlotMachineUIHolder(player, machine);
        GTDynamicItemSlotContainerMenu menu = new GTDynamicItemSlotContainerMenu(
                menuType(), 41, player.getInventory(), holder);
        player.containerMenu = menu;

        int firstDynamicSlotId = menu.slots.size();
        GTDynamicItemSlotBundle bundle = machine.sessionElement.appendBinding(new DynamicItemSlotBinding(
                BINDING_ID, TARGET_ID, TARGET_INCARNATION, firstDynamicSlotId, 1, true));
        GTDynamicItemSlotElement dynamicElement = bundle.elements().getFirst();
        helper.assertTrue(menu.slots.size() == firstDynamicSlotId + 1 &&
                dynamicElement.getSlot().index == firstDynamicSlotId &&
                menu.slots.get(firstDynamicSlotId) == dynamicElement.getSlot(),
                "fixture did not append its dynamic element as a real Vanilla menu slot");
        helper.assertFalse(dynamicElement.isInteractionEnabled(),
                "newly appended dynamic slot was interactive before protocol activation");
        helper.assertTrue(machine.selectedBindings.isEmpty(),
                "fixture applied a dynamic-slot selection without a protocol acknowledgement");
        return new MenuFixture(player, menu, machine, dynamicElement);
    }

    private static OpeningFixture activateOpening(GameTestHelper helper, MenuFixture fixture) {
        int baseSlotCount = fixture.dynamicElement().getSlot().index;
        DynamicItemSlotBinding binding = new DynamicItemSlotBinding(
                BINDING_ID, TARGET_ID, TARGET_INCARNATION, baseSlotCount, 1, true);
        DynamicItemSlotManifest manifest = new DynamicItemSlotManifest(
                0, 1, new UUID(0, 3), 0, baseSlotCount, List.of(binding));
        DynamicItemSlotOpeningToken token = DynamicItemSlotOpeningToken.of(
                fixture.menu().containerId, fixture.menu().getMenuSessionId(), manifest);
        DynamicItemSlotClientOpening client = new DynamicItemSlotClientOpening(
                fixture.menu().containerId, fixture.menu().getMenuSessionId(), baseSlotCount);
        DynamicItemSlotServerOpening server = new DynamicItemSlotServerOpening(
                fixture.menu().containerId, fixture.menu().getMenuSessionId(), baseSlotCount);

        helper.assertTrue(server.beginManifest(manifest) == DynamicItemSlotTransition.ACCEPTED &&
                client.receiveManifest(token, manifest) == DynamicItemSlotTransition.ACCEPTED,
                "opening states rejected the initial manifest");
        helper.assertTrue(client.completePreparation(token, Set.of(BINDING_ID), Set.of(BINDING_ID)) ==
                DynamicItemSlotTransition.ACCEPTED,
                "client opening rejected the real appended and resolved binding");
        helper.assertTrue(server.receivePreparedAcknowledgement(token) == DynamicItemSlotTransition.ACCEPTED &&
                server.markDisabledSlotsAppended(token, Set.of(BINDING_ID)) ==
                        DynamicItemSlotTransition.ACCEPTED &&
                server.markFullSnapshotSent(token) == DynamicItemSlotTransition.ACCEPTED,
                "server opening rejected the prepared dynamic slot");
        helper.assertTrue(client.receiveActivation(token, Optional.empty()) == DynamicItemSlotTransition.ACCEPTED &&
                server.receiveActivatedAcknowledgement(token) == DynamicItemSlotTransition.ACCEPTED,
                "opening states rejected activation without a selected page");
        return new OpeningFixture(client, server, binding);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static MenuType<ModularUIContainerMenu> menuType() {
        return (MenuType) MenuType.GENERIC_9x1;
    }

    private static int clickButton(ClickType clickType) {
        return clickType == ClickType.QUICK_CRAFT ? AbstractContainerMenu.getQuickcraftMask(
                AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE,
                AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE) : 0;
    }

    private static void assertStateUnchanged(GameTestHelper helper, MenuFixture fixture,
                                             MenuState expected, String message) {
        helper.assertTrue(expected.matches(fixture), message);
    }

    private record MenuFixture(ServerPlayer player, GTDynamicItemSlotContainerMenu menu,
                               TestDynamicSlotMachine machine, GTDynamicItemSlotElement dynamicElement) {

        private void setInteractionBaseline() {
            machine.fixedHandler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
            machine.dynamicHandler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 8));
            menu.setCarried(new ItemStack(Items.IRON_INGOT, 4));
        }
    }

    private record OpeningFixture(DynamicItemSlotClientOpening client, DynamicItemSlotServerOpening server,
                                  DynamicItemSlotBinding binding) {}

    private record MenuState(ItemStack fixed, ItemStack dynamic, ItemStack player, ItemStack carried) {

        private static MenuState capture(MenuFixture fixture) {
            return new MenuState(
                    fixture.machine().fixedHandler.getStackInSlot(0).copy(),
                    fixture.machine().dynamicHandler.getStackInSlot(0).copy(),
                    fixture.player().getInventory().getItem(0).copy(),
                    fixture.menu().getCarried().copy());
        }

        private boolean matches(MenuFixture fixture) {
            return ItemStack.matches(fixed, fixture.machine().fixedHandler.getStackInSlot(0)) &&
                    ItemStack.matches(dynamic, fixture.machine().dynamicHandler.getStackInSlot(0)) &&
                    ItemStack.matches(player, fixture.player().getInventory().getItem(0)) &&
                    ItemStack.matches(carried, fixture.menu().getCarried());
        }
    }

    private static final class TestDynamicSlotMachine extends MetaMachine
                                                      implements LDLib2DynamicItemSlotMachineUIProvider {

        private final CustomItemStackHandler fixedHandler = new CustomItemStackHandler(1);
        private final CustomItemStackHandler dynamicHandler = new CustomItemStackHandler(1);
        private GTItemSlotElement fixedElement;
        private GTItemSlotElement playerElement;
        private GTDynamicItemSlotSessionElement sessionElement;
        private final List<DynamicItemSlotBinding> selectedBindings = new ArrayList<>(1);
        private UIElement root;

        private TestDynamicSlotMachine(BlockEntityCreationInfo info) {
            super(info);
            fixedHandler.setNonMutatingEmptySlotCapacityQueryEnabled(true);
            dynamicHandler.setNonMutatingEmptySlotCapacityQueryEnabled(true);
        }

        @Override
        public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
            return holder.getMachine() == this;
        }

        @Override
        public UI createLDLib2UI(Player player, MachineUIHolder holder) {
            if (!canCreateLDLib2UI(player, holder)) {
                throw new IllegalArgumentException("Dynamic-slot test UI requires its installed machine holder.");
            }
            root = new UIElement();
            fixedElement = new GTItemSlotElement(fixedHandler, 0);
            playerElement = new GTItemSlotElement().bind(new Slot(player.getInventory(), 0, 0, 0));
            sessionElement = GTDynamicItemSlotSessionElement.builder()
                    .sourceRevision(() -> 0)
                    .definitionSource(() -> List.of(
                            new DynamicItemSlotDefinition(TARGET_ID, TARGET_INCARNATION, 1)))
                    .bindingAppender(this::appendBinding)
                    .bindingResolved(binding -> true)
                    .bindingSelectable(binding -> true)
                    .selectionListener(selection -> {
                        selectedBindings.clear();
                        selection.ifPresent(selectedBindings::add);
                    })
                    .build();
            root.addChildren(fixedElement, playerElement, sessionElement);
            return UI.of(root);
        }

        private List<GTDynamicItemSlotElement> appendBinding(DynamicItemSlotBinding binding) {
            DynamicItemHandlerRoute route = new DynamicItemHandlerRoute(
                    binding.targetId(), binding.targetIncarnation(), binding.slotCount(),
                    (targetId, targetIncarnation) -> TARGET_ID.equals(targetId) &&
                            TARGET_INCARNATION.equals(targetIncarnation) ? dynamicHandler : null);
            List<GTDynamicItemSlotElement> elements = new ArrayList<>(binding.slotCount());
            for (int slotIndex = 0; slotIndex < binding.slotCount(); slotIndex++) {
                GTDynamicItemSlotElement element = new GTDynamicItemSlotElement(route, slotIndex);
                root.addChild(element);
                elements.add(element);
            }
            return List.copyOf(elements);
        }
    }
}
