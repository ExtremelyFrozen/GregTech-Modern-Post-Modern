package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IDataAccessMachine;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.ItemStackTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.LuV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DataBankMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "DataBankMachineLDLib2UI")
    public static void controllerPagePreservesLegacyLayoutAndFilteredDataParts(GameTestHelper helper) {
        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            List<IMultiPart> dataParts = List.of(
                    requirePart(placeMachine(helper, new BlockPos(0, 1, 0),
                            GTResearchMachines.BASIC_DATA_ACCESS_HATCH)),
                    requirePart(placeMachine(helper, new BlockPos(1, 1, 0),
                            GTResearchMachines.DATA_HATCH_RECEIVER)),
                    requirePart(placeMachine(helper, new BlockPos(2, 1, 0),
                            GTResearchMachines.DATA_HATCH_TRANSMITTER)));
            IMultiPart creative = requirePart(placeMachine(helper, new BlockPos(3, 1, 0),
                    GTResearchMachines.CREATIVE_DATA_ACCESS_HATCH));
            IMultiPart energy = requirePart(placeMachine(helper, new BlockPos(4, 1, 0),
                    GTMachines.ENERGY_INPUT_HATCH[LuV]));
            IMultiPart maintenance = requirePart(placeMachine(helper, new BlockPos(0, 1, 1),
                    GTMachines.MAINTENANCE_HATCH));
            List<IMultiPart> allParts = new ArrayList<>(dataParts);
            allParts.add(creative);
            allParts.add(energy);
            allParts.add(maintenance);

            TestDataBankMachine dataBank = new TestDataBankMachine(allParts);
            dataBank.setLevel(helper.getLevel());
            dataBank.setFormedForTest(true);
            dataBank.refreshDisplaySnapshot();
            MutableMachineUIHolder holder = new MutableMachineUIHolder(dataBank);
            TestDataBankMachine replacement = new TestDataBankMachine(List.of());
            MutableMachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);

            helper.assertTrue(dataBank.canCreateLDLib2UI(player, holder),
                    "Data Bank rejected its matching controller holder");
            helper.assertFalse(dataBank.canCreateLDLib2UI(player, replacementHolder),
                    "Data Bank accepted another controller instance with the same definition");
            boolean replacementRejected = false;
            try {
                dataBank.createLDLib2UI(player, replacementHolder);
            } catch (IllegalArgumentException expected) {
                replacementRejected = expected.getMessage().contains("holder");
            }
            helper.assertTrue(replacementRejected,
                    "Data Bank created an LDLib2 UI for another controller instance");

            LDLib2FancyUIProvider controllerPage = dataBank.createLDLib2Page(player, holder);
            helper.assertTrue(controllerPage != dataBank.createLDLib2Page(player, holder),
                    "Data Bank reused its controller page across menu openings");
            Component expectedTitle = Component.translatable(dataBank.getDefinition().getDescriptionId());
            helper.assertTrue(controllerPage.getTitle().equals(expectedTitle) &&
                    controllerPage.getTabTooltips().equals(List.of(expectedTitle)),
                    "Data Bank controller page did not use its definition title");
            helper.assertTrue(controllerPage.getTabIcon() instanceof ItemStackTexture icon &&
                    icon.items.length == 1 && icon.items[0].is(dataBank.getDefinition().getItem()),
                    "Data Bank controller page did not use its definition icon");
            helper.assertTrue(controllerPage.getPageGroupingData() == null,
                    "Data Bank controller page invented grouping metadata");
            helper.assertTrue(controllerPage.getSubTabs().size() == dataParts.size(),
                    "Data Bank did not exclude Creative and non-data parts from its page list");
            List<Component> contextualTitles = controllerPage.getSubTabs().stream()
                    .map(LDLib2FancyUIProvider::getTitle)
                    .toList();
            List<Component> expectedContextualTitles = dataParts.stream()
                    .map(IMultiPart::self)
                    .map(MetaMachine::getDefinition)
                    .map(MachineDefinition::getDescriptionId)
                    .map(Component::translatable)
                    .toList();
            helper.assertTrue(contextualTitles.equals(expectedContextualTitles),
                    "Data Bank contextual page list did not preserve Data Access and optical port order");

            LDLib2FancyMachineUIElement shell = requireFancyShell(
                    dataBank.createLDLib2UI(player, holder).getRootElement(), "Data Bank");
            helper.assertTrue(shell.getHolder() == holder,
                    "Data Bank Fancy shell did not retain its controller holder");
            int expectedConfiguratorCount = dataBank.supportsBatchMode() ? 3 : 2;
            helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == expectedConfiguratorCount,
                    "Data Bank did not preserve voiding, conditional batch, and working configurators");
            helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                    "Data Bank did not expose its controller cover-direction page");
            helper.assertTrue(shell.getTooltipsPanel().getChildren().size() == 1,
                    "Data Bank did not expose its maintenance warning");

            UIElement pageContainer = shell.getChildren().getFirst();
            helper.assertTrue(pageContainer.getChildren().size() == dataParts.size() + 1,
                    "Data Bank did not create exactly its three contextual data pages");
            UIElement mainPage = pageContainer.getChildren().getFirst();
            helper.assertTrue(mainPage.getSizeWidth() == 190 && mainPage.getSizeHeight() == 125,
                    "Data Bank main page did not preserve its 190x125 body");
            helper.assertTrue(mainPage.getStyle().getInline(PropertyRegistry.BACKGROUND) ==
                    GuiTextures.BACKGROUND_INVERSE,
                    "Data Bank main page did not preserve its inverse background");
            helper.assertTrue(mainPage.getChildren().size() == 1 &&
                    mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                    "Data Bank main page did not create one display scroller");
            UIElement scroller = mainPage.getChildren().getFirst();
            helper.assertTrue(scroller.getLayoutX() == 4 && scroller.getLayoutY() == 4 &&
                    scroller.getSizeWidth() == 182 && scroller.getSizeHeight() == 117,
                    "Data Bank display scroller did not preserve its (4,4) 182x117 bounds");

            List<GTLabelElement> labels = descendants(mainPage).stream()
                    .filter(GTLabelElement.class::isInstance)
                    .map(GTLabelElement.class::cast)
                    .toList();
            helper.assertTrue(labels.size() == 1 && labels.getFirst().getLayoutX() == 4 &&
                    labels.getFirst().getLayoutY() == 5,
                    "Data Bank title did not preserve its (4,5) position");
            List<GTComponentPanelElement> panels = descendants(mainPage).stream()
                    .filter(GTComponentPanelElement.class::isInstance)
                    .map(GTComponentPanelElement.class::cast)
                    .toList();
            helper.assertTrue(panels.size() == 1 && panels.getFirst().getLayoutX() == 4 &&
                    panels.getFirst().getLayoutY() == 17 && panels.getFirst().getMaxWidthLimit() == 150,
                    "Data Bank display panel did not preserve its position and legacy text width");
            helper.assertTrue(panels.getFirst().getLastText().equals(dataBank.getDisplaySnapshot()),
                    "Data Bank display panel did not consume the synchronized snapshot");

            LDLib2FancyMachineUIElement secondShell = requireFancyShell(
                    dataBank.createLDLib2UI(player, holder).getRootElement(), "second Data Bank opening");
            List<UIElement> firstPages = pageContainer.getChildren();
            List<UIElement> secondPages = secondShell.getChildren().getFirst().getChildren();
            for (int index = 0; index < firstPages.size(); index++) {
                helper.assertTrue(firstPages.get(index) != secondPages.get(index),
                        "Data Bank reused a controller or part page across menu openings");
            }

            for (IMultiPart part : dataParts) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Supported Data Bank part has no LDLib2 Fancy page.");
                }
                MutableMachineUIHolder partHolder = new MutableMachineUIHolder(part.self());
                LDLib2FancyUIProvider firstPage = pageProvider.createLDLib2FancyPage(player, partHolder);
                LDLib2FancyUIProvider secondPage = pageProvider.createLDLib2FancyPage(player, partHolder);
                helper.assertTrue(firstPage != secondPage,
                        "Data Bank part reused a page provider across openings");
                LDLib2FancyMachineUIElement partShell = new LDLib2FancyMachineUIElement(firstPage,
                        player.getInventory(), partHolder,
                        firstPage.getLDLib2PageWidth(), firstPage.getLDLib2PageHeight());
                helper.assertTrue(partShell.getHolder() == partHolder,
                        "Data Bank part page did not retain its dedicated holder");
            }
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DataBankMachineLDLib2UI")
    public static void staleHolderAndMissingDataProviderFailFast(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestDataBankMachine dataBank = new TestDataBankMachine(List.of());
        MutableMachineUIHolder staleHolder = new MutableMachineUIHolder(dataBank);
        LDLib2FancyUIProvider stalePage = dataBank.createLDLib2Page(player, staleHolder);
        staleHolder.setMachine(new TestDataBankMachine(List.of()));

        boolean stalePageRejected = false;
        try {
            new LDLib2FancyMachineUIElement(stalePage, player.getInventory(), staleHolder,
                    stalePage.getLDLib2PageWidth(), stalePage.getLDLib2PageHeight());
        } catch (IllegalStateException expected) {
            stalePageRejected = expected.getMessage().contains("page holder");
        }
        helper.assertTrue(stalePageRejected,
                "Data Bank page accepted a same-definition replacement after opening");

        UnsupportedDataPart unsupportedDataPart = new UnsupportedDataPart();
        TestDataBankMachine invalidDataBank = new TestDataBankMachine(List.of(unsupportedDataPart));
        boolean missingProviderRejected = false;
        try {
            invalidDataBank.createLDLib2Page(player, new MutableMachineUIHolder(invalidDataBank));
        } catch (IllegalStateException expected) {
            missingProviderRejected = expected.getMessage().contains("data part");
        }
        helper.assertTrue(missingProviderRejected,
                "Data Bank silently omitted a related data part without an LDLib2 Fancy provider");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DataBankMachineLDLib2UI")
    public static void displayStatesPreserveLegacyOrderAndImmutableSnapshots(GameTestHelper helper) {
        int energyUsage = DataBankMachine.EUT_PER_HATCH * 3;
        Component energy = Component.translatable("gtpm.multiblock.energy_consumption",
                FormattingUtil.formatNumbers(energyUsage),
                Component.literal(GTValues.VNF[GTUtil.getTierByVoltage(energyUsage)]))
                .withStyle(ChatFormatting.GRAY);
        Component providingText = Component.translatable("gtpm.multiblock.data_bank.providing")
                .withStyle(ChatFormatting.GREEN);
        Component idlingText = Component.translatable("gtpm.multiblock.idling")
                .withStyle(ChatFormatting.GRAY);

        DataBankMachine.DisplayState providingState = DataBankMachine.captureDisplayState(
                true, true, true, energyUsage);
        List<Component> providing = DataBankMachine.createDisplaySnapshot(providingState);
        helper.assertTrue(providing.equals(List.of(energy, providingText)),
                "Data Bank providing snapshot lost its legacy energy and status order");

        List<Component> idling = DataBankMachine.createDisplaySnapshot(
                DataBankMachine.captureDisplayState(true, true, false, energyUsage));
        List<Component> disabled = DataBankMachine.createDisplaySnapshot(
                DataBankMachine.captureDisplayState(true, false, true, energyUsage));
        helper.assertTrue(idling.equals(List.of(energy, idlingText)) && disabled.equals(idling),
                "Data Bank idle or disabled snapshot lost its legacy text order");

        Component invalidStructure = Component.translatable("gtpm.multiblock.invalid_structure")
                .withStyle(ChatFormatting.RED)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                                .withStyle(ChatFormatting.GRAY))));
        List<Component> invalid = DataBankMachine.createDisplaySnapshot(
                DataBankMachine.captureDisplayState(false, true, true, energyUsage));
        helper.assertTrue(invalid.equals(List.of(invalidStructure)),
                "Unformed Data Bank did not expose only the legacy invalid-structure line");

        boolean immutable = false;
        try {
            providing.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Data Bank display snapshot was mutable");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DataBankMachineLDLib2UI")
    public static void snapshotLifecycleInitializesRefreshesAndClears(GameTestHelper helper) {
        TestDataBankMachine dataBank = new TestDataBankMachine(List.of());
        dataBank.setLevel(helper.getLevel());
        dataBank.onLoad();
        helper.assertTrue(dataBank.getDisplaySnapshot().equals(DataBankMachine.createDisplaySnapshot(
                new DataBankMachine.DisplayState(false, true, false, 0))),
                "Data Bank onLoad did not initialize its server-owned invalid-structure snapshot");
        dataBank.onUnload();
        helper.assertTrue(dataBank.getDisplaySnapshot().isEmpty(),
                "Data Bank retained its display snapshot after unload");

        TestDataBankMachine partUnloadBank = new TestDataBankMachine(List.of());
        partUnloadBank.setLevel(helper.getLevel());
        partUnloadBank.setFormedForTest(true);
        partUnloadBank.refreshDisplaySnapshot();
        helper.assertFalse(partUnloadBank.getDisplaySnapshot().isEmpty(),
                "formed Data Bank did not create a display snapshot");
        partUnloadBank.getWorkLogic().setStatus(WorkLogic.Status.WORKING);
        int refreshesBeforeTick = partUnloadBank.getDisplayRefreshCount();
        partUnloadBank.tick();
        helper.assertTrue(partUnloadBank.getDisplayRefreshCount() > refreshesBeforeTick,
                "Data Bank business tick did not refresh its display snapshot");
        helper.assertTrue(partUnloadBank.getDisplaySnapshot().equals(List.of(
                Component.translatable("gtpm.multiblock.data_bank.providing")
                        .withStyle(ChatFormatting.GREEN))),
                "Data Bank test precondition did not reach its providing snapshot");
        partUnloadBank.setWorkingEnabled(false);
        helper.assertTrue(partUnloadBank.getDisplaySnapshot().equals(List.of(
                Component.translatable("gtpm.multiblock.idling").withStyle(ChatFormatting.GRAY))),
                "Data Bank working action did not refresh its idling snapshot immediately");
        partUnloadBank.onPartUnload();
        helper.assertTrue(partUnloadBank.getDisplaySnapshot().isEmpty(),
                "Data Bank retained its display snapshot after a part unloaded");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DataBankMachineLDLib2UI", timeoutTicks = 20)
    public static void displaySubscriptionRunsWhileDisabledAndStopsOnUnload(GameTestHelper helper) {
        TestDataBankMachine dataBank = new TestDataBankMachine(List.of());
        dataBank.setLevel(helper.getLevel());
        dataBank.setFormedForTest(true);
        dataBank.suppressBusinessTicks();
        dataBank.setWorkingEnabled(false);
        dataBank.onLoad();
        int refreshesAfterLoad = dataBank.getDisplayRefreshCount();

        helper.runAfterDelay(3, () -> {
            int refreshesWhileDisabled = dataBank.getDisplayRefreshCount();
            helper.assertTrue(refreshesWhileDisabled > refreshesAfterLoad,
                    "Data Bank display subscription stopped with its disabled business tick");
            dataBank.onUnload();
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(dataBank.getDisplayRefreshCount() == refreshesWhileDisabled,
                        "Data Bank display subscription kept running after unload");
                helper.succeed();
            });
        });
    }

    private static LDLib2FancyMachineUIElement requireFancyShell(UIElement root, String description) {
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException(description + " did not create an LDLib2 Fancy shell.");
        }
        return shell;
    }

    private static MetaMachine placeMachine(GameTestHelper helper, BlockPos pos, MachineDefinition definition) {
        helper.setBlock(pos, definition.getBlock());
        if (!(helper.getBlockEntity(pos) instanceof MetaMachine machine)) {
            throw new IllegalStateException("Placed block did not create its expected machine.");
        }
        return machine;
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

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }

    private static class MutableMachineUIHolder implements MachineUIHolder {

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

    private static final class UnsupportedDataPart extends MultiblockPartMachine implements IDataAccessMachine {

        private UnsupportedDataPart() {
            super(info(GTResearchMachines.BASIC_DATA_ACCESS_HATCH));
        }

        @Override
        public boolean isRecipeAvailable(@NotNull GTRecipeType recipeType, @NotNull ResourceLocation recipeId) {
            return false;
        }
    }

    private static final class TestDataBankMachine extends DataBankMachine {

        private final List<IMultiPart> parts;
        private int displayRefreshCount;
        private boolean businessTicksSuppressed;

        private TestDataBankMachine(List<IMultiPart> parts) {
            super(info(GTResearchMachines.DATA_BANK));
            this.parts = List.copyOf(parts);
        }

        private void setFormedForTest(boolean formed) {
            this.isFormed = formed;
        }

        private int getDisplayRefreshCount() {
            return displayRefreshCount;
        }

        private void suppressBusinessTicks() {
            businessTicksSuppressed = true;
        }

        @Override
        void refreshDisplaySnapshot() {
            displayRefreshCount++;
            super.refreshDisplaySnapshot();
        }

        @Override
        protected void updateTickSubscription() {
            if (!businessTicksSuppressed) {
                super.updateTickSubscription();
            } else if (tickSubs != null) {
                tickSubs.unsubscribe();
                tickSubs = null;
            }
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
        }
    }
}
