package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.SelectableEnum;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2FancyConfiguratorButtonTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyConfiguratorButton")
    public static void buttonConfiguratorUsesIconTooltipsAndClickConsumer(GameTestHelper helper) {
        AtomicReference<UIEvent> clickedEvent = new AtomicReference<>();
        LDLib2ButtonConfigurator button = new LDLib2ButtonConfigurator(IGuiTexture.EMPTY, clickedEvent::set);

        helper.assertTrue(button.getIcon() == IGuiTexture.EMPTY, "button configurator should return the supplied icon");
        helper.assertTrue(button.getTooltips().isEmpty(), "button configurator should start with empty tooltips");

        List<Component> tooltips = List.of(Component.literal("configured tooltip"));
        LDLib2ButtonConfigurator returnedButton = button.setTooltips(tooltips);

        helper.assertTrue(returnedButton == button, "setTooltips should return the same button configurator");
        helper.assertTrue(button.getTooltips() == tooltips, "setTooltips should expose the supplied tooltip list");

        UIEvent clickEvent = UIEvent.create(UIEvents.CLICK);
        button.onClick(clickEvent);

        helper.assertTrue(clickedEvent.get() == clickEvent, "button click consumer should receive the same UIEvent");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyConfiguratorButton")
    public static void selectorConfiguratorCyclesValuesAndBuildsTooltips(GameTestHelper helper) {
        AtomicReference<TestSelection> currentValue = new AtomicReference<>(TestSelection.FIRST);
        AtomicReference<TestSelection> requestedValue = new AtomicReference<>();
        AtomicReference<UIEvent> changedEvent = new AtomicReference<>();
        LDLib2FancySelectorConfigurator<TestSelection> selector = new LDLib2FancySelectorConfigurator<>(
                new TestSelection[] { TestSelection.FIRST, TestSelection.SECOND },
                currentValue::get,
                (event, value) -> {
                    changedEvent.set(event);
                    requestedValue.set(value);
                });

        UIEvent firstClick = UIEvent.create(UIEvents.CLICK);
        selector.onClick(firstClick);
        helper.assertTrue(changedEvent.get() == firstClick, "selector callback should receive the first click event");
        helper.assertTrue(requestedValue.get() == TestSelection.SECOND,
                "selector should request SECOND after FIRST");

        currentValue.set(TestSelection.SECOND);
        UIEvent secondClick = UIEvent.create(UIEvents.CLICK);
        selector.onClick(secondClick);
        helper.assertTrue(changedEvent.get() == secondClick, "selector callback should receive the second click event");
        helper.assertTrue(requestedValue.get() == TestSelection.FIRST,
                "selector should wrap back to FIRST after SECOND");

        LDLib2FancySelectorConfigurator<TestSelection> returnedSelector = selector.setTooltip(
                value -> List.of(Component.literal("selected." + value.getTooltip())));

        helper.assertTrue(returnedSelector == selector, "setTooltip should return the same selector configurator");

        currentValue.set(TestSelection.FIRST);
        List<Component> firstTooltips = selector.getTooltips();
        helper.assertTrue(firstTooltips.size() == 1, "selector should create one tooltip for FIRST");
        helper.assertTrue("selected.test.selector.first".equals(firstTooltips.getFirst().getString()),
                "selector tooltip should be based on FIRST");

        currentValue.set(TestSelection.SECOND);
        List<Component> secondTooltips = selector.getTooltips();
        helper.assertTrue(secondTooltips.size() == 1, "selector should create one tooltip for SECOND");
        helper.assertTrue("selected.test.selector.second".equals(secondTooltips.getFirst().getString()),
                "selector tooltip should be based on SECOND");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyConfiguratorButton")
    public static void selectorConfiguratorRejectsCurrentValueOutsideProvidedValues(GameTestHelper helper) {
        try {
            new LDLib2FancySelectorConfigurator<>(
                    new TestSelection[] { TestSelection.FIRST, TestSelection.SECOND },
                    () -> TestSelection.THIRD,
                    (event, value) -> {
                        throw new AssertionError("selector callback must not run when construction fails");
                    });
            helper.assertTrue(false, "selector should reject a current value that is not in the provided values");
        } catch (NoSuchElementException exception) {
            helper.assertTrue(exception.getMessage().contains(TestSelection.THIRD.name()),
                    "selector rejection should identify the current value outside the provided values");
        }
        helper.succeed();
    }

    private enum TestSelection implements SelectableEnum {

        FIRST("test.selector.first"),
        SECOND("test.selector.second"),
        THIRD("test.selector.third");

        private final String tooltip;

        TestSelection(String tooltip) {
            this.tooltip = tooltip;
        }

        @Override
        public String getTooltip() {
            return tooltip;
        }

        @Override
        public IGuiTexture getIcon() {
            return IGuiTexture.EMPTY;
        }
    }
}
