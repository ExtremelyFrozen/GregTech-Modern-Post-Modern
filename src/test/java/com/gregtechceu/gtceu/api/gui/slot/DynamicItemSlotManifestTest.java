package com.gregtechceu.gtceu.api.gui.slot;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unused")
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DynamicItemSlotManifestTest {

    private static final String BATCH = "DynamicItemSlotManifest";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void validManifestAndDefinitionRoundTrip(GameTestHelper helper) {
        DynamicItemSlotDefinition definition = new DynamicItemSlotDefinition(id(10), 9);
        DynamicItemSlotManifest manifest = new DynamicItemSlotManifest(
                4,
                5,
                id(20),
                17,
                36,
                List.of(
                        binding(30, 10, 36, 9, true),
                        binding(31, 11, 45, 3, false)));
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        try {
            DynamicItemSlotDefinition.STREAM_CODEC.encode(buffer, definition);
            DynamicItemSlotManifest.STREAM_CODEC.encode(buffer, manifest);

            DynamicItemSlotDefinition decodedDefinition = DynamicItemSlotDefinition.STREAM_CODEC.decode(buffer);
            DynamicItemSlotManifest decodedManifest = DynamicItemSlotManifest.STREAM_CODEC.decode(buffer);

            helper.assertTrue(definition.equals(decodedDefinition), "definition codec changed the request");
            helper.assertTrue(manifest.equals(decodedManifest), "manifest codec changed the ordered bindings");
            helper.assertTrue(decodedManifest.totalSlotCount() == 48, "manifest reported the wrong total slot count");
            helper.assertTrue(decodedManifest.bindings().get(1).tombstone(), "tombstone state did not round-trip");
            helper.assertTrue(!buffer.isReadable(), "codec left unread manifest bytes");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void bindingsMustUseContinuousSlotIds(GameTestHelper helper) {
        new DynamicItemSlotManifest(
                0, 1, id(1), 0, 9,
                List.of(binding(2, 20, 9, 2, true), binding(3, 21, 11, 4, true)));

        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(
                        0, 1, id(1), 0, 9,
                        List.of(binding(2, 20, 9, 2, true), binding(3, 21, 12, 4, true))),
                "manifest accepted a gap in dynamic slot ids");
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(
                        0, 1, id(1), 0, 9,
                        List.of(binding(2, 20, 9, 2, true), binding(3, 21, 10, 4, true))),
                "manifest accepted overlapping dynamic slot ids");
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(
                        0, 1, id(1), 0, 9,
                        List.of(binding(2, 20, 11, 4, true), binding(3, 21, 9, 2, true))),
                "manifest accepted bindings ordered differently from their slot ids");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void duplicateBindingIdsAreRejected(GameTestHelper helper) {
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(
                        0, 1, id(1), 0, 0,
                        List.of(binding(2, 20, 0, 1, false), binding(2, 21, 1, 1, true))),
                "manifest accepted a reused binding lifecycle id");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void duplicatePresentTargetsAreRejected(GameTestHelper helper) {
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(
                        0, 1, id(1), 0, 0,
                        List.of(binding(2, 20, 0, 1, true), binding(3, 20, 1, 1, true))),
                "manifest accepted two present lifecycles for one target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void tombstoneAndNewPresentLifecycleMayShareTarget(GameTestHelper helper) {
        DynamicItemSlotManifest manifest = new DynamicItemSlotManifest(
                8,
                9,
                id(1),
                4,
                12,
                List.of(
                        binding(2, 20, 12, 9, false),
                        binding(3, 20, 21, 9, true)));

        helper.assertTrue(manifest.bindings().getFirst().tombstone(), "old target lifecycle was not a tombstone");
        helper.assertTrue(manifest.bindings().get(1).present(), "new target lifecycle was not present");
        helper.assertTrue(manifest.totalSlotCount() == 30, "shared target lifecycles did not reserve distinct ranges");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void epochsMustAdvanceOneAtATime(GameTestHelper helper) {
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(5, 7, id(1), 0, 0, List.of()),
                "manifest accepted a skipped epoch");
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(-1, 0, id(1), 0, 0, List.of()),
                "manifest accepted a negative previous epoch");
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(Long.MAX_VALUE, Long.MIN_VALUE, id(1), 0, 0, List.of()),
                "manifest accepted an epoch overflow");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void bindingAndTotalSlotLimitsAreEnforced(GameTestHelper helper) {
        new DynamicItemSlotDefinition(id(1), DynamicItemSlotDefinition.MAX_SLOT_COUNT);
        new DynamicItemSlotManifest(
                0,
                1,
                id(2),
                0,
                DynamicItemSlotManifest.MAX_TOTAL_SLOT_COUNT - DynamicItemSlotDefinition.MAX_SLOT_COUNT,
                List.of(binding(
                        3,
                        1,
                        DynamicItemSlotManifest.MAX_TOTAL_SLOT_COUNT - DynamicItemSlotDefinition.MAX_SLOT_COUNT,
                        DynamicItemSlotDefinition.MAX_SLOT_COUNT,
                        true)));

        assertIllegalArgument(helper,
                () -> new DynamicItemSlotDefinition(id(1), 0),
                "definition accepted zero slots");
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotDefinition(id(1), DynamicItemSlotDefinition.MAX_SLOT_COUNT + 1),
                "definition accepted more than 256 slots");
        assertIllegalArgument(helper,
                () -> binding(3, 1, -1, 1, true),
                "binding accepted a negative first slot id");
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(
                        0,
                        1,
                        id(2),
                        0,
                        DynamicItemSlotManifest.MAX_TOTAL_SLOT_COUNT - DynamicItemSlotDefinition.MAX_SLOT_COUNT + 1,
                        List.of(binding(
                                3,
                                1,
                                DynamicItemSlotManifest.MAX_TOTAL_SLOT_COUNT -
                                        DynamicItemSlotDefinition.MAX_SLOT_COUNT + 1,
                                DynamicItemSlotDefinition.MAX_SLOT_COUNT,
                                true))),
                "manifest accepted more than Short.MAX_VALUE total slots");
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(0, 1, id(2), -1, 0, List.of()),
                "manifest accepted a negative source revision");
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(0, 1, id(2), 0, -1, List.of()),
                "manifest accepted a negative base slot count");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void bindingCountIsBoundedBeforeDecodeAllocation(GameTestHelper helper) {
        List<DynamicItemSlotBinding> maximumBindings = new ArrayList<>(DynamicItemSlotManifest.MAX_BINDING_COUNT);
        for (int index = 0; index < DynamicItemSlotManifest.MAX_BINDING_COUNT; index++) {
            maximumBindings.add(binding(index + 1L, index + 5_000L, index, 1, true));
        }
        DynamicItemSlotManifest maximum = new DynamicItemSlotManifest(
                0, 1, id(10_000), 0, 0, maximumBindings);
        maximumBindings.clear();
        helper.assertTrue(maximum.bindings().size() == DynamicItemSlotManifest.MAX_BINDING_COUNT,
                "manifest did not snapshot its ordered binding list");

        List<DynamicItemSlotBinding> oversized = new ArrayList<>(DynamicItemSlotManifest.MAX_BINDING_COUNT + 1);
        for (int index = 0; index <= DynamicItemSlotManifest.MAX_BINDING_COUNT; index++) {
            oversized.add(binding(index + 1L, index + 10_000L, index, 1, true));
        }
        assertIllegalArgument(helper,
                () -> new DynamicItemSlotManifest(0, 1, id(20_000), 0, 0, oversized),
                "manifest accepted more than 4096 bindings");

        assertDecodeBindingCountRejected(helper, -1, "manifest codec accepted a negative binding count");
        assertDecodeBindingCountRejected(helper, DynamicItemSlotManifest.MAX_BINDING_COUNT + 1,
                "manifest codec accepted an oversized binding count");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void manifestSequenceRetainsRangesAndAppendsNewLifecycles(GameTestHelper helper) {
        UUID firstTarget = id(40);
        UUID secondTarget = id(41);
        UUID thirdTarget = id(42);
        int baseSlotCount = 36;

        DynamicItemSlotManifest initial = DynamicItemSlotManifestSequence.advance(
                Optional.empty(), 5, baseSlotCount,
                List.of(new DynamicItemSlotDefinition(firstTarget, 2),
                        new DynamicItemSlotDefinition(secondTarget, 1)));
        DynamicItemSlotManifest extended = DynamicItemSlotManifestSequence.advance(
                Optional.of(initial), 6, baseSlotCount,
                List.of(new DynamicItemSlotDefinition(secondTarget, 1),
                        new DynamicItemSlotDefinition(firstTarget, 2),
                        new DynamicItemSlotDefinition(thirdTarget, 3)));

        helper.assertTrue(extended.previousEpoch() == initial.epoch() && extended.epoch() == initial.epoch() + 1,
                "manifest sequence did not advance exactly one epoch");
        helper.assertTrue(extended.bindings().subList(0, 2).equals(initial.bindings()),
                "source reordering changed an existing lifecycle range");
        DynamicItemSlotBinding thirdBinding = extended.bindings().get(2);
        helper.assertTrue(thirdBinding.targetId().equals(thirdTarget) && thirdBinding.firstSlotId() == 39 &&
                thirdBinding.slotCount() == 3 && thirdBinding.present(),
                "new target lifecycle was not appended after the retained ranges");
        helper.assertTrue(extended.totalSlotCount() == 42,
                "extended manifest reported the wrong cumulative slot count");

        DynamicItemSlotManifest reshaped = DynamicItemSlotManifestSequence.advance(
                Optional.of(extended), 7, baseSlotCount,
                List.of(new DynamicItemSlotDefinition(thirdTarget, 3),
                        new DynamicItemSlotDefinition(firstTarget, 4)));
        DynamicItemSlotBinding oldFirstBinding = reshaped.bindings().get(0);
        DynamicItemSlotBinding removedSecondBinding = reshaped.bindings().get(1);
        DynamicItemSlotBinding retainedThirdBinding = reshaped.bindings().get(2);
        DynamicItemSlotBinding replacementFirstBinding = reshaped.bindings().get(3);
        helper.assertTrue(oldFirstBinding.tombstone() && removedSecondBinding.tombstone(),
                "removed or reshaped lifecycles did not become tombstones");
        helper.assertTrue(retainedThirdBinding.equals(thirdBinding),
                "unchanged target lifecycle moved while other targets changed");
        helper.assertTrue(replacementFirstBinding.present() && replacementFirstBinding.targetId().equals(firstTarget) &&
                !replacementFirstBinding.bindingId().equals(oldFirstBinding.bindingId()) &&
                replacementFirstBinding.firstSlotId() == 42 && replacementFirstBinding.slotCount() == 4,
                "reshaped target reused its old lifecycle instead of appending a replacement");

        DynamicItemSlotManifest rebuilt = DynamicItemSlotManifestSequence.advance(
                Optional.of(reshaped), 8, baseSlotCount,
                List.of(new DynamicItemSlotDefinition(secondTarget, 1),
                        new DynamicItemSlotDefinition(firstTarget, 4),
                        new DynamicItemSlotDefinition(thirdTarget, 3)));
        DynamicItemSlotBinding rebuiltSecondBinding = rebuilt.bindings().get(4);
        helper.assertTrue(rebuilt.bindings().subList(0, 4).equals(reshaped.bindings()) &&
                rebuiltSecondBinding.targetId().equals(secondTarget) && rebuiltSecondBinding.present() &&
                !rebuiltSecondBinding.bindingId().equals(removedSecondBinding.bindingId()) &&
                rebuiltSecondBinding.firstSlotId() == 46 && rebuilt.totalSlotCount() == 47,
                "rebuilt target did not append a fresh lifecycle after every retained range");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void manifestSequenceRejectsInvalidSourceSnapshots(GameTestHelper helper) {
        DynamicItemSlotManifest initial = DynamicItemSlotManifestSequence.advance(
                Optional.empty(), 5, 36,
                List.of(new DynamicItemSlotDefinition(id(50), 2)));

        assertIllegalArgument(helper,
                () -> DynamicItemSlotManifestSequence.advance(
                        Optional.of(initial), 4, 36,
                        List.of(new DynamicItemSlotDefinition(id(50), 2))),
                "manifest sequence accepted a source revision rollback");
        assertIllegalArgument(helper,
                () -> DynamicItemSlotManifestSequence.advance(
                        Optional.of(initial), 6, 37,
                        List.of(new DynamicItemSlotDefinition(id(50), 2))),
                "manifest sequence accepted a changed fixed-slot prefix");
        assertIllegalArgument(helper,
                () -> DynamicItemSlotManifestSequence.advance(
                        Optional.of(initial), 6, 36,
                        List.of(new DynamicItemSlotDefinition(id(50), 2),
                                new DynamicItemSlotDefinition(id(50), 2))),
                "manifest sequence accepted duplicate logical targets");
        helper.succeed();
    }

    private static void assertDecodeBindingCountRejected(GameTestHelper helper, int bindingCount, String message) {
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        try {
            buffer.writeVarLong(0);
            buffer.writeVarLong(1);
            buffer.writeUUID(id(30_000));
            buffer.writeVarLong(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(bindingCount);
            assertIllegalArgument(helper, () -> DynamicItemSlotManifest.STREAM_CODEC.decode(buffer), message);
        } finally {
            buffer.release();
        }
    }

    private static DynamicItemSlotBinding binding(long bindingId, long targetId, int firstSlotId, int slotCount,
                                                  boolean present) {
        return new DynamicItemSlotBinding(id(bindingId), id(targetId), firstSlotId, slotCount, present);
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }

    private static RegistryFriendlyByteBuf newBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.OTHER);
    }

    private static void assertIllegalArgument(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank(),
                    message + ": exception did not explain the rejected invariant");
            return;
        }
        throw new GameTestAssertException(message);
    }
}
