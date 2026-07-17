package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.DynamicItemSlotMachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2DynamicItemSlotMachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
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
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;
import com.gregtechceu.gtceu.common.machine.trait.CentralMonitorLogic;
import com.gregtechceu.gtceu.common.network.packets.SCPacketMonitorGroupDataChange;
import com.gregtechceu.gtceu.data.pattern.StructurePatternKey;
import com.gregtechceu.gtceu.data.pattern.StructurePatternResolver;

import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import com.google.gson.JsonElement;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CentralMonitorMachine extends WorkableElectricMultiblockMachine
                                   implements IMonitorComponent, IDataInfoProvider,
                                   LDLib2DynamicItemSlotMachineUIProvider,
                                   CentralMonitorMembershipActionTarget, CentralMonitorGroupTargetActionHost,
                                   CentralMonitorImageModuleActionHost, CentralMonitorTextModuleActionHost {

    static {
        CentralMonitorMembershipActions.initialize();
        CentralMonitorGroupTargetActions.initialize();
        CentralMonitorImageModuleActions.initialize();
        CentralMonitorTextModuleActions.initialize();
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
        MonitorGroup group = resolveCentralMonitorGroup(groupIdentity);
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
        MonitorGroup group = resolveCentralMonitorGroup(groupIdentity);
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
        MonitorGroup group = resolveCentralMonitorGroup(groupIdentity);
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
        MonitorGroup group = resolveCentralMonitorGroup(groupIdentity);
        if (group == null) {
            return false;
        }
        group.setTargetAndDataSlot(requested.targetPos(), requested.dataSlot());
        getSyncDataHolder().markClientSyncFieldDirty(MONITOR_GROUPS_SYNC_FIELD);
        return true;
    }

    /**
     * Publishes an in-place monitor group configuration change without treating it as a physical module replacement.
     */
    public void markMonitorGroupDataChanged() {
        getSyncDataHolder().markClientSyncFieldDirty(MONITOR_GROUPS_SYNC_FIELD);
    }

    @Override
    public void resyncCentralMonitorImageModuleState() {
        markMonitorGroupDataChanged();
    }

    @Override
    public void resyncCentralMonitorTextModuleState() {
        markMonitorGroupDataChanged();
    }

    /**
     * Resolves the current synchronized image module for a page opened against one physical module-slot occupant.
     */
    public @Nullable ItemStack resolveCentralMonitorImageModuleForOpening(UUID groupIdentity,
                                                                          UUID moduleSlotIncarnation) {
        MonitorGroup group = resolveCentralMonitorGroup(groupIdentity);
        if (group == null || !group.getModuleSlotIncarnation().equals(moduleSlotIncarnation)) {
            return null;
        }
        ItemStack module = group.getItemStackHandler().getStackInSlot(0);
        return CentralMonitorImageModuleActions.isImageModule(module) ? module : null;
    }

    /**
     * Resolves the current synchronized text module for a page opened against one physical module-slot occupant.
     */
    public @Nullable ItemStack resolveCentralMonitorTextModuleForOpening(UUID groupIdentity,
                                                                         UUID moduleSlotIncarnation) {
        MonitorGroup group = resolveCentralMonitorGroup(groupIdentity);
        if (group == null || !group.getModuleSlotIncarnation().equals(moduleSlotIncarnation)) {
            return null;
        }
        ItemStack module = group.getItemStackHandler().getStackInSlot(0);
        return CentralMonitorTextModuleActions.isTextModule(module) ? module : null;
    }

    @Override
    public boolean canSetCentralMonitorTextModuleConfiguration(UUID groupIdentity, UUID moduleSlotIncarnation,
                                                               long expectedConfigurationRevision,
                                                               ItemStack expectedModule,
                                                               TextLineList requestedConfiguration) {
        return resolveTextModuleChangeTarget(
                groupIdentity, moduleSlotIncarnation, expectedConfigurationRevision,
                expectedModule, requestedConfiguration) != null;
    }

    @Override
    public boolean setCentralMonitorTextModuleConfiguration(UUID groupIdentity, UUID moduleSlotIncarnation,
                                                            long expectedConfigurationRevision,
                                                            ItemStack expectedModule,
                                                            TextLineList requestedConfiguration) {
        MonitorGroup group = resolveTextModuleChangeTarget(
                groupIdentity, moduleSlotIncarnation, expectedConfigurationRevision,
                expectedModule, requestedConfiguration);
        if (group == null) {
            return false;
        }
        group.applyTextConfiguration(requestedConfiguration);
        getSyncDataHolder().markClientSyncFieldDirty(MONITOR_GROUPS_SYNC_FIELD);
        return true;
    }

    private @Nullable MonitorGroup resolveTextModuleChangeTarget(
                                                                 UUID groupIdentity,
                                                                 UUID moduleSlotIncarnation,
                                                                 long expectedConfigurationRevision,
                                                                 ItemStack expectedModule,
                                                                 TextLineList requestedConfiguration) {
        if (!isMembershipStructureAvailable() ||
                !CentralMonitorTextModuleActions.isValidScale(requestedConfiguration.scale())) {
            return null;
        }
        MonitorGroup group = resolveCentralMonitorGroup(groupIdentity);
        if (group == null || !group.getModuleSlotIncarnation().equals(moduleSlotIncarnation) ||
                group.getTextConfigurationRevision() != expectedConfigurationRevision) {
            return null;
        }
        ItemStack currentModule = group.getItemStackHandler().getStackInSlot(0);
        if (!CentralMonitorTextModuleActions.isTextModule(currentModule) ||
                !CentralMonitorTextModuleActions.matchesExpectedModule(currentModule, expectedModule)) {
            return null;
        }
        TextLineList currentConfiguration = currentModule.get(GTDataComponents.FORMAT_STRING_LIST.get());
        if (requestedConfiguration.equals(currentConfiguration)) {
            return null;
        }
        if (group.getTextConfigurationRevision() == Long.MAX_VALUE) {
            GTCEu.LOGGER.error("Central Monitor text configuration revision is exhausted for group {} at {}",
                    group.getIdentity(), getBlockPos());
            return null;
        }
        long nextRevision = Math.incrementExact(group.getTextConfigurationRevision());
        return canPersistCentralMonitorTextModuleConfiguration(
                group, currentModule, requestedConfiguration, nextRevision) ? group : null;
    }

    private boolean canPersistCentralMonitorTextModuleConfiguration(MonitorGroup group, ItemStack currentModule,
                                                                    TextLineList requestedConfiguration,
                                                                    long nextRevision) {
        Level level = getLevel();
        if (level == null) {
            GTCEu.LOGGER.error("Central Monitor at {} cannot validate text module save data without a level",
                    getBlockPos());
            return false;
        }

        ItemStack candidate = currentModule.copy();
        candidate.set(GTDataComponents.FORMAT_STRING_LIST.get(), requestedConfiguration);
        var stacks = group.getItemStackHandler().getStacks();
        ItemStack previous = stacks.set(0, candidate);
        long previousRevision = group.getTextConfigurationRevision();
        group.setTextConfigurationRevision(nextRevision);
        try {
            DataComponentMap savedData = getSyncDataHolder()
                    .serializeToComponents(level.registryAccess(), false, false);
            Tag savedTag = DataComponentMap.CODEC
                    .encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), savedData)
                    .getOrThrow();
            try (DataOutputStream output = new DataOutputStream(OutputStream.nullOutputStream())) {
                savedTag.write(output);
            }
            JsonElement monitorGroupsData = getSyncDataHolder()
                    .serializeClientFieldSnapshot(level.registryAccess(), MONITOR_GROUPS_SYNC_FIELD);
            if (!SyncFieldData.isFieldValueWithinNetworkLimit(monitorGroupsData)) {
                GTCEu.LOGGER.warn(
                        "Rejecting Central Monitor text module configuration for group {} at {} because monitorGroups exceeds the network field limit",
                        group.getIdentity(), getBlockPos());
                return false;
            }
            return true;
        } catch (UTFDataFormatException exception) {
            GTCEu.LOGGER.warn(
                    "Rejecting Central Monitor text module configuration for group {} at {} because its save data exceeds the modified-UTF limit",
                    group.getIdentity(), getBlockPos());
            return false;
        } catch (IOException exception) {
            GTCEu.LOGGER.error("Failed to validate Central Monitor text module save data for group {} at {}",
                    group.getIdentity(), getBlockPos(), exception);
            throw new IllegalStateException("Failed to validate Central Monitor text module save data", exception);
        } catch (RuntimeException exception) {
            GTCEu.LOGGER.error("Failed to encode Central Monitor text module save data for group {} at {}",
                    group.getIdentity(), getBlockPos(), exception);
            throw exception;
        } finally {
            stacks.set(0, previous);
            group.setTextConfigurationRevision(previousRevision);
        }
    }

    @Override
    public boolean canSetCentralMonitorImageModuleUrl(UUID groupIdentity, UUID moduleSlotIncarnation,
                                                      ItemStack expectedModule, @Nullable String expectedUrl,
                                                      String requestedUrl) {
        return resolveImageModuleChangeTarget(
                groupIdentity, moduleSlotIncarnation, expectedModule, expectedUrl, requestedUrl) != null;
    }

    @Override
    public boolean setCentralMonitorImageModuleUrl(UUID groupIdentity, UUID moduleSlotIncarnation,
                                                   ItemStack expectedModule, @Nullable String expectedUrl,
                                                   String requestedUrl) {
        MonitorGroup group = resolveImageModuleChangeTarget(
                groupIdentity, moduleSlotIncarnation, expectedModule, expectedUrl, requestedUrl);
        if (group == null) {
            return false;
        }
        group.getItemStackHandler().getStackInSlot(0).set(GTDataComponents.IMAGE_MODULE_URL.get(), requestedUrl);
        getSyncDataHolder().markClientSyncFieldDirty(MONITOR_GROUPS_SYNC_FIELD);
        return true;
    }

    private @Nullable MonitorGroup resolveImageModuleChangeTarget(
                                                                  UUID groupIdentity,
                                                                  UUID moduleSlotIncarnation,
                                                                  ItemStack expectedModule,
                                                                  @Nullable String expectedUrl,
                                                                  String requestedUrl) {
        if (!isMembershipStructureAvailable() ||
                !CentralMonitorImageModuleActions.isValidUrl(requestedUrl) ||
                sameNullableString(expectedUrl, requestedUrl)) {
            return null;
        }
        MonitorGroup group = resolveCentralMonitorGroup(groupIdentity);
        if (group == null || !group.getModuleSlotIncarnation().equals(moduleSlotIncarnation)) {
            return null;
        }
        ItemStack currentModule = group.getItemStackHandler().getStackInSlot(0);
        if (!CentralMonitorImageModuleActions.isImageModule(currentModule) ||
                !CentralMonitorImageModuleActions.matchesExpectedModule(currentModule, expectedModule)) {
            return null;
        }
        String snapshotUrl = expectedModule.get(GTDataComponents.IMAGE_MODULE_URL.get());
        String currentUrl = currentModule.get(GTDataComponents.IMAGE_MODULE_URL.get());
        if (!sameNullableString(snapshotUrl, expectedUrl) || !sameNullableString(currentUrl, expectedUrl)) {
            return null;
        }
        return canPersistCentralMonitorImageModuleUrl(group, currentModule, requestedUrl) ? group : null;
    }

    private boolean canPersistCentralMonitorImageModuleUrl(MonitorGroup group, ItemStack currentModule,
                                                           String requestedUrl) {
        Level level = getLevel();
        if (level == null) {
            GTCEu.LOGGER.error("Central Monitor at {} cannot validate image module save data without a level",
                    getBlockPos());
            return false;
        }

        ItemStack candidate = currentModule.copy();
        candidate.set(GTDataComponents.IMAGE_MODULE_URL.get(), requestedUrl);
        var stacks = group.getItemStackHandler().getStacks();
        ItemStack previous = stacks.set(0, candidate);
        try {
            DataComponentMap savedData = getSyncDataHolder()
                    .serializeToComponents(level.registryAccess(), false, false);
            Tag savedTag = DataComponentMap.CODEC
                    .encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), savedData)
                    .getOrThrow();
            try (DataOutputStream output = new DataOutputStream(OutputStream.nullOutputStream())) {
                savedTag.write(output);
            }
            return true;
        } catch (UTFDataFormatException exception) {
            GTCEu.LOGGER.warn(
                    "Rejecting Central Monitor image module URL with {} characters for group {} at {} because its save data exceeds the modified-UTF limit",
                    requestedUrl.length(), group.getIdentity(), getBlockPos());
            return false;
        } catch (IOException exception) {
            GTCEu.LOGGER.error("Failed to validate Central Monitor save data for group {} at {}",
                    group.getIdentity(), getBlockPos(), exception);
            throw new IllegalStateException("Failed to validate Central Monitor save data", exception);
        } catch (RuntimeException exception) {
            GTCEu.LOGGER.error("Failed to encode Central Monitor save data for group {} at {}",
                    group.getIdentity(), getBlockPos(), exception);
            throw exception;
        } finally {
            stacks.set(0, previous);
        }
    }

    private static boolean sameNullableString(@Nullable String first, @Nullable String second) {
        return first == null ? second == null : first.equals(second);
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

    @Nullable
    MonitorGroup resolveCentralMonitorGroup(UUID identity) {
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
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder instanceof DynamicItemSlotMachineUIHolder && holder.getMachine() == this &&
                holder.getMachineDefinitionId().equals(getDefinition().getId()) && isFormed();
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        CentralMonitorElement page = createCentralMonitorElement(player, holder);
        LDLib2FancyMachineUIElement root = new LDLib2FancyMachineUIElement(
                page, player.getInventory(), holder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        root.addChild(page.sessionElement());
        return UI.of(root);
    }

    CentralMonitorElement createCentralMonitorElement(Player player, MachineUIHolder holder) {
        if (!canCreateLDLib2UI(player, holder)) {
            GTCEu.LOGGER.error("Central Monitor at {} rejected an invalid or unformed LDLib2 opening",
                    getBlockPos());
            throw new IllegalArgumentException("Central Monitor UI requires its formed opening machine.");
        }
        updateStructureDimensions();
        return new CentralMonitorElement(this, player, holder);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .addWorkingStatusLine();
        getDefinition().getAdditionalDisplay().accept(this, textList);
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
