package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.integration.ae2.gui.element.AEItemConfigAmountEditorElement;
import com.gregtechceu.gtceu.integration.ae2.gui.element.AEItemConfigElement;
import com.gregtechceu.gtceu.integration.ae2.gui.element.AEItemConfigSlotElement;
import com.gregtechceu.gtceu.integration.ae2.gui.fancy.LDLib2MEItemAutoPullFancyConfigurator;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MEItemInputBusLDLib2UITest {

    private static final String BATCH = "MEItemInputBusLDLib2UI";
    private static final ResourceLocation SET_CONFIG_ACTION = MEItemConfigActions
            .createSetConfigAction(0, ItemStack.EMPTY).actionId();
    private static final ResourceLocation SET_AMOUNT_ACTION = MEItemConfigActions
            .createSetAmountAction(0, new ItemStack(Items.STONE), 1).actionId();
    private static final ResourceLocation PICKUP_STOCK_ACTION = MEItemConfigActions
            .createPickupStockAction(0, new ItemStack(Items.STONE)).actionId();

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void openingLayoutComponentsAndFancySemanticsArePreserved(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputBusPartMachine input = createInput();
        MEInputBusPartMachine replacementInput = createInput();
        MEStockingBusPartMachine stocking = createStocking();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(input);

        helper.assertTrue(input.canCreateLDLib2UI(player, holder),
                "ordinary ME item input rejected its matching holder");
        LDLib2FancyUIProvider first = input.createLDLib2Page(player, holder);
        LDLib2FancyUIProvider second = input.createLDLib2FancyPage(player, holder);
        helper.assertTrue(first != second, "ME item input reused a page provider across openings");
        LDLib2FancyMachineUIElement firstShell = new LDLib2FancyMachineUIElement(first, player.getInventory(),
                holder, first.getLDLib2PageWidth(), first.getLDLib2PageHeight());

        boolean mismatchRejected = false;
        try {
            input.createLDLib2Page(player, new MutableMachineUIHolder(stocking));
        } catch (IllegalArgumentException expected) {
            mismatchRejected = true;
        }
        helper.assertTrue(mismatchRejected, "ME item input accepted another machine's holder");

        holder.machine = replacementInput;
        boolean staleRejected = false;
        try {
            first.createLDLib2MainPage(firstShell);
        } catch (IllegalStateException expected) {
            staleRejected = true;
        }
        helper.assertTrue(staleRejected, "opened ME item page accepted a stale holder");

        MEInputBusPartMachine wrongDefinition = new MEInputBusPartMachine(info(GTAEMachines.FLUID_IMPORT_HATCH_ME));
        helper.assertFalse(wrongDefinition.canCreateLDLib2UI(player, new MutableMachineUIHolder(wrongDefinition)),
                "ME item page accepted a non-item machine definition");

        ItemStack namedDiamond = new ItemStack(Items.DIAMOND, 37);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Configured Diamond"));
        AEItemKey diamondKey = AEItemKey.of(namedDiamond);
        input.getMEItemConfigSlot(5).setConfig(new GenericStack(diamondKey, namedDiamond.getCount()));
        input.getMEItemConfigSlot(5).setStock(new GenericStack(diamondKey,
                (long) Integer.MAX_VALUE + 4_096L));
        input.setOnline(true);

        MutableMachineUIHolder inputHolder = new MutableMachineUIHolder(input);
        MutableMachineUIHolder stockingHolder = new MutableMachineUIHolder(stocking);
        LDLib2FancyUIProvider inputPage = input.createLDLib2Page(player, inputHolder);
        LDLib2FancyUIProvider stockingPage = stocking.createLDLib2Page(player, stockingHolder);
        LDLib2FancyMachineUIElement inputShell = createShell(player, input, inputHolder);
        LDLib2FancyMachineUIElement stockingShell = createShell(player, stocking, stockingHolder);
        AEItemConfigElement configRoot = pageRoot(inputShell);
        AEItemConfigElement stockingConfigRoot = pageRoot(stockingShell);
        configRoot.screenTick();
        stockingConfigRoot.screenTick();

        helper.assertTrue(inputPage.getLDLib2PageWidth() == 150 && inputPage.getLDLib2PageHeight() == 88 &&
                stockingPage.getLDLib2PageWidth() == 150 && stockingPage.getLDLib2PageHeight() == 88,
                "ordinary ME item page did not preserve its 150x88 body");
        helper.assertTrue(inputPage.getPageGroupingData() != null &&
                inputPage.getPageGroupingData().groupPositionWeight() == 1,
                "ME item page omitted import grouping weight one");
        helper.assertTrue(configRoot.getSlots().size() == AEItemConfigSnapshot.SLOT_COUNT,
                "ordinary ME item page did not expose sixteen config/stock columns");
        for (int index = 0; index < configRoot.getSlots().size(); index++) {
            AEItemConfigSlotElement slot = configRoot.getSlots().get(index);
            UITemplate.LDLib2Bounds bounds = UITemplate.getLDLib2Bounds(slot);
            helper.assertTrue(slot.getIndex() == index && bounds.width() == 18 && bounds.height() == 36,
                    "ME item slot " + index + " did not preserve its index or 18x36 bounds");
            helper.assertTrue(bounds.x() == 3 + index % 8 * 18 && bounds.y() == 10 + index / 8 * 38,
                    "ME item slot " + index + " did not preserve its fixed grid coordinates");
        }

        ItemStack displayedConfig = configRoot.getSlots().get(5).getConfigElement().getValue();
        ItemStack displayedStock = configRoot.getSlots().get(5).getStockElement().getValue();
        helper.assertTrue(ItemStack.isSameItemSameComponents(displayedConfig, namedDiamond) &&
                displayedConfig.getCount() == 1 &&
                input.getItemConfigSnapshot().slots().get(5).config().amount() == 37,
                "ME item config display lost components or the snapshot target amount");
        helper.assertTrue(ItemStack.isSameItemSameComponents(displayedStock, namedDiamond) &&
                displayedStock.getCount() == 1 &&
                input.getItemConfigSnapshot().slots().get(5).stock().amount() ==
                        (long) Integer.MAX_VALUE + 4_096L,
                "ME item stock display lost components or the snapshot truncated its long amount");
        GTLabelElement configAmount = label(configRoot, "me_item_config_amount_5");
        GTLabelElement stockAmount = label(configRoot, "me_item_stock_amount_5");
        helper.assertTrue(configAmount.isVisible() &&
                configAmount.getValue().getString().equals(FormattingUtil.formatNumberReadable(37, false)),
                "ordinary ME item config omitted its compact target amount label");
        helper.assertTrue(stockAmount.getValue().getString().equals(FormattingUtil.formatNumberReadable(
                (long) Integer.MAX_VALUE + 4_096L, false)),
                "ME item stock label did not compact its long amount");
        helper.assertTrue(!label(stockingConfigRoot, "me_item_config_amount_0").isVisible(),
                "stocking ME item config exposed an amount label");
        helper.assertTrue(configRoot.getChildren().stream()
                .anyMatch(element -> "me_network_status".equals(element.getId())),
                "ME item page omitted its synchronized online status label");
        helper.assertTrue(input.getItemConfigSnapshot().online(),
                "online status label did not have an online snapshot source");
        helper.assertTrue(inputShell.getConfiguratorPanel().getChildren().size() == 3,
                "ordinary ME item page did not expose working, distinct, and circuit configurators");
        helper.assertTrue(stockingShell.getConfiguratorPanel().getChildren().size() == 5,
                "stocking ME item page omitted distinct, auto-pull, or advanced stocking configurators");
        helper.assertTrue(inputShell.getSideTabsElement().getChildren().size() == 2,
                "ordinary ME item page omitted its directional side page");
        helper.assertTrue(stockingShell.getSideTabsElement().getChildren().size() == 1,
                "stocking ME item page exposed a directional or cover side page");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void itemConfigInteractionsSendExactlyOneOpenedHolderAction(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputBusPartMachine input = createInput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(input);
        List<CapturedAction> actions = new ArrayList<>();
        UIElement root = input.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> Boolean.TRUE.equals(event.customData));
        AEItemConfigElement configRoot = (AEItemConfigElement) root.getChildren().getFirst();
        AEItemConfigSlotElement slot = configRoot.getSlots().getFirst();

        ItemStack namedDiamond = new ItemStack(Items.DIAMOND, 7);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Action Diamond"));
        player.containerMenu.setCarried(namedDiamond.copy());
        click(slot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertOneAction(helper, actions, holder, SET_CONFIG_ACTION, "left config click");
        helper.assertTrue(ItemStack.matches(actionItem(actions), namedDiamond),
                "left config click did not preserve the carried count and components");

        actions.clear();
        click(slot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        assertOneAction(helper, actions, holder, SET_CONFIG_ACTION, "right config clear");
        helper.assertTrue(actionItem(actions).isEmpty(),
                "right config click did not send an empty clear payload");

        for (int index = 0; index < 4; index++) {
            input.getMEItemConfigSlot(index).setConfig(new GenericStack(AEItemKey.of(Items.STONE), 1_000));
        }
        player.containerMenu.setCarried(ItemStack.EMPTY);
        actions.clear();
        click(slot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        configRoot.screenTick();
        helper.assertTrue(actions.isEmpty() && configRoot.getSelectedSlot() == 0,
                "empty carried stack sent a config action or failed to select the amount editor");

        wheel(slot.getConfigElement(), 1, false);
        assertOneAction(helper, actions, holder, SET_AMOUNT_ACTION, "ordinary config wheel");
        helper.assertTrue(actionInteger(actions, "amount") == 1_001,
                "ordinary config wheel did not increment by one");

        actions.clear();
        wheel(configRoot.getSlots().get(1).getConfigElement(), 1, true);
        assertOneAction(helper, actions, holder, SET_AMOUNT_ACTION, "Ctrl config wheel");
        helper.assertTrue(actionInteger(actions, "amount") == 2_000,
                "Ctrl config wheel did not double the amount");

        actions.clear();
        wheel(configRoot.getSlots().get(2).getConfigElement(), -1, false);
        assertOneAction(helper, actions, holder, SET_AMOUNT_ACTION, "reverse ordinary config wheel");
        helper.assertTrue(actionInteger(actions, "amount") == 999,
                "reverse ordinary config wheel did not decrement by one");

        actions.clear();
        wheel(configRoot.getSlots().get(3).getConfigElement(), -1, true);
        assertOneAction(helper, actions, holder, SET_AMOUNT_ACTION, "reverse Ctrl config wheel");
        helper.assertTrue(actionInteger(actions, "amount") == 500,
                "reverse Ctrl config wheel did not halve the amount");

        actions.clear();
        ItemStack ghost = new ItemStack(Items.GOLD_INGOT, 3);
        ghost.set(DataComponents.CUSTOM_NAME, Component.literal("Ghost Gold"));
        slot.getConfigElement().setItem(ghost);
        assertOneAction(helper, actions, holder, SET_CONFIG_ACTION, "XEI ghost item drop");
        helper.assertTrue(ItemStack.matches(actionItem(actions), ghost),
                "XEI ghost item drop lost count or components");

        input.getMEItemConfigSlot(0).setStock(new GenericStack(AEItemKey.of(namedDiamond), 64));
        player.containerMenu.setCarried(ItemStack.EMPTY);
        actions.clear();
        click(slot.getStockElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertOneAction(helper, actions, holder, PICKUP_STOCK_ACTION, "ordinary stock click");
        helper.assertTrue(ItemStack.isSameItemSameComponents(actionItem(actions), namedDiamond) &&
                actionItem(actions).getCount() == 1,
                "stock pickup was not bound to the displayed item identity");

        player.containerMenu.setCarried(new ItemStack(Items.STONE));
        actions.clear();
        click(slot.getStockElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        helper.assertTrue(actions.isEmpty(), "non-empty cursor sent a stock pickup action");

        MEStockingBusPartMachine stocking = createStocking();
        MutableMachineUIHolder stockingHolder = new MutableMachineUIHolder(stocking);
        List<CapturedAction> stockingActions = new ArrayList<>();
        UIElement stockingRoot = stocking.createLDLib2MainElement(player, stockingHolder,
                (sentHolder, action) -> stockingActions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> Boolean.TRUE.equals(event.customData));
        AEItemConfigSlotElement stockingSlot = ((AEItemConfigElement) stockingRoot.getChildren().getFirst())
                .getSlots().getFirst();
        stocking.getMEItemConfigSlot(0).setConfig(new GenericStack(AEItemKey.of(Items.STONE), 1));
        stocking.getMEItemConfigSlot(0).setStock(new GenericStack(AEItemKey.of(Items.STONE), 1_000));
        player.containerMenu.setCarried(ItemStack.EMPTY);
        wheel(stockingSlot.getConfigElement(), 1, false);
        click(stockingSlot.getStockElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        helper.assertTrue(stockingActions.isEmpty(),
                "stocking amount wheel or stock click sent a forbidden action");

        stocking.setAutoPull(true);
        player.containerMenu.setCarried(new ItemStack(Items.DIAMOND));
        click(stockingSlot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        stockingSlot.getConfigElement().setItem(new ItemStack(Items.GOLD_INGOT));
        stockingSlot.screenTick();
        helper.assertTrue(stockingActions.isEmpty(),
                "auto-pull config click or ghost drop sent a forbidden action");
        helper.assertTrue(stockingSlot.getConfigElement().getValue().is(Items.STONE),
                "auto-pull ghost drop escaped the authoritative snapshot refresh");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void pendingAmountsUseLatestRequestAndExactItemComponents(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputBusPartMachine input = createInput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(input);
        List<CapturedAction> actions = new ArrayList<>();
        ItemStack firstIdentity = new ItemStack(Items.STONE);
        firstIdentity.set(DataComponents.CUSTOM_NAME, Component.literal("First Identity"));
        input.getMEItemConfigSlot(0).setConfig(new GenericStack(AEItemKey.of(firstIdentity), 1_000));

        UIElement root = input.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> Boolean.TRUE.equals(event.customData));
        AEItemConfigElement configRoot = (AEItemConfigElement) root.getChildren().getFirst();
        AEItemConfigSlotElement slot = configRoot.getSlots().getFirst();
        AEItemConfigAmountEditorElement editor = amountEditor(configRoot);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        click(slot.getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        configRoot.screenTick();

        wheel(slot.getConfigElement(), 1, false);
        wheel(slot.getConfigElement(), 1, false);
        configRoot.screenTick();
        helper.assertTrue(actions.size() == 2 && actionInteger(actions, 0, "amount") == 1_001 &&
                actionInteger(actions, 1, "amount") == 1_002 && "1002".equals(amountText(editor)),
                "consecutive amount wheels did not project 1001 then 1002");

        input.getMEItemConfigSlot(0).setConfig(new GenericStack(AEItemKey.of(firstIdentity), 1_001));
        configRoot.screenTick();
        helper.assertTrue("1002".equals(amountText(editor)),
                "intermediate authoritative snapshot replaced the latest pending amount");
        input.getMEItemConfigSlot(0).setConfig(new GenericStack(AEItemKey.of(firstIdentity), 1_002));
        configRoot.screenTick();
        input.getMEItemConfigSlot(0).setConfig(new GenericStack(AEItemKey.of(firstIdentity), 1_003));
        configRoot.screenTick();
        helper.assertTrue("1003".equals(amountText(editor)),
                "confirmed pending amount did not return control to authoritative snapshots");

        actions.clear();
        commandClick(editor.getAmountInput().getChildren().getLast());
        commandClick(editor.getAmountInput().getChildren().getLast());
        helper.assertTrue(actions.size() == 2 && actionInteger(actions, 0, "amount") == 1_004 &&
                actionInteger(actions, 1, "amount") == 1_005 && "1005".equals(amountText(editor)),
                "amount editor increments did not share the latest pending projection");

        ItemStack replacementIdentity = new ItemStack(Items.STONE);
        replacementIdentity.set(DataComponents.CUSTOM_NAME, Component.literal("Replacement Identity"));
        input.getMEItemConfigSlot(0).setConfig(new GenericStack(AEItemKey.of(replacementIdentity), 2_000));
        configRoot.screenTick();
        helper.assertTrue("2000".equals(amountText(editor)),
                "replacement item components retained another identity's pending amount");
        input.getMEItemConfigSlot(0).setConfig(new GenericStack(AEItemKey.of(firstIdentity), 1_003));
        configRoot.screenTick();
        helper.assertTrue("1003".equals(amountText(editor)),
                "restored item identity resurrected an invalidated pending amount");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void amountEditorClosesOutsideWithoutMutationOrAction(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputBusPartMachine input = createInput();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(input);
        List<CapturedAction> actions = new ArrayList<>();
        input.getMEItemConfigSlot(0).setConfig(new GenericStack(AEItemKey.of(Items.STONE), 1_000));

        UIElement root = input.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true, event -> false);
        AEItemConfigElement configRoot = (AEItemConfigElement) root.getChildren().getFirst();
        AEItemConfigAmountEditorElement editor = amountEditor(configRoot);
        player.containerMenu.setCarried(ItemStack.EMPTY);

        click(configRoot.getSlots().getFirst().getConfigElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        configRoot.screenTick();
        helper.assertTrue(configRoot.getSelectedSlot() == 0 && editor.isVisible(),
                "left-clicking a configured slot did not open its amount editor");

        click(editor.getAmountInput().getChildren().get(1), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        configRoot.screenTick();
        helper.assertTrue(configRoot.getSelectedSlot() == 0 && editor.isVisible(),
                "clicking inside the amount editor closed it");

        click(configRoot.getSlots().get(1).getStockElement(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        configRoot.screenTick();
        GenericStack config = input.getMEItemConfigSlot(0).getConfig();
        helper.assertTrue(configRoot.getSelectedSlot() == -1 && !editor.isVisible(),
                "clicking outside the amount editor did not close it");
        helper.assertTrue(actions.isEmpty() && config != null && config.amount() == 1_000 &&
                config.what().equals(AEItemKey.of(Items.STONE)),
                "closing the amount editor changed configuration or sent an action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void autoPullFancyToggleIsHolderScopedAndSendsExactlyOneAction(GameTestHelper helper) {
        MEStockingBusPartMachine stocking = createStocking();
        MEStockingBusPartMachine replacement = createStocking();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(stocking);
        List<CapturedAction> actions = new ArrayList<>();
        LDLib2MEItemAutoPullFancyConfigurator toggle = new LDLib2MEItemAutoPullFancyConfigurator(
                stocking, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true);

        UIEvent click = UIEvent.create(UIEvents.MOUSE_DOWN);
        toggle.onClick(click);
        assertOneAction(helper, actions, holder,
                MEItemConfigActions.createSetAutoPullAction(true).actionId(), "auto-pull toggle");
        helper.assertTrue(actionBoolean(actions, "autoPull"),
                "auto-pull toggle did not request the opposite state");

        holder.machine = replacement;
        toggle.onClick(UIEvent.create(UIEvents.MOUSE_DOWN));
        helper.assertTrue(actions.size() == 1, "stale auto-pull holder sent another action");

        boolean mismatchRejected = false;
        try {
            new LDLib2MEItemAutoPullFancyConfigurator(stocking, holder,
                    (sentHolder, action) -> {}, () -> true);
        } catch (IllegalArgumentException expected) {
            mismatchRejected = true;
        }
        helper.assertTrue(mismatchRejected, "auto-pull toggle accepted a mismatched opening holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void emptyConfigSlotsExposeOrdinaryStockingAndAutoPullTooltips(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        MEInputBusPartMachine input = createInput();
        MEStockingBusPartMachine stocking = createStocking();

        AEItemConfigSlotElement inputSlot = createConfigRoot(player, input).getSlots().getFirst();
        AEItemConfigSlotElement stockingSlot = createConfigRoot(player, stocking).getSlots().getFirst();
        assertTooltips(helper, inputSlot.getConfigElement(),
                "gtpm.gui.config_slot", "gtpm.gui.config_slot.set",
                "gtpm.gui.config_slot.scroll", "gtpm.gui.config_slot.remove");
        assertTooltips(helper, stockingSlot.getConfigElement(),
                "gtpm.gui.config_slot", "gtpm.gui.config_slot.set_only", "gtpm.gui.config_slot.remove");

        stocking.setAutoPull(true);
        stockingSlot.screenTick();
        assertTooltips(helper, stockingSlot.getConfigElement(),
                "gtpm.gui.config_slot", "gtpm.gui.config_slot.auto_pull_managed");
        helper.succeed();
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MEInputBusPartMachine machine,
                                                           MachineUIHolder holder) {
        UIElement root = machine.createLDLib2UI(player, holder).getRootElement();
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("ME item input did not create an LDLib2 Fancy shell");
        }
        return shell;
    }

    private static AEItemConfigElement pageRoot(LDLib2FancyMachineUIElement shell) {
        return descendants(shell).stream()
                .filter(AEItemConfigElement.class::isInstance)
                .map(AEItemConfigElement.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ME item input shell omitted its config element"));
    }

    private static AEItemConfigElement createConfigRoot(ServerPlayer player, MEInputBusPartMachine machine) {
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        UIElement root = machine.createLDLib2MainElement(player, holder, (sentHolder, action) -> {},
                () -> true, event -> false);
        return (AEItemConfigElement) root.getChildren().getFirst();
    }

    private static AEItemConfigAmountEditorElement amountEditor(UIElement root) {
        return descendants(root).stream()
                .filter(element -> element instanceof AEItemConfigAmountEditorElement)
                .map(element -> (AEItemConfigAmountEditorElement) element)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ME item page omitted its amount editor"));
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

    private static String amountText(AEItemConfigAmountEditorElement editor) {
        UIElement textElement = editor.getAmountInput().getChildren().get(1);
        if (!(textElement instanceof GTTextFieldElement textField)) {
            throw new IllegalStateException("ME item amount input omitted its text field");
        }
        return textField.getText();
    }

    private static GTLabelElement label(UIElement root, String id) {
        return descendants(root).stream()
                .filter(element -> id.equals(element.getId()))
                .map(GTLabelElement.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ME item page omitted label " + id));
    }

    private static void assertOneAction(GameTestHelper helper, List<CapturedAction> actions,
                                        MachineUIHolder holder, ResourceLocation actionId, String description) {
        helper.assertTrue(actions.size() == 1, description + " did not send exactly one action");
        helper.assertTrue(actions.getFirst().holder() == holder &&
                actions.getFirst().action().actionId().equals(actionId),
                description + " did not use the opened holder and expected action id");
    }

    private static ItemStack actionItem(List<CapturedAction> actions) {
        return actions.getFirst().action().payload()
                .getOrDefault(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), ItemStack.EMPTY);
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
        SyncFieldData fields = actions.getFirst().action().payload()
                .get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null || !(fields.get(SyncFieldData.key(field)) instanceof JsonPrimitive primitive) ||
                !primitive.isBoolean()) {
            throw new IllegalStateException("captured action omitted boolean field " + field);
        }
        return primitive.getAsBoolean();
    }

    private static void click(UIElement target, int button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = target;
        event.button = button;
        UIEventDispatcher.dispatchEvent(event, true, true, false);
    }

    private static void wheel(UIElement target, float deltaY, boolean ctrlDown) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_WHEEL);
        event.target = target;
        event.deltaY = deltaY;
        event.customData = ctrlDown;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static void commandClick(UIElement target) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = target;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static void assertTooltips(GameTestHelper helper, UIElement target, String... translationKeys) {
        UIEvent event = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
        event.target = target;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
        if (event.hoverTooltips == null) {
            throw new IllegalStateException("empty ME item config slot omitted its hover tooltips");
        }
        List<Component> tooltips = event.hoverTooltips.tooltipTexts();
        for (String translationKey : translationKeys) {
            helper.assertTrue(tooltips.contains(Component.translatable(translationKey)),
                    "empty ME item config slot omitted tooltip " + translationKey);
        }
        helper.assertTrue(tooltips.size() == translationKeys.length,
                "empty ME item config slot exposed unexpected tooltip entries");
    }

    private static ServerPlayer testPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return player;
    }

    private static MEInputBusPartMachine createInput() {
        MetaMachine machine = GTAEMachines.ITEM_IMPORT_BUS_ME.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.ITEM_IMPORT_BUS_ME.defaultBlockState());
        if (!(machine instanceof MEInputBusPartMachine input) || machine.getClass() != MEInputBusPartMachine.class) {
            throw new IllegalStateException("ME item input definition created the wrong machine type");
        }
        return input;
    }

    private static MEStockingBusPartMachine createStocking() {
        MetaMachine machine = GTAEMachines.STOCKING_IMPORT_BUS_ME.getBlockEntityType().create(
                BlockPos.ZERO, GTAEMachines.STOCKING_IMPORT_BUS_ME.defaultBlockState());
        if (!(machine instanceof MEStockingBusPartMachine stocking)) {
            throw new IllegalStateException("ME stocking item input definition created the wrong machine type");
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
}
