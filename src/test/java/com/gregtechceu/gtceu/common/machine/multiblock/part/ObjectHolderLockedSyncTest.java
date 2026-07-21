package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerList;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ObjectHolderLockedSyncTest {

    private static final String BATCH = "ObjectHolderLockedSync";
    private static final ResourceLocation LOCKED_FIELD = SyncFieldData.key("isLocked");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fullAndDeltaSyncLockStateWithoutNoOpDelta(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        ObjectHolderMachine server = createMachine();
        ObjectHolderMachine client = createMachine();

        server.setLocked(true);
        DataComponentMap initial = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertLockedField(helper, initial, true, "initial object-holder sync");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, initial);
        helper.assertTrue(client.isLocked(),
                "initial object-holder sync did not lock the client machine");

        server.setLocked(false);
        DataComponentMap changed = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertOnlyLockedField(helper, changed, false, "object-holder unlock delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, changed);
        helper.assertTrue(!client.isLocked(),
                "object-holder unlock delta did not unlock the client machine");

        server.setLocked(false);
        DataComponentMap unchanged = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        helper.assertTrue(unchanged.isEmpty(),
                "unchanged object-holder lock state produced a redundant client delta");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void recipeInputLocksAndRecipeOutputUnlocksThroughSync(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        ObjectHolderMachine server = createMachine();
        ObjectHolderMachine client = createMachine();
        ItemStack dataStick = GTItems.TOOL_DATA_STICK.asStack();
        ItemStack dataOrb = GTItems.TOOL_DATA_ORB.asStack();
        server.setDataItem(dataStick);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries,
                server.getSyncDataHolder().serializeFullClientSyncComponents(registries));

        handleDataRecipe(helper, server, IO.IN, dataStick, false, "object-holder recipe input");
        helper.assertTrue(server.isLocked(),
                "successful object-holder recipe input did not lock the data slot");
        DataComponentMap inputDelta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertLockedField(helper, inputDelta, true, "object-holder recipe-input delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, inputDelta);
        helper.assertTrue(client.isLocked(),
                "object-holder recipe-input delta did not lock the client machine");

        handleDataRecipe(helper, server, IO.OUT, dataOrb, true, "simulated object-holder recipe output");
        helper.assertTrue(server.isLocked(),
                "simulated object-holder recipe output changed the lock state");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "simulated object-holder recipe output produced a client delta");

        handleDataRecipe(helper, server, IO.OUT, dataOrb, false, "object-holder recipe output");
        helper.assertTrue(!server.isLocked(),
                "successful object-holder recipe output did not unlock the data slot");
        DataComponentMap outputDelta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertLockedField(helper, outputDelta, false, "object-holder recipe-output delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, outputDelta);
        helper.assertTrue(!client.isLocked(),
                "object-holder recipe-output delta did not unlock the client machine");
        helper.succeed();
    }

    private static ObjectHolderMachine createMachine() {
        var definition = GTResearchMachines.OBJECT_HOLDER;
        return new ObjectHolderMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static void handleDataRecipe(GameTestHelper helper, ObjectHolderMachine machine, IO io,
                                         ItemStack stack, boolean simulate, String description) {
        RecipeHandlerList dataHandlers = getDataHandlers(machine);
        Map<RecipeCapability<?>, List<Object>> contents = new HashMap<>();
        List<Object> items = new ArrayList<>();
        items.add(new SizedIngredient(Ingredient.of(stack), stack.getCount()));
        contents.put(ItemRecipeCapability.CAP, items);

        Map<RecipeCapability<?>, List<Object>> remaining = dataHandlers.handleRecipe(
                io, GTRecipeBuilder.ofRaw().build(), contents, simulate);

        helper.assertTrue(remaining.isEmpty(), description + " was not fully handled");
    }

    private static RecipeHandlerList getDataHandlers(ObjectHolderMachine machine) {
        for (RecipeHandlerList handlers : machine.getRecipeHandlers()) {
            if (handlers.getHandlerIO() == IO.BOTH && handlers.hasCapability(ItemRecipeCapability.CAP)) {
                return handlers;
            }
        }
        throw new IllegalStateException("Object holder did not expose its data-item recipe handler.");
    }

    private static void assertOnlyLockedField(GameTestHelper helper, DataComponentMap components,
                                              boolean expected, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(fields != null && fields.fields().size() == 1,
                description + " contained fields other than the lock state");
        assertLockedField(helper, components, expected, description);
    }

    private static void assertLockedField(GameTestHelper helper, DataComponentMap components,
                                          boolean expected, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        JsonElement value = fields == null ? null : fields.get(LOCKED_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected lock state");
    }
}
