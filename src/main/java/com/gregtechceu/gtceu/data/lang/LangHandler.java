package com.gregtechceu.gtceu.data.lang;

import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.data.LanguageProvider;

import com.tterrag.registrate.providers.RegistrateLangProvider;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LangHandler {

    public static void init(RegistrateLangProvider provider) {
        AdvancementLang.init(provider);
        BlockLang.init(provider);
        IntegrationLang.init(provider);
        ItemLang.init(provider);
        MachineLang.init(provider);
        ToolLang.init(provider);
        ConfigurationLang.init(provider);
        RecipeLogicLang.init(provider);

        provider.add("gtpm.gui.editor.tips.citation", "Number of citations");
        provider.add("gtpm.gui.editor.group.recipe_type", "cap");
        provider.add("gtpm.gui.editor.group.preview", "Preview");
        provider.add("gtpm.gui.editor.register.editor.gtpm", "GTPM UI Editor");
        provider.add("gtpm.gui.editor.register.editor.gtpm.rtui", "RecipeType UI Project");
        provider.add("gtpm.gui.editor.register.editor.gtpm.mui", "Machine UI Project");
        provider.add("gtpm.gui.editor.register.editor.gtpm.template_tab", "templates");
        provider.add("gtpm.gui.editor.group.widget.basic", "Basic Widgets");
        provider.add("gtpm.gui.editor.group.widget.group", "Group Widgets");
        provider.add("gtpm.gui.editor.group.widget.container", "Container Widgets");
        provider.add("gtpm.gui.editor.group.widget.custom", "Custom Widgets");
        provider.add("gtpm.gui.editor.group.widget.gtm_container", "GTM Container Widgets");
        provider.add("gtpm.gui.editor.register.widget.container.gtm_item_slot", "GTM Item Slot");
        provider.add("gtpm.gui.editor.register.widget.container.gtm_fluid_slot", "GTM Fluid Slot");
        provider.add("gtpm.gui.editor.register.widget.container.gtm_phantom_item_slot", "GTM Phantom Item Slot");
        provider.add("gtpm.gui.editor.register.widget.container.gtm_phantom_fluid_slot", "GTM Phantom Fluid Slot");

        provider.add("curios.identifier.gtpm_magnet", "GTCEu Magnet");
        // capabilities
        provider.add("recipe.capability.eu.name", "GTCEu Energy");
        provider.add("recipe.capability.fluid.name", "Fluid");
        provider.add("recipe.capability.item.name", "Item");
        multiLang(provider, "gtpm.oc.tooltip", "Min: %s", "Left click to increase the OC",
                "Right click to decrease the OC", "Middle click to reset the OC",
                "Hold Shift to change by Perfect OC");

        provider.add("recipe.condition.thunder.tooltip", "Thunder Level: %d");
        provider.add("recipe.condition.rain.tooltip", "Rain Level: %d");
        provider.add("recipe.condition.dimension.tooltip", "Dimension: %s");
        provider.add("recipe.condition.dimension_marker.tooltip", "Dimension:");
        provider.add("recipe.condition.biome.tooltip", "Biome: %s");
        provider.add("recipe.condition.pos_y.tooltip", "Y Level: %d <= Y <= %d");
        provider.add("recipe.condition.steam_vent.tooltip", "Clean steam vent");
        provider.add("recipe.condition.adjacent_fluid.tooltip", "Fluid blocks around");
        provider.add("recipe.condition.adjacent_block.tooltip", "Blocks around");
        provider.add("recipe.condition.eu_to_start.tooltip", "EU to Start: %d%s");
        provider.add("recipe.condition.daytime.day.tooltip", "Requires day time to work");
        provider.add("recipe.condition.daytime.night.tooltip", "Requires night time to work");
        provider.add("recipe.condition.gamestage.unlocked_stage", "Unlocked at stage: %s");
        provider.add("recipe.condition.gamestage.locked_stage", "Locked at stage: %s");
        provider.add("recipe.condition.quest.completed.tooltip", "Requires %s completed");
        provider.add("recipe.condition.quest.not_completed.tooltip", "Requires %s not completed");

        provider.add("gtpm.io.import", "Import");
        provider.add("gtpm.io.export", "Export");
        provider.add("gtpm.io.both", "Both");
        provider.add("gtpm.io.none", "None");

        provider.add("gtpm.multiblock.page_switcher.io.import", "§2Inputs");
        provider.add("gtpm.multiblock.page_switcher.io.export", "§4Outputs");
        provider.add("gtpm.multiblock.page_switcher.io.both", "§5Combined Inputs + Outputs");

        provider.add("enchantment.disjunction", "Disjunction");

        provider.add("item.invalid.name", "Invalid item");
        provider.add("fluid.empty", "Empty");
        provider.add("gtpm.tooltip.hold_shift", "§7Hold SHIFT for more info");
        provider.add("gtpm.tooltip.hold_ctrl", "§7Hold CTRL for more info");
        provider.add("gtpm.tooltip.fluid_pipe_hold_shift", "§7Hold SHIFT to show Fluid Containment Info");
        provider.add("gtpm.tooltip.tool_fluid_hold_shift",
                "§7Hold SHIFT to show Fluid Containment and Tool Info");
        provider.add("metaitem.generic.fluid_container.tooltip", "%d/%dL %s");
        provider.add("metaitem.generic.electric_item.tooltip", "%d/%d EU - Tier %s");
        provider.add("metaitem.generic.electric_item.stored", "%d/%d EU (%s)");
        provider.add("metaitem.electric.discharge_mode.enabled", "§eDischarge Mode Enabled");
        provider.add("metaitem.electric.discharge_mode.disabled", "§eDischarge Mode Disabled");
        provider.add("metaitem.electric.discharge_mode.tooltip", "Use while sneaking to toggle discharge mode");
        provider.add("metaitem.dust.tooltip.purify", "Right click a Cauldron to get clean Dust");
        provider.add("metaitem.crushed.tooltip.purify", "Right click a Cauldron to get Purified Ore");
        provider.add("metaitem.int_circuit.configuration", "Configuration: %d");

        provider.add("metaitem.machine_configuration.mode", "§aConfiguration Mode:§r %s");
        provider.add("gtpm.mode.fluid", "§9Fluid§r");
        provider.add("gtpm.mode.item", "§6Item§r");
        provider.add("gtpm.mode.both", "§dBoth (Fluid And Item)§r");

        provider.add("gtpm.tool.class.sword", "Sword");
        provider.add("gtpm.tool.class.pickaxe", "Pickaxe");
        provider.add("gtpm.tool.class.shovel", "Shovel");
        provider.add("gtpm.tool.class.axe", "Axe");
        provider.add("gtpm.tool.class.hoe", "Hoe");
        provider.add("gtpm.tool.class.mining_hammer", "Mining Hammer");
        provider.add("gtpm.tool.class.spade", "Spade");
        provider.add("gtpm.tool.class.saw", "Saw");
        provider.add("gtpm.tool.class.hammer", "Hammer");
        provider.add("gtpm.tool.class.mallet", "Soft Mallet");
        provider.add("gtpm.tool.class.wrench", "Wrench");
        provider.add("gtpm.tool.class.file", "File");
        provider.add("gtpm.tool.class.crowbar", "Crowbar");
        provider.add("gtpm.tool.class.screwdriver", "Screwdriver");
        provider.add("gtpm.tool.class.mortar", "Mortar");
        provider.add("gtpm.tool.class.wire_cutter", "Wire Cutter");
        provider.add("gtpm.tool.class.knife", "Knife");
        provider.add("gtpm.tool.class.butchery_knife", "Butchery Knife");
        provider.add("gtpm.tool.class.scythe", "Scythe");
        provider.add("gtpm.tool.class.rolling_pin", "Rolling Pin");
        provider.add("gtpm.tool.class.plunger", "Plunger");
        provider.add("gtpm.tool.class.shears", "Shears");
        provider.add("gtpm.tool.class.drill", "Drill");

        provider.add("argument.medical_condition.id.invalid", "Unknown medical condition '%s'");

        provider.add("command.gtpm.medical_condition.get", "Player %s has these medical conditions:");
        provider.add("command.gtpm.medical_condition.get.empty", "Player %s has no medical conditions.");
        provider.add("command.gtpm.medical_condition.get.element", "Condition %s§r: %s minutes %s seconds");
        provider.add("command.gtpm.medical_condition.get.element.permanent",
                "Condition %s§r: %s minutes %s seconds (permanent)");
        provider.add("command.gtpm.dump_data.success", "Dumped %s resources from registry %s to %s");
        provider.add("command.gtpm.place_vein.failure", "Failed to place vein %s at position %s");
        provider.add("command.gtpm.place_vein.success", "Placed vein %s at position %s");
        provider.add("command.gtpm.share_prospection_data.notification", "%s is sharing prospecting data with you!");
        provider.add("command.gtpm.cape.failure.does_not_exist", "Cape %s does not exist");
        provider.add("command.gtpm.cape.give.failed", "No new capes were unlocked");
        provider.add("command.gtpm.cape.give.success.multiple", "Unlocked %s capes for %s players");
        provider.add("command.gtpm.cape.give.success.single", "Unlocked %s capes for %s");
        provider.add("command.gtpm.cape.take.failed", "No capes could be removed");
        provider.add("command.gtpm.cape.take.success.multiple", "Took %s capes from %s players");
        provider.add("command.gtpm.cape.take.success.single", "Took %s capes from %s");
        provider.add("command.gtpm.cape.use.failed",
                "%s can't use cape %s because they don't have it (or it doesn't exist)!");
        provider.add("command.gtpm.cape.use.success", "%s is now using cape %s");
        provider.add("command.gtpm.cape.use.success.none", "%s is no longer using a cape");

        provider.add("gtpm.medical_condition.description", "§l§cHAZARDOUS §7Hold Shift to show details");
        provider.add("gtpm.medical_condition.description_shift", "§l§cHAZARDOUS:");
        provider.add("gtpm.medical_condition.chemical_burns", "§5Chemical burns");
        provider.add("gtpm.medical_condition.poison", "§2Poisonous");
        provider.add("gtpm.medical_condition.weak_poison", "§aWeakly poisonous");
        provider.add("gtpm.medical_condition.irritant", "§6Irritant");
        provider.add("gtpm.medical_condition.nausea", "§3Nauseating");
        provider.add("gtpm.medical_condition.carcinogen", "§eCarcinogenic");
        provider.add("gtpm.medical_condition.asbestosis", "§dAsbestosis");
        provider.add("gtpm.medical_condition.arsenicosis", "§bArsenicosis");
        provider.add("gtpm.medical_condition.silicosis", "§1Silicosis");
        provider.add("gtpm.medical_condition.berylliosis", "§5Berylliosis");
        provider.add("gtpm.medical_condition.methanol_poisoning", "§6Methanol Poisoning");
        provider.add("gtpm.medical_condition.carbon_monoxide_poisoning", "§7Carbon Monoxide Poisoning");
        provider.add("gtpm.medical_condition.none", "§2Not Dangerous");
        provider.add("gtpm.hazard_trigger.description", "Caused by:");
        provider.add("gtpm.hazard_trigger.protection.description", "Protects from:");
        provider.add("gtpm.hazard_trigger.inhalation", "Inhalation");
        provider.add("gtpm.hazard_trigger.any", "Any contact");

        provider.add("gtpm.hazard_trigger.skin_contact", "Skin contact");
        provider.add("gtpm.hazard_trigger.none", "Nothing");
        provider.add("gtpm.medical_condition.antidote.description", "§aAntidote §7Hold Shift to show details");
        provider.add("gtpm.medical_condition.antidote.description_shift", "§aCures these conditions:");
        provider.add("gtpm.medical_condition.antidote.description.effect_removed",
                "Removes %s%% of current conditions' effects");
        provider.add("gtpm.medical_condition.antidote.description.effect_removed.all",
                "Removes all of current conditions' effects");

        provider.add("gtpm.multiblock.dimension", "§eDimensions: §r%sx%sx%s");

        provider.add("item.gtpm.tool.replace_tool_head", "Craft with a new Tool Head to replace it");
        provider.add("item.gtpm.tool.usable_as", "§8Usable as: §f%s");
        provider.add("item.gtpm.tool.behavior.silk_ice", "§bIce Cutter: §fSilk Harvests Ice");
        provider.add("item.gtpm.tool.behavior.torch_place", "§eSpelunker: §fPlaces Torches on Right-Click");
        provider.add("item.gtpm.tool.behavior.tree_felling", "§4Lumberjack: §fTree Felling");
        provider.add("item.gtpm.tool.behavior.strip_log", "§5Artisan: §fStrips Logs");
        provider.add("item.gtpm.tool.behavior.scrape", "§bPolisher: §fRemoves Oxidation");
        provider.add("item.gtpm.tool.behavior.remove_wax", "§6Cleaner: §fRemoves Wax");
        provider.add("item.gtpm.tool.behavior.shield_disable", "§cBrute: §fDisables Shields");
        provider.add("item.gtpm.tool.behavior.relocate_mining", "§2Magnetic: §fRelocates Mined Blocks and Mob Drops");
        provider.add("item.gtpm.tool.behavior.aoe_mining", "§5Area-of-Effect: §f%sx%sx%s");
        provider.add("item.gtpm.tool.behavior.ground_tilling", "§eFarmer: §fTills Ground");
        provider.add("item.gtpm.tool.behavior.grass_path", "§eLandscaper: §fCreates Grass Paths");
        provider.add("item.gtpm.tool.behavior.rail_rotation", "§eRailroad Engineer: §fRotates Rails");
        provider.add("item.gtpm.tool.behavior.crop_harvesting", "§aHarvester: §fHarvests Crops");
        provider.add("item.gtpm.tool.behavior.plunger", "§9Plumber: §fDrains Fluids");
        provider.add("item.gtpm.tool.behavior.block_rotation", "§2Mechanic: §fRotates Blocks");
        provider.add("item.gtpm.tool.behavior.dowse_campfire", "§1Firefighter: §fDowses Campfires");
        provider.add("item.gtpm.tool.behavior.damage_boost", "§4Damage Boost: §fExtra damage against %s");
        provider.add("item.gtpm.tool.behavior.prospecting.air", "Found an air pocket");
        provider.add("item.gtpm.tool.behavior.prospecting.found", "Found %s");
        provider.add("item.gtpm.tool.behavior.prospecting.changing", "Detected material change");
        replace(provider, "item.gtpm.tool.sword", "%s Sword");
        replace(provider, "item.gtpm.tool.pickaxe", "%s Pickaxe");
        replace(provider, "item.gtpm.tool.shovel", "%s Shovel");
        replace(provider, "item.gtpm.tool.axe", "%s Axe");
        replace(provider, "item.gtpm.tool.hoe", "%s Hoe");
        replace(provider, "item.gtpm.tool.saw", "%s Saw");
        replace(provider, "item.gtpm.tool.hammer", "%s Hammer");
        provider.add("item.gtpm.tool.hammer.tooltip", "§8Crushes Blocks when harvesting them");
        replace(provider, "item.gtpm.tool.mallet", "%s Soft Mallet");
        multilineLang(provider, "item.gtpm.tool.mallet.tooltip",
                "§8Sneak to Pause Machine After Current Recipe.\n§8Stops/Starts Machines");
        replace(provider, "item.gtpm.tool.wrench", "%s Wrench");
        provider.add("item.gtpm.tool.wrench.tooltip", "§8Hold left click to dismantle Machines");
        replace(provider, "item.gtpm.tool.file", "%s File");
        replace(provider, "item.gtpm.tool.crowbar", "%s Crowbar");
        provider.add("item.gtpm.tool.crowbar.tooltip", "§8Dismounts Covers");
        replace(provider, "item.gtpm.tool.screwdriver", "%s Screwdriver");
        provider.add("item.gtpm.tool.screwdriver.tooltip", "§8Adjusts Covers and Machines");
        replace(provider, "item.gtpm.tool.mortar", "%s Mortar");
        replace(provider, "item.gtpm.tool.wire_cutter", "%s Wire Cutter");
        replace(provider, "item.gtpm.tool.knife", "%s Knife");
        replace(provider, "item.gtpm.tool.butchery_knife", "%s Butchery Knife");
        provider.add("item.gtpm.tool.butchery_knife.tooltip", "§8Has a slow Attack Rate");
        replace(provider, "item.gtpm.tool.scythe", "%s Scythe");
        provider.add("item.gtpm.tool.scythe.tooltip", "§8Because a Scythe doesn't make Sense");
        replace(provider, "item.gtpm.tool.rolling_pin", "%s Rolling Pin");
        replace(provider, "item.gtpm.tool.lv_drill", "%s Drill (LV)");
        replace(provider, "item.gtpm.tool.mv_drill", "%s Drill (MV)");
        replace(provider, "item.gtpm.tool.hv_drill", "%s Drill (HV)");
        replace(provider, "item.gtpm.tool.ev_drill", "%s Drill (EV)");
        replace(provider, "item.gtpm.tool.iv_drill", "%s Drill (IV)");
        replace(provider, "item.gtpm.tool.lv_wirecutter", "%s Wire Cutter (LV)");
        replace(provider, "item.gtpm.tool.hv_wirecutter", "%s Wire Cutter (HV)");
        replace(provider, "item.gtpm.tool.iv_wirecutter", "%s Wire Cutter (IV)");
        replace(provider, "item.gtpm.tool.mining_hammer", "%s Mining Hammer");
        provider.add("item.gtpm.tool.mining_hammer.tooltip",
                "§8Mines a large area at once (unless you're crouching)");
        replace(provider, "item.gtpm.tool.spade", "%s Spade");
        provider.add("item.gtpm.tool.spade.tooltip", "§8Mines a large area at once (unless you're crouching)");
        replace(provider, "item.gtpm.tool.lv_chainsaw", "%s Chainsaw (LV)");
        replace(provider, "item.gtpm.tool.mv_chainsaw", "%s Chainsaw (MV)");
        replace(provider, "item.gtpm.tool.hv_chainsaw", "%s Chainsaw (HV)");
        replace(provider, "item.gtpm.tool.iv_chainsaw", "%s Chainsaw (IV)");
        replace(provider, "item.gtpm.tool.lv_wrench", "%s Wrench (LV)");
        provider.add("item.gtpm.tool.lv_wrench.tooltip", "§8Hold left click to dismantle Machines");
        replace(provider, "item.gtpm.tool.hv_wrench", "%s Wrench (HV)");
        provider.add("item.gtpm.tool.hv_wrench.tooltip", "§8Hold left click to dismantle Machines");
        replace(provider, "item.gtpm.tool.iv_wrench", "%s Wrench (IV)");
        provider.add("item.gtpm.tool.iv_wrench.tooltip", "§8Hold left click to dismantle Machines");
        replace(provider, "item.gtpm.tool.lv_buzzsaw", "%s Buzzsaw (LV)");
        provider.add("item.gtpm.tool.lv_buzzsaw.tooltip", "§8Not suitable for harvesting Blocks");
        replace(provider, "item.gtpm.tool.lv_screwdriver", "%s Screwdriver (LV)");
        provider.add("item.gtpm.tool.lv_screwdriver.tooltip", "§8Adjusts Covers and Machines");
        replace(provider, "item.gtpm.tool.hv_screwdriver", "%s Screwdriver (HV)");
        provider.add("item.gtpm.tool.hv_screwdriver.tooltip", "§8Adjusts Covers and Machines");
        replace(provider, "item.gtpm.tool.iv_screwdriver", "%s Screwdriver (IV)");
        provider.add("item.gtpm.tool.iv_screwdriver.tooltip", "§8Adjusts Covers and Machines");
        replace(provider, "item.gtpm.tool.plunger", "%s Plunger");
        provider.add("item.gtpm.tool.plunger.tooltip", "§8Removes Fluids from Machines");
        replace(provider, "item.gtpm.tool.shears", "%s Shears");
        provider.add("item.gtpm.tool.tooltip.crafting_uses", "%s §aCrafting Uses");
        provider.add("item.gtpm.tool.tooltip.max_uses", "%s §eTotal Durability");
        provider.add("item.gtpm.tool.tooltip.general_uses", "%s §bDurability");
        provider.add("item.gtpm.tool.tooltip.attack_damage", "%s §cAttack Damage");
        provider.add("item.gtpm.tool.tooltip.attack_speed", "%s §9Attack Speed");
        provider.add("item.gtpm.tool.tooltip.mining_speed", "%s §dMining Speed");
        provider.add("item.gtpm.tool.tooltip.harvest_level", "§eHarvest Level %s");
        provider.add("item.gtpm.tool.tooltip.harvest_level_extra", "§eHarvest Level %s §f(%s§f)");
        multiLang(provider, "item.gtpm.tool.harvest_level", "§8Wood", "§7Stone", "§aIron", "§bDiamond",
                "§dNetherite", "§9Duranium", "§cNeutronium");
        provider.add("item.gtpm.tool.tooltip.repair_info", "§8Hold SHIFT to show Repair Info");
        provider.add("item.gtpm.tool.tooltip.repair_material", "§8Repair with: §f§a%s");
        provider.add("item.gtpm.tool.tooltip.innate_enchantments", "§5Innate Enchantments:");
        provider.add("item.gtpm.tool.aoe.rows", "Rows");
        provider.add("item.gtpm.tool.aoe.columns", "Columns");
        provider.add("item.gtpm.tool.aoe.layers", "Layers");

        provider.add("item.gtpm.armor.helmet", "%s Helmet");
        provider.add("item.gtpm.armor.chestplate", "%s Chestplate");
        provider.add("item.gtpm.armor.leggings", "%s Leggings");
        provider.add("item.gtpm.armor.boots", "%s Boots");

        provider.add("item.gtpm.turbine_rotor.tooltip", "Turbine Rotors for your power station");
        provider.add("metaitem.clipboard.tooltip",
                "Can be written on (without any writing Instrument). Right-click on Wall to place, and Shift-Right-Click to remove");
        provider.add("metaitem.behavior.mode_switch.tooltip", "Use while sneaking to switch mode");
        provider.add("metaitem.behavior.mode_switch.mode_switched", "§eMode Set to: %s");
        provider.add("metaitem.behavior.mode_switch.current_mode", "Mode: %s");
        provider.add("metaitem.tool.tooltip.primary_material", "§fMaterial: §e%s");
        provider.add("metaitem.tool.tooltip.durability", "§fDurability: §a%d / %d");
        provider.add("metaitem.tool.tooltip.rotor.efficiency", "Turbine Efficiency: §9%d%%");
        provider.add("metaitem.tool.tooltip.rotor.power", "Turbine Power: §9%d%%");
        provider.add("item.gtpm.ulv_voltage_coil.tooltip", "Primitive Coil");
        provider.add("item.gtpm.lv_voltage_coil.tooltip", "Basic Coil");
        provider.add("item.gtpm.mv_voltage_coil.tooltip", "Good Coil");
        provider.add("item.gtpm.hv_voltage_coil.tooltip", "Advanced Coil");
        provider.add("item.gtpm.ev_voltage_coil.tooltip", "Extreme Coil");
        provider.add("item.gtpm.iv_voltage_coil.tooltip", "Elite Coil");
        provider.add("item.gtpm.luv_voltage_coil.tooltip", "Master Coil");
        provider.add("item.gtpm.zpm_voltage_coil.tooltip", "Super Coil");
        provider.add("item.gtpm.uv_voltage_coil.tooltip", "Ultimate Coil");
        provider.add("item.gtpm.uhv_voltage_coil.tooltip", "Ultra Coil");
        provider.add("item.gtpm.uev_voltage_coil.tooltip", "Unreal Coil");
        provider.add("item.gtpm.uiv_voltage_coil.tooltip", "Insane Coil");
        provider.add("item.gtpm.uxv_voltage_coil.tooltip", "Epic Coil");
        provider.add("item.gtpm.opv_voltage_coil.tooltip", "Legendary Coil");
        provider.add("item.gtpm.max_voltage_coil.tooltip", "Maximum Coil");
        provider.add("metaitem.liquid_fuel_jetpack.tooltip", "Uses Combustion Generator Fuels for Thrust");
        provider.add("metaarmor.nms.nightvision.enabled", "NanoMuscle™ Suite: NightVision Enabled");
        provider.add("metaarmor.nms.nightvision.disabled", "NanoMuscle™ Suite: NightVision Disabled");
        provider.add("metaarmor.nms.nightvision.error", "NanoMuscle™ Suite: §cNot enough power!");
        provider.add("metaarmor.qts.nightvision.enabled", "QuarkTech™ Suite: NightVision Enabled");
        provider.add("metaarmor.qts.nightvision.disabled", "QuarkTech™ Suite: NightVision Disabled");
        provider.add("metaarmor.qts.nightvision.error", "QuarkTech™ Suite: §cNot enough power!");
        provider.add("metaarmor.nms.step_assist.disabled", "NanoMuscle™ Suite: StepAssist Disabled");
        provider.add("metaarmor.nms.step_assist.enabled", "NanoMuscle™ Suite: StepAssist Enabled");
        provider.add("metaarmor.qts.step_assist.disabled", "QuarkTech™ Suite: StepAssist Disabled");
        provider.add("metaarmor.qts.step_assist.enabled", "QuarkTech™ Suite: StepAssist Enabled");
        provider.add("metaarmor.qts.boosted_jump.enabled", "QuarkTech™ Suite: Jump Boost Enabled");
        provider.add("metaarmor.qts.boosted_jump.disabled", "QuarkTech™ Suite: Jump Boost Disabled");
        provider.add("metaarmor.jetpack.flight.enable", "Jetpack: Flight Enabled");
        provider.add("metaarmor.jetpack.flight.disable", "Jetpack: Flight Disabled");
        provider.add("metaarmor.jetpack.hover.enable", "Jetpack: Hover Mode Enabled");
        provider.add("metaarmor.jetpack.hover.disable", "Jetpack: Hover Mode Disabled");
        provider.add("metaarmor.jetpack.emergency_hover_mode", "Emergency Hover Mode Enabled!");
        provider.add("metaarmor.nms.share.enable", "NanoMuscle™ Suite: Charging Enabled");
        provider.add("metaarmor.nms.share.disable", "NanoMuscle™ Suite: Charging Disabled");
        provider.add("metaarmor.nms.share.error", "NanoMuscle™ Suite: §cNot enough power for charging!");
        provider.add("metaarmor.qts.share.enable", "QuarkTech™ Suite: Charging Enabled");
        provider.add("metaarmor.qts.share.disable", "QuarkTech™ Suite: Charging Disabled");
        provider.add("metaarmor.qts.share.error", "QuarkTech™ Suite: §cNot enough power for charging!");
        provider.add("metaarmor.message.nightvision.enabled", "§bNightVision: §aOn");
        provider.add("metaarmor.message.nightvision.disabled", "§bNightVision: §cOff");
        provider.add("metaarmor.message.nightvision.error", "§cNot enough power!");
        provider.add("metaarmor.message.step_assist.enabled", "§bStep-Assist: §aOn");
        provider.add("metaarmor.message.step_assist.disabled", "§bStep-Assist: §cOff");
        provider.add("metaarmor.tooltip.stepassist", "Provides Step-Assist");
        provider.add("metaarmor.tooltip.speed", "Increases Running Speed");
        provider.add("metaarmor.tooltip.jump", "Increases Jump Height and Distance");
        provider.add("metaarmor.tooltip.falldamage", "Nullifies Fall Damage");
        provider.add("metaarmor.tooltip.potions", "Nullifies Harmful Effects");
        provider.add("metaarmor.tooltip.burning", "Nullifies Burning");
        provider.add("metaarmor.tooltip.freezing", "Prevents Freezing");
        provider.add("metaarmor.tooltip.breath", "Replenishes Underwater Breath Bar");
        provider.add("metaarmor.tooltip.autoeat", "Replenishes Food Bar by Using Food from Inventory");
        provider.add("metaarmor.hud.status.enabled", "§aON");
        provider.add("metaarmor.hud.status.disabled", "§cOFF");
        provider.add("metaarmor.hud.energy_lvl", "Energy Level: %s");
        provider.add("metaarmor.hud.engine_enabled", "Engine Enabled: %s");
        provider.add("metaarmor.hud.fuel_lvl", "Fuel Level: %s");
        provider.add("metaarmor.hud.hover_mode", "Hover Mode: %s");
        provider.add("mataarmor.hud.supply_mode", "Supply Mode: %s");
        provider.add("metaarmor.hud.gravi_engine", "GraviEngine: %s");
        provider.add("metaarmor.energy_share.error", "Energy Supply: §cNot enough power for gadgets charging!");
        provider.add("metaarmor.energy_share.enable", "Energy Supply: Gadgets charging enabled");
        provider.add("metaarmor.energy_share.disable", "Energy Supply: Gadgets charging disabled");
        provider.add("metaarmor.energy_share.tooltip", "Supply mode: %s");
        provider.add("metaarmor.energy_share.tooltip.guide",
                "To change mode shift-right click when holding item");
        provider.add("metaitem.record.sus.tooltip", "§7Leonz - Among Us Drip");
        provider.add("item.gtpm.nan_certificate.tooltip", "Challenge Accepted!");
        provider.add("item.gtpm.blacklight.tooltip", "Long-Wave §dUltraviolet§7 light source");
        provider.add("gui.widget.incrementButton.default_tooltip",
                "Hold Shift, Ctrl or both to change the amount");
        provider.add("gui.widget.recipeProgressWidget.default_tooltip", "Show Recipes");
        multilineLang(provider, "gtpm.recipe_memory_widget.tooltip",
                "§7Left click to automatically input this recipe into the crafting grid\n§7Shift click to lock/unlock this recipe");
        provider.add("cover.filter.blacklist.disabled", "Whitelist");
        provider.add("cover.filter.blacklist.enabled", "Blacklist");
        provider.add("cover.tag_filter.title", "Tag Filter");
        multilineLang(provider, "cover.tag_filter.info",
                """
                        §bAccepts complex expressions
                        §6a & b§r = AND
                        §6a | b§r = OR
                        §6a ^ b§r = XOR
                        §6!a§r = NOT
                        §6(a)§r for grouping
                        §6*§r for wildcard
                        §6$§r for untagged
                        §bTags come in the form 'namespace:tag/subtype'.
                        The 'c:' namespace is assumed if one isn't provided.
                        §bExample: §6*dusts/gold | (gtpm:circuits & !*lv)
                        This matches all gold dusts or all circuits except LV ones""");
        provider.add("cover.tag_filter.test_slot.info",
                "Insert a item to test if it matches the filter expression");
        provider.add("cover.tag_filter.matches", "Item matches");
        provider.add("cover.tag_filter.matches_not", "Item does not match");
        provider.add("cover.fluid_filter.title", "Fluid Filter");
        multilineLang(provider, "cover.fluid_filter.config_amount",
                "Scroll wheel up increases amount, down decreases.\nShift[§6x10§r],Ctrl[§ex100§r],Shift+Ctrl[§ax1000§r]\nRight click increases amount, left click decreases.\nHold shift to double/halve.\nMiddle click to clear");
        provider.add("cover.fluid_filter.mode.filter_fill", "Filter Fill");
        provider.add("cover.fluid_filter.mode.filter_drain", "Filter Drain");
        provider.add("cover.fluid_filter.mode.filter_both", "Filter Fill & Drain");
        provider.add("cover.item_filter.title", "Item Filter");
        provider.add("cover.storage.title", "Storage Cover");
        provider.add("cover.filter.mode.filter_insert", "Filter Insert");
        provider.add("cover.filter.mode.filter_extract", "Filter Extract");
        provider.add("cover.filter.mode.filter_both", "Filter Insert/Extract");
        provider.add("cover.item_filter.ignore_damage.enabled", "Ignore Damage");
        provider.add("cover.item_filter.ignore_damage.disabled", "Respect Damage");
        provider.add("cover.item_filter.ignore_nbt.enabled", "Ignore NBT");
        provider.add("cover.item_filter.ignore_nbt.disabled", "Respect NBT");
        provider.add("cover.voiding.voiding_mode.void_any", "Void Matching");
        provider.add("cover.voiding.voiding_mode.void_overflow", "Void Overflow");
        multilineLang(provider, "cover.voiding.voiding_mode.description",
                "§eVoid Matching§r will void anything matching the filter. \n§eVoid Overflow§r will void anything matching the filter, up to the specified amount.");
        provider.add("cover.fluid.voiding.title", "Fluid Voiding Settings");
        provider.add("cover.fluid.voiding.advanced.title", "Advanced Fluid Voiding Settings");
        provider.add("cover.item.voiding.title", "Item Voiding Settings");
        provider.add("cover.item.voiding.advanced.title", "Advanced Item Voiding Settings");
        provider.add("cover.voiding.label.disabled", "Disabled");
        provider.add("cover.voiding.label.enabled", "Enabled");
        provider.add("cover.voiding.tooltip",
                "§cWARNING!§7 Setting this to \"Enabled\" means that fluids or items WILL be voided.");
        provider.add("cover.voiding.message.disabled", "Voiding Cover Disabled");
        provider.add("cover.voiding.message.enabled", "Voiding Cover Enabled");
        provider.add("cover.smart_item_filter.title", "Smart Item Filter");
        provider.add("cover.smart_item_filter.filtering_mode.electrolyzer", "Electrolyzer");
        provider.add("cover.smart_item_filter.filtering_mode.centrifuge", "Centrifuge");
        provider.add("cover.smart_item_filter.filtering_mode.sifter", "Sifter");
        multilineLang(provider, "cover.smart_item_filter.filtering_mode.description",
                "Select Machine this Smart Filter will use for filtering.\nIt will automatically pick right portions of items for robotic arm.");
        provider.add("cover.conveyor.title", "Conveyor Cover Settings (%s)");
        provider.add("cover.conveyor.transfer_rate", "§7items/sec");
        provider.add("cover.conveyor.mode", "Mode: %s");
        provider.add("cover.conveyor.mode.export", "Mode: Export");
        provider.add("cover.conveyor.mode.import", "Mode: Import");
        multilineLang(provider, "cover.conveyor.distribution.round_robin_global",
                "Distribution Mode: §bRound Robin\n§7Splits items equally across connected inventories");
        multilineLang(provider, "cover.conveyor.distribution.round_robin_prio",
                "Distribution Mode: §bRound Robin with Restriction\n§7Tries to split items equally across connected inventories.\n§7Will not send items down Restrictive item pipes unless no other paths are available.");
        multilineLang(provider, "cover.conveyor.distribution.insert_first",
                "Distribution Mode: §bPriority\n§7Will insert into the first inventory with the highest priority it can find.\n§7Restrictive item pipes lower the priority of a path.");
        multilineLang(provider, "cover.conveyor.blocks_input.enabled",
                "If enabled, items will not be inserted when cover is set to pull items from the inventory into pipe.\n§aEnabled");
        multilineLang(provider, "cover.conveyor.blocks_input.disabled",
                "If enabled, items will not be inserted when cover is set to pull items from the inventory into pipe.\n§cDisabled");
        provider.add("cover.universal.manual_import_export.mode.disabled",
                "Manual I/O: §bDisabled\n§7Items / Fluids will only move as specified by the cover and its filter.");
        provider.add("cover.universal.manual_import_export.mode.filtered",
                "Manual I/O: §bFiltered\n§7Items / Fluids can be extracted and inserted independently of the cover mode, as long as its filter matches (if any)");
        provider.add("cover.universal.manual_import_export.mode.unfiltered",
                "Manual I/O: §bUnfiltered\n§7Items / Fluids can be moved independently of the cover mode. The filter only applies to what is inserted or extracted by this cover itself.");
        multilineLang(provider, "cover.universal.manual_import_export.mode.description",
                "§eDisabled§r - Items/fluids will only move as specified by the cover and its filter. \n§eAllow Filtered§r - Items/fluids can be extracted and inserted independently of the cover mode, as long as its filter matches (if any). \n§eAllow Unfiltered§r - Items/fluids can be moved independently of the cover mode. Filter applies to the items inserted or extracted by this cover");
        provider.add("cover.conveyor.item_filter.title", "Item Filter");
        multiLang(provider, "cover.conveyor.tag.title", "Tag Name",
                "(use * for wildcard)");
        provider.add("cover.robotic_arm.title", "Robotic Arm Settings (%s)");
        provider.add("cover.robotic_arm.transfer_mode.transfer_any", "Transfer Any");
        provider.add("cover.robotic_arm.transfer_mode.transfer_exact", "Supply Exact");
        provider.add("cover.robotic_arm.transfer_mode.keep_exact", "Keep Exact");
        multilineLang(provider, "cover.robotic_arm.transfer_mode.description",
                "§eTransfer Any§r - in this mode, cover will transfer as many items matching its filter as possible.\n§eSupply Exact§r - in this mode, cover will supply items in portions specified in item filter slots (or variable under this button for tag filter). If amount of items is less than portion size, items won't be moved.\n§eKeep Exact§r - in this mode, cover will keep specified amount of items in the destination inventory, supplying additional amount of items if required.\n§7Tip: left/right click on filter slots to change item amount,  use shift clicking to change amount faster.");
        provider.add("cover.pump.title", "Pump Cover Settings (%s)");
        provider.add("cover.pump.transfer_rate", "%s");
        provider.add("cover.pump.mode.export", "Mode: Export");
        provider.add("cover.pump.mode.import", "Mode: Import");
        provider.add("cover.pump.fluid_filter.title", "Fluid Filter");
        provider.add("cover.bucket.mode.bucket", "B");
        provider.add("cover.bucket.mode.milli_bucket", "mB");
        provider.add("cover.fluid_regulator.title", "Fluid Regulator Settings (%s)");
        multilineLang(provider, "cover.fluid_regulator.transfer_mode.description",
                "§eTransfer Any§r - in this mode, cover will transfer as many fluids matching its filter as possible.\n§eSupply Exact§r - in this mode, cover will supply fluids in portions specified in the window underneath this button. If amount of fluids is less than portion size, fluids won't be moved.\n§eKeep Exact§r - in this mode, cover will keep specified amount of fluids in the destination inventory, supplying additional amount of fluids if required.\n§7Tip: shift click will multiply increase/decrease amounts by 10 and ctrl click will multiply by 100.");
        provider.add("cover.fluid_regulator.supply_exact", "Supply Exact: %s");
        provider.add("cover.fluid_regulator.keep_exact", "Keep Exact: %s");
        provider.add("cover.machine_controller.title", "Machine Controller Settings");
        provider.add("cover.machine_controller.normal", "Normal");
        provider.add("cover.machine_controller.inverted", "Inverted");
        multilineLang(provider, "cover.machine_controller.invert.enabled",
                "§eInverted§r - in this mode, the cover will require a signal stronger than the set redstone level to run");
        multilineLang(provider, "cover.machine_controller.invert.disabled",
                "§eNormal§r - in this mode, the cover will require a signal weaker than the set redstone level to run");
        provider.add("cover.machine_controller.redstone", "Min Redstone Strength: %d");
        provider.add("cover.machine_controller.suspend_powerfail", "Prevent Power Failing:");
        provider.add("cover.machine_controller.mode.machine", "Control Machine");
        provider.add("cover.machine_controller.mode.cover_up", "Control Cover (Top)");
        provider.add("cover.machine_controller.mode.cover_down", "Control Cover (Bottom)");
        provider.add("cover.machine_controller.mode.cover_south", "Control Cover (South)");
        provider.add("cover.machine_controller.mode.cover_north", "Control Cover (North)");
        provider.add("cover.machine_controller.mode.cover_east", "Control Cover (East)");
        provider.add("cover.machine_controller.mode.cover_west", "Control Cover (West)");
        provider.add("cover.machine_controller.mode.null", "Control Nothing");
        provider.add("cover.ender_fluid_link.title", "Ender Fluid Link");
        provider.add("cover.ender_item_link.title", "Ender Item Link");
        provider.add("cover.ender_redstone_link.title", "Ender Redstone Link");
        provider.add("cover.ender_fluid_link.iomode.enabled", "I/O Enabled");
        provider.add("cover.ender_fluid_link.iomode.disabled", "I/O Disabled");
        provider.add("cover.ender_fluid_link.tooltip.channel_description", "Set channel description with input text");
        provider.add("cover.ender_fluid_link.tooltip.channel_name", "Set channel name with input text");
        provider.add("cover.ender_fluid_link.tooltip.list_button", "Show channel list");
        provider.add("cover.ender_fluid_link.tooltip.clear_button", "Clear channel description");
        multilineLang(provider, "cover.ender_fluid_link.private.tooltip.disabled",
                "Switch to private tank mode\nPrivate mode uses the player who originally placed the cover");
        provider.add("cover.ender_fluid_link.private.tooltip.enabled", "Switch to public tank mode");
        multilineLang(provider, "cover.ender_fluid_link.incomplete_hex",
                "Inputted color is incomplete!\nIt will be applied once complete (all 8 hex digits)\nClosing the gui will lose edits!");
        provider.add("cover.detector_base.message_normal_state", "Monitoring Status: Normal");
        provider.add("cover.detector_base.message_inverted_state", "Monitoring Status: Inverted");

        var detectorLatchDescription = """
                Change the redstone behavior of this Cover.
                §eContinuous§7 - Default; values less than the minimum output 0; values higher than the maximum output 15; values between min and max output between 0 and 15
                §eLatched§7 - output 15 until above max, then output 0 until below min""";
        multilineLang(provider, "cover.advanced_detector.latch.enabled",
                "Behavior: Latched\n\n" + detectorLatchDescription);
        multilineLang(provider, "cover.advanced_detector.latch.disabled",
                "Behavior: Continuous\n\n" + detectorLatchDescription);

        provider.add("cover.advanced_energy_detector.label", "Advanced Energy Detector");
        provider.add("cover.advanced_energy_detector.min", "Min");
        provider.add("cover.advanced_energy_detector.max", "Max");

        var advancedEnergyDetectorInvertDescription = "Toggle to invert the redstone logic\nBy default, redstone is emitted when less than the minimum EU, and stops emitting when greater than the max EU";
        multilineLang(provider, "cover.advanced_energy_detector.invert.enabled",
                "Output: Inverted\n\n" + advancedEnergyDetectorInvertDescription);
        multilineLang(provider, "cover.advanced_energy_detector.invert.disabled",
                "Output: Normal\n\n" + advancedEnergyDetectorInvertDescription);
        var advancedEnergyDetectorModeDescription = "Change between using discrete EU values or percentages for comparing min/max against an attached energy storage.";
        multilineLang(provider, "cover.advanced_energy_detector.use_percent.enabled",
                "Mode: Percentage\n\n" + advancedEnergyDetectorModeDescription);
        multilineLang(provider, "cover.advanced_energy_detector.use_percent.disabled",
                "Mode: Discrete EU\n\n" + advancedEnergyDetectorModeDescription);

        provider.add("cover.advanced_fluid_detector.label", "Advanced Fluid Detector");
        var advancedFluidDetectorInvertDescription = "Toggle to invert the redstone logic\nBy default, redstone stops emitting when less than the minimum mB of fluid, and starts emitting when greater than the min mB of fluid up to the set maximum";
        multilineLang(provider, "cover.advanced_fluid_detector.invert.enabled",
                "Output: Inverted\n\n" + advancedFluidDetectorInvertDescription);
        multilineLang(provider, "cover.advanced_fluid_detector.invert.disabled",
                "Output: Normal\n\n" + advancedFluidDetectorInvertDescription);
        provider.add("cover.advanced_fluid_detector.max", "Max Fluid (mB)");
        provider.add("cover.advanced_fluid_detector.min", "Min Fluid (mB)");

        provider.add("cover.advanced_item_detector.label", "Advanced Item Detector");
        var advancedItemDetectorInvertDescription = "Toggle to invert the redstone logic\nBy default, redstone stops emitting when less than the minimum amount of items, and starts emitting when greater than the min amount of items up to the set maximum";
        multilineLang(provider, "cover.advanced_item_detector.invert.enabled",
                "Output: Inverted\n\n" + advancedItemDetectorInvertDescription);
        multilineLang(provider, "cover.advanced_item_detector.invert.disabled",
                "Output: Normal\n\n" + advancedItemDetectorInvertDescription);
        provider.add("cover.advanced_item_detector.max", "Max Items");
        provider.add("cover.advanced_item_detector.min", "Min Items");
        provider.add("cover.shutter.message.enabled", "Closed shutter");
        provider.add("cover.shutter.message.disabled", "Opened shutter");

        provider.add("item.gtpm.bucket", "%s Bucket");
        replace(provider, GTMaterials.FullersEarth.getUnlocalizedName(), "Fuller's Earth");
        replace(provider, GTMaterials.Cooperite.getUnlocalizedName(), "Sheldonite"); // greg's humor is now on
                                                                                     // 1.19...
        replace(provider, GTMaterials.YellowLimonite.getUnlocalizedName(), "Yellow Limonite");
        replace(provider, GTMaterials.HSSG.getUnlocalizedName(), "HSS-G");
        replace(provider, GTMaterials.HSSE.getUnlocalizedName(), "HSS-E");
        replace(provider, GTMaterials.HSSS.getUnlocalizedName(), "HSS-S");
        replace(provider, GTMaterials.RTMAlloy.getUnlocalizedName(), "RTM Alloy");
        replace(provider, GTMaterials.HSLASteel.getUnlocalizedName(), "HSLA Steel");

        replace(provider, GTMaterials.UUMatter.getUnlocalizedName(), "UU-Matter");
        replace(provider, GTMaterials.PCBCoolant.getUnlocalizedName(), "PCB Coolant");
        replace(provider, GTMaterials.TungstenSteel.getUnlocalizedName(), "Tungstensteel");
        replace(provider, GTMaterials.Iron3Chloride.getUnlocalizedName(), "Iron III Chloride");
        replace(provider, GTMaterials.Iron2Chloride.getUnlocalizedName(), "Iron II Chloride");

        replace(provider, GTMaterials.HydroCrackedButadiene.getUnlocalizedName(), "Hydro-Cracked Butadiene");
        replace(provider, GTMaterials.HydroCrackedButane.getUnlocalizedName(), "Hydro-Cracked Butane");
        replace(provider, GTMaterials.HydroCrackedButene.getUnlocalizedName(), "Hydro-Cracked Butene");
        replace(provider, GTMaterials.HydroCrackedButene.getUnlocalizedName(), "Hydro-Cracked Butene");
        replace(provider, GTMaterials.HydroCrackedEthane.getUnlocalizedName(), "Hydro-Cracked Ethane");
        replace(provider, GTMaterials.HydroCrackedEthylene.getUnlocalizedName(), "Hydro-Cracked Ethylene");
        replace(provider, GTMaterials.HydroCrackedPropane.getUnlocalizedName(), "Hydro-Cracked Propane");
        replace(provider, GTMaterials.HydroCrackedPropene.getUnlocalizedName(), "Hydro-Cracked Propene");
        replace(provider, GTMaterials.SteamCrackedButadiene.getUnlocalizedName(), "Steam-Cracked Butadiene");
        replace(provider, GTMaterials.SteamCrackedButane.getUnlocalizedName(), "Steam-Cracked Butane");
        replace(provider, GTMaterials.SteamCrackedButene.getUnlocalizedName(), "Steam-Cracked Butene");
        replace(provider, GTMaterials.SteamCrackedButene.getUnlocalizedName(), "Steam-Cracked Butene");
        replace(provider, GTMaterials.SteamCrackedEthane.getUnlocalizedName(), "Steam-Cracked Ethane");
        replace(provider, GTMaterials.SteamCrackedEthylene.getUnlocalizedName(), "Steam-Cracked Ethylene");
        replace(provider, GTMaterials.SteamCrackedPropane.getUnlocalizedName(), "Steam-Cracked Propane");
        replace(provider, GTMaterials.SteamCrackedPropene.getUnlocalizedName(), "Steam-Cracked Propene");
        replace(provider, GTMaterials.LightlyHydroCrackedGas.getUnlocalizedName(), "Lightly Hydro-Cracked Gas");
        replace(provider, GTMaterials.LightlyHydroCrackedHeavyFuel.getUnlocalizedName(),
                "Lightly Hydro-Cracked Heavy Fuel");
        replace(provider, GTMaterials.LightlyHydroCrackedLightFuel.getUnlocalizedName(),
                "Lightly Hydro-Cracked Light Fuel");
        replace(provider, GTMaterials.LightlyHydroCrackedNaphtha.getUnlocalizedName(),
                "Lightly Hydro-Cracked Naphtha");
        replace(provider, GTMaterials.LightlySteamCrackedGas.getUnlocalizedName(), "Lightly Steam-Cracked Gas");
        replace(provider, GTMaterials.LightlySteamCrackedHeavyFuel.getUnlocalizedName(),
                "Lightly Steam-Cracked Heavy Fuel");
        replace(provider, GTMaterials.LightlySteamCrackedLightFuel.getUnlocalizedName(),
                "Lightly Steam-Cracked Light Fuel");
        replace(provider, GTMaterials.LightlySteamCrackedNaphtha.getUnlocalizedName(),
                "Lightly Steam-Cracked Naphtha");
        replace(provider, GTMaterials.SeverelyHydroCrackedGas.getUnlocalizedName(),
                "Severely Hydro-Cracked Gas");
        replace(provider, GTMaterials.SeverelyHydroCrackedHeavyFuel.getUnlocalizedName(),
                "Severely Hydro-Cracked Heavy Fuel");
        replace(provider, GTMaterials.SeverelyHydroCrackedLightFuel.getUnlocalizedName(),
                "Severely Hydro-Cracked Light Fuel");
        replace(provider, GTMaterials.SeverelyHydroCrackedNaphtha.getUnlocalizedName(),
                "Severely Hydro-Cracked Naphtha");
        replace(provider, GTMaterials.SeverelySteamCrackedGas.getUnlocalizedName(),
                "Severely Steam-Cracked Gas");
        replace(provider, GTMaterials.SeverelySteamCrackedHeavyFuel.getUnlocalizedName(),
                "Severely Steam-Cracked Heavy Fuel");
        replace(provider, GTMaterials.SeverelySteamCrackedLightFuel.getUnlocalizedName(),
                "Severely Steam-Cracked Light Fuel");
        replace(provider, GTMaterials.SeverelySteamCrackedNaphtha.getUnlocalizedName(),
                "Severely Steam-Cracked Naphtha");
        replace(provider, GTMaterials.LPG.getUnlocalizedName(), "LPG");

        replace(provider, GTMaterials.Zeron100.getUnlocalizedName(), "Zeron-100");
        replace(provider, GTMaterials.IncoloyMA956.getUnlocalizedName(), "Incoloy MA-956");
        replace(provider, GTMaterials.Stellite100.getUnlocalizedName(), "Stellite-100");
        replace(provider, GTMaterials.HastelloyC276.getUnlocalizedName(), "Hastelloy C-276");

        replace(provider, GTBlocks.BATTERY_EMPTY_TIER_I.get().getDescriptionId(), "Empty Tier I Capacitor");
        replace(provider, GTBlocks.BATTERY_LAPOTRONIC_EV.get().getDescriptionId(), "EV Lapotronic Capacitor");
        replace(provider, GTBlocks.BATTERY_LAPOTRONIC_IV.get().getDescriptionId(), "IV Lapotronic Capacitor");
        replace(provider, GTBlocks.BATTERY_EMPTY_TIER_II.get().getDescriptionId(), "Empty Tier II Capacitor");
        replace(provider, GTBlocks.BATTERY_LAPOTRONIC_LuV.get().getDescriptionId(), "LuV Lapotronic Capacitor");
        replace(provider, GTBlocks.BATTERY_LAPOTRONIC_ZPM.get().getDescriptionId(), "ZPM Lapotronic Capacitor");
        replace(provider, GTBlocks.BATTERY_EMPTY_TIER_III.get().getDescriptionId(), "Empty Tier III Capacitor");
        replace(provider, GTBlocks.BATTERY_LAPOTRONIC_UV.get().getDescriptionId(), "UV Lapotronic Capacitor");
        replace(provider, GTBlocks.BATTERY_ULTIMATE_UHV.get().getDescriptionId(), "UHV Ultimate Capacitor");

        provider.add("block.gtpm.netherrack_nether_quartz_ore", "Nether Quartz Ore");
        provider.add("block.gtpm.surface_rock", "%s Surface Rock");

        provider.add("item.gtpm.tiny_gunpowder_dust", "Tiny Pile of Gunpowder");
        provider.add("item.gtpm.small_gunpowder_dust", "Small Pile of Gunpowder");
        provider.add("item.gtpm.tiny_paper_dust", "Tiny Pile of Chad");
        provider.add("item.gtpm.small_paper_dust", "Small Pile of Chad");
        provider.add("item.gtpm.paper_dust", "Chad");
        provider.add("item.gtpm.tiny_rare_earth_dust", "Tiny Pile of Rare Earth");
        provider.add("item.gtpm.small_rare_earth_dust", "Small Pile of Rare Earth");
        provider.add("item.gtpm.rare_earth_dust", "Rare Earth");
        provider.add("item.gtpm.tiny_ash_dust", "Tiny Pile of Ashes");
        provider.add("item.gtpm.small_ash_dust", "Small Pile of Ashes");
        provider.add("item.gtpm.ash_dust", "Ashes");
        provider.add("item.gtpm.tiny_bone_dust", "Tiny Pile of Bone Meal");
        provider.add("item.gtpm.small_bone_dust", "Small Pile of Bone Meal");
        provider.add("item.gtpm.bone_dust", "Bone Meal");
        provider.add("item.gtpm.refined_cassiterite_sand_ore", "Refined Cassiterite Sand");
        provider.add("item.gtpm.purified_cassiterite_sand_ore", "Purified Cassiterite Sand");
        provider.add("item.gtpm.crushed_cassiterite_sand_ore", "Ground Cassiterite Sand");
        provider.add("item.gtpm.tiny_cassiterite_sand_dust", "Tiny Pile of Cassiterite Sand");
        provider.add("item.gtpm.small_cassiterite_sand_dust", "Small Pile of Cassiterite Sand");
        provider.add("item.gtpm.impure_cassiterite_sand_dust", "Impure Pile of Cassiterite Sand");
        provider.add("item.gtpm.pure_cassiterite_sand_dust", "Purified Pile of Cassiterite Sand");
        provider.add("item.gtpm.cassiterite_sand_dust", "Cassiterite Sand");
        provider.add("item.gtpm.tiny_dark_ash_dust", "Tiny Pile of Dark Ashes");
        provider.add("item.gtpm.small_dark_ash_dust", "Small Pile of Dark Ashes");
        provider.add("item.gtpm.dark_ash_dust", "Dark Ashes");
        provider.add("item.gtpm.tiny_ice_dust", "Tiny Pile of Crushed Ice");
        provider.add("item.gtpm.small_ice_dust", "Small Pile of Crushed Ice");
        provider.add("item.gtpm.ice_dust", "Crushed Ice");
        provider.add("item.gtpm.sugar_gem", "Sugar Cube");
        provider.add("item.gtpm.chipped_sugar_gem", "Small Sugar Cubes");
        provider.add("item.gtpm.flawed_sugar_gem", "Tiny Sugar Cube");
        provider.add("item.gtpm.tiny_rock_salt_dust", "Tiny Pile of Rock Salt");
        provider.add("item.gtpm.small_rock_salt_dust", "Small Pile of Rock Salt");
        provider.add("item.gtpm.impure_rock_salt_dust", "Impure Pile of Rock Salt");
        provider.add("item.gtpm.pure_rock_salt_dust", "Purified Pile of Rock Salt");
        provider.add("item.gtpm.rock_salt_dust", "Rock Salt");
        provider.add("item.gtpm.tiny_salt_dust", "Tiny Pile of Salt");
        provider.add("item.gtpm.small_salt_dust", "Small Pile of Salt");
        provider.add("item.gtpm.impure_salt_dust", "Impure Pile of Salt");
        provider.add("item.gtpm.pure_salt_dust", "Purified Pile of Salt");
        provider.add("item.gtpm.salt_dust", "Salt");
        provider.add("item.gtpm.tiny_wood_dust", "Tiny Pile of Wood Pulp");
        provider.add("item.gtpm.small_wood_dust", "Small Pile of Wood Pulp");
        provider.add("item.gtpm.wood_dust", "Wood Pulp");
        provider.add("item.gtpm.wood_plate", "Wood Plank");
        provider.add("item.gtpm.long_wood_rod", "Long Wood Stick");
        provider.add("item.gtpm.wood_bolt", "Short Wood Stick");
        provider.add("item.gtpm.tiny_treated_wood_dust", "Tiny Pile of Treated Wood Pulp");
        provider.add("item.gtpm.small_treated_wood_dust", "Small Pile of Treated Wood Pulp");
        provider.add("item.gtpm.treated_wood_dust", "Treated Wood Pulp");
        provider.add("item.gtpm.treated_wood_plate", "Treated Wood Plank");
        provider.add("item.gtpm.treated_wood_rod", "Treated Wood Stick");
        provider.add("item.gtpm.long_treated_wood_rod", "Long Treated Wood Stick");
        provider.add("item.gtpm.treated_wood_bolt", "Short Treated Wood Stick");
        provider.add("item.gtpm.glass_gem", "Glass Crystal");
        provider.add("item.gtpm.chipped_glass_gem", "Chipped Glass Crystal");
        provider.add("item.gtpm.flawed_glass_gem", "Flawed Glass Crystal");
        provider.add("item.gtpm.flawless_glass_gem", "Flawless Glass Crystal");
        provider.add("item.gtpm.exquisite_glass_gem", "Exquisite Glass Crystal");
        provider.add("item.gtpm.glass_plate", "Glass Pane");
        provider.add("item.gtpm.tiny_blaze_dust", "Tiny Pile of Blaze Powder");
        provider.add("item.gtpm.small_blaze_dust", "Small Pile of Blaze Powder");
        provider.add("item.gtpm.tiny_sugar_dust", "Tiny Pile of Sugar");
        provider.add("item.gtpm.small_sugar_dust", "Small Pile of Sugar");
        provider.add("item.gtpm.tiny_basaltic_mineral_sand_dust", "Tiny Pile of Basaltic Mineral Sand");
        provider.add("item.gtpm.small_basaltic_mineral_sand_dust", "Small Pile of Basaltic Mineral Sand");
        provider.add("item.gtpm.basaltic_mineral_sand_dust", "Basaltic Mineral Sand");
        provider.add("item.gtpm.tiny_granitic_mineral_sand_dust", "Tiny Pile of Granitic Mineral Sand");
        provider.add("item.gtpm.small_granitic_mineral_sand_dust", "Small Pile of Granitic Mineral Sand");
        provider.add("item.gtpm.granitic_mineral_sand_dust", "Granitic Mineral Sand");
        provider.add("item.gtpm.tiny_garnet_sand_dust", "Tiny Pile of Garnet Sand");
        provider.add("item.gtpm.small_garnet_sand_dust", "Small Pile of Garnet Sand");
        provider.add("item.gtpm.garnet_sand_dust", "Garnet Sand");
        provider.add("item.gtpm.tiny_quartz_sand_dust", "Tiny Pile of Quartz Sand");
        provider.add("item.gtpm.small_quartz_sand_dust", "Small Pile of Quartz Sand");
        provider.add("item.gtpm.quartz_sand_dust", "Quartz Sand");
        provider.add("item.gtpm.tiny_glauconite_sand_dust", "Tiny Pile of Glauconite Sand");
        provider.add("item.gtpm.small_glauconite_sand_dust", "Small Pile of Glauconite Sand");
        provider.add("item.gtpm.glauconite_sand_dust", "Glauconite Sand");
        provider.add("item.gtpm.refined_bentonite_ore", "Refined Bentonite");
        provider.add("item.gtpm.purified_bentonite_ore", "Purified Bentonite");
        provider.add("item.gtpm.crushed_bentonite_ore", "Ground Bentonite");
        provider.add("item.gtpm.tiny_bentonite_dust", "Tiny Pile of Bentonite");
        provider.add("item.gtpm.small_bentonite_dust", "Small Pile of Bentonite");
        provider.add("item.gtpm.impure_bentonite_dust", "Impure Pile of Bentonite");
        provider.add("item.gtpm.pure_bentonite_dust", "Purified Pile of Bentonite");
        provider.add("item.gtpm.bentonite_dust", "Bentonite");
        provider.add("item.gtpm.tiny_fullers_earth_dust", "Tiny Pile of Fullers Earth");
        provider.add("item.gtpm.small_fullers_earth_dust", "Small Pile of Fullers Earth");
        provider.add("item.gtpm.fullers_earth_dust", "Fullers Earth");
        provider.add("item.gtpm.refined_pitchblende_ore", "Refined Pitchblende");
        provider.add("item.gtpm.purified_pitchblende_ore", "Purified Pitchblende");
        provider.add("item.gtpm.crushed_pitchblende_ore", "Ground Pitchblende");
        provider.add("item.gtpm.tiny_pitchblende_dust", "Tiny Pile of Pitchblende");
        provider.add("item.gtpm.small_pitchblende_dust", "Small Pile of Pitchblende");
        provider.add("item.gtpm.impure_pitchblende_dust", "Impure Pile of Pitchblende");
        provider.add("item.gtpm.pure_pitchblende_dust", "Purified Pile of Pitchblende");
        provider.add("item.gtpm.pitchblende_dust", "Pitchblende");
        provider.add("item.gtpm.refined_talc_ore", "Refined Talc");
        provider.add("item.gtpm.purified_talc_ore", "Purified Talc");
        provider.add("item.gtpm.crushed_talc_ore_ore", "Ground Talc");
        provider.add("item.gtpm.tiny_talc_dust", "Tiny Pile of Talc");
        provider.add("item.gtpm.small_talc_dust", "Small Pile of Talc");
        provider.add("item.gtpm.impure_talc_dust", "Impure Pile of Talc");
        provider.add("item.gtpm.pure_talc_dust", "Purified Pile of Talc");
        provider.add("item.gtpm.talc_dust", "Talc");
        provider.add("item.gtpm.tiny_wheat_dust", "Tiny Pile of Flour");
        provider.add("item.gtpm.small_wheat_dust", "Small Pile of Flour");
        provider.add("item.gtpm.wheat_dust", "Flour");
        provider.add("item.gtpm.tiny_meat_dust", "Tiny Pile of Mince Meat");
        provider.add("item.gtpm.small_meat_dust", "Small Pile of Mince Meat");
        provider.add("item.gtpm.meat_dust", "Mince Meat");
        provider.add("item.gtpm.borosilicate_glass_ingot", "Borosilicate Glass Bar");
        provider.add("item.gtpm.fine_borosilicate_glass_wire", "Borosilicate Glass Fibers");
        provider.add("item.gtpm.tiny_platinum_group_sludge_dust", "Tiny Clump of Platinum Group Sludge");
        provider.add("item.gtpm.small_platinum_group_sludge_dust", "Small Clump of Platinum Group Sludge");
        provider.add("item.gtpm.platinum_group_sludge_dust", "Platinum Group Sludge");
        provider.add("item.gtpm.tiny_platinum_raw_dust", "Tiny Pile of Raw Platinum Powder");
        provider.add("item.gtpm.small_platinum_raw_dust", "Small Pile of Raw Platinum Powder");
        provider.add("item.gtpm.platinum_raw_dust", "Raw Platinum Powder");
        provider.add("item.gtpm.tiny_palladium_raw_dust", "Tiny Pile of Raw Palladium Powder");
        provider.add("item.gtpm.small_palladium_raw_dust", "Small Pile of Raw Palladium Powder");
        provider.add("item.gtpm.palladium_raw_dust", "Raw Palladium Powder");
        provider.add("item.gtpm.tiny_inert_metal_mixture_dust", "Tiny Pile of Inert Metal Mixture");
        provider.add("item.gtpm.small_inert_metal_mixture_dust", "Small Pile of Inert Metal Mixture");
        provider.add("item.gtpm.inert_metal_mixture_dust", "Inert Metal Mixture");
        provider.add("item.gtpm.tiny_rarest_metal_mixture_dust", "Tiny Pile of Rarest Metal Mixture");
        provider.add("item.gtpm.small_rarest_metal_mixture_dust", "Small Pile of Rarest Metal Mixture");
        provider.add("item.gtpm.rarest_metal_mixture_dust", "Rarest Metal Mixture");
        provider.add("item.gtpm.tiny_platinum_sludge_residue_dust", "Tiny Pile of Platinum Sludge Residue");
        provider.add("item.gtpm.small_platinum_sludge_residue_dust", "Small Pile of Platinum Sludge Residue");
        provider.add("item.gtpm.platinum_sludge_residue_dust", "Platinum Sludge Residue");
        provider.add("item.gtpm.tiny_iridium_metal_residue_dust", "Tiny Pile of Iridium Metal Residue");
        provider.add("item.gtpm.small_iridium_metal_residue_dust", "Small Pile of Iridium Metal Residue");
        provider.add("item.gtpm.iridium_metal_residue_dust", "Iridium Metal Residue");

        provider.add("behaviour.hoe", "Can till dirt");
        provider.add("behaviour.soft_hammer", "Activates and Deactivates Machines");
        provider.add("behaviour.soft_hammer.enabled", "Working Enabled");
        provider.add("behaviour.soft_hammer.disabled", "Working Disabled");
        provider.add("behaviour.soft_hammer.disabled_cycle", "Working Disabled after current cycle");
        provider.add("behaviour.lighter.tooltip.description", "Can light things on fire");
        provider.add("behaviour.lighter.tooltip.usage", "Shift-right click to open/close");
        provider.add("behaviour.lighter.fluid.tooltip", "Can light things on fire with Butane or Propane");
        provider.add("behaviour.lighter.uses", "Remaining uses: %d");
        provider.add("behavior.toggle_energy_consumer.tooltip", "Use to toggle mode");
        provider.add("behaviour.hammer", "Turns on and off Muffling for Machines (by hitting them)");
        provider.add("behaviour.wrench", "Rotates Blocks on Rightclick");
        provider.add("behaviour.boor.by", "by %s");
        provider.add("behaviour.paintspray.solvent.tooltip", "Can remove color from things");
        provider.add("behaviour.paintspray.white.tooltip", "Can paint things in White");
        provider.add("behaviour.paintspray.orange.tooltip", "Can paint things in Orange");
        provider.add("behaviour.paintspray.magenta.tooltip", "Can paint things in Magenta");
        provider.add("behaviour.paintspray.light_blue.tooltip", "Can paint things in Light Blue");
        provider.add("behaviour.paintspray.yellow.tooltip", "Can paint things in Yellow");
        provider.add("behaviour.paintspray.lime.tooltip", "Can paint things in Lime");
        provider.add("behaviour.paintspray.pink.tooltip", "Can paint things in Pink");
        provider.add("behaviour.paintspray.gray.tooltip", "Can paint things in Gray");
        provider.add("behaviour.paintspray.light_gray.tooltip", "Can paint things in Light Gray");
        provider.add("behaviour.paintspray.cyan.tooltip", "Can paint things in Cyan");
        provider.add("behaviour.paintspray.purple.tooltip", "Can paint things in Purple");
        provider.add("behaviour.paintspray.blue.tooltip", "Can paint things in Blue");
        provider.add("behaviour.paintspray.brown.tooltip", "Can paint things in Brown");
        provider.add("behaviour.paintspray.green.tooltip", "Can paint things in Green");
        provider.add("behaviour.paintspray.red.tooltip", "Can paint things in Red");
        provider.add("behaviour.paintspray.black.tooltip", "Can paint things in Black");
        provider.add("behaviour.paintspray.uses", "Remaining Uses: %d");
        provider.add("behaviour.prospecting", "Usable for Prospecting");

        provider.add("behaviour.memory_card.tooltip.copy",
                "§7Sneak + R-Click to copy configuration, or clear stored data if a block other than a machine or pipe is targeted.");
        provider.add("behaviour.memory_card.tooltip.paste", "§7R-Click to paste machine configuration");
        provider.add("behaviour.memory_card.tooltip.view_stored", "§8<Sneak to view stored configuration>");
        provider.add("behaviour.memory_card.client_msg.cleared", "Stored configuration cleared");
        provider.add("behaviour.memory_card.client_msg.copied", "Copied machine configuration");
        provider.add("behaviour.memory_card.client_msg.pasted", "Applied machine configuration");
        provider.add("behaviour.memory_card.client_msg.missing_items", "Missing items required to paste configuration");
        provider.add("behaviour.memory_card.tooltip.items_to_paste",
                "The following items are needed to paste this configuration:");
        provider.add("behaviour.memory_card.enabled", "§aEnabled§r");
        provider.add("behaviour.memory_card.disabled", "§cDisabled§r");
        provider.add("behaviour.memory_card.copy_target", "Copying: %s");

        provider.add("behaviour.setting.tooltip.item_io", "Item Output: %s (%s)");
        provider.add("behaviour.setting.tooltip.fluid_io", "Fluid Output: %s (%s)");
        provider.add("behaviour.setting.tooltip.auto_output", "§2Auto Output§r");
        provider.add("behaviour.setting.tooltip.allow_input", "§2Allow Input§r");
        provider.add("behaviour.setting.tooltip.auto_output_allow_input", "§2Auto Output/Allow Input§r");
        provider.add("behaviour.setting.tooltip.pipe_connections", "Pipe connections: %s");
        provider.add("behaviour.setting.tooltip.pipe_blocked_connections", "Pipe shuttered sides: %s");
        provider.add("behaviour.setting.tooltip.muffled", "Muffling %s");
        provider.add("behaviour.setting.tooltip.circuit_config", "Programmed Circuit: ");
        provider.add("enchantment.damage.disjunction", "Disjunction");
        provider.add("enchantment.gtpm.disjunction.description",
                "Applies Weakness and Slowness to Ender-related mobs.");
        provider.add("enchantment.hard_hammer", "Hammering");
        provider.add("enchantment.gtpm.hard_hammer.description",
                "Breaks blocks as if they were mined with a GregTech Hammer.");
        provider.add("tile.gtpm.seal.name", "Sealed Block");
        provider.add("tile.gtpm.foam.name", "Foam");
        provider.add("tile.gtpm.reinforced_foam.name", "Reinforced Foam");
        provider.add("tile.gtpm.petrified_foam.name", "Petrified Foam");
        provider.add("tile.gtpm.reinforced_stone.name", "Reinforced Stone");
        provider.add("tile.gtpm.brittle_charcoal.name", "Brittle Charcoal");
        multilineLang(provider, "tile.gtpm.brittle_charcoal.tooltip",
                "Produced by the Charcoal Pile Igniter.\nMine this to get Charcoal.");
        provider.add("metaitem.prospector.mode.ores", "§aOre Prospection Mode§r");
        provider.add("metaitem.prospector.mode.fluid", "§bFluid Prospection Mode§r");
        provider.add("metaitem.prospector.mode.bedrock_ore", "§bBedrock Ore Prospection Mode§r");
        provider.add("metaitem.prospector.tooltip.radius", "Scans range in a %s Chunk Radius");
        provider.add("metaitem.prospector.tooltip.modes", "Available Modes:");
        provider.add("behavior.prospector.not_enough_energy", "Not Enough Energy!");
        provider.add("behavior.prospector.added_waypoint", "Created waypoint named %s!");
        provider.add("metaitem.tricorder_scanner.tooltip", "Tricorder");
        provider.add("metaitem.debug_scanner.tooltip", "Tricorder");
        provider.add("behavior.portable_scanner.bedrock_fluid.amount", "Fluid In Deposit: %s %s - %s%%");
        provider.add("behavior.portable_scanner.bedrock_fluid.amount_unknown", "Fluid In Deposit: %s%%");
        provider.add("behavior.portable_scanner.bedrock_fluid.nothing", "Fluid In Deposit: §6Nothing§r");
        provider.add("behavior.portable_scanner.environmental_hazard", "Environmental Hazard In Chunk: %s§r - %s ppm");
        provider.add("behavior.portable_scanner.environmental_hazard.nothing",
                "Environmental Hazard In Chunk: §6Nothing§r");
        provider.add("behavior.portable_scanner.local_hazard", "Local Hazard In Area: %s§r - %s ppm");
        provider.add("behavior.portable_scanner.local_hazard.nothing", "Local Hazard In Area: §6Nothing§r");
        provider.add("behavior.portable_scanner.block_hardness", "Hardness: %s Blast Resistance: %s");
        provider.add("behavior.portable_scanner.block_name", "Name: %s MetaData: %s");
        provider.add("behavior.portable_scanner.debug_cpu_load",
                "Average CPU load of ~%sns over %s ticks with worst time of %sns.");
        provider.add("behavior.portable_scanner.debug_cpu_load_seconds", "This is %s seconds.");
        provider.add("behavior.portable_scanner.debug_lag_count",
                "Caused %s Lag Spike Warnings (anything taking longer than %sms) on the Server.");
        provider.add("behavior.portable_scanner.debug_machine", "Meta-ID: %s");
        provider.add("behavior.portable_scanner.debug_machine_invalid", " invalid!");
        provider.add("behavior.portable_scanner.debug_machine_invalid_null", " invalid! MetaTileEntity = null!");
        provider.add("behavior.portable_scanner.debug_machine_valid", " valid");
        provider.add("behavior.portable_scanner.divider", "=========================");
        provider.add("behavior.portable_scanner.energy_container_in", "Max IN: %s (%s) EU at %s A");
        provider.add("behavior.portable_scanner.energy_container_out", "Max OUT: %s (%s) EU at %s A");
        provider.add("behavior.portable_scanner.energy_container_storage", "Energy: %s EU / %s EU");
        provider.add("behavior.portable_scanner.eu_per_sec", "Average (last second): %s EU/t");
        provider.add("behavior.portable_scanner.amp_per_sec", "Average (last second): %s A");
        provider.add("behavior.portable_scanner.machine_disabled", "Disabled.");
        provider.add("behavior.portable_scanner.machine_front_facing", "Front Facing: %s");
        provider.add("behavior.portable_scanner.machine_ownership", "§2Machine Owner Type: %s§r");
        provider.add("behavior.portable_scanner.guild_name", "§2Guild Name: %s§r");
        provider.add("behavior.portable_scanner.team_name", "§2Team Name: %s§r");
        provider.add("behavior.portable_scanner.player_name", "§2Player Name: %s§r, §7Player Online: %s§r");
        provider.add("behavior.portable_scanner.machine_power_loss", "Shut down due to power loss.");
        provider.add("behavior.portable_scanner.machine_progress", "Progress/Load: %s / %s");
        provider.add("behavior.portable_scanner.machine_upwards_facing", "Upwards Facing: %s");
        provider.add("behavior.portable_scanner.muffled", "Muffled.");
        provider.add("behavior.portable_scanner.multiblock_energy_input",
                "Max Energy Income: %s EU/t Tier: %s");
        provider.add("behavior.portable_scanner.multiblock_energy_output",
                "Max Energy Output: %s EU/t Tier: %s");
        provider.add("behavior.portable_scanner.multiblock_maintenance", "Problems: %s");
        provider.add("behavior.portable_scanner.multiblock_parallel", "Multi Processing: %s");
        provider.add("behavior.portable_scanner.position", "----- X: %s Y: %s Z: %s D: %s -----");
        provider.add("behavior.portable_scanner.state", "%s: %s");
        provider.add("behavior.portable_scanner.tank", "Tank %s: %s mB / %s mB %s");
        provider.add("behavior.portable_scanner.tanks_empty", "All Tanks Empty");
        provider.add("behavior.portable_scanner.workable_consumption", "Probably Uses: %s EU/t at %s A");
        provider.add("behavior.portable_scanner.workable_production", "Probably Produces: %s EU/t at %s A");
        provider.add("behavior.portable_scanner.workable_progress", "Progress: %s s / %s s");
        provider.add("behavior.portable_scanner.workable_stored_energy", "Stored Energy: %s EU / %s EU");
        provider.add("behavior.portable_scanner.mode.caption", "Display mode: %s");
        provider.add("behavior.portable_scanner.mode.show_all_info", "Show all info (excluding internal info)");
        provider.add("behavior.portable_scanner.mode.show_block_info", "Show block info");
        provider.add("behavior.portable_scanner.mode.show_machine_info", "Show machine info");
        provider.add("behavior.portable_scanner.mode.show_electrical_info", "Show electrical info");
        provider.add("behavior.portable_scanner.mode.show_recipe_info", "Show recipe info");
        provider.add("behavior.portable_scanner.mode.show_environmental_info", "Show environmental info");
        provider.add("behavior.portable_scanner.mode.show_internal_info", "Show internal debugging info");
        provider.add("behavior.item_magnet.enabled", "§aMagnetic Field Enabled");
        provider.add("behavior.item_magnet.disabled", "§cMagnetic Field Disabled");
        provider.add("behavior.data_item.title", "§n%s Construction Data:");
        provider.add("behavior.data_item.data", "- §a%s");

        provider.add("metaitem.terminal.tooltip", "Sharp tools make good work");
        provider.add("metaitem.terminal.tooltip.creative", "§bCreative Mode");
        provider.add("metaitem.terminal.tooltip.hardware", "§aHardware: %d");
        provider.add("metaitem.plugin.tooltips.1",
                "Plugins can be added to the screen for more functionality.");
        provider.add("metaitem.plugin.proxy.tooltips.1", "(Please adjust to proxy mode in the screen)");
        provider.add("metaitem.cover.digital.tooltip",
                "Connects machines over §fPower Cables§7 to the §fCentral Monitor§7 as §fCover§7.");

        provider.add("gtpm.machine.drum.enable_output", "Will drain Fluid to downward adjacent Tanks");
        provider.add("gtpm.machine.drum.disable_output", "Will not drain Fluid");
        provider.add("gtpm.machine.locked_safe.malfunctioning", "§cMalfunctioning!");
        provider.add("gtpm.machine.locked_safe.requirements", "§7Replacements required:");

        multilineLang(provider, "gtpm.machine.workbench.tooltip",
                "Better than Forestry\nHas Item Storage, Tool Storage, pulls from adjacent Inventories, and saves Recipes.");
        provider.add("gtpm.machine.workbench.tab.workbench", "Crafting");
        provider.add("gtpm.machine.workbench.tab.item_list", "Storage");
        multilineLang(provider, "gtpm.machine.workbench.storage_note",
                "(Available items from connected\ninventories usable for crafting)");
        provider.add("gtpm.item_list.item_stored", "§7Stored: %d");
        provider.add("gtpm.machine.workbench.tab.crafting", "Crafting");
        provider.add("gtpm.machine.workbench.tab.container", "Container");

        provider.add("gtpm.machine.parallel_hatch.display", "Adjust the maximum parallel of the multiblock");
        provider.add("gtpm.machine.basic.input_from_output_side.allow", "Allow Input from Output Side: ");
        provider.add("gtpm.machine.basic.input_from_output_side.disallow",
                "Disallow Input from Output Side: ");
        provider.add("gtpm.machine.muffle.on", "Sound Muffling: Enabled");
        provider.add("gtpm.machine.muffle.off", "Sound Muffling: Disabled");
        provider.add("gtpm.machine.perfect_oc", "Does not lose energy efficiency when overclocked.");
        provider.add("gtpm.machine.parallel_limit", "Can run up to §b%d§r§7 Recipes at once.");

        provider.add("gtpm.machine.multiblock.tank.tooltip",
                "Fill and drain through the controller or tank valves.");
        provider.add("gtpm.machine.tank_valve.tooltip",
                "Use to fill and drain multiblock tanks. Auto outputs when facing down.");

        provider.add("metaitem.cover.digital.mode.proxy.disabled", "Click to enable Proxy Mode");
        provider.add("metaitem.cover.digital.mode.proxy.enabled", "Proxy Mode enabled");
        provider.add("metaitem.cover.digital.mode.machine.disabled", "Click to enable Machine Mode");
        provider.add("metaitem.cover.digital.mode.machine.enabled", "Machine Mode enabled");
        provider.add("metaitem.cover.digital.mode.energy.disabled", "Click to enable Energy Mode");
        provider.add("metaitem.cover.digital.mode.energy.enabled", "Energy Mode enabled");
        provider.add("metaitem.cover.digital.mode.item.disabled", "Click to enable Item Mode");
        provider.add("metaitem.cover.digital.mode.item.enabled", "Item Mode enabled");
        provider.add("metaitem.cover.digital.mode.fluid.disabled", "Click to enable Fluid Mode");
        provider.add("metaitem.cover.digital.mode.fluid.enabled", "Fluid Mode enabled");

        provider.add("gtpm.part_sharing.disabled", "Multiblock Sharing §4Disabled");
        provider.add("gtpm.part_sharing.enabled", "Multiblock Sharing §aEnabled");
        provider.add("gtpm.universal.liters", "%s mB");
        provider.add("gtpm.universal.kiloliters", "%s B");
        provider.add("gtpm.universal.parentheses", "(%s)");
        provider.add("gtpm.universal.spaced_parentheses", "( %s )");
        provider.add("gtpm.universal.padded_parentheses", " (%s) ");
        provider.add("gtpm.universal.padded_spaced_parentheses", " ( %s ) ");
        provider.add("gtpm.universal.tooltip.voltage_in", "§aVoltage IN: §f%d EU/t (%s§f)");
        provider.add("gtpm.universal.tooltip.max_voltage_in", "§aMax Voltage IN: §f%d (%s§f)");
        provider.add("gtpm.universal.tooltip.voltage_out", "§aVoltage OUT: §f%d EU/t (%s§f)");
        provider.add("gtpm.universal.tooltip.max_voltage_out", "§aMax Voltage OUT: §f%d (%s§f)");
        provider.add("gtpm.universal.tooltip.voltage_in_out", "§aVoltage IN/OUT: §f%d EU/t (%s§f)");
        provider.add("gtpm.universal.tooltip.max_voltage_in_out", "§aMax Voltage IN/OUT: §f%d EU/t (%s§f)");
        provider.add("gtpm.universal.tooltip.amperage_in", "§eAmperage IN: §f%dA");
        provider.add("gtpm.universal.tooltip.amperage_in_till", "§eAmperage IN up to: §f%dA");
        provider.add("gtpm.universal.tooltip.amperage_out", "§eAmperage OUT: §f%dA");
        provider.add("gtpm.universal.tooltip.amperage_out_till", "§eAmperage OUT up to: §f%dA");
        provider.add("gtpm.universal.tooltip.amperage_in_out", "§eAmperage IN/OUT: §f%dA");
        provider.add("gtpm.universal.tooltip.amperage_in_out_till", "§eAmperage IN/OUT up to: §f%dA");
        provider.add("gtpm.universal.tooltip.energy_storage_capacity", "§cEnergy Capacity: §r%d EU");
        provider.add("gtpm.universal.tooltip.energy_tier_range", "§aAllowed Voltage Tiers: §f%s §f- %s");
        provider.add("gtpm.universal.tooltip.item_storage_capacity", "§6Item Slots: §f%d");
        provider.add("gtpm.universal.tooltip.item_storage_total", "§6Item Capacity: §f%d items");
        provider.add("gtpm.universal.tooltip.item_stored", "§dItem Stored: §f%s, %d items");
        provider.add("gtpm.universal.tooltip.item_transfer_rate", "§bTransfer Rate: §f%d items/s");
        provider.add("gtpm.universal.tooltip.item_transfer_rate_stacks", "§bTransfer Rate: §f%d stacks/s");
        provider.add("gtpm.universal.tooltip.fluid_storage_capacity", "§9Fluid Capacity: §f%d mB");
        provider.add("gtpm.universal.tooltip.fluid_storage_capacity_mult",
                "§9Fluid Capacity: §f%d §7Tanks, §f%d mB §7each");
        provider.add("gtpm.universal.tooltip.fluid_stored", "§2Fluid Stored: §f%s, %d mB");
        provider.add("gtpm.universal.tooltip.fluid_transfer_rate", "§bTransfer Rate: §f%d mB/t");
        provider.add("gtpm.universal.tooltip.parallel", "§dMax Parallel: §f%d");
        provider.add("gtpm.universal.tooltip.working_area", "§bWorking Area: §f%dx%d");
        provider.add("gtpm.universal.tooltip.chunk_mode", "Chunk Mode: ");
        provider.add("gtpm.universal.tooltip.silk_touch", "Silk Touch: ");
        provider.add("gtpm.universal.tooltip.working_area_chunks", "§bWorking Area: §f%dx%d Chunks");
        provider.add("gtpm.universal.tooltip.working_area_max", "§bMax Working Area: §f%dx%d");
        provider.add("gtpm.universal.tooltip.working_area_chunks_max", "§bMax Working Area: §f%dx%d Chunks");
        provider.add("gtpm.universal.tooltip.uses_per_tick", "Uses §f%d EU/t §7while working");
        provider.add("gtpm.universal.tooltip.uses_per_tick_steam", "Uses §f%d mB/t §7of §fSteam §7while working");
        provider.add("gtpm.universal.tooltip.uses_per_hour_lubricant",
                "Uses §f%d mB/hr §7of §6Lubricant §7while working");
        provider.add("gtpm.universal.tooltip.uses_per_second", "Uses §f%d EU/s §7while working");
        provider.add("gtpm.universal.tooltip.uses_per_op", "Uses §f%d EU/operation");
        provider.add("gtpm.universal.tooltip.base_production_eut", "§eBase Production: §f%d EU/t");
        provider.add("gtpm.universal.tooltip.base_production_fluid", "§eBase Production: §f%d mB/t");
        provider.add("gtpm.universal.tooltip.produces_fluid", "§eProduces: §f%d mB/t");
        provider.add("gtpm.universal.tooltip.terrain_resist",
                "This Machine will not explode when exposed to the Elements");
        provider.add("gtpm.universal.tooltip.requires_redstone", "§4Requires Redstone power");
        provider.add("gtpm.universal.tooltip.deprecated",
                "§4§lWARNING:§r§4 DEPRECATED. WILL BE REMOVED IN A FUTURE VERSION.§r");
        provider.add("gtpm.recipe.total", "Total: %s EU");
        provider.add("gtpm.recipe.max_eu", "Max. EU: %s EU");
        provider.add("gtpm.recipe.eu", "Usage: %s A @ %s");
        provider.add("gtpm.recipe.eu_inverted", "Generation: %s A @ %s");
        provider.add("gtpm.recipe.eu.total", "%s EU/t");
        provider.add("gtpm.recipe.duration", "Duration: %s secs");
        provider.add("gtpm.recipe.voltage", "Usage: %s A @ %s");
        provider.add("gtpm.recipe.total_eu", "Total Usage: %s EU/t");
        provider.add("gtpm.recipe.not_consumed", "Does not get consumed in the process");
        provider.add("gtpm.recipe.chance", "Chance: %s +%s/tier");
        provider.add("gtpm.recipe.temperature", "Temp: %s");
        provider.add("gtpm.recipe.coil.tier", "Coil: %s");
        provider.add("gtpm.recipe.explosive", "Explosive: %s");
        provider.add("gtpm.recipe.eu_to_start", "EU To Start: %sEU (%s)");
        provider.add("gtpm.recipe.dimensions", "Dimensions: %s");
        provider.add("gtpm.recipe.cleanroom", "Requires %s");
        provider.add("gtpm.recipe.environmental_hazard.reverse", "§cArea must be free of %s");
        provider.add("gtpm.recipe.environmental_hazard", "§cArea must have %s");
        provider.add("gtpm.recipe.cleanroom.display_name", "Cleanroom");
        provider.add("gtpm.recipe.cleanroom_sterile.display_name", "Sterile Cleanroom");
        provider.add("gtpm.recipe.research", "Requires Research");
        provider.add("gtpm.recipe.scan_for_research", "Scan for Assembly Line");
        provider.add("gtpm.recipe.computation_per_tick", "Min. Computation: %s CWU/t");
        provider.add("gtpm.recipe.total_computation", "Computation: %s CWU");
        provider.add("gtpm.recipe.byproduct_tier", "Byproducts from %s§r+");
        provider.add("gtpm.fluid.click_to_fill",
                "§7Click with a Fluid Container to §bfill §7the tank (Shift-click for a full stack).");
        provider.add("gtpm.fluid.click_combined",
                "§7Click with a Fluid Container to §cempty §7or §bfill §7the tank (Shift-click for a full stack).");
        provider.add("gtpm.fluid.click_to_empty",
                "§7Click with a Fluid Container to §cempty §7the tank (Shift-click for a full stack).");
        provider.add("gtpm.tool_action.show_tooltips", "Hold SHIFT to show Tool Info");
        provider.add("gtpm.tool_action.screwdriver.auto_output_covers",
                "§8Use Screwdriver to Allow Input from Output Side or access Covers");
        provider.add("gtpm.tool_action.screwdriver.toggle_mode_covers",
                "§8Use Screwdriver to toggle Modes or access Covers");
        provider.add("gtpm.tool_action.screwdriver.access_covers", "§8Use Screwdriver to access Covers");
        provider.add("gtpm.tool_action.screwdriver.auto_collapse",
                "§8Use Screwdriver to toggle Item collapsing");
        provider.add("gtpm.tool_action.screwdriver.auto_output", "§8Use Screwdriver to toggle Auto-Output");
        provider.add("gtpm.tool_action.screwdriver.toggle_mode", "§8Use Screwdriver to toggle Modes");
        provider.add("gtpm.tool_action.wrench.set_facing", "§8Use Wrench to set Facing");
        provider.add("gtpm.tool_action.wrench.connect",
                "§8Use Wrench to set Connections, sneak to block Connections");
        provider.add("gtpm.tool_action.wire_cutter.connect", "§8Use Wire Cutters to set Connections");
        provider.add("gtpm.tool_action.soft_mallet.reset", "§8Use Soft Mallet to toggle Working");
        provider.add("gtpm.tool_action.soft_mallet.toggle_mode", "§8Use Soft Mallet to toggle Modes");
        provider.add("gtpm.tool_action.hammer", "§8Use Hard Hammer to muffle Sounds");
        provider.add("gtpm.tool_action.crowbar", "§8Use Crowbar to remove Covers");
        provider.add("gtpm.tool_action.tape", "§8Use Tape to fix Maintenance Problems");
        provider.add("gtpm.fluid.liquid_generic", "Liquid %s");
        provider.add("gtpm.fluid.generic", "%s");
        provider.add("gtpm.fluid.gas_generic", "%s Gas");
        provider.add("gtpm.fluid.gas_vapor", "%s Vapor");
        provider.add("gtpm.fluid.plasma", "%s Plasma");
        provider.add("gtpm.fluid.molten", "Molten %s");
        provider.add("gtpm.fluid.empty", "Empty");
        provider.add("gtpm.fluid.amount", "§9Amount: %d/%d mB");
        provider.add("gtpm.fluid.temperature", "§cTemperature: %s");
        provider.add("gtpm.fluid.temperature.cryogenic", "§bCryogenic! Handle with care!");
        provider.add("gtpm.fluid.state_gas", "§aState: Gaseous");
        provider.add("gtpm.fluid.state_liquid", "§aState: Liquid");
        provider.add("gtpm.fluid.state_plasma", "§aState: Plasma");
        provider.add("gtpm.fluid.type_acid.tooltip", "§6Acidic! Handle with care!");
        provider.add("gtpm.gui.title_bar.back", "Back");
        provider.add("gtpm.gui.title_bar.page_switcher", "Pages");
        provider.add("gtpm.gui.fuel_amount", "Fuel Amount:");
        provider.add("gtpm.gui.fluid_amount", "Fluid Amount:");
        provider.add("gtpm.gui.toggle_view.disabled", "Toggle View (Fluids)");
        provider.add("gtpm.gui.toggle_view.enabled", "Toggle View (Items)");
        multilineLang(provider, "gtpm.gui.overclock.enabled", "Overclocking Enabled.\nClick to Disable");
        multilineLang(provider, "gtpm.gui.overclock.disabled", "Overclocking Disabled.\nClick to Enable");
        multilineLang(provider, "gtpm.gui.overclock.description",
                "Overclock Button\n§7Recipes can overclock up to the set tier");
        provider.add("gtpm.gui.overclock.off", "X");
        provider.add("gtpm.gui.sort", "Sort");
        provider.add("gtpm.gui.fluid_auto_output.tooltip.enabled", "Fluid Auto-Output Enabled");
        provider.add("gtpm.gui.fluid_auto_output.tooltip.disabled", "Fluid Auto-Output Disabled");
        provider.add("gtpm.gui.fluid_auto_input.tooltip.enabled", "Fluid Auto-Input Enabled");
        provider.add("gtpm.gui.fluid_auto_input.tooltip.disabled", "Fluid Auto-Input Disabled");
        provider.add("gtpm.gui.item_auto_output.tooltip.enabled", "Item Auto-Output Enabled");
        provider.add("gtpm.gui.item_auto_output.tooltip.disabled", "Item Auto-Output Disabled");
        provider.add("gtpm.gui.item_auto_input.tooltip.enabled", "Item Auto-Input Enabled");
        provider.add("gtpm.gui.item_auto_input.tooltip.disabled", "Item Auto-Input Disabled");
        multilineLang(provider, "gtpm.gui.charger_slot.tooltip",
                "§fCharger Slot§r\n§7Draws power from %s batteries§r\n§7Charges %s tools and batteries");
        multilineLang(provider, "gtpm.gui.configurator_slot.tooltip",
                "§fConfigurator Slot§r\n§7Place a §6Programmed Circuit§7 in this slot to\n§7change its configured value.\n§7Hold §6Shift§7 when clicking buttons to change by §65.\n§aA Programmed Circuit in this slot is also valid for recipe inputs.§r");
        provider.add("gtpm.gui.fluid_lock.tooltip.enabled", "Fluid Locking Enabled");
        provider.add("gtpm.gui.fluid_lock.tooltip.disabled", "Fluid Locking Disabled");
        provider.add("gtpm.gui.fluid_voiding_partial.tooltip.enabled", "Fluid Voiding Enabled");
        provider.add("gtpm.gui.fluid_voiding_partial.tooltip.disabled", "Fluid Voiding Disabled");
        provider.add("gtpm.gui.item_lock.tooltip.enabled", "Item Locking Enabled");
        provider.add("gtpm.gui.item_lock.tooltip.disabled", "Item Locking Disabled");
        provider.add("gtpm.gui.item_voiding_partial.tooltip.enabled", "Item Voiding Enabled");
        provider.add("gtpm.gui.item_voiding_partial.tooltip.disabled", "Item Voiding Disabled");
        multilineLang(provider, "gtpm.gui.silktouch.enabled",
                "Silk Touch Enabled: Click to Disable.\n§7Switching requires an idle machine.");
        multilineLang(provider, "gtpm.gui.silktouch.disabled",
                "Silk Touch Disabled: Click to Enable.\n§7Switching requires an idle machine.");
        multilineLang(provider, "gtpm.gui.chunkmode.enabled",
                "Chunk Mode Enabled: Click to Disable.\n§7Switching requires an idle machine.");
        multilineLang(provider, "gtpm.gui.chunkmode.disabled",
                "Chunk Mode Disabled: Click to Enable.\n§7Switching requires an idle machine.");
        provider.add("gtpm.gui.multiblock.voiding_mode", "Voiding Mode:");
        provider.add("gtpm.gui.item_voiding", "§7Voiding §6Items");
        provider.add("gtpm.gui.fluid_voiding", "§7Voiding §9Fluids");
        provider.add("gtpm.gui.all_voiding",
                "§7Voiding §cAll");
        provider.add("gtpm.gui.no_voiding", "§7Voiding Nothing");
        multilineLang(provider, "gtpm.gui.fisher_mode.tooltip",
                "Toggle junk items\nOff costs 2 string per operation");
        provider.add("ore.spawnlocation.name", "Ore Spawn Information");
        multiLang(provider, "gtpm.jei.ore.surface_rock",
                "Surface Rocks with this material denote vein spawn locations.",
                "They can be broken for 3 Tiny Piles of the dust, with Fortune giving a bonus.");
        provider.add("gtpm.jei.ore.biome_weighting_title", "§dModified Biome Total Weights:");
        provider.add("gtpm.jei.ore.biome_weighting", "§d%s Weight: §3%d");
        provider.add("gtpm.jei.ore.biome_weighting_no_spawn", "§d%s Weight: §cCannot Spawn");
        provider.add("gtpm.jei.ore.ore_weight", "Weight in vein: %d%%");
        multiLang(provider, "gtpm.jei.ore.primary", "Top Ore", "Spawns in the top %d layers of the vein");
        multiLang(provider, "gtpm.jei.ore.secondary", "Bottom Ore",
                "Spawns in the bottom %d layers of the vein");
        multiLang(provider, "gtpm.jei.ore.between", "Between Ore",
                "Spawns in the middle %d layers of the vein, with other ores");
        multiLang(provider, "gtpm.jei.ore.sporadic", "Sporadic Ore", "Spawns anywhere in the vein");
        provider.add("fluid.spawnlocation.name", "Fluid Vein Information");
        provider.add("gtpm.jei.fluid.vein_weight", "Vein Weight: %d");
        provider.add("gtpm.jei.fluid.min_yield", "Minimum Yield: %d");
        provider.add("gtpm.jei.fluid.max_yield", "Maximum Yield: %d");
        provider.add("gtpm.jei.fluid.depletion_chance", "Depletion Chance: %d%%");
        provider.add("gtpm.jei.fluid.depletion_amount", "Depletion Amount: %d");
        provider.add("gtpm.jei.fluid.depleted_rate", "Depleted Yield: %d");
        provider.add("gtpm.jei.fluid.dimension", "Dimensions:");
        provider.add("gtpm.jei.fluid.weight_hover",
                "The Weight of the vein. Hover over the fluid to see any possible biome modifications");
        provider.add("gtpm.jei.fluid.min_hover",
                "The minimum yield that any fluid vein of this fluid can have");
        provider.add("gtpm.jei.fluid.max_hover",
                "The maximum yield that any fluid vein of this fluid can have");
        provider.add("gtpm.jei.fluid.dep_chance_hover",
                "The percentage chance for the vein to be depleted upon harvest");
        provider.add("gtpm.jei.fluid.dep_amount_hover", "The amount the vein will be depleted by");
        provider.add("gtpm.jei.fluid.dep_yield_hover",
                "The maximum yield of the vein when it is fully depleted");
        provider.add("gtpm.jei.materials.average_mass", "Average mass: %d");
        provider.add("gtpm.jei.materials.average_protons", "Average protons: %d");
        provider.add("gtpm.jei.materials.average_neutrons", "Average neutrons: %d");
        provider.add("gtpm.item_filter.empty_item", "Empty (No Item)");
        provider.add("gtpm.item_filter.footer", "§eClick with item to override");
        provider.add("gtpm.cable.voltage", "§aMax Voltage:§r §a%d §a(%s§a)");
        provider.add("gtpm.cable.amperage", "§eMax Amperage:§r §e%d");
        provider.add("gtpm.cable.loss_per_block", "§cLoss/Meter/Ampere:§r §c%d§7 EU-Volt");
        provider.add("gtpm.cable.superconductor", "%s §dSuperconductor");
        provider.add("gtpm.fluid_pipe.capacity", "§9Capacity: §f%d mB");
        provider.add("gtpm.fluid_pipe.max_temperature", "§cTemperature Limit: §f%s");
        provider.add("gtpm.fluid_pipe.channels", "§eChannels: §f%d");
        provider.add("gtpm.fluid_pipe.gas_proof", "§6Can handle Gases");
        provider.add("gtpm.fluid_pipe.acid_proof", "§6Can handle Acids");
        provider.add("gtpm.fluid_pipe.cryo_proof", "§6Can handle Cryogenics");
        provider.add("gtpm.fluid_pipe.plasma_proof", "§6Can handle all Plasmas");
        provider.add("gtpm.fluid_pipe.not_gas_proof", "§4Gases may leak!");
        provider.add("gtpm.item_pipe.priority", "§9Priority: §f%d");
        provider.add("gtpm.duct_pipe.transfer_rate", "§bAir transfer rate: %s");
        provider.add("gtpm.multiblock.work_paused", "Work Paused.");
        provider.add("gtpm.multiblock.running", "Running perfectly.");
        provider.add("gtpm.multiblock.idling", "§6Idling.");
        provider.add("gtpm.multiblock.research_station.researching", "§6Researching.");
        provider.add("gtpm.multiblock.not_enough_energy", "WARNING: Machine needs more energy.");
        provider.add("gtpm.multiblock.not_enough_energy_output", "WARNING: Energy Dynamo Tier Too Low!");
        provider.add("gtpm.multiblock.waiting", "WARNING: Machine is waiting.");
        provider.add("gtpm.multiblock.total_runs", "Performing %d Recipes at once");
        provider.add("gtpm.multiblock.batch_enabled", "- %dx from Batching");
        provider.add("gtpm.multiblock.subtick_parallels", "- %dx from Overclocking");
        provider.add("gtpm.machine.batch_enabled", "Batching Enabled");
        provider.add("gtpm.machine.batch_disabled", "Batching Disabled");
        provider.add("gtpm.multiblock.progress_percent", "Progress: %s%%");
        provider.add("gtpm.multiblock.progress", "Progress: %ss / %ss (%s%%)");
        provider.add("gtpm.multiblock.output_line.0", "%s x §e%s§r (%ss/ea)");
        provider.add("gtpm.multiblock.output_line.1", "%s x §e%s§r (%s/s)");
        provider.add("gtpm.multiblock.output_line.2", "%s ≈ §e%s§r (%ss/ea)");
        provider.add("gtpm.multiblock.output_line.3", "%s ≈ §e%s§r (%s/s)");
        provider.add("gtpm.multiblock.invalid_structure", "Invalid structure.");
        provider.add("gtpm.multiblock.invalid_structure.tooltip",
                "This block is a controller of the multiblock structure. For building help, see structure template in JEI.");
        provider.add("gtpm.multiblock.validation_failed", "Invalid amount of inputs/outputs.");
        provider.add("gtpm.multiblock.max_recipe_tier", "Max Recipe Tier: %s");
        provider.add("gtpm.multiblock.max_recipe_tier_hover", "The maximum tier of recipes that can be run");
        provider.add("gtpm.multiblock.max_energy_per_tick", "Max EU/t: §a%s (%s§r)");
        provider.add("gtpm.multiblock.max_energy_per_tick_hover",
                "The maximum EU/t available for running recipes or overclocking");
        provider.add("gtpm.multiblock.max_energy_per_tick_amps", "Max EU/t: %s (%sA %s)");
        provider.add("gtpm.multiblock.energy_consumption", "Energy Usage: %s EU/t (%s)");
        provider.add("gtpm.multiblock.generation_eu", "Outputting: §a%s EU/t");
        provider.add("gtpm.multiblock.universal.no_problems", "No Maintenance Problems!");
        provider.add("gtpm.multiblock.universal.has_problems", "Has Maintenance Problems!");
        provider.add("gtpm.multiblock.universal.has_problems_header",
                "Fix the following issues in a Maintenance Hatch:");
        provider.add("gtpm.multiblock.universal.problem.wrench", "§7Pipe is loose. (§aWrench§7)");
        provider.add("gtpm.multiblock.universal.problem.screwdriver", "§7Screws are loose. (§aScrewdriver§7)");
        provider.add("gtpm.multiblock.universal.problem.soft_mallet", "§7Something is stuck. (§aSoft Mallet§7)");
        provider.add("gtpm.multiblock.universal.problem.hard_hammer", "§7Plating is dented. (§aHard Hammer§7)");
        provider.add("gtpm.multiblock.universal.problem.wire_cutter", "§7Wires burned out. (§aWire Cutter§7)");
        provider.add("gtpm.multiblock.universal.problem.crowbar", "§7That doesn't belong there. (§aCrowbar§7)");
        provider.add("gtpm.multiblock.universal.muffler_obstructed", "Muffler Hatch is Obstructed!");
        provider.add("gtpm.multiblock.universal.muffler_obstructed.tooltip",
                "Muffler Hatch must have a block of airspace in front of it.");
        provider.add("gtpm.multiblock.universal.rotor_obstructed", "Rotor is Obstructed!");
        provider.add("gtpm.multiblock.universal.distinct", "Distinct Buses:");
        provider.add("gtpm.multiblock.universal.distinct.no", "No");
        provider.add("gtpm.multiblock.universal.distinct.yes", "Yes");
        provider.add("gtpm.multiblock.universal.distinct.info",
                "If enabled, each Item Input Bus will be treated as fully distinct from each other for recipe lookup. Useful for things like Programmed Circuits, Extruder Shapes, etc.");
        provider.add("gtpm.multiblock.parallel", "Performing up to %d Recipes in Parallel");
        provider.add("gtpm.multiblock.parallel.exact", "- %dx from Parallels");
        provider.add("gtpm.multiblock.multiple_recipemaps.header", "Machine Mode:");
        provider.add("gtpm.multiblock.multiple_recipemaps.tooltip",
                "Screwdriver the controller to change which machine mode to use.");
        provider.add("gtpm.multiblock.multiple_recipemaps_recipes.tooltip", "Machine Modes: §e%s§r");
        provider.add("gtpm.multiblock.multiple_recipemaps.switch_message",
                "The machine must be off to switch modes!");
        provider.add("gtpm.multiblock.preview.zoom", "Use mousewheel or right-click + drag to zoom");
        provider.add("gtpm.multiblock.preview.rotate", "Click and drag to rotate");
        provider.add("gtpm.multiblock.preview.select", "Right-click to check candidates");
        provider.add("gtpm.multiblock.pattern.error", "Expected components (%s) at (%s).");
        provider.add("gtpm.multiblock.pattern.error.limited_exact", "§cExactly: %d§r");
        provider.add("gtpm.multiblock.pattern.error.limited_within", "§cBetween %d and %d§r");
        multiLang(provider, "gtpm.multiblock.pattern.error.limited", "§cMaximum: %d§r", "§cMinimum: %d§r",
                "§cMaximum: %d per layer§r", "§cMinimum: %d per layer§r");
        provider.add("gtpm.multiblock.pattern.error.coils", "§cAll heating coils must be the same§r");
        provider.add("gtpm.multiblock.pattern.error.filters", "§cAll filters must be the same§r");
        provider.add("gtpm.multiblock.pattern.error.batteries", "§cAll batteries must be the same§r");
        provider.add("gtpm.multiblock.pattern.clear_amount_1", "§6Must have a clear 1x1x1 space in front§r");
        provider.add("gtpm.multiblock.pattern.clear_amount_3", "§6Must have a clear 3x3x1 space in front§r");
        provider.add("gtpm.multiblock.pattern.single", "§6Only this block can be used§r");
        provider.add("gtpm.multiblock.pattern.location_end", "§cVery End§r");
        provider.add("gtpm.multiblock.pattern.replaceable_air", "Replaceable by Air");
        provider.add("gtpm.multiblock.autobuild.success",
                "Auto-build completed: placed %d, removed %d, stages %d.");
        provider.add("gtpm.multiblock.autobuild.failed", "Auto-build failed.");
        provider.add("gtpm.multiblock.autobuild.server_only", "Auto-build can only run on the server.");
        provider.add("gtpm.multiblock.autobuild.unknown_structure", "Unknown multiblock structure: %s.");
        provider.add("gtpm.multiblock.autobuild.pattern_unavailable",
                "Multiblock structure pattern is not available: %s.");
        provider.add("gtpm.multiblock.autobuild.permission_denied", "No permission at %s.");
        provider.add("gtpm.multiblock.autobuild.invalid_repeat", "Invalid repeat count: %d.");
        provider.add("gtpm.multiblock.autobuild.flip_not_allowed",
                "This multiblock does not support flipped auto-build.");
        provider.add("gtpm.multiblock.autobuild.unknown_tier_category", "Unknown tier block category: %s.");
        provider.add("gtpm.multiblock.autobuild.invalid_tier", "Invalid tier %2$d for category %1$s.");
        provider.add("gtpm.multiblock.autobuild.unloaded", "Target position is not loaded: %s.");
        provider.add("gtpm.multiblock.autobuild.blocked", "Target position is blocked: %s.");
        provider.add("gtpm.multiblock.autobuild.no_candidate", "No placeable candidate for %s.");
        provider.add("gtpm.multiblock.autobuild.missing_material", "Missing material for %s.");
        provider.add("gtpm.multiblock.autobuild.not_placeable", "%s cannot be placed.");
        provider.add("gtpm.multiblock.autobuild.unsupported_liquid_candidate",
                "Liquid candidate is not supported: %s.");
        provider.add("gtpm.multiblock.autobuild.commit_material_failed", "Could not commit material for %s.");
        provider.add("gtpm.multiblock.autobuild.demolition_failed", "Could not demolish block at %s.");
        provider.add("gtpm.multiblock.autobuild.place_failed", "Could not place block at %s.");
        provider.add("gtpm.multiblock.autobuild.controller_protected", "The controller cannot be removed.");
        provider.add("gtpm.multiblock.autobuild.unbreakable", "Target block is unbreakable: %s.");
        provider.add("gtpm.multiblock.autobuild.me_unavailable", "ME network is unavailable.");
        provider.add("gtpm.multiblock.autobuild.me_no_player_inventory", "Could not access player inventory for ME.");
        provider.add("gtpm.multiblock.autobuild.me_no_linked_terminal", "No linked ME terminal found.");
        provider.add("gtpm.multiblock.autobuild.me_linked_level_missing", "Linked ME dimension is unavailable.");
        provider.add("gtpm.multiblock.autobuild.me_wrong_dimension", "Linked ME access point is in another dimension.");
        provider.add("gtpm.multiblock.autobuild.me_access_point_missing", "Linked ME access point is missing.");
        provider.add("gtpm.multiblock.autobuild.me_access_point_inactive", "Linked ME access point is inactive.");
        provider.add("gtpm.multiblock.autobuild.me_out_of_range", "Linked ME access point is out of range.");
        provider.add("gtpm.multiblock.autobuild.me_grid_missing", "Linked ME grid is missing.");
        provider.add("gtpm.multiblock.autobuild.structure_check_failed",
                "Auto-built structure failed final pattern check: %s.");

        provider.add("gtpm.multiblock.computation.max", "Max CWU/t: %s");
        provider.add("gtpm.multiblock.computation.usage", "Using: %s");
        provider.add("gtpm.multiblock.computation.non_bridging", "Non-bridging connection found");
        provider.add("gtpm.multiblock.computation.non_bridging.detailed",
                "A Reception Hatch is linked to a machine which cannot bridge");
        provider.add("gtpm.multiblock.computation.not_enough_computation", "Machine needs more computation!");

        provider.add("gtpm.chat.cape",
                "§5Congrats: you just unlocked a new cape! See the Cape Selector terminal app to use it.§r");
        provider.add("gtpm.universal.clear_nbt_recipe.tooltip", "§cThis will destroy all contents!");
        provider.add("gtpm.cover.energy_detector.message_electricity_storage_normal",
                "Monitoring Normal Electricity Storage");
        provider.add("gtpm.cover.energy_detector.message_electricity_storage_inverted",
                "Monitoring Inverted Electricity Storage");
        provider.add("gtpm.cover.fluid_detector.message_fluid_storage_normal",
                "Monitoring Normal Fluid Storage");
        provider.add("gtpm.cover.fluid_detector.message_fluid_storage_inverted",
                "Monitoring Inverted Fluid Storage");
        provider.add("gtpm.cover.item_detector.message_item_storage_normal", "Monitoring Normal Item Storage");
        provider.add("gtpm.cover.item_detector.message_item_storage_inverted",
                "Monitoring Inverted Item Storage");
        provider.add("gtpm.cover.activity_detector.message_activity_normal",
                "Monitoring Normal Activity Status");
        provider.add("gtpm.cover.activity_detector.message_activity_inverted",
                "Monitoring Inverted Activity Status");
        provider.add("gtpm.cover.activity_detector_advanced.message_activity_normal",
                "Monitoring Normal Progress Status");
        provider.add("gtpm.cover.activity_detector_advanced.message_activity_inverted",
                "Monitoring Inverted Progress Status");

        multiLang(provider, "metaitem.cover.digital.wireless.tooltip",
                "§fWirelessly§7 connects machines to the §fCentral Monitor§7 as §fCover§7.",
                "§fRight Click§7 on the §fCentral Monitor§7 to remotely bind to it.",
                "§fSneak Right Click§7 to remove the current binding.",
                "§aBinding: §f%s");
        provider.add("monitor.gui.title.back", "Back");
        provider.add("monitor.gui.title.scale", "Scale:");
        provider.add("monitor.gui.title.argb", "ARGB:");
        provider.add("monitor.gui.title.slot", "Slot:");
        provider.add("monitor.gui.title.plugin", "Plugin:");
        provider.add("monitor.gui.title.config", "Config");
        provider.add("fluid.tile.lava", "Lava");
        provider.add("fluid.tile.water", "Water");
        provider.add("gtpm.key.armor_mode_switch", "Armor Mode Switch");
        provider.add("gtpm.key.armor_hover", "Armor Hover Toggle");
        provider.add("gtpm.key.enable_jetpack", "Enable Jetpack");
        provider.add("gtpm.key.enable_boots", "Enable Boosted Jump");
        provider.add("gtpm.key.armor_charging", "Armor Charging to Inventory Toggle");
        provider.add("gtpm.key.tool_aoe_change", "Tool AoE Mode Switch");
        provider.add("gtpm.key.enable_step_assist", "Enable StepAssist");
        provider.add("gtpm.debug.f3_h.enabled",
                "GregTech has modified the debug info! For Developers: enable the misc:debug config option in the GregTech config file to see more");
        provider.add("gtpm.debug.resource_rebuild.done", "Gradle resource rebuild done in %s");
        provider.add("gtpm.debug.resource_rebuild.start",
                "Invoking gradle resource rebuild (./gradlew :processResources)");
        provider.add("config.jade.plugin_gtpm.controllable_provider", "[GTCEu] Controllable");
        provider.add("config.jade.plugin_gtpm.workable_provider", "[GTCEu] Workable");
        provider.add("config.jade.plugin_gtpm.battery_info", "[GTCEu] Battery info");
        provider.add("config.jade.plugin_gtpm.electric_container_provider", "[GTCEu] Electric Container");
        provider.add("config.jade.plugin_gtpm.recipe_logic_provider", "[GTCEu] Recipe Logic");
        provider.add("config.jade.plugin_gtpm.hazard_cleaner_provider", "[GTCEu] Hazard Cleaner");
        provider.add("config.jade.plugin_gtpm.recipe_output_info", "[GTCEu] Recipe Output Info");
        provider.add("config.jade.plugin_gtpm.auto_output_info", "[GTCEu] Auto Output Info");
        provider.add("config.jade.plugin_gtpm.cable_info", "[GTCEu] Cable Info");
        provider.add("config.jade.plugin_gtpm.exhaust_vent_info", "[GTCEu] Exhaust Vent Info");
        provider.add("config.jade.plugin_gtpm.steam_boiler_info", "[GTCEu] Steam Boiler Info");
        provider.add("config.jade.plugin_gtpm.machine_mode", "[GTCEu] Machine Mode");
        provider.add("config.jade.plugin_gtpm.maintenance_info", "[GTCEu] Maintenance Info");
        provider.add("config.jade.plugin_gtpm.multiblock_structure", "[GTCEu] MultiBlock Structure");
        provider.add("config.jade.plugin_gtpm.parallel_info", "[GTCEu] Parallel Info");
        provider.add("config.jade.plugin_gtpm.primitive_pump", "[GTCEu] Primitive Pump Info");
        provider.add("config.jade.plugin_gtpm.data_bank", "[GTCEu] Data Bank Info");
        provider.add("config.jade.plugin_gtpm.transformer", "[GTCEu] Transformer Info");
        provider.add("config.jade.plugin_gtpm.stained_color", "[GTCEu] Stained Block Info");
        provider.add("config.jade.plugin_gtpm.me_pattern_buffer", "[GTCEu] Pattern Buffer Info");
        provider.add("config.jade.plugin_gtpm.me_pattern_buffer_proxy", "[GTCEu] Pattern Buffer Proxy Info");
        provider.add("config.jade.plugin_gtpm.energy_converter_provider", "[GTCEu] Energy Converter Mode");
        provider.add("config.jade.plugin_gtpm.ldp_endpoint", "[GTCEu] Long Distance Pipeline Endpoint Info");

        // gui
        provider.add("gtpm.button.ore_veins", "Show GT Ore Veins");
        provider.add("gtpm.button.bedrock_fluids", "Show Bedrock Fluid Veins");
        provider.add("gtpm.button.hide_depleted", "Hide Depleted Veins");
        provider.add("gtpm.button.show_depleted", "Show Depleted Veins");
        provider.add("gtpm.recipe_type.show_recipes", "Show Recipes");

        provider.add("gtpm.gui.cover_setting.title", "Cover Settings");
        provider.add("gtpm.gui.output_setting.title", "Output Settings");
        provider.add("gtpm.gui.circuit.title", "Circuit Settings");
        multiLang(provider, "gtpm.gui.output_setting.tooltips", "left-click to tune the item auto output",
                "right-click to tune the fluid auto output.");
        provider.add("gtpm.gui.item_auto_output.allow_input.enabled",
                "allow items input from the output side");
        provider.add("gtpm.gui.item_auto_output.allow_input.disabled",
                "disable items input from the output side");
        provider.add("gtpm.gui.item_auto_output.enabled", "Item Auto Output: §aEnabled");
        provider.add("gtpm.gui.item_auto_output.disabled", "Item Auto Output: §cDisabled");
        multilineLang(provider, "gtpm.gui.item_auto_output.unselected",
                """
                        Item Auto Output
                        §7Select a side of the machine to configure its output.
                        """);
        multilineLang(provider, "gtpm.gui.item_auto_output.other_direction",
                """
                        Item Auto Output: §6Other Direction
                        §7The machine's item output is set to another direction.
                        §7Click to move the output to the currently selected side.
                        """);
        provider.add("gtpm.gui.fluid_auto_output.allow_input.enabled",
                "allow fluids input from the output side");
        provider.add("gtpm.gui.fluid_auto_output.allow_input.disabled",
                "disable fluids input from the output side");
        provider.add("gtpm.gui.fluid_auto_output.enabled", "Fluid Auto Output: §aEnabled");
        provider.add("gtpm.gui.fluid_auto_output.disabled", "Fluid Auto Output: §cDisabled");
        multilineLang(provider, "gtpm.gui.fluid_auto_output.unselected",
                """
                        Fluid Auto Output
                        §7Select a side of the machine to configure its output.
                        """);
        multilineLang(provider, "gtpm.gui.fluid_auto_output.other_direction",
                """
                        Fluid Auto Output: §6Other Direction
                        §7The machine's fluid output is set to another direction.
                        §7Click to move the output to the currently selected side.
                        """);
        provider.add("gtpm.gui.auto_output.name", "auto");
        provider.add("gtpm.gui.overclock.title", "Overclock Tier");
        provider.add("gtpm.gui.overclock.range", "Available Tiers [%s, %s]");

        provider.add("gtpm.gui.directional_setting.title", "Directional Setting");
        provider.add("gtpm.gui.directional_setting.tab_tooltip", "Change Directional Setting");

        provider.add("gtpm.gui.machinemode.title", "Active Machine Mode");
        provider.add("gtpm.gui.machinemode", "Active Machine Mode: %s");
        provider.add("gtpm.gui.machinemode.tab_tooltip", "Change active Machine Mode");
        provider.add("gtpm.machine.available_recipe_map_1.tooltip", "Available Recipe Types: %s");
        provider.add("gtpm.machine.available_recipe_map_2.tooltip", "Available Recipe Types: %s, %s");
        provider.add("gtpm.machine.available_recipe_map_3.tooltip", "Available Recipe Types: %s, %s, %s");
        provider.add("gtpm.machine.available_recipe_map_4.tooltip", "Available Recipe Types: %s, %s, %s, %s");

        provider.add("gtpm.gui.content.chance_nc", "§cNot Consumed§r");
        provider.add("gtpm.gui.content.chance_nc_short", "§cNC§r");
        provider.add("gtpm.gui.content.chance_base", "Base Chance: %s%%");
        provider.add("gtpm.gui.content.chance_base_logic", "Base Chance: %s%% (%s)");
        provider.add("gtpm.gui.content.chance_no_boost", "Chance: %s%%");
        provider.add("gtpm.gui.content.chance_no_boost_logic", "Chance: %s%% (%s)");
        provider.add("gtpm.gui.content.chance_tier_boost_plus", "Bonus Chance: +%s%%/tier");
        provider.add("gtpm.gui.content.chance_tier_boost_minus", "Bonus Chance: -%s%%/tier");
        provider.add("gtpm.gui.content.chance_boosted", "Chance at Tier: %s%%");
        provider.add("gtpm.gui.content.chance_boosted_logic", "Chance at Tier: %s%% (%s)");
        provider.add("gtpm.gui.content.count_range", "%s-%sx");
        provider.add("gtpm.gui.content.fluid_range", "%s-%smB");
        provider.add("gtpm.gui.content.range", "%s-%s");
        provider.add("gtpm.gui.content.times_item", "x %s");

        provider.add("gtpm.chance_logic.or", "OR");
        provider.add("gtpm.chance_logic.and", "AND");
        provider.add("gtpm.chance_logic.xor", "XOR");
        provider.add("gtpm.chance_logic.first", "FIRST");
        provider.add("gtpm.chance_logic.none", "NONE");

        provider.add("gtpm.gui.content.per_tick", "§aConsumed/Produced Per Tick§r");
        provider.add("gtpm.gui.content.tips.per_tick_short", "§a/tick§r");
        provider.add("gtpm.gui.content.tips.per_second_short", "§a/second§r");

        provider.add("gtpm.gui.content.units.per_tick", "/t");
        provider.add("gtpm.gui.content.units.per_second", "/s");

        provider.add("gtpm.gui.me_network.online", "Network Status: §2Online§r");
        provider.add("gtpm.gui.me_network.offline", "Network Status: §4Offline§r");
        provider.add("gtpm.gui.waiting_list", "Sending Queue:");
        provider.add("gtpm.gui.config_slot", "§fConfig Slot§r");
        provider.add("gtpm.gui.config_slot.set", "§7Click to §bset/select§7 config slot.§r");
        provider.add("gtpm.gui.config_slot.scroll", "§7Scroll wheel to §achange§7 config amount.§r");
        provider.add("gtpm.gui.config_slot.remove", "§7Right click to §4clear§7 config slot.§r");
        provider.add("gtpm.gui.config_slot.set_only", "§7Click to §bset§7 config slot.§r");
        provider.add("gtpm.gui.config_slot.auto_pull_managed", "§4Disabled:§7 Managed by Auto-Pull");
        provider.add("gtpm.gui.me_bus.auto_pull_button", "Click to toggle automatic item pulling from ME");

        // Decor Stuff
        replace(provider, "block.gtpm.yellow_stripes_block.a", "Yellow Stripes Block");
        replace(provider, "block.gtpm.yellow_stripes_block.b", "Yellow Stripes Block");
        replace(provider, "block.gtpm.yellow_stripes_block.c", "Yellow Stripes Block");
        replace(provider, "block.gtpm.yellow_stripes_block.d", "Yellow Stripes Block");

        // Subtitles
        provider.add("gtpm.subtitle.boiler", "Boiler heating");
        provider.add("gtpm.subtitle.computation", "Computer beeps");
        provider.add("gtpm.subtitle.assembler", "Assembler constructing");
        provider.add("gtpm.subtitle.chainsaw", "Chainsaw revving");
        provider.add("gtpm.subtitle.compressor", "Compressor squeezing");
        provider.add("gtpm.subtitle.centrifuge", "Centrifuge spinning");
        provider.add("gtpm.subtitle.mortar", "Mortar crushing");
        provider.add("gtpm.subtitle.screwdriver", "Screwing");
        provider.add("gtpm.subtitle.saw", "Sawing");
        provider.add("gtpm.subtitle.miner", "Miner excavating");
        provider.add("gtpm.subtitle.turbine", "Turbine whizzing");
        provider.add("gtpm.subtitle.wrench", "Wrench rattling");
        provider.add("gtpm.subtitle.portal_opening", "Portal opens");
        provider.add("gtpm.subtitle.replicator", "Replicator copying");
        provider.add("gtpm.subtitle.arc", "Arcs buzzing");
        provider.add("gtpm.subtitle.combustion", "Combusting");
        provider.add("gtpm.subtitle.portable_scanner", "Scanning");
        provider.add("gtpm.subtitle.macerator", "Macerator crushing");
        provider.add("gtpm.subtitle.jet_engine", "Jet roaring");
        provider.add("gtpm.subtitle.spray_can", "Spraying");
        provider.add("gtpm.subtitle.mixer", "Mixer sloshing");
        provider.add("gtpm.subtitle.fire", "Fire crackling");
        provider.add("gtpm.subtitle.forge_hammer", "Forge Hammer thumping");
        provider.add("gtpm.subtitle.bath", "Bath fizzing");
        provider.add("gtpm.subtitle.soft_hammer", "Soft tap");
        provider.add("gtpm.subtitle.wirecutter", "Wire snipped");
        provider.add("gtpm.subtitle.chemical", "Chemical bubbling");
        provider.add("gtpm.subtitle.file", "File rasping");
        provider.add("gtpm.subtitle.portal_closing", "Portal closes");
        provider.add("gtpm.subtitle.motor", "Motor humming");
        provider.add("gtpm.subtitle.drill", "Drilling");
        provider.add("gtpm.subtitle.cut", "Cutter whirring");
        provider.add("gtpm.subtitle.furnace", "Furnace heating");
        provider.add("gtpm.subtitle.electrolyzer", "Electrolyzer sparking");
        provider.add("gtpm.subtitle.cooling", "Freezer humming");
        provider.add("gtpm.subtitle.plunger", "Plunger popping");
        provider.add("gtpm.subtitle.sus", "Sus...");
        provider.add("gtpm.subtitle.science", "s c i e n c e");
        provider.add("gtpm.subtitle.metal_pipe", "Destruction_Metal_Pole_L_Wave_2_0_0.wav");

        provider.add("effect.gtpm.weak_poison", "Weak Poison");

        provider.add("gtpm.tooltip.potion.header", "§6Contains effects:");
        provider.add("gtpm.tooltip.potion.each", "%s %s §7for§r %s §7ticks with a§r %s%% §7chance of happening§r");

        provider.add("gtpm.direction.tooltip.up", "Up");
        provider.add("gtpm.direction.tooltip.down", "Down");
        provider.add("gtpm.direction.tooltip.left", "Left");
        provider.add("gtpm.direction.tooltip.right", "Right");
        provider.add("gtpm.direction.tooltip.back", "Back");
        provider.add("gtpm.direction.tooltip.front", "Front");

        provider.add("gtpm.tooltip.status.trinary.false", "False");
        provider.add("gtpm.tooltip.status.trinary.true", "True");
        provider.add("gtpm.tooltip.status.trinary.unknown", "Unknown");

        provider.add("gtpm.tooltip.wireless_transmitter_bind",
                "Binding to a transmitter cover at %s %s %s facing %s in %s");
        provider.add("gtpm.tooltip.computer_monitor_config", "Storing computer monitor cover configuration data");
        provider.add("gtpm.tooltip.computer_monitor_data", "Storing data: %s");
        provider.add("gtpm.tooltip.player_name.placeholder_processor", "Placeholder processor");
        provider.add("gtpm.tooltip.player_name.unknown", "Unknown player");

        provider.add("gtpm.display_source.computer_monitor_cover", "Computer Monitor Cover");
        provider.add("gtpm.display_target.computer_monitor_cover", "Computer Monitor Cover");
        multiLang(provider, "gtpm.placeholder_info.energy",
                "Returns the amount of energy stored.",
                "Usage:",
                "  {energy} -> the amount of energy stored");
        multiLang(provider, "gtpm.placeholder_info.energyCapacity",
                "Returns the max amount of energy that can be stored",
                "Usage:",
                "{energyCapacity} -> the energy capacity");
        multiLang(provider, "gtpm.placeholder_info.itemCount",
                "Returns the amount of items (can be filtered).",
                "Usage:",
                "  {itemCount} -> total item amount",
                "  {itemCount <item_id>} -> amount of items with ids equal to item_id",
                "  {itemCount filter <slot_id>} -> amount of items matching filter in specified slot of this cover");
        multiLang(provider, "gtpm.placeholder_info.calc",
                "Returns the result of a math function or operation.",
                "Usage:",
                "  {calc <any_string>} -> any_string",
                "  {calc <round|floor|ceil|sqrt|~> <arg>} -> the result of the specified operation",
                "  {calc <first_arg> <+|-|*|/|//|>>|<<|%> <second_arg>} -> the result of the specified operation");
        multiLang(provider, "gtpm.placeholder_info.if",
                "Returns one of the arguments depending on the condition. The condition is considered true if it is not an empty string and is not equal to 0.",
                "Usage:",
                "  {if <condition> <returned_if_true> [returned_if_false]}");
        multiLang(provider, "gtpm.placeholder_info.obf",
                "Returns the text from the first argument, obfuscated.",
                "Usage:",
                "  {obf <text>} -> obfuscated text");
        multiLang(provider, "gtpm.placeholder_info.underline",
                "Returns the text from the first argument, underlined",
                "Usage:",
                "  {underline <text>} -> underlined text");
        multiLang(provider, "gtpm.placeholder_info.strike",
                "Returns the text from the first text, displaying it as if it was crossed out",
                "Usage:",
                "  {strike <text>} -> crossed-out text");
        multiLang(provider, "gtpm.placeholder_info.color",
                "Returns the text from the second argument, colored with the color from the first argument. All default minecraft chat colors can be used.",
                "Usage:",
                "  {color <color> <text>} -> colored text");
        multiLang(provider, "gtpm.placeholder_info.tick",
                "Returns the amount of ticks passed from when this cover was placed.",
                "Usage:",
                "  {tick} -> the amount of ticks");
        multiLang(provider, "gtpm.placeholder_info.block", "Returns the block symbol (█).",
                "Usage:",
                "  {block} -> '█'");
        multiLang(provider, "gtpm.placeholder_info.repeat",
                "Returns the text from the second arguments, repeated the amount of times specified in the first argument.",
                "Usage:",
                "  {repeat <amount> <text>} -> text repeated the specified amount of times");
        multiLang(provider, "gtpm.placeholder_info.random",
                "Returns a random number in the specified interval (inclusive).",
                "Usage:",
                "  {random <min> <max>} -> a random number between min and max (inclusive)");
        multiLang(provider, "gtpm.placeholder_info.select",
                "Returns the argument at the specified index (starting from 0)",
                "Usage:",
                "  {select <index> [arg1] [arg2] [arg3] ... -> argument at the specified index");
        multiLang(provider, "gtpm.placeholder_info.redstone",
                "Returns the redstone signal strength or sets the redstone output strength",
                "Usage:",
                "  {redstone get <up|down|north|south|east|west>} -> redstone signal strength (0-15) at the specified side",
                "  {redstone get link <slot_index> <freq_slot_index>} -> redstone signal strength of a Create redstone link frequency specified by a linked controller in slot #slot_index. freq_slot_index is the index of the frequency inside the controller (from left to right, 0-6)",
                "  {redstone set <power>} -> empty string, sets the redstone output strength from this cover's side",
                "  {redstone set link <slot_index> <freq_slot_index> <power>} -> empty string, broadcasts the specified redstone power on the specified Create redstone link frequency");
        multiLang(provider, "gtpm.placeholder_info.fluidCount",
                "Returns the amount of fluids (can be filtered).",
                "Usage:",
                "  {fluidCount [fluidId]} -> the amount of all fluids, or the fluid with fluidId if specified");
        multiLang(provider, "gtpm.placeholder_info.displayTarget",
                "Returns the specified line that was transmitted to this cover using a display link.",
                "Usage:",
                "  {displayTarget <line_number>} -> the text on the specified line (line number is 1-100)");
        multiLang(provider, "gtpm.placeholder_info.previousText",
                "Returns the text that was previously displayed by this cover at the specified line (before line-wrapping).",
                "Usage:",
                "  {previousText <line>} -> the text previously displayed on the specified line (index starts at 1)");
        multiLang(provider, "gtpm.placeholder_info.ae2itemCount",
                "Same as itemCount, but counts items in the ME network of the block this cover is attached to.",
                "Note that counting by filter or all items may cause lag!",
                "Usage:",
                "  {itemCount} -> total item amount",
                "  {itemCount <item_id>} -> amount of items with ids equal to item_id",
                "  {itemCount filter <slot_id>} -> amount of items matching filter in specified slot of this cover");
        multiLang(provider, "gtpm.placeholder_info.ae2fluidCount",
                "Same as fluidCount, but counts items in the ME network of the block this cover is attached to.",
                "Note that counting all fluids may cause lag!",
                "Usage:",
                "  {fluidCount [fluidId]} -> the amount of all fluids, or the fluid with fluidId if specified");
        multiLang(provider, "gtpm.placeholder_info.progress",
                "Returns the progress of the currently running recipe of the block this cover is attached to.",
                "Note that progress is an integer between 0 and {maxProgress}",
                "Usage:",
                "  {progress} -> the progress of the currently running recipe");
        multiLang(provider, "gtpm.placeholder_info.maxProgress",
                "Returns the maximum progress of the currently running recipe of the block this cover is attached to.",
                "Example: 'Progress: {calc {calc {progress} / {maxProgress}} * 100}%'",
                "Usage:",
                "  {maxProgress} -> the max progress of the currently running recipe");
        multiLang(provider, "gtpm.placeholder_info.maintenance",
                "Returns a 1 if there are maintenance problems in the block the cover is attached to, 0 otherwise.",
                "Example: 'Maintenance status: {if {maintenance} FIXING\\ REQUIRED OK}'",
                "Usage:",
                "  {maintenance} -> whether there are maintenance problems");
        multiLang(provider, "gtpm.placeholder_info.active",
                "Returns a 1 if the block the cover is attached to is currently running a recipe, 0 otherwise.",
                "Usage:",
                "  {active} -> whether there's a currently running recipe");
        multiLang(provider, "gtpm.placeholder_info.voltage",
                "Returns the voltage in the wire/cable the cover is on.",
                "Usage:",
                "  {voltage} -> the voltage in the wire/cable");
        multiLang(provider, "gtpm.placeholder_info.amperage",
                "Returns the amperage in the wire/cable the cover is on.",
                "Usage:",
                "  {amperage} -> the amperate in the wire/cable");
        multiLang(provider, "gtpm.placeholder_info.ae2energy",
                "Returns the energy currently stored in the ME network of the block this cover is on.",
                "Usage:",
                "  {ae2energy} -> the energy in the ME network (in AE units)");
        multiLang(provider, "gtpm.placeholder_info.ae2maxPower",
                "Returns the energy capacity of the ME network of the block this cover is on.",
                "Usage:",
                "  {ae2maxPower} -> the energy capacity of the ME network");
        multiLang(provider, "gtpm.placeholder_info.ae2powerUsage",
                "Returns the energy consumption of the ME network of the block this cover is on.",
                "Usage:",
                "  {ae2powerUsage} -> the energy consumption of the ME network");
        multiLang(provider, "gtpm.placeholder_info.ae2spatial",
                "Returns information about spatial I/O in the ME network of the block this cover is on.",
                "Usage:",
                "  {ae2spatial power} -> the amount of power required to initiate spatial I/O",
                "  {ae2spatial efficiency} -> the efficiency of the Spatial Containment Structure (SPS)",
                "  {ae2spatial size<X|Y|Z>} -> the size of the SPS along the specified axis (example: 'Size: {sizeX}x{sizeY}x{sizeZ}')");
        multiLang(provider, "gtpm.placeholder_info.ae2crafting",
                "Returns information about auto-crafting in the ME network of the block this cover is on.",
                "Usage:",
                "  {ae2crafting get amount} -> the amount of crafting CPUs in the ME network",
                "  {ae2crafting get <index> storage} -> the amount of crafting storage the specified CPU has",
                "  {ae2crafting get <index> threads} -> the amount of co-processors the specified CPU has",
                "  {ae2crafting get <index> name} -> the name of the specified crafting CPU",
                "  {ae2crafting get <index> selectionMode} -> the selection mode of the specified crafting CPU (used for manual, automatic or both requests)",
                "  {ae2crafting get <index> amount} -> the amount of the item that was requested, or 0 if the CPU is idle",
                "  {ae2crafting get <index> item} -> the display name of the item that was requested, or 0 if the CPU is idle",
                "  {ae2crafting get <index> progress} -> the crafting job progress, or 0 if the CPU is idle",
                "  {ae2crafting get <index> time} -> the amount of time elapsed from the start of the craft (in nanoseconds), or 0 if the CPU is idle");
        multiLang(provider, "gtpm.placeholder_info.count",
                "Returns how many of the provided arguments are equal to the first (compared as strings, so \"0\" != \"0.0\")",
                "Usage:",
                "  {count <arg1> [arg2] [arg3] [arg4] ...} -> the amount of arguments that are equal to the first");
        multiLang(provider, "gtpm.placeholder_info.data",
                "Stores or retrieves some data from a data item (data stick/orb/module) in one of the slots.",
                "If you leave the <index> argument empty, it will be replaced with the value p (p is an integer from 0 to (capacity - 1) that is stored in the data item nbt).",
                "Usage:",
                "  {data get <slot> <index>} -> the data stored in the item in the specified slot",
                "  {data set <slot> <index> <value>} -> sets the data stored in the item in the specified slot, returns an empty string",
                "  {data getp <slot>} -> p",
                "  {data setp <slot> <value>} -> sets p, returns an empty string",
                "  {data inc <slot>} -> increments p by 1, if p becomes more than or equal to capacity, sets p to 0",
                "  {data dec <slot>} -> decrements p by 1, if p becomes less than 0, sets p to (capacity - 1)");
        multiLang(provider, "gtpm.placeholder_info.combine",
                "Combines all of it's arguments into a single string (by escaping all spaces between the arguments)",
                "Example: {combine abc def ghi jkl mno} -> \"abc\\ def\\ ghi\\ jkl\\ mno\"",
                "Usage:",
                "  {combine [arg1] [arg2] [arg3] ...} -> a string that will be treated as a single argument in further placeholders");
        multiLang(provider, "gtpm.placeholder_info.nbt",
                "Returns the nbt data of the item in the specified slot",
                "Usage:",
                "  {nbt <slot> [key1] [key2] [key3] ...} -> item_nbt[key1][key2][key3][...]");
        multiLang(provider, "gtpm.placeholder_info.toChars",
                "Returns the characters of the provided string with spaces between them",
                "Example: {toChars example} -> 'e x a m p l e'",
                "Usage:",
                "  {toChars <arg>} -> characters");
        multiLang(provider, "gtpm.placeholder_info.toAscii",
                "Returns the ASCII code of the provided character",
                "Usage:",
                "  {toAscii <character>} -> ASCII code of the character");
        multiLang(provider, "gtpm.placeholder_info.fromAscii",
                "Returns the character represented by the provided ASCII code",
                "Usage:",
                "  {fromAscii <char_code>} -> a character");
        multiLang(provider, "gtpm.gui.computer_monitor_cover.placeholder_reference",
                "All placeholders:",
                "(hover for more info)");
        multiLang(provider, "gtpm.placeholder_info.subList",
                "Returns arguments from with indexes from l (inclusive) to r (exclusive) (starting from 0)",
                "Usage:",
                "  {subList <left> <right> [arg0] [arg1] ...} -> all arguments with indexes from l to r separated by spaces");
        multiLang(provider, "gtpm.placeholder_info.cmp",
                "Returns a 1 or 0 based on the expression in it's arguments",
                "Usage:",
                "  {cmp <a> <operator> <b>} -> 1 or 0, operator is one of >, <, >=, <=, ==, !=");
        multiLang(provider, "gtpm.placeholder_info.bf",
                "Usage:",
                "  {bf <data_item_slot_index> <code>} -> empty string");
        multiLang(provider, "gtpm.placeholder_info.cmd",
                "Executes Minecraft commands and returns their output.",
                "Requires a data item bound to a player, bind any data item to yourself by right-clicking with it.",
                "Usage:",
                "  {cmd <slot_index> <command>} -> command output");
        multiLang(provider, "gtpm.placeholder_info.tm",
                "Returns the ™ symbol",
                "Usage:",
                "  {tm} -> the ™ symbol");
        multiLang(provider, "gtpm.placeholder_info.formatInt",
                "Returns a string representation of the provided integer",
                "Example: {formatInt 1236457} -> 1.24M",
                "Usage:",
                "  {formatInt <arg>} -> string representation of the int");
        multiLang(provider, "gtpm.placeholder_info.click",
                "Returns whether the targeted advanced monitor was clicked before the current tick",
                "Usage:",
                "  {click} -> \"1\" if the targeted advanced monitor was clicked, \"0\" otherwise",
                "  {click x} -> the x position of the last click (between 0 and 1)",
                "  {click y} -> the y position of the last click (between 0 and 1)");
        multiLang(provider, "gtpm.placeholder_info.ender",
                "Interacts with ender link covers",
                "Can interact with private channels if provided with a data item bound to a player",
                "Usage:",
                "  {ender item <channel> [player_data_item_slot]} -> item count",
                "  {ender itemPull <channel> [player_data_item_slot]} -> pull 1 item from the ender link's buffer",
                "  {ender itemPush <channel> [player_data_item_slot]} -> push 1 item to the ender link's buffer",
                "  {ender itemId <channel> [player_data_item_slot]} -> the id of the item in the ender link's buffer (ex. \"26 minecraft:dirt\")",
                "  {ender fluid <channel> [player_data_item_slot]} -> fluid count",
                "  {ender redstone <channel> [player_data_item_slot] -> redstone signal level",
                "  {ender redstone <channel> <player_data_item_slot> <signal> -> sets the redstone signal outputed to the ender redstone link, returns empty string",
                "The player_data_item_slot argument may be left empty (not 0, empty string)");
        multiLang(provider, "gtpm.placeholder_info.eval",
                "Returns the result of evaluating the provided string which may placeholders",
                "Usage:",
                "  {eval abcdefg} -> abcdefg",
                "  {eval \"repeating a: {repeat 5 \\\"a \\\"}\" -> repeating a: a a a a a ",
                "  {eval \\\"\"{some random text}\"\\\" -> {some random text}",
                "  {eval \"text \"\\\"\"{something with spaces}\"\\\"\" more text\" -> text {something with spaces} more text");
        multiLang(provider, "gtpm.placeholder_info.module",
                "Renders the module in the specified slot onto the central monitor (does not work in a cover)",
                "Usage:",
                "  {module <slot> <x> <y>} -> empty string");
        multiLang(provider, "gtpm.placeholder_info.setImage",
                "Sets the image URL in an image module in the specified slot",
                "Usage:",
                "  {setImage <slot> <url>} -> empty string");
        multiLang(provider, "gtpm.placeholder_info.rect",
                "Draws a rectangle at the specified position with the specified coordinates and size",
                "Usage:",
                "  {rect <x> <y> <width> <height> <colorARGB>} -> empty string",
                "  {rect 0.5 0.25 2 1 0xFFFFFFFF} -> draws a white rectangle at (0.5, 0.25) with the size (2, 1)");
        multiLang(provider, "gtpm.placeholder_info.quad",
                "Draws a quad (must specify parameters for all 4 vertices)",
                "Usage:",
                "  {quad <x1> <y1> <x2> <y2> <x3> <y3> <x4> <y4> <color1> <color2> <color3> <color4>} -> empty string");
        multiLang(provider, "gtpm.placeholder_info.item",
                "Returns the amount and id of the item in a specified slot",
                "Usage:",
                "  {item <slot>} -> \"31 minecraft:diamond\" (for example)");
        multiLang(provider, "gtpm.placeholder_info.bufferText",
                "Returns the text from a buffer accessible by ComputerCraft",
                "Usage:",
                "  {bufferText <line>} -> text from the buffer on the specified line (line is 1-100)");
        multiLang(provider, "gtpm.placeholder_info.blockNbt",
                "Returns the data components of the block entity",
                "Usage:",
                "  {blockNbt} -> full block entity data component map",
                "  {blockNbt <component_id>} -> the value of a data component");
        provider.add("gtpm.ender_item_link_cover.title", "Ender Item Link");
        provider.add("gtpm.ender_item_link_cover.tooltip",
                "§7Transports §fItems§7 with a §fWireless §dEnder§f Connection§7 as §fCover§7.");
        provider.add("gtpm.ender_redstone_link_cover.title", "Ender Redstone Link");
        provider.add("gtpm.ender_redstone_link_cover.label", "Redstone power: %d");
        provider.add("gtpm.ender_redstone_link_cover.tooltip",
                "§7Transmits §fRedstone signals§7 with a §fWireless §dEnder§f Connection§7 as §fCover§7.");
        provider.add("gtpm.gui.computer_monitor_cover.update_interval", "Update interval (in ticks)");
        provider.add("gtpm.gui.computer_monitor_cover.edit_blank_placeholders", "Edit blank placeholders");
        provider.add("gtpm.gui.computer_monitor_cover.edit_displayed_text", "Edit displayed text");
        provider.add("gtpm.gui.central_monitor.text_scale", "Text scale");
        provider.add("gtpm.gui.central_monitor.group", "Group: %s");
        provider.add("gtpm.gui.central_monitor.group_default_name", "Group #%d");
        provider.add("gtpm.gui.central_monitor.none", "none");
        provider.add("gtpm.central_monitor.size", "Size: (%d+1+%d)x(%d+1+%d)");
        provider.add("gtpm.computer_monitor_cover.error.invalid_number", "Invalid number '%s'!");
        provider.add("gtpm.computer_monitor_cover.error.wrong_number_of_args", "Expected %d args, got %d!");
        provider.add("gtpm.computer_monitor_cover.error.not_enough_args", "Expected at least %d args, got %d!");
        provider.add("gtpm.computer_monitor_cover.error.no_cover", "No cover!");
        provider.add("gtpm.computer_monitor_cover.error.exception", "Unexpected exception occurred: %s");
        provider.add("gtpm.computer_monitor_cover.error.not_in_range",
                "Expected %s to be between %d and %d (inclusive), got %d");
        provider.add("gtpm.computer_monitor_cover.error.invalid_args", "Invalid arguments!");
        provider.add("gtpm.computer_monitor_cover.error.missing_item", "Missing %s in slot %d!");
        provider.add("gtpm.computer_monitor_cover.error.bf_invalid_num",
                "Invalid number at index %d when processing symbol number %d");
        provider.add("gtpm.computer_monitor_cover.error.bf_invalid", "Invalid character at %d");
        multiLang(provider, "gtpm.gui.computer_monitor_cover.main_textbox_tooltip",
                "Input string to display on line %d here.",
                "It can have placeholders, for example: 'Energy: {energy}/{energyCapacity} EU'",
                "Placeholders can also be inside other placeholders.");
        multiLang(provider, "gtpm.gui.computer_monitor_cover.slot_tooltip",
                "A slot for items that some placeholders can reference",
                "Slot number: %d");
        multiLang(provider, "gtpm.gui.computer_monitor_cover.second_page_textbox_tooltip",
                "Input placeholder to be used in place of %s '{}' here.",
                "For example, you can have a string 'Energy: {}/{} EU' and 'energy' and 'energyCapacity' in these text boxes.");;
        provider.add("gtpm.computer_monitor_cover.error.no_placeholder", "No such placeholder: '%s'!");
        provider.add("gtpm.computer_monitor_cover.error.unclosed_bracket", "Unclosed bracket!");
        provider.add("gtpm.computer_monitor_cover.error.unexpected_bracket", "Unexpected closing bracket!");
        provider.add("gtpm.computer_monitor_cover.error.no_ae", "Cover holder does not have an AE2 network!");
        provider.add("gtpm.computer_monitor_cover.error.not_supported",
                "This feature is not supported by this block/cover!");
        provider.add("gtpm.central_monitor.gui.create_group", "Create group");
        provider.add("gtpm.central_monitor.gui.remove_from_group", "Remove from group");
        provider.add("gtpm.central_monitor.gui.set_target", "Set target");
        provider.add("gtpm.central_monitor.gui.currently_editing", "Currently editing: %s");
        multiLang(provider, "gtpm.central_monitor.info_tooltip",
                "In order to use monitors, you have to split them into groups first. A group may only have 1 module in it.",
                "Select them by left-clicking, then click 'Create group'.",
                "Then in the settings page for the group you can insert a module, you can configure it in the same page.",
                "To delete a group, select all of it's components and click 'Remove from group'.",
                "You can quickly select all components of a group by clicking on it's name. Click again to unselect.",
                "Some modules may display things depending on the block they target, to set a target for a group select any component of that group and right-click on the target component.",
                "You may wish to select a target that is not in the multiblock, you have to use the wireless transmitter cover for that.",
                "Place the cover on the target block, right-click it with a data stick and put that data stick into a data access hatch in the multiblock.",
                "Then select the data access hatch as the target, and set the slot index of your data stick in the number field that appeared.");
        provider.add("gtpm.tooltip.player_bind", "Bound to player: %s");
    }

    /**
     * Returns the sub-key consisting of the given key plus the given index.<br>
     * E.g.,<br>
     *
     * <pre>
     * <code>getSubKey("terminal.fluid_prospector.tier", 0)</code>
     * </pre>
     *
     * returns the <code>String</code>:
     *
     * <pre>
     * <code>
     * "terminal.fluid_prospector.tier.0"</code>
     * </pre>
     *
     * @param key   Base key of the sub-key.
     * @param index Index of the sub-key.
     * @return Sub-key consisting of key and index.
     */
    protected static String getSubKey(String key, int index) {
        return key + "." + index;
    }

    /**
     * Registers multiple values under the same key with a given provider.<br>
     * <br>
     * For example, a cumbersome way to add translations would be the following:<br>
     *
     * <pre>
     * <code>provider.add("terminal.fluid_prospector.tier.0", "radius size 1");
     * provider.add("terminal.fluid_prospector.tier.1", "radius size 2");
     * provider.add("terminal.fluid_prospector.tier.2", "radius size 3");</code>
     * </pre>
     *
     * Instead, <code>multiLang</code> can be used for the same result:
     *
     * <pre>
     * <code>multiLang(provider, "terminal.fluid_prospector.tier", "radius size 1", "radius size 2", "radius size 3");</code>
     * </pre>
     *
     * In situations requiring a large number of generated translations, the
     * following could be used instead, which
     * generates translations for 100 tiers:
     *
     * <pre>
     * <code>multiLang(provider, "terminal.fluid_prospector.tier", IntStream.of(100)
     *                 .map(i -> i + 1)
     *                 .mapToObj(Integer::toString)
     *                 .map(i -> "radius size " + i)
     *                 .toArray(String[]::new));</code>
     * </pre>
     *
     * @param provider The provider to add to.
     * @param key      Base key of the key-value-pairs. The real key for each
     *                 translation will be appended by ".0" for
     *                 the first, ".1" for the second, etc. This ensures that the
     *                 keys are unique.
     * @param values   All translation values.
     */
    protected static void multiLang(RegistrateLangProvider provider, String key, String... values) {
        for (var i = 0; i < values.length; i++) {
            provider.add(getSubKey(key, i), values[i]);
        }
    }

    /**
     * Gets all translation components from a multi lang's sub-keys.<br>
     * E.g., given a multi lang:
     *
     * <pre>
     * <code>multiLang(provider, "terminal.fluid_prospector.tier", "radius size 1", "radius size 2", "radius size 3");</code>
     * </pre>
     *
     * The following code can be used to print out the translations:
     *
     * <pre>
     * <code>for (var component : getMultiLang("terminal.fluid_prospector.tier")) {
     *     System.out.println(component.getString());
     * }</code>
     * </pre>
     *
     * Result:
     *
     * <pre>
     * <code>radius size 1
     * radius size 2
     * radius size 3</code>
     * </pre>
     *
     * @param key Base key of the multi lang. E.g. "terminal.fluid_prospector.tier".
     * @return Returns all translation components from a multi lang's sub-keys
     */
    public static List<MutableComponent> getMultiLang(String key) {
        var outputKeys = new ArrayList<String>();
        var i = 0;
        var next = getSubKey(key, i);
        while (Language.getInstance().has(next)) {
            outputKeys.add(next);
            next = getSubKey(key, ++i);
        }
        return outputKeys.stream().map(Component::translatable).collect(Collectors.toList());
    }

    /**
     * Gets all translation components from a multi lang's sub-keys. Supports
     * additional arguments for the translation
     * components.<br>
     * E.g., given a multi lang:
     *
     * <pre>
     * <code>multiLang(provider, "terminal.fluid_prospector.tier", "radius size 1", "radius size 2", "radius size 3");</code>
     * </pre>
     *
     * The following code can be used to print out the translations:
     *
     * <pre>
     * <code>for (var component : getMultiLang("terminal.fluid_prospector.tier")) {
     *     System.out.println(component.getString());
     * }</code>
     * </pre>
     *
     * Result:
     *
     * <pre>
     * <code>radius size 1
     * radius size 2
     * radius size 3</code>
     * </pre>
     *
     * @param key Base key of the multi lang. E.g. "terminal.fluid_prospector.tier".
     * @return Returns all translation components from a multi lang's sub-keys.
     */
    public static List<MutableComponent> getMultiLang(String key, Object... args) {
        var outputKeys = new ArrayList<String>();
        var i = 0;
        var next = getSubKey(key, i);
        while (Language.getInstance().has(next)) {
            outputKeys.add(next);
            next = getSubKey(key, ++i);
        }
        return outputKeys.stream().map(k -> Component.translatable(k, args)).collect(Collectors.toList());
    }

    /**
     * See {@link #getMultiLang(String)}. If no multiline key is available, get
     * single instead.
     *
     * @param key Base key of the multi lang. E.g. "terminal.fluid_prospector.tier".
     * @return Returns all translation components from a multi lang's sub-keys.
     */
    public static List<MutableComponent> getSingleOrMultiLang(String key) {
        List<MutableComponent> multiLang = getMultiLang(key);

        if (!multiLang.isEmpty()) {
            return multiLang;
        }

        return List.of(Component.translatable(key));
    }

    /**
     * Gets a single translation from a multi lang.
     *
     * @param key   Base key of the multi lang. E.g. "gtpm.gui.overclock.enabled".
     * @param index Index of the single translation. E.g. 3 would return
     *              "gtpm.gui.overclock.enabled.3".
     * @return Returns a single translation from a multi lang.
     */
    public static MutableComponent getFromMultiLang(String key, int index) {
        return Component.translatable(getSubKey(key, index));
    }

    /**
     * Gets a single translation from a multi lang. Supports additional arguments
     * for the translation component.
     *
     * @param key   Base key of the multi lang. E.g. "gtpm.gui.overclock.enabled".
     * @param index Index of the single translation. E.g. 3 would return
     *              "gtpm.gui.overclock.enabled.3".
     * @return Returns a single translation from a multi lang.
     */
    public static MutableComponent getFromMultiLang(String key, int index, Object... args) {
        return Component.translatable(getSubKey(key, index), args);
    }

    /**
     * Adds one key-value-pair to the given lang provider per line in the given
     * multiline (a multiline is a String
     * containing newline characters).<br>
     * Example:
     *
     * <pre>
     * <code>multilineLang(provider, "gtpm.gui.overclock.enabled", "Overclocking Enabled.\nClick to Disable");</code>
     * </pre>
     *
     * This results in the following translations:<br>
     *
     * <pre>
     * <code>"gtpm.gui.overclock.enabled.0": "Overclocking Enabled.",
     * "gtpm.gui.overclock.enabled.1": "Click to Disable",</code>
     * </pre>
     *
     * @param provider  The provider to add to.
     * @param key       Base key of the key-value-pair. The real key for each line
     *                  will be appended by ".0" for the
     *                  first line, ".1" for the second, etc. This ensures that the
     *                  keys are unique.
     * @param multiline The multiline string. It is a multiline because it contains
     *                  at least one newline character '\n'.
     */
    protected static void multilineLang(RegistrateLangProvider provider, String key, String multiline) {
        var lines = multiline.split("\n");
        multiLang(provider, key, lines);
    }

    /**
     * Replace a value in a language provider's mappings
     *
     * @param provider the provider whose mappings should be modified
     * @param key      the key for the value
     * @param value    the value to use in place of the old one
     */
    public static void replace(@NotNull RegistrateLangProvider provider, @NotNull String key,
                               @NotNull String value) {
        try {
            // the regular lang mappings
            Field field = LanguageProvider.class.getDeclaredField("data");
            field.setAccessible(true);
            // noinspection unchecked
            Map<String, String> map = (Map<String, String>) field.get(provider);
            map.put(key, value);

            // upside-down lang mappings
            Field upsideDownField = RegistrateLangProvider.class.getDeclaredField("upsideDown");
            upsideDownField.setAccessible(true);
            // noinspection unchecked
            map = (Map<String, String>) field.get(upsideDownField.get(provider));

            Method toUpsideDown = RegistrateLangProvider.class.getDeclaredMethod("toUpsideDown",
                    String.class);
            toUpsideDown.setAccessible(true);

            map.put(key, (String) toUpsideDown.invoke(provider, value));
        } catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Error replacing entry in datagen.", e);
        }
    }
}
