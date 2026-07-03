---
title: 修改 Multiblock Controller UI
---
# 修改 Multiblock Controller UI

## 添加 text component
要向 UI 添加 text component，需要在 multiblock registration builder 中使用 `.additionalDisplay`。
`.additionalDisplay` 接收一个 lambda，该 lambda 接收 2 个参数：正在被添加 component 的 `IMultiController` machine，以及现有 components 的 `List<Component>`。
使用示例如下：

```js title="ui_modified_multiblock.js"
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('ui_modified_multiblock', 'multiblock')
		.rotationState(RotationState.NON_Y_AXIS)
		.recipeType('electrolyzer')
		.recipeModifiers([GTRecipeModifiers.OC_NON_PERFECT_SUBTICK])
		.appearanceBlock(() => Block.getBlock("gtceu:solid_machine_casing"))
		.pattern(definition => FactoryBlockPattern.start()
			.aisle('###','   ','###')
			.aisle('###',' S ','###')
			.aisle('#C#','   ','###')
			.where('C', Predicates.controller(Predicates.blocks(definition.get())))
			.where('#', Predicates.blocks("gtceu:solid_machine_casing")
				.or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
				.or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
				.or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(1).setPreviewCount(1)))
			.where('S', Predicates.blocks("gtceu:steel_machine_casing"))
			.where(' ', Predicates.any())
			.build())
		.workableCasingModel("gtceu:block/casings/solid/machine_casing_solid_steel", "gtceu:block/multiblock/blast_furnace")
		.additionalDisplay((machine, components) => { // (3)
			if (machine.isFormed()) { // (1)
				components.add(Component.literal("I am text component #1")) // (2)
                components.add(Component.literal("I am text component #2"))
			}
		})
});
```

1. 检查 multiblock 是否已 formed。
2. 要添加新行，请使用 `components.add(Component)`。
3. 使用 `.additionalDisplay()` 添加 text component。
