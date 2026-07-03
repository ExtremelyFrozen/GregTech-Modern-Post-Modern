---
title: "自定义 Ore Veins"
---


# 创建和修改 Ore Veins

你可以使用 KJS 创建自己的自定义 Ore Veins。
也可以修改甚至删除已有矿脉。


## 创建新矿脉

```js title="server_scripts/custom_ore_vein.js"
GTCEuServerEvents.oreVeins(event => {
    event.add("kubejs:custom_vein", vein => {
        // Basic vein generation properties
        vein.weight(200) // [*] (1)
        vein.clusterSize(40) // [*] (2)
        vein.density(0.25) // [*] (3)
        vein.discardChanceOnAirExposure(0) // (4)

        // Define where the vein can generate
        vein.layer("deepslate") // [*] (5)
        vein.dimensions("minecraft:overworld") // (6)
        vein.biomes("#minecraft:is_overworld") // (7)

        // Define a height range:
        // You must choose EXACTLY ONE of these options! [*]
        vein.heightRangeUniform(-60, 20) // (8)
        vein.heightRangeTriangle(-60, 20) // (9)
        vein.heightRange(/* ... */) // (10)

        // Define the vein's generator:
        vein.generator(/* ... */) // [*] (11)

        // Add one or more type of surface indicator to the vein:
        vein.addIndicator(/* ... */) // (12)
    })
})
```

1. Ore Vein 的 weight 决定在一个可能的矿脉位置上，相比其他矿脉类型它被选中生成的概率。
   weight 越高，该 Ore Vein 类型生成得越频繁。
2. Cluster size 决定 Ore Vein 的直径。
3. Density 决定矿脉内部矿石出现的频率。
4. 决定矿石方块暴露在空气中时被跳过的概率。必须介于 `0` 和 `1` 之间。
   **默认值：** `0`
5. 参见 [Layers & Dimensions](./04-Layers-and-Dimensions.md)
6. 将矿脉生成限制到提供的 Dimensions。注意这些矿脉的 layer 必须适用于这些 Dimensions。
   **默认值：** 该矿脉 layer 的所有 Dimensions。
   <br>
   _接受任意数量的参数。_
7. 决定矿脉可以在哪些 Biome（或 Biome tag）中生成。
   **默认值：** 如果没有显式设置 Biome，矿脉会在任意 Biome 中生成。
   <br>
   _接受单个 Biome tag（以 `#` 为前缀），或任意数量的单独 Biomes。_
8. 在高度范围内均匀分布
9. 偏向高度范围中心
10. 也可以直接使用 Minecraft 的 `HeightRangePlacement`，而不是上面的简写版本：
    ```js
    vein.heightRange(
        height: {
            type: "uniform",
            min_inclusive: {
                absolute: -60
            },
            max_inclusive: {
                absolute: 20
            }
        })
    ```
11. 可用矿脉生成器列表见 [矿脉生成器](./02-Generators.md#vein-generators)。
12. 可用指示物生成器列表见 [指示物生成器](./02-Generators.md#indicator-generators)。


??? example "为 Ore Vein 创建新的 Biome tag"
    如果想把 Ore Vein 限制到多个尚无公共 tag 的 Biomes，可以手动指定所有 Biomes，也可以创建一个 Biome tag：

    ```js title="server_scripts/biome_tags.js"
    ServerEvents.tags('biome', event => {
        event.add('kubejs:my_biome_tag', 'minecraft:forest')
        event.add('kubejs:my_biome_tag', 'minecraft:river')
    })
    ```

    之后只需在矿脉定义中调用 `vein.biomes('#kubejs:my_biome_tag')` 即可使用你的 Biome tag。


## 移除现有 Ore Vein

```js title="server_scripts/remove_ore_vein.js"
GTCEuServerEvents.oreVeins(event => {
     event.remove("gtceu:magnetite_vein_ow")
})
```


??? example "移除所有 Ore Veins"
    如果想移除**所有**预定义 Ore Veins（例如你想在整合包中完全改变 Ore Generation），
    可以使用以下代码：

    ```js
    GTCEuServerEvents.oreVeins(event => {
        event.removeAll()
    })
    ```

    也可以筛选要移除的矿脉：

    ```js
    event.removeAll((id, vein) => id.path != "magnetite_vein_ow")
    ```


## 修改现有矿脉

```js title="server_scripts/modify_ore_vein.js"
GTCEuServerEvents.oreVeins(event => {
    event.modify("gtceu:cassiterite_vein", vein => {
        vein.density(1.0)
    })
})
```

用于修改矿脉的 API 与创建新矿脉时相同。


!!! warning "将矿脉移动到其他 Dimensions"
    将默认矿脉之一移动到另一个 Dimension 时，请记得也要相应修改它们的 Biome(s)。


??? example "修改所有现有矿脉"
    你也可以一次修改所有现有 Ore Veins：

    ```js title="server_scripts/modify_all_veins.js"
    GTCEuServerEvents.oreVeins(event => {
        event.modifyAll((id, vein) => {
            console.log("Modifying vein: " + id)
            vein.density(1.0)
        })
    })
    ```
