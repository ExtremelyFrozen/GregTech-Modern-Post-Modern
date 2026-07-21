package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2ButtonConfigurator;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

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

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2FancyMachineUIElementTest {

    private static final ResourceLocation CACHED_SUB_TAB_KEY = GTCEu.id("cached_sub_tab_test");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyMachineUIElement")
    public static void shellCachesPagesAndRefreshesPanelsDuringLocalNavigation(GameTestHelper helper) {
        TestFancyPage subPage = new TestFancyPage("sub", 2, 2, List.of());
        TestFancyPage mainPage = new TestFancyPage("main", 1, 1, List.of(subPage));
        Inventory inventory = FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory();

        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(
                mainPage, inventory, new TestMachineUIHolder(), 176, 166);

        List<UIElement> shellChildren = shell.getChildren();
        helper.assertTrue(shellChildren.size() == 5,
                "shell should contain page container, title bar, side tabs, tooltips, and configurator");
        UIElement pageContainer = shellChildren.getFirst();
        helper.assertTrue(pageContainer.getChildren().size() == 2,
                "first shell child should be the page container cached with main and sub pages");

        UIElement mainElement = pageContainer.getChildren().getFirst();
        UIElement subElement = pageContainer.getChildren().get(1);
        assertPageState(helper, mainElement, true, true, "main page initial state");
        assertPageState(helper, subElement, false, false, "sub page initial state");
        assertShellChromeCounts(helper, shell, 1, 1, "initial shell chrome");
        assertPagesCreatedOnce(helper, mainPage, subPage, "initial page creation");

        shell.navigate(subPage);

        helper.assertTrue(pageContainer.getChildren().size() == 2,
                "local navigation should keep the cached main and sub pages");
        assertPageState(helper, mainElement, false, false, "main page after sub navigation");
        assertPageState(helper, subElement, true, true, "sub page after sub navigation");
        assertShellChromeCounts(helper, shell, 2, 2, "sub page shell chrome");
        assertPagesCreatedOnce(helper, mainPage, subPage, "sub navigation should reuse cached pages");

        shell.navigateBack();

        helper.assertTrue(pageContainer.getChildren().size() == 2,
                "back navigation should keep the cached main and sub pages");
        assertPageState(helper, mainElement, true, true, "main page after back navigation");
        assertPageState(helper, subElement, false, false, "sub page after back navigation");
        assertShellChromeCounts(helper, shell, 1, 1, "main page shell chrome after back navigation");
        assertPagesCreatedOnce(helper, mainPage, subPage, "back navigation should reuse cached pages");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyMachineUIElement")
    public static void shellReusesFactorySubTabsPerHomePage(GameTestHelper helper) {
        CachedFactoryFancyPage secondHome = new CachedFactoryFancyPage("second", List.of());
        CachedFactoryFancyPage mainPage = new CachedFactoryFancyPage("main", List.of(secondHome));
        Inventory inventory = FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory();
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(
                mainPage, inventory, new TestMachineUIHolder(), 176, 166);
        UIElement pageContainer = shell.getChildren().getFirst();

        LDLib2FancyUIProvider firstMainSubTab = mainPage.cachedSubTab();
        helper.assertTrue(mainPage.factoryCalls() == 1, "main home sub-tab factory should run once during setup");
        shell.navigate(firstMainSubTab);
        helper.assertTrue(pageContainer.getChildren().size() == 3,
                "first main sub-tab navigation should add one cached page");
        shell.navigateBack();

        shell.switchPage(secondHome);
        LDLib2FancyUIProvider firstSecondSubTab = secondHome.cachedSubTab();
        helper.assertTrue(secondHome.factoryCalls() == 1, "second home sub-tab factory should run once during setup");
        helper.assertTrue(firstSecondSubTab != firstMainSubTab,
                "the same cache key must not share providers between home pages");
        shell.navigate(firstSecondSubTab);
        helper.assertTrue(pageContainer.getChildren().size() == 4,
                "second home sub-tab navigation should add its own cached page");
        shell.navigateBack();

        shell.switchPage(mainPage);
        LDLib2FancyUIProvider secondMainSubTab = mainPage.cachedSubTab();
        helper.assertTrue(secondMainSubTab == firstMainSubTab,
                "returning to a home page should reuse its cached sub-tab provider");
        helper.assertTrue(mainPage.factoryCalls() == 1,
                "returning to a home page must not invoke its sub-tab factory again");
        shell.navigate(secondMainSubTab);
        helper.assertTrue(pageContainer.getChildren().size() == 4,
                "revisiting a cached sub-tab must not grow the shell page cache");
        helper.succeed();
    }

    private static void assertPageState(GameTestHelper helper, UIElement page, boolean visible, boolean active,
                                        String name) {
        helper.assertTrue(page.isVisible() == visible, name + " visibility should match expected state");
        helper.assertTrue(page.isActive() == active, name + " active state should match expected state");
    }

    private static void assertShellChromeCounts(GameTestHelper helper, LDLib2FancyMachineUIElement shell,
                                                int configuratorCount, int tooltipCount, String name) {
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                name + " side tabs should contain main and sub tab buttons");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == configuratorCount,
                name + " configurators should match expected count");
        helper.assertTrue(shell.getTooltipsPanel().getChildren().size() == tooltipCount,
                name + " tooltips should match expected count");
    }

    private static void assertPagesCreatedOnce(GameTestHelper helper, TestFancyPage mainPage, TestFancyPage subPage,
                                               String name) {
        helper.assertTrue(mainPage.createCalls() == 1, name + " main create calls should remain one");
        helper.assertTrue(subPage.createCalls() == 1, name + " sub create calls should remain one");
    }

    private static LDLib2ButtonConfigurator buttonConfigurator() {
        return new LDLib2ButtonConfigurator(IGuiTexture.EMPTY, event -> event.hasHandler = true);
    }

    private static IFancyTooltip.Basic tooltip(String name) {
        return new IFancyTooltip.Basic(
                () -> IGuiTexture.EMPTY,
                () -> List.of(Component.literal(name)),
                () -> true,
                () -> null);
    }

    private static class TestFancyPage implements LDLib2FancyUIProvider {

        private final String title;
        private final int configuratorCount;
        private final int tooltipCount;
        private final List<LDLib2FancyUIProvider> subTabs;
        private final AtomicInteger createCalls = new AtomicInteger();

        private TestFancyPage(String title, int configuratorCount, int tooltipCount,
                              List<LDLib2FancyUIProvider> subTabs) {
            this.title = title;
            this.configuratorCount = configuratorCount;
            this.tooltipCount = tooltipCount;
            this.subTabs = subTabs;
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            createCalls.incrementAndGet();
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
        public void attachSideTabs(LDLib2FancyTabsElement tabs) {
            for (LDLib2FancyUIProvider subTab : subTabs) {
                tabs.attachSubTab(subTab);
            }
        }

        @Override
        public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
            for (int index = 0; index < configuratorCount; index++) {
                configuratorPanel.attachConfigurators(buttonConfigurator());
            }
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            for (int index = 0; index < tooltipCount; index++) {
                tooltipsPanel.attachTooltips(tooltip(title + " tooltip " + index));
            }
        }

        @Override
        public boolean hasPlayerInventory() {
            return false;
        }

        @Override
        public List<LDLib2FancyUIProvider> getSubTabs() {
            return subTabs;
        }

        private int createCalls() {
            return createCalls.get();
        }
    }

    private static final class CachedFactoryFancyPage extends TestFancyPage {

        private final String cachedSubTabTitle;
        private final AtomicInteger factoryCalls = new AtomicInteger();
        @Nullable
        private LDLib2FancyUIProvider cachedSubTab;

        private CachedFactoryFancyPage(String title, List<LDLib2FancyUIProvider> homePages) {
            super(title, 0, 0, homePages);
            this.cachedSubTabTitle = title + " cached sub-tab";
        }

        @Override
        public void attachSideTabs(LDLib2FancyTabsElement tabs) {
            cachedSubTab = tabs.attachCachedSubTab(CACHED_SUB_TAB_KEY, () -> {
                factoryCalls.incrementAndGet();
                return new TestFancyPage(cachedSubTabTitle, 0, 0, List.of());
            });
        }

        private LDLib2FancyUIProvider cachedSubTab() {
            if (cachedSubTab == null) {
                throw new IllegalStateException("Cached sub-tab has not been attached.");
            }
            return cachedSubTab;
        }

        private int factoryCalls() {
            return factoryCalls.get();
        }
    }

    private record TestMachineUIHolder() implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return GTCEu.id("test_machine");
        }

        @Override
        public @Nullable MetaMachine getMachine() {
            return null;
        }
    }
}
