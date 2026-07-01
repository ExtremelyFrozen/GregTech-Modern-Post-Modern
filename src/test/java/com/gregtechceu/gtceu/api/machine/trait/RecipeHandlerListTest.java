package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class RecipeHandlerListTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RecipeHandlerList")
    public static void bothHandlersAreVisibleFromDirectionalHolderQueries(GameTestHelper helper) {
        TestHolderImpl holder = new TestHolderImpl();
        RecipeHandlerList bothHandlerList = RecipeHandlerList.of(IO.BOTH, new MutatingItemHandler(IO.BOTH, false));

        holder.addHandlerList(bothHandlerList);

        helper.assertTrue(holder.getCapabilitiesForIO(IO.IN).contains(bothHandlerList),
                "IO.BOTH handler list was not visible from IO.IN query");
        helper.assertTrue(holder.getCapabilitiesForIO(IO.OUT).contains(bothHandlerList),
                "IO.BOTH handler list was not visible from IO.OUT query");
        helper.assertTrue(holder.getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).size() == 1,
                "IO.BOTH item handler was not visible from flat IO.IN query");
        helper.assertTrue(holder.getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).size() == 1,
                "IO.BOTH item handler was not visible from flat IO.OUT query");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RecipeHandlerList")
    public static void simulatedHandlingDoesNotMutateSourceContents(GameTestHelper helper) {
        RecipeHandlerList handlerList = RecipeHandlerList.of(IO.IN, new MutatingItemHandler(IO.IN, true));
        Map<RecipeCapability<?>, List<Object>> contents = new Reference2ObjectOpenHashMap<>();
        contents.put(ItemRecipeCapability.CAP, new ArrayList<>(List.of(testIngredient(2))));

        Map<RecipeCapability<?>, List<Object>> remaining = handlerList.handleRecipe(IO.IN, dummyRecipe(), contents,
                true);

        helper.assertTrue(remaining.isEmpty(), "simulated handling should consume the copied input list");
        helper.assertTrue(contents.get(ItemRecipeCapability.CAP).size() == 1,
                "simulated handling removed the original capability entry");
        SizedIngredient original = (SizedIngredient) contents.get(ItemRecipeCapability.CAP).getFirst();
        helper.assertTrue(original.count() == 2, "simulated handling mutated the original content list");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RecipeHandlerList")
    public static void distinctStateSelectsMatchingHandlerGroup(GameTestHelper helper) {
        RecipeHandlerList handlerList = RecipeHandlerList.of(IO.IN, new MutatingItemHandler(IO.IN, false));

        handlerList.setDistinct(true);
        helper.assertTrue(handlerList.getGroup() == RecipeHandlerGroupDistinctness.BUS_DISTINCT,
                "distinct handler list did not use BUS_DISTINCT group");

        handlerList.setDistinct(false);
        helper.assertTrue(handlerList.getGroup().equals(RecipeHandlerGroupColor.UNDYED),
                "non-distinct handler list did not return to color group");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RecipeHandlerList")
    public static void itemAndFluidBothHandlersSupportDirectionalRecipeIO(GameTestHelper helper) {
        NotifiableItemStackHandler items = new NotifiableItemStackHandler(1, IO.BOTH, IO.BOTH);
        items.storage.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 1));
        List<SizedIngredient> remainingItems = items.handleRecipeInner(IO.IN, dummyRecipe(),
                new ArrayList<>(List.of(testIngredient(1))), false);
        helper.assertTrue(remainingItems.isEmpty(), "IO.BOTH item handler did not support recipe input");
        helper.assertTrue(items.storage.getStackInSlot(0).isEmpty(), "IO.BOTH item handler did not consume input");

        NotifiableFluidTank fluids = new NotifiableFluidTank(1, 1000, IO.BOTH, IO.BOTH);
        fluids.storages[0].setFluid(new FluidStack(Fluids.WATER, 1000));
        List<SizedFluidIngredient> remainingFluids = fluids.handleRecipeInner(IO.IN, dummyRecipe(),
                new ArrayList<>(List.of(SizedFluidIngredient.of(Fluids.WATER, 250))), false);
        helper.assertTrue(remainingFluids.isEmpty(), "IO.BOTH fluid handler did not support recipe input");
        helper.assertTrue(fluids.storages[0].getFluidAmount() == 750,
                "IO.BOTH fluid handler did not consume input");
        helper.succeed();
    }

    private static GTRecipe dummyRecipe() {
        return GTRecipeBuilder.ofRaw().build();
    }

    private static SizedIngredient testIngredient(int count) {
        return new SizedIngredient(Ingredient.of(Items.COBBLESTONE), count);
    }

    private static final class TestHolderImpl implements IRecipeCapabilityHolder {

        private final Map<IO, List<RecipeHandlerList>> capabilityProxy = new Reference2ObjectOpenHashMap<>();
        private final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> capabilitiesFlat = new Reference2ObjectOpenHashMap<>();

        @Override
        public @NotNull Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy() {
            return capabilityProxy;
        }

        @Override
        public @NotNull Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat() {
            return capabilitiesFlat;
        }
    }

    private static final class MutatingItemHandler implements IRecipeHandler<SizedIngredient> {

        private final IO handlerIO;
        private final boolean consumeAll;

        private MutatingItemHandler(IO handlerIO, boolean consumeAll) {
            this.handlerIO = handlerIO;
            this.consumeAll = consumeAll;
        }

        @Override
        public @NotNull List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left,
                                                                boolean simulate) {
            return left;
        }

        @Override
        @SuppressWarnings("unchecked")
        public @NotNull List<SizedIngredient> handleRecipe(IO io, GTRecipe recipe, List<?> left, boolean simulate) {
            if (!handlerIO.support(io)) return new ArrayList<>((List<SizedIngredient>) left);
            List<SizedIngredient> contents = (List<SizedIngredient>) left;
            if (consumeAll) {
                contents.clear();
            }
            return contents;
        }

        @Override
        public @NotNull List<Object> getContents() {
            return List.of();
        }

        @Override
        public double getTotalContentAmount() {
            return 0;
        }

        @Override
        public RecipeCapability<SizedIngredient> getCapability() {
            return ItemRecipeCapability.CAP;
        }
    }
}
