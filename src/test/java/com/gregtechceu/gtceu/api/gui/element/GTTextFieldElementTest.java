package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTTextFieldElementTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTTextFieldElement")
    public static void mouseDownPreservesCommonPropagationWithoutClientFontLinkage(GameTestHelper helper) {
        UIElement root = new UIElement();
        GTTextFieldElement textField = new GTTextFieldElement(0, 0, 60, 14);
        root.addChild(textField);

        List<String> phases = new ArrayList<>();
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> phases.add("capture"), true);
        textField.addEventListener(UIEvents.MOUSE_DOWN, event -> phases.add("target"));
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> phases.add("bubble"));

        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = textField;
        event.button = 0;
        UIEventDispatcher.dispatchEvent(event, true, true, false);

        helper.assertTrue(phases.equals(List.of("capture", "target", "bubble")),
                "text field mouse-down did not preserve capture and bubble propagation");
        helper.succeed();
    }
}
