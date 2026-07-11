package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToServer;
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.jetbrains.annotations.Nullable;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ServerFieldUpdateAnnotationTest {

    private static final String BATCH = "ServerFieldUpdateAnnotation";

    static {
        FieldCodecs.register(DecodeFailureValue.class, DecodeFailureValue.CODEC);
        FieldCodecs.registerContextual(ContextualOnlyValue.class, ContextualOnlyValueCodec.INSTANCE);
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void privatePrimitiveNormalizerAndChangedOnlyListenerUseOldAndCanonicalValues(
                                                                                                GameTestHelper helper) {
        PrimitiveNormalizerTarget target = new PrimitiveNormalizerTarget(5);
        RegistryAccess registries = helper.getLevel().registryAccess();

        ServerFieldUpdateResult changed = target.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload("value", new JsonPrimitive(15)));

        helper.assertTrue(changed.getAccepted(), "valid normalized primitive update was rejected");
        helper.assertTrue(changed.getChanged(), "canonical primitive update was not reported as changed");
        helper.assertTrue(target.value == 10, "private primitive normalizer did not clamp the candidate");
        helper.assertTrue(target.listenerCalls == 1, "changed primitive did not invoke its listener once");
        helper.assertTrue(target.listenerOldValue == 5 && target.listenerNewValue == 10,
                "listener did not receive the old and committed primitive values");

        ServerFieldUpdateResult unchanged = target.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload("value", new JsonPrimitive(99)));

        helper.assertTrue(unchanged.getAccepted(), "valid no-op canonical update was rejected");
        helper.assertTrue(!unchanged.getChanged(), "no-op canonical update was reported as changed");
        helper.assertTrue(target.value == 10, "no-op candidate did not retain the canonical value");
        helper.assertTrue(target.listenerCalls == 1, "no-op canonical update invoked the change listener");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void laterDecodeFailureRejectsBatchBeforeNormalizationOrCommit(GameTestHelper helper) {
        DecodeAtomicTarget target = new DecodeAtomicTarget(1, new DecodeFailureValue("old"));
        SyncFieldData fields = SyncFieldData.builder()
                .put(SyncFieldData.key("first"), new JsonPrimitive(7))
                .put(SyncFieldData.key("second"), new JsonPrimitive("reject"))
                .build();

        ServerFieldUpdateResult result = target.getSyncDataHolder().tryApplyServerNetworkUpdate(
                helper.getLevel().registryAccess(), payload(fields));

        helper.assertTrue(!result.getAccepted(), "batch with a failing second decode was accepted");
        helper.assertTrue(target.first == 1, "first field was committed before the second field decoded");
        helper.assertTrue(target.second.equals(new DecodeFailureValue("old")),
                "failing decoded field changed the holder");
        helper.assertTrue(target.listenerCalls == 0, "rejected decode batch invoked a listener");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void laterNormalizerFailureRejectsBatchBeforeCommit(GameTestHelper helper) {
        NormalizerAtomicTarget target = new NormalizerAtomicTarget(1, 2);
        SyncFieldData fields = SyncFieldData.builder()
                .put(SyncFieldData.key("first"), new JsonPrimitive(10))
                .put(SyncFieldData.key("second"), new JsonPrimitive(20))
                .build();

        ServerFieldUpdateResult result = target.getSyncDataHolder().tryApplyServerNetworkUpdate(
                helper.getLevel().registryAccess(), payload(fields));

        helper.assertTrue(!result.getAccepted(), "batch with a failing normalizer was accepted");
        helper.assertTrue(target.first == 1 && target.second == 2,
                "normalizer failure left a partially committed batch");
        helper.assertTrue(target.listenerCalls == 0, "rejected normalization batch invoked a listener");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void contextualOnlyServerCandidateRejectsCompleteBatch(GameTestHelper helper) {
        ContextualOnlyTarget target = new ContextualOnlyTarget(1, new ContextualOnlyValue("old"));
        SyncFieldData fields = SyncFieldData.builder()
                .put(SyncFieldData.key("first"), new JsonPrimitive(7))
                .put(SyncFieldData.key("second"), new JsonPrimitive("new"))
                .build();

        ServerFieldUpdateResult result = target.getSyncDataHolder().tryApplyServerNetworkUpdate(
                helper.getLevel().registryAccess(), payload(fields));

        helper.assertTrue(!result.getAccepted(), "contextual-only C2S candidate was accepted");
        helper.assertTrue(target.first == 1, "ordinary field committed before contextual-only rejection");
        helper.assertTrue(target.second.equals(new ContextualOnlyValue("old")),
                "contextual-only decoder mutated the server field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void nullContextualOnlyClientCandidateStillRequiresOrdinaryCodec(GameTestHelper helper) {
        NullableContextualOnlyTarget target = new NullableContextualOnlyTarget(new ContextualOnlyValue("initial"));
        target.value = null;

        try {
            target.getSyncDataHolder().collectServerNetworkChanges(helper.getLevel().registryAccess());
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage() != null &&
                    exception.getMessage().contains("requires a detached ordinary Codec"),
                    "null contextual-only C2S candidate failed for an unexpected reason");
            helper.succeed();
            return;
        }
        throw new GameTestAssertException("null contextual-only C2S candidate bypassed ordinary codec validation");
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidServerAnnotationMetadataFailsFast(GameTestHelper helper) {
        assertMetadataRejected(helper, "unknown normalizer target", UnknownNormalizerTarget.class);
        assertMetadataRejected(helper, "duplicate normalizers", DuplicateNormalizerTarget.class);
        assertMetadataRejected(helper, "static normalizer", StaticNormalizerTarget.class);
        assertMetadataRejected(helper, "invalid normalizer signature", InvalidNormalizerSignatureTarget.class);
        assertMetadataRejected(helper, "invalid listener signature", InvalidListenerSignatureTarget.class);
        assertMetadataRejected(helper, "normalizer on SyncToServer-only field",
                SyncToServerNormalizerTarget.class);
        assertMetadataRejected(helper, "final server field", FinalServerFieldTarget.class);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void listenerFailureDoesNotRejectCommittedBatchOrSkipLaterListeners(GameTestHelper helper) {
        ListenerFailureTarget target = new ListenerFailureTarget(1, 2);
        SyncFieldData fields = SyncFieldData.builder()
                .put(SyncFieldData.key("first"), new JsonPrimitive(10))
                .put(SyncFieldData.key("second"), new JsonPrimitive(20))
                .build();

        ServerFieldUpdateResult result = target.getSyncDataHolder().tryApplyServerNetworkUpdate(
                helper.getLevel().registryAccess(), payload(fields));

        helper.assertTrue(result.getAccepted() && result.getChanged(),
                "listener exception rejected an otherwise valid batch");
        helper.assertTrue(target.first == 10 && target.second == 20,
                "listener exception rolled back committed field values");
        helper.assertTrue(target.firstListenerCalls == 1, "throwing listener was not invoked once");
        helper.assertTrue(target.firstListenerSawCommittedSecond,
                "listener ran before the complete field batch was committed");
        helper.assertTrue(target.secondListenerCalls == 1,
                "throwing listener prevented a later field listener from running");
        helper.assertTrue(target.secondListenerOldValue == 2 && target.secondListenerNewValue == 20,
                "later listener received incorrect old or committed values");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void syncBothNoOpNormalizationStillAcksAndCanonicalAckDoesNotEcho(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        SyncBothTarget server = new SyncBothTarget(10);
        SyncBothTarget client = new SyncBothTarget(10);

        server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.value = 20;
        DataComponentMap request = client.getSyncDataHolder().collectServerNetworkChanges(registries);
        helper.assertTrue(!request.isEmpty(), "client did not emit its SyncBoth candidate");

        ServerFieldUpdateResult result = server.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, request);
        helper.assertTrue(result.getAccepted(), "valid SyncBoth candidate was rejected");
        helper.assertTrue(!result.getChanged(), "candidate normalized to the old server value was marked changed");
        helper.assertTrue(server.value == 10, "server did not retain the canonical SyncBoth value");
        helper.assertTrue(server.markedChanged, "canonical SyncBoth acknowledgement was not requested");

        helper.assertTrue(server.getSyncDataHolder().scanAndMarkChanges(registries),
                "unchanged canonical SyncBoth value did not force an S2C acknowledgement");
        DataComponentMap acknowledgement = server.getSyncDataHolder().collectClientNetworkChanges(registries, false);
        SyncFieldData acknowledgementFields = acknowledgement.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(acknowledgementFields != null, "canonical acknowledgement omitted sync field data");
        helper.assertTrue(acknowledgementFields.get(SyncFieldData.key("value")).getAsInt() == 10,
                "canonical acknowledgement did not contain the authoritative server value");

        client.getSyncDataHolder().applyClientNetworkUpdate(registries, acknowledgement);
        helper.assertTrue(client.value == 10, "client did not apply the canonical server acknowledgement");
        helper.assertTrue(client.getSyncDataHolder().collectServerNetworkChanges(registries).isEmpty(),
                "client echoed the canonical SyncBoth acknowledgement back to the server");
        helper.succeed();
    }

    private static DataComponentMap payload(String fieldName, JsonElement value) {
        return payload(SyncFieldData.builder()
                .put(SyncFieldData.key(fieldName), value)
                .build());
    }

    private static DataComponentMap payload(SyncFieldData fields) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .build();
    }

    private static void assertMetadataRejected(GameTestHelper helper, String name, Class<?> targetClass) {
        try {
            ClassSyncData.getClassData(targetClass);
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank(),
                    name + " failed without an explanatory message");
            return;
        }
        throw new GameTestAssertException(name + " was accepted");
    }

    private abstract static class TestSyncTarget implements ISyncManaged {

        private SyncDataHolder syncDataHolder;
        protected boolean markedChanged;

        protected final void initializeSyncDataHolder() {
            syncDataHolder = new SyncDataHolder(this);
        }

        @Override
        public final SyncDataHolder getSyncDataHolder() {
            return syncDataHolder;
        }

        @Override
        public final @Nullable ISyncManaged getParentSyncObject() {
            return null;
        }

        @Override
        public final void markAsChanged() {
            markedChanged = true;
        }

        @Override
        public final void scheduleRenderUpdate() {}
    }

    private static final class PrimitiveNormalizerTarget extends TestSyncTarget {

        @SyncBoth
        private int value;
        private int listenerCalls;
        private int listenerOldValue;
        private int listenerNewValue;

        private PrimitiveNormalizerTarget(int value) {
            this.value = value;
            initializeSyncDataHolder();
        }

        @ServerFieldNormalizer(fieldName = "value")
        private int normalizeValue(int candidate) {
            return Math.clamp(candidate, 0, 10);
        }

        @ServerFieldChangeListener(fieldName = "value")
        private void onValueChanged(int oldValue, int newValue) {
            listenerCalls++;
            listenerOldValue = oldValue;
            listenerNewValue = newValue;
        }
    }

    private static final class DecodeAtomicTarget extends TestSyncTarget {

        @SyncBoth
        private int first;
        @SyncToServer
        private DecodeFailureValue second;
        private int listenerCalls;

        private DecodeAtomicTarget(int first, DecodeFailureValue second) {
            this.first = first;
            this.second = second;
            initializeSyncDataHolder();
        }

        @ServerFieldNormalizer(fieldName = "first")
        private int normalizeFirst(int candidate) {
            return candidate;
        }

        @ServerFieldChangeListener(fieldName = "first")
        private void onFirstChanged(int oldValue, int newValue) {
            listenerCalls++;
        }
    }

    private static final class NormalizerAtomicTarget extends TestSyncTarget {

        @SyncBoth
        private int first;
        @SyncBoth
        private int second;
        private int listenerCalls;

        private NormalizerAtomicTarget(int first, int second) {
            this.first = first;
            this.second = second;
            initializeSyncDataHolder();
        }

        @ServerFieldNormalizer(fieldName = "first")
        private int normalizeFirst(int candidate) {
            return candidate;
        }

        @ServerFieldNormalizer(fieldName = "second")
        private int normalizeSecond(int candidate) {
            throw new IllegalArgumentException("intentional normalizer failure");
        }

        @ServerFieldChangeListener(fieldName = "first")
        private void onFirstChanged(int oldValue, int newValue) {
            listenerCalls++;
        }
    }

    private static final class ContextualOnlyTarget extends TestSyncTarget {

        @SyncToServer
        private int first;
        @SyncToServer
        private ContextualOnlyValue second;

        private ContextualOnlyTarget(int first, ContextualOnlyValue second) {
            this.first = first;
            this.second = second;
            initializeSyncDataHolder();
        }
    }

    private static final class NullableContextualOnlyTarget extends TestSyncTarget {

        @SyncToServer
        private @Nullable ContextualOnlyValue value;

        private NullableContextualOnlyTarget(ContextualOnlyValue value) {
            this.value = value;
            initializeSyncDataHolder();
        }
    }

    private static final class ListenerFailureTarget extends TestSyncTarget {

        @SyncToServer
        private int first;
        @SyncToServer
        private int second;
        private int firstListenerCalls;
        private int secondListenerCalls;
        private int secondListenerOldValue;
        private int secondListenerNewValue;
        private boolean firstListenerSawCommittedSecond;

        private ListenerFailureTarget(int first, int second) {
            this.first = first;
            this.second = second;
            initializeSyncDataHolder();
        }

        @ServerFieldChangeListener(fieldName = "first")
        private void onFirstChanged(int oldValue, int newValue) {
            firstListenerCalls++;
            firstListenerSawCommittedSecond = second == 20;
            throw new IllegalStateException("intentional listener failure");
        }

        @ServerFieldChangeListener(fieldName = "second")
        private void onSecondChanged(int oldValue, int newValue) {
            secondListenerCalls++;
            secondListenerOldValue = oldValue;
            secondListenerNewValue = newValue;
        }
    }

    private static final class SyncBothTarget extends TestSyncTarget {

        @SyncBoth
        private int value;

        private SyncBothTarget(int value) {
            this.value = value;
            initializeSyncDataHolder();
        }

        @ServerFieldNormalizer(fieldName = "value")
        private int normalizeValue(int candidate) {
            return Math.min(candidate, 10);
        }
    }

    private static final class UnknownNormalizerTarget extends TestSyncTarget {

        @ServerFieldNormalizer(fieldName = "missing")
        private int normalizeMissing(int candidate) {
            return candidate;
        }
    }

    private static final class DuplicateNormalizerTarget extends TestSyncTarget {

        @SyncBoth
        private int value;

        @ServerFieldNormalizer(fieldName = "value")
        private int normalizeFirst(int candidate) {
            return candidate;
        }

        @ServerFieldNormalizer(fieldName = "value")
        private int normalizeSecond(int candidate) {
            return candidate;
        }
    }

    private static final class StaticNormalizerTarget extends TestSyncTarget {

        @SyncBoth
        private int value;

        @ServerFieldNormalizer(fieldName = "value")
        private static int normalizeValue(int candidate) {
            return candidate;
        }
    }

    private static final class InvalidNormalizerSignatureTarget extends TestSyncTarget {

        @SyncBoth
        private int value;

        @ServerFieldNormalizer(fieldName = "value")
        private long normalizeValue(int candidate) {
            return candidate;
        }
    }

    private static final class InvalidListenerSignatureTarget extends TestSyncTarget {

        @SyncToServer
        private int value;

        @ServerFieldChangeListener(fieldName = "value")
        private void onValueChanged(long oldValue, long newValue) {}
    }

    private static final class SyncToServerNormalizerTarget extends TestSyncTarget {

        @SyncToServer
        private int value;

        @ServerFieldNormalizer(fieldName = "value")
        private int normalizeValue(int candidate) {
            return candidate;
        }
    }

    private static final class FinalServerFieldTarget extends TestSyncTarget {

        @SyncToServer
        private final int value = 1;
    }

    private record DecodeFailureValue(String value) {

        private static final Codec<DecodeFailureValue> CODEC = Codec.STRING.comapFlatMap(
                value -> DataResult.error(() -> "intentional decode failure"),
                DecodeFailureValue::value);
    }

    private record ContextualOnlyValue(String value) {}

    private enum ContextualOnlyValueCodec implements ContextualFieldCodec<ContextualOnlyValue> {

        INSTANCE;

        @Override
        public JsonElement serializeField(ContextualOnlyValue value, Context<ContextualOnlyValue> context) {
            return new JsonPrimitive(value.value);
        }

        @Override
        public ContextualOnlyValue deserializeField(JsonElement value, Context<ContextualOnlyValue> context) {
            return new ContextualOnlyValue(value.getAsString());
        }
    }
}
