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
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardFluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.miner.LargeMinerLogic;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

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
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
import static com.gregtechceu.gtceu.api.GTValues.IV;
import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.LuV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LargeMinerMachineLDLib2UITest {

    private static final String BATCH = "LargeMinerMachineLDLib2UI";
    private static final ResourceLocation TOGGLE_SILK_TOUCH_ACTION = GTCEu.id("toggle_large_miner_silk_touch");
    private static final ResourceLocation TOGGLE_CHUNK_MODE_ACTION = GTCEu.id("toggle_large_miner_chunk_mode");
    private static final Component FIRST_SERVER_LINE = Component.literal("first Large Miner server line");
    private static final Component SECOND_SERVER_LINE = Component.literal("second Large Miner server line");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void registeredTiersKeepConcreteLogicAndConstructionFormulas(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        int[] tiers = { EV, IV, LuV };
        int[] speeds = { 16, 12, 10 };
        int[] radii = { 24, 40, 56 };
        int[] fortunes = { 4, 5, 6 };
        int[] fluidPerTick = { 9, 8, 7 };

        for (int index = 0; index < tiers.length; index++) {
            int tier = tiers[index];
            MetaMachine registered = createMachine(GTMultiMachines.LARGE_MINER[tier]);
            helper.assertTrue(registered.getClass() == LargeMinerMachine.class &&
                    registered instanceof LDLib2MachineUIProvider &&
                    registered instanceof LDLib2FancyActionMachine,
                    "Large Miner tier did not create its concrete LDLib2 controller: " + tier);
            LargeMinerMachine miner = (LargeMinerMachine) registered;
            LargeMinerLogic logic = miner.getRecipeLogic();
            MutableMachineUIHolder holder = new MutableMachineUIHolder(miner);
            helper.assertTrue(miner.getTier() == tier && miner.getEnergyTier() == tier &&
                    miner.getMaxVoltage() == GTValues.V[tier] &&
                    miner.getDrillingFluidConsumePerTick() == fluidPerTick[index],
                    "Large Miner tier, empty-input voltage, or drilling-fluid formula changed: " + tier);
            helper.assertTrue(logic.getClass() == LargeMinerLogic.class && miner.getRecipeLogic() == logic &&
                    logic.getSpeed() == speeds[index] && logic.getMaximumRadius() == radii[index] &&
                    logic.getCurrentRadius() == radii[index] && logic.getFortune() == fortunes[index],
                    "Large Miner speed, radius, fortune, or specialized logic identity changed: " + tier);
            helper.assertTrue(miner.canCreateLDLib2UI(player, holder) &&
                    miner.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement &&
                    miner.getRecipeLogic() == logic,
                    "Large Miner UI rejected its matching holder or replaced mining logic: " + tier);
        }

        helper.assertTrue(LargeMinerMachine.getMaterial(EV) == GTMaterials.Steel &&
                LargeMinerMachine.getMaterial(IV) == GTMaterials.Titanium &&
                LargeMinerMachine.getMaterial(LuV) == GTMaterials.TungstenSteel,
                "Large Miner material tier mapping changed");
        helper.assertTrue(LargeMinerMachine.getCasingState(EV) ==
                GTBlocks.MATERIALS_TO_CASINGS.get(GTMaterials.Steel).get() &&
                LargeMinerMachine.getCasingState(IV) ==
                        GTBlocks.MATERIALS_TO_CASINGS.get(GTMaterials.Titanium).get() &&
                LargeMinerMachine.getCasingState(LuV) ==
                        GTBlocks.MATERIALS_TO_CASINGS.get(GTMaterials.TungstenSteel).get(),
                "Large Miner casing tier mapping changed");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void holderIdentityStalePagesAndUnsupportedPartsFailFast(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        LargeMinerMachine miner = requireLargeMiner(createMachine(GTMultiMachines.LARGE_MINER[EV]));
        MutableMachineUIHolder holder = new MutableMachineUIHolder(miner);

        MetaMachine wrongMachine = createMachine(GTMachines.MACERATOR[LV]);
        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(wrongMachine);
        helper.assertTrue(!miner.canCreateLDLib2UI(player, wrongHolder) &&
                createUIFails(miner, player, wrongHolder),
                "Large Miner accepted a holder for a different machine");

        LargeMinerMachine replacement = requireLargeMiner(createMachine(GTMultiMachines.LARGE_MINER[EV]));
        MutableMachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);
        helper.assertTrue(!miner.canCreateLDLib2UI(player, replacementHolder) &&
                createUIFails(miner, player, replacementHolder),
                "Large Miner accepted another same-definition controller instance");

        LDLib2FancyUIProvider stalePage = miner.createLDLib2Page(player, holder);
        holder.setMachine(replacement);
        boolean staleRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            staleRejected = expected.getMessage().contains("page holder");
        }
        helper.assertTrue(staleRejected,
                "Large Miner page accepted a same-definition replacement after opening");

        IMultiPart unsupportedPart = requirePart(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestLargeMinerMachine invalidMiner = new TestLargeMinerMachine(EV, List.of(unsupportedPart));
        helper.assertTrue(createUIFails(invalidMiner, player, new MutableMachineUIHolder(invalidMiner)),
                "Large Miner silently omitted a part without an LDLib2 Fancy page");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = BATCH)
    public static void allBuiltInAbilityFamiliesKeepOrderedOpeningScopedPagesAndLayout(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        List<MachineDefinition> providerDefinitions = List.of(
                GTMachines.ITEM_EXPORT_BUS[EV],
                GTMachines.DUAL_EXPORT_HATCH[LuV],
                GTAEMachines.ITEM_EXPORT_BUS_ME,
                GTMachines.FLUID_IMPORT_HATCH[EV],
                GTMachines.FLUID_IMPORT_HATCH_4X[EV],
                GTMachines.FLUID_IMPORT_HATCH_9X[EV],
                GTMachines.RESERVOIR_HATCH,
                GTMachines.DUAL_IMPORT_HATCH[LuV],
                GTAEMachines.FLUID_IMPORT_HATCH_ME,
                GTAEMachines.STOCKING_IMPORT_HATCH_ME,
                GTAEMachines.ME_PATTERN_BUFFER,
                GTAEMachines.ME_PATTERN_BUFFER_PROXY,
                GTMachines.ENERGY_INPUT_HATCH[EV],
                GTMachines.ENERGY_INPUT_HATCH_4A[EV],
                GTMachines.ENERGY_INPUT_HATCH_16A[EV]);
        for (MachineDefinition definition : providerDefinitions) {
            IMultiPart part = requirePart(createMachine(definition));
            helper.assertTrue(part instanceof LDLib2FancyPartUIProvider,
                    "Legal Large Miner part has no LDLib2 Fancy provider: " + definition.getId());
        }

        List<List<MachineDefinition>> legalFixtures = List.of(
                List.of(GTMachines.ITEM_EXPORT_BUS[EV], GTMachines.FLUID_IMPORT_HATCH[EV],
                        GTMachines.ENERGY_INPUT_HATCH[EV]),
                List.of(GTMachines.DUAL_EXPORT_HATCH[LuV], GTMachines.FLUID_IMPORT_HATCH_4X[EV],
                        GTMachines.ENERGY_INPUT_HATCH_4A[EV]),
                List.of(GTAEMachines.ITEM_EXPORT_BUS_ME, GTMachines.FLUID_IMPORT_HATCH_9X[EV],
                        GTMachines.ENERGY_INPUT_HATCH_16A[EV]),
                List.of(GTMachines.ITEM_EXPORT_BUS[IV], GTMachines.RESERVOIR_HATCH,
                        GTMachines.ENERGY_INPUT_HATCH[IV], GTMachines.ENERGY_INPUT_HATCH_4A[IV]),
                List.of(GTMachines.DUAL_EXPORT_HATCH[LuV], GTMachines.DUAL_IMPORT_HATCH[LuV],
                        GTMachines.ENERGY_INPUT_HATCH_4A[LuV]),
                List.of(GTAEMachines.ITEM_EXPORT_BUS_ME, GTAEMachines.FLUID_IMPORT_HATCH_ME,
                        GTMachines.ENERGY_INPUT_HATCH_16A[EV]),
                List.of(GTMachines.ITEM_EXPORT_BUS[LuV], GTAEMachines.STOCKING_IMPORT_HATCH_ME,
                        GTMachines.ENERGY_INPUT_HATCH[LuV]),
                List.of(GTMachines.DUAL_EXPORT_HATCH[LuV], GTAEMachines.ME_PATTERN_BUFFER,
                        GTMachines.ENERGY_INPUT_HATCH_4A[LuV]),
                List.of(GTAEMachines.ITEM_EXPORT_BUS_ME, GTAEMachines.ME_PATTERN_BUFFER_PROXY,
                        GTMachines.ENERGY_INPUT_HATCH_16A[LuV]));
        for (int fixtureIndex = 0; fixtureIndex < legalFixtures.size(); fixtureIndex++) {
            List<MachineDefinition> fixture = legalFixtures.get(fixtureIndex);
            List<IMultiPart> parts = new ArrayList<>(fixture.size());
            for (int partIndex = 0; partIndex < fixture.size(); partIndex++) {
                parts.add(requirePart(placeMachine(helper, new BlockPos(partIndex, 1, 0),
                        fixture.get(partIndex))));
            }
            assertLegalPartFixture(helper, player, parts, fixtureIndex == 0);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void modeActionsUseEmptyPayloadAndEnforceServerAuthority(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestLargeMinerMachine miner = new TestLargeMinerMachine(EV, List.of());
        miner.setLevel(helper.getLevel());
        miner.setFormedForTest(true);
        miner.useLiveDisplayContract();
        miner.getRecipeLogic().setCurrentRadius(1);
        miner.refreshDisplaySnapshot();
        LargeMinerLogic logic = miner.getRecipeLogic();
        int initialRefreshes = miner.getDisplayRefreshCount();

        MutableMachineUIHolder actionHolder = new MutableMachineUIHolder(miner);
        SyncActionData silkAction = miner.resolveLargeMinerDisplayAction(actionHolder, "silk_touch");
        SyncActionData chunkAction = miner.resolveLargeMinerDisplayAction(actionHolder, "chunk_mode");
        helper.assertTrue(silkAction.actionId().equals(TOGGLE_SILK_TOUCH_ACTION) &&
                chunkAction.actionId().equals(TOGGLE_CHUNK_MODE_ACTION) &&
                silkAction.sequence() == 0 && chunkAction.sequence() == 0 &&
                silkAction.payload().isEmpty() && chunkAction.payload().isEmpty(),
                "Large Miner display routes did not use two independent empty-payload protocols");
        helper.assertTrue(displayActionFails(miner, actionHolder, "unknown", "Unknown Large Miner display action"),
                "Large Miner display route accepted an unknown component id");
        LargeMinerMachine replacement = requireLargeMiner(createMachine(GTMultiMachines.LARGE_MINER[EV]));
        MutableMachineUIHolder staleActionHolder = new MutableMachineUIHolder(miner);
        staleActionHolder.setMachine(replacement);
        helper.assertTrue(displayActionFails(miner, staleActionHolder, "silk_touch", "opened controller"),
                "Large Miner display route accepted a replacement controller after opening");

        int frontFacingReadsBeforeSilk = miner.getFrontFacingReadCount();
        helper.assertTrue(dispatch(player, miner, silkAction) && logic.isSilkTouchMode() &&
                !logic.isChunkMode() && miner.getDisplayRefreshCount() == initialRefreshes + 1,
                "Large Miner silk-touch action did not execute once and refresh immediately");
        helper.assertTrue(miner.getFrontFacingReadCount() == frontFacingReadsBeforeSilk,
                "Large Miner silk-touch action reset its mining area");
        int frontFacingReadsBeforeChunk = miner.getFrontFacingReadCount();
        helper.assertTrue(dispatch(player, miner, chunkAction) && logic.isSilkTouchMode() &&
                logic.isChunkMode() && miner.getDisplayRefreshCount() == initialRefreshes + 2 &&
                miner.getFrontFacingReadCount() == frontFacingReadsBeforeChunk + 1,
                "Large Miner chunk-mode action did not execute once, reset once, and refresh immediately");
        List<Component> suffix = LargeMinerMachine.createLargeMinerDisplayText(miner.captureDisplayState());
        helper.assertTrue(miner.getDisplaySnapshot().subList(
                miner.getDisplaySnapshot().size() - suffix.size(), miner.getDisplaySnapshot().size()).equals(suffix),
                "Large Miner action refresh did not publish its changed mode state");

        MetaMachine wrongHolder = createMachine(GTMachines.MACERATOR[LV]);
        helper.assertTrue(!dispatch(player, wrongHolder, silkAction),
                "Large Miner mode action accepted a non-Large-Miner holder");
        DataComponentMap nonEmptyPayload = DataComponentMap.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("invalid payload"))
                .build();
        helper.assertTrue(!dispatch(player, miner,
                new SyncActionData(TOGGLE_SILK_TOUCH_ACTION, 0, nonEmptyPayload)),
                "Large Miner silk-touch action accepted a non-empty payload");
        helper.assertTrue(!dispatch(player, miner,
                new SyncActionData(TOGGLE_CHUNK_MODE_ACTION, 0, nonEmptyPayload)),
                "Large Miner chunk-mode action accepted a non-empty payload");

        boolean silkBeforeSpectator = logic.isSilkTouchMode();
        boolean chunkBeforeSpectator = logic.isChunkMode();
        int refreshesBeforeSpectator = miner.getDisplayRefreshCount();
        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorSilk;
        boolean spectatorChunk;
        try {
            spectatorSilk = dispatch(player, miner, silkAction);
            spectatorChunk = dispatch(player, miner, chunkAction);
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }
        helper.assertTrue(!spectatorSilk && !spectatorChunk &&
                logic.isSilkTouchMode() == silkBeforeSpectator && logic.isChunkMode() == chunkBeforeSpectator &&
                miner.getDisplayRefreshCount() == refreshesBeforeSpectator,
                "Spectator Large Miner action changed mode state or refreshed display");

        logic.setStatus(WorkLogic.Status.WORKING);
        int refreshesBeforeWorking = miner.getDisplayRefreshCount();
        helper.assertTrue(!dispatch(player, miner, silkAction) && !dispatch(player, miner, chunkAction) &&
                logic.isSilkTouchMode() == silkBeforeSpectator && logic.isChunkMode() == chunkBeforeSpectator &&
                miner.getDisplayRefreshCount() == refreshesBeforeWorking,
                "Working Large Miner accepted a mode action or refreshed display");
        logic.setStatus(WorkLogic.Status.IDLE);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void displayBranchesKeepCoordinatesButtonsAreaDoneAndSnapshotIdentity(GameTestHelper helper) {
        helper.assertTrue(LargeMinerMachine.createLargeMinerDisplayText(
                new LargeMinerMachine.DisplayState(false, 1, 2, 3, true, true, 24, true)).isEmpty(),
                "Unformed Large Miner appended formed-only display rows");

        LargeMinerMachine.DisplayState normalState = new LargeMinerMachine.DisplayState(
                true, Integer.MAX_VALUE, 12, -5, false, false, 24, false);
        List<Component> normal = LargeMinerMachine.createLargeMinerDisplayText(normalState);
        List<Component> expectedNormal = List.of(
                Component.translatable("gtpm.machine.miner.startx", 0),
                Component.translatable("gtpm.machine.miner.starty", 12),
                Component.translatable("gtpm.machine.miner.startz", -5),
                Component.translatable("gtpm.universal.tooltip.silk_touch")
                        .append(modeButton(false, "silk_touch")),
                Component.translatable("gtpm.universal.tooltip.chunk_mode")
                        .append(modeButton(false, "chunk_mode")),
                Component.translatable("gtpm.universal.tooltip.working_area", 49, 49));
        helper.assertTrue(normal.equals(expectedNormal),
                "Large Miner normal display changed coordinate fallback, click ids, area, or order");

        LargeMinerMachine.DisplayState chunkDoneState = new LargeMinerMachine.DisplayState(
                true, 7, Integer.MAX_VALUE, Integer.MAX_VALUE, true, true, 40, true);
        List<Component> chunkDone = LargeMinerMachine.createLargeMinerDisplayText(chunkDoneState);
        List<Component> expectedChunkDone = List.of(
                Component.translatable("gtpm.machine.miner.startx", 7),
                Component.translatable("gtpm.machine.miner.starty", 0),
                Component.translatable("gtpm.machine.miner.startz", 0),
                Component.translatable("gtpm.universal.tooltip.silk_touch")
                        .append(modeButton(true, "silk_touch")),
                Component.translatable("gtpm.universal.tooltip.chunk_mode")
                        .append(modeButton(true, "chunk_mode")),
                Component.translatable("gtpm.universal.tooltip.working_area_chunks", 5, 5),
                Component.translatable("gtpm.multiblock.large_miner.done")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));
        helper.assertTrue(chunkDone.equals(expectedChunkDone),
                "Large Miner chunk display changed coordinate fallback, click ids, area, done style, or order");
        boolean immutable = false;
        try {
            chunkDone.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Large Miner published a mutable display suffix");

        TestLargeMinerMachine miner = new TestLargeMinerMachine(EV, List.of());
        miner.setServerDisplay(List.of(FIRST_SERVER_LINE));
        miner.refreshDisplaySnapshot();
        List<Component> firstSnapshot = miner.getDisplaySnapshot();
        miner.refreshDisplaySnapshot();
        helper.assertTrue(miner.getDisplaySnapshot() == firstSnapshot,
                "Large Miner replaced an unchanged snapshot instance");
        int capturesBeforeRead = miner.getDisplayCaptureCount();
        List<Component> panelRead = new ArrayList<>();
        miner.addDisplayText(panelRead);
        helper.assertTrue(panelRead.equals(firstSnapshot) && miner.getDisplayCaptureCount() == capturesBeforeRead,
                "Large Miner panel read sampled live server state");

        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MutableMachineUIHolder holder = new MutableMachineUIHolder(miner);
        LDLib2FancyMachineUIElement shell = createShell(player, holder, miner.createLDLib2Page(player, holder));
        GTComponentPanelElement panel = descendants(shell.getChildren().getFirst()).stream()
                .filter(GTComponentPanelElement.class::isInstance)
                .map(GTComponentPanelElement.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Large Miner display panel is missing."));
        miner.setServerDisplay(List.of(SECOND_SERVER_LINE));
        miner.refreshDisplaySnapshot();
        panel.screenTick();
        helper.assertTrue(panel.getLastText().equals(List.of(SECOND_SERVER_LINE)) &&
                panel.getLastText().equals(miner.getDisplaySnapshot()),
                "Opened Large Miner panel did not observe its refreshed server snapshot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void serverAndClientLifecycleOwnSnapshotSubscription(GameTestHelper helper) {
        TestLargeMinerMachine miner = new TestLargeMinerMachine(EV, List.of());
        miner.setLevel(helper.getLevel());
        miner.useLiveDisplayContract();
        LargeMinerLogic logic = miner.getRecipeLogic();
        miner.onLoad();
        helper.assertTrue(miner.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                miner.getRecipeLogic() == logic,
                "Large Miner onLoad changed logic or omitted invalid_structure");

        miner.setUpwardsFacing(Direction.SOUTH);
        miner.formStructure(LargeMinerMachine.DEFAULT_STRUCTURE);
        TickableSubscription formedSubscription = miner.getCapturedSubscription();
        helper.assertTrue(miner.isFormed() && logic.getDir() == Direction.UP &&
                formedSubscription != null && formedSubscription.isStillSubscribed() &&
                !miner.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                miner.getRecipeLogic() == logic,
                "Large Miner formation did not preserve direction, logic, snapshot, or subscription");
        miner.invalidateStructure(LargeMinerMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(miner.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                !formedSubscription.isStillSubscribed(),
                "Large Miner invalidation did not publish invalid_structure and stop refresh");

        TestLargeMinerMachine partUnloadMiner = subscribedMiner();
        TickableSubscription partSubscription = partUnloadMiner.getCapturedSubscription();
        partUnloadMiner.onPartUnload();
        assertRuntimeDisplayCleared(helper, partUnloadMiner, partSubscription, "part unload");
        TestLargeMinerMachine controllerUnloadMiner = subscribedMiner();
        TickableSubscription controllerSubscription = controllerUnloadMiner.getCapturedSubscription();
        controllerUnloadMiner.onUnload();
        assertRuntimeDisplayCleared(helper, controllerUnloadMiner, controllerSubscription, "controller unload");

        TestLargeMinerMachine clientMiner = new TestLargeMinerMachine(EV, List.of());
        clientMiner.setLevel(helper.getLevel());
        clientMiner.setRemoteForTest(true);
        clientMiner.useLiveDisplayContract();
        clientMiner.setUpwardsFacing(Direction.NORTH);
        clientMiner.onLoad();
        clientMiner.formStructure(LargeMinerMachine.DEFAULT_STRUCTURE);
        clientMiner.invalidateStructure(LargeMinerMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(clientMiner.getDisplayCaptureCount() == 0 &&
                clientMiner.getCapturedSubscription() == null && clientMiner.getDisplaySnapshot().isEmpty() &&
                clientMiner.getRecipeLogic().getDir() == Direction.DOWN,
                "Large Miner client lifecycle sampled server state or skipped existing direction logic");
        clientMiner.onUnload();

        TestLargeMinerMachine loadedMiner = new TestLargeMinerMachine(EV, List.of());
        loadedMiner.setLevel(helper.getLevel());
        loadedMiner.setFormedForTest(true);
        loadedMiner.setServerDisplay(List.of(FIRST_SERVER_LINE));
        loadedMiner.onLoad();
        int refreshesBeforeTick = loadedMiner.getDisplayRefreshCount();
        helper.runAfterDelay(3, () -> {
            TickableSubscription loadedSubscription = loadedMiner.getCapturedSubscription();
            helper.assertTrue(loadedSubscription != null && loadedSubscription.isStillSubscribed(),
                    "Formed Large Miner onLoad did not initialize snapshot refresh");
            loadedSubscription.run();
            helper.assertTrue(loadedMiner.getDisplayRefreshCount() > refreshesBeforeTick,
                    "Large Miner subscription did not refresh while loaded");
            int refreshesBeforeUnload = loadedMiner.getDisplayRefreshCount();
            loadedMiner.onUnload();
            loadedSubscription.run();
            helper.assertTrue(!loadedSubscription.isStillSubscribed() &&
                    loadedMiner.getDisplayRefreshCount() == refreshesBeforeUnload &&
                    loadedMiner.getDisplaySnapshot().isEmpty(),
                    "Large Miner refresh or snapshot survived unload");
            helper.succeed();
        });
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = BATCH, timeoutTicks = 200)
    public static void energyFluidDirectionScannerAndRadiusSemanticsRemainUnchanged(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        EnergyHatchPartMachine energyHatch = requireEnergyHatch(placeMachine(helper, new BlockPos(0, 1, 0),
                GTMachines.ENERGY_INPUT_HATCH[LuV]));
        StandardFluidHatchPartMachine fluidHatch = requireFluidHatch(placeMachine(helper, new BlockPos(1, 1, 0),
                GTMachines.FLUID_IMPORT_HATCH[EV]));
        IMultiPart itemExport = requirePart(placeMachine(helper, new BlockPos(2, 1, 0),
                GTMachines.ITEM_EXPORT_BUS[EV]));
        TestLargeMinerMachine miner = new TestLargeMinerMachine(EV, List.of(energyHatch, fluidHatch, itemExport));
        miner.setLevel(helper.getLevel());
        miner.setUpwardsFacing(Direction.SOUTH);
        LargeMinerLogic logic = miner.getRecipeLogic();
        miner.formStructure(LargeMinerMachine.DEFAULT_STRUCTURE);

        helper.assertTrue(miner.getRecipeLogic() == logic && logic.getDir() == Direction.UP &&
                miner.getEnergyTier() == IV && logic.getVoltageTier() == LuV &&
                logic.getOverclockAmount() == 2 && miner.getMaxVoltage() == GTValues.V[IV],
                "Large Miner form changed logic identity, vertical direction, voltage clamp, or overclock formula");

        energyHatch.energyContainer.changeEnergy(energyHatch.energyContainer.getEnergyCapacity());
        fluidHatch.tank.setFluidInTank(0, GTMaterials.DrillingFluid.getFluid(1_000));
        long energyBefore = energyHatch.energyContainer.getEnergyStored();
        int fluidBefore = fluidHatch.tank.getFluidInTank(0).getAmount();
        helper.assertTrue(miner.drainInput(true) &&
                energyHatch.energyContainer.getEnergyStored() == energyBefore &&
                fluidHatch.tank.getFluidInTank(0).getAmount() == fluidBefore,
                "Large Miner simulated drain changed energy or drilling fluid");
        helper.assertTrue(miner.drainInput(false) &&
                energyBefore - energyHatch.energyContainer.getEnergyStored() == GTValues.VA[IV] &&
                fluidBefore - fluidHatch.tank.getFluidInTank(0).getAmount() == 18,
                "Large Miner execution changed its clamped EU/t or overclocked drilling-fluid cost");

        List<Component> scanner = miner.getDataInfo(PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO);
        helper.assertTrue(scanner.equals(List.of(Component.translatable(
                "gtpm.universal.tooltip.working_area", 49, 49))) &&
                miner.getDataInfo(PortableScannerBehavior.DisplayMode.SHOW_ELECTRICAL_INFO).isEmpty(),
                "Large Miner scanner changed its normal-area line or mode filter");

        ExtendedUseOnContext context = screwdriverContext(player, miner.getBlockPos());
        logic.setCurrentRadius(17);
        miner.onScrewdriverClick(context);
        helper.assertTrue(logic.getCurrentRadius() == 9,
                "Large Miner normal radius did not decrement by eight blocks");
        logic.setCurrentRadius(1);
        miner.onScrewdriverClick(context);
        helper.assertTrue(logic.getCurrentRadius() == logic.getMaximumRadius(),
                "Large Miner normal radius did not wrap to its maximum");

        logic.setCurrentRadius(1);
        logic.setChunkMode(true);
        helper.assertTrue(miner.getDataInfo(PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO).equals(
                List.of(Component.translatable("gtpm.universal.tooltip.working_area", 3, 3))),
                "Large Miner scanner switched to chunk units while chunk mode was active");
        logic.setCurrentRadius(17);
        miner.onScrewdriverClick(context);
        helper.assertTrue(logic.getCurrentRadius() == 1,
                "Large Miner chunk radius did not decrement by sixteen blocks");
        miner.onScrewdriverClick(context);
        helper.assertTrue(logic.getCurrentRadius() == logic.getMaximumRadius(),
                "Large Miner chunk radius did not wrap to its maximum");

        int activeRadius = logic.getCurrentRadius();
        logic.setStatus(WorkLogic.Status.WORKING);
        miner.onScrewdriverClick(context);
        helper.assertTrue(logic.getCurrentRadius() == activeRadius,
                "Active Large Miner changed radius from a screwdriver click");
        logic.setStatus(WorkLogic.Status.IDLE);
        helper.succeed();
    }

    private static void assertLegalPartFixture(GameTestHelper helper, ServerPlayer player,
                                               List<IMultiPart> parts, boolean verifyLayout) {
        TestLargeMinerMachine miner = new TestLargeMinerMachine(EV, parts);
        miner.setLevel(helper.getLevel());
        miner.setFormedForTest(true);
        miner.setServerDisplay(List.of(FIRST_SERVER_LINE));
        miner.refreshDisplaySnapshot();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(miner);
        LDLib2FancyUIProvider firstPage = miner.createLDLib2Page(player, holder);
        LDLib2FancyUIProvider secondPage = miner.createLDLib2Page(player, holder);

        List<Component> expectedTitles = parts.stream()
                .map(IMultiPart::self)
                .map(MetaMachine::getDefinition)
                .map(MachineDefinition::getDescriptionId)
                .<Component>map(Component::translatable)
                .toList();
        helper.assertTrue(firstPage != secondPage && firstPage.getSubTabs().size() == parts.size() &&
                firstPage.getSubTabs().stream().map(LDLib2FancyUIProvider::getTitle).toList()
                        .equals(expectedTitles),
                "Large Miner omitted or reordered a legal ability page");
        for (int index = 0; index < parts.size(); index++) {
            helper.assertTrue(firstPage.getSubTabs().get(index) != secondPage.getSubTabs().get(index),
                    "Large Miner reused a legal part page across openings: " +
                            parts.get(index).self().getDefinition().getId());
        }

        LDLib2FancyMachineUIElement firstShell = createShell(player, holder, firstPage);
        LDLib2FancyMachineUIElement secondShell = createShell(player, holder, secondPage);
        List<UIElement> configuratorTabs = firstShell.getConfiguratorPanel().getChildren();
        helper.assertTrue(configuratorTabs.size() == 2 &&
                configuratorTabs.getFirst().getChildren().size() == 2 &&
                configuratorTabs.getLast().getChildren().size() == 1 &&
                firstShell.getSideTabsElement().getChildren().size() == 2 &&
                firstShell.getTooltipsPanel().getChildren().isEmpty(),
                "Large Miner did not expose a voiding selector, working button, and one directional page");
        UIElement firstContainer = firstShell.getChildren().getFirst();
        UIElement secondContainer = secondShell.getChildren().getFirst();
        helper.assertTrue(firstContainer.getChildren().size() == parts.size() + 1 &&
                secondContainer.getChildren().size() == parts.size() + 1,
                "Large Miner did not build every part page in a legal fixture");
        for (int index = 0; index < firstContainer.getChildren().size(); index++) {
            helper.assertTrue(firstContainer.getChildren().get(index) != secondContainer.getChildren().get(index),
                    "Large Miner reused a controller or part element across openings");
        }

        if (verifyLayout) {
            assertMainPageLayout(helper, firstContainer.getChildren().getFirst(), miner);
        }
        clickButton(firstShell.getSideTabsElement().getChildren().get(1));
        clickButton(secondShell.getSideTabsElement().getChildren().get(1));
        helper.assertTrue(firstContainer.getChildren().size() == parts.size() + 2 &&
                secondContainer.getChildren().size() == parts.size() + 2 &&
                firstContainer.getChildren().getLast() != secondContainer.getChildren().getLast(),
                "Large Miner reused its directional page across openings");
        for (IMultiPart part : parts) {
            assertPartPageUsesDedicatedHolder(helper, player, part);
        }
    }

    private static void assertMainPageLayout(GameTestHelper helper, UIElement mainPage,
                                             LargeMinerMachine miner) {
        helper.assertTrue(mainPage.getSizeWidth() == 190 && mainPage.getSizeHeight() == 125 &&
                mainPage.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.BACKGROUND_INVERSE,
                "Large Miner main page lost its 190x125 inverse-background body");
        helper.assertTrue(mainPage.getChildren().size() == 1 &&
                mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                "Large Miner main page did not create one display scroller");
        GTScrollerViewElement scroller = (GTScrollerViewElement) mainPage.getChildren().getFirst();
        helper.assertTrue(scroller.getLayoutX() == 4 && scroller.getLayoutY() == 4 &&
                scroller.getSizeWidth() == 182 && scroller.getSizeHeight() == 117 &&
                scroller.getStyle().getInline(PropertyRegistry.BACKGROUND) == miner.getScreenTexture() &&
                scroller.viewPort.getStyle().getInline(PropertyRegistry.BACKGROUND) == miner.getScreenTexture() &&
                scroller.getScrollerViewStyle().mode() == ScrollerMode.VERTICAL &&
                scroller.getScrollerViewStyle().verticalScrollDisplay() == ScrollDisplay.AUTO &&
                scroller.getScrollerViewStyle().horizontalScrollDisplay() == ScrollDisplay.NEVER,
                "Large Miner display scroller lost its bounds, viewport texture, or vertical-only scrolling");
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
                labels.getFirst().getTextStyle().textAlignVertical() == Vertical.CENTER && panels.size() == 1 &&
                panels.getFirst().getLayoutX() == 4 && panels.getFirst().getLayoutY() == 17 &&
                panels.getFirst().getMaxWidthLimit() == 200 &&
                panels.getFirst().getLastText().equals(miner.getDisplaySnapshot()),
                "Large Miner title or snapshot panel lost its legacy bounds");
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        LargeMinerMachineActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO,
                null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static ExtendedUseOnContext screwdriverContext(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        return new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit);
    }

    private static Component modeButton(boolean enabled, String componentData) {
        return GTComponentPanelElement.withButton(Component.literal("[")
                .append(enabled ? Component.translatable("gtpm.creative.activity.on") :
                        Component.translatable("gtpm.creative.activity.off"))
                .append(Component.literal("]")), componentData);
    }

    private static boolean createUIFails(LargeMinerMachine miner, ServerPlayer player, MachineUIHolder holder) {
        try {
            miner.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static boolean displayActionFails(LargeMinerMachine miner, MachineUIHolder holder,
                                              String componentData, String expectedMessage) {
        try {
            miner.resolveLargeMinerDisplayAction(holder, componentData);
            return false;
        } catch (IllegalArgumentException expected) {
            return expected.getMessage().contains(expectedMessage);
        }
    }

    private static void assertPartPageUsesDedicatedHolder(GameTestHelper helper, ServerPlayer player,
                                                          IMultiPart part) {
        if (!(part instanceof LDLib2FancyPartUIProvider provider)) {
            throw new IllegalStateException("Legal Large Miner part has no LDLib2 Fancy page.");
        }
        MutableMachineUIHolder partHolder = new MutableMachineUIHolder(part.self());
        LDLib2FancyUIProvider partPage = provider.createLDLib2FancyPage(player, partHolder);
        helper.assertTrue(createShell(player, partHolder, partPage).getHolder() == partHolder,
                "Large Miner part page lost its dedicated holder");
    }

    private static TestLargeMinerMachine subscribedMiner() {
        TestLargeMinerMachine miner = new TestLargeMinerMachine(EV, List.of());
        miner.setFormedForTest(true);
        miner.setServerDisplay(List.of(FIRST_SERVER_LINE));
        miner.refreshDisplaySnapshot();
        miner.getDisplaySnapshotSubscription().updateSubscription();
        return miner;
    }

    private static void assertRuntimeDisplayCleared(GameTestHelper helper, TestLargeMinerMachine miner,
                                                    @Nullable TickableSubscription subscription,
                                                    String lifecycleEvent) {
        helper.assertTrue(miner.getDisplaySnapshot().isEmpty(),
                "Large Miner retained display text after " + lifecycleEvent);
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Large Miner retained its display subscription after " + lifecycleEvent);
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

    private static LargeMinerMachine requireLargeMiner(MetaMachine machine) {
        if (!(machine instanceof LargeMinerMachine miner)) {
            throw new IllegalStateException("Large Miner definition did not create its expected controller.");
        }
        return miner;
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

    private static StandardFluidHatchPartMachine requireFluidHatch(MetaMachine machine) {
        if (!(machine instanceof StandardFluidHatchPartMachine fluidHatch)) {
            throw new IllegalStateException("Fluid Input Hatch definition did not create its expected type.");
        }
        return fluidHatch;
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
        MachineDefinition definition = GTMultiMachines.LARGE_MINER[tier];
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

    private static final class TestLargeMinerMachine extends LargeMinerMachine {

        private final List<IMultiPart> parts;
        private List<Component> serverDisplay = List.of();
        private @Nullable TickableSubscription capturedSubscription;
        private boolean useLiveDisplayContract;
        private boolean remote;
        private int displayCaptureCount;
        private int displayRefreshCount;
        private int frontFacingReadCount;

        private TestLargeMinerMachine(int tier, List<IMultiPart> parts) {
            super(info(tier), tier, 64 / tier, 2 * tier - 5, tier, 8 - (tier - 5));
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
        public Direction getFrontFacing() {
            frontFacingReadCount++;
            return super.getFrontFacing();
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
            capturedSubscription = super.subscribeServerTick(runnable);
            if (capturedSubscription == null) {
                throw new IllegalStateException("Server-side display test did not create a tick subscription.");
            }
            return capturedSubscription;
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

        private @Nullable TickableSubscription getCapturedSubscription() {
            return capturedSubscription;
        }

        private int getDisplayCaptureCount() {
            return displayCaptureCount;
        }

        private int getDisplayRefreshCount() {
            return displayRefreshCount;
        }

        private int getFrontFacingReadCount() {
            return frontFacingReadCount;
        }
    }
}
