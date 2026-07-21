package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.WeightedMaterial;
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
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.BedrockOreMinerLogic;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputBusPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
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

import static com.gregtechceu.gtceu.api.GTValues.EV;
import static com.gregtechceu.gtceu.api.GTValues.HV;
import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.LuV;
import static com.gregtechceu.gtceu.api.GTValues.MV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class BedrockOreMinerMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "BedrockOreMinerMachineLDLib2UI")
    public static void allRegisteredTiersKeepConcreteControllerLogicAndLDLib2Contracts(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        int[] tiers = { MV, HV, EV };
        int[] depletionChances = { 1, 2, 8 };
        int[] multipliers = { 1, 4, 16 };
        for (int index = 0; index < tiers.length; index++) {
            int tier = tiers[index];
            MetaMachine registeredMachine = createMachine(GTMultiMachines.BEDROCK_ORE_MINER[tier]);
            helper.assertTrue(registeredMachine.getClass() == BedrockOreMinerMachine.class &&
                    registeredMachine instanceof LDLib2MachineUIProvider &&
                    registeredMachine instanceof LDLib2FancyActionMachine,
                    "Bedrock Ore Miner tier did not create its concrete LDLib2 controller: " + tier);
            BedrockOreMinerMachine miner = (BedrockOreMinerMachine) registeredMachine;
            BedrockOreMinerLogic logic = miner.getRecipeLogic();
            MutableMachineUIHolder holder = new MutableMachineUIHolder(miner);
            helper.assertTrue(miner.getTier() == tier && miner.getEnergyTier() == tier &&
                    logic.getClass() == BedrockOreMinerLogic.class && miner.getRecipeLogic() == logic,
                    "Bedrock Ore Miner tier, empty-input energy tier, or specialized recipe logic changed: " + tier);
            helper.assertTrue(miner.getRecipeTypes().length == 1 &&
                    miner.getRecipeType() == GTRecipeTypes.DUMMY_RECIPES && !miner.supportsBatchMode(),
                    "Bedrock Ore Miner invented a recipe mode or batch support: " + tier);
            helper.assertTrue(BedrockOreMinerMachine.getDepletionChance(tier) == depletionChances[index] &&
                    BedrockOreMinerMachine.getRigMultiplier(tier) == multipliers[index],
                    "Bedrock Ore Miner depletion or production tier semantics changed: " + tier);
            helper.assertTrue(miner.canCreateLDLib2UI(player, holder) &&
                    miner.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement &&
                    miner.getRecipeLogic() == logic,
                    "Bedrock Ore Miner UI replaced its specialized recipe logic: " + tier);
        }

        helper.assertTrue(BedrockOreMinerMachine.getCasingState(MV) == GTBlocks.CASING_STEEL_SOLID.get() &&
                BedrockOreMinerMachine.getCasingState(HV) == GTBlocks.CASING_TITANIUM_STABLE.get() &&
                BedrockOreMinerMachine.getCasingState(EV) == GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get(),
                "Bedrock Ore Miner casing tier mapping changed");
        helper.assertTrue(BedrockOreMinerMachine.getFrameState(MV) ==
                GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Steel).get() &&
                BedrockOreMinerMachine.getFrameState(HV) ==
                        GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Titanium).get() &&
                BedrockOreMinerMachine.getFrameState(EV) ==
                        GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.TungstenSteel).get(),
                "Bedrock Ore Miner frame tier mapping changed");
        helper.assertTrue(BedrockOreMinerMachine.getBaseTexture(MV).equals(
                GTCEu.id("block/casings/solid/machine_casing_solid_steel")) &&
                BedrockOreMinerMachine.getBaseTexture(HV).equals(
                        GTCEu.id("block/casings/solid/machine_casing_stable_titanium")) &&
                BedrockOreMinerMachine.getBaseTexture(EV).equals(
                        GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel")),
                "Bedrock Ore Miner base texture tier mapping changed");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "BedrockOreMinerMachineLDLib2UI")
    public static void controllerHolderIdentityAndUnsupportedPartsFailFast(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        BedrockOreMinerMachine miner = (BedrockOreMinerMachine) createMachine(
                GTMultiMachines.BEDROCK_ORE_MINER[MV]);
        MutableMachineUIHolder holder = new MutableMachineUIHolder(miner);

        MetaMachine wrongMachine = createMachine(GTMachines.MACERATOR[LV]);
        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(wrongMachine);
        helper.assertTrue(!miner.canCreateLDLib2UI(player, wrongHolder) &&
                createUIFails(miner, player, wrongHolder),
                "Bedrock Ore Miner accepted a holder for a different machine");

        BedrockOreMinerMachine replacement = (BedrockOreMinerMachine) createMachine(
                GTMultiMachines.BEDROCK_ORE_MINER[MV]);
        MutableMachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);
        helper.assertTrue(!miner.canCreateLDLib2UI(player, replacementHolder) &&
                createUIFails(miner, player, replacementHolder),
                "Bedrock Ore Miner accepted another same-definition controller instance");

        LDLib2FancyUIProvider stalePage = miner.createLDLib2Page(player, holder);
        holder.setMachine(replacement);
        boolean stalePageRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            stalePageRejected = expected.getMessage().contains("page holder");
        }
        helper.assertTrue(stalePageRejected,
                "Bedrock Ore Miner page accepted a same-definition replacement after opening");

        IMultiPart unsupportedPart = requirePart(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestBedrockOreMinerMachine invalidMiner = new TestBedrockOreMinerMachine(MV, List.of(unsupportedPart));
        boolean unsupportedRejected = false;
        try {
            invalidMiner.createLDLib2Page(player, new MutableMachineUIHolder(invalidMiner));
        } catch (IllegalStateException expected) {
            unsupportedRejected = expected.getMessage().contains("part");
        }
        helper.assertTrue(unsupportedRejected,
                "Bedrock Ore Miner silently omitted a part without an LDLib2 Fancy page");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "BedrockOreMinerMachineLDLib2UI")
    public static void energyTierAllowsExactlyOneOverclockTier(GameTestHelper helper) {
        EnergyHatchPartMachine hvInput = requireEnergyHatch(placeMachine(helper, new BlockPos(0, 1, 0),
                GTMachines.ENERGY_INPUT_HATCH[HV]));
        EnergyHatchPartMachine evInput = requireEnergyHatch(placeMachine(helper, new BlockPos(1, 1, 0),
                GTMachines.ENERGY_INPUT_HATCH[EV]));
        TestBedrockOreMinerMachine hvPoweredMiner = new TestBedrockOreMinerMachine(MV, List.of());
        TestBedrockOreMinerMachine evPoweredMiner = new TestBedrockOreMinerMachine(MV, List.of());
        addPartHandlers(hvPoweredMiner, hvInput);
        addPartHandlers(evPoweredMiner, evInput);

        helper.assertTrue(hvPoweredMiner.getEnergyTier() == HV,
                "Bedrock Ore Miner did not accept one overclock tier from an HV input");
        helper.assertTrue(evPoweredMiner.getEnergyTier() == HV,
                "Bedrock Ore Miner did not clamp a higher input to exactly one overclock tier");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "BedrockOreMinerMachineLDLib2UI")
    public static void shellPreservesLayoutAndOpeningScopedStandardParts(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        EnergyHatchPartMachine energyHatch = requireEnergyHatch(placeMachine(helper, new BlockPos(0, 1, 0),
                GTMachines.ENERGY_INPUT_HATCH[MV]));
        StandardItemBusPartMachine itemExport = requireItemBus(placeMachine(helper, new BlockPos(1, 1, 0),
                GTMachines.ITEM_EXPORT_BUS[MV]));
        List<IMultiPart> parts = List.of(energyHatch, itemExport);
        TestBedrockOreMinerMachine miner = new TestBedrockOreMinerMachine(MV, parts);
        miner.setLevel(helper.getLevel());
        miner.setFormedForTest(true);
        miner.setDisplayState(new BedrockOreMinerMachine.DisplayState(true, MV, null, 0));
        miner.refreshDisplaySnapshot();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(miner);
        LDLib2FancyUIProvider firstPage = miner.createLDLib2Page(player, holder);
        LDLib2FancyUIProvider secondPage = miner.createLDLib2Page(player, holder);

        List<Component> expectedPartTitles = parts.stream()
                .map(IMultiPart::self)
                .map(MetaMachine::getDefinition)
                .map(MachineDefinition::getDescriptionId)
                .<Component>map(Component::translatable)
                .toList();
        helper.assertTrue(firstPage != secondPage && firstPage.getSubTabs().size() == 2 &&
                firstPage.getSubTabs().stream().map(LDLib2FancyUIProvider::getTitle).toList()
                        .equals(expectedPartTitles),
                "Bedrock Ore Miner did not preserve energy-input then item-export page order");
        for (int index = 0; index < parts.size(); index++) {
            helper.assertTrue(firstPage.getSubTabs().get(index) != secondPage.getSubTabs().get(index),
                    "Bedrock Ore Miner reused a part page provider across openings");
        }

        LDLib2FancyMachineUIElement firstShell = createShell(player, holder, firstPage);
        LDLib2FancyMachineUIElement secondShell = createShell(player, holder, secondPage);
        helper.assertTrue(firstShell.getConfiguratorPanel().getChildren().size() == 2 &&
                firstShell.getSideTabsElement().getChildren().size() == 2 &&
                firstShell.getTooltipsPanel().getChildren().isEmpty(),
                "Bedrock Ore Miner did not preserve 2 configurators, 2 side tabs, and 0 tooltips");
        UIElement firstContainer = firstShell.getChildren().getFirst();
        UIElement secondContainer = secondShell.getChildren().getFirst();
        helper.assertTrue(firstContainer.getChildren().size() == 3 && secondContainer.getChildren().size() == 3,
                "Bedrock Ore Miner did not create exactly two valid contextual part pages");
        for (int index = 0; index < firstContainer.getChildren().size(); index++) {
            helper.assertTrue(firstContainer.getChildren().get(index) != secondContainer.getChildren().get(index),
                    "Bedrock Ore Miner reused a controller or part element across openings");
        }

        UIElement mainPage = firstContainer.getChildren().getFirst();
        UITemplate.LDLib2Bounds mainPageBounds = UITemplate.getLDLib2Bounds(mainPage);
        helper.assertTrue(mainPageBounds.width() == 190 && mainPageBounds.height() == 125,
                "Bedrock Ore Miner main page lost its 190x125 body");
        helper.assertTrue(mainPage.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.BACKGROUND_INVERSE,
                "Bedrock Ore Miner main page lost its inverse background");
        helper.assertTrue(mainPage.getChildren().size() == 1 &&
                mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                "Bedrock Ore Miner main page did not create one display scroller");
        UIElement scroller = mainPage.getChildren().getFirst();
        UITemplate.LDLib2Bounds scrollerBounds = UITemplate.getLDLib2Bounds(scroller);
        helper.assertTrue(scrollerBounds.x() == 4 && scrollerBounds.y() == 4 &&
                scrollerBounds.width() == 182 && scrollerBounds.height() == 117,
                "Bedrock Ore Miner display scroller lost its (4,4) 182x117 bounds");
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
                labelBounds.y() == 5 && panels.size() == 1 &&
                panelBounds.x() == 4 && panelBounds.y() == 17 &&
                panels.getFirst().getMaxWidthLimit() == 200 &&
                panels.getFirst().getLastText().equals(miner.getDisplaySnapshot()),
                "Bedrock Ore Miner title or snapshot panel lost its legacy bounds");

        clickButton(firstShell.getSideTabsElement().getChildren().get(1));
        clickButton(secondShell.getSideTabsElement().getChildren().get(1));
        helper.assertTrue(firstContainer.getChildren().size() == 4 && secondContainer.getChildren().size() == 4 &&
                firstContainer.getChildren().getLast() != secondContainer.getChildren().getLast(),
                "Bedrock Ore Miner reused its directional page across openings");

        for (IMultiPart part : parts) {
            assertPartPageUsesDedicatedHolder(helper, player, part);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "BedrockOreMinerMachineLDLib2UI")
    public static void specializedEnergyAndItemExportsKeepOpeningScopedContextualPages(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        EnergyHatchPartMachine fourAmpInput = requireEnergyHatch(placeMachine(helper, new BlockPos(0, 1, 0),
                GTMachines.ENERGY_INPUT_HATCH_4A[EV]));
        DualHatchPartMachine dualExport = requireDualHatch(placeMachine(helper, new BlockPos(1, 1, 0),
                GTMachines.DUAL_EXPORT_HATCH[LuV]));
        EnergyHatchPartMachine sixteenAmpInput = requireEnergyHatch(placeMachine(helper, new BlockPos(2, 1, 0),
                GTMachines.ENERGY_INPUT_HATCH_16A[EV]));
        MEOutputBusPartMachine meExport = requireMEItemExport(placeMachine(helper, new BlockPos(3, 1, 0),
                GTAEMachines.ITEM_EXPORT_BUS_ME));

        List<IMultiPart> dualParts = List.of(fourAmpInput, dualExport);
        List<IMultiPart> meParts = List.of(sixteenAmpInput, meExport);
        List<TestBedrockOreMinerMachine> miners = List.of(
                new TestBedrockOreMinerMachine(HV, dualParts),
                new TestBedrockOreMinerMachine(EV, meParts));
        for (int minerIndex = 0; minerIndex < miners.size(); minerIndex++) {
            TestBedrockOreMinerMachine miner = miners.get(minerIndex);
            List<IMultiPart> parts = minerIndex == 0 ? dualParts : meParts;
            MutableMachineUIHolder holder = new MutableMachineUIHolder(miner);
            LDLib2FancyUIProvider firstPage = miner.createLDLib2Page(player, holder);
            LDLib2FancyUIProvider secondPage = miner.createLDLib2Page(player, holder);
            helper.assertTrue(firstPage.getSubTabs().size() == 2 && secondPage.getSubTabs().size() == 2 &&
                    firstPage.getSubTabs().stream().map(LDLib2FancyUIProvider::getTitle).toList().equals(
                            parts.stream().map(IMultiPart::self).map(MetaMachine::getDefinition)
                                    .map(MachineDefinition::getDescriptionId).<Component>map(Component::translatable)
                                    .toList()),
                    "Bedrock Ore Miner omitted or reordered a legal specialized part page");
            for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
                helper.assertTrue(firstPage.getSubTabs().get(partIndex) != secondPage.getSubTabs().get(partIndex),
                        "Bedrock Ore Miner reused a specialized part page across openings");
            }

            UIElement firstContainer = createShell(player, holder, firstPage).getChildren().getFirst();
            UIElement secondContainer = createShell(player, holder, secondPage).getChildren().getFirst();
            helper.assertTrue(firstContainer.getChildren().size() == 3 &&
                    secondContainer.getChildren().size() == 3,
                    "Bedrock Ore Miner did not build one legal specialized export contextual page");
            for (int elementIndex = 0; elementIndex < firstContainer.getChildren().size(); elementIndex++) {
                helper.assertTrue(firstContainer.getChildren().get(elementIndex) !=
                        secondContainer.getChildren().get(elementIndex),
                        "Bedrock Ore Miner reused a specialized contextual element across openings");
            }
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "BedrockOreMinerMachineLDLib2UI")
    public static void displayBranchesAreExactOrderedImmutableAndVisibleAfterRefresh(GameTestHelper helper) {
        List<WeightedMaterial> sourceMaterials = new ArrayList<>();
        sourceMaterials.add(new WeightedMaterial(GTMaterials.Iron, 3));
        sourceMaterials.add(new WeightedMaterial(GTMaterials.Copper, 1));
        BedrockOreMinerMachine.DisplayState oreState = BedrockOreMinerMachine.captureDisplayState(
                true, HV, sourceMaterials, 125, 17.5f);
        sourceMaterials.clear();

        List<Component> withOres = BedrockOreMinerMachine.createDisplaySnapshot(oreState);
        Component ironInfo = GTMaterials.Iron.getLocalizedName().withStyle(ChatFormatting.GREEN);
        Component copperInfo = GTMaterials.Copper.getLocalizedName().withStyle(ChatFormatting.GREEN);
        Component amountInfo = Component.literal(FormattingUtil.formatNumbers(109.0f) + "/s")
                .withStyle(ChatFormatting.BLUE);
        List<Component> expectedWithOres = List.of(
                Component.translatable("gtpm.multiblock.max_energy_per_tick", GTValues.V[HV], GTValues.VNF[HV]),
                Component.translatable("gtpm.multiblock.ore_rig.drilled_ores_list")
                        .withStyle(ChatFormatting.GREEN),
                Component.translatable("gtpm.multiblock.ore_rig.drilled_ore_entry", ironInfo)
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gtpm.multiblock.ore_rig.drilled_ore_entry", copperInfo)
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gtpm.multiblock.ore_rig.ore_amount", amountInfo)
                        .withStyle(ChatFormatting.GRAY));
        helper.assertTrue(oreState.orePerSecond() == 109.0f &&
                oreState.veinMaterials().equals(List.of(
                        new WeightedMaterial(GTMaterials.Iron, 3),
                        new WeightedMaterial(GTMaterials.Copper, 1))) &&
                withOres.equals(expectedWithOres),
                "Bedrock Ore Miner ore snapshot changed its tickrate floor, copied order, text, or styles");

        BedrockOreMinerMachine.DisplayState emptyState = BedrockOreMinerMachine.captureDisplayState(
                true, MV, List.of(), 37, 20);
        Component emptyAmount = Component.literal(FormattingUtil.formatNumbers(37.0f) + "/s")
                .withStyle(ChatFormatting.BLUE);
        List<Component> emptyVein = BedrockOreMinerMachine.createDisplaySnapshot(emptyState);
        helper.assertTrue(emptyVein.equals(List.of(
                Component.translatable("gtpm.multiblock.max_energy_per_tick", GTValues.V[MV], GTValues.VNF[MV]),
                Component.translatable("gtpm.multiblock.ore_rig.drilled_ores_list")
                        .withStyle(ChatFormatting.GREEN),
                Component.translatable("gtpm.multiblock.ore_rig.ore_amount", emptyAmount)
                        .withStyle(ChatFormatting.GRAY))),
                "Bedrock Ore Miner empty material list did not remain distinct from a null vein");

        BedrockOreMinerMachine.DisplayState nullState = BedrockOreMinerMachine.captureDisplayState(
                true, MV, null, 800, 20);
        Component noOre = Component.translatable("gtpm.multiblock.fluid_rig.no_fluid_in_area")
                .withStyle(ChatFormatting.RED);
        List<Component> nullVein = BedrockOreMinerMachine.createDisplaySnapshot(nullState);
        helper.assertTrue(nullState.orePerSecond() == 0 && nullVein.equals(List.of(
                Component.translatable("gtpm.multiblock.max_energy_per_tick", GTValues.V[MV], GTValues.VNF[MV]),
                Component.translatable("gtpm.multiblock.ore_rig.drilled_ores_list")
                        .withStyle(ChatFormatting.GREEN),
                Component.translatable("gtpm.multiblock.ore_rig.drilled_ore_entry", noOre)
                        .withStyle(ChatFormatting.GRAY))),
                "Bedrock Ore Miner null vein changed its no-area translation, text, or styles");

        List<Component> invalid = BedrockOreMinerMachine.createDisplaySnapshot(
                BedrockOreMinerMachine.captureDisplayState(false, EV,
                        List.of(new WeightedMaterial(GTMaterials.Gold, 1)), 800, 20));
        helper.assertTrue(invalid.equals(List.of(invalidStructureLine())),
                "Unformed Bedrock Ore Miner did not publish only invalid_structure");

        boolean materialsImmutable = false;
        try {
            oreState.veinMaterials().add(new WeightedMaterial(GTMaterials.Gold, 1));
        } catch (UnsupportedOperationException expected) {
            materialsImmutable = true;
        }
        boolean snapshotImmutable = false;
        try {
            withOres.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            snapshotImmutable = true;
        }
        helper.assertTrue(materialsImmutable && snapshotImmutable,
                "Bedrock Ore Miner retained a mutable material list or display snapshot");

        TestBedrockOreMinerMachine miner = new TestBedrockOreMinerMachine(HV, List.of());
        miner.setDisplayState(oreState);
        miner.refreshDisplaySnapshot();
        List<Component> firstSnapshot = miner.getDisplaySnapshot();
        miner.refreshDisplaySnapshot();
        helper.assertTrue(miner.getDisplaySnapshot() == firstSnapshot,
                "Bedrock Ore Miner replaced an unchanged snapshot instance");
        int capturesBeforeRead = miner.getDisplayCaptureCount();
        List<Component> panelRead = new ArrayList<>();
        miner.addDisplayText(panelRead);
        helper.assertTrue(panelRead.equals(firstSnapshot) &&
                miner.getDisplayCaptureCount() == capturesBeforeRead,
                "Bedrock Ore Miner panel read sampled level, tickrate, or live vein state");

        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MutableMachineUIHolder holder = new MutableMachineUIHolder(miner);
        LDLib2FancyMachineUIElement shell = createShell(player, holder, miner.createLDLib2Page(player, holder));
        GTComponentPanelElement panel = descendants(shell.getChildren().getFirst()).stream()
                .filter(GTComponentPanelElement.class::isInstance)
                .map(GTComponentPanelElement.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Bedrock Ore Miner display panel is missing."));
        BedrockOreMinerMachine.DisplayState updated = BedrockOreMinerMachine.captureDisplayState(
                true, HV, List.of(new WeightedMaterial(GTMaterials.Gold, 1)), 200, 20);
        miner.setDisplayState(updated);
        miner.refreshDisplaySnapshot();
        panel.screenTick();
        helper.assertTrue(panel.getLastText().equals(miner.getDisplaySnapshot()) &&
                panel.getLastText().equals(BedrockOreMinerMachine.createDisplaySnapshot(updated)),
                "Opened Bedrock Ore Miner panel did not observe its refreshed snapshot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "BedrockOreMinerMachineLDLib2UI")
    public static void formInvalidateAndUnloadOwnSnapshotLifecycle(GameTestHelper helper) {
        TestBedrockOreMinerMachine miner = new TestBedrockOreMinerMachine(MV, List.of());
        miner.setLevel(helper.getLevel());
        miner.useLiveDisplayState();
        BedrockOreMinerLogic logic = miner.getRecipeLogic();
        miner.onLoad();
        helper.assertTrue(miner.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                miner.getRecipeLogic() == logic,
                "Bedrock Ore Miner onLoad changed its logic or omitted invalid_structure");

        miner.formStructure(BedrockOreMinerMachine.DEFAULT_STRUCTURE);
        TickableSubscription formedSubscription = miner.getCapturedSubscription();
        helper.assertTrue(miner.isFormed() && formedSubscription != null &&
                formedSubscription.isStillSubscribed() &&
                !miner.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                miner.getRecipeLogic() == logic,
                "Bedrock Ore Miner formation did not publish and subscribe without replacing its logic");
        miner.invalidateStructure(BedrockOreMinerMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(miner.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                !formedSubscription.isStillSubscribed(),
                "Bedrock Ore Miner invalidation did not publish invalid_structure and stop refresh");

        TestBedrockOreMinerMachine partUnloadMiner = subscribedMiner();
        TickableSubscription partSubscription = partUnloadMiner.getCapturedSubscription();
        partUnloadMiner.onPartUnload();
        assertRuntimeDisplayCleared(helper, partUnloadMiner, partSubscription, "part unload");
        TestBedrockOreMinerMachine controllerUnloadMiner = subscribedMiner();
        TickableSubscription controllerSubscription = controllerUnloadMiner.getCapturedSubscription();
        controllerUnloadMiner.onUnload();
        assertRuntimeDisplayCleared(helper, controllerUnloadMiner, controllerSubscription, "controller unload");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "BedrockOreMinerMachineLDLib2UI")
    public static void clientLifecycleNeverCapturesServerDisplayState(GameTestHelper helper) {
        TestBedrockOreMinerMachine miner = new TestBedrockOreMinerMachine(MV, List.of());
        miner.setLevel(helper.getLevel());
        miner.setRemoteForTest(true);

        miner.onLoad();
        miner.formStructure(BedrockOreMinerMachine.DEFAULT_STRUCTURE);
        miner.invalidateStructure(BedrockOreMinerMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(miner.getDisplayCaptureCount() == 0 && miner.getCapturedSubscription() == null &&
                miner.getDisplaySnapshot().isEmpty(),
                "Bedrock Ore Miner client lifecycle sampled or subscribed to server display state");
        miner.onUnload();
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "BedrockOreMinerMachineLDLib2UI", timeoutTicks = 20)
    public static void onLoadRefreshesFormedSnapshotUntilUnload(GameTestHelper helper) {
        TestBedrockOreMinerMachine miner = new TestBedrockOreMinerMachine(EV, List.of());
        miner.setLevel(helper.getLevel());
        miner.setFormedForTest(true);
        miner.setDisplayState(new BedrockOreMinerMachine.DisplayState(true, EV, null, 0));
        miner.setWorkingEnabled(false);
        miner.onLoad();
        int refreshesAfterLoad = miner.getDisplayRefreshCount();
        helper.runAfterDelay(3, () -> {
            TickableSubscription subscription = miner.getCapturedSubscription();
            helper.assertTrue(!miner.getWorkLogic().isWorkingEnabled() && subscription != null &&
                    subscription.isStillSubscribed(),
                    "Bedrock Ore Miner did not subscribe while formed with working disabled");
            subscription.run();
            int refreshesWhileLoaded = miner.getDisplayRefreshCount();
            helper.assertTrue(refreshesWhileLoaded > refreshesAfterLoad,
                    "Bedrock Ore Miner subscription did not refresh its display snapshot");
            miner.onUnload();
            subscription.run();
            helper.assertTrue(!subscription.isStillSubscribed() &&
                    miner.getDisplayRefreshCount() == refreshesWhileLoaded &&
                    miner.getDisplaySnapshot().isEmpty(),
                    "Bedrock Ore Miner refresh or snapshot survived unload");
            helper.succeed();
        });
    }

    private static boolean createUIFails(BedrockOreMinerMachine miner, ServerPlayer player,
                                         MachineUIHolder holder) {
        try {
            miner.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static void assertPartPageUsesDedicatedHolder(GameTestHelper helper, ServerPlayer player,
                                                          IMultiPart part) {
        if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
            throw new IllegalStateException("Valid Bedrock Ore Miner part has no LDLib2 Fancy page.");
        }
        MutableMachineUIHolder partHolder = new MutableMachineUIHolder(part.self());
        LDLib2FancyUIProvider partPage = pageProvider.createLDLib2FancyPage(player, partHolder);
        helper.assertTrue(createShell(player, partHolder, partPage).getHolder() == partHolder,
                "Bedrock Ore Miner part page lost its dedicated holder");
    }

    private static void addPartHandlers(BedrockOreMinerMachine miner, IMultiPart part) {
        for (var handlerList : part.getRecipeHandlers()) {
            miner.addHandlerList(handlerList);
        }
    }

    private static TestBedrockOreMinerMachine subscribedMiner() {
        TestBedrockOreMinerMachine miner = new TestBedrockOreMinerMachine(MV, List.of());
        miner.setFormedForTest(true);
        miner.setDisplayState(new BedrockOreMinerMachine.DisplayState(true, MV, null, 0));
        miner.refreshDisplaySnapshot();
        miner.getDisplaySnapshotSubscription().updateSubscription();
        return miner;
    }

    private static void assertRuntimeDisplayCleared(GameTestHelper helper,
                                                    TestBedrockOreMinerMachine miner,
                                                    @Nullable TickableSubscription subscription,
                                                    String lifecycleEvent) {
        helper.assertTrue(miner.getDisplaySnapshot().isEmpty(),
                "Bedrock Ore Miner retained display text after " + lifecycleEvent);
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Bedrock Ore Miner retained its display subscription after " + lifecycleEvent);
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

    private static IMultiPart requirePart(MetaMachine machine) {
        if (!(machine instanceof IMultiPart part)) {
            throw new IllegalStateException("Expected a multiblock part machine.");
        }
        return part;
    }

    private static EnergyHatchPartMachine requireEnergyHatch(MetaMachine machine) {
        if (!(machine instanceof EnergyHatchPartMachine energyHatch)) {
            throw new IllegalStateException("Energy Input Hatch definition did not create its expected type.");
        }
        return energyHatch;
    }

    private static StandardItemBusPartMachine requireItemBus(MetaMachine machine) {
        if (!(machine instanceof StandardItemBusPartMachine itemBus)) {
            throw new IllegalStateException("Item Output Bus definition did not create its expected type.");
        }
        return itemBus;
    }

    private static DualHatchPartMachine requireDualHatch(MetaMachine machine) {
        if (!(machine instanceof DualHatchPartMachine dualHatch)) {
            throw new IllegalStateException("Dual Output Hatch definition did not create its expected type.");
        }
        return dualHatch;
    }

    private static MEOutputBusPartMachine requireMEItemExport(MetaMachine machine) {
        if (!(machine instanceof MEOutputBusPartMachine itemExport)) {
            throw new IllegalStateException("ME Output Bus definition did not create its expected type.");
        }
        return itemExport;
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
        MachineDefinition definition = GTMultiMachines.BEDROCK_ORE_MINER[tier];
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

    private static final class TestBedrockOreMinerMachine extends BedrockOreMinerMachine {

        private final List<IMultiPart> parts;
        private DisplayState displayState;
        private @Nullable TickableSubscription capturedSubscription;
        private boolean useLiveDisplayState;
        private boolean remote;
        private int displayCaptureCount;
        private int displayRefreshCount;

        private TestBedrockOreMinerMachine(int tier, List<IMultiPart> parts) {
            super(info(tier), tier);
            this.parts = List.copyOf(parts);
            this.displayState = new DisplayState(false, tier, null, 0);
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
        protected DisplayState captureDisplayState() {
            displayCaptureCount++;
            return useLiveDisplayState ? super.captureDisplayState() : displayState;
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
                throw new IllegalStateException("Server-side display test did not create a tick subscription.");
            }
            capturedSubscription = subscription;
            return subscription;
        }

        @Override
        public TickableSubscription subscribeServerTick(@Nullable TickableSubscription last, Runnable runnable) {
            capturedSubscription = super.subscribeServerTick(last, runnable);
            if (capturedSubscription == null) {
                throw new IllegalStateException("Server-side display test did not create a tick subscription.");
            }
            return capturedSubscription;
        }

        private void setDisplayState(DisplayState displayState) {
            this.displayState = displayState;
            useLiveDisplayState = false;
        }

        private void useLiveDisplayState() {
            useLiveDisplayState = true;
        }

        private void setFormedForTest(boolean formed) {
            isFormed = formed;
        }

        private void setRemoteForTest(boolean remote) {
            this.remote = remote;
        }

        private @Nullable TickableSubscription getCapturedSubscription() {
            return capturedSubscription;
        }

        private int getDisplayCaptureCount() {
            return displayCaptureCount;
        }

        private int getDisplayRefreshCount() {
            return displayRefreshCount;
        }
    }
}
