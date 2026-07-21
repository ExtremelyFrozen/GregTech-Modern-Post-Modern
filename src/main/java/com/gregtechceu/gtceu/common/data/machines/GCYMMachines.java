package com.gregtechceu.gtceu.common.data.machines;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.multiblock.FactoryBlockPattern;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderHelper;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.DistillationTowerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.gcym.*;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Comparator;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.IS_FORMED;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;
import static com.gregtechceu.gtceu.common.data.GCYMBlocks.*;
import static com.gregtechceu.gtceu.common.data.GCYMRecipeTypes.ALLOY_BLAST_RECIPES;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;
import static com.gregtechceu.gtceu.common.data.GTRecipeModifiers.*;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.*;
import static com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.registerTieredMachines;
import static com.gregtechceu.gtceu.common.data.models.GTMachineModels.*;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

public class GCYMMachines {

    public static void init() {}

    public static final MachineDefinition[] PARALLEL_HATCH = registerTieredMachines("parallel_hatch",
            ParallelHatchPartMachine::new,
            (tier, builder) -> builder
                    .langValue(switch (tier) {
                        case 5 -> "Elite";
                        case 6 -> "Master";
                        case 7 -> "Ultimate";
                        case 8 -> "Super";
                        default -> "Simple"; // Should never be hit.
                    } + " Parallel Control Hatch")
                    .rotationState(RotationState.ALL)
                    .abilities(PartAbility.PARALLEL_HATCH)
                    .modelProperty(IS_FORMED, false)
                    .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, WorkLogic.Status.IDLE)
                    .model(createWorkableTieredHullMachineModel(
                            GTCEu.id("block/machines/parallel_hatch_mk" + (tier - 4)))
                            .andThen((ctx, prov, model) -> {
                                model.addReplaceableTextures("bottom", "top", "side");
                            }))
                    .tooltips(Component.translatable("gtpm.machine.parallel_hatch_mk" + tier + ".tooltip"),
                            Component.translatable("gtpm.part_sharing.disabled"))
                    .register(),
            IV, LuV, ZPM, UV);

    public final static MultiblockMachineDefinition LARGE_MACERATION_TOWER = REGISTRATE
            .multiblock("large_maceration_tower", LargeMacerationTowerMachine::new)
            .langValue("Large Maceration Tower")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    MACERATOR_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeType(MACERATOR_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_SECURE_MACERATION)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/secure_maceration_casing"),
                    GTCEu.id("block/multiblock/gcym/large_maceration_tower"))
            .register();

    public final static MultiblockMachineDefinition LARGE_CHEMICAL_BATH = REGISTRATE
            .multiblock("large_chemical_bath", LargeChemicalBathMachine::new)
            .langValue("Large Chemical Bath")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_2.tooltip",
                    ORE_WASHER_RECIPES.getName(), CHEMICAL_BATH_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeTypes(CHEMICAL_BATH_RECIPES, ORE_WASHER_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_WATERTIGHT)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .hasBER(true)
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, WorkLogic.Status.IDLE)
            .model(createWorkableCasingMachineModel(GTCEu.id("block/casings/gcym/watertight_casing"),
                    GTCEu.id("block/multiblock/gcym/large_chemical_bath"))
                    .andThen(b -> b.addDynamicRenderer(DynamicRenderHelper::makeRecipeFluidAreaRender)))
            .register();

    public final static MultiblockMachineDefinition LARGE_CENTRIFUGE = REGISTRATE
            .multiblock("large_centrifuge", WorkableElectricMultiblockMachine::new)
            .langValue("Large Centrifugal Unit")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_2.tooltip",
                    CENTRIFUGE_RECIPES.getName(), THERMAL_CENTRIFUGE_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeTypes(CENTRIFUGE_RECIPES, THERMAL_CENTRIFUGE_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_VIBRATION_SAFE)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/vibration_safe_casing"),
                    GTCEu.id("block/multiblock/gcym/large_centrifuge"))
            .register();

    public final static MultiblockMachineDefinition LARGE_MIXER = REGISTRATE
            .multiblock("large_mixer", LargeMixerMachine::new)
            .langValue("Large Mixing Vessel")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    MIXER_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeType(MIXER_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_REACTION_SAFE)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .hasBER(true)
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, WorkLogic.Status.IDLE)
            .model(createWorkableCasingMachineModel(GTCEu.id("block/casings/gcym/reaction_safe_mixing_casing"),
                    GTCEu.id("block/multiblock/gcym/large_mixer"))
                    .andThen(b -> b.addDynamicRenderer(DynamicRenderHelper::makeRecipeFluidAreaRender)))
            .register();

    public final static MultiblockMachineDefinition LARGE_ELECTROLYZER = REGISTRATE
            .multiblock("large_electrolyzer", WorkableElectricMultiblockMachine::new)
            .langValue("Large Electrolysis Chamber")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    ELECTROLYZER_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeType(ELECTROLYZER_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_NONCONDUCTING)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/nonconducting_casing"),
                    GTCEu.id("block/multiblock/gcym/large_electrolyzer"))
            .register();

    public final static MultiblockMachineDefinition LARGE_ELECTROMAGNET = REGISTRATE
            .multiblock("large_electromagnet", WorkableElectricMultiblockMachine::new)
            .langValue("Large Electromagnet")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_2.tooltip",
                    ELECTROMAGNETIC_SEPARATOR_RECIPES.getName(),
                    POLARIZER_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeTypes(ELECTROMAGNETIC_SEPARATOR_RECIPES, POLARIZER_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_NONCONDUCTING)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/nonconducting_casing"),
                    GTCEu.id("block/multiblock/gcym/large_electrolyzer"))
            .register();

    public final static MultiblockMachineDefinition LARGE_PACKER = REGISTRATE
            .multiblock("large_packer", WorkableElectricMultiblockMachine::new)
            .langValue("Large Packaging Machine")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtpm.packer")))
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.PACKER_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_TUNGSTENSTEEL_ROBUST)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"),
                    GTCEu.id("block/multiblock/gcym/large_packer"))
            .register();

    public final static MultiblockMachineDefinition LARGE_ASSEMBLER = REGISTRATE
            .multiblock("large_assembler", WorkableElectricMultiblockMachine::new)
            .langValue("Large Assembling Factory")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtpm.assembler")))
            .tooltips(Component.translatable("gtpm.multiblock.exact_hatch_1.tooltip"))
            .conditionalTooltip(GTMachineUtils.defaultEnvironmentRequirement(),
                    ConfigHolder.INSTANCE.gameplay.environmentalHazards)
            .rotationState(RotationState.ALL)
            .recipeType(ASSEMBLER_RECIPES)
            .recipeModifiers(DEFAULT_ENVIRONMENT_REQUIREMENT, GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK,
                    BATCH_MODE)
            .appearanceBlock(CASING_LARGE_SCALE_ASSEMBLING)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/large_scale_assembling_casing"),
                    GTCEu.id("block/multiblock/gcym/large_assembler"))
            .register();

    public final static MultiblockMachineDefinition LARGE_CIRCUIT_ASSEMBLER = REGISTRATE
            .multiblock("large_circuit_assembler", WorkableElectricMultiblockMachine::new)
            .langValue("Large Circuit Assembling Facility")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    CIRCUIT_ASSEMBLER_RECIPES.getName()))
            .tooltips(Component.translatable("gtpm.multiblock.exact_hatch_1.tooltip"))
            .conditionalTooltip(GTMachineUtils.defaultEnvironmentRequirement(),
                    ConfigHolder.INSTANCE.gameplay.environmentalHazards)
            .rotationState(RotationState.ALL)
            .recipeType(CIRCUIT_ASSEMBLER_RECIPES)
            .recipeModifiers(DEFAULT_ENVIRONMENT_REQUIREMENT, GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK,
                    BATCH_MODE)
            .appearanceBlock(CASING_LARGE_SCALE_ASSEMBLING)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/large_scale_assembling_casing"),
                    GTCEu.id("block/multiblock/gcym/large_circuit_assembler"))
            .register();

    public final static MultiblockMachineDefinition LARGE_ARC_SMELTER = REGISTRATE
            .multiblock("large_arc_smelter", WorkableElectricMultiblockMachine::new)
            .langValue("Large Arc Smelter")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    ARC_FURNACE_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeType(ARC_FURNACE_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_HIGH_TEMPERATURE_SMELTING)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/high_temperature_smelting_casing"),
                    GTCEu.id("block/multiblock/gcym/large_arc_smelter"))
            .register();

    public final static MultiblockMachineDefinition LARGE_ENGRAVING_LASER = REGISTRATE
            .multiblock("large_engraving_laser", WorkableElectricMultiblockMachine::new)
            .langValue("Large Engraving Laser")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    LASER_ENGRAVER_RECIPES.getName()))
            .conditionalTooltip(GTMachineUtils.defaultEnvironmentRequirement(),
                    ConfigHolder.INSTANCE.gameplay.environmentalHazards)
            .rotationState(RotationState.ALL)
            .recipeType(LASER_ENGRAVER_RECIPES)
            .recipeModifiers(DEFAULT_ENVIRONMENT_REQUIREMENT, GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK,
                    BATCH_MODE)
            .appearanceBlock(CASING_LASER_SAFE_ENGRAVING)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/laser_safe_engraving_casing"),
                    GTCEu.id("block/multiblock/gcym/large_engraving_laser"))
            .register();

    public final static MultiblockMachineDefinition LARGE_SIFTING_FUNNEL = REGISTRATE
            .multiblock("large_sifting_funnel", WorkableElectricMultiblockMachine::new)
            .langValue("Large Sifting Funnel")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    SIFTER_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeType(SIFTER_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_VIBRATION_SAFE)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/vibration_safe_casing"),
                    GTCEu.id("block/multiblock/gcym/large_sifting_funnel"))
            .register();

    public final static MultiblockMachineDefinition BLAST_ALLOY_SMELTER = REGISTRATE
            .multiblock("alloy_blast_smelter", CoilWorkableElectricMultiblockMachine::new)
            .langValue("Alloy Blast Smelter")
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    ALLOY_BLAST_RECIPES.getName()))
            .tooltips(Component.translatable("gtpm.machine.electric_blast_furnace.tooltip.0"),
                    Component.translatable("gtpm.machine.electric_blast_furnace.tooltip.1"),
                    Component.translatable("gtpm.machine.electric_blast_furnace.tooltip.2"))
            .rotationState(RotationState.ALL)
            .recipeType(ALLOY_BLAST_RECIPES)
            .recipeModifiers(GTRecipeModifiers::ebfOverclock)
            .appearanceBlock(CASING_HIGH_TEMPERATURE_SMELTING)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/high_temperature_smelting_casing"),
                    GTCEu.id("block/multiblock/gcym/blast_alloy_smelter"))
            .additionalDisplay((controller, components) -> {
                if (controller instanceof CoilWorkableElectricMultiblockMachine coilMachine && controller.isFormed()) {
                    components.add(Component.translatable("gtpm.multiblock.blast_furnace.max_temperature",
                            Component
                                    .translatable(
                                            FormattingUtil
                                                    .formatNumbers(coilMachine.getCoilType().getCoilTemperature() +
                                                            100L * Math.max(0, coilMachine.getTier() - GTValues.MV)) +
                                                    "K")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED))));
                }
            })
            .register();

    public final static MultiblockMachineDefinition LARGE_AUTOCLAVE = REGISTRATE
            .multiblock("large_autoclave", WorkableElectricMultiblockMachine::new)
            .langValue("Large Crystallization Chamber")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    AUTOCLAVE_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeType(AUTOCLAVE_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_WATERTIGHT)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/watertight_casing"),
                    GTCEu.id("block/multiblock/gcym/large_autoclave"))
            .register();

    public final static MultiblockMachineDefinition LARGE_MATERIAL_PRESS = REGISTRATE
            .multiblock("large_material_press", WorkableElectricMultiblockMachine::new)
            .langValue("Large Material Press")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_4.tooltip",
                    BENDER_RECIPES.getName(), COMPRESSOR_RECIPES.getName(),
                    FORGE_HAMMER_RECIPES.getName(), FORMING_PRESS_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeTypes(BENDER_RECIPES, COMPRESSOR_RECIPES, FORGE_HAMMER_RECIPES, FORMING_PRESS_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_STRESS_PROOF)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/stress_proof_casing"),
                    GTCEu.id("block/multiblock/gcym/large_material_press"))
            .register();

    public final static MultiblockMachineDefinition LARGE_BREWER = REGISTRATE
            .multiblock("large_brewer", WorkableElectricMultiblockMachine::new)
            .langValue("Large Brewing Vat")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_3.tooltip",
                    BREWING_RECIPES.getName(), FERMENTING_RECIPES.getName(),
                    FLUID_HEATER_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeTypes(BREWING_RECIPES, FERMENTING_RECIPES, FLUID_HEATER_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_CORROSION_PROOF)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/corrosion_proof_casing"),
                    GTCEu.id("block/multiblock/gcym/large_brewer"))
            .register();

    public final static MultiblockMachineDefinition LARGE_CUTTER = REGISTRATE
            .multiblock("large_cutter", WorkableElectricMultiblockMachine::new)
            .langValue("Large Cutting Saw")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_2.tooltip",
                    CUTTER_RECIPES.getName(), LATHE_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeTypes(CUTTER_RECIPES, LATHE_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_SHOCK_PROOF)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/shock_proof_cutting_casing"),
                    GTCEu.id("block/multiblock/gcym/large_cutter"))
            .register();

    public final static MultiblockMachineDefinition LARGE_DISTILLERY = REGISTRATE
            .multiblock("large_distillery", DistillationTowerMachine::new)
            .langValue("Large Fractionating Distillery")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_2.tooltip",
                    DISTILLATION_RECIPES.getName(), DISTILLERY_RECIPES.getName()))
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeTypes(DISTILLATION_RECIPES, DISTILLERY_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_WATERTIGHT)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern
                    .start(definition, RIGHT, BACK, UP)
                    .aislesFromDefinition()
                    .build())
            .allowExtendedFacing(false)
            .partSorter(Comparator.comparingInt((IMultiPart p) -> p.self().getBlockPos().getY()))
            .workableCasingModel(GTCEu.id("block/casings/gcym/watertight_casing"),
                    GTCEu.id("block/multiblock/gcym/large_distillery"))
            .register();

    public final static MultiblockMachineDefinition LARGE_EXTRACTOR = REGISTRATE
            .multiblock("large_extractor", WorkableElectricMultiblockMachine::new)
            .langValue("Large Extraction Machine")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_2.tooltip",
                    EXTRACTOR_RECIPES.getName(), CANNER_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeTypes(EXTRACTOR_RECIPES, CANNER_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_WATERTIGHT)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/watertight_casing"),
                    GTCEu.id("block/multiblock/gcym/large_extractor"))
            .register();

    public final static MultiblockMachineDefinition LARGE_EXTRUDER = REGISTRATE
            .multiblock("large_extruder", WorkableElectricMultiblockMachine::new)
            .langValue("Large Extrusion Machine")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    EXTRUDER_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeType(EXTRUDER_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_STRESS_PROOF)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/stress_proof_casing"),
                    GTCEu.id("block/multiblock/gcym/large_extruder"))
            .register();

    public final static MultiblockMachineDefinition LARGE_SOLIDIFIER = REGISTRATE
            .multiblock("large_solidifier", WorkableElectricMultiblockMachine::new)
            .langValue("Large Solidification Array")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    FLUID_SOLIDFICATION_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeType(FLUID_SOLIDFICATION_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_WATERTIGHT)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/watertight_casing"),
                    GTCEu.id("block/multiblock/gcym/large_solidifier"))
            .register();

    public final static MultiblockMachineDefinition LARGE_WIREMILL = REGISTRATE
            .multiblock("large_wiremill", WorkableElectricMultiblockMachine::new)
            .langValue("Large Wire Factory")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    WIREMILL_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeType(WIREMILL_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_STRESS_PROOF)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/stress_proof_casing"),
                    GTCEu.id("block/multiblock/gcym/large_wiremill"))
            .register();

    // spotless:off
    public static final MultiblockMachineDefinition ROTARY_HEARTH_FURNACE = REGISTRATE
            .multiblock("rotary_hearth_furnace", CoilWorkableElectricMultiblockMachine::new)
            .langValue("Rotary Hearth Furnace")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    BLAST_RECIPES.getName()))
            .tooltips(Component.translatable("gtpm.machine.electric_blast_furnace.tooltip.0"),
                    Component.translatable("gtpm.machine.electric_blast_furnace.tooltip.1"),
                    Component.translatable("gtpm.machine.electric_blast_furnace.tooltip.2"))
            .rotationState(RotationState.ALL)
            .recipeType(BLAST_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers::ebfOverclock, BATCH_MODE)
            .appearanceBlock(CASING_HIGH_TEMPERATURE_SMELTING)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/gcym/high_temperature_smelting_casing"),
                    GTCEu.id("block/multiblock/gcym/rotary_hearth_furnace"))
            .additionalDisplay((controller, components) -> {
                if (controller instanceof CoilWorkableElectricMultiblockMachine coilMachine && controller.isFormed()) {
                    components.add(Component.translatable("gtpm.multiblock.blast_furnace.max_temperature",
                            Component.translatable(
                                    FormattingUtil.formatNumbers(coilMachine.getCoilType().getCoilTemperature() +
                                            100L * Math.max(0, coilMachine.getTier() - GTValues.MV)) + "K")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED))));
                }
            })
            .register();

    public final static MultiblockMachineDefinition MEGA_VACUUM_FREEZER = REGISTRATE
            .multiblock("mega_vacuum_freezer", WorkableElectricMultiblockMachine::new)
            .langValue("Bulk Blast Chiller")
            .tooltips(Component.translatable("gtpm.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtpm.machine.available_recipe_map_1.tooltip",
                    VACUUM_RECIPES.getName()))
            .rotationState(RotationState.ALL)
            .recipeType(VACUUM_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, OC_NON_PERFECT_SUBTICK, BATCH_MODE)
            .appearanceBlock(CASING_ALUMINIUM_FROSTPROOF)
            .pattern(MultiblockControllerMachine.DEFAULT_STRUCTURE, definition -> FactoryBlockPattern.start(definition)
                    .aislesFromDefinition()
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_frost_proof"),
                    GTCEu.id("block/multiblock/gcym/mega_vacuum_freezer"))
            .register();
    // spotless:on
}
