package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEPatternBufferProxyActionsTest {

    private static final String BATCH = "MEPatternBufferProxyActions";
    private static final ResourceLocation PROXY_INCARNATION_FIELD = SyncFieldData.key("proxy_incarnation");
    private static final ResourceLocation BUFFER_POS_FIELD = SyncFieldData.key("buffer_pos");
    private static final ResourceLocation LINK_REVISION_FIELD = SyncFieldData.key("link_revision");
    private static final ResourceLocation NAME_FIELD = SyncFieldData.key("name");
    private static final ResourceLocation EXTRA_FIELD = SyncFieldData.key("extra");
    private static final MEPatternBufferProxyOpeningIdentity OPENING = new MEPatternBufferProxyOpeningIdentity(
            UUID.fromString("17306acd-3545-4d92-981d-7c137fd9a99a"), new BlockPos(12, 34, -56), 91L);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorsBindEveryCommandToTheExactOpening(GameTestHelper helper) {
        SyncActionData name = MEPatternBufferProxyActions.createSetNameAction(OPENING, "  Proxy Patterns  ");
        SyncActionData refund = MEPatternBufferProxyActions.createRefundAllAction(OPENING);
        SyncActionData tank = MEPatternBufferProxyActions.createClickShareTankAction(OPENING, 2, true);
        SyncActionData circuit = MEPatternBufferProxyActions.createSetCircuitConfigurationAction(OPENING, 17);
        SyncActionData place = MEPatternBufferProxyActions.createPlaceCoverAction(OPENING, Direction.WEST);
        SyncActionData remove = MEPatternBufferProxyActions.createRemoveCoverAction(OPENING, Direction.WEST);
        SyncActionData open = MEPatternBufferProxyActions.createOpenCoverAction(OPENING, Direction.WEST);

        for (SyncActionData action : new SyncActionData[] { name, refund, tank, circuit, place, remove, open }) {
            SyncFieldData fields = requireFields(action.payload());
            helper.assertTrue(readString(fields, PROXY_INCARNATION_FIELD).equals(
                    OPENING.proxyIncarnation().toString()), "Proxy action changed its opening incarnation");
            helper.assertTrue(readString(fields, BUFFER_POS_FIELD).equals(
                    Long.toString(OPENING.bufferPos().asLong())), "Proxy action changed its opening buffer position");
            helper.assertTrue(readString(fields, LINK_REVISION_FIELD).equals(
                    Long.toString(OPENING.linkRevision())), "Proxy action changed its opening link revision");
        }
        helper.assertTrue(readString(requireFields(name.payload()), NAME_FIELD).equals("  Proxy Patterns  "),
                "Proxy name creator normalized the exact name");
        assertCreatorRejected(helper,
                () -> MEPatternBufferProxyActions.createClickShareTankAction(OPENING, -1, false));
        assertCreatorRejected(helper,
                () -> MEPatternBufferProxyActions.createSetCircuitConfigurationAction(OPENING, 33));
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherExecutesEveryValidCommandExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        TestProxyActionTarget target = new TestProxyActionTarget(OPENING, 3);

        helper.assertTrue(dispatch(player, target,
                MEPatternBufferProxyActions.createSetNameAction(OPENING, "Exact")),
                "valid Proxy name action was rejected");
        helper.assertTrue(dispatch(player, target,
                MEPatternBufferProxyActions.createRefundAllAction(OPENING)),
                "valid Proxy refund action was rejected");
        helper.assertTrue(dispatch(player, target,
                MEPatternBufferProxyActions.createClickShareTankAction(OPENING, 2, true)),
                "valid Proxy tank action was rejected");
        helper.assertTrue(dispatch(player, target,
                MEPatternBufferProxyActions.createSetCircuitConfigurationAction(OPENING, 17)),
                "valid Proxy circuit action was rejected");
        helper.assertTrue(dispatch(player, target,
                MEPatternBufferProxyActions.createPlaceCoverAction(OPENING, Direction.NORTH)),
                "valid Proxy cover action was rejected");

        helper.assertTrue(target.nameWrites == 1 && "Exact".equals(target.name),
                "Proxy name action did not execute exactly once");
        helper.assertTrue(target.refunds == 1, "Proxy refund action did not execute exactly once");
        helper.assertTrue(target.tankClicks == 1 && target.lastTank == 2 && target.lastShift,
                "Proxy tank action did not execute exactly once");
        helper.assertTrue(target.circuitWrites == 1 && target.lastCircuit == 17,
                "Proxy circuit action did not execute exactly once");
        helper.assertTrue(target.coverWrites == 1 && target.lastSide == Direction.NORTH &&
                target.lastCoverOperation == MEPatternBufferProxyCoverOperation.PLACE,
                "Proxy cover action did not execute exactly once");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsStaleIdentityPermissionSpectatorAndTankBounds(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        TestProxyActionTarget target = new TestProxyActionTarget(OPENING, 3);
        SyncActionData valid = MEPatternBufferProxyActions.createRefundAllAction(OPENING);

        target.allowed = false;
        helper.assertTrue(!dispatch(player, target, valid), "Proxy action ignored owner permission failure");
        target.allowed = true;

        player.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(!dispatch(player, target, valid), "spectator executed a Proxy action");
        player.setGameMode(GameType.SURVIVAL);

        assertOpeningRejected(helper, player, target, new MEPatternBufferProxyOpeningIdentity(
                UUID.randomUUID(), OPENING.bufferPos(), OPENING.linkRevision()), "replacement Proxy incarnation");
        assertOpeningRejected(helper, player, target, new MEPatternBufferProxyOpeningIdentity(
                OPENING.proxyIncarnation(), OPENING.bufferPos().above(), OPENING.linkRevision()), "changed buffer pos");
        assertOpeningRejected(helper, player, target, new MEPatternBufferProxyOpeningIdentity(
                OPENING.proxyIncarnation(), OPENING.bufferPos(), OPENING.linkRevision() + 1), "changed revision");

        helper.assertTrue(!dispatch(player, target,
                MEPatternBufferProxyActions.createClickShareTankAction(OPENING, 3, false)),
                "Proxy tank action accepted its exclusive upper bound");
        helper.assertTrue(target.nameWrites == 0 && target.refunds == 0 && target.tankClicks == 0 &&
                target.circuitWrites == 0 && target.coverWrites == 0,
                "rejected Proxy action partially mutated its target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedAndExtraIdentityPayload(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper);
        TestProxyActionTarget target = new TestProxyActionTarget(OPENING, 3);
        SyncActionData valid = MEPatternBufferProxyActions.createSetNameAction(OPENING, "No mutation");

        assertMalformedRejected(helper, player, target, valid, identityFields(
                new JsonPrimitive("not-a-uuid"),
                new JsonPrimitive(Long.toString(OPENING.bufferPos().asLong())),
                new JsonPrimitive(Long.toString(OPENING.linkRevision())), false), "invalid incarnation");
        assertMalformedRejected(helper, player, target, valid, identityFields(
                new JsonPrimitive(OPENING.proxyIncarnation().toString()),
                new JsonPrimitive(Long.toString(OPENING.bufferPos().asLong())),
                new JsonPrimitive(OPENING.linkRevision()), false), "numeric revision");
        assertMalformedRejected(helper, player, target, valid, identityFields(
                new JsonPrimitive(OPENING.proxyIncarnation().toString()),
                new JsonPrimitive(Long.toString(OPENING.bufferPos().asLong())),
                new JsonPrimitive(Long.toString(OPENING.linkRevision())), true), "extra field");

        DataComponentMap extraComponent = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), requireFields(valid.payload()))
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), new ItemStack(Items.STONE))
                .build();
        helper.assertTrue(!dispatch(player, target,
                new SyncActionData(valid.actionId(), valid.sequence(), extraComponent)),
                "Proxy action accepted an extra data component");
        helper.assertTrue(target.nameWrites == 0, "malformed Proxy name action mutated its target");
        helper.succeed();
    }

    private static void assertOpeningRejected(GameTestHelper helper, ServerPlayer player,
                                              TestProxyActionTarget target,
                                              MEPatternBufferProxyOpeningIdentity opening, String description) {
        helper.assertTrue(!dispatch(player, target,
                MEPatternBufferProxyActions.createRefundAllAction(opening)),
                description + " was accepted");
    }

    private static void assertMalformedRejected(GameTestHelper helper, ServerPlayer player,
                                                TestProxyActionTarget target, SyncActionData valid,
                                                SyncFieldData malformed, String description) {
        DataComponentMap payload = malformed.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(!dispatch(player, target,
                new SyncActionData(valid.actionId(), valid.sequence(), payload)),
                description + " was accepted");
    }

    private static SyncFieldData identityFields(JsonPrimitive incarnation, JsonPrimitive bufferPos,
                                                JsonPrimitive revision, boolean includeExtra) {
        SyncFieldData.Builder builder = SyncFieldData.builder()
                .put(PROXY_INCARNATION_FIELD, incarnation)
                .put(BUFFER_POS_FIELD, bufferPos)
                .put(LINK_REVISION_FIELD, revision)
                .put(NAME_FIELD, new JsonPrimitive("No mutation"));
        if (includeExtra) {
            builder.put(EXTRA_FIELD, new JsonPrimitive(true));
        }
        return builder.build();
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        MEPatternBufferProxyActions.initialize();
        return SyncActionDispatchers.server().dispatch(new SyncActionContext(
                player, holder, action, BlockPos.ZERO, null, null, null));
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return player;
    }

    private static SyncFieldData requireFields(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Proxy action omitted sync field data.");
        }
        return fields;
    }

    private static String readString(SyncFieldData fields, ResourceLocation key) {
        if (!(fields.get(key) instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalStateException("Proxy action omitted string field " + key);
        }
        return primitive.getAsString();
    }

    private static void assertCreatorRejected(GameTestHelper helper, Runnable creator) {
        boolean rejected = false;
        try {
            creator.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Proxy action creator accepted an invalid argument");
    }

    private static final class TestProxyActionTarget implements MEPatternBufferProxyActionTarget {

        private final MEPatternBufferProxyOpeningIdentity opening;
        private final int tankCount;
        private boolean allowed = true;
        private String name = "";
        private int nameWrites;
        private int refunds;
        private int tankClicks;
        private int circuitWrites;
        private int coverWrites;
        private int lastTank = -1;
        private int lastCircuit = -2;
        private boolean lastShift;
        private Direction lastSide;
        private MEPatternBufferProxyCoverOperation lastCoverOperation;

        private TestProxyActionTarget(MEPatternBufferProxyOpeningIdentity opening, int tankCount) {
            this.opening = opening;
            this.tankCount = tankCount;
        }

        @Override
        public boolean canExecuteMEPatternBufferProxyAction(@NotNull ServerPlayer player,
                                                            @NotNull MEPatternBufferProxyOpeningIdentity opening) {
            return allowed && this.opening.equals(opening);
        }

        @Override
        public void setLinkedMEPatternBufferName(@NotNull MEPatternBufferProxyOpeningIdentity opening,
                                                 @NotNull String name) {
            this.name = name;
            nameWrites++;
        }

        @Override
        public void refundLinkedMEPatternBufferContents(@NotNull MEPatternBufferProxyOpeningIdentity opening) {
            refunds++;
        }

        @Override
        public int getLinkedMEPatternBufferShareTankCount(@NotNull MEPatternBufferProxyOpeningIdentity opening) {
            return tankCount;
        }

        @Override
        public void clickLinkedMEPatternBufferShareTank(@NotNull ServerPlayer player,
                                                        @NotNull MEPatternBufferProxyOpeningIdentity opening,
                                                        int tankIndex, boolean shiftDown) {
            tankClicks++;
            lastTank = tankIndex;
            lastShift = shiftDown;
        }

        @Override
        public void configureLinkedMEPatternBufferCircuit(@NotNull ServerPlayer player,
                                                          @NotNull MEPatternBufferProxyOpeningIdentity opening,
                                                          int configuration) {
            circuitWrites++;
            lastCircuit = configuration;
        }

        @Override
        public void configureLinkedMEPatternBufferCover(@NotNull ServerPlayer player,
                                                        @NotNull MEPatternBufferProxyOpeningIdentity opening,
                                                        @NotNull Direction side,
                                                        @NotNull MEPatternBufferProxyCoverOperation operation) {
            coverWrites++;
            lastSide = side;
            lastCoverOperation = operation;
        }
    }
}
