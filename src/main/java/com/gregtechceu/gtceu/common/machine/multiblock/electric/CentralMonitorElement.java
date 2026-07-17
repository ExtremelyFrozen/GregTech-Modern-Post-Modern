package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotSessionElement;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemHandlerRoute;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotBinding;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotDefinition;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.item.component.IMonitorModuleItem;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2VoidingModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.CentralMonitorGroupItemHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;
import com.gregtechceu.gtceu.data.lang.LangHandler;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns one Central Monitor LDLib2 opening, including its local selection draft and acknowledged group page.
 */
final class CentralMonitorElement extends UIElement implements LDLib2FancyUIProvider {

    static final int PAGE_WIDTH = 344;
    static final int PAGE_HEIGHT = 210;

    private static final int COMPONENT_SIZE = 18;
    private static final int COMPONENT_VIEW_X = 86;
    private static final int COMPONENT_VIEW_Y = 8;
    private static final int COMPONENT_VIEW_WIDTH = PAGE_WIDTH - COMPONENT_VIEW_X - 8;
    private static final int COMPONENT_VIEW_HEIGHT = 142;
    private static final int ACTION_Y = 158;
    private static final int GROUP_SLOT_COUNT = CentralMonitorGroupItemHandler.SLOT_COUNT;

    private final CentralMonitorMachine machine;
    private final Player player;
    private final MachineUIHolder holder;
    private final Consumer<SyncActionData> actionSender;
    private final Supplier<UUID> groupIdentitySupplier;
    private final GroupPageSelectionRequester pageRequester;
    private final LDLib2DirectionalFancyConfigurator directionalPage;
    private final List<LDLib2FancyUIProvider> partPages;
    private final Set<BlockPos> selectedMonitorPositions = new LinkedHashSet<>();
    private final Map<BlockPos, GTButtonElement> componentControls = new LinkedHashMap<>();
    private final Map<UUID, GTButtonElement> groupGearControls = new LinkedHashMap<>();
    private final Map<UUID, UIElement> bindingElements = new LinkedHashMap<>();

    private final UIElement overviewElement;
    private final UIElement groupElement;
    private final GTComponentPanelElement statusPanel;
    private final GTScrollerViewElement groupList;
    private final GTScrollerViewElement componentView;
    private final UIElement bindingHost;
    private final UIElement moduleConfigurationHost;
    private final GTLabelElement groupTitle;
    private final GTButtonElement overviewRequestButton;
    private final GTButtonElement createGroupButton;
    private final GTButtonElement removeMembersButton;
    private final GTButtonElement setTargetButton;
    private final GTIntInputElement dataSlotInput;
    private final GTDynamicItemSlotSessionElement sessionElement;

    private List<GroupSnapshot> displayedGroups = List.of();
    private Set<ComponentSnapshot> displayedComponents = Set.of();
    private @Nullable BlockPos selectedTargetPosition;
    private @Nullable UUID configuredGroupIdentity;
    private @Nullable UUID selectedBindingIdentity;
    private @Nullable AuthoritativeModuleSnapshot renderedModuleSnapshot;
    private int dataSlot = 1;
    private int dataSlotCount = 1;
    private int nextActionSequence;

    CentralMonitorElement(CentralMonitorMachine machine, Player player, MachineUIHolder holder) {
        this(machine, player, holder, action -> {
            if (player.level().isClientSide()) {
                MachineUIHelper.sendAction(holder, action);
            }
        }, UUID::randomUUID, null);
    }

    CentralMonitorElement(CentralMonitorMachine machine, Player player, MachineUIHolder holder,
                          Consumer<SyncActionData> actionSender, Supplier<UUID> groupIdentitySupplier) {
        this(machine, player, holder, actionSender, groupIdentitySupplier, null);
    }

    CentralMonitorElement(CentralMonitorMachine machine, Player player, MachineUIHolder holder,
                          Consumer<SyncActionData> actionSender, Supplier<UUID> groupIdentitySupplier,
                          @Nullable GroupPageSelectionRequester pageRequester) {
        this.machine = machine;
        this.player = player;
        this.holder = holder;
        this.actionSender = actionSender;
        this.groupIdentitySupplier = groupIdentitySupplier;
        requireOpeningMachine();
        directionalPage = new LDLib2DirectionalFancyConfigurator(machine, player, holder);
        List<LDLib2FancyUIProvider> pages = new ArrayList<>();
        for (IMultiPart part : machine.getParts()) {
            if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                GTCEu.LOGGER.error("Central Monitor part {} has no LDLib2 Fancy page",
                        part.self().getDefinition().getId());
                throw new IllegalStateException("Central Monitor part has no LDLib2 Fancy page: " +
                        part.self().getDefinition().getId());
            }
            pages.add(pageProvider.createLDLib2FancyPage(player, new MachineUIHolderContext(player, part.self())));
        }
        partPages = List.copyOf(pages);

