package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerList;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomReceiverTrait;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class WorkableMultiblockMachine extends MultiblockControllerMachine
                                                implements IWorkableMultiController, IMufflableMachine {

    @Getter
    protected final CleanroomReceiverTrait cleanroomReceiver;
    @Getter
    @SaveField
    @SyncToClient
    public final RecipeLogic recipeLogic;
    @Getter
    private GTRecipeType[] recipeTypes;
    @Getter
    @Setter
    @SaveField
    @SyncBoth
    private int activeRecipeType;
    @Getter
    protected final Map<IO, List<RecipeHandlerList>> capabilitiesProxy;
    @Getter
    protected final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> capabilitiesFlat;
    protected final List<ISubscription> traitSubscriptions;
    @Getter
    @SaveField
    @SyncToClient
    protected boolean isMuffled;
    protected boolean previouslyMuffled = true;
    @Nullable
    @Getter
    protected LongSet activeBlocks;

    @Getter
    @SaveField
    @SyncBoth
    protected VoidingMode voidingMode = VoidingMode.VOID_NONE;

    public WorkableMultiblockMachine(BlockEntityCreationInfo info,
                                     RecipeLogic recipeLogic) {
        super(info);
        this.recipeTypes = getDefinition().getRecipeTypes();
        this.activeRecipeType = 0;
        this.cleanroomReceiver = attachTrait(new CleanroomReceiverTrait());
        this.recipeLogic = attachTrait(recipeLogic);
        this.capabilitiesProxy = new EnumMap<>(IO.class);
        this.capabilitiesFlat = new EnumMap<>(IO.class);
        this.traitSubscriptions = new ArrayList<>();
    }

    public WorkableMultiblockMachine(BlockEntityCreationInfo info) {
        this(info, new RecipeLogic());
    }

    public void setMuffled(boolean muffled) {
        if (isMuffled == muffled) return;
        isMuffled = muffled;
    }

    @Override
    public WorkableMultiblockMachine self() {
        return this;
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public void onUnload() {
        super.onUnload();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////
    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        rebuildRecipeHandlers();
    }

    private void rebuildRecipeHandlers() {
        activeBlocks = collectActiveBlocks();
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
        for (IMultiPart part : getParts()) {
            IO io = getPartIO(part);
            if (io == IO.NONE) continue;

            var handlerLists = part.getRecipeHandlers();
            for (var handlerList : handlerLists) {
                if (!handlerList.isValid(io)) continue;
                this.addHandlerList(handlerList);
                traitSubscriptions.add(handlerList.subscribe(recipeLogic::onRecipeHandlerChanged));
            }
        }

        // attach self traits
        Map<IO, List<IRecipeHandler<?>>> ioTraits = new EnumMap<>(IO.class);
        for (MachineTrait trait : getAllTraits()) {
            if (trait instanceof IRecipeHandlerTrait<?> handlerTrait) {
                ioTraits.computeIfAbsent(handlerTrait.getHandlerIO(), i -> new ArrayList<>()).add(handlerTrait);
            }
        }

        for (var entry : ioTraits.entrySet()) {
            var handlerList = RecipeHandlerList.of(entry.getKey(), entry.getValue());
            this.addHandlerList(handlerList);
            traitSubscriptions.add(handlerList.subscribe(recipeLogic::onRecipeHandlerChanged));
        }
        // schedule recipe logic
        recipeLogic.updateTickSubscription();
    }

    private LongSet collectActiveBlocks() {
        LongOpenHashSet blocks = new LongOpenHashSet();
        for (String structureName : getDefinition().getStructureNames()) {
            if (!isStructureFormed(structureName)) continue;
            LongSet structureActiveBlocks = getMultiblockState(structureName).getMatchContext()
                    .getOrDefault("vaBlocks", LongSets.emptySet());
            blocks.addAll(structureActiveBlocks);
        }
        return blocks.isEmpty() ? LongSets.emptySet() : blocks;
    }

    private IO getPartIO(IMultiPart part) {
        String structureName = part.getSubstructureName(this);
        if (structureName == null) return IO.BOTH;
        Long2ObjectMap<IO> ioMap = getMultiblockState(structureName).getMatchContext()
                .getOrDefault("ioMap", Long2ObjectMaps.emptyMap());
        return ioMap.getOrDefault(part.self().getBlockPos().asLong(), IO.BOTH);
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        updateActiveBlocks(false);
        activeBlocks = null;
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
        if (DEFAULT_STRUCTURE.equals(structureName) || !isFormed()) {
            recipeLogic.resetRecipeLogic();
        } else {
            rebuildRecipeHandlers();
        }
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        updateActiveBlocks(false);
        activeBlocks = null;
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
        // fine some parts invalid now.
        // but we shouldn't reset recipe logic rn.
        // if it's due to chunk unload, we should just wait for it to be valid again.
        recipeLogic.updateTickSubscription();
    }

    //////////////////////////////////////
    // ****** RECIPE LOGIC *******//
    //////////////////////////////////////

    @Override
    public void clientTick() {
        super.clientTick();
        if (previouslyMuffled != isMuffled) {
            previouslyMuffled = isMuffled;

            recipeLogic.updateSound();
        }
    }

    @Nullable
    @Override
    public final GTRecipe doModifyRecipe(GTRecipe recipe) {
        for (IMultiPart part : getParts()) {
            recipe = part.modifyRecipe(recipe);
            if (recipe == null) return null;
        }
        return getRealRecipe(recipe);
    }

    @Nullable
    protected GTRecipe getRealRecipe(GTRecipe recipe) {
        return self().getDefinition().getRecipeModifier().applyModifier(self(), recipe);
    }

    public void updateActiveBlocks(boolean active) {
        if (activeBlocks != null) {
            for (long pos : activeBlocks) {
                var blockPos = BlockPos.of(pos);
                var blockState = getLevel().getBlockState(blockPos);
                if (blockState.hasProperty(GTBlockStateProperties.ACTIVE)) {
                    var newState = blockState.setValue(GTBlockStateProperties.ACTIVE, active);
                    if (newState != blockState) {
                        getLevel().setBlock(blockPos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    @Override
    public boolean keepSubscribing() {
        return false;
    }

    @Override
    public void notifyWorkStatusChanged(WorkLogic.Status oldStatus, WorkLogic.Status newStatus) {
        IWorkableMultiController.super.notifyWorkStatusChanged(oldStatus, newStatus);
        if (newStatus == WorkLogic.Status.WORKING || oldStatus == WorkLogic.Status.WORKING) {
            updateActiveBlocks(newStatus == WorkLogic.Status.WORKING);
        }
        for (IMultiPart part : getParts()) {
            MachineRenderState state = part.self().getRenderState();
            if (state.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS)) {
                part.self().setRenderState(state.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, newStatus));
            }
        }
    }

    @Override
    public boolean isWorkLogicAvailable() {
        return isFormed && !getMultiblockState(DEFAULT_STRUCTURE).hasError();
    }

    @Override
    public void afterWorking() {
        for (IMultiPart part : getParts()) {
            part.afterWorking(this);
        }
        IWorkableMultiController.super.afterWorking();
    }

    @Override
    @Nullable
    public Component beforeWorking(@Nullable GTRecipe recipe) {
        for (IMultiPart part : getParts()) {
            Component failReason = part.beforeWorking(this);
            if (failReason != null) {
                return failReason;
            }
        }
        return IWorkableMultiController.super.beforeWorking(recipe);
    }

    @Override
    public boolean onWorking() {
        for (IMultiPart part : getParts()) {
            if (!part.onWorking(this)) {
                return false;
            }
        }
        return IWorkableMultiController.super.onWorking();
    }

    @Override
    public void onWaiting() {
        for (IMultiPart part : getParts()) {
            part.onWaiting(this);
        }
        IWorkableMultiController.super.onWaiting();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (!isWorkingAllowed) {
            for (IMultiPart part : getParts()) {
                part.onPaused(this);
            }
        }
        IWorkableMultiController.super.setWorkingEnabled(isWorkingAllowed);
    }

    public GTRecipeType getRecipeType() {
        if (activeRecipeType >= recipeTypes.length) {
            GTCEu.LOGGER.warn("Preventing crash from bad recipe type index!");
            activeRecipeType = recipeTypes.length - 1;
        }
        return recipeTypes[activeRecipeType];
    }

    // Recipe compat
    public void setRecipeType(@NotNull GTRecipeType type) {
        int recipeIndex = -1;
        for (int i = 0; i < recipeTypes.length; i++) {
            if (type.equals(recipeTypes[i])) {
                recipeIndex = i;
                break;
            }
        }
        if (recipeIndex == -1) {
            var newer = new GTRecipeType[recipeTypes.length + 1];
            System.arraycopy(recipeTypes, 0, newer, 0, recipeTypes.length);
            newer[recipeTypes.length] = type;
            recipeTypes = newer;
            recipeIndex = recipeTypes.length - 1;
        }
        setActiveRecipeType(recipeIndex);
        recipeLogic.updateTickSubscription();
    }

    @ServerFieldNormalizer(fieldName = "activeRecipeType")
    private int normalizeActiveRecipeType(int candidate) {
        if (candidate < 0 || candidate >= recipeTypes.length) {
            throw new IllegalArgumentException("Active recipe type index is out of range.");
        }
        return candidate;
    }

    @ServerFieldChangeListener(fieldName = "activeRecipeType")
    private void onActiveRecipeTypeChanged(int oldIndex, int newIndex) {
        if (!keepSubscribing()) {
            recipeLogic.updateTickSubscription();
        }
    }

    @Override
    public void setVoidingMode(VoidingMode mode) {
        voidingMode = mode;
        getRecipeLogic().updateTickSubscription();
    }

    @ServerFieldNormalizer(fieldName = "voidingMode")
    private VoidingMode normalizeVoidingMode(VoidingMode candidate) {
        for (VoidingMode allowed : VoidingMode.VALUES) {
            if (allowed == candidate) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported voiding mode.");
    }

    @ServerFieldChangeListener(fieldName = "voidingMode")
    private void onVoidingModeChanged(VoidingMode oldMode, VoidingMode newMode) {
        getRecipeLogic().updateTickSubscription();
    }
}
