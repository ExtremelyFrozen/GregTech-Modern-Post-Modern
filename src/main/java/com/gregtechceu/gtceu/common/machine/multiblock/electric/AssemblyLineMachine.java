package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerGroup;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerGroupColor;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerGroupDistinctness;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.api.recipe.RecipeHelper.addToRecipeHandlerMap;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class AssemblyLineMachine extends WorkableElectricMultiblockMachine {

    @Accessors(fluent = true)
    @Getter
    @SaveField
    protected boolean allowCircuitSlots;
    private @Nullable RecipeHandlerGroup lastRecipeGroup;

    public AssemblyLineMachine(BlockEntityCreationInfo info, boolean allowCircuitSlots) {
        super(info, new AsslineRecipeLogic());
        this.allowCircuitSlots = allowCircuitSlots;
    }

    public AssemblyLineMachine(BlockEntityCreationInfo info) {
        this(info, false);
    }

    public static Comparator<IMultiPart> partSorter(MultiblockControllerMachine mc) {
        return Comparator.comparing(p -> p.self().getBlockPos(),
                RelativeDirection.RIGHT.getSorter(mc.getFrontFacing(), mc.getUpwardsFacing(), mc.isFlipped()));
    }

    private ActionResult checkItemInputs(GTRecipe recipe, boolean isTick) {
        var itemInputs = getItemInputs(recipe, isTick);
        if (itemInputs.isEmpty()) return ActionResult.SUCCESS;
        return checkOrderedContents(recipe, itemInputs, ItemRecipeCapability.CAP);
    }

    private ActionResult consumeItemContents(GTRecipe recipe, boolean isTick) {
        return consumeOrderedContents(recipe, getItemInputs(recipe, isTick), ItemRecipeCapability.CAP,
                "Assline.consumeItemContents");
    }

    private ActionResult checkFluidInputs(GTRecipe recipe, boolean isTick) {
        var fluidInputs = getFluidInputs(recipe, isTick);
        if (fluidInputs.isEmpty()) return ActionResult.SUCCESS;
        return checkOrderedContents(recipe, fluidInputs, FluidRecipeCapability.CAP);
    }

    private ActionResult consumeFluidContents(GTRecipe recipe, boolean isTick) {
        return consumeOrderedContents(recipe, getFluidInputs(recipe, isTick), FluidRecipeCapability.CAP,
                "Assline.consumeFluidContents");
    }

    private ActionResult consumeAll(GTRecipe recipe, boolean isTick,
                                    Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches) {
        RecipeHandlerGroup group = getLastRecipeGroup();
        GTRecipe copyWithItems = recipe.copy();
        copyWithItems.inputs.clear();
        copyWithItems.tickInputs.clear();

        GTRecipe copyWithFluids = recipe.copy();
        copyWithFluids.inputs.clear();
        copyWithFluids.tickInputs.clear();

        GTRecipe copyWithoutItemsFluids = recipe.copy();
        copyWithoutItemsFluids.inputs.clear();
        copyWithoutItemsFluids.tickInputs.clear();

        for (var entry : recipe.inputs.entrySet()) {
            if (entry.getKey().equals(FluidRecipeCapability.CAP)) {
                copyWithFluids.inputs.put(entry.getKey(), entry.getValue());
            } else if (entry.getKey().equals(ItemRecipeCapability.CAP)) {
                copyWithItems.inputs.put(entry.getKey(), entry.getValue());
            } else {
                copyWithoutItemsFluids.inputs.put(entry.getKey(), entry.getValue());
            }
        }
        for (var entry : recipe.tickInputs.entrySet()) {
            if (entry.getKey().equals(FluidRecipeCapability.CAP)) {
                copyWithFluids.tickInputs.put(entry.getKey(), entry.getValue());
            } else if (entry.getKey().equals(ItemRecipeCapability.CAP)) {
                copyWithItems.tickInputs.put(entry.getKey(), entry.getValue());
            } else {
                copyWithoutItemsFluids.tickInputs.put(entry.getKey(), entry.getValue());
            }
        }
        var config = ConfigHolder.INSTANCE.machines;
        ActionResult result;
        if (config.orderedAssemblyLineItems) {
            result = consumeItemContents(copyWithItems, isTick);
        } else {
            result = isTick ?
                    RecipeHelper.handleTickRecipeIO(this, copyWithItems, IO.IN, chanceCaches, group) :
                    RecipeHelper.handleRecipeIO(this, copyWithItems, IO.IN, chanceCaches, group);
        }
        if (!result.isSuccess()) return result;

        if (config.orderedAssemblyLineFluids) {
            result = consumeFluidContents(copyWithFluids, isTick);
        } else {
            result = isTick ?
                    RecipeHelper.handleTickRecipeIO(this, copyWithFluids, IO.IN, chanceCaches, group) :
                    RecipeHelper.handleRecipeIO(this, copyWithFluids, IO.IN, chanceCaches, group);
        }
        if (!result.isSuccess()) return result;

        return isTick ?
                RecipeHelper.handleTickRecipeIO(this, copyWithoutItemsFluids, IO.IN, chanceCaches, group) :
                RecipeHelper.handleRecipeIO(this, copyWithoutItemsFluids, IO.IN, chanceCaches, group);
    }

    private List<SizedIngredient> getItemInputs(GTRecipe recipe, boolean isTick) {
        return (isTick ? recipe.tickInputs : recipe.inputs)
                .getOrDefault(ItemRecipeCapability.CAP, Collections.emptyList())
                .stream()
                .map(content -> ItemRecipeCapability.CAP.of(content.content))
                .toList();
    }

    private List<SizedFluidIngredient> getFluidInputs(GTRecipe recipe, boolean isTick) {
        return (isTick ? recipe.tickInputs : recipe.inputs)
                .getOrDefault(FluidRecipeCapability.CAP, Collections.emptyList())
                .stream()
                .map(content -> FluidRecipeCapability.CAP.of(content.content))
                .toList();
    }

    private Map<RecipeHandlerGroup, List<RecipeHandlerList>> getOrderedHandlerGroups(RecipeCapability<?> capability) {
        Set<RecipeHandlerList> available = Collections.newSetFromMap(new IdentityHashMap<>());
        available.addAll(getCapabilitiesForIO(IO.IN));

        Set<RecipeHandlerList> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<RecipeHandlerGroup, List<RecipeHandlerList>> handlerGroups = new LinkedHashMap<>();
        List<IMultiPart> orderedParts = new ArrayList<>(getParts());
        orderedParts.sort(partSorter(this));
        for (IMultiPart part : orderedParts) {
            if (!isOrderedInputPart(part, capability)) continue;
            for (RecipeHandlerList handlerList : part.getRecipeHandlers()) {
                addOrderedHandlerGroup(capability, available, seen, handlerGroups, handlerList);
            }
        }
        return handlerGroups;
    }

    private boolean isOrderedInputPart(IMultiPart part, RecipeCapability<?> capability) {
        if (capability.equals(ItemRecipeCapability.CAP)) {
            return part instanceof ItemBusPartMachine;
        }
        if (capability.equals(FluidRecipeCapability.CAP)) {
            return part instanceof FluidHatchPartMachine;
        }
        return false;
    }

    private void addOrderedHandlerGroup(RecipeCapability<?> capability, Set<RecipeHandlerList> available,
                                        Set<RecipeHandlerList> seen,
                                        Map<RecipeHandlerGroup, List<RecipeHandlerList>> handlerGroups,
                                        RecipeHandlerList handlerList) {
        if (!available.contains(handlerList) || !seen.add(handlerList)) return;
        if (!handlerList.hasCapability(capability)) return;
        addToRecipeHandlerMap(handlerList.getGroup(), handlerList, handlerGroups);
    }

    private OrderedHandlers findOrderedHandlers(GTRecipe recipe, List<?> inputs, RecipeCapability<?> capability) {
        Map<RecipeHandlerGroup, List<RecipeHandlerList>> handlerGroups = getOrderedHandlerGroups(capability);
        for (RecipeHandlerGroup group : getOrderedCandidateGroups(recipe, handlerGroups)) {
            if (lastRecipeGroup != null && !lastRecipeGroup.equals(group)) continue;
            List<IRecipeHandler<?>> handlers = getOrderedHandlers(handlerGroups, group, capability);
            if (!canHandleOrderedContents(recipe, inputs, handlers)) continue;
            lastRecipeGroup = group;
            updateRecipeGroupColor(recipe, group);
            return new OrderedHandlers(handlers);
        }
        return OrderedHandlers.EMPTY;
    }

    private List<RecipeHandlerGroup> getOrderedCandidateGroups(GTRecipe recipe,
                                                               Map<RecipeHandlerGroup, List<RecipeHandlerList>> groups) {
        List<RecipeHandlerGroup> candidates = new ArrayList<>();
        if (recipe.groupColor != -1) {
            addCandidate(candidates, groups, new RecipeHandlerGroupColor(recipe.groupColor));
            addCandidate(candidates, groups, RecipeHandlerGroupColor.UNDYED);
            addCandidate(candidates, groups, RecipeHandlerGroupDistinctness.BYPASS_DISTINCT);
            return candidates;
        }

        addCandidate(candidates, groups, RecipeHandlerGroupDistinctness.BUS_DISTINCT);
        for (RecipeHandlerGroup group : groups.keySet()) {
            if (group == RecipeHandlerGroupDistinctness.BUS_DISTINCT ||
                    group == RecipeHandlerGroupDistinctness.BYPASS_DISTINCT) {
                continue;
            }
            addCandidate(candidates, groups, group);
        }
        addCandidate(candidates, groups, RecipeHandlerGroupDistinctness.BYPASS_DISTINCT);
        return candidates;
    }

    private void addCandidate(List<RecipeHandlerGroup> candidates,
                              Map<RecipeHandlerGroup, List<RecipeHandlerList>> groups,
                              RecipeHandlerGroup group) {
        if (groups.containsKey(group) && !candidates.contains(group)) {
            candidates.add(group);
        }
    }

    private List<IRecipeHandler<?>> getOrderedHandlers(Map<RecipeHandlerGroup, List<RecipeHandlerList>> groups,
                                                       RecipeHandlerGroup group,
                                                       RecipeCapability<?> capability) {
        List<IRecipeHandler<?>> handlers = new ArrayList<>();
        addOrderedHandlers(handlers, groups.getOrDefault(group, Collections.emptyList()), capability);
        if (group != RecipeHandlerGroupDistinctness.BYPASS_DISTINCT) {
            addOrderedHandlers(handlers,
                    groups.getOrDefault(RecipeHandlerGroupDistinctness.BYPASS_DISTINCT, Collections.emptyList()),
                    capability);
        }
        return handlers;
    }

    private void addOrderedHandlers(List<IRecipeHandler<?>> handlers, List<RecipeHandlerList> handlerLists,
                                    RecipeCapability<?> capability) {
        for (RecipeHandlerList handlerList : handlerLists) {
            for (IRecipeHandler<?> handler : handlerList.getCapability(capability)) {
                if (handler.shouldSearchContent()) {
                    handlers.add(handler);
                }
            }
        }
    }

    private void updateRecipeGroupColor(GTRecipe recipe, RecipeHandlerGroup group) {
        if (group instanceof RecipeHandlerGroupColor coloredGroup && coloredGroup.color() != -1) {
            recipe.groupColor = coloredGroup.color();
        }
    }

    private boolean canHandleOrderedContents(GTRecipe recipe, List<?> inputs, List<IRecipeHandler<?>> handlers) {
        if (handlers.size() < inputs.size()) return false;
        for (int i = 0; i < inputs.size(); i++) {
            if (!handlers.get(i).handleRecipe(IO.IN, recipe, List.of(inputs.get(i)), true).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ActionResult checkOrderedContents(GTRecipe recipe, List<?> inputs, RecipeCapability<?> capability) {
        if (!findOrderedHandlers(recipe, inputs, capability).found()) {
            return failOrderedInput(capability);
        }
        return ActionResult.SUCCESS;
    }

    private ActionResult consumeOrderedContents(GTRecipe recipe, List<?> inputs, RecipeCapability<?> capability,
                                                String context) {
        if (inputs.isEmpty()) return ActionResult.SUCCESS;
        OrderedHandlers orderedHandlers = findOrderedHandlers(recipe, inputs, capability);
        if (!orderedHandlers.found()) {
            return failOrderedInput(capability);
        }

        List<IRecipeHandler<?>> handlers = orderedHandlers.handlers();
        for (int i = 0; i < inputs.size(); i++) {
            if (!handlers.get(i).handleRecipe(IO.IN, recipe, List.of(inputs.get(i)), false).isEmpty()) {
                GTCEu.LOGGER.error(
                        "Recipe in {} was true when simulating, but false when consuming.", context);
                return failOrderedInput(capability);
            }
        }
        return ActionResult.SUCCESS;
    }

    private record OrderedHandlers(List<IRecipeHandler<?>> handlers) {

        private static final OrderedHandlers EMPTY = new OrderedHandlers(Collections.emptyList());

        private boolean found() {
            return this != EMPTY;
        }
    }

    private ActionResult failOrderedInput(RecipeCapability<?> capability) {
        return ActionResult.fail(Component.translatable("gtpm.recipe_logic.insufficient_in")
                .append(": ")
                .append(capability.getName()), capability, IO.IN);
    }

    private RecipeHandlerGroup getLastRecipeGroup() {
        if (lastRecipeGroup == null) {
            throw new IllegalStateException("Assembly line recipe group is missing while handling recipe IO.");
        }
        return lastRecipeGroup;
    }

    private static class AsslineRecipeLogic extends RecipeLogic {

        public AsslineRecipeLogic() {
            super();
        }

        @Override
        public AssemblyLineMachine getMachine() {
            return (AssemblyLineMachine) super.getMachine();
        }

        @Override
        protected List<Class<?>> validMachineClasses() {
            return List.of(AssemblyLineMachine.class);
        }

        @Override
        protected ActionResult handleRecipeIO(GTRecipe recipe, IO io) {
            if (io.equals(IO.IN)) {
                return getMachine().consumeAll(recipe, false, this.getChanceCaches());
            }
            return RecipeHelper.handleRecipeIO(getMachine(), recipe, io, this.chanceCaches);
        }

        @Override
        protected ActionResult handleTickRecipeIO(GTRecipe recipe, IO io) {
            if (io.equals(IO.IN)) {
                return getMachine().consumeAll(recipe, true, this.getChanceCaches());
            }
            return RecipeHelper.handleTickRecipeIO(getMachine(), recipe, io, this.chanceCaches);
        }

        @Override
        protected ActionResult matchRecipe(GTRecipe recipe) {
            getMachine().lastRecipeGroup = null;
            var normalMatch = RecipeHelper.matchContentsWithGroup(getMachine(), recipe, null);
            if (!normalMatch.result().isSuccess()) return normalMatch.result();
            RecipeHandlerGroup normalGroup = normalMatch.selectedGroup();

            var config = ConfigHolder.INSTANCE.machines;
            if (config.orderedAssemblyLineItems) {
                ActionResult itemMatch = getMachine().checkItemInputs(recipe, false);
                if (!itemMatch.isSuccess()) return itemMatch;
                itemMatch = getMachine().checkItemInputs(recipe, true);
                if (!itemMatch.isSuccess()) return itemMatch;
            }
            if (config.orderedAssemblyLineFluids) {
                ActionResult fluidMatch = getMachine().checkFluidInputs(recipe, false);
                if (!fluidMatch.isSuccess()) return fluidMatch;
                fluidMatch = getMachine().checkFluidInputs(recipe, true);
                if (!fluidMatch.isSuccess()) return fluidMatch;
            }

            if (getMachine().lastRecipeGroup == null) {
                getMachine().lastRecipeGroup = normalGroup;
            }
            return ActionResult.SUCCESS;
        }

        @Override
        public void onRecipeHandlerChanged() {
            super.onRecipeHandlerChanged();
            findAndHandleRecipeFromHandlerChange();
        }

        @Override
        public void resetRecipeLogic() {
            super.resetRecipeLogic();
            getMachine().lastRecipeGroup = null;
        }

        @Override
        public void onRecipeFinish() {
            super.onRecipeFinish();
            if (!isWorking()) {
                getMachine().lastRecipeGroup = null;
            }
        }
    }
}