        UITemplate.setLDLib2Bounds(this, 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        style(style -> style
                .backgroundTexture(GuiTextures.BACKGROUND_INVERSE)
                .overflowVisible(true));

        overviewElement = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        groupElement = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        groupElement.setVisible(false);
        groupElement.setActive(false);

        statusPanel = new GTComponentPanelElement(
                COMPONENT_VIEW_X + 4, COMPONENT_VIEW_Y + 9, machine::addDisplayText)
                .setMaxWidthLimit(COMPONENT_VIEW_WIDTH - 8)
                .clickHandler(machine::handleDisplayClick);
        groupList = new GTScrollerViewElement(6, 28, 74, 176);
        groupList.viewPort(viewPort -> viewPort.layout(layout -> layout.paddingAll(0)));
        groupList.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.NEVER));
        componentView = new GTScrollerViewElement(COMPONENT_VIEW_X, COMPONENT_VIEW_Y, COMPONENT_VIEW_WIDTH,
                COMPONENT_VIEW_HEIGHT);
        componentView.viewPort(viewPort -> viewPort.layout(layout -> layout.paddingAll(0)));
        componentView.scrollerStyle(style -> style
                .mode(ScrollerMode.BOTH)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.AUTO));
        bindingHost = UITemplate.setLDLib2Bounds(new UIElement(), 88, 34, 108, 58);
        moduleConfigurationHost = UITemplate.setLDLib2Bounds(new UIElement(), 86, 98, 248, 106);
        groupTitle = new GTLabelElement(112, 10, 216, 12, Component.empty());
        groupTitle.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));

        createGroupButton = createCommandButton(86, 76,
                "gtpm.central_monitor.gui.create_group", this::createSelectedGroup);
        removeMembersButton = createCommandButton(86, 82,
                "gtpm.central_monitor.gui.remove_from_group", this::removeSelectedMembers);
        setTargetButton = createCommandButton(174, 78,
                "gtpm.central_monitor.gui.set_target", this::applySelectedTarget);
        dataSlotInput = new GTIntInputElement(174, 184, 78, 18, () -> dataSlot, value -> dataSlot = value)
                .setMin(1)
                .setMax(dataSlotCount);
        dataSlotInput.style(style -> style.tooltips(
                Component.translatable("gtpm.central_monitor.gui.data_slot")));

        createGroupButton.setVisible(false);
        createGroupButton.setActive(false);
        removeMembersButton.setVisible(false);
        removeMembersButton.setActive(false);
        setTargetButton.setVisible(false);
        setTargetButton.setActive(false);
        dataSlotInput.setVisible(false);
        dataSlotInput.setActive(false);

        overviewElement.addChildren(statusPanel, componentView, createGroupButton, removeMembersButton,
                setTargetButton, dataSlotInput);
        overviewRequestButton = createOverviewRequestButton();
        groupElement.addChildren(overviewRequestButton, groupTitle, bindingHost, moduleConfigurationHost);

        sessionElement = GTDynamicItemSlotSessionElement.builder()
                .sourceRevision(machine::getCentralMonitorMembershipRevision)
                .definitionSource(this::createSlotDefinitions)
                .bindingAppender(this::appendBinding)
                .bindingResolved(this::isBindingResolved)
                .bindingSelectable(this::isBindingResolved)
                .selectionListener(this::applyAcknowledgedSelection)
                .build();
        UITemplate.setLDLib2Bounds(sessionElement, 0, 0, 0, 0);
        sessionElement.setAllowHitTest(false);
        this.pageRequester = pageRequester == null ? new GroupPageSelectionRequester() {

            @Override
            public void requestTargetSelection(UUID groupIdentity) {
                sessionElement.requestTargetSelection(groupIdentity);
            }

            @Override
            public void requestOverview() {
                sessionElement.requestOverview();
            }
        } : pageRequester;
        addChildren(overviewElement, groupElement, groupList, createInfoButton());

        reconcileOpeningState(true);
    }

    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        if (shell.getHolder() != holder || shell.getOpeningPlayer() != player) {
            GTCEu.LOGGER.error("Central Monitor Fancy page was attached to a different opening at {}",
                    machine.getBlockPos());
            throw new IllegalArgumentException("Central Monitor page belongs to another Fancy opening.");
        }
        return this;
    }

    @Override
    public IGuiTexture getTabIcon() {
        return GuiTextures.itemStack(machine.getDefinition().getItem());
    }

    @Override
    public Component getTitle() {
        return Component.translatable(machine.getDefinition().getDescriptionId());
    }

    @Override
    public int getLDLib2PageWidth() {
        return PAGE_WIDTH;
    }

    @Override
    public int getLDLib2PageHeight() {
        return PAGE_HEIGHT;
    }

    @Override
    public List<Component> getTabTooltips() {
        return List.of(getTitle());
    }

    @Override
    public void attachSideTabs(LDLib2FancyTabsElement tabs) {
        tabs.attachSubTab(directionalPage);
    }

    @Override
    public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
        LDLib2VoidingModeFancyConfigurator.attachConfigurators(configuratorPanel, machine);
        configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(machine, holder));
    }

    @Override
    public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
        for (IMultiPart part : machine.getParts()) {
            if (part instanceof IMaintenanceMachine maintenanceMachine) {
                maintenanceMachine.attachLDLib2MaintenanceTooltips(tooltipsPanel);
            }
        }
    }

    @Override
    public List<LDLib2FancyUIProvider> getSubTabs() {
        return partPages;
    }

    @Override
    public void screenTick() {
        super.screenTick();
        reconcileOpeningState(false);
    }

    @Override
    public void serverTick() {
        super.serverTick();
        reconcileOpeningState(false);
    }

    GTDynamicItemSlotSessionElement sessionElement() {
        return sessionElement;
    }

    UIElement overviewElement() {
        return overviewElement;
    }

    UIElement groupElement() {
        return groupElement;
    }

    GTComponentPanelElement statusPanel() {
        return statusPanel;
    }

    Optional<UUID> configuredGroupIdentity() {
        return Optional.ofNullable(configuredGroupIdentity);
    }

    Map<BlockPos, GTButtonElement> componentControls() {
        return Map.copyOf(componentControls);
    }

    Map<UUID, GTButtonElement> groupGearControls() {
        return Map.copyOf(groupGearControls);
    }

    GTButtonElement createGroupButton() {
        return createGroupButton;
    }

    GTButtonElement removeMembersButton() {
        return removeMembersButton;
    }

    GTButtonElement setTargetButton() {
        return setTargetButton;
    }

    GTIntInputElement dataSlotInput() {
        return dataSlotInput;
    }

    GTButtonElement overviewRequestButton() {
        return overviewRequestButton;
    }

    UIElement moduleConfigurationHost() {
        return moduleConfigurationHost;
    }

    Set<BlockPos> selectedMonitorPositions() {
        return Set.copyOf(selectedMonitorPositions);
    }

    Optional<BlockPos> selectedTargetPosition() {
        return Optional.ofNullable(selectedTargetPosition);
    }

    private GTButtonElement createInfoButton() {
        GTButtonElement info = new GTButtonElement(58, 6, 18, 18, GuiTextures.INFO_ICON, event -> {});
        info.noText();
        info.style(style -> style.tooltips(
                LangHandler.getSingleOrMultiLang("gtpm.central_monitor.info_tooltip").toArray(Component[]::new)));
        return info;
    }

    private GTButtonElement createOverviewRequestButton() {
        GTButtonElement button = new GTButtonElement(86, 8, 18, 18,
                GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.IO_CONFIG_COVER_SETTINGS),
                event -> requestOverview());
        button.noText();
        button.style(style -> style.tooltips(
                Component.translatable("gtpm.central_monitor.gui.currently_editing", "")));
        return button;
    }

    private GTButtonElement createCommandButton(int x, int width, String translationKey, Runnable command) {
        GTButtonElement button = new GTButtonElement(x, ACTION_Y, width, 18, GuiTextures.VANILLA_BUTTON,
                event -> {
                    if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        command.run();
                    }
                });
        button.setText(Component.translatable(translationKey));
        button.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        return button;
    }

    private List<DynamicItemSlotDefinition> createSlotDefinitions() {
        if (!isOpeningMachineCurrent()) {
            return List.of();
        }
        return machine.getMonitorGroups().stream()
                .map(group -> new DynamicItemSlotDefinition(
                        group.getIdentity(), group.getDynamicItemSlotIncarnation(), GROUP_SLOT_COUNT))
                .toList();
    }

    private List<GTDynamicItemSlotElement> appendBinding(DynamicItemSlotBinding binding) {
        DynamicItemHandlerRoute route = new DynamicItemHandlerRoute(
                binding.targetId(), binding.targetIncarnation(), binding.slotCount(), this::resolveGroupItemHandler);
        UIElement bindingElement = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, 108, 58);
        bindingElement.setVisible(false);
        bindingElement.setActive(false);

        List<GTDynamicItemSlotElement> slots = new ArrayList<>(binding.slotCount());
        for (int slotIndex = 0; slotIndex < binding.slotCount(); slotIndex++) {
            GTDynamicItemSlotElement slot = new GTDynamicItemSlotElement(route, slotIndex);
            int x;
            int y;
            if (slotIndex < CentralMonitorGroupItemHandler.PLACEHOLDER_SLOT_COUNT) {
                x = (slotIndex % 4) * 18;
                y = (slotIndex / 4) * 18;
                int placeholderNumber = slotIndex + 1;
                slot.style(style -> style.tooltips(
                        LangHandler.getMultiLang("gtpm.gui.computer_monitor_cover.slot_tooltip", placeholderNumber)
                                .toArray(Component[]::new)));
            } else {
                x = 82;
                y = 9;
            }
            UITemplate.setLDLib2Bounds(slot, x, y, 18, 18);
            slot.setBackgroundTexture(GuiTextures.SLOT);
            bindingElement.addChild(slot);
            slots.add(slot);
        }
        UIElement previous = bindingElements.put(binding.bindingId(), bindingElement);
        if (previous != null) {
            GTCEu.LOGGER.error("Central Monitor dynamic binding {} was appended twice for target {}",
                    binding.bindingId(), binding.targetId());
            throw new IllegalStateException("Central Monitor dynamic binding was appended twice: " +
                    binding.bindingId());
        }
        bindingHost.addChild(bindingElement);
        return List.copyOf(slots);
    }

    private @Nullable CentralMonitorGroupItemHandler resolveGroupItemHandler(UUID groupIdentity,
                                                                             UUID groupIncarnation) {
        MonitorGroup group = resolveGroup(groupIdentity, groupIncarnation);
        return group == null ? null : new CentralMonitorGroupItemHandler(group);
    }

    private @Nullable MonitorGroup resolveGroup(UUID groupIdentity, UUID groupIncarnation) {
        if (!isOpeningMachineCurrent()) {
            return null;
        }
        MonitorGroup group = machine.resolveCentralMonitorGroup(groupIdentity);
        return group != null && group.getDynamicItemSlotIncarnation().equals(groupIncarnation) ? group : null;
    }

    private boolean isBindingResolved(DynamicItemSlotBinding binding) {
        return binding.slotCount() == GROUP_SLOT_COUNT &&
                resolveGroup(binding.targetId(), binding.targetIncarnation()) != null;
    }

    private void applyAcknowledgedSelection(Optional<DynamicItemSlotBinding> selectedBinding) {
        bindingElements.values().forEach(element -> setDisplayed(element, false));
        if (selectedBinding.isEmpty()) {
            configuredGroupIdentity = null;
            selectedBindingIdentity = null;
            renderedModuleSnapshot = null;
            moduleConfigurationHost.clearAllChildren();
            setDisplayed(groupElement, false);
            setDisplayed(overviewElement, true);
            return;
        }

        DynamicItemSlotBinding binding = selectedBinding.orElseThrow();
        MonitorGroup group = resolveGroup(binding.targetId(), binding.targetIncarnation());
        UIElement bindingElement = bindingElements.get(binding.bindingId());
        if (group == null || bindingElement == null) {
            configuredGroupIdentity = null;
            selectedBindingIdentity = null;
            renderedModuleSnapshot = null;
            moduleConfigurationHost.clearAllChildren();
            setDisplayed(groupElement, false);
            setDisplayed(overviewElement, true);
            return;
        }

        boolean retainedGroup = group.getIdentity().equals(configuredGroupIdentity);
        configuredGroupIdentity = group.getIdentity();
        selectedBindingIdentity = binding.bindingId();
        setDisplayed(bindingElement, true);
        setDisplayed(overviewElement, false);
        setDisplayed(groupElement, true);
        updateGroupTitle(group);
        AuthoritativeModuleSnapshot authoritativeSnapshot = AuthoritativeModuleSnapshot.capture(group);
        if (!retainedGroup || authoritativeSnapshot.differsFrom(renderedModuleSnapshot)) {
            rebuildModuleConfiguration(group, authoritativeSnapshot);
        }
    }

    private void reconcileOpeningState(boolean force) {
        if (!isOpeningMachineCurrent()) {
            return;
        }
        Map<BlockPos, IMonitorComponent> components = machine.resolveMembershipComponents();
        Set<ComponentSnapshot> componentSnapshots = components.values().stream()
                .map(component -> new ComponentSnapshot(component.getBlockPos(), component.isMonitor()))
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (force || !componentSnapshots.equals(displayedComponents)) {
            rebuildComponentControls();
            displayedComponents = Set.copyOf(componentSnapshots);
        }

        selectedMonitorPositions.removeIf(position -> {
            IMonitorComponent component = components.get(position);
            return component == null || !component.isMonitor();
        });
        if (selectedTargetPosition != null && !components.containsKey(selectedTargetPosition)) {
            selectedTargetPosition = null;
        }

        List<GroupSnapshot> groupSnapshots = machine.getMonitorGroups().stream()
                .map(GroupSnapshot::capture)
                .toList();
        if (force || !groupSnapshots.equals(displayedGroups)) {
            rebuildGroupControls();
            displayedGroups = groupSnapshots;
        }

        refreshComponentControls(components);
        refreshActionControls(components);
        refreshConfiguredGroup();
    }

    private void rebuildComponentControls() {
        componentView.clearAllScrollViewChildren();
        componentControls.clear();
        for (int row = 0; row <= machine.getDownDist() + machine.getUpDist(); row++) {
            for (int column = 0; column <= machine.getLeftDist() + machine.getRightDist(); column++) {
                IMonitorComponent component = machine.getComponent(row, column);
                if (component == null) {
                    continue;
                }
                BlockPos position = component.getBlockPos();
                GTButtonElement button = new GTButtonElement(column * COMPONENT_SIZE, row * COMPONENT_SIZE,
                        COMPONENT_SIZE, COMPONENT_SIZE, component.getComponentIcon(),
                        event -> handleComponentClick(position, event.button));
                button.noText();
                GTButtonElement previous = componentControls.put(position, button);
                if (previous != null) {
                    GTCEu.LOGGER.error("Central Monitor UI at {} resolved duplicate component position {}",
                            machine.getBlockPos(), position);
                    throw new IllegalStateException(
                            "Central Monitor UI resolved duplicate component position " + position);
                }
                componentView.addScrollViewChild(button);
            }
        }
    }

    private void rebuildGroupControls() {
        groupList.clearAllScrollViewChildren();
        groupGearControls.clear();
        int y = 0;
        for (MonitorGroup group : machine.getMonitorGroups()) {
            UUID identity = group.getIdentity();
            GTButtonElement label = new GTButtonElement(20, y, 52, 16, GuiTextures.VANILLA_BUTTON,
                    event -> {
                        if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                            toggleGroup(identity);
                        }
                    });
            label.setText(group.getName(), false);
            label.textStyle(style -> style
                    .textColor(0x404040)
                    .textShadow(false)
                    .textAlignHorizontal(Horizontal.LEFT)
                    .textAlignVertical(Vertical.CENTER));

            GTButtonElement gear = new GTButtonElement(0, y, 18, 18,
                    GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.IO_CONFIG_COVER_SETTINGS),
                    event -> {
                        if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                            if (configuredGroupIdentity == null) {
                                requestTargetSelection(identity);
                            } else {
                                requestOverview();
                            }
                        }
                    });
            gear.noText();
            gear.style(style -> style.tooltips(
                    Component.translatable("gtpm.central_monitor.gui.currently_editing", group.getName())));
            groupList.addScrollViewChild(label);
            groupList.addScrollViewChild(gear);
            groupGearControls.put(identity, gear);
            y += 20;
        }
    }

    private void refreshComponentControls(Map<BlockPos, IMonitorComponent> components) {
        componentControls.forEach((position, button) -> {
            IMonitorComponent component = components.get(position);
            if (component == null) {
                setDisplayed(button, false);
                return;
            }
            setDisplayed(button, true);
            boolean selected = selectedMonitorPositions.contains(position);
            boolean target = position.equals(selectedTargetPosition);
            if (selected || target) {
                int color = selected && target ? Color.PINK.getRGB() : selected ? Color.RED.getRGB() :
                        Color.BLUE.getRGB();
                button.setButtonTexture(GuiTextures.group(GuiTextures.colorRect(color),
                        component.getComponentIcon()));
            } else {
                button.setButtonTexture(component.getComponentIcon());
            }
            MonitorGroup group = findGroupContaining(position);
            Component groupName = group == null ? Component.translatable("gtpm.gui.central_monitor.none") :
                    Component.literal(group.getName());
            button.style(style -> style.tooltips(
                    Component.translatable("gtpm.gui.central_monitor.group", groupName)));
        });
    }

    private void refreshActionControls(Map<BlockPos, IMonitorComponent> components) {
        boolean hasSelection = !selectedMonitorPositions.isEmpty();
        boolean allGrouped = hasSelection && selectedMonitorPositions.stream().allMatch(this::isInAnyGroup);
        boolean allUngrouped = hasSelection && selectedMonitorPositions.stream().noneMatch(this::isInAnyGroup);
        setDisplayed(createGroupButton, allUngrouped);
        setDisplayed(removeMembersButton, allGrouped);
        setDisplayed(setTargetButton, allGrouped);

        IMonitorComponent target = selectedTargetPosition == null ? null : components.get(selectedTargetPosition);
        IItemHandler dataItems = target == null ? null : target.getDataItems();
        boolean showDataSlot = dataItems != null;
        if (showDataSlot) {
            dataSlotCount = Math.max(1, dataItems.getSlots());
            dataSlotInput.setMax(dataSlotCount);
            dataSlotInput.setValue(dataSlot);
        }
        setDisplayed(dataSlotInput, showDataSlot);
    }

    private void refreshConfiguredGroup() {
        if (configuredGroupIdentity == null) {
            return;
        }
        MonitorGroup group = machine.resolveCentralMonitorGroup(configuredGroupIdentity);
        if (group == null) {
            configuredGroupIdentity = null;
            selectedBindingIdentity = null;
            renderedModuleSnapshot = null;
            moduleConfigurationHost.clearAllChildren();
            bindingElements.values().forEach(element -> setDisplayed(element, false));
            setDisplayed(groupElement, false);
            setDisplayed(overviewElement, true);
            return;
        }
        updateGroupTitle(group);
        AuthoritativeModuleSnapshot authoritativeSnapshot = AuthoritativeModuleSnapshot.capture(group);
        if (authoritativeSnapshot.differsFrom(renderedModuleSnapshot)) {
            rebuildModuleConfiguration(group, authoritativeSnapshot);
        }
        bindingElements.forEach(
                (bindingIdentity, element) -> setDisplayed(element, bindingIdentity.equals(selectedBindingIdentity)));
    }

    private void updateGroupTitle(MonitorGroup group) {
        groupTitle.setText(Component.translatable("gtpm.central_monitor.gui.currently_editing", group.getName()));
    }

    private void rebuildModuleConfiguration(MonitorGroup group,
                                            AuthoritativeModuleSnapshot authoritativeSnapshot) {
        moduleConfigurationHost.clearAllChildren();
        ItemStack moduleStack = group.getItemStackHandler().getStackInSlot(0);
        if (moduleStack.getItem() instanceof IComponentItem componentItem) {
            for (IItemComponent component : componentItem.getComponents()) {
                if (component instanceof IMonitorModuleItem module) {
                    moduleConfigurationHost.addChild(
                            module.createConfigurationElement(moduleStack, machine, group, actionSender));
                }
            }
        }
        renderedModuleSnapshot = authoritativeSnapshot;
    }

    private void handleComponentClick(BlockPos position, int button) {
        if (!isOpeningMachineCurrent()) {
            return;
        }
        IMonitorComponent component = machine.resolveMembershipComponents().get(position);
        if (component == null) {
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && component.isMonitor()) {
            if (!selectedMonitorPositions.remove(position)) {
                selectedMonitorPositions.add(position);
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            toggleTarget(position, component);
        }
        reconcileOpeningState(false);
    }

    private void toggleTarget(BlockPos position, IMonitorComponent component) {
        if (position.equals(selectedTargetPosition)) {
            selectedTargetPosition = null;
            return;
        }
        selectedTargetPosition = position;
        IItemHandler dataItems = component.getDataItems();
        if (dataItems == null) {
            return;
        }
        MonitorGroup selectedGroup = firstSelectedGroup();
        if (selectedGroup != null) {
            dataSlot = selectedGroup.getDataSlot() + 1;
        }
        dataSlotCount = Math.max(1, dataItems.getSlots());
        dataSlot = Math.max(1, Math.min(dataSlotCount, dataSlot));
    }

    private void toggleGroup(UUID groupIdentity) {
        MonitorGroup group = machine.resolveCentralMonitorGroup(groupIdentity);
        if (group == null) {
            return;
        }
        Map<BlockPos, IMonitorComponent> components = machine.resolveMembershipComponents();
        for (BlockPos position : group.getMonitorPositions()) {
            IMonitorComponent component = components.get(position);
            if (component != null && component.isMonitor()) {
                if (!selectedMonitorPositions.remove(position)) {
                    selectedMonitorPositions.add(position);
                }
            }
        }
        BlockPos target = group.getTargetRaw();
        IMonitorComponent targetComponent = target == null ? null : components.get(target);
        if (targetComponent != null) {
            toggleTarget(target, targetComponent);
        }
        reconcileOpeningState(false);
    }

    private void createSelectedGroup() {
        if (!isOpeningMachineCurrent() || selectedMonitorPositions.isEmpty() ||
                selectedMonitorPositions.stream().anyMatch(this::isInAnyGroup)) {
            return;
        }
        Set<BlockPos> positions = Set.copyOf(selectedMonitorPositions);
        int sequence = nextActionSequence;
        SyncActionData action = CentralMonitorMembershipActions.createGroupAction(
                machine.getCentralMonitorActionIncarnation(), machine.getCentralMonitorMembershipRevision(),
                groupIdentitySupplier.get(), positions, sequence);
        actionSender.accept(action);
        nextActionSequence = Math.incrementExact(sequence);
        clearSelectionDraft();
    }

    private void removeSelectedMembers() {
        if (!isOpeningMachineCurrent() || selectedMonitorPositions.isEmpty() ||
                selectedMonitorPositions.stream().anyMatch(position -> !isInAnyGroup(position))) {
            return;
        }
        long expectedRevision = machine.getCentralMonitorMembershipRevision();
        int sequence = nextActionSequence;
        for (MonitorGroup group : List.copyOf(machine.getMonitorGroups())) {
            Set<BlockPos> positions = new LinkedHashSet<>();
            for (BlockPos position : selectedMonitorPositions) {
                if (group.contains(position)) {
                    positions.add(position);
                }
            }
            if (positions.isEmpty()) {
                continue;
            }
            SyncActionData action = CentralMonitorMembershipActions.createRemoveGroupMembersAction(
                    machine.getCentralMonitorActionIncarnation(), expectedRevision, group.getIdentity(), positions,
                    sequence);
            actionSender.accept(action);
            expectedRevision = Math.incrementExact(expectedRevision);
            sequence = Math.incrementExact(sequence);
        }
        nextActionSequence = sequence;
        clearSelectionDraft();
    }

    private void applySelectedTarget() {
        if (!isOpeningMachineCurrent()) {
            return;
        }
        MonitorGroup group = firstSelectedGroup();
        if (group == null) {
            return;
        }
        CentralMonitorGroupTargetState expected = new CentralMonitorGroupTargetState(
                group.getTargetRaw(), group.getDataSlot());
        CentralMonitorGroupTargetState requested = createRequestedTargetState();
        if (expected.equals(requested)) {
            return;
        }
        int sequence = nextActionSequence;
        SyncActionData action = CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                machine.getCentralMonitorActionIncarnation(), group.getIdentity(), expected, requested, sequence);
        actionSender.accept(action);
        nextActionSequence = Math.incrementExact(sequence);
    }

    private CentralMonitorGroupTargetState createRequestedTargetState() {
        if (selectedTargetPosition == null) {
            return new CentralMonitorGroupTargetState(null, 0);
        }
        IMonitorComponent target = machine.resolveMembershipComponents().get(selectedTargetPosition);
        if (target == null) {
            return new CentralMonitorGroupTargetState(null, 0);
        }
        int requestedSlot = target.getDataItems() == null ? 0 : dataSlot - 1;
        return new CentralMonitorGroupTargetState(selectedTargetPosition, requestedSlot);
    }

    private void clearSelectionDraft() {
        selectedMonitorPositions.clear();
        selectedTargetPosition = null;
        reconcileOpeningState(false);
    }

    private @Nullable MonitorGroup firstSelectedGroup() {
        for (MonitorGroup group : machine.getMonitorGroups()) {
            for (BlockPos position : selectedMonitorPositions) {
                if (group.contains(position)) {
                    return group;
                }
            }
        }
        return null;
    }

    private @Nullable MonitorGroup findGroupContaining(BlockPos position) {
        for (MonitorGroup group : machine.getMonitorGroups()) {
            if (group.contains(position)) {
                return group;
            }
        }
        return null;
    }

    private boolean isInAnyGroup(BlockPos position) {
        return findGroupContaining(position) != null;
    }

    private void requestTargetSelection(UUID groupIdentity) {
        pageRequester.requestTargetSelection(groupIdentity);
    }

    private void requestOverview() {
        pageRequester.requestOverview();
    }

    private boolean isOpeningMachineCurrent() {
        return holder.getMachine() == machine &&
                holder.getMachineDefinitionId().equals(machine.getDefinition().getId()) && machine.isFormed();
    }

    private void requireOpeningMachine() {
        if (!isOpeningMachineCurrent()) {
            GTCEu.LOGGER.error("Central Monitor UI rejected an invalid or unformed opening at {}",
                    machine.getBlockPos());
            throw new IllegalArgumentException("Central Monitor UI requires its formed opening machine.");
        }
    }

    private static void setDisplayed(UIElement element, boolean displayed) {
        element.setVisible(displayed);
        element.setActive(displayed);
    }

    private record ComponentSnapshot(BlockPos position, boolean monitor) {}

    private record AuthoritativeModuleSnapshot(UUID moduleSlotIncarnation, ItemStack moduleSnapshot,
                                               long textConfigurationRevision) {

        private static final long NON_TEXT_CONFIGURATION_REVISION = -1;

        private static AuthoritativeModuleSnapshot capture(MonitorGroup group) {
            ItemStack module = group.getItemStackHandler().getStackInSlot(0);
            if (CentralMonitorTextModuleActions.isTextModule(module)) {
                return new AuthoritativeModuleSnapshot(
                        group.getModuleSlotIncarnation(),
                        CentralMonitorTextModuleActions.captureExpectedModule(module),
                        group.getTextConfigurationRevision());
            }
            if (CentralMonitorImageModuleActions.isImageModule(module)) {
                return new AuthoritativeModuleSnapshot(
                        group.getModuleSlotIncarnation(),
                        CentralMonitorImageModuleActions.captureExpectedModule(module),
                        NON_TEXT_CONFIGURATION_REVISION);
            }
            return new AuthoritativeModuleSnapshot(
                    group.getModuleSlotIncarnation(), module.copy(), NON_TEXT_CONFIGURATION_REVISION);
        }

        private boolean differsFrom(@Nullable AuthoritativeModuleSnapshot other) {
            return other == null || !moduleSlotIncarnation.equals(other.moduleSlotIncarnation) ||
                    textConfigurationRevision != other.textConfigurationRevision ||
                    !ItemStack.matches(moduleSnapshot, other.moduleSnapshot);
        }
    }

    /**
     * Sends opening-local group-page requests while leaving visibility changes to the matching protocol ACK.
     */
    interface GroupPageSelectionRequester {

        /** Requests the active dynamic-slot binding for one stable group UUID. */
        void requestTargetSelection(UUID groupIdentity);

        /** Requests the slot-free overview page for this opening. */
        void requestOverview();
    }

    private record GroupSnapshot(UUID identity, UUID dynamicItemSlotIncarnation, String name,
                                 Set<BlockPos> positions, @Nullable BlockPos target, int dataSlot,
                                 UUID moduleSlotIncarnation) {

        private static GroupSnapshot capture(MonitorGroup group) {
            return new GroupSnapshot(group.getIdentity(), group.getDynamicItemSlotIncarnation(), group.getName(),
                    Set.copyOf(group.getMonitorPositions()), group.getTargetRaw(), group.getDataSlot(),
                    group.getModuleSlotIncarnation());
        }
    }
}
