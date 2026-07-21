package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.gui.factory.GTCoverUIContainerMenu;
import com.gregtechceu.gtceu.api.gui.factory.GTCoverUIMenuType;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIHolderContext;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CPacketCoverActionToServerTest {

    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos REMOTE_ANCHOR = new BlockPos(12, 1, 1);
    private static final BlockPos FAR_FROM_REMOTE_ANCHOR = new BlockPos(1, 1, 1);
    private static final double EXTENDED_BLOCK_INTERACTION_RANGE = 32.0;
    private static final Direction COVER_SIDE = Direction.EAST;
    private static final ResourceLocation ACTION_ID = GTCEu.id("test_cover_ui_session_action");
    private static final ResourceLocation WRONG_DEFINITION_ID = GTCEu.id("wrong_cover_ui_session_target");

    static {
        SyncActionDispatchers.server().register(new TestCoverActionHandler());
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void sessionRoundTripsThroughHolderFactoryAndPacketCodec(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover cover = installCover(machine);
        ServerPlayer player = preparePlayer(helper, machine, "cover_codec");
        GTCoverUIContainerMenu serverMenu = openMenu(player, cover);
        LDLib2CoverUIHolderContext serverHolder = holder(serverMenu);
        RegistryFriendlyByteBuf menuData = newBuffer(helper);
        RegistryFriendlyByteBuf packetData = newBuffer(helper);
        RegistryFriendlyByteBuf roundTrip = newBuffer(helper);
        try {
            serverHolder.writeClientSideData(serverMenu, menuData);
            ModularUIContainerMenu decodedMenu = GTCoverUIMenuType.create(42, player.getInventory(), menuData);
            helper.assertTrue(!menuData.isReadable(), "cover menu client data left unread bytes");
            helper.assertTrue(decodedMenu instanceof GTCoverUIContainerMenu,
                    "cover menu client factory did not create the dedicated menu");
            LDLib2CoverUIHolderContext clientHolder = holder((GTCoverUIContainerMenu) decodedMenu);
            helper.assertTrue(clientHolder.getActionSessionId().equals(serverHolder.getActionSessionId()),
                    "cover menu client factory changed the server action session");
            helper.assertTrue(clientHolder.getInteractionAnchor().equals(machine.getBlockPos()),
                    "default cover menu did not synchronize its cover-position interaction anchor");

            CPacketCoverActionToServer.CODEC.encode(packetData, packet(clientHolder, machine.getBlockPos(),
                    COVER_SIDE, cover.coverDefinition.getId()));
            CPacketCoverActionToServer decodedPacket = CPacketCoverActionToServer.CODEC.decode(packetData);
            helper.assertTrue(!packetData.isReadable(), "cover action codec left unread packet bytes");
            CPacketCoverActionToServer.CODEC.encode(roundTrip, decodedPacket);

            helper.assertTrue(roundTrip.readBlockPos().equals(machine.getBlockPos()),
                    "cover action codec changed the target position");
            helper.assertTrue(roundTrip.readEnum(Direction.class) == COVER_SIDE,
                    "cover action codec changed the target side");
            helper.assertTrue(roundTrip.readResourceLocation().equals(cover.coverDefinition.getId()),
                    "cover action codec changed the cover definition");
            helper.assertTrue(roundTrip.readUUID().equals(serverHolder.getActionSessionId()),
                    "cover action codec changed the action session");
            SyncActionData decodedAction = SyncActionData.STREAM_CODEC.decode(roundTrip);
            helper.assertTrue(decodedAction.actionId().equals(ACTION_ID) && decodedAction.sequence() == 0 &&
                    decodedAction.payload().isEmpty(), "cover action codec changed the sync action");
            helper.assertTrue(!roundTrip.isReadable(), "cover action round-trip left unread bytes");
            execute(player, decodedPacket);
            helper.assertTrue(cover.actionCount == 1,
                    "packet decoded from the client holder session did not dispatch its action");
        } finally {
            menuData.release();
            packetData.release();
            roundTrip.release();
            player.closeContainer();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void currentExactCoverSessionDispatchesAction(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover cover = installCover(machine);
        ServerPlayer player = preparePlayer(helper, machine, "cover_accept");
        LDLib2CoverUIHolderContext holder = holder(openMenu(player, cover));

        execute(player, packet(holder, machine.getBlockPos(), COVER_SIDE, cover.coverDefinition.getId()));

        helper.assertTrue(cover.actionCount == 1, "current exact cover session did not dispatch its action");
        player.closeContainer();
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void defaultAnchorRetainsCoverDistanceCheck(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover cover = installCover(machine);
        ServerPlayer player = preparePlayer(helper, machine, "cover_default_anchor_distance");
        LDLib2CoverUIHolderContext holder = holder(openMenu(player, cover));
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(REMOTE_ANCHOR)));

        execute(player, packet(holder, machine.getBlockPos(), COVER_SIDE, cover.coverDefinition.getId()));

        helper.assertTrue(cover.actionCount == 0,
                "default cover-position anchor accepted a player outside its interaction distance");
        player.closeContainer();
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void validRemoteAnchorAuthorizesExactCoverSession(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover cover = installCover(machine);
        ServerPlayer player = prepareMenuPlayer(helper, machine, "cover_remote_anchor_accept");
        BlockPos remoteAnchor = helper.absolutePos(REMOTE_ANCHOR);
        try {
            LDLib2CoverUIHolderContext holder = holder(openAnchoredMenu(
                    player, cover, remoteAnchor, () -> true));
            player.moveTo(Vec3.atCenterOf(remoteAnchor));

            execute(player, packet(holder, machine.getBlockPos(), COVER_SIDE, cover.coverDefinition.getId()));

            helper.assertTrue(cover.actionCount == 1,
                    "valid remote interaction anchor did not authorize its exact cover session");
            helper.succeed();
        } finally {
            player.closeContainer();
        }
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void invalidAndDistantRemoteAnchorsRejectWithoutExecution(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover cover = installCover(machine);
        ServerPlayer player = prepareMenuPlayer(helper, machine, "cover_remote_anchor_reject");
        BlockPos remoteAnchor = helper.absolutePos(REMOTE_ANCHOR);
        AtomicBoolean anchorValid = new AtomicBoolean(true);
        try {
            LDLib2CoverUIHolderContext invalidHolder = holder(openAnchoredMenu(
                    player, cover, remoteAnchor, anchorValid::get));
            player.moveTo(Vec3.atCenterOf(remoteAnchor));
            anchorValid.set(false);

            execute(player, packet(invalidHolder, machine.getBlockPos(), COVER_SIDE,
                    cover.coverDefinition.getId()));
            helper.assertTrue(cover.actionCount == 0,
                    "invalid remote interaction anchor dispatched a cover action");

            player.closeContainer();
            LDLib2CoverUIHolderContext distantHolder = holder(openAnchoredMenu(
                    player, cover, remoteAnchor, () -> true));
            AttributeInstance blockInteractionRange = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
            if (blockInteractionRange == null) {
                throw new GameTestAssertException("test player has no block interaction range attribute");
            }
            blockInteractionRange.setBaseValue(EXTENDED_BLOCK_INTERACTION_RANGE);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(FAR_FROM_REMOTE_ANCHOR)));
            execute(player, packet(distantHolder, machine.getBlockPos(), COVER_SIDE,
                    cover.coverDefinition.getId()));

            helper.assertTrue(cover.actionCount == 0,
                    "remote interaction anchor accepted a player outside its interaction distance");
            helper.succeed();
        } finally {
            player.closeContainer();
        }
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void anchorInvalidatedDuringProviderValidationIsRejected(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover cover = installCover(machine);
        ServerPlayer player = prepareMenuPlayer(helper, machine, "cover_anchor_validation_revoke");
        BlockPos remoteAnchor = helper.absolutePos(REMOTE_ANCHOR);
        AtomicBoolean anchorValid = new AtomicBoolean(true);
        try {
            LDLib2CoverUIHolderContext holder = holder(openAnchoredMenu(
                    player, cover, remoteAnchor, anchorValid::get));
            player.moveTo(Vec3.atCenterOf(remoteAnchor));
            cover.validationAction = () -> anchorValid.set(false);

            execute(player, packet(holder, machine.getBlockPos(), COVER_SIDE, cover.coverDefinition.getId()));

            helper.assertTrue(cover.actionCount == 0,
                    "anchor invalidated during provider validation dispatched a cover action");
            helper.succeed();
        } finally {
            player.closeContainer();
        }
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void missingMenuAndPreviousOpenSessionAreRejected(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover cover = installCover(machine);
        ServerPlayer player = preparePlayer(helper, machine, "cover_stale");
        LDLib2CoverUIHolderContext firstHolder = holder(openMenu(player, cover));
        UUID firstSession = firstHolder.getActionSessionId();
        CPacketCoverActionToServer firstPacket = roundTripPacket(helper,
                packet(firstHolder, machine.getBlockPos(), COVER_SIDE, cover.coverDefinition.getId()));

        player.closeContainer();
        execute(player, firstPacket);
        LDLib2CoverUIHolderContext secondHolder = holder(openMenu(player, cover));
        helper.assertTrue(!secondHolder.getActionSessionId().equals(firstSession),
                "a later cover menu reused the previous action session");
        execute(player, firstPacket);
        helper.assertTrue(cover.actionCount == 0, "missing menu or previous open session dispatched an action");
        execute(player, packet(secondHolder, machine.getBlockPos(), COVER_SIDE, cover.coverDefinition.getId()));

        helper.assertTrue(cover.actionCount == 1, "current reopened cover session did not dispatch its action");
        player.closeContainer();
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void mismatchedSessionAndTargetIdentityAreRejected(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover cover = installCover(machine);
        SessionTestCover upCover = installCover(machine, Direction.UP);
        ServerPlayer player = preparePlayer(helper, machine, "cover_target");
        LDLib2CoverUIHolderContext holder = holder(openMenu(player, cover));
        UUID sessionId = holder.getActionSessionId();
        UUID wrongSessionId = new UUID(sessionId.getMostSignificantBits() ^ 1L, sessionId.getLeastSignificantBits());

        execute(player, new CPacketCoverActionToServer(machine.getBlockPos(), COVER_SIDE,
                cover.coverDefinition.getId(), wrongSessionId, action()));
        execute(player, packet(holder, machine.getBlockPos().east(), COVER_SIDE, cover.coverDefinition.getId()));
        execute(player, packet(holder, machine.getBlockPos(), Direction.UP, upCover.coverDefinition.getId()));
        execute(player, packet(holder, machine.getBlockPos(), COVER_SIDE, WRONG_DEFINITION_ID));

        helper.assertTrue(cover.actionCount == 0 && upCover.actionCount == 0,
                "mismatched cover session or target identity dispatched an action");
        player.closeContainer();
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void replacementRevocationAndProviderFailureAreRejected(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover openedCover = installCover(machine);
        ServerPlayer player = preparePlayer(helper, machine, "cover_replace");
        LDLib2CoverUIHolderContext holder = holder(openMenu(player, openedCover));
        CPacketCoverActionToServer packet = packet(holder, machine.getBlockPos(), COVER_SIDE,
                openedCover.coverDefinition.getId());

        SessionTestCover replacement = installCover(machine);
        execute(player, packet);
        machine.getCoverContainer().setCoverAtSide(openedCover, COVER_SIDE);
        openedCover.canCreateUI = false;
        execute(player, packet);
        openedCover.canCreateUI = true;
        openedCover.failCanCreateUI = true;
        execute(player, packet);

        helper.assertTrue(openedCover.actionCount == 0 && replacement.actionCount == 0,
                "replacement, revoked provider, or failed provider dispatched an action");
        openedCover.failCanCreateUI = false;
        player.closeContainer();
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void replacementDuringProviderValidationIsRejected(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover openedCover = installCover(machine);
        ServerPlayer player = preparePlayer(helper, machine, "cover_validation_replace");
        LDLib2CoverUIHolderContext holder = holder(openMenu(player, openedCover));
        SessionTestCover replacement = new SessionTestCover(machine, COVER_SIDE);
        openedCover.replacementDuringValidation = replacement;

        execute(player, packet(holder, machine.getBlockPos(), COVER_SIDE, openedCover.coverDefinition.getId()));

        helper.assertTrue(openedCover.actionCount == 0 && replacement.actionCount == 0,
                "provider validation replacement dispatched an action");
        helper.assertTrue(machine.getCoverContainer().getCoverAtSide(COVER_SIDE) == replacement,
                "provider validation did not exercise same-definition replacement");
        player.closeContainer();
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketCoverActionToServer")
    public static void menuClosedDuringProviderValidationIsRejected(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        SessionTestCover cover = installCover(machine);
        ServerPlayer player = preparePlayer(helper, machine, "cover_validation_close");
        GTCoverUIContainerMenu menu = openMenu(player, cover);
        LDLib2CoverUIHolderContext holder = holder(menu);
        cover.closeMenuDuringValidation = true;

        execute(player, packet(holder, machine.getBlockPos(), COVER_SIDE, cover.coverDefinition.getId()));

        helper.assertTrue(cover.actionCount == 0, "provider validation menu close dispatched an action");
        helper.assertTrue(player.containerMenu != menu,
                "provider validation did not exercise closing the active cover menu");
        helper.succeed();
    }

    private static BufferMachine createBuffer(GameTestHelper helper) {
        return (BufferMachine) TestUtils.setMachine(helper, MACHINE_POS, GTMachines.BUFFER[LV]);
    }

    private static SessionTestCover installCover(BufferMachine machine) {
        return installCover(machine, COVER_SIDE);
    }

    private static SessionTestCover installCover(BufferMachine machine, Direction side) {
        SessionTestCover cover = new SessionTestCover(machine, side);
        machine.getCoverContainer().setCoverAtSide(cover, side);
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

    private static ServerPlayer prepareMenuPlayer(GameTestHelper helper, BufferMachine machine, String name) {
        UUID profileId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        ServerPlayer player = new MenuCapableTestPlayer(helper.getLevel(), new GameProfile(profileId, name));
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(machine.getBlockPos()));
        return player;
    }

    private static GTCoverUIContainerMenu openMenu(ServerPlayer player, SessionTestCover cover) {
        LDLib2CoverUIHolderContext holder = new LDLib2CoverUIHolderContext(player, cover);
        if (holder.createMenu(1, player.getInventory(), player) instanceof GTCoverUIContainerMenu menu) {
            player.containerMenu = menu;
            return menu;
        }
        throw new GameTestAssertException("cover UI did not use the dedicated container menu");
    }

    private static GTCoverUIContainerMenu openAnchoredMenu(ServerPlayer player, SessionTestCover cover,
                                                           BlockPos interactionAnchor,
                                                           BooleanSupplier interactionAnchorValid) {
        if (!GTCoverUIMenuType.openUI(cover, player, interactionAnchor, interactionAnchorValid)) {
            throw new GameTestAssertException("cover UI rejected its interaction anchor");
        }
        if (player.containerMenu instanceof GTCoverUIContainerMenu menu) {
            return menu;
        }
        throw new GameTestAssertException("anchored cover UI did not use the dedicated container menu");
    }

    private static LDLib2CoverUIHolderContext holder(GTCoverUIContainerMenu menu) {
        if (menu.uiHolder instanceof LDLib2CoverUIHolderContext holder) {
            return holder;
        }
        throw new GameTestAssertException("cover menu did not expose its holder context");
    }

    private static CPacketCoverActionToServer packet(UICoverHolder holder, BlockPos pos, Direction side,
                                                     ResourceLocation coverDefinitionId) {
        return new CPacketCoverActionToServer(pos, side, coverDefinitionId, holder.getActionSessionId(), action());
    }

    private static SyncActionData action() {
        return new SyncActionData(ACTION_ID, 0, DataComponentMap.EMPTY);
    }

    private static void execute(ServerPlayer player, CPacketCoverActionToServer packet) {
        packet.execute(new ServerPayloadContext(player.connection, CPacketCoverActionToServer.ID));
    }

    private static CPacketCoverActionToServer roundTripPacket(GameTestHelper helper,
                                                              CPacketCoverActionToServer packet) {
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        try {
            CPacketCoverActionToServer.CODEC.encode(buffer, packet);
            CPacketCoverActionToServer decoded = CPacketCoverActionToServer.CODEC.decode(buffer);
            helper.assertTrue(!buffer.isReadable(), "cover action codec left unread bytes");
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf newBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.OTHER);
    }

    private static final class SessionTestCover extends CoverBehavior implements LDLib2CoverUIProvider {

        private boolean canCreateUI = true;
        private boolean failCanCreateUI;
        private boolean closeMenuDuringValidation;
        @Nullable
        private Runnable validationAction;
        @Nullable
        private SessionTestCover replacementDuringValidation;
        private int actionCount;

        private SessionTestCover(BufferMachine machine, Direction side) {
            super(GTCovers.MACHINE_CONTROLLER, machine.getCoverContainer(), side);
        }

        @Override
        public boolean canCreateLDLib2UI(Player player, UICoverHolder holder) {
            Runnable action = validationAction;
            if (action != null) {
                validationAction = null;
                action.run();
            }
            SessionTestCover replacement = replacementDuringValidation;
            if (replacement != null) {
                replacementDuringValidation = null;
                coverHolder.setCoverAtSide(replacement, attachedSide);
                return true;
            }
            if (closeMenuDuringValidation) {
                closeMenuDuringValidation = false;
                player.closeContainer();
                return true;
            }
            if (failCanCreateUI) {
                throw new IllegalStateException("Expected test provider validation failure.");
            }
            return canCreateUI && holder.getCover() == this;
        }

        @Override
        public UI createLDLib2UI(Player player, UICoverHolder holder) {
            return UI.of(new UIElement());
        }
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

    private static final class TestCoverActionHandler implements SyncActionHandler {

        @Override
        public @NotNull ResourceLocation actionId() {
            return ACTION_ID;
        }

        @Override
        public boolean acceptsHolder(@NotNull SyncActionContext context) {
            return context.holder() instanceof SessionTestCover;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            return payload.isEmpty();
        }

        @Override
        public boolean mayExecute(@NotNull ServerPlayer player, @NotNull SyncActionContext context) {
            return true;
        }

        @Override
        public void execute(@NotNull SyncActionContext context) {
            if (context.holder() instanceof SessionTestCover cover) {
                cover.actionCount++;
                return;
            }
            throw new IllegalStateException("Cover session test handler received an invalid holder.");
        }
    }
}
