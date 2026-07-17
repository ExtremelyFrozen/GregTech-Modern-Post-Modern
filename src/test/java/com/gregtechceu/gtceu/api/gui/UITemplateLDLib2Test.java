package com.gregtechceu.gtceu.api.gui;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class UITemplateLDLib2Test {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "UITemplateLDLib2")
    public static void bindPlayerInventoryLDLib2BuildsExpectedStructure(GameTestHelper helper) {
        Inventory inventory = FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory();

        assertMainInventoryOnly(helper, UITemplate.bindPlayerInventoryLDLib2(
                inventory, IGuiTexture.EMPTY, 7, 11, false));
        assertMainInventoryWithHotbar(helper, UITemplate.bindPlayerInventoryLDLib2(
                inventory, IGuiTexture.EMPTY, 7, 11, true));

        helper.succeed();
    }

    private static void assertMainInventoryOnly(GameTestHelper helper, UIElement root) {
        List<UIElement> children = root.getChildren();
        helper.assertTrue(children.size() == 27, "main inventory child count was not 27");
        assertBounds(helper, root, 7, 11, 162, 54, "main inventory root");

        for (int childIndex = 0; childIndex < children.size(); childIndex++) {
            assertSlot(helper, children.get(childIndex), childIndex + 9,
                    childIndex % 9 * 18, childIndex / 9 * 18);
        }
    }

    private static void assertMainInventoryWithHotbar(GameTestHelper helper, UIElement root) {
        List<UIElement> children = root.getChildren();
        helper.assertTrue(children.size() == 36, "inventory with hotbar child count was not 36");
        assertBounds(helper, root, 7, 11, 162, 76, "inventory with hotbar root");

        for (int childIndex = 0; childIndex < 27; childIndex++) {
            assertSlot(helper, children.get(childIndex), childIndex + 9,
                    childIndex % 9 * 18, childIndex / 9 * 18);
        }
        for (int hotbarIndex = 0; hotbarIndex < 9; hotbarIndex++) {
            assertSlot(helper, children.get(27 + hotbarIndex), hotbarIndex, hotbarIndex * 18, 58);
        }
    }

    private static void assertSlot(GameTestHelper helper, UIElement child, int expectedSlotIndex, int x, int y) {
        if (!(child instanceof GTItemSlotElement slotElement)) {
            helper.fail("child was not a GT item slot element");
            return;
        }

        assertBounds(helper, slotElement, x, y, 18, 18, "inventory slot " + expectedSlotIndex);
        helper.assertTrue(("inventory_" + expectedSlotIndex).equals(slotElement.getId()),
                "slot id did not match inventory_" + expectedSlotIndex);
        helper.assertTrue(slotElement.getSlot().getSlotIndex() == expectedSlotIndex,
                "slot index did not match " + expectedSlotIndex);
        helper.assertTrue(slotElement.getSlotStyle().isPlayerSlot(), "slot was not marked as a player slot");
    }

    private static void assertBounds(GameTestHelper helper, UIElement element, int x, int y, int width, int height,
                                     String elementName) {
        var expected = new UITemplate.LDLib2Bounds(x, y, width, height);
        helper.assertTrue(UITemplate.getLDLib2Bounds(element).equals(expected),
                elementName + " did not preserve its declared bounds");
    }
}
