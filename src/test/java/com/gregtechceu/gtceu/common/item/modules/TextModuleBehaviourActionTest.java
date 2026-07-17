package com.gregtechceu.gtceu.common.item.modules;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorTextModuleActions;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.CodeEditor;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.serialization.JsonOps;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class TextModuleBehaviourActionTest {

    private static final String BATCH = "TextModuleBehaviourAction";
    private static final long OPENING_REVISION = 7;
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_central_monitor_text_module_configuration");
    private static final ResourceLocation HOLDER_INCARNATION_FIELD = SyncFieldData.key("holder_incarnation");
    private static final ResourceLocation GROUP_IDENTITY_FIELD = SyncFieldData.key("group_identity");
    private static final ResourceLocation MODULE_SLOT_INCARNATION_FIELD = SyncFieldData
            .key("module_slot_incarnation");
    private static final ResourceLocation EXPECTED_CONFIGURATION_REVISION_FIELD = SyncFieldData
            .key("expected_configuration_revision");
    private static final ResourceLocation REQUESTED_CONFIGURATION_FIELD = SyncFieldData.key("requested_configuration");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2ControlsDispatchOpeningActionThroughRealClick(GameTestHelper helper) {
        TextEditorFixture fixture = createFixture();
        List<UIElement> children = fixture.root().getChildren();
        helper.assertTrue(children.size() == 4,
                "Text module LDLib2 root did not retain its four direct children");
        helper.assertTrue(children.get(0) instanceof CodeEditor &&
                children.get(1) instanceof GTButtonElement &&
                children.get(2) instanceof GTTextFieldElement &&
                children.get(3).getChildren().size() == 2,
                "Text module LDLib2 root changed its editor, save, scale, reference child order");

        EditorControls controls = controls(fixture.root());
        controls.editor().setLines(List.of("updated"));
        click(controls.saveButton());

        helper.assertTrue(fixture.actions().size() == 1,
                "Text module LDLib2 save button did not emit exactly one action");
        SyncActionData action = fixture.actions().getFirst();
        helper.assertTrue(action.actionId().equals(ACTION_ID) && action.sequence() == 0,
                "Text module LDLib2 save emitted the wrong action id or opening sequence");
        helper.assertTrue(configuration("updated", 1.0f).equals(requestedConfiguration(action)),
                "Text module LDLib2 editor lines were not encoded as the requested configuration");
        helper.assertTrue(uuidField(action, HOLDER_INCARNATION_FIELD).equals(fixture.holderIncarnation()) &&
                uuidField(action, GROUP_IDENTITY_FIELD).equals(fixture.groupIdentity()) &&
                uuidField(action, MODULE_SLOT_INCARNATION_FIELD).equals(fixture.moduleSlotIncarnation()),
                "Text module LDLib2 save did not retain its opening holder, group, and module-slot identities");
        assertExpectedSnapshot(helper, action, fixture.openingSnapshot(),
                "Text module LDLib2 save did not carry its opening module snapshot");
        helper.assertTrue(expectedRevision(action) == OPENING_REVISION,
                "Text module LDLib2 opening save did not carry the synchronized revision");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void pageRetainsOpeningSnapshotAfterSharedGroupChanges(GameTestHelper helper) {
        TextEditorFixture fixture = createFixture();
        EditorControls controls = controls(fixture.root());
        TextLineList concurrentConfiguration = configuration("concurrent", 2.0f);
        fixture.group().applyTextConfiguration(concurrentConfiguration);

        controls.editor().setLines(List.of("requested"));
        click(controls.saveButton());

        helper.assertTrue(fixture.actions().size() == 1,
                "Text module page did not emit its opening-scoped action after a shared group change");
        SyncActionData action = fixture.actions().getFirst();
        assertExpectedSnapshot(helper, action, fixture.openingSnapshot(),
                "Text module page replaced its opening snapshot with concurrent shared state");
        helper.assertTrue(expectedRevision(action) == OPENING_REVISION,
                "Text module page replaced its opening revision with the concurrent revision");
        helper.assertTrue(concurrentConfiguration.equals(
                fixture.sharedModule().get(GTDataComponents.FORMAT_STRING_LIST.get())) &&
                fixture.group().getTextConfigurationRevision() == OPENING_REVISION + 1,
                "Text module page changed the concurrently synchronized shared group state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void unacknowledgedSavesRemainRetryableAgainstAuthoritativeState(GameTestHelper helper) {
        TextEditorFixture fixture = createFixture();
        EditorControls controls = controls(fixture.root());
        TextLineList firstRequest = configuration("first", 1.0f);
        TextLineList secondRequest = configuration("second", 1.0f);

        controls.editor().setLines(List.of("first"));
        click(controls.saveButton());
        click(controls.saveButton());
        helper.assertTrue(configuration("opening", 1.0f).equals(
                fixture.sharedModule().get(GTDataComponents.FORMAT_STRING_LIST.get())),
                "First text module save optimistically changed the shared module stack");

        controls.editor().setLines(List.of("second"));
        click(controls.saveButton());

        helper.assertTrue(fixture.actions().size() == 3,
                "Text module page could not retry an unacknowledged save before changing its draft");
        SyncActionData firstAction = fixture.actions().get(0);
        SyncActionData retryAction = fixture.actions().get(1);
        SyncActionData secondAction = fixture.actions().get(2);
        helper.assertTrue(firstAction.sequence() == 0 && retryAction.sequence() == 1 &&
                secondAction.sequence() == 2,
                "Text module page did not advance action sequence across retry and changed draft");
        helper.assertTrue(expectedRevision(firstAction) == OPENING_REVISION &&
                expectedRevision(retryAction) == OPENING_REVISION &&
                expectedRevision(secondAction) == OPENING_REVISION,
                "Text module page used an unacknowledged revision as its CAS baseline");
        assertExpectedSnapshot(helper, firstAction, fixture.openingSnapshot(),
                "First text module save did not use the opening snapshot");
        assertExpectedSnapshot(helper, retryAction, fixture.openingSnapshot(),
                "Retried text module save stopped using the last authoritative snapshot");
        assertExpectedSnapshot(helper, secondAction, fixture.openingSnapshot(),
                "Second text module save stopped using the last authoritative snapshot");
        helper.assertTrue(firstRequest.equals(requestedConfiguration(firstAction)) &&
                firstRequest.equals(requestedConfiguration(retryAction)) &&
                secondRequest.equals(requestedConfiguration(secondAction)),
                "Text module page did not preserve both requested configurations");
        helper.assertTrue(configuration("opening", 1.0f).equals(
                fixture.sharedModule().get(GTDataComponents.FORMAT_STRING_LIST.get())) &&
                fixture.group().getTextConfigurationRevision() == OPENING_REVISION,
                "Text module page optimistically changed shared stack or revision state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2ScaleValidationRejectsRawErrorsAndAcceptsBounds(GameTestHelper helper) {
        TextEditorFixture invalidFixture = createFixture();
        EditorControls invalidControls = controls(invalidFixture.root());
        invalidControls.editor().setLines(List.of("updated"));
        for (String rawScale : List.of("", "invalid", "NaN", "Infinity", "0.00001", "1000.1")) {
            replaceRawText(invalidControls.scaleInput(), rawScale);
            helper.assertTrue(invalidControls.scaleInput().isError(),
                    "Text module scale field accepted invalid raw input: " + rawScale);
            click(invalidControls.saveButton());
            helper.assertTrue(invalidFixture.actions().isEmpty(),
                    "Text module save emitted an action for invalid raw scale: " + rawScale);
        }

        assertValidScaleSendsAction(helper, "0.0001", 0.0001f);
        assertValidScaleSendsAction(helper, "1000", 1000.0f);
        helper.succeed();
    }

    private static void assertValidScaleSendsAction(GameTestHelper helper, String rawScale, float expectedScale) {
        TextEditorFixture fixture = createFixture();
        EditorControls controls = controls(fixture.root());
        controls.editor().setLines(List.of("updated"));
        replaceRawText(controls.scaleInput(), rawScale);
        helper.assertTrue(!controls.scaleInput().isError(),
                "Text module scale field rejected supported boundary: " + rawScale);
        click(controls.saveButton());
        helper.assertTrue(fixture.actions().size() == 1,
                "Text module save did not emit one action for supported boundary: " + rawScale);
        helper.assertTrue(
                Float.compare(requestedConfiguration(fixture.actions().getFirst()).scale(), expectedScale) == 0,
                "Text module action changed supported boundary scale: " + rawScale);
    }

    private static TextEditorFixture createFixture() {
        TestCentralMonitorMachine machine = new TestCentralMonitorMachine();
        MonitorGroup group = new MonitorGroup("text-module-ui-test");
        group.getItemStackHandler().setStackInSlot(0, textModule("opening", 1.0f));
        group.setTextConfigurationRevision(OPENING_REVISION);
        ItemStack sharedModule = group.getItemStackHandler().getStackInSlot(0);
        ItemStack openingSnapshot = CentralMonitorTextModuleActions.captureExpectedModule(sharedModule);
        List<SyncActionData> actions = new ArrayList<>();
        UIElement root = new TextModuleBehaviour()
                .createConfigurationElement(sharedModule, machine, group, actions::add);
        return new TextEditorFixture(group, sharedModule, openingSnapshot,
                machine.getCentralMonitorActionIncarnation(), group.getIdentity(), group.getModuleSlotIncarnation(),
                root, actions);
    }

    private static EditorControls controls(UIElement root) {
        List<UIElement> children = root.getChildren();
        if (children.size() != 4 ||
                !(children.get(0) instanceof CodeEditor editor) ||
                !(children.get(1) instanceof GTButtonElement saveButton) ||
                !(children.get(2) instanceof GTTextFieldElement scaleInput)) {
            throw new IllegalStateException("Text module LDLib2 root does not expose its expected direct controls.");
        }
        return new EditorControls(editor, saveButton, scaleInput);
    }

    private static void replaceRawText(GTTextFieldElement input, String text) {
        input.setSelection(0, input.getRawText().length());
        input.insertText(text);
    }

    private static void click(GTButtonElement button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = button;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static void assertExpectedSnapshot(GameTestHelper helper, SyncActionData action,
                                               ItemStack expectedSnapshot, String message) {
        helper.assertTrue(ItemStack.matches(expectedSnapshot, expectedSnapshot(action)), message);
    }

    private static ItemStack expectedSnapshot(SyncActionData action) {
        return action.payload().get(GTDataComponents.PLACEHOLDER_ITEM_STACK.get());
    }

    private static long expectedRevision(SyncActionData action) {
        return fields(action).fields().get(EXPECTED_CONFIGURATION_REVISION_FIELD).getAsLong();
    }

    private static TextLineList requestedConfiguration(SyncActionData action) {
        return TextLineList.CODEC
                .parse(JsonOps.INSTANCE, fields(action).fields().get(REQUESTED_CONFIGURATION_FIELD))
                .getOrThrow();
    }

    private static SyncFieldData fields(SyncActionData action) {
        return action.payload().get(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static UUID uuidField(SyncActionData action, ResourceLocation field) {
        return UUIDUtil.CODEC.parse(JsonOps.INSTANCE, fields(action).fields().get(field)).getOrThrow();
    }

    private static TextLineList configuration(String line, float scale) {
        return CentralMonitorTextModuleActions.createConfiguration(List.of(line), scale);
    }

    private static ItemStack textModule(String line, float scale) {
        ItemStack module = GTItems.TEXT_MODULE.get().getDefaultInstance();
        module.set(GTDataComponents.FORMAT_STRING_LIST.get(), configuration(line, scale));
        return module;
    }

    private static BlockEntityCreationInfo centralMonitorInfo() {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), BlockPos.ZERO,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private record TextEditorFixture(MonitorGroup group, ItemStack sharedModule, ItemStack openingSnapshot,
                                     UUID holderIncarnation, UUID groupIdentity, UUID moduleSlotIncarnation,
                                     UIElement root, List<SyncActionData> actions) {}

    private record EditorControls(CodeEditor editor, GTButtonElement saveButton,
                                  GTTextFieldElement scaleInput) {}

    private static final class TestCentralMonitorMachine extends CentralMonitorMachine {

        private TestCentralMonitorMachine() {
            super(centralMonitorInfo());
        }

        @Override
        public boolean isRemote() {
            return true;
        }
    }
}
