package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.sync_system.codecs.MonitorGroupCodec;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MonitorGroupCodecTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MonitorGroupCodec")
    public static void identityPersistsAndLegacyGroupsReceiveOne(GameTestHelper helper) {
        ContextualFieldCodec.Context<MonitorGroup> context = context(helper);
        MonitorGroup first = new MonitorGroup("first");
        MonitorGroup second = new MonitorGroup("second");

        helper.assertTrue(!first.getIdentity().equals(second.getIdentity()),
                "new monitor groups reused the same identity");

        JsonObject encoded = MonitorGroupCodec.INSTANCE.serializeField(first, context).getAsJsonObject();
        MonitorGroup decoded = decode(encoded, context);
        helper.assertTrue(decoded.getIdentity().equals(first.getIdentity()),
                "monitor group identity changed during codec round-trip");

        JsonObject legacyJson = encoded.deepCopy();
        legacyJson.remove("identity");
        MonitorGroup legacyDecoded = decode(legacyJson, context);
        helper.assertTrue(!legacyDecoded.getIdentity().equals(first.getIdentity()),
                "legacy monitor group reused the identity removed from its payload");

        JsonObject migratedJson = MonitorGroupCodec.INSTANCE.serializeField(legacyDecoded, context).getAsJsonObject();
        helper.assertTrue(migratedJson.has("identity"),
                "re-serialized legacy monitor group omitted its generated identity");
        helper.assertTrue(decode(migratedJson, context).getIdentity().equals(legacyDecoded.getIdentity()),
                "generated legacy monitor group identity did not persist");

        JsonObject malformedJson = encoded.deepCopy();
        malformedJson.add("identity", new JsonPrimitive("malformed"));
        boolean malformedRejected = false;
        try {
            MonitorGroupCodec.INSTANCE.deserializeField(malformedJson, context);
        } catch (RuntimeException expected) {
            malformedRejected = true;
        }
        helper.assertTrue(malformedRejected, "monitor group codec accepted a malformed identity");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MonitorGroupCodec")
    public static void moduleSlotIncarnationPersistsAndTracksPhysicalReplacement(GameTestHelper helper) {
        ContextualFieldCodec.Context<MonitorGroup> context = context(helper);
        MonitorGroup group = new MonitorGroup("module-slot");
        MonitorGroup other = new MonitorGroup("other-module-slot");
        helper.assertTrue(!group.getModuleSlotIncarnation().equals(other.getModuleSlotIncarnation()),
                "new monitor groups reused the same module-slot incarnation");

        JsonObject encoded = MonitorGroupCodec.INSTANCE.serializeField(group, context).getAsJsonObject();
        MonitorGroup decoded = decode(encoded, context);
        helper.assertTrue(decoded.getModuleSlotIncarnation().equals(group.getModuleSlotIncarnation()),
                "module-slot incarnation changed during codec round-trip");

        ItemStack module = GTItems.IMAGE_MODULE.get().getDefaultInstance();
        UUID decodedIncarnation = decoded.getModuleSlotIncarnation();
        decoded.getItemStackHandler().setStackInSlot(0, module.copy());
        helper.assertTrue(decoded.getModuleSlotIncarnation().equals(decodedIncarnation),
                "decoded client-side handler installed an authoritative replacement listener");

        JsonObject legacyJson = encoded.deepCopy();
        legacyJson.remove("moduleSlotIncarnation");
        MonitorGroup legacyDecoded = decode(legacyJson, context);
        helper.assertTrue(!legacyDecoded.getModuleSlotIncarnation().equals(group.getModuleSlotIncarnation()),
                "legacy monitor group reused the removed module-slot incarnation");
        JsonObject migratedJson = MonitorGroupCodec.INSTANCE.serializeField(legacyDecoded, context).getAsJsonObject();
        helper.assertTrue(migratedJson.has("moduleSlotIncarnation"),
                "re-serialized legacy monitor group omitted its generated module-slot incarnation");
        helper.assertTrue(decode(migratedJson, context).getModuleSlotIncarnation()
                .equals(legacyDecoded.getModuleSlotIncarnation()),
                "generated legacy module-slot incarnation did not persist");

        JsonObject malformedJson = encoded.deepCopy();
        malformedJson.add("moduleSlotIncarnation", new JsonPrimitive("malformed"));
        boolean malformedRejected = false;
        try {
            MonitorGroupCodec.INSTANCE.deserializeField(malformedJson, context);
        } catch (RuntimeException expected) {
            malformedRejected = true;
        }
        helper.assertTrue(malformedRejected, "monitor group codec accepted a malformed module-slot incarnation");

        CustomItemStackHandler handler = group.getItemStackHandler();
        handler.setOnContentsChanged(group::rotateModuleSlotIncarnation);
        assertRotated(helper, group, () -> handler.setStackInSlot(0, module.copy()),
                "setting the module slot");
        assertRotated(helper, group, () -> handler.setStackInSlot(0, module.copy()),
                "byte-identical module replacement");

        UUID beforeConfiguration = group.getModuleSlotIncarnation();
        handler.getStackInSlot(0).set(DataComponents.CUSTOM_NAME, Component.literal("configured"));
        helper.assertTrue(group.getModuleSlotIncarnation().equals(beforeConfiguration),
                "in-place module configuration rotated the physical slot incarnation");

        UUID beforeCapacityQueries = group.getModuleSlotIncarnation();
        int directCapacity = handler.getMaxStackSizeForEmptySlot(0, module);
        int gtItemSlotCapacity = new GTItemSlotElement(handler, 0).getSlot().getMaxStackSize(module);
        helper.assertTrue(directCapacity > 0 && directCapacity == gtItemSlotCapacity,
                "handler and GT item slot capacity queries disagreed on the accepted empty-slot capacity");
        helper.assertTrue(group.getModuleSlotIncarnation().equals(beforeCapacityQueries),
                "slot capacity query triggered a physical module replacement");

        assertRotated(helper, group, handler::clear, "clearing the module slot");
        assertRotated(helper, group, () -> handler.insertItem(0, module.copy(), false),
                "inserting a module");
        assertRotated(helper, group, () -> handler.extractItem(0, 1, false),
                "extracting a module");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MonitorGroupCodec")
    public static void dynamicItemSlotIncarnationPersistsAcrossSaveAndSyncCodec(GameTestHelper helper) {
        ContextualFieldCodec.Context<MonitorGroup> context = context(helper);
        MonitorGroup group = new MonitorGroup("dynamic-item-slot");
        MonitorGroup other = new MonitorGroup("other-dynamic-item-slot");
        helper.assertTrue(!group.getDynamicItemSlotIncarnation().equals(other.getDynamicItemSlotIncarnation()),
                "new monitor groups reused the same dynamic item-slot incarnation");

        JsonObject encoded = MonitorGroupCodec.INSTANCE.serializeField(group, context).getAsJsonObject();
        MonitorGroup decoded = decode(encoded, context);
        helper.assertTrue(decoded.getDynamicItemSlotIncarnation().equals(group.getDynamicItemSlotIncarnation()),
                "dynamic item-slot incarnation changed during codec round-trip");

        JsonObject legacyJson = encoded.deepCopy();
        legacyJson.remove("dynamicItemSlotIncarnation");
        MonitorGroup legacyDecoded = decode(legacyJson, context);
        helper.assertTrue(!legacyDecoded.getDynamicItemSlotIncarnation()
                .equals(group.getDynamicItemSlotIncarnation()),
                "legacy monitor group reused the removed dynamic item-slot incarnation");
        JsonObject migratedJson = MonitorGroupCodec.INSTANCE.serializeField(legacyDecoded, context).getAsJsonObject();
        helper.assertTrue(migratedJson.has("dynamicItemSlotIncarnation") &&
                decode(migratedJson, context).getDynamicItemSlotIncarnation()
                        .equals(legacyDecoded.getDynamicItemSlotIncarnation()),
                "generated legacy dynamic item-slot incarnation did not persist");

        JsonObject malformedJson = encoded.deepCopy();
        malformedJson.add("dynamicItemSlotIncarnation", new JsonPrimitive("malformed"));
        boolean malformedRejected = false;
        try {
            MonitorGroupCodec.INSTANCE.deserializeField(malformedJson, context);
        } catch (RuntimeException expected) {
            malformedRejected = true;
        }
        helper.assertTrue(malformedRejected,
                "monitor group codec accepted a malformed dynamic item-slot incarnation");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MonitorGroupCodec")
    public static void textConfigurationRevisionPersistsAndTracksConfigurationLifetime(GameTestHelper helper) {
        ContextualFieldCodec.Context<MonitorGroup> context = context(helper);
        MonitorGroup group = new MonitorGroup("text-revision");
        ItemStack module = GTItems.TEXT_MODULE.get().getDefaultInstance();
        group.getItemStackHandler().setStackInSlot(0, module);
        UUID slotIncarnation = group.getModuleSlotIncarnation();

        TextLineList configured = new TextLineList(List.of(Component.literal("configured")), 2.0f);
        group.applyTextConfiguration(configured);
        helper.assertTrue(group.getTextConfigurationRevision() == 1,
                "in-place text configuration did not increment its revision");
        helper.assertTrue(group.getModuleSlotIncarnation().equals(slotIncarnation),
                "in-place text configuration rotated the physical slot incarnation");

        JsonObject encoded = MonitorGroupCodec.INSTANCE.serializeField(group, context).getAsJsonObject();
        MonitorGroup decoded = decode(encoded, context);
        helper.assertTrue(decoded.getTextConfigurationRevision() == 1,
                "text configuration revision changed during codec round-trip");
        helper.assertTrue(configured.equals(decoded.getItemStackHandler().getStackInSlot(0)
                .get(GTDataComponents.FORMAT_STRING_LIST.get())),
                "text configuration changed during codec round-trip");

        group.setTextConfigurationRevision(Long.MAX_VALUE);
        JsonObject maximumRevision = MonitorGroupCodec.INSTANCE.serializeField(group, context).getAsJsonObject();
        helper.assertTrue(decode(maximumRevision, context).getTextConfigurationRevision() == Long.MAX_VALUE,
                "maximum text configuration revision did not survive codec round-trip");

        JsonObject legacyJson = encoded.deepCopy();
        legacyJson.remove("textConfigurationRevision");
        MonitorGroup legacyDecoded = decode(legacyJson, context);
        helper.assertTrue(legacyDecoded.getTextConfigurationRevision() == 0,
                "legacy monitor group did not default its text configuration revision to zero");
        helper.assertTrue(MonitorGroupCodec.INSTANCE.serializeField(legacyDecoded, context).getAsJsonObject()
                .has("textConfigurationRevision"),
                "re-serialized legacy monitor group omitted its text configuration revision");

        assertRevisionRejected(helper, encoded, context, new JsonPrimitive(-1),
                "monitor group codec accepted a negative text configuration revision");
        assertRevisionRejected(helper, encoded, context, new JsonPrimitive(0.5),
                "monitor group codec accepted a fractional text configuration revision");
        assertRevisionRejected(helper, encoded, context, new JsonPrimitive("1"),
                "monitor group codec accepted a string text configuration revision");
        assertRevisionRejected(helper, encoded, context,
                new JsonPrimitive(new BigInteger("9223372036854775808")),
                "monitor group codec accepted an overflowing text configuration revision");

        group.getItemStackHandler().setOnContentsChanged(group::rotateModuleSlotIncarnation);
        group.getItemStackHandler().setStackInSlot(0, module.copy());
        helper.assertTrue(group.getTextConfigurationRevision() == 0,
                "physical module replacement did not reset the text configuration revision");
        helper.assertTrue(!group.getModuleSlotIncarnation().equals(slotIncarnation),
                "physical module replacement did not rotate the slot incarnation");
        helper.succeed();
    }

    private static ContextualFieldCodec.Context<MonitorGroup> context(GameTestHelper helper) {
        return new ContextualFieldCodec.Context<>(
                new Object(), new TypeDeclaration(MonitorGroup.class), null, "monitorGroup", true, true,
                helper.getLevel().registryAccess(), SyncSerializationTarget.DATA_COMPONENTS);
    }

    private static void assertRotated(GameTestHelper helper, MonitorGroup group, Runnable replacement,
                                      String description) {
        UUID before = group.getModuleSlotIncarnation();
        replacement.run();
        helper.assertTrue(!group.getModuleSlotIncarnation().equals(before),
                description + " did not rotate the module-slot incarnation");
    }

    private static void assertRevisionRejected(GameTestHelper helper, JsonObject encoded,
                                               ContextualFieldCodec.Context<MonitorGroup> context,
                                               JsonPrimitive revision, String message) {
        JsonObject malformed = encoded.deepCopy();
        malformed.add("textConfigurationRevision", revision);
        boolean rejected = false;
        try {
            MonitorGroupCodec.INSTANCE.deserializeField(malformed, context);
        } catch (RuntimeException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, message);
    }

    private static MonitorGroup decode(JsonElement json, ContextualFieldCodec.Context<MonitorGroup> context) {
        MonitorGroup decoded = MonitorGroupCodec.INSTANCE.deserializeField(json, context);
        if (decoded == null) {
            throw new GameTestAssertException("monitor group codec returned null for an object payload");
        }
        return decoded;
    }
}
