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
                inventory, IGuiTexture.EMPTY, 0, 0, false));
        assertMainInventoryWithHotbar(helper, UITemplate.bindPlayerInventoryLDLib2(
                inventory, IGuiTexture.EMPTY, 0, 0, true));

        helper.succeed();
    }

    private static void assertMainInventoryOnly(GameTestHelper helper, UIElement root) {
        List<UIElement> children = root.getChildren();
        helper.assertTrue(children.size() == 27, "main inventory child count was not 27");

        assertSlot(helper, children.getFirst(), 9);
        assertSlot(helper, children.getLast(), 35);
        for (int childIndex = 0; childIndex < children.size(); childIndex++) {
            assertSlot(helper, children.get(childIndex), childIndex + 9);
        }
    }

    private static void assertMainInventoryWithHotbar(GameTestHelper helper, UIElement root) {
        List<UIElement> children = root.getChildren();
        helper.assertTrue(children.size() == 36, "inventory with hotbar child count was not 36");

        for (int childIndex = 0; childIndex < 27; childIndex++) {
            assertSlot(helper, children.get(childIndex), childIndex + 9);
        }
        for (int hotbarIndex = 0; hotbarIndex < 9; hotbarIndex++) {
            assertSlot(helper, children.get(27 + hotbarIndex), hotbarIndex);
        }
    }

    private static void assertSlot(GameTestHelper helper, UIElement child, int expectedSlotIndex) {
        if (!(child instanceof GTItemSlotElement slotElement)) {
            helper.fail("child was not a GT item slot element");
            return;
        }

        helper.assertTrue(("inventory_" + expectedSlotIndex).equals(slotElement.getId()),
                "slot id did not match inventory_" + expectedSlotIndex);
        helper.assertTrue(slotElement.getSlot().getSlotIndex() == expectedSlotIndex,
                "slot index did not match " + expectedSlotIndex);
        helper.assertTrue(slotElement.getSlotStyle().isPlayerSlot(), "slot was not marked as a player slot");
    }
}
