package com.gregtechceu.gtceu.data.lang;

import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.registries.BuiltInRegistries;

import com.tterrag.registrate.providers.RegistrateLangProvider;

import static com.gregtechceu.gtceu.data.lang.LangHandler.multilineLang;
import static com.gregtechceu.gtceu.data.lang.LangHandler.replace;
import static com.gregtechceu.gtceu.utils.FormattingUtil.toEnglishName;

public class ItemLang {

    public static void init(RegistrateLangProvider provider) {
        initGeneratedNames(provider);
        initItemNames(provider);
        initItemTooltips(provider);
    }

    private static void initGeneratedNames(RegistrateLangProvider provider) {
        // RecipeTypes
        for (var recipeType : BuiltInRegistries.RECIPE_TYPE) {
            if (recipeType instanceof GTRecipeType gtRecipeType) {
                provider.add(gtRecipeType.getTranslationKey(), toEnglishName(gtRecipeType.registryName.getPath()));
            }
        }

        // Recipe Categories
        provider.add("recipe_category.gtpm.arc_furnace_recycling", "Arc Scrapping");
        provider.add("recipe_category.gtpm.macerator_recycling", "Part Grinding");
        provider.add("recipe_category.gtpm.extractor_recycling", "Scrap Remelting");
        provider.add("recipe_category.gtpm.ore_crushing", "Ore Grinding");
        provider.add("recipe_category.gtpm.ore_forging", "Ore Crushing");
        provider.add("recipe_category.gtpm.ore_bathing", "Ore Treating");
        provider.add("recipe_category.gtpm.chem_dyes", "Chemical Dyeing");
        provider.add("recipe_category.gtpm.ingot_molding", "Metal Molding");

        // TagPrefix
        for (TagPrefix tagPrefix : GTRegistries.TAG_PREFIXES) {
            provider.add(tagPrefix.getUnlocalizedName(), tagPrefix.langValue);
        }
        // GTToolType
        for (GTToolType toolType : GTToolType.getTypes().values()) {
            provider.add(toolType.getUnlocalizedName(), toEnglishName(toolType.name));
        }

        provider.add("tagprefix.polymer.plate", "%s Sheet");
        provider.add("tagprefix.polymer.foil", "Thin %s Sheet");
        provider.add("tagprefix.polymer.nugget", "%s Chip");
        provider.add("tagprefix.polymer.dense_plate", "Dense %s Sheet");
        provider.add("tagprefix.polymer.double_plate", "Double %s Sheet");
        provider.add("tagprefix.polymer.tiny_dust", "Tiny Pile of %s Pulp");
        provider.add("tagprefix.polymer.small_dust", "Small Pile of %s Pulp");
        provider.add("tagprefix.polymer.dust", "%s Pulp");
        provider.add("tagprefix.polymer.ingot", "%s Ingot");
    }

    private static void initItemNames(RegistrateLangProvider provider) {
        replace(provider, "item.gtpm.tungsten_steel_fluid_cell", "%s Tungstensteel Cell");
    }

