package com.gregtechceu.gtceu.integration.kjs;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeCategories;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.kjs.helpers.GTResourceLocation;
import com.gregtechceu.gtceu.integration.kjs.recipe.GTRecipeSchema;
import com.gregtechceu.gtceu.integration.kjs.recipe.GTShapedRecipeSchema;
import com.gregtechceu.gtceu.integration.kjs.recipe.KJSHelpers;
import com.gregtechceu.gtceu.integration.kjs.recipe.components.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeFactoryRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import dev.latvian.mods.rhino.Wrapper;

public class GregTechKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerRecipeSchemas(RecipeSchemaRegistry event) {
        for (var id : BuiltInRegistries.RECIPE_TYPE.keySet()) {
            RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(id);
            if (!(type instanceof GTRecipeType)) continue;
            event.register(id, GTRecipeSchema.SCHEMA);
        }
        event.namespace(GTCEu.MOD_ID).register("shaped", GTShapedRecipeSchema.SCHEMA);
    }

    @Override
    public void registerRecipeFactories(RecipeFactoryRegistry registry) {
        registry.register(GTRecipeSchema.RECIPE_FACTORY);
        registry.register(GTShapedRecipeSchema.RECIPE_FACTORY);
    }

    @Override
    public void registerRecipeComponents(RecipeComponentTypeRegistry registry) {
        registry.register(NbtTagComponent.NBT_TAG);
        registry.register(RecipeConditionComponent.RECIPE_CONDITION);
        registry.register(ResourceLocationComponent.RESOURCE_LOCATION);
        registry.register(RecipeCapabilityComponent.RECIPE_CAPABILITY);
        registry.register(GTRecipeComponents.CHANCE_LOGIC.type());
        registry.register(CapabilityMapComponent.CAPABILITY_MAP);

        registry.register(GTRecipeComponents.ITEM.type());
        registry.register(GTRecipeComponents.FLUID.type());
        registry.register(GTRecipeComponents.EU.type());
    }

    @Override
    public void registerBindings(BindingRegistry event) {
        event.add("GTRecipeTypes", GTRecipeTypes.class);
        event.add("GTRecipeCategories", GTRecipeCategories.class);
        event.add("RecipeCapability", RecipeCapability.class);
        event.add("ChanceLogic", ChanceLogic.class);
        event.add("EnergyStack", EnergyStack.class);
        event.add("IOEnergyStack", EnergyStack.WithIO.class);
    }

    @Override
    public void registerTypeWrappers(TypeWrapperRegistry registry) {
        registry.register(GTResourceLocation.class, GTResourceLocation::wrap);
        registry.register(GTRecipeType.class, o -> {
            o = Wrapper.unwrapped(o);
            if (o instanceof GTRecipeType recipeType) return recipeType;
            if (o instanceof CharSequence chars) return GTRecipeTypes.get(chars.toString());
            return null;
        });
        registry.register(GTRecipeCategory.class, o -> {
            o = Wrapper.unwrapped(o);
            if (o instanceof GTRecipeCategory recipeCategory) return recipeCategory;
            if (o instanceof CharSequence chars) return GTRecipeCategories.get(chars.toString());
            return null;
        });
        registry.register(RecipeCapability.class, o -> {
            o = Wrapper.unwrapped(o);
            if (o instanceof RecipeCapability<?> capability) return capability;
            if (o instanceof ResourceLocation id) return GTRegistries.RECIPE_CAPABILITIES.get(id);
            GTResourceLocation wrapper = GTResourceLocation.wrap(o);
            if (wrapper == null) return null;
            return GTRegistries.RECIPE_CAPABILITIES.get(wrapper.wrapped());
        });
        registry.register(ChanceLogic.class, o -> {
            o = Wrapper.unwrapped(o);
            if (o instanceof ChanceLogic capability) return capability;
            if (o instanceof ResourceLocation id) return GTRegistries.CHANCE_LOGICS.get(id);
            GTResourceLocation wrapper = GTResourceLocation.wrap(o);
            if (wrapper == null) return null;
            return GTRegistries.CHANCE_LOGICS.get(wrapper.wrapped());
        });
        registry.register(EnergyStack.class, KJSHelpers::parseEnergyStack);
        registry.register(EnergyStack.WithIO.class, KJSHelpers::parseIOEnergyStack);
    }
}
