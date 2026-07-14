package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.integration.ae2.gui.element.AEPatternViewSlotElement;
import com.gregtechceu.gtceu.integration.ae2.gui.element.MEPatternBufferNameEditorElement;
import com.gregtechceu.gtceu.integration.ae2.gui.element.MEPatternBufferPageElement;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.core.BlockPos;
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

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.EncodedPatternItem;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEPatternBufferLDLib2UITest {

    private static final String BATCH = "MEPatternBufferLDLib2UI";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void matchingDefinitionWrongAndStaleHoldersAreEnforcedPerOpening(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEPatternBufferPartMachine buffer = createBuffer();
        MEPatternBufferPartMachine replacement = createBuffer();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(buffer);

        helper.assertTrue(buffer.canCreateLDLib2UI(player, holder),
                "ME Pattern Buffer rejected its matching holder");
        LDLib2FancyUIProvider first = buffer.createLDLib2Page(player, holder);
        LDLib2FancyUIProvider second = buffer.createLDLib2Page(player, holder);
        helper.assertTrue(first != second, "ME Pattern Buffer reused a page provider across openings");

        boolean wrongHolderRejected = false;
        try {
            buffer.createLDLib2Page(player, new MutableMachineUIHolder(replacement));
        } catch (IllegalArgumentException expected) {
            wrongHolderRejected = true;
        }
        helper.assertTrue(wrongHolderRejected, "ME Pattern Buffer accepted another machine's holder");

        LDLib2FancyMachineUIElement firstShell = new LDLib2FancyMachineUIElement(first, player.getInventory(),
                holder, first.getLDLib2PageWidth(), first.getLDLib2PageHeight());
        holder.machine = replacement;
        boolean staleHolderRejected = false;
        try {
            first.createLDLib2MainPage(firstShell);
        } catch (IllegalStateException expected) {
            staleHolderRejected = true;
        }
        helper.assertTrue(staleHolderRejected, "opened ME Pattern Buffer page accepted a stale holder");

        MEPatternBufferPartMachine wrongDefinition = new MEPatternBufferPartMachine(
                info(GTAEMachines.ITEM_IMPORT_BUS_ME));
        helper.assertTrue(!wrongDefinition.canCreateLDLib2UI(player, new MutableMachineUIHolder(wrongDefinition)),
                "ME Pattern Buffer page accepted a non-pattern-buffer machine definition");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void mainPagePreservesGridFilterPreviewStatusAndEditorGeometry(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEPatternBufferPartMachine buffer = createBuffer();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(buffer);
        MEPatternBufferPageElement page = buffer.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> {}, () -> true);

        helper.assertTrue(buffer.getLDLib2PageWidth() == 178 && buffer.getLDLib2PageHeight() == 70 &&
                page.getSizeWidth() == 178 && page.getSizeHeight() == 70,
                "ME Pattern Buffer did not preserve its 178x70 legacy body");

        List<AEPatternViewSlotElement> slots = page.getPatternSlots();
        helper.assertTrue(slots.size() == 27, "ME Pattern Buffer did not expose twenty-seven pattern slots");
        ItemStack encodedPattern = processingPattern();
        helper.assertTrue(encodedPattern.getItem() instanceof EncodedPatternItem<?>,
                "processing pattern fixture was not an encoded AE2 pattern");
        helper.assertTrue(buffer.getPatternInventory().isItemValid(0, encodedPattern) &&
                !buffer.getPatternInventory().isItemValid(0, new ItemStack(Items.STONE)),
                "ME Pattern Buffer handler did not retain its encoded-pattern-only filter");

        for (int index = 0; index < slots.size(); index++) {
            AEPatternViewSlotElement slot = slots.get(index);
            helper.assertTrue(slot.getSlot() instanceof ItemHandlerSlot,
                    "pattern slot " + index + " was not bound through an item-handler slot");
            ItemHandlerSlot handlerSlot = (ItemHandlerSlot) slot.getSlot();
            helper.assertTrue(handlerSlot.getItemHandler() == buffer.getPatternInventory() &&
                    handlerSlot.getSlotIndex() == index,
                    "pattern slot " + index + " was bound to the wrong handler index");
            helper.assertTrue(slot.getPositionX() == 8 + index % 9 * 18 &&
                    slot.getPositionY() == 14 + index / 9 * 18 &&
                    slot.getSizeWidth() == 18 && slot.getSizeHeight() == 18,
                    "pattern slot " + index + " did not preserve its fixed grid coordinates");
            helper.assertTrue(handlerSlot.getCanPlace().test(encodedPattern) &&
                    !handlerSlot.getCanPlace().test(new ItemStack(Items.STONE)) &&
                    handlerSlot.getCanTake().test(player),
                    "pattern slot " + index + " did not preserve its insertion or extraction rules");
            helper.assertTrue(slot.getIngredientIO() == IngredientIO.NONE,
                    "pattern slot " + index + " unexpectedly exposed a recipe ingredient role");
            helper.assertTrue(slot.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.SLOT &&
                    slot.getSlotStyle().slotOverlay() == GuiTextures.PATTERN_OVERLAY &&
                    slot.getSlotStyle().showSlotOverlayOnlyEmpty(),
                    "pattern slot " + index + " did not preserve occupied and empty slot textures");
        }

        buffer.getPatternInventory().setStackInSlot(0, encodedPattern);
        ItemStack preview = slots.getFirst().getDisplayStack();
        helper.assertTrue(
                preview.is(Items.STONE) && slots.getFirst().getValue().getItem() instanceof EncodedPatternItem<?>,
                "pattern slot did not render the primary output while retaining the encoded pattern value");
        ItemStack undecodablePattern = AEItems.PROCESSING_PATTERN.stack();
        buffer.getPatternInventory().setStackInSlot(0, undecodablePattern);
        helper.assertTrue(ItemStack.isSameItemSameComponents(slots.getFirst().getDisplayStack(), undecodablePattern),
                "pattern slot did not fall back to the encoded pattern when no output could be decoded");

        GTLabelElement networkStatus = page.getNetworkStatusLabel();
        helper.assertTrue(networkStatus.getPositionX() == 8 && networkStatus.getPositionY() == 2,
                "ME network status label moved from its legacy origin");
        buffer.setOnline(false);
        networkStatus.screenTick();
        helper.assertTrue(networkStatus.getValue().equals(Component.translatable("gtpm.gui.me_network.offline")),
                "ME network status label did not read the offline machine state");
        buffer.setOnline(true);
        networkStatus.screenTick();
        helper.assertTrue(networkStatus.getValue().equals(Component.translatable("gtpm.gui.me_network.online")),
                "ME network status label did not read the online machine state");

        MEPatternBufferNameEditorElement editor = page.getNameEditor();
        helper.assertTrue(editor.getPositionX() == 100 && editor.getPositionY() == 2 &&
                editor.getSizeWidth() == 70 && editor.getSizeHeight() == 10,
                "ME Pattern Buffer name editor did not preserve its 70x10 top-right bounds");
        helper.assertTrue(editor.getTextField().getPositionX() == 0 &&
                editor.getTextField().getPositionY() == 0 && editor.getTextField().getSizeWidth() == 58 &&
                editor.getTextField().getSizeHeight() == 10 &&
                editor.getToggleButton().getPositionX() == 60 && editor.getToggleButton().getPositionY() == 0 &&
                editor.getToggleButton().getSizeWidth() == 10 && editor.getToggleButton().getSizeHeight() == 10,
                "ME Pattern Buffer name editor did not preserve its text and toggle geometry");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void nameDraftIsOpeningScopedAndConfirmSendsExactlyOneAction(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEPatternBufferPartMachine buffer = createBuffer();
        buffer.setCustomName("Initial");
        MutableMachineUIHolder holder = new MutableMachineUIHolder(buffer);
        List<CapturedAction> firstActions = new ArrayList<>();
        List<CapturedAction> secondActions = new ArrayList<>();
        BooleanSupplier holderValid = () -> holder.getMachine() == buffer;

        MEPatternBufferPageElement first = buffer.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> firstActions.add(new CapturedAction(sentHolder, action)), holderValid);
        MEPatternBufferPageElement second = buffer.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> secondActions.add(new CapturedAction(sentHolder, action)), holderValid);
        MEPatternBufferNameEditorElement firstEditor = first.getNameEditor();
        MEPatternBufferNameEditorElement secondEditor = second.getNameEditor();

        helper.assertTrue(firstEditor != secondEditor && firstEditor.getTextField() != secondEditor.getTextField(),
                "ME Pattern Buffer reused name-editor state across openings");
        helper.assertTrue(!firstEditor.isEditing() && !firstEditor.getTextField().isVisible() &&
                !firstEditor.getTextField().isActive() &&
                "Initial".equals(firstEditor.getTextField().getText()) &&
                "Initial".equals(secondEditor.getTextField().getText()),
                "ME Pattern Buffer name editors did not start from the synchronized machine name");

        click(firstEditor.getToggleButton(), false);
        helper.assertTrue(firstEditor.isEditing() && firstEditor.getTextField().isVisible() &&
                firstEditor.getTextField().isActive() && firstActions.isEmpty(),
                "entering name-edit mode sent an action or failed to reveal the draft field");
        firstEditor.getTextField().setText("First opening", true);
        helper.assertTrue(firstActions.isEmpty() && "First opening".equals(firstEditor.getTextField().getText()),
                "editing the local name draft sent a business action");
        helper.assertTrue(!secondEditor.isEditing() && "Initial".equals(secondEditor.getTextField().getText()) &&
                secondActions.isEmpty(),
                "a name draft leaked into another opening");

        click(firstEditor.getToggleButton(), false);
        assertOneAction(helper, firstActions, holder,
                MEPatternBufferActions.createSetNameAction("First opening"), "name confirmation");
        helper.assertTrue(!firstEditor.isEditing() && !firstEditor.getTextField().isVisible() &&
                !firstEditor.getTextField().isActive(),
                "name confirmation did not leave edit mode");

        click(firstEditor.getToggleButton(), false);
        firstEditor.getTextField().setText("Stale opening", true);
        holder.machine = createBuffer();
        click(firstEditor.getToggleButton(), false);
        helper.assertTrue(firstActions.size() == 1,
                "stale ME Pattern Buffer name editor sent an additional action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void refundAndAllSharedTankClicksUseOpenedHolderActions(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEPatternBufferPartMachine buffer = createBuffer();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(buffer);
        List<CapturedAction> actions = new ArrayList<>();
        LDLib2FancyMachineUIElement shell = createShell(player, buffer, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> holder.getMachine() == buffer,
                (UIEvent event) -> Boolean.TRUE.equals(event.customData));

        UIElement refundButton = shell.getConfiguratorPanel().getChildren().getFirst().getChildren().getFirst();
        click(refundButton, false);
        assertOneAction(helper, actions, holder, MEPatternBufferActions.createRefundAllAction(), "refund-all click");

        List<GTFluidSlotElement> tankSlots = descendants(shell.getConfiguratorPanel()).stream()
                .filter(GTFluidSlotElement.class::isInstance)
                .map(GTFluidSlotElement.class::cast)
                .toList();
        helper.assertTrue(tankSlots.size() == 9,
                "ME Pattern Buffer shared-tank configurator did not expose nine tank slots");
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        for (int tankIndex = 0; tankIndex < tankSlots.size(); tankIndex++) {
            boolean shiftDown = tankIndex == tankSlots.size() - 1;
            actions.clear();
            click(tankSlots.get(tankIndex), shiftDown);
            assertOneAction(helper, actions, holder,
                    MEPatternBufferActions.createClickShareTankAction(tankIndex, shiftDown),
                    "shared tank " + tankIndex + " click");
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fancyShellPreservesConfiguratorsDirectionPageAndImportGrouping(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEPatternBufferPartMachine buffer = createBuffer();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(buffer);
        LDLib2FancyUIProvider page = buffer.createLDLib2Page(player, holder);
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());

        helper.assertTrue(shell.getHolder() == holder,
                "ME Pattern Buffer Fancy shell did not retain its opening holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 4,
                "ME Pattern Buffer did not expose refund, circuit, shared-item, and shared-tank configurators");
        List<GTItemSlotElement> sharedInventorySlots = descendants(shell.getConfiguratorPanel()).stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .filter(slot -> slot.getSlot() instanceof ItemHandlerSlot handlerSlot &&
                        handlerSlot.getItemHandler() == buffer.getShareInventory().storage)
                .toList();
        helper.assertTrue(sharedInventorySlots.size() == 9,
                "ME Pattern Buffer shared-item configurator did not expose its nine slots");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "ME Pattern Buffer did not expose exactly one directional side page");

        LDLib2FancyUIProvider.PageGroupingData grouping = page.getPageGroupingData();
        helper.assertTrue(grouping != null &&
                "gtpm.multiblock.page_switcher.io.import".equals(grouping.groupKey()) &&
                grouping.groupPositionWeight() == 1,
                "ME Pattern Buffer did not retain import grouping metadata");

        UIElement pageContainer = shell.getChildren().getFirst();
        click(shell.getSideTabsElement().getChildren().get(1), false);
        helper.assertTrue(pageContainer.getChildren().size() == 2 &&
                pageContainer.getChildren().get(1).isVisible() && pageContainer.getChildren().get(1).isActive(),
                "ME Pattern Buffer directional tab did not navigate to its opening-scoped page");

        buffer.setCircuitSlotEnabled(false);
        LDLib2FancyMachineUIElement withoutCircuit = createShell(player, buffer, holder,
                (sentHolder, action) -> {}, () -> true,
                (UIEvent event) -> Boolean.TRUE.equals(event.customData));
        helper.assertTrue(withoutCircuit.getConfiguratorPanel().getChildren().size() == 3,
                "ME Pattern Buffer with a disabled circuit kept an empty circuit configurator");
        helper.succeed();
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MEPatternBufferPartMachine buffer,
                                                           MachineUIHolder holder,
                                                           BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                                           BooleanSupplier canSendAction,
                                                           Predicate<UIEvent> shiftDown) {
        LDLib2FancyUIProvider page = buffer.createLDLib2Page(player, holder, actionSender, canSendAction, shiftDown);
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
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
        CapturedAction captured = actions.getFirst();
        helper.assertTrue(captured.holder() == holder,
                description + " did not preserve the opened holder identity");
        SyncActionData actual = captured.action();
        helper.assertTrue(actual.actionId().equals(expected.actionId()) &&
                actual.sequence() == expected.sequence() && actual.payload().equals(expected.payload()),
                description + " did not send the expected action id, sequence, and payload");
    }

    private static ItemStack processingPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)));
    }

    private static ServerPlayer testPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static MEPatternBufferPartMachine createBuffer() {
        MetaMachine machine = GTAEMachines.ME_PATTERN_BUFFER.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.ME_PATTERN_BUFFER.defaultBlockState());
        if (!(machine instanceof MEPatternBufferPartMachine buffer)) {
            throw new IllegalStateException("ME Pattern Buffer definition created the wrong machine type");
        }
        return buffer;
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
