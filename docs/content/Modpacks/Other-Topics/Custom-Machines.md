---
title: 自定义 Machine
---


# 自定义 Machine


## 创建自定义 Steam Machine

```js title="test_steam_machine.js"
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_simple_steam_machine', 'steam', true) // (1)
})
```

1. Machine ID、Machine Type、是否具有 High Pressure Variant


## 创建自定义 Electric Machine

```js title="test_electric_machine.js"
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_electric', 'simple', 0, GTValues.LV, GTValues.MV, GTValues.HV) // (1)
        .rotationState(RotationState.NON_Y_AXIS)
        .recipeType('test_recipe_type')
        .tankScalingFunction(tier => tier * 3200)
})
```


1. Machine ID、Machine Type、产生的污染、Voltage Tiers



## 创建自定义 Kinetic Machine

```js title="test_kinetic_machine.js"
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_kinetic', 'kinetic', GTValues.LV, GTValues.MV, GTValues.HV)
        .rotationState(RotationState.NON_Y_AXIS)
        .recipeType('test_kinetic_recipe_type')
        .tankScalingFunction(tier => tier * 3200)
})
```


## 创建自定义 Generator

```js title="test_generator.js"
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_generator', 'generator', GTValues.LV, GTValues.MV, GTValues.HV) // (1)
        .recipeType('test_generator_recipe_type')
        .tankScalingFunction(tier => tier * 3200)
})
```


## 创建自定义 Multiblock

```js title="test_multiblock.js"
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_generator', 'multiblock')
        .tooltips(Component.translatable('your.langfile.entry.here')) // (1)
        .rotationState(RotationState.NON_Y_AXIS)
        .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
        .recipeTypes(['test_recipe_type_1', 'test_recipe_type_2'])
        .recipeModifiers([GTRecipeModifiers.PARALLEL_HATCH, ELECTRIC_OVERCLOCK.apply(PERFECT_OVERCLOCK)]) // (2)
        .pattern(definition => FactoryBlockPattern.start()
            .aisle('CCC', 'GGG', 'CCC')
            .aisle('CCC', 'GDG', 'CSC')
            .aisle('CKC', 'GGG', 'CMC')
            .where('K', Predicates.controller(Predicates.blocks(definition.get())))
            .where('M', Predicates.abilities(PartAbility.MAINTENANCE))
            .where('S', Predicates.abilities(PartAbility.MUFFLER))
            .where('D', Predicates.blocks(GTBlocks.COIL_CUPRONICKEL.get()))
            .where('G', Predicates.blocks('minecraft:glass'))
            .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                .or(Predicates.autoAbilities(definition.getRecipeTypes())))
        .build())
        .workableCasingModel(
            "gtceu:block/casings/solid/machine_casing_inert_ptfe",
            "gtceu:block/multiblock/large_chemical_reactor"
        )
})
```


1. 你可以为 multiblock controllers 添加 tooltips，这些内容会在鼠标悬停时显示。每次单独调用 ```.tooltips()``` 都会向 controller 的 tooltip 添加单独一行。```Component.translatable()``` 会读取放在 ```kubejs/assets/gtceu/lang``` 中的 .json lang 文件，或由独立资源包提供的条目。```Component``` 类会在编译时由 KubeJS 自动加载，不需要手动加载。
2. 如果 electric 和/或 multiblock machines 可以处理你的 custom recipe type，```.recipeModifiers()``` 可以让你在这些 Machine 运行该 custom recipe type 的 recipes 时微调其行为。```GTRecipeModifiers.PARALLEL_HATCH``` 会让 Multiblock Machines 能够通过可选的 Parallel Hatch 并行处理你的自定义类型 recipes，而 ```ELECTRIC_OVERCLOCK.apply(PERFECT_OVERCLOCK)``` 会定义你的 recipes 在 electric machines 和 multiblocks 中如何 overclock。

### Shape Info（形状信息）

Shape Info 用于手动定义你的 Multiblock 在 JEI/REI/EMI multiblock preview tab 中的显示方式。

```js title="shape_info_test.js"
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_generator', 'multiblock')
        .rotationState(RotationState.NON_Y_AXIS)
        .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
        .recipeTypes(['test_recipe_type_1', 'test_recipe_type_2'])
        .pattern(definition => FactoryBlockPattern.start()
            .aisle('CCC', 'GGG', 'CCC')
            .aisle('CCC', 'GDG', 'CSC')
            .aisle('CKC', 'GGG', 'CMC')
            .where('K', Predicates.controller(Predicates.blocks(definition.get())))
            .where('M', Predicates.abilities(PartAbility.MAINTENANCE))
            .where('S', Predicates.abilities(PartAbility.MUFFLER))
            .where('D', Predicates.blocks(GTBlocks.COIL_CUPRONICKEL.get()))
            .where('G', Predicates.blocks('minecraft:glass'))
            .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                .or(Predicates.autoAbilities(definition.getRecipeTypes())))
        .build())
        .shapeInfo(controller => MultiblockShapeInfo.builder()
            .aisle('eCe', 'GGG', 'CCC')
            .aisle('CCC', 'GDG', 'CSC')
            .aisle('iKo', 'GGG', 'CMC')
            .where('K', controller, Direction.SOUTH)
            .where('C', GTBlocks.CASING_STEEL_SOLID.get())
            .where('G', Block.getBlock('minecraft:glass'))
            .where('D', GTBlocks.COIL_CUPRONICKEL.get())
            .where('S', GTMachines.MUFFLER_HATCH[1], Direction.UP)
            .where('M', GTMachines.MAINTENANCE_HATCH, Direction.SOUTH)
            .where('e', GTMachines.ENERGY_INPUT_HATCH[1], Direction.NORTH)
            .where('i', GTMachines.ITEM_IMPORT_BUS[1], Direction.SOUTH)
            .where('0', GTMachines.ITEM_EXPORT_BUS[1], Direction.SOUTH)
        .build())
        .workableCasingModel(
            "gtceu:block/casings/solid/machine_casing_inert_ptfe",
            "gtceu:block/multiblock/large_chemical_reactor"
        )
})
```
