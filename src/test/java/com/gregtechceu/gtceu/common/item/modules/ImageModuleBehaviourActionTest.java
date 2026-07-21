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
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorImageModuleActions;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
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
public class ImageModuleBehaviourActionTest {

    private static final String BATCH = "ImageModuleBehaviourAction";
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_central_monitor_image_module_url");
    private static final ResourceLocation HOLDER_INCARNATION_FIELD = SyncFieldData.key("holder_incarnation");
    private static final ResourceLocation GROUP_IDENTITY_FIELD = SyncFieldData.key("group_identity");
    private static final ResourceLocation MODULE_SLOT_INCARNATION_FIELD = SyncFieldData
            .key("module_slot_incarnation");
    private static final ResourceLocation EXPECTED_URL_FIELD = SyncFieldData.key("expected_url");
    private static final ResourceLocation REQUESTED_URL_FIELD = SyncFieldData.key("requested_url");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2ControlsDispatchOpeningActionThroughRealClick(GameTestHelper helper) {
        ImageEditorFixture fixture = createFixture();
        EditorControls controls = controls(fixture.root());
        replaceRawText(controls.urlInput(), "requested");
        click(controls.saveButton());

        helper.assertTrue(fixture.actions().size() == 1,
                "Image module LDLib2 save button did not emit exactly one action");
        SyncActionData action = fixture.actions().getFirst();
        helper.assertTrue(action.actionId().equals(ACTION_ID) && action.sequence() == 0,
                "Image module LDLib2 save emitted the wrong action id or opening sequence");
        helper.assertTrue(uuidField(action, HOLDER_INCARNATION_FIELD).equals(fixture.holderIncarnation()) &&
                uuidField(action, GROUP_IDENTITY_FIELD).equals(fixture.groupIdentity()) &&
                uuidField(action, MODULE_SLOT_INCARNATION_FIELD).equals(fixture.moduleSlotIncarnation()),
                "Image module LDLib2 save did not retain its opening holder, group, and module-slot identities");
        assertExpectedSnapshot(helper, action, fixture.openingSnapshot(),
                "Image module LDLib2 save did not carry its opening module snapshot");
        helper.assertTrue("opening".equals(urlField(action, EXPECTED_URL_FIELD)) &&
                "requested".equals(urlField(action, REQUESTED_URL_FIELD)),
                "Image module LDLib2 save did not encode its opening and requested URLs");
        helper.assertTrue("opening".equals(sharedUrl(fixture)),
                "Image module LDLib2 save optimistically changed the shared module stack");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void pageRetainsOpeningSnapshotAfterSharedModuleChanges(GameTestHelper helper) {
        ImageEditorFixture fixture = createFixture();
        fixture.sharedModule().set(GTDataComponents.IMAGE_MODULE_URL.get(), "concurrent");

        EditorControls controls = controls(fixture.root());
        replaceRawText(controls.urlInput(), "requested");
        click(controls.saveButton());

        helper.assertTrue(fixture.actions().size() == 1,
                "Image module page did not emit its opening-scoped action after a shared stack change");
        SyncActionData action = fixture.actions().getFirst();
        assertExpectedSnapshot(helper, action, fixture.openingSnapshot(),
                "Image module page replaced its opening snapshot with concurrent shared state");
        helper.assertTrue("opening".equals(urlField(action, EXPECTED_URL_FIELD)) &&
                "concurrent".equals(sharedUrl(fixture)),
                "Image module page replaced its opening URL or changed the concurrent shared state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void unchangedAndOversizedUrlsDoNotDispatch(GameTestHelper helper) {
        ImageEditorFixture fixture = createFixture();
        EditorControls controls = controls(fixture.root());

        click(controls.saveButton());
        helper.assertTrue(fixture.actions().isEmpty(),
                "Image module save emitted an action for an unchanged URL");

        replaceRawText(controls.urlInput(), "x".repeat(32_768));
        helper.assertTrue(controls.urlInput().isError(),
                "Image module URL field accepted a value above the network limit");
        click(controls.saveButton());
        helper.assertTrue(fixture.actions().isEmpty(),
                "Image module save emitted an action for a URL above the network limit");
        helper.assertTrue("opening".equals(sharedUrl(fixture)),
                "Rejected image module URL input changed the shared module stack");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void unacknowledgedSavesRemainRetryableAgainstAuthoritativeState(GameTestHelper helper) {
        ImageEditorFixture fixture = createFixture();
        EditorControls controls = controls(fixture.root());

        replaceRawText(controls.urlInput(), "first");
        click(controls.saveButton());
        click(controls.saveButton());
        replaceRawText(controls.urlInput(), "second");
        click(controls.saveButton());

        helper.assertTrue(fixture.actions().size() == 3,
                "Image module page could not retry an unacknowledged save before changing its draft");
        SyncActionData firstAction = fixture.actions().get(0);
        SyncActionData retryAction = fixture.actions().get(1);
        SyncActionData secondAction = fixture.actions().get(2);
        helper.assertTrue(firstAction.sequence() == 0 && retryAction.sequence() == 1 &&
                secondAction.sequence() == 2,
                "Image module page did not advance action sequence across retry and changed draft");
        assertExpectedSnapshot(helper, firstAction, fixture.openingSnapshot(),
                "First image module save did not use the opening snapshot");
        assertExpectedSnapshot(helper, retryAction, fixture.openingSnapshot(),
                "Retried image module save stopped using the last authoritative snapshot");
        assertExpectedSnapshot(helper, secondAction, fixture.openingSnapshot(),
                "Second image module save stopped using the last authoritative snapshot");
        helper.assertTrue("opening".equals(urlField(firstAction, EXPECTED_URL_FIELD)) &&
                "first".equals(urlField(firstAction, REQUESTED_URL_FIELD)) &&
                "opening".equals(urlField(retryAction, EXPECTED_URL_FIELD)) &&
                "first".equals(urlField(retryAction, REQUESTED_URL_FIELD)) &&
                "opening".equals(urlField(secondAction, EXPECTED_URL_FIELD)) &&
                "second".equals(urlField(secondAction, REQUESTED_URL_FIELD)),
                "Image module page used an unacknowledged request as its CAS baseline");
        helper.assertTrue("opening".equals(sharedUrl(fixture)),
                "Image module page optimistically changed the shared module stack");
        helper.succeed();
    }

    private static ImageEditorFixture createFixture() {
        TestCentralMonitorMachine machine = new TestCentralMonitorMachine();
        MonitorGroup group = new MonitorGroup("image-module-ui-test");
        group.getItemStackHandler().setStackInSlot(0, imageModule("opening"));
        ItemStack sharedModule = group.getItemStackHandler().getStackInSlot(0);
        ItemStack openingSnapshot = CentralMonitorImageModuleActions.captureExpectedModule(sharedModule);
        List<SyncActionData> actions = new ArrayList<>();
        UIElement root = new ImageModuleBehaviour()
                .createConfigurationElement(sharedModule, machine, group, actions::add);
        return new ImageEditorFixture(sharedModule, openingSnapshot,
                machine.getCentralMonitorActionIncarnation(), group.getIdentity(), group.getModuleSlotIncarnation(),
                root, actions);
    }

    private static EditorControls controls(UIElement root) {
        List<UIElement> children = root.getChildren();
        if (children.size() != 2 ||
                !(children.get(0) instanceof GTTextFieldElement urlInput) ||
                !(children.get(1) instanceof GTButtonElement saveButton)) {
            throw new IllegalStateException("Image module LDLib2 root does not expose its expected direct controls.");
        }
        return new EditorControls(urlInput, saveButton);
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

    private static UUID uuidField(SyncActionData action, ResourceLocation field) {
        return UUIDUtil.CODEC.parse(JsonOps.INSTANCE, fields(action).fields().get(field)).getOrThrow();
    }

    private static String urlField(SyncActionData action, ResourceLocation field) {
        return fields(action).fields().get(field).getAsString();
    }

    private static SyncFieldData fields(SyncActionData action) {
        return action.payload().get(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static String sharedUrl(ImageEditorFixture fixture) {
        return fixture.sharedModule().get(GTDataComponents.IMAGE_MODULE_URL.get());
    }

    private static ItemStack imageModule(String url) {
        ItemStack module = GTItems.IMAGE_MODULE.get().getDefaultInstance();
        module.set(GTDataComponents.IMAGE_MODULE_URL.get(), url);
        return module;
    }

    private static BlockEntityCreationInfo centralMonitorInfo() {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), BlockPos.ZERO,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private record ImageEditorFixture(ItemStack sharedModule, ItemStack openingSnapshot,
                                      UUID holderIncarnation, UUID groupIdentity, UUID moduleSlotIncarnation,
                                      UIElement root, List<SyncActionData> actions) {}

    private record EditorControls(GTTextFieldElement urlInput, GTButtonElement saveButton) {}

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
