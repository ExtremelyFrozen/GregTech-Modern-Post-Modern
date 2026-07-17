package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;
import java.util.SortedSet;

public interface IMultiPart extends IMachineFeature, IFancyUIMachine {

    /**
     * Can it be shared among multi multiblock.
     */
    default boolean canShared(MultiblockControllerMachine controller, String structureName) {
        return true;
    }

    /**
     * Whether it belongs to...
     */
    boolean hasController(BlockPos controllerPos, String structureName);

    /**
     * Whether it belongs to a formed Multiblock.
     */
    boolean isFormed();

    /**
     * Get this MultiPart's controllers
     *
     * @return An Unmodifiable View of the part's controllers
     */
    @UnmodifiableView
    SortedSet<MultiblockControllerMachine> getControllers();

    /**
     * Called when it was removed from a multiblock.
     */
    void removedFromController(MultiblockControllerMachine controller, String structureName);

    /**
     * Called when it was added to a multiblock.
     */
    void addedToController(MultiblockControllerMachine controller, String structureName);

    /**
     * Returns the structure name this part belongs to for the supplied controller.
     */
    @Nullable
    String getSubstructureName(MultiblockControllerMachine controller);

    /**
     * Get all available traits for recipe logic.
     */
    List<RecipeHandlerList> getRecipeHandlers();

    /**
     * whether its base model can be replaced by controller when it is formed.
     */
    default boolean replacePartModelWhenFormed() {
        return true;
    }

    /**
     * get part's Appearance. same as IBlockExtension.getAppearance() / IFabricBlock.getAppearance()
     */
    @Nullable
    default BlockState getFormedAppearance(BlockState sourceState, BlockPos sourcePos, Direction side) {
        for (MultiblockControllerMachine controller : getControllers()) {
            var appearance = controller.getPartAppearance(this, side, sourceState, sourcePos);
            if (appearance != null) return appearance;
        }
        return null;
    }

    /**
     * Called per tick in {@link RecipeLogic#handleRecipeWorking()}
     */
    default boolean onWorking(IWorkableMultiController controller) {
        return true;
    }

    /**
     * Called per tick in {@link RecipeLogic#handleRecipeWorking()}
     */
    default boolean onWaiting(IWorkableMultiController controller) {
        return true;
    }

    /**
     * Called in {@link WorkableMultiblockMachine#setWorkingEnabled(boolean)}
     */
    default boolean onPaused(IWorkableMultiController controller) {
        return true;
    }

    /**
     * Called in {@link RecipeLogic#onRecipeFinish()} before outputs are produced
     */
    default boolean afterWorking(IWorkableMultiController controller) {
        return true;
    }

    /**
     * Called in {@link RecipeLogic#setupRecipe(GTRecipe)}
     */
    @Nullable
    default Component beforeWorking(IWorkableMultiController controller) {
        return null;
    }

    /**
     * Override it to modify recipe on the fly e.g. applying overclock, change chance, etc
     *
     * @param recipe recipe from detected from GTRecipeType
     * @return modified recipe.
     *         null -- this recipe is unavailable
     */
    default GTRecipe modifyRecipe(GTRecipe recipe) {
        return recipe;
    }

    /**
     * Add text to the multiblock's screen.
     *
     * @param textList the text list to add to.
     */
    default void addMultiText(List<Component> textList) {}

    /**
     * Attaches warnings owned by this part to an LDLib2 controller page.
     *
     * @param controller    controller whose page exposes the warning
     * @param tooltipsPanel tooltip panel owned by the current Fancy opening
     */
    default void attachLDLib2FancyTooltipsToController(MultiblockControllerMachine controller,
                                                       LDLib2FancyTooltipsPanelElement tooltipsPanel) {}
}
