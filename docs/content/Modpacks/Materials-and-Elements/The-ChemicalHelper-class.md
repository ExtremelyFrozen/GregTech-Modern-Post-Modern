---
title: "ChemicalHelper Class（ChemicalHelper 类）"
---


# `ChemicalHelper` Class（ChemicalHelper 类）

使用 GregTech Post Modern 制作整合包时，了解 ChemicalHelper class 会很有帮助。

此 class 可在 server scripts 中使用，包含许多实用方法；当无法使用物品或方块 tags，
或直接使用它们不够安全时，这些方法可以简化 GTCEu Materials 的处理。


## ChemicalHelper 提供的实用函数

整合包作者可以使用以下函数：


### `.getMaterial()`

可接受几乎任何形式的物品引用（`Item`、`ItemStack`、`Ingredient` 等），并返回
与其关联的 `Material` 条目。如果没有关联的 Material，该方法返回 `null`。
也可以传入 `Fluid` 作为输入。


### `.getPrefix()`

接受一个物品引用作为输入，并返回与其关联的 TagPrefix。如果没有关联，
该方法返回 `null`。


### `.getIngot()` / `.getDust()`

这两个方法各接受两个参数作为输入：一个 `Material`，以及一个表示材料数量的数值，
并在对应形态存在时返回相应 Material 的粉或锭形式的 ItemStack。

材料数量通常很大；它一般是预定义值 `GTValues.M` 的整数倍或分数。
`GTValues.M` 是通用约定中一（1）个锭或普通粉的材料数量。

根据传入数量不同，这些函数会返回不同物品：

- 例如 `.getIngot()` 会在传入数量足够大或足够小时，返回关联 Material 的方块或粒形式的 ItemStack。
- 类似地，`.getDust()` 会根据传入的材料数量返回普通、小堆或小撮粉的 ItemStack 表示。


### `.getTag()` / `.getBlockTag()` / `.getTags()` / `.getBlockTags()`

接受一个 `TagPrefix` 和一个非 `null` 的 `Material` 作为输入，并返回由该
`TagPrefix`-`Material` 组合所代表物品拥有的第一个物品或方块 tag
（如果使用复数形式函数，则返回所有物品或方块 tags 的 Java array）。


### `.get()`

接受一个 `TagPrefix`、一个 `Material`，以及可选的物品数量（默认值为 1），并返回一个
表示该 `TagPrefix`-`Material` 组合且具有指定数量的 ItemStack。


## 使用示例

```js title="chemicalhelper_example_script.js"
var ironMaterial = ChemicalHelper.getMaterial(Item.of("gtceu:double_iron_plate").asItem()) // (1)
var rawOrePrefix = ChemicalHelper.getPrefix(Item.of("gtceu:raw_platinum").asItem()) // (2)
var cobaltIngotStack = ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Cobalt, 32) // (3)

var goldNugget = ChemicalHelper.getIngot(GTMaterials.Gold, GTValues.M / 9) // (4)
var steelBlock = ChemicalHelper.getIngot(GTMaterials.Steel, GTValues.M * 9)

var ashSmallDust = ChemicalHelper.getDust(GTMaterials.Ash, GTValues.M / 4) // (5)
```

1. `ironMaterial` 现在是对 `GTMaterials.Iron` 的引用。
2. `rawOrePrefix` 现在是对 `TagPrefix.rawOre` 的引用。
3. `cobaltIngotStack` 现在是表示半组钴锭的 ItemStack。
4. `goldNugget` 现在是表示一个金粒的 ItemStack。
5. `ashSmallDust` 现在是表示一小堆灰烬粉的 ItemStack。
