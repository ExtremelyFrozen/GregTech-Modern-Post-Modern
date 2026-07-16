package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.ItemStackTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.gregtechceu.gtceu.api.GTValues.EV;
import static com.gregtechceu.gtceu.api.GTValues.HV;
import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CleanroomMachineLDLib2UITest {

    private static final String BATCH = "CleanroomMachineLDLib2UI";
    private static final Component FIRST_SERVER_LINE = Component.literal("first Cleanroom server line");
    private static final Component SECOND_SERVER_LINE = Component.literal("second Cleanroom server line");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void registrationAndHolderIdentityKeepCleanroomSemantics(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        CleanroomMachine cleanroom = requireCleanroom(createMachine(GTMultiMachines.CLEANROOM));
        MutableMachineUIHolder holder = new MutableMachineUIHolder(cleanroom);

        helper.assertTrue(cleanroom.getClass() == CleanroomMachine.class &&
                cleanroom instanceof LDLib2MachineUIProvider && cleanroom instanceof LDLib2FancyActionMachine,
                "Cleanroom registration did not create its concrete LDLib2 controller");
        helper.assertTrue(cleanroom.getRecipeTypes().length == 1 &&
                cleanroom.getRecipeType() == GTRecipeTypes.DUMMY_RECIPES && !cleanroom.supportsBatchMode(),
                "Cleanroom recipe or batch semantics changed");
        cleanroom.setWorkingEnabled(false);
        helper.assertTrue(cleanroom.isWorkingEnabled(),
                "Cleanroom stopped enforcing its always-enabled work contract");
        helper.assertTrue(cleanroom.canCreateLDLib2UI(player, holder) &&
                cleanroom.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement,
                "Cleanroom rejected its matching holder or lost its Fancy shell");

        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(createMachine(GTMachines.MACERATOR[LV]));
        helper.assertTrue(!cleanroom.canCreateLDLib2UI(player, wrongHolder) &&
                createUIFails(cleanroom, player, wrongHolder),
                "Cleanroom accepted a holder for another machine");

        CleanroomMachine replacement = requireCleanroom(createMachine(GTMultiMachines.CLEANROOM));
        MutableMachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);
        helper.assertTrue(!cleanroom.canCreateLDLib2UI(player, replacementHolder) &&
                createUIFails(cleanroom, player, replacementHolder),
                "Cleanroom accepted another same-definition controller instance");

        LDLib2FancyUIProvider stalePage = cleanroom.createLDLib2Page(player, holder);
        holder.setMachine(replacement);
        boolean staleRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            staleRejected = expected.getMessage().contains("page holder");
        }
        helper.assertTrue(staleRejected,
                "Cleanroom page accepted a same-definition replacement after opening");

        IMultiPart unsupportedPart = requirePart(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestCleanroomMachine invalidCleanroom = new TestCleanroomMachine(List.of(unsupportedPart));
        boolean unsupportedPartRejected = false;
        try {
            invalidCleanroom.createLDLib2UI(player, new MutableMachineUIHolder(invalidCleanroom));
        } catch (IllegalStateException expected) {
            unsupportedPartRejected = expected.getMessage().contains("Cleanroom part has no LDLib2 Fancy page");
        }
        helper.assertTrue(unsupportedPartRejected,
                "Cleanroom silently omitted a part without an LDLib2 Fancy page");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = BATCH, timeoutTicks = 200)
    public static void supportedPartsKeepOpeningScopeConfiguratorsAndLayout(GameTestHelper helper) {
        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            List<MachineDefinition> supportedDefinitions = List.of(
                    GTMachines.ENERGY_INPUT_HATCH[LV],
                    GTMachines.ENERGY_INPUT_HATCH_4A[EV],
                    GTMachines.ENERGY_INPUT_HATCH_16A[EV],
                    GTMachines.MAINTENANCE_HATCH,
                    GTMachines.AUTO_MAINTENANCE_HATCH,
                    GTMachines.ITEM_PASSTHROUGH_HATCH[LV],
                    GTMachines.FLUID_PASSTHROUGH_HATCH[LV],
                    GTMachines.HULL[HV],
                    GTMachines.DIODE[HV]);
            List<IMultiPart> supportedParts = new ArrayList<>(supportedDefinitions.size());
            for (int index = 0; index < supportedDefinitions.size(); index++) {
                MachineDefinition definition = supportedDefinitions.get(index);
                IMultiPart part = requirePart(placeMachine(helper,
                        new BlockPos(index % 5, 1, index / 5), definition));
                helper.assertTrue(part instanceof LDLib2FancyPartUIProvider,
                        "Legal Cleanroom part has no LDLib2 Fancy provider: " + definition.getId());
                supportedParts.add(part);
            }

            List<Integer> controllerPartIndexes = List.of(0, 1, 3, 5, 6, 7, 8);
            List<IMultiPart> controllerParts = controllerPartIndexes.stream().map(supportedParts::get).toList();
            List<MachineDefinition> controllerDefinitions = controllerPartIndexes.stream()
                    .map(supportedDefinitions::get)
                    .toList();
            TestCleanroomMachine cleanroom = new TestCleanroomMachine(controllerParts);
            cleanroom.setLevel(helper.getLevel());
            cleanroom.setScreenTexture(GuiTextures.SLOT);
            cleanroom.setServerDisplay(List.of(FIRST_SERVER_LINE));
            cleanroom.refreshDisplaySnapshot();
            MutableMachineUIHolder holder = new MutableMachineUIHolder(cleanroom);
            LDLib2FancyUIProvider firstPage = cleanroom.createLDLib2Page(player, holder);
            LDLib2FancyUIProvider secondPage = cleanroom.createLDLib2Page(player, holder);

            Component expectedTitle = Component.translatable(cleanroom.getDefinition().getDescriptionId());
            helper.assertTrue(firstPage != secondPage && firstPage.getTitle().equals(expectedTitle) &&
                    firstPage.getTabTooltips().equals(List.of(expectedTitle)) &&
                    firstPage.getTabIcon() instanceof ItemStackTexture icon && icon.items.length == 1 &&
                    icon.items[0].getItem() == cleanroom.getDefinition().getItem(),
                    "Cleanroom controller page lost opening scope, title, tooltip, or item icon");

            List<Component> expectedPartTitles = controllerParts.stream()
                    .map(IMultiPart::self)
                    .map(MetaMachine::getDefinition)
                    .map(MachineDefinition::getDescriptionId)
                    .<Component>map(Component::translatable)
                    .toList();
            helper.assertTrue(firstPage.getSubTabs().size() == controllerParts.size() &&
                    firstPage.getSubTabs().stream().map(LDLib2FancyUIProvider::getTitle).toList()
                            .equals(expectedPartTitles),
                    "Cleanroom omitted or reordered a legal contextual part page");
            for (int index = 0; index < controllerParts.size(); index++) {
                helper.assertTrue(firstPage.getSubTabs().get(index) != secondPage.getSubTabs().get(index),
                        "Cleanroom reused a part page across openings: " + controllerDefinitions.get(index).getId());
            }

            TestCleanroomMachine alternateCleanroom = new TestCleanroomMachine(
                    List.of(supportedParts.get(2), supportedParts.get(4)));
            alternateCleanroom.setLevel(helper.getLevel());
            MutableMachineUIHolder alternateHolder = new MutableMachineUIHolder(alternateCleanroom);
            LDLib2FancyUIProvider alternatePage = alternateCleanroom.createLDLib2Page(player, alternateHolder);
            List<Component> alternateTitles = List.of(supportedDefinitions.get(2), supportedDefinitions.get(4))
                    .stream()
                    .map(MachineDefinition::getDescriptionId)
                    .<Component>map(Component::translatable)
                    .toList();
            helper.assertTrue(alternatePage.getSubTabs().stream().map(LDLib2FancyUIProvider::getTitle).toList()
                    .equals(alternateTitles) &&
                    createShell(player, alternateHolder, alternatePage).getChildren().getFirst().getChildren()
                            .size() == 3,
                    "Cleanroom did not expose its 16A energy or Auto Maintenance alternative pages");

            LDLib2FancyMachineUIElement firstShell = createShell(player, holder, firstPage);
            LDLib2FancyMachineUIElement secondShell = createShell(player, holder, secondPage);
            List<UIElement> configurators = firstShell.getConfiguratorPanel().getChildren();
            helper.assertTrue(configurators.size() == 2 && configurators.get(0).getChildren().size() == 2 &&
                    configurators.get(1).getChildren().size() == 1,
                    "Cleanroom did not preserve voiding then working configurators without batch mode");
            helper.assertTrue(firstShell.getSideTabsElement().getChildren().size() == 2,
                    "Cleanroom did not expose exactly one directional side page");
            helper.assertTrue(firstShell.getTooltipsPanel().getChildren().size() == 1,
                    "Cleanroom did not expose exactly the maintenance warning from its legal parts");

            UIElement firstContainer = firstShell.getChildren().getFirst();
            UIElement secondContainer = secondShell.getChildren().getFirst();
            helper.assertTrue(firstContainer.getChildren().size() == controllerParts.size() + 1 &&
                    secondContainer.getChildren().size() == controllerParts.size() + 1,
                    "Cleanroom did not build every legal part page");
            for (int index = 0; index < firstContainer.getChildren().size(); index++) {
                helper.assertTrue(firstContainer.getChildren().get(index) != secondContainer.getChildren().get(index),
                        "Cleanroom reused a controller or part element across openings");
            }
            assertMainPageLayout(helper, firstContainer.getChildren().getFirst(), cleanroom);

            clickButton(firstShell.getSideTabsElement().getChildren().get(1));
            clickButton(secondShell.getSideTabsElement().getChildren().get(1));
            helper.assertTrue(firstContainer.getChildren().size() == controllerParts.size() + 2 &&
                    secondContainer.getChildren().size() == controllerParts.size() + 2 &&
                    firstContainer.getChildren().getLast() != secondContainer.getChildren().getLast(),
                    "Cleanroom reused its directional page across openings");
            for (IMultiPart part : supportedParts) {
                assertPartPageUsesDedicatedHolder(helper, player, part);
            }
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void maintenanceTooltipFollowsConfiguration(GameTestHelper helper) {
        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        try {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            IMultiPart maintenance = requirePart(createMachine(GTMachines.MAINTENANCE_HATCH));
            TestCleanroomMachine cleanroom = new TestCleanroomMachine(List.of(maintenance));
            MutableMachineUIHolder holder = new MutableMachineUIHolder(cleanroom);

            ConfigHolder.INSTANCE.machines.enableMaintenance = true;
            LDLib2FancyMachineUIElement enabledShell = createShell(player, holder,
                    cleanroom.createLDLib2Page(player, holder));
            helper.assertTrue(enabledShell.getTooltipsPanel().getChildren().size() == 1,
                    "Cleanroom maintenance warning was not attached while maintenance was enabled");

            ConfigHolder.INSTANCE.machines.enableMaintenance = false;
            LDLib2FancyMachineUIElement disabledShell = createShell(player, holder,
                    cleanroom.createLDLib2Page(player, holder));
            helper.assertTrue(disabledShell.getTooltipsPanel().getChildren().isEmpty(),
                    "Cleanroom maintenance warning remained attached while maintenance was disabled");
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void displayBranchesAreCompleteAndImmutable(GameTestHelper helper) {
        long maxVoltage = GTValues.V[HV];
        CleanroomMachine.DisplayState activeState = new CleanroomMachine.DisplayState(
                true, maxVoltage, CleanroomType.CLEANROOM, true, true, true,
                40, 100, 40, true, 95, 7, 6, 9);
        List<Component> activeExpected = List.of(
                Component.translatable("gtpm.multiblock.max_energy_per_tick", maxVoltage, GTValues.VNF[HV]),
                Component.translatable(CleanroomType.CLEANROOM.translationKey()),
                Component.translatable("gtpm.multiblock.running"),
                Component.translatable("gtpm.multiblock.progress", String.format(Locale.ROOT, "%.2f", 2.0f),
                        String.format(Locale.ROOT, "%.2f", 5.0f), 40),
                Component.translatable("gtpm.multiblock.waiting")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)),
                Component.translatable("gtpm.multiblock.cleanroom.clean_state"),
                Component.translatable("gtpm.multiblock.cleanroom.clean_amount", 95),
                Component.translatable("gtpm.multiblock.dimensions.0"),
                Component.translatable("gtpm.multiblock.dimensions.1", 7, 6, 9));
        List<Component> active = CleanroomMachine.createDisplaySnapshot(activeState);
        helper.assertTrue(active.equals(activeExpected),
                "Cleanroom active display lost voltage, type, progress, waiting, cleanliness, or dimensions");

        boolean immutable = false;
        try {
            active.add(Component.empty());
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Cleanroom display snapshot remained mutable");

        CleanroomMachine.DisplayState pausedState = new CleanroomMachine.DisplayState(
                true, 0, null, false, true, true,
                80, 100, 80, false, 12, 5, 5, 5);
        List<Component> pausedExpected = List.of(
                Component.translatable("gtpm.multiblock.work_paused"),
                Component.translatable("gtpm.multiblock.waiting")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)),
                Component.translatable("gtpm.multiblock.cleanroom.dirty_state"),
                Component.translatable("gtpm.multiblock.cleanroom.clean_amount", 12),
                Component.translatable("gtpm.multiblock.dimensions.0"),
                Component.translatable("gtpm.multiblock.dimensions.1", 5, 5, 5));
        helper.assertTrue(CleanroomMachine.createDisplaySnapshot(pausedState).equals(pausedExpected),
                "Cleanroom paused display leaked active progress or optional voltage/type rows");

        CleanroomMachine.DisplayState idleState = new CleanroomMachine.DisplayState(
                true, maxVoltage, CleanroomType.STERILE_CLEANROOM, true, false, false,
                0, 100, 0, false, 0, 5, 5, 5);
        List<Component> idle = CleanroomMachine.createDisplaySnapshot(idleState);
        helper.assertTrue(idle.contains(Component.translatable("gtpm.multiblock.idling")) &&
                idle.contains(Component.translatable(CleanroomType.STERILE_CLEANROOM.translationKey())) &&
                !idle.contains(Component.translatable("gtpm.multiblock.running")),
                "Cleanroom idle or sterile-type display branch changed");

        helper.assertTrue(CleanroomMachine.createDisplaySnapshot(new CleanroomMachine.DisplayState(
                false, maxVoltage, CleanroomType.CLEANROOM, true, true, true,
                40, 100, 40, true, 95, 7, 6, 9)).equals(List.of(invalidStructureLine())),
                "Cleanroom invalid structure display leaked formed state or lost its hover text");

        TestCleanroomMachine liveCleanroom = new TestCleanroomMachine(List.of());
        liveCleanroom.setLevel(helper.getLevel());
        liveCleanroom.useLiveDisplayContract();
        try {
            liveCleanroom.onLoad();
            liveCleanroom.formStructure(CleanroomMachine.DEFAULT_STRUCTURE);
            liveCleanroom.getRecipeLogic().setDuration(100);
            liveCleanroom.getRecipeLogic().setProgress(40);
            liveCleanroom.getRecipeLogic().setStatus(WorkLogic.Status.WAITING);
            liveCleanroom.adjustCleanAmount(CleanroomMachine.CLEAN_AMOUNT_THRESHOLD);
            liveCleanroom.refreshDisplaySnapshot();
            long liveVoltage = liveCleanroom.getMaxVoltage();
            List<Component> liveExpected = List.of(
                    Component.translatable("gtpm.multiblock.max_energy_per_tick", liveVoltage, GTValues.VNF[0]),
                    Component.translatable(CleanroomType.CLEANROOM.translationKey()),
                    Component.translatable("gtpm.multiblock.running"),
                    Component.translatable("gtpm.multiblock.progress", "2.00", "5.00", 40),
                    Component.translatable("gtpm.multiblock.waiting")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)),
                    Component.translatable("gtpm.multiblock.cleanroom.clean_state"),
                    Component.translatable("gtpm.multiblock.cleanroom.clean_amount",
                            CleanroomMachine.CLEAN_AMOUNT_THRESHOLD),
                    Component.translatable("gtpm.multiblock.dimensions.0"),
                    Component.translatable("gtpm.multiblock.dimensions.1", 1, 1, 1));
            helper.assertTrue(liveCleanroom.getDisplaySnapshot().equals(liveExpected),
                    "Cleanroom live capture mis-mapped its formed type, cleanliness, voltage, or dimensions");
        } finally {
            liveCleanroom.onUnload();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void serverAndClientLifecycleOwnDisplaySubscription(GameTestHelper helper) {
        TestCleanroomMachine formed = new TestCleanroomMachine(List.of());
        formed.setLevel(helper.getLevel());
        formed.setServerDisplay(List.of(FIRST_SERVER_LINE));
        formed.onLoad();
        formed.formStructure(CleanroomMachine.DEFAULT_STRUCTURE);
        TickableSubscription formedSubscription = formed.getCapturedDisplaySubscription();
        helper.assertTrue(formed.getDisplaySnapshot().equals(List.of(FIRST_SERVER_LINE)) &&
                formedSubscription != null && formedSubscription.isStillSubscribed(),
                "Cleanroom formation did not publish and subscribe its server display snapshot");
        formed.setServerDisplay(List.of(SECOND_SERVER_LINE));
        formedSubscription.run();
        helper.assertTrue(formed.getDisplaySnapshot().equals(List.of(SECOND_SERVER_LINE)),
                "Cleanroom server tick did not publish changed display text");
        formed.useLiveDisplayContract();
        formed.invalidateStructure(CleanroomMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(formed.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                !formedSubscription.isStillSubscribed(),
                "Cleanroom invalidation did not publish invalid text and stop its subscription");

        TestCleanroomMachine partUnload = subscribedCleanroom(helper);
        TickableSubscription partSubscription = partUnload.getCapturedDisplaySubscription();
        try {
            partUnload.onPartUnload();
            assertRuntimeDisplayCleared(helper, partUnload, partSubscription, "part unload");
            partUnload.setServerDisplay(List.of(SECOND_SERVER_LINE));
            partUnload.formStructure(CleanroomMachine.DEFAULT_STRUCTURE);
            TickableSubscription recoveredPartSubscription = partUnload.getCapturedDisplaySubscription();
            helper.assertTrue(recoveredPartSubscription != null && recoveredPartSubscription != partSubscription &&
                    recoveredPartSubscription.isStillSubscribed() &&
                    partUnload.getDisplaySnapshot().equals(List.of(SECOND_SERVER_LINE)),
                    "Cleanroom did not restore display refresh after a part-unload structure recovery");
        } finally {
            partUnload.onUnload();
        }

        TestCleanroomMachine controllerUnload = subscribedCleanroom(helper);
        TickableSubscription controllerSubscription = controllerUnload.getCapturedDisplaySubscription();
        controllerUnload.onUnload();
        assertRuntimeDisplayCleared(helper, controllerUnload, controllerSubscription, "controller unload");

        TestCleanroomMachine clientCleanroom = new TestCleanroomMachine(List.of());
        clientCleanroom.setLevel(helper.getLevel());
        clientCleanroom.setRemoteForTest(true);
        clientCleanroom.setFormedForTest(true);
        clientCleanroom.setServerDisplay(List.of(FIRST_SERVER_LINE));
        clientCleanroom.onLoad();
        helper.assertTrue(clientCleanroom.getDisplayCaptureCount() == 0 &&
                clientCleanroom.getCapturedDisplaySubscription() == null &&
                clientCleanroom.getDisplaySnapshot().isEmpty(),
                "Cleanroom client lifecycle sampled or subscribed to server display state");
        clientCleanroom.onUnload();

        TestCleanroomMachine unloadedBeforeInitialization = new TestCleanroomMachine(List.of());
        unloadedBeforeInitialization.setLevel(helper.getLevel());
        unloadedBeforeInitialization.setFormedForTest(true);
        unloadedBeforeInitialization.setServerDisplay(List.of(FIRST_SERVER_LINE));
        unloadedBeforeInitialization.onLoad();
        unloadedBeforeInitialization.onUnload();

        TestCleanroomMachine loadedCleanroom = new TestCleanroomMachine(List.of());
        loadedCleanroom.setLevel(helper.getLevel());
        loadedCleanroom.setFormedForTest(true);
        loadedCleanroom.setServerDisplay(List.of(FIRST_SERVER_LINE));
        loadedCleanroom.onLoad();
        int refreshesAfterLoad = loadedCleanroom.getDisplayRefreshCount();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(unloadedBeforeInitialization.getCapturedDisplaySubscription() == null &&
                    unloadedBeforeInitialization.getDisplaySnapshot().isEmpty(),
                    "Cleanroom delayed initialization recreated display state after unload");
            TickableSubscription loadedSubscription = loadedCleanroom.getCapturedDisplaySubscription();
            helper.assertTrue(loadedSubscription != null && loadedSubscription.isStillSubscribed(),
                    "Formed Cleanroom onLoad did not initialize delayed display refresh");
            loadedCleanroom.setServerDisplay(List.of(SECOND_SERVER_LINE));
            loadedSubscription.run();
            helper.assertTrue(loadedCleanroom.getDisplayRefreshCount() > refreshesAfterLoad &&
                    loadedCleanroom.getDisplaySnapshot().equals(List.of(SECOND_SERVER_LINE)),
                    "Cleanroom display subscription did not publish changed server text");
            int refreshesBeforeUnload = loadedCleanroom.getDisplayRefreshCount();
            loadedCleanroom.onUnload();
            loadedSubscription.run();
            helper.assertTrue(!loadedSubscription.isStillSubscribed() &&
                    loadedCleanroom.getDisplayRefreshCount() == refreshesBeforeUnload &&
                    loadedCleanroom.getDisplaySnapshot().isEmpty(),
                    "Cleanroom refresh or snapshot survived controller unload");
            helper.succeed();
        });
    }

    private static void assertMainPageLayout(GameTestHelper helper, UIElement mainPage,
                                             TestCleanroomMachine cleanroom) {
        helper.assertTrue(mainPage.getSizeWidth() == 190 && mainPage.getSizeHeight() == 125 &&
                mainPage.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.BACKGROUND_INVERSE,
                "Cleanroom main page lost its 190x125 inverse-background body");
        helper.assertTrue(mainPage.getChildren().size() == 1 &&
                mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                "Cleanroom main page did not create one display scroller");
        GTScrollerViewElement scroller = (GTScrollerViewElement) mainPage.getChildren().getFirst();
        helper.assertTrue(scroller.getLayoutX() == 4 && scroller.getLayoutY() == 4 &&
                scroller.getSizeWidth() == 182 && scroller.getSizeHeight() == 117 &&
                scroller.getStyle().getInline(PropertyRegistry.BACKGROUND) == cleanroom.getScreenTexture() &&
                scroller.viewPort.getStyle().getInline(PropertyRegistry.BACKGROUND) == cleanroom.getScreenTexture() &&
                scroller.getScrollerViewStyle().mode() == ScrollerMode.VERTICAL &&
                scroller.getScrollerViewStyle().verticalScrollDisplay() == ScrollDisplay.AUTO &&
                scroller.getScrollerViewStyle().horizontalScrollDisplay() == ScrollDisplay.NEVER,
                "Cleanroom display scroller lost its bounds, texture, or vertical-only scrolling");

        List<GTLabelElement> labels = descendants(mainPage).stream()
                .filter(GTLabelElement.class::isInstance)
                .map(GTLabelElement.class::cast)
                .toList();
        List<GTComponentPanelElement> panels = descendants(mainPage).stream()
                .filter(GTComponentPanelElement.class::isInstance)
                .map(GTComponentPanelElement.class::cast)
                .toList();
        helper.assertTrue(labels.size() == 1 && labels.getFirst().getLayoutX() == 4 &&
                labels.getFirst().getLayoutY() == 5 && labels.getFirst().getSizeWidth() == 174 &&
                labels.getFirst().getSizeHeight() == 10 &&
                labels.getFirst().getTextStyle().textColor() == 0x404040 &&
                !labels.getFirst().getTextStyle().textShadow() &&
                labels.getFirst().getTextStyle().textAlignHorizontal() == Horizontal.LEFT &&
                labels.getFirst().getTextStyle().textAlignVertical() == Vertical.CENTER &&
                panels.size() == 1 && panels.getFirst().getLayoutX() == 4 &&
                panels.getFirst().getLayoutY() == 17 && panels.getFirst().getMaxWidthLimit() == 200 &&
                panels.getFirst().getLastText().equals(cleanroom.getDisplaySnapshot()),
                "Cleanroom title or snapshot panel lost its legacy bounds");
    }

    private static boolean createUIFails(CleanroomMachine cleanroom, ServerPlayer player,
                                         MachineUIHolder holder) {
        try {
            cleanroom.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static void assertPartPageUsesDedicatedHolder(GameTestHelper helper, ServerPlayer player,
                                                          IMultiPart part) {
        if (!(part instanceof LDLib2FancyPartUIProvider provider)) {
            throw new IllegalStateException("Legal Cleanroom part has no LDLib2 Fancy page.");
        }
        MutableMachineUIHolder holder = new MutableMachineUIHolder(part.self());
        LDLib2FancyUIProvider page = provider.createLDLib2FancyPage(player, holder);
        helper.assertTrue(createShell(player, holder, page).getHolder() == holder,
                "Cleanroom part page lost its dedicated holder: " + part.self().getDefinition().getId());
    }

    private static TestCleanroomMachine subscribedCleanroom(GameTestHelper helper) {
        TestCleanroomMachine cleanroom = new TestCleanroomMachine(List.of());
        cleanroom.setLevel(helper.getLevel());
        cleanroom.setFormedForTest(true);
        cleanroom.setServerDisplay(List.of(FIRST_SERVER_LINE));
        cleanroom.onLoad();
        cleanroom.getDisplaySnapshotSubscription().updateSubscription();
        return cleanroom;
    }

    private static void assertRuntimeDisplayCleared(GameTestHelper helper, TestCleanroomMachine cleanroom,
                                                    @Nullable TickableSubscription subscription,
                                                    String lifecycleEvent) {
        helper.assertTrue(cleanroom.getDisplaySnapshot().isEmpty(),
                "Cleanroom retained display text after " + lifecycleEvent);
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Cleanroom retained its display subscription after " + lifecycleEvent);
    }

    private static Component invalidStructureLine() {
        Component tooltip = Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                .withStyle(ChatFormatting.GRAY);
        return Component.translatable("gtpm.multiblock.invalid_structure")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip)));
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
    }

    private static MetaMachine placeMachine(GameTestHelper helper, BlockPos pos, MachineDefinition definition) {
        helper.setBlock(pos, definition.getBlock());
        if (!(helper.getBlockEntity(pos) instanceof MetaMachine machine)) {
            throw new IllegalStateException("Placed block did not create its expected machine: " +
                    definition.getId());
        }
        return machine;
    }

    private static MetaMachine createMachine(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine == null) {
            throw new IllegalStateException("Machine definition did not create a MetaMachine: " + definition.getId());
        }
        return machine;
    }

    private static CleanroomMachine requireCleanroom(MetaMachine machine) {
        if (!(machine instanceof CleanroomMachine cleanroom)) {
            throw new IllegalStateException("Cleanroom definition did not create its expected controller.");
        }
        return cleanroom;
    }

    private static IMultiPart requirePart(MetaMachine machine) {
        if (!(machine instanceof IMultiPart part)) {
            throw new IllegalStateException("Expected a multiblock part machine.");
        }
        return part;
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

    private static void clickButton(UIElement button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = button;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static BlockEntityCreationInfo info() {
        MachineDefinition definition = GTMultiMachines.CLEANROOM;
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
            this.machine = machine;
        }

        private void setMachine(MetaMachine machine) {
            this.machine = machine;
        }

        @Override
        public BlockPos getPos() {
            return machine.getBlockPos();
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return machine.getDefinition().getId();
        }

        @Override
        public MetaMachine getMachine() {
            return machine;
        }
    }

    private static final class TestCleanroomMachine extends CleanroomMachine {

        private final List<IMultiPart> parts;
        private List<Component> serverDisplay = List.of();
        private IGuiTexture screenTexture = GuiTextures.DISPLAY;
        private @Nullable TickableSubscription capturedDisplaySubscription;
        private boolean useLiveDisplayContract;
        private boolean remote;
        private int displayCaptureCount;
        private int displayRefreshCount;

        private TestCleanroomMachine(List<IMultiPart> parts) {
            super(info());
            this.parts = List.copyOf(parts);
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
        }

        @Override
        public boolean isRemote() {
            return remote || super.isRemote();
        }

        @Override
        public IGuiTexture getScreenTexture() {
            return screenTexture;
        }

        @Override
        protected void collectServerDisplayText(List<Component> textList) {
            displayCaptureCount++;
            if (useLiveDisplayContract) {
                super.collectServerDisplayText(textList);
            } else {
                textList.addAll(serverDisplay);
            }
        }

        @Override
        void refreshDisplaySnapshot() {
            displayRefreshCount++;
            super.refreshDisplaySnapshot();
        }

        @Override
        public TickableSubscription subscribeServerTick(Runnable runnable) {
            TickableSubscription subscription = super.subscribeServerTick(runnable);
            if (subscription == null) {
                throw new IllegalStateException("Server-side Cleanroom display test did not create a subscription.");
            }
            capturedDisplaySubscription = subscription;
            return subscription;
        }

        private void setServerDisplay(List<Component> serverDisplay) {
            this.serverDisplay = List.copyOf(serverDisplay);
            useLiveDisplayContract = false;
        }

        private void useLiveDisplayContract() {
            useLiveDisplayContract = true;
        }

        private void setFormedForTest(boolean formed) {
            isFormed = formed;
        }

        private void setRemoteForTest(boolean remote) {
            this.remote = remote;
        }

        private void setScreenTexture(IGuiTexture screenTexture) {
            this.screenTexture = screenTexture;
        }

        private @Nullable TickableSubscription getCapturedDisplaySubscription() {
            return capturedDisplaySubscription;
        }

        private int getDisplayCaptureCount() {
            return displayCaptureCount;
        }

        private int getDisplayRefreshCount() {
            return displayRefreshCount;
        }
    }
}
