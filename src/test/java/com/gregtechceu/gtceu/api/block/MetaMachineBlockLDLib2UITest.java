package com.gregtechceu.gtceu.api.block;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.UITemplate;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import dev.vfyjxf.taffy.style.TaffyPosition;

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
}
