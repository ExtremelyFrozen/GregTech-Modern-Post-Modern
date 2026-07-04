---
title: "自定义并行仓"
---


# 自定义并行仓 Multi-Part（作者：Sparked）

## 并行仓

```js title="extra_parallel_hatch.js"

const $ParallelHatchPartMachine = Java.loadClass(
	'com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine'
) // (1)

GTCEuStartupEvents.registry('gtceu:machine', event => { // (2)
    event.create(
        "uhv_parallel_hatch", // (3)
        "custom",
        (holder, tier) => {
            return new $ParallelHatchPartMachine(holder, tier);
        },
        GTValues.UHV // (4)
    )
	.abilities(PartAbility.PARALLEL_HATCH) // (5)
	.workableTieredHullRenderer(GTCEu.id("block/machines/parallel_hatch_mk4")) // (6)
})
```

1. 加载构建并行仓 multi part 所需的 Java 类
2. 使用 GT 注册事件注册 multi part，它属于机器注册表的一部分
3. 新并行仓的 ID
4. 并行仓使用的 tier
5. 指定该 multipart 使用并行仓能力
6. 该 multipart 使用的纹理；本示例仅使用 t4 纹理作为占位
	你可以查看 gtm 的 assets，了解可编辑的动画和纹理