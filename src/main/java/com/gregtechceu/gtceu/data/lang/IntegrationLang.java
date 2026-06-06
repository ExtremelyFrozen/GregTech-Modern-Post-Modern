package com.gregtechceu.gtceu.data.lang;

import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.common.data.GTBedrockFluids;
import com.gregtechceu.gtceu.common.data.GTOreVeins;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class IntegrationLang {

    public static void init(RegistrateLangProvider provider) {
        initRecipeViewerLang(provider);
        initWailaLikeLang(provider);
        initMinimapLang(provider);
        initOwnershipLang(provider);
    }

    /** JEI, REI, EMI */
    private static void initRecipeViewerLang(RegistrateLangProvider provider) {
        provider.add("gtpm.jei.multiblock_info", "Multiblock Info");
        provider.add("gtpm.jei.ore_processing_diagram", "Ore Processing Diagram");
        provider.add("gtpm.jei.ore_vein_diagram", "Ore Vein Diagram");
        provider.add("gtpm.jei.programmed_circuit", "Programmed Circuit Page");
        provider.add("gtpm.jei.bedrock_fluid_diagram", "Bedrock Fluid Diagram");
        provider.add("gtpm.jei.bedrock_ore_diagram", "Bedrock Ore Diagram");
        provider.add("gtpm.jei.ore_vein_diagram.chance", "§eChance: %s§r");
        provider.add("gtpm.jei.ore_vein_diagram.spawn_range", "Spawn Range:");
        provider.add("gtpm.jei.ore_vein_diagram.weight", "Weight: %s");
        provider.add("gtpm.jei.ore_vein_diagram.dimensions", "Dimensions:");
        for (ResourceKey<GTOreDefinition> key : GTOreVeins.ALL_KEYS) {
            ResourceLocation id = key.location();
            String name = id.getPath();
            provider.add(id.toLanguageKey("ore_vein"), RegistrateLangProvider.toEnglishName(name));
        }
        for (ResourceKey<BedrockFluidDefinition> key : GTBedrockFluids.ALL_KEYS) {
            ResourceLocation id = key.location();
            String name = id.getPath();
            provider.add(id.toLanguageKey("bedrock_fluid"), RegistrateLangProvider.toEnglishName(name));
        }

        provider.add("gtpm.rei.group.potion_fluids", "Potion Fluids");
    }

    /** Jade */
    private static void initWailaLikeLang(RegistrateLangProvider provider) {
        provider.add("gtpm.top.working_disabled", "Working Disabled");
        provider.add("gtpm.top.energy_consumption", "Using");
        provider.add("gtpm.top.energy_production", "Producing");
        provider.add("gtpm.top.transform_up", "§cStep Up§r %s");
        provider.add("gtpm.top.transform_down", "§aStep Down§r %s");
        provider.add("gtpm.top.transform_input", "§6Input:§r %s");
        provider.add("gtpm.top.transform_output", "§9Output:§r %s");
        provider.add("gtpm.top.convert_eu", "Converting §eEU§r -> §cFE§r");
        provider.add("gtpm.top.convert_fe", "Converting §cFE§r -> §eEU§r");
        provider.add("gtpm.top.fuel_min_consume", "Needs");
        provider.add("gtpm.top.fuel_none", "No fuel");
        provider.add("gtpm.top.invalid_structure", "Structure Incomplete");
        provider.add("gtpm.top.valid_structure", "Structure Formed");
        provider.add("gtpm.top.obstructed_structure", "Structure Obstructed");
        provider.add("gtpm.top.maintenance_fixed", "Maintenance Fine");
        provider.add("gtpm.top.maintenance_broken", "Needs Maintenance");
        provider.add("gtpm.top.maintenance.wrench", "Pipe is loose");
        provider.add("gtpm.top.maintenance.screwdriver", "Screws are loose");
        provider.add("gtpm.top.maintenance.soft_mallet", "Something is stuck");
        provider.add("gtpm.top.maintenance.hard_hammer", "Plating is dented");
        provider.add("gtpm.top.maintenance.wire_cutter", "Wires burned out");
        provider.add("gtpm.top.maintenance.crowbar", "That doesn't belong there");
        provider.add("gtpm.top.primitive_pump_production", "Production: %s mB/s");
        provider.add("gtpm.top.filter.label", "Filter:");
        provider.add("gtpm.top.link_cover.color", "Color:");
        provider.add("gtpm.top.mode.export", "Exporting");
        provider.add("gtpm.top.mode.import", "Importing");
        provider.add("gtpm.top.unit.items", "Items");
        provider.add("gtpm.top.unit.fluid_milibuckets", "L");
        provider.add("gtpm.top.unit.fluid_buckets", "kL");
        provider.add("gtpm.top.recipe_output", "Recipe Outputs:");
        provider.add("gtpm.top.item_auto_output", "Item Output: %s");
        provider.add("gtpm.top.fluid_auto_output", "Fluid Output: %s");
        provider.add("gtpm.top.auto_output", "Auto Output");
        provider.add("gtpm.top.allow_output_input", "Allow Input");
        provider.add("gtpm.top.cable_voltage", "Voltage: ");
        provider.add("gtpm.top.cable_amperage", "Amperage: ");
        provider.add("gtpm.top.cable_overloaded", "§4OVERHEATING: %s%%§r");
        provider.add("gtpm.top.exhaust_vent_direction", "Exhaust Vent: %s");
        provider.add("gtpm.top.exhaust_vent_blocked", "Blocked");
        provider.add("gtpm.top.machine_mode", "Machine Mode: ");
        provider.add("gtpm.top.stained", "Colored: %s");
        provider.add("gtpm.top.buffer_not_bound", "Buffer Not Currently Bound");
        provider.add("gtpm.top.buffer_bound_pos", "Bound To - X: %s, Y: %s, Z: %s");
        provider.add("gtpm.top.proxies_bound", "Buffer Proxies Bound: %s");

        provider.add("gtpm.jade.energy_stored", "%d / %d EU");
        provider.add("gtpm.jade.progress_computation", "%s / %s CWU");
        provider.add("gtpm.jade.progress_sec", "%s / %s s");
        provider.add("gtpm.jade.progress_tick", "%s / %s t");
        provider.add("gtpm.jade.cleaned_this_second", "Cleaned hazard: %s/s");
        provider.add("gtpm.jade.fluid_use", "%s mB/t");
        provider.add("gtpm.jade.amperage_use", "%s A");
        provider.add("gtpm.jade.at", " @ ");
        provider.add("gtpm.jade.remaining_charge_time", "Until charged: %s");
        provider.add("gtpm.jade.remaining_discharge_time", "Until empty: %s");
        provider.add("gtpm.jade.changes_eu_sec", "%s EU/s");
        provider.add("gtpm.jade.seconds", "%s seconds");
        provider.add("gtpm.jade.minutes", "%s minutes");
        provider.add("gtpm.jade.hours", "%s hours");
        provider.add("gtpm.jade.days", "%s days");
        provider.add("gtpm.jade.years", "%s years");

        provider.add("gtpm.top.energy_stored", " / %d EU");
        provider.add("gtpm.top.progress_computation", " / %s CWU");
        provider.add("gtpm.top.progress_sec", " / %s s");
        provider.add("gtpm.top.progress_tick", " / %s t");

        provider.add("gtpm.top.ldp_endpoint.is_formed", "§aPipeline Formed§r");
        provider.add("gtpm.top.ldp_endpoint.not_formed", "§cPipeline Incomplete§r");
        provider.add("gtpm.top.ldp_endpoint.io_type", "IO Type: %s");
        provider.add("gtpm.top.ldp_endpoint.output_direction", "Output Direction: %s");
    }

    private static void initMinimapLang(RegistrateLangProvider provider) {
        provider.add("gtpm.minimap.ore_vein.depleted", "Depleted");

        provider.add("message.gtpm.new_veins.amount", "Prospected %d new veins!");
        provider.add("message.gtpm.new_veins.name", "Prospected %s!");
        provider.add("button.gtpm.mark_as_depleted.name", "Mark as Depleted");
        provider.add("button.gtpm.toggle_waypoint.name", "Toggle Waypoint");

        provider.add("gtpm.journeymap.options.layers", "Prospection layers");
        provider.add("gtpm.journeymap.options.layers.ore_veins", "Show Ore Veins");
        provider.add("gtpm.journeymap.options.layers.bedrock_fluids", "Show Bedrock Fluid Veins");
        provider.add("gtpm.journeymap.options.layers.hide_depleted", "Hide Depleted Veins");
    }

    private static void initOwnershipLang(RegistrateLangProvider provider) {
        provider.add("gtpm.ownership.name.player", "Player");
        provider.add("gtpm.ownership.name.ftb", "FTB Teams");
        provider.add("gtpm.ownership.name.argonauts", "Argonauts Guild");
    }
}
