---
title: "Bedrock Fluid Veins（基岩流体矿脉）"
---


# Bedrock Fluid Veins（基岩流体矿脉）

Bedrock Fluid Veins 是存在于基岩层下方的不可见矿脉；要找到 Fluid Veins，至少需要 HV 等级的 Prospector。必须使用 Fluid Drilling Rig 才能从矿脉中取得流体。

## 创建 Bedrock Fluid Vein

```js title="fluid_veins.js"
// In server events
GTCEuServerEvents.fluidVeins(event => {

    event.add('gtceu:custom_bedrock_fluid_vein', vein => {
        vein.dimensions('minecraft:overworld') // (1)
        vein.fluid(() => Fluid.of('gtceu:custom_fluid').fluid)
        vein.weight(600)
        vein.minimumYield(120)
        vein.maximumYield(720)
        vein.depletionAmount(2)
        vein.depletionChance(1)
        vein.depletedYield(50)
    });
});

```

1. 流体矿脉会生成的 Dimension。
