package com.gregtechceu.gtceu.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerList;
import com.gregtechceu.gtceu.api.sync_system.annotations.ClientFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockPartMachine extends MetaMachine implements IMultiPart {

    @SyncToClient
    @RerenderOnChanged
    protected final Map<BlockPos, Set<String>> controllerStructures = new Object2ObjectOpenHashMap<>(8);
    protected final SortedSet<MultiblockControllerMachine> controllers = new ReferenceLinkedOpenHashSet<>(8);

    private @Nullable RecipeHandlerList handlerList;

    public MultiblockPartMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public boolean hasController(BlockPos controllerPos, String structureName) {
        Set<String> structures = controllerStructures.get(controllerPos);
        return structures != null && structures.contains(structureName);
    }

    @Override
    public boolean isFormed() {
        return controllerStructures.values().stream().anyMatch(structures -> !structures.isEmpty());
    }

    // Not sure if necessary, but added to match the Controller class
    @ClientFieldChangeListener(fieldName = "controllerStructures")
    public void onControllersUpdated() {
        controllers.clear();
        for (BlockPos blockPos : controllerStructures.keySet()) {
            if (MetaMachine.getMachine(getLevel(), blockPos) instanceof MultiblockControllerMachine controller) {
                controllers.add(controller);
            }
        }
    }

    @Override
    @UnmodifiableView
    public SortedSet<MultiblockControllerMachine> getControllers() {
        // Necessary to rebuild the set of controllers on client-side
        if (controllers.size() != controllerStructures.size()) {
            onControllersUpdated();
        }
        return Collections.unmodifiableSortedSet(controllers);
    }

    public List<RecipeHandlerList> getRecipeHandlers() {
        return List.of(getHandlerList());
    }

    protected RecipeHandlerList getHandlerList() {
        if (handlerList == null) {
            List<IRecipeHandler<?>> handlers = new ArrayList<>();
            IO handlerIO = null;
            for (var trait : getAllTraits()) {
                if (trait instanceof IRecipeHandlerTrait<?> rht) {
                    if (handlerIO == null) handlerIO = rht.getHandlerIO();
                    handlers.add(rht);
                }
            }

            if (handlers.isEmpty()) {
                handlerList = RecipeHandlerList.NO_DATA;
            } else {
                handlerList = RecipeHandlerList.of(handlerIO, getPaintingColor(), handlers);
            }
        }
        return handlerList;
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (getLevel() instanceof ServerLevel serverLevel) {
            // Need to copy if > 1 so that we can call removedFromController safely without CME
            Set<MultiblockControllerMachine> toIter = controllers.size() > 1 ? new ObjectOpenHashSet<>(controllers) :
                    controllers;
            for (MultiblockControllerMachine controller : toIter) {
                if (serverLevel.isLoaded(controller.self().getBlockPos())) {
                    Set<String> structureNames = controllerStructures.get(controller.self().getBlockPos());
                    if (structureNames != null) {
                        for (String structureName : new ArrayList<>(structureNames)) {
                            removedFromController(controller, structureName);
                        }
                    }
                    controller.onPartUnload();
                }
            }
        }
        controllerStructures.clear();
        controllers.clear();
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////

    @MustBeInvokedByOverriders
    @Override
    public void removedFromController(MultiblockControllerMachine controller, String structureName) {
        BlockPos controllerPos = controller.self().getBlockPos();
        Set<String> structures = controllerStructures.get(controllerPos);
        if (structures != null) {
            structures.remove(structureName);
            if (structures.isEmpty()) {
                controllerStructures.remove(controllerPos);
                controllers.remove(controller);
            }
        }

        if (!isFormed()) {
            MachineRenderState renderState = getRenderState();
            if (renderState.hasProperty(GTMachineModelProperties.IS_FORMED)) {
                setRenderState(renderState.setValue(GTMachineModelProperties.IS_FORMED, false));
            }
        }
        syncDataHolder.markClientSyncFieldDirty("controllerStructures");
    }

    @MustBeInvokedByOverriders
    @Override
    public void addedToController(MultiblockControllerMachine controller, String structureName) {
        controllerStructures.computeIfAbsent(controller.self().getBlockPos().immutable(), $ -> new LinkedHashSet<>())
                .add(structureName);
        controllers.add(controller);

        syncDataHolder.markClientSyncFieldDirty("controllerStructures");
        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.IS_FORMED)) {
            setRenderState(renderState.setValue(GTMachineModelProperties.IS_FORMED, true));
        }
    }

    @Override
    @Nullable
    public String getSubstructureName(MultiblockControllerMachine controller) {
        Set<String> structures = controllerStructures.get(controller.self().getBlockPos());
        if (structures == null || structures.isEmpty()) {
            return null;
        }
        if (structures.contains(MultiblockControllerMachine.DEFAULT_STRUCTURE)) {
            return MultiblockControllerMachine.DEFAULT_STRUCTURE;
        }
        return structures.stream().min(Comparator.naturalOrder()).orElse(null);
    }

    @Override
    public boolean replacePartModelWhenFormed() {
        var renderState = getRenderState();
        return renderState.hasProperty(GTMachineModelProperties.IS_FORMED) &&
                renderState.getValue(GTMachineModelProperties.IS_FORMED);
    }

    @Override
    @Nullable
    public BlockState getFormedAppearance(BlockState sourceState, BlockPos sourcePos, Direction side) {
        if (!replacePartModelWhenFormed()) return null;
        return IMultiPart.super.getFormedAppearance(sourceState, sourcePos, side);
    }
}
