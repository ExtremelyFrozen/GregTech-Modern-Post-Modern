---
title: "Generators（生成器）"
---


# 矿脉与指示物生成器

## 矿脉生成器 { #vein-generators }


### 分层矿脉生成器

```js
vein.layeredVeinGenerator(generator => generator
    .buildLayerPattern(pattern => pattern
        .layer(l => l.weight(3).mat(GTMaterials.Silver).size(2, 4))
        .layer(l => l.weight(2).mat(GTMaterials.Gold).size(1, 1))
        .layer(l => l.weight(1).block(() => Block.getBlock('minecraft:oak_log')).size(1, 1))
        .layer(l => l.weight(1).state(() => Block.getBlock('minecraft:oak_planks').defaultBlockState()).size(1, 1))
    )
)
```


### 脉状矿脉生成器

```js
vein.veinedVeinGenerator(generator => generator
    .oreBlock(GTMaterials.Silver, 4) // (1)
    .rareBlock(GTMaterials.Gold, 1) // (2)
    .rareBlockChance(0.25)
    .veininessThreshold(0.1)
    .maxRichnessThreshold(0.3)
    .minRichness(0.3)
    .maxRichness(0.5)
    .edgeRoundoffBegin(10) // (3)
    .maxEdgeRoundoff(0.2) // (4)
)
```

1. **参数 1：** Material 或 block state
   **参数 2：** 生成权重
2. **参数 1：** Material 或 block state
   **参数 2：** 生成权重
3. 决定矿脉向末端变细的程度
4. 决定矿脉向末端变细的程度


!!! info "噪声参数"
    矿脉的噪声参数可以概括如下：
    - `veininessThreshold` 定义矿脉边缘有多“锐利”。
      值越高，边缘越“模糊”。
    - `maxRichnessThreshold` 定义矿脉_内部_生成多少矿石（必须 `>= veininessThreshold`）。
      两个值之间的距离越大，矿脉越不“充实”。
    - `minRichness` 和 `maxRichness` 允许你将此计算的输出限制在特定范围内。

    此计算的输出会决定矿脉中每个方块生成的概率。


!!! info "高度范围"
    如果你在矿脉定义中使用 `heightRangeUniform()` 或 `heightRangeTriangle()`，并且是在_设置生成器之前_调用，生成器的高度范围会自动推断。否则需要手动设置高度范围：

    ```js
    generator.minYLevel(10)
    generator.maxYLevel(90)
    ```


### 岩墙矿脉生成器

```js
vein.dikeVeinGenerator(generator => generator
    .withBlock(GTMaterials.Silver, 3, 20, 60) // (1)
    .withBlock(GTMaterials.Gold, 1, 20, 40)
)
```

1. **参数 1：** Material 或 block state
   **参数 2：** 生成权重
   **参数 3：** 最小 Y 坐标
   **参数 4：** 最大 Y 坐标

!!! info "高度范围"
    如果你在矿脉定义中使用 `heightRangeUniform()` 或 `heightRangeTriangle()`，并且是在_设置生成器之前_调用，生成器的高度范围会自动推断。否则需要手动设置高度范围：

    ```js
    generator.minYLevel(10)
    generator.maxYLevel(90)
    ```


### 标准矿脉生成器

```js
vein.standardVeinGenerator(generator => /* ... */)
```


### 晶洞矿脉生成器

```js
vein.geodeVeinGenerator(generator => /* ... */)
```


## 指示物生成器 { #indicator-generators }


### 地表岩石指示物

```js
vein.surfaceIndicatorGenerator(indicator => indicator
    .surfaceRock(GTMaterials.Platinum) // [*] (1)
    .placement("above") // (2)
    .density(0.4)
    .radius(5)
)
```

1. 除了 surface rock（地表岩石），也可以定义任意其他方块：
    ```js
    // Using a block:
    indicator.block(Block.getBlock('minecraft:oak_log'))

    // Using a block state:
    indicator.state(Block.getBlock('minecraft:oak_log').defaultBlockState())
    ```
2. 有效选项：
   `surface` 在世界表面生成指示物
   `above` 在上方下一个自由空间生成指示物
   `below` 在下方下一个自由空间生成指示物
   <br>
   **默认值：** `surface`
