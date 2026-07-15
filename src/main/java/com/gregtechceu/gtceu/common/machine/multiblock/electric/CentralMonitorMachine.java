package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.texture.ColorBorderTexture;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.TextTexture;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.item.component.IMonitorModuleItem;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.multiblock.*;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;
import com.gregtechceu.gtceu.common.machine.trait.CentralMonitorLogic;
import com.gregtechceu.gtceu.common.network.packets.SCPacketMonitorGroupDataChange;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.data.pattern.StructurePatternKey;
import com.gregtechceu.gtceu.data.pattern.StructurePatternResolver;
import com.gregtechceu.gtceu.utils.GTStringUtils;

import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CentralMonitorMachine extends WorkableElectricMultiblockMachine
                                   implements IMonitorComponent, IDataInfoProvider,
                                   CentralMonitorMembershipActionTarget, CentralMonitorGroupTargetActionHost {

    static {
        CentralMonitorMembershipActions.initialize();
        CentralMonitorGroupTargetActions.initialize();
    }

    private static final String MONITOR_GROUPS_SYNC_FIELD = "monitorGroups";
    private static final String MONITOR_GROUP_MEMBERSHIP_REVISION_SYNC_FIELD = "monitorGroupMembershipRevision";

    @SaveField
    @SyncToClient
    @Getter
    private int leftDist = 0, rightDist = 0, upDist = 0, downDist = 0;
    @SaveField
    @SyncToClient
    @Getter
    @RerenderOnChanged
    private List<MonitorGroup> monitorGroups = new ArrayList<>();
    @SaveField
    @SyncToClient
    private UUID centralMonitorActionIncarnation = UUID.randomUUID();
    @SaveField
    @SyncToClient
    private long monitorGroupMembershipRevision;
    private final Set<IMonitorComponent> selectedComponents = new HashSet<>();
    private final List<IMonitorComponent> selectedTargets = new ArrayList<>();

    private @Nullable MultiblockState patternFindingState;

    private static @Nullable TraceabilityPredicate MULTI_PREDICATE = null;

    public CentralMonitorMachine(BlockEntityCreationInfo info) {
        super(info, new CentralMonitorLogic());
    }

    public static TraceabilityPredicate getMultiPredicate() {
        if (MULTI_PREDICATE == null) {
            MULTI_PREDICATE = Predicates.abilities(PartAbility.INPUT_ENERGY)
                    .setMinGlobalLimited(1).setMaxGlobalLimited(2).setPreviewCount(1)
                    .or(Predicates.abilities(PartAbility.DATA_ACCESS).setPreviewCount(1)
                            .or(Predicates.machines(GTMachines.BATTERY_BUFFER_4).setPreviewCount(0))
                            .or(Predicates.machines(GTMachines.BATTERY_BUFFER_16).setPreviewCount(0))
                            .setMaxGlobalLimited(4))
                    .or(Predicates.machines(GTMachines.HULL))
                    .or(Predicates.machines(GTMachines.MONITOR))
                    .or(Predicates.machines(GTMachines.ADVANCED_MONITOR))
                    .or(Predicates.blocks(GTBlocks.CASING_ALUMINIUM_FROSTPROOF.get()));
        }
        return MULTI_PREDICATE;
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName)) {
            this.clearPatternFindingState();
        }
    }

    @Override
    public CentralMonitorLogic getRecipeLogic() {
        return (CentralMonitorLogic) super.getRecipeLogic();
    }

    public @Nullable EnergyContainerList getFormedEnergyContainer() {
        return this.energyContainer;
    }

    public void tick() {
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        for (MonitorGroup group : monitorGroups) {
            ItemStack stack = group.getItemStackHandler().getStackInSlot(0);
            if (stack.isEmpty() || !(stack.getItem() instanceof IComponentItem componentItem)) {
                continue;
            }

            for (IItemComponent component : componentItem.getComponents()) {
                if (!(component instanceof IMonitorModuleItem module)) {
                    continue;
                }
                module.tick(stack, this, group);
                PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(getBlockPos()),
                        new SCPacketMonitorGroupDataChange(stack, group, this));
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        monitorGroups.forEach(this::bindMonitorGroupModuleHandler);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        this.clearPatternFindingState();
    }

    protected void clearPatternFindingState() {
        if (this.patternFindingState != null)
            this.patternFindingState.clean();
        this.patternFindingState = null;
    }

    protected MultiblockState getPatternFindingState() {
        if (this.patternFindingState == null) {
            this.patternFindingState = new MultiblockState(getLevel(), getBlockPos(), DEFAULT_STRUCTURE);
            this.patternFindingState.clean();
        }
        return this.patternFindingState;
    }

    public boolean isValidMonitorBlock(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;

        MultiblockState state = getPatternFindingState();
        if (!state.update(pos, getMultiPredicate())) {
            return false;
        }
        state.io = IO.BOTH;

        return Stream.concat(state.predicate.common.stream(), state.predicate.limited.stream())
                .anyMatch(predicate -> predicate.test(state));
    }

    public void updateStructureDimensions() {
        Level level = getLevel();
        if (level == null) return;

        Direction front = getFrontFacing();
        Direction spin = getUpwardsFacing();

        Direction left = RelativeDirection.LEFT.getRelative(front, spin, false);
        Direction right = RelativeDirection.RIGHT.getRelative(front, spin, false);
        Direction up = RelativeDirection.UP.getRelative(front, spin, false);
        Direction down = RelativeDirection.DOWN.getRelative(front, spin, false);
        BlockPos.MutableBlockPos posLeft = getBlockPos().mutable().move(left);
        BlockPos.MutableBlockPos posRight = getBlockPos().mutable().move(right);
        BlockPos.MutableBlockPos posUp = getBlockPos().mutable().move(up);
        BlockPos.MutableBlockPos posDown = getBlockPos().mutable().move(down);
        this.leftDist = 0;
        this.rightDist = 0;
        this.upDist = 0;
        this.downDist = 0;

        while (isValidMonitorBlock(level, posLeft)) {
            posLeft.move(left);
            leftDist++;
        }
        while (isValidMonitorBlock(level, posRight)) {
            posRight.move(right);
            rightDist++;
        }
        while (isValidMonitorBlockRow(level, posUp, leftDist, rightDist, left, right)) {
            posUp.move(up);
            upDist++;
        }
        while (isValidMonitorBlockRow(level, posDown, leftDist, rightDist, left, right)) {
            posDown.move(down);
            downDist++;
        }
    }

    private boolean isValidMonitorBlockRow(Level level, BlockPos pos, int leftDist, int rightDist, Direction left,
                                           Direction right) {
        BlockPos.MutableBlockPos mutable = pos.mutable();
        mutable.move(left, leftDist);
        for (int i = 0; i < leftDist + rightDist; i++) {
            if (!isValidMonitorBlock(level, mutable)) return false;
            mutable.move(right);
        }
        return isValidMonitorBlock(level, mutable);
    }

    @Override
    public BlockPattern getPattern(String structureName) {
        if (!DEFAULT_STRUCTURE.equals(structureName)) {
            return super.getPattern(structureName);
        }
        updateStructureDimensions();
        if (leftDist + rightDist < 1 || upDist + downDist < 1) {
            leftDist = 3;
            rightDist = 0;
            upDist = 1;
            downDist = 1;
        }

        StringBuilder[] pattern = new StringBuilder[upDist + downDist + 1];
        for (int i = 0; i < upDist + downDist + 1; i++) {
            pattern[i] = new StringBuilder(leftDist + rightDist + 1);
            for (int j = 0; j < leftDist + rightDist + 1; j++) {
                if (i == downDist && j == rightDist)
                    pattern[i].append('~'); // controller
                else
                    pattern[i].append('B'); // any valid block
            }
        }

        String[] aisle = new String[upDist + downDist + 1];
        for (int i = 0; i < upDist + downDist + 1; i++) {
            aisle[i] = pattern[i].toString();
        }

        BlockPattern baseline = FactoryBlockPattern.start(getDefinition())
                .aisle("~")
                .build();
        return StructurePatternResolver.rebuildRuntimeStringArrayPattern(
                this.getDefinition(),
                StructurePatternKey.main(this.getDefinition().getId()),
                baseline,
                List.of(new StructurePatternResolver.Unit(Collections.singletonList(aisle), 1, 1)));
    }

    public BlockPos toRelative(BlockPos pos) {
        Direction front = getFrontFacing();
        Direction spin = getUpwardsFacing();
        boolean flipped = isFlipped();
        Direction right = RelativeDirection.RIGHT.getRelative(front, spin, flipped);
        Direction up = RelativeDirection.UP.getRelative(front, spin, flipped);

        BlockPos tmp = getBlockPos().mutable().move(right, rightDist).move(up, upDist);

        return new BlockPos(Math.abs(tmp.get(right.getAxis()) - pos.get(right.getAxis())),
                Math.abs(tmp.get(up.getAxis()) - pos.get(up.getAxis())),
                0);
    }

    @Nullable
    public IMonitorComponent getComponent(int row, int col) {
        Level level = getLevel();
        if (level == null) return null;

        Direction front = getFrontFacing();
        Direction spin = getUpwardsFacing();
        boolean flipped = isFlipped();

        Direction left = RelativeDirection.LEFT.getRelative(front, spin, flipped);
        Direction up = RelativeDirection.UP.getRelative(front, spin, flipped);

        col = leftDist + rightDist - col;
        BlockPos pos = getBlockPos().relative(left, leftDist - col).relative(up, upDist - row);

        return GTCapabilityHelper.getMonitorComponent(level, pos, null);
    }

    public boolean isMonitor(int row, int col) {
        IMonitorComponent component = this.getComponent(row, col);
        if (component == null) return false;
        return component.isMonitor();
    }

    private IGuiTexture getComponentTexture(int row, int col) {
        if (row < 0 || col < 0 || row > downDist + upDist + 1 || col > leftDist + rightDist + 1)
            return GuiTextures.BLANK_TRANSPARENT;
        IMonitorComponent component = getComponent(row, col);
        if (component == null) return GuiTextures.BLANK_TRANSPARENT;
        return component.getComponentIcon();
    }

    private boolean isInAnyGroup(IMonitorComponent component) {
        return monitorGroups.stream().anyMatch(group -> group.contains(component.getBlockPos()));
    }

    @Override
    public UUID getCentralMonitorActionIncarnation() {
        return centralMonitorActionIncarnation;
    }

    @Override
    public int getCentralMonitorMembershipCapacity() {
        int width = Math.addExact(Math.addExact(leftDist, rightDist), 1);
        int height = Math.addExact(Math.addExact(upDist, downDist), 1);
        return Math.multiplyExact(width, height);
    }

    @Override
    public long getCentralMonitorMembershipRevision() {
        return monitorGroupMembershipRevision;
    }

    @Override
    public boolean canCreateCentralMonitorGroup(long expectedRevision, UUID groupIdentity,
                                                Set<BlockPos> positions) {
        if (!isMembershipStructureAvailable() || monitorGroupMembershipRevision != expectedRevision ||
                positions.isEmpty() || positions.size() > getCentralMonitorMembershipCapacity() ||
                hasMonitorGroupIdentity(groupIdentity)) {
            return false;
        }
        Map<BlockPos, IMonitorComponent> components = resolveMembershipComponents();
        for (BlockPos position : positions) {
            IMonitorComponent component = components.get(position);
            if (component == null || !component.isMonitor() || isInAnyGroup(component)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean createCentralMonitorGroup(long expectedRevision, UUID groupIdentity, Set<BlockPos> positions) {
        if (!canCreateCentralMonitorGroup(expectedRevision, groupIdentity, positions)) {
            return false;
        }
        long nextRevision = Math.incrementExact(monitorGroupMembershipRevision);
        MonitorGroup group = MonitorGroup.createWithIdentity(groupIdentity, nextDefaultMonitorGroupName());
        positions.forEach(group::add);
        monitorGroups.add(group);
        bindMonitorGroupModuleHandler(group);
        completeMonitorGroupMembershipChange(nextRevision);
        return true;
    }

    @Override
    public boolean canRemoveCentralMonitorGroupMembers(long expectedRevision, UUID groupIdentity,
                                                       Set<BlockPos> positions) {
        if (!isMembershipStructureAvailable() || monitorGroupMembershipRevision != expectedRevision ||
                positions.isEmpty() || positions.size() > getCentralMonitorMembershipCapacity()) {
            return false;
        }
        MonitorGroup group = findUniqueMonitorGroup(groupIdentity);
        if (group == null) {
            return false;
        }
        Map<BlockPos, IMonitorComponent> components = resolveMembershipComponents();
        for (BlockPos position : positions) {
            IMonitorComponent component = components.get(position);
            if (component == null || !component.isMonitor() || !group.contains(position)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean removeCentralMonitorGroupMembers(long expectedRevision, UUID groupIdentity,
                                                    Set<BlockPos> positions) {
        if (!canRemoveCentralMonitorGroupMembers(expectedRevision, groupIdentity, positions)) {
            return false;
        }
        MonitorGroup group = findUniqueMonitorGroup(groupIdentity);
        if (group == null) {
            return false;
        }
        long nextRevision = Math.incrementExact(monitorGroupMembershipRevision);
        boolean removesEntireGroup = positions.size() == group.getMonitorPositions().size();
        if (removesEntireGroup) {
            dropMonitorGroupInventory(group);
            monitorGroups.remove(group);
        } else {
            positions.forEach(group::remove);
        }
        completeMonitorGroupMembershipChange(nextRevision);
        return true;
    }

    @Override
    public boolean canSetCentralMonitorGroupTarget(UUID groupIdentity, CentralMonitorGroupTargetState expected,
                                                   CentralMonitorGroupTargetState requested) {
        if (!isMembershipStructureAvailable() || expected.dataSlot() < 0 || requested.dataSlot() < 0 ||
                expected.equals(requested)) {
            return false;
        }
        MonitorGroup group = findUniqueMonitorGroup(groupIdentity);
        if (group == null || !readMonitorGroupTargetState(group).equals(expected)) {
            return false;
        }
        BlockPos requestedPosition = requested.targetPos();
        if (requestedPosition == null) {
            return requested.dataSlot() == 0;
        }
        IMonitorComponent component = resolveMembershipComponents().get(requestedPosition);
        if (component == null) {
            return false;
        }
        IItemHandler dataItems = component.getDataItems();
        return dataItems == null ? requested.dataSlot() == 0 : requested.dataSlot() < dataItems.getSlots();
    }

    @Override
    public boolean setCentralMonitorGroupTarget(UUID groupIdentity, CentralMonitorGroupTargetState expected,
                                                CentralMonitorGroupTargetState requested) {
        if (!canSetCentralMonitorGroupTarget(groupIdentity, expected, requested)) {
            return false;
        }
        MonitorGroup group = findUniqueMonitorGroup(groupIdentity);
        if (group == null) {
            return false;
        }
        group.setTargetAndDataSlot(requested.targetPos(), requested.dataSlot());
        getSyncDataHolder().markClientSyncFieldDirty(MONITOR_GROUPS_SYNC_FIELD);
        return true;
    }

    /**
     * Returns whether membership actions may resolve components from the currently formed structure.
     */
    protected boolean isMembershipStructureAvailable() {
        return isFormed();
    }

    /**
     * Resolves the current Central Monitor grid once for one atomic membership validation.
     */
    protected Map<BlockPos, IMonitorComponent> resolveMembershipComponents() {
        Map<BlockPos, IMonitorComponent> components = new HashMap<>();
        for (int row = 0; row <= downDist + upDist; row++) {
            for (int column = 0; column <= leftDist + rightDist; column++) {
                IMonitorComponent component = getComponent(row, column);
                if (component == null) {
                    continue;
                }
                IMonitorComponent previous = components.put(component.getBlockPos(), component);
                if (previous != null) {
                    throw new IllegalStateException("Central Monitor grid resolved the same position more than once: " +
                            component.getBlockPos());
                }
            }
        }
        return components;
    }

    /**
     * Drops both inventories exactly once before an empty group is removed.
     */
    protected void dropMonitorGroupInventory(MonitorGroup group) {
        Level level = getLevel();
        if (level == null) {
            throw new IllegalStateException("Central Monitor cannot drop group inventory without a level.");
        }
        group.getItemStackHandler().dropInventoryInWorld(level, getBlockPos());
        group.getPlaceholderSlotsHandler().dropInventoryInWorld(level, getBlockPos());
    }

    private boolean hasMonitorGroupIdentity(UUID identity) {
        return monitorGroups.stream().anyMatch(group -> group.getIdentity().equals(identity));
    }

    private @Nullable MonitorGroup findUniqueMonitorGroup(UUID identity) {
        MonitorGroup match = null;
        for (MonitorGroup group : monitorGroups) {
            if (!group.getIdentity().equals(identity)) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = group;
        }
        return match;
    }

    private CentralMonitorGroupTargetState readMonitorGroupTargetState(MonitorGroup group) {
        return new CentralMonitorGroupTargetState(group.getTargetRaw(), group.getDataSlot());
    }

    private String nextDefaultMonitorGroupName() {
        int suffix = 1;
        while (true) {
            String candidate = Component.translatable("gtpm.gui.central_monitor.group_default_name", suffix)
                    .getString();
            if (monitorGroups.stream().noneMatch(group -> group.getName().equals(candidate))) {
                return candidate;
            }
            suffix = Math.incrementExact(suffix);
        }
    }

    private void bindMonitorGroupModuleHandler(MonitorGroup group) {
        if (isRemote()) {
            return;
        }
        group.getItemStackHandler().setOnContentsChanged(() -> {
            group.rotateModuleSlotIncarnation();
            getSyncDataHolder().markClientSyncFieldDirty(MONITOR_GROUPS_SYNC_FIELD);
        });
    }

    private void completeMonitorGroupMembershipChange(long nextRevision) {
        monitorGroupMembershipRevision = nextRevision;
        getSyncDataHolder().markClientSyncFieldDirty(MONITOR_GROUPS_SYNC_FIELD);
        getSyncDataHolder().markClientSyncFieldDirty(MONITOR_GROUP_MEMBERSHIP_REVISION_SYNC_FIELD);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .addWorkingStatusLine();
        getDefinition().getAdditionalDisplay().accept(this, textList);
    }

    @Override
    public Widget createUIWidget() {
        updateStructureDimensions();
        selectedComponents.clear();
        WidgetGroup builder = (WidgetGroup) super.createUIWidget();

        WidgetGroup main = new WidgetGroup();
        DraggableScrollableWidgetGroup componentSelection = new DraggableScrollableWidgetGroup(0, 10, 200, 110);
        main.addWidget(componentSelection);
        WidgetGroup options = new WidgetGroup(-100, 20, 60, 20);
        WidgetGroup groupConfig = new WidgetGroup(10, 30, 100, 100);
        groupConfig.setVisible(false);

        ButtonWidget infoWidget = new ButtonWidget(200, 10, 20, 20, null);
        infoWidget.setButtonTexture(GuiTextures.INFO_ICON);
        infoWidget.setHoverTooltips(
                GTStringUtils.toImmutable(LangHandler.getSingleOrMultiLang("gtpm.central_monitor.info_tooltip")));
        builder.addWidget(infoWidget);
        List<@Nullable MonitorGroup> configGroup = new ArrayList<>();
        configGroup.add(null);

        Consumer<@Nullable MonitorGroup> openGroupConfig = (group) -> {
            configGroup.set(0, group);
            if (group == null) {
                main.setVisible(true);
                groupConfig.setVisible(false);
                return;
            }
            groupConfig.clearAllWidgets();
            groupConfig.addWidget(new LabelWidget(0, 5, () -> {
                String currentName = "";
                if (configGroup.get(0) != null) {
                    currentName = configGroup.get(0).getName();
                }
                return Component.translatable("gtpm.central_monitor.gui.currently_editing", currentName).getString();
            }));
            for (int i = 0; i < 8; i++) {
                SlotWidget slot = new SlotWidget(group.getPlaceholderSlotsHandler(), i, -38, 16 * i + 46);
                slot.setHoverTooltips(GTStringUtils
                        .toImmutable(LangHandler.getMultiLang("gtpm.gui.computer_monitor_cover.slot_tooltip", i + 1)));
                groupConfig.addWidget(slot);
            }
            SlotWidget slot = new SlotWidget(
                    group.getItemStackHandler(), 0,
                    0, 20);
            WidgetGroup itemUI = new WidgetGroup(40, 20, 100, 100);
            Runnable changeListener = () -> {
                if (slot.getLastItem().is(slot.getItem().getItem())) return;
                itemUI.clearAllWidgets();
                if (slot.getItem().getItem() instanceof IComponentItem item) {
                    for (IItemComponent component : item.getComponents()) {
                        if (component instanceof IMonitorModuleItem module) {
                            itemUI.addWidget(module.createUIWidget(slot.getItem(), this, group));
                        }
                    }
                }
            };
            slot.setChangeListener(changeListener);
            changeListener.run();
            groupConfig.addWidget(itemUI);
            groupConfig.addWidget(slot);
            main.setVisible(false);
            groupConfig.setVisible(true);
        };
        builder.addWidget(groupConfig);
        DraggableScrollableWidgetGroup groupList = new DraggableScrollableWidgetGroup(-100, 50, 70, 80);

        List<List<Consumer<Iterator<IMonitorComponent>>>> imageButtons = new ArrayList<>();
        Map<BlockPos, Runnable> rightClickCallbacks = new HashMap<>();
        int[] dataSlot = new int[2]; // list to be able to modify it in lambdas
        dataSlot[0] = 1; // the slot (index starts from 1)
        dataSlot[1] = 9; // amount of slots
        IntInputWidget dataSlotInput = new IntInputWidget(120, 20, 60, -20, () -> dataSlot[0],
                n -> dataSlot[0] = Mth.clamp(n, 1, dataSlot[1]));
        dataSlotInput.setVisible(false);
        builder.addWidget(dataSlotInput);

        Consumer<MonitorGroup> addGroupToList = group -> {
            ButtonWidget label = new ButtonWidget(20, groupList.widgets.size() * 15 + 5, 60, 10, null);
            TextTexture text = GuiTextures.text(group.getName());
            text.setType(TextTexture.TextType.LEFT);
            label.setButtonTexture(text);
            label.setOnPressCallback(click -> {
                group.getMonitorPositions().forEach(pos -> {
                    BlockPos rel = toRelative(pos);
                    if (imageButtons.size() - 1 < rel.getY()) return;
                    if (imageButtons.get(rel.getY()).size() - 1 < rel.getX()) return;
                    imageButtons.get(rel.getY()).get(rel.getX()).accept(null);
                });
                if (group.getTargetRaw() != null) {
                    rightClickCallbacks.getOrDefault(group.getTargetRaw(), () -> {}).run();
                }
            });
            groupList.addWidget(label);

            ButtonWidget configButton = new ButtonWidget(
                    0, label.getSelfPositionY() - 3,
                    16, 16,
                    GuiTextures.IO_CONFIG_COVER_SETTINGS,
                    click -> {
                        if (configGroup.get(0) == null) {
                            openGroupConfig.accept(group);
                        } else {
                            openGroupConfig.accept(null);
                        }
                    });
            groupList.addWidget(configButton);
        };

        monitorGroups.forEach(addGroupToList);
        builder.addWidget(groupList);
        main.addWidget(options);
        ButtonWidget removeFromGroupButton = new ButtonWidget(0, 0, 60, 20, null);
        removeFromGroupButton.setButtonTexture(GuiTextures.text("gtpm.central_monitor.gui.remove_from_group"));
        removeFromGroupButton.setVisible(false);
        ButtonWidget setTargetButton = new ButtonWidget(0, 15, 60, 20, null);
        setTargetButton.setButtonTexture(GuiTextures.text("gtpm.central_monitor.gui.set_target"));
        setTargetButton.setVisible(false);
        ButtonWidget createGroupButton = new ButtonWidget(0, 0, 60, 20, null);
        createGroupButton.setOnPressCallback(click -> {
            Set<BlockPos> requestedPositions = new LinkedHashSet<>();
            for (IMonitorComponent component : selectedComponents) {
                if (isInAnyGroup(component)) return;
                requestedPositions.add(component.getBlockPos());
            }
            UUID groupIdentity = UUID.randomUUID();
            if (!createCentralMonitorGroup(monitorGroupMembershipRevision, groupIdentity, requestedPositions)) {
                return;
            }
            MonitorGroup group = findUniqueMonitorGroup(groupIdentity);
            if (group == null) {
                throw new IllegalStateException("Central Monitor lost a group immediately after creating it.");
            }
            addGroupToList.accept(group);

            createGroupButton.setVisible(false);
            removeFromGroupButton.setVisible(true);
            Iterator<IMonitorComponent> it = selectedComponents.iterator();
            while (it.hasNext()) {
                IMonitorComponent c = it.next();
                BlockPos rel = toRelative(c.getBlockPos());
                imageButtons.get(rel.getY()).get(rel.getX()).accept(it);
            }
            if (!selectedTargets.isEmpty()) {
                rightClickCallbacks.getOrDefault(selectedTargets.get(0).getBlockPos(), () -> {}).run();
            }
        });
        setTargetButton.setOnPressCallback(click -> {
            MonitorGroup group = null;
            for (MonitorGroup group2 : monitorGroups) {
                for (IMonitorComponent component : selectedComponents) {
                    if (group2.contains(component.getBlockPos())) {
                        group = group2;
                        break;
                    }
                }
                if (group != null) break;
            }
            if (group == null) return;
            CentralMonitorGroupTargetState expected = readMonitorGroupTargetState(group);
            CentralMonitorGroupTargetState requested;
            if (selectedTargets.isEmpty()) {
                requested = new CentralMonitorGroupTargetState(null, 0);
            } else {
                IMonitorComponent target = selectedTargets.getFirst();
                int requestedDataSlot = target.getDataItems() == null ? 0 : dataSlot[0] - 1;
                requested = new CentralMonitorGroupTargetState(target.getBlockPos(), requestedDataSlot);
            }
            setCentralMonitorGroupTarget(group.getIdentity(), expected, requested);
        });
        removeFromGroupButton.setOnPressCallback(click -> {
            for (MonitorGroup group : List.copyOf(monitorGroups)) {
                Set<BlockPos> requestedPositions = new LinkedHashSet<>();
                for (IMonitorComponent component : selectedComponents) {
                    if (group.contains(component.getBlockPos())) {
                        requestedPositions.add(component.getBlockPos());
                    }
                }
                if (!requestedPositions.isEmpty() &&
                        !removeCentralMonitorGroupMembers(
                                monitorGroupMembershipRevision, group.getIdentity(), requestedPositions)) {
                    return;
                }
            }
            groupList.clearAllWidgets();
            monitorGroups.forEach(addGroupToList);

            removeFromGroupButton.setVisible(false);
            createGroupButton.setVisible(true);
            Iterator<IMonitorComponent> it = selectedComponents.iterator();
            while (it.hasNext()) {
                IMonitorComponent c = it.next();
                BlockPos rel = toRelative(c.getBlockPos());
                if (imageButtons.size() - 1 < rel.getY()) continue;
                if (imageButtons.get(rel.getY()).size() - 1 < rel.getX()) continue;
                imageButtons.get(rel.getY()).get(rel.getX()).accept(it);
            }
            if (!selectedTargets.isEmpty()) {
                rightClickCallbacks.getOrDefault(selectedTargets.get(0).getBlockPos(), () -> {}).run();
            }
        });
        createGroupButton.setButtonTexture(GuiTextures.text("gtpm.central_monitor.gui.create_group"));
        createGroupButton.setVisible(false);
        options.addWidget(removeFromGroupButton);
        options.addWidget(createGroupButton);
        options.addWidget(setTargetButton);
        int startX = 20;
        int startY = 30;
        for (int row = 0; row <= downDist + upDist; row++) {
            imageButtons.add(new ArrayList<>());
            for (int col = 0; col <= leftDist + rightDist; col++) {
                IGuiTexture texture = getComponentTexture(row, col);
                IGuiTexture textures = GuiTextures.group(texture, new ColorBorderTexture(2, 0xFFFFFF));
                IMonitorComponent component = getComponent(row, col);
                if (component == null) {
                    imageButtons.getLast().add(it -> {});
                    continue;
                }
                ButtonWidget img = new ButtonWidget(startX + (16 * col), startY + (16 * row), 16, 16, textures, null);
                Consumer<Iterator<IMonitorComponent>> callback = (it) -> {
                    if (!component.isMonitor()) return;
                    if (selectedComponents.contains(component)) {
                        if (it == null) {
                            selectedComponents.remove(component);
                        } else {
                            it.remove();
                        }

                        if (!selectedTargets.isEmpty() && selectedTargets.get(0) == component) {
                            IGuiTexture rect = GuiTextures.colorRect(Color.BLUE);
                            img.setButtonTexture(GuiTextures.group(rect, texture));
                        } else {
                            img.setButtonTexture(texture);
                        }

                        createGroupButton.setVisible(selectedComponents.stream().noneMatch(this::isInAnyGroup));
                        removeFromGroupButton.setVisible(selectedComponents.stream().allMatch(this::isInAnyGroup));
                        setTargetButton.setVisible(removeFromGroupButton.isVisible());

                        if (selectedComponents.isEmpty()) {
                            createGroupButton.setVisible(false);
                            removeFromGroupButton.setVisible(false);
                            setTargetButton.setVisible(false);
                        }
                    } else {
                        boolean inAnyGroup = isInAnyGroup(component);
                        // yes I know this is terrible but if it works don't touch it :)
                        if (selectedComponents.isEmpty() && !inAnyGroup) createGroupButton.setVisible(true);
                        if (inAnyGroup) createGroupButton.setVisible(false);
                        if (selectedComponents.isEmpty() && inAnyGroup) {
                            removeFromGroupButton.setVisible(true);
                            setTargetButton.setVisible(true);
                        }
                        if (!inAnyGroup) {
                            removeFromGroupButton.setVisible(false);
                            setTargetButton.setVisible(false);
                        }
                        selectedComponents.add(component);
                        IGuiTexture rect = GuiTextures.colorRect(
                                (selectedTargets.isEmpty() || selectedTargets.get(0) != component) ? Color.RED :
                                        Color.PINK);
                        img.setButtonTexture(GuiTextures.group(rect, texture));
                    }
                    if (isInAnyGroup(component)) {
                        monitorGroups.forEach(group -> {
                            if (group.contains(component.getBlockPos())) {
                                img.setHoverTooltips(
                                        Component.translatable("gtpm.gui.central_monitor.group", group.getName()));
                            }
                        });
                    } else {
                        img.setHoverTooltips(Component.translatable("gtpm.gui.central_monitor.group",
                                Component.translatable("gtpm.gui.central_monitor.none")));
                    }
                };
                Runnable rightClickCallback = () -> {
                    if (!selectedTargets.isEmpty()) {
                        if (selectedTargets.get(0).getBlockPos() == component.getBlockPos()) {
                            selectedTargets.clear();
                            if (selectedComponents.contains(component)) {
                                IGuiTexture rect = GuiTextures.colorRect(Color.RED);
                                img.setButtonTexture(GuiTextures.group(rect, texture));
                            } else {
                                img.setButtonTexture(texture);
                            }
                            dataSlotInput.setVisible(false);
                            return;
                        } else {
                            try {
                                rightClickCallbacks.get(selectedTargets.get(0).getBlockPos()).run();
                            } catch (StackOverflowError e) {
                                GTCEu.LOGGER.error(
                                        "Stack overflow when right-clicking monitor component {} at {} (selectedTarget is {} at {})",
                                        component, component.getBlockPos(), selectedTargets.get(0),
                                        selectedTargets.get(0).getBlockPos());
                            }
                        }
                    }
                    selectedTargets.add(component);
                    IGuiTexture rect;
                    if (selectedComponents.contains(component)) {
                        rect = GuiTextures.colorRect(Color.PINK);
                    } else {
                        rect = GuiTextures.colorRect(Color.BLUE);
                    }
                    img.setButtonTexture(GuiTextures.group(rect, texture));
                    if (component.getDataItems() != null) {
                        IItemHandler dataItems = component.getDataItems();
                        MonitorGroup selectedGroup = null;
                        for (MonitorGroup group : monitorGroups) {
                            for (IMonitorComponent c : selectedComponents) {
                                if (group.contains(c.getBlockPos())) {
                                    if (selectedGroup == null || selectedGroup == group) {
                                        selectedGroup = group;
                                    } else {
                                        selectedGroup = null;
                                        break;
                                    }
                                }
                            }
                        }
                        if (selectedGroup != null) {
                            dataSlot[0] = selectedGroup.getDataSlot() + 1;
                        }
                        dataSlot[1] = dataItems.getSlots();
                        dataSlotInput.setVisible(true);
                    }
                };
                if (isInAnyGroup(component)) {
                    monitorGroups.forEach(group -> {
                        if (group.contains(component.getBlockPos())) img.setHoverTooltips(
                                Component.translatable("gtpm.gui.central_monitor.group", group.getName()));
                    });
                } else {
                    img.setHoverTooltips(Component.translatable("gtpm.gui.central_monitor.group",
                            Component.translatable("gtpm.gui.central_monitor.none")));
                }
                img.setOnPressCallback(click -> {
                    if (click.button == 0) callback.accept(null);
                    else if (click.button == 1) rightClickCallback.run();
                });
                componentSelection.addWidget(img);
                imageButtons.getLast().add(callback);
                rightClickCallbacks.put(component.getBlockPos(), rightClickCallback);
            }
        }
        builder.addWidget(main);
        return builder;
    }

    @Override
    public IGuiTexture getComponentIcon() {
        return GuiTextures.spirit(GTCEu.id("block/multiblock/network_switch/overlay_front_active"));
    }

    @Override
    public @NotNull List<Component> getDebugInfo(Player player, int logLevel,
                                                 PortableScannerBehavior.DisplayMode mode) {
        return List.of(Component.translatable("gtpm.central_monitor.size", leftDist, rightDist, upDist, downDist));
    }

    @Override
    public @NotNull List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        return List.of(Component.translatable("gtpm.central_monitor.size", leftDist, rightDist, upDist, downDist));
    }

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        for (MonitorGroup group : monitorGroups) {
            group.getItemStackHandler().dropInventoryInWorld(getLevel(), getBlockPos());;
            group.getPlaceholderSlotsHandler().dropInventoryInWorld(getLevel(), getBlockPos());
        }
    }
}
