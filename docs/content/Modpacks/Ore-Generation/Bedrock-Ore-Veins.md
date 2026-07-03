---
title: "Bedrock Ore Veins（基岩矿脉）"
---


# Bedrock Ore Veins（基岩矿脉）

虽然默认未启用，但 GTCEu Modern 包含 bedrock ore veins 和 bedrock ore miners。

要启用此功能，需要启用配置选项 **Machines -> doBedrockOres** 并重启游戏。

!!! warning "默认没有配方"
    各等级的 bedrock ore miners 默认没有任何配方。整合包开发者需要自行为这些机器创建
    合成配方。


## 添加 Bedrock Veins

默认情况下，mod 不包含任何 bedrock ore veins。

可以使用 `bedrockOreVeins` server event 添加它们：

```js
GTCEuServerEvents.bedrockOreVeins(event => {
    event.add('kubejs:overworld_bedrock_ore_vein_iron', vein => {
        vein.weight(100)
            .size(3) // (1)
            .yield(10, 20)
            .material(GTMaterials.Goethite, 5) // (2)
            .material(GTMaterials.Limonite, 2)
            .material(GTMaterials.Hematite, 2)
            .material(GTMaterials.Malachite, 1)
            .dimensions('minecraft:overworld')
    })
})
```

1. Bedrock vein 的直径，以 chunks 为单位
2. 第二个参数定义每个循环中开采到各 Material 的概率
