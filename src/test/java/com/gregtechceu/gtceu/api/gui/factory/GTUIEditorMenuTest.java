package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib2.gui.factory.LDMenuTypes;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTUIEditorMenuTest {

    private static final GameProfile PLAYER_PROFILE = new GameProfile(
            UUID.fromString("853a7214-fb49-4c31-90c0-1c9f778f4353"), "[GTUIEditorMenuTest]");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTUIEditorMenu")
    public static void playerMenuRegistrationBuildsServerEditorUI(GameTestHelper helper) {
        ServerPlayer player = new MenuOpeningFakePlayer(helper.getLevel());

        PlayerUIMenuType.unregister(GTUIEditorMenu.WINDOW_ID);
        try {
            helper.assertTrue(!GTUIEditorMenu.open(player), "unregistered editor menu unexpectedly opened");

            GTUIEditorMenu.register();
            helper.assertTrue(GTUIEditorMenu.open(player), "registered editor menu did not open");
            if (!(player.containerMenu instanceof ModularUIContainerMenu menu)) {
                helper.fail("editor did not open through the LDLib2 player menu type");
                return;
            }
            helper.assertTrue(menu.getType() == LDMenuTypes.PLAYER_UI.get(),
                    "editor menu did not use the LDLib2 player menu registry type");
            helper.assertTrue(menu.getModularUI().ui == UI.empty(), "opened server menu did not use the empty UI");
        } finally {
            GTUIEditorMenu.register();
        }

        helper.succeed();
    }

    @MethodsReturnNonnullByDefault
    private static final class MenuOpeningFakePlayer extends FakePlayer {

        private static final int CONTAINER_ID = 1;

        private MenuOpeningFakePlayer(ServerLevel level) {
            super(level, PLAYER_PROFILE);
        }

        @Override
        public OptionalInt openMenu(@Nullable MenuProvider menuProvider,
                                    @Nullable Consumer<RegistryFriendlyByteBuf> ignored) {
            if (menuProvider == null) {
                return OptionalInt.empty();
            }
            AbstractContainerMenu openedMenu = menuProvider.createMenu(CONTAINER_ID, getInventory(), this);
            if (openedMenu == null) {
                return OptionalInt.empty();
            }
            containerMenu = openedMenu;
            return OptionalInt.of(CONTAINER_ID);
        }
    }
}
