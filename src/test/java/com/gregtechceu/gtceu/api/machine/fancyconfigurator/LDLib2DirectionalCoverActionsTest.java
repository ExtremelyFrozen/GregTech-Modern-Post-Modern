package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIHolderContext;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.item.behavior.CoverPlaceBehavior;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2DirectionalCoverActionsTest {

    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final ResourceLocation PLACE_DIRECTIONAL_COVER_ACTION = GTCEu.id("place_directional_cover");
    private static final ResourceLocation REMOVE_DIRECTIONAL_COVER_ACTION = GTCEu.id("remove_directional_cover");
    private static final ResourceLocation OPEN_DIRECTIONAL_COVER_ACTION = GTCEu.id("open_directional_cover");
    private static final ResourceLocation SIDE_FIELD = SyncFieldData.key("side");

    static {
        LDLib2DirectionalCoverActions.initialize();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherPlacesCoverFromServerCarriedStackAndConsumesOne(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        ServerPlayer player = preparePlayer(helper);
        player.containerMenu.setCarried(GTItems.CONVEYOR_MODULE_LV.asStack(2));

        boolean result = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createPlaceCoverAction(Direction.EAST));

        CoverBehavior placedCover = machine.getCoverContainer().getCoverAtSide(Direction.EAST);
        helper.assertTrue(result, "valid directional cover placement was rejected");
        helper.assertTrue(placedCover != null && placedCover.getAttachItem().is(GTItems.CONVEYOR_MODULE_LV.get()),
                "directional cover placement did not install the server-carried cover");
        helper.assertTrue(player.containerMenu.getCarried().is(GTItems.CONVEYOR_MODULE_LV.get()) &&
                player.containerMenu.getCarried().getCount() == 1,
                "directional cover placement did not consume exactly one carried cover");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherCannotReuseConsumedCarriedCover(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        ServerPlayer player = preparePlayer(helper);
        player.containerMenu.setCarried(GTItems.CONVEYOR_MODULE_LV.asStack());

        boolean firstResult = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createPlaceCoverAction(Direction.EAST));
        boolean secondResult = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createPlaceCoverAction(Direction.UP));

        helper.assertTrue(firstResult, "last carried cover was not placed");
        helper.assertTrue(!secondResult, "consumed carried cover was reused on another side");
        helper.assertTrue(player.containerMenu.getCarried().isEmpty(),
                "placing the last carried cover did not exhaust the stack");
        helper.assertTrue(machine.getCoverContainer().getCoverAtSide(Direction.EAST) != null &&
                machine.getCoverContainer().getCoverAtSide(Direction.UP) == null,
                "consumed carried cover installed more than one cover");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherPlacesCoverWithoutConsumingCreativeCarriedStack(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        ServerPlayer player = preparePlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        player.containerMenu.setCarried(GTItems.CONVEYOR_MODULE_LV.asStack(2));

        try {
            boolean result = dispatch(player, machine,
                    LDLib2DirectionalCoverActions.createPlaceCoverAction(Direction.UP));

            helper.assertTrue(result, "creative directional cover placement was rejected");
            helper.assertTrue(machine.getCoverContainer().getCoverAtSide(Direction.UP) != null,
                    "creative directional cover placement did not install a cover");
            helper.assertTrue(player.containerMenu.getCarried().is(GTItems.CONVEYOR_MODULE_LV.get()) &&
                    player.containerMenu.getCarried().getCount() == 2,
                    "creative directional cover placement consumed the carried cover");
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherReplacesSingleCoverAndReturnsPreviousCoverToCursor(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CoverBehavior previousCover = TestUtils.placeCover(helper, machine, GTItems.COVER_SHUTTER.asStack(),
                Direction.EAST);
        ServerPlayer player = preparePlayer(helper);
        player.containerMenu.setCarried(GTItems.CONVEYOR_MODULE_LV.asStack());

        boolean result = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createPlaceCoverAction(Direction.EAST));

        CoverBehavior replacement = machine.getCoverContainer().getCoverAtSide(Direction.EAST);
        helper.assertTrue(result, "valid directional cover replacement was rejected");
        helper.assertTrue(replacement != null && replacement != previousCover &&
                replacement.getAttachItem().is(GTItems.CONVEYOR_MODULE_LV.get()),
                "directional cover replacement did not install the requested cover");
        helper.assertTrue(player.containerMenu.getCarried().is(GTItems.COVER_SHUTTER.get()) &&
                player.containerMenu.getCarried().getCount() == 1,
                "directional cover replacement did not swap the previous cover into the cursor");
        helper.assertTrue(player.getInventory().countItem(GTItems.COVER_SHUTTER.get()) == 0,
                "directional cover replacement duplicated the previous cover into the inventory");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherRejectsStackedReplacementAtomically(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CoverBehavior previousCover = TestUtils.placeCover(helper, machine, GTItems.COVER_SHUTTER.asStack(),
                Direction.EAST);
        ServerPlayer player = preparePlayer(helper);
        player.containerMenu.setCarried(GTItems.CONVEYOR_MODULE_LV.asStack(2));

        boolean result = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createPlaceCoverAction(Direction.EAST));

        helper.assertTrue(!result, "directional action accepted a stacked replacement");
        helper.assertTrue(machine.getCoverContainer().getCoverAtSide(Direction.EAST) == previousCover,
                "rejected stacked replacement changed the installed cover");
        helper.assertTrue(player.containerMenu.getCarried().is(GTItems.CONVEYOR_MODULE_LV.get()) &&
                player.containerMenu.getCarried().getCount() == 2,
                "rejected stacked replacement changed the carried stack");
        helper.assertTrue(player.getInventory().countItem(GTItems.COVER_SHUTTER.get()) == 0,
                "rejected stacked replacement duplicated the installed cover");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherRejectsSameCoverReplacementAtomically(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CoverBehavior previousCover = TestUtils.placeCover(helper, machine, GTItems.CONVEYOR_MODULE_LV.asStack(),
                Direction.EAST);
        ServerPlayer player = preparePlayer(helper);
        player.containerMenu.setCarried(GTItems.CONVEYOR_MODULE_LV.asStack());

        boolean result = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createPlaceCoverAction(Direction.EAST));

        helper.assertTrue(!result, "directional action replaced a cover with the same carried cover");
        helper.assertTrue(machine.getCoverContainer().getCoverAtSide(Direction.EAST) == previousCover,
                "rejected same-cover replacement changed the installed cover");
        helper.assertTrue(player.containerMenu.getCarried().is(GTItems.CONVEYOR_MODULE_LV.get()) &&
                player.containerMenu.getCarried().getCount() == 1,
                "rejected same-cover replacement changed the carried stack");
        helper.assertTrue(player.getInventory().countItem(GTItems.CONVEYOR_MODULE_LV.get()) == 0,
                "rejected same-cover replacement duplicated the installed cover");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherCreativeReplacementReturnsPreviousCoverToCursor(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CoverBehavior previousCover = TestUtils.placeCover(helper, machine, GTItems.COVER_SHUTTER.asStack(),
                Direction.EAST);
        ServerPlayer player = preparePlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        player.containerMenu.setCarried(GTItems.CONVEYOR_MODULE_LV.asStack());

        try {
            boolean result = dispatch(player, machine,
                    LDLib2DirectionalCoverActions.createPlaceCoverAction(Direction.EAST));

            CoverBehavior replacement = machine.getCoverContainer().getCoverAtSide(Direction.EAST);
            helper.assertTrue(result, "creative directional cover replacement was rejected");
            helper.assertTrue(replacement != null && replacement != previousCover &&
                    replacement.getAttachItem().is(GTItems.CONVEYOR_MODULE_LV.get()),
                    "creative directional cover replacement did not install the requested cover");
            helper.assertTrue(player.containerMenu.getCarried().is(GTItems.COVER_SHUTTER.get()) &&
                    player.containerMenu.getCarried().getCount() == 1,
                    "creative directional cover replacement did not return the previous cover to the cursor");
            helper.assertTrue(player.getInventory().countItem(GTItems.COVER_SHUTTER.get()) == 0,
                    "creative directional cover replacement duplicated the previous cover into the inventory");
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void prevalidatedReplacementDoesNotRepeatMutableAttachmentCheck(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CoverBehavior previousCover = TestUtils.placeCover(helper, machine, GTItems.COVER_SHUTTER.asStack(),
                Direction.EAST);
        ServerPlayer player = preparePlayer(helper);
        ItemStack candidateStack = GTItems.CONVEYOR_MODULE_LV.asStack();
        player.containerMenu.setCarried(candidateStack);
        CoverDefinition candidateDefinition = CoverPlaceBehavior.findCoverDefinition(candidateStack);
        helper.assertTrue(candidateDefinition != null,
                "conveyor test item did not expose its cover definition");
        SingleAttachmentCheckCover replacement = new SingleAttachmentCheckCover(candidateDefinition,
                machine.getCoverContainer(), Direction.EAST);
        helper.assertTrue(machine.getCoverContainer().canPlaceCoverOnSide(candidateDefinition, Direction.EAST) &&
                replacement.canAttach(), "replacement test fixture did not pass its first placement validation");

        boolean result = machine.getCoverContainer().replaceCoverOnSide(Direction.EAST, previousCover, replacement,
                candidateStack, player);

        helper.assertTrue(result, "prevalidated replacement was rejected");
        helper.assertTrue(machine.getCoverContainer().getCoverAtSide(Direction.EAST) == replacement,
                "prevalidated replacement did not become the installed cover");
        helper.assertTrue(replacement.getAttachmentCheckCount() == 1,
                "cover exchange repeated the mutable canAttach validation");
        helper.assertTrue(player.containerMenu.getCarried() == candidateStack && candidateStack.getCount() == 1,
                "cover exchange unexpectedly changed the caller-owned cursor stack");
        helper.assertTrue(player.getInventory().countItem(GTItems.COVER_SHUTTER.get()) == 0,
                "cover exchange dropped the previous cover into the inventory");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void prevalidatedReplacementRejectsStaleExpectedCoverWithoutMutation(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CoverBehavior installedCover = TestUtils.placeCover(helper, machine, GTItems.COVER_SHUTTER.asStack(),
                Direction.EAST);
        ServerPlayer player = preparePlayer(helper);
        ItemStack candidateStack = GTItems.CONVEYOR_MODULE_LV.asStack();
        player.containerMenu.setCarried(candidateStack);
        CoverDefinition candidateDefinition = CoverPlaceBehavior.findCoverDefinition(candidateStack);
        helper.assertTrue(candidateDefinition != null,
                "conveyor test item did not expose its cover definition");
        SingleAttachmentCheckCover replacement = new SingleAttachmentCheckCover(candidateDefinition,
                machine.getCoverContainer(), Direction.EAST);
        helper.assertTrue(machine.getCoverContainer().canPlaceCoverOnSide(candidateDefinition, Direction.EAST) &&
                replacement.canAttach(), "stale-state test fixture did not pass its placement validation");
        CoverBehavior staleExpectedCover = installedCover.coverDefinition.createCoverBehavior(
                machine.getCoverContainer(), Direction.EAST);

        boolean result = machine.getCoverContainer().replaceCoverOnSide(Direction.EAST, staleExpectedCover,
                replacement, candidateStack, player);

        helper.assertTrue(!result, "cover exchange accepted a stale expected cover");
        helper.assertTrue(machine.getCoverContainer().getCoverAtSide(Direction.EAST) == installedCover,
                "stale cover exchange changed the installed cover");
        helper.assertTrue(replacement.getAttachmentCheckCount() == 1 && replacement.getAttachItem().isEmpty(),
                "stale cover exchange revalidated or attached the replacement");
        helper.assertTrue(player.containerMenu.getCarried() == candidateStack && candidateStack.getCount() == 1,
                "stale cover exchange changed the cursor stack");
        helper.assertTrue(player.getInventory().countItem(GTItems.COVER_SHUTTER.get()) == 0,
                "stale cover exchange changed the player inventory");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherRejectsInvalidReplacementAtomically(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CoverBehavior previousCover = TestUtils.placeCover(helper, machine, GTItems.CONVEYOR_MODULE_LV.asStack(),
                Direction.EAST);
        ServerPlayer player = preparePlayer(helper);
        ItemStack candidateStack = GTItems.COVER_MACHINE_CONTROLLER.asStack();
        CoverDefinition candidateDefinition = CoverPlaceBehavior.findCoverDefinition(candidateStack);
        helper.assertTrue(candidateDefinition != null,
                "machine controller test item did not expose its cover definition");
        helper.assertTrue(!candidateDefinition.createCoverBehavior(machine.getCoverContainer(), Direction.EAST)
                .canAttach(), "failed replacement test requires a cover that cannot attach to the buffer");
        player.containerMenu.setCarried(candidateStack);

        boolean result = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createPlaceCoverAction(Direction.EAST));

        helper.assertTrue(!result, "directional action accepted a cover that cannot attach");
        helper.assertTrue(machine.getCoverContainer().getCoverAtSide(Direction.EAST) == previousCover,
                "failed directional replacement removed or replaced the existing cover");
        helper.assertTrue(player.containerMenu.getCarried().is(GTItems.COVER_MACHINE_CONTROLLER.get()) &&
                player.containerMenu.getCarried().getCount() == 1,
                "failed directional replacement changed the carried candidate stack");
        helper.assertTrue(player.getInventory().countItem(GTItems.CONVEYOR_MODULE_LV.get()) == 0,
                "failed directional replacement returned the still-installed cover to the player");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherRemovesCoverToEmptyCursorOnlyOnce(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        TestUtils.placeCover(helper, machine, GTItems.CONVEYOR_MODULE_LV.asStack(), Direction.EAST);
        ServerPlayer player = preparePlayer(helper);

        boolean firstResult = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createRemoveCoverAction(Direction.EAST));
        boolean secondResult = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createRemoveCoverAction(Direction.EAST));

        helper.assertTrue(firstResult, "valid directional cover removal was rejected");
        helper.assertTrue(!secondResult, "directional action removed the same cover twice");
        helper.assertTrue(machine.getCoverContainer().getCoverAtSide(Direction.EAST) == null,
                "directional cover removal left the cover installed");
        helper.assertTrue(player.containerMenu.getCarried().is(GTItems.CONVEYOR_MODULE_LV.get()) &&
                player.containerMenu.getCarried().getCount() == 1,
                "directional cover removal did not return the cover to the cursor");
        helper.assertTrue(player.getInventory().countItem(GTItems.CONVEYOR_MODULE_LV.get()) == 0,
                "directional cover removal duplicated the removed cover into the inventory");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherRejectsRemovalWithOccupiedCursor(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CoverBehavior installedCover = TestUtils.placeCover(helper, machine, GTItems.CONVEYOR_MODULE_LV.asStack(),
                Direction.EAST);
        ServerPlayer player = preparePlayer(helper);
        player.containerMenu.setCarried(GTItems.COVER_SHUTTER.asStack());

        boolean result = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createRemoveCoverAction(Direction.EAST));

        helper.assertTrue(!result, "directional action removed a cover while the cursor was occupied");
        helper.assertTrue(machine.getCoverContainer().getCoverAtSide(Direction.EAST) == installedCover,
                "rejected occupied-cursor removal changed the installed cover");
        helper.assertTrue(player.containerMenu.getCarried().is(GTItems.COVER_SHUTTER.get()) &&
                player.containerMenu.getCarried().getCount() == 1,
                "rejected occupied-cursor removal changed the carried stack");
        helper.assertTrue(player.getInventory().countItem(GTItems.CONVEYOR_MODULE_LV.get()) == 0,
                "rejected occupied-cursor removal duplicated the installed cover");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherRejectsInvalidSidePayloadsWithoutMutation(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        ServerPlayer player = preparePlayer(helper);
        player.containerMenu.setCarried(GTItems.CONVEYOR_MODULE_LV.asStack(2));

        boolean missing = dispatch(player, machine,
                new SyncActionData(PLACE_DIRECTIONAL_COVER_ACTION, 0, DataComponentMap.EMPTY));
        boolean string = dispatch(player, machine,
                action(REMOVE_DIRECTIONAL_COVER_ACTION, new JsonPrimitive("0")));
        boolean decimal = dispatch(player, machine,
                action(OPEN_DIRECTIONAL_COVER_ACTION, new JsonPrimitive(new BigDecimal("0.5"))));
        boolean huge = dispatch(player, machine,
                action(PLACE_DIRECTIONAL_COVER_ACTION,
                        new JsonPrimitive(new BigInteger("18446744073709551616"))));
        boolean negative = dispatch(player, machine,
                action(REMOVE_DIRECTIONAL_COVER_ACTION, new JsonPrimitive(-1)));
        boolean overflow = dispatch(player, machine,
                action(OPEN_DIRECTIONAL_COVER_ACTION, new JsonPrimitive(Direction.values().length)));

        helper.assertTrue(!missing && !string && !decimal && !huge && !negative && !overflow,
                "directional cover action accepted an invalid side payload");
        helper.assertTrue(machine.getCoverContainer().getCovers().isEmpty(),
                "invalid side payload installed a cover");
        helper.assertTrue(player.containerMenu.getCarried().is(GTItems.CONVEYOR_MODULE_LV.get()) &&
                player.containerMenu.getCarried().getCount() == 2,
                "invalid side payload changed the server-carried cover stack");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherRejectsWrongUnmarkedAndSpectatorHolders(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        MetaMachine unmarkedMachine = TestUtils.setMachine(helper, new BlockPos(3, 1, 1), GTMachines.WOODEN_CRATE);
        ServerPlayer player = preparePlayer(helper);
        player.containerMenu.setCarried(GTItems.CONVEYOR_MODULE_LV.asStack(2));
        SyncActionData action = LDLib2DirectionalCoverActions.createPlaceCoverAction(Direction.EAST);

        boolean wrongHolder = dispatch(player, new Object(), action);
        boolean unmarkedHolder = dispatch(player, unmarkedMachine, action);
        player.setGameMode(GameType.SPECTATOR);
        boolean spectator;
        try {
            spectator = dispatch(player, machine, action);
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!wrongHolder, "directional cover action accepted a non-machine holder");
        helper.assertTrue(!unmarkedHolder, "directional cover action accepted an unmarked machine holder");
        helper.assertTrue(!spectator, "directional cover action accepted a spectator");
        helper.assertTrue(machine.getCoverContainer().getCovers().isEmpty() &&
                unmarkedMachine.getCoverContainer().getCovers().isEmpty(),
                "rejected directional cover action changed a holder");
        helper.assertTrue(player.containerMenu.getCarried().is(GTItems.CONVEYOR_MODULE_LV.get()) &&
                player.containerMenu.getCarried().getCount() == 2,
                "rejected directional cover action changed the carried stack");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherOpensOnlyTheCurrentConfigurableCover(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        CoverBehavior cover = TestUtils.placeCover(helper, machine, GTItems.CONVEYOR_MODULE_LV.asStack(),
                Direction.EAST);
        ServerPlayer player = prepareMenuPlayer(helper);

        boolean result = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createOpenCoverAction(Direction.EAST));

        helper.assertTrue(result, "directional action did not open a configurable cover");
        helper.assertTrue(player.containerMenu instanceof ModularUIContainerMenu menu &&
                menu.uiHolder instanceof LDLib2CoverUIHolderContext holder && holder.getCover() == cover,
                "directional cover action opened the wrong menu or cover holder");
        player.closeContainer();
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalCoverActions")
    public static void dispatcherRejectsMissingAndNonConfigurableCoverOpenRequests(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        ServerPlayer player = preparePlayer(helper);
        Object initialMenu = player.containerMenu;

        boolean missingCover = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createOpenCoverAction(Direction.EAST));
        TestUtils.placeCover(helper, machine, GTItems.COVER_SHUTTER.asStack(), Direction.UP);
        boolean nonConfigurableCover = dispatch(player, machine,
                LDLib2DirectionalCoverActions.createOpenCoverAction(Direction.UP));

        helper.assertTrue(!missingCover, "directional action opened an empty cover side");
        helper.assertTrue(!nonConfigurableCover, "directional action opened a cover without an LDLib2 UI");
        helper.assertTrue(player.containerMenu == initialMenu,
                "rejected directional cover open request changed the player's menu");
        helper.succeed();
    }

    private static BufferMachine createBuffer(GameTestHelper helper) {
        BufferMachine machine = (BufferMachine) TestUtils.setMachine(helper, MACHINE_POS, GTMachines.BUFFER[LV]);
        machine.setFrontFacing(Direction.NORTH);
        return machine;
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper) {
        return preparePlayer(FakePlayerFactory.getMinecraft(helper.getLevel()));
    }

    private static ServerPlayer prepareMenuPlayer(GameTestHelper helper) {
        GameProfile profile = FakePlayerFactory.getMinecraft(helper.getLevel()).getGameProfile();
        return preparePlayer(new MenuCapableTestPlayer(helper.getLevel(), profile));
    }

    private static ServerPlayer preparePlayer(ServerPlayer player) {
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return player;
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        BlockPos pos = holder instanceof MetaMachine machine ? machine.getBlockPos() : BlockPos.ZERO;
        SyncActionContext context = new SyncActionContext(player, holder, action, pos, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(ResourceLocation actionId, JsonElement side) {
        return new SyncActionData(actionId, 0, payload(side));
    }

    private static DataComponentMap payload(JsonElement side) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SIDE_FIELD, side)
                        .build())
                .build();
    }

    private static final class MenuCapableTestPlayer extends ServerPlayer {

        private MenuCapableTestPlayer(ServerLevel level, GameProfile profile) {
            super(level.getServer(), level, profile, ClientInformation.createDefault());
            connection = new DiscardingServerGamePacketListener(level.getServer(), this);
        }
    }

    private static final class DiscardingServerGamePacketListener extends ServerGamePacketListenerImpl {

        private DiscardingServerGamePacketListener(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(@NotNull Packet<?> packet) {}

        @Override
        public void send(@NotNull Packet<?> packet, @Nullable PacketSendListener sendListener) {}
    }

    private static final class SingleAttachmentCheckCover extends CoverBehavior {

        private int attachmentCheckCount;

        private SingleAttachmentCheckCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
            super(definition, coverHolder, attachedSide);
        }

        @Override
        public boolean canAttach() {
            attachmentCheckCount++;
            return attachmentCheckCount == 1 && super.canAttach();
        }

        private int getAttachmentCheckCount() {
            return attachmentCheckCount;
        }
    }
}
