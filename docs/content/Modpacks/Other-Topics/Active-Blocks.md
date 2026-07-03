---
title: 创建 Active Block
---

**Active Block** 是一种会在其所属 Multiblock 运行时切换模型的 Block，
例如 Engine Intake Casing 或 Assembly Line casing。

可以在 KubeJS 的 block registry event 中创建它，方式是将 `'gtceu:active'` 添加为 Block 类型。
blockstate JSON 会自动生成。

可用的标准类型有三种：`.simple()`、`.firebox()` 和 `.bloom()`：

- `.simple(texture)` 会创建一个 6 个面使用相同纹理的 Block，并在非活动纹理和活动纹理之间切换。活动纹理必须带有 `_active` 后缀。
- `.firebox(bottom, side, top)` 会创建一个 firebox casing，活动时会点亮火焰效果。
- `.bloom(texture)` 会创建一个活动时发光的 Block，类似 Assembly Line casing。bloom 纹理必须带有 `_bloom` 后缀。

使用这三种标准类型时，Block 的 model JSON 会自动生成。

如果想制作更复杂的 Active Block，可以省略标准类型，改为提供自己的 model JSON 文件。
你需要两个 model 文件，一个用于基础状态，一个用于活动状态，分别放在 KubeJS assets 中的 `models/block/BLOCK_ID.json` 和 `models/block/BLOCK_ID_active.json`。

#### 示例脚本
```js title="active_blocks.js"
StartupEvents.registry('block', event => {
    event.create('custom_engine_intake_casing', 'gtceu:active')
        .simple('kubejs:block/casings/custom_engine_intake')
        // The active texture should be 'kubejs:block/casings/custom_engine_intake_active'
        .displayName('Test Engine Intake')
        .soundType('metal')
        .resistance(6).hardness(5)
        .tagBlock("mineable/pickaxe")
        .tagBlock("forge:mineable/wrench")
        .requiresTool(true)

    event.create('custom_firebox', 'gtceu:active')
        .firebox('gtceu:block/casings/solid/machine_casing_bronze_plated_bricks',
            'gtceu:block/casings/firebox/machine_casing_firebox_bronze',
            'gtceu:block/casings/solid/machine_casing_bronze_plated_bricks')
        .displayName('Test Firebox')
        .soundType('metal')
        .resistance(6).hardness(5)
        .tagBlock("mineable/pickaxe")
        .tagBlock("forge:mineable/wrench")
        .requiresTool(true)

    event.create('custom_bloom', 'gtceu:active')
        .bloom('gtceu:block/casings/fusion/superconducting_coil')
        // The bloom texture should be 'gtceu:block/casings/fusion/superconducting_coil_bloom'
        .displayName('Test Bloom')
        .soundType('metal')
        .resistance(6).hardness(5)
        .tagBlock("mineable/pickaxe")
        .tagBlock("forge:mineable/wrench")
        .requiresTool(true)
})
```
