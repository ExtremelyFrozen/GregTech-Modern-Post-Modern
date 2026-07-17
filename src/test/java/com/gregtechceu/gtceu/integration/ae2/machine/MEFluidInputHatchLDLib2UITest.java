package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachineActions;
import com.gregtechceu.gtceu.integration.ae2.gui.element.AEFluidConfigAmountEditorElement;
import com.gregtechceu.gtceu.integration.ae2.gui.element.AEFluidConfigElement;
import com.gregtechceu.gtceu.integration.ae2.gui.element.AEFluidConfigSlotElement;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEFluidInputHatchLDLib2UITest {

    private static final String BATCH = "MEFluidInputHatchLDLib2UI";
    private static final ResourceLocation SET_CONFIG_ACTION = GTCEu.id("set_me_fluid_config");
    private static final ResourceLocation SET_AMOUNT_ACTION = GTCEu.id("set_me_fluid_config_amount");
    private static final ResourceLocation FLUID_HATCH_CLICK_ACTION = FluidHatchPartMachineActions
            .createClickFluidSlotAction(0, false).actionId();

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void openingScopeDefinitionAndStaleHolderAreEnforced(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputHatchPartMachine input = createInput();
        MEInputHatchPartMachine replacementInput = createInput();
        MEStockingHatchPartMachine stocking = createStocking();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(input);

        helper.assertTrue(input.canCreateLDLib2UI(player, holder),
                "ordinary ME fluid input rejected its matching holder");
        LDLib2FancyUIProvider first = input.createLDLib2Page(player, holder);
        LDLib2FancyUIProvider second = input.createLDLib2FancyPage(player, holder);
        helper.assertTrue(first != second, "ME fluid input reused a page provider across openings");
        LDLib2FancyMachineUIElement firstShell = new LDLib2FancyMachineUIElement(first, player.getInventory(),
                holder, first.getLDLib2PageWidth(), first.getLDLib2PageHeight());

        boolean mismatchRejected = false;
        try {
            input.createLDLib2Page(player, new MutableMachineUIHolder(stocking));
        } catch (IllegalArgumentException expected) {
            mismatchRejected = true;
        }
        helper.assertTrue(mismatchRejected, "ME fluid input accepted another machine's holder");

        holder.machine = replacementInput;
        boolean staleRejected = false;
        try {
            first.createLDLib2MainPage(firstShell);
        } catch (IllegalStateException expected) {
            staleRejected = true;
        }
        helper.assertTrue(staleRejected, "opened ME fluid page accepted a stale holder");

        MEInputHatchPartMachine wrongDefinition = new MEInputHatchPartMachine(info(GTAEMachines.ITEM_IMPORT_BUS_ME));
        helper.assertTrue(!wrongDefinition.canCreateLDLib2UI(player, new MutableMachineUIHolder(wrongDefinition)),
                "ME fluid page accepted a non-fluid machine definition");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void pagesExposeFixedSixteenSlotLayoutAndVariantConfigurators(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputHatchPartMachine input = createInput();
        MEStockingHatchPartMachine stocking = createStocking();
        input.setOnline(true);
        MutableMachineUIHolder inputHolder = new MutableMachineUIHolder(input);
        MutableMachineUIHolder stockingHolder = new MutableMachineUIHolder(stocking);
        LDLib2FancyUIProvider inputPage = input.createLDLib2Page(player, inputHolder);
        LDLib2FancyUIProvider stockingPage = stocking.createLDLib2Page(player, stockingHolder);

        helper.assertTrue(inputPage.getLDLib2PageWidth() == 150 && inputPage.getLDLib2PageHeight() == 88,
                "ordinary ME fluid page did not preserve its 150x88 body");
        helper.assertTrue(stockingPage.getLDLib2PageWidth() == 150 && stockingPage.getLDLib2PageHeight() == 88,
                "stocking ME fluid page did not preserve its 150x88 body");
        helper.assertTrue(inputPage.getPageGroupingData() != null &&
                inputPage.getPageGroupingData().groupPositionWeight() == 1,
                "ME fluid page omitted import grouping weight one");

        LDLib2FancyMachineUIElement inputShell = createShell(player, input, inputHolder);
        LDLib2FancyMachineUIElement stockingShell = createShell(player, stocking, stockingHolder);
        List<AEFluidConfigSlotElement> slots = descendants(pageRoot(inputShell)).stream()
                .filter(element -> element instanceof AEFluidConfigSlotElement)
                .map(element -> (AEFluidConfigSlotElement) element)
                .toList();
        helper.assertTrue(slots.size() == 16, "ordinary ME fluid page did not expose sixteen config/stock columns");
        for (int index = 0; index < slots.size(); index++) {
            AEFluidConfigSlotElement slot = slots.get(index);
            UITemplate.LDLib2Bounds bounds = UITemplate.getLDLib2Bounds(slot);
            helper.assertTrue(slot.getIndex() == index && bounds.width() == 18 && bounds.height() == 36,
                    "ME fluid slot " + index + " did not preserve its index or 18x36 bounds");
            helper.assertTrue(bounds.x() == 3 + index % 8 * 18 && bounds.y() == 10 + index / 8 * 38,
                    "ME fluid slot " + index + " did not preserve its fixed grid coordinates");
        }
        List<AEFluidConfigAmountEditorElement> inputEditors = descendants(pageRoot(inputShell)).stream()
                .filter(element -> element instanceof AEFluidConfigAmountEditorElement)
                .map(element -> (AEFluidConfigAmountEditorElement) element)
                .toList();
        List<AEFluidConfigAmountEditorElement> stockingEditors = descendants(pageRoot(stockingShell)).stream()
                .filter(element -> element instanceof AEFluidConfigAmountEditorElement)
                .map(element -> (AEFluidConfigAmountEditorElement) element)
                .toList();
        helper.assertTrue(inputEditors.size() == 1 && !inputEditors.getFirst().isVisible(),
                "ordinary ME fluid page omitted its selection-scoped amount editor");
        helper.assertTrue(stockingEditors.size() == 1 && !stockingEditors.getFirst().isVisible(),
                "stocking ME fluid page exposed a visible amount editor");
        helper.assertTrue(descendants(pageRoot(inputShell)).stream()
                .anyMatch(element -> "me_network_status".equals(element.getId())),
                "ME fluid page omitted its synchronized online status label");
        helper.assertTrue(input.getFluidConfigSnapshot().online(),
                "online status label did not have an online snapshot source");
        helper.assertTrue(inputShell.getConfiguratorPanel().getChildren().size() == 2,
                "ordinary ME fluid page did not expose working and circuit configurators");
        helper.assertTrue(stockingShell.getConfiguratorPanel().getChildren().size() == 4,
                "stocking ME fluid page did not expose working, circuit, auto-pull, and auto-stocking configurators");
        helper.assertTrue(inputShell.getSideTabsElement().getChildren().size() == 2,
                "ordinary ME fluid page omitted its directional side page");
        helper.assertTrue(stockingShell.getSideTabsElement().getChildren().size() == 1,
                "stocking ME fluid page exposed a directional or cover side page");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void configClicksWheelGhostAndStockEachSendExactlyOneOpenedHolderAction(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputHatchPartMachine input = createInput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(input);
        List<CapturedAction> actions = new ArrayList<>();
        UIElement root = input.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> Boolean.TRUE.equals(event.customData));
        AEFluidConfigElement configRoot = (AEFluidConfigElement) root.getChildren().getFirst();
        AEFluidConfigSlotElement slot = configRoot.getSlots().getFirst();

        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        click(slot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        assertOneAction(helper, actions, holder, SET_CONFIG_ACTION, "left config click");
        helper.assertTrue(actionFluid(actions).is(Fluids.WATER),
                "left config click did not send the carried water payload");

        actions.clear();
        click(slot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
        assertOneAction(helper, actions, holder, SET_CONFIG_ACTION, "right config clear");
        helper.assertTrue(actionFluid(actions).isEmpty(), "right config click did not send an empty clear payload");

        for (int index = 0; index < 4; index++) {
            input.getMEFluidConfigSlot(index).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));
        }
        player.containerMenu.setCarried(new ItemStack(Items.STONE));
        click(slot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        configRoot.screenTick();
        AEFluidConfigAmountEditorElement amountEditor = descendants(configRoot).stream()
                .filter(element -> element instanceof AEFluidConfigAmountEditorElement)
                .map(element -> (AEFluidConfigAmountEditorElement) element)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ordinary ME fluid page omitted its amount editor"));
        helper.assertTrue(amountEditor.isVisible(),
                "selected ordinary config did not reveal its amount editor after the snapshot arrived");
        actions.clear();
        wheel(slot.getConfigElement(), 1, false);
        assertOneAction(helper, actions, holder, SET_AMOUNT_ACTION, "ordinary config wheel");
        helper.assertTrue(actionInteger(actions, "amount") == 1_001,
                "ordinary config wheel did not increment the snapshot amount by one");

        actions.clear();
        wheel(configRoot.getSlots().get(1).getConfigElement(), 1, true);
        assertOneAction(helper, actions, holder, SET_AMOUNT_ACTION, "Ctrl config wheel");
        helper.assertTrue(actionInteger(actions, "amount") == 2_000,
                "Ctrl config wheel did not double the snapshot amount");

        actions.clear();
        wheel(configRoot.getSlots().get(2).getConfigElement(), -1, false);
        assertOneAction(helper, actions, holder, SET_AMOUNT_ACTION, "reverse ordinary config wheel");
        helper.assertTrue(actionInteger(actions, "amount") == 999,
                "reverse ordinary config wheel did not decrement the snapshot amount by one");

        actions.clear();
        wheel(configRoot.getSlots().get(3).getConfigElement(), -1, true);
        assertOneAction(helper, actions, holder, SET_AMOUNT_ACTION, "reverse Ctrl config wheel");
        helper.assertTrue(actionInteger(actions, "amount") == 500,
                "reverse Ctrl config wheel did not halve the snapshot amount");

        actions.clear();
        slot.getConfigElement().setFluid(new FluidStack(Fluids.LAVA, 0));
        assertOneAction(helper, actions, holder, SET_CONFIG_ACTION, "XEI ghost fluid drop");
        helper.assertTrue(actionFluid(actions).is(Fluids.LAVA) &&
                actionFluid(actions).getAmount() == FluidType.BUCKET_VOLUME,
                "XEI zero-amount ghost drop was not normalized to one lava bucket");

        input.getMEFluidConfigSlot(0).setStock(new GenericStack(AEFluidKey.of(Fluids.WATER),
                FluidType.BUCKET_VOLUME));
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        actions.clear();
        click(slot.getStockElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, true);
        assertOneAction(helper, actions, holder, FLUID_HATCH_CLICK_ACTION, "Shift stock click");
        helper.assertTrue(actionBoolean(actions, "shift"),
                "Shift stock click did not preserve its all-containers flag");

        player.containerMenu.setCarried(new ItemStack(Items.STONE));
        actions.clear();
        click(slot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        helper.assertTrue(actions.isEmpty(), "non-fluid carried item sent a config action");

        MEStockingHatchPartMachine stocking = createStocking();
        MutableMachineUIHolder stockingHolder = new MutableMachineUIHolder(stocking);
        List<CapturedAction> stockingActions = new ArrayList<>();
        UIElement stockingRoot = stocking.createLDLib2MainElement(player, stockingHolder,
                (sentHolder, action) -> stockingActions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> Boolean.TRUE.equals(event.customData));
        AEFluidConfigSlotElement stockingSlot = ((AEFluidConfigElement) stockingRoot.getChildren().getFirst())
                .getSlots().getFirst();
        stocking.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));
        stocking.getMEFluidConfigSlot(0).setStock(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        wheel(stockingSlot.getConfigElement(), 1, false);
        click(stockingSlot.getStockElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        helper.assertTrue(stockingActions.isEmpty(),
                "stocking amount wheel or stock click sent a forbidden action");

        stocking.setAutoPull(true);
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        click(stockingSlot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        stockingSlot.getConfigElement().setFluid(new FluidStack(Fluids.LAVA, FluidType.BUCKET_VOLUME));
        helper.assertTrue(stockingActions.isEmpty(),
                "auto-pull config click or ghost drop sent a forbidden action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void wheelAmountKeepsLatestPendingValueUntilTheFinalSnapshotArrives(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputHatchPartMachine input = createInput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(input);
        List<CapturedAction> actions = new ArrayList<>();
        input.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));
        UIElement root = input.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> Boolean.TRUE.equals(event.customData));
        AEFluidConfigElement configRoot = (AEFluidConfigElement) root.getChildren().getFirst();
        AEFluidConfigSlotElement slot = configRoot.getSlots().getFirst();
        AEFluidConfigAmountEditorElement editor = amountEditor(configRoot);
        player.containerMenu.setCarried(new ItemStack(Items.STONE));
        click(slot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        configRoot.screenTick();

        wheel(slot.getConfigElement(), 1, false);
        wheel(slot.getConfigElement(), 1, false);
        configRoot.screenTick();
        helper.assertTrue(actions.size() == 2 && actionInteger(actions, 0, "amount") == 1_001 &&
                actionInteger(actions, 1, "amount") == 1_002 && "1002".equals(amountText(editor)),
                "consecutive amount wheels did not project 1001 then 1002");
        helper.assertTrue(dispatch(player, input, actions.getFirst().action()),
                "first pending wheel action was rejected");
        configRoot.screenTick();
        helper.assertTrue("1002".equals(amountText(editor)),
                "intermediate wheel snapshot replaced the latest pending amount");
        helper.assertTrue(dispatch(player, input, actions.get(1).action()),
                "final pending wheel action was rejected");
        configRoot.screenTick();
        GenericStack finalWheelConfig = input.getMEFluidConfigSlot(0).getConfig();
        helper.assertTrue(finalWheelConfig != null && finalWheelConfig.amount() == 1_002 &&
                "1002".equals(amountText(editor)),
                "final wheel snapshot did not confirm the projected amount");

        input.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));
        configRoot.screenTick();
        actions.clear();
        wheel(slot.getConfigElement(), 1, true);
        wheel(slot.getConfigElement(), 1, true);
        configRoot.screenTick();
        helper.assertTrue(actions.size() == 2 && actionInteger(actions, 0, "amount") == 2_000 &&
                actionInteger(actions, 1, "amount") == 4_000 && "4000".equals(amountText(editor)),
                "consecutive Ctrl wheels did not project 2000 then 4000");
        helper.assertTrue(dispatch(player, input, actions.getFirst().action()),
                "first pending Ctrl wheel action was rejected");
        configRoot.screenTick();
        helper.assertTrue("4000".equals(amountText(editor)),
                "intermediate Ctrl wheel snapshot replaced the latest pending amount");
        helper.assertTrue(dispatch(player, input, actions.get(1).action()),
                "final pending Ctrl wheel action was rejected");
        configRoot.screenTick();
        GenericStack finalCtrlWheelConfig = input.getMEFluidConfigSlot(0).getConfig();
        helper.assertTrue(finalCtrlWheelConfig != null && finalCtrlWheelConfig.amount() == 4_000 &&
                "4000".equals(amountText(editor)),
                "final Ctrl wheel snapshot did not confirm the projected amount");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void realMachineActionsApplyConfigAmountClearAndAutoPullWithoutStaleKeyWrites(
                                                                                                GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputHatchPartMachine input = createInput();
        MEStockingHatchPartMachine stocking = createStocking();
        FluidStack water = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);

        helper.assertTrue(dispatch(player, input, MEFluidConfigActions.createSetConfigAction(3, water)),
                "real ordinary hatch rejected its config action");
        GenericStack configuredWater = input.getMEFluidConfigSlot(3).getConfig();
        helper.assertTrue(configuredWater != null && configuredWater.what().equals(AEFluidKey.of(water)) &&
                configuredWater.amount() == FluidType.BUCKET_VOLUME &&
                input.getFluidConfigSnapshot().slots().get(3).config() != null,
                "real config action did not update the target slot and snapshot");

        helper.assertTrue(dispatch(player, input,
                MEFluidConfigActions.createSetAmountAction(3, water, 2_000)),
                "real ordinary hatch rejected its amount action");
        GenericStack amountConfig = input.getMEFluidConfigSlot(3).getConfig();
        GenericStack amountSnapshot = input.getFluidConfigSnapshot().slots().get(3).config();
        helper.assertTrue(amountConfig != null && amountSnapshot != null &&
                amountConfig.amount() == 2_000 && amountSnapshot.amount() == 2_000,
                "real amount action did not update the target slot and snapshot");

        SyncActionData staleWaterAmount = MEFluidConfigActions.createSetAmountAction(3, water, 3_000);
        input.getMEFluidConfigSlot(3).setConfig(new GenericStack(AEFluidKey.of(Fluids.LAVA), 1_500));
        boolean staleRejected = !dispatch(player, input, staleWaterAmount);
        GenericStack replacementConfig = input.getMEFluidConfigSlot(3).getConfig();
        helper.assertTrue(staleRejected && replacementConfig != null &&
                replacementConfig.what().equals(AEFluidKey.of(Fluids.LAVA)) &&
                replacementConfig.amount() == 1_500,
                "stale water amount action changed the replacement lava configuration");

        helper.assertTrue(dispatch(player, input,
                MEFluidConfigActions.createSetConfigAction(3, FluidStack.EMPTY)),
                "real ordinary hatch rejected its clear action");
        helper.assertTrue(input.getMEFluidConfigSlot(3).getConfig() == null &&
                input.getFluidConfigSnapshot().slots().get(3).config() == null,
                "real clear action retained the target config or snapshot value");

        stocking.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1));
        helper.assertTrue(dispatch(player, stocking, MEFluidConfigActions.createSetAutoPullAction(true)) &&
                stocking.isAutoPull() && stocking.getFluidConfigSnapshot().autoPull(),
                "real stocking hatch did not enable auto-pull through its action");
        helper.assertTrue(dispatch(player, stocking, MEFluidConfigActions.createSetAutoPullAction(false)) &&
                !stocking.isAutoPull() && !stocking.getFluidConfigSnapshot().autoPull() &&
                stocking.getMEFluidConfigSlot(0).getConfig() == null,
                "real stocking hatch did not disable auto-pull and clear managed config");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void amountEditorKeepsLatestPendingValueUntilTheFinalSnapshotArrives(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputHatchPartMachine input = createInput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(input);
        List<CapturedAction> actions = new ArrayList<>();
        input.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));
        UIElement root = input.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> false);
        AEFluidConfigElement configRoot = (AEFluidConfigElement) root.getChildren().getFirst();
        AEFluidConfigAmountEditorElement editor = amountEditor(configRoot);

        player.containerMenu.setCarried(new ItemStack(Items.STONE));
        click(configRoot.getSlots().getFirst().getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        configRoot.screenTick();
        actions.clear();
        commandClick(editor.getAmountInput().getChildren().getLast());
        commandClick(editor.getAmountInput().getChildren().getLast());

        helper.assertTrue(actions.size() == 2 &&
                actionInteger(actions, 0, "amount") == 1_001 &&
                actionInteger(actions, 1, "amount") == 1_002,
                "consecutive amount increments did not project 1001 then 1002");
        helper.assertTrue("1002".equals(amountText(editor)),
                "amount editor immediately reverted to the old snapshot value");

        helper.assertTrue(dispatch(player, input, actions.getFirst().action()),
                "first pending amount action was rejected");
        configRoot.screenTick();
        helper.assertTrue("1002".equals(amountText(editor)) && actions.size() == 2,
                "intermediate 1001 snapshot replaced the latest pending value or emitted another action");

        helper.assertTrue(dispatch(player, input, actions.get(1).action()),
                "final pending amount action was rejected");
        configRoot.screenTick();
        input.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_003));
        configRoot.screenTick();
        helper.assertTrue("1003".equals(amountText(editor)),
                "confirmed pending amount did not return control to authoritative snapshots");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void amountEditorScopesPendingValuesBySlotKeyOpeningAndHolderValidity(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputHatchPartMachine input = createInput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(input);
        AtomicBoolean canSend = new AtomicBoolean(true);
        List<CapturedAction> firstActions = new ArrayList<>();
        List<CapturedAction> secondActions = new ArrayList<>();
        input.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));
        input.getMEFluidConfigSlot(1).setConfig(new GenericStack(AEFluidKey.of(Fluids.LAVA), 2_000));

        UIElement firstRoot = input.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> firstActions.add(new CapturedAction(sentHolder, action)),
                canSend::get, event -> false);
        AEFluidConfigElement firstConfig = (AEFluidConfigElement) firstRoot.getChildren().getFirst();
        AEFluidConfigAmountEditorElement firstEditor = amountEditor(firstConfig);
        player.containerMenu.setCarried(new ItemStack(Items.STONE));
        click(firstConfig.getSlots().get(0).getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        firstConfig.screenTick();
        commandClick(firstEditor.getAmountInput().getChildren().getLast());
        helper.assertTrue("1001".equals(amountText(firstEditor)),
                "first slot did not retain its pending amount projection");

        UIElement secondRoot = input.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> secondActions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> false);
        AEFluidConfigElement secondConfig = (AEFluidConfigElement) secondRoot.getChildren().getFirst();
        AEFluidConfigAmountEditorElement secondEditor = amountEditor(secondConfig);
        click(secondConfig.getSlots().get(0).getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        secondConfig.screenTick();
        helper.assertTrue("1000".equals(amountText(secondEditor)) && secondActions.isEmpty(),
                "a new opening inherited another editor's pending amount");

        click(firstConfig.getSlots().get(1).getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        firstConfig.screenTick();
        commandClick(firstEditor.getAmountInput().getChildren().getLast());
        helper.assertTrue("2001".equals(amountText(firstEditor)) && firstActions.size() == 2 &&
                actionInteger(firstActions, 1, "slot") == 1,
                "second slot did not create an independent pending projection");
        click(firstConfig.getSlots().get(0).getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        firstConfig.screenTick();
        helper.assertTrue("1001".equals(amountText(firstEditor)),
                "switching slots discarded the first slot's pending projection");

        input.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.LAVA), 3_000));
        firstConfig.screenTick();
        helper.assertTrue("3000".equals(amountText(firstEditor)),
                "replacement fluid key retained the previous key's pending amount");
        input.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));
        firstConfig.screenTick();
        helper.assertTrue("1000".equals(amountText(firstEditor)),
                "restored fluid key resurrected an invalidated pending amount");

        commandClick(firstEditor.getAmountInput().getChildren().getLast());
        canSend.set(false);
        firstConfig.screenTick();
        helper.assertTrue(firstEditor.isActive(),
                "invalid holder transport deactivated the editor container needed for outside-click capture");
        helper.assertTrue(!firstEditor.getAmountInput().isActive(),
                "invalid holder transport left the amount input active");
        helper.assertTrue("1000".equals(amountText(firstEditor)),
                "invalid holder transport retained the pending amount projection");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void amountEditorClosesOnOutsideClickWithoutChangingConfiguration(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputHatchPartMachine input = createInput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(input);
        List<CapturedAction> actions = new ArrayList<>();
        input.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000));

        UIElement root = input.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> false);
        AEFluidConfigElement configRoot = (AEFluidConfigElement) root.getChildren().getFirst();
        AEFluidConfigAmountEditorElement editor = amountEditor(configRoot);

        click(configRoot.getSlots().getFirst().getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        configRoot.screenTick();
        helper.assertTrue(configRoot.getSelectedSlot() == 0 && editor.isVisible(),
                "left-clicking a configured slot did not open its amount editor");

        click(editor.getAmountInput().getChildren().get(1), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        configRoot.screenTick();
        helper.assertTrue(configRoot.getSelectedSlot() == 0 && editor.isVisible(),
                "clicking inside the amount editor closed it");

        click(configRoot.getSlots().get(1).getStockElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        configRoot.screenTick();
        GenericStack config = input.getMEFluidConfigSlot(0).getConfig();
        helper.assertTrue(configRoot.getSelectedSlot() == -1 && !editor.isVisible(),
                "clicking outside the amount editor did not close it");
        helper.assertTrue(actions.isEmpty() && config != null && config.amount() == 1_000 &&
                config.what().equals(AEFluidKey.of(Fluids.WATER)),
                "closing the amount editor changed the configuration or sent an action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void stockingDrainRefreshesDisplayFromLiveNetworkAfterAStaleSnapshot(GameTestHelper helper) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        TrackingFluidStorage storage = new TrackingFluidStorage(water, 1_500);
        NetworkBackedStockingHatch stocking = new NetworkBackedStockingHatch(
                info(GTAEMachines.STOCKING_IMPORT_HATCH_ME), storage);
        stocking.getMEFluidConfigSlot(0).setConfig(new GenericStack(water, 1));
        stocking.getMEFluidConfigSlot(0).setStock(new GenericStack(water, 500));

        FluidStack drained = stocking.getMEFluidConfigSlot(0).drain(1_000, IFluidHandler.FluidAction.EXECUTE);

        GenericStack displayedStock = stocking.getMEFluidConfigSlot(0).getStock();
        GenericStack snapshotStock = stocking.getFluidConfigSnapshot().slots().getFirst().stock();
        helper.assertTrue(drained.getFluid() == Fluids.WATER && drained.getAmount() == 1_000,
                "stocking hatch did not drain the requested live network amount");
        helper.assertTrue(storage.amount == 500 && storage.modulateExtractions == 1 && storage.simulations == 1,
                "stocking drain did not extract once and then query the live remainder once");
        helper.assertTrue(displayedStock != null && displayedStock.amount() == 500 &&
                snapshotStock != null && snapshotStock.amount() == 500,
                "stale display stock produced a negative or inaccurate post-drain snapshot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void sharedFluidHatchActionFillsSingleAndShiftContainersAndRejectsInvalidTargets(
                                                                                                   GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        player.getInventory().clearContent();
        MEInputHatchPartMachine input = createInput();
        MEStockingHatchPartMachine stocking = createStocking();

        input.getMEFluidConfigSlot(0).setStock(new GenericStack(AEFluidKey.of(Fluids.WATER),
                3L * FluidType.BUCKET_VOLUME));
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(dispatch(player, input,
                FluidHatchPartMachineActions.createClickFluidSlotAction(0, false)),
                "ordinary ME fluid input rejected the shared single-container action");
        GenericStack remainingStock = input.getMEFluidConfigSlot(0).getStock();
        if (remainingStock == null) {
            throw new IllegalStateException("single-container stock action drained all three buckets");
        }
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET) &&
                remainingStock.amount() == 2L * FluidType.BUCKET_VOLUME,
                "single-container stock action did not fill exactly one bucket");

        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 2));
        helper.assertTrue(dispatch(player, input,
                FluidHatchPartMachineActions.createClickFluidSlotAction(0, true)),
                "ordinary ME fluid input rejected the shared Shift action");
        int storedWaterBuckets = player.getInventory().items.stream()
                .filter(stack -> stack.is(Items.WATER_BUCKET))
                .mapToInt(ItemStack::getCount)
                .sum();
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET) &&
                player.containerMenu.getCarried().getCount() == 1 && storedWaterBuckets == 1 &&
                input.getMEFluidConfigSlot(0).getStock() == null,
                "Shift stock action did not fill the complete carried stack");

        stocking.getMEFluidConfigSlot(0).setStock(new GenericStack(AEFluidKey.of(Fluids.WATER),
                FluidType.BUCKET_VOLUME));
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        SyncActionData sharedClick = FluidHatchPartMachineActions.createClickFluidSlotAction(0, false);
        helper.assertTrue(!dispatch(player, stocking, sharedClick),
                "stocking ME fluid hatch accepted the shared manual stock action");
        helper.assertTrue(!dispatch(player, new Object(), sharedClick),
                "shared fluid hatch action accepted an unrelated holder");
        input.getMEFluidConfigSlot(0).setStock(new GenericStack(AEFluidKey.of(Fluids.WATER),
                FluidType.BUCKET_VOLUME));
        player.containerMenu.setCarried(new ItemStack(Items.STONE));
        helper.assertTrue(dispatch(player, input, sharedClick),
                "shared fluid hatch action rejected a valid target after the cursor changed");
        GenericStack unchangedStock = input.getMEFluidConfigSlot(0).getStock();
        helper.assertTrue(player.containerMenu.getCarried().is(Items.STONE) && unchangedStock != null &&
                unchangedStock.amount() == FluidType.BUCKET_VOLUME,
                "non-fluid cursor changed stock or cursor state");
        input.getMEFluidConfigSlot(0).setStock(null);
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(dispatch(player, input, sharedClick) && player.containerMenu.getCarried().is(Items.BUCKET),
                "empty stock slot did not remain a no-op");
        helper.assertTrue(!dispatch(player, input,
                FluidHatchPartMachineActions.createClickFluidSlotAction(16, false)),
                "shared fluid hatch action accepted slot 16");
        input.getMEFluidConfigSlot(0).setStock(new GenericStack(AEFluidKey.of(Fluids.WATER),
                FluidType.BUCKET_VOLUME));
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        player.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(!dispatch(player, input, sharedClick),
                "shared fluid hatch action accepted a spectator");
        player.setGameMode(GameType.SURVIVAL);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void stockingConfigRejectsLocalAndExternalPartDuplicatesBeforeMutation(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEStockingHatchPartMachine stocking = createStocking();
        stocking.getMEFluidConfigSlot(0).setConfig(new GenericStack(AEFluidKey.of(Fluids.WATER), 1));
        helper.assertTrue(!dispatch(player, stocking, MEFluidConfigActions.createSetConfigAction(
                1, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME))),
                "stocking hatch accepted a duplicate fluid from another local slot");
        helper.assertTrue(stocking.getMEFluidConfigSlot(1).getConfig() == null,
                "rejected local duplicate partially changed its target slot");

        ExternalDuplicateStockingHatch externalDuplicate = new ExternalDuplicateStockingHatch(
                info(GTAEMachines.STOCKING_IMPORT_HATCH_ME));
        externalDuplicate.rejectExternal = true;
        helper.assertTrue(!dispatch(player, externalDuplicate, MEFluidConfigActions.createSetConfigAction(
                2, new FluidStack(Fluids.LAVA, FluidType.BUCKET_VOLUME))),
                "stocking hatch accepted a duplicate reported by another part");
        helper.assertTrue(externalDuplicate.externalChecks == 1 &&
                externalDuplicate.getMEFluidConfigSlot(2).getConfig() == null,
                "external duplicate gate was skipped or partially changed its target slot");
        helper.succeed();
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MEInputHatchPartMachine machine,
                                                           MachineUIHolder holder) {
        UIElement root = machine.createLDLib2UI(player, holder).getRootElement();
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("ME fluid input did not create an LDLib2 Fancy shell");
        }
        return shell;
    }

    private static UIElement pageRoot(LDLib2FancyMachineUIElement shell) {
        return shell.getChildren().getFirst().getChildren().getFirst();
    }

    private static List<UIElement> descendants(UIElement root) {
        List<UIElement> descendants = new ArrayList<>();
        collectDescendants(root, descendants);
        return descendants;
    }

    private static AEFluidConfigAmountEditorElement amountEditor(UIElement root) {
        return descendants(root).stream()
                .filter(element -> element instanceof AEFluidConfigAmountEditorElement)
                .map(element -> (AEFluidConfigAmountEditorElement) element)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ME fluid page omitted its amount editor"));
    }

    private static String amountText(AEFluidConfigAmountEditorElement editor) {
        UIElement textElement = editor.getAmountInput().getChildren().get(1);
        if (!(textElement instanceof GTTextFieldElement textField)) {
            throw new IllegalStateException("ME fluid amount input omitted its text field");
        }
        return textField.getText();
    }

    private static void collectDescendants(UIElement root, List<UIElement> descendants) {
        for (UIElement child : root.getChildren()) {
            descendants.add(child);
            collectDescendants(child, descendants);
        }
    }

    private static void assertOneAction(GameTestHelper helper, List<CapturedAction> actions,
                                        MachineUIHolder holder, ResourceLocation actionId, String description) {
        helper.assertTrue(actions.size() == 1, description + " did not send exactly one action");
        helper.assertTrue(actions.getFirst().holder() == holder &&
                actions.getFirst().action().actionId().equals(actionId),
                description + " did not use the opened holder and expected action id");
    }

    private static FluidStack actionFluid(List<CapturedAction> actions) {
        return actions.getFirst().action().payload()
                .getOrDefault(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY)
                .copy();
    }

    private static int actionInteger(List<CapturedAction> actions, String field) {
        return actionInteger(actions, 0, field);
    }

    private static int actionInteger(List<CapturedAction> actions, int actionIndex, String field) {
        SyncFieldData fields = actions.get(actionIndex).action().payload()
                .get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null || !(fields.get(SyncFieldData.key(field)) instanceof JsonPrimitive primitive) ||
                !primitive.isNumber()) {
            throw new IllegalStateException("captured action omitted integer field " + field);
        }
        return primitive.getAsInt();
    }

    private static boolean actionBoolean(List<CapturedAction> actions, String field) {
        SyncFieldData fields = actions.getFirst().action().payload().get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null || !(fields.get(SyncFieldData.key(field)) instanceof JsonPrimitive primitive) ||
                !primitive.isBoolean()) {
            throw new IllegalStateException("captured action omitted boolean field " + field);
        }
        return primitive.getAsBoolean();
    }

    private static void click(UIElement target, int button, boolean modified) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = target;
        event.button = button;
        event.customData = modified;
        UIEventDispatcher.dispatchEvent(event, true, true, false);
    }

    private static void wheel(UIElement target, float deltaY, boolean modified) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_WHEEL);
        event.target = target;
        event.deltaY = deltaY;
        event.customData = modified;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static void commandClick(UIElement target) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = target;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        return SyncActionDispatchers.server().dispatch(new SyncActionContext(
                player, holder, action, BlockPos.ZERO, null, null, null));
    }

    private static ServerPlayer testPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static MEInputHatchPartMachine createInput() {
        MetaMachine machine = GTAEMachines.FLUID_IMPORT_HATCH_ME.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.FLUID_IMPORT_HATCH_ME.defaultBlockState());
        if (!(machine instanceof MEInputHatchPartMachine input)) {
            throw new IllegalStateException("ME fluid input definition created the wrong machine type");
        }
        return input;
    }

    private static MEStockingHatchPartMachine createStocking() {
        MetaMachine machine = GTAEMachines.STOCKING_IMPORT_HATCH_ME.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.STOCKING_IMPORT_HATCH_ME.defaultBlockState());
        if (!(machine instanceof MEStockingHatchPartMachine stocking)) {
            throw new IllegalStateException("ME stocking fluid input definition created the wrong machine type");
        }
        return stocking;
    }

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
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

    private record CapturedAction(MachineUIHolder holder, SyncActionData action) {}

    private static final class ExternalDuplicateStockingHatch extends MEStockingHatchPartMachine {

        private boolean rejectExternal;
        private int externalChecks;

        private ExternalDuplicateStockingHatch(BlockEntityCreationInfo info) {
            super(info);
        }

        @Override
        protected boolean isConfiguredInOtherStockingPart(@NotNull GenericStack stack) {
            externalChecks++;
            return rejectExternal;
        }
    }

    private static final class NetworkBackedStockingHatch extends MEStockingHatchPartMachine {

        private final MEStorage storage;

        private NetworkBackedStockingHatch(BlockEntityCreationInfo info, MEStorage storage) {
            super(info);
            this.storage = storage;
        }

        @Override
        public boolean isOnline() {
            return true;
        }

        @Override
        @NotNull
        MEStorage getStockingFluidNetworkStorage() {
            return storage;
        }
    }

    private static final class TrackingFluidStorage implements MEStorage {

        private final AEFluidKey fluid;
        private long amount;
        private int modulateExtractions;
        private int simulations;

        private TrackingFluidStorage(AEFluidKey fluid, long amount) {
            this.fluid = fluid;
            this.amount = amount;
        }

        @Override
        public long extract(AEKey what, long requestedAmount, Actionable mode, IActionSource source) {
            if (!fluid.equals(what)) {
                return 0;
            }
            long extracted = Math.min(amount, requestedAmount);
            if (mode == Actionable.MODULATE) {
                amount -= extracted;
                modulateExtractions++;
            } else {
                simulations++;
            }
            return extracted;
        }

        @Override
        public Component getDescription() {
            return Component.literal("tracking fluid storage");
        }
    }
}
