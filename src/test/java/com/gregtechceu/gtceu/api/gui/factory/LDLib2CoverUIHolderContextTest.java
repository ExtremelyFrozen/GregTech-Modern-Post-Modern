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
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

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
        ServerPlayer player = preparePlayer(helper);
        GTCoverUIContainerMenu menu = openMenu(helper, player, cover);

        player.closeContainer();
        menu.removed(player);

        helper.assertTrue(cover.closeCount == 1, "server menu close did not notify the opened cover exactly once");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2CoverUIHolderContext")
    public static void replacementDoesNotReceiveOpenedCoverClose(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CloseTrackingCover openedCover = installCover(machine, false);
        ServerPlayer player = preparePlayer(helper);
        GTCoverUIContainerMenu menu = openMenu(helper, player, openedCover);
        CloseTrackingCover replacement = installCover(machine, false);

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
        ServerPlayer player = preparePlayer(helper);
        GTCoverUIContainerMenu menu = openMenu(helper, player, cover);

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

    private static ServerPlayer preparePlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static GTCoverUIContainerMenu openMenu(GameTestHelper helper, ServerPlayer player,
                                                   CloseTrackingCover cover) {
        helper.assertTrue(CoverUIHelper.open(cover, player), "cover UI did not open");
        if (player.containerMenu instanceof GTCoverUIContainerMenu menu) {
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
