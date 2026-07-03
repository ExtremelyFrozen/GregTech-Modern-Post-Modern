---
title: 自定义 Recipe Type
---


## 创建 Recipe Type

!!! important "Recipe Type 必须先于 Machine 或 Multiblock 注册"

```js title="test_recipe_type.js"
GTCEuStartupEvents.registry('gtceu:recipe_type', event => {
    event.create('test_recipe_type')
        .category('test')
        .setEUIO('in')
        .setMaxIOSize(3, 3, 3, 3) // (1)
        .setSlotOverlay(false, false, GuiTextures.SOLIDIFIER_OVERLAY)
        .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, FillDirection.LEFT_TO_RIGHT) // (2)
        .setSound(GTSoundEntries.COOLING)
})
```

1. 最大 Item 输入数、最大 Item 输出数、最大 Fluid 输入数、最大 Fluid 输出数
2. 可用的 ```GuiTextures``` 和 ```FillDirection``` 列表可在 GregTech Post Modern GitHub 或 .jar 文件中找到。

