package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class HeldItemUIHolderContextTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "HeldItemUIHolderContext")
    public static void holderSnapshotsOpenedStackAndTracksCurrentHeldItem(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack opened = new ItemStack(Items.STICK, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, opened.copy());
        HeldItemUIHolderContext holder = new HeldItemUIHolderContext(player, InteractionHand.MAIN_HAND, opened);
        TestLDLib2HeldItemUIProvider provider = new TestLDLib2HeldItemUIProvider();

        opened.setCount(2);

        helper.assertTrue(holder.getOpenedStack().getCount() == 1, "opened stack was not snapshotted");
        helper.assertTrue(holder.getOpenedStack().is(Items.STICK), "opened stack item changed unexpectedly");
        helper.assertTrue(provider.isLDLib2UIStillValid(player, holder),
                "matching held and opened stacks were rejected");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 1));

        helper.assertTrue(holder.getHeld().is(Items.DIAMOND), "held stack did not follow the player's main hand");
        helper.assertTrue(holder.getOpenedStack().is(Items.STICK), "opened stack did not remain the original item");
        helper.assertTrue(holder.getOpenedStack().getCount() == 1, "opened stack count changed after held update");
        helper.assertTrue(!provider.isLDLib2UIStillValid(player, holder),
                "changed held stack was still accepted by the default LDLib2 validity check");
        helper.succeed();
    }

    private static final class TestLDLib2HeldItemUIProvider implements LDLib2HeldItemUIProvider {

        @Override
        public UI createLDLib2UI(Player player, HeldItemUIHolder holder) {
            return UI.of(new UIElement());
        }
    }
}
