package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTraitType;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.electric.ItemCollectorMachine;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CPacketMachineTraitSyncToServerTest {

    private static final String BATCH = "CPacketMachineTraitSyncToServer";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final int MACHINE_TRAIT_TARGET_MARKER = 0x47545452;
    private static final ResourceLocation VALUE_FIELD = SyncFieldData.key("value");
    private static final ResourceLocation GUARDED_FIELD = SyncFieldData.key("guarded");
    private static final ResourceLocation CLIENT_ONLY_FIELD = SyncFieldData.key("clientOnly");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void rootPacketWireAndTargetBehaviorRemainCompatible(GameTestHelper helper) {
        ItemCollectorMachine machine = (ItemCollectorMachine) TestUtils.setMachine(helper, MACHINE_POS,
                GTMachines.ITEM_COLLECTOR[LV]);
        ServerPlayer player = preparePlayer(helper, machine, "machine_root_sync");
        CPacketMachineSyncToServer packet = new CPacketMachineSyncToServer(machine.getBlockPos(),
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(machine.getType()),
                payload(SyncFieldData.key("range"), new JsonPrimitive(4)));

        execute(player, roundTrip(helper, packet));

        helper.assertTrue(machine.getRange() == 4,
                "root machine field packet no longer updates the root SyncDataHolder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void exactTraitPacketPreservesNormalizerListenerAndAck(GameTestHelper helper) {
        MetaMachine machine = TestUtils.setMachine(helper, MACHINE_POS, GTMachines.BUFFER[LV]);
        TestServerSyncTrait trait = machine.attachTrait(new TestServerSyncTrait());
        RegistryAccess registries = helper.getLevel().registryAccess();
        trait.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        ServerPlayer player = preparePlayer(helper, machine, "machine_trait_sync");

        CPacketMachineSyncToServer packet = CPacketMachineSyncToServer.forMachineTrait(machine, trait,
                payload(VALUE_FIELD, new JsonPrimitive(25)));
        execute(player, roundTrip(helper, packet));

        helper.assertTrue(trait.value == 20, "trait normalizer did not canonicalize the client candidate");
        helper.assertTrue(trait.valueListenerCalls == 1 && trait.valueListenerOldValue == 1 &&
                trait.valueListenerNewValue == 20,
                "trait server listener did not observe the committed normalized update exactly once");
        SyncFieldData acknowledgement = trait.getSyncDataHolder().serializeToFieldData(registries, true, false);
        helper.assertTrue(acknowledgement.get(VALUE_FIELD).getAsInt() == 20,
                "trait SyncBoth update did not request its authoritative acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidTraitTargetsAndPayloadsAreRejectedAtomically(GameTestHelper helper) {
        MetaMachine machine = TestUtils.setMachine(helper, MACHINE_POS, GTMachines.BUFFER[LV]);
        TestServerSyncTrait trait = machine.attachTrait(new TestServerSyncTrait());
        ServerPlayer player = preparePlayer(helper, machine, "machine_trait_reject");
        int traitIndex = machine.getAllTraits().indexOf(trait);
        ResourceLocation blockEntityTypeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(machine.getType());
        ResourceLocation definitionId = machine.getDefinition().getId();

        execute(player, decodeTraitPacket(helper, machine.getBlockPos(), blockEntityTypeId,
                GTCEu.id("wrong_machine_definition"), traitIndex, trait.getClass().getName(),
                payload(VALUE_FIELD, new JsonPrimitive(8))));
        execute(player, decodeTraitPacket(helper, machine.getBlockPos(), blockEntityTypeId,
                definitionId, machine.getAllTraits().size(), trait.getClass().getName(),
                payload(VALUE_FIELD, new JsonPrimitive(8))));
        execute(player, decodeTraitPacket(helper, machine.getBlockPos(), blockEntityTypeId,
                definitionId, traitIndex, MachineTrait.class.getName(),
                payload(VALUE_FIELD, new JsonPrimitive(8))));
        execute(player, CPacketMachineSyncToServer.forMachineTrait(machine, trait,
                payload(CLIENT_ONLY_FIELD, new JsonPrimitive(8))));
        execute(player, CPacketMachineSyncToServer.forMachineTrait(machine, trait,
                payload(VALUE_FIELD, new JsonPrimitive("not an integer"))));
        execute(player, CPacketMachineSyncToServer.forMachineTrait(machine, trait,
                payload(SyncFieldData.builder()
                        .put(VALUE_FIELD, new JsonPrimitive(8))
                        .put(GUARDED_FIELD, new JsonPrimitive(-1))
                        .build())));

        helper.assertTrue(trait.hasInitialState(),
                "invalid trait target or payload partially changed the nested holder");
        helper.assertTrue(trait.valueListenerCalls == 0 && trait.guardedListenerCalls == 0,
                "rejected trait update invoked a server change listener");

        player.setGameMode(GameType.SPECTATOR);
        execute(player, CPacketMachineSyncToServer.forMachineTrait(machine, trait,
                payload(VALUE_FIELD, new JsonPrimitive(8))));
        helper.assertTrue(trait.hasInitialState(), "spectator permission failure changed the nested holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void traitPacketFactoryRequiresCurrentMachineOwnership(GameTestHelper helper) {
        MetaMachine machine = TestUtils.setMachine(helper, MACHINE_POS, GTMachines.BUFFER[LV]);
        TestServerSyncTrait unattached = new TestServerSyncTrait();

        try {
            CPacketMachineSyncToServer.forMachineTrait(machine, unattached,
                    payload(VALUE_FIELD, new JsonPrimitive(8)));
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank(),
                    "unattached trait failed without an explanatory message");
            helper.succeed();
            return;
        }
        throw new GameTestAssertException("packet factory accepted a trait that does not belong to its machine");
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void delayedPacketKeepsAttachmentOrderTargetAfterPriorityChange(GameTestHelper helper) {
        MetaMachine machine = TestUtils.setMachine(helper, MACHINE_POS, GTMachines.BUFFER[LV]);
        TestServerSyncTrait target = machine.attachTrait(new TestServerSyncTrait());
        CPacketMachineSyncToServer packet = CPacketMachineSyncToServer.forMachineTrait(machine, target,
                payload(VALUE_FIELD, new JsonPrimitive(8)));
        machine.attachTrait(new ReorderingTrait(), 10);
        ServerPlayer player = preparePlayer(helper, machine, "machine_trait_order");

        execute(player, roundTrip(helper, packet));

        helper.assertTrue(target.value == 8 && target.valueListenerCalls == 1,
                "trait priority sorting retargeted a delayed field update");
        helper.succeed();
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper, MetaMachine machine, String name) {
        UUID profileId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(profileId, name));
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(machine.getBlockPos()));
        return player;
    }

    private static void execute(ServerPlayer player, CPacketMachineSyncToServer packet) {
        packet.execute(new ServerPayloadContext(player.connection, CPacketMachineSyncToServer.ID));
    }

    private static CPacketMachineSyncToServer roundTrip(GameTestHelper helper,
                                                        CPacketMachineSyncToServer packet) {
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        try {
            CPacketMachineSyncToServer.CODEC.encode(buffer, packet);
            CPacketMachineSyncToServer decoded = CPacketMachineSyncToServer.CODEC.decode(buffer);
            helper.assertTrue(!buffer.isReadable(), "machine sync packet codec left unread bytes");
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static CPacketMachineSyncToServer decodeTraitPacket(GameTestHelper helper, BlockPos pos,
                                                                ResourceLocation blockEntityTypeId,
                                                                ResourceLocation definitionId, int traitIndex,
                                                                String traitClassName, DataComponentMap data) {
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        try {
            CPacketMachineSyncToServer.CODEC.encode(buffer,
                    new CPacketMachineSyncToServer(pos, blockEntityTypeId, data));
            buffer.writeInt(MACHINE_TRAIT_TARGET_MARKER);
            buffer.writeResourceLocation(definitionId);
            buffer.writeVarInt(traitIndex);
            buffer.writeUtf(traitClassName, 512);
            return CPacketMachineSyncToServer.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf newBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.OTHER);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonPrimitive value) {
        return payload(SyncFieldData.builder().put(field, value).build());
    }

    private static DataComponentMap payload(SyncFieldData fields) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .build();
    }

    private static final class TestServerSyncTrait extends MachineTrait {

        private static final MachineTraitType<TestServerSyncTrait> TYPE = new MachineTraitType<>(
                TestServerSyncTrait.class, false);

        @SyncBoth
        private int value = 1;
        @SyncBoth
        private int guarded = 2;
        @SyncToClient
        private int clientOnly = 3;
        private int valueListenerCalls;
        private int valueListenerOldValue;
        private int valueListenerNewValue;
        private int guardedListenerCalls;

        @Override
        public MachineTraitType<TestServerSyncTrait> getTraitType() {
            return TYPE;
        }

        @ServerFieldNormalizer(fieldName = "value")
        private int normalizeValue(int candidate) {
            return Math.clamp(candidate, 0, 20);
        }

        @ServerFieldNormalizer(fieldName = "guarded")
        private int normalizeGuarded(int candidate) {
            if (candidate < 0) {
                throw new IllegalArgumentException("guarded value cannot be negative");
            }
            return candidate;
        }

        @ServerFieldChangeListener(fieldName = "value")
        private void onValueChanged(int oldValue, int newValue) {
            valueListenerCalls++;
            valueListenerOldValue = oldValue;
            valueListenerNewValue = newValue;
        }

        @ServerFieldChangeListener(fieldName = "guarded")
        private void onGuardedChanged(int oldValue, int newValue) {
            guardedListenerCalls++;
        }

        private boolean hasInitialState() {
            return value == 1 && guarded == 2 && clientOnly == 3;
        }
    }

    private static final class ReorderingTrait extends MachineTrait {

        private static final MachineTraitType<ReorderingTrait> TYPE = new MachineTraitType<>(ReorderingTrait.class,
                false);

        @Override
        public MachineTraitType<ReorderingTrait> getTraitType() {
            return TYPE;
        }
    }
}
