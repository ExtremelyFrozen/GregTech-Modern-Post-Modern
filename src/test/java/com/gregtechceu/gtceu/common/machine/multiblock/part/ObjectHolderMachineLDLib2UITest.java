package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.ItemStackTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ObjectHolderMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ObjectHolderMachineLDLib2UI")
    public static void standaloneAndContextualPagesPreserveLayoutSlotsAndLockState(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ObjectHolderMachine machine = createMachine();
        MachineUIHolder holder = new MutableMachineUIHolder(machine);

        UI standalone = machine.createLDLib2UI(player, holder);
        UIElement standaloneRoot = standalone.getRootElement();
        helper.assertTrue(
                UITemplate.getLDLib2Bounds(standaloneRoot).width() == 176 &&
                        UITemplate.getLDLib2Bounds(standaloneRoot).height() == 166,
                "standalone Object Holder UI lost its 176x166 bounds");
        helper.assertTrue(standaloneRoot.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.BACKGROUND,
                "standalone Object Holder UI lost its background");

        List<GTLabelElement> titleLabels = standaloneRoot.getChildren().stream()
                .filter(GTLabelElement.class::isInstance)
                .map(GTLabelElement.class::cast)
                .toList();
        helper.assertTrue(titleLabels.size() == 1 && UITemplate.getLDLib2Bounds(titleLabels.getFirst()).x() == 10 &&
                UITemplate.getLDLib2Bounds(titleLabels.getFirst()).y() == 5,
                "standalone Object Holder UI lost its definition title");

        List<UIElement> inventoryRoots = standaloneRoot.getChildren().stream()
                .filter(child -> child.getChildren().size() == 36)
                .filter(child -> child.getChildren().stream().allMatch(GTItemSlotElement.class::isInstance))
                .toList();
        helper.assertTrue(inventoryRoots.size() == 1 &&
                UITemplate.getLDLib2Bounds(inventoryRoots.getFirst()).x() == 7 &&
                UITemplate.getLDLib2Bounds(inventoryRoots.getFirst()).y() == 84,
                "standalone Object Holder UI lost its 36-slot player inventory at y=84");
        assertObjectHolderBody(helper, player, machine, standaloneRoot);

        machine.setLocked(false);
        LDLib2FancyUIProvider contextualPage = machine.createLDLib2FancyPage(player, holder);
        LDLib2FancyMachineUIElement shell = createShell(player, holder, contextualPage);
        UIElement contextualRoot = shell.getChildren().getFirst().getChildren().getFirst();
        helper.assertTrue(contextualPage.getLDLib2PageWidth() == 176 &&
                contextualPage.getLDLib2PageHeight() == 84 &&
                UITemplate.getLDLib2Bounds(contextualRoot).width() == 176 &&
                UITemplate.getLDLib2Bounds(contextualRoot).height() == 84,
                "contextual Object Holder page lost its stable 176x84 body");
        helper.assertTrue(contextualRoot.getChildren().stream().noneMatch(GTLabelElement.class::isInstance),
                "contextual Object Holder body repeated the standalone title");
        helper.assertTrue(descendants(contextualRoot).stream()
                .filter(GTItemSlotElement.class::isInstance)
                .count() == 2,
                "contextual Object Holder body repeated the player inventory");
        helper.assertTrue(descendants(shell).stream()
                .filter(child -> child.getChildren().size() == 36)
                .filter(child -> child.getChildren().stream().allMatch(GTItemSlotElement.class::isInstance))
                .count() == 1,
                "contextual Object Holder shell did not contain exactly one player inventory");
        assertObjectHolderBody(helper, player, machine, contextualRoot);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ObjectHolderMachineLDLib2UI")
    public static void contextualPageLifecycleMetadataAndPanelsAreHolderScoped(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ObjectHolderMachine machine = createMachine();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        LDLib2FancyUIProvider firstPage = machine.createLDLib2FancyPage(player, holder);
        LDLib2FancyUIProvider secondPage = machine.createLDLib2FancyPage(player, holder);

        helper.assertTrue(firstPage != secondPage,
                "Object Holder reused a contextual page provider across openings");
        Component expectedTitle = Component.translatable(machine.getDefinition().getDescriptionId());
        helper.assertTrue(firstPage.getTitle().equals(expectedTitle) &&
                firstPage.getTabTooltips().equals(List.of(expectedTitle)),
                "Object Holder contextual page did not use its definition title");
        helper.assertTrue(firstPage.getTabIcon() instanceof ItemStackTexture icon &&
                icon.items.length == 1 && icon.items[0].is(machine.getDefinition().getItem()),
                "Object Holder contextual page did not use its definition icon");
        helper.assertTrue(firstPage.getPageGroupingData() == null,
                "Object Holder contextual page invented grouping metadata");

        LDLib2FancyMachineUIElement shell = createShell(player, holder, firstPage);
        helper.assertTrue(shell.getHolder() == holder,
                "Object Holder contextual shell lost its dedicated holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().isEmpty(),
                "Object Holder exposed an unsupported working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Object Holder did not expose exactly one contextual directional side page");
        int expectedTooltips = machine.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) machine.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                "Object Holder did not attach its default machine and trait tooltips");

        ObjectHolderMachine replacement = createMachine();
        boolean mismatchedHolderRejected = false;
        try {
            machine.createLDLib2FancyPage(player, new MutableMachineUIHolder(replacement));
        } catch (IllegalArgumentException expected) {
            mismatchedHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedHolderRejected,
                "Object Holder contextual page accepted another machine's holder");

        LDLib2FancyUIProvider stalePage = machine.createLDLib2FancyPage(player, holder);
        holder.setMachine(replacement);
        boolean staleHolderRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            staleHolderRejected = expected.getMessage().contains("no longer");
        }
        helper.assertTrue(staleHolderRejected,
                "Object Holder contextual page accepted a same-definition holder replacement");
        helper.succeed();
    }

    private static void assertObjectHolderBody(GameTestHelper helper, ServerPlayer player,
                                               ObjectHolderMachine machine, UIElement root) {
        machine.setLocked(false);
        ItemStack heldItem = new ItemStack(Items.STONE);
        ItemStack dataItem = GTItems.TOOL_DATA_STICK.asStack();
        machine.setHeldItem(heldItem);
        machine.setDataItem(dataItem);

        List<GTItemSlotElement> slots = root.getChildren().stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .toList();
        helper.assertTrue(slots.size() == 2,
                "Object Holder body did not expose exactly two business item slots");
        GTItemSlotElement dataSlot = slotAt(slots, 15, 36);
        GTItemSlotElement inputSlot = slotAt(slots, 79, 36);
        ItemHandlerSlot dataHandlerSlot = requireHandlerSlot(dataSlot);
        ItemHandlerSlot inputHandlerSlot = requireHandlerSlot(inputSlot);
        helper.assertTrue(dataHandlerSlot.getItemHandler() != inputHandlerSlot.getItemHandler() &&
                dataHandlerSlot.getSlotIndex() == 0 && inputHandlerSlot.getSlotIndex() == 0,
                "Object Holder business slots did not bind distinct real slot-zero handlers");
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                dataHandlerSlot.getItemHandler().getStackInSlot(0), machine.getDataItem(false)) &&
                ItemStack.isSameItemSameComponents(
                        inputHandlerSlot.getItemHandler().getStackInSlot(0), machine.getHeldItem(false)),
                "Object Holder business slots did not bind the machine's real inventories");
        helper.assertTrue(inputHandlerSlot.getItemHandler().isItemValid(0, heldItem) &&
                !inputHandlerSlot.getItemHandler().isItemValid(0, dataItem) &&
                !dataHandlerSlot.getItemHandler().isItemValid(0, heldItem) &&
                dataHandlerSlot.getItemHandler().isItemValid(0, dataItem),
                "Object Holder business slots did not preserve their opposite item filters");
        for (GTItemSlotElement slot : slots) {
            ItemHandlerSlot handlerSlot = requireHandlerSlot(slot);
            helper.assertTrue(handlerSlot.getCanPlace().test(new ItemStack(Items.STONE)) &&
                    handlerSlot.getCanTake().test(player),
                    "unlocked Object Holder slot rejected insertion or extraction");
            helper.assertTrue(slot.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.SLOT,
                    "Object Holder business slot lost its GT slot background");
        }

        List<GTImageElement> images = root.getChildren().stream()
                .filter(GTImageElement.class::isInstance)
                .map(GTImageElement.class::cast)
                .toList();
        helper.assertTrue(
                images.size() == 3 && images.stream()
                        .anyMatch(image -> UITemplate.getLDLib2Bounds(image).x() == 46 &&
                                UITemplate.getLDLib2Bounds(image).y() == 15 &&
                                UITemplate.getLDLib2Bounds(image).width() == 84 &&
                                UITemplate.getLDLib2Bounds(image).height() == 60),
                "Object Holder body lost its research-station image");
        List<GTImageElement> overlays = images.stream()
                .filter(image -> UITemplate.getLDLib2Bounds(image).width() == 16 &&
                        UITemplate.getLDLib2Bounds(image).height() == 16)
                .toList();
        helper.assertTrue(overlays.size() == 2 &&
                overlays.stream()
                        .anyMatch(image -> UITemplate.getLDLib2Bounds(image).x() == 16 &&
                                UITemplate.getLDLib2Bounds(image).y() == 37) &&
                overlays.stream()
                        .anyMatch(image -> UITemplate.getLDLib2Bounds(image).x() == 80 &&
                                UITemplate.getLDLib2Bounds(image).y() == 37) &&
                overlays.stream().noneMatch(UIElement::isVisible),
                "unlocked Object Holder did not hide both locked overlays at their original coordinates");

        machine.setLocked(true);
        overlays.forEach(UIElement::screenTick);
        helper.assertTrue(overlays.stream().allMatch(UIElement::isVisible),
                "locked Object Holder did not show both locked overlays");
        for (GTItemSlotElement slot : slots) {
            ItemHandlerSlot handlerSlot = requireHandlerSlot(slot);
            helper.assertTrue(!handlerSlot.getCanPlace().test(new ItemStack(Items.STONE)) &&
                    !handlerSlot.getCanTake().test(player),
                    "locked Object Holder slot still allowed insertion or extraction");
        }

        machine.setLocked(false);
        overlays.forEach(UIElement::screenTick);
        helper.assertTrue(overlays.stream().noneMatch(UIElement::isVisible),
                "unlocked Object Holder retained its locked overlays");
    }

    private static GTItemSlotElement slotAt(List<GTItemSlotElement> slots, int x, int y) {
        return slots.stream()
                .filter(slot -> UITemplate.getLDLib2Bounds(slot).x() == x && UITemplate.getLDLib2Bounds(slot).y() == y)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Object Holder business slot moved."));
    }

    private static ItemHandlerSlot requireHandlerSlot(GTItemSlotElement slot) {
        if (slot.getSlot() instanceof ItemHandlerSlot handlerSlot) {
            return handlerSlot;
        }
        throw new IllegalStateException("Object Holder business slot was not bound through an item handler.");
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
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

    private static ObjectHolderMachine createMachine() {
        MachineDefinition definition = GTResearchMachines.OBJECT_HOLDER;
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof ObjectHolderMachine objectHolder) {
            return objectHolder;
        }
        throw new IllegalStateException("Object Holder definition did not create its expected machine.");
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

        private void setMachine(MetaMachine machine) {
            this.machine = machine;
        }
    }
}
