package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2CoverUIHolderContextTest {

    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final Direction COVER_SIDE = Direction.EAST;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2CoverUIHolderContext")
    public static void serverCloseNotifiesOpenedCoverOnce(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CloseTrackingCover cover = installCover(machine, false);
        ServerPlayer player = preparePlayer(helper, machine, "close_once");
        GTCoverUIContainerMenu menu = openMenu(player, cover, 1);

        player.closeContainer();
        menu.removed(player);

        helper.assertTrue(cover.closeCount == 1, "server menu close did not notify the opened cover exactly once");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2CoverUIHolderContext")
    public static void removalInvalidatesMenuAndNotifiesOpenedCoverOnce(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CloseTrackingCover openedCover = installCover(machine, false);
        ServerPlayer player = preparePlayer(helper, machine, "close_remove");
        GTCoverUIContainerMenu menu = openMenu(player, openedCover, 2);

        helper.assertTrue(machine.getCoverContainer().removeCover(false, COVER_SIDE, player),
                "opened cover could not be removed");
        helper.assertTrue(!menu.stillValid(player), "removed cover left its opened menu valid");

        player.closeContainer();
        menu.removed(player);
        menu.removed(player);

        helper.assertTrue(openedCover.closeCount == 1,
                "removed cover did not receive exactly one close callback");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2CoverUIHolderContext")
    public static void replacementDoesNotReceiveOpenedCoverClose(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CloseTrackingCover openedCover = installCover(machine, false);
        ServerPlayer player = preparePlayer(helper, machine, "close_replace");
        GTCoverUIContainerMenu menu = openMenu(player, openedCover, 3);
        CloseTrackingCover replacement = new CloseTrackingCover(machine, false);

        helper.assertTrue(machine.getCoverContainer().canPlaceCoverOnSide(replacement.coverDefinition, COVER_SIDE),
                "replacement cover placement was rejected");
        helper.assertTrue(replacement.canAttach(), "replacement cover could not attach");
        helper.assertTrue(machine.getCoverContainer().replaceCoverOnSide(
                COVER_SIDE, openedCover, replacement, ItemStack.EMPTY, player),
                "opened cover could not be replaced");
        helper.assertTrue(!menu.stillValid(player), "replacement left the earlier cover menu valid");

        player.closeContainer();
        menu.removed(player);

        helper.assertTrue(openedCover.closeCount == 1, "replacement prevented the opened cover close callback");
        helper.assertTrue(replacement.closeCount == 0, "replacement received an earlier menu's close callback");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2CoverUIHolderContext")
    public static void closeFailureIsLoggedAndNotRetried(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CloseTrackingCover cover = installCover(machine, true);
        ServerPlayer player = preparePlayer(helper, machine, "close_failure");
        GTCoverUIContainerMenu menu = openMenu(player, cover, 4);

        player.closeContainer();
        menu.removed(player);

        helper.assertTrue(cover.closeCount == 1, "failed close callback was retried");
        helper.succeed();
    }

    private static BufferMachine createBuffer(GameTestHelper helper) {
        return (BufferMachine) TestUtils.setMachine(helper, MACHINE_POS, GTMachines.BUFFER[LV]);
    }

    private static CloseTrackingCover installCover(BufferMachine machine, boolean failOnClose) {
        CloseTrackingCover cover = new CloseTrackingCover(machine, failOnClose);
        machine.getCoverContainer().setCoverAtSide(cover, COVER_SIDE);
        return cover;
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper, BufferMachine machine, String name) {
        UUID profileId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(profileId, name));
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(machine.getBlockPos()));
        return player;
    }

    private static GTCoverUIContainerMenu openMenu(ServerPlayer player, CloseTrackingCover cover, int containerId) {
        LDLib2CoverUIHolderContext holder = new LDLib2CoverUIHolderContext(player, cover);
        if (holder.createMenu(containerId, player.getInventory(), player) instanceof GTCoverUIContainerMenu menu) {
            player.containerMenu = menu;
            return menu;
        }
        throw new GameTestAssertException("cover UI did not use the dedicated container menu");
    }

    private static final class CloseTrackingCover extends CoverBehavior implements LDLib2CoverUIProvider {

        private final boolean failOnClose;
        private int closeCount;

        private CloseTrackingCover(BufferMachine machine, boolean failOnClose) {
            super(GTCovers.MACHINE_CONTROLLER, machine.getCoverContainer(), COVER_SIDE);
            this.failOnClose = failOnClose;
        }

        @Override
        public boolean canCreateLDLib2UI(Player player, UICoverHolder holder) {
            return true;
        }

        @Override
        public UI createLDLib2UI(Player player, UICoverHolder holder) {
            return UI.of(new UIElement());
        }

        @Override
        public void onUIClosed() {
            closeCount++;
            if (failOnClose) {
                throw new IllegalStateException("Expected test close failure.");
            }
        }
    }
}
