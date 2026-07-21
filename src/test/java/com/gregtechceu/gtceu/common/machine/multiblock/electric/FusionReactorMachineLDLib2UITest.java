package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.common.block.FusionCasingBlock;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

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

import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.LuV;
import static com.gregtechceu.gtceu.api.GTValues.UV;
import static com.gregtechceu.gtceu.api.GTValues.ZPM;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class FusionReactorMachineLDLib2UITest {

    private static final String BATCH = "FusionReactorMachineLDLib2UI";
    private static final Component FIRST_SERVER_LINE = Component.literal("first Fusion Reactor server line");
    private static final Component SECOND_SERVER_LINE = Component.literal("second Fusion Reactor server line");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void registeredTiersKeepFusionSemanticsAndLDLib2Contract(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        int[] tiers = { LuV, ZPM, UV };
        long[] capacities = { 160_000_000L, 320_000_000L, 640_000_000L };

        for (int index = 0; index < tiers.length; index++) {
            int tier = tiers[index];
            MetaMachine registered = createMachine(GTMultiMachines.FUSION_REACTOR[tier]);
            helper.assertTrue(registered.getClass() == FusionReactorMachine.class &&
                    registered instanceof LDLib2MachineUIProvider &&
                    registered instanceof LDLib2FancyActionMachine,
                    "Fusion tier did not create its concrete LDLib2 controller: " + tier);
            FusionReactorMachine reactor = (FusionReactorMachine) registered;
            MutableMachineUIHolder holder = new MutableMachineUIHolder(reactor);
            helper.assertTrue(reactor.getTier() == tier && reactor.getRecipeTypes().length == 1 &&
                    reactor.getRecipeType() == GTRecipeTypes.FUSION_RECIPES && reactor.supportsBatchMode() &&
                    FusionReactorMachine.calculateEnergyStorageFactor(tier, 16) == capacities[index],
                    "Fusion tier, recipe type, batch modifier, or energy capacity changed: " + tier);
            helper.assertTrue(reactor.canCreateLDLib2UI(player, holder) &&
                    reactor.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement,
                    "Fusion tier rejected its matching holder or lost its Fancy shell: " + tier);
        }

        helper.assertTrue(FusionReactorMachine.getCasingState(LuV) == GTBlocks.FUSION_CASING.get() &&
                FusionReactorMachine.getCasingState(ZPM) == GTBlocks.FUSION_CASING_MK2.get() &&
                FusionReactorMachine.getCasingState(UV) == GTBlocks.FUSION_CASING_MK3.get() &&
                FusionReactorMachine.getCoilState(LuV) == GTBlocks.SUPERCONDUCTING_COIL.get() &&
                FusionReactorMachine.getCoilState(ZPM) == GTBlocks.FUSION_COIL.get() &&
                FusionReactorMachine.getCoilState(UV) == GTBlocks.FUSION_COIL.get(),
                "Fusion casing or coil tier mapping changed");
        helper.assertTrue(FusionReactorMachine.getCasingType(LuV) ==
                FusionCasingBlock.CasingType.FUSION_CASING &&
                FusionReactorMachine.getCasingType(ZPM) == FusionCasingBlock.CasingType.FUSION_CASING_MK2 &&
                FusionReactorMachine.getCasingType(UV) == FusionCasingBlock.CasingType.FUSION_CASING_MK3,
                "Fusion renderer casing type mapping changed");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void holderIdentityStalePagesAndUnsupportedPartsFailFast(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        FusionReactorMachine reactor = requireFusion(createMachine(GTMultiMachines.FUSION_REACTOR[LuV]));
        MutableMachineUIHolder holder = new MutableMachineUIHolder(reactor);

        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(createMachine(GTMachines.MACERATOR[LV]));
        helper.assertTrue(!reactor.canCreateLDLib2UI(player, wrongHolder) &&
                createUIFails(reactor, player, wrongHolder),
                "Fusion Reactor accepted a holder for another machine");

        FusionReactorMachine replacement = requireFusion(createMachine(GTMultiMachines.FUSION_REACTOR[LuV]));
        MutableMachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);
        helper.assertTrue(!reactor.canCreateLDLib2UI(player, replacementHolder) &&
                createUIFails(reactor, player, replacementHolder),
                "Fusion Reactor accepted another same-definition controller instance");

        LDLib2FancyUIProvider stalePage = reactor.createLDLib2Page(player, holder);
        holder.setMachine(replacement);
        boolean staleRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            staleRejected = expected.getMessage().contains("page holder");
        }
        helper.assertTrue(staleRejected,
                "Fusion Reactor page accepted a same-definition replacement after opening");

        IMultiPart unsupportedPart = requirePart(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestFusionReactorMachine invalidReactor = new TestFusionReactorMachine(LuV, List.of(unsupportedPart));
        helper.assertTrue(createUIFails(invalidReactor, player,
                new MutableMachineUIHolder(invalidReactor)),
                "Fusion Reactor silently omitted a part without an LDLib2 Fancy page");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = BATCH, timeoutTicks = 200)
    public static void legalPartFamiliesKeepOpeningScopedPagesConfiguratorsAndLayout(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        List<MachineDefinition> definitions = List.of(
                GTMachines.ENERGY_INPUT_HATCH[LuV],
                GTMachines.ENERGY_INPUT_HATCH_4A[LuV],
                GTMachines.ENERGY_INPUT_HATCH_16A[LuV],
                GTMachines.FLUID_IMPORT_HATCH[LuV],
                GTMachines.FLUID_IMPORT_HATCH_4X[LuV],
                GTMachines.FLUID_IMPORT_HATCH_9X[LuV],
                GTMachines.RESERVOIR_HATCH,
                GTMachines.DUAL_IMPORT_HATCH[LuV],
                GTAEMachines.FLUID_IMPORT_HATCH_ME,
                GTAEMachines.STOCKING_IMPORT_HATCH_ME,
                GTAEMachines.ME_PATTERN_BUFFER,
                GTAEMachines.ME_PATTERN_BUFFER_PROXY,
                GTMachines.FLUID_EXPORT_HATCH[LuV],
                GTMachines.FLUID_EXPORT_HATCH_4X[LuV],
                GTMachines.FLUID_EXPORT_HATCH_9X[LuV],
                GTMachines.DUAL_EXPORT_HATCH[LuV],
                GTAEMachines.FLUID_EXPORT_HATCH_ME);
        List<IMultiPart> parts = new ArrayList<>(definitions.size());
        for (int index = 0; index < definitions.size(); index++) {
            MachineDefinition definition = definitions.get(index);
            IMultiPart part = requirePart(placeMachine(helper,
                    new BlockPos(index % 5, 1, index / 5), definition));
            helper.assertTrue(part instanceof LDLib2FancyPartUIProvider,
                    "Legal Fusion part has no LDLib2 Fancy provider: " + definition.getId());
            parts.add(part);
        }

        TestFusionReactorMachine reactor = new TestFusionReactorMachine(LuV, parts);
        reactor.setLevel(helper.getLevel());
        reactor.setDisplayState(true, 12_345L, 160_000_000L, 67_890L);
        reactor.useLiveDisplayContract();
        reactor.refreshDisplaySnapshot();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(reactor);
        LDLib2FancyUIProvider firstPage = reactor.createLDLib2Page(player, holder);
        LDLib2FancyUIProvider secondPage = reactor.createLDLib2Page(player, holder);

        List<Component> expectedTitles = parts.stream()
                .map(IMultiPart::self)
                .map(MetaMachine::getDefinition)
                .map(MachineDefinition::getDescriptionId)
                .<Component>map(Component::translatable)
                .toList();
        helper.assertTrue(firstPage != secondPage && firstPage.getSubTabs().size() == parts.size() &&
                firstPage.getSubTabs().stream().map(LDLib2FancyUIProvider::getTitle).toList()
                        .equals(expectedTitles),
                "Fusion Reactor omitted or reordered a legal contextual part page");
        for (int index = 0; index < parts.size(); index++) {
            helper.assertTrue(firstPage.getSubTabs().get(index) != secondPage.getSubTabs().get(index),
                    "Fusion Reactor reused a part page across openings: " + definitions.get(index).getId());
        }

        LDLib2FancyMachineUIElement firstShell = createShell(player, holder, firstPage);
        LDLib2FancyMachineUIElement secondShell = createShell(player, holder, secondPage);
        List<UIElement> configuratorTabs = firstShell.getConfiguratorPanel().getChildren();
        helper.assertTrue(configuratorTabs.size() == 3 &&
                configuratorTabs.get(0).getChildren().size() == 1 &&
                configuratorTabs.get(1).getChildren().size() == 1 &&
                configuratorTabs.get(2).getChildren().size() == 1 &&
                firstShell.getSideTabsElement().getChildren().size() == 2 &&
                firstShell.getTooltipsPanel().getChildren().isEmpty(),
                "Fusion Reactor did not expose voiding, batch, working, and one directional page");

        UIElement firstContainer = firstShell.getChildren().getFirst();
        UIElement secondContainer = secondShell.getChildren().getFirst();
        helper.assertTrue(firstContainer.getChildren().size() == parts.size() + 1 &&
                secondContainer.getChildren().size() == parts.size() + 1,
                "Fusion Reactor did not build every legal part page");
        for (int index = 0; index < firstContainer.getChildren().size(); index++) {
            helper.assertTrue(firstContainer.getChildren().get(index) != secondContainer.getChildren().get(index),
                    "Fusion Reactor reused a controller or part element across openings");
        }
        assertMainPageLayout(helper, firstContainer.getChildren().getFirst(), reactor);

        clickButton(firstShell.getSideTabsElement().getChildren().get(1));
        clickButton(secondShell.getSideTabsElement().getChildren().get(1));
        helper.assertTrue(firstContainer.getChildren().size() == parts.size() + 2 &&
                secondContainer.getChildren().size() == parts.size() + 2 &&
                firstContainer.getChildren().getLast() != secondContainer.getChildren().getLast(),
                "Fusion Reactor reused its directional page across openings");
        for (IMultiPart part : parts) {
            assertPartPageUsesDedicatedHolder(helper, player, part);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fusionDisplayBranchIsImmutableAndRefreshVisible(GameTestHelper helper) {
        FusionReactorMachine.DisplayState state = new FusionReactorMachine.DisplayState(
                true, 12_345L, 160_000_000L, 67_890L);
        List<Component> expected = List.of(
                Component.translatable("gtpm.multiblock.fusion_reactor.energy", 12_345L, 160_000_000L),
                Component.translatable("gtpm.multiblock.fusion_reactor.heat", 67_890L));
        List<Component> specialized = FusionReactorMachine.createFusionDisplayText(state);
        helper.assertTrue(specialized.equals(expected) &&
                FusionReactorMachine.createFusionDisplayText(
                        new FusionReactorMachine.DisplayState(false, 1, 2, 3)).isEmpty(),
                "Fusion display changed its formed energy/heat branch or added text while unformed");
        boolean immutable = false;
        try {
            specialized.add(Component.empty());
        } catch (UnsupportedOperationException expectedException) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Fusion display branch returned a mutable list");

        TestFusionReactorMachine reactor = new TestFusionReactorMachine(LuV, List.of());
        reactor.setDisplayState(true, 12_345L, 160_000_000L, 67_890L);
        reactor.useLiveDisplayContract();
        FusionReactorMachine.DisplayState captured = reactor.captureDisplayState();
        reactor.refreshDisplaySnapshot();
        List<Component> firstSnapshot = reactor.getDisplaySnapshot();
        reactor.refreshDisplaySnapshot();
        helper.assertTrue(captured.equals(state) && reactor.getDisplaySnapshot() == firstSnapshot &&
                reactor.getDisplaySnapshot().subList(
                        reactor.getDisplaySnapshot().size() - expected.size(),
                        reactor.getDisplaySnapshot().size()).equals(expected),
                "Fusion snapshot failed to capture its specialized state or replaced equal immutable data");

        List<Component> clientText = new ArrayList<>();
        int capturesBeforeClientRead = reactor.getDisplayCaptureCount();
        reactor.setRemoteForTest(true);
        reactor.setDisplayState(true, 99L, 100L, 101L);
        reactor.addDisplayText(clientText);
        helper.assertTrue(clientText.equals(firstSnapshot) &&
                reactor.getDisplayCaptureCount() == capturesBeforeClientRead,
                "Fusion client display sampled live energy or heat instead of its synced snapshot");

        reactor.setRemoteForTest(false);
        reactor.refreshDisplaySnapshot();
        List<Component> refreshed = new ArrayList<>();
        reactor.addDisplayText(refreshed);
        List<Component> changedSuffix = List.of(
                Component.translatable("gtpm.multiblock.fusion_reactor.energy", 99L, 100L),
                Component.translatable("gtpm.multiblock.fusion_reactor.heat", 101L));
        helper.assertTrue(refreshed.subList(refreshed.size() - changedSuffix.size(), refreshed.size())
                .equals(changedSuffix),
                "Fusion server refresh did not publish changed energy and heat");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void serverAndClientLifecycleOwnDisplaySubscription(GameTestHelper helper) {
        TestFusionReactorMachine reactor = new TestFusionReactorMachine(LuV, List.of());
        reactor.setLevel(helper.getLevel());
        reactor.useLiveDisplayContract();
        reactor.onLoad();
        helper.assertTrue(reactor.getDisplaySnapshot().equals(List.of(invalidStructureLine())),
                "Fusion onLoad omitted the base invalid_structure display");

        reactor.formStructure(FusionReactorMachine.DEFAULT_STRUCTURE);
        TickableSubscription formedSubscription = reactor.getCapturedDisplaySubscription();
        helper.assertTrue(reactor.isFormed() && formedSubscription != null &&
                formedSubscription.isStillSubscribed() &&
                !reactor.getDisplaySnapshot().equals(List.of(invalidStructureLine())),
                "Fusion formation did not publish and subscribe its display snapshot");
        reactor.invalidateStructure(FusionReactorMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(reactor.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                !formedSubscription.isStillSubscribed(),
                "Fusion invalidation did not publish invalid_structure and stop refresh");

        TestFusionReactorMachine partUnloadReactor = subscribedReactor(helper);
        TickableSubscription partSubscription = partUnloadReactor.getCapturedDisplaySubscription();
        partUnloadReactor.onPartUnload();
        assertRuntimeDisplayCleared(helper, partUnloadReactor, partSubscription, "part unload");
        TestFusionReactorMachine controllerUnloadReactor = subscribedReactor(helper);
        TickableSubscription controllerSubscription = controllerUnloadReactor.getCapturedDisplaySubscription();
        controllerUnloadReactor.onUnload();
        assertRuntimeDisplayCleared(helper, controllerUnloadReactor, controllerSubscription, "controller unload");

        TestFusionReactorMachine clientReactor = new TestFusionReactorMachine(LuV, List.of());
        clientReactor.setLevel(helper.getLevel());
        clientReactor.setRemoteForTest(true);
        clientReactor.useLiveDisplayContract();
        clientReactor.onLoad();
        clientReactor.formStructure(FusionReactorMachine.DEFAULT_STRUCTURE);
        clientReactor.invalidateStructure(FusionReactorMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(clientReactor.getDisplayCaptureCount() == 0 &&
                clientReactor.getCapturedDisplaySubscription() == null &&
                clientReactor.getDisplaySnapshot().isEmpty(),
                "Fusion client lifecycle sampled or subscribed to server display state");
        clientReactor.onUnload();

        TestFusionReactorMachine loadedReactor = new TestFusionReactorMachine(UV, List.of());
        loadedReactor.setLevel(helper.getLevel());
        loadedReactor.setFormedForTest(true);
        loadedReactor.setServerDisplay(List.of(FIRST_SERVER_LINE));
        loadedReactor.onLoad();
        int refreshesAfterLoad = loadedReactor.getDisplayRefreshCount();
        helper.runAfterDelay(3, () -> {
            TickableSubscription subscription = loadedReactor.getCapturedDisplaySubscription();
            helper.assertTrue(subscription != null && subscription.isStillSubscribed(),
                    "Formed Fusion onLoad did not initialize delayed display refresh");
            loadedReactor.setServerDisplay(List.of(SECOND_SERVER_LINE));
            subscription.run();
            helper.assertTrue(loadedReactor.getDisplayRefreshCount() > refreshesAfterLoad &&
                    loadedReactor.getDisplaySnapshot().equals(List.of(SECOND_SERVER_LINE)),
                    "Fusion display subscription did not publish changed server text");
            int refreshesBeforeUnload = loadedReactor.getDisplayRefreshCount();
            loadedReactor.onUnload();
            subscription.run();
            helper.assertTrue(!subscription.isStillSubscribed() &&
                    loadedReactor.getDisplayRefreshCount() == refreshesBeforeUnload &&
                    loadedReactor.getDisplaySnapshot().isEmpty(),
                    "Fusion refresh or snapshot survived controller unload");
            helper.succeed();
        });
    }

    private static void assertMainPageLayout(GameTestHelper helper, UIElement mainPage,
                                             FusionReactorMachine reactor) {
        UITemplate.LDLib2Bounds mainPageBounds = UITemplate.getLDLib2Bounds(mainPage);
        helper.assertTrue(mainPageBounds.width() == 190 && mainPageBounds.height() == 125,
                "Fusion main page lost its 190x125 body");
        helper.assertTrue(mainPage.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.BACKGROUND_INVERSE,
                "Fusion main page lost its inverse background");
        helper.assertTrue(mainPage.getChildren().size() == 1 &&
                mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                "Fusion main page did not create one display scroller");
        GTScrollerViewElement scroller = (GTScrollerViewElement) mainPage.getChildren().getFirst();
        UITemplate.LDLib2Bounds scrollerBounds = UITemplate.getLDLib2Bounds(scroller);
        helper.assertTrue(scrollerBounds.x() == 4 &&
                scrollerBounds.y() == 4 &&
                scrollerBounds.width() == 182 &&
                scrollerBounds.height() == 117,
                "Fusion display scroller lost its bounds");
        helper.assertTrue(scroller.getStyle().getInline(PropertyRegistry.BACKGROUND) == reactor.getScreenTexture(),
                "Fusion display scroller lost its texture");
        helper.assertTrue(
                scroller.viewPort.getStyle().getInline(PropertyRegistry.BACKGROUND) == reactor.getScreenTexture(),
                "Fusion display viewport lost its texture");
        helper.assertTrue(UITemplate.getLDLib2StyleCandidate(scroller, PropertyRegistry.SCROLLER_VIEW_MODE) ==
                ScrollerMode.VERTICAL,
                "Fusion display scroller changed its scroll mode");
        helper.assertTrue(UITemplate.getLDLib2StyleCandidate(scroller, PropertyRegistry.SCROLLER_VERTICAL_DISPLAY) ==
                ScrollDisplay.AUTO,
                "Fusion display scroller changed its vertical scrollbar policy");
        helper.assertTrue(UITemplate.getLDLib2StyleCandidate(scroller, PropertyRegistry.SCROLLER_HORIZONTAL_DISPLAY) ==
                ScrollDisplay.NEVER,
                "Fusion display scroller enabled horizontal scrolling");
        List<GTLabelElement> labels = descendants(mainPage).stream()
                .filter(GTLabelElement.class::isInstance)
                .map(GTLabelElement.class::cast)
                .toList();
        List<GTComponentPanelElement> panels = descendants(mainPage).stream()
                .filter(GTComponentPanelElement.class::isInstance)
                .map(GTComponentPanelElement.class::cast)
                .toList();
        UITemplate.LDLib2Bounds labelBounds = UITemplate.getLDLib2Bounds(labels.getFirst());
        UITemplate.LDLib2Bounds panelBounds = UITemplate.getLDLib2Bounds(panels.getFirst());
        helper.assertTrue(labels.size() == 1 && labelBounds.x() == 4 &&
                labelBounds.y() == 5 &&
                labelBounds.width() == 174 &&
                labelBounds.height() == 10 &&
                panels.size() == 1 && panelBounds.x() == 4 &&
                panelBounds.y() == 17 &&
                panels.getFirst().getMaxWidthLimit() == 200 &&
                panels.getFirst().getLastText().equals(reactor.getDisplaySnapshot()),
                "Fusion title or snapshot panel lost its legacy bounds");
        helper.assertTrue(Integer.valueOf(0x404040).equals(
                labels.getFirst().getTextStyle().getInline(PropertyRegistry.TEXT_COLOR)) &&
                Boolean.FALSE.equals(labels.getFirst().getTextStyle().getInline(PropertyRegistry.TEXT_SHADOW)) &&
                Horizontal.LEFT == labels.getFirst().getTextStyle().getInline(PropertyRegistry.HORIZONTAL_ALIGN) &&
                Vertical.CENTER == labels.getFirst().getTextStyle().getInline(PropertyRegistry.VERTICAL_ALIGN),
                "Fusion title lost its text style");
    }

    private static boolean createUIFails(FusionReactorMachine reactor, ServerPlayer player,
                                         MachineUIHolder holder) {
        try {
            reactor.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static void assertPartPageUsesDedicatedHolder(GameTestHelper helper, ServerPlayer player,
                                                          IMultiPart part) {
        if (!(part instanceof LDLib2FancyPartUIProvider provider)) {
            throw new IllegalStateException("Legal Fusion part has no LDLib2 Fancy page.");
        }
        MutableMachineUIHolder holder = new MutableMachineUIHolder(part.self());
        LDLib2FancyUIProvider page = provider.createLDLib2FancyPage(player, holder);
        helper.assertTrue(createShell(player, holder, page).getHolder() == holder,
                "Fusion part page lost its dedicated holder: " + part.self().getDefinition().getId());
    }

    private static TestFusionReactorMachine subscribedReactor(GameTestHelper helper) {
        TestFusionReactorMachine reactor = new TestFusionReactorMachine(LuV, List.of());
        reactor.setLevel(helper.getLevel());
        reactor.setFormedForTest(true);
        reactor.setServerDisplay(List.of(FIRST_SERVER_LINE));
        reactor.refreshDisplaySnapshot();
        reactor.getDisplaySnapshotSubscription().updateSubscription();
        return reactor;
    }

    private static void assertRuntimeDisplayCleared(GameTestHelper helper, TestFusionReactorMachine reactor,
                                                    @Nullable TickableSubscription subscription,
                                                    String lifecycleEvent) {
        helper.assertTrue(reactor.getDisplaySnapshot().isEmpty(),
                "Fusion retained display text after " + lifecycleEvent);
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Fusion retained its display subscription after " + lifecycleEvent);
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

    private static FusionReactorMachine requireFusion(MetaMachine machine) {
        if (!(machine instanceof FusionReactorMachine reactor)) {
            throw new IllegalStateException("Fusion definition did not create its expected controller.");
        }
        return reactor;
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

    private static BlockEntityCreationInfo info(int tier) {
        MachineDefinition definition = GTMultiMachines.FUSION_REACTOR[tier];
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

    private static final class TestFusionReactorMachine extends FusionReactorMachine {

        private final List<IMultiPart> parts;
        private List<Component> serverDisplay = List.of();
        private @Nullable TickableSubscription capturedDisplaySubscription;
        private boolean useLiveDisplayContract;
        private boolean remote;
        private int displayCaptureCount;
        private int displayRefreshCount;

        private TestFusionReactorMachine(int tier, List<IMultiPart> parts) {
            super(info(tier), tier);
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
            if (subscription != null) {
                capturedDisplaySubscription = subscription;
            }
            return subscription;
        }

        private void setDisplayState(boolean formed, long energyStored, long energyCapacity, long heat) {
            isFormed = formed;
            energyContainer.resetBasicInfo(energyCapacity, 0, 0, 0, 0);
            energyContainer.setEnergyStored(energyStored);
            this.heat = heat;
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