    private static void initItemTooltips(RegistrateLangProvider provider) {
        provider.add("item.gtpm.empty_mold.tooltip", "§7Raw Plate to make Molds and Extrude Shapes");
        provider.add("item.gtpm.nano_saber.tooltip", "§7Ryujin no ken wo kurae!");
        provider.add("item.gtpm.plate_casting_mold.tooltip", "§7Mold for making Plates");
        provider.add("item.gtpm.casing_casting_mold.tooltip", "§7Mold for making Item Casings");
        provider.add("item.gtpm.gear_casting_mold.tooltip", "§7Mold for making Gears");
        provider.add("item.gtpm.bottle_casting_mold.tooltip", "§7Mold for making Bottles");
        provider.add("item.gtpm.ingot_casting_mold.tooltip", "§7Mold for making Ingots");
        provider.add("item.gtpm.ball_casting_mold.tooltip", "§7Mold for making Balls");
        provider.add("item.gtpm.tiny_pipe_casting_mold.tooltip", "§7Mold for making tiny Pipes");
        provider.add("item.gtpm.small_pipe_casting_mold.tooltip", "§7Mold for making small Pipes");
        provider.add("item.gtpm.normal_pipe_casting_mold.tooltip", "§7Mold for making Pipes");
        provider.add("item.gtpm.large_pipe_casting_mold.tooltip", "§7Mold for making large Pipes");
        provider.add("item.gtpm.huge_pipe_casting_mold.tooltip", "§7Mold for making full Block Pipes");
        provider.add("item.gtpm.block_casting_mold.tooltip", "§7Mold for making Blocks");
        provider.add("item.gtpm.nugget_casting_mold.tooltip", "§7Mold for making Nuggets");
        provider.add("item.gtpm.cylinder_casting_mold.tooltip", "§7Mold for shaping Cylinders");
        provider.add("item.gtpm.anvil_casting_mold.tooltip", "§7Mold for shaping Anvils");
        provider.add("item.gtpm.name_casting_mold.tooltip",
                "§7Mold for naming Items in the Forming Press (rename Mold with Anvil)");
        provider.add("item.gtpm.small_gear_casting_mold.tooltip", "§7Mold for making small Gears");
        provider.add("item.gtpm.rotor_casting_mold.tooltip", "§7Mold for making Rotors");
        provider.add("item.gtpm.plate_extruder_mold.tooltip", "§7Extruder Shape for making Plates");
        provider.add("item.gtpm.rod_extruder_mold.tooltip", "§7Extruder Shape for making Rods");
        provider.add("item.gtpm.bolt_extruder_mold.tooltip", "§7Extruder Shape for making Bolts");
        provider.add("item.gtpm.ring_extruder_mold.tooltip", "§7Extruder Shape for making Rings");
        provider.add("item.gtpm.cell_extruder_mold.tooltip", "§7Extruder Shape for making Cells");
        provider.add("item.gtpm.ingot_extruder_mold.tooltip",
                "§7Extruder Shape for, wait, can't we just use a Furnace?");
        provider.add("item.gtpm.wire_extruder_mold.tooltip", "§7Extruder Shape for making Wires");
        provider.add("item.gtpm.casing_extruder_mold.tooltip", "§7Extruder Shape for making Item Casings");
        provider.add("item.gtpm.tiny_pipe_extruder_mold.tooltip", "§7Extruder Shape for making tiny Pipes");
        provider.add("item.gtpm.small_pipe_extruder_mold.tooltip", "§7Extruder Shape for making small Pipes");
        provider.add("item.gtpm.normal_pipe_extruder_mold.tooltip", "§7Extruder Shape for making Pipes");
        provider.add("item.gtpm.large_pipe_extruder_mold.tooltip", "§7Extruder Shape for making large Pipes");
        provider.add("item.gtpm.huge_pipe_extruder_mold.tooltip", "§7Extruder Shape for making full Block Pipes");
        provider.add("item.gtpm.block_extruder_mold.tooltip", "§7Extruder Shape for making Blocks");
        provider.add("item.gtpm.sword_extruder_mold.tooltip", "§7Extruder Shape for making Swords");
        provider.add("item.gtpm.pickaxe_extruder_mold.tooltip", "§7Extruder Shape for making Pickaxes");
        provider.add("item.gtpm.shovel_extruder_mold.tooltip", "§7Extruder Shape for making Shovels");
        provider.add("item.gtpm.axe_extruder_mold.tooltip", "§7Extruder Shape for making Axes");
        provider.add("item.gtpm.hoe_extruder_mold.tooltip", "§7Extruder Shape for making Hoes");
        provider.add("item.gtpm.hammer_extruder_mold.tooltip", "§7Extruder Shape for making Hammers");
        provider.add("item.gtpm.file_extruder_mold.tooltip", "§7Extruder Shape for making Files");
        provider.add("item.gtpm.saw_extruder_mold.tooltip", "§7Extruder Shape for making Saws");
        provider.add("item.gtpm.gear_extruder_mold.tooltip", "§7Extruder Shape for making Gears");
        provider.add("item.gtpm.bottle_extruder_mold.tooltip", "§7Extruder Shape for making Bottles");
        provider.add("item.gtpm.small_gear_extruder_mold.tooltip", "§7Extruder Shape for making Small Gears");
        provider.add("item.gtpm.foil_extruder_mold.tooltip", "§7Extruder Shape for making Foils from Non-Metals");
        provider.add("item.gtpm.long_rod_extruder_mold.tooltip", "§7Extruder Shape for making Long Rods"); // unused
        provider.add("item.gtpm.rotor_extruder_mold.tooltip", "§7Extruder Shape for making Rotors");
        provider.add("item.gtpm.empty_spray_can.tooltip", "§7Can be filled with sprays of various colors");
        provider.add("fluid_cell.empty", "Empty");
        provider.add("item.gtpm.tool.matchbox.tooltip", "§7This is not a Car");
        provider.add("item.gtpm.tool.lighter.platinum.tooltip", "§7A known Prank Master is engraved on it");
        provider.add("item.gtpm.lv_battery_hull.tooltip", "§7An empty LV Battery Hull");
        provider.add("item.gtpm.mv_battery_hull.tooltip", "§7An empty §bMV §7Battery Hull");
        provider.add("item.gtpm.hv_battery_hull.tooltip", "§7An empty §6HV §7Battery Hull");
        provider.add("item.gtpm.ev_battery_hull.tooltip", "§7An empty §5EV §7Battery Hull");
        provider.add("item.gtpm.iv_battery_hull.tooltip", "§7An empty §1IV §7Battery Hull");
        provider.add("item.gtpm.luv_battery_hull.tooltip", "§7An empty §dLuV §7Battery Hull");
        provider.add("item.gtpm.zpm_battery_hull.tooltip", "§7An empty §fZPM §7Battery Hull");
        provider.add("item.gtpm.uv_battery_hull.tooltip", "§7An empty §3UV §7Battery Hull");
        provider.add("item.gtpm.battery.charge_time", "§aHolds %s %s of Power (%s)");
        provider.add("item.gtpm.battery.charge_detailed", "%s/%s EU§7 - Tier %s §7(%s/%s %s remaining§7)");
        provider.add("item.gtpm.battery.charge_unit.second", "seconds");
        provider.add("item.gtpm.battery.charge_unit.minute", "minutes");
        provider.add("item.gtpm.battery.charge_unit.hour", "hours");
        provider.add("item.gtpm.ulv_tantalum_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.lv_cadmium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.lv_lithium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.lv_sodium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.mv_cadmium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.mv_lithium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.mv_sodium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.hv_cadmium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.hv_lithium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.hv_sodium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.ev_vanadium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.iv_vanadium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.luv_vanadium_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.zpm_naquadria_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.uv_naquadria_battery.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.energy_crystal.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.lapotron_crystal.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.lapotronic_energy_orb.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.lapotronic_energy_orb_cluster.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.energy_module.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.energy_cluster.tooltip", "§7Reusable Battery");
        provider.add("item.gtpm.max_battery.tooltip", "§7Fill this to win Minecraft");
        provider.add("item.gtpm.electric.pump.tooltip", "§7Transfers §fFluids§7 at specific rates as §fCover§7.");
        provider.add("item.gtpm.fluid.regulator.tooltip", "§7Limits §fFluids§7 to specific quantities as §fCover§7.");
        provider.add("item.gtpm.conveyor.module.tooltip", "§7Transfers §fItems§7 at specific rates as §fCover§7.");
        provider.add("item.gtpm.robot.arm.tooltip", "§7Limits §fItems§7 to specific quantities as §fCover§7.");
        provider.add("item.gtpm.data_stick.tooltip", "§7A Low Capacity Data Storage");
        provider.add("item.gtpm.data_orb.tooltip", "§7A High Capacity Data Storage");
        multilineLang(provider, "item.gtpm.programmed_circuit.tooltip",
                "Use to open configuration GUI\nShift-Right-Click on a machine\nwith a circuit slot to set it to\nthis circuit's value.");
        provider.add("item.gtpm.circuit.integrated.gui", "§7Programmed Circuit Configuration");
        // multilineLang(provider, "item.gtpm.circuit.integrated.jei_description", "JEI is only showing recipes for the
        // given configuration.\n\nYou can select a configuration in the Programmed Circuit configuration tab.");
        provider.add("item.glass_lens", "Glass Lens (White)"); // todo move to material overrides
        provider.add("item.gtpm.silicon_boule.tooltip", "§7Raw Circuit");
        provider.add("item.gtpm.phosphorus_boule.tooltip", "§7Raw Circuit");
        provider.add("item.gtpm.naquadah_boule.tooltip", "§7Raw Circuit");
        provider.add("item.gtpm.neutronium_boule.tooltip", "§7Raw Circuit");
        provider.add("item.gtpm.silicon_wafer.tooltip", "§7Raw Circuit");
        provider.add("item.gtpm.phosphorus_wafer.tooltip", "§7Raw Circuit");
        provider.add("item.gtpm.naquadah_wafer.tooltip", "§7Raw Circuit");
        provider.add("item.gtpm.neutronium_wafer.tooltip", "§7Raw Circuit");
        provider.add("item.gtpm.resin_circuit_board.tooltip", "§7A Coated Board");
        provider.add("item.gtpm.phenolic_circuit_board.tooltip", "§7A Good Board");
        provider.add("item.gtpm.plastic_circuit_board.tooltip", "§7A Good Board");
        provider.add("item.gtpm.epoxy_circuit_board.tooltip", "§7An Advanced Board");
        provider.add("item.gtpm.fiber_reinforced_circuit_board.tooltip", "§7An Extreme Board");
        provider.add("item.gtpm.multilayer_fiber_reinforced_circuit_board.tooltip", "§7An Elite Board");
        provider.add("item.gtpm.wetware_circuit_board.tooltip", "§7The Board that keeps life");
        provider.add("item.gtpm.resin_printed_circuit_board.tooltip", "§7A Basic Circuit Board");
        provider.add("item.gtpm.phenolic_printed_circuit_board.tooltip", "§7A Good Circuit Board");
        provider.add("item.gtpm.plastic_printed_circuit_board.tooltip", "§7A Good Circuit Board");
        provider.add("item.gtpm.epoxy_printed_circuit_board.tooltip", "§7An Advanced Circuit Board");
        provider.add("item.gtpm.fiber_reinforced_printed_circuit_board.tooltip", "§7A More Advanced Circuit Board");
        provider.add("item.gtpm.multilayer_fiber_reinforced_printed_circuit_board.tooltip",
                "§7An Elite Circuit Board");
        provider.add("item.gtpm.wetware_printed_circuit_board.tooltip", "§7The Board that keeps life");
        multilineLang(provider, "item.gtpm.vacuum_tube.tooltip", "§7Technically a Diode\n§cULV-Tier");
        provider.add("item.gtpm.diode.tooltip", "§7Basic Electronic Component");
        provider.add("item.gtpm.resistor.tooltip", "§7Basic Electronic Component");
        provider.add("item.gtpm.transistor.tooltip", "§7Basic Electronic Component");
        provider.add("item.gtpm.capacitor.tooltip", "§7Basic Electronic Component");
        provider.add("item.gtpm.inductor.tooltip", "§7A Small Coil");
        provider.add("item.gtpm.smd_diode.tooltip", "§7Electronic Component");
        provider.add("item.gtpm.smd_capacitor.tooltip", "§7Electronic Component");
        provider.add("item.gtpm.smd_transistor.tooltip", "§7Electronic Component");
        provider.add("item.gtpm.smd_resistor.tooltip", "§7Electronic Component");
        provider.add("item.gtpm.smd_inductor.tooltip", "§7Electronic Component");
        provider.add("item.gtpm.advanced_smd_diode.tooltip", "§7Advanced Electronic Component");
        provider.add("item.gtpm.advanced_smd_capacitor.tooltip", "§7Advanced Electronic Component");
        provider.add("item.gtpm.advanced_smd_transistor.tooltip", "§7Advanced Electronic Component");
        provider.add("item.gtpm.advanced_smd_resistor.tooltip", "§7Advanced Electronic Component");
        provider.add("item.gtpm.advanced_smd_inductor.tooltip", "§7Advanced Electronic Component");
        provider.add("item.gtpm.highly_advanced_soc_wafer.tooltip", "§7Raw Highly Advanced Circuit");
        provider.add("item.gtpm.advanced_soc_wafer.tooltip", "§7Raw Advanced Circuit");
        provider.add("item.gtpm.ilc_wafer.tooltip", "§7Raw Integrated Circuit");
        provider.add("item.gtpm.cpu_wafer.tooltip", "§7Raw Processing Unit");
        provider.add("item.gtpm.hpic_wafer.tooltip", "§7Raw High Power Circuit");
        provider.add("item.gtpm.uhpic_wafer.tooltip", "§7Raw Ultra High Power Circuit");
        provider.add("item.gtpm.nand_memory_wafer.tooltip", "§7Raw Logic Gate");
        provider.add("item.gtpm.ulpic_wafer.tooltip", "§7Raw Ultra Low Power Circuit");
        provider.add("item.gtpm.lpic_wafer.tooltip", "§7Raw Low Power Circuit");
        provider.add("item.gtpm.mpic_wafer.tooltip", "§7Raw Power Circuit");
        provider.add("item.gtpm.nano_cpu_wafer.tooltip", "§7Raw Nano Circuit");
        provider.add("item.gtpm.nor_memory_wafer.tooltip", "§7Raw Logic Gate");
        provider.add("item.gtpm.qbit_cpu_wafer.tooltip", "§7Raw Qubit Circuit");
        provider.add("item.gtpm.ram_wafer.tooltip", "§7Raw Memory");
        provider.add("item.gtpm.soc_wafer.tooltip", "§7Raw Basic Circuit");
        provider.add("item.gtpm.simple_soc_wafer.tooltip", "§7Raw Simple Circuit");
        provider.add("item.gtpm.engraved_crystal_chip.tooltip", "§7Needed for Circuits");
        provider.add("item.gtpm.raw_crystal_chip.tooltip", "§7Raw Crystal Processor");
        provider.add("item.gtpm.raw_crystal_chip_parts.tooltip", "§7Raw Crystal Processor Parts");
        provider.add("item.gtpm.crystal_cpu.tooltip", "§7Crystal Processing Unit");
        provider.add("item.gtpm.crystal_soc.tooltip", "§7Crystal System on Chip");
        provider.add("item.gtpm.advanced_soc.tooltip", "§7Advanced System on Chip");
        provider.add("item.gtpm.highly_advanced_soc.tooltip", "§7Highly Advanced System on Chip");
        provider.add("item.gtpm.ilc_chip.tooltip", "§7Integrated Logic Circuit");
        provider.add("item.gtpm.cpu_chip.tooltip", "§7Central Processing Unit");
        provider.add("item.gtpm.hpic_chip.tooltip", "§7High Power IC");
        provider.add("item.gtpm.uhpic_chip.tooltip", "§7Ultra High Power IC");
        provider.add("item.gtpm.nand_memory_chip.tooltip", "§7NAND Logic Gate");
        provider.add("item.gtpm.nano_cpu_chip.tooltip", "§7Nano Central Processing Unit");
        provider.add("item.gtpm.nor_memory_chip.tooltip", "§7NOR Logic Gate");
        provider.add("item.gtpm.ulpic_chip.tooltip", "§7Ultra Low Power IC");
        provider.add("item.gtpm.lpic_chip.tooltip", "§7Low Power IC");
        provider.add("item.gtpm.mpic_chip.tooltip", "§7Power IC");
        provider.add("item.gtpm.qbit_cpu_chip.tooltip", "§7Qubit Central Processing Unit");
        provider.add("item.gtpm.ram_chip.tooltip", "§7Random Access Memory");
        provider.add("item.gtpm.soc.tooltip", "§7System on Chip");
        provider.add("item.gtpm.simple_soc.tooltip", "§7Simple System on Chip");
        multilineLang(provider, "item.gtpm.basic_electronic_circuit.tooltip",
                "§7Your First Circuit\n§cLV-Tier Circuit");
        multilineLang(provider, "item.gtpm.good_electronic_circuit.tooltip",
                "§7Your Second Circuit\n§cMV-Tier Circuit");
        multilineLang(provider, "item.gtpm.basic_integrated_circuit.tooltip",
                "§7Smaller and more powerful\n§6LV-Tier Circuit");
        multilineLang(provider, "item.gtpm.good_integrated_circuit.tooltip",
                "§7Smaller and more powerful\n§6MV-Tier Circuit");
        multilineLang(provider, "item.gtpm.advanced_integrated_circuit.tooltip",
                "§7Smaller and more powerful\n§6HV-Tier Circuit");
        multilineLang(provider, "item.gtpm.nand_chip.tooltip", "§7A Superior Simple Circuit\n§6ULV-Tier Circuit");
        multilineLang(provider, "item.gtpm.microchip_processor.tooltip",
                "§7A Superior Basic Circuit\n§eLV-Tier Circuit");
        multilineLang(provider, "item.gtpm.micro_processor.tooltip",
                "§7Amazing Computation Speed!\n§eMV-Tier Circuit");
        multilineLang(provider, "item.gtpm.micro_processor_assembly.tooltip",
                "§7Amazing Computation Speed!\n§eHV-Tier Circuit");
        multilineLang(provider, "item.gtpm.micro_processor_computer.tooltip",
                "§7Amazing Computation Speed!\n§eEV-Tier Circuit");
        multilineLang(provider, "item.gtpm.micro_processor_mainframe.tooltip",
                "§7Amazing Computation Speed!\n§eIV-Tier Circuit");
        multilineLang(provider, "item.gtpm.nano_processor.tooltip", "§7Smaller than ever\n§bHV-Tier Circuit");
        multilineLang(provider, "item.gtpm.nano_processor_assembly.tooltip", "§7Smaller than ever\n§bEV-Tier Circuit");
        multilineLang(provider, "item.gtpm.nano_processor_computer.tooltip", "§7Smaller than ever\n§bIV-Tier Circuit");
        multilineLang(provider, "item.gtpm.nano_processor_mainframe.tooltip",
                "§7Smaller than ever\n§bLuV-Tier Circuit");
        multilineLang(provider, "item.gtpm.quantum_processor.tooltip",
                "§7Quantum Computing comes to life!\n§aEV-Tier Circuit");
        multilineLang(provider, "item.gtpm.quantum_processor_assembly.tooltip",
                "§7Quantum Computing comes to life!\n§aIV-Tier Circuit");
        multilineLang(provider, "item.gtpm.quantum_processor_computer.tooltip",
                "§7Quantum Computing comes to life!\n§aLuV-Tier Circuit");
        multilineLang(provider, "item.gtpm.quantum_processor_mainframe.tooltip",
                "§7Quantum Computing comes to life!\n§aZPM-Tier Circuit");
        multilineLang(provider, "item.gtpm.crystal_processor.tooltip",
                "§7Taking Advantage of Crystal Engraving\n§9IV-Tier Circuit");
        multilineLang(provider, "item.gtpm.crystal_processor_assembly.tooltip",
                "§7Taking Advantage of Crystal Engraving\n§9LuV-Tier Circuit");
        multilineLang(provider, "item.gtpm.crystal_processor_computer.tooltip",
                "§7Taking Advantage of Crystal Engraving\n§9ZPM-Tier Circuit");
        multilineLang(provider, "item.gtpm.crystal_processor_mainframe.tooltip",
                "§7Taking Advantage of Crystal Engraving\n§9UV-Tier Circuit");
        multilineLang(provider, "item.gtpm.wetware_processor.tooltip",
                "§7You have a feeling like it's watching you\n§4LuV-Tier Circuit");
        multilineLang(provider, "item.gtpm.wetware_processor_assembly.tooltip",
                "§7Can run Minecraft\n§4ZPM-Tier Circuit");
        multilineLang(provider, "item.gtpm.wetware_processor_computer.tooltip",
                "§7Ultimate fusion of Flesh and Machine\n§4UV-Tier Circuit");
        multilineLang(provider, "item.gtpm.wetware_processor_mainframe.tooltip",
                "§7The best Man has ever seen\n§4UHV-Tier Circuit");
        provider.add("item.gtpm.stem_cells.tooltip", "§7Raw Intelligence");
        provider.add("item.gtpm.neuro_processing_unit.tooltip", "§7Neuro CPU");
        provider.add("item.gtpm.petri_dish.tooltip", "§7For cultivating Cells");
        provider.add("item.gtpm.neutron_reflector.tooltip", "§7Indestructible");
        provider.add("item.gtpm.duct_tape.tooltip", "§7If you can't fix it with this, use more of it!");
        provider.add("item.gtpm.quantum_eye.tooltip", "§7Improved Ender Eye");
        provider.add("item.gtpm.quantum_star.tooltip", "§7Improved Nether Star");
        provider.add("item.gtpm.gravi_star.tooltip", "§7Ultimate Nether Star");
        multilineLang(provider, "item.gtpm.item_filter.tooltip",
                "§7Filters §fItem§7 I/O as §fCover§7.\nCan be used as a §fConveyor Module§7 and §fRobotic Arm§7 upgrade.");
        multilineLang(provider, "item.gtpm.item_tag_filter.tooltip",
                "§7Filters §fItem§7 I/O with §fItem Tags§7 as §fCover§7.\nCan be used as a §fConveyor Module§7 and §fRobotic Arm§7 upgrade.");
        multilineLang(provider, "item.gtpm.tag_filter.tooltip",
                "§7Filters §fItem§7 I/O with §fTag§7 as §fCover§7.\nCan be used as a §fConveyor Module§7 and §fRobotic Arm§7 upgrade.");
        multilineLang(provider, "item.gtpm.fluid_filter.tooltip",
                "§7Filters §fFluid§7 I/O as §fCover§7.\nCan be used as an §fElectric Pump§7 and §fFluid Regulator§7 upgrade.");
        multilineLang(provider, "item.gtpm.fluid_tag_filter.tooltip",
                "§7Filters §fFluid§7 I/O with §fFluid Tags§7 as §fCover§7.\nCan be used as an §fElectric Pump§7 and §fFluid Regulator§7 upgrade.");
        multilineLang(provider, "item.gtpm.smart_item_filter.tooltip",
                "§7Filters §fItem§7 I/O with §fMachine Recipes§7 as §fCover§7.\nCan be used as a §fConveyor Module§7 and §fRobotic Arm§7 upgrade.");
        provider.add("item.gtpm.machine_controller.tooltip", "§7Turns Machines §fON/OFF§7 as §fCover§7.");
        provider.add("item.gtpm.activity_detector_cover.tooltip",
                "§7Gives out §fActivity Status§7 as Redstone as §fCover§7.");
        provider.add("item.gtpm.advanced_activity_detector_cover.tooltip",
                "§7Gives out §fMachine Progress§7 as Redstone as §fCover§7.");
        provider.add("item.gtpm.fluid_detector_cover.tooltip",
                "§7Gives out §fFluid Amount§7 as Redstone as §fCover§7.");
        provider.add("item.gtpm.advanced_fluid_detector_cover.tooltip",
                "§7Gives §fRS-Latch§7 controlled §fFluid Storage Status§7 as Redstone as §fCover§7.");
        provider.add("item.gtpm.item_detector_cover.tooltip", "§7Gives out §fItem Amount§7 as Redstone as §fCover§7.");
        provider.add("item.gtpm.advanced_item_detector_cover.tooltip",
                "§7Gives §fRS-Latch§7 controlled §fItem Storage Status§7 as Redstone as §fCover§7.");
        provider.add("item.gtpm.energy_detector_cover.tooltip",
                "§7Gives out §fEnergy Amount§7 as Redstone as §fCover§7.");
        provider.add("item.gtpm.advanced_energy_detector_cover.tooltip",
                "§7Gives §fRS-Latch§7 controlled §fEnergy Status§7 as Redstone as §fCover§7.");
        multilineLang(provider, "item.gtpm.fluid_voiding_cover.tooltip",
                "§7Voids §fFluids§7 as §fCover§7.\nActivate with §fSoft Mallet§7 after placement.");
        multilineLang(provider, "item.gtpm.advanced_fluid_voiding_cover.tooltip",
                "§7Voids §fFluids§7 with amount control as §fCover§7.\nActivate with §fSoft Mallet§7 after placement.");
        multilineLang(provider, "item.gtpm.item_voiding_cover.tooltip",
                "§7Voids §fItems§7 as §fCover§7.\nActivate with §fSoft Mallet§7 after placement.");
        multilineLang(provider, "item.gtpm.advanced_item_voiding_cover.tooltip",
                "§7Voids §fItems§7 with amount control as §fCover§7.\nActivate with §fSoft Mallet§7 after placement.");
        multilineLang(provider, "item.gtpm.facade_cover.tooltip",
                "§7Decorative Outfit §fCover§7.\n§7Crafted using an Iron Plate and any block");
        provider.add("item.gtpm.computer_monitor_cover.tooltip", "§7Displays §fData§7 as §fCover§7.");
        provider.add("item.gtpm.shutter_module_cover.tooltip",
                "§fBlocks Transfer§7 through attached Side as §fCover§7.");
        multilineLang(provider, "item.gtpm.solar_panel.tooltip",
                "§7May the Sun be with you.\nProduces §fEnergy§7 from the §eSun§7 as §fCover§7.");
        provider.add("item.gtpm.infinite_water_cover.tooltip",
                "§7Fills attached containers with §9Water§7 as §fCover§7.");
        provider.add("item.gtpm.ender_fluid_link_cover.tooltip",
                "§7Transports §fFluids§7 with a §fWireless §dEnder§f Connection§7 as §fCover§7.");
        provider.add("item.gtpm.gelled_toluene.tooltip", "§7Raw Explosive");
        provider.add("item.gtpm.bottle.purple.drink.tooltip",
                "§7How about Lemonade. Or some Ice Tea? I got Purple Drink!");
        multilineLang(provider, "item.gtpm.foam_sprayer.tooltip",
                "§7Sprays Construction Foam\nUse on a frame to foam connected frames\nFoam can be colored");
        provider.add("item.gtpm.firebrick.tooltip", "§7Heat resistant");
        provider.add("item.gtpm.basic_tape.tooltip",
                "§7Not strong enough for mechanical issues\nCan be used to pick up crates without dropping their items");
        provider.add("item.gtpm.terminal.tooltip",
                "Shift + R-Click on a controller to automatically build the multiblock");

        provider.add("item.gtpm.sus_record.desc", "§7sussy!");
    }
}
