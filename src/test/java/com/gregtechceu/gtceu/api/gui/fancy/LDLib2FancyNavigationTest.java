package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider.PageGroupingData;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.Nullable;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2FancyNavigationTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyNavigation")
    public static void pageSwitcherRebuildsVisibleNavigationChildren(GameTestHelper helper) {
        LDLib2FancyPageSwitcher pageSwitcher = new LDLib2FancyPageSwitcher(page -> {});
        TestLDLib2FancyUIProvider firstPage = page("first");

        pageSwitcher.setPageList(List.of(firstPage, page("second"), page("third")), firstPage);

        UIElement root = pageSwitcher.createLDLib2MainPage(null);
        helper.assertTrue(root.getChildren().size() == 1, "page switcher root should contain one scroll child");
        UIElement scrollChild = root.getChildren().getFirst();
        helper.assertTrue(scrollChild.getChildren().size() == 3,
                "page switcher should create one child per ungrouped page");

        pageSwitcher.setPageList(List.of(page("replacement first"), page("replacement second")), firstPage);

        helper.assertTrue(root.getChildren().size() == 1,
                "page switcher root should keep one scroll child after rebuild");
        helper.assertTrue(scrollChild.getChildren().size() == 2,
                "page switcher should replace old page children when the page list changes");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyNavigation")
    public static void pageSwitcherAddsObservableGroupLabels(GameTestHelper helper) {
        LDLib2FancyPageSwitcher pageSwitcher = new LDLib2FancyPageSwitcher(page -> {});
        TestLDLib2FancyUIProvider firstPage = groupedPage("first", "group.alpha", 0);

        pageSwitcher.setPageList(List.of(
                firstPage,
                groupedPage("second", "group.alpha", 0),
                groupedPage("third", "group.beta", 1)), firstPage);

        UIElement root = pageSwitcher.createLDLib2MainPage(null);
        UIElement scrollChild = root.getChildren().getFirst();

        helper.assertTrue(root.getChildren().size() == 1, "grouped page switcher root should contain one scroll child");
        helper.assertTrue(scrollChild.getChildren().size() == 5,
                "two page groups should add two label children plus three page button children");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyNavigation")
    public static void tabsRebuildWithoutAccumulatingChildren(GameTestHelper helper) {
        LDLib2FancyTabsElement tabs = new LDLib2FancyTabsElement(tab -> {}, 0, 0, 100, 24, false);
        TestLDLib2FancyUIProvider mainTab = page("main");
        TestLDLib2FancyUIProvider firstSubTab = page("sub first");
        TestLDLib2FancyUIProvider secondSubTab = page("sub second");

        tabs.setMainTab(mainTab);
        helper.assertTrue(tabs.getChildren().size() == 1, "tabs should contain only the main tab after setMainTab");

        tabs.attachSubTab(firstSubTab);
        tabs.attachSubTab(secondSubTab);
        helper.assertTrue(tabs.getChildren().size() == 3, "tabs should contain main tab and two sub tabs");

        tabs.selectTab(secondSubTab);
        helper.assertTrue(tabs.getChildren().size() == 3, "selectTab should rebuild without accumulating children");

        tabs.setMainTab(mainTab);
        helper.assertTrue(tabs.getChildren().size() == 3,
                "repeated setMainTab should rebuild without accumulating children");

        tabs.clearSubTabs();
        helper.assertTrue(tabs.getChildren().size() == 1, "clearSubTabs should leave only the main tab");
        helper.succeed();
    }

    private static TestLDLib2FancyUIProvider page(String title) {
        return new TestLDLib2FancyUIProvider(title, null);
    }

    private static TestLDLib2FancyUIProvider groupedPage(String title, String groupKey, int groupPositionWeight) {
        return new TestLDLib2FancyUIProvider(title, new PageGroupingData(groupKey, groupPositionWeight));
    }

    private record TestLDLib2FancyUIProvider(String title, @Nullable PageGroupingData groupingData)
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
        public @Nullable PageGroupingData getPageGroupingData() {
            return groupingData;
        }
    }
}
