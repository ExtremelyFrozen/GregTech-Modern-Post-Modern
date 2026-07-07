package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2FancyTooltipsPanelElementTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyTooltipsPanelElement")
    public static void tooltipsPanelRefreshesVisibleIconChildren(GameTestHelper helper) {
        AtomicBoolean firstVisible = new AtomicBoolean(true);
        AtomicBoolean secondVisible = new AtomicBoolean(false);
        LDLib2FancyTooltipsPanelElement panel = new LDLib2FancyTooltipsPanelElement(0, 0);

        helper.assertTrue(panel.getChildren().isEmpty(), "new tooltips panel should not contain children");

        panel.attachTooltips(
                tooltip("first", firstVisible),
                tooltip("second", secondVisible));

        helper.assertTrue(panel.getChildren().size() == 1, "panel should contain only initially visible tooltip");
        helper.assertTrue(panel.getChildren().getFirst() instanceof GTImageElement,
                "visible tooltip child should be an image element");

        secondVisible.set(true);
        panel.screenTick();

        helper.assertTrue(panel.getChildren().size() == 2,
                "screenTick should refresh newly visible tooltip children");

        panel.screenTick();

        helper.assertTrue(panel.getChildren().size() == 2,
                "repeated screenTick should rebuild without accumulating tooltip children");

        firstVisible.set(false);
        panel.screenTick();

        helper.assertTrue(panel.getChildren().size() == 1,
                "screenTick should remove tooltip children whose predicate becomes false");

        firstVisible.set(true);
        panel.moveTo(8, 12);

        helper.assertTrue(panel.getChildren().size() == 2,
                "moveTo should refresh tooltip children for current predicates");

        panel.moveTo(16, 24);

        helper.assertTrue(panel.getChildren().size() == 2,
                "repeated moveTo should rebuild without accumulating tooltip children");

        panel.clear();

        helper.assertTrue(panel.getChildren().isEmpty(), "clear should remove all tooltip children");
        helper.succeed();
    }

    private static IFancyTooltip.Basic tooltip(String name, AtomicBoolean visible) {
        return new IFancyTooltip.Basic(
                () -> IGuiTexture.EMPTY,
                () -> List.of(Component.literal(name)),
                visible::get,
                () -> null);
    }
}
