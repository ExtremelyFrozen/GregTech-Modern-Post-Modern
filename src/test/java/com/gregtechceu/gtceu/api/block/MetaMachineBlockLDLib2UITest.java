package com.gregtechceu.gtceu.api.block;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MetaMachineBlockLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MetaMachineBlockLDLib2UI")
    public static void machineUIRootIsCenteredWithoutChangingFixedLayout(GameTestHelper helper) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, 176, 166);
        UIElement child = UITemplate.setLDLib2Bounds(new UIElement(), 7, 11, 18, 18);
        root.addChild(child);
        UI ui = UI.of(root);

        float width = root.getStyleBag().computeCandidate(LayoutProperties.WIDTH).getValue();
        float height = root.getStyleBag().computeCandidate(LayoutProperties.HEIGHT).getValue();
        UITemplate.LDLib2Bounds childBounds = UITemplate.getLDLib2Bounds(child);

        MetaMachineBlock.centerMachineUIRoot(ui);

        helper.assertTrue(root.getStyleBag().computeCandidate(LayoutProperties.POSITION) == TaffyPosition.RELATIVE,
                "machine UI root was not changed to relative positioning for screen centering");
        helper.assertTrue(root.getStyleBag().computeCandidate(LayoutProperties.WIDTH).getValue() == width,
                "machine UI root width changed while enabling screen centering");
        helper.assertTrue(root.getStyleBag().computeCandidate(LayoutProperties.HEIGHT).getValue() == height,
                "machine UI root height changed while enabling screen centering");
        helper.assertTrue(UITemplate.getLDLib2Bounds(child).equals(childBounds),
                "machine UI child absolute bounds changed while enabling screen centering");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MetaMachineBlockLDLib2UI")
    public static void fancyMachineUIRootKeepsCenteringContractAcrossPageRebuilds(GameTestHelper helper) {
        TestFancyPage subPage = new TestFancyPage("sub", 200, 120, List.of());
        TestFancyPage mainPage = new TestFancyPage("main", 176, 166, List.of(subPage));
        Inventory inventory = FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory();
        TestFancyMachineUIElement root = new TestFancyMachineUIElement(
                mainPage, inventory, new TestMachineUIHolder(), 176, 166);
        UI ui = UI.of(root);

        MetaMachineBlock.centerMachineUIRoot(ui);
        float mainWidth = root.getStyleBag().computeCandidate(LayoutProperties.WIDTH).getValue();
        float mainHeight = root.getStyleBag().computeCandidate(LayoutProperties.HEIGHT).getValue();
        assertCenteringContract(helper, root, "main page");

        root.navigateTo(subPage);
        assertCenteringContract(helper, root, "sub-page");
        helper.assertTrue(root.getStyleBag().computeCandidate(LayoutProperties.WIDTH).getValue() > mainWidth &&
                root.getStyleBag().computeCandidate(LayoutProperties.HEIGHT).getValue() < mainHeight,
                "Fancy sub-page rebuild did not update the shell dimensions");

        root.navigateBackToPreviousPage();
        assertCenteringContract(helper, root, "restored main page");
        helper.assertTrue(root.getStyleBag().computeCandidate(LayoutProperties.WIDTH).getValue() == mainWidth &&
                root.getStyleBag().computeCandidate(LayoutProperties.HEIGHT).getValue() == mainHeight,
                "Fancy main-page dimensions were not restored after navigation");
        helper.succeed();
    }

    private static void assertCenteringContract(GameTestHelper helper, UIElement root, String state) {
        helper.assertTrue(root.getStyleBag().computeCandidate(LayoutProperties.POSITION) == TaffyPosition.RELATIVE,
                state + " should retain relative root positioning");
    }

    private static final class TestFancyMachineUIElement extends LDLib2FancyMachineUIElement {

        private TestFancyMachineUIElement(LDLib2FancyUIProvider mainPage, Inventory inventory, MachineUIHolder holder,
                                          int width, int height) {
            super(mainPage, inventory, holder, width, height);
        }

        private void navigateTo(LDLib2FancyUIProvider page) {
            navigate(page);
        }

        private void navigateBackToPreviousPage() {
            navigateBack();
        }
    }

    private record TestFancyPage(String title, int width, int height,
                                 List<LDLib2FancyUIProvider> subPages)
            implements LDLib2FancyUIProvider {

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            return new UIElement();
        }

        @Override
        public IGuiTexture getTabIcon() {
            return IGuiTexture.EMPTY;
        }

        @Override
        public Component getTitle() {
            return Component.literal(title);
        }

        @Override
        public int getLDLib2PageWidth() {
            return width;
        }

        @Override
        public int getLDLib2PageHeight() {
            return height;
        }

        @Override
        public boolean hasPlayerInventory() {
            return false;
        }

        @Override
        public List<LDLib2FancyUIProvider> getSubTabs() {
            return subPages;
        }
    }

    private record TestMachineUIHolder() implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return GTCEu.id("fancy_resize_test_machine");
        }

        @Override
        public @Nullable MetaMachine getMachine() {
            return null;
        }
    }
}
