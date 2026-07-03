---
title: 自定义 Recipe Modifiers
---

# 自定义 Recipe Modifiers / Data Logic

## 添加 Modifier
KubeJS 中的自定义 recipe modifiers 通过函数实现。这个示例会制作一个像 EBF 一样要求配方温度的多方块。
```js title="temperature_recipe_modifier.js"
const $GTRecipe = Java.loadClass("com.gregtechceu.gtceu.api.recipe.GTRecipe");
const $MetaMachine = Java.loadClass("com.gregtechceu.gtceu.api.machine.MetaMachine");

function TemperatureModifier(machine, recipe) {
    if (!(machine instanceof $MetaMachine)) return ModifierFunction.NULL // (1)
    if (!(recipe instanceof $GTRecipe)) return ModifierFunction.NULL

    if (!machine instanceof $CoilWorkableElectricMultiblockMachine) {
        return $RecipeModifier.nullWrongType($CoilWorkableElectricMultiblockMachine, machine);
    } else {

        let temp = machine.getCoilType().getCoilTemperature() // (3)

        let recipeTemp = recipe.data.getInt("RequiredTemp") // (4)
        if (recipeTemp > temp) {
            return ModifierFunction.NULL
        }
        return ModifierFunction.IDENTITY // (2)
    }
}
```

1. `ModifierFunction.NULL` 会停止配方。
2. `ModifierFunction.IDENTITY` 会启动配方。
3. 获取线圈温度，多方块任意 key 中**必须**包含 ``.heatingCoils()``。
4. 检查线圈温度是否足够。

## 使用 Modifier
```js title="example_temperature_multiblock.js"
const $CoilWorkableElectricMultiblockMachine = Java.loadClass("com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine");

GTCEuStartupEvents.registry('gtceu:recipe_type', event => {
	event.create('example_smelting')
		.category('multiblock')
		.setMaxIOSize(1, 1, 0, 0)
		.setProgressBar(GuiTextures.PROGRESS_BAR_FUSION, FillDirection.LEFT_TO_RIGHT)
		.setSound(GTSoundEntries.BATH);
});

GTCEuStartupEvents.registry('gtceu:machine', event => {

	GTRecipeTypes.get("example_smelting").addDataInfo((data) => (
		`Temperature: ${data.getInt("RequiredTemp")}K` // (4)
	)) // (3)

	event.create('example_smelter', 'multiblock')
		.rotationState(RotationState.NON_Y_AXIS)
		.machine((holder) => new $CoilWorkableElectricMultiblockMachine(holder)) // (1)
		.recipeType('alchemy')
		.recipeModifiers([(machine, recipe) => TemperatureModifier(machine, recipe)]) // (2)
		.appearanceBlock(() => Block.getBlock("gtceu:solid_machine_casing"))
		.pattern(definition => FactoryBlockPattern.start()
			.aisle('###','HHH','###')
			.aisle('###','H H','###')
			.aisle('#C#','HHH','###')
			.where('C', Predicates.controller(Predicates.blocks(definition.get())))
			.where('#', Predicates.blocks("gtceu:solid_machine_casing")
				.or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
				.or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
				.or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(1).setPreviewCount(1)))
			.where('H', Predicates.heatingCoils())
			.where(' ', Predicates.any())
			.build())
		.workableCasingModel("gtceu:block/casings/solid/machine_casing_solid_steel", "gtceu:block/multiblock/blast_furnace")
})
```

1. 创建多方块 **coilMachine**；没有它，我们的 modifier 无法工作。
2. 使用我们的 modifier。
3. 在 EMI 中显示我们的数据。
4. 从配方中获取 `RequiredTemp` 数据。

## 在配方中使用我们的 Modifier
要在配方中使用我们的 modifier，需要向配方添加 data。
```js title="example_smelting.js"
ServerEvents.recipes(event => {
	event.recipes.gtceu.example_smelting('example:diamondirt')
        .itemInputs('minecraft:dirt')
        .itemOutputs('gtceu:raw_diamond')
        .addData("RequiredTemp", 1000) // (1)
        .duration(320)
        .EUt(GTValues.VA[GTValues.LV]);
})
```

1. 向配方添加 data，在这个场景中是 Temperature。
