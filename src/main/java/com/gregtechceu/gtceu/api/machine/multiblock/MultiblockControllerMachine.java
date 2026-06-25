package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.multiblock.MultiblockMachineTrait;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.multiblock.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.sync_system.annotations.ClientFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockControllerMachine extends MetaMachine {

    public static final String DEFAULT_STRUCTURE = "main";

    private final Map<String, MultiblockState> multiblockStates = new LinkedHashMap<>();
    @SyncToClient
    @RerenderOnChanged
    private final Map<String, Boolean> formedStructures = new LinkedHashMap<>();
    private final Map<String, List<IMultiPart>> structureParts = new LinkedHashMap<>();
    private final List<IMultiPart> parts = new ArrayList<>();
    private @Nullable ParallelHatchPartMachine parallelHatch = null;
    @Getter
    @SyncToClient
    @RerenderOnChanged
    private BlockPos[] partPositions = new BlockPos[0];

    /**
     * Whether the main multiblock structure is formed.
     * <br>
     * NOTE: even machine is formed, it doesn't mean to workable!
     * Its parts maybe invalid due to chunk unload.
     */
    @Getter
    @SaveField
    @SyncToClient
    @RerenderOnChanged
    protected boolean isFormed;
    @Getter
    @SaveField
    @SyncToClient
    protected boolean isFlipped;

    public MultiblockControllerMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    //////////////////////////////////////
    // *** Multiblock Lifecycle ***//
    //////////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).addAsyncLogic(this);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).removeAsyncLogic(this);
        }
    }

    /**
     * Called when a named structure is formed, have to be called after {@link #checkPattern(String)}.
     * (server-side / fake scene only)
     * <br>
     * Trigger points:
     * <br>
     * 1 - Blocks in structure changed but still formed.
     * <br>
     * 2 - Literally, structure formed.
     */
    public void formStructure(String structureName) {
        structureName = validateStructureName(structureName);
        setStructureFormed(structureName, true);

        MultiblockState state = getMultiblockState(structureName);
        List<IMultiPart> newParts = new ArrayList<>();
        Set<IMultiPart> set = state.getMatchContext().getOrCreate("parts", Collections::emptySet);
        for (IMultiPart part : set) {
            if (shouldAddPartToController(part)) {
                newParts.add(part);
            }
        }
        newParts.sort(getPartSorter());
        replaceStructureParts(structureName, newParts);
        for (var part : newParts) {
            part.addedToController(this, structureName);
        }
        rebuildParts();

        for (var trait : getAllTraits()) {
            if (trait instanceof MultiblockMachineTrait multiblockMachineTrait)
                multiblockMachineTrait.onStructureFormed(structureName);
        }
    }

    /**
     * Called when a named structure is invalid. (server-side / fake scene only)
     * <br>
     * Trigger points:
     * <br>
     * 1 - Blocks in structure changed.
     * <br>
     * 2 - Before controller machine removed.
     */
    public void invalidateStructure(String structureName) {
        structureName = validateStructureName(structureName);
        setStructureFormed(structureName, false);
        detachStructureParts(structureName);

        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).removeMapping(getMultiblockState(structureName));
        }

        for (var trait : getAllTraits()) {
            if (trait instanceof MultiblockMachineTrait multiblockMachineTrait)
                multiblockMachineTrait.onStructureInvalid(structureName);
        }
    }

    public void invalidateAllStructures() {
        Set<String> structureNames = new LinkedHashSet<>(multiblockStates.keySet());
        structureNames.addAll(formedStructures.keySet());
        structureNames.addAll(getDefinition().getStructureNames());
        for (String structureName : structureNames) {
            invalidateStructure(structureName);
        }
    }

    /**
     * Called from part, when part is invalid due to chunk unload or broken.
     */
    public void onPartUnload() {
        boolean anyPartRemoved = false;
        for (var entry : structureParts.entrySet()) {
            if (entry.getValue().removeIf(part -> part.self().isRemoved())) {
                getMultiblockState(entry.getKey()).setError(MultiblockState.UNLOAD_ERROR);
                anyPartRemoved = true;
            }
        }
        parts.removeIf(part -> part.self().isRemoved());
        if (!anyPartRemoved) {
            for (String structureName : getDefinition().getStructureNames()) {
                getMultiblockState(structureName).setError(MultiblockState.UNLOAD_ERROR);
            }
        }
        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).addAsyncLogic(this);
        }
        rebuildParts();
    }

    //////////////////////////////////////
    // ***** Getters ******//
    /// ///////////////////////////////////

    @Override
    public MultiblockMachineDefinition getDefinition() {
        return (MultiblockMachineDefinition) super.getDefinition();
    }

    /**
     * Get the named MultiblockState. It records all structure-related information.
     */
    public MultiblockState getMultiblockState(String structureName) {
        structureName = validateStructureName(structureName);
        return multiblockStates.computeIfAbsent(structureName,
                name -> new MultiblockState(getLevel(), getBlockPos(), name));
    }

    public boolean isStructureFormed(String structureName) {
        structureName = validateStructureName(structureName);
        return formedStructures.getOrDefault(structureName, false);
    }

    public @Nullable BlockState getPartAppearance(IMultiPart part, Direction side, BlockState sourceState,
                                                  BlockPos sourcePos) {
        String structureName = part.getSubstructureName(this);
        if (structureName != null && isStructureFormed(structureName)) {
            return getDefinition().getPartAppearance().apply(this, part, side);
        }
        return null;
    }

    public Comparator<IMultiPart> getPartSorter() {
        return getDefinition().getPartSorter().apply(this);
    }

    /**
     * Get all parts
     */
    public List<IMultiPart> getParts() {
        // for the client side, when the chunk unloaded
        if (parts.size() != this.partPositions.length) {
            parts.clear();
            for (var pos : this.partPositions) {
                if (getMachine(getLevel(), pos) instanceof IMultiPart part) {
                    parts.add(part);
                }
            }
        }
        return this.parts;
    }

    /**
     * The instance of {@link ParallelHatchPartMachine} attached to this Controller.
     * <p>
     * Note that this will return a singular instance, and will not account for multiple attached IParallelHatches
     *
     * @return an {@link Optional} of the attached IParallelHatch, empty if one is not attached
     */
    public Optional<ParallelHatchPartMachine> getParallelHatch() {
        return Optional.ofNullable(parallelHatch);
    }

    /**
     *
     * @return Whether batching is enabled on this multiblock
     */
    public boolean isBatchEnabled() {
        return false;
    }

    public void setFlipped(boolean flipped) {
        isFlipped = flipped;
        syncDataHolder.markClientSyncFieldDirty("isFlipped");
    }

    @SuppressWarnings("unused")
    @ClientFieldChangeListener(fieldName = "partPositions")
    protected void onPartsUpdated() {
        parts.clear();
        for (var pos : partPositions) {
            if (getMachine(getLevel(), pos) instanceof IMultiPart part) {
                parts.add(part);
            }
        }
    }

    protected void updatePartPositions() {
        this.partPositions = this.parts.isEmpty() ? new BlockPos[0] :
                this.parts.stream().map(part -> part.self().getBlockPos()).toArray(BlockPos[]::new);
        syncDataHolder.markClientSyncFieldDirty("partPositions");
    }

    public void setBatchEnabled(boolean batch) {}

    /**
     * should add part to the part list.
     */
    public boolean shouldAddPartToController(IMultiPart part) {
        return true;
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        if (oldFacing != newFacing && getLevel() instanceof ServerLevel serverLevel) {
            invalidateAllStructures();
            MultiblockWorldSavedData.getOrCreate(serverLevel).addAsyncLogic(this);
        }
    }

    public boolean allowFlip() {
        return getDefinition().isAllowFlip();
    }

    @Override
    public void setUpwardsFacing(Direction upwardsFacing) {
        if (!getDefinition().isAllowExtendedFacing()) {
            return;
        }
        if (upwardsFacing.getAxis() == Direction.Axis.Y) {
            GTCEu.LOGGER.error("Tried to set upwards facing to invalid facing {}! Skipping", upwardsFacing);
            return;
        }
        var blockState = getBlockState();
        if (blockState.getBlock() instanceof MetaMachineBlock &&
                blockState.getValue(GTBlockStateProperties.UPWARDS_FACING) != upwardsFacing) {
            getLevel().setBlockAndUpdate(getBlockPos(),
                    blockState.setValue(GTBlockStateProperties.UPWARDS_FACING, upwardsFacing));
            if (getLevel() != null && !getLevel().isClientSide) {
                notifyBlockUpdate();
                checkAllPatternsWithLock();
            }
        }
    }

    @Override
    public void setFrontFacing(Direction facing) {
        super.setFrontFacing(facing);

        if (getLevel() != null && !getLevel().isClientSide) {
            checkAllPatternsWithLock();
        }
    }

    /**
     * Show the preview of the main structure.
     */
    @Override
    public InteractionResult onUse(ExtendedUseOnContext context) {
        if (!isFormed() && context.getPlayer().isShiftKeyDown()) {
            if (isRemote()) {
                MultiblockInWorldPreviewRenderer.showPreview(getBlockPos(), this,
                        ConfigHolder.INSTANCE.client.inWorldPreviewDuration * 20);
            }
            return InteractionResult.SUCCESS;
        }
        return super.onUse(context);
    }

    public boolean allowCircuitSlots() {
        return true;
    }

    //////////////////////////////////////
    // *** Pattern checking ***//
    //////////////////////////////////////

    /**
     * Get named structure pattern.
     * You can override it to create dynamic patterns.
     */
    public @Nullable BlockPattern getPattern(String structureName) {
        structureName = validateStructureName(structureName);
        return getDefinition().getPattern(structureName);
    }

    /**
     * Get lock for pattern checking.
     */
    @Getter
    private final Lock patternLock = new ReentrantLock();

    /**
     * Called in an async thread. It's unsafe, Don't modify anything of world but checking information.
     * It will be called per 5 tick.
     *
     * @param periodID period Tick
     */
    public void asyncCheckPattern(long periodID) {
        for (String structureName : getDefinition().getStructureNames()) {
            MultiblockState state = getMultiblockState(structureName);
            if ((state.hasError() || !isStructureFormed(structureName)) && (getOffset() + periodID) % 4 == 0 &&
                    checkPatternWithTryLock(structureName)) { // per second
                if (getLevel() instanceof ServerLevel serverLevel) {
                    serverLevel.getServer().execute(() -> {
                        patternLock.lock();
                        try {
                            if (checkPattern(structureName)) { // formed
                                MultiblockState checkedState = getMultiblockState(structureName);
                                if (DEFAULT_STRUCTURE.equals(structureName)) {
                                    setFlipped(checkedState.isNeededFlip());
                                }
                                formStructure(structureName);
                                var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                                mwsd.addMapping(checkedState);
                                if (!hasPendingStructureSearch()) {
                                    mwsd.removeAsyncLogic(this);
                                }
                            }
                        } finally {
                            patternLock.unlock();
                        }
                    });
                }
            }
        }
    }

    /**
     * Check a named MultiBlock Pattern. Just checking pattern without any other logic.
     * You can override it but it's unsafe for calling. because it will also be called in an async thread.
     * <br>
     * you should always use {@link MultiblockControllerMachine#checkPatternWithLock(String)} )} and
     * {@link MultiblockControllerMachine#checkPatternWithTryLock(String)} instead.
     *
     * @return whether it can be formed.
     */
    public boolean checkPattern(String structureName) {
        structureName = validateStructureName(structureName);
        BlockPattern pattern = getPattern(structureName);
        return pattern != null && pattern.checkPatternAt(getMultiblockState(structureName), false);
    }

    /**
     * Check named pattern with a lock.
     */
    public boolean checkPatternWithLock(String structureName) {
        var lock = getPatternLock();
        lock.lock();
        try {
            return checkPattern(structureName);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check named pattern with a try lock
     *
     * @return false - checking failed or cant get the lock.
     */
    public boolean checkPatternWithTryLock(String structureName) {
        var lock = getPatternLock();
        if (lock.tryLock()) {
            try {
                return checkPattern(structureName);
            } finally {
                lock.unlock();
            }
        } else {
            return false;
        }
    }

    protected String validateStructureName(String structureName) {
        Objects.requireNonNull(structureName, "structureName");
        if (structureName.isBlank()) {
            throw new IllegalArgumentException("structureName must not be blank");
        }
        if (!getDefinition().getStructureNames().contains(structureName)) {
            throw new IllegalArgumentException("Unknown multiblock structure '" + structureName + "' for " +
                    getDefinition().getId());
        }
        return structureName;
    }

    private void setStructureFormed(String structureName, boolean formed) {
        formedStructures.put(structureName, formed);
        syncDataHolder.markClientSyncFieldDirty("formedStructures");
        if (DEFAULT_STRUCTURE.equals(structureName)) {
            isFormed = formed;
            syncDataHolder.markClientSyncFieldDirty("isFormed");
            MachineRenderState renderState = getRenderState();
            if (renderState.hasProperty(GTMachineModelProperties.IS_FORMED)) {
                setRenderState(renderState.setValue(GTMachineModelProperties.IS_FORMED, formed));
            }
        }
    }

    private void replaceStructureParts(String structureName, List<IMultiPart> newParts) {
        List<IMultiPart> oldParts = structureParts.getOrDefault(structureName, Collections.emptyList());
        Set<IMultiPart> newPartSet = Collections.newSetFromMap(new IdentityHashMap<>());
        newPartSet.addAll(newParts);
        for (IMultiPart oldPart : oldParts) {
            if (!newPartSet.contains(oldPart)) {
                oldPart.removedFromController(this, structureName);
            }
        }
        structureParts.put(structureName, newParts);
    }

    private void detachStructureParts(String structureName) {
        List<IMultiPart> oldParts = structureParts.remove(structureName);
        if (oldParts != null) {
            for (IMultiPart part : oldParts) {
                part.removedFromController(this, structureName);
            }
        }
        rebuildParts();
    }

    private void rebuildParts() {
        this.parts.clear();
        this.parallelHatch = null;
        Set<IMultiPart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<IMultiPart> structurePartList : structureParts.values()) {
            for (IMultiPart part : structurePartList) {
                if (seen.add(part)) {
                    this.parts.add(part);
                    if (part instanceof ParallelHatchPartMachine pHatch) {
                        parallelHatch = pHatch;
                    }
                }
            }
        }
        this.parts.sort(getPartSorter());
        updatePartPositions();
    }

    private boolean hasPendingStructureSearch() {
        for (String structureName : getDefinition().getStructureNames()) {
            MultiblockState state = getMultiblockState(structureName);
            if (state.hasError() || !isStructureFormed(structureName)) {
                return true;
            }
        }
        return false;
    }

    private void checkAllPatternsWithLock() {
        for (String structureName : getDefinition().getStructureNames()) {
            checkPatternWithLock(structureName);
        }
    }
}
