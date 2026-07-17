package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTUIEditorMenuTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTUIEditorMenu")
    public static void playerMenuRegistrationBuildsServerAndClientEditorUI(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        PlayerUIMenuType.unregister(GTUIEditorMenu.WINDOW_ID);
        try {
            helper.assertTrue(!GTUIEditorMenu.open(player), "unregistered editor menu unexpectedly opened");

            ModularUI serverUI = GTUIEditorMenu.createHolder(player).createUI(player);
            helper.assertTrue(serverUI.ui == UI.empty(), "server holder did not use the empty UI");

            GTUIEditorMenu.register();
            helper.assertTrue(GTUIEditorMenu.open(player), "registered editor menu did not open");
            if (!(player.containerMenu instanceof ModularUIContainerMenu menu)) {
                helper.fail("editor did not open through the LDLib2 player menu type");
                return;
            }
            helper.assertTrue(menu.getModularUI().ui == UI.empty(), "opened server menu did not use the empty UI");

            ModularUI clientUI = GTUIEditorMenu.createClientUI();
            helper.assertTrue(!clientUI.shouldCloseOnEsc(), "client editor closes on Escape");
            helper.assertTrue(!clientUI.shouldCloseOnKeyInventory(), "client editor closes on the inventory key");
            if (!(clientUI.ui.getRootElement() instanceof EditorWindow editorWindow)) {
                helper.fail("client holder did not create an editor window");
                return;
            }
            helper.assertTrue(GTUIEditorMenu.WINDOW_ID.equals(editorWindow.windowID),
                    "client editor window used the wrong id");
        } finally {
            GTUIEditorMenu.register();
        }

        helper.succeed();
    }
}
