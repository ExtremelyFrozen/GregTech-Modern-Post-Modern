---
title: "材料条目"
---

# 材料条目

GregTech 中有大量物品，也就会有大量用于合成这些物品的配方。不过，有时你会制作过多某种物品，并希望回收损失的材料。幸运的是，GT 内置的回收系统可以做到这一点。

任何会合成物品的配方，无论是通过 crafting table 还是 GT 机器，都可以指定为额外生成一个配方，用于在 Macerator、Arc Furnace 和 Extractor 中分解该输出物品。

## ItemMaterialInfo

在 7.0 之前，指定物品分解信息（称为 `ItemMaterialInfo`）的方式是像下面这样严格追加：

```java title="ItemMaterialInfo.java"
ChemicalHelper.registerMaterialInfo(GTBlocks.COIL_KANTHAL.get(),
                new ItemMaterialInfo(new MaterialStack(GTMaterials.Kanthal, M * 8), // double wire
                        new MaterialStack(GTMaterials.Aluminium, M * 2), // foil
                        new MaterialStack(GTMaterials.Copper, M)) // ingot
        ); // (1)

        VanillaRecipeHelper.addShapedRecipe(provider, true, // (2)
             "casing_bronze_bricks", GTBlocks.CASING_BRONZE_BRICKS.asStack(ConfigHolder.INSTANCE.recipes.casingsPerCraft),
             "PhP", "PBP", "PwP",
             'P', new MaterialEntry(TagPrefix.plate, GTMaterials.Bronze),
             'B', new ItemStack(Blocks.BRICKS));

```

1. `GTValues.M` 表示一个（1）mol 的材料量（通常等于 1 个完整粉的量）
2. 该 boolean 表示是否为此配方生成分解配方

在 7.0 中，引入了一个系统，可以自动检测配方输入，并在为结果物品生成分解配方时使用这些信息。

你可以用 `.addMaterialInfo()` 让配方只基于 item inputs 生成回收信息，也可以用 `addMaterialInfo(true, true)` 基于 item 和 fluid inputs 生成。还可以使用 `.removePreviousMaterialInfo()` 从输出物品上移除现有 ItemMaterialInfo，这会告诉 GT 不要为该配方的物品输出生成回收配方。

在 KubeJS 中，为配方添加分解信息如下：

```js title="itemDecomp.js"

ServerEvents.recipes(event => {
    event.recipes.gtceu.assembler('mv_hatch')
    .itemInputs('17x gtceu:iron_plate')
    .itemOutputs('1x gtceu:mv_energy_output_hatch')
    .duration(20)
    .addMaterialInfo(true) // (1)
    .EUt(10)

    event.recipes.gtceu.assembler('bucket')
    .itemInputs('4x minecraft:gold_ingot')
    .itemOutputs('minecraft:bucket')
    .removePreviousMaterialInfo() // (2)
    .duration(20)
    .EUt(23)
})

```

1. 生成一个将 MV energy hatch 研磨成 17 个 iron dust 的回收配方。
!!! note inline end
    如果原本存在回收配方，这会覆盖它。

2. Buckets 将不再拥有回收配方

ItemMaterialInfo 系统在向物品追加材料信息时，只会考虑配方的第一个 item output。不过，它会根据输出 stack 的数量自动缩放分解比例。

```js title="Seven Dirt"
ServerEvents.recipes(event => {
    event.recipes.gtceu.assembler('mv_hatch')
    .itemInputs('21x gtceu:iron_plate')
    .itemOutputs('7x minecraft:dirt')
    .duration(20)
    .addMaterialInfo(true) // (1)
    .EUt(10)
})
```

1. 每个 dirt 被研磨时都会变成 3 个 iron dust

## 带分解信息的 Crafting Table 配方

```js title="Crafting Table"
ServerEvents.recipes(event => {
    event.recipes.gtceu.shaped('4x kubejs:examplium', [
            " A ",
            "ABA",
            " A "
        ], {
            A: "gtceu:steel_ingot",
            B: "minecraft:nether_star"
        })
        .addMaterialInfo(true) // (1)
})
```

1. 每个 examplium 被研磨时都会变成 1 个 steel dust 和 1 个 small nether star dust

??? tip "Java 中的分解配方"
    你仍然可以用 `VanillaRecipeHelper` 为 shapeless 或 shaped recipes 生成分解信息，只是参数名从 `withUnificationData` 重命名为 `setMaterialInfoData`。
