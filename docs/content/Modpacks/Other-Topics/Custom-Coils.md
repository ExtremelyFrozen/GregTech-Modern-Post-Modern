---
title: 自定义 Coil
---


## Coil 创建 { #standard-coils }

```js
StartupEvents.registry('block', event => {
    event.create('infinity_coil_block', 'gtceu:coil')
        .temperature(100)
        .level(0)
        .energyDiscount(1) // (1)
        .tier(10)
        .coilMaterial(() => GTMaterials.get('infinity'))
        .texture('kubejs:block/example_block')
        .hardness(5)
        .requiresTool(true)
        .material('metal')
})
```

1. Energy Discount 必须至少为 1。
