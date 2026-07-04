---
title: "Layers 与 Dimensions"
---


# Layers 与 Dimensions


## 创建新的 World Gen Layer

要在其他 Dimension 中（或只在某些方块所在位置）创建 Ore Veins，需要创建新的 worldgen layer。
你可能还需要为矿石添加自定义 stone type。

```js title="startup_scripts/world_gen_layers.js"
GTCEuStartupEvents.registry('gtceu:world_gen_layer', event => {
    event.create('my_custom_layer')
        .targets('#minecraft:stone_ore_replaceables', 'minecraft:endstone') // [*] (1)
        .dimensions('minecraft:overworld', 'minecraft:the_end') // [*]
})
```

1. 接受 tags、blocks 和 block states。
   如果你需要更高灵活性，也接受 `RuleTest` 或 `RuleTestSupplier`。


创建 layer 后，你可以在创建或修改 Ore Vein 时通过名称引用它：

```js title="server_scripts/ores.js"
GTCEuServerEvents.oreVeins(event => {
    event.add("kubejs:custom_vein", vein => {
        vein.layer("my_custom_layer")
        // ...
    })
})
```
