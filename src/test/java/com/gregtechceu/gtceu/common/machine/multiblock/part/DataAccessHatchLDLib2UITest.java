package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.ItemStackTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
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
public class DataAccessHatchLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DataAccessHatchLDLib2UI")
    public static void contextualPagesPreserveInventoryTiersAndHolderIdentity(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        List<DataAccessHatchMachine> hatches = List.of(
                createHatch(GTResearchMachines.BASIC_DATA_ACCESS_HATCH),
                createHatch(GTResearchMachines.DATA_ACCESS_HATCH),
                createHatch(GTResearchMachines.ADVANCED_DATA_ACCESS_HATCH));
        List<Integer> slotCounts = List.of(4, 9, 16);

        for (int index = 0; index < hatches.size(); index++) {
            DataAccessHatchMachine hatch = hatches.get(index);
            MutableMachineUIHolder holder = new MutableMachineUIHolder(hatch);
            LDLib2FancyPartUIProvider contextualProvider = hatch;
            LDLib2FancyUIProvider page = contextualProvider.createLDLib2FancyPage(player, holder);

            helper.assertTrue(page != contextualProvider.createLDLib2FancyPage(player, holder),
                    "Data Access Hatch reused a contextual page across menu openings");
            assertContextualPage(helper, player, hatch, holder, page, slotCounts.get(index));
        }

        DataAccessHatchMachine basic = hatches.getFirst();
        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(hatches.get(1));
        boolean wrongHolderRejected = false;
        try {
            basic.createLDLib2FancyPage(player, wrongHolder);
        } catch (IllegalArgumentException expected) {
            wrongHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(wrongHolderRejected,
                "Data Access Hatch contextual page accepted another hatch holder");

        MutableMachineUIHolder staleHolder = new MutableMachineUIHolder(basic);
        LDLib2FancyUIProvider stalePage = basic.createLDLib2FancyPage(player, staleHolder);
        staleHolder.setMachine(createHatch(GTResearchMachines.BASIC_DATA_ACCESS_HATCH));
        boolean replacementRejected = false;
        try {
            new LDLib2FancyMachineUIElement(stalePage, player.getInventory(), staleHolder,
                    stalePage.getLDLib2PageWidth(), stalePage.getLDLib2PageHeight());
        } catch (IllegalStateException expected) {
            replacementRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(replacementRejected,
                "cached Data Access Hatch page accepted a same-definition replacement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DataAccessHatchLDLib2UI")
    public static void standaloneInventoryAndCreativeExclusionRemainExplicit(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        List<DataAccessHatchMachine> hatches = List.of(
                createHatch(GTResearchMachines.BASIC_DATA_ACCESS_HATCH),
                createHatch(GTResearchMachines.DATA_ACCESS_HATCH),
                createHatch(GTResearchMachines.ADVANCED_DATA_ACCESS_HATCH));
        List<Integer> slotCounts = List.of(4, 9, 16);

        for (int index = 0; index < hatches.size(); index++) {
            DataAccessHatchMachine hatch = hatches.get(index);
            MachineUIHolder holder = new MutableMachineUIHolder(hatch);
            helper.assertTrue(hatch.canCreateLDLib2UI(player, holder),
                    "ordinary Data Access Hatch rejected its standalone LDLib2 UI");
            assertStandalonePage(helper, player, hatch, holder, slotCounts.get(index));
        }
        helper.assertFalse(hatches.getFirst().canCreateLDLib2UI(
                player, new MutableMachineUIHolder(hatches.get(1))),
                "standalone Data Access Hatch UI accepted another machine's holder");

        DataAccessHatchMachine creative = createHatch(GTResearchMachines.CREATIVE_DATA_ACCESS_HATCH);
        MachineUIHolder creativeHolder = new MutableMachineUIHolder(creative);
        helper.assertFalse(creative.canCreateLDLib2UI(player, creativeHolder),
                "Creative Data Access Hatch exposed the standalone inventory UI");
        boolean creativeExcluded = false;
        try {
            creative.createLDLib2FancyPage(player, creativeHolder);
        } catch (IllegalStateException expected) {
            creativeExcluded = expected.getMessage().contains("excluded");
        }
        helper.assertTrue(creativeExcluded,
                "Creative Data Access Hatch created an empty contextual inventory page");
        helper.succeed();
    }

    private static void assertContextualPage(GameTestHelper helper, ServerPlayer player,
                                             DataAccessHatchMachine hatch, MachineUIHolder holder,
                                             LDLib2FancyUIProvider page, int expectedSlotCount) {
        int expectedSize = (int) Math.sqrt(expectedSlotCount) * 18;
        Component expectedTitle = Component.translatable(hatch.getDefinition().getDescriptionId());
        helper.assertTrue(page.getLDLib2PageWidth() == expectedSize &&
                page.getLDLib2PageHeight() == expectedSize,
                "Data Access Hatch contextual page did not match its inventory grid dimensions");
        helper.assertTrue(page.getPageGroupingData() == null,
                "Data Access Hatch contextual page invented grouping metadata");
        helper.assertTrue(page.getTitle().equals(expectedTitle) &&
                page.getTabTooltips().equals(List.of(expectedTitle)),
                "Data Access Hatch contextual page did not use its definition title");
        helper.assertTrue(page.getTabIcon() instanceof ItemStackTexture icon &&
                icon.items.length == 1 && icon.items[0].is(hatch.getDefinition().getItem()),
                "Data Access Hatch contextual page did not use its definition icon");

        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        helper.assertTrue(shell.getHolder() == holder,
                "Data Access Hatch Fancy shell lost its dedicated holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().isEmpty(),
                "Data Access Hatch exposed an unsupported working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Data Access Hatch did not expose exactly one contextual directional side page");

        int expectedTooltips = hatch.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) hatch.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                "Data Access Hatch did not attach its default machine and trait tooltips");

        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        helper.assertTrue(pageRoot.getSizeWidth() == expectedSize && pageRoot.getSizeHeight() == expectedSize,
                "Data Access Hatch contextual page root has incorrect bounds");
        List<GTItemSlotElement> slots = pageRoot.getChildren().stream()
                .map(GTItemSlotElement.class::cast)
                .toList();
        helper.assertTrue(slots.size() == expectedSlotCount,
                "Data Access Hatch contextual page exposed the wrong number of data slots");
        assertDataSlots(helper, player, hatch, slots);
    }

    private static void assertStandalonePage(GameTestHelper helper, ServerPlayer player,
                                             DataAccessHatchMachine hatch, MachineUIHolder holder,
                                             int expectedSlotCount) {
        UI ui = hatch.createLDLib2UI(player, holder);
        UIElement root = ui.getRootElement();
        List<GTItemSlotElement> slots = descendants(root).stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .toList();
        List<GTItemSlotElement> dataSlots = slots.stream()
                .filter(slot -> slot.getSlot() instanceof ItemHandlerSlot handlerSlot &&
                        handlerSlot.getItemHandler() == hatch.importItems)
                .toList();
        helper.assertTrue(dataSlots.size() == expectedSlotCount,
                "standalone Data Access Hatch UI exposed the wrong number of data slots");
        helper.assertTrue(slots.size() - dataSlots.size() == 36,
                "standalone Data Access Hatch UI did not preserve the full player inventory");
        assertDataSlots(helper, player, hatch, dataSlots);
    }

    private static void assertDataSlots(GameTestHelper helper, ServerPlayer player,
                                        DataAccessHatchMachine hatch, List<GTItemSlotElement> slots) {
        for (int index = 0; index < slots.size(); index++) {
            GTItemSlotElement slot = slots.get(index);
            helper.assertTrue(slot.getSlot() instanceof ItemHandlerSlot,
                    "Data Access Hatch data element was not bound through an item-handler slot");
            ItemHandlerSlot handlerSlot = (ItemHandlerSlot) slot.getSlot();
            helper.assertTrue(handlerSlot.getItemHandler() == hatch.importItems &&
                    handlerSlot.getSlotIndex() == index,
                    "Data Access Hatch data element was bound to the wrong real inventory slot");
            helper.assertTrue(handlerSlot.getCanPlace().test(new ItemStack(Items.PAPER)),
                    "Data Access Hatch data slot did not allow insertion");
            helper.assertTrue(handlerSlot.getCanTake().test(player),
                    "Data Access Hatch data slot did not allow extraction");
            helper.assertTrue(slot.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.SLOT,
                    "Data Access Hatch data slot did not retain the GT slot background");
        }
    }

    private static List<UIElement> descendants(UIElement root) {
        List<UIElement> descendants = new ArrayList<>();
        for (UIElement child : root.getChildren()) {
            descendants.add(child);
            descendants.addAll(descendants(child));
        }
        return descendants;
    }

    private static DataAccessHatchMachine createHatch(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof DataAccessHatchMachine hatch)) {
            throw new IllegalStateException("Data Access Hatch definition did not create its expected machine.");
        }
        return hatch;
    }

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
            this.machine = machine;
        }

        private void setMachine(MetaMachine machine) {
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
}
