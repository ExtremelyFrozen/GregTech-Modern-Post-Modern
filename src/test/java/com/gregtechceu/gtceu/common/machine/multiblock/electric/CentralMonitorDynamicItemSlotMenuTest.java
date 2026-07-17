package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotBundle;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotSessionElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.gui.factory.DynamicItemSlotMachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.GTDynamicItemSlotContainerMenu;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotBinding;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotDefinition;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotManifest;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotManifestSequence;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.CentralMonitorGroupItemHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.CodeEditor;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CentralMonitorDynamicItemSlotMenuTest {

    private static final String BATCH = "CentralMonitorDynamicItemSlotMenu";
    private static final BlockPos FIRST_MONITOR = new BlockPos(1, 0, 0);
    private static final BlockPos SECOND_MONITOR = new BlockPos(2, 0, 0);
    private static final UUID FIRST_GROUP_ID = new UUID(0, 301);
    private static final UUID SECOND_GROUP_ID = new UUID(0, 302);
    private static final UUID FIRST_PLAYER_ID = new UUID(0, 401);
    private static final UUID SECOND_PLAYER_ID = new UUID(0, 402);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void onlyDedicatedDynamicSlotHolderCanBuildCentralMonitorUI(GameTestHelper helper) {
        MenuFixture fixture = createMenuFixture(helper);
        MachineUIHolder dynamicHolder = new DynamicItemSlotMachineUIHolder(fixture.player(), fixture.machine());
        MachineUIHolder genericHolder = new MachineUIHolderContext(fixture.player(), fixture.machine());

        helper.assertTrue(fixture.machine().canCreateLDLib2UI(fixture.player(), dynamicHolder) &&
                !fixture.machine().canCreateLDLib2UI(fixture.player(), genericHolder),
                "Central Monitor did not require its dedicated dynamic-slot holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dynamicSlotSessionRemainsActiveOutsideTheCachedMainPage(GameTestHelper helper) {
        MenuFixture fixture = createMenuFixture(helper);
        CentralMonitorElement mainPage = fixture.element();
        GTDynamicItemSlotSessionElement session = mainPage.sessionElement();
        if (!(session.getParent() instanceof LDLib2FancyMachineUIElement)) {
            throw new IllegalStateException("Central Monitor dynamic-slot session is not a Fancy shell root child");
        }
        helper.assertTrue(!descendants(mainPage).contains(session),
                "Central Monitor dynamic-slot session remained owned by its cached main page");

        mainPage.setVisible(false);
        mainPage.setActive(false);
        helper.assertTrue(session.isVisible() && session.isActive() && session.isDisplayed(),
                "Central Monitor dynamic-slot session stopped while its cached main page was hidden");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void authoritativeModuleSnapshotsRefreshEditorsWithoutInterruptingDerivedText(GameTestHelper helper) {
        MenuFixture fixture = createMenuFixture(helper);
        CentralMonitorElement element = fixture.element();
        MonitorGroup group = fixture.machine().resolveCentralMonitorGroup(FIRST_GROUP_ID);
        if (group == null) {
            throw new IllegalStateException("Central Monitor module snapshot test could not resolve its group");
        }

        ItemStack imageModule = GTItems.IMAGE_MODULE.get().getDefaultInstance();
        imageModule.set(GTDataComponents.IMAGE_MODULE_URL.get(), "opening");
        group.getItemStackHandler().setStackInSlot(0, imageModule);
        UUID imageIncarnation = group.getModuleSlotIncarnation();

        DynamicItemSlotManifest manifest = appendManifest(fixture);
        DynamicItemSlotBinding binding = bindingFor(manifest, FIRST_GROUP_ID);
        element.sessionElement().applySelection(Optional.of(binding.bindingId()), manifest);
        GTTextFieldElement openingImageInput = singleImageUrlInput(element.moduleConfigurationHost());
        group.getItemStackHandler().getStackInSlot(0)
                .set(GTDataComponents.IMAGE_MODULE_URL.get(), "authoritative");
        element.screenTick();

        GTTextFieldElement authoritativeImageInput = singleImageUrlInput(element.moduleConfigurationHost());
        helper.assertTrue(group.getModuleSlotIncarnation().equals(imageIncarnation) &&
                authoritativeImageInput != openingImageInput &&
                authoritativeImageInput.getRawText().equals("authoritative"),
                "same-slot authoritative image URL change did not replace the stale editor session");

        ItemStack textModule = GTItems.TEXT_MODULE.get().getDefaultInstance();
        textModule.set(GTDataComponents.FORMAT_STRING_LIST.get(),
                CentralMonitorTextModuleActions.createConfiguration(List.of("opening"), 1.0f));
        group.getItemStackHandler().setStackInSlot(0, textModule);
        group.rotateModuleSlotIncarnation();
        group.setTextConfigurationRevision(11);
        element.screenTick();

        UUID textIncarnation = group.getModuleSlotIncarnation();
        CodeEditor openingTextEditor = singleCodeEditor(element.moduleConfigurationHost());
        ItemStack currentTextModule = group.getItemStackHandler().getStackInSlot(0);
        currentTextModule.set(GTDataComponents.TEXT_LINE_LIST.get(),
                CentralMonitorTextModuleActions.createConfiguration(List.of("derived"), 2.0f));
        currentTextModule.set(GTDataComponents.PLACEHOLDER_UUID.get(), UUID.randomUUID());
        element.screenTick();
        helper.assertTrue(singleCodeEditor(element.moduleConfigurationHost()) == openingTextEditor,
                "derived text state interrupted the opening editor draft");

        group.setTextConfigurationRevision(12);
        element.screenTick();
        CodeEditor revisionTextEditor = singleCodeEditor(element.moduleConfigurationHost());
        helper.assertTrue(revisionTextEditor != openingTextEditor &&
                List.of(revisionTextEditor.getValue()).equals(List.of("opening")),
                "same-slot authoritative text revision did not replace the stale editor session");

        TextLineList authoritativeConfiguration = CentralMonitorTextModuleActions.createConfiguration(
                List.of("authoritative"), 3.0f);
        group.applyTextConfiguration(authoritativeConfiguration);
        element.screenTick();
        CodeEditor authoritativeTextEditor = singleCodeEditor(element.moduleConfigurationHost());
        helper.assertTrue(group.getModuleSlotIncarnation().equals(textIncarnation) &&
                group.getTextConfigurationRevision() == 13 &&
                authoritativeTextEditor != revisionTextEditor &&
                List.of(authoritativeTextEditor.getValue()).equals(List.of("authoritative")),
                "same-slot authoritative text configuration did not replace the stale editor session");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void groupBindingsRegisterNineOrderedSlotsAndFollowSynchronizedReplacement(GameTestHelper helper) {
        MenuFixture fixture = createMenuFixture(helper);
        CentralMonitorElement element = fixture.element();
        GTDynamicItemSlotSessionElement session = element.sessionElement();
        int baseSlotCount = fixture.menu().slots.size();

        List<DynamicItemSlotDefinition> definitions = session.definitions();
        MonitorGroup originalGroup = fixture.machine().resolveCentralMonitorGroup(FIRST_GROUP_ID);
        MonitorGroup originalSecondGroup = fixture.machine().resolveCentralMonitorGroup(SECOND_GROUP_ID);
        if (originalGroup == null || originalSecondGroup == null) {
            throw new IllegalStateException("Central Monitor definition test could not resolve its groups");
        }
        helper.assertTrue(definitions.equals(List.of(
                new DynamicItemSlotDefinition(
                        FIRST_GROUP_ID, originalGroup.getDynamicItemSlotIncarnation(),
                        CentralMonitorGroupItemHandler.SLOT_COUNT),
                new DynamicItemSlotDefinition(
                        SECOND_GROUP_ID, originalSecondGroup.getDynamicItemSlotIncarnation(),
                        CentralMonitorGroupItemHandler.SLOT_COUNT))),
                "Central Monitor did not publish one ordered nine-slot definition per group");

        DynamicItemSlotManifest manifest = DynamicItemSlotManifestSequence.advance(
                Optional.empty(), session.sourceRevision(), baseSlotCount, definitions);
        List<GTDynamicItemSlotBundle> bundles = manifest.bindings().stream()
                .map(session::appendBinding)
                .toList();
        helper.assertTrue(fixture.menu().slots.size() == baseSlotCount + 2 * CentralMonitorGroupItemHandler.SLOT_COUNT,
                "Central Monitor bindings did not append eighteen real Vanilla menu slots");
        assertBindingRegistration(helper, fixture.menu(), bundles.getFirst(), baseSlotCount, FIRST_GROUP_ID);
        assertBindingRegistration(helper, fixture.menu(), bundles.get(1),
                baseSlotCount + CentralMonitorGroupItemHandler.SLOT_COUNT, SECOND_GROUP_ID);

        originalGroup.getPlaceholderSlotsHandler().setStackInSlot(0, Items.COBBLESTONE.getDefaultInstance());

        GTDynamicItemSlotBundle selectedBundle = bundles.getFirst();
        DynamicItemSlotBinding selectedBinding = selectedBundle.binding();
        helper.assertTrue(element.overviewElement().isVisible() && !element.groupElement().isVisible() &&
                element.configuredGroupIdentity().isEmpty(),
                "Central Monitor opening did not begin on its overview before a selection acknowledgement");
        session.applySelection(Optional.of(selectedBinding.bindingId()), manifest);
        session.applyInteractionState(bindingId -> bindingId.equals(selectedBinding.bindingId()));
        helper.assertTrue(!element.overviewElement().isVisible() && element.groupElement().isVisible() &&
                element.configuredGroupIdentity().equals(Optional.of(FIRST_GROUP_ID)),
                "Central Monitor did not switch to the acknowledged group binding");

        TestCentralMonitorMachine synchronizedSource = preparedMachine(centralMonitorInfo(BlockPos.ZERO), false);
        helper.assertTrue(synchronizedSource.createCentralMonitorGroup(0, FIRST_GROUP_ID, Set.of(FIRST_MONITOR)) &&
                synchronizedSource.createCentralMonitorGroup(1, SECOND_GROUP_ID, Set.of(SECOND_MONITOR)),
                "synchronized source rejected its group setup");
        MonitorGroup synchronizedGroup = synchronizedSource.resolveCentralMonitorGroup(FIRST_GROUP_ID);
        helper.assertTrue(synchronizedGroup != null, "synchronized source group did not resolve");
        MonitorGroup synchronizedLifecycle = MonitorGroup.restore(
                synchronizedGroup.getIdentity(), originalGroup.getDynamicItemSlotIncarnation(),
                synchronizedGroup.getModuleSlotIncarnation(), synchronizedGroup.getTextConfigurationRevision(),
                synchronizedGroup.getName(), synchronizedGroup.getItemStackHandler(),
                synchronizedGroup.getPlaceholderSlotsHandler());
        synchronizedGroup.getMonitorPositions().forEach(synchronizedLifecycle::add);
        synchronizedSource.getMonitorGroups().set(0, synchronizedLifecycle);
        synchronizedGroup = synchronizedLifecycle;
        synchronizedGroup.getPlaceholderSlotsHandler().setStackInSlot(0, Items.GOLD_INGOT.getDefaultInstance());
        ItemStack imageModule = GTItems.IMAGE_MODULE.get().getDefaultInstance();
        synchronizedGroup.getItemStackHandler().setStackInSlot(0, imageModule.copy());

        DataComponentMap fullSync = synchronizedSource.getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        fixture.machine().getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), fullSync);
        synchronizedSource.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);
        MonitorGroup replacementGroup = fixture.machine().resolveCentralMonitorGroup(FIRST_GROUP_ID);
        helper.assertTrue(replacementGroup != null && replacementGroup != originalGroup,
                "field sync did not replace the Central Monitor group object for the same UUID");

        GTDynamicItemSlotElement placeholderSlot = selectedBundle.elements().getFirst();
        GTDynamicItemSlotElement moduleSlot = selectedBundle.elements()
                .get(CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET);
        helper.assertTrue(placeholderSlot.getSlot().getItem().is(Items.GOLD_INGOT) &&
                ItemStack.isSameItemSameComponents(moduleSlot.getSlot().getItem(), imageModule),
                "existing dynamic routes retained the old group's placeholder or module handler");
        placeholderSlot.getSlot().set(Items.DIAMOND.getDefaultInstance());
        helper.assertTrue(replacementGroup.getPlaceholderSlotsHandler().getStackInSlot(0).is(Items.DIAMOND) &&
                originalGroup.getPlaceholderSlotsHandler().getStackInSlot(0).is(Items.COBBLESTONE),
                "dynamic route wrote through the replaced group instead of the current UUID target");
        helper.assertTrue(placeholderSlot.getSlot().mayPlace(Items.STONE.getDefaultInstance()) &&
                moduleSlot.getSlot().mayPlace(imageModule) &&
                !moduleSlot.getSlot().mayPlace(Items.STONE.getDefaultInstance()),
                "logical offsets zero through seven or module offset eight lost their handler filters");

        element.serverTick();
        List<GTTextFieldElement> imageUrlInputs = descendants(element.moduleConfigurationHost()).stream()
                .filter(GTTextFieldElement.class::isInstance)
                .map(GTTextFieldElement.class::cast)
                .toList();
        helper.assertTrue(imageUrlInputs.size() == 1,
                "image module incarnation did not build exactly one URL input");
        GTTextFieldElement imageUrlInput = imageUrlInputs.getFirst();

        ItemStack textModule = GTItems.TEXT_MODULE.get().getDefaultInstance();
        synchronizedGroup.getItemStackHandler().setStackInSlot(0, textModule.copy());
        DataComponentMap delta = synchronizedSource.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);
        fixture.machine().getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), delta);
        element.serverTick();
        List<UIElement> textConfiguration = descendants(element.moduleConfigurationHost());
        helper.assertTrue(textConfiguration.stream().noneMatch(child -> child == imageUrlInput) &&
                textConfiguration.stream().anyMatch(CodeEditor.class::isInstance),
                "text module incarnation did not replace the image URL input with its code editor");

        session.applyInteractionState(bindingId -> false);
        session.applySelection(Optional.empty(), manifest);
        helper.assertTrue(element.overviewElement().isVisible() && !element.groupElement().isVisible() &&
                element.configuredGroupIdentity().isEmpty() &&
                selectedBundle.elements().stream().noneMatch(GTDynamicItemSlotElement::isInteractionEnabled),
                "overview acknowledgement retained a configured group or interactive dynamic slot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void groupPageClicksWaitForAcknowledgementAndStayOpeningLocal(GameTestHelper helper) {
        ServerPlayer firstPlayer = preparedPlayer(helper, FIRST_PLAYER_ID, "central_monitor_first");
        ServerPlayer secondPlayer = preparedPlayer(helper, SECOND_PLAYER_ID, "central_monitor_second");
        PageRequests firstRequests = new PageRequests();
        PageRequests secondRequests = new PageRequests();
        Map<UUID, PageRequests> openingRequests = Map.of(
                FIRST_PLAYER_ID, firstRequests,
                SECOND_PLAYER_ID, secondRequests);
        BlockPos machinePos = helper.absolutePos(new BlockPos(2, 2, 2));
        PageRequestCentralMonitorMachine machine = preparedPageRequestMachine(
                centralMonitorInfo(machinePos), openingRequests);
        installMachine(helper, machinePos, machine);
        createGroups(helper, machine);
        MenuFixture firstFixture = openMenu(helper, firstPlayer, machine, 74);
        MenuFixture secondFixture = openMenu(helper, secondPlayer, machine, 75);
        DynamicItemSlotManifest firstManifest = appendManifest(firstFixture);
        DynamicItemSlotManifest secondManifest = appendManifest(secondFixture);
        DynamicItemSlotBinding firstBinding = bindingFor(firstManifest, FIRST_GROUP_ID);
        DynamicItemSlotBinding secondBinding = bindingFor(secondManifest, SECOND_GROUP_ID);
        CentralMonitorElement first = firstFixture.element();
        CentralMonitorElement second = secondFixture.element();

        click(first.groupGearControls().get(FIRST_GROUP_ID));
        click(second.groupGearControls().get(SECOND_GROUP_ID));
        helper.assertTrue(firstRequests.targetSelections.equals(List.of(FIRST_GROUP_ID)) &&
                secondRequests.targetSelections.equals(List.of(SECOND_GROUP_ID)),
                "gear clicks did not emit their opening-local group requests");
        helper.assertTrue(first.overviewElement().isVisible() && second.overviewElement().isVisible() &&
                first.configuredGroupIdentity().isEmpty() && second.configuredGroupIdentity().isEmpty(),
                "a group request changed pages before its protocol acknowledgement");

        first.sessionElement().applySelection(Optional.of(firstBinding.bindingId()), firstManifest);
        helper.assertTrue(first.configuredGroupIdentity().equals(Optional.of(FIRST_GROUP_ID)) &&
                first.groupElement().isVisible() && second.configuredGroupIdentity().isEmpty() &&
                second.overviewElement().isVisible(),
                "the first group acknowledgement changed the second opening");
        second.sessionElement().applySelection(Optional.of(secondBinding.bindingId()), secondManifest);
        helper.assertTrue(second.configuredGroupIdentity().equals(Optional.of(SECOND_GROUP_ID)) &&
                second.groupElement().isVisible(),
                "the second opening did not apply its own group acknowledgement");

        click(first.overviewRequestButton());
        helper.assertTrue(firstRequests.overviewRequests == 1 && first.groupElement().isVisible() &&
                first.configuredGroupIdentity().equals(Optional.of(FIRST_GROUP_ID)),
                "overview request changed the first opening before its acknowledgement");
        helper.assertTrue(secondRequests.overviewRequests == 0 && second.groupElement().isVisible() &&
                second.configuredGroupIdentity().equals(Optional.of(SECOND_GROUP_ID)),
                "the first overview request leaked into the second opening");

        first.sessionElement().applySelection(Optional.empty(), firstManifest);
        helper.assertTrue(first.overviewElement().isVisible() && !first.groupElement().isVisible() &&
                first.configuredGroupIdentity().isEmpty() && second.groupElement().isVisible() &&
                second.configuredGroupIdentity().equals(Optional.of(SECOND_GROUP_ID)),
                "overview acknowledgement did not remain isolated to its opening");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void removedAndRebuiltGroupBetweenRefreshesGetsANewLifecycle(GameTestHelper helper) {
        MenuFixture fixture = createMenuFixture(helper);
        GTDynamicItemSlotSessionElement session = fixture.element().sessionElement();
        int baseSlotCount = fixture.menu().slots.size();
        DynamicItemSlotManifest activeManifest = DynamicItemSlotManifestSequence.advance(
                Optional.empty(), session.sourceRevision(), baseSlotCount, session.definitions());
        activeManifest.bindings().forEach(session::appendBinding);
        DynamicItemSlotBinding originalBinding = bindingFor(activeManifest, FIRST_GROUP_ID);
        GTDynamicItemSlotBundle originalBundle = session.appendBinding(originalBinding);
        UUID originalIncarnation = originalBinding.targetIncarnation();
        long initialRevision = fixture.machine().getCentralMonitorMembershipRevision();

        helper.assertTrue(fixture.machine().removeCentralMonitorGroupMembers(
                initialRevision, FIRST_GROUP_ID, Set.of(FIRST_MONITOR)),
                "Central Monitor rejected removal of the first complete group");
        long removedRevision = fixture.machine().getCentralMonitorMembershipRevision();
        helper.assertTrue(removedRevision == Math.incrementExact(initialRevision) &&
                fixture.machine().resolveCentralMonitorGroup(FIRST_GROUP_ID) == null,
                "group removal did not advance membership revision and remove its UUID target");

        helper.assertTrue(fixture.machine().createCentralMonitorGroup(
                removedRevision, FIRST_GROUP_ID, Set.of(FIRST_MONITOR)),
                "Central Monitor rejected rebuilding the removed group UUID");
        long rebuiltRevision = fixture.machine().getCentralMonitorMembershipRevision();
        helper.assertTrue(rebuiltRevision == Math.incrementExact(removedRevision),
                "group rebuild did not advance membership revision");
        MonitorGroup rebuiltGroup = fixture.machine().resolveCentralMonitorGroup(FIRST_GROUP_ID);
        if (rebuiltGroup == null) {
            throw new IllegalStateException("rebuilt Central Monitor group did not resolve");
        }
        helper.assertTrue(!rebuiltGroup.getDynamicItemSlotIncarnation().equals(originalIncarnation),
                "rebuilt Central Monitor group reused its old dynamic item-slot incarnation");
        rebuiltGroup.getPlaceholderSlotsHandler().setStackInSlot(0, Items.GOLD_INGOT.getDefaultInstance());
        GTDynamicItemSlotElement oldSlot = originalBundle.elements().getFirst();
        helper.assertTrue(oldSlot.getSlot().getItem().isEmpty(),
                "old route read from the rebuilt group before a manifest refresh");
        oldSlot.getSlot().set(Items.DIAMOND.getDefaultInstance());
        helper.assertTrue(rebuiltGroup.getPlaceholderSlotsHandler().getStackInSlot(0).is(Items.GOLD_INGOT),
                "old route wrote into the rebuilt group before a manifest refresh");

        DynamicItemSlotManifest rebuiltManifest = DynamicItemSlotManifestSequence.advance(
                Optional.of(activeManifest), session.sourceRevision(), baseSlotCount, session.definitions());
        DynamicItemSlotBinding retainedTombstone = bindingWithId(rebuiltManifest, originalBinding.bindingId());
        List<DynamicItemSlotBinding> rebuiltTargetBindings = rebuiltManifest.bindings().stream()
                .filter(binding -> binding.targetId().equals(FIRST_GROUP_ID))
                .toList();
        helper.assertTrue(rebuiltManifest.sourceRevision() == rebuiltRevision &&
                rebuiltTargetBindings.size() == 2 && retainedTombstone.tombstone(),
                "rebuilt group UUID did not retain exactly one old tombstone and one new lifecycle");
        DynamicItemSlotBinding rebuiltBinding = rebuiltTargetBindings.stream()
                .filter(DynamicItemSlotBinding::present)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("rebuilt Central Monitor binding is missing"));
        int appendedSlotId = activeManifest.bindings().getLast().firstSlotId() +
                activeManifest.bindings().getLast().slotCount();
        helper.assertTrue(!rebuiltBinding.bindingId().equals(originalBinding.bindingId()) &&
                rebuiltBinding.targetIncarnation().equals(rebuiltGroup.getDynamicItemSlotIncarnation()) &&
                rebuiltBinding.firstSlotId() == appendedSlotId &&
                rebuiltBinding.slotCount() == CentralMonitorGroupItemHandler.SLOT_COUNT,
                "rebuilt group reused its old binding identity or range instead of appending a new lifecycle");

        GTDynamicItemSlotBundle rebuiltBundle = session.appendBinding(rebuiltBinding);
        assertBindingRegistration(helper, fixture.menu(), rebuiltBundle, appendedSlotId, FIRST_GROUP_ID);
        Set<UUID> rebuiltResolvedBindings = session.resolvedPresentBindingIds(rebuiltManifest);
        helper.assertTrue(rebuiltResolvedBindings.contains(rebuiltBinding.bindingId()) &&
                !rebuiltResolvedBindings.contains(retainedTombstone.bindingId()) &&
                session.isBindingSelectable(rebuiltBinding) &&
                !session.isBindingSelectable(retainedTombstone),
                "rebuilt group did not resolve exclusively through its new appended binding");
        helper.succeed();
    }

    private static DynamicItemSlotManifest appendManifest(MenuFixture fixture) {
        GTDynamicItemSlotSessionElement session = fixture.element().sessionElement();
        DynamicItemSlotManifest manifest = DynamicItemSlotManifestSequence.advance(
                Optional.empty(), session.sourceRevision(), fixture.menu().slots.size(), session.definitions());
        manifest.bindings().forEach(session::appendBinding);
        return manifest;
    }

    private static DynamicItemSlotBinding bindingFor(DynamicItemSlotManifest manifest, UUID targetIdentity) {
        return manifest.bindings().stream()
                .filter(binding -> binding.targetId().equals(targetIdentity))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing Central Monitor group binding"));
    }

    private static DynamicItemSlotBinding bindingWithId(DynamicItemSlotManifest manifest, UUID bindingIdentity) {
        return manifest.bindings().stream()
                .filter(binding -> binding.bindingId().equals(bindingIdentity))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing Central Monitor binding lifecycle"));
    }

    private static void click(UIElement target) {
        if (target == null) {
            throw new IllegalStateException("Central Monitor test could not resolve its click target");
        }
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = target;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static void assertBindingRegistration(GameTestHelper helper, GTDynamicItemSlotContainerMenu menu,
                                                  GTDynamicItemSlotBundle bundle, int firstSlotId,
                                                  UUID targetIdentity) {
        helper.assertTrue(bundle.binding().targetId().equals(targetIdentity) &&
                bundle.binding().firstSlotId() == firstSlotId &&
                bundle.elements().size() == CentralMonitorGroupItemHandler.SLOT_COUNT,
                "Central Monitor manifest assigned the wrong group identity, range, or slot count");
        for (int offset = 0; offset < bundle.elements().size(); offset++) {
            GTDynamicItemSlotElement element = bundle.elements().get(offset);
            int expectedSlotId = firstSlotId + offset;
            helper.assertTrue(element.getSlot().index == expectedSlotId &&
                    menu.slots.get(expectedSlotId) == element.getSlot(),
                    "Central Monitor dynamic slot offset " + offset + " was not registered at its manifest id");
        }
    }

    private static List<UIElement> descendants(UIElement root) {
        List<UIElement> descendants = new ArrayList<>();
        collectDescendants(root, descendants);
        return descendants;
    }

    private static void collectDescendants(UIElement root, List<UIElement> descendants) {
        for (UIElement child : root.getChildren()) {
            descendants.add(child);
            collectDescendants(child, descendants);
        }
    }

    private static GTTextFieldElement singleImageUrlInput(UIElement root) {
        List<GTTextFieldElement> inputs = new ArrayList<>();
        for (UIElement element : descendants(root)) {
            if (element instanceof GTTextFieldElement input) {
                inputs.add(input);
            }
        }
        if (inputs.size() != 1) {
            throw new IllegalStateException("Central Monitor image module did not expose exactly one URL input");
        }
        return inputs.getFirst();
    }

    private static CodeEditor singleCodeEditor(UIElement root) {
        List<CodeEditor> editors = new ArrayList<>();
        for (UIElement element : descendants(root)) {
            if (element instanceof CodeEditor editor) {
                editors.add(editor);
            }
        }
        if (editors.size() != 1) {
            throw new IllegalStateException("Central Monitor text module did not expose exactly one code editor");
        }
        return editors.getFirst();
    }

    private static MenuFixture createMenuFixture(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.getInventory().clearContent();

        BlockPos machinePos = helper.absolutePos(new BlockPos(2, 2, 2));
        TestCentralMonitorMachine machine = preparedMachine(centralMonitorInfo(machinePos), true);
        installMachine(helper, machinePos, machine);
        createGroups(helper, machine);
        return openMenu(helper, player, machine, 73);
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper, UUID identity, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(identity, name));
        player.closeContainer();
        player.getInventory().clearContent();
        return player;
    }

    private static void installMachine(GameTestHelper helper, BlockPos machinePos,
                                       TestCentralMonitorMachine machine) {
        helper.getLevel().setBlockAndUpdate(machinePos, GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
        helper.getLevel().removeBlockEntity(machinePos);
        machine.setLevel(helper.getLevel());
        helper.getLevel().setBlockEntity(machine);
    }

    private static void createGroups(GameTestHelper helper, TestCentralMonitorMachine machine) {
        helper.assertTrue(machine.createCentralMonitorGroup(0, FIRST_GROUP_ID, Set.of(FIRST_MONITOR)) &&
                machine.createCentralMonitorGroup(1, SECOND_GROUP_ID, Set.of(SECOND_MONITOR)),
                "menu fixture rejected its group setup");
    }

    private static MenuFixture openMenu(GameTestHelper helper, ServerPlayer player,
                                        TestCentralMonitorMachine machine, int containerId) {
        DynamicItemSlotMachineUIHolder holder = new DynamicItemSlotMachineUIHolder(player, machine);
        GTDynamicItemSlotContainerMenu menu = new GTDynamicItemSlotContainerMenu(
                menuType(), containerId, player.getInventory(), holder);
        player.containerMenu = menu;
        List<CentralMonitorElement> elements = menu.getModularUI().getAllElements().stream()
                .filter(CentralMonitorElement.class::isInstance)
                .map(CentralMonitorElement.class::cast)
                .toList();
        helper.assertTrue(elements.size() == 1,
                "Central Monitor menu did not install exactly one opening-local root element");
        return new MenuFixture(player, machine, menu, elements.getFirst());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static MenuType<ModularUIContainerMenu> menuType() {
        return (MenuType) MenuType.GENERIC_9x1;
    }

    private static TestCentralMonitorMachine preparedMachine(BlockEntityCreationInfo info, boolean remote) {
        TestCentralMonitorMachine machine = new TestCentralMonitorMachine(info, remote);
        machine.addComponent(FIRST_MONITOR);
        machine.addComponent(SECOND_MONITOR);
        return machine;
    }

    private static PageRequestCentralMonitorMachine preparedPageRequestMachine(
                                                                               BlockEntityCreationInfo info,
                                                                               Map<UUID, PageRequests> openingRequests) {
        PageRequestCentralMonitorMachine machine = new PageRequestCentralMonitorMachine(info, openingRequests);
        machine.addComponent(FIRST_MONITOR);
        machine.addComponent(SECOND_MONITOR);
        return machine;
    }

    private static BlockEntityCreationInfo centralMonitorInfo(BlockPos position) {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), position,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private record MenuFixture(ServerPlayer player, TestCentralMonitorMachine machine,
                               GTDynamicItemSlotContainerMenu menu, CentralMonitorElement element) {}

    private static class TestCentralMonitorMachine extends CentralMonitorMachine {

        private final Map<BlockPos, IMonitorComponent> components = new LinkedHashMap<>();
        private final List<IMonitorComponent> gridComponents = new ArrayList<>();
        private final boolean remote;

        private TestCentralMonitorMachine(BlockEntityCreationInfo info, boolean remote) {
            super(info);
            this.remote = remote;
            isFormed = true;
        }

        protected final void addComponent(BlockPos position) {
            TestMonitorComponent component = new TestMonitorComponent(position);
            components.put(position, component);
            gridComponents.add(component);
        }

        @Override
        public int getLeftDist() {
            return 0;
        }

        @Override
        public int getRightDist() {
            return Math.max(0, gridComponents.size() - 1);
        }

        @Override
        public int getUpDist() {
            return 0;
        }

        @Override
        public int getDownDist() {
            return 0;
        }

        @Override
        public @Nullable IMonitorComponent getComponent(int row, int column) {
            return row == 0 && column >= 0 && column < gridComponents.size() ? gridComponents.get(column) : null;
        }

        @Override
        protected boolean isMembershipStructureAvailable() {
            return true;
        }

        @Override
        public int getCentralMonitorMembershipCapacity() {
            return components.size();
        }

        @Override
        protected @NotNull Map<BlockPos, IMonitorComponent> resolveMembershipComponents() {
            return new LinkedHashMap<>(components);
        }

        @Override
        public boolean isRemote() {
            return remote;
        }
    }

    private static final class PageRequestCentralMonitorMachine extends TestCentralMonitorMachine {

        private final Map<UUID, PageRequests> openingRequests;

        private PageRequestCentralMonitorMachine(BlockEntityCreationInfo info,
                                                 Map<UUID, PageRequests> openingRequests) {
            super(info, true);
            this.openingRequests = Map.copyOf(openingRequests);
        }

        @Override
        @NotNull
        CentralMonitorElement createCentralMonitorElement(@NotNull Player player, @NotNull MachineUIHolder holder) {
            PageRequests requests = openingRequests.get(player.getUUID());
            if (requests == null) {
                throw new IllegalStateException("missing page requester for Central Monitor test opening");
            }
            return new CentralMonitorElement(this, player, holder, action -> {
                throw new IllegalStateException("page selection test emitted an unrelated sync action");
            }, UUID::randomUUID, requests);
        }
    }

    private static final class PageRequests implements CentralMonitorElement.GroupPageSelectionRequester {

        private final List<UUID> targetSelections = new ArrayList<>();
        private int overviewRequests;

        @Override
        public void requestTargetSelection(@NotNull UUID groupIdentity) {
            targetSelections.add(groupIdentity);
        }

        @Override
        public void requestOverview() {
            overviewRequests = Math.incrementExact(overviewRequests);
        }
    }

    private record TestMonitorComponent(BlockPos position) implements IMonitorComponent {

        @Override
        public IGuiTexture getComponentIcon() {
            return GuiTextures.BLANK_TRANSPARENT;
        }

        @Override
        public BlockPos getBlockPos() {
            return position;
        }

        @Override
        public @Nullable IItemHandler getDataItems() {
            return null;
        }
    }
}
