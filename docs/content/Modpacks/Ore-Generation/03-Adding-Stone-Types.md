---
title: "添加 Stone Types"
---


# 为矿石方块添加 Stone Types

在整合包中，你可能想添加自己的 stone types，以便将 GT 的 Ore Generation 与其他 mod 的方块整合。

为此，你需要为矿石注册 tag prefix、添加语言键，并允许你的矿石实际生成。


## 文件

出于示例目的，本指南使用方块 “Blockium”（ID：`my_mod:blockium`）。
请将它替换为你想添加矿石的方块。

```js title="startup_scripts/ore_types.js"
GTCEuStartupEvents.registry('gtceu:tag_prefix', event => {
    event.create('blockium', 'ore') // (1)
        .stateSupplier(() => Block.getBlock('my_mod:blockium').defaultBlockState()) // (2)
        .baseModelLocation('my_mod:block/blockium') // (3)
        .unificationEnabled(true)
        .materialIconType(GTMaterialIconType.ore)
        .generationCondition(ItemGenerationCondition.hasOreProperty)
})
```

1. `create()` 的第一个参数是与你的 stone type 对应的名称。第二个参数**始终**是 `'ore'`！
2. 对于 `Block.getBlock()`，必须使用该 stone type 的方块 ID 作为参数。
3. 这是基础 stone type 模型的 `ResourceLocation`。如果基础方块使用自定义渲染，你可能需要创建自己的模型。


```json title="assets/gtceu/lang/en_us.json"
{
    "tagprefix.blockium": "Blockium %s Ore"
}
```


## 生成矿石


要让你的矿石实际生成在世界中，有几种选择。
如果只想在默认 Dimensions 中进行 Ore Generation，最简单的方式是将新的矿石基底方块加入以下 block tags 之一：


- **Overworld：** `minecraft:stone_ore_replaceables` 或 `minecraft:deepslate_ore_replaceables`
- **Nether：** `minecraft:nether_carver_replaceables`
- **The End：** forge 上使用 `forge:end_stone_ore_replaceables` / fabric 上使用 `c:end_stone_ore_replaceables`

??? example "将方块添加到 tag"
    ```js title="server_scripts/ore_type_tags.js"
    ServerEvents.tags('block', event => {
        event.add('minecraft:stone_ore_replaceables', 'my_mod:blockium')
    })
    ```

你也可以在其他 Dimensions 中添加矿石，但为此需要创建自定义 World Generation Layer。
你将在 [Layers & Dimensions](./04-Layers-and-Dimensions.md) 中学习如何操作。


## 非默认 BlockStates

某些 mod 可能会生成与其 `defaultBlockState` 不同的 `BlockState` 方块。
在这种情况下，你必须在矿石 stone type 的 `stateSupplier` 中指定实际生成的 block state：

```js
let UtilsJS = Java.loadClass("dev.latvian.mods.kubejs.util.UtilsJS")

GTCEuStartupEvents.registry('gtceu:tag_prefix', event => {
    event.create(type.path, 'ore')
        .stateSupplier(() => UtilsJS.parseBlockState("my_mod:blockium[some_blockstate_property=true]"))
})
```
